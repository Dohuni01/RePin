package com.akkageugot.demo.engine.platform

/**
 * 음성 인식 입력 (engine/PROMPT.md 4절).
 * 실구현: Pleos SpeechToText / 목업: TypedUtterance·adb 입력.
 * 인식 결과는 setOnResult 콜백으로 전달한다.
 */
interface SpeechInput {
    val sourceLabel: String
    fun setOnResult(callback: (String) -> Unit)
    /** MicPressed 시 호출. 듣기 시작 */
    fun start()
    /** MicReleased 시 호출. 듣기 종료 */
    fun stop()
    /** TypedUtterance/adb 경로로 들어온 발화. 목업은 이를 인식 결과로 바로 돌려준다 */
    fun feedUtterance(text: String)
}

/**
 * 에뮬레이터용 목업. 실제 마이크 대신 TypedUtterance/adb로 들어온 텍스트를
 * 그대로 인식 결과로 넘긴다.
 */
class MockSpeechInput : SpeechInput {
    private var callback: ((String) -> Unit)? = null
    override val sourceLabel: String = "목업(TypedUtterance/adb)"
    override fun setOnResult(callback: (String) -> Unit) { this.callback = callback }
    override fun start() {}
    override fun stop() {}
    override fun feedUtterance(text: String) { callback?.invoke(text) }
}
