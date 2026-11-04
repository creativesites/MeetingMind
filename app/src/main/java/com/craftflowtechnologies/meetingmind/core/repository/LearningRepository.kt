package com.craftflowtechnologies.meetingmind.core.repository

import android.content.Context
import androidx.room.withTransaction
import com.craftflowtechnologies.meetingmind.core.database.ActivityAttemptEntity
import com.craftflowtechnologies.meetingmind.core.database.LearningActivityEntity
import com.craftflowtechnologies.meetingmind.core.database.LearningConceptEntity
import com.craftflowtechnologies.meetingmind.core.database.LearningGoalEntity
import com.craftflowtechnologies.meetingmind.core.database.LearningSessionEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.database.ReviewScheduleEntity
import com.craftflowtechnologies.meetingmind.core.learning.SpokenNotationNormalizer
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.CourseProperties
import com.craftflowtechnologies.meetingmind.core.model.DeterministicSpacedScheduler
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.ExamPriorityItem
import com.craftflowtechnologies.meetingmind.core.model.ExamPriorityRanker
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.MasteryCalculator
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.core.repository.NoteCodec.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Substrate-native repository for the Learning vertical.
 * Coordinates courses ([NotebookSpace.LEARNING]), sessions, concepts, active-recall attempts,
 * deterministic spaced schedules, transparent mastery evaluations, daily companion briefs,
 * and exam readiness artifacts.
 */
