package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.view.LayoutInflater // XML 레이아웃 파일을 View 객체로 인스턴스화하는 클래스
import android.view.ViewGroup // 뷰들을 담는 컨테이너 역할을 하는 뷰의 기본 클래스
import androidx.recyclerview.widget.RecyclerView // 목록 데이터를 효율적으로 표시하는 유연한 뷰 그룹
import com.kotlinsun.deuapp.databinding.ItemHistoryCaseBinding // 뷰 바인딩을 통해 아이템 레이아웃의 뷰에 접근하는 생성된 클래스
import java.text.SimpleDateFormat // 날짜/시간 포맷을 위한 클래스
import java.util.* // 유틸리티 클래스 (Locale 등)

// HistoryAdapter 클래스 선언: RecyclerView.Adapter를 상속받고 HistoryViewHolder를 사용
// 생성자로 CompletedCase 객체의 리스트를 받습니다.
class HistoryAdapter(private val historyList: List<CompletedCase>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    // 1. 클릭 이벤트를 액티비티로 전달하기 위한 인터페이스 정의
    interface OnItemClickListener {
        fun onItemClick(case: CompletedCase) // 아이템 클릭 시 호출될 콜백 함수
    }

    private var listener: OnItemClickListener? = null // OnItemClickListener 인터페이스를 구현한 객체를 저장할 변수

    // 2. 외부(액티비티)에서 리스너를 설정하기 위한 공개 함수
    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener // 전달받은 리스너 객체를 내부 변수에 저장
    }

    // 3. 각 아이템 뷰를 보관하고 바인딩하는 ViewHolder 클래스
    // RecyclerView.ViewHolder를 상속받으며, 뷰 바인딩 객체를 생성자로 받습니다.
    inner class HistoryViewHolder(private val binding: ItemHistoryCaseBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // ViewHolder가 생성될 때 (즉, 아이템 뷰가 인플레이트될 때) 아이템 뷰 전체에 클릭 리스너를 설정
            binding.root.setOnClickListener {
                val position = adapterPosition // 클릭된 아이템의 현재 어댑터 위치 가져오기
                // 위치가 유효한지 (즉, RecyclerView.NO_POSITION이 아닌지) 확인
                if (position != RecyclerView.NO_POSITION) {
                    // 설정된 리스너가 있다면, 클릭된 아이템의 CompletedCase 데이터를 전달하여 onItemClick 콜백 함수 호출
                    listener?.onItemClick(historyList[position])
                }
            }
        }

        // CompletedCase 데이터를 받아서 뷰에 연결(바인딩)하는 함수
        fun bind(case: CompletedCase) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) // 날짜와 시간을 "yyyy-MM-dd HH:mm" 형식으로 포맷하기 위한 SimpleDateFormat 객체 (한국 로케일)

            binding.textViewPatientName.text = "환자 이름: ${case.patientName}" // 환자 이름을 텍스트 뷰에 설정
            binding.textViewSymptom.text = "주요 증상: ${case.symptom}" // 주요 증상을 텍스트 뷰에 설정
            binding.textViewHospitalName.text = "이송 병원: ${case.hospitalName}" // 이송 병원 이름을 텍스트 뷰에 설정

            // 요청 시각이 null이 아니면 포맷하여 표시, null이면 "정보 없음"으로 표시
            binding.textViewCreatedAt.text = if (case.createdAt != null) {
                "요청 시각: ${sdf.format(case.createdAt.toDate())}"
            } else {
                "요청 시각 정보 없음"
            }

            // 완료 시각이 null이 아니면 포맷하여 표시, null이면 "정보 없음"으로 표시
            binding.textViewCompletedAt.text = if (case.completedAt != null) {
                "완료 시각: ${sdf.format(case.completedAt.toDate())}"
            } else {
                "완료 시각 정보 없음"
            }
        }
    }

    // --- RecyclerView.Adapter 필수 구현 메소드 ---

    // ViewHolder 객체를 생성할 때 호출되는 콜백 메소드
    // 새로운 아이템 뷰를 만들고 해당 뷰를 위한 ViewHolder를 반환합니다.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        // ItemHistoryCaseBinding을 사용하여 레이아웃 XML 파일을 인플레이트하고 바인딩 객체 생성
        val binding = ItemHistoryCaseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding) // 새로 생성된 ViewHolder 반환
    }

    // ViewHolder가 데이터를 표시할 때 호출되는 콜백 메소드
    // 특정 위치(position)의 데이터를 가져와 ViewHolder의 뷰에 바인딩합니다.
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position]) // 현재 위치의 CompletedCase 데이터를 ViewHolder의 bind 함수에 전달
    }

    // 어댑터가 관리하는 전체 아이템의 개수를 반환하는 메소드
    override fun getItemCount(): Int {
        return historyList.size // historyList의 크기 반환
    }
}