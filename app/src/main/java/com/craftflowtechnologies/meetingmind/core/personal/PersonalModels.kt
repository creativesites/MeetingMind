package com.craftflowtechnologies.meetingmind.core.personal

import com.craftflowtechnologies.meetingmind.core.database.GrowthEnrollmentEntity
import com.craftflowtechnologies.meetingmind.core.database.GrowthStepProgressEntity
import com.craftflowtechnologies.meetingmind.core.database.PersonalDailyPlanEntity
import com.craftflowtechnologies.meetingmind.core.database.PersonalRoutineEntity
import com.craftflowtechnologies.meetingmind.core.database.RoutineCompletionEntity
import com.craftflowtechnologies.meetingmind.core.database.RoutineStepEntity
import org.json.JSONArray

/**
 * Pure domain models for the Personal Vertical ("Remember · Reflect · Build · Grow · Preserve").
 * No Android or Compose dependencies so business logic is fast and unit-testable on the plain JVM.
 */

enum class RoutineTimeWindow(val label: String, val defaultMinute: Int) {
    MORNING("Morning", 7 * 60 + 30),
    AFTERNOON("Afternoon", 13 * 60 + 30),
    EVENING("Evening", 21 * 60),
    ANYTIME("Anytime", 12 * 60);

    companion object {
        fun fromNameOrDefault(name: String?): RoutineTimeWindow =
            entries.firstOrNull { it.name == name } ?: MORNING

        fun forHourOfDay(hour: Int): RoutineTimeWindow = when (hour) {
            in 4..11 -> MORNING
            in 12..17 -> AFTERNOON
            else -> EVENING
        }
    }
}

enum class RoutineStepKind(val label: String) {
    CHECK("Action"),
    BREATHING("Breathing"),
    VOICE_REFLECTION("Voice reflection"),
    QUIET_WRITE("Quiet write"),
    INTENTION("Today's intention"),
    COACHING_AUDIO("Guided audio"),
    TIMER("Focus timer"),
    MEMORY("Memory glance");

    val capturesJournal: Boolean
        get() = this == VOICE_REFLECTION || this == QUIET_WRITE || this == INTENTION

    companion object {
        fun fromNameOrDefault(name: String?): RoutineStepKind =
            entries.firstOrNull { it.name == name } ?: CHECK
    }
}

enum class RoutineCompletionStatus {
    IN_PROGRESS,
    COMPLETED,
    COMPLETED_MIN_EFFORT,
    SKIPPED;

    val isDone: Boolean get() = this == COMPLETED || this == COMPLETED_MIN_EFFORT

    companion object {
        fun fromNameOrDefault(name: String?): RoutineCompletionStatus =
            entries.firstOrNull { it.name == name } ?: IN_PROGRESS
    }
}

enum class PersonalSoundscape(val label: String, val tagline: String) {
    NONE("Quiet", "Silence and natural focus"),
    RAIN("Soft Rain", "Gentle rain against glass"),
    FIREPLACE("Warm Ember", "Crackling evening hearth"),
    FOREST("Morning Woods", "Soft breeze and distant birds"),
    DAWN("Golden Hum", "Warm ambient stillness");

