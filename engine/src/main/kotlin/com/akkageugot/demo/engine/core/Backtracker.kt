package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Candidate
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.ScoreDetail
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.contract.Vec2
import kotlin.math.cos
import kotlin.math.sin

enum class Confidence { HIGH, MEDIUM, LOW }

/** Contract Candidate + 엔진 내부용 부가 정보 */
data class ScoredCandidate(
    val candidate: Candidate,
    val tPassMs: Long,
    /** t0 시점 기준 가게를 지나온 거리(m). HeadsUp subtitle "Nm 지나옴"에 쓴다 */
    val passedM: Double
)

data class BacktrackResult(
    val candidates: List<ScoredCandidate>,
    val confidence: Confidence,
    val corridor: List<Vec2>,
    val t0Pos: Vec2?
) {
    companion object {
        val EMPTY = BacktrackResult(emptyList(), Confidence.LOW, emptyList(), null)
    }
}

/**
 * 지나온 궤적을 되짚어 발화와 맞는 가게 후보를 찾는다 (engine/PROMPT.md 6절).
 *
 * - 탐색 구간: t0 기준 뒤쪽 clamp(속도×8초, 30m, 250m), 앞쪽 속도×1초
 * - "아까"만 있으면(vaguePast) 뒤쪽 폭 2배
 * - 후보: 구간 궤적 선분에서 수직 거리 60m 이내
 * - score = 0.35*(1-d/60) + 0.25*방향 + 0.25*업종 + 0.15*시간
 */
class Backtracker(private val pois: List<Poi>) {

    fun search(trail: List<TrailSample>, t0Ms: Long, intent: ParsedIntent): BacktrackResult {
        if (trail.size < 2) return BacktrackResult.EMPTY

        // t0 보정: "아까 N분 전"이면 N분 앞으로
        val effectiveT0 = t0Ms - (intent.timeOffsetSec ?: 0) * 1000L
        val clamped = effectiveT0.coerceIn(trail.first().tMs, trail.last().tMs)

        val arc = TrailArc(trail)
        val s0 = arc.arcAtTime(clamped)
        val t0Pos = arc.posAtArc(s0)
        val speedAtT0 = arc.sampleNear(clamped).speedMps

        var backM = (speedAtT0 * 8).coerceIn(MIN_BACK_M, MAX_BACK_M)
        if (intent.vaguePast) backM *= 2
        val forwardM = speedAtT0 * 1

        val corridor = arc.slice(s0 - backM, s0 + forwardM)
        if (corridor.positions.size < 2) return BacktrackResult(emptyList(), Confidence.LOW, corridor.positions, t0Pos)

        val scored = pois.mapNotNull { poi -> score(poi, corridor, arc, clamped, s0, intent) }
            .sortedByDescending { it.candidate.score }
            .take(3)

        val confidence = when {
            scored.isEmpty() -> Confidence.LOW
            scored[0].candidate.score >= HIGH_SCORE &&
                (scored.size < 2 || scored[0].candidate.score - scored[1].candidate.score >= HIGH_GAP) -> Confidence.HIGH
            scored[0].candidate.score >= MEDIUM_SCORE -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        return BacktrackResult(scored, confidence, corridor.positions, t0Pos)
    }

    private fun score(
        poi: Poi,
        corridor: TrailArc.Slice,
        arc: TrailArc,
        t0Ms: Long,
        s0: Double,
        intent: ParsedIntent
    ): ScoredCandidate? {
        // 구간 선분들 중 최단 거리
        var bestD = Double.MAX_VALUE
        var bestPoint = corridor.positions.first()
        var bestTimeMs = corridor.timesMs.first()
        var bestArc = corridor.arcs.first()
        var bestSegIndex = 0
        for (i in 0 until corridor.positions.size - 1) {
            val proj = Geo.closestOnSegment(poi.pos, corridor.positions[i], corridor.positions[i + 1])
            val d = Geo.distance(poi.pos, proj.point)
            if (d < bestD) {
                bestD = d
                bestPoint = proj.point
                bestTimeMs = lerpLong(corridor.timesMs[i], corridor.timesMs[i + 1], proj.t)
                bestArc = corridor.arcs[i] + (corridor.arcs[i + 1] - corridor.arcs[i]) * proj.t
                bestSegIndex = i
            }
        }
        if (bestD > MAX_LATERAL_M) return null

        // 좌우: 그 시점 진행 방향 h, 차→가게 (dx, dy), cross > 0 LEFT / < 0 RIGHT
        val headingRad = arc.sampleNear(bestTimeMs).headingRad
        val hx = cos(headingRad)
        val hy = sin(headingRad)
        val dx = poi.pos.x - bestPoint.x
        val dy = poi.pos.y - bestPoint.y
        val side = if (Geo.cross(hx, hy, dx, dy) > 0) Side.LEFT else Side.RIGHT

        val distanceScore = 1.0 - bestD / MAX_LATERAL_M
        val sideScore = when {
            intent.side == Side.UNKNOWN -> if (side == Side.RIGHT) 0.6 else 0.4 // 우측통행 가중
            intent.side == side -> 1.0
            else -> 0.0
        }
        val categoryScore = when {
            intent.category == null -> 0.5
            intent.category == poi.category -> 1.0
            else -> 0.0
        }
        val dtSec = (t0Ms - bestTimeMs) / 1000.0
        val timeScore = when {
            dtSec < 0.0 -> 0.0
            dtSec < 2.0 -> dtSec / 2.0
            dtSec <= 8.0 -> 1.0
            dtSec <= 15.0 -> (15.0 - dtSec) / 7.0
            else -> 0.0
        }

        val detail = ScoreDetail(
            distance = distanceScore,
            side = sideScore,
            category = categoryScore,
            time = timeScore
        )
        val total = 0.35 * distanceScore + 0.25 * sideScore + 0.25 * categoryScore + 0.15 * timeScore

        return ScoredCandidate(
            candidate = Candidate(
                poi = poi,
                score = total,
                side = side,
                distanceM = bestD,
                detail = detail
            ),
            tPassMs = bestTimeMs,
            passedM = (s0 - bestArc).coerceAtLeast(0.0)
        )
    }

    private fun lerpLong(a: Long, b: Long, t: Double): Long = (a + (b - a) * t).toLong()

    companion object {
        const val MIN_BACK_M = 30.0
        const val MAX_BACK_M = 250.0
        const val MAX_LATERAL_M = 60.0
        const val HIGH_SCORE = 0.75
        const val HIGH_GAP = 0.15
        const val MEDIUM_SCORE = 0.5
    }
}

/**
 * 시간이 붙은 궤적을 호 길이(arc length) 기준으로 다루는 유틸.
 */
class TrailArc(private val samples: List<TrailSample>) {

