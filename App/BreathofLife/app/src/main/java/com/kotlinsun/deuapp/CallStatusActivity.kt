package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.app.Dialog // 커스텀 다이얼로그 생성을 위한 Dialog 클래스
import android.content.Intent // 액티비티 전환 및 데이터 전달을 위한 Intent 클래스
import android.location.Location // GPS 등에서 얻은 위치 정보를 담는 클래스
import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.util.Log // 로그 출력을 위한 유틸리티 클래스
import android.view.Window // 다이얼로그의 윈도우 속성을 제어하기 위한 클래스
import android.widget.Button // 버튼 UI 요소
import android.widget.TextView // 텍스트 뷰 UI 요소
import android.widget.Toast // 사용자에게 짧은 메시지를 표시하는 Toast 클래스
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import androidx.lifecycle.lifecycleScope // 코루틴을 액티비티/프래그먼트의 라이프사이클에 바인딩하는 스코프
import com.google.firebase.auth.ktx.auth // Firebase Authentication KTX 확장
import com.google.firebase.firestore.FieldValue // Firestore 필드 값 유틸리티 클래스 (서버 타임스탬프 등)
import com.google.firebase.firestore.FirebaseFirestore // Firebase Firestore 데이터베이스 클라이언트
import com.google.firebase.firestore.GeoPoint // Firestore 지리적 위치 데이터 타입
import com.google.firebase.firestore.ListenerRegistration // Firestore 실시간 리스너 등록 객체
import com.google.firebase.ktx.Firebase // Firebase KTX 확장
import com.kotlinsun.deuapp.databinding.ActivityCallStatusBinding // 뷰 바인딩 클래스
import kotlinx.coroutines.Job // 코루틴 작업 객체
import kotlinx.coroutines.delay // 코루틴 내에서 지연을 위한 함수
import kotlinx.coroutines.launch // 코루틴 시작을 위한 함수

