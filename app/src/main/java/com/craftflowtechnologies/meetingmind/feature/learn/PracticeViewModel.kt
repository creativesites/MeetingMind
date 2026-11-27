package com.craftflowtechnologies.meetingmind.feature.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.learning.DueCard
import com.craftflowtechnologies.meetingmind.core.learning.RoundAnswer
import com.craftflowtechnologies.meetingmind.core.learning.RoundComposer
import com.craftflowtechnologies.meetingmind.core.learning.SessionSnapshot
import com.craftflowtechnologies.meetingmind.core.learning.SkyLayout
import com.craftflowtechnologies.meetingmind.core.learning.StudyCalendar
import com.craftflowtechnologies.meetingmind.core.learning.StudyFormat
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.core.model.DeterministicSpacedScheduler
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeState
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.RoundResult
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Which due items a practice round draws on. */
sealed interface PracticeScope {
    /** Everything due today, across every lesson. */
    data object AllDue : PracticeScope

    /** The due items of one lesson. */
    data class OneSession(val sessionId: String) : PracticeScope

    /** "Quiz me on this": every active item for one concept, due or not. */
    data class OneConcept(val sessionId: String, val conceptId: String) : PracticeScope
}

/**
 * A practice round: builds it from the scope, takes answers, re-queues a miss once, records every
 * attempt through [com.craftflowtechnologies.meetingmind.core.repository.LearningRepository] and
 * summarises the round at the end. Works offline: the round is built from stored items and kits.
 *
 * Actions: [answerChoice] for multiple choice, [reveal] and [rate] for recall, [notSure] to skip
 * with the answer shown, and [continueNext] to move on.
 */
