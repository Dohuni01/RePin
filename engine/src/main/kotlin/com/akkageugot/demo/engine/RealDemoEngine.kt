package com.akkageugot.demo.engine

import android.content.Context
import android.util.Log
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.DebugInfo
import com.akkageugot.demo.contract.DemoEngine
import com.akkageugot.demo.contract.DriveState
import com.akkageugot.demo.contract.EngineSettings
import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.HeadsUp
import com.akkageugot.demo.contract.MapData
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.Speaker
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.contract.VoiceLine
import com.akkageugot.demo.contract.VoicePhase
import com.akkageugot.demo.contract.emptyAppState
import com.akkageugot.demo.engine.core.Backtracker
import com.akkageugot.demo.engine.core.BacktrackResult
import com.akkageugot.demo.engine.core.DemoRoutePlanner
import com.akkageugot.demo.engine.core.DialogEffect
import com.akkageugot.demo.engine.core.DialogEvent
import com.akkageugot.demo.engine.core.DialogMachine
import com.akkageugot.demo.engine.core.EngineClock
import com.akkageugot.demo.engine.core.TrailBuffer
import com.akkageugot.demo.engine.core.TrailSample
import com.akkageugot.demo.engine.data.ScenarioData
import com.akkageugot.demo.engine.data.ScenarioLoader
import com.akkageugot.demo.engine.platform.MockSpeechInput
import com.akkageugot.demo.engine.platform.MockUxRestrictions
import com.akkageugot.demo.engine.platform.MockVehicleSignals
import com.akkageugot.demo.engine.platform.AndroidSpeechOutput
import com.akkageugot.demo.engine.platform.SpeechInput
import com.akkageugot.demo.engine.platform.SpeechOutput
import com.akkageugot.demo.engine.platform.UxRestrictions
import com.akkageugot.demo.engine.platform.VehicleSignals
import com.akkageugot.demo.engine.platform.DemoNavigation
import com.akkageugot.demo.engine.platform.pleos.NaviHelperMirror
import com.akkageugot.demo.engine.platform.pleos.PleosSpeechInput
import com.akkageugot.demo.engine.platform.pleos.PleosSpeechOutput
import com.akkageugot.demo.engine.core.LocalProjection
import com.akkageugot.demo.engine.sim.SimulatedDrive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlin.math.ceil

/**
 * 진짜 엔진. 3단계: SimulatedDrive 틱 루프에 DialogMachine, 목업 음성, 타이머,
 * 저장 목록, 경유지를 연결한다. 모든 상태 변경은 단일 루프 스레드에서만 일어난다.
 * 생성자 시그니처 (Context)는 바꾸지 않는다 (docs/CONTRACT.md).
 */
open class RealDemoEngine(private val context: Context) : DemoEngine {

    private val _state = MutableStateFlow(emptyAppState())
    override val state: StateFlow<AppState> = _state.asStateFlow()

    @Suppress("DEPRECATION")
    private val loopContext = newSingleThreadContext("akka-engine")
    private val scope = CoroutineScope(SupervisorJob() + loopContext)
    private val actions = Channel<UiAction>(Channel.UNLIMITED)

    private lateinit var data: ScenarioData
    private lateinit var drive: SimulatedDrive
    private lateinit var navigation: DemoNavigation
    private var naviMirror: NaviHelperMirror? = null
    private lateinit var vehicle: VehicleSignals
    private lateinit var ux: UxRestrictions
    protected lateinit var speechInput: SpeechInput
    protected lateinit var speechOutput: SpeechOutput
    private lateinit var dialog: DialogMachine
    private val trail = TrailBuffer()

    private var settings = EngineSettings()
    private var runningScenario: Int? = null
    private var simClockMs = 0L
    private var lastTrailSimMs = Long.MIN_VALUE
    private var lastLogRealMs = 0L
    private var idSeq = 0

    // 대화/음성/카드 상태 (모두 루프 스레드에서만 접근)
    private var voicePhase = VoicePhase.IDLE
    private val voiceLines = mutableListOf<TimedVoiceLine>()
    private var headsUp: HeadsUp? = null
    private val saved = mutableListOf<SavedPlace>()
    private var lastResult: BacktrackResult = BacktrackResult.EMPTY
    private var lastIntentJson = "{}"
    private var answerDeadlineMs: Long? = null
    private var currentWaypoint: Poi? = null
    private var etaDelayMin = 0

