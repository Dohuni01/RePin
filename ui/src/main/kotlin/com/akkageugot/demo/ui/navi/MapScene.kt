package com.akkageugot.demo.ui.navi

import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.DebugInfo
import com.akkageugot.demo.contract.DriveState
import com.akkageugot.demo.contract.MapData
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.ui.geo.RouteGeo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================
// 데모 내비 지도 장면 계산 (순수 Kotlin, Compose/Android 의존 없음).
// 기울어진 3D 시점으로 땅 위 좌표를 화면에 투영하고, 그릴 도형 목록(MapPrim)을 만든다.
// DemoNaviView는 이 목록을 순서대로 그리기만 한다.
// =====================================================================

/** 지도 색 (ARGB Long). Theme.kt의 AkkaColors.map으로 주간/야간을 고른다 */
data class MapPalette(
    val ground: Long,
    val building: Long,
    val building2: Long,
    val buildingSide: Long,
    val road: Long,
    val roadMajor: Long,
    val roadEdge: Long,
    val park: Long,
    val water: Long,
    val route: Long,
    val routeEdge: Long,
    val routePassed: Long,
    val label: Long,
    val labelHalo: Long,
    val accent: Long,
    val debugWarn: Long,
    val debugDanger: Long,
    val debugText: Long
)

val DayMapPalette = MapPalette(
    ground = 0xFFE9EDF3, building = 0xFFE0E5ED, building2 = 0xFFD9DFE9, buildingSide = 0xFFC5CCD8,
    road = 0xFFFFFFFF, roadMajor = 0xFFFFFFFF, roadEdge = 0xFFCDD4DF,
    park = 0xFFD2E9CE, water = 0xFFC6DDF4,
    route = 0xFF2F6FED, routeEdge = 0xFF1B4FC4, routePassed = 0xFFB4BDCB,
    label = 0xFF3A4559, labelHalo = 0xFFFFFFFF, accent = 0xFF2F6FED,
    debugWarn = 0xFFB36A00, debugDanger = 0xFFC62828, debugText = 0xFF10151C
)

val NightMapPalette = MapPalette(
    ground = 0xFF0F1828, building = 0xFF18243A, building2 = 0xFF1C2A43, buildingSide = 0xFF0B1220,
    road = 0xFF24334D, roadMajor = 0xFF2F4263, roadEdge = 0xFF0B1220,
    park = 0xFF12302A, water = 0xFF0F2742,
    route = 0xFF3D8BFF, routeEdge = 0xFFA9CBFF, routePassed = 0xFF44526B,
    label = 0xFFC9D4E5, labelHalo = 0xFF0C1422, accent = 0xFF3D8BFF,
    debugWarn = 0xFFFFB84D, debugDanger = 0xFFFF6B6B, debugText = 0xFFF4F7FB
)

/** 지도 위 아이콘 종류. 가게(Category) + 장식용(주차장, 주유소, 약국, ATM) */
enum class MapIcon { BAKERY, CAFE, RESTAURANT, FLOWER, CONVENIENCE, ETC, PARKING, FUEL, PHARMACY, BANK, PIN }

fun Category?.toMapIcon(): MapIcon = when (this) {
    Category.BAKERY -> MapIcon.BAKERY
    Category.CAFE -> MapIcon.CAFE
    Category.RESTAURANT -> MapIcon.RESTAURANT
    Category.FLOWER -> MapIcon.FLOWER
    Category.CONVENIENCE -> MapIcon.CONVENIENCE
    Category.ETC -> MapIcon.ETC
    null -> MapIcon.PIN
}

/** 아이콘 배지 색. 흰 아이콘이 잘 보이는 선명한 색 (주간/야간 공통) */
fun mapIconColor(icon: MapIcon): Long = when (icon) {
    MapIcon.BAKERY -> 0xFFE8912A
    MapIcon.CAFE -> 0xFF8A6A52
    MapIcon.RESTAURANT -> 0xFFEF6C3E
    MapIcon.FLOWER -> 0xFF9A63D6
    MapIcon.CONVENIENCE -> 0xFF20A46B
    MapIcon.ETC -> 0xFF5E6B7C
    MapIcon.PARKING -> 0xFF3D7BD9
    MapIcon.FUEL -> 0xFFD94B5B
    MapIcon.PHARMACY -> 0xFFDE5D9A
    MapIcon.BANK -> 0xFF3F9FB8
    MapIcon.PIN -> 0xFF4A6FD6
}

