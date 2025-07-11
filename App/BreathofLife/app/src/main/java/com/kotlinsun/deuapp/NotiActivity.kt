package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import androidx.activity.enableEdgeToEdge // 엣지 투 엣지 화면을 활성화하기 위한 함수 (시스템 바 영역까지 UI 확장)
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import androidx.core.view.ViewCompat // 뷰의 호환성 기능을 제공하는 유틸리티 클래스
import androidx.core.view.WindowInsetsCompat // 윈도우 인셋(상태 바, 내비게이션 바 등) 정보를 제공하는 호환성 클래스
import com.kotlinsun.deuapp.databinding.ActivityNotiBinding // 1. 생성된 바인딩 클래스를 임포트합니다.

// NotiActivity 클래스 선언: AppCompatActivity를 상속
class NotiActivity : AppCompatActivity() {

    // 2. 뷰 바인딩 객체를 선언합니다. lateinit으로 나중에 초기화할 것을 명시합니다.
    private lateinit var binding: ActivityNotiBinding

    // 액티비티가 처음 생성될 때 호출되는 콜백 메소드
    override fun onCreate(savedInstanceState: Bundle?) {
        // 엣지 투 엣지 화면을 활성화합니다. (선택 사항이며, UI가 시스템 바 아래까지 확장되도록 합니다.)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 뷰 바인딩 인스턴스를 초기화합니다.
        // ActivityNotiBinding.inflate() 메소드를 사용하여 레이아웃 XML 파일을 인플레이트하고 해당 뷰에 대한 바인딩 객체를 생성합니다.
        binding = ActivityNotiBinding.inflate(layoutInflater)
        // 액티비티의 콘텐츠 뷰를 바인딩 객체의 루트 뷰로 설정합니다.
        setContentView(binding.root)

        // 윈도우 인셋 리스너를 설정하여 시스템 바(상태 바, 내비게이션 바)와 UI 요소 간의 충돌을 처리합니다.
        // 이 부분은 엣지 투 엣지 화면 활성화 시 UI 요소가 시스템 바와 겹치지 않도록 패딩을 조정하는 데 사용됩니다.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // '뒤로가기' 버튼 (notiBackBtn) 클릭 리스너를 설정합니다.
        binding.notiBackBtn.setOnClickListener {
            finish() // 현재 액티비티를 종료합니다.
        }
    }
}