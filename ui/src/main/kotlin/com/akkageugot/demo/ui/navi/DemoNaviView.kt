package com.akkageugot.demo.ui.navi

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.ui.common.IconKind
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.common.badgeColor
import com.akkageugot.demo.ui.common.drawIcon
import com.akkageugot.demo.ui.common.iconKind
import com.akkageugot.demo.ui.geo.RouteGeo
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 한 화면 세로에 보이는 실제 거리(m). 차는 세로 70% 지점이라 앞쪽이 약 220m 보인다 */
private const val VISIBLE_HEIGHT_M = 320f

/**
 * 데모 내비 지도. 지도 타일 없이 로컬 미터 좌표를 Canvas에 직접 그린다.
 * heading-up: 진행 방향이 항상 화면 위쪽. 차 아이콘은 가로 중앙, 세로 70% 고정.
 *
 * @param showNames 발표자 모드: 가게 이름 표시
 * @param showDebug 디버그 오버레이: trail, t0, corridor, 후보 순위
 */
@Composable
fun DemoNaviView(
    state: AppState,
    modifier: Modifier = Modifier,
    showNames: Boolean = false,
    showDebug: Boolean = false
) {
    val colors = LocalAkkaColors.current
    val map = state.map
    val drive = state.drive
    val decor = remember(map.originalRoute) { buildDecor(map.originalRoute) }
    val routeGeo = remember(map.route) { RouteGeo(map.route) }
    val labelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD } }

    Canvas(modifier.clipToBounds()) {
        val pxPerM = size.height / VISIBLE_HEIGHT_M
        val cx = size.width / 2f
        val cy = size.height * 0.7f
        // 진행 방향 f, 그 오른쪽 r (계약 좌표: y 북쪽 +). 화면은 y 아래 + 이므로 앞쪽 성분을 빼 준다
        val fx = cos(drive.headingRad)
        val fy = sin(drive.headingRad)
        fun toScreen(v: Vec2): Offset {
            val dx = v.x - drive.pos.x
            val dy = v.y - drive.pos.y
            val right = dx * fy - dy * fx
            val forward = dx * fx + dy * fy
            return Offset(cx + (right * pxPerM).toFloat(), cy - (forward * pxPerM).toFloat())
        }
        val viewRadiusM = hypot(size.width, size.height) / pxPerM
        fun near(v: Vec2, extra: Double = 0.0) =
            hypot(v.x - drive.pos.x, v.y - drive.pos.y) < viewRadiusM + extra
        fun onScreen(o: Offset, margin: Float = 40f) =
            o.x > -margin && o.x < size.width + margin && o.y > -margin && o.y < size.height + margin

        drawRect(colors.mapBg)

        // 장식: 격자 보조 도로 + 건물 블록
        val roadW = 11f * pxPerM
        for (x in decor.roadXs) {
            drawLine(colors.mapRoad, toScreen(Vec2(x, decor.minY)), toScreen(Vec2(x, decor.maxY)), roadW)
        }
        for (y in decor.roadYs) {
            drawLine(colors.mapRoad, toScreen(Vec2(decor.minX, y)), toScreen(Vec2(decor.maxX, y)), roadW)
        }
        val blockPath = Path()
        for (b in decor.blocks) {
            if (!near(Vec2((b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2), 120.0)) continue
            val p0 = toScreen(Vec2(b.x0, b.y0))
            val p1 = toScreen(Vec2(b.x1, b.y0))
            val p2 = toScreen(Vec2(b.x1, b.y1))
            val p3 = toScreen(Vec2(b.x0, b.y1))
            blockPath.moveTo(p0.x, p0.y); blockPath.lineTo(p1.x, p1.y)
            blockPath.lineTo(p2.x, p2.y); blockPath.lineTo(p3.x, p3.y); blockPath.close()
        }
        drawPath(blockPath, colors.mapBlock)

        // 경로: 도로 바탕 → 지나온 구간(회색) → 남은 구간(파랑)
        val d = drive.distanceAlongRouteM
        fun polyline(points: List<Vec2>): Path {
            val path = Path()
            points.forEachIndexed { i, v ->
                val o = toScreen(v)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            return path
        }
        fun strokeOf(widthM: Float) = Stroke(widthM * pxPerM, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (map.route.size >= 2) {
            drawPath(polyline(map.route), colors.mapRoad, style = strokeOf(15f))
            val passed = routeGeo.slice(0.0, d)
            val rest = routeGeo.slice(d, routeGeo.length)
            if (showDebug && state.debug.corridor.size >= 2) {
                drawPath(polyline(state.debug.corridor), colors.warn.copy(alpha = 0.38f), style = strokeOf(46f))
            }
            if (passed.size >= 2) drawPath(polyline(passed), colors.routePassed, style = strokeOf(6f))
            if (rest.size >= 2) drawPath(polyline(rest), colors.route, style = strokeOf(8f))
        }

        // 가게: 업종 아이콘만 (이름은 발표자 모드에서만)
        val badgeR = 17.dp.toPx()
        labelPaint.textSize = 14.sp.toPx()
        labelPaint.color = colors.text.toArgb()
        for (poi in map.pois) {
            if (!near(poi.pos)) continue
            val o = toScreen(poi.pos)
            if (!onScreen(o)) continue
            val isWaypoint = map.waypoint?.id == poi.id
            if (isWaypoint) continue
            drawCircle(poi.category.badgeColor(), badgeR, o)
            drawIcon(poi.category.iconKind(), o, badgeR * 1.3f, Color.White)
            if (showNames) drawLabel(poi.name, o.x + badgeR + 4.dp.toPx(), o.y + 5.dp.toPx(), labelPaint, colors.mapBg)
        }

        // 디버그: 후보 순위, trail 점, t0 마커
        if (showDebug) {
            state.debug.candidates.forEachIndexed { i, c ->
                val o = toScreen(c.poi.pos)
                drawCircle(colors.warn, badgeR + 5.dp.toPx(), o, style = Stroke(3.dp.toPx()))
                drawLabel("${i + 1}", o.x - 4.dp.toPx(), o.y - badgeR - 8.dp.toPx(), labelPaint, colors.mapBg)
            }
            for (v in state.debug.trail) {
                val o = toScreen(v)
                if (onScreen(o)) drawCircle(colors.text, 3.dp.toPx(), o)
            }
            state.debug.t0Pos?.let {
                val o = toScreen(it)
                drawCircle(colors.danger, 7.dp.toPx(), o)
                drawCircle(colors.danger, 13.dp.toPx(), o, style = Stroke(2.dp.toPx()))
                drawLabel("t0", o.x + 16.dp.toPx(), o.y + 5.dp.toPx(), labelPaint, colors.mapBg)
            }
        }

        // 목적지, 경유지 핀
        val pinR = 19.dp.toPx()
        val dest = toScreen(map.destination)
        if (map.route.isNotEmpty() && onScreen(dest)) {
            drawCircle(colors.mapBg, pinR + 3.dp.toPx(), dest)
            drawCircle(colors.route, pinR, dest)
            drawIcon(IconKind.FLAG, dest, pinR * 1.2f, Color.White)
        }
        map.waypoint?.let { wp ->
            val o = toScreen(wp.pos)
            if (onScreen(o)) {
                drawCircle(colors.mapBg, pinR + 3.dp.toPx(), o)
                drawCircle(colors.ok, pinR, o)
                drawIcon(IconKind.PIN, o, pinR * 1.25f, Color.White)
                if (showNames) drawLabel(wp.name, o.x + pinR + 4.dp.toPx(), o.y + 5.dp.toPx(), labelPaint, colors.mapBg)
            }
        }

        // 차 (항상 위쪽을 본다)
        val s = 1.dp.toPx()
        val car = Path().apply {
            moveTo(cx, cy - 20 * s)
            lineTo(cx + 15 * s, cy + 15 * s)
            lineTo(cx, cy + 7 * s)
            lineTo(cx - 15 * s, cy + 15 * s)
            close()
        }
        drawCircle(colors.route.copy(alpha = 0.25f), 30 * s, Offset(cx, cy))
        drawPath(car, colors.car)
        drawPath(car, colors.route, style = Stroke(2.5f * s, join = StrokeJoin.Round))
    }
}

/** 글자 뒤에 배경색 테두리를 둘러 지도 위에서도 읽히게 한다 */
private fun DrawScope.drawLabel(text: String, x: Float, y: Float, paint: Paint, halo: Color) {
    val canvas = drawContext.canvas.nativeCanvas
    val fill = paint.color
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = paint.textSize * 0.28f
    paint.color = halo.toArgb()
    canvas.drawText(text, x, y, paint)
    paint.style = Paint.Style.FILL
    paint.color = fill
    canvas.drawText(text, x, y, paint)
}

// ---------------------------------------------------------------- 장식 (UI에서 생성, 시드 고정)

internal class Block(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

internal class Decor(
    val roadXs: List<Double>, val roadYs: List<Double>,
    val minX: Double, val maxX: Double, val minY: Double, val maxY: Double,
    val blocks: List<Block>
)

private const val GRID_M = 200.0
private const val BLOCK_INSET_M = 15.0
private const val BLOCK_GAP_M = 9.0

/** 경로 바운딩 박스 기준 격자 도로와 그 사이 건물 블록 */
internal fun buildDecor(route: List<Vec2>): Decor {
    if (route.isEmpty()) return Decor(emptyList(), emptyList(), 0.0, 0.0, 0.0, 0.0, emptyList())
    val ox = route[0].x
    val oy = route[0].y
    fun snapDown(v: Double, o: Double) = o + Math.floor((v - o) / GRID_M) * GRID_M
    val minX = snapDown(route.minOf { it.x } - 400.0, ox)
    val maxX = snapDown(route.maxOf { it.x } + 600.0, ox)
    val minY = snapDown(route.minOf { it.y } - 400.0, oy)
    val maxY = snapDown(route.maxOf { it.y } + 600.0, oy)
    val xs = generateSequence(minX) { it + GRID_M }.takeWhile { it <= maxX + 1e-6 }.toList()
    val ys = generateSequence(minY) { it + GRID_M }.takeWhile { it <= maxY + 1e-6 }.toList()

    val rnd = java.util.Random(20261114L)
    val blocks = ArrayList<Block>()
    for (i in 0 until xs.size - 1) for (j in 0 until ys.size - 1) {
        val x0 = xs[i] + BLOCK_INSET_M
        val x1 = xs[i + 1] - BLOCK_INSET_M
        val y0 = ys[j] + BLOCK_INSET_M
        val y1 = ys[j + 1] - BLOCK_INSET_M
        val splitX = x0 + (x1 - x0) * (0.35 + 0.3 * rnd.nextDouble())
        val splitY = y0 + (y1 - y0) * (0.35 + 0.3 * rnd.nextDouble())
        val g = BLOCK_GAP_M / 2
        when (rnd.nextInt(4)) {
            0 -> blocks.add(Block(x0, y0, x1, y1))
            1 -> {
                blocks.add(Block(x0, y0, splitX - g, y1)); blocks.add(Block(splitX + g, y0, x1, y1))
            }
            2 -> {
                blocks.add(Block(x0, y0, x1, splitY - g)); blocks.add(Block(x0, splitY + g, x1, y1))
            }
            else -> {
                blocks.add(Block(x0, y0, splitX - g, splitY - g)); blocks.add(Block(splitX + g, y0, x1, splitY - g))
                blocks.add(Block(x0, splitY + g, splitX - g, y1)); blocks.add(Block(splitX + g, splitY + g, x1, y1))
            }
        }
    }
    return Decor(xs, ys, minX, maxX, minY, maxY, blocks)
}
