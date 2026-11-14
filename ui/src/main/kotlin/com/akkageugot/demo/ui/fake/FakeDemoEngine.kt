package com.akkageugot.demo.ui.fake

import android.content.Context
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.DemoEngine
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.contract.emptyAppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 화면 개발용 가짜 엔진. 0단계에서는 emptyAppState()만 내보내는 스텁이다.
 * 이후 구현은 B 담당 — A는 이 파일을 더 이상 고치지 않는다.
 * 생성자 시그니처 (Context)는 바꾸지 않는다 (docs/CONTRACT.md).
 */
class FakeDemoEngine(private val context: Context) : DemoEngine {

    private val _state = MutableStateFlow(emptyAppState())
    override val state: StateFlow<AppState> = _state.asStateFlow()

    override fun dispatch(action: UiAction) {
        // 0단계 스텁
    }

    override fun start() {
        // 0단계 스텁
    }

    override fun stop() {
        // 0단계 스텁
    }
}
