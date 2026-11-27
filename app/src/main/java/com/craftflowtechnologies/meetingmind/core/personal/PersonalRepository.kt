package com.craftflowtechnologies.meetingmind.core.personal

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.NoteLinkKind
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Single source of truth for the Personal Vertical:
 * - Routines & resumable routine completions (with starter seeding on first visit)
 * - Guided Journeys & Challenges enrollments and step progress
 * - The adaptive Personal Daily Plan (intention, low-energy mode, mood vibe)
 * - Day-Page Journal Stitching (appending morning intentions, routine reflections, and guided check-ins
 *   into one cohesive daily journal document, plus private "Reflect in Journal" bridges from any note)
 */
class PersonalRepository(
    private val context: Context,
    private val database: MeetMindDatabase = MeetMindDatabase.getInstance(context),
    private val noteRepository: NoteRepository = NoteRepository(context, database),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.personalGrowthDao()

    // ------------------------------------------------------------------ Time helpers (minSdk 24 safe)

    fun todayEpochDay(nowMs: Long = clock()): Long = epochDayOf(nowMs)

    fun dayOfWeekIndex(nowMs: Long = clock()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        // Calendar.MONDAY = 2 .. Calendar.SATURDAY = 7, Calendar.SUNDAY = 1 -> map to 0..6 (Mon..Sun)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }
    }

    // ------------------------------------------------------------------ Seeding defaults

    /**
     * Seeds the two gentle default starter routines (Morning Reset & Evening Wind-Down) once
     * when the user has never configured Personal routines yet. Never recreates deleted routines
     * if the user has already customized or archived routines.
     */
    suspend fun ensureStarterRoutinesSeeded() = withContext(Dispatchers.IO) {
        if (dao.routineCount() > 0) return@withContext
        val now = clock()
        PersonalGrowthCatalog.routineTemplates
            .filter { it.isDefaultStarter }
            .forEachIndexed { idx, tpl ->
                val routineId = tpl.id
                val routine = PersonalRoutine(
                    id = routineId,
                    title = tpl.title,
                    whyDescription = tpl.whyDescription,
                    timeWindow = tpl.timeWindow,
                    scheduledMinuteOfDay = tpl.defaultMinuteOfDay,
                    daysOfWeekMask = tpl.daysOfWeekMask,
                    reminderEnabled = false,
                    anchorCue = tpl.anchorCue,
                    soundscape = tpl.soundscape,
                    sortOrder = idx,
                    createdAt = now,
                    updatedAt = now
                )
                val steps = tpl.steps.mapIndexed { sIdx, st ->
                    RoutineStep(
                        id = "${routineId}_${st.idSuffix}",
                        routineId = routineId,
                        position = sIdx,
                        kind = st.kind,
                        title = st.title,
                        minEffortTitle = st.minEffortTitle,
                        durationSeconds = st.durationSeconds,
                        minEffortDurationSeconds = st.minEffortDurationSeconds,
                        promptText = st.promptText,
                        guidedActivityId = st.guidedActivityId,
                        companionState = st.companionState
                    )
                }
                dao.saveRoutineWithSteps(routine.toEntity(), steps.map { it.toEntity() })
            }
    }

    // ------------------------------------------------------------------ Routines

    fun observeActiveRoutines(): Flow<List<PersonalRoutine>> =
        combine(dao.observeActiveRoutines(), dao.observeAllSteps()) { routines, steps ->
            val byRoutine = steps.groupBy { it.routineId }
            routines.map { r -> PersonalRoutine.fromEntity(r, byRoutine[r.id].orEmpty()) }
        }.flowOn(Dispatchers.IO)

    suspend fun getRoutine(routineId: String): PersonalRoutine? = withContext(Dispatchers.IO) {
        val entity = dao.getRoutineById(routineId) ?: return@withContext null
        val steps = dao.getStepsForRoutine(routineId)
        PersonalRoutine.fromEntity(entity, steps)
    }

    suspend fun addRoutineFromTemplate(templateId: String): PersonalRoutine? = withContext(Dispatchers.IO) {
        val tpl = PersonalGrowthCatalog.routineTemplateById(templateId) ?: return@withContext null
        val existing = dao.getRoutineById(tpl.id)
        val now = clock()
        if (existing != null) {
            if (existing.isArchived) {
                dao.setRoutineArchived(existing.id, archived = false, updatedAt = now)
            }
            return@withContext getRoutine(existing.id)
        }
        val routineId = tpl.id
        val sortOrder = dao.getActiveRoutines().size
        val routine = PersonalRoutine(
            id = routineId,
            title = tpl.title,
            whyDescription = tpl.whyDescription,
            timeWindow = tpl.timeWindow,
            scheduledMinuteOfDay = tpl.defaultMinuteOfDay,
            daysOfWeekMask = tpl.daysOfWeekMask,
            reminderEnabled = false,
            anchorCue = tpl.anchorCue,
            soundscape = tpl.soundscape,
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now
        )
        val steps = tpl.steps.mapIndexed { idx, st ->
            RoutineStep(
                id = "${routineId}_${st.idSuffix}",
                routineId = routineId,
                position = idx,
                kind = st.kind,
                title = st.title,
                minEffortTitle = st.minEffortTitle,
                durationSeconds = st.durationSeconds,
                minEffortDurationSeconds = st.minEffortDurationSeconds,
                promptText = st.promptText,
                guidedActivityId = st.guidedActivityId,
                companionState = st.companionState
            )
        }
        dao.saveRoutineWithSteps(routine.toEntity(), steps.map { it.toEntity() })
        routine.copy(steps = steps)
    }

    suspend fun saveCustomRoutine(
        title: String,
        whyDescription: String,
        timeWindow: RoutineTimeWindow,
        anchorCue: String,
        soundscape: PersonalSoundscape,
        steps: List<RoutineStep>,
        existingId: String? = null,
        scheduledMinuteOfDay: Int? = timeWindow.defaultMinute,
        daysOfWeekMask: Int = PersonalRoutine.ALL_DAYS_MASK,
        reminderEnabled: Boolean = false
    ): PersonalRoutine = withContext(Dispatchers.IO) {
        val now = clock()
        val id = existingId ?: newId("routine")
        val existing = existingId?.let { dao.getRoutineById(it) }
        val routine = PersonalRoutine(
            id = id,
            title = title.trim().ifBlank { "My Routine" },
            whyDescription = whyDescription.trim(),
            timeWindow = timeWindow,
            scheduledMinuteOfDay = scheduledMinuteOfDay,
            daysOfWeekMask = daysOfWeekMask,
            reminderEnabled = reminderEnabled,
            anchorCue = anchorCue.trim(),
            soundscape = soundscape,
            linkedJourneyId = existing?.linkedJourneyId,
            lifeChapterNotebookId = existing?.lifeChapterNotebookId,
            isArchived = false,
            sortOrder = existing?.sortOrder ?: dao.getActiveRoutines().size,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        val normalizedSteps = steps.ifEmpty {
            listOf(
                RoutineStep(
                    id = newId("step"),
                    routineId = id,
                    position = 0,
                    kind = RoutineStepKind.CHECK,
                    title = "Take a quiet pause",
                    minEffortTitle = "Take 3 deep breaths",
                    durationSeconds = 60,
                    minEffortDurationSeconds = 20
                )
            )
        }.mapIndexed { idx, step ->
            step.copy(
                id = step.id.ifBlank { newId("step") },
                routineId = id,
                position = idx
            )
        }
        dao.saveRoutineWithSteps(routine.toEntity(), normalizedSteps.map { it.toEntity() })
        routine.copy(steps = normalizedSteps)
    }

    suspend fun appendGuidedActivityToRoutine(routineId: String, activity: GuidedActivityDefinition): Boolean =
        withContext(Dispatchers.IO) {
            val routine = getRoutine(routineId) ?: return@withContext false
            val newStep = RoutineStep(
                id = newId("step"),
                routineId = routineId,
                position = routine.steps.size,
                kind = activity.stepKind,
                title = activity.title,
                minEffortTitle = "60-second ${activity.title.lowercase()}",
                durationSeconds = (activity.durationMinutes * 60).coerceAtLeast(60),
                minEffortDurationSeconds = 60,
                promptText = activity.reflectionPrompt.orEmpty(),
                guidedActivityId = activity.id,
                companionState = if (activity.stepKind == RoutineStepKind.BREATHING) "BREATHING" else "READING"
            )
            val updated = routine.copy(updatedAt = clock(), steps = routine.steps + newStep)
            dao.saveRoutineWithSteps(updated.toEntity(), updated.steps.map { it.toEntity() })
            true
        }

    suspend fun archiveRoutine(routineId: String) = withContext(Dispatchers.IO) {
        dao.setRoutineArchived(routineId, archived = true, updatedAt = clock())
    }

    // ------------------------------------------------------------------ Routine Executions & Resumability

    fun observeCompletionsForDay(epochDay: Long = todayEpochDay()): Flow<List<RoutineCompletion>> =
        dao.observeCompletionsForDay(epochDay).map { list -> list.map(RoutineCompletion::fromEntity) }.flowOn(Dispatchers.IO)

    fun observeCompletionsSince(fromEpochDay: Long): Flow<List<RoutineCompletion>> =
        dao.observeCompletionsSince(fromEpochDay).map { list -> list.map(RoutineCompletion::fromEntity) }.flowOn(Dispatchers.IO)

    fun observeInProgressCompletion(): Flow<RoutineCompletion?> =
        dao.observeInProgressCompletion().map { it?.let(RoutineCompletion::fromEntity) }.flowOn(Dispatchers.IO)

    suspend fun startOrResumeRoutine(
        routineId: String,
        minEffortMode: Boolean,
        epochDay: Long = todayEpochDay()
    ): RoutineCompletion = withContext(Dispatchers.IO) {
        val existing = dao.latestCompletionForDay(routineId, epochDay)
        if (existing != null && existing.status == RoutineCompletionStatus.IN_PROGRESS.name) {
            return@withContext RoutineCompletion.fromEntity(existing)
        }
        val started = RoutineCompletion(
            id = newId("rcomp"),
            routineId = routineId,
            epochDay = epochDay,
            status = RoutineCompletionStatus.IN_PROGRESS,
            currentStepIndex = 0,
            completedStepIds = emptyList(),
            usedMinEffortMode = minEffortMode,
            actualDurationSeconds = 0,
            startedAt = clock()
        )
        dao.upsertCompletion(started.toEntity())
        started
    }

    suspend fun saveRoutineProgress(completion: RoutineCompletion) = withContext(Dispatchers.IO) {
        dao.upsertCompletion(completion.toEntity())
    }

    suspend fun completeRoutine(
        completion: RoutineCompletion,
        reflectionNoteId: String? = completion.reflectionNoteId
    ): RoutineCompletion = withContext(Dispatchers.IO) {
        val finished = completion.copy(
            status = if (completion.usedMinEffortMode) RoutineCompletionStatus.COMPLETED_MIN_EFFORT else RoutineCompletionStatus.COMPLETED,
            reflectionNoteId = reflectionNoteId,
            completedAt = clock()
        )
        dao.upsertCompletion(finished.toEntity())
        finished
    }

    suspend fun skipRoutineForToday(routineId: String, epochDay: Long = todayEpochDay()) = withContext(Dispatchers.IO) {
        val now = clock()
        val skipped = RoutineCompletion(
            id = newId("rcomp"),
            routineId = routineId,
            epochDay = epochDay,
            status = RoutineCompletionStatus.SKIPPED,
            startedAt = now,
            completedAt = now
        )
        dao.upsertCompletion(skipped.toEntity())
    }

    // ------------------------------------------------------------------ Daily Plan (Sanctuary Today)

    fun observeDailyPlan(epochDay: Long = todayEpochDay()): Flow<PersonalDailyPlan> =
        dao.observeDailyPlan(epochDay).map { entity ->
            entity?.let(PersonalDailyPlan::fromEntity) ?: PersonalDailyPlan(epochDay = epochDay)
        }.flowOn(Dispatchers.IO)

    suspend fun getOrCreateDailyPlan(epochDay: Long = todayEpochDay()): PersonalDailyPlan = withContext(Dispatchers.IO) {
        dao.getDailyPlan(epochDay)?.let(PersonalDailyPlan::fromEntity)
            ?: PersonalDailyPlan(epochDay = epochDay, updatedAt = clock()).also { dao.upsertDailyPlan(it.toEntity()) }
    }

    suspend fun setTodayIntention(
        intentionText: String,
        epochDay: Long = todayEpochDay(),
        stitchIntoJournal: Boolean = true
    ): PersonalDailyPlan = withContext(Dispatchers.IO) {
        val trimmed = intentionText.trim()
        val existing = getOrCreateDailyPlan(epochDay)
        val noteAndBlock = if (stitchIntoJournal && trimmed.isNotBlank()) {
            stitchEntryIntoDayPage(
                epochDay = epochDay,
                sectionHeading = "Today's Intention",
                bodyText = trimmed,
                sectionKey = "morning_intention",
                blockType = NoteBlockType.QUOTE
            )
        } else null
        val updated = existing.copy(
            intentionText = trimmed,
            intentionNoteBlockId = noteAndBlock?.second ?: existing.intentionNoteBlockId,
            dayPageNoteId = noteAndBlock?.first ?: existing.dayPageNoteId,
            updatedAt = clock()
        )
        dao.upsertDailyPlan(updated.toEntity())
        updated
    }

    suspend fun setMinEffortDay(enabled: Boolean, epochDay: Long = todayEpochDay()): PersonalDailyPlan =
        withContext(Dispatchers.IO) {
            val updated = getOrCreateDailyPlan(epochDay).copy(isMinEffortDay = enabled, updatedAt = clock())
            dao.upsertDailyPlan(updated.toEntity())
            updated
        }

    suspend fun setMoodVibe(vibe: JournalMoodVibe?, epochDay: Long = todayEpochDay()): PersonalDailyPlan =
        withContext(Dispatchers.IO) {
            val plan = getOrCreateDailyPlan(epochDay).copy(moodVibe = vibe, updatedAt = clock())
            dao.upsertDailyPlan(plan.toEntity())
            plan.dayPageNoteId?.let { noteId ->
                noteRepository.getNote(noteId)?.let { note ->
                    val meta = if (vibe != null) note.metadata + (PersonalNoteMeta.MOOD_VIBE to vibe.name)
                    else note.metadata - PersonalNoteMeta.MOOD_VIBE
                    noteRepository.updateNote(note.copy(metadata = meta))
                }
            }
            plan
        }

    suspend fun dismissItemForToday(itemId: String, epochDay: Long = todayEpochDay()) = withContext(Dispatchers.IO) {
        val plan = getOrCreateDailyPlan(epochDay)
        dao.upsertDailyPlan(plan.copy(dismissedItemIds = plan.dismissedItemIds + itemId, updatedAt = clock()).toEntity())
    }

    // ------------------------------------------------------------------ Journeys & Challenges

    fun observeActiveEnrollments(): Flow<List<GrowthEnrollment>> =
        dao.observeActiveEnrollments().map { list -> list.map(GrowthEnrollment::fromEntity) }.flowOn(Dispatchers.IO)

    fun observeAllEnrollments(): Flow<List<GrowthEnrollment>> =
        dao.observeAllEnrollments().map { list -> list.map(GrowthEnrollment::fromEntity) }.flowOn(Dispatchers.IO)

    fun observeStepProgress(enrollmentId: String): Flow<List<GrowthStepProgress>> =
        dao.observeStepProgress(enrollmentId).map { list -> list.map(GrowthStepProgress::fromEntity) }.flowOn(Dispatchers.IO)

    suspend fun startJourney(
        journey: JourneyDefinition,
        userWhyText: String = "",
        epochDay: Long = todayEpochDay()
    ): GrowthEnrollment = withContext(Dispatchers.IO) {
        val existing = dao.findActiveByCatalogId(journey.id)
        if (existing != null) return@withContext GrowthEnrollment.fromEntity(existing)
        val now = clock()
        val enrollmentId = newId("enroll")
        val whyNoteId = if (userWhyText.isNotBlank()) {
            val created = noteRepository.createNote(
                workflow = RecordingType.JOURNAL,
                title = "Why I started: ${journey.title}",
                isPrivate = true,
                metadata = mapOf(
                    PersonalNoteMeta.SOURCE_ENROLLMENT_ID to enrollmentId,
                    PersonalNoteMeta.DAY_PAGE_EPOCH_DAY to epochDay.toString()
                ),
                initialBlocks = listOf(
                    NoteBlock(
                        id = NoteRepository.newId("block"),
                        noteId = "",
                        position = 0,
                        type = NoteBlockType.QUOTE,
                        content = RichText.plain(journey.whyPrompt),
                        source = BlockSource.USER
                    ),
                    NoteBlock(
                        id = NoteRepository.newId("block"),
                        noteId = "",
                        position = 1,
                        type = NoteBlockType.PARAGRAPH,
                        content = RichText.plain(userWhyText.trim()),
                        source = BlockSource.USER
                    )
                ),
                useTemplate = false
            )
            created.id
        } else null

        val enrollment = GrowthEnrollment(
            id = enrollmentId,
            kind = GrowthEnrollmentKind.JOURNEY,
            catalogId = journey.id,
            title = journey.title,
            status = GrowthEnrollmentStatus.ACTIVE,
            startedEpochDay = epochDay,
            currentStepOrDay = 0,
            targetStepsOrDays = journey.steps.size.coerceAtLeast(1),
            anchorWhyNoteId = whyNoteId,
            catalogVersion = journey.version,
            lastActivityAt = now
        )
        dao.upsertEnrollment(enrollment.toEntity())
        enrollment
    }

    suspend fun startChallenge(
        challenge: ChallengeDefinition,
        epochDay: Long = todayEpochDay()
    ): GrowthEnrollment = withContext(Dispatchers.IO) {
        val existing = dao.findActiveByCatalogId(challenge.id)
        if (existing != null) return@withContext GrowthEnrollment.fromEntity(existing)
        val now = clock()
        val enrollment = GrowthEnrollment(
            id = newId("enroll"),
            kind = GrowthEnrollmentKind.CHALLENGE,
            catalogId = challenge.id,
            title = challenge.title,
            status = GrowthEnrollmentStatus.ACTIVE,
            startedEpochDay = epochDay,
            currentStepOrDay = 0,
            targetStepsOrDays = challenge.durationDays.coerceAtLeast(1),
            catalogVersion = PersonalGrowthCatalog.CATALOG_VERSION,
            lastActivityAt = now
        )
        dao.upsertEnrollment(enrollment.toEntity())
        enrollment
    }

    /** Creates a custom user-defined or YouTube-playbook-derived Challenge. */
    suspend fun startCustomChallenge(
        title: String,
        durationDays: Int,
        config: CustomChallengeConfig,
        epochDay: Long = todayEpochDay()
    ): GrowthEnrollment = withContext(Dispatchers.IO) {
        val now = clock()
        val id = newId("enroll")
        val enrollment = GrowthEnrollment(
            id = id,
            kind = GrowthEnrollmentKind.CHALLENGE,
            catalogId = "custom:$id",
            title = title.trim().ifBlank { "My Personal Challenge" },
            customConfigJson = config.toJson(),
            status = GrowthEnrollmentStatus.ACTIVE,
            startedEpochDay = epochDay,
            currentStepOrDay = 0,
            targetStepsOrDays = durationDays.coerceIn(3, 60),
            catalogVersion = PersonalGrowthCatalog.CATALOG_VERSION,
            lastActivityAt = now
        )
        dao.upsertEnrollment(enrollment.toEntity())
        enrollment
    }

    /** Turns any imported `MOTIVATION` or `LIFE_SKILLS` playbook note into a grounded 7-day Challenge. */
    suspend fun startChallengeFromPlaybook(
        playbook: Note,
        durationDays: Int = 7,
        epochDay: Long = todayEpochDay()
    ): GrowthEnrollment {
        val (title, config) = PersonalGrowthIntelligence.deriveChallengeFromPlaybook(playbook, durationDays)
        return startCustomChallenge(title, durationDays, config, epochDay)
    }

    /** Re-runs a completed Journey or Challenge as a fresh 7-day refresher sprint. */
    suspend fun restartAsSevenDayRefresher(
        completedEnrollment: GrowthEnrollment,
        epochDay: Long = todayEpochDay()
    ): GrowthEnrollment {
        val journey = PersonalGrowthCatalog.journeyById(completedEnrollment.catalogId)
        val challenge = PersonalGrowthCatalog.challengeById(completedEnrollment.catalogId)
        val config = CustomChallengeConfig(
            dailyAction = challenge?.dailyAction ?: journey?.tagline ?: "Practice one core step from ${completedEnrollment.title}",
            minEffortAction = challenge?.minEffortAction ?: "Take 2 minutes to revisit this habit gently",
            reflectionPrompt = challenge?.reflectionPrompt ?: "What came back easily today?"
        )
        return startCustomChallenge(
            title = "7-Day Refresher: ${completedEnrollment.title}",
            durationDays = 7,
            config = config,
            epochDay = epochDay
        )
    }

    // ------------------------------------------------------------------ Life Chapters (reuses NotebookEntity)

    fun observeLifeChapters(): Flow<List<LifeChapter>> =
        combine(
            database.notebookDao().observeActive(),
            database.notebookDao().observeNoteCounts()
        ) { notebooks, counts ->
            val countById = counts.associate { it.notebookId to it.count }
            notebooks
                .filter { it.space == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL.name && it.kind == com.craftflowtechnologies.meetingmind.core.model.NotebookKind.LIFE_CHAPTER }
                .map { nb -> LifeChapter.fromNotebook(nb, countById[nb.id] ?: 0) }
        }.flowOn(Dispatchers.IO)

    suspend fun createLifeChapter(title: String, subtitle: String = ""): LifeChapter = withContext(Dispatchers.IO) {
        val now = clock()
        val id = newId("chapter")
        val existing = database.notebookDao().getBySpace(com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL.name)
        val entity = LifeChapter.toNotebookEntity(
            id = id,
            title = title,
            subtitle = subtitle,
            nowMs = now,
            sortOrder = existing.size
        )
        database.notebookDao().upsert(entity)
        LifeChapter.fromNotebook(entity, noteCount = 0)
    }

    /** Graduates an Idea Spark into a dedicated Life Chapter and moves the genesis note inside it. */
    suspend fun graduateIdeaToLifeChapter(ideaNoteId: String): LifeChapter? = withContext(Dispatchers.IO) {
        val note = noteRepository.getNote(ideaNoteId) ?: return@withContext null
        val chapter = createLifeChapter(
            title = note.title.ifBlank { "New Chapter" },
            subtitle = "Born from an Idea Studio spark"
        )
        noteRepository.moveToNotebook(note.id, chapter.id)
        advanceIdeaStage(note.id, IdeaStage.BUILDING)
        chapter
    }

    /** Links two collided ideas into a bi-directional Story Thread. */
    suspend fun connectIdeasIntoThread(firstNoteId: String, secondNoteId: String) = withContext(Dispatchers.IO) {
        noteRepository.link(firstNoteId, secondNoteId, NoteLinkKind.STORY_THREAD)
    }

    /** Returns all canonical note-ID pairs already linked via [NoteLinkKind.STORY_THREAD]. */
    suspend fun getStoryThreadLinkedPairs(): Set<Pair<String, String>> = withContext(Dispatchers.IO) {
        noteRepository.getAllLinks()
            .filter { it.kind == NoteLinkKind.STORY_THREAD }
            .map { PersonalGrowthIntelligence.canonicalPair(it.fromNoteId, it.toNoteId) }
            .toSet()
    }

    fun observeNotesInChapter(chapterId: String): Flow<List<Note>> =
        noteRepository.observeNotesInNotebook(chapterId)

    suspend fun assignNoteToChapter(noteId: String, chapterId: String?) = withContext(Dispatchers.IO) {
        noteRepository.moveToNotebook(noteId, chapterId)
    }

    suspend fun archiveLifeChapter(chapterId: String) = withContext(Dispatchers.IO) {
        val nb = database.notebookDao().getById(chapterId) ?: return@withContext
        database.notebookDao().upsert(nb.copy(archivedAt = clock(), updatedAt = clock()))
    }

    suspend fun updateEnrollmentStatus(enrollmentId: String, status: GrowthEnrollmentStatus): GrowthEnrollment? =
        withContext(Dispatchers.IO) {
            val current = dao.getEnrollmentById(enrollmentId)?.let(GrowthEnrollment::fromEntity) ?: return@withContext null
            val now = clock()
            val updated = current.copy(
                status = status,
                lastActivityAt = now,
                completedAt = if (status == GrowthEnrollmentStatus.COMPLETED) now else current.completedAt
            )
            dao.upsertEnrollment(updated.toEntity())
            updated
        }

    suspend fun getStepProgress(enrollmentId: String): List<GrowthStepProgress> = withContext(Dispatchers.IO) {
        dao.getStepProgress(enrollmentId).map(GrowthStepProgress::fromEntity)
    }

    suspend fun getNote(noteId: String): Note? = noteRepository.getNote(noteId)

    /** Picks a meaningful past journal entry (older than today, non-capsule) to surface in a MEMORY routine step. */
    suspend fun getRecentMemoryForRoutine(): Note? = withContext(Dispatchers.IO) {
        val todayStart = todayEpochDay()
        observeJournalNotes().first()
            .filter { it.metadata[PersonalNoteMeta.CAPSULE_UNLOCK_AT_MS].isNullOrBlank() }
            .firstOrNull { note ->
                epochDayOf(note.createdAt) < todayStart && note.plainText.isNotBlank()
            }
    }

    suspend fun completeNextEnrollmentStep(
        enrollmentId: String,
        usedMinEffort: Boolean = false,
        reflectionText: String = "",
        epochDay: Long = todayEpochDay()
    ): GrowthEnrollment? = withContext(Dispatchers.IO) {
        val current = dao.getEnrollmentById(enrollmentId)?.let(GrowthEnrollment::fromEntity) ?: return@withContext null
        val stepIndex = current.currentStepOrDay.coerceAtMost((current.targetStepsOrDays - 1).coerceAtLeast(0))
        val now = clock()
        val stitched = if (reflectionText.isNotBlank()) {
            stitchEntryIntoDayPage(
                epochDay = epochDay,
                sectionHeading = "${current.title} · Day ${stepIndex + 1}",
                bodyText = reflectionText.trim(),
                sectionKey = "growth_${current.id}_$stepIndex"
            )
        } else null

        val progress = GrowthStepProgress(
            id = newId("gprog"),
            enrollmentId = current.id,
            stepIndex = stepIndex,
            completedEpochDay = epochDay,
            usedMinEffort = usedMinEffort,
            reflectionNoteId = stitched?.first,
            reflectionBlockId = stitched?.second,
            completedAt = now
        )
        dao.upsertStepProgress(progress.toEntity())

        val nextCursor = current.currentStepOrDay + 1
        val finished = nextCursor >= current.targetStepsOrDays
        val finaleNoteId = if (finished && current.finaleNoteId == null) {
            buildGroundedFinaleNote(current, epochDay)
        } else current.finaleNoteId

        val updated = current.copy(
            currentStepOrDay = nextCursor.coerceAtMost(current.targetStepsOrDays),
            status = if (finished) GrowthEnrollmentStatus.COMPLETED else current.status,
            finaleNoteId = finaleNoteId,
            lastActivityAt = now,
            completedAt = if (finished) now else current.completedAt
        )
        dao.upsertEnrollment(updated.toEntity())
        updated
    }

    /**
     * Compiles a permanent, private Anthology Note from the user's actual Day-1 "Why" Anchor Note
     * and verbatim step reflections when a Journey or Challenge completes. Never invents words.
     */
    private suspend fun buildGroundedFinaleNote(enrollment: GrowthEnrollment, epochDay: Long): String {
        val blocks = mutableListOf<NoteBlock>()
        blocks += NoteBlock(
            id = NoteRepository.newId("block"),
            noteId = "",
            position = 0,
            type = NoteBlockType.HEADING_2,
            content = RichText.plain("What You Completed"),
            source = BlockSource.USER
        )
        blocks += NoteBlock(
            id = NoteRepository.newId("block"),
            noteId = "",
            position = 1,
            type = NoteBlockType.PARAGRAPH,
            content = RichText.plain("${enrollment.title} · ${enrollment.targetStepsOrDays} steps completed."),
            source = BlockSource.USER
        )

        enrollment.anchorWhyNoteId?.let { whyId ->
            noteRepository.getNote(whyId)?.plainText?.takeIf { it.isNotBlank() }?.let { whyText ->
                blocks += NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = blocks.size,
                    type = NoteBlockType.HEADING_2,
                    content = RichText.plain("Your Day 1 Why"),
                    source = BlockSource.USER
                )
                blocks += NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = blocks.size,
                    type = NoteBlockType.QUOTE,
                    content = RichText.plain(whyText.trim()),
                    source = BlockSource.USER
                )
            }
        }

        val steps = dao.getStepProgress(enrollment.id)
        val reflectionLines = steps.mapNotNull { st ->
            val nId = st.reflectionNoteId ?: return@mapNotNull null
            val bId = st.reflectionBlockId ?: return@mapNotNull null
            val doc = noteRepository.getDocument(nId) ?: return@mapNotNull null
            val text = doc.blocks.firstOrNull { it.id == bId }?.content?.text?.trim()
            if (text.isNullOrBlank()) null else "Day ${st.stepIndex + 1}: $text"
        }
        if (reflectionLines.isNotEmpty()) {
            blocks += NoteBlock(
                id = NoteRepository.newId("block"),
                noteId = "",
                position = blocks.size,
                type = NoteBlockType.HEADING_2,
                content = RichText.plain("Reflections Along the Way (Your Words)"),
                source = BlockSource.USER
            )
            reflectionLines.forEach { line ->
                blocks += NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = blocks.size,
                    type = NoteBlockType.BULLET,
                    content = RichText.plain(line),
                    source = BlockSource.USER
                )
            }
        }

        val created = noteRepository.createNote(
            workflow = RecordingType.JOURNAL,
            title = "Completed: ${enrollment.title}",
            notebookId = enrollment.lifeChapterNotebookId,
            isPrivate = true,
            metadata = mapOf(
                PersonalNoteMeta.SOURCE_ENROLLMENT_ID to enrollment.id,
                PersonalNoteMeta.DAY_PAGE_EPOCH_DAY to epochDay.toString()
            ),
            initialBlocks = blocks,
            useTemplate = false
        )
        return created.id
    }

    /**
     * "Reply to your past self" (Memory Vault & On This Day): creates a private journal entry
     * bridged to a past memory or unlocked Time Capsule via [NoteLinkKind.STORY_THREAD].
     */
    suspend fun replyToPastMemory(pastNoteId: String, replyText: String): Note? = withContext(Dispatchers.IO) {
        val past = noteRepository.getNote(pastNoteId) ?: return@withContext null
        val pastTitle = past.title.ifBlank { "Past memory" }
        val reply = noteRepository.createNote(
            workflow = RecordingType.JOURNAL,
            title = "Reply to: $pastTitle",
            isPrivate = true,
            metadata = mapOf(
                PersonalNoteMeta.SOURCE_NOTE_TITLE to pastTitle,
                PersonalNoteMeta.DAY_PAGE_EPOCH_DAY to todayEpochDay().toString()
            ),
            initialBlocks = listOf(
                NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = 0,
                    type = NoteBlockType.QUOTE,
                    content = RichText.plain("Looking back at \"$pastTitle\""),
                    source = BlockSource.USER
                ),
                NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = 1,
                    type = NoteBlockType.PARAGRAPH,
                    content = RichText.plain(replyText.trim()),
                    source = BlockSource.USER
                )
            ),
            useTemplate = false
        )
        noteRepository.link(reply.id, past.id, NoteLinkKind.STORY_THREAD)
        reply
    }

    // ------------------------------------------------------------------ Inner Circle People & Personal Tasks

    /** Observes private Personal relationships (family, close friends, mentors) stored in `people` with space = PERSONAL. */
    fun observeInnerCirclePeople(): Flow<List<com.craftflowtechnologies.meetingmind.core.database.PersonEntity>> =
        database.peopleDao()
            .observeBySpace(com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL.name)
            .flowOn(Dispatchers.IO)

    suspend fun addInnerCirclePerson(name: String, relationship: String, note: String = ""): com.craftflowtechnologies.meetingmind.core.database.PersonEntity =
        withContext(Dispatchers.IO) {
            val now = clock()
            val entity = com.craftflowtechnologies.meetingmind.core.database.PersonEntity(
                id = newId("person"),
                name = name.trim(),
                relationship = relationship.trim().ifBlank { null },
                notes = note.trim(),
                createdAt = now,
                updatedAt = now,
                space = com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL.name
            )
            database.peopleDao().upsert(entity)
            entity
        }

    /** Finds recent journal/voice-memo lines mentioning [personName] ("Gift & Mention Radar"). */
    fun findMentionsForPerson(personName: String, notes: List<Note>, limit: Int = 5): List<Pair<Note, String>> {
        val needle = personName.trim().lowercase()
        if (needle.length < 2) return emptyList()
        val out = mutableListOf<Pair<Note, String>>()
        for (n in notes) {
            val hitLine = n.plainText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.lowercase().contains(needle) && it.length >= needle.length + 4 }
            if (hitLine != null) {
                out += n to hitLine.take(160)
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** Computes a live, truthful 1-line Personal glance string for Today Home's `SpacesRow`. */
    suspend fun computeLiveSpaceGlance(nowMs: Long = clock()): String = withContext(Dispatchers.IO) {
        val today = todayEpochDay(nowMs)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val window = RoutineTimeWindow.forHourOfDay(cal.get(Calendar.HOUR_OF_DAY))
        val routines = dao.getActiveRoutines().map { PersonalRoutine.fromEntity(it) }
        val doneIds = dao.observeCompletionsForDay(today).first().filter { RoutineCompletionStatus.fromNameOrDefault(it.status).isDone }.map { it.routineId }.toSet()
        val nextRoutine = routines.firstOrNull { it.timeWindow == window && it.id !in doneIds }
            ?: routines.firstOrNull { it.id !in doneIds }
        val activeJourney = dao.observeActiveEnrollments().first().firstOrNull { it.kind == GrowthEnrollmentKind.JOURNEY.name }
        val parts = buildList {
            if (nextRoutine != null) add("${nextRoutine.title} ready")
            if (activeJourney != null) add("Day ${activeJourney.currentStepOrDay + 1} of ${activeJourney.title}")
        }
        parts.joinToString(" · ").ifBlank { "Routines, journal and growth" }
    }

    // ------------------------------------------------------------------ Living Journal & Day-Page Stitching

    /**
     * Observes every Personal journal & reflection note (newest first), excluding soft-deleted notes.
     */
    fun observeJournalNotes(): Flow<List<Note>> =
        noteRepository.observeNotesForWorkflows(
            listOf(RecordingType.JOURNAL, RecordingType.REFLECTION, RecordingType.GRATITUDE)
        )

    /** Observes Personal Spark Vault notes (Ideas, Brainstorms, Voice Memos). */
    fun observeIdeaNotes(): Flow<List<Note>> =
        noteRepository.observeNotesForWorkflows(
            listOf(RecordingType.IDEA, RecordingType.BRAINSTORM, RecordingType.VOICE_MEMO)
        )

    /** Observes imported Motivation & Life Skills playbooks. */
    fun observeGrowthPlaybooks(): Flow<List<Note>> =
        noteRepository.observeNotesForWorkflows(
            listOf(RecordingType.MOTIVATION, RecordingType.LIFE_SKILLS)
        )

    /**
     * Finds or creates Today's cohesive Day-Page Journal note and stitches a timestamped section
     * (heading + body block) into it so Morning Intentions, Routine Reflections, and Evening
     * check-ins live together as one editorial daily document.
     *
     * Returns (noteId, bodyBlockId).
     */
    suspend fun stitchEntryIntoDayPage(
        epochDay: Long = todayEpochDay(),
        sectionHeading: String,
        bodyText: String,
        sectionKey: String? = null,
        blockType: NoteBlockType = NoteBlockType.PARAGRAPH,
        sourceRoutineId: String? = null
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val dateIso = formatIsoDate(epochDay)
        val prettyTitle = formatJournalDayTitle(epochDay)
        val timeStamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(clock()))
        val plan = dao.getDailyPlan(epochDay)?.let(PersonalDailyPlan::fromEntity)
        val existingId = plan?.dayPageNoteId?.takeIf { noteRepository.getNote(it)?.deletedAt == null }
            ?: findExistingDayPageNoteId(dateIso)

        val bodyBlockId = NoteRepository.newId("block")
        val headingBlock = NoteBlock(
            id = NoteRepository.newId("block"),
            noteId = existingId.orEmpty(),
            position = 0,
            type = NoteBlockType.HEADING_2,
            content = RichText.plain("$sectionHeading · $timeStamp"),
            source = BlockSource.USER,
            sectionKey = sectionKey
        )
        val bodyBlock = NoteBlock(
            id = bodyBlockId,
            noteId = existingId.orEmpty(),
            position = 1,
            type = blockType,
            content = RichText.plain(bodyText.trim()),
            source = BlockSource.USER,
            sectionKey = sectionKey
        )

        val noteId = if (existingId != null) {
            val doc = noteRepository.getDocument(existingId)
            val currentBlocks = doc?.blocks.orEmpty().filterNot {
                it.type == NoteBlockType.PARAGRAPH && it.content.text.isBlank()
            }
            // Replace same sectionKey (e.g. updating Today's Intention) instead of duplicating it.
            val cleaned = if (sectionKey != null) currentBlocks.filterNot { it.sectionKey == sectionKey } else currentBlocks
            val nextBlocks = cleaned + listOf(
                headingBlock.copy(noteId = existingId),
                bodyBlock.copy(noteId = existingId)
            )
            noteRepository.saveBlocks(existingId, nextBlocks)
            existingId
        } else {
            val meta = buildMap {
                put(PersonalNoteMeta.DAY_PAGE_DATE, dateIso)
                put(PersonalNoteMeta.DAY_PAGE_EPOCH_DAY, epochDay.toString())
                sourceRoutineId?.let { put(PersonalNoteMeta.SOURCE_ROUTINE_ID, it) }
                plan?.moodVibe?.let { put(PersonalNoteMeta.MOOD_VIBE, it.name) }
            }
            val created = noteRepository.createNote(
                workflow = RecordingType.JOURNAL,
                title = prettyTitle,
                isPrivate = true,
                eventDate = clock(),
                metadata = meta,
                initialBlocks = listOf(headingBlock, bodyBlock),
                useTemplate = false
            )
            created.id
        }

        val updatedPlan = (plan ?: PersonalDailyPlan(epochDay = epochDay)).copy(
            dayPageNoteId = noteId,
            updatedAt = clock()
        )
        dao.upsertDailyPlan(updatedPlan.toEntity())
        noteId to bodyBlockId
    }

    /**
     * Private Margin Bridge: opens or creates a private Personal Journal reflection linked
     * bi-directionally to any Work, Learning, Faith, or YouTube note via [NoteLinkKind.PERSONAL_REFLECTION].
     */
    suspend fun createPrivateReflectionForNote(sourceNoteId: String): Note? = withContext(Dispatchers.IO) {
        val source = noteRepository.getNote(sourceNoteId) ?: return@withContext null
        val sourceTitle = source.title.ifBlank { source.workflow.displayName }
        val reflection = noteRepository.createNote(
            workflow = RecordingType.JOURNAL,
            title = "Reflection: $sourceTitle",
            isPrivate = true,
            metadata = mapOf(
                PersonalNoteMeta.SOURCE_NOTE_TITLE to sourceTitle,
                PersonalNoteMeta.DAY_PAGE_EPOCH_DAY to todayEpochDay().toString()
            ),
            initialBlocks = listOf(
                NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = 0,
                    type = NoteBlockType.QUOTE,
                    content = RichText.plain("Private reflection on \"$sourceTitle\""),
                    source = BlockSource.USER
                ),
                NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = 1,
                    type = NoteBlockType.PARAGRAPH,
                    payload = mapOf(NoteBlock.PAYLOAD_HINT to "What stood out to you personally, and how do you want to respond?"),
                    source = BlockSource.USER
                )
            ),
            useTemplate = false
        )
        noteRepository.link(reflection.id, source.id, NoteLinkKind.PERSONAL_REFLECTION)
        reflection
    }

    /**
     * Quick Spark capture: saves an IDEA note directly into the Idea Studio Spark Vault.
     */
    suspend fun captureQuickSpark(title: String, body: String): Note = withContext(Dispatchers.IO) {
        noteRepository.createNote(
            workflow = RecordingType.IDEA,
            title = title.trim().ifBlank { "New spark" },
            isPrivate = true,
            metadata = mapOf(PersonalNoteMeta.IDEA_STAGE to IdeaStage.SPARK.name),
            initialBlocks = listOf(
                NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = 0,
                    type = NoteBlockType.PARAGRAPH,
                    content = RichText.plain(body.trim()),
                    source = BlockSource.USER
                )
            ),
            useTemplate = false
        )
    }

    /**
     * Seals a Time Capsule note until [unlockAtMs] (Memory Vault).
     */
    suspend fun sealTimeCapsule(title: String, body: String, unlockAtMs: Long, label: String): Note =
        withContext(Dispatchers.IO) {
            noteRepository.createNote(
                workflow = RecordingType.JOURNAL,
                title = title.trim().ifBlank { "Letter to my future self" },
                isPrivate = true,
                metadata = mapOf(
                    PersonalNoteMeta.CAPSULE_UNLOCK_AT_MS to unlockAtMs.toString(),
                    PersonalNoteMeta.CAPSULE_LABEL to label.trim()
                ),
                initialBlocks = listOf(
                    NoteBlock(
                        id = NoteRepository.newId("block"),
                        noteId = "",
                        position = 0,
                        type = NoteBlockType.PARAGRAPH,
                        content = RichText.plain(body.trim()),
                        source = BlockSource.USER
                    )
                ),
                useTemplate = false
            )
        }

    suspend fun advanceIdeaStage(noteId: String, nextStage: IdeaStage) = withContext(Dispatchers.IO) {
        val note = noteRepository.getNote(noteId) ?: return@withContext
        noteRepository.updateNote(note.copy(metadata = note.metadata + (PersonalNoteMeta.IDEA_STAGE to nextStage.name)))
    }

    private suspend fun findExistingDayPageNoteId(dateIso: String): String? {
        return observeJournalNotes().first()
            .firstOrNull { it.metadata[PersonalNoteMeta.DAY_PAGE_DATE] == dateIso }
            ?.id
    }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L

        fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        fun epochDayOf(epochMs: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
            val offset = cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)
            return Math.floorDiv(epochMs + offset, MILLIS_PER_DAY)
        }

        fun formatIsoDate(epochDay: Long): String {
            val cal = Calendar.getInstance().apply {
                clear()
                set(1970, Calendar.JANUARY, 1)
                add(Calendar.DAY_OF_YEAR, epochDay.toInt())
            }
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }

        fun formatJournalDayTitle(epochDay: Long): String {
            val cal = Calendar.getInstance().apply {
                clear()
                set(1970, Calendar.JANUARY, 1)
                add(Calendar.DAY_OF_YEAR, epochDay.toInt())
            }
            return SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(cal.time)
        }
    }
}
