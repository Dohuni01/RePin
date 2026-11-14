package com.akkageugot.demo.ui.fake

import android.content.Context
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.Candidate
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.DebugInfo
import com.akkageugot.demo.contract.DemoEngine
import com.akkageugot.demo.contract.DriveState
import com.akkageugot.demo.contract.EngineSettings
import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.HeadsUp
import com.akkageugot.demo.contract.HeadsUpButton
import com.akkageugot.demo.contract.HeadsUpKind
import com.akkageugot.demo.contract.MapData
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.SavedStatus
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.contract.Speaker
import com.akkageugot.demo.contract.TurnPoint
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.contract.VoiceLine
import com.akkageugot.demo.contract.VoicePhase
import com.akkageugot.demo.ui.common.labelKo
import com.akkageugot.demo.ui.geo.RouteGeo
import com.akkageugot.demo.ui.geo.computeTurns
import com.akkageugot.demo.ui.geo.minus
import com.akkageugot.demo.ui.geo.plus
import com.akkageugot.demo.ui.geo.rightNormal
import com.akkageugot.demo.ui.geo.times
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 실제 엔진 없이 모든 화면 상태를 재현하는 가짜 엔진.
 * 진짜 엔진과 똑같이 카드 6초, 말풍선 4초 타이머도 여기서 처리한다 (화면은 타이머를 갖지 않는다).
 */
class FakeDemoEngine(@Suppress("UNUSED_PARAMETER") context: Context) : DemoEngine {

    private enum class AnswerKind { BAKERY, PAIR, NONE }
    private class Timer(val dueMs: Long, val tag: String, val run: () -> Unit)
    private class Pending(val candidates: List<Candidate>, val index: Int, val utterance: String, val pos: Vec2)

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val timers = ArrayList<Timer>()
    private var seq = 0

    // 주행
    private var route: List<Vec2> = FakeWorld.route
    private var geo = RouteGeo(route)
    private var turns: List<TurnPoint> = computeTurns(route)
    private var waypoint: Poi? = null
    private var waypointAlong = Double.MAX_VALUE
    private var d = 0.0
    private var speedKmh = 0.0
    private var gear = Gear.D
    private var etaDelayMin = 0

    // 대화
    private var voicePhase = VoicePhase.IDLE
    private var voiceLines: List<VoiceLine> = emptyList()
    private var headsUp: HeadsUp? = null
    private var pending: Pending? = null
    private var revisitPoi: Poi? = null
    private var locationPoi: Poi? = null // 위치만 저장 카드의 [들르기] 대상
    private var answerKind = AnswerKind.BAKERY
    private var typedText: String? = null
    private var t0D = 0.0

    private var saved: List<SavedPlace> = FakeWorld.sampleSaved(System.currentTimeMillis())
    private var settings = EngineSettings(useSdk = SDK_KEYS.associateWith { false })
    private var runningScenario: Int? = null

    // 디버그
    private val trail = ArrayDeque<Vec2>()
    private var lastTrailD = 0.0
    private var t0Pos: Vec2? = null
    private var corridor: List<Vec2> = emptyList()
    private var debugCandidates: List<Candidate> = emptyList()
    private var lastIntentJson = "{}"

    private val _state = MutableStateFlow(snapshot())
    override val state: StateFlow<AppState> = _state

    // ---------------------------------------------------------------- 생명주기

