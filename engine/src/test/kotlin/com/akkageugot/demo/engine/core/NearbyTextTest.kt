package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** "옆에 뭐 있어?" 답 문구: 띄어쓰기와 받침에 맞는 조사 */
class NearbyTextTest {

    @Test
    fun `가게 한 곳 - 받침 있는 이름은 이`() {
        assertEquals("왼쪽에 꽃과바람이 있어요.", BuildingText.nearbyOne(Side.LEFT, "꽃과바람"))
    }

    @Test
    fun `가게 한 곳 - 받침 없는 이름은 가`() {
        assertEquals("오른쪽에 달빛제과가 있어요.", BuildingText.nearbyOne(Side.RIGHT, "달빛제과"))
    }

    @Test
    fun `가게 한 곳 - 좌우를 모르면 근처`() {
        assertEquals("근처에 솔빵집이 있어요.", BuildingText.nearbyOne(Side.UNKNOWN, "솔빵집"))
    }

    @Test
    fun `이름 없는 건물에 두 곳`() {
        assertEquals(
            "오른쪽 건물에 꽃과바람이 있어요. 같은 건물에 달빛제과도 있어요.",
            BuildingText.nearbyPair(Side.RIGHT, "꽃과바람", "달빛제과")
        )
        assertEquals(
            "왼쪽 건물에 달빛제과가 있어요. 같은 건물에 꽃과바람도 있어요.",
            BuildingText.nearbyPair(Side.LEFT, "달빛제과", "꽃과바람")
        )
    }

    @Test
    fun `이름으로 답할 때 이요 요`() {
        assertEquals("달빛제과요", BuildingText.nameAnswer("달빛제과"))
        assertEquals("꽃과바람이요", BuildingText.nameAnswer("꽃과바람"))
    }
}
