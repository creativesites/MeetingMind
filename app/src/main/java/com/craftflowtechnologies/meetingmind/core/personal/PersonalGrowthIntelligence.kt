package com.craftflowtechnologies.meetingmind.core.personal

import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NotebookKind
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Pure Phase 2 Personal intelligence helpers:
 * - Life Chapter domain projection over `NotebookEntity(kind = LIFE_CHAPTER)` (zero duplicate tables)
 * - Idea Collision Engine (finds semantically related Spark Vault notes using token/TF cosine similarity
 *   with zero network dependency and instant offline execution)
 * - Playbook-to-Challenge extractor (turns any `MOTIVATION` or `LIFE_SKILLS` YouTube/Audio note into
 *   a 7-day focused Challenge grounded in the note's actual takeaways)
 * - Custom Challenge & Journey config serializer
 */

data class LifeChapter(
    val id: String,
    val title: String,
    val subtitle: String,
    val themeColorHex: String?,
    val isActive: Boolean,
    val noteCount: Int = 0,
    val startedAt: Long = 0L
) {
    companion object {
        fun fromNotebook(entity: NotebookEntity, noteCount: Int = 0): LifeChapter {
            val props = runCatching { JSONObject(entity.propertiesJson) }.getOrNull()
            val subtitle = props?.optString("subtitle").orEmpty()
            val active = props?.optBoolean("active", true) ?: true
            return LifeChapter(
                id = entity.id,
                title = entity.name,
                subtitle = subtitle,
                themeColorHex = entity.colorHex,
                isActive = active,
                noteCount = noteCount,
                startedAt = entity.createdAt
            )
        }

        fun toNotebookEntity(
            id: String,
            title: String,
            subtitle: String,
            nowMs: Long,
            sortOrder: Int = 0
        ): NotebookEntity {
            val props = JSONObject().apply {
                put("subtitle", subtitle.trim())
                put("active", true)
            }.toString()
            return NotebookEntity(
                id = id,
                name = title.trim().ifBlank { "My Season" },
                space = NotebookSpace.PERSONAL.name,
                colorHex = null,
                icon = "chapter",
                createdAt = nowMs,
                updatedAt = nowMs,
                archivedAt = null,
                sortOrder = sortOrder,
                kind = NotebookKind.LIFE_CHAPTER,
                propertiesJson = props
            )
        }
    }
}

/** A pair of Spark Vault ideas that share meaningful thematic overlap ("Idea Collision"). */
data class IdeaCollision(
    val first: Note,
    val second: Note,
    val sharedThemes: List<String>,
    val score: Float
)

/** Custom challenge configuration stored in [GrowthEnrollment.customConfigJson]. */
data class CustomChallengeConfig(
    val dailyAction: String,
    val minEffortAction: String,
    val reflectionPrompt: String,
    val sourceNoteId: String? = null,
    val sourceNoteTitle: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("dailyAction", dailyAction)
        put("minEffortAction", minEffortAction)
        put("reflectionPrompt", reflectionPrompt)
        sourceNoteId?.let { put("sourceNoteId", it) }
        sourceNoteTitle?.let { put("sourceNoteTitle", it) }
    }.toString()

    companion object {
        fun fromJson(raw: String?): CustomChallengeConfig? {
            if (raw.isNullOrBlank() || raw == "{}") return null
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val daily = o.optString("dailyAction").ifBlank { return null }
            return CustomChallengeConfig(
                dailyAction = daily,
                minEffortAction = o.optString("minEffortAction").ifBlank { "Take 2 minutes on a small version of this" },
                reflectionPrompt = o.optString("reflectionPrompt").ifBlank { "What did you notice today?" },
                sourceNoteId = o.optString("sourceNoteId").ifBlank { null },
                sourceNoteTitle = o.optString("sourceNoteTitle").ifBlank { null }
            )
        }
    }
}

