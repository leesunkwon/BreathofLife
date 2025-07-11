package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.content.Intent // 액티비티 전환 및 데이터 전달을 위한 Intent 클래스
import android.media.AudioAttributes // SoundPool에서 오디오 재생 속성 설정을 위한 클래스
import android.media.SoundPool // 짧은 오디오 클립을 재생하기 위한 클래스
import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.util.Log // 로그 출력을 위한 유틸리티 클래스
import android.view.View // 안드로이드 UI 구성 요소의 기본 클래스
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import androidx.lifecycle.lifecycleScope // 코루틴을 액티비티/프래그먼트의 라이프사이클에 바인딩하는 스코프
import com.google.firebase.auth.FirebaseAuth // Firebase Authentication 클라이언트
import com.google.firebase.auth.ktx.auth // Firebase Authentication KTX 확장 (FirebaseAuth 인스턴스 가져오기)
import com.google.firebase.firestore.ListenerRegistration // Firestore 실시간 리스너 등록 객체
import com.google.firebase.firestore.ktx.firestore // Firebase Firestore KTX 확장 (Firestore 인스턴스 가져오기)
import com.google.firebase.ktx.Firebase // Firebase KTX 확장 (Firebase 초기화 및 서비스 접근)
import com.kotlinsun.deuapp.databinding.ActivityMainRealBinding // 뷰 바인딩을 통해 레이아웃의 뷰에 접근하는 생성된 클래스
import kotlinx.coroutines.delay // 코루틴 내에서 지연을 위한 함수
import kotlinx.coroutines.launch // 코루틴 시작을 위한 함수
import java.text.SimpleDateFormat // 날짜/시간 포맷을 위한 클래스
import java.util.* // 유틸리티 클래스 (Date, Locale 등)

