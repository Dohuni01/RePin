package com.akkageugot.demo.ui.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.Candidate
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.common.badgeColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val WINDOW_SIDE_M = 40.0   // 좌/우 40m 이내
private const val WINDOW_SPAN_M = 60.0   // 창 하나에 보이는 앞뒤 거리 (±60m)

private class Sign(val poi: Poi, val forwardM: Double)

/**
 * 발표자 모드 "창밖 보기" 띠. 지금 차 기준 좌/우 40m 이내 가게 간판을 단색 사각형 + 이름으로 보여 준다.
 * drive.pos, headingRad, map.pois로 화면에서 계산한다. 주행 중에도 이 띠만 움직임이 허용된다.
 */
@Composable
fun WindowStrip(state: AppState, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    val drive = state.drive
    val fx = cos(drive.headingRad)
    val fy = sin(drive.headingRad)
    val left = ArrayList<Sign>()
    val right = ArrayList<Sign>()
    for (poi in state.map.pois) {
        val dx = poi.pos.x - drive.pos.x
        val dy = poi.pos.y - drive.pos.y
        val forward = dx * fx + dy * fy
        val side = dx * fy - dy * fx // 오른쪽 +
        if (abs(forward) > WINDOW_SPAN_M || abs(side) > WINDOW_SIDE_M) continue
        (if (side >= 0) right else left).add(Sign(poi, forward))
    }
    Row(
        modifier
            .widthIn(max = 524.dp) // 좁은 화면(정차 반쪽)에서는 줄어든다
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(14.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 왼쪽 창을 보면 차 앞쪽이 화면 오른쪽, 오른쪽 창을 보면 차 앞쪽이 화면 왼쪽
        WindowPane("왼쪽 창", left, forwardToRight = true, modifier = Modifier.weight(1f))
        WindowPane("오른쪽 창", right, forwardToRight = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WindowPane(title: String, signs: List<Sign>, forwardToRight: Boolean, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    Column(modifier) {
        Text(title, color = c.textDim, fontSize = 13.sp, maxLines = 1)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.mapBlock)
                .clipToBounds()
        ) {
            val paneW = maxWidth
            val signW = min(96.dp, paneW * 0.4f)
            for (s in signs) {
                val t = (s.forwardM / WINDOW_SPAN_M).toFloat() * (if (forwardToRight) 1f else -1f)
                val x: Dp = (paneW - signW) / 2 + (paneW / 2) * t
                Box(
                    Modifier
                        .offset(x = x)
                        .width(signW)
                        .fillMaxHeight()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(s.poi.category.badgeColor()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        s.poi.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 실제 제품 화면이 아님을 알리는 작은 표기 */
@Composable
fun PresenterTag(modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    Text(
        "발표자 모드", color = c.textDim, fontSize = 12.sp,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 디버그 오버레이 패널: 후보별 점수 막대 + ScoreDetail 4개 값 + 좌우 판정 */
@Composable
fun DebugPanel(candidates: List<Candidate>, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    Column(
        modifier
            .width(330.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.warn, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("역추적 후보", color = c.warn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (candidates.isEmpty()) {
            Text("후보 없음 (말하기 버튼을 누르면 채워져요)", color = c.textDim, fontSize = 13.sp)
        }
        candidates.take(3).forEachIndexed { i, cand ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}. ${cand.poi.name}", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    val side = when (cand.side) {
                        Side.LEFT -> "왼쪽"
                        Side.RIGHT -> "오른쪽"
                        Side.UNKNOWN -> "모름"
                    }
                    Text("$side · ${cand.distanceM.toInt()}m", color = c.textDim, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(c.surfaceHigh)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(cand.score.toFloat().coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(if (cand.score >= 0.75) c.ok else if (cand.score >= 0.5) c.warn else c.textDim)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(fmt(cand.score), color = c.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
                Text(
                    "거리 ${fmt(cand.detail.distance)}  방향 ${fmt(cand.detail.side)}  " +
                        "업종 ${fmt(cand.detail.category)}  시간 ${fmt(cand.detail.time)}",
                    color = c.textDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1
                )
            }
        }
    }
}

private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
