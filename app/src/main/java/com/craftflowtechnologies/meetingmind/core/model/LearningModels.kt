package com.craftflowtechnologies.meetingmind.core.model

import com.craftflowtechnologies.meetingmind.ai.notes.SourcePassage
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
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

/** Types of practice activities supported in Learning. */
enum class LearningActivityType(val label: String) {
    RECALL("Recall"),
    MULTIPLE_CHOICE("Multiple Choice"),
    APPLICATION("Application"),
    WORKED_PROBLEM("Worked Problem"),
    CODE_TRACE("Code & Complexity"),
    JOURNAL_ENTRY("Accounting Entry"),
    TEACH_BACK("Teach It Back")
}

/** Instructor emphasis and exam relevance tier for a concept. */
enum class ExamImportance(val label: String) {
    NORMAL("Standard"),
    HIGH_YIELD("High-yield"),
    LECTURER_FLAGGED("Exam hint")
}

/** Coverage state for Teach-It-Back rubric evaluation. */
enum class TeachBackState(val label: String) {
    COVERED("Covered"),
    PARTLY_COVERED("Partly covered"),
    NOT_YET_DEMONSTRATED("Not yet demonstrated")
}

/** Source-grounded criterion in a Teach-It-Back activity rubric. */
data class TeachBackCriterion(
    val claim: String,
    val required: Boolean = true,
    val evidence: LearningEvidence? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("claim", claim)
        put("required", required)
        evidence?.let { put("evidence", it.toJson()) }
    }

    companion object {
        fun fromJson(obj: JSONObject): TeachBackCriterion = TeachBackCriterion(
            claim = obj.optString("claim", ""),
            required = obj.optBoolean("required", true),
            evidence = obj.optJSONObject("evidence")?.let { LearningEvidence.fromJson(it) }
        )

        fun parseList(jsonStr: String?): List<TeachBackCriterion> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(jsonStr)
                List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
                    .filter { it.claim.isNotBlank() }
            }.getOrDefault(emptyList())
        }

        fun listToJson(list: List<TeachBackCriterion>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/**
 * Structured metadata stored inside a LEARNING [Notebook.propertiesJson].
 * Safely merges with any pre-existing notebook JSON properties (`confidential`, `status`, etc.).
 */
data class CourseProperties(
    val courseCode: String = "",
    val domain: SubjectDomain = SubjectDomain.GENERAL,
    val lecturer: String = "",
    val syllabusTerms: List<String> = emptyList(),
    val targetGrade: String = ""
) {
    @JvmOverloads
    fun toJson(existingRaw: String? = null): String {
        val base = if (!existingRaw.isNullOrBlank()) {
            runCatching { JSONObject(existingRaw) }.getOrElse { JSONObject() }
        } else {
            JSONObject()
        }
        base.put("courseCode", courseCode.trim())
        base.put("domain", domain.name)
        base.put("lecturer", lecturer.trim())
        base.put("syllabusTerms", JSONArray(syllabusTerms.map { it.trim() }.filter { it.isNotEmpty() }))
        base.put("targetGrade", targetGrade.trim())
        return base.toString()
    }

    companion object {
        fun fromJson(raw: String?): CourseProperties {
            if (raw.isNullOrBlank()) return CourseProperties()
            return runCatching {
                val obj = JSONObject(raw)
                val terms = mutableListOf<String>()
                obj.optJSONArray("syllabusTerms")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val t = arr.optString(i).trim()
                        if (t.isNotEmpty()) terms.add(t)
                    }
                }
                CourseProperties(
                    courseCode = obj.optString("courseCode", ""),
                    domain = SubjectDomain.fromName(obj.optString("domain", "GENERAL")),
                    lecturer = obj.optString("lecturer", ""),
                    syllabusTerms = terms,
                    targetGrade = obj.optString("targetGrade", "")
                )
            }.getOrDefault(CourseProperties())
        }
    }
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

/**
 * Canonical Learning source passage abstraction.
 * Unifies NoteBlocks, TranscriptSegments, and NoteText so that evidence extraction
 * preserves authentic source provenance (noteId, blockId, meetingId, segmentId, timestamps, quotes).
 */
