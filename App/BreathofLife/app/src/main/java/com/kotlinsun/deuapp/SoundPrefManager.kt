package com.kotlinsun.deuapp // 코틀린 패키지 선언

import android.content.Context // 안드로이드 애플리케이션 환경 정보에 접근하기 위한 Context 클래스
import android.content.SharedPreferences // 간단한 키-값 쌍 데이터를 저장하고 불러오는 데 사용되는 인터페이스

// 싱글톤 객체로 SoundPrefManager를 선언합니다.
// 이 객체는 알림 사운드 설정을 SharedPreferences에 저장하고 불러오는 역할을 합니다.
object SoundPrefManager {

    private const val PREFERENCES_NAME = "SoundPrefs" // SharedPreferences 파일의 이름
    private const val KEY_SOUND_RESOURCE_ID = "sound_resource_id" // 사운드 리소스 ID를 저장할 때 사용할 키

    // SharedPreferences 인스턴스를 가져오는 비공개 함수
    // @param context 컨텍스트 (애플리케이션의 컨텍스트를 사용하여 SharedPreferences에 접근)
    // @return 지정된 이름의 SharedPreferences 인스턴스
    private fun getPreferences(context: Context): SharedPreferences {
        // Context.MODE_PRIVATE: 이 SharedPreferences 파일은 호출하는 애플리케이션만 접근할 수 있습니다.
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 선택된 사운드의 리소스 ID를 SharedPreferences에 저장하는 함수입니다.
     *
     * @param context 컨텍스트 (현재 애플리케이션의 컨텍스트)
     * @param resourceId 저장할 사운드의 리소스 ID (예: `R.raw.notisound`, `R.raw.alert` 등)
     */
    fun saveSoundPreference(context: Context, resourceId: Int) {
        val editor = getPreferences(context).edit() // SharedPreferences를 수정하기 위한 Editor 객체를 가져옵니다.
        editor.putInt(KEY_SOUND_RESOURCE_ID, resourceId) // 지정된 키에 사운드 리소스 ID를 Int 형태로 저장합니다.
        editor.apply() // 변경 사항을 비동기적으로(백그라운드에서) 커밋합니다.
    }

    /**
     * SharedPreferences에 저장된 사운드의 리소스 ID를 불러오는 함수입니다.
     *
     * @param context 컨텍스트 (현재 애플리케이션의 컨텍스트)
     * @return 저장된 사운드 리소스 ID를 반환합니다. 만약 해당 키로 저장된 값이 없으면
     * 기본값인 `R.raw.notisound`를 반환합니다.
     */
    fun getSoundPreference(context: Context): Int {
        // getInt(key, defaultValue)를 사용하여 저장된 값을 가져옵니다.
        // 저장된 값이 없을 경우 `R.raw.notisound`를 기본값으로 반환합니다.
        return getPreferences(context).getInt(KEY_SOUND_RESOURCE_ID, R.raw.notisound)
    }
}