package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.HeadsUp
import com.akkageugot.demo.contract.HeadsUpButton
import com.akkageugot.demo.contract.HeadsUpKind
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.SavedStatus
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.contract.VoicePhase

object CategoryLabels {
    fun korean(category: Category?): String = when (category) {
        Category.BAKERY -> "빵집"
        Category.CAFE -> "카페"
        Category.RESTAURANT -> "식당"
        Category.FLOWER -> "꽃집"
        Category.CONVENIENCE -> "편의점"
        Category.ETC -> "가게"
        null -> "장소"
    }

    fun korean(side: Side): String = when (side) {
        Side.LEFT -> "왼쪽"
        Side.RIGHT -> "오른쪽"
        Side.UNKNOWN -> ""
    }
}

sealed interface DialogEvent {
    /** MicPressed 또는 adb say를 받은 순간. 이 순간이 t0다 */
    data class UtteranceStarted(val t0Ms: Long) : DialogEvent
    data class UtteranceFinished(val text: String, val nowMs: Long) : DialogEvent
    /** STT가 아무것도 못 받았을 때 */
    data class UtteranceFailed(val nowMs: Long) : DialogEvent
    data class ButtonClicked(val button: HeadsUpButton, val nowMs: Long) : DialogEvent
    /** ANSWERED 상태에서 6초 무응답 */
    data class AnswerTimeout(val nowMs: Long) : DialogEvent
}

sealed interface DialogEffect {
    data class SetPhase(val phase: VoicePhase) : DialogEffect
    data class Speak(val text: String) : DialogEffect
    data class ShowHeadsUp(val headsUp: HeadsUp) : DialogEffect
    data object ClearHeadsUp : DialogEffect
    data class Save(val place: SavedPlace) : DialogEffect
    /** 엔진이 경유지 추가 + "경유지로 넣었어요. N분 늘어나요." 발화까지 처리한다 */
    data class AddWaypoint(val poi: Poi) : DialogEffect
    data object StartAnswerTimer : DialogEffect
    data object CancelAnswerTimer : DialogEffect
    data class SetIntentJson(val json: String) : DialogEffect
}

/**
 * 대화 상태 머신: IDLE → LISTENING → THINKING → ANSWERED (engine/PROMPT.md 6절).
 * 타이머 실행과 음성 입출력은 엔진이 하고, 여기서는 결정만 한다.
 */
