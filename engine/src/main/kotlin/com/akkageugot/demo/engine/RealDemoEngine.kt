package com.akkageugot.demo.engine

import android.content.Context
import android.util.Log
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.DebugInfo
import com.akkageugot.demo.contract.DemoEngine
import com.akkageugot.demo.contract.DriveState
import com.akkageugot.demo.contract.EngineSettings
import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.MapData
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.contract.emptyAppState
import com.akkageugot.demo.engine.core.EngineClock
import com.akkageugot.demo.engine.core.TrailBuffer
import com.akkageugot.demo.engine.core.TrailSample
import com.akkageugot.demo.engine.data.ScenarioData
import com.akkageugot.demo.engine.data.ScenarioLoader
import com.akkageugot.demo.engine.platform.MockUxRestrictions
import com.akkageugot.demo.engine.platform.MockVehicleSignals
import com.akkageugot.demo.engine.platform.UxRestrictions
import com.akkageugot.demo.engine.platform.VehicleSignals
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

/**
 * 진짜 엔진. 2단계: SimulatedDrive를 100ms 틱으로 돌리고 AppState(map/drive)를 내보낸다.
 * 모든 상태 변경은 단일 루프 스레드에서만 일어나도록 UiAction을 채널에 큐잉한다.
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
    private lateinit var vehicle: VehicleSignals
    private lateinit var ux: UxRestrictions
    private val trail = TrailBuffer()

    private var settings = EngineSettings()
    private var runningScenario: Int? = null
    private var simClockMs = 0L
    private var lastTrailSimMs = Long.MIN_VALUE
    private var lastLogRealMs = 0L

    override fun start() {
        data = ScenarioLoader.parse(readAsset("scenarios.json"))
        drive = SimulatedDrive(data.route.points).apply {
            targetSpeedKmh = settings.targetSpeedKmh
            timeScale = settings.timeScale
        }
        vehicle = MockVehicleSignals(gearProvider = { drive.gear }, speedProvider = { drive.speedKmh })
        ux = MockUxRestrictions(restrictedProvider = { drive.restrictedByMock() })
        vehicle.start()
        ux.start()
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

    override fun stop() {
        vehicle.stop()
        ux.stop()
        scope.cancel()
        loopContext.close()
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

            if (simClockMs - lastTrailSimMs >= TRAIL_INTERVAL_MS) {
                lastTrailSimMs = simClockMs
                trail.add(
                    TrailSample(simClockMs, drive.pos, drive.speedMps, drive.headingRad)
                )
            }
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
                settings = settings.copy(targetSpeedKmh = action.kmh)
                drive.targetSpeedKmh = action.kmh
            }
            is UiAction.SetTimeScale -> {
                settings = settings.copy(timeScale = action.scale)
                drive.timeScale = action.scale
            }
            is UiAction.SetGear -> drive.gear = action.gear
            is UiAction.RunScenario -> startScenario(action.n)
            is UiAction.SetSdkEnabled ->
                settings = settings.copy(useSdk = settings.useSdk + (action.key to action.enabled))
            is UiAction.SetMirrorToRealNavi ->
                settings = settings.copy(mirrorToRealNavi = action.enabled)
            else -> Unit // 음성/대화/저장 관련은 이후 단계에서 처리
        }
    }

    private fun startScenario(n: Int) {
        val scenario = data.scenarios.firstOrNull { it.n == n } ?: return
        drive.setRoute(data.route.points)
        drive.startAt(scenario.startDistanceAlongRouteM, Gear.D)
        trail.clear()
        lastTrailSimMs = Long.MIN_VALUE
        runningScenario = n
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
                waypoint = null,
                destination = data.route.points.lastOrNull() ?: Vec2(0.0, 0.0)
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
                etaDelayMin = 0
            ),
            settings = settings,
            debug = DebugInfo(
                trail = trail.samples().map { it.pos },
                t0Pos = null,
                corridor = emptyList(),
                candidates = emptyList(),
                lastIntentJson = "{}",
                sdkStatus = mapOf(
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
            "gear=${drive.gear} speed=%.1fkm/h along=%.0fm remain=%.0fm restricted=%b scenario=%s"
                .format(
                    drive.speedKmh, drive.distanceAlongM, drive.remainingM,
                    ux.restricted, runningScenario?.toString() ?: "-"
                )
        )
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {
        const val TAG = "AKKA"
        const val TICK_MS = 100L
        const val TRAIL_INTERVAL_MS = 1000L
    }
}
