package com.akkageugot.demo.engine.sim

import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.engine.core.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimulatedDriveTest {

    private fun newDrive() = SimulatedDrive(TestFixtures.data.route.points)

    /** dtMs를 steps번 틱 */
    private fun SimulatedDrive.run(steps: Int, dtMs: Long = 100) {
        repeat(steps) { tick(dtMs) }
    }

    @Test
    fun `P단에서는 움직이지 않는다`() {
        val d = newDrive().apply { startAt(100.0, Gear.P) }
        d.run(50)
        assertEquals(100.0, d.distanceAlongM, 0.001)
        assertEquals(0.0, d.speedKmh, 0.001)
    }

    @Test
    fun `D단에서 목표 속도까지 가속한다`() {
        val d = newDrive().apply { targetSpeedKmh = 50; startAt(0.0, Gear.D) }
        d.run(100) // 10초
        assertEquals(50.0, d.speedKmh, 1.0)
        assertTrue(d.distanceAlongM > 0)
    }

    @Test
    fun `회전 지점에 다다르면 20km_h까지 감속한다`() {
        // 첫 우회전은 1200m 지점. 50m 전(1150m)부터 감속해 회전 지점에서는 20km/h 근처
        val d = newDrive().apply { targetSpeedKmh = 60; startAt(1050.0, Gear.D) }
        var guard = 0
        while (d.distanceAlongM < 1199.0 && guard < 3000) { d.tick(100); guard++ }
        assertTrue(d.speedKmh <= 21.0, "회전 지점에서 감속해야 하는데 ${d.speedKmh}km/h")
    }

    @Test
    fun `회전 50m 전부터 속도가 목표보다 낮아지기 시작한다`() {
        val d = newDrive().apply { targetSpeedKmh = 60; startAt(1050.0, Gear.D) }
        var guard = 0
        while (d.distanceAlongM < 1170.0 && guard < 3000) { d.tick(100); guard++ }
        assertTrue(d.speedKmh < 60.0, "감속 구간인데 ${d.speedKmh}km/h")
    }

    @Test
    fun `목적지에 도착하면 기어가 P로 바뀐다`() {
        val d = newDrive().apply { targetSpeedKmh = 80; startAt(TestFixtures.data.route.lengthM - 30, Gear.D) }
        var guard = 0
        while (d.gear == Gear.D && guard < 3000) { d.tick(100); guard++ }
        assertEquals(Gear.P, d.gear)
        assertEquals(0.0, d.remainingM, 0.5)
    }

    @Test
    fun `timeScale 배속이면 같은 실제 시간에 더 멀리 간다`() {
        val x1 = newDrive().apply { targetSpeedKmh = 50; timeScale = 1; startAt(0.0, Gear.D) }
        val x4 = newDrive().apply { targetSpeedKmh = 50; timeScale = 4; startAt(0.0, Gear.D) }
        x1.run(50)
        x4.run(50)
        assertTrue(x4.distanceAlongM > x1.distanceAlongM * 3)
    }

    @Test
    fun `경로를 바꾸면 현재 위치를 새 경로에 투영해 이어간다`() {
        val d = newDrive().apply { targetSpeedKmh = 50; startAt(400.0, Gear.D) }
        val before = d.pos
        // 원래 경로를 그대로 다시 세팅해도 위치가 보존된다
        d.setRoute(TestFixtures.data.route.points)
        assertEquals(400.0, d.distanceAlongM, 2.0)
        assertEquals(0.0, com.akkageugot.demo.engine.core.Geo.distance(before, d.pos), 2.0)
    }

    @Test
    fun `mock 주행 제한은 D단으로 움직일 때만 true`() {
        val d = newDrive().apply { targetSpeedKmh = 50; startAt(0.0, Gear.D) }
        assertTrue(!d.restrictedByMock()) // 아직 정지
        d.run(60)
        assertTrue(d.restrictedByMock()) // 주행 중
        d.gear = Gear.P
        d.tick(100)
        assertTrue(!d.restrictedByMock())
    }

    @Test
    fun `다음 회전 정보를 노출한다`() {
        val d = newDrive().apply { startAt(1000.0, Gear.D) }
        assertEquals(com.akkageugot.demo.contract.TurnDirection.RIGHT, d.nextTurn?.direction)
        assertEquals(200.0, d.distanceToNextTurnM!!, 1.0)
    }
}
