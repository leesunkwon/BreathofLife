package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.widget.Toast // 사용자에게 짧은 메시지를 표시하는 Toast 클래스
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import com.kotlinsun.deuapp.databinding.ActivitySignUpBinding // 뷰 바인딩을 통해 레이아웃의 뷰에 접근하는 생성된 클래스
import com.google.firebase.auth.FirebaseAuth // Firebase Authentication 클라이언트
import com.google.firebase.auth.ktx.auth // Firebase Authentication KTX 확장 (FirebaseAuth 인스턴스 가져오기)
import com.google.firebase.firestore.FirebaseFirestore // Firebase Firestore 데이터베이스 클라이언트
import com.google.firebase.firestore.ktx.firestore // Firebase Firestore KTX 확장 (Firestore 인스턴스 가져오기)
import com.google.firebase.ktx.Firebase // Firebase KTX 확장 (Firebase 초기화 및 서비스 접근)

// SignUpActivity 클래스 선언: AppCompatActivity를 상속
class SignUpActivity : AppCompatActivity() {

    // View Binding 및 Firebase 인스턴스를 지연 초기화
    private lateinit var binding: ActivitySignUpBinding // UI 요소에 접근하기 위한 뷰 바인딩 인스턴스
    private lateinit var auth: FirebaseAuth // Firebase Authentication 인스턴스
    private lateinit var db: FirebaseFirestore // Firebase Firestore 데이터베이스 인스턴스

    // 액티비티가 처음 생성될 때 호출되는 콜백 메소드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater) // 뷰 바인딩 인스턴스 초기화
        setContentView(binding.root) // 액티비티의 레이아웃을 설정

        // Firebase 인스턴스 가져오기
        auth = Firebase.auth // Firebase Authentication 인스턴스 초기화
        db = Firebase.firestore // Firebase Firestore 인스턴스 초기화

        // '로그인 화면으로 이동' 레이아웃 클릭 리스너: 현재 액티비티 종료 (로그인 화면으로 돌아감)
        binding.layoutGoToLogin.setOnClickListener {
            finish()
        }

        // '뒤로가기' 버튼 클릭 리스너: 현재 액티비티 종료
        binding.signupBackBtn.setOnClickListener {
            finish()
        }

        // 회원가입 버튼 클릭 리스너
        binding.buttonSignUp.setOnClickListener {
            val name = binding.editTextName.text.toString() // 이름 입력 필드 텍스트 가져오기
            val email = binding.editTextSignUpEmail.text.toString() // 이메일 입력 필드 텍스트 가져오기
            val password = binding.editTextSignUpPassword.text.toString() // 비밀번호 입력 필드 텍스트 가져오기
            val organization = binding.editTextOrganization.text.toString() // 소속 입력 필드 텍스트 가져오기

            // 입력 값 유효성 검사 (모든 필드가 비어있지 않은지 확인)
            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() && organization.isNotEmpty()) {
                // 비밀번호 길이가 6자리 미만인지 확인
                if (password.length < 6) {
                    Toast.makeText(this, "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show() // 토스트 메시지
                    return@setOnClickListener // 함수 종료
                }

                // 1. Firebase Authentication으로 신규 사용자 생성 시도
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task -> // 사용자 생성 완료 리스너
                        if (task.isSuccessful) { // 사용자 생성 성공 시
                            // 회원가입 성공 시, Firestore에 추가 사용자 정보 저장
                            val user = auth.currentUser // 새로 생성된 사용자 객체 가져오기
                            val uid = user?.uid // 사용자의 UID 가져오기 (고유 식별자)

                            if (uid != null) { // UID가 null이 아닌 경우에만 데이터 저장 진행
                                // 2. Firestore에 저장할 사용자 데이터맵 생성
                                val userMap = hashMapOf(
                                    "name" to name, // 사용자 이름
                                    "email" to email, // 사용자 이메일
                                    "organization" to organization, // 사용자 소속
                                    "role" to "paramedic" // ⭐ 구급대원 역할(role)을 "paramedic"으로 자동 할당
                                )

                                // 3. 'users' 컬렉션에 사용자 정보 저장 (문서 ID를 UID로 설정)
                                db.collection("users").document(uid)
                                    .set(userMap) // 데이터를 문서에 설정 (기존 문서가 있으면 덮어씀)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "회원가입 성공! 로그인 해주세요.", Toast.LENGTH_LONG).show() // 성공 메시지
                                        finish() // 회원가입 화면 종료하고 로그인 화면으로 돌아가기
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Firestore 저장 실패: ${e.message}", Toast.LENGTH_LONG).show() // Firestore 저장 실패 메시지
                                    }
                            }
                        } else {
                            // 회원가입 실패 시 (예: 이미 등록된 이메일, 잘못된 이메일 형식 등)
                            Toast.makeText(this, "회원가입 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show() // 실패 메시지 및 예외 메시지 표시
                        }
                    }

            } else {
                // 필수 입력 필드가 하나라도 비어있을 경우 메시지 표시
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}