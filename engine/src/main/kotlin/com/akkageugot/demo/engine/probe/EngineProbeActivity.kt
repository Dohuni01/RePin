package com.akkageugot.demo.engine.probe

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.EngineProvider
import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.engine.demo.DemoCommandReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 엔진 상태를 보고 조작하는 개발용 화면 (engine/PROMPT.md 4절).
 * 왼쪽: 버튼(말하기/프리셋 발화/기어/시나리오/속도/배속), 오른쪽: AppState 덤프.
 * 음성 관련 버튼은 3단계부터 실제로 동작한다.
 */
class EngineProbeActivity : Activity() {

    private lateinit var dump: TextView
    private var scope: CoroutineScope? = null

    private val presets = DemoCommandReceiver.PRESETS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#101418"))
        }

        val controls = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        controls.addView(buildControls())

        dump = TextView(this).apply {
            setTextColor(Color.parseColor("#D8E0E8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(24, 24, 24, 24)
        }
        val dumpScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f)
            addView(dump)
        }

        root.addView(controls)
        root.addView(dumpScroll)
        setContentView(root)
    }

    private fun buildControls(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        col.addView(label("말하기"))
        col.addView(micButton())
        col.addView(label("프리셋 발화"))
        presets.forEachIndexed { i, text ->
            col.addView(button("${i + 1}. $text") {
                dispatch(UiAction.MicPressed)
                dispatch(UiAction.TypedUtterance(text))
                dispatch(UiAction.MicReleased)
            })
        }

        col.addView(label("기어"))
        col.addView(row(
            button("P") { dispatch(UiAction.SetGear(Gear.P)) },
            button("D") { dispatch(UiAction.SetGear(Gear.D)) }
        ))

        col.addView(label("시나리오"))
        col.addView(row(*(1..5).map { n ->
            button("$n") { dispatch(UiAction.RunScenario(n)) }
        }.toTypedArray()))

        col.addView(label("목표 속도(km/h)"))
        col.addView(row(*listOf(30, 50, 80).map { kmh ->
            button("$kmh") { dispatch(UiAction.SetTargetSpeed(kmh)) }
        }.toTypedArray()))

        col.addView(label("배속"))
        col.addView(row(*listOf(1, 2, 4).map { s ->
            button("x$s") { dispatch(UiAction.SetTimeScale(s)) }
        }.toTypedArray()))

        return col
    }

    private fun micButton(): Button = Button(this).apply {
        text = "말하기 (누르는 동안)"
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dispatch(UiAction.MicPressed); v.performClick(); true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    dispatch(UiAction.MicReleased); true
                }
                else -> false
            }
        }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#7FB2FF"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, 20, 0, 6)
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View): View {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        views.forEach {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inner.addView(it)
        }
        return HorizontalScrollView(this).apply { addView(inner) }
    }

    private fun dispatch(action: UiAction) = EngineProvider.engine.dispatch(action)

    override fun onStart() {
        super.onStart()
        val s = CoroutineScope(Dispatchers.Main + Job())
        scope = s
        s.launch {
            EngineProvider.engine.state.collect { dump.text = render(it) }
        }
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
        super.onStop()
    }

    private fun render(s: AppState): String = buildString {
        appendLine("── DRIVE ──")
        appendLine("gear=${s.drive.gear} speed=%.1f km/h".format(s.drive.speedKmh))
        appendLine("along=%.0f m  remain=%.0f m".format(s.drive.distanceAlongRouteM, s.drive.remainingM))
        appendLine("heading=%.2f rad  restricted=${s.drive.restricted}".format(s.drive.headingRad))
        appendLine("nextTurn=${s.drive.nextTurn?.direction} in ${s.drive.distanceToNextTurnM?.toInt()}m")
        appendLine("eta=${s.drive.etaText}  delay=${s.drive.etaDelayMin}분")
        appendLine("scenario=${s.runningScenario}")
        appendLine()
        appendLine("── VOICE ──")
        appendLine("phase=${s.voicePhase}")
        s.voiceLines.forEach { appendLine("  ${it.speaker}: ${it.text}") }
        s.headsUp?.let {
            appendLine("headsUp: ${it.title} / ${it.subtitle}")
            appendLine("  buttons=${it.buttons}")
        }
        appendLine()
        appendLine("── SAVED (${s.saved.size}) ──")
        s.saved.forEach { appendLine("  [${it.status}] ${it.label} sel=${it.selectedPoiId}") }
        appendLine()
        appendLine("── DEBUG ──")
        appendLine("trail=${s.debug.trail.size} pts  corridor=${s.debug.corridor.size} pts")
        appendLine("candidates=${s.debug.candidates.size}")
        appendLine("intent=${s.debug.lastIntentJson}")
        appendLine("sdk=${s.debug.sdkStatus}")
        appendLine()
        appendLine("── MAP ──")
        appendLine("route=${s.map.route.size} pts  pois=${s.map.pois.size}  turns=${s.map.turns.size}")
        appendLine("waypoint=${s.map.waypoint?.name}")
    }
}
