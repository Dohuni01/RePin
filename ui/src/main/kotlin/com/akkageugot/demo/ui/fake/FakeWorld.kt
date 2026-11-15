package com.akkageugot.demo.ui.fake

import com.akkageugot.demo.contract.Candidate
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.SavedStatus
import com.akkageugot.demo.contract.ScoreDetail
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.ui.geo.RouteGeo
import com.akkageugot.demo.ui.geo.plus
import com.akkageugot.demo.ui.geo.rightNormal
import com.akkageugot.demo.ui.geo.times

/** 가짜 엔진과 미리보기가 같이 쓰는 지어낸 지도 데이터 */
object FakeWorld {
    /** 약 3km: 북쪽 직진 1000m → 우회전 → 동쪽 1200m → 좌회전 → 북쪽 800m */
    val route: List<Vec2> = listOf(
        Vec2(0.0, 0.0),
        Vec2(0.0, 1000.0),
        Vec2(1200.0, 1000.0),
        Vec2(1200.0, 1800.0)
    )
    val geo = RouteGeo(route)

    /** 가게별 경로상 위치(m). 가짜 엔진이 "지나온 거리"를 계산할 때 쓴다 */
    val alongById = HashMap<String, Double>()

    private fun poi(
        id: String, name: String, category: Category,
        along: Double, right: Boolean, offset: Double, open: Boolean = true
    ): Poi {
        val n = geo.dirAt(along).rightNormal() * (if (right) offset else -offset)
        alongById[id] = along
        return Poi(id, name, category, geo.posAt(along) + n, open)
    }

    /** 시나리오가 참조하는 손으로 쓴 가게들 (id 고정). 지도에는 아래 buildingShops와 합쳐 그린다 */
    private val handPois: List<Poi> = listOf(
        // 첫 직선 (0~1000m)
        poi("p01", "오후세시커피", Category.CAFE, 120.0, false, 20.0),
        poi("p02", "골목국수", Category.RESTAURANT, 200.0, true, 22.0),
        poi("p03", "초록꽃집", Category.FLOWER, 280.0, false, 18.0),
        poi("p04", "달빛제과", Category.BAKERY, 420.0, true, 18.0),      // 오른쪽에 빵집 하나만
        poi("p05", "모퉁이편의점", Category.CONVENIENCE, 560.0, false, 20.0),
        poi("p06", "별밭식당", Category.RESTAURANT, 680.0, true, 24.0, open = false),
        poi("p07", "구름빵집", Category.BAKERY, 800.0, true, 18.0),       // 빵집 + 카페가 붙은 구간
        poi("p08", "느린오후카페", Category.CAFE, 825.0, true, 18.0),
        poi("p09", "한입문구", Category.ETC, 900.0, false, 20.0),
        // 동쪽 구간 (1000~2200m)
        poi("p10", "봄날커피", Category.CAFE, 1150.0, true, 20.0),
        poi("p11", "든든밥상", Category.RESTAURANT, 1260.0, false, 22.0),
        poi("p12", "밀밭베이커리", Category.BAKERY, 1400.0, false, 18.0), // 왼쪽 빵집, 오른쪽 카페
        poi("p13", "파란문카페", Category.CAFE, 1400.0, true, 18.0),
        poi("p14", "길모퉁이꽃집", Category.FLOWER, 1560.0, true, 20.0, open = false),
        poi("p15", "새벽편의점", Category.CONVENIENCE, 1700.0, false, 20.0),
        poi("p16", "은행나무식당", Category.RESTAURANT, 1850.0, true, 22.0),
        // 1900~2420m: 가게 데이터가 없는 구간
        // 마지막 직선 (2200~3000m)
        poi("p17", "하루세탁", Category.ETC, 2480.0, false, 20.0),
        poi("p18", "소금빵연구소", Category.BAKERY, 2600.0, true, 18.0),
        poi("p19", "노을커피", Category.CAFE, 2740.0, false, 20.0),
        poi("p20", "종점편의점", Category.CONVENIENCE, 2880.0, true, 20.0)
    )

    // ---------------------------------------------------------------- 빌딩(한 건물에 여러 샵)

    /** 한 건물에 들어있는 샵 묶음. shops는 추천 순서(인기도 내림차순)로 저장한다 */
    data class Building(val id: String, val along: Double, val side: Side, val shops: List<Poi>)

