package com.akkageugot.demo.engine.core

import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.Side

/** 여러 가게가 들어 있는 건물. members는 데이터 순서(대개 1층부터) */
data class Building(val name: String, val members: List<Poi>)

/** 건물 안내 문구. 예) "오른쪽 볕들빌딩이에요. 볕드는카페, 스마일마트가 있어요." */
object BuildingText {
    fun speech(side: Side, b: Building): String {
        val sideWord = CategoryLabels.korean(side)
        val names = b.members.joinToString(", ") { it.name }
        val head = listOf(sideWord, b.name).filter { it.isNotEmpty() }.joinToString(" ")
        return "${head}${copula(b.name)}. ${names}${subjectParticle(b.members.last().name)} 있어요."
    }

    /** 예) "카페·편의점 2곳 · 오른쪽 · 150m 지나옴" */
    fun subtitle(side: Side, b: Building, passedM: Double?): String {
        val cats = b.members.map { CategoryLabels.korean(it.category) }.distinct().joinToString("·")
        return listOfNotNull(
            "$cats ${b.members.size}곳",
            CategoryLabels.korean(side).ifEmpty { null },
            passedM?.let { "${it.toInt()}m 지나옴" }
        ).joinToString(" · ")
    }

    /** 가게 한 곳 안내. 예) "왼쪽에 꽃과바람이 있어요." */
    fun nearbyOne(side: Side, name: String): String =
        "${placeWord(side)}에 ${name}${subjectParticle(name)} 있어요."

    /** 이름 없는 건물에 가게가 여럿. 예) "오른쪽 건물에 달빛제과가 있어요. 같은 건물에 솔빵집도 있어요." */
    fun nearbyPair(side: Side, first: String, second: String): String =
        "${placeWord(side)} 건물에 ${first}${subjectParticle(first)} 있어요. 같은 건물에 ${second}도 있어요."

    /** 이름만으로 답할 때. 예) "꽃과바람이요", "달빛제과요" */
    fun nameAnswer(name: String): String = name + if (hasBatchim(name)) "이요" else "요"

    private fun placeWord(side: Side): String = CategoryLabels.korean(side).ifEmpty { "근처" }

    fun copula(word: String): String = if (hasBatchim(word)) "이에요" else "예요"

    fun subjectParticle(word: String): String = if (hasBatchim(word)) "이" else "가"

    private fun hasBatchim(word: String): Boolean {
        val c = word.trim().lastOrNull() ?: return false
        if (c !in '가'..'힣') return false
        return (c.code - 0xAC00) % 28 != 0
    }
}
