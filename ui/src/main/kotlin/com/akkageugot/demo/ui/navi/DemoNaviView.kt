package com.akkageugot.demo.ui.navi

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.HeadsUpKind
import com.akkageugot.demo.ui.common.IconKind
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.common.drawIcon
import com.akkageugot.demo.ui.common.iconKind
import com.akkageugot.demo.ui.geo.RouteGeo

/**
 * 데모 내비 지도. 실제 내비처럼 기울어진 3D 시점으로 그린다 (지도 타일 없음).
 * - 차 아이콘은 가로 중앙, 세로 72% 고정. 진행 방향이 화면 위쪽
 * - 도로(큰길/골목), 솟은 건물, 공원, 하천, 가게 아이콘과 이름
 * - 카드에 뜬 가게는 지도에서 강조하고, 이미 지나쳐 화면 밖이면 아래쪽에 "○m 뒤"로 붙인다
 * 계산은 MapScene.kt(buildMapScene)가 하고, 여기서는 그 결과를 순서대로 그리기만 한다.
 *
 * @param showNames 발표자 모드: 보이는 가게 이름을 전부 표시 (평소엔 가까운 가게만)
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
    val pal = colors.map
    val map = state.map
    val drive = state.drive
    // 도시 장식은 경로가 바뀔 때만 다시 만든다 (시드 고정)
    val decor = remember(map.originalRoute, map.pois) { buildMapDecor(map.originalRoute, map.pois.map { it.pos }) }
    val routeGeo = remember(map.route) { RouteGeo(map.route) }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }
    val headsUp = state.headsUp
    val highlight: Set<String> =
        if (headsUp != null && headsUp.kind == HeadsUpKind.ANSWER) {
            map.pois.filter { it.name == headsUp.title }.map { it.id }.toSet()
        } else {
            emptySet()
        }

    Canvas(modifier.clipToBounds()) {
        val prims = buildMapScene(
            w = size.width,
            h = size.height,
            dpPx = 1.dp.toPx(),
            spPx = 1.sp.toPx(),
            map = map,
            drive = drive,
            decor = decor,
            routeGeo = routeGeo,
            pal = pal,
            highlightPoiIds = highlight,
            showAllNames = showNames,
            debug = if (showDebug) state.debug else null
        )
        for (p in prims) {
            when (p) {
                is MapPrim.Poly -> drawPath(polyPath(p.xy), Color(p.color))
                is MapPrim.Disc -> {
                    val c = Offset(p.x, p.y)
                    if (p.strokeW > 0f) drawCircle(Color(p.color), p.r, c, style = Stroke(p.strokeW))
                    else drawCircle(Color(p.color), p.r, c)
                }
                is MapPrim.Badge -> {
                    val c = Offset(p.x, p.y)
                    if (p.ring) drawCircle(Color(pal.accent), p.r + 5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    drawCircle(Color(pal.labelHalo), p.r + 2.dp.toPx(), c)
                    drawCircle(Color(mapIconColor(p.icon)), p.r, c)
                    drawIcon(p.icon.iconKind(), c, p.r * 1.25f, Color.White)
                }
                is MapPrim.Label -> {
                    labelPaint.textSize = p.size
                    drawLabel(p.text, p.x, p.y, labelPaint, Color(p.color), Color(p.halo))
                }
                is MapPrim.Pin -> {
                    val c = Offset(p.x, p.y)
                    drawCircle(Color(pal.labelHalo), p.r + 3.dp.toPx(), c)
                    if (p.waypoint) {
                        drawCircle(colors.ok, p.r, c)
                        drawIcon(IconKind.PIN, c, p.r * 1.25f, Color.White)
                    } else {
                        drawCircle(Color(pal.route), p.r, c)
                        drawIcon(IconKind.FLAG, c, p.r * 1.2f, Color.White)
                    }
                }
                is MapPrim.Car -> drawCar(p.x, p.y, p.size / 2f, Color(pal.route))
                is MapPrim.Fog -> {
                    val c = Color(p.color)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to c,
                            (p.solidTo / p.fadeTo) to c,
                            1f to c.copy(alpha = 0f),
                            startY = 0f,
                            endY = p.fadeTo
                        ),
                        size = Size(size.width, p.fadeTo)
                    )
                }
            }
        }
    }
}

private fun polyPath(xy: FloatArray): Path {
    val path = Path()
    path.moveTo(xy[0], xy[1])
    var i = 2
    while (i < xy.size) {
        path.lineTo(xy[i], xy[i + 1])
        i += 2
    }
    path.close()
    return path
}

/** 내 차: 바닥 그림자 + 흰 원 + 경로색 원 + 흰 화살표 (항상 위쪽을 본다) */
private fun DrawScope.drawCar(x: Float, y: Float, r: Float, routeColor: Color) {
    drawOval(routeColor.copy(alpha = 0.18f), topLeft = Offset(x - r * 1.7f, y - r * 0.75f), size = Size(r * 3.4f, r * 1.9f))
    drawCircle(Color.White, r, Offset(x, y))
    drawCircle(routeColor, r * 0.8f, Offset(x, y))
    val arrow = Path().apply {
        moveTo(x, y - r * 0.55f)
        lineTo(x + r * 0.42f, y + r * 0.45f)
        lineTo(x, y + r * 0.2f)
        lineTo(x - r * 0.42f, y + r * 0.45f)
        close()
    }
    drawPath(arrow, Color.White)
}

/** 글자 뒤에 배경색 테두리를 둘러 지도 위에서도 읽히게 한다 (가운데 정렬 Paint 사용) */
private fun DrawScope.drawLabel(text: String, x: Float, y: Float, paint: Paint, color: Color, halo: Color) {
    val canvas = drawContext.canvas.nativeCanvas
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = paint.textSize * 0.28f
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = halo.toArgb()
    canvas.drawText(text, x, y, paint)
    paint.style = Paint.Style.FILL
    paint.color = color.toArgb()
    canvas.drawText(text, x, y, paint)
}
