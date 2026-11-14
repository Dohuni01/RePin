package com.akkageugot.demo.ui.geo

import com.akkageugot.demo.contract.TurnDirection
import com.akkageugot.demo.contract.TurnPoint
import com.akkageugot.demo.contract.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

operator fun Vec2.plus(o: Vec2) = Vec2(x + o.x, y + o.y)
operator fun Vec2.minus(o: Vec2) = Vec2(x - o.x, y - o.y)
operator fun Vec2.times(k: Double) = Vec2(x * k, y * k)
fun Vec2.dot(o: Vec2) = x * o.x + y * o.y
fun Vec2.length() = hypot(x, y)

/** 진행 방향 단위 벡터 기준 오른쪽 법선 (계약 좌표: y 북쪽 +) */
fun Vec2.rightNormal() = Vec2(y, -x)

/** 꺾은선 경로 위의 거리 계산. 화면 전용 계산과 가짜 엔진이 같이 쓴다. */
class RouteGeo(val pts: List<Vec2>) {
    private val cum = DoubleArray(pts.size.coerceAtLeast(1))
    val length: Double

    init {
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + (pts[i] - pts[i - 1]).length()
        length = if (pts.isEmpty()) 0.0 else cum[pts.size - 1]
    }

    fun distanceAtIndex(i: Int): Double = cum[i.coerceIn(0, cum.size - 1)]

    /** d가 속한 선분의 시작 인덱스 */
    fun segIndexAt(d: Double): Int {
        if (pts.size < 2) return 0
        val dd = d.coerceIn(0.0, length)
        for (i in 0 until pts.size - 1) if (dd <= cum[i + 1]) return i
        return pts.size - 2
    }

    fun posAt(d: Double): Vec2 {
        if (pts.isEmpty()) return Vec2(0.0, 0.0)
        if (pts.size == 1) return pts[0]
        val dd = d.coerceIn(0.0, length)
        val i = segIndexAt(dd)
        val segLen = cum[i + 1] - cum[i]
        if (segLen <= 1e-9) return pts[i]
        val t = (dd - cum[i]) / segLen
        return pts[i] + (pts[i + 1] - pts[i]) * t
    }

    /** d 지점 선분의 단위 방향 벡터 */
    fun dirAt(d: Double): Vec2 {
        if (pts.size < 2) return Vec2(0.0, 1.0)
        var i = segIndexAt(d)
        // 길이 0인 선분은 건너뛴다
        while (i < pts.size - 2 && cum[i + 1] - cum[i] <= 1e-9) i++
        val v = pts[i + 1] - pts[i]
        val l = v.length()
        return if (l <= 1e-9) Vec2(0.0, 1.0) else v * (1.0 / l)
    }

    /** 모퉁이에서 부드럽게 도는 진행 방향 (0 = 동쪽, 반시계 +) */
    fun headingAt(d: Double, window: Double = 8.0): Double {
        val v = posAt(d + window) - posAt(d - window)
        if (v.length() < 1e-6) {
            val u = dirAt(d)
            return atan2(u.y, u.x)
        }
        return atan2(v.y, v.x)
    }

    class Projection(val along: Double, val lateral: Double) // lateral: 왼쪽 +, 오른쪽 -

    /** [fromD, toD] 구간에서 p와 가장 가까운 경로 위 지점 */
    fun project(p: Vec2, fromD: Double = 0.0, toD: Double = length): Projection? {
        if (pts.size < 2) return null
        var best: Projection? = null
        var bestDist = Double.MAX_VALUE
        for (i in 0 until pts.size - 1) {
            val a0 = cum[i]
            val a1 = cum[i + 1]
            if (a1 < fromD || a0 > toD || a1 - a0 <= 1e-9) continue
            val u = (pts[i + 1] - pts[i]) * (1.0 / (a1 - a0))
            val rel = p - pts[i]
            val lo = maxOf(a0, fromD) - a0
            val hi = minOf(a1, toD) - a0
            val t = rel.dot(u).coerceIn(lo, hi)
            val foot = pts[i] + u * t
            val dist = (p - foot).length()
            if (dist < bestDist) {
                bestDist = dist
                val cross = u.x * (p.y - foot.y) - u.y * (p.x - foot.x)
                best = Projection(a0 + t, if (cross >= 0) dist else -dist)
            }
        }
        return best
    }

    /** [from, to] 구간의 점 목록 */
    fun slice(from: Double, to: Double): List<Vec2> {
        if (pts.size < 2) return emptyList()
        val a = from.coerceIn(0.0, length)
        val b = to.coerceIn(0.0, length)
        if (b <= a) return emptyList()
        val out = ArrayList<Vec2>()
        out.add(posAt(a))
        for (i in 1 until pts.size - 1) if (cum[i] > a && cum[i] < b) out.add(pts[i])
        out.add(posAt(b))
        return out
    }
}

/** 꺾이는 각도가 큰 지점을 회전 지점으로 뽑는다 */
fun computeTurns(route: List<Vec2>): List<TurnPoint> {
    val geo = RouteGeo(route)
    val out = ArrayList<TurnPoint>()
    for (i in 1 until route.size - 1) {
        val a = route[i] - route[i - 1]
        val b = route[i + 1] - route[i]
        if (a.length() < 1e-6 || b.length() < 1e-6) continue
        val cross = a.x * b.y - a.y * b.x
        val sin = cross / (a.length() * b.length())
        if (abs(sin) < 0.35) continue
        out.add(
            TurnPoint(
                pos = route[i],
                distanceAlongRouteM = geo.distanceAtIndex(i),
                direction = if (cross > 0) TurnDirection.LEFT else TurnDirection.RIGHT
            )
        )
    }
    return out
}
