package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.app.Dialog // 커스텀 다이얼로그 생성을 위한 Dialog 클래스
import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.util.Log // 로그 출력을 위한 유틸리티 클래스
import android.view.View // 안드로이드 UI 구성 요소의 기본 클래스
import android.view.Window // 다이얼로그의 윈도우 속성을 제어하기 위한 클래스
import android.widget.Button // 버튼 UI 요소
import android.widget.TextView // 텍스트 뷰 UI 요소
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import androidx.lifecycle.lifecycleScope // 코루틴을 액티비티/프래그먼트의 라이프사이클에 바인딩하는 스코프
import androidx.recyclerview.widget.LinearLayoutManager // RecyclerView에 아이템을 선형으로 배치하는 레이아웃 매니저
import com.google.ai.client.generativeai.GenerativeModel // Google Generative AI 모델을 사용하는 클래스
import com.google.ai.client.generativeai.type.generationConfig // Gemini 모델의 응답 생성 설정을 위한 함수
import com.google.firebase.auth.ktx.auth // Firebase Authentication KTX 확장
import com.google.firebase.firestore.Query // Firestore 쿼리 정렬을 위한 클래스
import com.google.firebase.firestore.ktx.firestore // Firebase Firestore KTX 확장 (Firestore 인스턴스 가져오기)
import com.google.firebase.ktx.Firebase // Firebase KTX 확장 (Firebase 초기화 및 서비스 접근)
import com.kotlinsun.deuapp.databinding.ActivityHistoryBinding // 뷰 바인딩 클래스
import kotlinx.coroutines.launch // 코루틴 시작을 위한 함수
import org.json.JSONObject // JSON 데이터를 파싱하고 생성하는 클래스
import java.text.SimpleDateFormat // 날짜/시간 포맷을 위한 클래스
import java.util.Locale // 언어 및 국가 정보를 나타내는 클래스 (예: 한국어 설정)

// HistoryAdapter의 클릭 리스너 인터페이스를 구현하는 HistoryActivity 클래스 선언
class HistoryActivity : AppCompatActivity(), HistoryAdapter.OnItemClickListener {

    private lateinit var binding: ActivityHistoryBinding // 뷰 바인딩 인스턴스
    private lateinit var historyAdapter: HistoryAdapter // 기록 목록을 표시할 RecyclerView 어댑터
    private val db = Firebase.firestore // Firebase Firestore 데이터베이스 인스턴스
    private val auth = Firebase.auth // Firebase Authentication 인스턴스
    private var allCasesText: String = "" // 모든 완료된 케이스 데이터를 텍스트로 저장할 변수 (LLM 분석용)

    // Gemini 모델을 지연 초기화 (lazy)
    private val generativeModel by lazy {
        // 응답을 JSON 형식으로 받도록 설정
        val config = generationConfig {
            responseMimeType = "application/json" // 응답 MIME 타입을 JSON으로 명시
        }
        GenerativeModel(
            modelName = "gemini-2.5-flash", // 사용할 Gemini 모델 이름 (예: gemini-2.5-flash 또는 gemini-pro)
            // ⚠️ 보안 경고: API 키를 코드에 직접 하드코딩하는 것은 매우 위험합니다.
            // 이 방식은 테스트용으로만 사용하고, 실제 앱 배포 시에는 절대 사용하지 마세요.
            apiKey = "", // Gemini API 키
            generationConfig = config // 생성 설정 적용
        )
    }

    // 액티비티가 처음 생성될 때 호출되는 콜백 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater) // 뷰 바인딩 인스턴스 초기화
        setContentView(binding.root) // 액티비티 레이아웃 설정

        setupRecyclerView() // RecyclerView 설정
        loadCompletedCases() // 완료된 케이스 데이터 로드

