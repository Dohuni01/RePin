package com.akkageugot.demo.ui.dev

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.ui.common.IconKind
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.navi.RoundIconButton
import kotlin.math.roundToInt

/** 화면 전용 설정 (엔진 상태가 아님). 재시작해도 유지된다 */
data class UiFlags(
    val presenter: Boolean = false,
    val debugOverlay: Boolean = false,
    val dayTheme: Boolean = true
)

class UiPrefs(context: Context) {
    private val sp = context.getSharedPreferences("akka_ui", Context.MODE_PRIVATE)

    var flags by mutableStateOf(
        UiFlags(
            presenter = sp.getBoolean("presenter", false),
            debugOverlay = sp.getBoolean("debugOverlay", false),
            dayTheme = sp.getBoolean("dayTheme", true)
        )
    )
        private set

    fun update(f: UiFlags) {
        flags = f
        sp.edit()
            .putBoolean("presenter", f.presenter)
            .putBoolean("debugOverlay", f.debugOverlay)
            .putBoolean("dayTheme", f.dayTheme)
            .apply()
    }
}

private val SDK_KEYS = listOf("STT", "TTS", "NAVI", "VEHICLE", "UX")
private val SCENARIO_NAMES = listOf("1 저장", "2 들르기", "3 그 옆에", "4 위치만", "5 다시 지나감")

/** 설정/발표자 화면. 정차 중에만 열린다 (톱니 아이콘) */
@Composable
fun DevScreen(
    state: AppState,
    flags: UiFlags,
    onFlags: (UiFlags) -> Unit,
    dispatch: (UiAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAkkaColors.current
    val s = state.settings
    Column(
        modifier
            .fillMaxSize()
            .background(c.mapBg)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(IconKind.BACK, onClose, size = 60.dp)
            Spacer(Modifier.width(14.dp))
            Text("설정 · 발표자", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        Section("주행")
        var speed by remember(s.targetSpeedKmh) { mutableStateOf(s.targetSpeedKmh.toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("속도 ${speed.roundToInt()}km/h", color = c.text, fontSize = 20.sp, modifier = Modifier.width(150.dp))
            Slider(
                value = speed,
                onValueChange = { speed = it },
                onValueChangeFinished = { dispatch(UiAction.SetTargetSpeed(speed.roundToInt())) },
                valueRange = 0f..80f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("배속", color = c.text, fontSize = 20.sp, modifier = Modifier.width(70.dp))
            for (scale in listOf(1, 2, 4)) {
                Chip("x$scale", s.timeScale == scale) { dispatch(UiAction.SetTimeScale(scale)) }
            }
            Spacer(Modifier.width(18.dp))
            Text("기어", color = c.text, fontSize = 20.sp)
            Chip("P", state.drive.gear == Gear.P) { dispatch(UiAction.SetGear(Gear.P)) }
            Chip("D", state.drive.gear == Gear.D) { dispatch(UiAction.SetGear(Gear.D)) }
        }

        Section("시나리오")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SCENARIO_NAMES.take(3).forEachIndexed { i, name ->
                Chip(name, state.runningScenario == i + 1, Modifier.weight(1f)) { dispatch(UiAction.RunScenario(i + 1)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SCENARIO_NAMES.drop(3).forEachIndexed { i, name ->
                Chip(name, state.runningScenario == i + 4, Modifier.weight(1f)) { dispatch(UiAction.RunScenario(i + 4)) }
            }
        }

        Section("SDK / 목업")
        for (key in SDK_KEYS) {
            ToggleRow(
                title = key,
                badge = state.debug.sdkStatus[key],
                checked = s.useSdk[key] == true,
                onChange = { dispatch(UiAction.SetSdkEnabled(key, it)) }
            )
        }
        ToggleRow("실제 내비로도 보내기", null, s.mirrorToRealNavi) { dispatch(UiAction.SetMirrorToRealNavi(it)) }

        Section("마지막 발화 해석")
        Text(
            state.debug.lastIntentJson, color = c.textDim, fontSize = 16.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceHigh)
                .padding(12.dp)
        )

        Section("화면")
        ToggleRow("발표자 모드", null, flags.presenter) { onFlags(flags.copy(presenter = it)) }
        ToggleRow("디버그 오버레이 (발표자 모드에서만)", null, flags.debugOverlay) { onFlags(flags.copy(debugOverlay = it)) }
        ToggleRow("노을 테마 (끄면 밤)", null, flags.dayTheme) { onFlags(flags.copy(dayTheme = it)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    val c = LocalAkkaColors.current
    Text(
        title, color = c.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(if (selected) c.accent else c.surfaceHigh)
            .border(1.dp, if (selected) c.accent else c.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) c.onAccent else c.text,
            fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1
        )
    }
}

@Composable
private fun ToggleRow(title: String, badge: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAkkaColors.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = c.text, fontSize = 20.sp)
        if (badge != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                badge, color = c.textDim, fontSize = 15.sp,
                modifier = Modifier
                    .border(1.dp, c.outline, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
