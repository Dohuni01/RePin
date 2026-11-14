package com.akkageugot.demo.engine.platform

import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.engine.core.DemoRoutePlanner
import com.akkageugot.demo.engine.core.PlannedRoute

/**
 * 데모 내비 (engine/PROMPT.md 4·6절). 경로 계산은 순수 Kotlin DemoRoutePlanner가 하고,
 * 실제 Pleos NaviHelper로의 미러링은 옵션(NaviMirror)이다.
 *
 * 기본 구현 = DemoNavigation(DemoRoutePlanner). 설정 mirrorToRealNavi=true면
 * 같은 요청을 NaviMirror에도 보내고 콜백 결과를 로그/상태로 받는다.
 */
class DemoNavigation(
    routePoints: List<Vec2>,
    private val mirror: NaviMirror? = null
) {
    private val planner = DemoRoutePlanner(routePoints)

    val originalRoutePoints: List<Vec2> get() = planner.originalRoute.points
    val destination: Vec2 get() = originalRoutePoints.lastOrNull() ?: Vec2(0.0, 0.0)

    /** 시나리오 시작/목적지 설정 시 실제 내비에도 목적지를 요청한다 */
    fun requestDestination(destinationName: String) {
        mirror?.requestRoute(destination, destinationName)
    }

    /** 경유지 경로 계산 + (옵션) 실제 내비에 경유지 추가 */
    fun addWaypoint(currentDistanceAlongM: Double, poi: Poi): PlannedRoute {
        mirror?.addWaypoint(poi.pos, poi.name)
        return planner.routeWithWaypoint(currentDistanceAlongM, poi)
    }

    /** 정차 후 선택한 장소로 가는 단순 경로 */
    fun routeToPlace(currentPos: Vec2, target: Vec2, name: String): List<Vec2> {
        mirror?.requestRoute(target, name)
        return planner.routeToPlace(currentPos, target)
    }

    val mirrorStatus: String get() = mirror?.status ?: "DemoNavigation(미러 꺼짐)"
}

/**
 * 실제 내비(Pleos NaviHelper)로의 미러링 인터페이스. Vec2(로컬 미터)를 위경도로 바꿔 보낸다.
 * 구현: platform/pleos/NaviHelperMirror.
 */
interface NaviMirror {
    val status: String
    fun start()
    fun requestRoute(destination: Vec2, name: String)
    fun addWaypoint(waypoint: Vec2, name: String)
    fun stop()
}
