package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Vec2
import java.util.ArrayDeque

/** 1초 간격으로 쌓이는 주행 흔적 샘플 */
data class TrailSample(
    val tMs: Long,
    val pos: Vec2,
    val speedMps: Double,
    val headingRad: Double
)

/**
 * 최근 10분의 주행 흔적. 기기 밖으로 내보내지 않는다 (engine/PROMPT.md 6절).
 */
class TrailBuffer(private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {

    private val deque = ArrayDeque<TrailSample>()

    fun add(sample: TrailSample) {
        deque.addLast(sample)
        while (deque.isNotEmpty() && sample.tMs - deque.first.tMs > maxAgeMs) {
            deque.removeFirst()
        }
    }

    fun samples(): List<TrailSample> = deque.toList()

    fun isEmpty(): Boolean = deque.isEmpty()

    /** t 시점과 가장 가까운 샘플 (없으면 null) */
    fun sampleNear(tMs: Long): TrailSample? = deque.minByOrNull { kotlin.math.abs(it.tMs - tMs) }

    fun clear() = deque.clear()

    companion object {
        const val DEFAULT_MAX_AGE_MS = 10L * 60 * 1000
    }
}