// ---------------------------------------------------------------- 그릴 도형

sealed class MapPrim {
    /** 채운 다각형. xy = x0,y0,x1,y1,... (화면 px) */
    class Poly(val xy: FloatArray, val color: Long) : MapPrim()

    /** 원. strokeW > 0이면 테두리만 */
    class Disc(val x: Float, val y: Float, val r: Float, val color: Long, val strokeW: Float = 0f) : MapPrim()

    /** 가게 아이콘 배지. (x, y)는 배지 중심 */
    class Badge(val x: Float, val y: Float, val r: Float, val icon: MapIcon, val ring: Boolean) : MapPrim()

    /** 가운데 정렬 글자. y는 글자 기준선 */
    class Label(val text: String, val x: Float, val y: Float, val size: Float, val color: Long, val halo: Long) : MapPrim()

    /** 목적지(깃발) / 경유지(핀) */
    class Pin(val x: Float, val y: Float, val r: Float, val waypoint: Boolean) : MapPrim()

    /** 내 차 표시. size = 지름 */
    class Car(val x: Float, val y: Float, val size: Float) : MapPrim()

    /** 화면 위쪽(지평선)을 배경색으로 덮는 세로 그라데이션 */
    class Fog(val solidTo: Float, val fadeTo: Float, val color: Long) : MapPrim()
}

// ---------------------------------------------------------------- 장식 (경로 기준으로 한 번만 생성)

class MapRoad(val a: Vec2, val b: Vec2, val widthM: Double, val major: Boolean)
class MapBuilding(val pts: List<Vec2>, val center: Vec2, val heightM: Double, val alt: Boolean)
class MapDecorPoi(val pos: Vec2, val icon: MapIcon, val name: String)

class MapDecor(
    val roads: List<MapRoad>,
    val buildings: List<MapBuilding>,
    val parks: List<List<Vec2>>,
    val river: List<Vec2>,
    val riverWidthM: Double,
    val pois: List<MapDecorPoi>
)

private const val ROAD_SPACING_M = 78.0
private const val ROUTE_ROAD_W = 18.0
private const val MAJOR_ROAD_W = 13.0
private const val MINOR_ROAD_W = 8.0

/** 지어낸 이름 (실제 브랜드가 아닌 이름만) */
private val DECOR_NAMES: Map<MapIcon, List<String>> = mapOf(
    MapIcon.CONVENIENCE to listOf("하루마트", "골목24", "늘봄마트", "온새미편의점"),
    MapIcon.CAFE to listOf("숲속커피", "작은창카페", "온기커피", "모퉁이로스터리"),
    MapIcon.RESTAURANT to listOf("청기와식당", "소담국밥", "골목분식", "솔밭칼국수"),
    MapIcon.BAKERY to listOf("아침빵집", "보리밭제과"),
    MapIcon.PHARMACY to listOf("해오름약국", "중앙약국"),
    MapIcon.BANK to listOf("ATM"),
    MapIcon.PARKING to listOf("공영주차장"),
    MapIcon.FUEL to listOf("새길주유소", "한길주유소"),
    MapIcon.FLOWER to listOf("들꽃화원")
)
private val DECOR_ICON_POOL = listOf(
    MapIcon.CONVENIENCE, MapIcon.CONVENIENCE, MapIcon.CAFE, MapIcon.CAFE, MapIcon.RESTAURANT, MapIcon.RESTAURANT,
    MapIcon.PHARMACY, MapIcon.BANK, MapIcon.PARKING, MapIcon.PARKING, MapIcon.FUEL, MapIcon.BAKERY, MapIcon.FLOWER
)

