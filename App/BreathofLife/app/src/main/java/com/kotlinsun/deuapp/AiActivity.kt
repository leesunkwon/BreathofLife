package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.Manifest // Android 권한 관련 클래스 (예: RECORD_AUDIO, ACCESS_FINE_LOCATION)
import android.annotation.SuppressLint // Lint 경고를 무시하기 위한 어노테이션
import android.content.Intent // 액티비티 전환 및 데이터 전달을 위한 Intent 클래스
import android.content.pm.PackageManager // 패키지 정보 및 권한 상태 확인을 위한 클래스
import android.location.Location // GPS 등에서 얻은 위치 정보를 담는 클래스
import android.os.Bundle // 액티비티의 상태를 저장하고 복원하는 데 사용되는 Bundle 클래스
import android.speech.RecognitionListener // 음성 인식 이벤트 콜백을 위한 인터페이스
import android.speech.RecognizerIntent // 음성 인식을 시작하기 위한 표준 Intent 액션 및 추가 데이터
import android.speech.SpeechRecognizer // 음성을 텍스트로 변환하는 기능을 제공하는 클래스
import android.speech.tts.TextToSpeech // 텍스트를 음성으로 변환하는 기능을 제공하는 클래스
import android.util.Log // 로그 메시지를 출력하기 위한 유틸리티 클래스
import android.view.Gravity // 뷰의 정렬 (예: 왼쪽, 오른쪽, 중앙)을 설정하기 위한 상수
import android.view.View // 안드로이드 UI 구성 요소의 기본 클래스
import android.widget.LinearLayout // 자식 뷰를 선형으로 정렬하는 레이아웃
import android.widget.TextView // 텍스트를 표시하는 UI 요소
import android.widget.Toast // 사용자에게 짧은 팝업 메시지를 표시하는 클래스
import androidx.activity.result.contract.ActivityResultContracts // 액티비티 결과를 처리하기 위한 계약 클래스
import androidx.appcompat.app.AppCompatActivity // 호환성을 위해 제공되는 기본 액티비티 클래스
import androidx.core.app.ActivityCompat // 런타임 권한 요청을 돕는 유틸리티 클래스
import androidx.core.content.ContextCompat // 리소스 (예: 색상, 드로어블) 및 권한 상태를 가져오는 유틸리티 클래스
import androidx.lifecycle.lifecycleScope // 코루틴을 액티비티/프래그먼트의 라이프사이클에 바인딩하는 스코프
import com.google.ai.client.generativeai.GenerativeModel // Google Generative AI 모델을 사용하는 클래스
import com.google.ai.client.generativeai.type.Content // AI 모델과의 대화에서 메시지 내용을 나타내는 클래스
import com.google.ai.client.generativeai.type.TextPart // 대화 내용 중 텍스트 부분을 나타내는 클래스
import com.google.android.gms.location.LocationServices // Google 위치 서비스에 접근하는 진입점
import com.google.android.gms.location.Priority // 위치 요청의 정확도 및 전력 소비 우선순위 설정
import com.google.firebase.auth.ktx.auth // Firebase Authentication 기능을 사용하기 위한 Kotlin 확장 함수
import com.google.firebase.firestore.FieldValue // Firebase Firestore에서 서버 타임스탬프와 같은 특수 필드 값을 설정
import com.google.firebase.firestore.FirebaseFirestore // Firebase Firestore 데이터베이스 클라이언트 인스턴스
import com.google.firebase.firestore.GeoPoint // Firebase Firestore에서 지리적 위치(위도, 경도)를 나타내는 클래스
import com.google.firebase.ktx.Firebase // Firebase SDK 초기화 및 서비스에 접근하기 위한 Kotlin 확장
import com.kotlinsun.deuapp.databinding.ActivityAiBinding // 뷰 바인딩을 통해 레이아웃의 뷰에 접근하는 생성된 클래스
import kotlinx.coroutines.launch // 코루틴을 시작하기 위한 함수
import org.json.JSONObject // JSON 데이터를 파싱하고 생성하는 클래스
import java.util.Locale // 언어 및 국가 정보를 나타내는 클래스 (예: 한국어 설정)

