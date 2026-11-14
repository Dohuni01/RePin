package com.akkageugot.demo.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.ui.navi.DayMapPalette
import com.akkageugot.demo.ui.navi.MapPalette
import com.akkageugot.demo.ui.navi.NightMapPalette
import com.akkageugot.demo.ui.navi.mapIconColor
import com.akkageugot.demo.ui.navi.toMapIcon

/** 화면 전체에서 쓰는 색. 기본은 야간, 주간은 설정에서 토글 */
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
    val userBubble: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    /** 데모 내비 지도 색 (MapScene.kt) */
    val map: MapPalette
)

val NightColors = AkkaColors(
    isDay = false,
    mapBg = Color(NightMapPalette.ground),
    mapBlock = Color(NightMapPalette.building),
    mapRoad = Color(NightMapPalette.road),
    route = Color(0xFF3D8BFF),
    routePassed = Color(0xFF5B6675),
    car = Color(0xFFFFFFFF),
    surface = Color(0xF2161C25),
    surfaceHigh = Color(0xFF222B38),
    outline = Color(0xFF3A4657),
    text = Color(0xFFF4F7FB),
    textDim = Color(0xFFA9B4C3),
    accent = Color(0xFF4CC2FF),
    onAccent = Color(0xFF04121C),
    userBubble = Color(0xFF39424F),
    ok = Color(0xFF52D689),
    warn = Color(0xFFFFB84D),
    danger = Color(0xFFFF6B6B),
    map = NightMapPalette
)

val DayColors = AkkaColors(
    isDay = true,
    mapBg = Color(DayMapPalette.ground),
    mapBlock = Color(DayMapPalette.building2),
    mapRoad = Color(DayMapPalette.road),
    route = Color(DayMapPalette.route),
    routePassed = Color(DayMapPalette.routePassed),
    car = Color(0xFF10151C),
    surface = Color(0xF7FFFFFF),
    surfaceHigh = Color(0xFFF0F3F7),
    outline = Color(0xFFBCC5D1),
    text = Color(0xFF10151C),
    textDim = Color(0xFF4D5968),
    accent = Color(0xFF0B63CE),
    onAccent = Color(0xFFFFFFFF),
    userBubble = Color(0xFFDDE2E9),
    ok = Color(0xFF12894A),
    warn = Color(0xFFB36A00),
    danger = Color(0xFFC62828),
    map = DayMapPalette
)

val LocalAkkaColors = staticCompositionLocalOf { NightColors }

@Composable
fun AkkaTheme(day: Boolean = false, content: @Composable () -> Unit) {
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
