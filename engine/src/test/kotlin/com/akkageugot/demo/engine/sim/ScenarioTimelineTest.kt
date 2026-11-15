package com.akkageugot.demo.engine.sim

import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.engine.core.Backtracker
import com.akkageugot.demo.engine.core.IntentParser
import com.akkageugot.demo.engine.core.TestFixtures
import com.akkageugot.demo.engine.core.TrailBuffer
import com.akkageugot.demo.engine.core.TrailSample
import com.akkageugot.demo.engine.data.ScenarioJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 엔진 루프(100ms 실제 틱, 1초 sim 간격 기록, 트리거 뒤 delaySec sim 발화)를 그대로 흉내 내
 * 시나리오의 시간표를 확인한다.
 * - 시작 후 첫 발화까지 실제 5초 안
 * - 미리 채운 기록으로 찾은 가게 = 예전 출발 지점(prefillFromM)부터 실제로 달려 찾은 가게
 * - 마지막 발화 뒤 실제 8초 동안 도착(기어 P)하지 않는다
 */
class ScenarioTimelineTest {

    private val data = TestFixtures.data
    /** 설정 화면 목표 속도 범위 안에서 확인 (기본 50) */
    private val speeds = listOf(30, 50, 80)
    private val tickMs = 100L

    /** 시나리오 첫 발화 프리셋 문구 (DemoCommandReceiver.PRESETS와 같은 번호) */
    private val questionText = mapOf(
        1 to "방금 오른쪽에 있던 빵집 뭐야?",
        5 to "아까 3분 전에 지나친 카페 뭐였지?",
        6 to "방금 거 뭐야?",
        7 to "방금 지나친 카페 뭐야?"
    )

    private class Fire(val preset: Int, val realMs: Long, val simMs: Long, val alongM: Double, val speedMps: Double)

    private class Run(
        val fires: List<Fire>,
        val trail: List<TrailSample>,
        val arrivedRealMs: Long?
    )

    /** startScenario + runLoop 흉내. usePrefill=false면 예전 방식(prefillFromM에서 0km/h로 출발) */
    private fun simulate(s: ScenarioJson, usePrefill: Boolean, targetKmh: Int = 50, extraRealMs: Long = 8_000): Run {
        val drive = SimulatedDrive(data.route.points).apply { targetSpeedKmh = targetKmh }
        var simClock = 1_000_000L
        val trail = TrailBuffer()
        val start: Double
        if (usePrefill && s.prefillFromM != null) {
            val p = TrailPrefill.build(data.route.points, s.prefillFromM!!, s.startDistanceAlongRouteM, targetKmh, simClock)
            p.samples.forEach { trail.add(it) }
            start = s.startDistanceAlongRouteM
            drive.startAt(start, Gear.D, p.endSpeedMps)
        } else {
            start = s.prefillFromM ?: s.startDistanceAlongRouteM
            drive.startAt(start, Gear.D)
        }
        drive.timeScale = s.timeScale
        var lastTrail = simClock - 1000
        val fireAt = arrayOfNulls<Long>(s.steps.size)
        val fired = BooleanArray(s.steps.size)
        val fires = mutableListOf<Fire>()
        var real = 0L
        var arrived: Long? = null
        var stopAt = Long.MAX_VALUE
        while (real < stopAt && real < 600_000) {
            real += tickMs
            drive.tick(tickMs)
            simClock += tickMs * drive.timeScale
            s.steps.forEachIndexed { i, st ->
                if (fired[i]) return@forEachIndexed
                if (fireAt[i] == null && drive.distanceAlongM >= st.triggerDistanceAlongRouteM) {
                    fireAt[i] = simClock + (st.delaySec * 1000).toLong()
                }
                val f = fireAt[i]
                if (f != null && simClock >= f) {
                    fired[i] = true
                    fires += Fire(st.preset, real, simClock, drive.distanceAlongM, drive.speedMps)
                }
            }
            if (simClock - lastTrail >= 1000) {
                lastTrail = simClock
                trail.add(TrailSample(simClock, drive.pos, drive.speedMps, drive.headingRad))
            }
            if (arrived == null && drive.gear == Gear.P) arrived = real
            if (fired.all { it } && stopAt == Long.MAX_VALUE) stopAt = real + extraRealMs
            if (s.steps.isEmpty()) break
        }
        return Run(fires, trail.samples(), arrived)
    }

