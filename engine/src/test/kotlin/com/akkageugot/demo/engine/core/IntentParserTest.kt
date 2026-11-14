package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntentParserTest {

    @Test
    fun `프리셋 1 - 방금 오른쪽에 있던 빵집 뭐야`() {
        val i = IntentParser.parse("방금 오른쪽에 있던 빵집 뭐야?")
        assertEquals(IntentType.ASK, i.type)
        assertEquals(Category.BAKERY, i.category)
        assertEquals(Side.RIGHT, i.side)
        assertNull(i.timeOffsetSec)
        assertFalse(i.vaguePast)
    }

    @Test
    fun `프리셋 2 - 저장해`() {
        assertEquals(IntentType.SAVE, IntentParser.parse("저장해").type)
    }

    @Test
    fun `프리셋 3 - 들렀다 가자`() {
        assertEquals(IntentType.VISIT, IntentParser.parse("들렀다 가자").type)
    }

    @Test
    fun `프리셋 4 - 아니 그 옆에`() {
        assertEquals(IntentType.REJECT, IntentParser.parse("아니 그 옆에").type)
    }

    @Test
    fun `프리셋 5 - 아까 3분 전에 지나친 카페 뭐였지`() {
        val i = IntentParser.parse("아까 3분 전에 지나친 카페 뭐였지?")
        assertEquals(IntentType.ASK, i.type)
        assertEquals(Category.CAFE, i.category)
        assertEquals(180, i.timeOffsetSec)
        assertFalse(i.vaguePast) // N분이 있으면 모호하지 않다
    }

    @Test
    fun `프리셋 6 - 방금 거 뭐야`() {
        val i = IntentParser.parse("방금 거 뭐야?")
        assertEquals(IntentType.ASK, i.type)
        assertNull(i.category)
        assertEquals(Side.UNKNOWN, i.side)
    }

    @Test
    fun `아까만 있으면 vaguePast`() {
        val i = IntentParser.parse("아까 지나친 빵집 뭐였지?")
        assertTrue(i.vaguePast)
        assertNull(i.timeOffsetSec)
    }

    @Test
    fun `ASK와 SAVE가 같이 오면 바로 저장`() {
        val i = IntentParser.parse("방금 빵집 저장해줘")
        assertEquals(IntentType.SAVE, i.type)
        assertTrue(i.askAndSave)
        assertEquals(Category.BAKERY, i.category)
    }

    @Test
    fun `업종 동의어 매핑`() {
        assertEquals(Category.BAKERY, IntentParser.parse("베이커리 뭐야").category)
        assertEquals(Category.BAKERY, IntentParser.parse("그 제과점 뭐야").category)
        assertEquals(Category.CAFE, IntentParser.parse("커피집 뭐야").category)
        assertEquals(Category.RESTAURANT, IntentParser.parse("밥집 뭐야").category)
        assertEquals(Category.RESTAURANT, IntentParser.parse("국수가게 뭐야").category)
        assertEquals(Category.FLOWER, IntentParser.parse("꽃집 뭐야").category)
        assertEquals(Category.CONVENIENCE, IntentParser.parse("편의점 뭐야").category)
    }

    @Test
    fun `JSON 직렬화가 된다`() {
        val json = IntentParser.toJson(IntentParser.parse("방금 오른쪽 빵집 뭐야?"))
        assertTrue(json.contains("\"type\""))
        assertTrue(json.contains("ASK"))
        assertTrue(json.contains("BAKERY"))
    }

    @Test
    fun `분류 안 되는 말은 MEMO`() {
        assertEquals(IntentType.MEMO, IntentParser.parse("오늘 날씨 좋네").type)
    }
}
