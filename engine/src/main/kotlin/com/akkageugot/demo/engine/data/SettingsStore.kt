package com.akkageugot.demo.engine.data

import android.content.Context
import com.akkageugot.demo.contract.EngineSettings

/**
 * 엔진 설정 영속화 (engine/PROMPT.md 7절: SDK 선택은 저장해서 재시작해도 유지).
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("akka_settings", Context.MODE_PRIVATE)
    private val sdkKeys = listOf("STT", "TTS", "NAVI", "VEHICLE", "UX")

    fun load(): EngineSettings {
        val base = EngineSettings()
        val useSdk = sdkKeys.filter { prefs.contains("sdk_$it") }
            .associateWith { prefs.getBoolean("sdk_$it", true) }
        return base.copy(
            targetSpeedKmh = prefs.getInt("targetSpeedKmh", base.targetSpeedKmh),
            timeScale = prefs.getInt("timeScale", base.timeScale),
            useSdk = useSdk,
            mirrorToRealNavi = prefs.getBoolean("mirrorToRealNavi", base.mirrorToRealNavi)
        )
    }

    fun save(settings: EngineSettings) {
        prefs.edit().apply {
            putInt("targetSpeedKmh", settings.targetSpeedKmh)
            putInt("timeScale", settings.timeScale)
            putBoolean("mirrorToRealNavi", settings.mirrorToRealNavi)
            settings.useSdk.forEach { (k, v) -> putBoolean("sdk_$k", v) }
            apply()
        }
    }
}
