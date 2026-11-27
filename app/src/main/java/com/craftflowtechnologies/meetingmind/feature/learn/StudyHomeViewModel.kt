package com.craftflowtechnologies.meetingmind.feature.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.learning.DueCard
import com.craftflowtechnologies.meetingmind.core.learning.RoundComposer
import com.craftflowtechnologies.meetingmind.core.learning.SessionSnapshot
import com.craftflowtechnologies.meetingmind.core.learning.SkyLayout
import com.craftflowtechnologies.meetingmind.core.learning.StudyCalendar
import com.craftflowtechnologies.meetingmind.core.learning.StudyFormat
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The Study home: the round due today, the sky, the lesson to continue, courses and the week.
 * Works offline: it only reads the Learning tables and the stored kits' existence through
 * [StudyServices].
 */
class StudyHomeViewModel(
    application: Application,
    private val services: StudyServices = StudyServices.forApp(application)
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(StudyHomeState(loading = true))
    val state: StateFlow<StudyHomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads everything. Call when the home comes back on screen, and after a round. */
    fun refresh() {
        viewModelScope.launch { _state.value = load() }
    }

    internal suspend fun load(): StudyHomeState {
        val now = services.now()
        val zone = ZoneId.systemDefault()
        val sessions = services.snapshots.sessions()
        val due = sessions.flatMap { dueCardsOf(it, now) }

        val allAttempts = sessions.flatMap { it.attempts }
        val sky = SkyLayout.build(
            concepts = sessions.flatMap { it.concepts },
            attemptsByConcept = sessions.flatMap { it.attemptsByConcept.entries }.associate { it.key to it.value },
            schedulesByConcept = sessions.flatMap { it.scheduleByConcept.entries }.associate { it.key to it.value },
            now = now
        )

        val dueCount = due.size
        val round = if (dueCount == 0) null else RoundSummary(
            dueCount = dueCount,
            minutes = RoundComposer.estimateMinutes(dueCount.coerceAtMost(RoundComposer.MAX_ITEMS)),
            sourceTitles = due.map { it.sourceTitle }.distinct()
        )

        val byRecency = sessions.sortedByDescending { it.session.lastStudiedAt ?: it.session.updatedAt }
        val continueLesson = byRecency.firstOrNull()?.let { continueFor(it) }

        val courses = sessions.groupBy { it.session.courseName?.takeIf { name -> name.isNotBlank() } ?: OTHER_LESSONS }
            .map { (name, group) -> courseTile(name, group, due, now) }

        return StudyHomeState(
            loading = false,
            round = round,
            sky = sky,
            continueLesson = continueLesson,
            courses = courses,
            studiedDays = StudyCalendar.studiedDays(allAttempts.map { it.createdAt }, now, zone),
            todayIndex = StudyCalendar.todayIndex(now, zone),
            companionLine = companionLine(dueCount),
            hasAnyLesson = sessions.isNotEmpty(),
            recentLessons = byRecency.take(RECENT_LIMIT).map { continueFor(it) }
        )
    }

    /** A due card with the session title it belongs to, for the round summary. */
    private class DueItem(val sourceTitle: String, val card: DueCard)

    private fun dueCardsOf(snap: SessionSnapshot, now: Long): List<DueItem> =
        snap.dueCards(now).map { (activity, schedule) ->
            val concept = snap.concepts.firstOrNull { it.id == activity.conceptId }
            DueItem(
                sourceTitle = snap.session.title,
                card = DueCard(
                    activity = activity,
                    schedule = schedule,
                    concept = concept,
                    sessionId = snap.session.id,
                    sourceLabel = StudyFormat.sourceLabel(snap.session.courseName, snap.session.title, concept?.name),
                    meetingId = snap.session.meetingId,
                    mastery = concept?.let { snap.masteryOf(it, now) }
                )
            )
        }

    private fun continueFor(snap: SessionSnapshot): ContinueLesson {
        val conceptIndex = snap.concepts.withIndex().associate { it.value.id to it.index }
        val latest = snap.attempts.maxByOrNull { it.createdAt }
        val stoppedAt = latest?.conceptId?.let { id ->
            val concept = snap.concepts.firstOrNull { it.id == id }
            concept?.let { "${(conceptIndex[id] ?: 0) + 1} · ${it.name}" }
        }
        val touched = snap.concepts.count { snap.attemptsByConcept.containsKey(it.id) }
        val progress = if (snap.concepts.isEmpty()) 0f else touched.toFloat() / snap.concepts.size
        return ContinueLesson(
            sessionId = snap.session.id,
            title = snap.session.title,
            courseName = snap.session.courseName,
            stoppedAt = stoppedAt,
            progress = progress
        )
    }

    private fun courseTile(name: String, group: List<SessionSnapshot>, due: List<DueItem>, now: Long): CourseTile {
        val worth = group.sumOf { snap -> snap.concepts.count { snap.masteryOf(it, now) == LearningMasteryState.NEEDS_REVIEW } }
        val dueHere = due.count { d -> group.any { it.session.id == d.card.sessionId } }
        val status = when {
            worth > 0 -> "$worth worth a look"
            dueHere == 0 -> "All caught up"
            else -> "${group.size} lessons"
        }
        return CourseTile(
            name = name,
            code = StudyFormat.courseCode(name),
            status = status,
            needsAttention = worth > 0
        )
    }

    private fun companionLine(dueCount: Int): String = when (dueCount) {
        0 -> "All caught up. Nice work."
        1 -> "1 card is due. A quick round?"
        else -> "$dueCount cards are due. A quick round?"
    }

    private companion object {
        const val OTHER_LESSONS = "Other lessons"
        const val RECENT_LIMIT = 5
    }
}
