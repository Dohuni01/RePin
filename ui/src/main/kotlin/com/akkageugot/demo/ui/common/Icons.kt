package com.akkageugot.demo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.ui.navi.MapIcon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 사진 대신 쓰는 단순 아이콘. 외부 아이콘 라이브러리 없이 Canvas로 직접 그린다 (24 격자 기준) */
enum class IconKind {
    BAKERY, CAFE, RESTAURANT, FLOWER, CONVENIENCE, ETC, PIN,
    PARKING, FUEL, PHARMACY, BANK,
    MIC, GEAR, LIST, TURN_LEFT, TURN_RIGHT, STRAIGHT, FLAG, BACK, CHECK, PHONE,
    MAP, STAR, HOME, EYE, STOP, CLOSE
}

fun Category?.iconKind(): IconKind = when (this) {
    Category.BAKERY -> IconKind.BAKERY
    Category.CAFE -> IconKind.CAFE
    Category.RESTAURANT -> IconKind.RESTAURANT
    Category.FLOWER -> IconKind.FLOWER
    Category.CONVENIENCE -> IconKind.CONVENIENCE
    Category.ETC -> IconKind.ETC
    null -> IconKind.PIN
}

/** 지도 아이콘(MapScene) → 그리기용 아이콘 */
fun MapIcon.iconKind(): IconKind = when (this) {
    MapIcon.BAKERY -> IconKind.BAKERY
    MapIcon.CAFE -> IconKind.CAFE
    MapIcon.RESTAURANT -> IconKind.RESTAURANT
    MapIcon.FLOWER -> IconKind.FLOWER
    MapIcon.CONVENIENCE -> IconKind.CONVENIENCE
    MapIcon.ETC -> IconKind.ETC
    MapIcon.PARKING -> IconKind.PARKING
    MapIcon.FUEL -> IconKind.FUEL
    MapIcon.PHARMACY -> IconKind.PHARMACY
    MapIcon.BANK -> IconKind.BANK
    MapIcon.PIN -> IconKind.PIN
}

@Composable
fun AkkaIcon(kind: IconKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawIcon(kind, Offset(size.width / 2f, size.height / 2f), minOf(size.width, size.height), tint)
    }
}