    private data class TimedVoiceLine(val line: VoiceLine, val removeAtMs: Long)

    override fun start() {
        data = ScenarioLoader.parse(readAsset("scenarios.json"))
        drive = SimulatedDrive(data.route.points).apply {
            targetSpeedKmh = settings.targetSpeedKmh
            timeScale = settings.timeScale
        }
        navigation = buildNavigation()
        vehicle = MockVehicleSignals(gearProvider = { drive.gear }, speedProvider = { drive.speedKmh })
        ux = MockUxRestrictions(restrictedProvider = { drive.restrictedByMock() })
        speechInput = createSpeechInput()
        speechOutput = createSpeechOutput()
        dialog = DialogMachine(
            backtrack = { t0, intent ->
                Backtracker(data.pois).search(trail.samples(), t0, intent).also { lastResult = it }
            },
            currentPos = { drive.pos },
            nowMs = { System.currentTimeMillis() },
            labelFor = { EngineClock.savedLabel(it) },
            newId = { "sp-${idSeq++}" }
        )

        vehicle.start()
        ux.start()
        speechOutput.start()
        speechInput.setOnResult { text -> onRecognized(text) }
        publish()

        Log.i(TAG, "start(): route=${data.route.points.size}pts pois=${data.pois.size} 루프 시작")
        scope.launch {
            try {
                runLoop()
            } catch (t: Throwable) {
                Log.e(TAG, "runLoop 예외", t)
            }
        }
    }

    /** mirrorToRealNavi면 Pleos NaviHelper 미러를 붙인 DemoNavigation, 아니면 순수 DemoNavigation */
    private fun buildNavigation(): DemoNavigation {
        val mirror = if (settings.mirrorToRealNavi) {
            NaviHelperMirror(context, LocalProjection(data.routeLatLon.first())).also {
                naviMirror = it
                it.start()
            }
        } else {
            naviMirror = null
            null
        }
        return DemoNavigation(data.route.points, mirror)
    }

    /** settings.useSdk["STT"]가 false가 아니면 Pleos STT(실패 시 자동 목업), 명시적 false면 목업 */
    protected open fun createSpeechInput(): SpeechInput =
        if (settings.useSdk["STT"] == false) MockSpeechInput()
        else PleosSpeechInput(context).also { it.initialize() }

    /** settings.useSdk["TTS"]가 false가 아니면 Pleos TTS(실패 시 Android TTS), 명시적 false면 Android TTS */
    protected open fun createSpeechOutput(): SpeechOutput =
        if (settings.useSdk["TTS"] == false) AndroidSpeechOutput(context)
        else PleosSpeechOutput(context)

    override fun stop() {
        vehicle.stop()
        ux.stop()
        releaseSpeechInput()
        speechOutput.stop()
        naviMirror?.stop()
        scope.cancel()
        loopContext.close()
    }

    private fun releaseSpeechInput() {
        speechInput.stop()
        (speechInput as? PleosSpeechInput)?.release()
    }

    override fun dispatch(action: UiAction) {
        actions.trySend(action)
    }

