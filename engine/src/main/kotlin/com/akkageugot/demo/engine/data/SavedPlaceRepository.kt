package com.akkageugot.demo.engine.data

import android.content.Context
import android.util.Log
import com.akkageugot.demo.contract.Candidate
import com.akkageugot.demo.contract.Category
import com.akkageugot.demo.contract.Poi
import com.akkageugot.demo.contract.SavedPlace
import com.akkageugot.demo.contract.SavedStatus
import com.akkageugot.demo.contract.ScoreDetail
import com.akkageugot.demo.contract.Side
import com.akkageugot.demo.contract.Vec2
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 저장 목록을 filesDir의 JSON으로 영속화한다 (engine/PROMPT.md 4절).
 * Contract의 data class는 동결·직렬화 어노테이션이 없으므로 내부 DTO로 매핑한다.
 */
class SavedPlaceRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun load(): List<SavedPlace> {
        return try {
            if (!file.exists()) return emptyList()
            json.decodeFromString<List<SavedPlaceDto>>(file.readText(Charsets.UTF_8)).map { it.toModel() }
        } catch (t: Throwable) {
            Log.w("AKKA", "저장 목록 로드 실패: ${t.message}")
            emptyList()
        }
    }

    fun save(places: List<SavedPlace>) {
        try {
            file.writeText(json.encodeToString(places.map { SavedPlaceDto.from(it) }), Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.w("AKKA", "저장 목록 쓰기 실패: ${t.message}")
        }
    }

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        const val FILE_NAME = "saved_places.json"
    }
}

@Serializable
private data class Vec2Dto(val x: Double, val y: Double) {
    fun toModel() = Vec2(x, y)
    companion object { fun from(v: Vec2) = Vec2Dto(v.x, v.y) }
}

@Serializable
private data class PoiDto(
    val id: String, val name: String, val category: String, val pos: Vec2Dto, val open: Boolean
) {
    fun toModel() = Poi(id, name, Category.valueOf(category), pos.toModel(), open)
    companion object {
        fun from(p: Poi) = PoiDto(p.id, p.name, p.category.name, Vec2Dto.from(p.pos), p.open)
    }
}

@Serializable
private data class ScoreDetailDto(
    val distance: Double, val side: Double, val category: Double, val time: Double
) {
    fun toModel() = ScoreDetail(distance, side, category, time)
    companion object {
        fun from(d: ScoreDetail) = ScoreDetailDto(d.distance, d.side, d.category, d.time)
    }
}

@Serializable
private data class CandidateDto(
    val poi: PoiDto, val score: Double, val side: String, val distanceM: Double, val detail: ScoreDetailDto
) {
    fun toModel() = Candidate(poi.toModel(), score, Side.valueOf(side), distanceM, detail.toModel())
    companion object {
        fun from(c: Candidate) =
            CandidateDto(PoiDto.from(c.poi), c.score, c.side.name, c.distanceM, ScoreDetailDto.from(c.detail))
    }
}

@Serializable
private data class SavedPlaceDto(
    val id: String,
    val createdAtMs: Long,
    val label: String,
    val pos: Vec2Dto,
    val utterance: String,
    val candidates: List<CandidateDto>,
    val selectedPoiId: String?,
    val status: String,
    val memo: String
) {
    fun toModel() = SavedPlace(
        id = id, createdAtMs = createdAtMs, label = label, pos = pos.toModel(),
        utterance = utterance, candidates = candidates.map { it.toModel() },
        selectedPoiId = selectedPoiId, status = SavedStatus.valueOf(status), memo = memo
    )
    companion object {
        fun from(p: SavedPlace) = SavedPlaceDto(
            id = p.id, createdAtMs = p.createdAtMs, label = p.label, pos = Vec2Dto.from(p.pos),
            utterance = p.utterance, candidates = p.candidates.map { CandidateDto.from(it) },
            selectedPoiId = p.selectedPoiId, status = p.status.name, memo = p.memo
        )
    }
}
