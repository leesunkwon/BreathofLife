package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.Manifest // Android 권한 관련 클래스 (위치 권한 등)
import android.annotation.SuppressLint // Lint 경고 억제를 위한 어노테이션
import android.content.pm.PackageManager // 패키지 관리 및 권한 확인을 위한 클래스
import android.location.Location // GPS 등에서 얻은 위치 정보를 담는 클래스
import android.os.Bundle // 액티비티 상태 저장을 위한 Bundle 클래스
import android.util.Log // 로그 출력을 위한 유틸리티 클래스
import android.view.View // 안드로이드 UI 구성 요소의 기본 클래스
import androidx.activity.result.contract.ActivityResultContracts // 액티비티 결과를 처리하기 위한 계약 클래스
import androidx.appcompat.app.AppCompatActivity // 안드로이드 호환성 액티비티
import androidx.appcompat.widget.SearchView // 검색 기능을 위한 위젯
import androidx.core.content.ContextCompat // 리소스 (예: 색상, 드로어블) 및 권한 상태를 가져오는 유틸리티 클래스
import androidx.recyclerview.widget.LinearLayoutManager // RecyclerView에 아이템을 선형으로 배치하는 레이아웃 매니저
import com.google.android.gms.location.LocationServices // Google Location Service 클라이언트
import com.google.android.gms.location.Priority // 위치 요청 우선순위 설정
import com.google.android.material.chip.Chip // Material Design Chip (필터링 등에 사용)
import com.google.firebase.firestore.ktx.firestore // Firebase Firestore KTX 확장 (Firestore 인스턴스 가져오기)
import com.google.firebase.ktx.Firebase // Firebase KTX 확장 (Firebase 초기화 및 서비스 접근)
import com.kotlinsun.deuapp.databinding.ActivityFindBinding // 뷰 바인딩 클래스