class LearningRepository(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    constructor(
        context: Context,
        clock: () -> Long = System::currentTimeMillis
    ) : this(MeetMindDatabase.getInstance(context), clock)

    private val notebookDao = database.notebookDao()
    private val noteDao = database.noteDao()
    private val sessionDao = database.learningSessionDao()
    private val conceptDao = database.learningConceptDao()
    private val activityDao = database.learningActivityDao()
    private val attemptDao = database.activityAttemptDao()
    private val scheduleDao = database.reviewScheduleDao()
    private val goalDao = database.learningGoalDao()

    data class LearningCourse(
        val notebook: Notebook,
        val properties: CourseProperties,
        val noteCount: Int = 0,
        val sessionCount: Int = 0
    )

    data class LearningSessionDetail(
        val session: LearningSession,
        val concepts: List<LearningConcept>,
        val activities: List<LearningActivity>,
        val attempts: List<ActivityAttempt>,
        val schedules: List<ReviewSchedule>
    )

    data class AttemptOutcome(
        val attempt: ActivityAttempt,
        val schedule: ReviewSchedule,
        val masteryEvaluation: MasteryCalculator.MasteryEvaluation?
    )

    data class DueReviewItem(
        val schedule: ReviewSchedule,
        val activity: LearningActivity,
        val concept: LearningConcept?,
        val session: LearningSession
    )

    data class UpcomingExamCountdown(
        val goal: LearningGoal,
        val daysRemaining: Int
    )

    data class DailyCompanionBrief(
        val recommendedMinutes: Int,
        val dueReviews: List<DueReviewItem>,
        val weakHighYieldConcepts: List<LearningConcept>,
        val unresolvedConfusionCount: Int,
        val upcomingExams: List<UpcomingExamCountdown>,
        val sevenDayActivity: List<Boolean>,
        val sevenDayAttemptCounts: List<Int>
    )

    // ---------------------------------------------------------------- courses

    fun observeCourses(): Flow<List<LearningCourse>> = combine(
        notebookDao.observeActive(),
        notebookDao.observeNoteCounts(),
        sessionDao.observeAll()
    ) { notebooks, noteCounts, sessions ->
        val countsById = noteCounts.associate { it.notebookId to it.count }
        notebooks
            .filter { it.space == NotebookSpace.LEARNING.name }
            .map { entity ->
                val nb = entity.toDomain()
                val props = CourseProperties.fromJson(nb.propertiesJson)
                val matchingSessions = sessions.count { s ->
                    s.courseName != null && (
                        s.courseName.equals(nb.name, ignoreCase = true) ||
                            (props.courseCode.isNotBlank() && s.courseName.equals(props.courseCode, ignoreCase = true))
                        )
                }
                LearningCourse(
                    notebook = nb,
                    properties = props,
                    noteCount = countsById[nb.id] ?: 0,
                    sessionCount = matchingSessions
                )
            }
    }.flowOn(Dispatchers.IO)

    fun getCourseProperties(notebook: Notebook): CourseProperties =
        CourseProperties.fromJson(notebook.propertiesJson)

    suspend fun createOrUpdateCourse(
        id: String? = null,
        name: String,
        properties: CourseProperties = CourseProperties(),
        colorHex: String? = null,
        icon: String? = null
    ): LearningCourse = withContext(Dispatchers.IO) {
        val now = clock()
        val existing = id?.let { notebookDao.getById(it) }
        val resolvedId = existing?.id ?: id ?: "course_${UUID.randomUUID()}"
        val mergedJson = properties.toJson(existing?.propertiesJson)
        val entity = NotebookEntity(
            id = resolvedId,
            name = name.trim(),
            space = NotebookSpace.LEARNING.name,
            colorHex = colorHex ?: existing?.colorHex,
            icon = icon ?: existing?.icon,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            archivedAt = existing?.archivedAt,
            sortOrder = existing?.sortOrder ?: 0,
            deletedAt = existing?.deletedAt,
            kind = existing?.kind ?: "NOTEBOOK",
            propertiesJson = mergedJson
        )
        notebookDao.upsert(entity)
        val domainNotebook = entity.toDomain()
        LearningCourse(
            notebook = domainNotebook,
            properties = CourseProperties.fromJson(domainNotebook.propertiesJson)
        )
    }

    // ---------------------------------------------------------------- sessions

    fun observeSessions(): Flow<List<LearningSession>> =
        sessionDao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    fun observeSessionByNoteId(noteId: String): Flow<LearningSession?> =
        sessionDao.observeByNoteId(noteId)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.IO)

    fun observeSessionDetail(sessionId: String): Flow<LearningSessionDetail?> = combine(
        sessionDao.observeById(sessionId),
        conceptDao.observeBySession(sessionId),
        activityDao.observeBySession(sessionId),
        attemptDao.observeBySession(sessionId),
        scheduleDao.observeBySession(sessionId)
    ) { session, concepts, activities, attempts, schedules ->
        session?.let {
            LearningSessionDetail(
                session = it.toDomain(),
                concepts = concepts.map { c -> c.toDomain() },
                activities = activities.map { a -> a.toDomain() },
                attempts = attempts.map { att -> att.toDomain() },
                schedules = schedules.map { sch -> sch.toDomain() }
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getSessionDetail(sessionId: String): LearningSessionDetail? = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId)?.toDomain() ?: return@withContext null
        LearningSessionDetail(
            session = session,
            concepts = conceptDao.getBySession(sessionId).map { it.toDomain() },
            activities = activityDao.getBySession(sessionId).map { it.toDomain() },
            attempts = attemptDao.getBySession(sessionId).map { it.toDomain() },
            schedules = scheduleDao.getBySession(sessionId).map { it.toDomain() }
        )
    }

    suspend fun ensureSessionForNote(
        noteId: String,
        meetingId: String? = null,
        title: String = "",
        courseName: String? = null,
        domain: SubjectDomain? = null
    ): LearningSession = withContext(Dispatchers.IO) {
        val now = clock()
        val existing = sessionDao.getByNoteId(noteId)
        if (existing != null) {
            val updatedTitle = if (title.isNotBlank() && existing.title.isBlank()) title.trim() else existing.title
            val updatedMeetingId = meetingId ?: existing.meetingId
            val updatedCourse = courseName?.takeIf { it.isNotBlank() } ?: existing.courseName
            val updatedDomain = if (domain != null && existing.subjectDomain == SubjectDomain.GENERAL.name) {
                domain.name
            } else {
                existing.subjectDomain
            }
            val changed = updatedTitle != existing.title ||
                updatedMeetingId != existing.meetingId ||
                updatedCourse != existing.courseName ||
                updatedDomain != existing.subjectDomain
            return@withContext if (changed) {
                val updated = existing.copy(
                    title = updatedTitle,
                    meetingId = updatedMeetingId,
                    courseName = updatedCourse,
                    subjectDomain = updatedDomain,
                    updatedAt = now
                )
                sessionDao.upsert(updated)
                updated.toDomain()
            } else {
                existing.toDomain()
            }
        }

        val note = noteDao.getById(noteId)
        val courseNotebook = note?.notebookId?.let { nbId ->
            notebookDao.getById(nbId)?.takeIf { it.space == NotebookSpace.LEARNING.name }
        }
        val courseProps = courseNotebook?.let { CourseProperties.fromJson(it.propertiesJson) }
        val resolvedTitle = title.ifBlank { note?.title.orEmpty() }.ifBlank { "Untitled Study Session" }.trim()
        val resolvedCourse = courseName?.takeIf { it.isNotBlank() } ?: courseNotebook?.name
        val resolvedDomain = domain
            ?: courseProps?.domain?.takeIf { it != SubjectDomain.GENERAL }
            ?: SubjectDomain.detect(resolvedTitle, note?.plainText.orEmpty())

        val created = LearningSessionEntity(
            id = "ls_${UUID.randomUUID()}",
            noteId = noteId,
            meetingId = meetingId,
            title = resolvedTitle,
            courseName = resolvedCourse,
            status = "ACTIVE",
            lastStudiedAt = null,
            createdAt = now,
            updatedAt = now,
            subjectDomain = resolvedDomain.name,
            examSignalCount = 0,
            unresolvedConfusionCount = 0
        )
        sessionDao.upsert(created)
        created.toDomain()
    }

    suspend fun toggleSessionPause(sessionId: String): LearningSession? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existing = sessionDao.getById(sessionId) ?: return@withTransaction null
            val now = clock()
            val nextStatus = if (existing.status == "PAUSED") "ACTIVE" else "PAUSED"
            val updated = existing.copy(status = nextStatus, updatedAt = now)
            sessionDao.upsert(updated)
            scheduleDao.setPausedBySession(
                sessionId = sessionId,
                isPaused = nextStatus == "PAUSED",
                updatedAt = now
            )
            updated.toDomain()
        }
    }

    suspend fun updateSessionSignals(
        sessionId: String,
        examSignalCount: Int? = null,
        unresolvedConfusionCount: Int? = null,
        domain: SubjectDomain? = null
    ): LearningSession? = withContext(Dispatchers.IO) {
        val existing = sessionDao.getById(sessionId) ?: return@withContext null
        val updated = existing.copy(
            examSignalCount = examSignalCount ?: existing.examSignalCount,
            unresolvedConfusionCount = unresolvedConfusionCount ?: existing.unresolvedConfusionCount,
            subjectDomain = domain?.name ?: existing.subjectDomain,
            updatedAt = clock()
        )
        sessionDao.upsert(updated)
        updated.toDomain()
    }

    suspend fun saveConceptsAndActivities(
        sessionId: String,
        concepts: List<LearningConcept>,
        activities: List<LearningActivity>,
        createInitialSchedules: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val now = clock()
        database.withTransaction {
            if (concepts.isNotEmpty()) {
                conceptDao.upsertAll(concepts.map { LearningConceptEntity.fromDomain(it) })
            }
            if (activities.isNotEmpty()) {
                activityDao.upsertAll(activities.map { LearningActivityEntity.fromDomain(it) })
                if (createInitialSchedules) {
                    val sessionPaused = sessionDao.getById(sessionId)?.status == "PAUSED"
                    val newSchedules = activities.mapNotNull { act ->
                        if (scheduleDao.getByActivity(act.id) != null) {
                            null
                        } else {
                            ReviewScheduleEntity.fromDomain(
                                DeterministicSpacedScheduler.initialSchedule(
                                    activityId = act.id,
                                    sessionId = sessionId,
                                    conceptId = act.conceptId,
                                    now = now
                                ).copy(isPaused = sessionPaused)
                            )
                        }
                    }
                    if (newSchedules.isNotEmpty()) {
                        scheduleDao.upsertAll(newSchedules)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- active recall & attempts

    /**
     * Atomically records an [ActivityAttempt], advances or resets its [ReviewSchedule] via
     * [DeterministicSpacedScheduler], and recomputes + persists [LearningConcept.state] via
     * [MasteryCalculator.evaluate].
     */
    suspend fun recordAttempt(
        activityId: String,
        userResponse: String,
        isCorrect: Boolean,
        rating: RecallRating? = null,
        feedback: String = "",
        audioPath: String? = null,
        attachmentId: String? = null,
        now: Long = clock()
    ): AttemptOutcome = withContext(Dispatchers.IO) {
        database.withTransaction {
            val activity = activityDao.getById(activityId)
                ?: throw IllegalArgumentException("LearningActivity not found: $activityId")

            val effectiveRating = rating ?: if (isCorrect) RecallRating.GOOD else RecallRating.AGAIN
            val attemptEntity = ActivityAttemptEntity(
                id = "att_${UUID.randomUUID()}",
                activityId = activity.id,
                sessionId = activity.sessionId,
                conceptId = activity.conceptId,
                userResponse = userResponse,
                isCorrect = isCorrect,
                selfRating = effectiveRating.name,
                feedback = feedback,
                createdAt = now,
                audioPath = audioPath,
                attachmentId = attachmentId
            )
            attemptDao.insert(attemptEntity)

            val existingSchedule = scheduleDao.getByActivity(activity.id)?.toDomain()
                ?: DeterministicSpacedScheduler.initialSchedule(
                    activityId = activity.id,
                    sessionId = activity.sessionId,
                    conceptId = activity.conceptId,
                    now = now
                )
            val nextSchedule = DeterministicSpacedScheduler.calculateNextSchedule(
                current = existingSchedule,
                isCorrect = isCorrect,
                rating = effectiveRating,
                now = now
            )
            scheduleDao.upsert(ReviewScheduleEntity.fromDomain(nextSchedule))

            sessionDao.getById(activity.sessionId)?.let { session ->
                sessionDao.upsert(session.copy(lastStudiedAt = now, updatedAt = now))
            }

            val masteryEvaluation = activity.conceptId?.let { cId ->
                val conceptEntity = conceptDao.getById(cId) ?: return@let null
                val allAttempts = attemptDao.getByConcept(cId).map { it.toDomain() }.let { list ->
                    if (list.any { it.id == attemptEntity.id }) list else list + attemptEntity.toDomain()
                }
                val eval = MasteryCalculator.evaluate(
                    attempts = allAttempts,
                    schedule = nextSchedule,
                    now = now
                )
                conceptDao.upsert(
                    conceptEntity.copy(
                        state = eval.state.name,
                        updatedAt = now
                    )
                )
                eval
            }

            AttemptOutcome(
                attempt = attemptEntity.toDomain(),
                schedule = nextSchedule,
                masteryEvaluation = masteryEvaluation
            )
        }
    }

    // ---------------------------------------------------------------- daily companion brief

    fun observeDailyBrief(now: Long = clock()): Flow<DailyCompanionBrief> {
        val sevenDaysAgo = now - (6 * DeterministicSpacedScheduler.DAY_MS)
        val leftFlow = combine(
            sessionDao.observeAll(),
            conceptDao.observeAll(),
            activityDao.observeAll(),
            ::Triple
        )
        val rightFlow = combine(
            scheduleDao.observeDue(now),
            attemptDao.observeRecentAttemptsSince(sevenDaysAgo),
            goalDao.observeUpcoming(now),
            ::Triple
        )
        return combine(leftFlow, rightFlow) { (sessions, concepts, activities), (dueSchedules, recentAttempts, upcomingGoals) ->
            val activeSessionsById = sessions
                .filter { it.status != "PAUSED" }
                .associate { it.id to it.toDomain() }

            val conceptsById = concepts
                .filter { !it.isDismissed && it.sessionId in activeSessionsById }
                .associate { it.id to it.toDomain() }

            val activitiesById = activities
                .filter { !it.isDismissed && !it.isStale && it.sessionId in activeSessionsById }
                .associate { it.id to it.toDomain() }

            val dueItems = dueSchedules.mapNotNull { schEntity ->
                val session = activeSessionsById[schEntity.sessionId] ?: return@mapNotNull null
                val activity = activitiesById[schEntity.activityId] ?: return@mapNotNull null
                val concept = schEntity.conceptId?.let { conceptsById[it] }
                DueReviewItem(
                    schedule = schEntity.toDomain(),
                    activity = activity,
                    concept = concept,
                    session = session
                )
            }

            val weakHighYield = conceptsById.values
                .filter { c ->
                    c.examImportance != ExamImportance.NORMAL &&
                        c.state in setOf(
                            LearningMasteryState.NEEDS_REVIEW,
                            LearningMasteryState.NEW,
                            LearningMasteryState.LEARNING
                        )
                }
                .sortedWith(
                    compareByDescending<LearningConcept> { ExamPriorityRanker.importanceScore(it.examImportance) }
                        .thenByDescending { ExamPriorityRanker.masteryGapScore(it.state) }
                )

            val unresolvedConfusions = activeSessionsById.values.sumOf { it.unresolvedConfusionCount }

            val countdowns = upcomingGoals.map { gEntity ->
                val g = gEntity.toDomain()
                val days = ((g.examDateMs - now).coerceAtLeast(0L) / DeterministicSpacedScheduler.DAY_MS).toInt()
                UpcomingExamCountdown(goal = g, daysRemaining = days)
            }

            val todayBucket = now / DeterministicSpacedScheduler.DAY_MS
            val countsByBucket = recentAttempts.groupingBy { it.createdAt / DeterministicSpacedScheduler.DAY_MS }.eachCount()
            val dailyCounts = (6 downTo 0).map { offset -> countsByBucket[todayBucket - offset] ?: 0 }
            val dailyActive = dailyCounts.map { it > 0 }

            val estimatedWorkMinutes = dueItems.size * 2 + weakHighYield.size * 3 + unresolvedConfusions * 2
            val maxCap = countdowns.firstOrNull()?.goal?.dailyMinutesTarget?.coerceIn(10, 60) ?: 45
            val recommendedMinutes = when {
                estimatedWorkMinutes > 0 -> estimatedWorkMinutes.coerceIn(5, maxCap)
                countdowns.isNotEmpty() -> 10.coerceAtMost(maxCap)
                else -> 0
            }

            DailyCompanionBrief(
                recommendedMinutes = recommendedMinutes,
                dueReviews = dueItems,
                weakHighYieldConcepts = weakHighYield,
                unresolvedConfusionCount = unresolvedConfusions,
                upcomingExams = countdowns,
                sevenDayActivity = dailyActive,
                sevenDayAttemptCounts = dailyCounts
            )
        }.flowOn(Dispatchers.IO)
    }

    // ---------------------------------------------------------------- exam readiness & goals

    fun observeGoals(): Flow<List<LearningGoal>> =
        goalDao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    fun observeGoalsByNotebook(notebookId: String): Flow<List<LearningGoal>> =
        goalDao.observeByNotebook(notebookId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    suspend fun saveGoal(goal: LearningGoal): LearningGoal = withContext(Dispatchers.IO) {
        val now = clock()
        val updated = goal.copy(
            id = goal.id.ifBlank { "goal_${UUID.randomUUID()}" },
            updatedAt = now
        )
        goalDao.upsert(LearningGoalEntity.fromDomain(updated))
        updated
    }

    suspend fun deleteGoal(goalId: String) = withContext(Dispatchers.IO) {
        goalDao.delete(goalId)
    }

    /**
     * Ranks concepts by transparent exam priority for a specific [LearningGoal] ID,
     * course [Notebook] ID, or across all active sessions if `null`.
     */
    suspend fun computeExamPriorityMatrix(
        goalIdOrNotebookId: String? = null,
        now: Long = clock()
    ): List<ExamPriorityItem> = withContext(Dispatchers.IO) {
        val directGoal = goalIdOrNotebookId?.let { goalDao.getById(it)?.toDomain() }
        val notebook = if (directGoal == null && goalIdOrNotebookId != null) {
            notebookDao.getById(goalIdOrNotebookId)
        } else {
            directGoal?.notebookId?.let { notebookDao.getById(it) }
        }
        val effectiveGoal = directGoal ?: notebook?.let { nb ->
            goalDao.getByNotebook(nb.id).firstOrNull { it.examDateMs >= now }?.toDomain()
                ?: goalDao.getByNotebook(nb.id).firstOrNull()?.toDomain()
        }

        val targetSessions: List<LearningSessionEntity> = when {
            effectiveGoal != null && effectiveGoal.sessionIds.isNotEmpty() -> {
                sessionDao.getByIds(effectiveGoal.sessionIds)
            }
            notebook != null -> {
                val noteIds = noteDao.getAll()
                    .filter { it.notebookId == notebook.id && it.deletedAt == null }
                    .map { it.id }
                val byNotes = if (noteIds.isNotEmpty()) sessionDao.getByNoteIds(noteIds) else emptyList()
                val byCourseName = sessionDao.getAllSessions().filter {
                    it.courseName != null && it.courseName.equals(notebook.name, ignoreCase = true)
                }
                (byNotes + byCourseName).distinctBy { it.id }
            }
            else -> sessionDao.getAllSessions().filter { it.status != "PAUSED" }
        }

        if (targetSessions.isEmpty()) return@withContext emptyList()

        val sessionIds = targetSessions.map { it.id }
        val sessionTitles = targetSessions.associate { it.id to it.title }
        val concepts = conceptDao.getBySessions(sessionIds).map { it.toDomain() }
        if (concepts.isEmpty()) return@withContext emptyList()

        val attemptsByConcept = attemptDao.getBySessions(sessionIds)
            .map { it.toDomain() }
            .filter { it.conceptId != null }
            .groupBy { it.conceptId!! }

        val schedulesByConcept = scheduleDao.getBySessions(sessionIds)
            .map { it.toDomain() }
            .filter { it.conceptId != null }
            .associateBy { it.conceptId!! }

        val masteryMap = concepts.associate { concept ->
            val conceptAttempts = attemptsByConcept[concept.id].orEmpty()
            val eval = if (conceptAttempts.isEmpty()) {
                MasteryCalculator.MasteryEvaluation(concept.state, concept.state.description)
            } else {
                MasteryCalculator.evaluate(
                    attempts = conceptAttempts,
                    schedule = schedulesByConcept[concept.id],
                    now = now
                )
            }
            concept.id to eval
        }

        ExamPriorityRanker.rank(
            concepts = concepts,
            sessionTitles = sessionTitles,
            masteryByConceptId = masteryMap,
            examDateMs = effectiveGoal?.examDateMs,
            topicWeights = effectiveGoal?.topicWeights.orEmpty(),
            now = now
        )
    }

    /**
     * Synthesizes a structured Markdown cram sheet aggregating exam hints, definitions,
     * extracted formulas, and concept relationships with source provenance labels.
     */
    suspend fun buildCramSheetMarkdown(
        sessionIds: List<String>,
        title: String = "Exam Cram Sheet"
    ): String = withContext(Dispatchers.IO) {
        if (sessionIds.isEmpty()) return@withContext "# $title\n\n_No study sessions selected._\n"

        val sessions = sessionDao.getByIds(sessionIds).associateBy { it.id }
        val concepts = conceptDao.getBySessions(sessionIds).map { it.toDomain() }
        if (concepts.isEmpty()) return@withContext "# $title\n\n_No concepts extracted yet._\n"

        fun sourceTag(concept: LearningConcept): String {
            val sessionTitle = sessions[concept.sessionId]?.title ?: "Session"
            val ev = concept.evidence.firstOrNull()?.displayLabel()
            return if (ev != null) "$sessionTitle · $ev" else sessionTitle
        }

        val sb = StringBuilder()
        sb.appendLine("# $title")
        sb.appendLine()

        val flagged = concepts.filter { it.examImportance != ExamImportance.NORMAL }
        if (flagged.isNotEmpty()) {
            sb.appendLine("## Exam Hints & High-Yield Concepts")
            flagged.forEach { c ->
                sb.appendLine("- **[${c.examImportance.label}] ${c.name}**: ${c.definition} _(${sourceTag(c)})_")
            }
            sb.appendLine()
        }

        sb.appendLine("## Core Definitions")
        concepts.forEach { c ->
            sb.appendLine("- **${c.name}**: ${c.definition} _(${sourceTag(c)})_")
        }
        sb.appendLine()

        val formulaEntries = mutableListOf<Triple<String, String, String>>()
        concepts.forEach { c ->
            val domain = SubjectDomain.fromName(sessions[c.sessionId]?.subjectDomain)
            val src = sourceTag(c)
            if (!c.formulaLatex.isNullOrBlank()) {
                formulaEntries.add(Triple(c.name, c.formulaLatex, src))
            } else {
                SpokenNotationNormalizer.extractFormulas(c.definition, domain).forEach { f ->
                    formulaEntries.add(Triple(c.name, f, src))
                }
            }
        }
        if (formulaEntries.isNotEmpty()) {
            sb.appendLine("## Formulas & Equations")
            formulaEntries.distinctBy { it.first to it.second }.forEach { (name, formula, src) ->
                sb.appendLine("- **$name**: `$formula` _($src)_")
            }
            sb.appendLine()
        }

        val relationshipLines = concepts.flatMap { c ->
            val src = sourceTag(c)
            c.relationships.map { rel -> "- **${c.name}** → $rel _($src)_" }
        }
        if (relationshipLines.isNotEmpty()) {
            sb.appendLine("## Key Relationships")
            relationshipLines.forEach { sb.appendLine(it) }
            sb.appendLine()
        }

        sb.toString().trimEnd() + "\n"
    }
}
