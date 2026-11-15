package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Candidate
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.HeadsUpButton
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.SavedStatus
import com.akkageugot.demo.contract.ScoreDetail
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.contract.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SideAskAndBuildingTest {

    private fun poi(id: String, name: String, category: Category) =
        Poi(id, name, category, Vec2(100.0, -15.0), open = true)

    private fun scored(poi: Poi, score: Double, side: Side, passedM: Double = 150.0) =
        ScoredCandidate(
            candidate = Candidate(poi, score, side, 15.0, ScoreDetail(0.8, 1.0, 1.0, 1.0)),
            tPassMs = 0L,
            passedM = passedM
        )

    private val rightBakery = poi("p05", "달빛제과", Category.BAKERY)
    private val leftCafe = poi("p09", "오후세시커피", Category.CAFE)
    private val leftBakery = poi("p11", "솔빵집", Category.BAKERY)

    private val sunCafe = poi("b01s0", "볕드는카페", Category.CAFE)
    private val smileMart = poi("b01s1", "스마일마트", Category.CONVENIENCE)
    private val sunBuilding = Building("볕들빌딩", listOf(sunCafe, smileMart))

    /** 호출 기록 (t0, intent)과 함께, intent.side로 후보를 거르는 가짜 역추적 */
    private class FakeBacktrack(private val all: List<ScoredCandidate>) {
        val calls = mutableListOf<Pair<Long, ParsedIntent>>()
        fun search(t0: Long, intent: ParsedIntent): BacktrackResult {
            calls += t0 to intent
            val list = all.filter { intent.side == Side.UNKNOWN || it.candidate.side == intent.side }
                .filter { intent.category == null || it.candidate.poi.category == intent.category }
            return BacktrackResult(
                candidates = list,
                confidence = if (list.isEmpty()) Confidence.LOW else Confidence.HIGH,
                corridor = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                t0Pos = Vec2(100.0, 0.0)
            )
        }
    }

    private fun machine(
        fake: FakeBacktrack,
        buildings: Map<String, Building> = emptyMap(),
        askSide: Boolean = true
    ): DialogMachine {
        var seq = 0
        return DialogMachine(
            backtrack = { t0, intent -> fake.search(t0, intent) },
            currentPos = { Vec2(0.0, 0.0) },
            nowMs = { 123_000L },
            labelFor = { "오전 8:12 · 주행" },
            newId = { "id${seq++}" },
            buildingOf = { buildings[it.id] },
            askSideEnabled = { askSide }
        )
    }

    private fun say(m: DialogMachine, text: String, t0: Long): List<DialogEffect> {
        m.onEvent(DialogEvent.UtteranceStarted(t0))
        return m.onEvent(DialogEvent.UtteranceFinished(text, t0 + 1_000L))
    }

    private fun speech(effects: List<DialogEffect>) =
        effects.filterIsInstance<DialogEffect.Speak>().map { it.text }

    private val bothSides = listOf(scored(rightBakery, 0.9, Side.RIGHT), scored(leftCafe, 0.7, Side.LEFT))

    @Test
    fun `좌우를 안 말했고 양쪽에 가게가 있으면 되묻는다`() {
        val m = machine(FakeBacktrack(bothSides))
        val effects = say(m, "방금 거기 뭐야?", 10_000L)
        assertEquals(listOf("오른쪽이었어요, 왼쪽이었어요?"), speech(effects))
        assertTrue(effects.none { it is DialogEffect.ShowHeadsUp })
        assertTrue(effects.any { it is DialogEffect.StartAnswerTimer })
        assertTrue(m.isAskingSide)
    }

    @Test
    fun `왼쪽이라고 답하면 처음 t0로 왼쪽만 다시 찾는다`() {
        val fake = FakeBacktrack(bothSides)
        val m = machine(fake)
        say(m, "방금 거기 뭐야?", 10_000L)
        val effects = say(m, "왼쪽", 15_000L)
        val last = fake.calls.last()
        assertEquals(10_000L, last.first)
        assertEquals(Side.LEFT, last.second.side)
        assertFalse(m.isAskingSide)
        assertEquals("오후세시커피", effects.filterIsInstance<DialogEffect.ShowHeadsUp>().single().headsUp.title)
        assertEquals(DialogMachine.State.ANSWERED, m.state)
    }

    @Test
    fun `parseSide - 우측 오른편 좌측 왼편을 읽는다`() {
        assertEquals(Side.RIGHT, IntentParser.parseSide("우측이요"))
        assertEquals(Side.RIGHT, IntentParser.parseSide("오른편"))
        assertEquals(Side.LEFT, IntentParser.parseSide("좌측"))
        assertEquals(Side.LEFT, IntentParser.parseSide("왼편이었어"))
        assertEquals(Side.UNKNOWN, IntentParser.parseSide("몰라"))
    }

    @Test
    fun `되물은 뒤 대답이 없으면 좌우 없이 찾는다`() {
        val fake = FakeBacktrack(bothSides)
        val m = machine(fake)
        say(m, "방금 거기 뭐야?", 10_000L)
        val effects = m.onEvent(DialogEvent.AnswerTimeout(20_000L))
        assertEquals(Side.UNKNOWN, fake.calls.last().second.side)
        assertFalse(m.isAskingSide)
        assertEquals(listOf("달빛제과요. 저장할까요, 들렀다 갈까요?"), speech(effects))
    }

    @Test
    fun `한쪽에만 가게가 있으면 바로 답한다`() {
        val m = machine(FakeBacktrack(listOf(scored(rightBakery, 0.9, Side.RIGHT))))
        val effects = say(m, "방금 거기 뭐야?", 10_000L)
        assertEquals(listOf("달빛제과요. 저장할까요, 들렀다 갈까요?"), speech(effects))
        assertFalse(m.isAskingSide)
    }

    @Test
    fun `말한 종류가 한쪽에만 있으면 되묻지 않는다`() {
        // 빵집은 오른쪽에만, 카페는 왼쪽에 있다
        val m = machine(FakeBacktrack(bothSides))
        val effects = say(m, "방금 빵집 뭐야?", 10_000L)
        assertEquals(listOf("달빛제과요. 저장할까요, 들렀다 갈까요?"), speech(effects))
        assertFalse(m.isAskingSide)
    }

    @Test
    fun `같은 종류가 양쪽에 있으면 종류를 말해도 되묻는다`() {
        val m = machine(FakeBacktrack(bothSides + scored(leftBakery, 0.6, Side.LEFT)))
        val effects = say(m, "방금 빵집 뭐야?", 10_000L)
        assertEquals(listOf("오른쪽이었어요, 왼쪽이었어요?"), speech(effects))
    }

    @Test
    fun `askSideEnabled가 false면 되묻지 않는다`() {
        val m = machine(FakeBacktrack(bothSides), askSide = false)
        val effects = say(m, "방금 거기 뭐야?", 10_000L)
        assertEquals(listOf("달빛제과요. 저장할까요, 들렀다 갈까요?"), speech(effects))
        assertFalse(m.isAskingSide)
    }

    private fun buildingMachine(): Pair<DialogMachine, FakeBacktrack> {
        val fake = FakeBacktrack(listOf(scored(sunCafe, 0.9, Side.RIGHT, passedM = 150.0)))
        val m = machine(fake, buildings = mapOf(sunCafe.id to sunBuilding, smileMart.id to sunBuilding))
        return m to fake
    }

    @Test
    fun `건물에 든 가게면 건물 이름과 가게 목록으로 답한다`() {
        val (m, _) = buildingMachine()
        val effects = say(m, "방금 오른쪽 거기 뭐야?", 10_000L)
        assertEquals(listOf("오른쪽 볕들빌딩이에요. 볕드는카페, 스마일마트가 있어요."), speech(effects))
        val card = effects.filterIsInstance<DialogEffect.ShowHeadsUp>().single().headsUp
        assertEquals("볕들빌딩", card.title)
        assertEquals("카페·편의점 2곳 · 오른쪽 · 150m 지나옴", card.subtitle)
        assertEquals(listOf(HeadsUpButton.SAVE, HeadsUpButton.VISIT), card.buttons)
        assertEquals(DialogMachine.State.ANSWERED, m.state)
    }

    @Test
    fun `건물 답 뒤 저장하면 PENDING으로 건물 가게들을 후보로 저장한다`() {
        val (m, _) = buildingMachine()
        say(m, "방금 오른쪽 거기 뭐야?", 10_000L)
        val effects = m.onEvent(DialogEvent.ButtonClicked(HeadsUpButton.SAVE, 12_000L))
        val place = effects.filterIsInstance<DialogEffect.Save>().single().place
        assertEquals(SavedStatus.PENDING, place.status)
        assertNull(place.selectedPoiId)
        assertEquals(listOf("b01s0", "b01s1"), place.candidates.map { it.poi.id })
        assertEquals(listOf("저장했어요. 정차하면 어느 가게인지 골라 주세요."), speech(effects))
    }

    @Test
    fun `종류를 말하면 그 가게로 답하고 건물 이름을 덧붙인다`() {
        val (m, _) = buildingMachine()
        val effects = say(m, "방금 오른쪽 카페 뭐야?", 10_000L)
        assertEquals(listOf("볕드는카페요. 볕들빌딩에 있어요. 저장할까요, 들렀다 갈까요?"), speech(effects))
        val card = effects.filterIsInstance<DialogEffect.ShowHeadsUp>().single().headsUp
        assertEquals("볕드는카페", card.title)
        assertEquals("카페 · 오른쪽 · 150m 지나옴 · 볕들빌딩", card.subtitle)
    }

    @Test
    fun `BuildingText - 받침 없는 건물 이름과 가게 이름`() {
        val b = Building("샛별타워", listOf(poi("b03s0", "밤샘편의점", Category.CONVENIENCE), poi("b03s1", "사계화원", Category.FLOWER)))
        assertEquals("오른쪽 샛별타워예요. 밤샘편의점, 사계화원이 있어요.", BuildingText.speech(Side.RIGHT, b))
    }

    @Test
    fun `시나리오 데이터에서 건물을 읽는다`() {
        val data = TestFixtures.data
        val cafe = data.pois.single { it.id == "b01s0" }
        val b = data.buildingOf(cafe)
        assertEquals("볕들빌딩", b?.name)
        assertEquals(listOf("b01s0", "b01s1"), b?.members?.map { it.id })
        assertEquals(3, data.buildingOf(data.pois.single { it.id == "b02s1" })?.members?.size)
        assertNull(data.buildingOf(data.pois.first { !it.id.startsWith("b0") }))
    }

    // ---------------------------------------------------------------- "옆에 뭐 있어?"

    private fun nearby(effects: List<DialogEffect>) =
        effects.filterIsInstance<DialogEffect.AnswerNearby>().map { it.side }

    @Test
    fun `파서 - 옆에 뭐 있어는 NEARBY, 빵집 질문은 ASK`() {
        assertEquals(IntentType.NEARBY, IntentParser.parse("옆에 뭐 있어?").type)
        assertEquals(IntentType.NEARBY, IntentParser.parse("오른쪽에 뭐있어").type)
        assertEquals(Side.RIGHT, IntentParser.parse("오른쪽에 뭐있어").side)
        val left = IntentParser.parse("왼쪽에 뭐가 있어?")
        assertEquals(IntentType.NEARBY, left.type)
        assertEquals(Side.LEFT, left.side)
        assertEquals(IntentType.ASK, IntentParser.parse("방금 오른쪽에 있던 빵집 뭐야?").type)
    }

    @Test
    fun `옆에 뭐 있어 - 좌우를 먼저 묻고 왼쪽 대답에 왼쪽을 답한다`() {
        val fake = FakeBacktrack(bothSides)
        val m = machine(fake)
        val first = say(m, "옆에 뭐 있어?", 10_000L)
        assertEquals(listOf("오른쪽이요, 왼쪽이요?"), speech(first))
        assertTrue(nearby(first).isEmpty())
        assertTrue(m.isAskingSide)

        val second = say(m, "왼쪽", 13_000L)
        assertEquals(listOf(Side.LEFT), nearby(second))
        assertFalse(m.isAskingSide)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun `오른쪽에 뭐 있어 - 묻지 않고 바로 오른쪽을 답한다`() {
        val m = machine(FakeBacktrack(bothSides))
        val effects = say(m, "오른쪽에 뭐 있어?", 10_000L)
        assertTrue(speech(effects).isEmpty())
        assertEquals(listOf(Side.RIGHT), nearby(effects))
        assertFalse(m.isAskingSide)
    }

    @Test
    fun `지도 버튼도 먼저 묻고 오른쪽이요 대답에 오른쪽을 답한다`() {
        val m = machine(FakeBacktrack(bothSides))
        val first = m.onEvent(DialogEvent.NearbyPressed(10_000L))
        assertEquals(listOf("오른쪽이요, 왼쪽이요?"), speech(first))
        assertTrue(m.isAskingSide)
        assertEquals(listOf(Side.RIGHT), nearby(say(m, "오른쪽이요", 13_000L)))
    }

    @Test
    fun `되묻기 중 대답이 없으면 가장 가까운 건물을 답한다`() {
        val m = machine(FakeBacktrack(bothSides))
        say(m, "옆에 뭐 있어?", 10_000L)
        val effects = m.onEvent(DialogEvent.AnswerTimeout(17_000L))
        assertEquals(listOf(Side.UNKNOWN), nearby(effects))
        assertFalse(m.isAskingSide)
    }

    @Test
    fun `되묻기를 끄면 옆에 뭐 있어도 묻지 않는다`() {
        val m = machine(FakeBacktrack(bothSides), askSide = false)
        val effects = say(m, "옆에 뭐 있어?", 10_000L)
        assertTrue(speech(effects).isEmpty())
        assertEquals(listOf(Side.UNKNOWN), nearby(effects))
    }
}
