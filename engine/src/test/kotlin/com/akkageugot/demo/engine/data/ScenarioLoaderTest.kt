package com.akkageugot.demo.engine.data

import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.engine.core.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioLoaderTest {

    private val data = TestFixtures.data

    @Test
    fun `경로는 약 4000m다 (마지막 1000m는 가게 없는 직선, 도착 전 카드가 닫히도록)`() {
        assertEquals(4000.0, data.route.lengthM, 15.0)
        val lastPoi = data.pois.maxOf { data.route.project(it.pos).distanceAlongM }
        assertTrue(lastPoi <= 3000.0)
    }

    @Test
    fun `가게 52개, 시나리오 6개`() {
        assertEquals(52, data.pois.size)
        assertEquals(6, data.scenarios.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), data.scenarios.map { it.n })
    }

    @Test
    fun `오른쪽 가게는 동쪽 진행 구간에서 남쪽(-y)에 있다`() {
        val p09 = data.pois.first { it.id == "p09" } // 달빛제과, 400m 오른쪽 15m
        assertEquals(400.0, p09.pos.x, 2.0)
        assertEquals(-15.0, p09.pos.y, 2.0)
        assertEquals(Category.BAKERY, p09.category)
    }

    @Test
    fun `카페 두 곳이 붙어 있다 (주말 시나리오용)`() {
        val coffee = data.pois.first { it.id == "p05" } // 오후세시커피 250m 오른쪽
        val alley = data.pois.first { it.id == "p06" }   // 골목카페 262m 오른쪽
        assertEquals(Category.CAFE, coffee.category)
        assertEquals(Category.CAFE, alley.category)
        assertTrue(kotlin.math.abs(coffee.pos.x - alley.pos.x) < 20.0)
    }

    @Test
    fun `남쪽 진행 구간의 왼쪽 가게는 동쪽(+x)에 있다`() {
        val p31 = data.pois.first { it.id == "p31" } // 밤달베이커리, 1620m 왼쪽
        assertEquals(1215.0, p31.pos.x, 2.0)
        assertEquals(-420.0, p31.pos.y, 3.0)
    }

    @Test
    fun `지도에 없는 곳 구간 2300~2700m에는 가게가 없다`() {
        val inGap = data.pois.filter {
            val d = data.route.project(it.pos).distanceAlongM
            d in 2300.0..2700.0
        }
        assertTrue(inGap.isEmpty())
    }

    @Test
    fun `시나리오1은 가게를 지난 뒤 3초에 발화한다`() {
        val s1 = data.scenarios.first { it.n == 1 }
        assertEquals(365.0, s1.startDistanceAlongRouteM)
        assertEquals(250.0, s1.prefillFromM)
        assertEquals(400.0, s1.steps[0].triggerDistanceAlongRouteM)
        assertEquals(3.0, s1.steps[0].delaySec)
        assertEquals(1, s1.steps[0].preset)
    }

    @Test
    fun `시나리오2는 3분 전 카페 질문으로 배속 재생한다`() {
        val s2 = data.scenarios.first { it.n == 2 }
        assertEquals(5, s2.steps[0].preset)   // "아까 3분 전에 지나친 카페 뭐였지?"
        assertTrue(s2.timeScale >= 2)
        assertEquals(60.0, s2.prefillFromM)    // 60m부터 달린 3분치 기록을 미리 채운다
        assertTrue(s2.steps[0].triggerDistanceAlongRouteM - s2.startDistanceAlongRouteM <= 100.0)
    }

    @Test
    fun `시나리오3은 카페 질문만 좌우를 되묻고 오른쪽 대답 뒤 저장한다`() {
        val s3 = data.scenarios.first { it.n == 3 }
        assertTrue(s3.steps[0].askSide)
        assertEquals(listOf(false, false), s3.steps.drop(1).map { it.askSide })
        assertEquals(listOf(7, 8, 2), s3.steps.map { it.preset })
    }

    @Test
    fun `시나리오4는 옆에 뭐 있어, 왼쪽, 오른쪽에 뭐 있어 순서다`() {
        val s4 = data.scenarios.first { it.n == 4 }
        assertTrue(s4.steps[0].askSide)
        assertEquals(listOf(9, 10, 11), s4.steps.map { it.preset })
    }

    @Test
    fun `모든 시나리오는 첫 트리거 100m 안에서 출발한다`() {
        data.scenarios.filter { it.steps.isNotEmpty() }.forEach { s ->
            val gap = s.steps.minOf { it.triggerDistanceAlongRouteM } - s.startDistanceAlongRouteM
            assertTrue(gap in 0.0..100.0, "시나리오 ${s.n}: 출발 ${s.startDistanceAlongRouteM}m, 트리거까지 ${gap}m")
            s.prefillFromM?.let { assertTrue(it < s.startDistanceAlongRouteM, "시나리오 ${s.n} prefill") }
        }
    }

    @Test
    fun `마지막 단계 트리거 뒤에도 여유 거리가 남는다`() {
        // 마지막 질문 뒤 카드·음성이 끝나기 전에 도착하지 않도록 (배속 3, 80km/h로 약 40초 sim ≈ 900m)
        data.scenarios.filter { it.steps.isNotEmpty() }.forEach { s ->
            val last = s.steps.maxOf { it.triggerDistanceAlongRouteM }
            assertTrue(last + 900.0 < data.route.lengthM, "시나리오 ${s.n}: 마지막 트리거 ${last}m")
        }
    }
}
