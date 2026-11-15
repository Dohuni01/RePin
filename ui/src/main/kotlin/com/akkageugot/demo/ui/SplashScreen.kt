package com.akkageugot.demo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 스플래시: ex.png(거의 정사각형)를 가로 화면에 꽉 채우기 위해
 * 뒤에는 같은 이미지를 늘려 블러로 깔고, 앞에는 원본을 비율 그대로 중앙에 띄운다.
 * 양옆이 같은 색감으로 이어져 한 장의 풀스크린 이미지처럼 보인다.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var targetAlpha by remember { mutableStateOf(0f) }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 500),
        label = "splash_alpha"
    )
    LaunchedEffect(Unit) {
        targetAlpha = 1f
        delay(3000)
        targetAlpha = 0f
        delay(500)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.splash_bg),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().blur(60.dp)
        )
        Image(
            painter = painterResource(R.drawable.splash_bg),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxHeight().aspectRatio(851f / 818f)
        )
    }
}