sealed class LearningPassage {
    abstract val id: String
    abstract val text: String
    abstract val label: String
    abstract val noteId: String

    abstract fun toEvidence(): LearningEvidence

    fun toSourcePassage(): SourcePassage = SourcePassage(
        id = id,
        text = text,
        label = label,
        noteId = noteId
    )

    data class NoteBlock(
        override val id: String,
        override val text: String,
        override val label: String,
        override val noteId: String,
        val blockId: String
    ) : LearningPassage() {
        override fun toEvidence(): LearningEvidence = LearningEvidence(
            noteId = noteId,
            blockId = blockId,
            meetingId = null,
            segmentIds = emptyList(),
            startMs = null,
            endMs = null,
            quote = text.take(200)
        )
    }

    data class TranscriptSegment(
        override val id: String,
        override val text: String,
        override val label: String,
        override val noteId: String,
        val meetingId: String,
        val segmentId: String,
        val startMs: Long?,
        val endMs: Long?
    ) : LearningPassage() {
        override fun toEvidence(): LearningEvidence = LearningEvidence(
            noteId = noteId,
            blockId = null,
            meetingId = meetingId,
            segmentIds = listOf(segmentId),
            startMs = startMs,
            endMs = endMs,
            quote = text.take(200)
        )
    }

    data class NoteText(
        override val id: String,
        override val text: String,
        override val label: String,
        override val noteId: String
    ) : LearningPassage() {
        override fun toEvidence(): LearningEvidence = LearningEvidence(
            noteId = noteId,
            blockId = null,
            meetingId = null,
            segmentIds = emptyList(),
            startMs = null,
            endMs = null,
            quote = text.take(200)
        )
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
    val updatedAt: Long,
    val subjectDomain: String = "GENERAL",
    val examSignalCount: Int = 0,
    val unresolvedConfusionCount: Int = 0
) {
    val isPaused: Boolean get() = status == "PAUSED"
    val domain: SubjectDomain get() = SubjectDomain.fromName(subjectDomain)
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
    val isStale: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val examImportance: ExamImportance = ExamImportance.NORMAL,
    val formulaLatex: String? = null
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
    val updatedAt: Long,
    val rubric: List<TeachBackCriterion> = emptyList()
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
    val createdAt: Long,
    val audioPath: String? = null,
    val attachmentId: String? = null
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

/** Exam or study milestone goal scoped optionally to a course notebook and/or sessions. */
data class LearningGoal(
    val id: String,
    val notebookId: String? = null,
    val title: String,
    val examDateMs: Long,
    val examFormat: String = "MIXED",
    val sessionIds: List<String> = emptyList(),
    val topicWeights: Map<String, Float> = emptyMap(),
    val pastPaperNoteId: String? = null,
    val dailyMinutesTarget: Int = 25,
    val createdAt: Long,
    val updatedAt: Long
)

/** Ranked study priority entry for exam readiness matrices. */
data class ExamPriorityItem(
    val concept: LearningConcept,
    val sessionTitle: String,
    val mastery: MasteryCalculator.MasteryEvaluation,
    val priorityScore: Double,
    val reason: String
)

/**
 * Transparently ranks concepts for exam preparation by combining:
 * 1. [ExamImportance] (`LECTURER_FLAGGED` > `HIGH_YIELD` > `NORMAL`)
 * 2. [LearningMasteryState] (`NEEDS_REVIEW` > `NEW` > `LEARNING` > `DEVELOPING` > `STRONG`)
 * 3. Days remaining until the exam (closer exams amplify urgency)
 */
object ExamPriorityRanker {

    fun importanceScore(importance: ExamImportance): Double = when (importance) {
        ExamImportance.LECTURER_FLAGGED -> 50.0
        ExamImportance.HIGH_YIELD -> 30.0
        ExamImportance.NORMAL -> 10.0
    }

    fun masteryGapScore(state: LearningMasteryState): Double = when (state) {
        LearningMasteryState.NEEDS_REVIEW -> 40.0
        LearningMasteryState.NEW -> 32.0
        LearningMasteryState.LEARNING -> 22.0
        LearningMasteryState.DEVELOPING -> 10.0
        LearningMasteryState.STRONG -> 2.0
    }

    fun examUrgencyBoost(daysUntilExam: Int?): Double {
        if (daysUntilExam == null) return 0.0
        return when {
            daysUntilExam <= 1 -> 18.0
            daysUntilExam <= 3 -> 14.0
            daysUntilExam <= 7 -> 10.0
            daysUntilExam <= 14 -> 6.0
            daysUntilExam <= 30 -> 3.0
            else -> 0.0
        }
    }

    fun rank(
        concepts: List<LearningConcept>,
        sessionTitles: Map<String, String> = emptyMap(),
        masteryByConceptId: Map<String, MasteryCalculator.MasteryEvaluation> = emptyMap(),
        examDateMs: Long? = null,
        topicWeights: Map<String, Float> = emptyMap(),
        now: Long = System.currentTimeMillis()
    ): List<ExamPriorityItem> {
        val daysUntilExam = examDateMs?.let {
            ((it - now).coerceAtLeast(0L) / DeterministicSpacedScheduler.DAY_MS).toInt()
        }
        val urgency = examUrgencyBoost(daysUntilExam)

        return concepts
            .filter { !it.isDismissed }
            .map { concept ->
                val sessionTitle = sessionTitles[concept.sessionId] ?: "Study Session"
                val mastery = masteryByConceptId[concept.id]
                    ?: MasteryCalculator.MasteryEvaluation(concept.state, concept.state.description)
                val weight = resolveTopicWeight(concept, sessionTitle, topicWeights)
                val rawScore = (importanceScore(concept.examImportance) + masteryGapScore(mastery.state)) * weight + urgency
                val reasonParts = mutableListOf<String>()
                if (concept.examImportance != ExamImportance.NORMAL) {
                    reasonParts.add(concept.examImportance.label)
                }
                reasonParts.add(mastery.state.label)
                if (daysUntilExam != null && daysUntilExam <= 30) {
                    reasonParts.add(if (daysUntilExam == 0) "Exam today" else "${daysUntilExam}d to exam")
                }
                ExamPriorityItem(
                    concept = concept,
                    sessionTitle = sessionTitle,
                    mastery = mastery,
                    priorityScore = rawScore,
                    reason = reasonParts.joinToString(" · ")
                )
            }
            .sortedWith(
                compareByDescending<ExamPriorityItem> { it.priorityScore }
                    .thenBy { it.concept.createdAt }
            )
    }

    private fun resolveTopicWeight(
        concept: LearningConcept,
        sessionTitle: String,
        topicWeights: Map<String, Float>
    ): Double {
        if (topicWeights.isEmpty()) return 1.0
        topicWeights[concept.sessionId]?.let { return it.coerceIn(0.5f, 3.0f).toDouble() }
        topicWeights.entries.firstOrNull { (k, _) ->
            k.equals(sessionTitle, ignoreCase = true) ||
                k.equals(concept.name, ignoreCase = true)
        }?.value?.let { return it.coerceIn(0.5f, 3.0f).toDouble() }
        return 1.0
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

    /**
     * Dynamically computes the preview string for the next review interval
     * for a given rating. Respects current repetition count, ease factor, and
     * explicitly resets to 1d on AGAIN.
     */
    fun previewInterval(
        current: ReviewSchedule?,
        rating: RecallRating,
        now: Long = System.currentTimeMillis()
    ): String {
        if (current == null) {
            return when (rating) {
                RecallRating.AGAIN -> "<1d"
                RecallRating.HARD -> "1d"
                RecallRating.GOOD -> "1d"
                RecallRating.EASY -> "3d"
            }
        }
        val next = calculateNextSchedule(
            current = current,
            isCorrect = rating != RecallRating.AGAIN,
            rating = rating,
            now = now
        )
        return formatIntervalDays(next.intervalDays)
    }

    fun formatIntervalDays(days: Int): String {
        return when {
            days <= 0 -> "<1d"
            days == 1 -> "1d"
            days < 30 -> "${days}d"
            days < 365 -> "${days / 30}mo"
            else -> "${days / 365}y"
        }
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
