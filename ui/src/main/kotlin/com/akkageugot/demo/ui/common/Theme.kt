package com.akkageugot.demo.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.ui.navi.DayMapPalette
import com.akkageugot.demo.ui.navi.MapPalette
import com.akkageugot.demo.ui.navi.NightMapPalette
import com.akkageugot.demo.ui.navi.mapIconColor
import com.akkageugot.demo.ui.navi.toMapIcon

/**
 * 화면 전체에서 쓰는 색. RePin "백미러" 디자인 (2026-11-15).
 * 기본은 노을(주간) 테마, 밤 테마는 설정에서 토글.
 * - accent: 산호색 (주 버튼, 말하기), accent2: 보라 (경로, 아이콘)
 * - sky*: 노을 하늘 그라데이션 (정차 화면 배경, 거울 속 장면)
 * - chrome*: 백미러 테두리 금속색
 */
@Immutable
data class AkkaColors(
    val isDay: Boolean,
    val mapBg: Color,
    val mapBlock: Color,
    val mapRoad: Color,
    val route: Color,
    val routePassed: Color,
    val car: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val onAccent: Color,
    val accentDeep: Color,
    val accent2: Color,
    val userBubble: Color,
    val appBubble: Color,
    val onAppBubble: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    val skyBottom: Color,
    val onSky: Color,
    val chromeLight: Color,
    val chromeDark: Color,
    /** 데모 내비 지도 색 (MapScene.kt) */
    val map: MapPalette
) {
    /** 노을 하늘 (위 → 아래) */
    val skyBrush: Brush get() = Brush.verticalGradient(listOf(skyTop, skyMid, skyLow, skyBottom))

    /** 백미러 테두리 (빛 받은 금속) */
    val chromeBrush: Brush
        get() = Brush.linearGradient(
            0f to chromeLight, 0.4f to Color(0xFFC9CFDA), 0.62f to chromeDark, 1f to Color(0xFFE6E9EF)
        )

    /** 말하기 버튼 등 산호색 그라데이션 */
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accent, accentDeep))
}

val NightColors = AkkaColors(
    isDay = false,
    mapBg = Color(NightMapPalette.ground),
    mapBlock = Color(NightMapPalette.building),
    mapRoad = Color(NightMapPalette.road),
    route = Color(NightMapPalette.route),
    routePassed = Color(NightMapPalette.routePassed),
    car = Color(0xFFFFFFFF),
    surface = Color(0xEB1F1C38),
    surfaceHigh = Color(0xFF2A2648),
    outline = Color(0xFF3D3760),
    text = Color(0xFFF4F0FB),
    textDim = Color(0xFFB3AACB),
    accent = Color(0xFFFF7A6B),
    onAccent = Color(0xFFFFFFFF),
    accentDeep = Color(0xFFE2555E),
    accent2 = Color(0xFFB49BFF),
    userBubble = Color(0xEB2A2648),
    appBubble = Color(0xFF4A3D86),
    onAppBubble = Color(0xFFFFFFFF),
    ok = Color(0xFF52D689),
    warn = Color(0xFFFFB84D),
    danger = Color(0xFFFF6B6B),
    skyTop = Color(0xFF141230),
    skyMid = Color(0xFF2F2860),
    skyLow = Color(0xFF5A3F7E),
    skyBottom = Color(0xFF8A5A82),
    onSky = Color(0xFFFFFFFF),
    chromeLight = Color(0xFFE9ECF2),
    chromeDark = Color(0xFF6E7688),
    map = NightMapPalette
)

val DayColors = AkkaColors(
    isDay = true,
    mapBg = Color(DayMapPalette.ground),
    mapBlock = Color(DayMapPalette.building2),
    mapRoad = Color(DayMapPalette.road),
    route = Color(DayMapPalette.route),
    routePassed = Color(DayMapPalette.routePassed),
    car = Color(0xFF2B2D5C),
    surface = Color(0xEBFFFFFF),
    surfaceHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFE5D8E8),
    text = Color(0xFF1E2140),
    textDim = Color(0xFF5D5E80),
    accent = Color(0xFFFF6F61),
    onAccent = Color(0xFFFFFFFF),
    accentDeep = Color(0xFFE2555E),
    accent2 = Color(0xFF7A5FA8),
    userBubble = Color(0xEBFFFFFF),
    appBubble = Color(0xFF2B2D5C),
    onAppBubble = Color(0xFFFFFFFF),
    ok = Color(0xFF15803D),
    warn = Color(0xFFB45309),
    danger = Color(0xFFD92D20),
    skyTop = Color(0xFF2B2D5C),
    skyMid = Color(0xFF7A5FA8),
    skyLow = Color(0xFFF09A9A),
    skyBottom = Color(0xFFFFC9A3),
    onSky = Color(0xFFFFFFFF),
    chromeLight = Color(0xFFFFFFFF),
    chromeDark = Color(0xFF8E97A8),
    map = DayMapPalette
)

val LocalAkkaColors = staticCompositionLocalOf { NightColors }

@Composable
fun AkkaTheme(day: Boolean = true, content: @Composable () -> Unit) {
    val c = if (day) DayColors else NightColors
    val scheme = if (day) {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.mapBg, onBackground = c.text,
            surface = c.surfaceHigh, onSurface = c.text,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textDim,
            outline = c.outline, error = c.danger
        )
    } else {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.mapBg, onBackground = c.text,
            surface = c.surfaceHigh, onSurface = c.text,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textDim,
            outline = c.outline, error = c.danger
        )
    }
    CompositionLocalProvider(LocalAkkaColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

fun Category.labelKo(): String = when (this) {
    Category.BAKERY -> "빵집"
    Category.CAFE -> "카페"
    Category.RESTAURANT -> "식당"
    Category.FLOWER -> "꽃집"
    Category.CONVENIENCE -> "편의점"
    Category.ETC -> "기타"
}

/** 업종별 색 (지도 아이콘 배지, 카드, 창밖 간판). 지도와 같은 색을 쓰도록 MapScene.kt의 mapIconColor를 따른다 */
fun Category?.badgeColor(): Color = Color(mapIconColor(this.toMapIcon()))
