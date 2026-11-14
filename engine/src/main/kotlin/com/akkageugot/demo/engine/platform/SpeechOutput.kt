package com.akkageugot.demo.engine.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 음성 합성 출력 (engine/PROMPT.md 4절).
 * 실구현: Pleos TextToSpeech / 목업: Android TextToSpeech.
 */
interface SpeechOutput {
    val sourceLabel: String
    fun start()
    fun speak(text: String)
    fun stop()
}

/**
 * Android 표준 TextToSpeech 목업. Pleos TTS 초기화에 실패하면 이걸로 대체한다.
 */
class AndroidSpeechOutput(private val context: Context) : SpeechOutput {

    private var tts: TextToSpeech? = null
    private var ready = false

    override val sourceLabel: String = "목업(Android TTS)"

    override fun start() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                ready = true
            } else {
                Log.w("AKKA", "Android TTS 초기화 실패: $status")
            }
        }
    }

    override fun speak(text: String) {
        if (!ready) {
            Log.i("AKKA", "TTS(미준비): $text")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    override fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
