package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.Manifest // Android 권한 관련 클래스 (예: ACCESS_FINE_LOCATION)
import android.annotation.SuppressLint // Lint 경고 억제를 위한 어노테이션
import android.content.Intent // 액티비티 전환 및 데이터 전달을 위한 Intent 클래스
import android.content.pm.PackageManager // 패키지 관리 및 권한 확인을 위한 클래스
import android.location.Location // GPS 등에서 얻은 위치 정보를 담는 클래스
import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.util.Log // 로그 출력을 위한 유틸리티 클래스
import android.view.View // 안드로이드 UI 구성 요소의 기본 클래스
import android.widget.Toast // 사용자에게 짧은 메시지를 표시하는 Toast 클래스
import androidx.activity.result.contract.ActivityResultContracts // 액티비티 결과를 처리하기 위한 계약 클래스
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import androidx.core.content.ContextCompat // 리소스 및 권한 관련 유틸리티 클래스
import androidx.lifecycle.lifecycleScope // 코루틴을 액티비티/프래그먼트의 라이프사이클에 바인딩하는 스코프
import com.google.ai.client.generativeai.GenerativeModel // Google Generative AI 모델을 사용하는 클래스
import com.google.android.gms.location.LocationServices // Google Location Service 클라이언트
import com.google.android.gms.location.Priority // 위치 요청 우선순위 설정
import com.google.firebase.auth.ktx.auth // Firebase Authentication KTX 확장 (FirebaseAuth 인스턴스 가져오기)
import com.google.firebase.firestore.FieldValue // Firestore 필드 값 유틸리티 클래스 (서버 타임스탬프 등)
import com.google.firebase.firestore.FirebaseFirestore // Firebase Firestore 데이터베이스 클라이언트
import com.google.firebase.firestore.GeoPoint // Firestore 지리적 위치 데이터 타입
import com.google.firebase.ktx.Firebase // Firebase KTX 확장 (Firebase 초기화 및 서비스 접근)
import com.kotlinsun.deuapp.databinding.ActivityRequestBinding // 뷰 바인딩을 통해 레이아웃의 뷰에 접근하는 생성된 클래스
import kotlinx.coroutines.launch // 코루틴 시작을 위한 함수

