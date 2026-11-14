package com.akkageugot.demo.ui.parked

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.akkageugot.demo.contract.Candidate
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.SavedStatus
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.ui.common.AkkaColors
import com.akkageugot.demo.ui.common.AkkaIcon
import com.akkageugot.demo.ui.common.IconKind
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.common.badgeColor
import com.akkageugot.demo.ui.common.iconKind
import com.akkageugot.demo.ui.common.labelKo

/** 정차 화면 오른쪽 절반: 오늘 저장한 곳 목록과 후보 확인 */
@Composable
fun ParkedPanel(
    saved: List<SavedPlace>,
    dispatch: (UiAction) -> Unit,
    onShowQr: (SavedPlace) -> Unit,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis()
) {
    val c = LocalAkkaColors.current
    // 오늘 저장한 것만 (최근에 저장한 곳이 위로)
    val today = remember(saved, nowMs / 60_000L) {
        val start = startOfDay(nowMs)
        saved.filter { it.createdAtMs >= start }.asReversed()
    }
    Column(modifier.fillMaxSize().background(c.mapBg).padding(horizontal = 20.dp)) {
        Text(
            "오늘 저장한 곳 ${today.size}개",
            color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
        )
        if (today.isEmpty()) {
            Text(
                "아직 저장한 곳이 없어요.\n주행 중에 \"방금 거기 뭐야?\"라고 물어보세요.",
                color = c.textDim, fontSize = 20.sp, modifier = Modifier.padding(top = 24.dp)
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(today, key = { it.id }) { place ->
                SavedItem(place, dispatch, onShowQr)
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

private fun startOfDay(ms: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = ms
    set(java.util.Calendar.HOUR_OF_DAY, 0)
    set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun SavedItem(place: SavedPlace, dispatch: (UiAction) -> Unit, onShowQr: (SavedPlace) -> Unit) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceHigh)
            .border(1.dp, c.outline, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                place.label, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            StatusBadge(place.status)
        }
        if (place.utterance.isNotBlank()) {
            Text(
                "\"${place.utterance}\"", color = c.textDim, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            place.candidates.take(3).forEachIndexed { i, cand ->
                CandidateCard(
                    cand = cand,
                    first = i == 0,
                    selected = place.selectedPoiId == cand.poi.id,
                    onClick = { dispatch(UiAction.SelectCandidate(place.id, cand.poi.id)) }
                )
            }
            LocationOnlyCard(
                place = place,
                selected = place.status == SavedStatus.LOCATION_ONLY,
                onSelect = { dispatch(UiAction.SelectCandidate(place.id, null)) },
                onMemo = { dispatch(UiAction.UpdateMemo(place.id, it)) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelButton("안내", IconKind.FLAG, primary = true, modifier = Modifier.weight(1f)) {
                dispatch(UiAction.NavigateTo(place.id))
            }
            PanelButton("폰으로", IconKind.PHONE, primary = false, modifier = Modifier.weight(1f)) {
                onShowQr(place)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SavedStatus) {
    val c = LocalAkkaColors.current
    val (text, color) = when (status) {
        SavedStatus.CONFIRMED -> "확인됨" to c.ok
        SavedStatus.PENDING -> "확인 필요" to c.warn
        SavedStatus.LOCATION_ONLY -> "위치만" to c.textDim
    }
    Text(
        text, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(1.5.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

private fun matchText(score: Double): String = when {
    score >= 0.75 -> "높음"
    score >= 0.5 -> "보통"
    else -> "낮음"
}

private fun matchColor(score: Double, c: AkkaColors): Color = when {
    score >= 0.75 -> c.ok
    score >= 0.5 -> c.warn
    else -> c.textDim
}

private val CARD_WIDTH = 176.dp
private val CARD_HEIGHT = 148.dp

@Composable
private fun CandidateCard(cand: Candidate, first: Boolean, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.18f) else c.surface)
            // 1순위는 강조 테두리
            .border(if (first) 3.dp else 1.dp, if (first || selected) c.accent else c.outline, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(cand.poi.category.badgeColor()),
                contentAlignment = Alignment.Center
            ) {
                AkkaIcon(cand.poi.category.iconKind(), Color.White, Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            if (selected) AkkaIcon(IconKind.CHECK, c.accent, Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            cand.poi.name, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            "${cand.poi.category.labelKo()} · ${if (cand.poi.open) "영업 중" else "영업 종료"}",
            color = c.textDim, fontSize = 16.sp, maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Text(
            "일치도 ${matchText(cand.score)}", color = matchColor(cand.score, c),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1
        )
    }
}

/** 마지막 카드: "이 근처였어요 (위치만)" + 메모 */
@Composable
private fun LocationOnlyCard(place: SavedPlace, selected: Boolean, onSelect: () -> Unit, onMemo: (String) -> Unit) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(16.dp)
    // 입력 중 글자는 로컬에 두고(한글 조합 보호) 바뀔 때마다 엔진에 알린다
    var memo by remember(place.id) { mutableStateOf(place.memo) }
    Column(
        Modifier
            .width(CARD_WIDTH + 30.dp)
            .height(CARD_HEIGHT)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.18f) else c.surface)
            .border(1.dp, if (selected) c.accent else c.outline, shape)
            .clickable(onClick = onSelect)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background((null as Category?).badgeColor()),
                contentAlignment = Alignment.Center
            ) {
                AkkaIcon(IconKind.PIN, Color.White, Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            if (selected) AkkaIcon(IconKind.CHECK, c.accent, Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("이 근처였어요", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("위치만", color = c.textDim, fontSize = 16.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        BasicTextField(
            value = memo,
            onValueChange = {
                memo = it
                onMemo(it)
            },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceHigh)
                .border(1.dp, c.outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (memo.isEmpty()) Text("메모 입력", color = c.textDim, fontSize = 16.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
internal fun PanelButton(
    text: String,
    icon: IconKind?,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(16.dp)
    val fg = if (primary) c.onAccent else c.text
    Row(
        modifier
            .height(60.dp)
            .clip(shape)
            .background(if (primary) c.accent else c.surface)
            .border(1.dp, if (primary) c.accent else c.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            AkkaIcon(icon, fg, Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ---------------------------------------------------------------- 가짜 QR

/** [폰으로] 다이얼로그. 진짜 QR이 아니라 그럴듯한 격자 무늬다 */
@Composable
fun QrDialog(place: SavedPlace, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        QrDialogContent(place, onDismiss)
    }
}

@Composable
fun QrDialogContent(place: SavedPlace, onDismiss: () -> Unit) {
    val c = LocalAkkaColors.current
    val shape = RoundedCornerShape(26.dp)
    val name = place.candidates.firstOrNull { it.poi.id == place.selectedPoiId }?.poi?.name ?: "저장한 위치"
    Column(
        Modifier
            .clip(shape)
            .background(c.surfaceHigh)
            .border(1.dp, c.outline, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("폰으로 보내기", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("$name · ${place.label}", color = c.textDim, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        FakeQr(seed = place.id.hashCode(), modifier = Modifier.size(240.dp))
        Spacer(Modifier.height(10.dp))
        Text("폰 카메라로 비추면 지도 앱에서 열려요", color = c.textDim, fontSize = 17.sp)
        Spacer(Modifier.height(16.dp))
        PanelButton("닫기", null, primary = true, modifier = Modifier.width(240.dp), onClick = onDismiss)
    }
}

@Composable
private fun FakeQr(seed: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp)) {
        val n = 25
        val cell = size.minDimension / n
        val rnd = java.util.Random(seed.toLong())
        fun inFinder(x: Int, y: Int) = (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8)
        for (y in 0 until n) for (x in 0 until n) {
            val on = rnd.nextInt(100) < 48
            if (on && !inFinder(x, y)) {
                drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
        fun finder(gx: Int, gy: Int) {
            drawRect(Color.Black, Offset(gx * cell, gy * cell), Size(7 * cell, 7 * cell))
            drawRect(Color.White, Offset((gx + 1) * cell, (gy + 1) * cell), Size(5 * cell, 5 * cell))
            drawRect(Color.Black, Offset((gx + 2) * cell, (gy + 2) * cell), Size(3 * cell, 3 * cell))
        }
        finder(0, 0); finder(n - 7, 0); finder(0, n - 7)
    }
}