    override fun start() {
        synchronized(lock) {
            if (job?.isActive == true) return
            job = scope.launch {
                var last = nowMs()
                while (isActive) {
                    delay(TICK_MS)
                    val now = nowMs()
                    val dt = ((now - last) / 1000.0).coerceIn(0.0, 0.3)
                    last = now
                    synchronized(lock) {
                        tick(dt, now)
                        publish()
                    }
                }
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
    }

    override fun dispatch(action: UiAction) {
        synchronized(lock) {
            handle(action)
            publish()
        }
    }

    // ---------------------------------------------------------------- 틱

    private fun tick(dt: Double, now: Long) {
        // 예약된 일 처리 (카드 6초, 말풍선 4초, 시나리오 스크립트)
        val due = timers.filter { it.dueMs <= now }
        if (due.isNotEmpty()) {
            timers.removeAll(due)
            due.sortedBy { it.dueMs }.forEach { it.run() }
        }

        if (gear != Gear.D) return
        val scale = settings.timeScale.coerceIn(1, 8)
        val target = settings.targetSpeedKmh.toDouble()
        val toTurn = turns.firstOrNull { it.distanceAlongRouteM > d + 0.5 }?.let { it.distanceAlongRouteM - d }
        val desired = if (toTurn != null && toTurn < 50.0) min(20.0, target) else target
        speedKmh = if (speedKmh < desired) min(desired, speedKmh + 15.0 * dt * scale)
        else max(desired, speedKmh - 30.0 * dt * scale)
        d += speedKmh / 3.6 * dt * scale

        if (abs(d - lastTrailD) >= TRAIL_STEP_M) {
            lastTrailD = d
            trail.addLast(geo.posAt(d))
            while (trail.size > TRAIL_MAX) trail.removeFirst()
        }
        if (waypoint != null && d >= waypointAlong) {
            waypoint = null
            waypointAlong = Double.MAX_VALUE
            etaDelayMin = 0
        }
        if (d >= geo.length) {
            d = geo.length
            speedKmh = 0.0
            gear = Gear.P
            endScenario()
            addLine(Speaker.APP, "목적지에 도착했어요.")
        }
    }

    // ---------------------------------------------------------------- 액션

    private fun handle(action: UiAction) {
        when (action) {
            UiAction.MicPressed -> micPressed()
            UiAction.MicReleased -> micReleased()
            is UiAction.TypedUtterance -> typed(action.text)
            is UiAction.HeadsUpClicked -> {
                val h = headsUp ?: return
                if (h.id != action.id) return
                when (action.button) {
                    HeadsUpButton.SAVE -> doSave()
                    HeadsUpButton.VISIT -> doVisit()
                    HeadsUpButton.DISMISS -> clearHeadsUp()
                }
            }
            is UiAction.SelectCandidate -> saved = saved.map {
                if (it.id != action.savedId) it
                else it.copy(
                    selectedPoiId = action.poiId,
                    status = if (action.poiId != null) SavedStatus.CONFIRMED else SavedStatus.LOCATION_ONLY
                )
            }
            is UiAction.UpdateMemo -> saved = saved.map {
                if (it.id == action.savedId) it.copy(memo = action.memo) else it
            }
            is UiAction.NavigateTo -> {
                val place = saved.firstOrNull { it.id == action.savedId } ?: return
                val poi = place.candidates.firstOrNull { it.poi.id == place.selectedPoiId }?.poi
                    ?: Poi("loc_${place.id}", place.memo.ifBlank { "저장한 위치" }, Category.ETC, place.pos, true)
                if (d >= geo.length - 1.0) resetDrive(0.0)
                // 경로로 갈 수 없는 위치(뒤쪽 다른 구간)면 데모 경로를 처음부터 다시 달린다
                if (!addWaypoint(poi)) {
                    resetDrive(0.0)
                    addWaypoint(poi)
                }
                addLine(Speaker.APP, "안내를 시작할게요. 출발하면 들렀다 가요.")
            }
            is UiAction.SetTargetSpeed -> settings = settings.copy(targetSpeedKmh = action.kmh.coerceIn(0, 80))
            is UiAction.SetTimeScale -> settings = settings.copy(timeScale = action.scale.coerceIn(1, 4))
            is UiAction.SetGear -> setGear(action.gear)
            is UiAction.RunScenario -> runScenario(action.n)
            is UiAction.SetSdkEnabled -> settings = settings.copy(useSdk = settings.useSdk + (action.key to action.enabled))
            is UiAction.SetMirrorToRealNavi -> settings = settings.copy(mirrorToRealNavi = action.enabled)
        }
    }

    private fun setGear(g: Gear) {
        if (g == gear) return
        if (g == Gear.D && d >= geo.length - 1.0) resetDrive(0.0) // 도착 후 다시 출발하면 처음부터
        gear = g
        if (g == Gear.P) speedKmh = 0.0
    }

    // ---------------------------------------------------------------- 대화 흐름

    private fun micPressed() {
        cancel(TAG_FLOW)
        cancel(TAG_PHASE)
        voicePhase = VoicePhase.LISTENING
        typedText = null
        markT0()
    }

    private fun micReleased() {
        if (voicePhase != VoicePhase.LISTENING) return
        val text = typedText
        typedText = null
        cancel(TAG_FLOW)
        after(1000, TAG_FLOW) { think(text) }
    }

    private fun typed(text: String) {
        if (voicePhase == VoicePhase.LISTENING) {
            typedText = text
            return
        }
        markT0()
        think(text)
    }

    private fun markT0() {
        t0D = d
        t0Pos = geo.posAt(d)
        val mps = speedKmh / 3.6
        corridor = geo.slice(d - (mps * 8.0).coerceIn(30.0, 250.0), d + mps)
        debugCandidates = emptyList()
    }

    private fun think(utterance: String?) {
        val text = utterance ?: when (answerKind) {
            AnswerKind.BAKERY -> "방금 오른쪽에 있던 빵집 뭐야?"
            AnswerKind.PAIR -> "방금 거기 뭐야?"
            AnswerKind.NONE -> "방금 거 뭐야?"
        }
        // 카드가 떠 있을 때의 짧은 대답은 바로 처리한다
        voicePhase = VoicePhase.IDLE
        if (headsUp != null && userCommand(text)) return
        voicePhase = VoicePhase.THINKING
        addLine(Speaker.USER, text)
        cancel(TAG_FLOW)
        after(800, TAG_FLOW) { answer(text) }
    }

    /** "저장해", "들렀다 가자", "아니 그 옆에" 처리. 해당하면 true */
    private fun userCommand(text: String): Boolean {
        val type = when {
            listOf("아니", "그 옆", "다른").any { text.contains(it) } -> "REJECT"
            text.contains("저장") -> "SAVE"
            listOf("들르", "들렀", "들러", "가자", "거기로").any { text.contains(it) } -> "VISIT"
            else -> return false
        }
        if (headsUp == null) return false
        lastIntentJson = """{"type":"$type","category":null,"side":null,"timeOffsetSec":0}"""
        addLine(Speaker.USER, text)
        when (type) {
            "SAVE" -> doSave()
            "VISIT" -> doVisit()
            else -> doReject()
        }
        return true
    }

    private fun answer(utterance: String) {
        val t0 = t0Pos ?: geo.posAt(d)
        when (answerKind) {
            AnswerKind.BAKERY -> {
                lastIntentJson = """{"type":"ASK","category":"BAKERY","side":"RIGHT","timeOffsetSec":0}"""
                debugCandidates = FakeWorld.bakeryCandidates
                pending = Pending(debugCandidates, 0, utterance, t0)
                showAnswerCard()
                speak("달빛제과요. 저장할까요, 들렀다 갈까요?")
            }
            AnswerKind.PAIR -> {
                lastIntentJson = """{"type":"ASK","category":null,"side":null,"timeOffsetSec":0}"""
                debugCandidates = FakeWorld.pairCandidates
                pending = Pending(debugCandidates, 0, utterance, t0)
                showAnswerCard()
                speak("아마 구름빵집 같아요. 옆에 느린오후카페도 있었어요.")
            }
            AnswerKind.NONE -> {
                lastIntentJson = """{"type":"ASK","category":null,"side":null,"timeOffsetSec":0}"""
                debugCandidates = emptyList()
                pending = null
                revisitPoi = null
                addSaved(utterance, t0, emptyList(), null, SavedStatus.LOCATION_ONLY)
                val loc = Poi("loc_${saved.last().id}", "저장한 위치", Category.ETC, t0, true)
                setHeadsUp(
                    HeadsUp(
                        id = "h${++seq}",
                        kind = HeadsUpKind.ANSWER,
                        title = "이 근처를 저장했어요",
                        subtitle = "위치만 저장 · 정차하면 확인해요",
                        category = null,
                        buttons = listOf(HeadsUpButton.SAVE, HeadsUpButton.VISIT) // ANSWER 카드는 항상 [저장, 들르기]
                    )
                )
                locationPoi = loc // setHeadsUp 뒤에 넣는다
                speak("지도에선 못 찾았어요. 위치는 저장해 둘게요.")
            }
        }
    }

    private fun showAnswerCard() {
        val p = pending ?: return
        val c = p.candidates[p.index]
        val along = FakeWorld.alongById[c.poi.id]
        val passed = along?.let { t0D - it }?.takeIf { it in 20.0..400.0 }
            ?.let { (it / 10.0).roundToInt() * 10 } ?: 150
        val sideText = when (c.side) {
            Side.LEFT -> "왼쪽"
            Side.RIGHT -> "오른쪽"
            Side.UNKNOWN -> "근처"
        }
        revisitPoi = null
        locationPoi = null
        setHeadsUp(
            HeadsUp(
                id = "h${++seq}",
                kind = HeadsUpKind.ANSWER,
                title = c.poi.name,
                subtitle = "${c.poi.category.labelKo()} · $sideText · ${passed}m 지나옴",
                category = c.poi.category,
                buttons = listOf(HeadsUpButton.SAVE, HeadsUpButton.VISIT)
            )
        )
    }

    private fun showRevisitCard(poi: Poi) {
        pending = null
        locationPoi = null
        revisitPoi = poi
        setHeadsUp(
            HeadsUp(
                id = "h${++seq}",
                kind = HeadsUpKind.REVISIT,
                title = poi.name,
                subtitle = "${poi.category.labelKo()} · 300m 앞 오른쪽",
                category = poi.category,
                buttons = listOf(HeadsUpButton.VISIT, HeadsUpButton.DISMISS)
            )
        )
        speak("지난번 저장한 ${poi.name}가 300m 앞 오른쪽이에요. 들를까요?")
    }

    private fun setHeadsUp(h: HeadsUp) {
        headsUp = h
        cancel(TAG_HEADS_UP)
        after(6000, TAG_HEADS_UP) {
            // 6초 무응답: 음성 없이 확인 필요로 자동 저장 (절대 잃어버리지 않기)
            pending?.let { addSaved(it.utterance, it.pos, it.candidates, null, SavedStatus.PENDING) }
            clearHeadsUp()
        }
    }

    private fun clearHeadsUp() {
        cancel(TAG_HEADS_UP)
        headsUp = null
        pending = null
        revisitPoi = null
        locationPoi = null
    }

    private fun doSave() {
        val p = pending
        val locationOnly = locationPoi != null
        if (p != null) {
            addSaved(p.utterance, p.pos, p.candidates, p.candidates[p.index].poi.id, SavedStatus.CONFIRMED)
        }
        clearHeadsUp()
        // 위치만 저장 카드는 카드가 뜰 때 이미 저장했다
        speak(if (locationOnly) "위치를 저장해 뒀어요. 정차하면 같이 확인해요." else "저장했어요. 도착하면 보여드릴게요.")
    }

    private fun doVisit() {
        val poi = pending?.let { it.candidates[it.index].poi } ?: revisitPoi ?: locationPoi
        clearHeadsUp()
        if (poi == null) return
        if (addWaypoint(poi)) speak("경유지로 넣었어요. ${etaDelayMin}분 늘어나요.")
        else speak("지금은 들르기 어려워요. 정차하면 다시 안내할게요.")
    }

    private fun doReject() {
        val p = pending ?: return
        if (p.index == 0 && p.candidates.size > 1) {
            pending = Pending(p.candidates, 1, p.utterance, p.pos)
            showAnswerCard()
            speak("그럼 ${p.candidates[1].poi.name}요. 저장할까요?")
        } else {
            addSaved(p.utterance, p.pos, p.candidates, null, SavedStatus.PENDING)
            clearHeadsUp()
            speak("도착하면 같이 골라요.")
        }
    }

    private fun speak(text: String) {
        addLine(Speaker.APP, text)
        voicePhase = VoicePhase.SPEAKING
        cancel(TAG_PHASE)
        after(2500, TAG_PHASE) { voicePhase = VoicePhase.IDLE }
    }

    private fun addLine(speaker: Speaker, text: String) {
        voiceLines = (voiceLines + VoiceLine("v${++seq}", speaker, text)).takeLast(2)
        cancel(TAG_LINES)
        after(4000, TAG_LINES) { voiceLines = emptyList() }
    }

    private fun addSaved(utterance: String, pos: Vec2, candidates: List<Candidate>, selected: String?, status: SavedStatus) {
        if (selected != null && saved.any { it.selectedPoiId == selected && it.status == SavedStatus.CONFIRMED }) return
        val now = System.currentTimeMillis()
        saved = saved + SavedPlace(
            id = "s${++seq}",
            createdAtMs = now,
            label = "${clockText(now)} · 주행 중",
            pos = pos,
            utterance = utterance,
            candidates = candidates.take(3),
            selectedPoiId = selected,
            status = status,
            memo = ""
        )
    }

    // ---------------------------------------------------------------- 경로

    /**
     * 지나친 가게면 블록을 한 바퀴 돌아 다시 그 앞을 지나도록 경로를 꺾는다.
     * 경유지를 넣지 못하면 false (경유지 핀, "+3분"을 남기지 않는다).
     */
    private fun addWaypoint(poi: Poi): Boolean {
        val ahead = geo.project(poi.pos, d, geo.length)
        if (ahead != null && abs(ahead.lateral) <= 45.0 && ahead.along > d + 5.0) {
            setWaypoint(poi, ahead.along) // 앞에 있는 가게: 경로는 그대로
            return true
        }
        val behind = geo.project(poi.pos, 0.0, d) ?: return clearWaypoint()
        val seg = geo.segIndexAt(d)
        if (abs(behind.lateral) > 45.0 || geo.segIndexAt(behind.along) != seg) return clearWaypoint()

        val segStart = geo.distanceAtIndex(seg)
        val segEnd = geo.distanceAtIndex(seg + 1)
        val aheadLen = min(60.0, segEnd - d - 10.0)
        val rejoin = max(segStart + 5.0, behind.along - 30.0)
        if (aheadLen < 15.0 || rejoin >= d) return clearWaypoint()

        val u = geo.dirAt(d)
        val n = u.rightNormal() * (if (behind.lateral < 0) 1.0 else -1.0) // 가게가 있는 쪽으로 돈다
        val cur = geo.posAt(d)
        val a = cur + u * aheadLen
        val b = a + n * 80.0
        val c = b - u * (aheadLen + (d - rejoin))
        val traveled = geo.slice(0.0, d).ifEmpty { listOf(cur) }
        val newRoute = traveled + listOf(a, b, c) + geo.slice(rejoin, geo.length)

        route = newRoute
        geo = RouteGeo(newRoute)
        turns = computeTurns(newRoute)
        val along = geo.project(poi.pos, d + aheadLen + 100.0, geo.length)?.along
            ?: return clearWaypoint()
        setWaypoint(poi, along)
        return true
    }

    private fun setWaypoint(poi: Poi, along: Double) {
        waypoint = poi
        waypointAlong = along
        etaDelayMin = 3
    }

    private fun clearWaypoint(): Boolean {
        waypoint = null
        waypointAlong = Double.MAX_VALUE
        etaDelayMin = 0
        return false
    }

    private fun resetDrive(startD: Double) {
        route = FakeWorld.route
        geo = RouteGeo(route)
        turns = computeTurns(route)
        waypoint = null
        waypointAlong = Double.MAX_VALUE
        etaDelayMin = 0
        d = startD.coerceIn(0.0, geo.length)
        trail.clear()
        lastTrailD = d
        t0Pos = null
        corridor = emptyList()
        debugCandidates = emptyList()
    }

    // ---------------------------------------------------------------- 시나리오

    private fun runScenario(n: Int) {
        if (n !in 1..5) return
        timers.clear()
        voicePhase = VoicePhase.IDLE
        voiceLines = emptyList()
        headsUp = null
        pending = null
        revisitPoi = null
        locationPoi = null
        typedText = null
        if (settings.targetSpeedKmh <= 0) settings = settings.copy(targetSpeedKmh = 50)
        gear = Gear.D
        speedKmh = settings.targetSpeedKmh.toDouble()
        runningScenario = n

        when (n) {
            1 -> { // 확실한 빵집 → 질문 → "저장해" → 목적지 도착 → P
                begin(AnswerKind.BAKERY, 556.0)
                ask()
                after(6800, TAG_SCRIPT) { userCommand("저장해") }
                after(10500, TAG_SCRIPT) { // 발표용 빨리 감기: 목적지 150m 앞으로
                    d = geo.length - 150.0
                    trail.clear()
                    lastTrailD = d
                }
            }
            2 -> { // 확실한 빵집 → 질문 → "들렀다 가자" → 경유지
                begin(AnswerKind.BAKERY, 556.0)
                ask()
                after(6800, TAG_SCRIPT) { userCommand("들렀다 가자") }
                after(14000, TAG_SCRIPT) { endScenario() }
            }
            3 -> { // 빵집 + 카페 → 질문 → "아니 그 옆에" → 2순위 카드로 교체
                begin(AnswerKind.PAIR, 900.0)
                ask()
                after(6800, TAG_SCRIPT) { userCommand("아니 그 옆에") }
                after(14000, TAG_SCRIPT) { endScenario() }
            }
            4 -> { // 데이터 없는 구간 → 질문 → 위치만 저장
                begin(AnswerKind.NONE, 2000.0)
                ask()
                after(12000, TAG_SCRIPT) { endScenario() }
            }
            5 -> { // 저장한 곳 재통과 → REVISIT
                begin(AnswerKind.BAKERY, 100.0)
                addSaved("방금 오른쪽에 있던 빵집 뭐야?", geo.posAt(570.0), FakeWorld.bakeryCandidates, FakeWorld.bakery.id, SavedStatus.CONFIRMED)
                after(1500, TAG_SCRIPT) { showRevisitCard(FakeWorld.bakery) }
                after(9500, TAG_SCRIPT) { endScenario() }
            }
        }
    }

    private fun begin(kind: AnswerKind, startD: Double) {
        answerKind = kind
        resetDrive(startD)
    }

    private fun ask() {
        after(1000, TAG_SCRIPT) { micPressed() }
        after(2500, TAG_SCRIPT) { micReleased() }
    }

    private fun endScenario() {
        runningScenario = null
        answerKind = AnswerKind.BAKERY
    }

    // ---------------------------------------------------------------- 예약

    private fun after(ms: Long, tag: String, run: () -> Unit) {
        timers.add(Timer(nowMs() + ms, tag, run))
    }

    private fun cancel(tag: String) {
        timers.removeAll { it.tag == tag }
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    // ---------------------------------------------------------------- 상태 만들기

    private fun publish() {
        _state.value = snapshot()
    }

    private fun snapshot(): AppState {
        val pos = geo.posAt(d)
        val nextTurn = turns.firstOrNull { it.distanceAlongRouteM > d + 0.5 }
        val remaining = (geo.length - d).coerceAtLeast(0.0)
        val cruiseMps = max(settings.targetSpeedKmh, 30) / 3.6
        val arriveMs = System.currentTimeMillis() + ((remaining / cruiseMps) * 1000).toLong() + etaDelayMin * 60_000L
        return AppState(
            map = MapData(
                route = route,
                originalRoute = FakeWorld.route,
                turns = turns,
                pois = FakeWorld.pois,
                waypoint = waypoint,
                destination = FakeWorld.route.last()
            ),
            drive = DriveState(
                pos = pos,
                headingRad = geo.headingAt(d),
                speedKmh = speedKmh,
                gear = gear,
                restricted = gear == Gear.D,
                distanceAlongRouteM = d,
                nextTurn = nextTurn,
                distanceToNextTurnM = nextTurn?.let { it.distanceAlongRouteM - d },
                remainingM = remaining,
                etaText = "${clockText(arriveMs)} 도착",
                etaDelayMin = etaDelayMin
            ),
            voicePhase = voicePhase,
            voiceLines = voiceLines,
            headsUp = headsUp,
            saved = saved,
            debug = DebugInfo(
                trail = trail.toList(),
                t0Pos = t0Pos,
                corridor = corridor,
                candidates = debugCandidates,
                lastIntentJson = lastIntentJson,
                sdkStatus = SDK_KEYS.associateWith { if (settings.useSdk[it] == true) "Pleos SDK (가짜)" else "목업" }
            ),
            settings = settings,
            runningScenario = runningScenario
        )
    }

    companion object {
        val SDK_KEYS = listOf("STT", "TTS", "NAVI", "VEHICLE", "UX")
        private const val TICK_MS = 100L
        private const val TRAIL_STEP_M = 10.0
        private const val TRAIL_MAX = 60
        private const val TAG_FLOW = "flow"
        private const val TAG_PHASE = "phase"
        private const val TAG_HEADS_UP = "headsUp"
        private const val TAG_LINES = "lines"
        private const val TAG_SCRIPT = "script"

        /** 예: "오후 3:42" */
        fun clockText(epochMs: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
            val h24 = cal.get(Calendar.HOUR_OF_DAY)
            val h12 = if (h24 % 12 == 0) 12 else h24 % 12
            val m = cal.get(Calendar.MINUTE)
            return "${if (h24 < 12) "오전" else "오후"} $h12:${m.toString().padStart(2, '0')}"
        }
    }
}
