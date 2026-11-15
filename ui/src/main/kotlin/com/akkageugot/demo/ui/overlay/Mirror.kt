package com.akkageugot.demo.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.ui.common.AkkaColors
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.common.badgeColor

/**
 * RePin "백미러" 디자인의 거울.
 * 금속 테두리 안에 지나온 길(뒤쪽 풍경)과 찾은 가게를 비춰 보여 준다.
 * 주행 중에도 쓰이므로 애니메이션은 없다 (정지 그림만).
 */
@Composable
fun MirrorFrame(
    modifier: Modifier = Modifier,
    rim: Dp = 6.dp,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val c = LocalAkkaColors.current
    val outer = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .shadow(elevation, outer, clip = false)
            .clip(outer)
            .background(c.chromeBrush)
            .padding(rim)
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(percent = 50)), content = content)
    }
}

/** 거울 속 장면: 노을 하늘, 멀어지는 길, 길가의 가게(업종 색). category가 null이면 위치 핀 */
@Composable
fun MirrorReflection(category: Category?, side: Side, modifier: Modifier = Modifier) {
    val c = LocalAkkaColors.current
    val shopColor = category.badgeColor()
    Canvas(modifier.fillMaxSize()) { drawReflection(c, category, shopColor, side) }
}

/** 카드 부제(예: "빵집 · 오른쪽 · 150m 지나옴")에서 좌우를 읽는다. 엔진 계약에 좌우 칸이 따로 없어서 글자로 판단 */
fun sideFromText(text: String): Side = when {
    "오른쪽" in text -> Side.RIGHT
    "왼쪽" in text -> Side.LEFT
    else -> Side.UNKNOWN
}

private fun DrawScope.drawReflection(c: AkkaColors, category: Category?, shopColor: Color, side: Side) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.56f

    // 하늘
    drawRect(Brush.verticalGradient(listOf(c.skyMid, c.skyLow, c.skyBottom), endY = horizon))
    // 땅 (지평선 아래)
    drawRect(
        Brush.verticalGradient(listOf(c.skyBottom, c.skyLow), startY = horizon, endY = h),
        topLeft = Offset(0f, horizon), size = Size(w, h - horizon)
    )
    // 해 (가운데 지평선)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF4D6), Color(0xCCFFD39A), Color(0x00FFB38A)),
            center = Offset(w * 0.5f, horizon), radius = h * 0.55f
        ),
        radius = h * 0.55f, center = Offset(w * 0.5f, horizon)
    )
    // 먼 건물 실루엣
    val city = c.skyTop.copy(alpha = 0.45f)
    var x = 0f
    var i = 0
    while (x < w) {
        val bw = w * (0.05f + ((i * 17) % 30) / 400f)
        val bh = h * (0.08f + ((i * 29) % 40) / 260f)
        drawRect(city, Offset(x, horizon - bh), Size(bw, bh))
        x += bw + w * 0.006f
        i++
    }
    // 반대편 길가 (어두운 면)
    val shopRight = side != Side.LEFT
    val curb = Path().apply {
        if (shopRight) {
            moveTo(0f, h); lineTo(0f, horizon + h * 0.12f); lineTo(w * 0.33f, horizon + h * 0.08f); lineTo(w * 0.31f, h)
        } else {
            moveTo(w, h); lineTo(w, horizon + h * 0.12f); lineTo(w * 0.67f, horizon + h * 0.08f); lineTo(w * 0.69f, h)
        }
        close()
    }
    drawPath(curb, c.skyTop.copy(alpha = 0.55f))
    // 길 (멀어지는 사다리꼴)
    val road = Path().apply {
        moveTo(w * 0.33f, h); lineTo(w * 0.475f, horizon); lineTo(w * 0.525f, horizon); lineTo(w * 0.67f, h); close()
    }
    drawPath(road, c.skyTop.copy(alpha = 0.85f))
    drawLine(
        Color(0xFFFFE3C4), Offset(w * 0.5f, h), Offset(w * 0.5f, horizon + h * 0.02f),
        strokeWidth = h * 0.02f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.07f, h * 0.06f))
    )

    // 가게 (또는 위치 핀)
    val sw = w * 0.25f
    val sh = h * 0.56f
    val sx = if (shopRight) w * 0.70f else w * 0.05f
    val sy = h * 0.30f
    if (category == null) {
        drawPin(Offset(sx + sw / 2f, sy + sh * 0.8f), h * 0.2f, c.accent)
    } else {
        drawShop(sx, sy, sw, sh, shopColor)
    }

    // 유리 반사광
    drawRect(
        Brush.linearGradient(
            listOf(Color(0x66FFFFFF), Color(0x00FFFFFF)),
            start = Offset(0f, 0f), end = Offset(w * 0.45f, h * 0.9f)
        )
    )
}