// CallStatusActivity 클래스 선언: AppCompatActivity를 상속
class CallStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallStatusBinding // 뷰 바인딩 인스턴스
    private val db = FirebaseFirestore.getInstance() // Firebase Firestore 데이터베이스 인스턴스
    private val auth = Firebase.auth // Firebase Authentication 인스턴스
    private val TAG = "CallStatusActivity" // 로그캣에 사용할 태그

    private var currentCallId: String? = null // 현재 처리 중인 호출(call)의 ID
    private var callListener: ListenerRegistration? = null // Firestore 실시간 리스너 등록 객체
    private var timeoutJob: Job? = null // 타임아웃 코루틴 Job
    private var currentPatientInfo: Map<String, Any>? = null // 현재 환자 정보를 저장할 변수

    // 액티비티가 처음 생성될 때 호출되는 콜백 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallStatusBinding.inflate(layoutInflater) // 뷰 바인딩 인스턴스 초기화
        setContentView(binding.root) // 액티비티의 레이아웃 설정

        currentCallId = intent.getStringExtra("CALL_ID") // Intent에서 호출 ID 가져오기
        // 호출 ID가 없으면 오류 메시지 표시 후 액티비티 종료
        if (currentCallId == null) {
            Toast.makeText(this, "오류: 호출 ID가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 첫 요청 시에는 30초 타임아웃을 적용하여 리스너 시작
        listenToCall(currentCallId!!, true)

        // 요청 취소 버튼 클릭 리스너 설정
        binding.buttonCancelRequest.setOnClickListener {
            cancelRequest() // 요청 취소 함수 호출
        }

        // 환자 정보 보기 버튼 클릭 리스너 설정
        binding.buttonShowPatientInfo.setOnClickListener {
            // currentPatientInfo가 null이 아니면 다이얼로그 표시, null이면 로딩 중 메시지
            currentPatientInfo?.let {
                showPatientInfoDialog(it)
            } ?: run {
                Toast.makeText(this, "환자 정보를 불러오는 중입니다...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 사용자가 응급 요청을 직접 취소하고 Firestore에서 해당 문서를 삭제하는 함수
     */
    private fun cancelRequest() {
        timeoutJob?.cancel() // 진행 중인 타임아웃 코루틴 취소
        callListener?.remove() // Firestore 리스너 제거

        // 현재 호출 ID가 null이 아니면 Firestore에서 문서 삭제
        if (currentCallId != null) {
            db.collection("emergency_calls").document(currentCallId!!)
                .delete() // 문서 삭제
                .addOnSuccessListener {
                    Log.d(TAG, "요청($currentCallId)이 사용자에 의해 취소 및 삭제되었습니다.") // 성공 로그
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "문서 삭제 중 오류 발생", e) // 실패 로그
                }
        }
        Toast.makeText(this, "요청이 취소되었습니다.", Toast.LENGTH_SHORT).show() // 사용자에게 취소 메시지
        finish() // 액티비티 종료
    }

    /**
     * 특정 응급 콜의 상태 변화를 실시간으로 감시하고, 필요 시 30초 타임아웃을 설정하는 함수
     * @param callId 감시할 호출 ID
     * @param applyTimeout 타임아웃을 적용할지 여부 (초기 요청 시 true, 재요청 시 false)
     */
    private fun listenToCall(callId: String, applyTimeout: Boolean) {
        // UI 상태 메시지 업데이트
        binding.textViewStatus.text = if (applyTimeout) "주변 병원에 응답을 요청하는 중..." else "확장된 반경으로 요청 중..."

        callListener?.remove() // 기존 리스너가 있다면 제거
        timeoutJob?.cancel() // 기존 타임아웃 Job이 있다면 취소

        val callDocRef = db.collection("emergency_calls").document(callId) // 해당 호출 문서 참조
        // Firestore 문서 스냅샷 리스너 추가 (실시간 변경 감지)
        callListener = callDocRef.addSnapshotListener { snapshot, e ->
            // 리스너 오류가 있거나, 스냅샷이 null이거나, 문서가 존재하지 않으면 로그 출력 후 종료
            if (e != null || snapshot == null || !snapshot.exists()) {
                Log.w(TAG, "리스너 오류 또는 문서 없음: ", e)
                return@addSnapshotListener
            }

            // 환자 정보를 클래스 변수에 저장하여 '환자 정보 보기' 버튼에서 사용 가능하도록 함
            this.currentPatientInfo = snapshot.get("patientInfo") as? Map<String, Any>

            // 문서의 'status' 필드가 "accepted" (수락됨)인지 확인
            if (snapshot.getString("status") == "accepted") {
                Log.d(TAG, "요청이 수락되었습니다!") // 로그 출력
                timeoutJob?.cancel() // 타임아웃 Job 취소
                callListener?.remove() // Firestore 리스너 제거

                val acceptedHospitalId = snapshot.getString("acceptedHospitalId") // 수락한 병원 ID 가져오기
                // CallAcceptedActivity로 이동하는 Intent 생성 및 데이터 추가
                val intent = Intent(this, CallAcceptedActivity::class.java).apply {
                    putExtra("CALL_ID", callId)
                    putExtra("HOSPITAL_ID", acceptedHospitalId)
                }
                startActivity(intent) // 액티비티 시작
                finish() // 현재 액티비티 종료
            }
        }

        // applyTimeout이 true일 경우에만 타임아웃 Job 시작
        if (applyTimeout) {
            timeoutJob = lifecycleScope.launch {
                delay(30000) // 30초 대기
                Log.d(TAG, "30초 타임아웃. 반경을 30km로 확장합니다.") // 타임아웃 로그
                binding.textViewStatus.text = "응답이 없어 반경을 30km로 확장하여 재요청합니다..." // UI 상태 메시지 업데이트
                callListener?.remove() // 기존 리스너 제거
                expandSearchAndReRequest(callId) // 검색 반경 확장 및 재요청 함수 호출
            }
        }
    }

    /**
     * 기존 요청 정보를 바탕으로 검색 반경을 확장하여 재요청하는 절차를 시작하는 함수
     * @param originalCallId 기존 응급 호출 ID
     */
    private fun expandSearchAndReRequest(originalCallId: String) {
        val originalCallDocRef = db.collection("emergency_calls").document(originalCallId) // 기존 호출 문서 참조

        originalCallDocRef.get() // 기존 문서 데이터 가져오기
            .addOnSuccessListener { originalCallDoc ->
                // 문서가 null이거나 존재하지 않으면 함수 종료
                if (originalCallDoc == null || !originalCallDoc.exists()) {
                    return@addOnSuccessListener
                }

                // 기존 문서에서 환자 정보, 사용자 위치(GeoPoint), 추천 진료과 목록 추출
                val patientInfo = originalCallDoc.get("patientInfo") as HashMap<String, Any>
                val userGeoPoint = originalCallDoc.getGeoPoint("location")!! // 위치는 필수이므로 !! 사용
                val recommendedDepts = originalCallDoc.get("recommendedDepartments") as? List<String>

                // 재요청에 필요한 정보(추천 진료과)가 부족하면 오류 메시지 표시
                if (recommendedDepts.isNullOrEmpty()) {
                    binding.textViewStatus.text = "오류: 재요청에 필요한 정보가 부족합니다."
                    return@addOnSuccessListener
                }

                // GeoPoint를 Location 객체로 변환
                val userLocation = Location("").apply {
                    latitude = userGeoPoint.latitude
                    longitude = userGeoPoint.longitude
                }

                // 기존 요청 문서를 Firestore에서 삭제
                originalCallDocRef.delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "기존 요청 문서($originalCallId)를 성공적으로 삭제했습니다.") // 삭제 성공 로그
                        val radiiToSearch = listOf(10000.0, 30000.0) // 재요청 시 검색할 새로운 반경 목록 (10km, 30km)
                        // 새로운 반경으로 병원 검색 재귀 함수 호출 (인덱스 0부터 시작)
                        findNearbyHospitalsRecursively(userLocation, patientInfo, recommendedDepts, radiiToSearch, 0)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "기존 요청 문서 삭제에 실패했습니다.", e) // 삭제 실패 로그
                        // 삭제 실패 시에도 새로운 반경으로 병원 검색 시도 (기존 반경과 다른 10km, 20km)
                        val radiiToSearch = listOf(10000.0, 20000.0)
                        findNearbyHospitalsRecursively(userLocation, patientInfo, recommendedDepts, radiiToSearch, 0)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "재요청을 위한 원본 문서 로드에 실패했습니다.", e) // 원본 문서 로드 실패 로그
                Toast.makeText(this, "재요청에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show() // 사용자에게 실패 메시지
                finish() // 액티비티 종료
            }
    }

    /**
     * 재요청 시 반경을 순차적으로 넓혀가며 적합한 병원을 찾는 재귀 함수
     * @param userLocation 현재 사용자 위치
     * @param patientInfo 환자 정보
     * @param recommendedDepts 추천 진료과 목록
     * @param radii 검색할 반경 목록
     * @param index 현재 검색 중인 반경의 인덱스
     */
    private fun findNearbyHospitalsRecursively(userLocation: Location, patientInfo: HashMap<String, Any>, recommendedDepts: List<String>, radii: List<Double>, index: Int) {
        // 모든 반경을 다 검색했음에도 병원을 찾지 못했을 경우
        if (index >= radii.size) {
            binding.textViewStatus.text = "오류: 확장된 반경 내에 조건에 맞는 병원이 없습니다." // 상태 메시지 업데이트
            return // 함수 종료
        }
        val currentRadius = radii[index] // 현재 검색할 반경 (미터 단위)
        val radiusInKm = (currentRadius / 1000).toInt() // 반경을 킬로미터로 변환
        binding.textViewStatus.text = "응답이 없어 반경을 ${radiusInKm}km로 확장하여 재요청합니다..." // UI 상태 메시지 업데이트

        // Firestore "hospitals" 컬렉션에서 추천 진료과를 포함하는 병원 검색
        db.collection("hospitals")
            .whereArrayContainsAny("availableDepartments", recommendedDepts)
            .get()
            .addOnSuccessListener { hospitalSnapshot -> // 병원 문서 가져오기 성공 시
                val nearbyHospitalIds = hospitalSnapshot.documents.mapNotNull { doc ->
                    val hospital = doc.toObject(Hospital::class.java) // 문서를 Hospital 객체로 변환
                    hospital?.location?.let { loc -> // 병원 위치 정보가 있다면
                        val distance = calculateDistance(userLocation, loc) // 사용자 위치와 병원 간 거리 계산
                        if (distance <= currentRadius) hospital.id else null // 현재 반경 내에 있으면 병원 ID 반환
                    }
                }

                if (nearbyHospitalIds.isEmpty()) {
                    // 현재 반경 내에 병원이 없으면 다음 반경으로 재귀 호출
                    findNearbyHospitalsRecursively(userLocation, patientInfo, recommendedDepts, radii, index + 1)
                } else {
                    // 병원을 찾았다면 새로운 응급 콜 문서 생성
                    createNewCall(userLocation, patientInfo, nearbyHospitalIds, recommendedDepts)
                }
            }
        // 실패 시 오류 처리 (현재는 명시적인 onFailureListener 없음, 필요 시 추가 가능)
    }

    /**
     * 확장된 반경으로 찾은 병원들을 대상으로 새로운 응급 콜 문서를 Firestore에 생성하는 함수
     * @param userLocation 현재 사용자 위치
     * @param patientInfo 환자 정보
     * @param nearbyHospitalIds 주변에 있는 병원 ID 목록
     * @param recommendedDepts 추천 진료과 목록
     */
    private fun createNewCall(userLocation: Location, patientInfo: HashMap<String, Any>, nearbyHospitalIds: List<String>, recommendedDepts: List<String>) {
        // 새로운 응급 호출 데이터 맵 생성
        val newCallData = hashMapOf(
            "paramedicId" to auth.currentUser!!.uid, // 현재 로그인한 구급대원 ID
            "patientInfo" to patientInfo, // 환자 정보
            "location" to GeoPoint(userLocation.latitude, userLocation.longitude), // 현재 위치 GeoPoint
            "status" to "pending", // 초기 상태 "pending" (대기 중)
            "acceptedHospitalId" to null, // 수락 병원 ID (초기에는 null)
            "createdAt" to FieldValue.serverTimestamp(), // 생성 시간 (서버 타임스탬프)
            "completedAt" to null, // 완료 시간 (초기에는 null)
            "targetedHospitalIds" to nearbyHospitalIds, // 대상 병원 ID 목록
            "recommendedDepartments" to recommendedDepts // 추천 진료과 목록
        )

        db.collection("emergency_calls").add(newCallData) // "emergency_calls" 컬렉션에 새 문서 추가
            .addOnSuccessListener { newDocRef -> // 문서 추가 성공 시
                Log.d(TAG, "반경 확장 후 새 요청 생성 완료: ${newDocRef.id}") // 성공 로그
                this.currentCallId = newDocRef.id // 현재 호출 ID를 새로 생성된 문서 ID로 업데이트
                listenToCall(newDocRef.id, false) // 새로운 호출 ID로 리스너 시작 (이때는 타임아웃 미적용)
            }
        // 실패 시 오류 처리 (현재는 명시적인 onFailureListener 없음, 필요 시 추가 가능)
    }

    /**
     * 환자 상세 정보 다이얼로그를 표시하는 함수
     * @param patientInfo 표시할 환자 정보 맵
     */
    private fun showPatientInfoDialog(patientInfo: Map<String, Any>) {
        val dialog = Dialog(this) // 새 Dialog 객체 생성
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE) // 다이얼로그 타이틀 바 제거
        dialog.setContentView(R.layout.dialog_case_details) // 커스텀 레이아웃 설정

        // 다이얼로그 내의 UI 요소들 참조 가져오기
        val tvName: TextView = dialog.findViewById(R.id.textViewDetailName)
        val tvAge: TextView = dialog.findViewById(R.id.textViewDetailAge)
        val tvGender: TextView = dialog.findViewById(R.id.textViewDetailGender)
        val tvSymptom: TextView = dialog.findViewById(R.id.textViewDetailSymptom)
        val tvOtherInfo: TextView = dialog.findViewById(R.id.textViewDetailOtherInfo)
        val btnClose: Button = dialog.findViewById(R.id.buttonClose)

        // 환자 정보 맵에서 데이터를 가져와 텍스트 뷰에 설정
        tvName.text = "이름: ${patientInfo["name"] ?: "정보 없음"}"
        tvAge.text = "나이: ${patientInfo["age"] ?: "정보 없음"}"
        tvGender.text = "성별: ${patientInfo["gender"] ?: "정보 없음"}"
        tvSymptom.text = "주요 증상: ${patientInfo["symptom"] ?: "정보 없음"}"
        tvOtherInfo.text = "기타 정보: ${patientInfo["otherInfo"] ?: "정보 없음"}"

        // 닫기 버튼 클릭 리스너 설정
        btnClose.setOnClickListener {
            dialog.dismiss() // 다이얼로그 닫기
        }

        dialog.show() // 다이얼로그 표시
    }

    /**
     * 두 지점(Location 객체와 GeoPoint 객체) 사이의 거리를 미터(m) 단위로 계산하는 유틸리티 함수
     * @param start 시작 지점 (Location 객체)
     * @param end 종료 지점 (GeoPoint 객체)
     * @return 두 지점 간의 거리 (float)
     */
    private fun calculateDistance(start: Location, end: GeoPoint): Float {
        val results = FloatArray(1) // 거리 결과를 저장할 배열
        // Location.distanceBetween 함수를 사용하여 위도, 경도 기반 거리 계산 (미터 단위)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0] // 계산된 거리 반환
    }

    /**
     * 액티비티가 소멸될 때 호출되는 콜백 메서드.
     * 메모리 누수를 방지하기 위해 Firestore 리스너와 코루틴 Job을 정리합니다.
     */
    override fun onDestroy() {
        super.onDestroy()
        callListener?.remove() // Firestore 리스너 제거
        timeoutJob?.cancel() // 타임아웃 코루틴 Job 취소
    }
}