    private val arcs = DoubleArray(samples.size).also { acc ->
        for (i in 1 until samples.size) {
            acc[i] = acc[i - 1] + Geo.distance(samples[i - 1].pos, samples[i].pos)
        }
    }

    val lengthM: Double get() = arcs.last()

    data class Slice(
        val positions: List<Vec2>,
        val timesMs: List<Long>,
        val arcs: List<Double>
    )

    fun sampleNear(tMs: Long): TrailSample = samples.minBy { kotlin.math.abs(it.tMs - tMs) }

    /** t 시점의 호 길이 위치 (샘플 사이 시간 선형 보간) */
    fun arcAtTime(tMs: Long): Double {
        if (tMs <= samples.first().tMs) return 0.0
        if (tMs >= samples.last().tMs) return lengthM
        val i = samples.indexOfLast { it.tMs <= tMs }
        val a = samples[i]
        val b = samples[i + 1]
        val span = (b.tMs - a.tMs).toDouble()
        val t = if (span == 0.0) 0.0 else (tMs - a.tMs) / span
        return arcs[i] + (arcs[i + 1] - arcs[i]) * t
    }

    /** 호 길이 s 지점의 좌표 */
    fun posAtArc(s: Double): Vec2 {
        val clamped = s.coerceIn(0.0, lengthM)
        val i = indexAtArc(clamped)
        val segLen = arcs[i + 1] - arcs[i]
        val t = if (segLen == 0.0) 0.0 else (clamped - arcs[i]) / segLen
        return Geo.lerp(samples[i].pos, samples[i + 1].pos, t)
    }

    private fun timeAtArc(s: Double): Long {
        val clamped = s.coerceIn(0.0, lengthM)
        val i = indexAtArc(clamped)
        val segLen = arcs[i + 1] - arcs[i]
        val t = if (segLen == 0.0) 0.0 else (clamped - arcs[i]) / segLen
        return (samples[i].tMs + (samples[i + 1].tMs - samples[i].tMs) * t).toLong()
    }

    /** [fromS, toS] 범위의 궤적 조각 (양 끝은 보간) */
    fun slice(fromS: Double, toS: Double): Slice {
        val lo = fromS.coerceIn(0.0, lengthM)
        val hi = toS.coerceIn(0.0, lengthM)
        if (hi <= lo) return Slice(emptyList(), emptyList(), emptyList())

        val positions = mutableListOf(posAtArc(lo))
        val timesMs = mutableListOf(timeAtArc(lo))
        val arcList = mutableListOf(lo)
        for (i in samples.indices) {
            if (arcs[i] > lo && arcs[i] < hi) {
                positions += samples[i].pos
                timesMs += samples[i].tMs
                arcList += arcs[i]
            }
        }
        positions += posAtArc(hi)
        timesMs += timeAtArc(hi)
        arcList += hi
        return Slice(positions, timesMs, arcList)
    }

    private fun indexAtArc(s: Double): Int {
        var lo = 0
        var hi = samples.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (arcs[mid] <= s) lo = mid else hi = mid - 1
        }
        return lo
    }
}
