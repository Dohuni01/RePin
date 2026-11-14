package com.akkageugot.demo.engine.core

import java.util.Calendar

/** 시각과 ETA 문자열 유틸. 테스트에서 now를 주입할 수 있게 분리 */
object EngineClock {

    /** 남은 거리와 평균 속도로 "오후 3:42 도착" 형식 ETA 생성 */
    fun etaText(remainingM: Double, cruiseKmh: Int, nowMs: Long): String {
        val cruiseMps = (cruiseKmh / 3.6).coerceAtLeast(1.0)
        val etaMs = nowMs + (remainingM / cruiseMps * 1000).toLong()
        val cal = Calendar.getInstance().apply { timeInMillis = etaMs }
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val ampm = if (hour24 < 12) "오전" else "오후"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "%s %d:%02d 도착".format(ampm, hour12, minute)
    }

    /** "오전 8:12 · 주행" 같은 저장 라벨 */
    fun savedLabel(nowMs: Long, suffix: String = "주행"): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val ampm = if (hour24 < 12) "오전" else "오후"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "%s %d:%02d · %s".format(ampm, hour12, minute, suffix)
    }
}
