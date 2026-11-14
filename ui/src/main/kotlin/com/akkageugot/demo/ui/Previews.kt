package com.akkageugot.demo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.DebugInfo
import com.akkageugot.demo.contract.DriveState
import com.akkageugot.demo.contract.EngineSettings
import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.HeadsUp
import com.akkageugot.demo.contract.HeadsUpButton
import com.akkageugot.demo.contract.HeadsUpKind
import com.akkageugot.demo.contract.MapData
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.Speaker
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.contract.VoiceLine
import com.akkageugot.demo.contract.VoicePhase
import com.akkageugot.demo.ui.common.AkkaTheme
import com.akkageugot.demo.ui.dev.UiFlags
import com.akkageugot.demo.ui.fake.FakeWorld
import com.akkageugot.demo.ui.geo.RouteGeo
import com.akkageugot.demo.ui.geo.computeTurns
import com.akkageugot.demo.ui.parked.QrDialogContent

// 미리보기용 가짜 AppState. 엔진 없이 직접 만들어 넣는다.

private const val W = 1280
private const val H = 640

private fun previewState(
    d: Double = 570.0,
    gear: Gear = Gear.D,
    route: List<Vec2> = FakeWorld.route
): AppState {
    val geo = RouteGeo(route)
    val turns = computeTurns(route)
    val next = turns.firstOrNull { it.distanceAlongRouteM > d }
    return AppState(
        map = MapData(route, FakeWorld.route, turns, FakeWorld.pois, null, FakeWorld.route.last()),
        drive = DriveState(
            pos = geo.posAt(d),
            headingRad = geo.headingAt(d),
            speedKmh = if (gear == Gear.D) 50.0 else 0.0,
            gear = gear,
            restricted = gear == Gear.D,
            distanceAlongRouteM = d,
            nextTurn = next,
            distanceToNextTurnM = next?.let { it.distanceAlongRouteM - d },
            remainingM = geo.length - d,
            etaText = "오후 3:42 도착",
            etaDelayMin = 0
        ),
        voicePhase = VoicePhase.IDLE,
        voiceLines = emptyList(),
        headsUp = null,
        saved = FakeWorld.sampleSaved(System.currentTimeMillis()),
        debug = DebugInfo(emptyList(), null, emptyList(), emptyList(), "{}", emptyMap()),
        settings = EngineSettings(),
        runningScenario = null
    )
}

private val askLines = listOf(
    VoiceLine("v1", Speaker.USER, "방금 오른쪽에 있던 빵집 뭐야?"),
    VoiceLine("v2", Speaker.APP, "달빛제과요. 저장할까요, 들렀다 갈까요?")
)

private val answerCard = HeadsUp(
    "h1", HeadsUpKind.ANSWER, "달빛제과", "빵집 · 오른쪽 · 150m 지나옴",
    Category.BAKERY, listOf(HeadsUpButton.SAVE, HeadsUpButton.VISIT)
)

@Composable
private fun Frame(state: AppState, flags: UiFlags = UiFlags(), devOpen: Boolean = false) {
    AkkaTheme(day = flags.dayTheme) {
        DemoScreen(state, flags, onFlags = {}, dispatch = {}, initialDevOpen = devOpen)
    }
}

@Preview(name = "1 주행 기본", widthDp = W, heightDp = H)
@Composable
private fun PreviewDriving() = Frame(previewState())

@Preview(name = "2 듣는 중", widthDp = W, heightDp = H)
@Composable
private fun PreviewListening() = Frame(previewState().copy(voicePhase = VoicePhase.LISTENING))

@Preview(name = "3 카드 HIGH", widthDp = W, heightDp = H)
@Composable
private fun PreviewCardHigh() = Frame(
    previewState().copy(voicePhase = VoicePhase.SPEAKING, voiceLines = askLines, headsUp = answerCard)
)

