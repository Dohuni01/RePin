package com.akkageugot.demo.engine.platform

/**
 * 주행 중 제한 상태 (engine/PROMPT.md 4절).
 * 실구현: AAOS CarUxRestrictionsManager / 목업: gear==D && speed>0.
 */
interface UxRestrictions {
    val restricted: Boolean
    val sourceLabel: String
    fun start() {}
    fun stop() {}
}

/** gear==D && speed>0 로 제한을 흉내내는 목업 */
class MockUxRestrictions(
    private val restrictedProvider: () -> Boolean
) : UxRestrictions {
    override val restricted: Boolean get() = restrictedProvider()
    override val sourceLabel: String = "목업(gear==D && speed>0)"
}
