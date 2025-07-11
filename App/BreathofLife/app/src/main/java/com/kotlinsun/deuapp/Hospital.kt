package com.kotlinsun.deuapp // 코틀린 패키지 선언

import com.google.firebase.Timestamp // Firebase Timestamp 타입 (날짜 및 시간 정보를 저장하는 데 사용)
import com.google.firebase.firestore.DocumentId // Firestore 문서 ID 필드를 지정하는 어노테이션
import com.google.firebase.firestore.Exclude // Firestore 직렬화/역직렬화에서 필드를 제외하는 어노테이션
import com.google.firebase.firestore.GeoPoint // Firestore에서 지리적 위치(위도, 경도)를 나타내는 타입
import com.google.firebase.firestore.IgnoreExtraProperties // Firestore에서 문서에 정의되지 않은 필드를 무시하는 어노테이션

/**
 * [IgnoreExtraProperties] 어노테이션은 Firestore 문서에 이 데이터 클래스에서 정의되지 않은 필드가 있을 경우,
 * 해당 필드를 무시하고 역직렬화 오류를 발생시키지 않도록 합니다.
 *
 * 병원의 침대 가용성 정보를 나타내는 데이터 클래스입니다.
 */
@IgnoreExtraProperties
data class Beds(
    val available: Long = 0L, // 현재 사용 가능한 침대 수 (Long 타입, 기본값 0L)
    val total: Long = 0L, // 전체 침대 수 (Long 타입, 기본값 0L)
    val lastUpdated: Timestamp? = null // 침대 정보가 마지막으로 업데이트된 시간 (Timestamp 타입, null 허용)
)




@IgnoreExtraProperties
data class Hospital(
    @DocumentId val id: String = "", // Firestore 문서의 ID를 이 필드에 자동으로 매핑 (기본값 빈 문자열)
    val hospitalName: String = "", // 병원 이름 (기본값 빈 문자열)
    val address: Map<String, String> = emptyMap(), // 병원 주소 정보 (맵 형태로 저장, 예: "full", "city", "street" 등)
    val beds: Beds? = null, // 병원의 침대 정보 (Beds 데이터 클래스, null 허용)
    val availableDepartments: List<String> = emptyList(), // 병원에서 진료 가능한 과 목록 (문자열 리스트, 기본값 빈 리스트)
    val location: GeoPoint? = null // 병원의 지리적 위치 (GeoPoint 타입, null 허용)
) {
    /**
     * [Exclude] 어노테이션은 이 필드가 Firestore에 직렬화되거나 Firestore에서 역직렬화될 때 제외되도록 합니다.
     * 이 필드는 앱 내부에서만 사용되는 임시 거리 계산 값을 저장합니다.
     * `get`과 `set` 모두에 적용하여 이 필드가 Firestore 문서에 포함되지 않도록 합니다.
     */
    @get:Exclude
    @set:Exclude
    var distanceInKm: Float? = null // 현재 사용자 위치로부터의 병원 거리 (킬로미터 단위, null 허용)

    /**
     * 병원의 전체 주소를 반환하는 getter 프로퍼티입니다.
     * 'address' 맵에서 "full" 키에 해당하는 값을 가져오거나, 없으면 "주소 정보 없음"을 반환합니다.
     */
    val fullAddress: String
        get() = address["full"] ?: "주소 정보 없음"

    /**
     * 사용 가능한 침대 수를 반환하는 getter 프로퍼티입니다.
     * 'beds' 객체가 null이 아니면 'available' 값을 반환하고, null이면 0L을 반환합니다.
     */
    val availableBeds: Long
        get() = beds?.available ?: 0L

    /**
     * 전체 침대 수를 반환하는 getter 프로퍼티입니다.
     * 'beds' 객체가 null이 아니면 'total' 값을 반환하고, null이면 0L을 반환합니다.
     */
    val totalBeds: Long
        get() = beds?.total ?: 0L
}