object PersonalGrowthIntelligence {

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "that", "this", "from", "have", "about", "into", "your",
        "what", "when", "where", "would", "could", "should", "there", "their", "they", "them",
        "been", "will", "just", "more", "some", "make", "like", "time", "very", "want", "need",
        "also", "idea", "note", "spark", "today", "really", "think", "thing", "things", "something"
    )

    /**
     * Order-independent pair key for two note IDs, used to deduplicate linked or dismissed Idea Collisions.
     */
    fun canonicalPair(a: String, b: String): Pair<String, String> =
        if (a <= b) a to b else b to a

    /**
     * Offline Idea Collision Engine: discovers the strongest non-trivial pair of ideas in the user's
     * Spark Vault that share meaningful terms/themes, so older sparks resurface and cross-pollinate.
     * Skips any pairs in [excludedPairs] (already linked into a Story Thread or dismissed by the user).
     */
    fun findBestIdeaCollision(
        ideas: List<Note>,
        minScore: Float = 0.16f,
        excludedPairs: Set<Pair<String, String>> = emptySet()
    ): IdeaCollision? {
        if (ideas.size < 2) return null
        val tokenized = ideas.map { note ->
            note to extractTokens("${note.title} ${note.plainText}")
        }.filter { it.second.size >= 2 }
        if (tokenized.size < 2) return null

        var best: IdeaCollision? = null
        for (i in 0 until tokenized.size) {
            val (aNote, aFreq) = tokenized[i]
            for (j in i + 1 until tokenized.size) {
                val (bNote, bFreq) = tokenized[j]
                if (canonicalPair(aNote.id, bNote.id) in excludedPairs) continue
                val shared = aFreq.keys.intersect(bFreq.keys)
                if (shared.size < 2) continue
                val score = cosineSimilarity(aFreq, bFreq)
                if (score >= minScore && (best == null || score > best.score)) {
                    val topThemes = shared
                        .sortedByDescending { (aFreq[it] ?: 0) + (bFreq[it] ?: 0) }
                        .take(3)
                        .map { it.replaceFirstChar { ch -> ch.uppercase() } }
                    best = IdeaCollision(first = aNote, second = bNote, sharedThemes = topThemes, score = score)
                }
            }
        }
        return best
    }

    /**
     * Extracts a grounded 7-day Challenge definition directly from an imported `MOTIVATION` or
     * `LIFE_SKILLS` YouTube/Audio note using its real lines (never inventing external claims).
     */
    fun deriveChallengeFromPlaybook(playbook: Note, durationDays: Int = 7): Pair<String, CustomChallengeConfig> {
        val cleanTitle = playbook.title.trim().ifBlank { "Personal Playbook" }
        val lines = playbook.plainText.lineSequence()
            .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .filter { it.length in 12..140 && !it.endsWith(":") }
            .toList()
        val firstAction = lines.firstOrNull() ?: "Apply one takeaway from \"$cleanTitle\""
        val challengeTitle = "${durationDays} Days: ${cleanTitle.take(44)}"
        val config = CustomChallengeConfig(
            dailyAction = firstAction,
            minEffortAction = "Review 1 key byte from \"$cleanTitle\" (2 min)",
            reflectionPrompt = "How did practicing \"$cleanTitle\" show up in your day?",
            sourceNoteId = playbook.id,
            sourceNoteTitle = cleanTitle
        )
        return challengeTitle to config
    }

    internal fun extractTokens(text: String): Map<String, Int> {
        val counts = HashMap<String, Int>()
        Regex("[a-zA-Z]{4,}").findAll(text.lowercase()).forEach { m ->
            val w = m.value
            if (w !in STOP_WORDS) counts[w] = (counts[w] ?: 0) + 1
        }
        return counts
    }

    private fun cosineSimilarity(a: Map<String, Int>, b: Map<String, Int>): Float {
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for ((k, v) in a) {
            magA += v * v
            b[k]?.let { dot += v * it }
        }
        for (v in b.values) magB += v * v
        if (magA == 0.0 || magB == 0.0) return 0f
        return (dot / (sqrt(magA) * sqrt(magB))).toFloat()
    }
}