private fun segDist(p: Vec2, a: Vec2, b: Vec2): Double {
    val vx = b.x - a.x
    val vy = b.y - a.y
    val l2 = vx * vx + vy * vy
    if (l2 < 1e-9) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / l2).coerceIn(0.0, 1.0)
    return hypot(p.x - (a.x + vx * t), p.y - (a.y + vy * t))
}

private fun polyDist(p: Vec2, line: List<Vec2>): Double {
    var best = Double.MAX_VALUE
    for (i in 0 until line.size - 1) best = min(best, segDist(p, line[i], line[i + 1]))
    return best
}

/**
 * 경로 주변 도시를 만든다 (시드 고정이라 매번 같은 모양).
 * 경로의 남북·동서 구간은 그대로 큰길이 되고, 그 사이를 골목으로 채운다.
 * 건물·공원·장식 가게는 경로, 대각선 도로, 하천과 겹치지 않게 둔다.
 */
fun buildMapDecor(route: List<Vec2>, realPois: List<Vec2> = emptyList(), seed: Long = 20261114L): MapDecor {
    if (route.size < 2) return MapDecor(emptyList(), emptyList(), emptyList(), emptyList(), 0.0, emptyList())
    val rnd = java.util.Random(seed)
    val margin = 650.0
    val minX = route.minOf { it.x } - margin
    val maxX = route.maxOf { it.x } + margin
    val minY = route.minOf { it.y } - margin
    val maxY = route.maxOf { it.y } + margin

    val routeXs = ArrayList<Double>()
    val routeYs = ArrayList<Double>()
    for (i in 0 until route.size - 1) {
        val a = route[i]
        val b = route[i + 1]
        if (abs(a.x - b.x) < 1.0 && abs(a.y - b.y) > 1.0) routeXs.add(a.x)
        if (abs(a.y - b.y) < 1.0 && abs(a.x - b.x) > 1.0) routeYs.add(a.y)
    }
    fun fill(lo: Double, hi: Double, anchors: List<Double>): List<Double> {
        val pts = (listOf(lo, hi) + anchors.filter { it > lo && it < hi }).distinct().sorted()
        val out = ArrayList<Double>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            out.add(a)
            val n = max(1, ((b - a) / ROAD_SPACING_M).roundToInt())
            val step = (b - a) / n
            for (k in 1 until n) out.add(a + step * k + (rnd.nextDouble() - 0.5) * step * 0.3)
        }
        out.add(pts.last())
        return out
    }
    val xs = fill(minX, maxX, routeXs)
    val ys = fill(minY, maxY, routeYs)
    fun widthFor(v: Double, anchors: List<Double>): Double = when {
        anchors.any { abs(it - v) < 0.5 } -> ROUTE_ROAD_W
        rnd.nextDouble() < 0.2 -> MAJOR_ROAD_W
        else -> MINOR_ROAD_W
    }
    val xw = xs.map { widthFor(it, routeXs) }
    val yw = ys.map { widthFor(it, routeYs) }

    val roads = ArrayList<MapRoad>()
    xs.forEachIndexed { i, x -> roads.add(MapRoad(Vec2(x, minY), Vec2(x, maxY), xw[i], xw[i] > MINOR_ROAD_W)) }
    ys.forEachIndexed { j, y -> roads.add(MapRoad(Vec2(minX, y), Vec2(maxX, y), yw[j], yw[j] > MINOR_ROAD_W)) }
    val diagA = Vec2(minX, minY + (maxY - minY) * 0.64)
    val diagB = Vec2(maxX, minY + (maxY - minY) * 0.30)
    roads.add(MapRoad(diagA, diagB, MAJOR_ROAD_W, true))

    // 하천: 지도를 가로지르는 완만한 곡선
    val river = ArrayList<Vec2>()
    val ry = minY + (maxY - minY) * 0.74
    for (t in 0..24) {
        val x = minX + (maxX - minX) * t / 24.0
        river.add(Vec2(x, ry + sin(t * 0.55) * 55.0))
    }
    val riverW = 70.0

    fun blocked(p: Vec2, pad: Double): Boolean =
        polyDist(p, route) < ROUTE_ROAD_W / 2 + pad ||
            segDist(p, diagA, diagB) < MAJOR_ROAD_W / 2 + pad ||
            polyDist(p, river) < riverW / 2 + pad

    val buildings = ArrayList<MapBuilding>()
    val parks = ArrayList<List<Vec2>>()
    val pois = ArrayList<MapDecorPoi>()
    for (i in 0 until xs.size - 1) for (j in 0 until ys.size - 1) {
        val x0 = xs[i] + xw[i] / 2 + 4
        val x1 = xs[i + 1] - xw[i + 1] / 2 - 4
        val y0 = ys[j] + yw[j] / 2 + 4
        val y1 = ys[j + 1] - yw[j + 1] / 2 - 4
        if (x1 - x0 < 16 || y1 - y0 < 16) continue
        val corners = listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))
        val center = Vec2((x0 + x1) / 2, (y0 + y1) / 2)
        if (rnd.nextDouble() < 0.05 && corners.none { blocked(it, 2.0) } && !blocked(center, 2.0)) {
            parks.add(corners)
            continue
        }
        val cols = 1 + rnd.nextInt(3)
        val rows = 1 + rnd.nextInt(2)
        val cw = (x1 - x0) / cols
        val rh = (y1 - y0) / rows
        for (c in 0 until cols) for (r in 0 until rows) {
            if (rnd.nextDouble() < 0.1) continue
            val bx0 = x0 + c * cw + 2 + rnd.nextDouble() * cw * 0.15
            val bx1 = x0 + (c + 1) * cw - 2 - rnd.nextDouble() * cw * 0.15
            val by0 = y0 + r * rh + 2 + rnd.nextDouble() * rh * 0.15
            val by1 = y0 + (r + 1) * rh - 2 - rnd.nextDouble() * rh * 0.15
            if (bx1 - bx0 < 7 || by1 - by0 < 7) continue
            val pts = listOf(Vec2(bx0, by0), Vec2(bx1, by0), Vec2(bx1, by1), Vec2(bx0, by1))
            val bc = Vec2((bx0 + bx1) / 2, (by0 + by1) / 2)
            if (pts.any { blocked(it, 3.0) } || blocked(bc, 3.0)) continue
            val u = rnd.nextDouble()
            val height = if (rnd.nextDouble() < 0.06) 38.0 + u * 22.0 else 6.0 + u * u * 26.0
            buildings.add(MapBuilding(pts, bc, height, rnd.nextBoolean()))
        }
        // 장식 가게: 길가 쪽 가장자리에
        if (rnd.nextDouble() < 0.32) {
            val side = rnd.nextInt(4)
            val t = 0.2 + rnd.nextDouble() * 0.6
            val p = when (side) {
                0 -> Vec2(x0 + 5, y0 + (y1 - y0) * t)
                1 -> Vec2(x1 - 5, y0 + (y1 - y0) * t)
                2 -> Vec2(x0 + (x1 - x0) * t, y0 + 5)
                else -> Vec2(x0 + (x1 - x0) * t, y1 - 5)
            }
            val farFromRoute = polyDist(p, route) > 45.0
            val farFromReal = realPois.none { hypot(it.x - p.x, it.y - p.y) < 30.0 }
            if (farFromRoute && farFromReal && !blocked(p, 2.0)) {
                val icon = DECOR_ICON_POOL[rnd.nextInt(DECOR_ICON_POOL.size)]
                val names = DECOR_NAMES[icon].orEmpty()
                val name = if (names.isNotEmpty() && rnd.nextDouble() < 0.65) names[rnd.nextInt(names.size)] else ""
                pois.add(MapDecorPoi(p, icon, name))
            }
        }
    }
    return MapDecor(roads, buildings, parks, river, riverW, pois)
}

