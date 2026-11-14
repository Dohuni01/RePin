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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogMachineTest {

    private fun poi(id: String, name: String, category: Category) =
        Poi(id, name, category, Vec2(100.0, -15.0), open = true)

    private fun scored(poi: Poi, score: Double, side: Side = Side.RIGHT, passedM: Double = 150.0) =
        ScoredCandidate(
            candidate = Candidate(poi, score, side, 15.0, ScoreDetail(0.8, 1.0, 1.0, 1.0)),
            tPassMs = 0L,
            passedM = passedM
        )

    private fun machine(result: BacktrackResult): DialogMachine {
        var seq = 0
        return DialogMachine(
            backtrack = { _, _ -> result },
            currentPos = { Vec2(0.0, 0.0) },
            nowMs = { 123_000L },
            labelFor = { "오전 8:12 · 주행" },
            newId = { "id${seq++}" }
        )
    }

    private val bakery = poi("p05", "달빛제과", Category.BAKERY)
    private val cafe = poi("p09", "오후세시커피", Category.CAFE)

    private fun highResult() = BacktrackResult(
        candidates = listOf(scored(bakery, 0.9), scored(cafe, 0.6)),
        confidence = Confidence.HIGH,
        corridor = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
        t0Pos = Vec2(100.0, 0.0)
    )

    private fun ask(m: DialogMachine, text: String = "방금 오른쪽에 있던 빵집 뭐야?"): List<DialogEffect> {
        m.onEvent(DialogEvent.UtteranceStarted(10_000L))
        return m.onEvent(DialogEvent.UtteranceFinished(text, 11_000L))
    }

    @Test
    fun `HIGH - 이름과 제안을 말하고 카드를 띄운다`() {
        val m = machine(highResult())
        val effects = ask(m)
        val speak = effects.filterIsInstance<DialogEffect.Speak>().single()
        assertEquals("달빛제과요. 저장할까요, 들렀다 갈까요?", speak.text)
        val headsUp = effects.filterIsInstance<DialogEffect.ShowHeadsUp>().single().headsUp
        assertEquals("달빛제과", headsUp.title)
        assertEquals("빵집 · 오른쪽 · 150m 지나옴", headsUp.subtitle)
        assertEquals(listOf(HeadsUpButton.SAVE, HeadsUpButton.VISIT), headsUp.buttons)
        assertTrue(effects.any { it is DialogEffect.StartAnswerTimer })
        assertEquals(DialogMachine.State.ANSWERED, m.state)
    }

    @Test
    fun `MEDIUM - 아마 같아요, 옆 가게도 알려준다`() {
        val m = machine(highResult().copy(confidence = Confidence.MEDIUM))
        val effects = ask(m)
        val speak = effects.filterIsInstance<DialogEffect.Speak>().single()
        assertEquals("아마 달빛제과 같아요. 옆에 오후세시커피도 있었어요.", speak.text)
    }

    @Test
    fun `저장해 - CONFIRMED로 저장하고 안내한다`() {
        val m = machine(highResult())
        ask(m)
        m.onEvent(DialogEvent.UtteranceStarted(20_000L))
        val effects = m.onEvent(DialogEvent.UtteranceFinished("저장해", 21_000L))
        val save = effects.filterIsInstance<DialogEffect.Save>().single().place
        assertEquals(SavedStatus.CONFIRMED, save.status)
        assertEquals("p05", save.selectedPoiId)
        assertEquals("오전 8:12 · 주행", save.label)
        assertTrue(effects.filterIsInstance<DialogEffect.Speak>().single().text.startsWith("저장했어요"))
        assertEquals(DialogMachine.State.IDLE, m.state)
    }

    @Test
    fun `들렀다 가자 - 경유지 효과를 낸다`() {
        val m = machine(highResult())
        ask(m)
        m.onEvent(DialogEvent.UtteranceStarted(20_000L))
        val effects = m.onEvent(DialogEvent.UtteranceFinished("들렀다 가자", 21_000L))
        assertEquals(bakery, effects.filterIsInstance<DialogEffect.AddWaypoint>().single().poi)
    }

    @Test
    fun `카드 버튼으로도 저장된다`() {
        val m = machine(highResult())
        ask(m)
        val effects = m.onEvent(DialogEvent.ButtonClicked(HeadsUpButton.SAVE, 20_000L))
        assertEquals(SavedStatus.CONFIRMED, effects.filterIsInstance<DialogEffect.Save>().single().place.status)
    }

    @Test
    fun `아니 그 옆에 - 다음 후보로 교체한다`() {
        val m = machine(highResult())
        ask(m)
        m.onEvent(DialogEvent.UtteranceStarted(20_000L))
        val effects = m.onEvent(DialogEvent.UtteranceFinished("아니 그 옆에", 21_000L))
        val headsUp = effects.filterIsInstance<DialogEffect.ShowHeadsUp>().single().headsUp
        assertEquals("오후세시커피", headsUp.title)
        assertEquals(DialogMachine.State.ANSWERED, m.state)
    }

    @Test
    fun `두 번 거절하면 PENDING으로 저장하고 도착 때 고른다`() {
        val m = machine(highResult())
        ask(m)
        m.onEvent(DialogEvent.UtteranceStarted(20_000L))
        m.onEvent(DialogEvent.UtteranceFinished("아니 그 옆에", 21_000L))
        m.onEvent(DialogEvent.UtteranceStarted(22_000L))
        val effects = m.onEvent(DialogEvent.UtteranceFinished("아니 다른 거", 23_000L))
        val save = effects.filterIsInstance<DialogEffect.Save>().single().place
        assertEquals(SavedStatus.PENDING, save.status)
        assertNull(save.selectedPoiId)
        assertEquals(2, save.candidates.size)
        assertEquals("도착하면 같이 골라요.", effects.filterIsInstance<DialogEffect.Speak>().single().text)
    }

    @Test
    fun `6초 무응답 - 음성 없이 PENDING 자동 저장`() {
        val m = machine(highResult())
        ask(m)
        val effects = m.onEvent(DialogEvent.AnswerTimeout(20_000L))
        val save = effects.filterIsInstance<DialogEffect.Save>().single().place
        assertEquals(SavedStatus.PENDING, save.status)
        assertTrue(effects.filterIsInstance<DialogEffect.Speak>().isEmpty())
        assertEquals(DialogMachine.State.IDLE, m.state)
    }

    @Test
    fun `LOW - 위치만 저장하고 카드 category는 null`() {
        val m = machine(BacktrackResult(emptyList(), Confidence.LOW, emptyList(), Vec2(100.0, 0.0)))
        val effects = ask(m, "방금 거 뭐야?")
        assertEquals(
            "지도에선 못 찾았어요. 위치는 저장해 둘게요.",
            effects.filterIsInstance<DialogEffect.Speak>().single().text
        )
        val save = effects.filterIsInstance<DialogEffect.Save>().single().place
        assertEquals(SavedStatus.LOCATION_ONLY, save.status)
        assertEquals(Vec2(100.0, 0.0), save.pos) // t0 위치에 저장
        assertNull(effects.filterIsInstance<DialogEffect.ShowHeadsUp>().single().headsUp.category)
    }

    @Test
    fun `방금 빵집 저장해줘 - 탐색 후 바로 저장`() {
        val m = machine(highResult())
        m.onEvent(DialogEvent.UtteranceStarted(10_000L))
        val effects = m.onEvent(DialogEvent.UtteranceFinished("방금 빵집 저장해줘", 11_000L))
        val save = effects.filterIsInstance<DialogEffect.Save>().single().place
        assertEquals(SavedStatus.CONFIRMED, save.status)
        assertEquals("p05", save.selectedPoiId)
    }

    @Test
    fun `발화 전체가 저장 기록에 남는다`() {
        val m = machine(highResult())
        ask(m)
        val effects = m.onEvent(DialogEvent.AnswerTimeout(20_000L))
        assertEquals(
            "방금 오른쪽에 있던 빵집 뭐야?",
            effects.filterIsInstance<DialogEffect.Save>().single().place.utterance
        )
    }
}
