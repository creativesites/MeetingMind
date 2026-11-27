package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.MasteryCalculator
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import kotlinx.coroutines.flow.first

/** One Learning session with everything the Study screens read about it. */
data class SessionSnapshot(
    val session: LearningSession,
    val concepts: List<LearningConcept>,
    val activities: List<LearningActivity>,
    /** Keyed by activity id. Every saved activity has one. */
    val schedules: Map<String, ReviewSchedule>,
    val attempts: List<ActivityAttempt>
) {
    val attemptsByConcept: Map<String, List<ActivityAttempt>> =
        attempts.filter { it.conceptId != null }.groupBy { it.conceptId!! }

    /** The most overdue schedule per concept: the one that decides its state. */
    val scheduleByConcept: Map<String, ReviewSchedule> = activities
        .filter { it.conceptId != null }
        .mapNotNull { a -> schedules[a.id]?.let { a.conceptId!! to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> list.minBy { it.dueAt } }

    fun masteryOf(concept: LearningConcept, now: Long): LearningMasteryState {
        val attempts = attemptsByConcept[concept.id].orEmpty()
        if (attempts.isEmpty()) return LearningMasteryState.NEW
        return MasteryCalculator.evaluate(attempts, scheduleByConcept[concept.id], now).state
    }

    /** Active activities (not dismissed, not stale) whose review is due now. */
    fun dueCards(now: Long): List<Pair<LearningActivity, ReviewSchedule>> = activities
        .filter { !it.isStale }
        .mapNotNull { a -> schedules[a.id]?.takeIf { it.isDue(now) }?.let { a to it } }
}

/**
 * Loads what the Study screens need from the Learning tables, once per call. Every read goes
 * through [StudyRepository], so nothing here needs the network.
 */
class StudySnapshotLoader(private val learning: StudyRepository) {

    suspend fun sessions(): List<SessionSnapshot> = learning.observeAllSessions().first().map { load(it) }

    suspend fun session(sessionId: String): SessionSnapshot? =
        learning.getSession(sessionId)?.let { load(it) }

    private suspend fun load(session: LearningSession): SessionSnapshot {
        val concepts = learning.getConcepts(session.id)
        val activities = learning.getActivities(session.id)
        val schedules = activities.mapNotNull { a -> learning.getSchedule(a.id)?.let { a.id to it } }.toMap()
        val attempts = learning.getAttemptsForSession(session.id)
        return SessionSnapshot(session, concepts, activities, schedules, attempts)
    }
}
