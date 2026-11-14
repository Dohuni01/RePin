package com.akkageugot.demo.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
 * 주행 중 우측 상단에 잠깐 뜨는 카드 한 장. 사진 없이 아이콘 + 이름 + 한 줄 + 큰 버튼.
 * 애니메이션 없이 나타나고 사라지기만 한다 (headsUp == null이면 호출하지 않는다).
 */
@Composable
fun HeadsUpCard(headsUp: HeadsUp, onClick: (HeadsUpButton) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(24.dp)
    val edge = if (headsUp.kind == HeadsUpKind.REVISIT) c.ok else c.accent
    Column(
        modifier
            .width(380.dp)
            .clip(shape)
            .background(c.surface)
            .border(2.dp, edge, shape)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        }
        if (headsUp.buttons.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                headsUp.buttons.forEachIndexed { i, b ->
                    val primary = i == 0
                    val btnShape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(btnShape)
                            .background(if (primary) c.accent else c.surfaceHigh)
                            .border(2.dp, if (primary) c.accent else c.outline, btnShape)
                            .clickable { onClick(b) },
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
}

/** 좌측 하단 말풍선. 최대 2줄: 사용자는 회색, 앱은 강조색 */
@Composable
fun VoiceBubble(lines: List<VoiceLine>, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.takeLast(2).forEach { line ->
            val isUser = line.speaker == Speaker.USER
            Text(
                line.text,
                color = if (isUser) c.text else c.onAccent,
                fontSize = 22.sp,
                fontWeight = if (isUser) FontWeight.Normal else FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isUser) c.userBubble else c.accent)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}