/** center를 중심으로 한 변 sizePx 정사각형 안에 아이콘을 그린다 */
fun DrawScope.drawIcon(kind: IconKind, center: Offset, sizePx: Float, color: Color) {
    val u = sizePx / 24f
    fun p(x: Float, y: Float) = Offset(center.x + (x - 12f) * u, center.y + (y - 12f) * u)
    val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = 2f * u, cap = StrokeCap.Round)
    fun poly(vararg xy: Float, fill: Boolean = false, close: Boolean = false) {
        val path = Path()
        var i = 0
        while (i < xy.size) {
            val o = p(xy[i], xy[i + 1])
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            i += 2
        }
        if (close || fill) path.close()
        if (fill) drawPath(path, color) else drawPath(path, color, style = stroke)
    }
    fun rrect(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, fill: Boolean = false) {
        val tl = p(x1, y1)
        val sz = Size((x2 - x1) * u, (y2 - y1) * u)
        if (fill) drawRoundRect(color, tl, sz, CornerRadius(r * u))
        else drawRoundRect(color, tl, sz, CornerRadius(r * u), style = stroke)
    }

    when (kind) {
        IconKind.BAKERY -> {
            rrect(3.5f, 8f, 20.5f, 19f, 5f)
            line(9f, 11f, 8f, 14.5f); line(12.5f, 11f, 11.5f, 14.5f); line(16f, 11f, 15f, 14.5f)
        }
        IconKind.CAFE -> {
            rrect(4.5f, 9f, 16f, 20f, 2.5f)
            drawArc(color, -90f, 180f, false, p(14f, 11.5f), Size(5f * u, 5f * u), style = stroke)
            line(8f, 3.5f, 8f, 6f); line(12.5f, 3.5f, 12.5f, 6f)
        }
        IconKind.RESTAURANT -> {
            line(8f, 4f, 8f, 20f); line(5.5f, 4f, 5.5f, 9f); line(10.5f, 4f, 10.5f, 9f); line(5.5f, 9f, 10.5f, 9f)
            line(16f, 4f, 16f, 20f)
            poly(16f, 4f, 19f, 8f, 16f, 12.5f)
        }
        IconKind.FLOWER -> {
            for (i in 0 until 5) {
                val a = -PI / 2 + i * 2 * PI / 5
                drawCircle(color, 2.8f * u, p(12f + 4.2f * cos(a).toFloat(), 9.5f + 4.2f * sin(a).toFloat()))
            }
            drawCircle(color, 1.8f * u, p(12f, 9.5f))
            line(12f, 15f, 12f, 21.5f)
        }
        IconKind.CONVENIENCE -> {
            rrect(3.5f, 4.5f, 20.5f, 9f, 1f, fill = true)
            rrect(5f, 9f, 19f, 20f, 1f)
            poly(10f, 20f, 10f, 14f, 14f, 14f, 14f, 20f)
        }
        IconKind.ETC -> {
            drawCircle(color, 7f * u, p(12f, 12f), style = stroke)
            drawCircle(color, 2f * u, p(12f, 12f))
        }
        IconKind.PARKING -> {
            line(9f, 4.5f, 9f, 19.5f)
            line(9f, 4.5f, 13f, 4.5f); line(9f, 13.5f, 13f, 13.5f)
            drawArc(color, -90f, 180f, false, p(8.5f, 4.5f), Size(9f * u, 9f * u), style = stroke)
        }
        IconKind.FUEL -> {
            rrect(5f, 4f, 14f, 20f, 1.5f)
            line(7.5f, 8f, 11.5f, 8f)
            poly(14f, 10f, 17.5f, 10f, 17.5f, 18f)
        }
        IconKind.PHARMACY -> {
            poly(10f, 4f, 14f, 4f, 14f, 10f, 20f, 10f, 20f, 14f, 14f, 14f, 14f, 20f, 10f, 20f, 10f, 14f, 4f, 14f, 4f, 10f, 10f, 10f, fill = true)
        }
        IconKind.BANK -> {
            poly(4f, 10f, 12f, 5f, 20f, 10f)
            line(6.5f, 11f, 6.5f, 17f); line(10.5f, 11f, 10.5f, 17f); line(13.5f, 11f, 13.5f, 17f); line(17.5f, 11f, 17.5f, 17f)
            line(4f, 19f, 20f, 19f)
        }
        IconKind.PIN -> {
            drawArc(color, 150f, 240f, false, p(6f, 3f), Size(12f * u, 12f * u), style = stroke)
            poly(6.8f, 12f, 12f, 21.5f, 17.2f, 12f)
            drawCircle(color, 2f * u, p(12f, 9f))
        }
        IconKind.MIC -> {
            rrect(9f, 2.5f, 15f, 14f, 3f, fill = true)
            drawArc(color, 0f, 180f, false, p(5.5f, 5.5f), Size(13f * u, 12f * u), style = stroke)
            line(12f, 17.5f, 12f, 21f); line(8.5f, 21f, 15.5f, 21f)
        }
        IconKind.GEAR -> {
            drawCircle(color, 6f * u, p(12f, 12f), style = stroke)
            drawCircle(color, 2.2f * u, p(12f, 12f), style = stroke)
            for (i in 0 until 8) {
                val a = i * PI / 4
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                line(12f + 6.5f * c, 12f + 6.5f * s, 12f + 9.5f * c, 12f + 9.5f * s)
            }
        }
        IconKind.LIST -> {
            for (y in listOf(6.5f, 12f, 17.5f)) {
                drawCircle(color, 1.5f * u, p(4.5f, y))
                line(9f, y, 20.5f, y)
            }
        }
        IconKind.TURN_RIGHT -> {
            poly(7f, 21f, 7f, 10f, 18f, 10f)
            poly(14f, 6f, 18.5f, 10f, 14f, 14f)
        }
        IconKind.TURN_LEFT -> {
            poly(17f, 21f, 17f, 10f, 6f, 10f)
            poly(10f, 6f, 5.5f, 10f, 10f, 14f)
        }
        IconKind.STRAIGHT -> {
            line(12f, 21f, 12f, 4.5f)
            poly(7.5f, 9f, 12f, 4f, 16.5f, 9f)
        }
        IconKind.FLAG -> {
            line(7f, 3.5f, 7f, 21f)
            poly(7f, 4.5f, 19f, 8.5f, 7f, 12.5f, fill = true)
        }
        IconKind.BACK -> poly(15f, 5f, 8f, 12f, 15f, 19f)
        IconKind.CHECK -> poly(4.5f, 12.5f, 10f, 18f, 19.5f, 6.5f)
        IconKind.PHONE -> {
            rrect(7f, 2.5f, 17f, 21.5f, 2.5f)
            drawCircle(color, 1.1f * u, p(12f, 18.3f))
        }
        IconKind.MAP -> {
            poly(3f, 6f, 9f, 3.5f, 15f, 6f, 21f, 3.5f, 21f, 18f, 15f, 20.5f, 9f, 18f, 3f, 20.5f, close = true)
            line(9f, 3.5f, 9f, 18f); line(15f, 6f, 15f, 20.5f)
        }
        IconKind.STAR -> {
            val xy = FloatArray(20)
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5
                val r = if (i % 2 == 0) 9.5f else 4.2f
                xy[i * 2] = 12f + r * cos(a).toFloat()
                xy[i * 2 + 1] = 12.8f + r * sin(a).toFloat()
            }
            poly(*xy, close = true)
        }
        IconKind.HOME -> {
            poly(3.5f, 11f, 12f, 3.5f, 20.5f, 11f)
            poly(6f, 9.5f, 6f, 20f, 18f, 20f, 18f, 9.5f)
            poly(10f, 20f, 10f, 14.5f, 14f, 14.5f, 14f, 20f)
        }
        IconKind.EYE -> {
            poly(3f, 12f, 8f, 6.5f, 16f, 6.5f, 21f, 12f, 16f, 17.5f, 8f, 17.5f, close = true)
            drawCircle(color, 3.2f * u, p(12f, 12f))
        }
        IconKind.STOP -> rrect(6f, 6f, 18f, 18f, 2.5f, fill = true)
        IconKind.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
    }
}
