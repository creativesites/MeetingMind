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
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.MasteryCalculator
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion
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
    private val meetingDao = database.meetingDao()
    private val transcriptDao = database.transcriptDao()
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
        val sessionCount: Int = 0,
        val conceptCount: Int = 0,
        val strongConceptCount: Int = 0,
        val dueReviewCount: Int = 0,
        val sessionIds: List<String> = emptyList()
    )

    data class LectureNoteSummary(
        val noteId: String,
        val sessionId: String?,
        val meetingId: String?,
        val title: String,
        val summary: String,
        val notebookId: String?,
        val courseName: String?,
        val domain: SubjectDomain,
        val workflow: String,
        val durationSeconds: Long,
        val hasRecording: Boolean,
        val conceptCount: Int,
        val activityCount: Int,
        val strongCount: Int,
        val updatedAt: Long
    ) {
        val isAssignedToCourse: Boolean
            get() = !notebookId.isNullOrBlank() || !courseName.isNullOrBlank()
    }

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
        noteDao.observeActive(),
        sessionDao.observeAll(),
        conceptDao.observeAll(),
        scheduleDao.observeAll()
    ) { notebooks, activeNotes, sessions, concepts, schedules ->
        val now = clock()
        val notesByNotebook = activeNotes.filter { it.notebookId != null }.groupBy { it.notebookId!! }
        notebooks
            .filter { it.space == NotebookSpace.LEARNING.name }
            .map { entity ->
                val nb = entity.toDomain()
                val props = CourseProperties.fromJson(nb.propertiesJson)
                val directNotes = notesByNotebook[nb.id].orEmpty()
                val directNoteIds = directNotes.map { it.id }.toSet()
                val matchingSessions = sessions.filter { s ->
                    s.noteId in directNoteIds || (
                        s.courseName != null && (
                            s.courseName.equals(nb.name, ignoreCase = true) ||
                                (props.courseCode.isNotBlank() && s.courseName.equals(props.courseCode, ignoreCase = true))
                            )
                        )
                }
                val matchingSessionIds = matchingSessions.map { it.id }.toSet()
                val courseConcepts = concepts.filter { !it.isDismissed && it.sessionId in matchingSessionIds }
                val strongCount = courseConcepts.count { it.state == LearningMasteryState.STRONG.name }
                val dueCount = schedules.count { sch ->
                    !sch.isPaused &&
                        sch.sessionId in matchingSessionIds &&
                        (sch.snoozedUntil == null || sch.snoozedUntil <= now) &&
                        sch.dueAt <= now
                }
                val allCourseNoteIds = directNoteIds + matchingSessions.map { it.noteId }
                LearningCourse(
                    notebook = nb,
                    properties = props,
                    noteCount = allCourseNoteIds.size,
                    sessionCount = matchingSessions.size,
                    conceptCount = courseConcepts.size,
                    strongConceptCount = strongCount,
                    dueReviewCount = dueCount,
                    sessionIds = matchingSessions.map { it.id }
                )
            }
    }.flowOn(Dispatchers.IO)

    fun observeLectureNotes(): Flow<List<LectureNoteSummary>> = combine(
        noteDao.observeActive(),
        notebookDao.observeActive(),
        sessionDao.observeAll(),
        conceptDao.observeAll(),
        activityDao.observeAll()
    ) { activeNotes, notebooks, sessions, concepts, activities ->
        val learningNotebooks = notebooks.filter { it.space == NotebookSpace.LEARNING.name }
        val learningNotebooksById = learningNotebooks.associateBy { it.id }
        val sessionsByNoteId = sessions.associateBy { it.noteId }
        val conceptsBySession = concepts.filter { !it.isDismissed }.groupBy { it.sessionId }
        val activitiesBySession = activities.filter { !it.isDismissed && !it.isStale }.groupBy { it.sessionId }

        val candidateNotes = activeNotes.filter { note ->
            note.workflow == "LECTURE" ||
                note.workflow == "RESEARCH" ||
                note.notebookId in learningNotebooksById ||
                note.id in sessionsByNoteId
        }
        val meetingsByNoteId = if (candidateNotes.isNotEmpty()) {
            runCatching { noteDao.getMeetingsForNotes(candidateNotes.map { it.id }).associateBy { it.noteId } }
                .getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        candidateNotes.map { note ->
            val session = sessionsByNoteId[note.id]
            val courseNb = note.notebookId?.let { learningNotebooksById[it] }
                ?: learningNotebooks.firstOrNull { nb ->
                    val props = CourseProperties.fromJson(nb.propertiesJson)
                    session?.courseName != null && (
                        session.courseName.equals(nb.name, ignoreCase = true) ||
                            (props.courseCode.isNotBlank() && session.courseName.equals(props.courseCode, ignoreCase = true))
                        )
                }
            val cleanedSessionCourse = session?.courseName?.takeIf { cName ->
                cName.isNotBlank() &&
                    !cName.equals("Course", ignoreCase = true) &&
                    (!cName.equals(note.title, ignoreCase = true) || courseNb != null)
            }
            val resolvedCourseName = courseNb?.name ?: cleanedSessionCourse
            val resolvedCourseId = courseNb?.id
            val meeting = meetingsByNoteId[note.id]
            val sessionConcepts = session?.let { conceptsBySession[it.id] }.orEmpty()
            val sessionActivities = session?.let { activitiesBySession[it.id] }.orEmpty()
            val domain = session?.subjectDomain?.let { SubjectDomain.fromName(it) }
                ?.takeIf { it != SubjectDomain.GENERAL }
                ?: courseNb?.let { CourseProperties.fromJson(it.propertiesJson).domain }
                    ?.takeIf { it != SubjectDomain.GENERAL }
                ?: SubjectDomain.detect(note.title, note.plainText)

            LectureNoteSummary(
                noteId = note.id,
                sessionId = session?.id,
                meetingId = session?.meetingId ?: meeting?.id,
                title = note.title.ifBlank { session?.title ?: "Untitled Lecture" },
                summary = meeting?.summaryText?.ifBlank { null } ?: note.plainText.take(180).replace('\n', ' ').trim(),
                notebookId = resolvedCourseId,
                courseName = resolvedCourseName,
                domain = domain,
                workflow = note.workflow,
                durationSeconds = (meeting?.durationMs ?: 0L) / 1000L,
                hasRecording = meeting != null || session?.meetingId != null,
                conceptCount = sessionConcepts.size,
                activityCount = sessionActivities.size,
                strongCount = sessionConcepts.count { it.state == LearningMasteryState.STRONG.name },
                updatedAt = maxOf(note.updatedAt, session?.updatedAt ?: 0L)
            )
        }.sortedByDescending { it.updatedAt }
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

    /**
     * Creates a new [NotebookSpace.LEARNING] course directly from one or more recorded lecture/study notes,
     * auto-detecting the academic [SubjectDomain] and course title if omitted, linking the notes to the course,
     * and ensuring each note has an active [LearningSession] with extracted concepts and practice activities.
     */
    suspend fun createCourseFromNotes(
        noteIds: List<String>,
        courseName: String? = null,
        courseCode: String = "",
        instructor: String = "",
        term: String = "",
        targetGrade: String = "",
        domain: SubjectDomain? = null
    ): LearningCourse = withContext(Dispatchers.IO) {
        val primaryNote = noteIds.firstOrNull()?.let { noteDao.getById(it) }
        val primarySession = primaryNote?.let { sessionDao.getByNoteId(it.id) }
        val derivedName = courseName?.trim()?.takeIf { it.isNotBlank() }
            ?: deriveCourseNameFromNoteTitle(primaryNote?.title ?: primarySession?.title ?: "New Course")
        val resolvedDomain = domain
            ?: primarySession?.subjectDomain?.let { SubjectDomain.fromName(it) }?.takeIf { it != SubjectDomain.GENERAL }
            ?: SubjectDomain.detect(
                title = listOfNotNull(derivedName, primaryNote?.title).joinToString(" "),
                sampleText = primaryNote?.plainText.orEmpty()
            )
        val derivedCode = courseCode.trim().ifBlank {
            extractCourseCode(primaryNote?.title.orEmpty()).ifBlank {
                "${resolvedDomain.shortLabel.take(4).uppercase()} 101"
            }
        }
        val course = createOrUpdateCourse(
            name = derivedName,
            properties = CourseProperties(
                courseCode = derivedCode,
                lecturer = instructor.trim(),
                syllabusTerms = if (term.isNotBlank()) listOf(term.trim()) else emptyList(),
                targetGrade = targetGrade.trim(),
                domain = resolvedDomain
            )
        )
        if (noteIds.isNotEmpty()) {
            assignNotesToCourse(course.notebook.id, noteIds)
        }
        course
    }

    /**
     * Links one or more lecture/study notes to an existing [NotebookSpace.LEARNING] course notebook,
     * synchronizes their [LearningSession] course metadata, and bootstraps practice cards if empty.
     */
    suspend fun assignNotesToCourse(
        courseNotebookId: String,
        noteIds: List<String>
    ) = withContext(Dispatchers.IO) {
        val courseNb = notebookDao.getById(courseNotebookId) ?: return@withContext
        val courseProps = CourseProperties.fromJson(courseNb.propertiesJson)
        val now = clock()
        for (noteId in noteIds.distinct()) {
            val note = noteDao.getById(noteId) ?: continue
            if (note.notebookId != courseNotebookId) {
                noteDao.upsert(note.copy(notebookId = courseNotebookId, updatedAt = now))
            }
            val meeting = meetingDao.getMeetingByNoteId(noteId)
            val existingSession = sessionDao.getByNoteId(noteId)
            val resolvedDomain = courseProps.domain.takeIf { it != SubjectDomain.GENERAL }
                ?: existingSession?.subjectDomain?.let { SubjectDomain.fromName(it) }?.takeIf { it != SubjectDomain.GENERAL }
                ?: SubjectDomain.detect(note.title, note.plainText)
            val session = if (existingSession != null) {
                val updated = existingSession.copy(
                    courseName = courseNb.name,
                    meetingId = existingSession.meetingId ?: meeting?.id,
                    subjectDomain = resolvedDomain.name,
                    updatedAt = now
                )
                sessionDao.upsert(updated)
                updated.toDomain()
            } else {
                ensureSessionForNote(
                    noteId = noteId,
                    meetingId = meeting?.id,
                    title = note.title,
                    courseName = courseNb.name,
                    domain = resolvedDomain
                )
            }
            bootstrapSessionMaterials(session.id, allowDefaultFallback = true)
        }
    }

    private fun deriveCourseNameFromNoteTitle(rawTitle: String): String {
        val cleaned = rawTitle
            .replace(Regex("""^(Lecture|Lesson|Session|Class|Week|Module|Ch\.?|Chapter)\s*\d*\s*[:\-–—]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[\-–—]\s*(Lecture|Lesson|Part)\s*\d+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.ifBlank { rawTitle.trim() }.ifBlank { "General Studies" }
    }

    private fun extractCourseCode(text: String): String {
        val match = Regex("""\b([A-Z]{2,5})\s*[- ]?\s*(\d{3}[A-Z]?)\b""").find(text)
        return if (match != null) "${match.groupValues[1]} ${match.groupValues[2]}" else ""
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

    // ---------------------------------------------------------------- lecture sync & practice deck builder

    /**
     * Synchronizes all active `LECTURE` / `RESEARCH` notes and `LEARNING` notebook notes with
     * [LearningSessionEntity] records, repairs legacy `courseName == title` entries, and bootstraps
     * concepts + active-recall cards for any lecture note that has content or transcript segments.
     */
    suspend fun syncLectureNotesToSessions() = withContext(Dispatchers.IO) {
        val now = clock()
        val learningNotebooks = notebookDao.getBySpace(NotebookSpace.LEARNING.name)
        val learningNotebooksById = learningNotebooks.associateBy { it.id }
        val activeNotes = noteDao.getActiveOnce()
        val existingSessions = sessionDao.getAllSessions()
        val sessionsByNoteId = existingSessions.associateBy { it.noteId }.toMutableMap()

        // 1. Repair existing sessions whose courseName was set to the note title or "Course" when no course matches
        for (session in existingSessions) {
            val note = activeNotes.firstOrNull { it.id == session.noteId } ?: noteDao.getById(session.noteId)
            val nb = note?.notebookId?.let { learningNotebooksById[it] }
            val matchingCourseNb = nb ?: learningNotebooks.firstOrNull { cNb ->
                val props = CourseProperties.fromJson(cNb.propertiesJson)
                session.courseName != null && (
                    session.courseName.equals(cNb.name, ignoreCase = true) ||
                        (props.courseCode.isNotBlank() && session.courseName.equals(props.courseCode, ignoreCase = true))
                    )
            }
            val desiredCourseName = matchingCourseNb?.name
                ?: session.courseName?.takeIf { cName ->
                    cName.isNotBlank() &&
                        !cName.equals("Course", ignoreCase = true) &&
                        !cName.equals(session.title, ignoreCase = true) &&
                        !cName.equals(note?.title.orEmpty(), ignoreCase = true)
                }
            if (desiredCourseName != session.courseName) {
                val repaired = session.copy(courseName = desiredCourseName, updatedAt = now)
                sessionDao.upsert(repaired)
                sessionsByNoteId[session.noteId] = repaired
            }
        }

        // 2. Ensure every LECTURE / RESEARCH / LEARNING-notebook note has a LearningSession
        val candidateNotes = activeNotes.filter { note ->
            note.workflow == "LECTURE" ||
                note.workflow == "RESEARCH" ||
                note.notebookId in learningNotebooksById ||
                note.id in sessionsByNoteId
        }
        val meetingsByNoteId = if (candidateNotes.isNotEmpty()) {
            runCatching { noteDao.getMeetingsForNotes(candidateNotes.map { it.id }).associateBy { it.noteId } }
                .getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        for (note in candidateNotes) {
            val meeting = meetingsByNoteId[note.id]
            val courseNb = note.notebookId?.let { learningNotebooksById[it] }
            val session = ensureSessionForNote(
                noteId = note.id,
                meetingId = meeting?.id,
                title = note.title.ifBlank { meeting?.title.orEmpty() }.ifBlank { "Lecture Notes" },
                courseName = courseNb?.name,
                domain = courseNb?.let { CourseProperties.fromJson(it.propertiesJson).domain }
                    ?.takeIf { it != SubjectDomain.GENERAL }
                    ?: SubjectDomain.detect(note.title, note.plainText)
            )
            // Bootstrap materials if the note has actual text/blocks/transcript and no activities yet
            bootstrapSessionMaterials(session.id, allowDefaultFallback = meeting != null || note.workflow == "LECTURE")
        }
    }

    /**
     * Ensures that a [LearningSession] has extracted [LearningConcept]s and multi-modal
     * [LearningActivity] cards (`RECALL`, `WORKED_PROBLEM`, `MULTIPLE_CHOICE`, `TEACH_BACK`).
     */
    suspend fun bootstrapSessionMaterials(
        sessionId: String,
        force: Boolean = false,
        allowDefaultFallback: Boolean = false
    ): LearningSessionDetail? = withContext(Dispatchers.IO) {
        val sessionEntity = sessionDao.getById(sessionId) ?: return@withContext null
        val domain = SubjectDomain.fromName(sessionEntity.subjectDomain)
        val now = clock()

        var concepts = conceptDao.getBySession(sessionId).map { it.toDomain() }
        val existingActivities = activityDao.getBySession(sessionId).map { it.toDomain() }

        val note = noteDao.getById(sessionEntity.noteId)
        val blocks = noteDao.getBlocks(sessionEntity.noteId)
        val segments = sessionEntity.meetingId?.let { transcriptDao.getSegmentsForMeetingDirect(it) }.orEmpty()

        val hasSourceContent = !note?.plainText.isNullOrBlank() ||
            blocks.any { it.text.isNotBlank() } ||
            segments.any { it.text.isNotBlank() }

        if (concepts.isEmpty() && (force || allowDefaultFallback || hasSourceContent)) {
            val extracted = extractDeterministicConcepts(
                sessionId = sessionId,
                sessionTitle = sessionEntity.title,
                domain = domain,
                blocks = blocks.map { it.text },
                plainText = listOfNotNull(note?.plainText, segments.joinToString(" ") { it.cleanedText ?: it.text }).joinToString("\n"),
                now = now
            )
            if (extracted.isNotEmpty()) {
                conceptDao.upsertAll(extracted.map { LearningConceptEntity.fromDomain(it) })
                concepts = extracted
            }
        }

        if (concepts.isNotEmpty() && (existingActivities.isEmpty() || force)) {
            val generatedActivities = buildActivitiesFromConcepts(
                sessionId = sessionId,
                sessionTitle = sessionEntity.title,
                domain = domain,
                concepts = concepts,
                existingActivities = if (force) emptyList() else existingActivities,
                now = now
            )
            if (generatedActivities.isNotEmpty()) {
                saveConceptsAndActivities(
                    sessionId = sessionId,
                    concepts = emptyList(),
                    activities = generatedActivities,
                    createInitialSchedules = true
                )
            }
        } else if (concepts.isNotEmpty() && existingActivities.none { it.type == LearningActivityType.TEACH_BACK }) {
            // Ensure at least one Teach-Back activity and one Multiple-Choice / Worked Problem exist for the session
            val supplemental = buildSupplementalActivities(
                sessionId = sessionId,
                sessionTitle = sessionEntity.title,
                domain = domain,
                concepts = concepts,
                existingActivities = existingActivities,
                now = now
            )
            if (supplemental.isNotEmpty()) {
                saveConceptsAndActivities(
                    sessionId = sessionId,
                    concepts = emptyList(),
                    activities = supplemental,
                    createInitialSchedules = true
                )
            }
        }

        getSessionDetail(sessionId)
    }

    /**
     * Loads a prioritized deck of [LearningActivity] cards for Practice Arena.
     * Automatically syncs lecture notes and bootstraps activities from existing sessions/concepts
     * so the student never hits an empty Practice Arena when study material exists.
     */
    suspend fun getPracticeDeck(
        activityTypes: Set<LearningActivityType>? = null,
        sessionId: String? = null,
        courseNotebookId: String? = null,
        onlyDue: Boolean = false
    ): List<LearningActivity> = withContext(Dispatchers.IO) {
        val now = clock()
        syncLectureNotesToSessions()

        val allSessions = sessionDao.getAllSessions().filter { it.status != "PAUSED" }
        val targetSessions = when {
            !sessionId.isNullOrBlank() -> allSessions.filter { it.id == sessionId }
            !courseNotebookId.isNullOrBlank() -> {
                val nb = notebookDao.getById(courseNotebookId)
                val props = nb?.let { CourseProperties.fromJson(it.propertiesJson) }
                val noteIds = noteDao.getActiveOnce()
                    .filter { it.notebookId == courseNotebookId }
                    .map { it.id }
                    .toSet()
                allSessions.filter { s ->
                    s.noteId in noteIds || (
                        nb != null && s.courseName != null && (
                            s.courseName.equals(nb.name, ignoreCase = true) ||
                                (props?.courseCode?.isNotBlank() == true && s.courseName.equals(props.courseCode, ignoreCase = true))
                            )
                        )
                }
            }
            else -> allSessions
        }

        // Ensure every target session has activities generated from its concepts/notes
        for (s in targetSessions) {
            bootstrapSessionMaterials(s.id, allowDefaultFallback = true)
        }

        val sessionIds = targetSessions.map { it.id }
        if (sessionIds.isEmpty()) return@withContext emptyList()

        val allActivities = activityDao.getBySessions(sessionIds)
            .filter { !it.isDismissed && !it.isStale }
            .map { it.toDomain() }

        val filtered = if (activityTypes.isNullOrEmpty()) {
            allActivities
        } else {
            allActivities.filter { it.type in activityTypes }.ifEmpty { allActivities }
        }

        val schedulesByActivity = scheduleDao.getBySessions(sessionIds).associateBy { it.activityId }
        val conceptsById = conceptDao.getBySessions(sessionIds).associate { it.id to it.toDomain() }

        if (onlyDue) {
            val due = filtered.filter { act ->
                val sch = schedulesByActivity[act.id]
                sch == null || (!sch.isPaused && (sch.snoozedUntil == null || sch.snoozedUntil <= now) && sch.dueAt <= now)
            }
            if (due.isNotEmpty()) return@withContext due
        }

        // Order: due first, then high-yield/weak concepts, then newest
        filtered.sortedWith(
            compareBy<LearningActivity> { act ->
                val sch = schedulesByActivity[act.id]
                val isDue = sch == null || (!sch.isPaused && (sch.snoozedUntil == null || sch.snoozedUntil <= now) && sch.dueAt <= now)
                if (isDue) 0 else 1
            }.thenByDescending { act ->
                val concept = act.conceptId?.let { conceptsById[it] }
                if (concept != null) {
                    ExamPriorityRanker.importanceScore(concept.examImportance) + ExamPriorityRanker.masteryGapScore(concept.state)
                } else {
                    0.0
                }
            }.thenBy { act ->
                schedulesByActivity[act.id]?.dueAt ?: 0L
            }
        )
    }

    private fun extractDeterministicConcepts(
        sessionId: String,
        sessionTitle: String,
        domain: SubjectDomain,
        blocks: List<String>,
        plainText: String,
        now: Long
    ): List<LearningConcept> {
        val results = mutableListOf<LearningConcept>()
        val combinedLines = (blocks + plainText.lines())
            .map { it.trim() }
            .filter { it.length >= 8 }
            .distinct()

        // 1. Parse "**Term**: Definition" or "Term: Definition" lines
        val colonPattern = Regex("""^(?:[-*•]\s*)?\*{0,2}([A-Za-z0-9][A-Za-z0-9\s\-/()]{2,42}?)\*{0,2}\s*[:–—]\s+(.{12,240})$""")
        for (line in combinedLines) {
            if (results.size >= 6) break
            val m = colonPattern.find(line) ?: continue
            val rawName = m.groupValues[1].trim()
            val rawDef = m.groupValues[2].trim()
            if (rawName.lowercase() in setOf("note", "summary", "overview", "example", "question", "answer", "lecture", "speaker")) continue
            if (results.any { it.name.equals(rawName, ignoreCase = true) }) continue
            val formulas = SpokenNotationNormalizer.extractFormulas("$rawName $rawDef", domain)
            val isHighYield = line.contains("exam", ignoreCase = true) ||
                line.contains("important", ignoreCase = true) ||
                line.contains("key", ignoreCase = true) ||
                formulas.isNotEmpty()
            results.add(
                LearningConcept(
                    id = "concept_${UUID.randomUUID()}",
                    sessionId = sessionId,
                    name = rawName,
                    definition = rawDef,
                    state = LearningMasteryState.DEVELOPING,
                    examImportance = if (isHighYield) ExamImportance.HIGH_YIELD else ExamImportance.NORMAL,
                    formulaLatex = formulas.firstOrNull(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // 2. Match domain vocabulary hints in the note text
        if (results.size < 3 && plainText.isNotBlank()) {
            val sentences = plainText.split(Regex("""(?<=[.!?])\s+|\n+""")).map { it.trim() }.filter { it.length in 15..260 }
            val matchedTerms = domain.vocabularyHints().filter { term ->
                plainText.contains(term, ignoreCase = true) && results.none { it.name.equals(term, ignoreCase = true) }
            }
            for (term in matchedTerms.take(5 - results.size)) {
                val sentence = sentences.firstOrNull { it.contains(term, ignoreCase = true) }
                    ?: "Core ${domain.shortLabel} principle regarding $term in $sessionTitle."
                val formulas = SpokenNotationNormalizer.extractFormulas(sentence, domain)
                results.add(
                    LearningConcept(
                        id = "concept_${UUID.randomUUID()}",
                        sessionId = sessionId,
                        name = term.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        definition = sentence,
                        state = LearningMasteryState.DEVELOPING,
                        examImportance = if (results.isEmpty()) ExamImportance.HIGH_YIELD else ExamImportance.NORMAL,
                        formulaLatex = formulas.firstOrNull(),
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }

        // 3. Extract meaningful sentences from plainText if still empty
        if (results.isEmpty() && plainText.isNotBlank()) {
            val sentences = plainText.split(Regex("""(?<=[.!?])\s+|\n+"""))
                .map { it.trim() }
                .filter { it.length in 20..220 }
                .take(3)
            sentences.forEachIndexed { idx, sentence ->
                val words = sentence.split(Regex("""\s+""")).take(4).joinToString(" ").trimEnd(',', ':', '.', ';')
                val formulas = SpokenNotationNormalizer.extractFormulas(sentence, domain)
                results.add(
                    LearningConcept(
                        id = "concept_${UUID.randomUUID()}",
                        sessionId = sessionId,
                        name = if (words.length >= 4) words else "$sessionTitle Key Point ${idx + 1}",
                        definition = sentence,
                        state = LearningMasteryState.DEVELOPING,
                        examImportance = if (idx == 0) ExamImportance.HIGH_YIELD else ExamImportance.NORMAL,
                        formulaLatex = formulas.firstOrNull(),
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }

        // 4. Domain fallback when session has no text yet
        if (results.isEmpty()) {
            val cleanTitle = sessionTitle.ifBlank { domain.displayName }
            val vocab = domain.vocabularyHints().take(2)
            results.add(
                LearningConcept(
                    id = "concept_${UUID.randomUUID()}",
                    sessionId = sessionId,
                    name = "$cleanTitle Core Principle",
                    definition = "Fundamental mechanism, definition, and boundary conditions of $cleanTitle in ${domain.displayName}.",
                    state = LearningMasteryState.DEVELOPING,
                    examImportance = ExamImportance.HIGH_YIELD,
                    createdAt = now,
                    updatedAt = now
                )
            )
            vocab.forEachIndexed { idx, term ->
                val capTerm = term.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                results.add(
                    LearningConcept(
                        id = "concept_${UUID.randomUUID()}",
                        sessionId = sessionId,
                        name = "$capTerm in $cleanTitle",
                        definition = "How $term applies to solving problems and analyzing systems in $cleanTitle.",
                        state = LearningMasteryState.DEVELOPING,
                        examImportance = if (idx == 0) ExamImportance.HIGH_YIELD else ExamImportance.NORMAL,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }

        return results
    }

    private fun buildActivitiesFromConcepts(
        sessionId: String,
        sessionTitle: String,
        domain: SubjectDomain,
        concepts: List<LearningConcept>,
        existingActivities: List<LearningActivity>,
        now: Long
    ): List<LearningActivity> {
        val result = mutableListOf<LearningActivity>()
        concepts.forEachIndexed { idx, c ->
            // 1. Active Recall Flashcard
            if (existingActivities.none { it.conceptId == c.id && it.type == LearningActivityType.RECALL }) {
                val expected = buildString {
                    append(c.definition.ifBlank { "Key concept from $sessionTitle." })
                    if (!c.formulaLatex.isNullOrBlank()) {
                        append("\nFormula: ${c.formulaLatex}")
                    }
                }
                result.add(
                    LearningActivity(
                        id = "act_${UUID.randomUUID()}",
                        sessionId = sessionId,
                        conceptId = c.id,
                        type = LearningActivityType.RECALL,
                        prompt = "Define **${c.name}** and explain its significance in $sessionTitle.",
                        expectedAnswer = expected,
                        difficulty = if (c.examImportance != ExamImportance.NORMAL) "HARD" else "MEDIUM",
                        isDiagnostic = idx < 3,
                        createdAt = now + idx,
                        updatedAt = now + idx
                    )
                )
            }
        }
        result.addAll(
            buildSupplementalActivities(
                sessionId = sessionId,
                sessionTitle = sessionTitle,
                domain = domain,
                concepts = concepts,
                existingActivities = existingActivities + result,
                now = now + 10
            )
        )
        return result
    }

    private fun buildSupplementalActivities(
        sessionId: String,
        sessionTitle: String,
        domain: SubjectDomain,
        concepts: List<LearningConcept>,
        existingActivities: List<LearningActivity>,
        now: Long
    ): List<LearningActivity> {
        if (concepts.isEmpty()) return emptyList()
        val supplemental = mutableListOf<LearningActivity>()
        val primary = concepts.firstOrNull { it.examImportance != ExamImportance.NORMAL } ?: concepts.first()

        // Ensure at least one Multiple-Choice or Worked Problem activity
        if (existingActivities.none { it.type in setOf(LearningActivityType.MULTIPLE_CHOICE, LearningActivityType.WORKED_PROBLEM, LearningActivityType.APPLICATION) }) {
            val correctOption = primary.definition.ifBlank { "Core principle of ${primary.name} in ${domain.displayName}" }
            val otherDefs = concepts.filter { it.id != primary.id && it.definition.isNotBlank() }.map { it.definition }
            val distractors = (otherDefs + listOf(
                "An unrelated constant that remains invariant regardless of ${primary.name}",
                "The inverse boundary condition that only applies outside ${domain.shortLabel}",
                "A secondary side-effect rather than the governing definition of ${primary.name}"
            )).distinct().filter { it != correctOption }.take(3)
            val options = (listOf(correctOption) + distractors).shuffled()

            supplemental.add(
                LearningActivity(
                    id = "act_${UUID.randomUUID()}",
                    sessionId = sessionId,
                    conceptId = primary.id,
                    type = if (!primary.formulaLatex.isNullOrBlank()) LearningActivityType.WORKED_PROBLEM else LearningActivityType.MULTIPLE_CHOICE,
                    prompt = if (!primary.formulaLatex.isNullOrBlank()) {
                        "Apply ${primary.name} (${primary.formulaLatex}) to explain how a change in input affects the outcome in $sessionTitle."
                    } else {
                        "Which of the following best describes ${primary.name} in $sessionTitle?"
                    },
                    expectedAnswer = correctOption,
                    options = options,
                    difficulty = "MEDIUM",
                    isDiagnostic = true,
                    createdAt = now + 1,
                    updatedAt = now + 1
                )
            )
        }

        // Ensure at least one Voice Teach-Back activity with rubric criteria
        if (existingActivities.none { it.type == LearningActivityType.TEACH_BACK }) {
            val rubric = mutableListOf(
                TeachBackCriterion(
                    claim = "State the core definition of ${primary.name}: ${primary.definition.take(90)}",
                    required = true
                ),
                TeachBackCriterion(
                    claim = if (!primary.formulaLatex.isNullOrBlank()) {
                        "Explain the formula or relationship ${primary.formulaLatex}"
                    } else {
                        "Explain the underlying mechanism or intuition of ${primary.name} in ${domain.shortLabel}"
                    },
                    required = true
                ),
                TeachBackCriterion(
                    claim = "Give a concrete example or exam application from $sessionTitle",
                    required = false
                )
            )
            supplemental.add(
                LearningActivity(
                    id = "act_${UUID.randomUUID()}",
                    sessionId = sessionId,
                    conceptId = primary.id,
                    type = LearningActivityType.TEACH_BACK,
                    prompt = "Teach back **${primary.name}** in your own words as if explaining it to a classmate.",
                    expectedAnswer = primary.definition,
                    rubric = rubric,
                    difficulty = "HARD",
                    isDiagnostic = false,
                    createdAt = now + 2,
                    updatedAt = now + 2
                )
            )
        }

        return supplemental
    }
}