// AiActivity 클래스 선언: AppCompatActivity를 상속하고 TextToSpeech.OnInitListener 인터페이스를 구현
class AiActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityAiBinding // UI 요소에 접근하기 위한 뷰 바인딩 인스턴스
    private lateinit var tts: TextToSpeech // 텍스트 음성 변환 (Text-to-Speech) 객체
    private lateinit var speechRecognizer: SpeechRecognizer // 음성 인식 객체
    private lateinit var generativeModel: GenerativeModel // Gemini AI 모델 객체

    private val db = FirebaseFirestore.getInstance() // Firestore 데이터베이스 인스턴스 가져오기
    private val auth = Firebase.auth // Firebase Authentication 인스턴스 가져오기
    private val TAG = "AiActivity" // 로그캣에 사용할 태그

    private val chatHistory = mutableListOf<Content>() // AI 모델과의 대화 기록을 저장하는 리스트
    private val RECORD_AUDIO_PERMISSION_CODE = 1 // 음성 녹음 권한 요청 시 사용될 고유 코드

    // 메시지 발신자를 구분하기 위한 열거형 (사용자 또는 AI)
    private enum class Sender { USER, AI }

    // 위치 권한 요청 결과를 처리하는 ActivityResultLauncher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions() // 여러 권한을 한 번에 요청
    ) { permissions ->
        // ACCESS_FINE_LOCATION 권한이 허용되지 않았다면 (기본값 false)
        if (!permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            // 사용자에게 토스트 메시지를 띄워 권한 부족을 알림
            Toast.makeText(this, "위치 권한이 없어 주변 병원을 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    // 액티비티가 처음 생성될 때 호출되는 콜백 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiBinding.inflate(layoutInflater) // 뷰 바인딩 인스턴스 초기화
        setContentView(binding.root) // 액티비티의 레이아웃을 설정

        // RECORD_AUDIO 권한이 부여되었는지 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없으면 사용자에게 권한 요청
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            // 권한이 이미 있다면 초기화 함수 호출
            initialize()
        }

        // 마이크 버튼 클릭 리스너 설정
        binding.micButton.setOnClickListener {
            // TTS가 현재 음성을 출력 중이라면 즉시 중지
            if (::tts.isInitialized && tts.isSpeaking) {
                tts.stop()
            }
            // TTS 중지 후 바로 음성 인식 시작
            startListening()
        }

        // 뒤로가기 버튼 클릭 리스너 설정
        binding.aiBackntn.setOnClickListener {
            finish() // 현재 액티비티 종료
        }
    }

    // 사용자에게 권한 요청 후 결과가 반환될 때 호출되는 콜백 메서드
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 요청 코드가 RECORD_AUDIO_PERMISSION_CODE와 일치하는지 확인
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            // 권한 요청 결과가 비어있지 않고 첫 번째 권한이 승인되었는지 확인
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initialize() // 권한이 부여되었다면 초기화 함수 호출
            } else {
                // 권한이 거부되었다면 상태 메시지 변경 및 마이크 버튼 비활성화
                binding.statusTextView.text = "음성 녹음 권한이 필요합니다."
                binding.micButton.isEnabled = false
            }
        }
    }

    // TTS와 Generative AI 모델을 초기화하는 함수
    private fun initialize() {
        tts = TextToSpeech(this, this) // TextToSpeech 객체를 현재 컨텍스트와 리스너를 사용하여 초기화
        generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash", // 사용할 Gemini 모델의 이름
            apiKey = "" // Gemini API 키 (보안을 위해 제거됨)
        )
    }

    // TextToSpeech 초기화가 완료될 때 호출되는 콜백 메서드
    override fun onInit(status: Int) {
        // TextToSpeech 초기화 성공 여부 확인
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN) // TTS 언어를 한국어로 설정
            // 언어 데이터가 없거나 지원되지 않는 언어일 경우
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                binding.statusTextView.text = "음성 기능을 지원하지 않는 언어입니다." // 상태 메시지 업데이트
            } else {
                binding.statusTextView.text = "마이크 버튼을 눌러주세요" // 초기 상태 메시지
                addChatMessageView("안녕하세요, 숨결이입니다. 어떤 도움이 필요하신가요?", Sender.AI) // AI의 시작 메시지를 채팅에 추가
            }
        } else {
            binding.statusTextView.text = "음성 기능 초기화 실패" // 초기화 실패 시 상태 메시지
        }
    }

    // 음성 인식을 시작하는 함수
    private fun startListening() {
        // 음성 인식을 위한 Intent 생성
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName) // 호출하는 패키지 이름 설정
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR") // 인식할 언어 설정 (한국어)
        }
        // SpeechRecognizer 객체가 아직 초기화되지 않았다면 새로 생성하고 리스너 설정
        if (!::speechRecognizer.isInitialized) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener) // 음성 인식 리스너 설정
            }
        }
        speechRecognizer.startListening(intent) // 음성 인식 시작
    }

    // 음성 인식 이벤트를 처리하는 리스너 객체
    private val recognitionListener = object : RecognitionListener {
        // 음성 인식을 위한 준비가 완료되었을 때 호출
        override fun onReadyForSpeech(params: Bundle?) {
            binding.statusTextView.text = "듣고 있어요..." // 상태 메시지 변경
            binding.micButton.isEnabled = false // 음성 인식 중에는 마이크 버튼 비활성화
        }
        override fun onBeginningOfSpeech() {} // 사용자의 음성 입력이 시작되었을 때 호출
        override fun onRmsChanged(rmsdB: Float) {} // 현재 입력되는 음성의 RMS(Root Mean Square) 값이 변경될 때 호출
        override fun onBufferReceived(buffer: ByteArray?) {} // 음성 데이터를 버퍼로 받았을 때 호출
        // 음성 입력이 종료되었을 때 호출
        override fun onEndOfSpeech() {
            binding.statusTextView.text = "음성 인식 중..." // 상태 메시지 변경
        }

        // 음성 인식 중 오류가 발생했을 때 호출
        override fun onError(error: Int) {
            // 오류 코드에 따라 적절한 메시지 설정
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "음성을 인식하지 못했어요. 다시 시도해주세요."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "시간이 초과되었습니다. 다시 시도해주세요."
                else -> "음성 인식 중 오류가 발생했습니다."
            }
            binding.statusTextView.text = message // 상태 메시지 업데이트
            binding.micButton.isEnabled = true // 마이크 버튼 다시 활성화
        }

        // 음성 인식 최종 결과가 나왔을 때 호출
        override fun onResults(results: Bundle?) {
            // 인식된 텍스트 목록을 가져옴
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0] // 가장 정확하다고 판단된 첫 번째 텍스트 가져오기
                addChatMessageView(recognizedText, Sender.USER) // 인식된 텍스트를 사용자 메시지로 채팅 UI에 추가
                getChatResponse(recognizedText) // AI에게 응답을 요청
            } else {
                binding.micButton.isEnabled = true // 인식된 텍스트가 없으면 마이크 버튼 활성화
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {} // 부분적인 음성 인식 결과가 나왔을 때 호출
        override fun onEvent(eventType: Int, params: Bundle?) {} // 특정 이벤트가 발생했을 때 호출
    }

    // 채팅 메시지를 UI에 추가하는 함수
    private fun addChatMessageView(message: String, sender: Sender) {
        val textView = TextView(this).apply {
            text = message // 텍스트 뷰에 메시지 설정
            textSize = 16f // 텍스트 크기 설정
            setTextColor(ContextCompat.getColor(context, android.R.color.black)) // 텍스트 색상 설정
            // 가로 및 세로 패딩 계산 및 설정
            val hPadding = (16 * resources.displayMetrics.density).toInt()
            val vPadding = (12 * resources.displayMetrics.density).toInt()
            setPadding(hPadding, vPadding, hPadding, vPadding)

            // LinearLayout에 적용될 LayoutParams 설정
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, // 너비를 텍스트 내용에 맞춤
                LinearLayout.LayoutParams.WRAP_CONTENT // 높이를 텍스트 내용에 맞춤
            ).apply {
                bottomMargin = (12 * resources.displayMetrics.density).toInt() // 하단 마진 설정
                // 메시지 발신자에 따라 배경 드로어블과 정렬 (사용자: 오른쪽, AI: 왼쪽) 설정
                if (sender == Sender.USER) {
                    background = ContextCompat.getDrawable(context, R.drawable.bg_chat_user) // 사용자 메시지 배경
                    gravity = Gravity.END // 오른쪽 정렬
                } else {
                    background = ContextCompat.getDrawable(context, R.drawable.bg_chat_ai) // AI 메시지 배경
                    gravity = Gravity.START // 왼쪽 정렬
                }
            }
            this.layoutParams = layoutParams // 텍스트 뷰에 레이아웃 파라미터 적용
        }
        binding.chatContainer.addView(textView) // 채팅 메시지를 표시할 컨테이너에 텍스트 뷰 추가
        binding.scrollView.post { binding.scrollView.fullScroll(View.FOCUS_DOWN) } // 스크롤 뷰를 최하단으로 이동
    }

    // AI로부터 채팅 응답을 받아 처리하는 함수
    private fun getChatResponse(question: String) {
        binding.statusTextView.text = "AI 생각 중..." // AI가 응답을 생성 중임을 표시
        // AI의 역할, 목표, 대화 규칙을 정의하는 시스템 프롬프트
        val systemPrompt = """
            [너의 역할]
            너는 '숨결이'라는 이름의 AI 비서야. 사용자인 의료진이나 보호자로부터 환자 정보를 입력받아 정리하는 중요한 임무를 맡고 있어. 차분하고 명확한 말투를 사용해.
            [너의 목표]
            사용자의 말을 듣고 다음 5가지 항목에 대한 정보를 수집해서 최종적으로 JSON 형식으로 만드는 것이야.
            - 이름 (필수)
            - 나이 (숫자만, 필수)
            - 성별 (필수)
            - 주요증상 (필수)
            - 특이사항
            [대화 규칙]
            1. 정보 수집: 사용자가 말하는 내용에서 위 5가지 항목의 정보를 파악해.
            2. 추가 질문: 만약 '이름', '나이', '성별', '주요증상' 같은 필수 정보가 부족하면, 반드시 예의 바르게 추가 질문을 해서 빈칸을 채워야 해.
            3. 확인 요청: 모든 필수 정보가 채워졌다고 판단되면, 수집된 정보를 간결하게 요약해서 보여주고 반드시 "이대로 요청할까요?" 라는 질문으로 사용자에게 확인을 받아야 해.
            4. 최종 응답 (가장 중요): 사용자가 "응", "네", "요청해줘", "부탁해" 와 같이 긍정적으로 대답하면, 너의 최종 답변은 반드시 `{"status": "완료", "patientInfo": {"name": "...", "age": 52, "gender": "...", "symptom": "...", "otherInfo": "..."}}` 형식의 JSON 문자열이어야 해. 다른 말은 절대 덧붙이면 안 돼.
            5. 수정: 만약 사용자가 확인 단계에서 정보 수정을 원하면, 해당 정보를 수정한 뒤 다시 요약하고 "이대로 요청할까요?" 라고 물어봐.
            6. 답변은 항상 간단명료하게 해줘.
            7. 답변을 꾸미기 위한 특수 기호(예: -, *, • 등)는 절대 사용하지 마.
            8. 최종 JSON에서 'age' 값은 반드시 따옴표 없는 숫자여야 해. (예: "age": 28 (O), "age": "28" (X), "age": "28세" (X))
        """.trimIndent()

        lifecycleScope.launch { // 비동기 작업을 위한 코루틴 시작
            try {
                // 채팅 기록이 비어있으면 시스템 프롬프트를 먼저 추가
                if (chatHistory.isEmpty()) {
                    chatHistory.add(Content(role = "user", parts = listOf(TextPart(systemPrompt))))
                }
                chatHistory.add(Content(role = "user", parts = listOf(TextPart(question)))) // 사용자 질문을 대화 기록에 추가

                val chat = generativeModel.startChat(history = chatHistory) // 대화 기록을 포함하여 새로운 채팅 세션 시작
                val response = chat.sendMessage(question) // AI 모델에 사용자 질문을 전송하고 응답 받기
                // AI 응답 후보 중 첫 번째 내용 가져오기 (없으면 예외 발생)
                val modelResponseContent = response.candidates.firstOrNull()?.content
                    ?: throw IllegalStateException("No candidate response found")
                chatHistory.add(modelResponseContent) // AI 응답 내용을 대화 기록에 추가
                val responseText = (modelResponseContent.parts.first() as TextPart).text // AI 응답 텍스트 추출

                try {
                    val jsonResponse = JSONObject(responseText) // 응답 텍스트를 JSON 객체로 파싱 시도
                    // JSON 응답의 "status" 필드가 "완료"인지 확인
                    if (jsonResponse.getString("status") == "완료") {
                        val patientInfoJson = jsonResponse.getJSONObject("patientInfo") // 환자 정보 JSON 객체 추출
                        // 환자 정보를 HashMap으로 변환
                        val patientInfoMap = hashMapOf<String, Any>(
                            "name" to patientInfoJson.getString("name"),
                            "age" to patientInfoJson.getInt("age"), // age는 정수로 파싱
                            "gender" to patientInfoJson.getString("gender"),
                            "symptom" to patientInfoJson.getString("symptom"),
                            "otherInfo" to patientInfoJson.getString("otherInfo")
                        )
                        val confirmationMessage = "알겠습니다. 주변 병원 검색을 시작합니다." // 확인 메시지
                        speakOut(confirmationMessage, false) // AI가 확인 메시지 말하도록 지시 (이후 음성 인식 시작 안 함)
                        startIntelligentRequest(patientInfoMap) // 지능형 요청 시작 (병원 검색 등)
                        chatHistory.clear() // 대화 기록 초기화
                        return@launch // 코루틴 종료
                    }
                } catch (e: Exception) {
                    // 응답이 JSON 형식이 아닐 경우 로그 출력 (정상적인 대화 흐름일 수 있음)
                    Log.d("JSON", "응답이 JSON 형식이 아님: $responseText")
                }
                speakOut(responseText, true) // AI 응답을 음성으로 출력하고 이후 음성 인식 다시 시작
            } catch (e: Exception) {
                Log.e("Gemini", "API 호출 실패", e) // API 호출 실패 시 오류 로그 출력
                val errorMessage = "오류가 발생했어요. 다시 시도해주세요." // 사용자에게 보여줄 오류 메시지
                speakOut(errorMessage, true) // 오류 메시지를 음성으로 출력하고 이후 음성 인식 다시 시작
            }
        }
    }

    // 환자 정보를 바탕으로 지능적인 요청(병원 검색 등)을 시작하는 함수
    private fun startIntelligentRequest(patientInfo: HashMap<String, Any>) {
        val symptom = patientInfo["symptom"] as? String // 환자 정보에서 주요 증상 추출
        if (symptom.isNullOrEmpty()) {
            Toast.makeText(this, "분석할 증상 정보가 없습니다.", Toast.LENGTH_SHORT).show() // 증상 없으면 토스트 메시지
            return // 함수 종료
        }
        showLoading(true, "현재 위치 확인 중...") // 로딩 표시 및 메시지 업데이트
        checkLocationPermissionAndProceed(symptom, patientInfo) // 위치 권한 확인 후 진행
    }

    // 위치 권한을 확인하고 다음 단계로 진행하는 함수
    private fun checkLocationPermissionAndProceed(symptom: String, patientInfo: HashMap<String, Any>) {
        // ACCESS_FINE_LOCATION 권한이 부여되었는지 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndProceed(symptom, patientInfo) // 권한이 있으면 현재 위치 가져오기 진행
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) // 권한이 없으면 요청
            showLoading(false) // 로딩 숨김
        }
    }

    // 현재 위치를 가져오고 다음 단계로 진행하는 함수 (권한이 이미 있다고 가정)
    @SuppressLint("MissingPermission") // 권한 검사를 Lint에서 무시하도록 지시 (실제로는 checkLocationPermissionAndProceed에서 검사됨)
    private fun getCurrentLocationAndProceed(symptom: String, patientInfo: HashMap<String, Any>) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) // FusedLocationProviderClient 가져오기
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null) // 현재 위치를 높은 정확도로 요청
            .addOnSuccessListener { location: Location? -> // 위치 정보 성공적으로 가져왔을 때
                if (location != null) {
                    getDepartmentsFromSymptom(symptom, location, patientInfo) // 증상 기반 진료과 찾기 진행
                } else {
                    Toast.makeText(this, "현재 위치를 가져올 수 없습니다. GPS를 확인해주세요.", Toast.LENGTH_LONG).show() // 위치 정보 없으면 토스트 메시지
                    showLoading(false) // 로딩 숨김
                }
            }
            .addOnFailureListener { // 위치 정보 가져오기 실패 시
                Toast.makeText(this, "현재 위치를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show() // 토스트 메시지
                showLoading(false) // 로딩 숨김
            }
    }

    // 증상에 따라 관련 진료과를 추천받고 주변 병원을 검색하는 함수
    private fun getDepartmentsFromSymptom(symptom: String, userLocation: Location, patientInfo: HashMap<String, Any>) {
        showLoading(true, "증상 분석 및 병원 검색 중...") // 로딩 표시 및 메시지 업데이트
        val departmentList = listOf( // 사전에 정의된 진료과 목록
            "가정의학과", "내과", "마취통증의학과", "병리과", "비뇨의학과", "산부인과",
            "성형외과", "소아청소년과", "신경외과", "안과", "영상의학과",
            "이비인후과", "재활의학과", "정신건강의학과", "정형외과", "직업환경의학과",
            "진단검사의학과", "피부과", "핵의학과", "흉부외과"
        ).joinToString(", ") // 쉼표로 구분된 문자열로 변환

        // Gemini AI에 보낼 프롬프트: 증상에 맞는 진료과 추천 요청
        val prompt = "환자의 주요 증상은 '$symptom' 입니다. 이 증상과 가장 관련성이 높은 진료과를 다음 목록에서 최대 2개 골라주세요: [$departmentList]. 다른 설명은 모두 제외하고, 쉼표(,)로 구분된 진료과 이름만 응답해주세요."

        lifecycleScope.launch { // 코루틴 시작
            try {
                // 증상 분석을 위한 새로운 GenerativeModel 인스턴스 생성 (기존 API 키 사용)
                val symptomModel = GenerativeModel(modelName = generativeModel.modelName, apiKey = generativeModel.apiKey)
                val response = symptomModel.generateContent(prompt) // 프롬프트로 AI 응답 생성
                // 응답 텍스트를 쉼표로 분리하고 공백 제거, 비어있지 않은 항목만 필터링하여 추천 진료과 목록 생성
                val recommendedDepts = response.text?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

                if (recommendedDepts.isNullOrEmpty()) {
                    Toast.makeText(this@AiActivity, "증상에 맞는 진료과를 찾지 못했습니다.", Toast.LENGTH_LONG).show() // 추천 진료과 없으면 토스트
                    showLoading(false) // 로딩 숨김
                } else {
                    Log.d(TAG, "Gemini 추천 진료과: $recommendedDepts") // 추천 진료과 로그 출력
                    val radiiToSearch = listOf(5000.0, 10000.0, 20000.0) // 검색할 반경 목록 (미터 단위)
                    // 재귀적으로 주변 병원 검색 시작
                    findNearbyHospitalsRecursively(userLocation, recommendedDepts, patientInfo, radiiToSearch, 0)
                }
            } catch (e: Exception) {
                Toast.makeText(this@AiActivity, "증상 분석 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show() // 증상 분석 중 오류 발생 시 토스트
                showLoading(false) // 로딩 숨김
            }
        }
    }

    // 재귀적으로 주변 병원을 찾아 응급 요청 문서를 생성하는 함수
    private fun findNearbyHospitalsRecursively(userLocation: Location, recommendedDepts: List<String>, patientInfo: HashMap<String, Any>, radii: List<Double>, index: Int) {
        // 모든 반경을 검색했음에도 병원을 찾지 못했을 경우
        if (index >= radii.size) {
            Toast.makeText(this, "조건에 맞는 병원이 주변에 없습니다.", Toast.LENGTH_LONG).show() // 토스트 메시지
            showLoading(false) // 로딩 숨김
            return // 함수 종료
        }
        val currentRadius = radii[index] // 현재 검색할 반경
        val radiusInKm = (currentRadius / 1000).toInt() // 반경을 KM 단위로 변환
        showLoading(true, "$radiusInKm km 반경 내 병원 검색 중...") // 로딩 표시 및 메시지 업데이트

        db.collection("hospitals") // Firestore "hospitals" 컬렉션 참조
            .whereArrayContainsAny("availableDepartments", recommendedDepts) // 추천 진료과를 포함하는 병원 필터링
            .get() // 문서 가져오기
            .addOnSuccessListener { hospitalSnapshot -> // 문서 가져오기 성공 시
                val nearbyHospitalIds = hospitalSnapshot.documents.mapNotNull { doc ->
                    val hospital = doc.toObject(Hospital::class.java) // 문서를 Hospital 객체로 변환
                    hospital?.location?.let { loc -> // 병원 위치 정보가 있다면
                        val distance = calculateDistance(userLocation, loc) // 사용자 위치와 병원 간 거리 계산
                        if (distance <= currentRadius) hospital.id else null // 현재 반경 내에 있으면 병원 ID 반환
                    }
                }

                if (nearbyHospitalIds.isEmpty()) {
                    // 현재 반경 내에 병원이 없으면 다음 반경으로 재귀 호출
                    findNearbyHospitalsRecursively(userLocation, recommendedDepts, patientInfo, radii, index + 1)
                } else {
                    // 병원을 찾았다면 Firestore에 응급 호출 문서 생성
                    createFirestoreCallDocument(userLocation, nearbyHospitalIds, recommendedDepts, patientInfo)
                }
            }
            .addOnFailureListener { // 문서 가져오기 실패 시
                Toast.makeText(this, "병원 목록을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show() // 토스트 메시지
                showLoading(false) // 로딩 숨김
            }
    }

    // Firestore에 응급 호출 문서를 생성하는 함수
    private fun createFirestoreCallDocument(userLocation: Location, targetHospitalIds: List<String>, recommendedDepts: List<String>, patientInfo: HashMap<String, Any>) {
        val paramedic = auth.currentUser!! // 현재 로그인한 구급대원 사용자 정보 (반드시 존재한다고 가정)
        val location = GeoPoint(userLocation.latitude, userLocation.longitude) // 현재 사용자 위치를 GeoPoint로 변환
        // Firestore에 저장할 응급 호출 데이터 생성
        val emergencyCallData = hashMapOf(
            "paramedicId" to paramedic.uid, // 구급대원 ID
            "patientInfo" to patientInfo, // 환자 정보
            "location" to location, // 현재 위치
            "status" to "pending", // 호출 상태 (대기 중)
            "acceptedHospitalId" to null, // 수락한 병원 ID (초기에는 null)
            "createdAt" to FieldValue.serverTimestamp(), // 생성 시간 (서버 타임스탬프)
            "completedAt" to null, // 완료 시간 (초기에는 null)
            "targetedHospitalIds" to targetHospitalIds, // 대상 병원 ID 목록
            "recommendedDepartments" to recommendedDepts // 추천 진료과 목록
        )

        db.collection("emergency_calls").add(emergencyCallData) // "emergency_calls" 컬렉션에 새 문서 추가
            .addOnSuccessListener { documentReference -> // 문서 추가 성공 시
                showLoading(false) // 로딩 숨김
                Toast.makeText(this, "${targetHospitalIds.size}개 병원에 응급 요청을 전송했습니다.", Toast.LENGTH_LONG).show() // 성공 토스트
                val intent = Intent(this, CallStatusActivity::class.java) // CallStatusActivity로 이동할 Intent 생성
                intent.putExtra("CALL_ID", documentReference.id) // 생성된 호출 문서 ID를 Intent에 추가
                startActivity(intent) // 액티비티 시작
                finish() // 현재 액티비티 종료
            }
            .addOnFailureListener { // 문서 추가 실패 시
                showLoading(false) // 로딩 숨김
                Toast.makeText(this, "요청 생성에 실패했습니다.", Toast.LENGTH_SHORT).show() // 실패 토스트
            }
    }

    // 텍스트를 음성으로 출력하는 함수
    private fun speakOut(text: String, startListeningAfter: Boolean) {
        addChatMessageView(text, Sender.AI) // AI 메시지를 채팅 UI에 추가

        // AI가 말하는 동안에도 마이크 버튼을 활성화하여 사용자가 중간에 말을 끊을 수 있도록 함
        binding.micButton.isEnabled = true

        // TTS 음성 출력 진행 상황을 모니터링하는 리스너 설정
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { binding.micButton.isEnabled = true } // 음성 출력이 시작되면 마이크 버튼 활성화 (안전 장치)
            }
            override fun onError(utteranceId: String?) {
                runOnUiThread { binding.micButton.isEnabled = true } // 음성 출력 중 오류 발생 시 마이크 버튼 활성화
            }
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    if (startListeningAfter) {
                        startListening() // 음성 출력이 끝나면 음성 인식 다시 시작
                    } else {
                        binding.micButton.isEnabled = true // 음성 인식 시작하지 않고 마이크 버튼 활성화
                    }
                }
            }
        })
        // 텍스트를 음성으로 출력 (이전 큐의 모든 음성을 지우고 새 음성 재생)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    // 두 지점(Location 객체와 GeoPoint 객체) 사이의 거리를 계산하는 함수
    private fun calculateDistance(start: Location, end: GeoPoint): Float {
        val results = FloatArray(1) // 거리 결과를 저장할 배열
        // Location.distanceBetween 함수를 사용하여 위도, 경도 기반 거리 계산 (미터 단위)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0] // 계산된 거리 반환
    }

    // 로딩 오버레이의 가시성을 제어하고 메시지를 설정하는 함수
    private fun showLoading(isLoading: Boolean, message: String = "요청 처리 중...") {
        // isLoading 값에 따라 로딩 오버레이의 가시성을 설정 (true면 VISIBLE, false면 GONE)
        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.progressText.text = message // 로딩 메시지 설정
    }

    // 액티비티가 소멸될 때 호출되는 콜백 메서드
    override fun onDestroy() {
        // TextToSpeech 객체가 초기화되었다면 중지 및 종료
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        // SpeechRecognizer 객체가 초기화되었다면 리소스 해제
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        super.onDestroy() // 상위 클래스의 onDestroy 호출
    }
}