// RequestActivity 클래스 선언: AppCompatActivity를 상속
class RequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestBinding // UI 요소에 접근하기 위한 뷰 바인딩 인스턴스
    private val db = FirebaseFirestore.getInstance() // Firebase Firestore 데이터베이스 인스턴스
    private val auth = Firebase.auth // Firebase Authentication 인스턴스
    private val TAG = "RequestActivity" // 로그캣에 사용할 태그

    // Gemini AI 모델 초기화
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash", // 사용할 Gemini 모델의 이름
        apiKey = "" // ⚠️ 여기에 자신의 Gemini API 키를 입력하세요! (실제 배포 시에는 더 안전한 방식으로 관리해야 합니다)
    )

    // 위치 권한 요청 결과를 처리하는 ActivityResultLauncher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions() // 여러 권한을 한 번에 요청
    ) { permissions ->
        // ACCESS_FINE_LOCATION 권한이 허용되었는지 확인
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            createInitialCall() // 권한이 있으면 초기 호출 생성 진행
        } else {
            Toast.makeText(this, "위치 권한이 없어 주변 병원을 찾을 수 없습니다.", Toast.LENGTH_LONG).show() // 권한 없으면 토스트 메시지
            showLoading(false) // 로딩 UI 숨김
        }
    }

    // 액티비티가 처음 생성될 때 호출되는 콜백 메소드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestBinding.inflate(layoutInflater) // 뷰 바인딩 인스턴스 초기화
        setContentView(binding.root) // 액티비티의 레이아웃 설정

        // 요청 버튼 레이아웃 클릭 리스너 설정
        binding.requestButtonLayout.setOnClickListener {
            createInitialCall() // 초기 호출 생성 함수 호출
        }
        // 뒤로가기 버튼 클릭 리스너 설정
        binding.backBtncall.setOnClickListener {
            finish() // 현재 액티비티 종료
        }
    }

    // 초기 응급 호출을 생성하는 함수 (증상 유효성 검사 및 위치 권한 확인)
    private fun createInitialCall() {
        val symptom = binding.editTextSymptom.text.toString().trim() // 증상 입력 필드 텍스트 가져오기 (앞뒤 공백 제거)
        if (symptom.isEmpty()) { // 증상이 비어있으면
            Toast.makeText(this, "주요 증상을 입력해주세요.", Toast.LENGTH_SHORT).show() // 토스트 메시지 표시
            return // 함수 종료
        }
        showLoading(true, "현재 위치 확인 중...") // 로딩 UI 표시 및 메시지 설정
        // ACCESS_FINE_LOCATION 권한이 부여되었는지 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndProceed() // 권한이 있으면 현재 위치 가져오기 진행
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) // 권한이 없으면 권한 요청
        }
    }

    // 현재 위치를 가져오고 다음 단계로 진행하는 함수 (권한이 이미 있다고 가정)
    @SuppressLint("MissingPermission") // Lint 경고: 권한 검사 없이 위치 API를 사용하고 있음을 무시
    private fun getCurrentLocationAndProceed() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) // FusedLocationProviderClient 인스턴스 가져오기
        // 현재 위치를 높은 정확도로 한 번만 가져오도록 요청
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? -> // 위치 정보 가져오기 성공 시
                if (location != null) { // 위치 정보가 null이 아니면
                    val symptom = binding.editTextSymptom.text.toString().trim() // 다시 증상 텍스트 가져오기
                    getDepartmentsFromSymptom(symptom, location) // 증상 기반 진료과 찾기 진행
                } else {
                    Toast.makeText(this, "현재 위치를 가져올 수 없습니다. GPS를 확인해주세요.", Toast.LENGTH_LONG).show() // 위치 정보 없으면 토스트 메시지
                    showLoading(false) // 로딩 UI 숨김
                }
            }
            .addOnFailureListener { // 위치 정보 가져오기 실패 시
                Toast.makeText(this, "현재 위치를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show() // 토스트 메시지
                showLoading(false) // 로딩 UI 숨김
            }
    }

    // 증상에 따라 관련 진료과를 추천받는 함수 (Gemini AI 사용)
    private fun getDepartmentsFromSymptom(symptom: String, userLocation: Location) {
        showLoading(true, "증상 분석 및 병원 검색 중...") // 로딩 UI 표시 및 메시지 업데이트
        // 미리 정의된 진료과 목록 (쉼표로 구분된 문자열로 변환)
        val departmentList = listOf(
            "가정의학과", "내과", "마취통증의학과", "병리과", "비뇨의학과", "산부인과",
            "성형외과", "소아청소년과", "신경외과", "안과", "영상의학과",
            "이비인후과", "재활의학과", "정신건강의학과", "정형외과", "직업환경의학과",
            "진단검사의학과", "피부과", "핵의학과", "흉부외과"
        ).joinToString(", ")

        // Gemini AI에 보낼 프롬프트: 증상에 맞는 진료과 추천 요청
        val prompt = "환자의 주요 증상은 '$symptom' 입니다. 이 증상과 가장 관련성이 높은 진료과를 다음 목록에서 최대 2개 골라주세요: [$departmentList]. 다른 설명은 모두 제외하고, 쉼표(,)로 구분된 진료과 이름만 응답해주세요."

        lifecycleScope.launch { // 비동기 작업을 위한 코루틴 시작
            try {
                val response = generativeModel.generateContent(prompt) // Gemini 모델에 프롬프트 전송 및 응답 받기
                // 응답 텍스트를 쉼표로 분리하고 공백 제거, 비어있지 않은 항목만 필터링하여 추천 진료과 목록 생성
                val recommendedDepts = response.text?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

                if (recommendedDepts.isNullOrEmpty()) { // 추천 진료과가 없으면
                    Toast.makeText(this@RequestActivity, "증상에 맞는 진료과를 찾지 못했습니다.", Toast.LENGTH_LONG).show() // 토스트 메시지
                    showLoading(false) // 로딩 UI 숨김
                } else {
                    Log.d(TAG, "Gemini 추천 진료과: $recommendedDepts") // 추천 진료과 로그 출력
                    val radiiToSearch = listOf(5000.0, 10000.0, 20000.0) // 검색할 반경 목록 (미터 단위: 5km, 10km, 20km)
                    // 재귀적으로 주변 병원 검색 시작
                    findNearbyHospitalsRecursively(userLocation, recommendedDepts, radiiToSearch, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini API 호출 오류", e) // API 호출 실패 시 오류 로그
                Toast.makeText(this@RequestActivity, "증상 분석 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show() // 토스트 메시지
                showLoading(false) // 로딩 UI 숨김
            }
        }
    }

    // 재귀적으로 주변 병원을 찾아 응급 요청을 제출하는 함수
    private fun findNearbyHospitalsRecursively(userLocation: Location, recommendedDepts: List<String>, radii: List<Double>, index: Int) {
        // 모든 반경을 검색했음에도 병원을 찾지 못했을 경우
        if (index >= radii.size) {
            Toast.makeText(this, "조건에 맞는 병원이 주변에 없습니다.", Toast.LENGTH_LONG).show() // 토스트 메시지
            showLoading(false) // 로딩 UI 숨김
            return // 함수 종료
        }
        val currentRadius = radii[index] // 현재 검색할 반경
        val radiusInKm = (currentRadius / 1000).toInt() // 반경을 킬로미터 단위로 변환
        showLoading(true, "$radiusInKm km 반경 내 병원 검색 중...") // 로딩 UI 메시지 업데이트

        // Firestore "hospitals" 컬렉션에서 추천 진료과를 포함하는 병원 필터링
        db.collection("hospitals")
            .whereArrayContainsAny("availableDepartments", recommendedDepts)
            .get() // 문서 가져오기
            .addOnSuccessListener { hospitalSnapshot -> // 병원 문서 가져오기 성공 시
                val nearbyHospitalIds = hospitalSnapshot.documents.mapNotNull { doc ->
                    val hospital = doc.toObject(Hospital::class.java) // 문서를 Hospital 객체로 변환
                    hospital?.location?.let { loc -> // 병원 위치 정보가 있다면
                        val distance = calculateDistance(userLocation, loc) // 사용자 위치와 병원 간 거리 계산
                        if (distance <= currentRadius) hospital.id else null // 현재 반경 내에 있으면 병원 ID 반환
                    }
                }

                if (nearbyHospitalIds.isEmpty()) { // 현재 반경 내에 병원이 없으면
                    // 다음 반경으로 재귀 호출하여 검색 범위 확장
                    findNearbyHospitalsRecursively(userLocation, recommendedDepts, radii, index + 1)
                } else {
                    submitEmergencyCall(userLocation, nearbyHospitalIds, recommendedDepts) // 병원을 찾았다면 응급 호출 제출
                }
            }
            .addOnFailureListener { // 병원 목록 불러오기 실패 시
                Toast.makeText(this, "병원 목록을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show() // 토스트 메시지
                showLoading(false) // 로딩 UI 숨김
            }
    }

    // 응급 호출 데이터를 Firebase Firestore에 제출하는 함수
    private fun submitEmergencyCall(userLocation: Location, targetHospitalIds: List<String>, recommendedDepts: List<String>) {
        // UI에서 환자 정보 가져오기 (앞뒤 공백 제거)
        val patientName = binding.editTextPatientName.text.toString().trim()
        val patientAge = binding.editTextPatientAge.text.toString().trim()
        val patientGender = binding.editTextPatientGender.text.toString().trim()
        val symptom = binding.editTextSymptom.text.toString().trim()
        val otherInfo = binding.editTextOtherInfo.text.toString().trim()

        // 환자 이름, 나이, 성별이 필수 정보인지 확인
        if (patientName.isEmpty() || patientAge.isEmpty() || patientGender.isEmpty()) {
            Toast.makeText(this, "환자 이름, 나이, 성별은 필수 정보입니다.", Toast.LENGTH_SHORT).show() // 필수 정보 누락 시 토스트 메시지
            showLoading(false) // 로딩 UI 숨김
            return // 함수 종료
        }

        val paramedic = auth.currentUser!! // 현재 로그인된 구급대원 사용자 정보 (반드시 존재한다고 가정)
        // 환자 정보를 HashMap 형태로 구성
        val patientInfo = hashMapOf(
            "name" to patientName,
            "age" to patientAge.toIntOrNull(), // 나이를 Int 또는 null로 변환
            "gender" to patientGender,
            "symptom" to symptom,
            "otherInfo" to otherInfo
        )
        val location = GeoPoint(userLocation.latitude, userLocation.longitude) // 사용자 현재 위치를 GeoPoint로 변환

        // Firestore에 저장할 응급 호출 데이터 HashMap 생성
        val emergencyCallData = hashMapOf(
            "paramedicId" to paramedic.uid, // 구급대원 ID
            "patientInfo" to patientInfo, // 환자 정보 맵
            "location" to location, // 사용자 위치
            "status" to "pending", // 호출 상태 (대기 중)
            "acceptedHospitalId" to null, // 수락한 병원 ID (초기에는 null)
            "createdAt" to FieldValue.serverTimestamp(), // 생성 시간 (Firestore 서버 타임스탬프)
            "completedAt" to null, // 완료 시간 (초기에는 null)
            "targetedHospitalIds" to targetHospitalIds, // 대상 병원 ID 목록
            "recommendedDepartments" to recommendedDepts // 추천 진료과 목록
        )

        db.collection("emergency_calls").add(emergencyCallData) // "emergency_calls" 컬렉션에 새 문서 추가
            .addOnSuccessListener { documentReference -> // 문서 추가 성공 시
                showLoading(false) // 로딩 UI 숨김
                Toast.makeText(this, "${targetHospitalIds.size}개 병원에 응급 요청을 전송했습니다.", Toast.LENGTH_LONG).show() // 성공 토스트
                val intent = Intent(this, CallStatusActivity::class.java) // CallStatusActivity로 이동할 Intent 생성
                intent.putExtra("CALL_ID", documentReference.id) // 생성된 호출 문서 ID를 Intent에 추가
                startActivity(intent) // 액티비티 시작
                finish() // 현재 액티비티 종료
            }
            .addOnFailureListener { // 문서 추가 실패 시
                showLoading(false) // 로딩 UI 숨김
                Toast.makeText(this, "요청 생성에 실패했습니다.", Toast.LENGTH_SHORT).show() // 실패 토스트
            }
    }

    // 두 지점(Location 객체와 GeoPoint 객체) 사이의 거리를 미터(m) 단위로 계산하는 유틸리티 함수
    private fun calculateDistance(start: Location, end: GeoPoint): Float {
        val results = FloatArray(1) // 거리 결과를 저장할 배열
        // Location.distanceBetween 함수를 사용하여 위도, 경도 기반 거리 계산 (미터 단위)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0] // 계산된 거리 반환
    }

    // 로딩 오버레이의 가시성을 제어하고 메시지를 설정하는 함수
    private fun showLoading(isLoading: Boolean, message: String = "요청 처리 중...") {
        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE // 로딩 오버레이 가시성 설정
        binding.progressText.text = message // 로딩 메시지 설정
    }
}