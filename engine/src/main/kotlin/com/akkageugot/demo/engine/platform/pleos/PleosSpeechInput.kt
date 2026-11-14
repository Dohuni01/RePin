package com.akkageugot.demo.engine.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import com.akkageugot.demo.engine.platform.SpeechInput
import java.util.Locale

/**
 * Pleos SpeechToText 기반 음성 입력 (docs/PLEOS_SDK_NOTES.md).
 * 온디바이스 모드로 초기화하고, 실패하면 TypedUtterance/adb 경로로만 동작하는
 * 목업 수준으로 자동 강등한다 (engine/CLAUDE.md: 실패 시 자동 대체).
 *
 * 에뮬레이터에서는 adb say(=TypedUtterance)가 항상 인식 결과로 전달되므로
 * SDK 가용 여부와 무관하게 발표 시나리오가 끝까지 돈다.
 */
class PleosSpeechInput(private val context: Context) : SpeechInput {

    private var stt: SpeechToText? = null
    private var callback: ((String) -> Unit)? = null
    @Volatile private var ready = false
    @Volatile private var failed = false

    override val sourceLabel: String
        get() = when {
            ready -> "Pleos STT"
            failed -> "목업(Pleos STT 초기화 실패)"
            else -> "Pleos STT (초기화 중)"
        }

    private val resultListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (isFinal && text.isNotBlank()) callback?.invoke(text)
        }
        override fun onUpdatedEpdData(startMs: Long, endMs: Long) {}
        override fun onStartedRecognition() {}
        override fun onEndedRecognition() {}
        override fun onError(error: ErrorType) {
            Log.w("AKKA", "Pleos STT onError: $error")
        }
        override fun onReady() {}
        override fun onUpdatedRms(rms: Double) {}
    }

    fun initialize() {
        try {
            val engine = SpeechToText(context, Mode.ON_DEVICE)
            stt = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    // 서비스가 바인딩된 뒤에 설정한다 (초기화 전 호출 시 SDK 내부 lateinit 오류)
                    runCatching {
                        engine.setLocale(Locale.KOREAN)
                        engine.addListener(resultListener)
                        ready = true
                        Log.i("AKKA", "Pleos STT 초기화 성공")
                    }.onFailure {
                        failed = true
                        Log.w("AKKA", "Pleos STT 설정 실패 → 목업 대체: ${it.message}")
                    }
                }
                override fun onFailure(error: ErrorType) {
                    failed = true
                    Log.w("AKKA", "Pleos STT 초기화 실패: $error → 목업 대체")
                }
            })
        } catch (t: Throwable) {
            failed = true
            Log.w("AKKA", "Pleos STT 사용 불가 → 목업 대체: ${t.message}")
        }
    }

    override fun setOnResult(callback: (String) -> Unit) { this.callback = callback }

    override fun start() {
        if (ready) runCatching { stt?.request() }
            .onFailure { Log.w("AKKA", "Pleos STT request 실패: ${it.message}") }
    }

    override fun stop() {
        if (ready) runCatching { stt?.stop() }
    }

    /** adb/TypedUtterance 경로는 SDK 상태와 무관하게 항상 인식 결과로 전달 */
    override fun feedUtterance(text: String) { callback?.invoke(text) }

    fun release() {
        runCatching {
            stt?.removeListener(resultListener)
            stt?.release()
        }
        stt = null
    }
}
