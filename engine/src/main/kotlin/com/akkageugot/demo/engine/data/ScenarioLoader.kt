package com.akkageugot.demo.engine.data

import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.engine.core.Geo
import com.akkageugot.demo.engine.core.LatLon
import com.akkageugot.demo.engine.core.LocalProjection
import com.akkageugot.demo.engine.core.Polyline
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.cos
import kotlin.math.sin

@Serializable
data class RoutePointJson(val lat: Double, val lon: Double)

@Serializable
data class PoiJson(
    val id: String,
    val name: String,
    val category: String,
    val distanceAlongRouteM: Double,
    val side: String, // "right" | "left"
    val offsetM: Double,
    val open: Boolean = true
)

@Serializable
data class ScenarioStepJson(
    /** 이 지점을 지나면 */
    val triggerDistanceAlongRouteM: Double,
    /** delaySec 뒤에 */
    val delaySec: Double,
    /** 이 프리셋 발화를 자동 입력한다 (engine/PROMPT.md 8절 번호) */
    val preset: Int
)

@Serializable
data class ScenarioJson(
    val n: Int,
    val startDistanceAlongRouteM: Double,
    /** 발표용 배속. 긴 구간을 지나 "3분 전"을 되짚는 시나리오는 더 빠르게 재생한다 */
    val timeScale: Int = 1,
    val steps: List<ScenarioStepJson> = emptyList()
)

@Serializable
data class ScenarioFileJson(
    val route: List<RoutePointJson>,
    val pois: List<PoiJson>,
    val scenarios: List<ScenarioJson>
)

/** 파싱 완료된 시나리오 데이터. POI 좌표는 경로 기준 정의에서 계산한다 */
data class ScenarioData(
    val routeLatLon: List<LatLon>,
    val route: Polyline,
    val pois: List<Poi>,
    val scenarios: List<ScenarioJson>
)

/**
 * engine/src/main/assets/scenarios.json 파서. 파일 읽기는 호출자(엔진) 책임이라
 * 순수 JVM 테스트가 가능하다.
 */
object ScenarioLoader {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ScenarioData {
        val file = json.decodeFromString<ScenarioFileJson>(text)
        require(file.route.size >= 2) { "route에는 점이 2개 이상 필요하다" }

        val latLons = file.route.map { LatLon(it.lat, it.lon) }
        val projection = LocalProjection(latLons.first())
        val route = Polyline(latLons.map { projection.toLocal(it) })

        val pois = file.pois.map { p ->
            val road = route.pointAt(p.distanceAlongRouteM)
            val heading = route.headingAt(p.distanceAlongRouteM)
            val hx = cos(heading)
            val hy = sin(heading)
            // 진행 방향 기준 오른쪽 법선 = (hy, -hx), 왼쪽 = (-hy, hx)
            val pos = when (p.side.lowercase()) {
                "right" -> Vec2(road.x + hy * p.offsetM, road.y - hx * p.offsetM)
                "left" -> Vec2(road.x - hy * p.offsetM, road.y + hx * p.offsetM)
                else -> throw IllegalArgumentException("side는 right/left만 가능: ${p.side}")
            }
            Poi(
                id = p.id,
                name = p.name,
                category = Category.valueOf(p.category),
                pos = pos,
                open = p.open
            )
        }

        return ScenarioData(latLons, route, pois, file.scenarios)
    }
}