// ---------------------------------------------------------------- 카메라 (기울어진 시점)

/**
 * 차 뒤쪽 위에서 앞을 내려다보는 시점.
 * 차 기준 (오른쪽 r, 앞쪽 f) 미터를 화면으로: 거리 배율 k = D / (D + f)
 *   x = cx + r * s0 * k,  y = yH + (yCar - yH) * k
 */
private class MapCamera(val w: Float, val h: Float, car: Vec2, heading: Double) {
    val cx = w / 2.0
    val yCar = h * 0.72
    val yH = h * 0.06
    val s0 = h / 230.0          // 차 위치에서 1m당 px
    val depth = 95.0            // D: 클수록 덜 기울어 보인다
    val fNear = -depth * 0.72
    val fFar = 1600.0
    private val carX = car.x
    private val carY = car.y
    private val fx = cos(heading)
    private val fy = sin(heading)

    fun right(v: Vec2) = (v.x - carX) * fy - (v.y - carY) * fx
    fun fwd(v: Vec2) = (v.x - carX) * fx + (v.y - carY) * fy
    fun k(f: Double) = depth / (depth + f)
    fun sx(r: Double, f: Double) = (cx + r * s0 * k(f)).toFloat()
    fun sy(f: Double) = (yH + (yCar - yH) * k(f)).toFloat()

