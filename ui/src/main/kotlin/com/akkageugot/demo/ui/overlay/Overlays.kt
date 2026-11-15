package com.akkageugot.demo.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import com.akkageugot.demo.contract.Side
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akkageugot.demo.contract.HeadsUp
import com.akkageugot.demo.contract.HeadsUpButton
import com.akkageugot.demo.contract.HeadsUpKind
import com.akkageugot.demo.contract.Speaker
import com.akkageugot.demo.contract.VoiceLine
import com.akkageugot.demo.ui.common.AkkaIcon
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.common.badgeColor
import com.akkageugot.demo.ui.common.iconKind

fun HeadsUpButton.labelKo(): String = when (this) {
    HeadsUpButton.SAVE -> "저장"
    HeadsUpButton.VISIT -> "들르기"
    HeadsUpButton.DISMISS -> "괜찮아요"
}

/**
 * 주행 중 화면 위쪽 가운데에 뜨는 "백미러" 카드 (RePin 디자인).
 * 위: 금속 테두리 거울 속에 지나온 길과 찾은 가게가 비친다 (좌우는 거울 속 위치로)
 * 아래: 이름 + 한 줄 + 큰 버튼 막대
 * 애니메이션 없이 나타나고 사라지기만 한다 (headsUp == null이면 호출하지 않는다).
 */
@Composable
fun HeadsUpCard(headsUp: HeadsUp, onClick: (HeadsUpButton) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    val side = sideFromText(headsUp.subtitle)
    val revisit = headsUp.kind == HeadsUpKind.REVISIT
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // 거울 걸이
        Box(Modifier.width(18.dp).height(10.dp).clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(c.chromeDark))
        MirrorFrame(Modifier.fillMaxWidth().aspectRatio(3.1f)) {
            MirrorReflection(headsUp.category, side)
            Text(
                if (revisit) "다시 지나가는 곳" else "뒤에서 찾았어요",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color(0x66000000), blurRadius = 6f)),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 46.dp, top = 16.dp)
            )
            val sideText = when (side) {
                Side.RIGHT -> "오른쪽 ▶"
                Side.LEFT -> "◀ 왼쪽"
                Side.UNKNOWN -> ""
            }
            if (sideText.isNotEmpty()) {
                Text(
                    sideText, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(shadow = Shadow(Color(0x80000000), blurRadius = 8f)),
                    modifier = Modifier
                        .align(if (side == Side.LEFT) Alignment.TopStart else Alignment.TopEnd)
                        .padding(
                            start = if (side == Side.LEFT) 46.dp else 0.dp,
                            end = if (side == Side.LEFT) 0.dp else 46.dp,
                            top = if (side == Side.LEFT) 44.dp else 12.dp
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val bar = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, bar, clip = false)
                .clip(bar)
                .background(c.surface)
                .border(1.dp, c.outline, bar)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(headsUp.category.badgeColor()),
                contentAlignment = Alignment.Center
            ) {
                AkkaIcon(headsUp.category.iconKind(), Color.White, Modifier.size(36.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    headsUp.title, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    headsUp.subtitle, color = c.textDim, fontSize = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            headsUp.buttons.forEachIndexed { i, b ->
                val primary = i == 0
                val btnShape = RoundedCornerShape(22.dp)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .widthIn(min = 112.dp)
                        .height(72.dp)
                        .clip(btnShape)
                        .then(if (primary) Modifier.background(c.accentBrush) else Modifier.background(c.surfaceHigh))
                        .border(if (primary) 0.dp else 1.5.dp, if (primary) Color.Transparent else c.outline, btnShape)
                        .clickable { onClick(b) }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        b.labelKo(), color = if (primary) c.onAccent else c.text,
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                }
            }
        }
    }
}

/** 좌측 하단 말풍선. 최대 2줄: 사용자는 흰 유리, 앱은 남색 */
@Composable
fun VoiceBubble(lines: List<VoiceLine>, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.takeLast(2).forEach { line ->
            val isUser = line.speaker == Speaker.USER
            val shape = RoundedCornerShape(20.dp)
            Text(
                line.text,
                color = if (isUser) c.text else c.onAppBubble,
                fontSize = 22.sp,
                fontWeight = if (isUser) FontWeight.Normal else FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .shadow(4.dp, shape, clip = false)
                    .clip(shape)
                    .background(if (isUser) c.userBubble else c.appBubble)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}
