package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/** 위경도 한 점 */
data class LatLon(val lat: Double, val lon: Double)

/**
 * 경로 시작점 기준 equirectangular 근사 투영.
 * x = 동쪽 +, y = 북쪽 + (Contract의 Vec2 좌표계)
 */
class LocalProjection(private val ref: LatLon) {

    fun toLocal(p: LatLon): Vec2 {
        val x = Math.toRadians(p.lon - ref.lon) * EARTH_RADIUS_M * cos(Math.toRadians(ref.lat))
        val y = Math.toRadians(p.lat - ref.lat) * EARTH_RADIUS_M
        return Vec2(x, y)
    }

    fun toLatLon(p: Vec2): LatLon {
        val lat = ref.lat + Math.toDegrees(p.y / EARTH_RADIUS_M)
        val lon = ref.lon + Math.toDegrees(p.x / (EARTH_RADIUS_M * cos(Math.toRadians(ref.lat))))
        return LatLon(lat, lon)
    }

    companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}

object Geo {

    fun distance(a: Vec2, b: Vec2): Double = hypot(b.x - a.x, b.y - a.y)

    fun lerp(a: Vec2, b: Vec2, t: Double): Vec2 =
        Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    /** 점 p에서 선분 ab까지의 최단 거리 */
    fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double =
        distance(p, closestOnSegment(p, a, b).point)

    /** 점 p에서 선분 ab 위 가장 가까운 점과 그 보간 계수 t(0~1) */
    fun closestOnSegment(p: Vec2, a: Vec2, b: Vec2): SegmentProjection {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return SegmentProjection(a, 0.0)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        return SegmentProjection(Vec2(a.x + dx * t, a.y + dy * t), t)
    }

    /**
     * 진행 방향 h = (hx, hy)에서 본 차→가게 벡터 (dx, dy)의 좌우.
     * cross = hx*dy - hy*dx → 양수 LEFT, 음수 RIGHT (engine/PROMPT.md 6절)
     */
    fun cross(hx: Double, hy: Double, dx: Double, dy: Double): Double = hx * dy - hy * dx

    /** 두 점을 잇는 방향각(rad). 0 = 동쪽, 반시계 + */
    fun heading(from: Vec2, to: Vec2): Double = atan2(to.y - from.y, to.x - from.x)

    /** 각도를 (-PI, PI] 범위로 정규화 */
    fun normalizeAngle(rad: Double): Double {
        var a = rad
        while (a > Math.PI) a -= 2 * Math.PI
        while (a <= -Math.PI) a += 2 * Math.PI
        return a
    }
}

data class SegmentProjection(val point: Vec2, val t: Double)

/** 폴리라인 위 투영 결과 */
data class RouteProjection(
    val distanceAlongM: Double,
    val lateralM: Double,
    val point: Vec2
)

/**
 * 경로(꺾은선) 유틸. 누적 거리, 거리→좌표/방향, 좌표→경로 투영을 제공한다.
 */
class Polyline(val points: List<Vec2>) {

    val cumulative: DoubleArray = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) {
            acc[i] = acc[i - 1] + Geo.distance(points[i - 1], points[i])
        }
    }

    val lengthM: Double = if (points.isEmpty()) 0.0 else cumulative.last()

    /** 경로 시작점부터 거리 d 지점의 좌표 (범위 밖이면 양 끝점) */
    fun pointAt(d: Double): Vec2 {
        require(points.isNotEmpty()) { "빈 폴리라인" }
        if (d <= 0.0) return points.first()
        if (d >= lengthM) return points.last()
        val i = segmentIndexAt(d)
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen == 0.0) 0.0 else (d - cumulative[i]) / segLen
        return Geo.lerp(points[i], points[i + 1], t)
    }

    /** 거리 d 지점의 진행 방향각(rad) */
    fun headingAt(d: Double): Double {
        require(points.size >= 2) { "점이 2개 이상 필요" }
        val i = segmentIndexAt(d.coerceIn(0.0, lengthM - 1e-9))
        return Geo.heading(points[i], points[i + 1])
    }

    /** 점 p를 경로에 수직 투영. 가장 가까운 선분 기준 */
    fun project(p: Vec2): RouteProjection {
        require(points.size >= 2) { "점이 2개 이상 필요" }
        var best = RouteProjection(0.0, Double.MAX_VALUE, points.first())
        for (i in 0 until points.size - 1) {
            val proj = Geo.closestOnSegment(p, points[i], points[i + 1])
            val d = Geo.distance(p, proj.point)
            if (d < best.lateralM) {
                val along = cumulative[i] + Geo.distance(points[i], proj.point)
                best = RouteProjection(along, d, proj.point)
            }
        }
        return best
    }

    private fun segmentIndexAt(d: Double): Int {
        var lo = 0
        var hi = points.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cumulative[mid] <= d) lo = mid else hi = mid - 1
        }
        return lo
    }
}