    private val SHOP_NAMES: Map<Category, List<String>> = mapOf(
        Category.BAKERY to listOf("소보로상점", "밀알베이커리", "아침빵공방", "부엉이제과", "오븐의하루"),
        Category.CAFE to listOf("볕드는카페", "구름로스터리", "한스푼커피", "오늘의커피", "작은숲다방"),
        Category.RESTAURANT to listOf("한상차림", "골목식당", "맛나분식", "청기와국밥", "손칼국수집"),
        Category.CONVENIENCE to listOf("스마일마트", "밤샘편의점", "온동네마트", "스물네시간", "골목상회"),
        Category.FLOWER to listOf("꽃내음", "사계화원", "들꽃공방", "플로라", "봄뜰꽃집")
    )
    private val SHOP_CATEGORIES = listOf(
        Category.BAKERY, Category.CAFE, Category.RESTAURANT, Category.CONVENIENCE, Category.FLOWER
    )

    /** 시드 고정이라 매번 같은 배치. 경로를 따라 여러 샵이 든 건물을 만든다 */
    val buildings: List<Building> = buildBuildings(seed = 20261115L)

    private fun buildBuildings(seed: Long): List<Building> {
        val rnd = java.util.Random(seed)
        // 손으로 쓴 가게와 겹치지 않게 비는 구간에 건물을 둔다
        val anchors = listOf(
            150.0 to true, 340.0 to false, 640.0 to true, 940.0 to false,
            1180.0 to true, 1480.0 to false, 1720.0 to true, 2060.0 to false,
            2460.0 to true, 2760.0 to false
        )
        val result = ArrayList<Building>()
        anchors.forEachIndexed { bi, (along, right) ->
            val count = 2 + if (rnd.nextDouble() < 0.5) 1 else 0   // 2~3개
            val cats = SHOP_CATEGORIES.shuffled(rnd).take(count)
            val used = HashSet<String>()
            val shops = cats.mapIndexed { si, cat ->
                val names = SHOP_NAMES.getValue(cat).filter { it !in used }
                val name = names[rnd.nextInt(names.size)]
                used += name
                val id = "b${(bi + 1).toString().padStart(2, '0')}s$si"
                val popularity = rnd.nextDouble()
                // 같은 건물이라 거의 같은 자리. 지도에서 덩어리로 보이게 along만 살짝 어긋나게 둔다
                val shopAlong = along + si * 7.0
                val open = rnd.nextDouble() > 0.15
                Triple(poi(id, name, cat, shopAlong, right, 17.0, open = open), popularity, si)
            }.sortedByDescending { it.second }  // 인기도 높은 순 = 추천 순서
            result += Building("b${(bi + 1).toString().padStart(2, '0')}", along, if (right) Side.RIGHT else Side.LEFT,
                shops.map { it.first })
        }
        return result
    }

    private val buildingShops: List<Poi> = buildings.flatMap { it.shops }

    /** 지도에 그릴 가게 전체 = 손으로 쓴 시나리오 가게 + 건물 샵 */
    val pois: List<Poi> = handPois + buildingShops

    /** 손으로 쓴 가게도 각각 '한 칸짜리 건물'로 보고 "옆에 뭐 있어?" 후보에 넣는다 */
    private val handBuildings: List<Building> = handPois.map { p ->
        Building("hb_${p.id}", alongById.getValue(p.id), sideOf(p), listOf(p))
    }

    /** "옆에 뭐 있어?" 후보 = 건물 클러스터 + 손으로 쓴 가게 (경로 전체에 고르게 퍼져 있다) */
    private val nearbyCandidates: List<Building> = buildings + handBuildings

    /**
     * 기준 거리에서 지금 옆/바로 지나온 가게(건물).
     * 건물과 손으로 쓴 가게를 모두 후보로 보고 along이 가장 가까운 걸 고르되,
     * 90m을 벗어나면(옆에 아무것도 없으면) null을 돌려, 한참 전이나 멀리 있는 걸
     * 억지로 추천하지 않는다 (예전 nearestBuilding은 거리 제한이 없어 200~300m 전 건물을 추천했다).
     */
    fun nearbyBuilding(refAlong: Double): Building? =
        nearbyCandidates.minByOrNull { kotlin.math.abs(it.along - refAlong) }
            ?.takeIf { kotlin.math.abs(it.along - refAlong) <= NEARBY_MATCH_M }

