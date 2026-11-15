package com.akkageugot.demo.engine.sim

import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.TurnPoint
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.engine.core.Geo
import com.akkageugot.demo.engine.core.Polyline
import com.akkageugot.demo.engine.core.RouteAnalyzer

/**
 * 에뮬레이터에선 차가 움직이지 않으므로 주행을 앱 안에서 시뮬레이션한다
 * (engine/PROMPT.md 7절). 순수 Kotlin이라 틱 루프 없이 단위 테스트할 수 있다.
 *
 * - targetSpeedKmh로 주행, 회전 지점 50m 전부터 20km/h로 감속 후 다시 가속
 * - timeScale 배속
 * - route가 바뀌면 현재 위치를 새 경로에 투영해 이어 달린다
 * - 목적지 도착 시 gear = P
 */
class SimulatedDrive(initialRoute: List<Vec2>) {

    var route: Polyline = Polyline(initialRoute)
        private set
    var turns: List<TurnPoint> = RouteAnalyzer.detectTurns(route)
        private set

    var distanceAlongM: Double = 0.0
        private set
    var speedMps: Double = 0.0
        private set
    var gear: Gear = Gear.P

    var targetSpeedKmh: Int = 50
    var timeScale: Int = 1

    val pos: Vec2 get() = route.pointAt(distanceAlongM)
    val headingRad: Double get() = if (route.points.size >= 2) route.headingAt(distanceAlongM) else 0.0
    val speedKmh: Double get() = speedMps * 3.6
    val remainingM: Double get() = (route.lengthM - distanceAlongM).coerceAtLeast(0.0)

    val nextTurn: TurnPoint? get() = turns.firstOrNull { it.distanceAlongRouteM > distanceAlongM + 1.0 }
    val distanceToNextTurnM: Double? get() = nextTurn?.let { it.distanceAlongRouteM - distanceAlongM }

    /** 경유지 등으로 경로가 바뀔 때. 현재 좌표를 새 경로에 투영해 이어 달린다 */
    fun setRoute(points: List<Vec2>) {
        val here = pos
        route = Polyline(points)
        turns = RouteAnalyzer.detectTurns(route)
        distanceAlongM = route.project(here).distanceAlongM
    }

    /** startM 지점에서 출발. initialSpeedMps로 달리던 속도 그대로 이어 갈 수 있다 (P면 0) */
    fun startAt(startM: Double, gear: Gear = Gear.D, initialSpeedMps: Double = 0.0) {
        distanceAlongM = startM.coerceIn(0.0, route.lengthM)
        this.gear = gear
        speedMps = if (gear == Gear.P) 0.0 else initialSpeedMps.coerceAtLeast(0.0)
    }

    /** dtMs(실제 경과). timeScale만큼 가상 시간이 더 흐른다 */
    fun tick(dtMs: Long) {
        if (gear == Gear.P) {
            speedMps = 0.0
            return
        }
        val dt = dtMs / 1000.0 * timeScale
        if (dt <= 0.0) return

        val target = targetForNow()
        speedMps = approach(speedMps, target, dt)
        distanceAlongM += speedMps * dt

        if (distanceAlongM >= route.lengthM) {
            distanceAlongM = route.lengthM
            speedMps = 0.0
            gear = Gear.P
        }
    }

    /** mock UxRestrictions용: 주행 중 제한 = gear==D && speed>0 */
    fun restrictedByMock(): Boolean = gear == Gear.D && speedMps > 0.1

    private fun targetForNow(): Double {
        val cruise = targetSpeedKmh / 3.6
        val toTurn = distanceToNextTurnM
        return if (toTurn != null && toTurn <= TURN_SLOWDOWN_M) TURN_SPEED_MPS.coerceAtMost(cruise)
        else cruise
    }

    private fun approach(current: Double, target: Double, dt: Double): Double {
        return if (current < target) (current + ACCEL_MPS2 * dt).coerceAtMost(target)
        else (current - DECEL_MPS2 * dt).coerceAtLeast(target)
    }

    companion object {
        const val ACCEL_MPS2 = 2.5
        const val DECEL_MPS2 = 3.5
        const val TURN_SLOWDOWN_M = 50.0
        const val TURN_SPEED_MPS = 20.0 / 3.6
    }
}
