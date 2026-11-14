package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrailBufferTest {

    @Test
    fun `10분 넘은 샘플은 버린다`() {
        val buffer = TrailBuffer()
        for (i in 0 until 700) {
            buffer.add(TrailSample(i * 1000L, Vec2(i.toDouble(), 0.0), 10.0, 0.0))
        }
        val samples = buffer.samples()
        // 마지막 샘플 t=699초, 10분(600초) 이내만 유지
        assertTrue(samples.first().tMs >= (699 - 600) * 1000L)
        assertEquals(699_000L, samples.last().tMs)
        assertEquals(601, samples.size)
    }

    @Test
    fun `가장 가까운 샘플을 찾는다`() {
        val buffer = TrailBuffer()
        for (i in 0 until 10) {
            buffer.add(TrailSample(i * 1000L, Vec2(i.toDouble(), 0.0), 10.0, 0.0))
        }
        assertEquals(3000L, buffer.sampleNear(3300L)?.tMs)
        assertEquals(4000L, buffer.sampleNear(3700L)?.tMs)
    }
}