    /** 이 깊이에서 화면 가로 절반이 덮는 미터 */
    fun halfWidthM(f: Double) = (w / 2.0) / (s0 * k(f))

    /** 땅 위 다각형 → 화면 다각형 (앞/뒤 잘라내기 포함). 안 보이면 null */
    fun polygon(world: List<Vec2>): FloatArray? {
        var cam: List<DoubleArray> = world.map { doubleArrayOf(right(it), fwd(it)) }
        if (cam.all { it[1] < fNear } || cam.all { it[1] > fFar }) return null
        cam = clipF(cam, fNear, keepAbove = true)
        cam = clipF(cam, fFar, keepAbove = false)
        if (cam.size < 3) return null
        val out = FloatArray(cam.size * 2)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        cam.forEachIndexed { i, p ->
            val x = sx(p[0], p[1])
            val y = sy(p[1])
            out[i * 2] = x
            out[i * 2 + 1] = y
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y)
        }
        if (maxX < -w * 0.2f || minX > w * 1.2f || minY > h + 4f) return null
        return out
    }

    private fun clipF(poly: List<DoubleArray>, limit: Double, keepAbove: Boolean): List<DoubleArray> {
        if (poly.isEmpty()) return poly
        val out = ArrayList<DoubleArray>(poly.size + 2)
        fun inside(p: DoubleArray) = if (keepAbove) p[1] >= limit else p[1] <= limit
        fun cut(a: DoubleArray, b: DoubleArray): DoubleArray {
            val t = (limit - a[1]) / (b[1] - a[1])
            return doubleArrayOf(a[0] + (b[0] - a[0]) * t, limit)
        }
        for (i in poly.indices) {
            val cur = poly[i]
            val prev = poly[(i + poly.size - 1) % poly.size]
            val ci = inside(cur)
            val pi = inside(prev)
            if (ci) {
                if (!pi) out.add(cut(prev, cur))
                out.add(cur)
            } else if (pi) {
                out.add(cut(prev, cur))
            }
        }
        return out
    }
}

private fun quad(a: Vec2, b: Vec2, widthM: Double): List<Vec2>? {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len < 1e-6) return null
    val nx = dy / len * widthM / 2
    val ny = -dx / len * widthM / 2
    return listOf(Vec2(a.x + nx, a.y + ny), Vec2(b.x + nx, b.y + ny), Vec2(b.x - nx, b.y - ny), Vec2(a.x - nx, a.y - ny))
}

private fun disk(c: Vec2, radiusM: Double, n: Int = 12): List<Vec2> =
    (0 until n).map { i ->
        val a = 2 * PI * i / n
        Vec2(c.x + radiusM * cos(a), c.y + radiusM * sin(a))
    }

/** 꺾은선을 폭 widthM짜리 띠로 (모퉁이는 원으로 메움) */
private fun MapCamera.ribbon(line: List<Vec2>, widthM: Double, color: Long, out: MutableList<MapPrim>) {
    for (i in 0 until line.size - 1) {
        quad(line[i], line[i + 1], widthM)?.let { q -> polygon(q)?.let { out.add(MapPrim.Poly(it, color)) } }
    }
    for (i in 1 until line.size - 1) polygon(disk(line[i], widthM / 2))?.let { out.add(MapPrim.Poly(it, color)) }
}

