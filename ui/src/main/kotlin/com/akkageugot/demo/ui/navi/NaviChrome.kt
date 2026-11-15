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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

/** 좌측 상단 TBT 칩 (작게 두 줄): 회전 방향 아이콘 + "300m" / "후 우회전". 가운데 거울 카드와 겹치지 않게 폭을 줄였다 */
@Composable
fun TbtChip(drive: DriveState, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    val turn = drive.nextTurn
    val dist = drive.distanceToNextTurnM
    val icon: IconKind
    val big: String
    val small: String
    if (turn != null && dist != null) {
        icon = if (turn.direction == TurnDirection.LEFT) IconKind.TURN_LEFT else IconKind.TURN_RIGHT
        val dir = if (turn.direction == TurnDirection.LEFT) "좌회전" else "우회전"
        if (dist < 30.0) {
            big = "곧"
            small = dir
        } else {
            big = formatDistance(dist)
            small = "후 $dir"
        }
    } else {
        icon = IconKind.STRAIGHT
        big = "직진"
        small = "목적지까지"
    }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.outline, shape)
            .padding(start = 14.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.accent2.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            AkkaIcon(icon, c.accent2, Modifier.size(42.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(big, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(small, color = c.textDim, fontSize = 18.sp, maxLines = 1)
        }
    }
}

/** 하단 정보: 남은 거리, 도착 시각, 경유지로 늘어난 시간 */
@Composable
fun BottomInfo(drive: DriveState, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.outline, shape)
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
 * 큰 [말하기] 버튼 (산호색 그라데이션 + 흰 테). 누르면 onPress(MicPressed), 떼면 onRelease(MicReleased).
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
    val bg: Brush
    val label: String
    when (phase) {
        VoicePhase.IDLE -> { bg = c.accentBrush; label = "말하기" }
        VoicePhase.LISTENING -> { bg = SolidColor(c.accentDeep); label = "듣는 중" }
        VoicePhase.THINKING -> { bg = SolidColor(c.accent2); label = "찾는 중" }
        VoicePhase.SPEAKING -> { bg = SolidColor(c.appBubble); label = "답하는 중" }
    }
    val fg = Color.White
    Column(
        modifier
            .size(diameter)
            .shadow(12.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(bg)
            .border(4.dp, Color.White.copy(alpha = 0.45f), CircleShape)
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
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AkkaIcon(icon, c.accent2, Modifier.size(size * 0.48f))
    }
}
