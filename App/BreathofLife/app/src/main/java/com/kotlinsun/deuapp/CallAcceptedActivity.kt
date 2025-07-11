package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.Manifest // Android 권한 관련 클래스
import android.annotation.SuppressLint // Lint 경고 억제를 위한 어노테이션
import android.content.ActivityNotFoundException // 특정 액티비티를 찾을 수 없을 때 발생하는 예외
import android.content.Intent // 액티비티 전환 및 데이터 전달을 위한 Intent 클래스
import android.content.pm.PackageManager // 패키지 관리 및 권한 확인을 위한 클래스
import android.location.Location // GPS 등에서 얻은 위치 정보를 담는 클래스
import android.media.AudioAttributes // SoundPool에서 오디오 재생 속성 설정을 위한 클래스
import android.media.SoundPool // 짧은 오디오 클립을 재생하기 위한 클래스
import android.net.Uri // URI(Uniform Resource Identifier)를 다루기 위한 클래스
import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.util.Log // 로그 출력을 위한 유틸리티 클래스
import android.widget.Toast // 사용자에게 짧은 메시지를 표시하는 Toast 클래스
import androidx.activity.result.contract.ActivityResultContracts // 액티비티 결과 계약 클래스
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import androidx.core.content.ContextCompat // 리소스 및 권한 관련 유틸리티 클래스
import com.google.android.gms.location.LocationServices // Google Location Service 클라이언트
import com.google.android.gms.location.Priority // 위치 요청 우선순위 클래스
import com.google.firebase.firestore.FirebaseFirestore // Firebase Firestore 데이터베이스 클라이언트
import com.google.firebase.firestore.GeoPoint // Firestore 지리적 위치 데이터 타입
import com.kotlinsun.deuapp.databinding.ActivityCallAcceptedBinding // 뷰 바인딩 클래스
import java.net.URLEncoder // [추가] URL 인코딩을 위한 클래스
import java.text.DecimalFormat // 숫자를 특정 형식으로 포맷하기 위한 클래스

