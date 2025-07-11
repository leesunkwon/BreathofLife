package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.content.Intent // 액티비티 전환 및 데이터 전달을 위한 Intent 클래스
import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.widget.Toast // 사용자에게 짧은 메시지를 표시하는 Toast 클래스
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import com.kotlinsun.deuapp.databinding.ActivityMainBinding // 뷰 바인딩을 통해 레이아웃의 뷰에 접근하는 생성된 클래스
import com.google.firebase.auth.FirebaseAuth // Firebase Authentication 클라이언트
import com.google.firebase.auth.ktx.auth // Firebase Authentication KTX 확장 (FirebaseAuth 인스턴스 가져오기)
import com.google.firebase.ktx.Firebase // Firebase KTX 확장 (Firebase 초기화 및 서비스 접근)

// MainActivity 클래스 선언: AppCompatActivity를 상속
class MainActivity : AppCompatActivity() {

    // View Binding 및 Firebase Auth 인스턴스를 지연 초기화
    private lateinit var binding: ActivityMainBinding // UI 요소에 접근하기 위한 뷰 바인딩 인스턴스
    private lateinit var auth: FirebaseAuth // Firebase Authentication 인스턴스

    // [수정] 액티비티가 사용자에게 보이기 직전에 호출되는 콜백 메소드
    public override fun onStart() {
        super.onStart() // 상위 클래스의 onStart 호출
        // 사용자가 현재 로그인되어 있는지 확인합니다.
        val currentUser = auth.currentUser // 현재 Firebase에 로그인된 사용자 객체 가져오기
        if (currentUser != null) { // 사용자가 로그인되어 있다면
            // 사용자에게 자동 로그인 성공 메시지 표시
            Toast.makeText(this, "자동 로그인 되었습니다.", Toast.LENGTH_SHORT).show()
            // MainRealActivity로 이동하는 Intent 생성
            val intent = Intent(this, MainRealActivity::class.java)
            startActivity(intent) // MainRealActivity 시작
            finish() // 현재 MainActivity를 스택에서 제거하여 사용자가 뒤로가기 버튼을 눌러도 다시 이 화면으로 돌아오지 못하게 함
        }
        // 로그인되어 있지 않다면, 현재 MainActivity 화면에 머무르며 사용자 입력을 기다립니다.
    }

    // 액티비티가 처음 생성될 때 호출되는 콜백 메소드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater) // 뷰 바인딩 인스턴스 초기화
        setContentView(binding.root) // 액티비티의 레이아웃을 설정

        // Firebase Auth 인스턴스 가져오기
        auth = Firebase.auth

        // 로그인 버튼 클릭 리스너 설정
        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextEmail.text.toString() // 이메일 입력 필드의 텍스트 가져오기
            val password = binding.editTextPassword.text.toString() // 비밀번호 입력 필드의 텍스트 가져오기

            // 이메일과 비밀번호가 비어있지 않은지 확인
            if (email.isNotEmpty() && password.isNotEmpty()) {
                // Firebase Auth를 사용하여 이메일과 비밀번호로 로그인 시도
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task -> // 로그인 시도 완료 리스너
                        if (task.isSuccessful) { // 로그인 성공 시
                            Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show() // 성공 메시지 표시
                            val intent = Intent(this, MainRealActivity::class.java) // MainRealActivity로 이동하는 Intent 생성
                            startActivity(intent) // MainRealActivity 시작
                            finish() // 현재 액티비티 종료
                        } else { // 로그인 실패 시
                            // 실패 메시지 및 예외 메시지 표시
                            Toast.makeText(this, "로그인 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                // 이메일 또는 비밀번호가 비어있을 경우 메시지 표시
                Toast.makeText(this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 회원가입 레이아웃 (텍스트뷰 또는 버튼) 클릭 리스너 -> SignUpActivity로 이동
        binding.layoutGoToSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java) // SignUpActivity로 이동하는 Intent 생성
            startActivity(intent) // SignUpActivity 시작
        }
    }
}