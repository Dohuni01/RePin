package com.akkageugot.demo.engine.probe

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import com.akkageugot.demo.contract.EngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 엔진 상태를 텍스트로 확인하는 개발용 화면. 0단계에서는 AppState 덤프만 한다.
 * 2단계에서 버튼(말하기, 프리셋 발화, 기어, 시나리오)을 붙인다.
 */
class EngineProbeActivity : Activity() {

    private lateinit var textView: TextView
    private var scope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView = TextView(this).apply {
            textSize = 12f
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(textView) })
    }

    override fun onStart() {
        super.onStart()
        val s = CoroutineScope(Dispatchers.Main + Job())
        scope = s
        s.launch {
            EngineProvider.engine.state.collect { state ->
                textView.text = state.toString()
            }
        }
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
        super.onStop()
    }
}
