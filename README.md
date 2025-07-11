# BreathofLife
AI 기술을 활용한 실시간 응급실 현황 공유 및 최적 병원 자동 추천 플랫폼
<img width="1920" height="1440" alt="image" src="https://github.com/user-attachments/assets/b1193b14-c54b-4c18-a314-cda33ef31f6d" />

개발 기간: 2025.06.28 ~ 2025.07.09
담당 역할

- 이선권 : Android 클라이언트 앱 전체 설계 및 개발
- 임민욱 : Android Xml 설계 및 위치 서비스 관련 개발
- 김희수 : 웹 전체 설계 및 개발
- 양진원 : AI 분석 백엔드 개발



적용 기술 스택 (Technology Stack)

- 클라이언트 (Android)
    - Language: `Kotlin`
    - Architecture: `MVVM` 패턴 기반 설계
    - Asynchronous: `Kotlin Coroutines` (lifecycleScope, Job), `Callback Listeners`
    - UI: `ViewBinding`, `RecyclerView`, `ConstraintLayout`, `Material Design Components`
    - Android API: `STT` (SpeechRecognizer), `TTS` (TextToSpeech), `FusedLocationProviderClient`, `SoundPool`
    - Library: `Google AI SDK for Gemini`
- 백엔드 (Firebase)
    - `Firebase Authentication`: 이메일/비밀번호 기반 사용자 인증 및 세션 관리
    - `Firebase Firestore`: NoSQL 기반 실시간 데이터베이스
        - 실시간 리스너(SnapshotListener)를 통한 데이터 동기화
        - Security Rules를 통한 역할 기반 접근 제어
        - `whereArrayContainsAny`, `collectionGroup` 등 고급 쿼리 활용
        - 색인(Index)을 통한 쿼리 성능 최적화
- 인공지능 (AI)
    - `Google Gemini API (gemini-2.0-flash)`:
        - 자연어 이해(NLU): 환자 증상 텍스트 분석 및 최적 진료과 추천
        - 대화형 AI: 시스템 프롬프트 기반 역할 부여 및 JSON 형식의 구조화된 데이터 추출

폰트 출처
https://www.seoul.go.kr/seoul/font.do