class DialogMachine(
    private val backtrack: (t0Ms: Long, intent: ParsedIntent) -> BacktrackResult,
    private val currentPos: () -> Vec2,
    private val nowMs: () -> Long,
    private val labelFor: (createdAtMs: Long) -> String,
    private val newId: () -> String
) {

    enum class State { IDLE, LISTENING, THINKING, ANSWERED }

    var state: State = State.IDLE
        private set

    private var t0Ms: Long = 0
    private var lastUtterance: String = ""
    private var result: BacktrackResult = BacktrackResult.EMPTY
    private var candidateIndex: Int = 0
    private var rejectCount: Int = 0

    /** 마지막 탐색 결과 (debug.corridor, debug.candidates 반영용) */
    val lastResult: BacktrackResult get() = result

    fun onEvent(event: DialogEvent): List<DialogEffect> = when (event) {
        is DialogEvent.UtteranceStarted -> onUtteranceStarted(event)
        is DialogEvent.UtteranceFinished -> onUtteranceFinished(event)
        is DialogEvent.UtteranceFailed -> listOf(DialogEffect.SetPhase(VoicePhase.IDLE)).also {
            if (state == State.LISTENING) state = if (hasActiveAnswer()) State.ANSWERED else State.IDLE
        }
        is DialogEvent.ButtonClicked -> onButtonClicked(event)
        is DialogEvent.AnswerTimeout -> onAnswerTimeout()
    }

    private fun onUtteranceStarted(event: DialogEvent.UtteranceStarted): List<DialogEffect> {
        t0Ms = event.t0Ms
        state = State.LISTENING
        // 듣는 동안 6초 타이머에 걸려 PENDING으로 넘어가지 않게 멈춘다
        return listOf(DialogEffect.CancelAnswerTimer, DialogEffect.SetPhase(VoicePhase.LISTENING))
    }

    private fun onUtteranceFinished(event: DialogEvent.UtteranceFinished): List<DialogEffect> {
        lastUtterance = event.text
        val intent = IntentParser.parse(event.text)
        val effects = mutableListOf<DialogEffect>(
            DialogEffect.SetIntentJson(IntentParser.toJson(intent))
        )
        when (intent.type) {
            IntentType.ASK -> effects += runSearch(intent, autoSave = intent.askAndSave)
            IntentType.SAVE ->
                if (hasActiveAnswer()) effects += doSave(SavedStatus.CONFIRMED)
                else effects += runSearch(intent, autoSave = true)
            IntentType.VISIT ->
                if (hasActiveAnswer()) effects += doVisit()
                else effects += runSearch(intent, autoVisit = true)
            IntentType.REJECT ->
                if (hasActiveAnswer()) effects += nextCandidate()
                else {
                    state = State.IDLE
                    effects += DialogEffect.SetPhase(VoicePhase.IDLE)
                }
            IntentType.MEMO -> {
                // 메모/미분류 발화: 데모 범위에서는 무시하고 대기 상태로 돌아간다
                state = if (hasActiveAnswer()) State.ANSWERED else State.IDLE
                effects += DialogEffect.SetPhase(VoicePhase.IDLE)
            }
        }
        return effects
    }

    private fun runSearch(
        intent: ParsedIntent,
        autoSave: Boolean = false,
        autoVisit: Boolean = false
    ): List<DialogEffect> {
        state = State.THINKING
        val effects = mutableListOf<DialogEffect>(DialogEffect.SetPhase(VoicePhase.THINKING))
        result = backtrack(t0Ms, intent)
        candidateIndex = 0
        rejectCount = 0

        val top = result.candidates.getOrNull(0)
        if (top == null || result.confidence == Confidence.LOW) {
            // LOW: 위치만 저장. 절대 잃어버리지 않는다
            effects += DialogEffect.Speak("지도에선 못 찾았어요. 위치는 저장해 둘게요.")
            effects += DialogEffect.Save(buildPlace(SavedStatus.LOCATION_ONLY, selected = null))
            effects += DialogEffect.ShowHeadsUp(
                HeadsUp(
                    id = newId(),
                    kind = HeadsUpKind.ANSWER,
                    title = "이 근처를 저장했어요",
                    subtitle = "위치만 저장됨",
                    category = null,
                    buttons = listOf(HeadsUpButton.SAVE, HeadsUpButton.VISIT)
                )
            )
            state = State.IDLE
            effects += DialogEffect.SetPhase(VoicePhase.IDLE)
            return effects
        }

        if (autoSave) {
            effects += doSave(SavedStatus.CONFIRMED)
            return effects
        }
        if (autoVisit) {
            effects += doVisit()
            return effects
        }

        val name = top.candidate.poi.name
        val text = when (result.confidence) {
            Confidence.HIGH -> "${name}요. 저장할까요, 들렀다 갈까요?"
            else -> {
                val second = result.candidates.getOrNull(1)
                if (second != null) "아마 $name 같아요. 옆에 ${second.candidate.poi.name}도 있었어요."
                else "아마 $name 같아요."
            }
        }
        effects += DialogEffect.Speak(text)
        effects += DialogEffect.ShowHeadsUp(headsUpFor(top))
        effects += DialogEffect.StartAnswerTimer
        state = State.ANSWERED
        effects += DialogEffect.SetPhase(VoicePhase.IDLE)
        return effects
    }

    private fun onButtonClicked(event: DialogEvent.ButtonClicked): List<DialogEffect> {
        if (!hasActiveAnswer()) {
            return if (event.button == HeadsUpButton.DISMISS) listOf(DialogEffect.ClearHeadsUp) else emptyList()
        }
        return when (event.button) {
            HeadsUpButton.SAVE -> doSave(SavedStatus.CONFIRMED)
            HeadsUpButton.VISIT -> doVisit()
            HeadsUpButton.DISMISS -> {
                state = State.IDLE
                listOf(DialogEffect.CancelAnswerTimer, DialogEffect.ClearHeadsUp)
            }
        }
    }

    private fun onAnswerTimeout(): List<DialogEffect> {
        if (!hasActiveAnswer()) return emptyList()
        // 6초 무응답 → 음성 없이 PENDING 자동 저장
        state = State.IDLE
        return listOf(
            DialogEffect.Save(buildPlace(SavedStatus.PENDING, selected = null)),
            DialogEffect.ClearHeadsUp
        )
    }

    private fun doSave(status: SavedStatus): List<DialogEffect> {
        val current = result.candidates.getOrNull(candidateIndex)
        state = State.IDLE
        return listOf(
            DialogEffect.CancelAnswerTimer,
            DialogEffect.Save(buildPlace(status, selected = current)),
            DialogEffect.Speak("저장했어요. 도착하면 보여드릴게요."),
            DialogEffect.ClearHeadsUp,
            DialogEffect.SetPhase(VoicePhase.IDLE)
        )
    }

    private fun doVisit(): List<DialogEffect> {
        val current = result.candidates.getOrNull(candidateIndex) ?: return emptyList()
        state = State.IDLE
        return listOf(
            DialogEffect.CancelAnswerTimer,
            DialogEffect.AddWaypoint(current.candidate.poi),
            DialogEffect.ClearHeadsUp,
            DialogEffect.SetPhase(VoicePhase.IDLE)
        )
    }

    private fun nextCandidate(): List<DialogEffect> {
        rejectCount++
        val next = result.candidates.getOrNull(candidateIndex + 1)
        if (rejectCount >= 2 || next == null) {
            // 두 번 거절 → 도착해서 고른다. PENDING으로 보관
            state = State.IDLE
            return listOf(
                DialogEffect.CancelAnswerTimer,
                DialogEffect.Save(buildPlace(SavedStatus.PENDING, selected = null)),
                DialogEffect.Speak("도착하면 같이 골라요."),
                DialogEffect.ClearHeadsUp,
                DialogEffect.SetPhase(VoicePhase.IDLE)
            )
        }
        candidateIndex++
        state = State.ANSWERED
        return listOf(
            DialogEffect.Speak("아마 ${next.candidate.poi.name} 같아요."),
            DialogEffect.ShowHeadsUp(headsUpFor(next)),
            DialogEffect.StartAnswerTimer,
            DialogEffect.SetPhase(VoicePhase.IDLE)
        )
    }

    private fun hasActiveAnswer(): Boolean =
        (state == State.ANSWERED || state == State.LISTENING) && result.candidates.isNotEmpty()

    private fun headsUpFor(sc: ScoredCandidate): HeadsUp {
        val poi = sc.candidate.poi
        // subtitle은 만드는 순간 값 고정 (docs/CONTRACT.md)
        val subtitle = listOf(
            CategoryLabels.korean(poi.category),
            CategoryLabels.korean(sc.candidate.side),
            "${sc.passedM.toInt()}m 지나옴"
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        return HeadsUp(
            id = newId(),
            kind = HeadsUpKind.ANSWER,
            title = poi.name,
            subtitle = subtitle,
            category = poi.category,
            buttons = listOf(HeadsUpButton.SAVE, HeadsUpButton.VISIT)
        )
    }

    private fun buildPlace(status: SavedStatus, selected: ScoredCandidate?): SavedPlace {
        val createdAt = nowMs()
        val pos = selected?.candidate?.poi?.pos ?: result.t0Pos ?: currentPos()
        return SavedPlace(
            id = newId(),
            createdAtMs = createdAt,
            label = labelFor(createdAt),
            pos = pos,
            utterance = lastUtterance,
            candidates = result.candidates.map { it.candidate },
            selectedPoiId = selected?.candidate?.poi?.id,
            status = status,
            memo = ""
        )
    }
}
