package com.akkageugot.demo.engine.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.akkageugot.demo.contract.EngineProvider
import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.UiAction

/**
 * 발표용 adb 명령 수신기 (engine/PROMPT.md 8절).
 * 한글은 adb로 보내지 않고 번호(preset)로 받는다 (Windows cmd 인코딩 문제).
 *
 * 예:
 *   adb shell am broadcast -n com.akkageugot.demo/com.akkageugot.demo.engine.demo.DemoCommandReceiver \
 *     -a com.akkageugot.demo.DEMO --es cmd say --ei preset 1
 */
class DemoCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val cmd = intent.getStringExtra("cmd")) {
            "say" -> handleSay(intent.getIntExtra("preset", -1))
            "scenario" -> dispatch(UiAction.RunScenario(intent.getIntExtra("n", -1)))
            "gear" -> handleGear(intent.getStringExtra("value"))
            "speed" -> dispatch(UiAction.SetTargetSpeed(intent.getIntExtra("kmh", 50)))
            "timescale" -> dispatch(UiAction.SetTimeScale(intent.getIntExtra("scale", 1)))
            else -> Log.w(TAG, "알 수 없는 cmd=$cmd")
        }
    }

    private fun handleSay(preset: Int) {
        val text = PRESETS.getOrNull(preset - 1)
        if (text == null) {
            Log.w(TAG, "잘못된 preset=$preset (1~${PRESETS.size})")
            return
        }
        // MicPressed → TypedUtterance → MicReleased 와 똑같은 경로 (t0 = 명령 받은 순간)
        dispatch(UiAction.MicPressed)
        dispatch(UiAction.TypedUtterance(text))
        dispatch(UiAction.MicReleased)
        Log.i(TAG, "say preset $preset: $text")
    }

    private fun handleGear(value: String?) {
        val gear = when (value?.uppercase()) {
            "P" -> Gear.P
            "D" -> Gear.D
            else -> { Log.w(TAG, "gear value는 P/D만: $value"); return }
        }
        dispatch(UiAction.SetGear(gear))
    }

    private fun dispatch(action: UiAction) = EngineProvider.engine.dispatch(action)

    companion object {
        const val TAG = "AKKA"

        /** engine/PROMPT.md 8절 프리셋 번호 */
        val PRESETS = listOf(
            "방금 오른쪽에 있던 빵집 뭐야?",   // 1
            "저장해",                          // 2
            "들렀다 가자",                     // 3
            "아니 그 옆에",                    // 4
            "아까 3분 전에 지나친 카페 뭐였지?", // 5
            "방금 거 뭐야?"                    // 6
        )
    }
}
