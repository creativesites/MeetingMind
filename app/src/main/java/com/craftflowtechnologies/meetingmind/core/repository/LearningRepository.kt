package com.craftflowtechnologies.meetingmind.core.repository

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.database.ActivityAttemptEntity
import com.craftflowtechnologies.meetingmind.core.database.LearningActivityEntity
import com.craftflowtechnologies.meetingmind.core.database.LearningConceptEntity
import com.craftflowtechnologies.meetingmind.core.database.LearningSessionEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.ReviewScheduleEntity
import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.DeterministicSpacedScheduler
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningPassage
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.MasteryCalculator
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Encapsulates the daily brief recommendations for the Learn home tab (PLAN_LEARNING §1.2).
 */
data class DailyBrief(
    val dueActivities: List<Pair<LearningActivity, ReviewSchedule>>,
    val weakConcept: Pair<LearningConcept, String>?,
    val continueSession: LearningSession?,
    val estimatedMinutes: Int,
    val isDoneForToday: Boolean
)

class LearningRepository(
    private val context: Context,
    private val database: MeetMindDatabase
) {
    private val sessionDao = database.learningSessionDao()
    private val conceptDao = database.learningConceptDao()
    private val activityDao = database.learningActivityDao()
    private val attemptDao = database.activityAttemptDao()
    private val scheduleDao = database.reviewScheduleDao()
    private val noteRepository = NoteRepository(context, database)

    suspend fun getOrCreateSessionForNote(noteId: String, courseName: String? = null): LearningSession = withContext(Dispatchers.IO) {
        val existing = sessionDao.getByNoteId(noteId)
        if (existing != null) return@withContext existing.toDomain()

        val note = database.noteDao().getById(noteId)
            ?: throw IllegalArgumentException("Note with ID $noteId does not exist")
        val meeting = database.meetingDao().getMeetingByNoteId(noteId)

        val now = System.currentTimeMillis()
        val session = LearningSession(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            meetingId = meeting?.id,
            title = note.title.ifBlank { "Lecture Session" },
            courseName = courseName,
            status = "ACTIVE",
            lastStudiedAt = null,
            createdAt = now,
            updatedAt = now
        )
        sessionDao.upsert(LearningSessionEntity.fromDomain(session))
        session
    }

    suspend fun createTypedSession(title: String, courseName: String? = null): LearningSession = withContext(Dispatchers.IO) {
        val learningNotebook = noteRepository.ensureSpaceNotebook(NotebookSpace.LEARNING)
        val now = System.currentTimeMillis()
        val noteId = UUID.randomUUID().toString()

        val noteEntity = NoteEntity(
            id = noteId,
            title = title,
            workflow = RecordingType.LECTURE.name,
            notebookId = learningNotebook.id,
            createdAt = now,
            updatedAt = now,
            eventDate = now,
            pinned = false,
            isPrivate = false,
            status = NoteStatus.OPEN.name,
            answeredAt = null,
            metadataJson = "{}",
            archivedAt = null,
            plainText = ""
        )
        database.noteDao().upsert(noteEntity)

        val session = LearningSession(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            meetingId = null,
            title = title,
            courseName = courseName,
            status = "ACTIVE",
            lastStudiedAt = null,
            createdAt = now,
            updatedAt = now
        )
        sessionDao.upsert(LearningSessionEntity.fromDomain(session))
        session
    }

    suspend fun getSession(id: String): LearningSession? = withContext(Dispatchers.IO) {
        sessionDao.getById(id)?.toDomain()
    }

    fun observeSession(id: String): Flow<LearningSession?> =
        sessionDao.observeById(id).map { it?.toDomain() }

    fun observeAllSessions(): Flow<List<LearningSession>> =
        sessionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeConcepts(sessionId: String): Flow<List<LearningConcept>> =
        conceptDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getConcepts(sessionId: String): List<LearningConcept> = withContext(Dispatchers.IO) {
        conceptDao.getBySession(sessionId).map { it.toDomain() }
    }

    suspend fun saveConcepts(concepts: List<LearningConcept>) = withContext(Dispatchers.IO) {
        conceptDao.upsertAll(concepts.map { LearningConceptEntity.fromDomain(it) })
    }

    suspend fun addCustomConcept(
        sessionId: String,
        name: String,
        definition: String,
        emphasis: String? = null
    ): LearningConcept = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val concept = LearningConcept(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            name = name.trim(),
            definition = definition.trim(),
            emphasis = emphasis?.trim()?.takeIf { it.isNotBlank() },
            relationships = emptyList(),
            evidence = emptyList(),
            state = LearningMasteryState.NEW,
            isUserEdited = true,
            isDismissed = false,
            isStale = false,
            createdAt = now,
            updatedAt = now
        )
        conceptDao.upsert(LearningConceptEntity.fromDomain(concept))
        concept
    }

    suspend fun updateConcept(concept: LearningConcept): LearningConcept = withContext(Dispatchers.IO) {
        val updated = concept.copy(
            isUserEdited = true,
            updatedAt = System.currentTimeMillis()
        )
        conceptDao.update(LearningConceptEntity.fromDomain(updated))
        updated
    }

    suspend fun dismissConcept(conceptId: String) = withContext(Dispatchers.IO) {
        val concept = conceptDao.getById(conceptId) ?: return@withContext
        conceptDao.update(concept.copy(isDismissed = true, updatedAt = System.currentTimeMillis()))
    }

    fun observeActivities(sessionId: String): Flow<List<LearningActivity>> =
        activityDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    fun observeDiagnosticActivities(sessionId: String): Flow<List<LearningActivity>> =
        activityDao.observeDiagnosticBySession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getActivities(sessionId: String): List<LearningActivity> = withContext(Dispatchers.IO) {
        activityDao.getBySession(sessionId).map { it.toDomain() }
    }

    suspend fun getDiagnosticActivities(sessionId: String): List<LearningActivity> = withContext(Dispatchers.IO) {
        activityDao.getDiagnosticBySession(sessionId).map { it.toDomain() }
    }

    suspend fun saveActivities(activities: List<LearningActivity>) = withContext(Dispatchers.IO) {
        activityDao.upsertAll(activities.map { LearningActivityEntity.fromDomain(it) })
        // Initialize review schedules for any new activities that do not have one
        val now = System.currentTimeMillis()
        val schedules = activities.map { act ->
            val existing = scheduleDao.getByActivity(act.id)
            existing ?: ReviewScheduleEntity.fromDomain(
                DeterministicSpacedScheduler.initialSchedule(
                    activityId = act.id,
                    sessionId = act.sessionId,
                    conceptId = act.conceptId,
                    now = now
                )
            )
        }
        scheduleDao.upsertAll(schedules)
    }

    suspend fun updateActivity(activity: LearningActivity) = withContext(Dispatchers.IO) {
        val updated = activity.copy(updatedAt = System.currentTimeMillis())
        activityDao.update(LearningActivityEntity.fromDomain(updated))
    }

    suspend fun dismissActivity(activityId: String) = withContext(Dispatchers.IO) {
        val activity = activityDao.getById(activityId) ?: return@withContext
        activityDao.update(activity.copy(isDismissed = true, updatedAt = System.currentTimeMillis()))
        scheduleDao.deleteByActivity(activityId)
    }

    /**
     * Session regeneration: replaces unedited auto-generated concepts and activities
     * without duplicating active items or review schedules.
     * Preserves:
     * - User-edited concepts (isUserEdited == true)
     * - All previous ActivityAttempts as immutable history.
     */
    suspend fun regenerateSession(
        sessionId: String,
        newConcepts: List<LearningConcept>,
        newActivities: List<LearningActivity>
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val currentConcepts = conceptDao.getBySession(sessionId)
        val currentActivities = activityDao.getBySession(sessionId)

        // 1. Process existing activities:
        // If an activity has attempts, mark isDismissed = true and delete its review schedule so it's not scheduled again.
        // If an activity has NO attempts, delete it completely along with its schedule.
        for (act in currentActivities) {
            val attempts = attemptDao.getByActivity(act.id)
            if (attempts.isNotEmpty()) {
                activityDao.update(act.copy(isDismissed = true, updatedAt = now))
                scheduleDao.deleteByActivity(act.id)
            } else {
                scheduleDao.deleteByActivity(act.id)
                activityDao.delete(act.id)
            }
        }

        // 2. Process existing concepts:
        // Preserve user-edited concepts (isUserEdited == true).
        // For unedited concepts: if any activities with attempts reference them, mark isDismissed = true.
        // Otherwise, delete the unedited concept.
        for (c in currentConcepts) {
            if (!c.isUserEdited) {
                val referencingActivities = activityDao.getBySession(sessionId).filter { it.conceptId == c.id }
                if (referencingActivities.isNotEmpty()) {
                    conceptDao.update(c.copy(isDismissed = true, updatedAt = now))
                } else {
                    conceptDao.delete(c.id)
                }
            }
        }

        // 3. Insert new concepts (excluding any whose names match existing user-edited concepts)
        val userEditedConceptNames = currentConcepts.filter { it.isUserEdited }.map { it.name.trim().lowercase() }.toSet()
        val conceptsToSave = newConcepts.filter { it.name.trim().lowercase() !in userEditedConceptNames }
        conceptDao.upsertAll(conceptsToSave.map { LearningConceptEntity.fromDomain(it) })

        // 4. Save new activities and initialize their review schedules
        saveActivities(newActivities)
    }

    suspend fun recordAttempt(
        activityId: String,
        userResponse: String,
        isCorrect: Boolean,
        selfRating: RecallRating? = null,
        feedback: String = ""
    ): ActivityAttempt = withContext(Dispatchers.IO) {
        val activityEntity = activityDao.getById(activityId)
            ?: throw IllegalArgumentException("Activity $activityId does not exist")
        val activity = activityEntity.toDomain()
        val now = System.currentTimeMillis()

        // 1. Immutable Attempt Log
        val attempt = ActivityAttempt(
            id = UUID.randomUUID().toString(),
            activityId = activityId,
            sessionId = activity.sessionId,
            conceptId = activity.conceptId,
            userResponse = userResponse,
            isCorrect = isCorrect,
            selfRating = selfRating,
            feedback = feedback,
            createdAt = now
        )
        attemptDao.insert(ActivityAttemptEntity.fromDomain(attempt))

        // 2. Deterministic Spaced Retrieval Update
        val currentSchedule = scheduleDao.getByActivity(activityId)?.toDomain()
            ?: DeterministicSpacedScheduler.initialSchedule(activityId, activity.sessionId, activity.conceptId, now)

        val rating = selfRating ?: if (isCorrect) RecallRating.GOOD else RecallRating.AGAIN
        val nextSchedule = DeterministicSpacedScheduler.calculateNextSchedule(currentSchedule, isCorrect, rating, now)
        scheduleDao.upsert(ReviewScheduleEntity.fromDomain(nextSchedule))

        // 3. Transparent Mastery Calculation for concept
        activity.conceptId?.let { cid ->
            val conceptEntity = conceptDao.getById(cid)
            if (conceptEntity != null) {
                val attempts = attemptDao.getByConcept(cid).map { it.toDomain() }
                val evaluation = MasteryCalculator.evaluate(attempts, nextSchedule, now)
                val updatedConcept = conceptEntity.copy(
                    state = evaluation.state.name,
                    updatedAt = now
                )
                conceptDao.update(updatedConcept)
            }
        }

        // 4. Update session lastStudiedAt
        val session = sessionDao.getById(activity.sessionId)
        if (session != null) {
            sessionDao.update(session.copy(lastStudiedAt = now, updatedAt = now))
        }

        attempt
    }

    fun observeAttempts(sessionId: String): Flow<List<ActivityAttempt>> =
        attemptDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getAttemptsForSession(sessionId: String): List<ActivityAttempt> = withContext(Dispatchers.IO) {
        attemptDao.getBySession(sessionId).map { it.toDomain() }
    }

    suspend fun snoozeActivity(activityId: String, untilMs: Long) = withContext(Dispatchers.IO) {
        val schedule = scheduleDao.getByActivity(activityId) ?: return@withContext
        scheduleDao.update(schedule.copy(snoozedUntil = untilMs, updatedAt = System.currentTimeMillis()))
    }

    suspend fun pauseSession(sessionId: String, isPaused: Boolean) = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId) ?: return@withContext
        sessionDao.update(session.copy(status = if (isPaused) "PAUSED" else "ACTIVE", updatedAt = System.currentTimeMillis()))
        val schedules = scheduleDao.getBySession(sessionId)
        val updated = schedules.map { it.copy(isPaused = isPaused, updatedAt = System.currentTimeMillis()) }
        scheduleDao.upsertAll(updated)
    }

    fun observeDueReviews(now: Long = System.currentTimeMillis()): Flow<List<Pair<LearningActivity, ReviewSchedule>>> {
        return scheduleDao.observeDue(now).combine(activityDao.observeAll()) { schedules: List<ReviewScheduleEntity>, activities: List<LearningActivityEntity> ->
            val actMap = activities.associateBy { it.id }
            schedules.mapNotNull { sched ->
                val act = actMap[sched.activityId]
                if (act != null && !act.isDismissed && !act.isStale) {
                    act.toDomain() to sched.toDomain()
                } else null
            }
        }
    }

    fun observeDailyBrief(now: Long = System.currentTimeMillis()): Flow<DailyBrief> {
        return combine(
            observeDueReviews(now),
            conceptDao.observeAll(),
            sessionDao.observeAll()
        ) { dueList, allConcepts, allSessions ->
            // Bounded queue: cap daily reviews to max 10 to prevent punitive backlogs (PLAN_LEARNING §1.2)
            val boundedDue = dueList.take(10)
            val estMinutes = (boundedDue.size * 1.5).toInt().coerceAtLeast(if (boundedDue.isNotEmpty()) 2 else 0)

            // Weak concept recommendation: look for concepts in NEEDS_REVIEW or with failed attempts (withhold dismissed/stale)
            val weakCandidate = allConcepts
                .map { it.toDomain() }
                .filter { !it.isDismissed && !it.isStale }
                .firstOrNull { it.state == LearningMasteryState.NEEDS_REVIEW }
                ?: allConcepts
                    .map { it.toDomain() }
                    .filter { !it.isDismissed && !it.isStale }
                    .firstOrNull { it.state == LearningMasteryState.LEARNING }

            val weakConcept = weakCandidate?.let { c ->
                val reason = when (c.state) {
                    LearningMasteryState.NEEDS_REVIEW -> "Recent difficulty or overdue retrieval"
                    LearningMasteryState.LEARNING -> "Needs spaced reinforcement to consolidate"
                    else -> "Recommended for review"
                }
                c to reason
            }

            val continueSession = allSessions
                .map { it.toDomain() }
                .filter { it.status == "ACTIVE" }
                .maxByOrNull { it.lastStudiedAt ?: it.updatedAt }

            DailyBrief(
                dueActivities = boundedDue,
                weakConcept = weakConcept,
                continueSession = continueSession,
                estimatedMinutes = estMinutes,
                isDoneForToday = boundedDue.isEmpty()
            )
        }
    }

    /**
     * Canonical gathering of source passages for a LearningSession.
     * Preserves authentic noteId, blockId, meetingId, segmentId, and timestamps.
     */
    suspend fun gatherSessionPassages(sessionId: String): List<LearningPassage> = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId) ?: return@withContext emptyList()
        val results = mutableListOf<LearningPassage>()

        // 1. Note Blocks
        val blocks = database.noteDao().getBlocks(session.noteId)
        val validBlocks = blocks.filter { it.text.isNotBlank() }
        if (validBlocks.isNotEmpty()) {
            validBlocks.forEach { b ->
                results.add(
                    LearningPassage.NoteBlock(
                        id = b.id,
                        text = b.text,
                        label = b.sectionKey ?: "Note section",
                        noteId = session.noteId,
                        blockId = b.id
                    )
                )
            }
        } else {
            val note = database.noteDao().getById(session.noteId)
            if (note != null && note.plainText.isNotBlank()) {
                results.add(
                    LearningPassage.NoteText(
                        id = session.noteId,
                        text = note.plainText,
                        label = "Note content",
                        noteId = session.noteId
                    )
                )
            }
        }

        // 2. Transcript segments (if linked to a lecture recording)
        val meetingId = session.meetingId ?: database.meetingDao().getMeetingByNoteId(session.noteId)?.id
        if (meetingId != null) {
            val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
            segments.filter { it.text.isNotBlank() }.take(50).forEach { seg ->
                results.add(
                    LearningPassage.TranscriptSegment(
                        id = seg.id,
                        text = seg.cleanedText ?: seg.text,
                        label = "Lecture @ ${seg.startMs / 1000}s",
                        noteId = session.noteId,
                        meetingId = meetingId,
                        segmentId = seg.id,
                        startMs = seg.startMs,
                        endMs = seg.endMs
                    )
                )
            }
        }

        results
    }

    /**
     * Checks both concepts and activities in a session and flags them as stale (isStale = true)
     * if their cited source blocks/segments have been deleted.
     */
    suspend fun checkAndMarkStaleEntities(sessionId: String) = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId) ?: return@withContext
        val activities = activityDao.getBySession(sessionId)
        val concepts = conceptDao.getBySession(sessionId)
        val note = database.noteDao().getById(session.noteId)
        val validBlocks = database.noteDao().getBlocks(session.noteId).map { it.id }.toSet()
        val validSegments = if (session.meetingId != null) {
            database.transcriptDao().getSegmentsForMeetingDirect(session.meetingId).map { it.id }.toSet()
        } else emptySet()

        fun isEvidenceStale(evidenceList: List<LearningEvidence>): Boolean {
            if (evidenceList.isEmpty()) return false
            val hasValidCitation = evidenceList.any { ev ->
                (ev.blockId != null && ev.blockId in validBlocks) ||
                (ev.segmentIds.isNotEmpty() && ev.segmentIds.any { it in validSegments }) ||
                (ev.blockId == null && ev.segmentIds.isEmpty() && ev.noteId == session.noteId && note != null)
            }
            return !hasValidCitation
        }

        val now = System.currentTimeMillis()
        for (actEntity in activities) {
            val evidenceList = LearningEvidence.parseList(actEntity.evidenceJson)
            if (isEvidenceStale(evidenceList) && !actEntity.isStale) {
                activityDao.update(actEntity.copy(isStale = true, updatedAt = now))
            }
        }

        for (conceptEntity in concepts) {
            val evidenceList = LearningEvidence.parseList(conceptEntity.evidenceJson)
            if (isEvidenceStale(evidenceList) && !conceptEntity.isStale) {
                conceptDao.update(conceptEntity.copy(isStale = true, updatedAt = now))
            }
        }
    }

    suspend fun checkAndMarkStaleActivities(sessionId: String) = checkAndMarkStaleEntities(sessionId)
}
