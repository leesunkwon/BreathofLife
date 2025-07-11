# BreathofLife
AI 기술을 활용한 실시간 응급실 현황 공유 및 최적 병원 자동 추천 플랫폼
<img width="1920" height="1440" alt="image" src="https://github.com/user-attachments/assets/b1193b14-c54b-4c18-a314-cda33ef31f6d" />




🚑 AI 기반 응급 진료 추천 시스템 (숨결)
AI가 환자의 증상을 분석하여 최적의 진료과를 추천하고, 주변 병원의 실시간 정보를 제공하는 안드로이드 & 웹 솔루션입니다.



📅 개발 기간 및 참여 인원
개발 기간: 2025.06.28 ~ 2025.07.09

담당 역할

이름

주요 기여

🐧 Android 총괄

이선권

클라이언트 앱 전체 설계 및 기능 개발

🎨 Android UI/Location

임민욱

XML 설계, 위치 서비스 및 유저 정보 관리 서비스 개발

🌐 Web

김희수

웹 시스템 전체 설계 및 개발

🤖 AI Backend

양진원

AI 분석 및 백엔드 로직 개발


🛠️ 적용 기술 스택 (Technology Stack)
📱 클라이언트 (Android)
Language: Kotlin

Architecture: MVVM 패턴 기반 설계

Asynchronous: Kotlin Coroutines (lifecycleScope, Job), Callback Listeners

UI: ViewBinding, RecyclerView, ConstraintLayout, Material Design Components

Android API: STT (SpeechRecognizer), TTS (TextToSpeech), FusedLocationProviderClient, SoundPool

Library: Google AI SDK for Gemini

☁️ 백엔드 (Firebase)
Firebase Authentication: 이메일/비밀번호 기반 사용자 인증 및 세션 관리

Firebase Firestore: NoSQL 기반 실시간 데이터베이스

실시간 리스너(SnapshotListener)를 통한 데이터 동기화

Security Rules를 통한 역할 기반 접근 제어

whereArrayContainsAny, collectionGroup 등 고급 쿼리 활용

**색인(Index)**을 통한 쿼리 성능 최적화

🤖 인공지능 (AI)
Google Gemini API (gemini-2.0-flash)

자연어 이해(NLU): 환자 증상 텍스트 분석 및 최적 진료과 추천

대화형 AI: 시스템 프롬프트 기반 역할 부여 및 JSON 형식의 구조화된 데이터 추출



⚙️ 실행 방법 (Setup & Run)
Firebase 프로젝트 구성

Firebase 콘솔에서 새 프로젝트를 생성합니다.

Authentication (이메일/비밀번호 방식)과 Firestore를 활성화합니다.

프로젝트 설정에서 google-services.json 파일을 다운로드하여 Android 프로젝트의 app 모듈에 추가합니다.

Firestore 색인 설정

Firestore 데이터베이스의 [색인] 탭으로 이동하여 필요한 복합 색인을 생성합니다.

whereArrayContainsAny 또는 collectionGroup 쿼리 사용 시 콘솔에 표시되는 링크를 통해 필요한 색인을 간편하게 추가할 수 있습니다.

API 키 설정

Google AI Studio에서 Gemini API Key를 발급받습니다.

프로젝트의 local.properties 파일에 API 키를 추가하고, 코드에서 안전하게 참조하도록 설정합니다.

Properties

# local.properties
GEMINI_API_KEY="YOUR_API_KEY"


🌐 테스트용 웹 기능
Note: 웹은 병원 관계자의 관리를 위한 테스트 목적으로 제작되었습니다.

병원 회원가입 및 로그인

환자의 응급 요청 수락 및 거절

진료 가능한 진료과 목록 관리

가용 병상 수 실시간 관리



📝 폰트 정보 (Font)
서울서체 (Seoul Font)

출처: https://www.seoul.go.kr/seoul/font.do