    /** 경로 기준 가게의 좌우 (project lateral: 왼쪽 +, 오른쪽 -) */
    private fun sideOf(p: Poi): Side {
        val lat = geo.project(p.pos)?.lateral ?: return Side.UNKNOWN
        return if (lat < 0) Side.RIGHT else Side.LEFT
    }

    private const val NEARBY_MATCH_M = 90.0

    fun poi(id: String): Poi = pois.first { it.id == id }
    val bakery: Poi get() = poi("p04")

    /** 진짜 엔진과 같은 식: 0.35*(1 - d/60) + 0.25*방향 + 0.25*업종 + 0.15*시간 */
    fun candidate(id: String, side: Side, distanceM: Double, sideScore: Double, categoryScore: Double, timeScore: Double): Candidate {
        val distScore = (1.0 - distanceM / 60.0).coerceIn(0.0, 1.0)
        val score = 0.35 * distScore + 0.25 * sideScore + 0.25 * categoryScore + 0.15 * timeScore
        return Candidate(poi(id), score, side, distanceM, ScoreDetail(distScore, sideScore, categoryScore, timeScore))
    }

    /** "방금 오른쪽에 있던 빵집 뭐야?" → 달빛제과가 확실 */
    val bakeryCandidates: List<Candidate> = listOf(
        candidate("p04", Side.RIGHT, 18.0, 1.0, 1.0, 1.0),
        candidate("p05", Side.LEFT, 20.0, 0.0, 0.0, 0.3),
        candidate("p03", Side.LEFT, 18.0, 0.0, 0.0, 0.0)
    )

    /** "방금 거기 뭐야?" → 빵집과 카페가 붙어 있어 애매 */
    val pairCandidates: List<Candidate> = listOf(
        candidate("p07", Side.RIGHT, 18.0, 0.6, 0.5, 1.0),
        candidate("p08", Side.RIGHT, 18.0, 0.6, 0.5, 0.8),
        candidate("p09", Side.LEFT, 20.0, 0.4, 0.5, 0.3)
    )

    private val facingCandidates: List<Candidate> = listOf(
        candidate("p12", Side.LEFT, 18.0, 0.4, 0.5, 1.0),
        candidate("p13", Side.RIGHT, 18.0, 0.6, 0.5, 0.6),
        candidate("p11", Side.LEFT, 22.0, 0.4, 0.5, 0.0)
    )

    /** 샘플 3개. 새벽에 실행해도 "오늘" 목록에 보이도록 오늘 0시 이후 시각으로 맞춘다 */
    fun sampleSaved(nowMs: Long): List<SavedPlace> {
        val start = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun at(hoursAgo: Int) = maxOf(nowMs - hoursAgo * 3_600_000L, start + (4 - hoursAgo) * 1000L)
        return sampleList(::at)
    }

    private fun sampleList(at: (Int) -> Long): List<SavedPlace> = listOf(
        SavedPlace(
            id = "s_sample_1",
            createdAtMs = at(3),
            label = "오전 8:12 · 출근길",
            pos = geo.posAt(2740.0),
            utterance = "방금 오른쪽에 있던 빵집 뭐야?",
            candidates = listOf(
                candidate("p18", Side.RIGHT, 18.0, 1.0, 1.0, 1.0),
                candidate("p19", Side.LEFT, 20.0, 0.0, 0.0, 0.4),
                candidate("p17", Side.LEFT, 20.0, 0.0, 0.0, 0.0)
            ),
            selectedPoiId = "p18",
            status = SavedStatus.CONFIRMED,
            memo = ""
        ),
        SavedPlace(
            id = "s_sample_2",
            createdAtMs = at(2),
            label = "오전 8:31 · 출근길",
            pos = geo.posAt(1520.0),
            utterance = "방금 거기 뭐야?",
            candidates = facingCandidates,
            selectedPoiId = null,
            status = SavedStatus.PENDING,
            memo = ""
        ),
        SavedPlace(
            id = "s_sample_3",
            createdAtMs = at(1),
            label = "오후 12:47 · 점심 무렵",
            pos = geo.posAt(2100.0),
            utterance = "방금 거 뭐야?",
            candidates = emptyList(),
            selectedPoiId = null,
            status = SavedStatus.LOCATION_ONLY,
            memo = "노란 간판, 새로 생긴 가게"
        )
    )
}
