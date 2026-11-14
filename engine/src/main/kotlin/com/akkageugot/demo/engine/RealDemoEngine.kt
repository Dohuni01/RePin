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
import com.akkageugot.demo.contract.SavedStatus
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
import com.akkageugot.demo.engine.core.RevisitWatcher
import com.akkageugot.demo.engine.core.TrailBuffer
import com.akkageugot.demo.engine.core.TrailSample
import com.akkageugot.demo.contract.HeadsUpButton
import com.akkageugot.demo.contract.HeadsUpKind
import com.akkageugot.demo.engine.core.CategoryLabels
import com.akkageugot.demo.engine.data.ScenarioData
import com.akkageugot.demo.engine.data.ScenarioLoader
import com.akkageugot.demo.engine.data.ScenarioStepJson
import com.akkageugot.demo.engine.data.SavedPlaceRepository
import com.akkageugot.demo.engine.data.SettingsStore
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
    private lateinit var savedRepo: SavedPlaceRepository
    private lateinit var settingsStore: SettingsStore
    private val revisitWatcher = RevisitWatcher()
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
    private var revisitClearDeadlineMs: Long? = null
    private var currentWaypoint: Poi? = null
    private var revisitPoi: Poi? = null
    private var revisitPlace: SavedPlace? = null
    private var etaDelayMin = 0
    private var lastRevisitCheckMs = 0L
    private val scenarioSteps = mutableListOf<StepRuntime>()

    private data class TimedVoiceLine(val line: VoiceLine, val removeAtMs: Long)

    /** 시나리오 자동 발화 한 단계의 진행 상태 */
    private class StepRuntime(val step: ScenarioStepJson) {
        var fireAtSimMs: Long? = null // 트리거 지점을 지난 뒤 발화 예정 시각(sim)
        var fired = false
    }

    override fun start() {
        data = ScenarioLoader.parse(readAsset("scenarios.json"))
        settingsStore = SettingsStore(context)
        settings = settingsStore.load()
        savedRepo = SavedPlaceRepository(context)
        saved.addAll(savedRepo.load())
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
            runScenarioSteps()
            checkRevisit(now)

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
                persistSettings()
            }
            is UiAction.SetTimeScale -> {
                settings = settings.copy(timeScale = action.scale); drive.timeScale = action.scale
                persistSettings()
            }
            is UiAction.SetGear -> drive.gear = action.gear
            is UiAction.RunScenario -> startScenario(action.n)
            is UiAction.SetSdkEnabled -> {
                settings = settings.copy(useSdk = settings.useSdk + (action.key to action.enabled))
                persistSettings()
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
                persistSettings()
                naviMirror?.stop()
                navigation = buildNavigation()
            }
            is UiAction.NavigateTo -> navigateToSaved(action.savedId)
            is UiAction.SelectCandidate -> selectCandidate(action.savedId, action.poiId)
            is UiAction.UpdateMemo -> updateMemo(action.savedId, action.memo)

            is UiAction.MicPressed -> {
                addVoiceLine(Speaker.USER, "…") // 듣는 중 표시
                apply(dialog.onEvent(DialogEvent.UtteranceStarted(simClockMs)))
                speechInput.start()
            }
            is UiAction.MicReleased -> speechInput.stop()
            is UiAction.TypedUtterance -> speechInput.feedUtterance(action.text)
            is UiAction.HeadsUpClicked -> {
                if (headsUp?.kind == HeadsUpKind.REVISIT) handleRevisitClick(action.button)
                else apply(dialog.onEvent(DialogEvent.ButtonClicked(action.button, System.currentTimeMillis())))
            }

            else -> Unit
        }
    }

    private fun selectCandidate(savedId: String, poiId: String?) {
        val i = saved.indexOfFirst { it.id == savedId }
        if (i < 0) return
        saved[i] = saved[i].copy(selectedPoiId = poiId)
        savedRepo.save(saved)
    }

    private fun updateMemo(savedId: String, memo: String) {
        val i = saved.indexOfFirst { it.id == savedId }
        if (i < 0) return
        saved[i] = saved[i].copy(memo = memo)
        savedRepo.save(saved)
    }

    private fun handleRevisitClick(button: HeadsUpButton) {
        val wp = revisitPoi
        // 눌렀으면(들르기든 닫기든) 그 장소는 오늘 다시 알리지 않는다 (재출현 버그 수정)
        revisitPlace?.let { revisitWatcher.markHandled(it, System.currentTimeMillis()) }
        if (wp != null && button == HeadsUpButton.VISIT) addWaypoint(wp)
        headsUp = null
        revisitClearDeadlineMs = null
        revisitPoi = null
        revisitPlace = null
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
        // REVISIT 카드는 6초 무응답이면 조용히 사라진다 (저장은 그대로 유지)
        revisitClearDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                revisitClearDeadlineMs = null
                if (headsUp?.kind == HeadsUpKind.REVISIT) headsUp = null
                revisitPoi = null
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
        // 같은 가게(selectedPoiId)를 CONFIRMED로 다시 저장하면 새 항목을 쌓지 않고 기존 걸 갱신한다
        val dupIndex = if (place.status == SavedStatus.CONFIRMED && place.selectedPoiId != null) {
            saved.indexOfFirst { it.status == SavedStatus.CONFIRMED && it.selectedPoiId == place.selectedPoiId }
        } else -1
        val i = if (dupIndex >= 0) dupIndex else saved.indexOfFirst { it.id == place.id }
        if (i >= 0) saved[i] = place else saved += place
        savedRepo.save(saved)
        Log.i(TAG, "저장: [${place.status}] ${place.label} sel=${place.selectedPoiId}")
    }

    private fun persistSettings() = settingsStore.save(settings)

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
        if (scenario.timeScale > 0) {
            drive.timeScale = scenario.timeScale
            settings = settings.copy(timeScale = scenario.timeScale) // 화면 표시용(저장은 안 함)
        }
        trail.clear()
        lastTrailSimMs = simClockMs - TRAIL_INTERVAL_MS
        currentWaypoint = null
        etaDelayMin = 0
        headsUp = null
        answerDeadlineMs = null
        lastResult = BacktrackResult.EMPTY
        revisitPoi = null
        revisitClearDeadlineMs = null
        scenarioSteps.clear()
        scenario.steps.forEach { scenarioSteps += StepRuntime(it) }
        runningScenario = n
        navigation.requestDestination("목적지")
        Log.i(TAG, "RunScenario $n: start=${scenario.startDistanceAlongRouteM}m, steps=${scenario.steps.size}")
    }

    /** 시나리오 자동 발화: 트리거 지점을 지나면 delaySec(sim) 뒤 프리셋 발화를 넣는다 */
    private fun runScenarioSteps() {
        if (scenarioSteps.isEmpty()) return
        scenarioSteps.forEach { rt ->
            if (rt.fired) return@forEach
            if (rt.fireAtSimMs == null && drive.distanceAlongM >= rt.step.triggerDistanceAlongRouteM) {
                rt.fireAtSimMs = simClockMs + (rt.step.delaySec * 1000).toLong()
            }
            val fireAt = rt.fireAtSimMs
            if (fireAt != null && simClockMs >= fireAt) {
                rt.fired = true
                firePreset(rt.step.preset)
            }
        }
    }

    /** adb say와 동일 경로로 프리셋 발화 자동 입력 */
    private fun firePreset(preset: Int) {
        val text = com.akkageugot.demo.engine.demo.DemoCommandReceiver.PRESETS.getOrNull(preset - 1) ?: return
        Log.i(TAG, "시나리오 자동 발화 preset $preset: $text")
        addVoiceLine(Speaker.USER, "…")
        apply(dialog.onEvent(DialogEvent.UtteranceStarted(simClockMs)))
        speechInput.start()
        speechInput.feedUtterance(text)
        speechInput.stop()
    }

    /** CONFIRMED 저장 장소가 300m 앞·경로 60m 이내면 REVISIT 안내 (engine/PROMPT.md 6절) */
    private fun checkRevisit(nowReal: Long) {
        if (nowReal - lastRevisitCheckMs < 1000) return
        lastRevisitCheckMs = nowReal
        if (headsUp != null || drive.gear != Gear.D) return
        val hit = revisitWatcher.check(saved, drive.route, drive.distanceAlongM, System.currentTimeMillis()) ?: return

        val poi = resolvePoi(hit.place) ?: return
        val side = hit.place.candidates.firstOrNull { it.poi.id == poi.id }?.side
        val sideWord = side?.let { CategoryLabels.korean(it) } ?: ""
        val subtitle = listOf(CategoryLabels.korean(poi.category), sideWord, "${hit.aheadM.toInt()}m 앞")
            .filter { it.isNotEmpty() }.joinToString(" · ")
        revisitPoi = poi
        revisitPlace = hit.place
        headsUp = HeadsUp(
            id = "hu-${idSeq++}",
            kind = HeadsUpKind.REVISIT,
            title = poi.name,
            subtitle = subtitle,
            category = poi.category,
            buttons = listOf(HeadsUpButton.VISIT, HeadsUpButton.DISMISS)
        )
        revisitClearDeadlineMs = nowReal + ANSWER_TIMEOUT_MS
        val line = "지난번 저장한 ${poi.name}가 ${hit.aheadM.toInt()}m 앞 ${sideWord}이에요. 들를까요?"
        speechOutput.speak(line)
        addVoiceLine(Speaker.APP, line)
        Log.i(TAG, "REVISIT: ${poi.name} ${hit.aheadM.toInt()}m 앞")
    }

    private fun resolvePoi(place: SavedPlace): Poi? =
        place.selectedPoiId?.let { id -> data.pois.firstOrNull { it.id == id } }
            ?: data.pois.minByOrNull {
                com.akkageugot.demo.engine.core.Geo.distance(it.pos, place.pos)
            }?.takeIf {
                com.akkageugot.demo.engine.core.Geo.distance(it.pos, place.pos) < 30.0
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
