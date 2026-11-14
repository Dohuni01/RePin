package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.SavedStatus
import com.akkageugot.demo.contract.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RevisitWatcherTest {

    private val route = TestFixtures.data.route

    private fun saved(id: String, pos: Vec2, status: SavedStatus = SavedStatus.CONFIRMED) =
        SavedPlace(
            id = id, createdAtMs = 0L, label = "오전 8:12 · 주행", pos = pos,
            utterance = "방금 오른쪽에 있던 빵집 뭐야?", candidates = emptyList(),
            selectedPoiId = null, status = status, memo = ""
        )

    @Test
    fun `진행 방향 300m 앞 60m 이내면 알린다`() {
        val watcher = RevisitWatcher()
        // 달빛제과 위치(경로 400m 지점, 오른쪽 15m)
        val place = saved("s1", Vec2(400.0, -15.0))
        val hit = watcher.check(listOf(place), route, currentDistanceAlongM = 150.0, nowMs = 0L)
        assertEquals("s1", hit?.place?.id)
        assertEquals(250.0, hit!!.aheadM, 1.0)
    }

    @Test
    fun `아직 300m보다 멀면 조용하다`() {
        val watcher = RevisitWatcher()
        val place = saved("s1", Vec2(400.0, -15.0))
        assertNull(watcher.check(listOf(place), route, currentDistanceAlongM = 50.0, nowMs = 0L))
    }

    @Test
    fun `이미 지나친 곳은 알리지 않는다`() {
        val watcher = RevisitWatcher()
        val place = saved("s1", Vec2(400.0, -15.0))
        assertNull(watcher.check(listOf(place), route, currentDistanceAlongM = 450.0, nowMs = 0L))
    }

    @Test
    fun `경로에서 60m 넘게 떨어진 곳은 알리지 않는다`() {
        val watcher = RevisitWatcher()
        val place = saved("s1", Vec2(400.0, -100.0))
        assertNull(watcher.check(listOf(place), route, currentDistanceAlongM = 150.0, nowMs = 0L))
    }

    @Test
    fun `CONFIRMED만 대상이다`() {
        val watcher = RevisitWatcher()
        val pending = saved("s1", Vec2(400.0, -15.0), SavedStatus.PENDING)
        assertNull(watcher.check(listOf(pending), route, currentDistanceAlongM = 150.0, nowMs = 0L))
    }

    @Test
    fun `같은 곳은 하루에 한 번만`() {
        val watcher = RevisitWatcher()
        val place = saved("s1", Vec2(400.0, -15.0))
        watcher.check(listOf(place), route, 150.0, nowMs = 0L)
        assertNull(watcher.check(listOf(place), route, 160.0, nowMs = 60_000L))
        // 다음 날은 다시 알린다
        val nextDay = 25L * 60 * 60 * 1000
        assertEquals("s1", watcher.check(listOf(place), route, 150.0, nowMs = nextDay)?.place?.id)
    }
}
