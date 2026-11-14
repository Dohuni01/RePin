package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.SavedStatus

data class RevisitHit(
    val place: SavedPlace,
    val aheadM: Double
)

/**
 * CONFIRMED 저장 장소가 진행 방향 300m 앞, 경로에서 60m 이내면 REVISIT을 알린다.
 * 같은 곳은 하루 1번만 (engine/PROMPT.md 6절).
 */
class RevisitWatcher(
    private val aheadMaxM: Double = 300.0,
    private val lateralMaxM: Double = 60.0
) {

    // 장소(위치) 기준으로 알림 여부를 기억한다. 같은 가게를 여러 번 저장해도 하루 한 번만 (버그 수정)
    private val notifiedDayByKey = mutableMapOf<String, Long>()

    fun check(
        saved: List<SavedPlace>,
        route: Polyline,
        currentDistanceAlongM: Double,
        nowMs: Long
    ): RevisitHit? {
        val today = dayOf(nowMs)
        for (place in saved) {
            if (place.status != SavedStatus.CONFIRMED) continue
            if (notifiedDayByKey[locationKey(place)] == today) continue

            val proj = route.project(place.pos)
            if (proj.lateralM > lateralMaxM) continue
            val ahead = proj.distanceAlongM - currentDistanceAlongM
            if (ahead < 0 || ahead > aheadMaxM) continue

            notifiedDayByKey[locationKey(place)] = today
            return RevisitHit(place, ahead)
        }
        return null
    }

    /** 사용자가 알림을 닫았거나 들렀으면 그 장소는 오늘 다시 띄우지 않는다 */
    fun markHandled(place: SavedPlace, nowMs: Long) {
        notifiedDayByKey[locationKey(place)] = dayOf(nowMs)
    }

    /** 가게 식별: 선택된 가게 id가 있으면 그걸로, 없으면 좌표를 10m 격자로 묶어 키로 쓴다 */
    private fun locationKey(place: SavedPlace): String =
        place.selectedPoiId ?: "loc:${(place.pos.x / 10).toInt()}:${(place.pos.y / 10).toInt()}"

    /** 데모 리셋용 */
    fun reset() = notifiedDayByKey.clear()

    private fun dayOf(nowMs: Long): Long = nowMs / (24L * 60 * 60 * 1000)
}
