package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.TurnDirection
import com.akkageugot.demo.contract.TurnPoint

/**
 * 경로 꼭짓점에서 회전 지점을 찾는다. 진행 방향이 임계각 이상 꺾이면 회전으로 본다.
 */
object RouteAnalyzer {

    fun detectTurns(route: Polyline, thresholdDeg: Double = 30.0): List<TurnPoint> {
        val pts = route.points
        if (pts.size < 3) return emptyList()
        val thresholdRad = Math.toRadians(thresholdDeg)
        val turns = mutableListOf<TurnPoint>()
        for (i in 1 until pts.size - 1) {
            val before = Geo.heading(pts[i - 1], pts[i])
            val after = Geo.heading(pts[i], pts[i + 1])
            val delta = Geo.normalizeAngle(after - before)
            if (kotlin.math.abs(delta) >= thresholdRad) {
                turns += TurnPoint(
                    pos = pts[i],
                    distanceAlongRouteM = route.cumulative[i],
                    // 수학 좌표계: 반시계(+)가 좌회전
                    direction = if (delta > 0) TurnDirection.LEFT else TurnDirection.RIGHT
                )
            }
        }
        return turns
    }
}
