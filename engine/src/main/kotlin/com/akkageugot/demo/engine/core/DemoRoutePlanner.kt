package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.Vec2

data class PlannedRoute(
    val route: List<Vec2>,
    /** 새 경로 기준 경유지(가게 앞 도로 지점)까지의 거리 */
    val waypointDistanceAlongM: Double,
    /** 경유로 늘어난 거리(m). ETA 지연 계산용 */
    val extraDistanceM: Double
)

/**
 * 데모 내비 경로 계산 (engine/PROMPT.md 6절).
 * 현재 위치 → 가게 앞 도로 지점 → 원래 경로 합류점(가게 지나 200m)으로 새 경로를 만든다.
 * 단순 직선 연결을 허용한다.
 */
class DemoRoutePlanner(originalRoutePoints: List<Vec2>) {

    val originalRoute = Polyline(originalRoutePoints)

    /**
     * 경유지 경로. currentDistanceAlongM은 원래 경로 기준 현재 위치다.
     */
    fun routeWithWaypoint(currentDistanceAlongM: Double, poi: Poi): PlannedRoute {
        val poiProj = originalRoute.project(poi.pos)
        val roadPoint = poiProj.point // 가게 앞 도로 지점
        val rejoinDistance = (poiProj.distanceAlongM + REJOIN_AFTER_M)
            .coerceAtMost(originalRoute.lengthM)
        val rejoinPoint = originalRoute.pointAt(rejoinDistance)
        val currentPoint = originalRoute.pointAt(currentDistanceAlongM)

        val newPoints = mutableListOf(currentPoint)
        if (Geo.distance(currentPoint, roadPoint) > 1.0) newPoints += roadPoint
        if (Geo.distance(roadPoint, rejoinPoint) > 1.0) newPoints += rejoinPoint
        // 합류점부터 원래 경로의 남은 부분을 따라간다
        for (i in originalRoute.points.indices) {
            if (originalRoute.cumulative[i] > rejoinDistance) newPoints += originalRoute.points[i]
        }

        val newRoute = Polyline(newPoints)
        val remainingOriginalM = originalRoute.lengthM - currentDistanceAlongM
        val waypointAlong = Geo.distance(currentPoint, roadPoint)
        return PlannedRoute(
            route = newPoints,
            waypointDistanceAlongM = waypointAlong,
            extraDistanceM = (newRoute.lengthM - remainingOriginalM).coerceAtLeast(0.0)
        )
    }

    /** 정차 중 저장 목록에서 고른 장소로 가는 단순 경로 */
    fun routeToPlace(currentPos: Vec2, target: Vec2): List<Vec2> = listOf(currentPos, target)

    companion object {
        const val REJOIN_AFTER_M = 200.0
        /** 가게 20m 이내 도착 시 경유지 해제 (RealDemoEngine에서 사용) */
        const val WAYPOINT_ARRIVE_M = 20.0
    }
}
