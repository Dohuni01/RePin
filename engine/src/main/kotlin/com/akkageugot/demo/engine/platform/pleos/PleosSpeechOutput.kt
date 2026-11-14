package com.akkageugot.demo.engine.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.akkageugot.demo.engine.platform.AndroidSpeechOutput
import com.akkageugot.demo.engine.platform.SpeechOutput
import java.util.Locale

/**
 * Pleos TextToSpeech 기반 음성 출력 (docs/PLEOS_SDK_NOTES.md).
 * 온디바이스 모드로 초기화하고, 실패하면 Android 표준 TTS로 자동 대체한다
 * (engine/PROMPT.md 7절: 실패 시 Android TextToSpeech).
 */
class PleosSpeechOutput(private val context: Context) : SpeechOutput {

    private var tts: TextToSpeech? = null
    private val fallback = AndroidSpeechOutput(context)
    @Volatile private var ready = false
    @Volatile private var failed = false
    @Volatile private var runtimeFailed = false
    private var fallbackStarted = false

    override val sourceLabel: String
        get() = when {
            ready && !runtimeFailed -> "Pleos TTS"
            runtimeFailed -> "목업(Android TTS, Pleos 런타임 오류)"
            failed -> "목업(Android TTS, Pleos 실패)"
            else -> "Pleos TTS (초기화 중)"
        }

    private val eventListener = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() {}
        override fun onError(error: ErrorType) {
            // 온디바이스 합성 엔진 오류 등 런타임 실패 시 Android TTS로 강등
            Log.w("AKKA", "Pleos TTS onError: $error → Android TTS 대체")
            runtimeFailed = true
            ensureFallback()
        }
        override fun onStop() {}
        override fun onUpdatedRms(rms: Double) {}
    }

    override fun start() {
        try {
            val engine = TextToSpeech(context, Mode.ON_DEVICE)
            tts = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    // 서비스 바인딩 후 설정 (초기화 전 호출 시 SDK 내부 lateinit 오류)
                    runCatching {
                        engine.setLocale(Locale.KOREAN)
                        engine.addEventListener(eventListener)
                        ready = true
                        Log.i("AKKA", "Pleos TTS 초기화 성공")
                    }.onFailure {
                        failed = true
                        ensureFallback()
                        Log.w("AKKA", "Pleos TTS 설정 실패 → Android TTS 대체: ${it.message}")
                    }
                }
                override fun onFailure(error: ErrorType) {
                    failed = true
                    ensureFallback()
                    Log.w("AKKA", "Pleos TTS 초기화 실패: $error → Android TTS 대체")
                }
            })
        } catch (t: Throwable) {
            failed = true
            ensureFallback()
            Log.w("AKKA", "Pleos TTS 사용 불가 → Android TTS 대체: ${t.message}")
        }
    }

    override fun speak(text: String) {
        if (ready && !runtimeFailed) {
            runCatching { tts?.speak(text, false) }
                .onFailure {
                    Log.w("AKKA", "Pleos TTS speak 실패 → Android TTS 대체: ${it.message}")
                    runtimeFailed = true
                    ensureFallback()
                    fallback.speak(text)
                }
        } else {
            ensureFallback()
            fallback.speak(text)
        }
    }

    override fun stop() {
        runCatching { tts?.removeEventListener(eventListener); tts?.release() }
        tts = null
        if (fallbackStarted) fallback.stop()
    }

    private fun ensureFallback() {
        if (!fallbackStarted) {
            fallback.start()
            fallbackStarted = true
        }
    }
}
