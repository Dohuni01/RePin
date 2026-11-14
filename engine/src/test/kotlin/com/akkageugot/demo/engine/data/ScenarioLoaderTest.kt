package com.akkageugot.demo.engine.data

import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.engine.core.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioLoaderTest {

    private val data = TestFixtures.data

    @Test
    fun `경로는 약 3km다`() {
        assertEquals(3000.0, data.route.lengthM, 15.0)
    }

    @Test
    fun `가게 45개, 시나리오 5개`() {
        assertEquals(45, data.pois.size)
        assertEquals(5, data.scenarios.size)
        assertEquals(listOf(1, 2, 3, 4, 5), data.scenarios.map { it.n })
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
        assertEquals(250.0, s1.startDistanceAlongRouteM)
        assertEquals(400.0, s1.steps[0].triggerDistanceAlongRouteM)
        assertEquals(3.0, s1.steps[0].delaySec)
        assertEquals(1, s1.steps[0].preset)
    }

    @Test
    fun `시나리오2는 3분 전 카페 질문으로 배속 재생한다`() {
        val s2 = data.scenarios.first { it.n == 2 }
        assertEquals(5, s2.steps[0].preset)   // "아까 3분 전에 지나친 카페 뭐였지?"
        assertTrue(s2.timeScale >= 2)
    }
}
