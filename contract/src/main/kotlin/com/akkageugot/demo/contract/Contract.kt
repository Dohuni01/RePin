package com.akkageugot.demo.contract

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI

// =====================================================================
// 엔진(A, engine/)과 화면(B, ui/)의 계약.
// 0단계(chore/skeleton) 이후 수정 금지.
// 변경이 꼭 필요하면 MERGE_NOTES.md에 제안 → 둘이 합의 → chore/contract-* PR로만 바꾼다.
// 자세한 설명: docs/CONTRACT.md
// =====================================================================

/** 로컬 미터 좌표. 경로 시작점 = (0,0), x = 동쪽 +, y = 북쪽 + */
data class Vec2(val x: Double, val y: Double)

enum class Category { BAKERY, CAFE, RESTAURANT, FLOWER, CONVENIENCE, ETC }
enum class Side { LEFT, RIGHT, UNKNOWN }
enum class Gear { P, D }
enum class SavedStatus { CONFIRMED, PENDING, LOCATION_ONLY }
enum class TurnDirection { LEFT, RIGHT }
enum class Speaker { USER, APP }
enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }
enum class HeadsUpKind { ANSWER, REVISIT }
enum class HeadsUpButton { SAVE, VISIT, DISMISS }

data class Poi(
    val id: String,
    val name: String,
    val category: Category,
    val pos: Vec2,
    val open: Boolean
)

data class TurnPoint(
    val pos: Vec2,
    val distanceAlongRouteM: Double,
    val direction: TurnDirection
)

data class MapData(
    val route: List<Vec2>,          // 지금 따라가는 경로 (경유지 반영)
    val originalRoute: List<Vec2>,  // 시나리오 원래 경로
    val turns: List<TurnPoint>,
    val pois: List<Poi>,            // 지도에 표시할 목업 가게 전체
    val waypoint: Poi?,
    val destination: Vec2
)

data class DriveState(
    val pos: Vec2,
    val headingRad: Double,         // 0 = 동쪽, 반시계 방향 + (수학 좌표계)
    val speedKmh: Double,
    val gear: Gear,
    val restricted: Boolean,        // 주행 중 제한(CarUxRestrictions) 여부
    val distanceAlongRouteM: Double,
    val nextTurn: TurnPoint?,
    val distanceToNextTurnM: Double?,
    val remainingM: Double,
    val etaText: String,            // 예: "오후 3:42 도착"
    val etaDelayMin: Int            // 경유지로 늘어난 분. 0이면 표시 안 함
)

data class ScoreDetail(
    val distance: Double,
    val side: Double,
    val category: Double,
    val time: Double
)

data class Candidate(
    val poi: Poi,
    val score: Double,
    val side: Side,
    val distanceM: Double,
    val detail: ScoreDetail
)

data class HeadsUp(
    val id: String,
    val kind: HeadsUpKind,
    val title: String,              // 가게 이름 (위치만 저장이면 "이 근처를 저장했어요")
    val subtitle: String,           // 예: "빵집 · 오른쪽 · 150m 지나옴" — 엔진이 만드는 순간 값 고정
    val category: Category?,        // 아이콘용. null이면 위치만 저장
    val buttons: List<HeadsUpButton> // ANSWER = [SAVE, VISIT], REVISIT = [VISIT, DISMISS]
)

data class VoiceLine(
    val id: String,
    val speaker: Speaker,
    val text: String
)

data class SavedPlace(
    val id: String,
    val createdAtMs: Long,
    val label: String,              // 예: "오전 8:12 · 출근길"
    val pos: Vec2,
    val utterance: String,
    val candidates: List<Candidate>, // 최대 3개
    val selectedPoiId: String?,
    val status: SavedStatus,
    val memo: String
)

data class DebugInfo(
    val trail: List<Vec2>,
    val t0Pos: Vec2?,
    val corridor: List<Vec2>,       // 탐색 구간 궤적 조각
    val candidates: List<Candidate>,
    val lastIntentJson: String,
    val sdkStatus: Map<String, String> // 예: "STT" to "Pleos SDK" 또는 "목업"
)

data class EngineSettings(
    val targetSpeedKmh: Int = 50,
    val timeScale: Int = 1,
    val useSdk: Map<String, Boolean> = emptyMap(), // 키: STT, TTS, NAVI, VEHICLE, UX
    val mirrorToRealNavi: Boolean = false
)

data class AppState(
    val map: MapData,
    val drive: DriveState,
    val voicePhase: VoicePhase,
    val voiceLines: List<VoiceLine>, // 화면에 보일 것만, 최대 2개 (엔진이 4초 뒤 제거)
    val headsUp: HeadsUp?,           // 엔진이 6초 뒤 null로 바꾼다
    val saved: List<SavedPlace>,
    val debug: DebugInfo,
    val settings: EngineSettings,
    val runningScenario: Int?
)

sealed interface UiAction {
    data object MicPressed : UiAction                      // 이 순간이 t0
    data object MicReleased : UiAction
    data class TypedUtterance(val text: String) : UiAction
    data class HeadsUpClicked(val id: String, val button: HeadsUpButton) : UiAction
    data class SelectCandidate(val savedId: String, val poiId: String?) : UiAction // null = 위치만
    data class UpdateMemo(val savedId: String, val memo: String) : UiAction
    data class NavigateTo(val savedId: String) : UiAction
    data class SetTargetSpeed(val kmh: Int) : UiAction
    data class SetTimeScale(val scale: Int) : UiAction
    data class SetGear(val gear: Gear) : UiAction
    data class RunScenario(val n: Int) : UiAction
    data class SetSdkEnabled(val key: String, val enabled: Boolean) : UiAction
    data class SetMirrorToRealNavi(val enabled: Boolean) : UiAction
}

interface DemoEngine {
    val state: StateFlow<AppState>
    fun dispatch(action: UiAction)
    fun start()
    fun stop()
}

/** :app의 Application.onCreate에서 flavor(fake/real)에 따라 설정한다 */
object EngineProvider {
    lateinit var engine: DemoEngine
}

fun emptyAppState(): AppState = AppState(
    map = MapData(
        route = emptyList(),
        originalRoute = emptyList(),
        turns = emptyList(),
        pois = emptyList(),
        waypoint = null,
        destination = Vec2(0.0, 0.0)
    ),
    drive = DriveState(
        pos = Vec2(0.0, 0.0),
        headingRad = PI / 2,
        speedKmh = 0.0,
        gear = Gear.P,
        restricted = false,
        distanceAlongRouteM = 0.0,
        nextTurn = null,
        distanceToNextTurnM = null,
        remainingM = 0.0,
        etaText = "",
        etaDelayMin = 0
    ),
    voicePhase = VoicePhase.IDLE,
    voiceLines = emptyList(),
    headsUp = null,
    saved = emptyList(),
    debug = DebugInfo(
        trail = emptyList(),
        t0Pos = null,
        corridor = emptyList(),
        candidates = emptyList(),
        lastIntentJson = "{}",
        sdkStatus = emptyMap()
    ),
    settings = EngineSettings(),
    runningScenario = null
)