    companion object {
        fun fromNameOrDefault(name: String?): PersonalSoundscape =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

enum class JournalMoodVibe(val label: String) {
    PEACEFUL("Peaceful"),
    GRATEFUL("Grateful"),
    JOYFUL("Joyful"),
    REFLECTIVE("Reflective"),
    CELEBRATORY("Celebratory");

    companion object {
        fun fromNameOrNull(name: String?): JournalMoodVibe? =
            entries.firstOrNull { it.name == name }
    }
}

enum class GrowthEnrollmentKind {
    JOURNEY,
    CHALLENGE;

    companion object {
        fun fromNameOrDefault(name: String?): GrowthEnrollmentKind =
            entries.firstOrNull { it.name == name } ?: JOURNEY
    }
}

enum class GrowthEnrollmentStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ABANDONED;

    companion object {
        fun fromNameOrDefault(name: String?): GrowthEnrollmentStatus =
            entries.firstOrNull { it.name == name } ?: ACTIVE
    }
}

data class RoutineStep(
    val id: String,
    val routineId: String,
    val position: Int,
    val kind: RoutineStepKind,
    val title: String,
    val minEffortTitle: String = "",
    val durationSeconds: Int = 120,
    val minEffortDurationSeconds: Int = 45,
    val promptText: String = "",
    val guidedActivityId: String? = null,
    val companionState: String = "IDLE",
    val isEnabled: Boolean = true
) {
    fun effectiveTitle(minEffort: Boolean): String =
        if (minEffort && minEffortTitle.isNotBlank()) minEffortTitle else title

    fun effectiveDurationSeconds(minEffort: Boolean): Int =
        if (minEffort) minEffortDurationSeconds.coerceAtLeast(15) else durationSeconds.coerceAtLeast(15)

    fun toEntity(): RoutineStepEntity = RoutineStepEntity(
        id = id,
        routineId = routineId,
        position = position,
        kind = kind.name,
        title = title,
        minEffortTitle = minEffortTitle,
        durationSeconds = durationSeconds,
        minEffortDurationSeconds = minEffortDurationSeconds,
        promptText = promptText,
        guidedActivityId = guidedActivityId,
        companionState = companionState,
        isEnabled = isEnabled
    )

    companion object {
        fun fromEntity(e: RoutineStepEntity): RoutineStep = RoutineStep(
            id = e.id,
            routineId = e.routineId,
            position = e.position,
            kind = RoutineStepKind.fromNameOrDefault(e.kind),
            title = e.title,
            minEffortTitle = e.minEffortTitle,
            durationSeconds = e.durationSeconds,
            minEffortDurationSeconds = e.minEffortDurationSeconds,
            promptText = e.promptText,
            guidedActivityId = e.guidedActivityId,
            companionState = e.companionState,
            isEnabled = e.isEnabled
        )
    }
}

data class PersonalRoutine(
    val id: String,
    val title: String,
    val whyDescription: String = "",
    val timeWindow: RoutineTimeWindow = RoutineTimeWindow.MORNING,
    val scheduledMinuteOfDay: Int? = null,
    val daysOfWeekMask: Int = ALL_DAYS_MASK,
    val reminderEnabled: Boolean = false,
    val anchorCue: String = "",
    val soundscape: PersonalSoundscape = PersonalSoundscape.NONE,
    val linkedJourneyId: String? = null,
    val lifeChapterNotebookId: String? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val steps: List<RoutineStep> = emptyList()
) {
    val activeSteps: List<RoutineStep> get() = steps.filter { it.isEnabled }.sortedBy { it.position }

    fun totalMinutes(minEffort: Boolean = false): Int {
        val secs = activeSteps.sumOf { it.effectiveDurationSeconds(minEffort) }
        return ((secs + 59) / 60).coerceAtLeast(1)
    }

    /** [dayOfWeekIndex]: 0 = Monday .. 6 = Sunday. */
    fun isScheduledOnDay(dayOfWeekIndex: Int): Boolean {
        val bit = 1 shl dayOfWeekIndex.coerceIn(0, 6)
        return (daysOfWeekMask and bit) != 0
    }

    fun toEntity(): PersonalRoutineEntity = PersonalRoutineEntity(
        id = id,
        title = title,
        whyDescription = whyDescription,
        timeWindow = timeWindow.name,
        scheduledMinuteOfDay = scheduledMinuteOfDay,
        daysOfWeekMask = daysOfWeekMask,
        reminderEnabled = reminderEnabled,
        anchorCue = anchorCue,
        soundscapeId = soundscape.name,
        linkedJourneyId = linkedJourneyId,
        lifeChapterNotebookId = lifeChapterNotebookId,
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        const val ALL_DAYS_MASK = 127 // Mon..Sun

        fun fromEntity(e: PersonalRoutineEntity, steps: List<RoutineStepEntity> = emptyList()): PersonalRoutine =
            PersonalRoutine(
                id = e.id,
                title = e.title,
                whyDescription = e.whyDescription,
                timeWindow = RoutineTimeWindow.fromNameOrDefault(e.timeWindow),
                scheduledMinuteOfDay = e.scheduledMinuteOfDay,
                daysOfWeekMask = e.daysOfWeekMask,
                reminderEnabled = e.reminderEnabled,
                anchorCue = e.anchorCue,
                soundscape = PersonalSoundscape.fromNameOrDefault(e.soundscapeId),
                linkedJourneyId = e.linkedJourneyId,
                lifeChapterNotebookId = e.lifeChapterNotebookId,
                isArchived = e.isArchived,
                sortOrder = e.sortOrder,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
                steps = steps.map(RoutineStep::fromEntity).sortedBy { it.position }
            )
    }
}

data class RoutineCompletion(
    val id: String,
    val routineId: String,
    val epochDay: Long,
    val status: RoutineCompletionStatus,
    val currentStepIndex: Int = 0,
    val completedStepIds: List<String> = emptyList(),
    val usedMinEffortMode: Boolean = false,
    val actualDurationSeconds: Int = 0,
    val reflectionNoteId: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null
) {
    fun toEntity(): RoutineCompletionEntity = RoutineCompletionEntity(
        id = id,
        routineId = routineId,
        epochDay = epochDay,
        status = status.name,
        currentStepIndex = currentStepIndex,
        completedStepIdsJson = encodeStringList(completedStepIds),
        usedMinEffortMode = usedMinEffortMode,
        actualDurationSeconds = actualDurationSeconds,
        reflectionNoteId = reflectionNoteId,
        startedAt = startedAt,
        completedAt = completedAt
    )

    companion object {
        fun fromEntity(e: RoutineCompletionEntity): RoutineCompletion = RoutineCompletion(
            id = e.id,
            routineId = e.routineId,
            epochDay = e.epochDay,
            status = RoutineCompletionStatus.fromNameOrDefault(e.status),
            currentStepIndex = e.currentStepIndex,
            completedStepIds = decodeStringList(e.completedStepIdsJson),
            usedMinEffortMode = e.usedMinEffortMode,
            actualDurationSeconds = e.actualDurationSeconds,
            reflectionNoteId = e.reflectionNoteId,
            startedAt = e.startedAt,
            completedAt = e.completedAt
        )
    }
}

data class GrowthEnrollment(
    val id: String,
    val kind: GrowthEnrollmentKind,
    val catalogId: String,
    val title: String,
    val customConfigJson: String = "{}",
    val status: GrowthEnrollmentStatus = GrowthEnrollmentStatus.ACTIVE,
    val startedEpochDay: Long,
    val currentStepOrDay: Int = 0,
    val targetStepsOrDays: Int,
    val anchorWhyNoteId: String? = null,
    val finaleNoteId: String? = null,
    val lifeChapterNotebookId: String? = null,
    val catalogVersion: Int = 1,
    val lastActivityAt: Long,
    val completedAt: Long? = null
) {
    val progressFraction: Float
        get() = if (targetStepsOrDays <= 0) 0f else (currentStepOrDay.toFloat() / targetStepsOrDays).coerceIn(0f, 1f)

    fun toEntity(): GrowthEnrollmentEntity = GrowthEnrollmentEntity(
        id = id,
        kind = kind.name,
        catalogId = catalogId,
        title = title,
        customConfigJson = customConfigJson,
        status = status.name,
        startedEpochDay = startedEpochDay,
        currentStepOrDay = currentStepOrDay,
        targetStepsOrDays = targetStepsOrDays,
        anchorWhyNoteId = anchorWhyNoteId,
        finaleNoteId = finaleNoteId,
        lifeChapterNotebookId = lifeChapterNotebookId,
        catalogVersion = catalogVersion,
        lastActivityAt = lastActivityAt,
        completedAt = completedAt
    )

    companion object {
        fun fromEntity(e: GrowthEnrollmentEntity): GrowthEnrollment = GrowthEnrollment(
            id = e.id,
            kind = GrowthEnrollmentKind.fromNameOrDefault(e.kind),
            catalogId = e.catalogId,
            title = e.title,
            customConfigJson = e.customConfigJson,
            status = GrowthEnrollmentStatus.fromNameOrDefault(e.status),
            startedEpochDay = e.startedEpochDay,
            currentStepOrDay = e.currentStepOrDay,
            targetStepsOrDays = e.targetStepsOrDays,
            anchorWhyNoteId = e.anchorWhyNoteId,
            finaleNoteId = e.finaleNoteId,
            lifeChapterNotebookId = e.lifeChapterNotebookId,
            catalogVersion = e.catalogVersion,
            lastActivityAt = e.lastActivityAt,
            completedAt = e.completedAt
        )
    }
}

data class GrowthStepProgress(
    val id: String,
    val enrollmentId: String,
    val stepIndex: Int,
    val completedEpochDay: Long,
    val usedMinEffort: Boolean = false,
    val reflectionNoteId: String? = null,
    val reflectionBlockId: String? = null,
    val completedAt: Long
) {
    fun toEntity(): GrowthStepProgressEntity = GrowthStepProgressEntity(
        id = id,
        enrollmentId = enrollmentId,
        stepIndex = stepIndex,
        completedEpochDay = completedEpochDay,
        usedMinEffort = usedMinEffort,
        reflectionNoteId = reflectionNoteId,
        reflectionBlockId = reflectionBlockId,
        completedAt = completedAt
    )

    companion object {
        fun fromEntity(e: GrowthStepProgressEntity): GrowthStepProgress = GrowthStepProgress(
            id = e.id,
            enrollmentId = e.enrollmentId,
            stepIndex = e.stepIndex,
            completedEpochDay = e.completedEpochDay,
            usedMinEffort = e.usedMinEffort,
            reflectionNoteId = e.reflectionNoteId,
            reflectionBlockId = e.reflectionBlockId,
            completedAt = e.completedAt
        )
    }
}

data class PersonalDailyPlan(
    val epochDay: Long,
    val intentionText: String = "",
    val intentionNoteBlockId: String? = null,
    val committedItemIds: List<String> = emptyList(),
    val dismissedItemIds: Set<String> = emptySet(),
    val isMinEffortDay: Boolean = false,
    val moodVibe: JournalMoodVibe? = null,
    val dayPageNoteId: String? = null,
    val updatedAt: Long = 0L
) {
    fun toEntity(): PersonalDailyPlanEntity = PersonalDailyPlanEntity(
        epochDay = epochDay,
        intentionText = intentionText,
        intentionNoteBlockId = intentionNoteBlockId,
        committedItemIdsJson = encodeStringList(committedItemIds),
        dismissedItemIdsJson = encodeStringList(dismissedItemIds.toList()),
        isMinEffortDay = isMinEffortDay,
        moodVibe = moodVibe?.name,
        dayPageNoteId = dayPageNoteId,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(e: PersonalDailyPlanEntity): PersonalDailyPlan = PersonalDailyPlan(
            epochDay = e.epochDay,
            intentionText = e.intentionText,
            intentionNoteBlockId = e.intentionNoteBlockId,
            committedItemIds = decodeStringList(e.committedItemIdsJson),
            dismissedItemIds = decodeStringList(e.dismissedItemIdsJson).toSet(),
            isMinEffortDay = e.isMinEffortDay,
            moodVibe = JournalMoodVibe.fromNameOrNull(e.moodVibe),
            dayPageNoteId = e.dayPageNoteId,
            updatedAt = e.updatedAt
        )
    }
}

/** Metadata keys stored on [com.craftflowtechnologies.meetingmind.core.model.Note.metadata] for Personal features. */
object PersonalNoteMeta {
    /** ISO local date "YYYY-MM-DD" for a stitched Day-Page Journal entry. */
    const val DAY_PAGE_DATE = "personal_day_page_date"
    /** Epoch day string matching [PersonalDailyPlan.epochDay]. */
    const val DAY_PAGE_EPOCH_DAY = "personal_day_page_epoch_day"
    /** Selected emotional vibe palette for the day's entry. */
    const val MOOD_VIBE = "personal_mood_vibe"
    /** Idea studio pipeline stage: SPARK, SIMMERING, BUILDING, SHIPPED. */
    const val IDEA_STAGE = "personal_idea_stage"
    /** Epoch millis when a sealed 3D Time Capsule unlocks. */
    const val CAPSULE_UNLOCK_AT_MS = "personal_capsule_unlock_at_ms"
    /** Label for a Time Capsule seal ("New Year's Eve", "1 year from today"). */
    const val CAPSULE_LABEL = "personal_capsule_label"
    /** Routine ID or Journey ID that created/linked this reflection. */
    const val SOURCE_ROUTINE_ID = "personal_source_routine_id"
    const val SOURCE_ENROLLMENT_ID = "personal_source_enrollment_id"
    const val SOURCE_NOTE_TITLE = "personal_source_note_title"
}

enum class IdeaStage(val label: String) {
    SPARK("Seed"),
    SIMMERING("Simmering"),
    BUILDING("Building"),
    SHIPPED("Shipped");

    companion object {
        fun fromNameOrDefault(name: String?): IdeaStage =
            entries.firstOrNull { it.name == name } ?: SPARK
    }
}

internal fun encodeStringList(items: List<String>): String =
    JSONArray(items).toString()

internal fun decodeStringList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
