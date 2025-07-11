package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.view.LayoutInflater // XML 레이아웃 파일을 View 객체로 인스턴스화하는 클래스
import android.view.View // 안드로이드 UI 구성 요소의 기본 클래스
import android.view.ViewGroup // 뷰들을 담는 컨테이너 역할을 하는 뷰의 기본 클래스
import androidx.recyclerview.widget.RecyclerView // 목록 데이터를 효율적으로 표시하는 유연한 뷰 그룹
import com.kotlinsun.deuapp.databinding.ItemHospitalBinding // 뷰 바인딩을 통해 아이템 레이아웃의 뷰에 접근하는 생성된 클래스

// HospitalAdapter 클래스 선언: RecyclerView.Adapter를 상속받고 HospitalViewHolder를 사용
// 생성자로 Hospital 객체의 리스트를 받습니다.
class HospitalAdapter(private var hospitalList: List<Hospital>) : RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder>() {

    // 각 아이템 뷰를 보관하고 데이터를 바인딩하는 ViewHolder 클래스
    // RecyclerView.ViewHolder를 상속받으며, 뷰 바인딩 객체를 생성자로 받습니다.
    inner class HospitalViewHolder(private val binding: ItemHospitalBinding) : RecyclerView.ViewHolder(binding.root) {
        // Hospital 데이터를 받아서 뷰에 연결(바인딩)하는 함수
        fun bind(hospital: Hospital) {
            binding.tvHospitalName.text = hospital.hospitalName // 병원 이름을 텍스트 뷰에 설정
            binding.tvHospitalAddress.text = hospital.fullAddress // 병원 전체 주소를 텍스트 뷰에 설정
            binding.tvAvailableBeds.text = hospital.availableBeds.toString() // 사용 가능한 침대 수를 텍스트 뷰에 설정
            binding.tvTotalBeds.text = hospital.totalBeds.toString() // 전체 침대 수를 텍스트 뷰에 설정

            // 병원 거리가 존재하면 표시하고, 없으면 숨김
            hospital.distanceInKm?.let { distance ->
                binding.tvDistance.visibility = View.VISIBLE // 거리 텍스트 뷰를 보이게 함
                binding.tvDistance.text = String.format("약 %.1fkm", distance) // 거리를 소수점 첫째 자리까지 포맷하여 표시
            } ?: run {
                binding.tvDistance.visibility = View.GONE // 거리 텍스트 뷰를 숨김
            }

            // 진료 가능 과목 목록이 비어있지 않으면 표시하고, 없으면 숨김
            if (hospital.availableDepartments.isNotEmpty()) {
                binding.tvAvailableDepartments.visibility = View.VISIBLE // 진료 가능 과목 텍스트 뷰를 보이게 함
                // 진료 가능 과목들을 쉼표로 연결하여 텍스트 뷰에 설정
                binding.tvAvailableDepartments.text = "진료 가능: " + hospital.availableDepartments.joinToString(", ")
            } else {
                binding.tvAvailableDepartments.visibility = View.GONE // 진료 가능 과목 텍스트 뷰를 숨김
            }
        }
    }

    // --- RecyclerView.Adapter 필수 구현 메소드 ---

    // ViewHolder 객체를 생성할 때 호출되는 콜백 메소드
    // 새로운 아이템 뷰를 만들고 해당 뷰를 위한 ViewHolder를 반환합니다.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        // ItemHospitalBinding을 사용하여 레이아웃 XML 파일을 인플레이트하고 바인딩 객체 생성
        val binding = ItemHospitalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HospitalViewHolder(binding) // 새로 생성된 ViewHolder 반환
    }

    // ViewHolder가 데이터를 표시할 때 호출되는 콜백 메소드
    // 특정 위치(position)의 데이터를 가져와 ViewHolder의 뷰에 바인딩합니다.
    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        holder.bind(hospitalList[position]) // 현재 위치의 Hospital 데이터를 ViewHolder의 bind 함수에 전달
    }

    // 어댑터가 관리하는 전체 아이템의 개수를 반환하는 메소드
    override fun getItemCount(): Int = hospitalList.size // hospitalList의 크기 반환 (단일 표현식 함수)

    // RecyclerView의 데이터를 업데이트하고 UI를 갱신하는 함수
    fun updateData(newList: List<Hospital>) {
        hospitalList = newList // 새로운 리스트로 데이터 업데이트
        notifyDataSetChanged() // 어댑터에 데이터가 변경되었음을 알리고 UI 갱신 요청
    }
}