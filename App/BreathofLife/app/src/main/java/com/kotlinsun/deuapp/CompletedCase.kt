package com.kotlinsun.deuapp // 코틀린 패키지 선언

import com.google.firebase.Timestamp // Firebase Timestamp 타입 (Firestore에서 날짜/시간을 다룰 때 사용)
import java.io.Serializable // 객체를 직렬화하여 Intent 등으로 전달할 수 있게 하는 인터페이스

// [수정] Serializable 인터페이스 구현
// 이 데이터 클래스는 완료된 응급 호출 사례의 정보를 담기 위해 사용됩니다.
// Serializable을 구현하여 이 객체를 Intent에 담아 다른 액티비티로 전달할 수 있습니다.
data class CompletedCase(
    val patientName: String = "", // 환자의 이름 (기본값 빈 문자열)
    val hospitalName: String = "", // 환자를 수락한 병원의 이름 (기본값 빈 문자열)
    val symptom: String = "", // 환자의 주요 증상 (기본값 빈 문자열)
    val createdAt: Timestamp? = null, // 응급 호출이 생성된 시간 (Timestamp 타입, null 허용)
    val completedAt: Timestamp? = null, // 응급 호출이 완료된 시간 (Timestamp 타입, null 허용)
    // [추가] 상세 정보를 위한 필드들
    val age: Long? = 0, // 환자의 나이 (Firestore의 숫자 타입은 Long으로 받는 것이 안전하며, null 허용, 기본값 0)
    val gender: String = "", // 환자의 성별 (기본값 빈 문자열)
    val otherInfo: String = "" // 환자의 기타 특이사항 (기본값 빈 문자열)
) : Serializable // 객체를 Intent에 담아 전달하기 위해 Serializable 인터페이스를 구현합니다.