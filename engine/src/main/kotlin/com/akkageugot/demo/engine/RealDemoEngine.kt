package com.akkageugot.demo.engine

import android.content.Context
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.DemoEngine
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.contract.emptyAppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 진짜 엔진. 0단계에서는 emptyAppState()만 내보내는 스텁이다.
 * 생성자 시그니처 (Context)는 바꾸지 않는다 (docs/CONTRACT.md).
 */
class RealDemoEngine(private val context: Context) : DemoEngine {

    private val _state = MutableStateFlow(emptyAppState())
    override val state: StateFlow<AppState> = _state.asStateFlow()

    override fun dispatch(action: UiAction) {
        // 0단계 스텁: 아직 아무것도 하지 않는다
    }

    override fun start() {
        // 0단계 스텁
    }

    override fun stop() {
        // 0단계 스텁
    }
}