        // 분석 버튼 클릭 리스너 설정
        binding.buttonAnalyze.setOnClickListener {
            if (allCasesText.isNotBlank()) { // 분석할 데이터가 있는지 확인
                analyzeCasesWithGemini() // Gemini 모델을 사용하여 케이스 분석 시작
            } else {
                binding.cardViewAnalysis.visibility = View.VISIBLE // 분석 결과 카드 뷰 표시
                binding.textViewAnalysisResult.text = "분석할 데이터가 없습니다." // 데이터 없음 메시지 표시
            }
        }

        // 뒤로가기 버튼 클릭 리스너 설정
        binding.backBtn.setOnClickListener {
            finish() // 현재 액티비티 종료
        }
    }

    // RecyclerView를 초기 설정하는 함수
    private fun setupRecyclerView() {
        // 어댑터를 미리 생성하고, 클릭 리스너를 현재 액티비티(this)로 설정
        historyAdapter = HistoryAdapter(emptyList()) // 빈 리스트로 어댑터 초기화
        historyAdapter.setOnItemClickListener(this) // 클릭 리스너 설정

        binding.recyclerViewHistory.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity) // 선형 레이아웃 매니저 설정
            adapter = historyAdapter // 어댑터 연결
        }
    }

    // Firebase Firestore에서 완료된 응급 호출 케이스들을 로드하는 함수
    private fun loadCompletedCases() {
        val user = auth.currentUser // 현재 로그인한 사용자 가져오기
        if (user == null) { // 사용자가 로그인되어 있지 않으면
            binding.textViewNoHistory.visibility = View.VISIBLE // 기록 없음 메시지 표시
            return // 함수 종료
        }

        // Firestore에서 'completed_cases' 컬렉션 그룹을 쿼리 (모든 하위 컬렉션에서 검색)
        db.collectionGroup("completed_cases")
            .whereEqualTo("paramedicId", user.uid) // 현재 사용자 ID와 일치하는 문서만 필터링
            .orderBy("caseCompletedAt", Query.Direction.DESCENDING) // 완료 시간 내림차순으로 정렬
            .get() // 문서 가져오기
            .addOnSuccessListener { documents -> // 문서 가져오기 성공 시
                val totalCount = documents.size() // 가져온 문서의 총 개수
                binding.textViewTotalCount.text = "총 ${totalCount}건의\n응급이 발생하였습니다." // 총 기록 건수 UI 업데이트

                if (documents.isEmpty) { // 문서가 비어있으면 (기록이 없으면)
                    binding.textViewNoHistory.visibility = View.VISIBLE // 기록 없음 메시지 표시
                    binding.buttonAnalyze.isEnabled = false // 데이터가 없으므로 분석 버튼 비활성화
                    return@addOnSuccessListener
                } else {
                    binding.buttonAnalyze.isEnabled = true // 데이터가 있으면 분석 버튼 활성화
                }

                val caseList = mutableListOf<CompletedCase>() // CompletedCase 객체를 담을 리스트
                for (doc in documents) { // 각 문서를 순회하며 CompletedCase 객체로 변환
                    val patientInfo = doc.get("patientInfo") as? Map<*, *> // 환자 정보 맵 가져오기
                    val caseData = CompletedCase( // CompletedCase 객체 생성
                        patientName = patientInfo?.get("name")?.toString() ?: "정보 없음", // 환자 이름
                        hospitalName = doc.getString("acceptedHospitalName") ?: "병원 정보 없음", // 수락한 병원 이름
                        symptom = patientInfo?.get("symptom")?.toString() ?: "정보 없음", // 주요 증상
                        createdAt = doc.getTimestamp("createdAt"), // 생성 시간
                        completedAt = doc.getTimestamp("caseCompletedAt"), // 완료 시간
                        age = patientInfo?.get("age") as? Long, // 나이 (Long 타입으로 캐스팅)
                        gender = patientInfo?.get("gender")?.toString() ?: "정보 없음", // 성별
                        otherInfo = patientInfo?.get("otherInfo")?.toString() ?: "정보 없음" // 기타 정보
                    )
                    caseList.add(caseData) // 리스트에 추가
                }

                formatCasesForLLM(caseList) // LLM 분석을 위해 케이스 데이터를 텍스트 형식으로 포맷

                // 새로 생성된 caseList로 어댑터 업데이트 및 클릭 리스너 재설정
                historyAdapter = HistoryAdapter(caseList)
                historyAdapter.setOnItemClickListener(this)
                binding.recyclerViewHistory.adapter = historyAdapter
            }
            .addOnFailureListener { exception -> // 문서 가져오기 실패 시
                Log.w("HistoryActivity", "Error getting documents: ", exception) // 오류 로그
                binding.textViewNoHistory.text = "기록을 불러오는 중 오류가 발생했습니다." // 오류 메시지
                binding.textViewNoHistory.visibility = View.VISIBLE // 오류 메시지 표시
            }
    }

    // 완료된 케이스 목록을 LLM(Large Language Model) 분석에 적합한 텍스트 형식으로 포맷하는 함수
    private fun formatCasesForLLM(cases: List<CompletedCase>) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA) // 날짜 포맷터 (한국 로케일)
        val stringBuilder = StringBuilder() // 효율적인 문자열 생성을 위한 StringBuilder

        stringBuilder.append("총 ${cases.size}건의 이송 기록 데이터입니다.\n\n") // 총 기록 건수 추가

        cases.forEachIndexed { index, case -> // 각 케이스에 대해 반복
            stringBuilder.append("[기록 ${index + 1}]\n") // 기록 번호
            stringBuilder.append("- 환자 이름: ${case.patientName}\n") // 환자 이름
            stringBuilder.append("- 나이: ${case.age ?: "정보 없음"}\n") // 나이 (정보 없으면 "정보 없음")
            stringBuilder.append("- 성별: ${case.gender}\n") // 성별
            stringBuilder.append("- 주요 증상: ${case.symptom}\n") // 주요 증상
            stringBuilder.append("- 기타 정보: ${case.otherInfo}\n") // 기타 정보
            stringBuilder.append("- 이송 병원: ${case.hospitalName}\n") // 이송 병원
            // 요청 시각과 완료 시각을 포맷하여 추가
            val requestTime = if (case.createdAt != null) sdf.format(case.createdAt.toDate()) else "정보 없음"
            val completeTime = if (case.completedAt != null) sdf.format(case.completedAt.toDate()) else "정보 없음"
            stringBuilder.append("- 요청 시각: $requestTime\n")
            stringBuilder.append("- 완료 시각: $completeTime\n")
            stringBuilder.append("---\n\n") // 각 기록 구분선
        }

        allCasesText = stringBuilder.toString() // 최종 문자열을 allCasesText에 저장
        Log.d("LLM_DATA", allCasesText) // LLM 분석용 데이터 로그 출력
    }

    // Gemini 모델을 사용하여 케이스 데이터를 분석하는 함수
    private fun analyzeCasesWithGemini() {
        binding.progressBarAnalysis.visibility = View.VISIBLE // 분석 진행률 바 표시
        binding.cardViewAnalysis.visibility = View.GONE // 기존 분석 결과 카드 뷰 숨김

        lifecycleScope.launch { // 비동기 작업을 위한 코루틴 시작
            try {
                // Gemini 모델에 보낼 프롬프트 정의
                val prompt = """
                당신은 응급 출동 기록 데이터 분석 전문가입니다.
                아래 제공되는 여러 건의 출동 기록 텍스트를 분석하여 다음 두 가지 항목에 대한 인사이트를 찾아주세요.
                1. timeAnalysis: 어떤 시간대에 주로 응급 상황이 발생하는지에 대한 분석 (예: "주로 저녁 8시 이후 야간 시간에 신고가 집중되었습니다.")
                2. symptomAnalysis: 가장 자주 발생하는 주요 증상들에 대한 분석 (예: "가슴 통증과 호흡 곤란이 가장 빈번한 증상이었으며, 그 뒤를 복통이 이었습니다.")

                응답은 반드시 아래와 같은 형식의 JSON 객체로만 제공해야 합니다. 다른 설명은 절대 추가하지 마세요.

                ```json
                {
                  "timeAnalysis": "분석된 시간대 경향성",
                  "symptomAnalysis": "분석된 주요 증상 경향성"
                }
                ```

                [분석할 데이터]
                $allCasesText
                """.trimIndent()

                val response = generativeModel.generateContent(prompt) // Gemini 모델에 프롬프트 전송 및 응답 받기

                val jsonObject = JSONObject(response.text ?: "{}") // 응답 텍스트를 JSON 객체로 파싱 (null이면 빈 객체)
                // JSON 객체에서 "timeAnalysis"와 "symptomAnalysis" 값을 추출
                val timeAnalysis = jsonObject.optString("timeAnalysis", "시간대 분석 결과를 가져올 수 없습니다.")
                val symptomAnalysis = jsonObject.optString("symptomAnalysis", "주요 증상 분석 결과를 가져올 수 없습니다.")

                // 분석 결과를 보기 좋게 포맷팅
                val formattedResult = """
                🕒 시간대 분석
                $timeAnalysis

                🩺 주요 증상 분석
                $symptomAnalysis
                """.trimIndent()

                binding.textViewAnalysisResult.text = formattedResult // 포맷된 결과를 텍스트 뷰에 표시
                binding.progressBarAnalysis.visibility = View.GONE // 진행률 바 숨김
                binding.cardViewAnalysis.visibility = View.VISIBLE // 분석 결과 카드 뷰 표시

            } catch (e: Exception) {
                Log.e("GeminiAnalysis", "API 호출 실패", e) // API 호출 실패 시 오류 로그
                binding.progressBarAnalysis.visibility = View.GONE // 진행률 바 숨김
                binding.textViewAnalysisResult.text = "오류가 발생하여 분석에 실패했습니다.\n(${e.localizedMessage})" // 오류 메시지 표시
                binding.cardViewAnalysis.visibility = View.VISIBLE // 분석 결과 카드 뷰 표시
            }
        }
    }

    // RecyclerView 아이템 클릭 시 호출되는 콜백 메서드 (HistoryAdapter.OnItemClickListener 구현)
    override fun onItemClick(case: CompletedCase) {
        val dialog = Dialog(this) // 새 Dialog 객체 생성
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE) // 다이얼로그 타이틀 바 제거
        dialog.setContentView(R.layout.dialog_case_details) // 커스텀 레이아웃 설정 (환자 상세 정보 다이얼로그)

        // 다이얼로그 내의 UI 요소들 참조 가져오기
        val tvName: TextView = dialog.findViewById(R.id.textViewDetailName)
        val tvAge: TextView = dialog.findViewById(R.id.textViewDetailAge)
        val tvGender: TextView = dialog.findViewById(R.id.textViewDetailGender)
        val tvSymptom: TextView = dialog.findViewById(R.id.textViewDetailSymptom)
        val tvOtherInfo: TextView = dialog.findViewById(R.id.textViewDetailOtherInfo)
        val btnClose: Button = dialog.findViewById(R.id.buttonClose)

        // 클릭된 CompletedCase 객체의 데이터를 가져와 텍스트 뷰에 설정
        tvName.text = "이름: ${case.patientName}"
        tvAge.text = "나이: ${case.age ?: "정보 없음"}"
        tvGender.text = "성별: ${case.gender}"
        tvSymptom.text = "주요 증상: ${case.symptom}"
        tvOtherInfo.text = "기타 정보: ${case.otherInfo}"

        // 닫기 버튼 클릭 리스너 설정
        btnClose.setOnClickListener {
            dialog.dismiss() // 다이얼로그 닫기
        }
        dialog.show() // 다이얼로그 표시
    }
}