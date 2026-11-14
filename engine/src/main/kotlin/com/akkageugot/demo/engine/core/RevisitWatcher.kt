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

    private val notifiedDayByPlaceId = mutableMapOf<String, Long>()

    fun check(
        saved: List<SavedPlace>,
        route: Polyline,
        currentDistanceAlongM: Double,
        nowMs: Long
    ): RevisitHit? {
        val today = dayOf(nowMs)
        for (place in saved) {
            if (place.status != SavedStatus.CONFIRMED) continue
            if (notifiedDayByPlaceId[place.id] == today) continue

            val proj = route.project(place.pos)
            if (proj.lateralM > lateralMaxM) continue
            val ahead = proj.distanceAlongM - currentDistanceAlongM
            if (ahead < 0 || ahead > aheadMaxM) continue

            notifiedDayByPlaceId[place.id] = today
            return RevisitHit(place, ahead)
        }
        return null
    }

    /** 데모 리셋용 */
    fun reset() = notifiedDayByPlaceId.clear()

    private fun dayOf(nowMs: Long): Long = nowMs / (24L * 60 * 60 * 1000)
}