@Preview(name = "4 카드 위치만", widthDp = W, heightDp = H)
@Composable
private fun PreviewCardLocationOnly() = Frame(
    previewState(d = 2050.0).copy(
        voiceLines = listOf(
            VoiceLine("v1", Speaker.USER, "방금 거 뭐야?"),
            VoiceLine("v2", Speaker.APP, "지도에선 못 찾았어요. 위치는 저장해 둘게요.")
        ),
        headsUp = HeadsUp(
            "h2", HeadsUpKind.ANSWER, "이 근처를 저장했어요", "위치만 저장 · 정차하면 확인해요",
            null, listOf(HeadsUpButton.SAVE, HeadsUpButton.VISIT)
        )
    )
)

@Preview(name = "5 REVISIT 카드", widthDp = W, heightDp = H)
@Composable
private fun PreviewCardRevisit() = Frame(
    previewState(d = 120.0).copy(
        voiceLines = listOf(VoiceLine("v1", Speaker.APP, "지난번 저장한 달빛제과가 300m 앞 오른쪽이에요. 들를까요?")),
        headsUp = HeadsUp(
            "h3", HeadsUpKind.REVISIT, "달빛제과", "빵집 · 300m 앞 오른쪽",
            Category.BAKERY, listOf(HeadsUpButton.VISIT, HeadsUpButton.DISMISS)
        )
    )
)

@Preview(name = "6 경유지 추가 후", widthDp = W, heightDp = H)
@Composable
private fun PreviewWaypoint() {
    // 달빛제과(420m 지점)를 지나친 뒤 블록을 돌아가는 경로
    val route = listOf(
        Vec2(0.0, 0.0), Vec2(0.0, 630.0), Vec2(80.0, 630.0), Vec2(80.0, 390.0), Vec2(0.0, 390.0),
        Vec2(0.0, 1000.0), Vec2(1200.0, 1000.0), Vec2(1200.0, 1800.0)
    )
    val base = previewState(d = 575.0, route = route)
    Frame(
        base.copy(
            map = base.map.copy(waypoint = FakeWorld.bakery),
            drive = base.drive.copy(etaDelayMin = 3),
            voiceLines = listOf(VoiceLine("v1", Speaker.APP, "경유지로 넣었어요. 3분 늘어나요."))
        )
    )
}

@Preview(name = "7 정차 목록", widthDp = W, heightDp = H)
@Composable
private fun PreviewParked() = Frame(previewState(d = 3000.0, gear = Gear.P))

@Preview(name = "8 QR 다이얼로그", widthDp = W, heightDp = H)
@Composable
private fun PreviewQr() {
    val state = previewState(d = 3000.0, gear = Gear.P)
    AkkaTheme {
        Box(Modifier.fillMaxSize()) {
            DemoScreen(state, UiFlags(), onFlags = {}, dispatch = {})
            Box(Modifier.align(Alignment.Center)) { QrDialogContent(state.saved.first(), onDismiss = {}) }
        }
    }
}

@Preview(name = "9 발표자 모드 + 디버그 오버레이", widthDp = W, heightDp = H)
@Composable
private fun PreviewPresenterDebug() {
    val base = previewState()
    Frame(
        base.copy(
            voiceLines = askLines,
            headsUp = answerCard,
            debug = DebugInfo(
                trail = (27..57).map { FakeWorld.geo.posAt(it * 10.0) },
                t0Pos = FakeWorld.geo.posAt(570.0),
                corridor = FakeWorld.geo.slice(459.0, 584.0),
                candidates = FakeWorld.bakeryCandidates,
                lastIntentJson = """{"type":"ASK","category":"BAKERY","side":"RIGHT","timeOffsetSec":0}""",
                sdkStatus = emptyMap()
            )
        ),
        flags = UiFlags(presenter = true, debugOverlay = true)
    )
}

@Preview(name = "10 설정 화면", widthDp = W, heightDp = H)
@Composable
private fun PreviewDev() = Frame(previewState(d = 3000.0, gear = Gear.P), devOpen = true)

@Preview(name = "11 주간 테마", widthDp = W, heightDp = H)
@Composable
private fun PreviewDay() = Frame(
    previewState().copy(voiceLines = askLines, headsUp = answerCard),
    flags = UiFlags(dayTheme = true)
)