    private suspend fun runLoop() {
        var lastReal = System.currentTimeMillis()
        while (scope.isActive) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()
            val realDt = now - lastReal
            lastReal = now

            drainActions()
            drive.tick(realDt)
            simClockMs += realDt * drive.timeScale
            checkWaypointArrival()

            if (simClockMs - lastTrailSimMs >= TRAIL_INTERVAL_MS) {
                lastTrailSimMs = simClockMs
                trail.add(TrailSample(simClockMs, drive.pos, drive.speedMps, drive.headingRad))
            }
            expireTimers(now)
            if (now - lastLogRealMs >= 1000) {
                lastLogRealMs = now
                logSummary()
            }
            publish()
        }
    }

    private fun drainActions() {
        while (true) {
            val action = actions.tryReceive().getOrNull() ?: break
            handle(action)
        }
    }

    protected open fun handle(action: UiAction) {
        when (action) {
            is UiAction.SetTargetSpeed -> {
                settings = settings.copy(targetSpeedKmh = action.kmh); drive.targetSpeedKmh = action.kmh
            }
            is UiAction.SetTimeScale -> {
                settings = settings.copy(timeScale = action.scale); drive.timeScale = action.scale
            }
            is UiAction.SetGear -> drive.gear = action.gear
            is UiAction.RunScenario -> startScenario(action.n)
            is UiAction.SetSdkEnabled -> {
                settings = settings.copy(useSdk = settings.useSdk + (action.key to action.enabled))
                when (action.key) {
                    "STT" -> {
                        releaseSpeechInput()
                        speechInput = createSpeechInput()
                        speechInput.setOnResult { text -> onRecognized(text) }
                    }
                    "TTS" -> {
                        speechOutput.stop()
                        speechOutput = createSpeechOutput().also { it.start() }
                    }
                }
            }
            is UiAction.SetMirrorToRealNavi -> {
                settings = settings.copy(mirrorToRealNavi = action.enabled)
                naviMirror?.stop()
                navigation = buildNavigation()
            }
            is UiAction.NavigateTo -> navigateToSaved(action.savedId)

            is UiAction.MicPressed -> {
                addVoiceLine(Speaker.USER, "…") // 듣는 중 표시
                apply(dialog.onEvent(DialogEvent.UtteranceStarted(simClockMs)))
                speechInput.start()
            }
            is UiAction.MicReleased -> speechInput.stop()
            is UiAction.TypedUtterance -> speechInput.feedUtterance(action.text)
            is UiAction.HeadsUpClicked ->
                apply(dialog.onEvent(DialogEvent.ButtonClicked(action.button, System.currentTimeMillis())))

            else -> Unit // SelectCandidate/UpdateMemo 는 6단계
        }
    }

    private fun navigateToSaved(savedId: String) {
        val place = saved.firstOrNull { it.id == savedId } ?: return
        val route = navigation.routeToPlace(drive.pos, place.pos, place.label)
        drive.setRoute(route)
        drive.startAt(0.0, Gear.D)
        currentWaypoint = null
        etaDelayMin = 0
        Log.i(TAG, "목적지 설정: ${place.label}")
    }

    private fun onRecognized(text: String) {
        // 듣는 중 표시를 실제 발화로 교체
        voiceLines.removeAll { it.line.speaker == Speaker.USER && it.line.text == "…" }
        addVoiceLine(Speaker.USER, text)
        apply(dialog.onEvent(DialogEvent.UtteranceFinished(text, simClockMs)))
    }

    private fun apply(effects: List<DialogEffect>) {
        val now = System.currentTimeMillis()
        effects.forEach { e ->
            when (e) {
                is DialogEffect.SetPhase -> voicePhase = e.phase
                is DialogEffect.Speak -> { speechOutput.speak(e.text); addVoiceLine(Speaker.APP, e.text) }
                is DialogEffect.ShowHeadsUp -> headsUp = e.headsUp
                is DialogEffect.ClearHeadsUp -> headsUp = null
                is DialogEffect.Save -> upsertSaved(e.place)
                is DialogEffect.AddWaypoint -> addWaypoint(e.poi)
                is DialogEffect.StartAnswerTimer -> answerDeadlineMs = now + ANSWER_TIMEOUT_MS
                is DialogEffect.CancelAnswerTimer -> answerDeadlineMs = null
                is DialogEffect.SetIntentJson -> lastIntentJson = e.json
            }
        }
    }

    private fun expireTimers(now: Long) {
        voiceLines.removeAll { it.removeAtMs <= now }
        answerDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                answerDeadlineMs = null
                apply(dialog.onEvent(DialogEvent.AnswerTimeout(simClockMs)))
            }
        }
    }

    private fun addVoiceLine(speaker: Speaker, text: String) {
        voiceLines += TimedVoiceLine(
            VoiceLine(id = "vl-${idSeq++}", speaker = speaker, text = text),
            removeAtMs = System.currentTimeMillis() + VOICE_LINE_MS
        )
        while (voiceLines.size > MAX_VOICE_LINES) voiceLines.removeAt(0)
    }

    private fun upsertSaved(place: SavedPlace) {
        val i = saved.indexOfFirst { it.id == place.id }
        if (i >= 0) saved[i] = place else saved += place
        Log.i(TAG, "저장: [${place.status}] ${place.label} sel=${place.selectedPoiId}")
    }

    /** VISIT 선택 시 경유지 경로 생성 + 안내. mirrorToRealNavi면 NaviHelper에도 미러링 */
    private fun addWaypoint(poi: Poi) {
        val planned = navigation.addWaypoint(drive.distanceAlongM, poi)
        drive.setRoute(planned.route)
        currentWaypoint = poi
        val cruiseMps = (settings.targetSpeedKmh / 3.6).coerceAtLeast(1.0)
        etaDelayMin = ceil(planned.extraDistanceM / cruiseMps / 60.0).toInt()
        val line = "경유지로 넣었어요. ${etaDelayMin}분 늘어나요."
        speechOutput.speak(line)
        addVoiceLine(Speaker.APP, line)
        Log.i(TAG, "경유지 추가: ${poi.name}, +${etaDelayMin}분")
    }

    /** 가게 20m 이내 도착 시 경유지 해제, 원래 경로로 복귀 (engine/PROMPT.md 6절) */
    private fun checkWaypointArrival() {
        val wp = currentWaypoint ?: return
        if (com.akkageugot.demo.engine.core.Geo.distance(drive.pos, wp.pos) <= DemoRoutePlanner.WAYPOINT_ARRIVE_M) {
            drive.setRoute(navigation.originalRoutePoints)
            currentWaypoint = null
            etaDelayMin = 0
            Log.i(TAG, "경유지 도착: ${wp.name} → 원래 경로 복귀")
        }
    }

    private fun startScenario(n: Int) {
        val scenario = data.scenarios.firstOrNull { it.n == n } ?: return
        drive.setRoute(data.route.points)
        drive.startAt(scenario.startDistanceAlongRouteM, Gear.D)
        trail.clear()
        lastTrailSimMs = simClockMs - TRAIL_INTERVAL_MS
        currentWaypoint = null
        etaDelayMin = 0
        headsUp = null
        answerDeadlineMs = null
        lastResult = BacktrackResult.EMPTY
        runningScenario = n
        navigation.requestDestination("목적지")
        Log.i(TAG, "RunScenario $n: start=${scenario.startDistanceAlongRouteM}m")
    }

    private fun publish() {
        _state.value = buildState()
    }

    protected fun buildState(): AppState {
        val now = System.currentTimeMillis()
        val base = emptyAppState()
        return base.copy(
            map = MapData(
                route = drive.route.points,
                originalRoute = data.route.points,
                turns = drive.turns,
                pois = data.pois,
                waypoint = currentWaypoint,
                destination = navigation.destination
            ),
            drive = DriveState(
                pos = drive.pos,
                headingRad = drive.headingRad,
                speedKmh = drive.speedKmh,
                gear = drive.gear,
                restricted = ux.restricted,
                distanceAlongRouteM = drive.distanceAlongM,
                nextTurn = drive.nextTurn,
                distanceToNextTurnM = drive.distanceToNextTurnM,
                remainingM = drive.remainingM,
                etaText = EngineClock.etaText(drive.remainingM, settings.targetSpeedKmh, now),
                etaDelayMin = etaDelayMin
            ),
            voicePhase = voicePhase,
            voiceLines = voiceLines.map { it.line },
            headsUp = headsUp,
            saved = saved.toList(),
            settings = settings,
            debug = DebugInfo(
                trail = trail.samples().map { it.pos },
                t0Pos = lastResult.t0Pos,
                corridor = lastResult.corridor,
                candidates = lastResult.candidates.map { it.candidate },
                lastIntentJson = lastIntentJson,
                sdkStatus = mapOf(
                    "STT" to speechInput.sourceLabel,
                    "TTS" to speechOutput.sourceLabel,
                    "NAVI" to navigation.mirrorStatus,
                    "VEHICLE" to vehicle.sourceLabel,
                    "UX" to ux.sourceLabel
                )
            ),
            runningScenario = runningScenario
        )
    }

    private fun logSummary() {
        Log.i(
            TAG,
            "gear=${drive.gear} speed=%.1fkm/h along=%.0fm remain=%.0fm restricted=%b phase=%s saved=%d scenario=%s"
                .format(
                    drive.speedKmh, drive.distanceAlongM, drive.remainingM,
                    ux.restricted, voicePhase, saved.size, runningScenario?.toString() ?: "-"
                )
        )
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {
        const val TAG = "AKKA"
        const val TICK_MS = 100L
        const val TRAIL_INTERVAL_MS = 1000L
        const val ANSWER_TIMEOUT_MS = 6000L
        const val VOICE_LINE_MS = 4000L
        const val MAX_VOICE_LINES = 2
    }
}
