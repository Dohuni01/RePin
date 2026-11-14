package com.akkageugot.demo.ui.navi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akkageugot.demo.contract.DriveState
import com.akkageugot.demo.contract.TurnDirection
import com.akkageugot.demo.contract.VoicePhase
import com.akkageugot.demo.ui.common.AkkaIcon
import com.akkageugot.demo.ui.common.IconKind
import com.akkageugot.demo.ui.common.LocalAkkaColors
import kotlin.math.roundToInt

fun formatDistance(m: Double): String = when {
    m >= 1000.0 -> "${(m / 100.0).roundToInt() / 10.0}km"
    m >= 100.0 -> "${(m / 50.0).roundToInt() * 50}m"
    m < 5.0 -> "0m" // 도착
    else -> "${((m / 10.0).roundToInt() * 10).coerceAtLeast(10)}m"
}

/** 좌측 상단 TBT 칩: 회전 방향 아이콘 + "300m 후 우회전" */
@Composable
fun TbtChip(drive: DriveState, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    val turn = drive.nextTurn
    val dist = drive.distanceToNextTurnM
    val icon: IconKind
    val text: String
    if (turn != null && dist != null) {
        icon = if (turn.direction == TurnDirection.LEFT) IconKind.TURN_LEFT else IconKind.TURN_RIGHT
        val dir = if (turn.direction == TurnDirection.LEFT) "좌회전" else "우회전"
        text = if (dist < 30.0) "곧 $dir" else "${formatDistance(dist)} 후 $dir"
    } else {
        icon = IconKind.STRAIGHT
        text = "목적지까지 직진"
    }
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AkkaIcon(icon, c.accent, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 하단 정보: 남은 거리, 도착 시각, 경유지로 늘어난 시간 */
@Composable
fun BottomInfo(drive: DriveState, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(formatDistance(drive.remainingM), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (drive.etaText.isNotEmpty()) {
            Text(drive.etaText, color = c.textDim, fontSize = 22.sp, maxLines = 1)
        }
        if (drive.etaDelayMin > 0) {
            Text("+${drive.etaDelayMin}분", color = c.warn, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 큰 [말하기] 버튼. 누르면 onPress(MicPressed), 떼면 onRelease(MicReleased).
 * voicePhase는 색과 글자만 바뀐다 (주행 중 애니메이션 금지).
 */
@Composable
fun MicButton(
    phase: VoicePhase,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 116.dp
) {
    val c = LocalAkkaColors.current
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val bg: Color
    val fg: Color
    val label: String
    when (phase) {
        VoicePhase.IDLE -> { bg = c.accent; fg = c.onAccent; label = "말하기" }
        VoicePhase.LISTENING -> { bg = c.danger; fg = Color.White; label = "듣는 중" }
        VoicePhase.THINKING -> { bg = c.warn; fg = if (c.isDay) Color.White else Color(0xFF1B1200); label = "찾는 중" }
        VoicePhase.SPEAKING -> { bg = c.ok; fg = if (c.isDay) Color.White else Color(0xFF02170B); label = "답하는 중" }
    }
    Column(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(bg)
            .border(3.dp, c.mapBg.copy(alpha = 0.6f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    press()
                    // 누른 채로 화면이 바뀌어(취소) 끝나도 MicReleased는 꼭 보낸다
                    try {
                        tryAwaitRelease()
                    } finally {
                        release()
                    }
                })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AkkaIcon(IconKind.MIC, fg, Modifier.size(44.dp))
        Text(label, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 큰 원형 아이콘 버튼 (저장 목록, 설정) */
@Composable
fun RoundIconButton(icon: IconKind, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 76.dp) {
    val c = LocalAkkaColors.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AkkaIcon(icon, c.text, Modifier.size(size * 0.48f))
    }
}
