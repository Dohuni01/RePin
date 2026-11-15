package com.akkageugot.demo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.akkageugot.demo.contract.EngineProvider
import com.akkageugot.demo.ui.common.AkkaTheme
import com.akkageugot.demo.ui.dev.UiPrefs

/**
 * 데모 화면 Activity (landscape, distractionOptimized — AndroidManifest 참고).
 * EngineProvider.engine.state를 그리고 dispatch(UiAction)만 보낸다.
 * 엔진 start/stop은 :app의 Application이 맡는다.
 */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = UiPrefs(applicationContext)
        setContent {
            val engine = remember { EngineProvider.engine }
            val state by engine.state.collectAsState()
            var splashDone by remember { mutableStateOf(false) }
            AkkaTheme(day = prefs.flags.dayTheme) {
                if (!splashDone) {
                    SplashScreen(onFinished = { splashDone = true })
                } else {
                    DemoScreen(
                        state = state,
                        flags = prefs.flags,
                        onFlags = prefs::update,
                        dispatch = engine::dispatch
                    )
                }
            }
        }
    }
}