// MainRealActivity 클래스 선언: AppCompatActivity를 상속
class MainRealActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainRealBinding // UI 요소에 접근하기 위한 뷰 바인딩 인스턴스
    private val db = Firebase.firestore // Firebase Firestore 데이터베이스 인스턴스
    private lateinit var auth: FirebaseAuth // Firebase Authentication 인스턴스
    private var pendingCallListener: ListenerRegistration? = null // 보류 중인 호출을 감지하는 Firestore 리스너
    private lateinit var soundPool: SoundPool // 알림 사운드 재생을 위한 SoundPool 인스턴스
    private var soundId: Int = 0 // SoundPool에 로드된 사운드의 ID

    // 액티비티가 처음 생성될 때 호출되는 콜백 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainRealBinding.inflate(layoutInflater) // 뷰 바인딩 인스턴스 초기화
        setContentView(binding.root) // 액티비티의 레이아웃 설정

        auth = Firebase.auth // Firebase Auth 인스턴스 가져오기

        startTimeUpdates() // 시간 업데이트 시작
        loadUserName() // 사용자 이름 로드

        // SoundPool 초기화는 onCreate에서 한 번만 수행하여 리소스를 효율적으로 관리합니다.
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION) // 오디오 사용 용도를 알림으로 설정
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) // 콘텐츠 타입을 소리화(sonification)로 설정
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1) // 동시에 재생할 수 있는 최대 스트림 수 (1개로 제한)
            .setAudioAttributes(audioAttributes) // 위에서 설정한 오디오 속성 적용
            .build()

        // 설정 버튼 클릭 리스너: SettingsActivity로 이동
        binding.buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 환자 추가 (FAB) 버튼 클릭 리스너: RequestActivity로 이동
        binding.fabAddPatient.setOnClickListener {
            val intent = Intent(this, RequestActivity::class.java)
            startActivity(intent)
        }

        // 기록 버튼 클릭 리스너: HistoryActivity로 이동
        binding.HistoryBtn.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // 병원 찾기 버튼 클릭 리스너: FindActivity로 이동
        binding.findBtn.setOnClickListener {
            val intent = Intent(this, FindActivity::class.java)
            startActivity(intent)
        }

        // AI 버튼 클릭 리스너: AiActivity로 이동
        binding.AiBtn.setOnClickListener {
            val intent = Intent(this, AiActivity::class.java)
            startActivity(intent)
        }

        // 알림 버튼 클릭 리스너: NotiActivity로 이동
        binding.notiBtn.setOnClickListener {
            val intent = Intent(this, NotiActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * 화면이 사용자에게 다시 보일 때마다 호출됩니다.
     * (예: 다른 액티비티에서 돌아왔을 때, 액티비티가 처음 시작될 때)
     * 이 메서드에서 실시간 데이터를 새로고침하거나, 리소스를 다시 로드하는 것이 일반적입니다.
     */
    override fun onStart() {
        super.onStart() // 상위 클래스의 onStart 호출

        // 1. 기존에 로드된 사운드가 있다면 메모리에서 해제하여 불필요한 리소스 낭비를 방지합니다.
        if (soundId != 0) {
            soundPool.unload(soundId)
        }

        // 2. SharedPreferences에서 최신 설정값(알림 사운드)을 다시 불러와 SoundPool에 로드합니다.
        loadSelectedSound()

        // 3. Firestore에서 보류 중이거나 수락된 호출이 있는지 다시 확인하고 리스너를 설정합니다.
        checkForPendingCalls()
    }

    /**
     * 액티비티가 더 이상 사용자에게 보이지 않게 될 때 호출됩니다.
     * (예: 다른 액티비티가 전면으로 나오거나, 홈 버튼을 눌렀을 때)
     * 이 메서드에서 불필요한 리스너를 제거하여 메모리 누수를 방지합니다.
     */
    override fun onStop() {
        super.onStop() // 상위 클래스의 onStop 호출
        pendingCallListener?.remove() // Firestore 리스너가 있다면 제거하여 실시간 업데이트를 중단합니다.
    }

    /**
     * SharedPreferences에 저장된 알림 사운드 설정을 불러와 SoundPool에 로드하는 함수입니다.
     */
    private fun loadSelectedSound() {
        // SoundPrefManager를 사용하여 저장된 사운드 리소스 ID를 가져옵니다.
        val selectedSound = SoundPrefManager.getSoundPreference(this)
        // SoundPool에 해당 사운드를 로드하고, 로드된 사운드의 ID를 soundId 변수에 저장합니다.
        soundId = soundPool.load(this, selectedSound, 1) // 우선순위는 1로 설정
        Log.d("MainRealActivity", "Sound loaded: $selectedSound") // 로드된 사운드 리소스 ID를 로그로 출력
    }

    // 화면 상단의 시간을 매 초 업데이트하는 함수
    private fun startTimeUpdates() {
        lifecycleScope.launch { // 코루틴을 사용하여 백그라운드에서 실행
            while (true) { // 무한 루프
                val sdf = SimpleDateFormat("a hh:mm", Locale.KOREA) // "오전/오후 시:분" 형식의 날짜 포맷터
                val currentTime = sdf.format(Date()) // 현재 시간을 포맷
                //binding.timeTv.text = currentTime // 주석 처리됨: 시간 텍스트 뷰 업데이트 (현재 사용 안 함)
                delay(1000) // 1초(1000밀리초) 대기
            }
        }
    }

    // Firebase Firestore에서 현재 로그인한 사용자의 이름을 로드하여 UI에 표시하는 함수
    private fun loadUserName() {
        val user = auth.currentUser // 현재 로그인된 사용자 객체 가져오기
        if (user != null) { // 사용자가 로그인되어 있다면
            val uid = user.uid // 사용자의 UID 가져오기
            db.collection("users").document(uid) // "users" 컬렉션에서 해당 UID 문서 참조
                .get() // 문서 데이터 가져오기
                .addOnSuccessListener { document -> // 문서 가져오기 성공 시
                    if (document != null && document.exists()) { // 문서가 존재하고 유효한 경우
                        val name = document.getString("name") // 문서에서 "name" 필드 값 가져오기
                        binding.nameTv.text = "${name}님" // UI 텍스트 뷰에 사용자 이름 표시
                        Log.d("Firestore", "User name loaded: $name") // 로그 출력
                    } else {
                        binding.nameTv.text = "사용자 정보 없음" // 문서가 없으면 "사용자 정보 없음" 표시
                    }
                }
                .addOnFailureListener { // 문서 가져오기 실패 시
                    binding.nameTv.text = "정보 로딩 실패" // "정보 로딩 실패" 표시
                }
        } else {
            binding.nameTv.text = "로그인이 필요합니다." // 로그인되어 있지 않으면 "로그인이 필요합니다." 표시
        }
    }

    // 보류 중이거나 수락된 응급 호출이 있는지 Firebase Firestore에서 실시간으로 확인하는 함수
    private fun checkForPendingCalls() {
        val user = auth.currentUser ?: return // 현재 로그인된 사용자 가져오기, 없으면 함수 종료

        // 'emergency_calls' 컬렉션에서 현재 사용자의 호출 중 'pending' 또는 'accepted' 상태인 문서를 쿼리
        val query = db.collection("emergency_calls")
            .whereEqualTo("paramedicId", user.uid) // 현재 구급대원의 호출만 필터링
            .whereIn("status", listOf("pending", "accepted")) // 상태가 'pending' 또는 'accepted'인 경우

        pendingCallListener?.remove() // 기존 리스너가 있다면 제거 (중복 방지)

        // 쿼리에 실시간 스냅샷 리스너 추가
        pendingCallListener = query.addSnapshotListener { snapshots, e ->
            // 오류가 발생하거나 스냅샷이 null이면 로그 출력 후 종료
            if (e != null) {
                Log.w("PendingCheck", "Listen failed.", e)
                return@addSnapshotListener
            }

            var acceptedCallId: String? = null // 수락된 호출 ID
            var acceptedHospitalId: String? = null // 수락한 병원 ID
            var pendingCallId: String? = null // 보류 중인 호출 ID

            // 스냅샷이 null이 아니고 비어있지 않다면
            if (snapshots != null && !snapshots.isEmpty) {
                for (doc in snapshots.documents) { // 각 문서 순회
                    // 상태가 'accepted'인 호출을 찾으면 해당 정보 저장 후 반복 중단
                    if (doc.getString("status") == "accepted") {
                        acceptedCallId = doc.id
                        acceptedHospitalId = doc.getString("acceptedHospitalId")
                        break // 수락된 호출이 더 중요하므로 찾으면 바로 종료
                    }
                    // 상태가 'pending'인 호출을 찾으면 해당 정보 저장
                    if (doc.getString("status") == "pending") {
                        pendingCallId = doc.id
                    }
                }
            }

            // 수락된 호출이 있다면 UI 업데이트 및 CallAcceptedActivity로 이동 링크 설정
            if (acceptedCallId != null && acceptedHospitalId != null) {
                binding.statTv.visibility = View.VISIBLE // 상태 텍스트 뷰 표시
                binding.StatImg.setImageResource(R.drawable.checkicon) // 상태 이미지 (체크 아이콘) 설정
                soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f) // 알림 사운드 재생
                binding.statTv.setOnClickListener { // 상태 텍스트 뷰 클릭 리스너
                    val intent = Intent(this, CallAcceptedActivity::class.java).apply {
                        putExtra("CALL_ID", acceptedCallId) // 호출 ID 전달
                        putExtra("HOSPITAL_ID", acceptedHospitalId) // 병원 ID 전달
                    }
                    startActivity(intent) // CallAcceptedActivity 시작
                }
            }
            // 보류 중인 호출만 있다면 UI 업데이트 및 CallStatusActivity로 이동 링크 설정
            else if (pendingCallId != null) {
                binding.statTv.visibility = View.VISIBLE // 상태 텍스트 뷰 표시
                binding.StatImg.setImageResource(R.drawable.staticon) // 상태 이미지 (대기 아이콘) 설정
                binding.statTv.setOnClickListener { // 상태 텍스트 뷰 클릭 리스너
                    val intent = Intent(this, CallStatusActivity::class.java)
                    intent.putExtra("CALL_ID", pendingCallId) // 호출 ID 전달
                    startActivity(intent) // CallStatusActivity 시작
                }
            }
            // 보류 중이거나 수락된 호출이 없다면 UI 업데이트 (기본 아이콘)
            else {
                binding.StatImg.setImageResource(R.drawable.sleepicon) // 상태 이미지 (수면 아이콘) 설정
            }
        }
    }

    /**
     * 액티비티가 완전히 소멸될 때 호출됩니다.
     * 이 메서드에서 SoundPool 리소스를 해제합니다.
     */
    override fun onDestroy() {
        super.onDestroy() // 상위 클래스의 onDestroy 호출
        soundPool.release() // SoundPool의 모든 리소스 해제
    }
}