package com.akkageugot.demo.engine.core

import com.akkageugot.demo.engine.data.ScenarioData
import com.akkageugot.demo.engine.data.ScenarioLoader
import java.io.File

object TestFixtures {

    /** 실제 데모 데이터 (engine/src/main/assets/scenarios.json) */
    val data: ScenarioData by lazy {
        val file = File("src/main/assets/scenarios.json")
        ScenarioLoader.parse(file.readText(Charsets.UTF_8))
    }

    /**
     * 경로를 따라 일정 속도로 달린 궤적을 1초 간격 샘플로 만든다.
     * @return (샘플 목록, 특정 경로 거리의 시각을 돌려주는 함수)
     */
    fun drive(
        fromM: Double,
        toM: Double,
        speedMps: Double,
        startTimeMs: Long = 0L
    ): List<TrailSample> {
        val route = data.route
        val samples = mutableListOf<TrailSample>()
        var d = fromM
        var t = startTimeMs
        while (d <= toM) {
            samples += TrailSample(
                tMs = t,
                pos = route.pointAt(d),
                speedMps = speedMps,
                headingRad = route.headingAt(d)
            )
            d += speedMps
            t += 1000
        }
        return samples
    }

    /** fromM에서 출발한 주행에서 거리 dM 지점을 지나는 시각(ms) */
    fun timeAt(fromM: Double, dM: Double, speedMps: Double, startTimeMs: Long = 0L): Long =
        startTimeMs + ((dM - fromM) / speedMps * 1000).toLong()
}