class PracticeViewModel(
    application: Application,
    private val scope: PracticeScope = PracticeScope.AllDue,
    private val services: StudyServices = StudyServices.forApp(application)
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PracticeState(loading = true))
    val state: StateFlow<PracticeState> = _state.asStateFlow()

    private var queue: List<PracticeItem> = emptyList()
    private var index = 0
    private val cards = mutableMapOf<String, DueCard>()
    private val schedules = mutableMapOf<String, ReviewSchedule>()
    private val requeued = mutableSetOf<String>()
    private val firstTry = linkedMapOf<String, Boolean>()
    private val misses = mutableMapOf<String, Int>()
    private val nextDue = mutableMapOf<String, Long>()
    private val kits = mutableMapOf<String, StudyKit?>()
    private var sessionIds: Set<String> = emptySet()
    private var startedAt = 0L
    private var starsBefore: Map<String, StarState> = emptyMap()
    private val persistLock = Mutex()

    init {
        viewModelScope.launch { load() }
    }

    internal suspend fun load() {
        val now = services.now()
        startedAt = now
        val all = services.snapshots.sessions()
        val relevant = when (val s = scope) {
            PracticeScope.AllDue -> all
            is PracticeScope.OneSession -> all.filter { it.session.id == s.sessionId }
            is PracticeScope.OneConcept -> all.filter { it.session.id == s.sessionId }
        }
        sessionIds = relevant.map { it.session.id }.toSet()
        starsBefore = starsOf(all, now)

        val candidates = relevant.flatMap { snap ->
            kits[snap.session.id] = services.kits.load(snap.session.id)
            candidatesOf(snap, now)
        }
        val round = when (scope) {
            is PracticeScope.OneConcept -> candidates.sortedBy { it.schedule.dueAt }.take(RoundComposer.MAX_ITEMS)
            else -> RoundComposer.compose(candidates, now)
        }

        round.forEach { card ->
            cards[card.activity.id] = card
            schedules[card.activity.id] = card.schedule
        }
        queue = round.map { RoundComposer.toPracticeItem(it, kits[it.sessionId]) }
        index = 0
        val first = queue.firstOrNull()
        _state.value = PracticeState(
            loading = false,
            total = queue.size,
            index = 0,
            current = first,
            recallHints = first?.let { hintsFor(it) }.orEmpty(),
            emptyMessage = if (queue.isEmpty()) emptyMessageFor() else null
        )
    }

    /** Multiple choice: the learner picked option [i]. */
    fun answerChoice(i: Int) {
        val item = current() as? PracticeItem.Choice ?: return
        if (!canAnswer()) return
        val correct = i == item.correctIndex
        val why = if (correct) item.rightWhy else item.wrongWhys[i] ?: "The lecture's answer is: ${item.options[item.correctIndex]}."
        grade(item, response = item.options.getOrElse(i) { "" }, correct = correct, rating = null, chosen = i, why = why)
    }

    /** Recall: show the answer side. */
    fun reveal() {
        if (current() !is PracticeItem.Recall) return
        _state.update { it.copy(revealed = true) }
    }

    /** Recall: the learner rated how well they knew it. */
    fun rate(button: RecallButton) {
        val item = current() as? PracticeItem.Recall ?: return
        if (!canAnswer()) return
        grade(item, response = "", correct = button != RecallButton.MISSED, rating = button.rating, chosen = null, why = item.answer)
    }

    /** "Not sure": counts as a miss, and shows the answer. */
    fun notSure() {
        when (val item = current()) {
            is PracticeItem.Choice -> if (canAnswer()) {
                grade(item, response = "", correct = false, rating = null, chosen = null, why = item.rightWhy)
            }
            is PracticeItem.Recall -> if (canAnswer()) {
                grade(item, response = "", correct = false, rating = RecallRating.AGAIN, chosen = null, why = item.answer)
            }
            null -> Unit
        }
    }

    /** Moves on after feedback. After the last item the round's result is worked out. */
    fun continueNext() {
        if (_state.value.feedback == null) return
        val next = index + 1
        if (next >= queue.size) {
            finish()
        } else {
            index = next
            val item = queue[next]
            _state.update {
                it.copy(
                    index = next,
                    total = queue.size,
                    current = item,
                    revealed = false,
                    feedback = null,
                    recallHints = hintsFor(item)
                )
            }
        }
    }

    // ---------------------------------------------------------------- internals

    private fun current(): PracticeItem? = _state.value.current

    private fun canAnswer(): Boolean = _state.value.feedback == null && _state.value.result == null

    private fun grade(
        item: PracticeItem,
        response: String,
        correct: Boolean,
        rating: RecallRating?,
        chosen: Int?,
        why: String
    ) {
        val id = item.activityId
        val now = services.now()
        val before = schedules[id] ?: return
        val effective = rating ?: if (correct) RecallRating.GOOD else RecallRating.AGAIN
        val after = DeterministicSpacedScheduler.calculateNextSchedule(before, correct, effective, now)
        schedules[id] = after
        nextDue[id] = after.dueAt
        firstTry.putIfAbsent(id, correct)
        if (!correct) misses[id] = (misses[id] ?: 0) + 1

        var comesBack = false
        if (!correct && id !in requeued) {
            requeued += id
            queue = RoundComposer.requeue(queue, index, item, alreadyRequeued = false)
            comesBack = true
        }
        val nextLine = when {
            correct -> "Next time: in ${DeterministicSpacedScheduler.formatIntervalDays(after.intervalDays)}"
            comesBack -> "This one comes back in ${RoundComposer.REQUEUE_OFFSET} questions"
            else -> "Next time: later today"
        }
        _state.update {
            it.copy(
                total = queue.size,
                revealed = if (item is PracticeItem.Recall) true else it.revealed,
                feedback = AnswerFeedback(chosenIndex = chosen, correct = correct, why = why, nextLine = nextLine)
            )
        }
        persist(id, response, correct, rating, why)
    }

    private fun persist(activityId: String, response: String, correct: Boolean, rating: RecallRating?, why: String) {
        viewModelScope.launch {
            persistLock.withLock {
                services.learning.recordAttempt(
                    activityId = activityId,
                    userResponse = response,
                    isCorrect = correct,
                    selfRating = rating,
                    feedback = why
                )
            }
        }
    }

    private fun finish() {
        viewModelScope.launch {
            // Wait for every attempt to be written before reading the sky back.
            persistLock.withLock { }
            val now = services.now()
            val all = services.snapshots.sessions()
            val after = starsOf(all, now)
            val newStars = starsBefore.filter { (id, star) -> star != StarState.SHINING && after[id] == StarState.SHINING }
                .keys.mapNotNull { id -> all.flatMap { it.concepts }.firstOrNull { it.id == id }?.name }
            val remaining = all.sumOf { it.dueCards(now).size }
            val allConcepts = all.flatMap { it.concepts }
            val sky = SkyLayout.build(
                concepts = allConcepts,
                attemptsByConcept = all.flatMap { it.attemptsByConcept.entries }.associate { it.key to it.value },
                schedulesByConcept = all.flatMap { it.scheduleByConcept.entries }.associate { it.key to it.value },
                now = now
            )
            val answers = firstTry.map { (id, ok) ->
                val card = cards[id]
                RoundAnswer(
                    activityId = id,
                    conceptId = card?.activity?.conceptId,
                    conceptName = card?.concept?.name,
                    sessionId = card?.sessionId.orEmpty(),
                    firstTryCorrect = ok,
                    misses = misses[id] ?: 0,
                    nextDueAt = nextDue[id]
                )
            }
            val result: RoundResult = RoundComposer.summarize(
                answers = answers,
                total = cards.size,
                newStars = newStars,
                dueRemaining = remaining,
                elapsedMs = now - startedAt,
                studiedDaysThisWeek = StudyCalendar.daysThisWeek(all.flatMap { it.attempts }.map { it.createdAt }, now),
                sessionTitles = all.associate { it.session.id to it.session.title },
                now = now,
                sky = sky
            )
            _state.update { it.copy(result = result, feedback = null, revealed = false, current = null) }
        }
    }

    private fun candidatesOf(snap: SessionSnapshot, now: Long): List<DueCard> {
        val pairs: List<Pair<LearningActivity, ReviewSchedule>> = when (val s = scope) {
            is PracticeScope.OneConcept -> snap.activities
                .filter { it.conceptId == s.conceptId && !it.isStale }
                .mapNotNull { a -> snap.schedules[a.id]?.let { a to it } }
            else -> snap.dueCards(now)
        }
        return pairs.map { (activity, schedule) ->
            val concept: LearningConcept? = snap.concepts.firstOrNull { it.id == activity.conceptId }
            DueCard(
                activity = activity,
                schedule = schedule,
                concept = concept,
                sessionId = snap.session.id,
                sourceLabel = StudyFormat.sourceLabel(snap.session.courseName, snap.session.title, concept?.name),
                meetingId = snap.session.meetingId,
                mastery = concept?.let { snap.masteryOf(it, now) }
            )
        }
    }

    private fun starsOf(snaps: List<SessionSnapshot>, now: Long): Map<String, StarState> {
        val attempts = snaps.flatMap { it.attemptsByConcept.entries }.associate { it.key to it.value }
        val schedules = snaps.flatMap { it.scheduleByConcept.entries }.associate { it.key to it.value }
        return snaps.flatMap { it.concepts }.associate { c ->
            c.id to SkyLayout.conceptStar(attempts[c.id].orEmpty(), schedules[c.id], now)
        }
    }

    private fun hintsFor(item: PracticeItem): Map<RecallButton, String> {
        if (item !is PracticeItem.Recall) return emptyMap()
        val schedule = schedules[item.activityId]
        val now = services.now()
        return RecallButton.values().associateWith { button ->
            when (val preview = DeterministicSpacedScheduler.previewInterval(schedule, button.rating, now)) {
                "<1d" -> "Back later today"
                "1d" -> "Back tomorrow"
                else -> "Back in $preview"
            }
        }
    }

    private fun emptyMessageFor(): String = when (scope) {
        PracticeScope.AllDue -> "All caught up. Nice work."
        is PracticeScope.OneSession, is PracticeScope.OneConcept -> "There is nothing to practise here yet."
    }
}
