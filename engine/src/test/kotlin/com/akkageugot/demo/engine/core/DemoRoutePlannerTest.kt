package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DemoRoutePlannerTest {

    private val planner = DemoRoutePlanner(TestFixtures.data.route.points)

    @Test
    fun `경유지 경로는 가게 앞을 지나 200m 뒤에서 합류한다`() {
        // 442m 지점에서 400m의 달빛제과(오른쪽 15m)로 되돌아가기
        val poi = Poi("p05", "달빛제과", Category.BAKERY, Vec2(400.0, -15.0), true)
        val planned = planner.routeWithWaypoint(442.0, poi)
        val newRoute = Polyline(planned.route)

        // 가게 앞 도로 지점(400, 0)을 지난다
        val near = planned.route.minBy { Geo.distance(it, Vec2(400.0, 0.0)) }
        assertEquals(0.0, Geo.distance(near, Vec2(400.0, 0.0)), 1.0)

        // 새 경로는 원래 남은 거리보다 길다 (되돌아갔다가 합류)
        val remainingOriginal = TestFixtures.data.route.lengthM - 442.0
        assertTrue(newRoute.lengthM > remainingOriginal)
        assertEquals(84.0, planned.extraDistanceM, 5.0)

        // 끝점은 원래 목적지와 같다
        assertEquals(0.0, Geo.distance(planned.route.last(), TestFixtures.data.route.points.last()), 1.0)
    }

    @Test
    fun `정차 후 선택한 장소로는 단순 경로`() {
        val r = planner.routeToPlace(Vec2(0.0, 0.0), Vec2(400.0, -15.0))
        assertEquals(2, r.size)
        assertEquals(Vec2(400.0, -15.0), r.last())
    }
}