// CallAcceptedActivity 클래스 선언: AppCompatActivity를 상속
class CallAcceptedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallAcceptedBinding // 뷰 바인딩 인스턴스
    private val db = FirebaseFirestore.getInstance() // Firestore 데이터베이스 인스턴스
    private var callId: String? = null // 호출 ID를 저장할 변수
    private var hospitalId: String? = null // 수락한 병원 ID를 저장할 변수

    private var hospitalLocationPoint: GeoPoint? = null // 병원의 GeoPoint 위치 정보
    private var hospitalName: String? = null // 병원 이름

    private lateinit var soundPool: SoundPool // SoundPool 인스턴스
    private var soundId: Int = 0 // 로드된 사운드의 ID

    // 위치 권한 요청 결과를 처리하는 ActivityResultLauncher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions() // 여러 권한을 한 번에 요청
    ) { permissions ->
        // ACCESS_FINE_LOCATION 권한이 허용되었는지 확인
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            getCurrentLocationAndLoadInfo() // 권한이 있으면 현재 위치 가져오기 및 정보 로드
        } else {
            Toast.makeText(this, "위치 권한이 없어 거리를 계산할 수 없습니다.", Toast.LENGTH_LONG).show() // 권한 없으면 토스트 메시지
            loadAcceptedInfo(null) // 위치 정보 없이 수락된 정보 로드
        }
    }

    // 액티비티 생성 시 호출되는 콜백 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallAcceptedBinding.inflate(layoutInflater) // 뷰 바인딩 초기화
        setContentView(binding.root) // 액티비티 레이아웃 설정

        // SoundPool 초기화: 오디오 속성 설정
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION) // 알림 용도로 설정
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) // 소리화(sonification) 타입으로 설정
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1) // 최대 동시 재생 스트림 수
            .setAudioAttributes(audioAttributes) // 오디오 속성 적용
            .build()

        // 사운드 로드 (R.raw.applepay는 리소스 폴더에 있는 오디오 파일)
        soundId = soundPool.load(this, R.raw.applepay, 1) // 로드된 사운드의 ID 저장

        // Intent로부터 CALL_ID와 HOSPITAL_ID 가져오기
        callId = intent.getStringExtra("CALL_ID")
        hospitalId = intent.getStringExtra("HOSPITAL_ID")

        // callId 또는 hospitalId가 null이면 오류 메시지 표시 후 액티비티 종료
        if (callId == null || hospitalId == null) {
            binding.textViewHospitalInfo.text = "정보를 불러올 수 없습니다."
            finish()
            return
        }

        checkLocationPermissionAndLoadInfo() // 위치 권한 확인 및 정보 로드 시작

        // [수정] 길안내 버튼 클릭 리스너 설정
        binding.buttonDirections.setOnClickListener {
            soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f) // 버튼 클릭 시 사운드 재생
            // 병원 위치 정보와 이름이 모두 존재하면 네이버 지도 내비게이션 시작
            if (hospitalLocationPoint != null && hospitalName != null) {
                startNaverMapNavigation(
                    destinationLat = hospitalLocationPoint!!.latitude, // 목적지 위도
                    destinationLng = hospitalLocationPoint!!.longitude, // 목적지 경도
                    destinationName = hospitalName!! // 목적지 이름
                )
            } else {
                Toast.makeText(this, "병원 위치 정보를 불러오는 중입니다.", Toast.LENGTH_SHORT).show() // 정보 없으면 토스트 메시지
            }
        }
    }

    /**
     * [신규] 네이버 지도로 길 안내를 시작하는 함수
     * @param destinationLat 목적지 위도
     * @param destinationLng 목적지 경도
     * @param destinationName 목적지 이름
     */
    private fun startNaverMapNavigation(destinationLat: Double, destinationLng: Double, destinationName: String) {
        try {
            // 목적지 이름을 URL에 안전하게 인코딩
            val encodedName = URLEncoder.encode(destinationName, "UTF-8")

            // 네이버 지도 앱의 내비게이션 스킴 URL 생성
            // 'nmap://navigation' 스킴을 사용하여 특정 위/경도와 이름으로 길 안내 시작
            val url = "nmap://navigation?dlat=$destinationLat&dlng=$destinationLng&dname=$encodedName"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE) // 웹 브라우저가 처리할 수 있는 카테고리 추가
            }

            // 네이버 지도 앱 실행 시도
            startActivity(intent)

        } catch (e: ActivityNotFoundException) {
            // 네이버 지도 앱이 기기에 설치되어 있지 않은 경우
            Toast.makeText(this, "네이버 지도 앱을 설치해주세요.", Toast.LENGTH_SHORT).show() // 사용자에게 앱 설치 요청
            // Google Play 스토어로 이동하여 네이버 지도 앱 페이지 열기
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.nhn.android.nmap"))
            startActivity(marketIntent)
        } catch (e: Exception) {
            // 기타 예외 처리 (예: 인코딩 실패 등)
            Toast.makeText(this, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show() // 오류 메시지 표시
            e.printStackTrace() // 스택 트레이스 출력 (디버깅 용도)
        }
    }


    // --- 아래는 기존과 동일한 코드 ---

    // 위치 권한을 확인하고, 권한이 있으면 현재 위치를 가져와 정보 로드를 시작하는 함수
    private fun checkLocationPermissionAndLoadInfo() {
        // ACCESS_FINE_LOCATION 권한이 부여되었는지 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndLoadInfo() // 권한이 있으면 현재 위치 가져오기 시작
        } else {
            // 권한이 없으면 사용자에게 ACCESS_FINE_LOCATION 및 ACCESS_COARSE_LOCATION 권한 요청
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // 현재 위치를 가져와 수락된 정보를 로드하는 함수 (권한이 이미 있다고 가정)
    @SuppressLint("MissingPermission") // Lint 경고: 권한 검사 없이 위치 API를 사용하고 있음을 무시
    private fun getCurrentLocationAndLoadInfo() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) // FusedLocationProviderClient 인스턴스 가져오기
        // 현재 위치를 높은 정확도로 한 번만 가져오도록 요청
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? -> // 위치 정보 가져오기 성공 시
                loadAcceptedInfo(location) // 가져온 위치 정보와 함께 수락된 정보 로드
            }
            .addOnFailureListener { // 위치 정보 가져오기 실패 시
                Log.e("Location", "Failed to get current location.", it) // 오류 로그 출력
                Toast.makeText(this, "현재 위치를 가져오는데 실패했습니다.", Toast.LENGTH_SHORT).show() // 사용자에게 토스트 메시지 표시
                loadAcceptedInfo(null) // 위치 정보 없이 수락된 정보 로드 (거리 계산 불가능)
            }
    }

    // 수락된 병원 및 환자 정보를 Firestore에서 로드하고 UI를 업데이트하는 함수
    private fun loadAcceptedInfo(currentUserLocation: Location?) {
        // 병원 ID를 사용하여 Firestore에서 병원 문서 가져오기
        db.collection("hospitals").document(hospitalId!!).get()
            .addOnSuccessListener { hospitalDoc -> // 문서 가져오기 성공 시
                if (hospitalDoc != null && hospitalDoc.exists()) { // 문서가 존재하고 유효한 경우
                    this.hospitalName = hospitalDoc.getString("hospitalName") ?: "알 수 없는 병원" // 병원 이름 가져오기
                    this.hospitalLocationPoint = hospitalDoc.getGeoPoint("location") // 병원 위치(GeoPoint) 가져오기
                    binding.textViewHospitalInfo.text = this.hospitalName // 병원 이름을 UI에 표시

                    val bedsInfo = hospitalDoc.get("beds") as? Map<*, *> // 침대 정보 (맵 형태로) 가져오기
                    // 수용 가능 인원 및 전체 인원 문자열 생성
                    val bedString = "수용 가능 인원: ${bedsInfo?.get("available") ?: -1} / ${bedsInfo?.get("total") ?: -1}"

                    var distanceString = "거리 정보 없음" // 거리 정보 초기화
                    // 현재 사용자 위치와 병원 위치가 모두 존재하면 거리 계산
                    if (currentUserLocation != null && hospitalLocationPoint != null) {
                        val distanceInMeters = calculateDistance(currentUserLocation, hospitalLocationPoint!!) // 거리 계산 (미터 단위)
                        distanceString = formatDistance(distanceInMeters) // 거리를 보기 좋은 형식으로 포맷
                    }

                    binding.textViewMapinfo.text = "$bedString\n$distanceString" // 침대 정보와 거리 정보를 UI에 표시
                }
            }

        // 호출 ID를 사용하여 Firestore에서 응급 호출 문서 가져오기
        db.collection("emergency_calls").document(callId!!).get()
            .addOnSuccessListener { callDoc -> // 문서 가져오기 성공 시
                if (callDoc != null && callDoc.exists()) { // 문서가 존재하고 유효한 경우
                    val patientInfo = callDoc.get("patientInfo") as? Map<*, *> // 환자 정보 (맵 형태로) 가져오기
                    // 환자 이름 및 주요 증상을 UI에 표시
                    binding.textViewPatientName.text = "환자 이름: ${patientInfo?.get("name") ?: "정보 없음"}"
                    binding.textViewPatientSymptom.text = "주요 증상: ${patientInfo?.get("symptom") ?: "정보 없음"}"
                }
            }
    }

    // 현재 위치(Location)와 목적지(GeoPoint) 사이의 거리를 계산하는 함수
    private fun calculateDistance(start: Location, end: GeoPoint): Float {
        // GeoPoint를 Location 객체로 변환하여 distanceTo 메서드 사용
        val endLocation = Location("end").apply {
            latitude = end.latitude
            longitude = end.longitude
        }
        return start.distanceTo(endLocation) // 두 위치 사이의 거리 (미터 단위) 반환
    }

    // 미터 단위의 거리를 보기 좋은 문자열 형식으로 포맷하는 함수 (예: m 또는 km)
    private fun formatDistance(distanceInMeters: Float): String {
        return if (distanceInMeters < 1000) {
            "약 ${distanceInMeters.toInt()}m" // 1000m 미만이면 미터 단위로 표시
        } else {
            val distanceInKm = distanceInMeters / 1000f // 킬로미터로 변환
            val df = DecimalFormat("#.#") // 소수점 한 자리까지 표시하는 포맷터
            "약 ${df.format(distanceInKm)}km" // 킬로미터 단위로 표시
        }
    }

    // 액티비티가 소멸될 때 호출되는 콜백 메서드
    override fun onDestroy() {
        super.onDestroy()
        soundPool.release() // SoundPool 리소스 해제
    }
}