/** 가게 앞모습: 벽, 간판, 줄무늬 차양, 불 켜진 진열창, 문 */
private fun DrawScope.drawShop(x: Float, y: Float, w: Float, h: Float, color: Color) {
    val wall = lerpWhite(color, 0.78f)
    drawRect(Color(0x33000000), Offset(x - w * 0.04f, y + h * 0.95f), Size(w * 1.08f, h * 0.08f))
    drawRect(wall, Offset(x, y), Size(w, h))
    // 간판
    drawRoundRect(
        Color.White, Offset(x + w * 0.12f, y + h * 0.05f), Size(w * 0.76f, h * 0.14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.03f)
    )
    drawRoundRect(
        color, Offset(x + w * 0.12f, y + h * 0.05f), Size(w * 0.76f, h * 0.14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.03f), style = Stroke(h * 0.015f)
    )
    drawLine(color, Offset(x + w * 0.25f, y + h * 0.12f), Offset(x + w * 0.75f, y + h * 0.12f), strokeWidth = h * 0.03f)
    // 줄무늬 차양 + 물결 끝
    val stripes = 6
    val stw = w / stripes
    val ay = y + h * 0.24f
    val ah = h * 0.13f
    for (k in 0 until stripes) {
        val col = if (k % 2 == 0) color else Color.White
        drawRect(col, Offset(x + k * stw, ay), Size(stw, ah))
        drawArc(col, 0f, 180f, true, Offset(x + k * stw, ay + ah - stw / 2f), Size(stw, stw))
    }
    // 진열창 (불빛)
    drawRect(Color(0xFFFFF4DC), Offset(x + w * 0.1f, y + h * 0.52f), Size(w * 0.58f, h * 0.3f))
    drawRect(Color.White, Offset(x + w * 0.1f, y + h * 0.52f), Size(w * 0.58f, h * 0.3f), style = Stroke(h * 0.015f))
    for (k in 0 until 3) {
        drawOval(
            color.copy(alpha = 0.85f),
            Offset(x + w * (0.15f + k * 0.17f), y + h * 0.68f), Size(w * 0.14f, h * 0.08f)
        )
    }
    // 문
    drawRect(color, Offset(x + w * 0.74f, y + h * 0.5f), Size(w * 0.18f, h * 0.48f))
    drawCircle(Color.White, h * 0.012f, Offset(x + w * 0.88f, y + h * 0.75f))
}

private fun DrawScope.drawPin(tip: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(tip.x, tip.y)
        cubicTo(tip.x - r * 0.8f, tip.y - r * 0.9f, tip.x - r, tip.y - r * 1.4f, tip.x - r, tip.y - r * 1.9f)
        arcTo(androidx.compose.ui.geometry.Rect(tip.x - r, tip.y - r * 2.9f, tip.x + r, tip.y - r * 0.9f), 180f, 180f, false)
        cubicTo(tip.x + r, tip.y - r * 1.4f, tip.x + r * 0.8f, tip.y - r * 0.9f, tip.x, tip.y)
        close()
    }
    drawPath(p, color)
    drawPath(p, Color.White, style = Stroke(r * 0.14f))
    drawCircle(Color.White, r * 0.4f, Offset(tip.x, tip.y - r * 1.9f))
}

private fun lerpWhite(c: Color, t: Float) = Color(
    red = c.red + (1f - c.red) * t,
    green = c.green + (1f - c.green) * t,
    blue = c.blue + (1f - c.blue) * t,
    alpha = 1f
)
