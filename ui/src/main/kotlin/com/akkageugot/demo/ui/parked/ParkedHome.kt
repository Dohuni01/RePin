package com.akkageugot.demo.ui.parked

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.SavedStatus
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.ui.common.AkkaIcon
import com.akkageugot.demo.ui.common.IconKind
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.common.badgeColor
import com.akkageugot.demo.ui.geo.RouteGeo
import com.akkageugot.demo.ui.navi.MicButton
import com.akkageugot.demo.ui.overlay.HeadsUpCard
import com.akkageugot.demo.ui.overlay.MirrorFrame
import com.akkageugot.demo.ui.overlay.MirrorReflection
import com.akkageugot.demo.ui.overlay.VoiceBubble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 정차(P) 화면에서 무엇을 보여 줄지. MAP이면 홈 대신 내비 지도 전체 화면 */
enum class ParkedView { HOME, MAP, SAVED, SETTINGS }

/**
 * 정차 홈 (RePin 거울 앨범). 기어 P + 주행 제한 해제일 때 화면 전체를 쓴다.
 * 가운데: 홈(최근 장소 큰 카드 + 작은 카드 + 오늘 지나온 길) / 저장 목록 / 설정
 * 아래 막대: [말하기] [지도] [저장] [설정]
 */
@Composable
fun ParkedHome(
    state: AppState,
    view: ParkedView,
    onView: (ParkedView) -> Unit,
    dispatch: (UiAction) -> Unit,
    onShowQr: (SavedPlace) -> Unit,
    settingsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis()
) {
    val c = LocalAkkaColors.current
    val today = remember(state.saved, nowMs / 60_000L) { todaySaved(state.saved, nowMs) }
    Box(modifier.fillMaxSize().background(c.skyBrush)) {
        DuskBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (view) {
                    ParkedView.SAVED -> ParkedPanel(
                        saved = state.saved,
                        dispatch = dispatch,
                        onShowQr = onShowQr,
                        onBack = { onView(ParkedView.HOME) },
                        nowMs = nowMs
                    )
                    ParkedView.SETTINGS -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp))) {
                        settingsContent()
                    }
                    else -> HomeContent(state, today, dispatch, onShowQr, nowMs)
                }
            }
            Spacer(Modifier.height(14.dp))
            BottomBar(state, view, onView, dispatch)
        }

        // 정차 중에도 말하기는 된다: 말풍선과 거울 카드를 홈 위에 띄운다
        if (state.voiceLines.isNotEmpty()) {
            VoiceBubble(
                state.voiceLines,
                Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 140.dp).widthIn(max = 560.dp)
            )
        }
        state.headsUp?.let { h ->
            HeadsUpCard(
                headsUp = h,
                onClick = { dispatch(UiAction.HeadsUpClicked(h.id, it)) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).widthIn(max = 640.dp).fillMaxWidth(0.55f)
            )
        }
    }
}

// ---------------------------------------------------------------- 홈 본문