// FindActivity 클래스 선언: AppCompatActivity를 상속
class FindActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFindBinding // UI 요소에 접근하기 위한 뷰 바인딩 인스턴스
    private lateinit var hospitalAdapter: HospitalAdapter // 병원 목록을 표시할 RecyclerView 어댑터
    private val db = Firebase.firestore // Firebase Firestore 데이터베이스 인스턴스
    private val TAG = "FindActivity" // 로그캣에 사용할 태그

    private var fullHospitalList: List<Hospital> = listOf() // Firestore에서 불러온 전체 병원 목록
    private var currentSearchText = "" // 현재 검색창에 입력된 텍스트
    private var selectedDepartment: String? = null // 현재 선택된 진료과 필터 (null이면 전체)
    private var currentUserLocation: Location? = null // 현재 사용자 위치 정보

    // 위치 권한 요청 결과를 처리하는 ActivityResultLauncher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions() // 여러 권한을 한 번에 요청
    ) { permissions ->
        // ACCESS_FINE_LOCATION 권한이 허용되었는지 확인
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            getCurrentLocation() // 권한이 있으면 현재 위치 가져오기 시작
        } else {
            Log.d(TAG, "Location permission denied.") // 위치 권한 거부 시 로그
            loadHospitals() // 위치 권한 없이 병원 목록 로드
        }
    }

    // 미리 정의된 진료과 목록
    private val departmentList = listOf(
        "가정의학과", "내과", "마취통증의학과", "병리과", "비뇨의학과", "산부인과",
        "성형외과", "소아청소년과", "신경외과", "안과", "영상의학과",
        "이비인후과", "재활의학과", "정신건강의학과", "정형외과", "직업환경의학과",
        "진단검사의학과", "피부과", "핵의학과", "흉부외과"
    )

    // 액티비티가 처음 생성될 때 호출되는 콜백 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFindBinding.inflate(layoutInflater) // 뷰 바인딩 인스턴스 초기화
        setContentView(binding.root) // 액티비티 레이아웃 설정

        setupRecyclerView() // RecyclerView 설정
        setupDepartmentChips() // 진료과 칩들 설정
        setupListeners() // 리스너들 설정 (검색, 칩 클릭 등)

        showLoading(true) // 데이터 로딩 시작 전에 로딩 UI 표시
        checkLocationPermissionAndGetLocation() // 위치 권한 확인 후 위치 가져오기 또는 병원 로드

        // 뒤로가기 버튼 클릭 리스너 설정
        binding.backBtnFind.setOnClickListener {
            finish() // 현재 액티비티 종료
        }
    }

    // RecyclerView를 초기 설정하는 함수
    private fun setupRecyclerView() {
        hospitalAdapter = HospitalAdapter(emptyList()) // 어댑터 초기화 (빈 리스트로 시작)
        binding.recyclerViewHospitals.apply {
            layoutManager = LinearLayoutManager(this@FindActivity) // 선형 레이아웃 매니저 설정
            adapter = hospitalAdapter // 어댑터 연결
        }
    }

    // 진료과 필터링을 위한 Chip들을 동적으로 생성하고 추가하는 함수
    private fun setupDepartmentChips() {
        departmentList.forEach { departmentName -> // 각 진료과 이름에 대해 반복
            val chip = Chip(this).apply {
                text = departmentName // 칩의 텍스트를 진료과 이름으로 설정
                isCheckable = true // 칩을 선택 가능하도록 설정
            }
            binding.chipGroupDepartments.addView(chip) // ChipGroup에 칩 추가
        }
    }

    // UI 요소들의 리스너를 설정하는 함수
    private fun setupListeners() {
        // 검색 뷰의 텍스트 변경 리스너 설정
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false // 검색 제출 시 동작 (여기서는 사용 안 함)
            override fun onQueryTextChange(newText: String?): Boolean { // 검색 텍스트 변경 시 호출
                currentSearchText = newText.orEmpty() // 현재 검색 텍스트 업데이트 (null이면 빈 문자열)
                filterAndSearchList() // 목록 필터링 및 검색 재실행
                return true
            }
        })

        // 칩 그룹의 체크 변경 리스너 설정
        binding.chipGroupDepartments.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == View.NO_ID) { // 선택된 칩이 없을 경우
                selectedDepartment = null // 선택된 진료과 필터 초기화
            } else {
                val clickedChip = group.findViewById<Chip>(checkedId) // 클릭된 칩 가져오기
                // 클릭된 칩이 'chipAll' (가상의 전체 칩)이면 필터 초기화, 아니면 해당 칩 텍스트로 필터 설정
                selectedDepartment = if (clickedChip.id == R.id.chipAll) null else clickedChip.text.toString()
            }
            filterAndSearchList() // 필터링 및 검색 재실행
        }
    }

    // 위치 권한을 확인하고, 권한이 있으면 현재 위치를 가져오는 함수
    private fun checkLocationPermissionAndGetLocation() {
        // ACCESS_FINE_LOCATION 권한이 부여되었는지 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation() // 권한이 있으면 현재 위치 가져오기 시작
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) // 권한이 없으면 권한 요청
        }
    }

    // 현재 위치를 가져오는 함수 (권한이 이미 있다고 가정)
    @SuppressLint("MissingPermission") // Lint 경고: 권한 검사 없이 위치 API를 사용하고 있음을 무시
    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) // FusedLocationProviderClient 인스턴스 가져오기
        // 현재 위치를 높은 정확도로 한 번만 가져오도록 요청
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? -> // 위치 정보 가져오기 성공 시
                if (location != null) {
                    this.currentUserLocation = location // 현재 사용자 위치 업데이트
                }
                loadHospitals() // 위치 정보 업데이트 후 병원 목록 로드
            }
            .addOnFailureListener { // 위치 정보 가져오기 실패 시
                // 실패해도 병원 목록은 로드
                loadHospitals()
            }
    }

    // Firebase Firestore에서 병원 목록을 로드하는 함수
    private fun loadHospitals() {
        db.collection("hospitals") // "hospitals" 컬렉션 참조
            .get() // 모든 문서 가져오기
            .addOnSuccessListener { result -> // 문서 가져오기 성공 시
                fullHospitalList = result.toObjects(Hospital::class.java) // 문서들을 Hospital 객체 리스트로 변환
                filterAndSearchList() // 로드된 전체 목록으로 필터링 및 검색 실행
            }
            .addOnFailureListener { exception -> // 문서 가져오기 실패 시
                Log.w(TAG, "Error getting documents.", exception) // 오류 로그 출력
                showError("병원 목록을 불러오는 데 실패했습니다.") // 사용자에게 오류 메시지 표시
            }
    }

    // 전체 병원 목록을 현재 검색 텍스트와 선택된 진료과 필터에 따라 필터링하고 검색하는 함수
    private fun filterAndSearchList() {
        var filteredList = fullHospitalList // 전체 목록으로 시작

        // 진료과 필터가 선택되어 있으면 해당 진료과를 포함하는 병원만 필터링
        selectedDepartment?.let { department ->
            filteredList = filteredList.filter { hospital -> hospital.availableDepartments.contains(department) }
        }

        // 검색 텍스트가 비어있지 않으면 병원 이름에 검색 텍스트를 포함하는 병원만 필터링
        if (currentSearchText.isNotEmpty()) {
            filteredList = filteredList.filter { hospital -> hospital.hospitalName.contains(currentSearchText, ignoreCase = true) } // 대소문자 무시
        }

        // 현재 사용자 위치가 있다면, 각 병원과의 거리를 계산하여 정렬
        currentUserLocation?.let { userLocation ->
            filteredList.forEach { hospital -> // 필터링된 각 병원에 대해 반복
                hospital.location?.let { hospitalGeoPoint -> // 병원 위치 정보가 있다면
                    val results = FloatArray(1) // 거리 계산 결과를 저장할 배열
                    // 사용자 위치와 병원 위치 사이의 거리 계산 (미터 단위)
                    Location.distanceBetween(
                        userLocation.latitude, userLocation.longitude,
                        hospitalGeoPoint.latitude, hospitalGeoPoint.longitude,
                        results
                    )
                    hospital.distanceInKm = results[0] / 1000f // 미터를 킬로미터로 변환하여 Hospital 객체에 저장
                }
            }
            // 계산된 거리를 기준으로 오름차순 정렬 (거리가 없는 병원은 가장 뒤로 보냄)
            filteredList = filteredList.sortedWith(compareBy { it.distanceInKm ?: Float.MAX_VALUE })
        }

        hospitalAdapter.updateData(filteredList) // 필터링 및 정렬된 목록으로 어댑터 데이터 업데이트
        showLoading(false) // 모든 데이터 처리 완료 후 로딩 UI 숨기기
    }

    // 로딩 UI의 가시성을 제어하는 함수
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE // 로딩 바 가시성 설정
        binding.recyclerViewHospitals.visibility = if (isLoading) View.GONE else View.VISIBLE // RecyclerView 가시성 설정
        binding.textViewError.visibility = View.GONE // 에러 텍스트 뷰는 항상 숨김
    }

    // 오류 메시지를 표시하는 함수
    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE // 로딩 바 숨김
        binding.recyclerViewHospitals.visibility = View.GONE // RecyclerView 숨김
        binding.textViewError.visibility = View.VISIBLE // 에러 텍스트 뷰 표시
        binding.textViewError.text = message // 에러 메시지 설정
    }
}