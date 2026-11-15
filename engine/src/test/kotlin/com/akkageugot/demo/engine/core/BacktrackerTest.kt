package com.akkageugot.demo.engine.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * scenarios.json의 실제 데이터로 (a)~(d) 상황을 검증한다 (engine/PROMPT.md 6절).
 * 50km/h ≈ 13.89m/s. 가게를 지나고 3초 뒤 질문한 상황을 재현한다.
 */
class BacktrackerTest {

    private val speed50 = 50.0 / 3.6
    private val backtracker = Backtracker(TestFixtures.data.pois)

    private fun askAt(fromM: Double, atM: Double, speedMps: Double, utterance: String): BacktrackResult {
        val trail = TestFixtures.drive(fromM, atM + speedMps, speedMps)
        val t0 = TestFixtures.timeAt(fromM, atM, speedMps)
        return backtracker.search(trail, t0, IntentParser.parse(utterance))
    }

    @Test
    fun `(a) 오른쪽에 빵집 하나만 있는 구간 - HIGH로 달빛제과`() {
        // 달빛제과(p09, 400m 오른쪽)를 지나고 3초 뒤(442m) 질문
        val r = askAt(250.0, 442.0, speed50, "방금 오른쪽에 있던 빵집 뭐야?")
        assertEquals("p09", r.candidates.first().candidate.poi.id)
        assertEquals("달빛제과", r.candidates.first().candidate.poi.name)
        assertEquals(Confidence.HIGH, r.confidence)
        assertEquals(com.akkageugot.demo.contract.Side.RIGHT, r.candidates.first().candidate.side)
    }

    @Test
    fun `(b) 카페 둘이 붙은 구간 - 1위 오후세시커피, 2위 골목카페`() {
        // 오후세시커피(p05, 250m), 골목카페(p06, 262m) 둘 다 오른쪽
        val r = askAt(60.0, 300.0, speed50, "방금 오른쪽에 있던 카페 뭐야?")
        assertEquals("p05", r.candidates[0].candidate.poi.id)
        assertEquals("p06", r.candidates[1].candidate.poi.id)
    }

    @Test
    fun `(c) 왼쪽 빵집 - 밤달베이커리를 찾는다`() {
        // 1620m 지점 왼쪽 밤달베이커리(p31)
        val r = askAt(1470.0, 1662.0, speed50, "방금 왼쪽에 있던 빵집 뭐야?")
        assertEquals("p31", r.candidates.first().candidate.poi.id)
        assertEquals(com.akkageugot.demo.contract.Side.LEFT, r.candidates.first().candidate.side)
    }

    @Test
    fun `(d) 가게 데이터가 없는 구간 - LOW, 후보 없음`() {
        // 2240~2760m 빈 구간. 2500m에서 질문
        val r = askAt(2300.0, 2500.0, speed50, "방금 거 뭐야?")
        assertTrue(r.candidates.isEmpty())
        assertEquals(Confidence.LOW, r.confidence)
    }

    @Test
    fun `속도가 빠르면 탐색 구간이 길어진다`() {
        val slow = askAt(250.0, 442.0, 30.0 / 3.6, "방금 거 뭐야?")   // 8.33m/s → 66.7m
        val fast = askAt(250.0, 700.0, 90.0 / 3.6, "방금 거 뭐야?")   // 25m/s → 200m
        val slowLen = corridorLength(slow)
        val fastLen = corridorLength(fast)
        // 뒤 66.7 + 앞 8.3 ≈ 75 / 뒤 200 + 앞 25 ≈ 225
        assertEquals(75.0, slowLen, 5.0)
        assertEquals(225.0, fastLen, 10.0)
    }

    @Test
    fun `최저 30m, 최대 250m로 제한된다`() {
        // 아주 느린 속도(2m/s): 2*8=16 → 30m로 올린다
        val crawl = askAt(380.0, 442.0, 2.0, "방금 거 뭐야?")
        assertEquals(32.0, corridorLength(crawl), 3.0) // 뒤 30 + 앞 2
    }

    @Test
    fun `아까만 말하면 탐색 폭이 2배다`() {
        val normal = askAt(250.0, 500.0, speed50, "방금 거 뭐야?")
        val vague = askAt(250.0, 500.0, speed50, "아까 지나친 거 뭐였지?")
        val n = corridorLength(normal)
        val v = corridorLength(vague)
        assertEquals(n + 111.0, v, 8.0) // 뒤쪽 111m가 한 번 더 늘어난다
    }

    @Test
    fun `아까 3분 전이면 t0를 3분 앞으로 옮긴다`() {
        // 10m/s로 주행, 2050m(t=205s)에서 "3분 전" 카페 → t=25s, 250m 부근 → 오후세시커피(p05)
        val trail = TestFixtures.drive(0.0, 2100.0, 10.0)
        val t0 = TestFixtures.timeAt(0.0, 2050.0, 10.0)
        val r = backtracker.search(trail, t0, IntentParser.parse("아까 3분 전에 지나친 카페 뭐였지?"))
        assertEquals("p05", r.candidates.first().candidate.poi.id)
        assertEquals("오후세시커피", r.candidates.first().candidate.poi.name)
    }

    @Test
    fun `지나온 거리 passedM이 subtitle용으로 계산된다`() {
        val r = askAt(250.0, 442.0, speed50, "방금 오른쪽에 있던 빵집 뭐야?")
        val top = r.candidates.first()
        assertEquals(42.0, top.passedM, 5.0) // 400m 가게, 442m에서 질문
    }

    @Test
    fun `볕들빌딩 앞 카페 질문 - 양쪽에 카페가 있고 오른쪽으로 찾으면 볕드는카페`() {
        // 시나리오3: 볕드는카페(b01s0, 480m 오른쪽)를 지나 514m에서 질문
        val r = askAt(300.0, 514.0, speed50, "방금 지나친 카페 뭐야?")
        val sides = r.candidates.filter { it.candidate.poi.category == com.akkageugot.demo.contract.Category.CAFE }
            .map { it.candidate.side }.toSet()
        assertTrue(com.akkageugot.demo.contract.Side.RIGHT in sides)
        assertTrue(com.akkageugot.demo.contract.Side.LEFT in sides)

        val trail = TestFixtures.drive(300.0, 514.0 + speed50, speed50)
        val t0 = TestFixtures.timeAt(300.0, 514.0, speed50)
        val right = backtracker.search(
            trail, t0, IntentParser.parse("방금 지나친 카페 뭐야?").copy(side = com.akkageugot.demo.contract.Side.RIGHT)
        )
        assertEquals("b01s0", right.candidates.first().candidate.poi.id)
    }

    private fun corridorLength(r: BacktrackResult): Double {
        var sum = 0.0
        for (i in 0 until r.corridor.size - 1) {
            sum += Geo.distance(r.corridor[i], r.corridor[i + 1])
        }
        return sum
    }
}