@Composable
private fun HomeContent(
    state: AppState,
    today: List<SavedPlace>,
    dispatch: (UiAction) -> Unit,
    onShowQr: (SavedPlace) -> Unit,
    nowMs: Long
) {
    // 작은 카드를 누르면 그 장소가 큰 카드로 올라온다 (화면 안의 선택일 뿐, 엔진 상태는 안 바꾼다)
    var featuredId by remember { mutableStateOf<String?>(null) }
    val featured = today.firstOrNull { it.id == featuredId } ?: today.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        HomeHeader(state, today.size)
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val smallCount = if (maxWidth < 900.dp) 1 else 2
            val others = today.filter { it.id != featured?.id }.take(smallCount)
            if (featured == null) {
                EmptyHome(Modifier.fillMaxSize())
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FeaturedCard(featured, dispatch, onShowQr, Modifier.weight(1.2f).fillMaxHeight())
                    others.forEach { p ->
                        SmallCard(p, onClick = { featuredId = p.id }, modifier = Modifier.weight(1f))
                    }
                    repeat(smallCount - others.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (today.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            DayRibbon(today, nowMs)
        }
    }
}

@Composable
private fun HomeHeader(state: AppState, todayCount: Int) {
    val c = LocalAkkaColors.current
    // 엔진 값으로 계산: 지나온 거리 = 경로 위 진행 거리, 지나친 가게 = 이미 지나온 구간 길가(60m 안)의 가게 수
    val bucket = (state.drive.distanceAlongRouteM / 20.0).toInt()
    val passedShops = remember(state.map.route, state.map.pois, bucket) {
        val geo = RouteGeo(state.map.route)
        val d = state.drive.distanceAlongRouteM
        state.map.pois.count { poi ->
            val pr = geo.project(poi.pos)
            pr != null && pr.along <= d && abs(pr.lateral) <= 60.0
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MirrorFrame(Modifier.width(84.dp).height(46.dp), rim = 4.dp, elevation = 4.dp) {
            Box(Modifier.fillMaxSize().background(c.skyBrush))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("RePin", color = c.onSky, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("오늘 거울에 비친 곳 ${todayCount}개", color = c.onSky.copy(alpha = 0.85f), fontSize = 18.sp)
        }
        // 아직 달린 거리가 없으면(주행 전) 통계 상자는 숨긴다
        if (state.drive.distanceAlongRouteM >= 1.0) Row(
            Modifier.glass(RoundedCornerShape(22.dp)).padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Stat("지나온 길", "%.1fkm".format(state.drive.distanceAlongRouteM / 1000.0))
            Stat("지나친 가게", "${passedShops}곳")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val c = LocalAkkaColors.current
    Column {
        Text(label, color = c.textDim, fontSize = 15.sp)
        Text(value, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun EmptyHome(modifier: Modifier) {
    val c = LocalAkkaColors.current
    Column(
        modifier.glass(RoundedCornerShape(26.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MirrorFrame(Modifier.width(320.dp).height(130.dp)) {
            MirrorReflection(null, Side.RIGHT)
        }
        Spacer(Modifier.height(18.dp))
        Text("아직 거울에 비친 곳이 없어요", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "주행 중에 \"방금 거기 뭐야?\"라고 물어보세요",
            color = c.textDim, fontSize = 19.sp, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** 큰 카드: 거울 + 이름/상태 + 시간·방향 + [안내 시작] [폰으로] */
@Composable
private fun FeaturedCard(
    place: SavedPlace,
    dispatch: (UiAction) -> Unit,
    onShowQr: (SavedPlace) -> Unit,
    modifier: Modifier
) {
    val c = LocalAkkaColors.current
    val chosen = place.chosen()
    Column(modifier.glass(RoundedCornerShape(26.dp)).padding(14.dp)) {
        // 거울은 가로로 긴 비율을 지킨다 (높이만 늘면 동그랗게 변하므로)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            MirrorFrame(Modifier.fillMaxWidth().aspectRatio(2.4f)) {
                MirrorReflection(chosen?.poi?.category, chosen?.side ?: Side.UNKNOWN)
                SideTag(chosen?.side, 18)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chosen?.poi?.name ?: "이 근처",
                color = c.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            StatusBadge(place.status)
        }
        Text(
            place.label + sideText(chosen?.side),
            color = c.textDim, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelButton("안내 시작", IconKind.FLAG, primary = true, modifier = Modifier.weight(1f)) {
                dispatch(UiAction.NavigateTo(place.id))
            }
            PanelButton("폰으로", IconKind.PHONE, primary = false, modifier = Modifier.weight(1f)) {
                onShowQr(place)
            }
        }
    }
}

/** 작은 카드: 누르면 큰 카드로 올라간다 */
@Composable
private fun SmallCard(place: SavedPlace, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalAkkaColors.current
    val chosen = place.chosen()
    Column(modifier.glass(RoundedCornerShape(26.dp)).clickable(onClick = onClick).padding(12.dp)) {
        MirrorFrame(Modifier.fillMaxWidth().aspectRatio(2.0f), rim = 5.dp, elevation = 6.dp) {
            MirrorReflection(chosen?.poi?.category, chosen?.side ?: Side.UNKNOWN)
            SideTag(chosen?.side, 15)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chosen?.poi?.name ?: "이 근처",
                color = c.text, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            StatusBadge(place.status)
        }
        Text(
            place.label + sideText(chosen?.side),
            color = c.textDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun BoxScope.SideTag(side: Side?, sizeSp: Int) {
    val text = when (side) {
        Side.RIGHT -> "오른쪽 ▶"
        Side.LEFT -> "◀ 왼쪽"
        else -> return
    }
    Text(
        text, color = Color.White, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .align(if (side == Side.RIGHT) Alignment.TopEnd else Alignment.TopStart)
            .padding(horizontal = 34.dp, vertical = 10.dp)
    )
}

private fun sideText(side: Side?): String = when (side) {
    Side.LEFT -> " · 왼쪽에서 지나침"
    Side.RIGHT -> " · 오른쪽에서 지나침"
    else -> ""
}

// ---------------------------------------------------------------- 오늘 지나온 길

/** 첫 저장부터 지금까지를 노을 그라데이션 선으로, 저장한 곳은 업종 색 점 + 시각 */
@Composable
internal fun DayRibbon(today: List<SavedPlace>, nowMs: Long) {
    val c = LocalAkkaColors.current
    val first = today.minOf { it.createdAtMs }
    val span = (nowMs - first).coerceAtLeast(60_000L).toFloat()
    val fmt = remember { SimpleDateFormat("h:mm", Locale.KOREA) }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("오늘 지나온 길", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("거울에 비친 순간 ${today.size}번", color = c.textDim, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(50.dp)) {
            val r = 11.dp
            val w = maxWidth
            fun xAt(t: Float): Dp = r + (w - r * 2) * t
            Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                val y = size.height / 2f
                val rp = r.toPx()
                drawLine(
                    Brush.horizontalGradient(listOf(c.skyTop, c.skyMid, c.skyLow, c.accent)),
                    Offset(rp, y), Offset(size.width - rp, y), strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round
                )
                for (p in today) {
                    val t = ((p.createdAtMs - first) / span).coerceIn(0f, 1f)
                    val x = xAt(t).toPx()
                    drawCircle(Color.White, rp + 3.dp.toPx(), Offset(x, y))
                    drawCircle(p.chosen()?.poi?.category.badgeColor(), rp, Offset(x, y))
                }
                val ex = size.width - rp
                drawCircle(Color.White, rp + 3.dp.toPx(), Offset(ex, y))
                drawCircle(c.accent, rp, Offset(ex, y))
            }
            // 시각 글자: 서로(또는 "지금"과) 너무 붙으면 뒤의 것을 생략한다
            val end = xAt(1f)
            var lastX = (-100).dp
            today.sortedBy { it.createdAtMs }.forEach { p ->
                val x = xAt(((p.createdAtMs - first) / span).coerceIn(0f, 1f))
                if (x - lastX >= 56.dp && end - x >= 56.dp) {
                    RibbonLabel(fmt.format(Date(p.createdAtMs)), x)
                    lastX = x
                }
            }
            RibbonLabel("지금", end)
        }
    }
}

@Composable
private fun RibbonLabel(text: String, x: Dp) {
    val c = LocalAkkaColors.current
    Text(
        text, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        maxLines = 1, modifier = Modifier.offset(x = x - 30.dp, y = 28.dp).width(60.dp)
    )
}

// ---------------------------------------------------------------- 아래 막대

@Composable
private fun BottomBar(state: AppState, view: ParkedView, onView: (ParkedView) -> Unit, dispatch: (UiAction) -> Unit) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(42.dp)
    // 같은 탭을 다시 누르면 홈으로
    fun toggle(target: ParkedView) = onView(if (view == target) ParkedView.HOME else target)
    Box(Modifier.fillMaxWidth().height(84.dp)) {
        Box(Modifier.matchParentSize().glass(shape))
        Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                MicButton(
                    phase = state.voicePhase,
                    onPress = { dispatch(UiAction.MicPressed) },
                    onRelease = { dispatch(UiAction.MicReleased) },
                    modifier = Modifier.wrapContentSize(unbounded = true).offset(y = (-16).dp),
                    diameter = 104.dp
                )
            }
            BarTab("지도", IconKind.MAP, selected = false, Modifier.weight(1f)) { onView(ParkedView.MAP) }
            BarTab("저장", IconKind.STAR, selected = view == ParkedView.SAVED, Modifier.weight(1f)) { toggle(ParkedView.SAVED) }
            BarTab("설정", IconKind.GEAR, selected = view == ParkedView.SETTINGS, Modifier.weight(1f)) { toggle(ParkedView.SETTINGS) }
        }
    }
}

@Composable
private fun BarTab(label: String, icon: IconKind, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAkkaColors.current
    val tint = if (selected) c.accent else c.text
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(30.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AkkaIcon(icon, tint, Modifier.size(30.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- 배경, 공통

/** 노을 하늘 위 해 번짐(오른쪽 위)과 아래쪽 건물 실루엣 */
@Composable
private fun DuskBackdrop(modifier: Modifier) {
    val c = LocalAkkaColors.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x66FFE2B8), Color(0x00FFE2B8)),
                center = Offset(w * 0.93f, h * 0.18f), radius = h * 0.55f
            ),
            radius = h * 0.55f, center = Offset(w * 0.93f, h * 0.18f)
        )
        val city = c.skyTop.copy(alpha = 0.22f)
        var x = 0f
        var i = 0
        while (x < w) {
            val bw = w * (0.035f + ((i * 17) % 30) / 900f)
            val bh = h * (0.07f + ((i * 29) % 40) / 300f)
            drawRect(city, Offset(x, h - bh), Size(bw, bh))
            x += bw + w * 0.004f
            i++
        }
    }
}

/** 노을 위에 뜬 반투명 유리 카드 */
@Composable
private fun Modifier.glass(shape: Shape): Modifier {
    val c = LocalAkkaColors.current
    return this
        .shadow(8.dp, shape, clip = false)
        .clip(shape)
        .background(c.surface.copy(alpha = 0.86f))
        .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
}

internal fun todaySaved(saved: List<SavedPlace>, nowMs: Long): List<SavedPlace> {
    val start = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    // 최근에 저장한 곳이 앞으로
    return saved.filter { it.createdAtMs >= start }.asReversed()
}

/** 고른 후보(없으면 1순위, 위치만이면 null) */
internal fun SavedPlace.chosen() = candidates.firstOrNull { it.poi.id == selectedPoiId }
    ?: if (status == SavedStatus.LOCATION_ONLY) null else candidates.firstOrNull()