private fun convexHull(xy: FloatArray): FloatArray {
    val n = xy.size / 2
    val idx = (0 until n).sortedWith(compareBy({ xy[it * 2] }, { xy[it * 2 + 1] }))
    fun cross(o: Int, a: Int, b: Int) =
        (xy[a * 2] - xy[o * 2]) * (xy[b * 2 + 1] - xy[o * 2 + 1]) - (xy[a * 2 + 1] - xy[o * 2 + 1]) * (xy[b * 2] - xy[o * 2])
    val hull = ArrayList<Int>()
    for (i in idx) {
        while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], i) <= 0) hull.removeAt(hull.size - 1)
        hull.add(i)
    }
    val lower = hull.size + 1
    for (i in idx.reversed()) {
        while (hull.size >= lower && cross(hull[hull.size - 2], hull[hull.size - 1], i) <= 0) hull.removeAt(hull.size - 1)
        hull.add(i)
    }
    hull.removeAt(hull.size - 1)
    val out = FloatArray(hull.size * 2)
    hull.forEachIndexed { k, i -> out[k * 2] = xy[i * 2]; out[k * 2 + 1] = xy[i * 2 + 1] }
    return out
}

private fun withAlpha(color: Long, alpha: Int): Long = (alpha.toLong() shl 24) or (color and 0xFFFFFFL)

// ---------------------------------------------------------------- 장면

/**
 * 한 프레임에 그릴 도형 목록.
 * @param dpPx 1dp의 px, spPx 1sp의 px
 * @param highlightPoiIds 지금 카드에 뜬 가게 (지도에서 강조)
 * @param showAllNames 발표자 모드: 보이는 가게 이름 전부 표시 (평소엔 가까운 가게만)
 * @param debug null이 아니면 탐색 구간, 궤적, t0, 후보 순위를 겹쳐 그린다
 */
