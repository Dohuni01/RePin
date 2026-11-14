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
    fun `가게 30개, 시나리오 5개`() {
        assertEquals(30, data.pois.size)
        assertEquals(5, data.scenarios.size)
        assertEquals(listOf(1, 2, 3, 4, 5), data.scenarios.map { it.n })
    }

    @Test
    fun `오른쪽 가게는 동쪽 진행 구간에서 남쪽(-y)에 있다`() {
        val p05 = data.pois.first { it.id == "p05" } // 달빛제과, 400m 오른쪽 15m
        assertEquals(400.0, p05.pos.x, 2.0)
        assertEquals(-15.0, p05.pos.y, 2.0)
        assertEquals(Category.BAKERY, p05.category)
    }

    @Test
    fun `남쪽 진행 구간의 왼쪽 가게는 동쪽(+x)에 있다`() {
        val p18 = data.pois.first { it.id == "p18" } // 밤달베이커리, 1600m 왼쪽
        assertEquals(1215.0, p18.pos.x, 2.0)
        assertEquals(-400.0, p18.pos.y, 2.0)
    }

    @Test
    fun `(d) 2350~2700m에는 가게가 없다`() {
        val inGap = data.pois.filter {
            val d = data.route.project(it.pos).distanceAlongM
            d in 2350.0..2700.0
        }
        assertTrue(inGap.isEmpty())
    }

    @Test
    fun `시나리오 발화는 가게를 지난 뒤 3초에 시작한다`() {
        val s1 = data.scenarios.first { it.n == 1 }
        assertEquals(250.0, s1.startDistanceAlongRouteM)
        assertEquals(400.0, s1.steps[0].triggerDistanceAlongRouteM)
        assertEquals(3.0, s1.steps[0].delaySec)
        assertEquals(1, s1.steps[0].preset)
    }
}
