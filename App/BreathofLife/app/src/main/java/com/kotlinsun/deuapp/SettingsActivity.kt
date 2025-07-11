package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.content.Intent // 액티비티 전환 및 데이터 전달을 위한 Intent 클래스
import android.graphics.Color // 색상 정의를 위한 클래스
import android.graphics.drawable.ColorDrawable // 색상으로 된 Drawable을 생성하는 클래스
import android.media.AudioAttributes // SoundPool에서 오디오 재생 속성 설정을 위한 클래스
import android.media.SoundPool // 짧은 오디오 클립을 재생하기 위한 클래스
import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.util.Log // 로그 출력을 위한 유틸리티 클래스
import android.widget.Button // 버튼 UI 요소
import android.widget.EditText // 텍스트 입력 필드 UI 요소
import android.widget.Toast // 사용자에게 짧은 메시지를 표시하는 Toast 클래스
import androidx.appcompat.app.AlertDialog // AlertDialog (팝업 다이얼로그) 클래스
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import com.google.firebase.auth.EmailAuthProvider // 이메일/비밀번호 기반 자격 증명을 생성하는 클래스
import com.google.firebase.auth.FirebaseAuth // Firebase Authentication 클라이언트
import com.google.firebase.firestore.ktx.firestore // Firebase Firestore KTX 확장 (Firestore 인스턴스 가져오기)
import com.google.firebase.ktx.Firebase // Firebase KTX 확장 (Firebase 초기화 및 서비스 접근)
import com.kotlinsun.deuapp.databinding.ActivitySettingsBinding // 뷰 바인딩을 통해 레이아웃의 뷰에 접근하는 생성된 클래스

// SettingsActivity 클래스 선언: AppCompatActivity를 상속
class SettingsActivity : AppCompatActivity() {

    // 뷰 바인딩 객체 (_binding은 nullable로 선언하고, binding은 non-null getter로 사용)
    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!! // _binding이 null이 아님을 보장하는 getter

    private lateinit var auth: FirebaseAuth // Firebase Authentication 인스턴스
    private val db = Firebase.firestore // Firebase Firestore 데이터베이스 인스턴스

    // SoundPool 관련 변수
    private lateinit var soundPool: SoundPool // 알림 사운드 재생을 위한 SoundPool 인스턴스
    // 각 사운드 리소스(R.raw.xxx)와 SoundPool에 로드된 사운드 ID를 매핑할 mutable 맵
    private val soundMap = mutableMapOf<Int, Int>()

    // 액티비티가 처음 생성될 때 호출되는 콜백 메소드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivitySettingsBinding.inflate(layoutInflater) // 뷰 바인딩 초기화
        setContentView(binding.root) // 액티비티 레이아웃 설정

        auth = FirebaseAuth.getInstance() // Firebase Auth 인스턴스 가져오기

        // SoundPool 초기화 및 미리보기 사운드 로드 함수 호출
        initializeSoundPool()
        loadPreviewSounds()

        loadUserData() // 사용자 정보 로드
        setupSoundSelection() // 사운드 선택 라디오 버튼 리스너 설정
        loadAndApplySoundPreference() // 저장된 사운드 설정 불러와 적용

        // 뒤로가기 버튼 클릭 리스너: 시스템 뒤로가기 동작 수행
        binding.backBtnSet.setOnClickListener {
            onBackPressedDispatcher.onBackPressed() // AndroidX의 뒤로가기 처리
        }

