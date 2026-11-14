package com.akkageugot.demo.engine.platform

import com.akkageugot.demo.contract.Gear

/**
 * 기어와 속도 신호 (engine/PROMPT.md 4절).
 * 실구현: AAOS CarPropertyManager + Vehicle SDK Brake / 목업: 시뮬레이터 값.
 * 2단계에서는 시뮬레이터 목업만 쓴다. Pleos/AAOS 구현은 이후 단계.
 */
interface VehicleSignals {
    val gear: Gear
    val speedKmh: Double
    /** 어떤 소스인지 debug.sdkStatus에 표시 */
    val sourceLabel: String
    fun start() {}
    fun stop() {}
}

/** 시뮬레이터 값을 그대로 돌려주는 목업 */
class MockVehicleSignals(
    private val gearProvider: () -> Gear,
    private val speedProvider: () -> Double
) : VehicleSignals {
    override val gear: Gear get() = gearProvider()
    override val speedKmh: Double get() = speedProvider()
    override val sourceLabel: String = "목업(시뮬레이터)"
}
