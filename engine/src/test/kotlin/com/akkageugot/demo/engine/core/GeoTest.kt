package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.TurnDirection
import com.akkageugot.demo.contract.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoTest {

    @Test
    fun `위경도-로컬미터 왕복 변환이 일치한다`() {
        val proj = LocalProjection(LatLon(37.5440, 127.0560))
        val original = LatLon(37.5500, 127.0650)
        val local = proj.toLocal(original)
        val back = proj.toLatLon(local)
        assertEquals(original.lat, back.lat, 1e-9)
        assertEquals(original.lon, back.lon, 1e-9)
    }

    @Test
    fun `동쪽으로 1도는 약 88km, 북쪽으로 1도는 약 111km다`() {
        val proj = LocalProjection(LatLon(37.5440, 127.0560))
        val east = proj.toLocal(LatLon(37.5440, 128.0560))
        val north = proj.toLocal(LatLon(38.5440, 127.0560))
        assertEquals(88_000.0, east.x, 1_000.0)
        assertEquals(0.0, east.y, 1.0)
        assertEquals(111_000.0, north.y, 500.0)
    }

    @Test
    fun `폴리라인 거리-좌표 변환`() {
        val line = Polyline(listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 100.0)))
        assertEquals(200.0, line.lengthM, 1e-9)
        assertEquals(Vec2(50.0, 0.0), line.pointAt(50.0))
        assertEquals(Vec2(100.0, 50.0), line.pointAt(150.0))
        assertEquals(0.0, line.headingAt(50.0), 1e-9)              // 동쪽
        assertEquals(Math.PI / 2, line.headingAt(150.0), 1e-9)      // 북쪽
    }

    @Test
    fun `폴리라인 수직 투영`() {
        val line = Polyline(listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0)))
        val p = line.project(Vec2(30.0, 40.0))
        assertEquals(30.0, p.distanceAlongM, 1e-9)
        assertEquals(40.0, p.lateralM, 1e-9)
    }

    @Test
    fun `cross 부호 - 동쪽 진행 중 북쪽 가게는 LEFT(양수)`() {
        // 진행 방향 동쪽 (1,0), 가게는 북쪽 (0,1)
        val c = Geo.cross(1.0, 0.0, 0.0, 1.0)
        assert(c > 0)
        // 가게가 남쪽이면 음수 (RIGHT)
        assert(Geo.cross(1.0, 0.0, 0.0, -1.0) < 0)
    }

    @Test
    fun `데모 경로에서 우회전 1번, 좌회전 1번을 찾는다`() {
        val turns = RouteAnalyzer.detectTurns(TestFixtures.data.route)
        assertEquals(2, turns.size)
        assertEquals(TurnDirection.RIGHT, turns[0].direction)
        assertEquals(1200.0, turns[0].distanceAlongRouteM, 1.0)
        assertEquals(TurnDirection.LEFT, turns[1].direction)
        assertEquals(2000.0, turns[1].distanceAlongRouteM, 1.0)
    }
}