        // 소속 변경 버튼 클릭 리스너
        binding.orgChange.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_change_organization, null) // 다이얼로그 레이아웃 인플레이트
            val builder = AlertDialog.Builder(this).setView(dialogView) // AlertDialog.Builder 생성
            val dialog = builder.create() // 다이얼로그 생성
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // 다이얼로그 배경을 투명하게 설정 (둥근 모서리 등 커스텀 배경을 위해)

            // 다이얼로그 내 UI 요소 참조
            val etNewOrganization = dialogView.findViewById<EditText>(R.id.et_new_organization)
            val btnCancel = dialogView.findViewById<Button>(R.id.btn_dialog_cancel)
            val btnConfirm = dialogView.findViewById<Button>(R.id.btn_dialog_confirm)

            btnCancel.setOnClickListener { dialog.dismiss() } // 취소 버튼 클릭 시 다이얼로그 닫기

            btnConfirm.setOnClickListener { // 확인 버튼 클릭 시
                val newOrgName = etNewOrganization.text.toString().trim() // 새 소속 이름 가져오기
                if (newOrgName.isNotEmpty()) { // 소속 이름이 비어있지 않으면
                    updateOrganization(newOrgName) // 소속 업데이트 함수 호출
                    dialog.dismiss() // 다이얼로그 닫기
                } else {
                    Toast.makeText(this, "소속을 입력해주세요.", Toast.LENGTH_SHORT).show() // 입력 요청 토스트
                }
            }
            dialog.show() // 다이얼로그 표시
        }

        // 비밀번호 변경 버튼 클릭 리스너
        binding.pwChange.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null) // 다이얼로그 레이아웃 인플레이트
            val builder = AlertDialog.Builder(this).setView(dialogView) // AlertDialog.Builder 생성
            val dialog = builder.create() // 다이얼로그 생성
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // 다이얼로그 배경을 투명하게 설정

            // 다이얼로그 내 UI 요소 참조
            val etCurrentPassword = dialogView.findViewById<EditText>(R.id.et_current_password)
            val etNewPassword = dialogView.findViewById<EditText>(R.id.et_new_password)
            val etConfirmPassword = dialogView.findViewById<EditText>(R.id.et_confirm_password)
            val btnCancel = dialogView.findViewById<Button>(R.id.btn_dialog_cancel)
            val btnConfirm = dialogView.findViewById<Button>(R.id.btn_dialog_confirm)

            btnCancel.setOnClickListener { dialog.dismiss() } // 취소 버튼 클릭 시 다이얼로그 닫기

            btnConfirm.setOnClickListener { // 확인 버튼 클릭 시
                val currentPw = etCurrentPassword.text.toString() // 현재 비밀번호
                val newPw = etNewPassword.text.toString() // 새 비밀번호
                val confirmPw = etConfirmPassword.text.toString() // 새 비밀번호 확인

                // 모든 필드가 입력되었는지 확인
                if (currentPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
                    Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // 새 비밀번호가 6자 미만인지 확인
                if (newPw.length < 6) {
                    Toast.makeText(this, "새 비밀번호는 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // 새 비밀번호와 확인 비밀번호가 일치하는지 확인
                if (newPw != confirmPw) {
                    Toast.makeText(this, "새 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 비밀번호 업데이트 함수 호출 및 완료 콜백 처리
                updatePassword(currentPw, newPw) { success ->
                    if (success) {
                        dialog.dismiss() // 성공 시 다이얼로그 닫기
                    }
                }
            }
            dialog.show() // 다이얼로그 표시
        }

        // 로그아웃 버튼 클릭 리스너
        binding.logoutBtn.setOnClickListener {
            auth.signOut() // Firebase에서 로그아웃
            val intent = Intent(this, MainActivity::class.java) // MainActivity로 이동하는 Intent 생성
            // 모든 이전 액티비티를 지우고 새로운 태스크로 시작하도록 플래그 설정
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent) // MainActivity 시작
            Toast.makeText(this, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show() // 로그아웃 메시지 표시
        }

        // 회원 탈퇴 버튼 클릭 리스너
        binding.infoDel.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account, null) // 다이얼로그 레이아웃 인플레이트
            val builder = AlertDialog.Builder(this).setView(dialogView) // AlertDialog.Builder 생성
            val dialog = builder.create() // 다이얼로그 생성
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // 다이얼로그 배경을 투명하게 설정

            // 다이얼로그 내 UI 요소 참조
            val etPassword = dialogView.findViewById<EditText>(R.id.et_password_for_delete)
            val btnCancel = dialogView.findViewById<Button>(R.id.btn_dialog_cancel)
            val btnConfirm = dialogView.findViewById<Button>(R.id.btn_dialog_confirm)

            btnCancel.setOnClickListener { dialog.dismiss() } // 취소 버튼 클릭 시 다이얼로그 닫기

            btnConfirm.setOnClickListener { // 확인 버튼 클릭 시
                val password = etPassword.text.toString() // 입력된 비밀번호 가져오기
                if (password.isEmpty()) { // 비밀번호가 비어있으면
                    Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show() // 입력 요청 토스트
                    return@setOnClickListener
                }

                // 계정 삭제 함수 호출 및 완료 콜백 처리
                deleteAccount(password) { success ->
                    if (success) {
                        dialog.dismiss() // 성공 시 다이얼로그 닫기
                    }
                }
            }
            dialog.show() // 다이얼로그 표시
        }
    }

    // SoundPool을 초기화하는 함수
    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION) // 오디오 사용 용도를 알림으로 설정
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) // 콘텐츠 타입을 소리화(sonification)로 설정
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4) // 동시에 재생할 수 있는 최대 스트림 수
            .setAudioAttributes(audioAttributes) // 위에서 설정한 오디오 속성 적용
            .build()
    }

    // 미리보기 사운드를 SoundPool에 로드하고 soundMap에 매핑하는 함수
    private fun loadPreviewSounds() {
        soundMap[R.raw.notisound] = soundPool.load(this, R.raw.notisound, 1) // 기본 알림 사운드
        soundMap[R.raw.alert] = soundPool.load(this, R.raw.alert, 1) // alert 사운드
        soundMap[R.raw.iphoneglass] = soundPool.load(this, R.raw.iphoneglass, 1) // iphoneglass 사운드
        soundMap[R.raw.iphonepopcorn] = soundPool.load(this, R.raw.iphonepopcorn, 1) // iphonepopcorn 사운드
    }

    // 사운드 선택 라디오 그룹의 리스너를 설정하는 함수
    private fun setupSoundSelection() {
        binding.radioGroupSounds.setOnCheckedChangeListener { _, checkedId ->
            // 선택된 라디오 버튼 ID에 따라 해당 사운드 리소스 ID 결정
            val selectedSoundId = when (checkedId) {
                R.id.radio_alert -> R.raw.alert
                R.id.radio_glass -> R.raw.iphoneglass
                R.id.radio_popcorn -> R.raw.iphonepopcorn
                else -> R.raw.notisound // 기본값
            }
            SoundPrefManager.saveSoundPreference(this, selectedSoundId) // 선택된 사운드 ID를 SharedPreferences에 저장

            // 로드된 사운드 ID 맵에서 해당 사운드를 찾아 재생
            soundMap[selectedSoundId]?.let { soundIdToPlay ->
                soundPool.play(soundIdToPlay, 1.0f, 1.0f, 0, 0, 1.0f) // 볼륨 1.0f, 반복 안 함(0), 재생 속도 1.0f
            }
        }
    }

    // 저장된 사운드 선호도를 불러와 라디오 버튼에 적용하고 리스너를 재설정하는 함수
    private fun loadAndApplySoundPreference() {
        val currentSoundId = SoundPrefManager.getSoundPreference(this) // SharedPreferences에서 현재 저장된 사운드 ID 가져오기
        // 현재 사운드 ID에 해당하는 라디오 버튼 ID 찾기
        val radioButtonId = when (currentSoundId) {
            R.raw.alert -> R.id.radio_alert
            R.raw.iphoneglass -> R.id.radio_glass
            R.raw.iphonepopcorn -> R.id.radio_popcorn
            else -> R.id.radio_notisound // 기본값
        }
        // 일시적으로 리스너를 null로 설정하여 체크 변경 시 콜백이 불필요하게 호출되는 것을 방지
        binding.radioGroupSounds.setOnCheckedChangeListener(null)
        binding.radioGroupSounds.check(radioButtonId) // 해당 라디오 버튼을 체크
        setupSoundSelection() // 다시 리스너 설정
    }

    // Firebase Firestore에서 현재 로그인된 사용자 정보를 로드하여 UI에 표시하는 함수
    private fun loadUserData() {
        val user = auth.currentUser // 현재 로그인된 사용자 객체 가져오기
        if (user != null) { // 사용자가 로그인되어 있다면
            val uid = user.uid // 사용자의 UID 가져오기
            db.collection("users").document(uid).get() // "users" 컬렉션에서 해당 UID 문서 참조 후 데이터 가져오기
                .addOnSuccessListener { documentSnapshot -> // 문서 가져오기 성공 시
                    if (documentSnapshot != null && documentSnapshot.exists()) { // 문서가 존재하고 유효한 경우
                        binding.rName.text = documentSnapshot.getString("name") ?: "이름 없음" // 이름 표시 (없으면 "이름 없음")
                        binding.rOrg.text = documentSnapshot.getString("organization") ?: "소속 없음" // 소속 표시 (없으면 "소속 없음")
                        Log.d("SettingsActivity", "사용자 정보 로딩 성공") // 성공 로그
                    } else {
                        Log.w("SettingsActivity", "해당 사용자의 문서가 존재하지 않습니다.") // 문서 없음 로그
                        binding.rName.text = "정보 없음" // "정보 없음" 표시
                        binding.rOrg.text = "정보 없음" // "정보 없음" 표시
                    }
                }
                .addOnFailureListener { e -> // 문서 가져오기 실패 시
                    Log.e("SettingsActivity", "사용자 정보 로딩 실패", e) // 실패 로그
                    Toast.makeText(this, "사용자 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show() // 토스트 메시지
                }
        } else {
            Log.w("SettingsActivity", "로그인된 사용자가 없어 정보를 로드할 수 없습니다.") // 로그인된 사용자 없음 로그
        }
    }

    // Firestore에 사용자 소속 정보를 업데이트하는 함수
    private fun updateOrganization(newOrgName: String) {
        val user = auth.currentUser ?: return // 현재 로그인된 사용자 가져오기, 없으면 함수 종료
        db.collection("users").document(user.uid) // "users" 컬렉션에서 해당 사용자 문서 참조
            .update("organization", newOrgName) // "organization" 필드를 새 소속 이름으로 업데이트
            .addOnSuccessListener {
                binding.rOrg.text = newOrgName // UI 텍스트 뷰 업데이트
                Toast.makeText(this, "소속이 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show() // 성공 토스트
                Log.d("SettingsActivity", "소속 업데이트 성공") // 성공 로그
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "소속 변경에 실패했습니다.", Toast.LENGTH_SHORT).show() // 실패 토스트
                Log.e("SettingsActivity", "소속 업데이트 실패", e) // 실패 로그
            }
    }

    // 사용자 비밀번호를 업데이트하는 함수
    private fun updatePassword(currentPw: String, newPw: String, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser // 현재 로그인된 사용자 객체
        if (user?.email == null) { // 사용자의 이메일이 없으면 (익명 로그인 등)
            onComplete(false) // 콜백으로 실패 알림
            return // 함수 종료
        }

        // 사용자의 이메일과 현재 비밀번호를 사용하여 자격 증명 생성
        val credential = EmailAuthProvider.getCredential(user.email!!, currentPw)
        // 사용자를 재인증하여 보안 검사 수행
        user.reauthenticate(credential).addOnSuccessListener {
            // 재인증 성공 시 새 비밀번호로 업데이트
            user.updatePassword(newPw).addOnSuccessListener {
                Toast.makeText(this, "비밀번호가 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show() // 성공 토스트
                onComplete(true) // 콜백으로 성공 알림
            }.addOnFailureListener { e ->
                Toast.makeText(this, "비밀번호 변경에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show() // 실패 토스트
                onComplete(false) // 콜백으로 실패 알림
            }
        }.addOnFailureListener {
            Toast.makeText(this, "현재 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show() // 현재 비밀번호 불일치 토스트
            onComplete(false) // 콜백으로 실패 알림
        }
    }

    // 사용자 계정을 삭제하는 함수
    private fun deleteAccount(password: String, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser // 현재 로그인된 사용자 객체
        if (user?.email == null) { // 사용자의 이메일이 없으면
            onComplete(false) // 콜백으로 실패 알림
            return // 함수 종료
        }

        // 사용자의 이메일과 입력된 비밀번호를 사용하여 자격 증명 생성
        val credential = EmailAuthProvider.getCredential(user.email!!, password)
        // 사용자를 재인증하여 보안 검사 수행
        user.reauthenticate(credential).addOnSuccessListener {
            val uid = user.uid // 사용자의 UID 가져오기
            // Firestore에서 사용자 데이터 삭제
            db.collection("users").document(uid).delete().addOnSuccessListener {
                // Firestore 데이터 삭제 성공 시, Firebase Auth에서 사용자 계정 삭제
                user.delete().addOnSuccessListener {
                    Toast.makeText(this, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show() // 성공 토스트
                    val intent = Intent(this, MainActivity::class.java) // MainActivity로 이동하는 Intent 생성
                    // 모든 이전 액티비티를 지우고 새로운 태스크로 시작하도록 플래그 설정
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent) // MainActivity 시작
                    onComplete(true) // 콜백으로 성공 알림
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "계정 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show() // 계정 삭제 실패 토스트
                    Log.e("SettingsActivity", "Auth 계정 삭제 실패", e) // Auth 계정 삭제 실패 로그
                    onComplete(false) // 콜백으로 실패 알림
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "데이터 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show() // 데이터 삭제 실패 토스트
                Log.e("SettingsActivity", "Firestore 데이터 삭제 실패", e) // Firestore 데이터 삭제 실패 로그
                onComplete(false) // 콜백으로 실패 알림
            }
        }.addOnFailureListener {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show() // 비밀번호 불일치 토스트
            onComplete(false) // 콜백으로 실패 알림
        }
    }

    // 액티비티가 소멸될 때 호출되는 콜백 메소드
    override fun onDestroy() {
        super.onDestroy() // 상위 클래스의 onDestroy 호출
        soundPool.release() // SoundPool 리소스 해제
        _binding = null // 뷰 바인딩 객체를 null로 설정하여 메모리 누수 방지
    }
}