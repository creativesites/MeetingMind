package com.craftflowtechnologies.meetingmind.core.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Qualitative mastery states for a concept (docs/PLAN_LEARNING.md §5.3).
 * Transparent and grounded in evidence — never an opaque percentage.
 */
enum class LearningMasteryState(val label: String, val description: String) {
    NEW("New", "Captured or extracted, not yet tested"),
    LEARNING("Learning", "Some evidence, early reviews due or mixed outcomes"),
    DEVELOPING("Developing", "Recalled successfully across more than one session"),
    STRONG("Strong", "Sustained successful recall and application over time"),
    NEEDS_REVIEW("Needs review", "Overdue review or recent difficulty requires attention")
}

/** Types of practice activities supported in Learning Release 1. */
enum class LearningActivityType(val label: String) {
    RECALL("Recall"),
    MULTIPLE_CHOICE("Multiple Choice"),
    APPLICATION("Application")
}

/** Rating given during a recall activity self-evaluation. */
enum class RecallRating(val factorMultiplier: Double) {
    AGAIN(0.8),
    HARD(0.9),
    GOOD(1.0),
    EASY(1.15)
}

/**
 * Cited source link to a note block or transcript segment.
 * Reuses the repository's citation model (PLAN_LEARNING §5.2).
 */
data class LearningEvidence(
    val noteId: String? = null,
    val blockId: String? = null,
    val meetingId: String? = null,
    val segmentIds: List<String> = emptyList(),
    val startMs: Long? = null,
    val endMs: Long? = null,
    val quote: String = ""
) {
    val excerpt: String? get() = quote.takeIf { it.isNotBlank() }

    fun displayLabel(): String = when {
        meetingId != null && segmentIds.isNotEmpty() -> "Transcript Seg #${segmentIds.first()}"
        startMs != null -> "Recording @ ${startMs / 1000}s"
        blockId != null -> "Note block $blockId"
        noteId != null -> "Note excerpt"
        else -> "Lecture source"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("noteId", noteId)
        put("blockId", blockId)
        put("meetingId", meetingId)
        put("segmentIds", JSONArray(segmentIds))
        put("startMs", startMs)
        put("endMs", endMs)
        put("quote", quote)
    }

    companion object {
        fun fromJson(obj: JSONObject): LearningEvidence {
            val segs = mutableListOf<String>()
            obj.optJSONArray("segmentIds")?.let { arr ->
                for (i in 0 until arr.length()) segs.add(arr.optString(i))
            }
            return LearningEvidence(
                noteId = obj.optString("noteId").takeIf { it.isNotBlank() },
                blockId = obj.optString("blockId").takeIf { it.isNotBlank() },
                meetingId = obj.optString("meetingId").takeIf { it.isNotBlank() },
                segmentIds = segs,
                startMs = if (obj.has("startMs") && !obj.isNull("startMs")) obj.optLong("startMs") else null,
                endMs = if (obj.has("endMs") && !obj.isNull("endMs")) obj.optLong("endMs") else null,
                quote = obj.optString("quote", "")
            )
        }

        fun parseList(jsonStr: String): List<LearningEvidence> = runCatching {
            val arr = JSONArray(jsonStr)
            List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())

        fun listToJson(list: List<LearningEvidence>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/** A durable Learning Session scoped view over a Note (PLAN_LEARNING §0). */
data class LearningSession(
    val id: String,
    val noteId: String,
    val meetingId: String? = null,
    val title: String,
    val courseName: String? = null,
    val status: String = "ACTIVE",
    val lastStudiedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isPaused: Boolean get() = status == "PAUSED"
}

/** A key concept extracted or created within a Learning Session. */
data class LearningConcept(
    val id: String,
    val sessionId: String,
    val name: String,
    val definition: String,
    val emphasis: String? = null,
    val relationships: List<String> = emptyList(),
    val evidence: List<LearningEvidence> = emptyList(),
    val state: LearningMasteryState = LearningMasteryState.NEW,
    val isUserEdited: Boolean = false,
    val isDismissed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

/** An activity: question prompt, rubric, options, and evidence links. */
data class LearningActivity(
    val id: String,
    val sessionId: String,
    val conceptId: String? = null,
    val type: LearningActivityType,
    val prompt: String,
    val expectedAnswer: String,
    val options: List<String> = emptyList(),
    val difficulty: String = "MEDIUM",
    val evidence: List<LearningEvidence> = emptyList(),
    val isDiagnostic: Boolean = false,
    val isDismissed: Boolean = false,
    val isStale: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

/** An immutable attempt record capturing learner recall/answer. */
data class ActivityAttempt(
    val id: String,
    val activityId: String,
    val sessionId: String,
    val conceptId: String? = null,
    val userResponse: String,
    val isCorrect: Boolean,
    val selfRating: RecallRating? = null,
    val feedback: String = "",
    val createdAt: Long
) {
    val attemptedAt: Long get() = createdAt
}

/** Spaced review schedule for a practice activity. */
data class ReviewSchedule(
    val id: String,
    val activityId: String,
    val sessionId: String,
    val conceptId: String? = null,
    val dueAt: Long,
    val intervalDays: Int = 0,
    val repetitionCount: Int = 0,
    val easeFactor: Double = 2.5,
    val isPaused: Boolean = false,
    val snoozedUntil: Long? = null,
    val lastReviewedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun isDue(now: Long): Boolean {
        if (isPaused) return false
        snoozedUntil?.let { if (it > now) return false }
        return dueAt <= now
    }
}

/**
 * Deterministic Spaced Retrieval Scheduler (PLAN_LEARNING §5.3).
 * Conservative intervals: same day (0) → 1 day → 3 days → 7 days → 14 days...
 */
object DeterministicSpacedScheduler {
    const val DAY_MS: Long = 24 * 60 * 60 * 1000L

    fun calculateNextSchedule(
        current: ReviewSchedule,
        isCorrect: Boolean,
        rating: RecallRating = if (isCorrect) RecallRating.GOOD else RecallRating.AGAIN,
        now: Long = System.currentTimeMillis()
    ): ReviewSchedule {
        return if (isCorrect) {
            val newRepetition = current.repetitionCount + 1
            val newEase = (current.easeFactor * rating.factorMultiplier).coerceIn(1.3, 3.0)
            val newIntervalDays = when (newRepetition) {
                1 -> 1
                2 -> 3
                3 -> 7
                4 -> 14
                else -> (current.intervalDays * newEase).toInt().coerceAtLeast(current.intervalDays + 7)
            }
            current.copy(
                intervalDays = newIntervalDays,
                repetitionCount = newRepetition,
                easeFactor = newEase,
                dueAt = now + (newIntervalDays * DAY_MS),
                snoozedUntil = null,
                lastReviewedAt = now,
                updatedAt = now
            )
        } else {
            // Unsuccessful attempt resets repetition count and steps back conservatively
            val newEase = (current.easeFactor * 0.85).coerceIn(1.3, 3.0)
            current.copy(
                intervalDays = 1,
                repetitionCount = 0,
                easeFactor = newEase,
                dueAt = now + DAY_MS,
                snoozedUntil = null,
                lastReviewedAt = now,
                updatedAt = now
            )
        }
    }

    fun initialSchedule(
        activityId: String,
        sessionId: String,
        conceptId: String?,
        now: Long = System.currentTimeMillis()
    ): ReviewSchedule {
        return ReviewSchedule(
            id = java.util.UUID.randomUUID().toString(),
            activityId = activityId,
            sessionId = sessionId,
            conceptId = conceptId,
            dueAt = now, // Due immediately for initial diagnostic/practice
            intervalDays = 0,
            repetitionCount = 0,
            easeFactor = 2.5,
            isPaused = false,
            snoozedUntil = null,
            lastReviewedAt = null,
            createdAt = now,
            updatedAt = now
        )
    }
}

/**
 * Calculates transparent concept mastery based on attempts and time.
 * (PLAN_LEARNING §5.3)
 */
object MasteryCalculator {
    data class MasteryEvaluation(
        val state: LearningMasteryState,
        val reason: String
    )

    fun evaluate(
        attempts: List<ActivityAttempt>,
        schedule: ReviewSchedule?,
        now: Long = System.currentTimeMillis()
    ): MasteryEvaluation {
        if (attempts.isEmpty()) {
            return MasteryEvaluation(LearningMasteryState.NEW, "Not yet tested in diagnostic or practice.")
        }

        val sortedAttempts = attempts.sortedBy { it.createdAt }
        val latestAttempt = sortedAttempts.last()

        // If the latest attempt failed, or if an item is overdue by > 2 days
        val isOverdue = schedule != null && !schedule.isPaused && (now - schedule.dueAt > 2 * DeterministicSpacedScheduler.DAY_MS)
        if (!latestAttempt.isCorrect) {
            return MasteryEvaluation(
                LearningMasteryState.NEEDS_REVIEW,
                "Last attempt was incorrect; review recommended."
            )
        }
        if (isOverdue) {
            return MasteryEvaluation(
                LearningMasteryState.NEEDS_REVIEW,
                "Review is overdue by more than two days."
            )
        }

        val successfulCount = attempts.count { it.isCorrect }
        val distinctDays = attempts.map { it.createdAt / DeterministicSpacedScheduler.DAY_MS }.distinct().size

        return when {
            successfulCount >= 3 && (schedule?.intervalDays ?: 0) >= 7 && distinctDays >= 3 -> {
                MasteryEvaluation(
                    LearningMasteryState.STRONG,
                    "Retained across $distinctDays separate days with a 7+ day interval."
                )
            }
            distinctDays >= 2 && successfulCount >= 2 -> {
                MasteryEvaluation(
                    LearningMasteryState.DEVELOPING,
                    "Successfully recalled across multiple study sessions."
                )
            }
            else -> {
                MasteryEvaluation(
                    LearningMasteryState.LEARNING,
                    "Early recall demonstrated; spaced repetition in progress."
                )
            }
        }
    }
}
