package com.akkageugot.demo.engine.sim

import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.engine.core.TrailSample

/**
 * 시나리오 시작 때 "지나온 길" 기록을 미리 만든다.
 * fromM에서 출발해 toM 바로 앞까지 실제 주행과 같은 방식(가속, 회전 감속)으로 1초씩 달리고,
 * 그 1초 간격 샘플과 toM 직전의 속도를 돌려준다. "N분 전" 질문을 시작하자마자 보여 주기 위함.
 */
object TrailPrefill {

    data class Result(val samples: List<TrailSample>, val endSpeedMps: Double)

    /**
     * @param endClockMs 마지막 샘플 다음 1초가 이 시각이 되도록 시각을 매긴다
     *   (샘플 n개의 i번째 = endClockMs - (n - i) * 1000)
     */
    fun build(
        routePoints: List<Vec2>,
        fromM: Double,
        toM: Double,
        targetSpeedKmh: Int,
        endClockMs: Long
    ): Result {
        val sim = SimulatedDrive(routePoints).apply {
            this.targetSpeedKmh = targetSpeedKmh
            timeScale = 1
            startAt(fromM, Gear.D)
        }
        val raw = mutableListOf<Triple<Vec2, Double, Double>>()
        var guard = 0
        while (sim.distanceAlongM < toM && sim.gear == Gear.D && guard < MAX_STEPS) {
            raw += Triple(sim.pos, sim.speedMps, sim.headingRad)
            sim.tick(STEP_MS)
            guard++
        }
        val n = raw.size
        val samples = raw.mapIndexed { i, (pos, speed, heading) ->
            TrailSample(endClockMs - (n - i) * STEP_MS, pos, speed, heading)
        }
        return Result(samples, sim.speedMps)
    }

    private const val STEP_MS = 1000L
    private const val MAX_STEPS = 3600 // 안전장치(1시간)
}
