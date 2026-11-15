package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.Side
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class IntentType { ASK, NEARBY, SAVE, VISIT, REJECT, MEMO }

@Serializable
data class ParsedIntent(
    val type: IntentType,
    val category: Category? = null,
    val side: Side = Side.UNKNOWN,
    val timeOffsetSec: Int? = null,
    /** "아까"만 있고 "N분 전"이 없을 때 true → 탐색 폭 2배 */
    val vaguePast: Boolean = false,
    /** ASK와 SAVE가 같이 왔을 때 ("방금 빵집 저장해줘") true → 바로 저장 */
    val askAndSave: Boolean = false,
    val rawText: String = ""
)

/**
 * 규칙 기반 발화 파서. 나중에 Pleos LLM SDK로 바꿀 수 있게
 * "문자열 → ParsedIntent" 입출력만 유지한다 (engine/PROMPT.md 6절).
 */
object IntentParser {

    private val json = Json { encodeDefaults = true }
    private val minutesAgoRegex = Regex("""(\d+)\s*분\s*전""")

    fun parse(text: String): ParsedIntent {
        val t = text.trim()

        val hasAsk = listOf("뭐야", "뭐였지", "뭐지").any { t.contains(it) }
        val hasSave = t.contains("저장")
        val hasVisit = listOf("들르", "들렀다", "가자", "거기로").any { t.contains(it) }
        val hasReject = listOf("아니", "그 옆", "다른 거").any { t.contains(it) }
        // "옆에 뭐 있어?", "오른쪽에 뭐있어" → 주변 가게 묻기
        val compact = t.replace(" ", "")
        val hasNearby = listOf("뭐있어", "뭐가있어", "뭐있지", "뭐있나").any { compact.contains(it) }

        val type = when {
            hasReject -> IntentType.REJECT
            hasVisit -> IntentType.VISIT
            hasSave -> IntentType.SAVE
            hasAsk -> IntentType.ASK
            hasNearby -> IntentType.NEARBY
            else -> IntentType.MEMO
        }

        val category = parseCategory(t)
        val side = parseSide(t)

        val minutes = minutesAgoRegex.find(t)?.groupValues?.get(1)?.toIntOrNull()
        val vaguePast = minutes == null && t.contains("아까")

        return ParsedIntent(
            type = type,
            category = category,
            side = side,
            timeOffsetSec = minutes?.let { it * 60 },
            vaguePast = vaguePast,
            askAndSave = hasAsk && hasSave || (hasSave && category != null),
            rawText = t
        )
    }

    /** "오른쪽/우측/오른편", "왼쪽/좌측/왼편". 좌우 되묻기의 대답도 이걸로 읽는다 */
    fun parseSide(t: String): Side = when {
        listOf("오른쪽", "우측", "오른편").any { t.contains(it) } -> Side.RIGHT
        listOf("왼쪽", "좌측", "왼편").any { t.contains(it) } -> Side.LEFT
        else -> Side.UNKNOWN
    }

    fun toJson(intent: ParsedIntent): String = json.encodeToString(intent)

    private fun parseCategory(t: String): Category? = when {
        listOf("빵집", "베이커리", "제과").any { t.contains(it) } -> Category.BAKERY
        listOf("카페", "커피").any { t.contains(it) } -> Category.CAFE
        listOf("식당", "밥집", "국수").any { t.contains(it) } -> Category.RESTAURANT
        t.contains("꽃집") -> Category.FLOWER
        t.contains("편의점") -> Category.CONVENIENCE
        else -> null
    }
}