    /** 첫 발화 순간의 기록만으로 역추적 (엔진도 t0 = 발화 시각의 simClock) */
    private fun firstAnswer(run: Run): String? {
        val f = run.fires.first()
        val text = questionText[f.preset] ?: return null
        val trailAtT0 = run.trail.filter { it.tMs <= f.simMs }
        val r = Backtracker(data.pois).search(trailAtT0, f.simMs, IntentParser.parse(text))
        return r.candidates.firstOrNull()?.candidate?.poi?.id
    }

    @Test
    fun `모든 시나리오는 시작 후 실제 5초 안에 첫 질문을 한다`() {
        for (kmh in speeds) data.scenarios.filter { it.steps.isNotEmpty() }.forEach { s ->
            val run = simulate(s, usePrefill = true, targetKmh = kmh)
            val first = run.fires.first()
            assertTrue(first.realMs <= 5_000, "시나리오 ${s.n} ${kmh}km/h: 첫 질문까지 ${first.realMs}ms")
        }
    }

    @Test
    fun `미리 채운 기록으로도 예전과 같은 가게를 찾는다`() {
        // 80km/h에선 60m~2700m를 3분 안에 다 달려 "3분 전"이 기록 맨 앞에 걸린다(예전에도 카페를 못 찾음). 30, 50만 비교
        for (kmh in listOf(30, 50)) data.scenarios.filter { it.prefillFromM != null && it.steps.isNotEmpty() }.forEach { s ->
            val before = simulate(s, usePrefill = false, targetKmh = kmh)
            val now = simulate(s, usePrefill = true, targetKmh = kmh)
            val a = firstAnswer(before)
            val b = firstAnswer(now)
            assertEquals(a, b, "시나리오 ${s.n} ${kmh}km/h: 예전 $a, 지금 $b")
            if (s.steps.first().preset in listOf(1, 5, 7)) assertTrue(b != null, "시나리오 ${s.n}: 가게를 못 찾음")
            println("시나리오 ${s.n} ${kmh}km/h: 첫 질문 ${now.fires.first().realMs}ms(실제), 답 $b, 마지막 질문 지점 ${"%.0f".format(now.fires.last().alongM)}m")
            // "옆에 뭐 있어?"(9)처럼 위치·속도로 답하는 경우도 같은 곳에서 묻는다
            assertEquals(before.fires.first().alongM, now.fires.first().alongM, 3.0, "시나리오 ${s.n} 질문 지점")
            assertEquals(before.fires.first().speedMps, now.fires.first().speedMps, 0.5, "시나리오 ${s.n} 질문 속도")
        }
    }

    @Test
    fun `3분 전 카페는 미리 채운 기록에서 찾는다`() {
        val s2 = data.scenarios.first { it.n == 2 }
        val run = simulate(s2, usePrefill = true)
        val id = firstAnswer(run)
        val poi = data.pois.first { it.id == id }
        assertEquals(com.akkageugot.demo.contract.Category.CAFE, poi.category)
        assertTrue(data.route.project(poi.pos).distanceAlongM < s2.startDistanceAlongRouteM)
    }

    @Test
    fun `마지막 질문 뒤 실제 8초 동안 도착하지 않는다`() {
        for (kmh in speeds) data.scenarios.filter { it.steps.isNotEmpty() }.forEach { s ->
            val run = simulate(s, usePrefill = true, targetKmh = kmh, extraRealMs = 8_000)
            assertEquals(s.steps.size, run.fires.size, "시나리오 ${s.n} ${kmh}km/h: 모든 단계 발화")
            assertEquals(null, run.arrivedRealMs, "시나리오 ${s.n} ${kmh}km/h: 카드가 떠 있는 동안 도착")
        }
    }
}