fun buildMapScene(
    w: Float,
    h: Float,
    dpPx: Float,
    spPx: Float,
    map: MapData,
    drive: DriveState,
    decor: MapDecor,
    routeGeo: RouteGeo,
    pal: MapPalette,
    highlightPoiIds: Set<String>,
    showAllNames: Boolean,
    debug: DebugInfo?
): List<MapPrim> {
    val out = ArrayList<MapPrim>(2048)
    val cam = MapCamera(w, h, drive.pos, drive.headingRad)
    out.add(MapPrim.Poly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), pal.ground))

    // 하천, 공원
    if (decor.river.size >= 2) cam.ribbon(decor.river, decor.riverWidthM, pal.water, out)
    for (p in decor.parks) cam.polygon(p)?.let { out.add(MapPrim.Poly(it, pal.park)) }

    // 도로: 가장자리 → 골목 → 큰길 → 경로 밑 큰길
    val minors = decor.roads.filter { !it.major }
    val majors = decor.roads.filter { it.major }
    for (r in decor.roads) quad(r.a, r.b, r.widthM + 2.0)?.let { q -> cam.polygon(q)?.let { out.add(MapPrim.Poly(it, pal.roadEdge)) } }
    if (map.route.size >= 2) cam.ribbon(map.route, ROUTE_ROAD_W + 2.0, pal.roadEdge, out)
    for (r in minors) quad(r.a, r.b, r.widthM)?.let { q -> cam.polygon(q)?.let { out.add(MapPrim.Poly(it, pal.road)) } }
    for (r in majors) quad(r.a, r.b, r.widthM)?.let { q -> cam.polygon(q)?.let { out.add(MapPrim.Poly(it, pal.roadMajor)) } }
    if (map.route.size >= 2) cam.ribbon(map.route, ROUTE_ROAD_W, pal.roadMajor, out)

    // 건물: 먼 것부터 (옆면 → 윗면)
    class Drawn(val f: Double, val side: FloatArray, val top: FloatArray, val alt: Boolean)
    val drawn = ArrayList<Drawn>()
    for (b in decor.buildings) {
        val f = cam.fwd(b.center)
        if (f < cam.fNear + 8 || f > 650) continue
        if (abs(cam.right(b.center)) > cam.halfWidthM(f) + 40) continue
        val base = cam.polygon(b.pts) ?: continue
        val lift = (b.heightM * cam.s0 * cam.k(f) * 0.5).toFloat()
        val top = base.copyOf()
        for (i in 1 until top.size step 2) top[i] -= lift
        val side = convexHull(base + top)
        drawn.add(Drawn(f, side, top, b.alt))
    }
    drawn.sortByDescending { it.f }
    for (d in drawn) {
        out.add(MapPrim.Poly(d.side, pal.buildingSide))
        out.add(MapPrim.Poly(d.top, if (d.alt) pal.building2 else pal.building))
    }

    // 경로: 지나온 구간(회색) → 디버그 탐색 구간 → 남은 구간(테두리 + 파랑)
    val along = drive.distanceAlongRouteM
    if (map.route.size >= 2) {
        val passed = routeGeo.slice(0.0, along)
        val rest = routeGeo.slice(along, routeGeo.length)
        if (passed.size >= 2) cam.ribbon(passed, 5.5, pal.routePassed, out)
        if (debug != null && debug.corridor.size >= 2) cam.ribbon(debug.corridor, 26.0, withAlpha(pal.debugWarn, 0x61), out)
        if (rest.size >= 2) {
            cam.ribbon(rest, 9.5, pal.routeEdge, out)
            cam.ribbon(rest, 7.0, pal.route, out)
        }
    }

    out.add(MapPrim.Fog(h * 0.07f, h * 0.30f, pal.ground))

    // 가게: 먼 것부터. 실제 가게(엔진 데이터) + 장식 가게
    class PoiDraw(val f: Double, val pos: Vec2, val icon: MapIcon, val name: String, val real: Boolean, val hl: Boolean)
    val poiDraws = ArrayList<PoiDraw>()
    for (p in map.pois) {
        if (map.waypoint?.id == p.id) continue
        poiDraws.add(PoiDraw(cam.fwd(p.pos), p.pos, p.category.toMapIcon(), p.name, true, p.id in highlightPoiIds))
    }
    for (p in decor.pois) poiDraws.add(PoiDraw(cam.fwd(p.pos), p.pos, p.icon, p.name, false, false))
    poiDraws.sortByDescending { it.f }
    // 화면 맨 아래가 보여 주는 뒤쪽 거리 (차가 세로 72%라 약 25m)
    val fBottom = cam.depth / ((h - cam.yH) / (cam.yCar - cam.yH)) - cam.depth
    val behind = ArrayList<PoiDraw>()
    class Placed(val p: PoiDraw, val x: Float, val y: Float, val k: Float)
    val placed = ArrayList<Placed>()
    for (p in poiDraws) {
        if (p.hl && p.f < fBottom + 6) {
            behind.add(p)
            continue
        }
        if (p.f < -26 || p.f > 420) continue
        val y = cam.sy(p.f)
        val x = cam.sx(cam.right(p.pos), p.f)
        if (y < h * 0.15f || y > h + 10f || x < 16f || x > w - 16f) continue
        placed.add(Placed(p, x, y, cam.k(p.f).coerceIn(0.42, 1.15).toFloat()))
    }
    // 이름표 자리 잡기: 강조 → 가까운 가게 순으로 먼저 자리를 차지하고, 겹치면 이름을 생략한다
    val taken = ArrayList<FloatArray>()
    val labelOf = HashMap<Placed, MapPrim.Label>()
    for (q in placed.sortedWith(compareBy<Placed>({ !it.p.hl }, { it.p.f }))) {
        val p = q.p
        val nameVisible = p.name.isNotEmpty() &&
            (p.hl || (showAllNames && q.k >= 0.36f) || (p.real && q.k >= 0.4f) || (!p.real && q.k >= 0.46f))
        if (!nameVisible) continue
        val fs = 13f * spPx * (q.k * 1.15f).coerceIn(0.85f, 1.25f) * (if (p.hl) 1.2f else 1f)
        val half = p.name.length * fs * 0.5f + 4f
        val box = floatArrayOf(q.x - half, q.y - fs * 0.2f, q.x + half, q.y + fs * 1.3f)
        val hit = taken.any { it[0] < box[2] && box[0] < it[2] && it[1] < box[3] && box[1] < it[3] }
        if (hit && !p.hl) continue
        taken.add(box)
        labelOf[q] = MapPrim.Label(p.name, q.x, q.y + fs * 1.0f, fs, pal.label, pal.labelHalo)
    }
    for (q in placed) {
        val r = (if (q.p.real) 17f else 14.5f) * dpPx * q.k * (if (q.p.hl) 1.2f else 1f)
        out.add(MapPrim.Badge(q.x, q.y - r - 2f * dpPx * q.k, r, q.p.icon, q.p.hl))
        labelOf[q]?.let { out.add(it) }
    }
    // 카드에 뜬 가게가 이미 차 뒤로 지나가 화면 밖이면, 화면 아래쪽에 붙여 "○m 뒤"로 보여 준다
    for (p in behind) {
        if (p.f < -400) continue
        val r = 19f * dpPx
        val x = cam.sx(cam.right(p.pos), fBottom + 6).coerceIn(w * 0.3f, w * 0.7f)
        val y = h - r - 42f * dpPx
        out.add(MapPrim.Badge(x, y, r, p.icon, true))
        val fs = 15f * spPx
        val text = if (p.name.isNotEmpty()) "${p.name} · ${(-p.f).roundToInt()}m 뒤" else "${(-p.f).roundToInt()}m 뒤"
        out.add(MapPrim.Label(text, x, y + r + fs * 1.25f, fs, pal.label, pal.labelHalo))
    }

    // 목적지, 경유지
    fun pinAt(v: Vec2, waypoint: Boolean) {
        val f = cam.fwd(v)
        if (f < cam.fNear + 5 || f > 900) return
        val y = cam.sy(f)
        val x = cam.sx(cam.right(v), f)
        if (y < h * 0.12f || x < 0f || x > w) return
        val r = 19f * dpPx * cam.k(f).coerceIn(0.6, 1.1).toFloat()
        out.add(MapPrim.Pin(x, y - r, r, waypoint))
    }
    if (map.route.isNotEmpty()) pinAt(map.destination, false)
    map.waypoint?.let { pinAt(it.pos, true) }

    // 디버그: 궤적 점, t0, 후보 순위
    if (debug != null) {
        fun visible(v: Vec2): Pair<Float, Float>? {
            val f = cam.fwd(v)
            if (f < cam.fNear + 5 || f > 900) return null
            val x = cam.sx(cam.right(v), f)
            val y = cam.sy(f)
            return if (x in 0f..w && y in 0f..h) Pair(x, y) else null
        }
        for (v in debug.trail) visible(v)?.let { out.add(MapPrim.Disc(it.first, it.second, 3f * dpPx, pal.debugText)) }
        debug.t0Pos?.let { t0 ->
            visible(t0)?.let {
                out.add(MapPrim.Disc(it.first, it.second, 7f * dpPx, pal.debugDanger))
                out.add(MapPrim.Disc(it.first, it.second, 13f * dpPx, pal.debugDanger, 2f * dpPx))
                out.add(MapPrim.Label("t0", it.first + 22f * dpPx, it.second + 5f * dpPx, 14f * spPx, pal.debugText, pal.labelHalo))
            }
        }
        debug.candidates.forEachIndexed { i, c ->
            visible(c.poi.pos)?.let {
                out.add(MapPrim.Disc(it.first, it.second - 19f * dpPx, 23f * dpPx, pal.debugWarn, 3f * dpPx))
                out.add(MapPrim.Label("${i + 1}", it.first, it.second - 46f * dpPx, 16f * spPx, pal.debugText, pal.labelHalo))
            }
        }
    }

    out.add(MapPrim.Car(cam.cx.toFloat(), cam.yCar.toFloat(), 58f * dpPx))
    return out
}
