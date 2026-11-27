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
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.feature.learn.practice.PracticeBuilder
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeFormat
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticePlanItem
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeSetSize
import com.craftflowtechnologies.meetingmind.feature.learning.space.buildPracticeDeck
import com.craftflowtechnologies.meetingmind.feature.learning.space.clozeMatches
import com.craftflowtechnologies.meetingmind.feature.learning.space.nativeVariant
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

    /** The due items of every lesson in one course. Sessions carry the course name, so it is the key. */
    data class OneCourse(val courseName: String) : PracticeScope

    /** "Quiz me on this": every active item for one concept, due or not. */
    data class OneConcept(val sessionId: String, val conceptId: String) : PracticeScope
}

/**
 * The one practice engine. It builds a round with Learning's planner ([buildPracticeDeck]: formats
 * follow each concept's mastery, no format twice in a row, at most [PracticeSetSize] items interleaved
 * across lessons), turns each planned variant into a [PracticeItem] ([PracticeBuilder]), takes
 * answers, re-queues a miss once, records every attempt through [StudyServices.learning] (the same
 * tables Learning reads), and summarises the round with [RoundComposer] and [SkyLayout].
 *
 * Works offline: the round is built from stored concepts, activities and kits.
 *
 * Actions: [answer] for the answerable formats (options, true or false, fill, match, order, short
 * answer typed), [reveal] and [rate] for the self-rated ones (recall, worked, short, explain),
 * [notSure] to skip with the answer shown, and [continueNext] to move on.
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
    /** How each activity is asked this round, written to the attempt so the next round asks it another way. */
    private val formats = mutableMapOf<String, PracticeFormat>()
    private val schedules = mutableMapOf<String, ReviewSchedule>()
    private val requeued = mutableSetOf<String>()
    private val firstTry = linkedMapOf<String, Boolean>()
    private val misses = mutableMapOf<String, Int>()
    private val nextDue = mutableMapOf<String, Long>()
    private val kits = mutableMapOf<String, com.craftflowtechnologies.meetingmind.feature.learn.StudyKit?>()
    private var sessionIds: Set<String> = emptySet()
    private var startedAt = 0L
    private var starsBefore: Map<String, StarState> = emptyMap()
    private val persistLock = Mutex()
    /** A short answer as typed, saved with the learner's rating. */
    private var typedResponse = ""

    init {
        viewModelScope.launch { load() }
    }

    internal suspend fun load() {
        val now = services.now()
        startedAt = now
        val all = services.snapshots.sessions()
        val relevant = sessionsIn(all)
        sessionIds = relevant.map { it.session.id }.toSet()
        starsBefore = starsOf(all, now)

        val candidates = relevant.flatMap { snap ->
            kits[snap.session.id] = services.kits.load(snap.session.id)
            candidatesOf(snap, now)
        }
        val ordered = if (scope is PracticeScope.OneConcept) {
            candidates.sortedBy { it.schedule.dueAt }
        } else {
            candidates.sortedWith(compareBy({ !isPriority(it, now) }, { it.schedule.dueAt }, { it.activity.id }))
        }
        val byId = ordered.associateBy { it.activity.id }
        val siblingNames = relevant.flatMap { it.concepts }.map { it.name }.distinct()

        val plan = planRound(ordered, relevant, now)
        queue = plan.mapNotNull { item ->
            val card = byId[item.activity.id] ?: return@mapNotNull null
            cards[card.activity.id] = card
            schedules[card.activity.id] = card.schedule
            formats[card.activity.id] = item.variant.format
            PracticeBuilder.toItem(item.variant, card, kits[card.sessionId], siblingNames)
        }
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

    /**
     * Plans the round. Candidates arrive in priority order (overdue and needs-review first); the
     * planner picks each one's format from its mastery and history, and interleaves lessons.
     */
    private fun planRound(candidates: List<DueCard>, relevant: List<SessionSnapshot>, now: Long): List<PracticePlanItem> {
        if (candidates.isEmpty()) return emptyList()
        val oneConcept = scope is PracticeScope.OneConcept
        val concepts: Map<String, LearningConcept> = relevant.flatMap { it.concepts }.associateBy { it.id }
        val attempts = relevant.flatMap { it.attempts }
        val siblings: List<LearningActivity> = relevant.flatMap { it.activities }.distinctBy { it.id }
        return runCatching {
            buildPracticeDeck(
                activities = candidates.map { it.activity },
                concepts = concepts,
                attempts = attempts,
                maxItems = PracticeSetSize,
                seed = now / DeterministicSpacedScheduler.DAY_MS * 31,
                interleave = !oneConcept,
                rampUp = oneConcept,
                siblingActivities = siblings
            )
        }.getOrElse {
            // If planning fails, the items are asked as written, so the round always opens.
            candidates.take(PracticeSetSize).map { PracticePlanItem(it.activity, nativeVariant(it.activity)) }
        }
    }

    /** "Hear your lecturer": plays the item's source clip, then pauses at its end. */
    fun playSource(item: PracticeItem) {
        val meetingId = item.meetingId ?: return
        val start = item.sourceStartMs ?: return
        viewModelScope.launch {
            val meeting = services.meetings.getMeetingByIdDirect(meetingId) ?: return@launch
            val file = meeting.audioFilePath?.let { java.io.File(it) }?.takeIf { it.exists() } ?: return@launch
            val controller = com.craftflowtechnologies.meetingmind.core.audio.PlaybackController
            controller.playAt(getApplication(), meetingId, meeting.title, file, start)
            val end = item.sourceEndMs ?: (start + DEFAULT_CLIP_MS)
            kotlinx.coroutines.delay((end - start).coerceIn(2_000L, 30_000L))
            val ps = controller.state.value
            if (ps.recordingId == meetingId && ps.isPlaying) controller.pause()
        }
    }

    /**
     * The learner's answer to the current item. Options and true or false take [PracticeAnswer.Pick];
     * fill the gap [PracticeAnswer.Fill]; match [PracticeAnswer.Match]; order [PracticeAnswer.Order].
     * A typed short answer is saved and reveals the model answer; the learner then rates it.
     */
    fun answer(answer: PracticeAnswer) {
        val item = current() ?: return
        if (!canAnswer()) return
        when (answer) {
            is PracticeAnswer.Pick -> when (item) {
                is PracticeItem.Choice -> gradePick(item, answer.index, item.correctIndex, item.rightWhy) {
                    item.wrongWhys[it] ?: "The lecture's answer is: ${item.options[item.correctIndex]}."
                }
                is PracticeWhichIdea -> gradePick(item, answer.index, item.correctIndex, item.rightWhy) {
                    item.wrongWhys[it] ?: "The idea here is: ${item.options[item.correctIndex]}."
                }
                is PracticeTrueFalse -> gradePick(item, answer.index, item.correctIndex, item.rightWhy) { item.wrongWhy }
                else -> Unit
            }
            is PracticeAnswer.Fill -> (item as? PracticeFillGap)?.let { gradeFill(it, answer.word) }
            is PracticeAnswer.Match -> (item as? PracticeMatch)?.let { gradeMatch(it, answer.termToMeaning) }
            is PracticeAnswer.Order -> (item as? PracticeOrder)?.let { gradeOrder(it, answer.order) }
            is PracticeAnswer.Typed -> if (item is PracticeShortAnswer) {
                typedResponse = answer.text
                _state.update { it.copy(revealed = true) }
            }
        }
    }

    /** The items of the round in order, including any re-queued miss. For tests and diagnostics. */
    internal fun roundItems(): List<PracticeItem> = queue

    /** The format each item of the round is asked in, in order. For tests and diagnostics. */
    internal fun roundFormats(): List<PracticeFormat> = queue.mapNotNull { formats[it.activityId] }

    /** Multiple choice: the learner picked option [i]. */
    fun answerChoice(i: Int) = answer(PracticeAnswer.Pick(i))

    /** Self-rated items: show the answer side. */
    fun reveal() {
        if (current() == null) return
        _state.update { it.copy(revealed = true) }
    }

    /** Self-rated items: the learner rated how well they knew it. */
    fun rate(button: RecallButton) {
        val item = current() ?: return
        if (!canAnswer() || !isRateable(item)) return
        grade(item, typedResponse, correct = button != RecallButton.MISSED, rating = button.rating, chosen = null, why = answerText(item))
    }

    /** "Not sure": counts as a miss, and shows the answer. */
    fun notSure() {
        val item = current() ?: return
        if (!canAnswer()) return
        if (isRateable(item)) {
            grade(item, "", correct = false, rating = RecallRating.AGAIN, chosen = null, why = answerText(item))
        } else {
            grade(item, "", correct = false, rating = null, chosen = null, why = answerText(item))
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
            typedResponse = ""
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

    // ---------------------------------------------------------------- grading

    private fun current(): PracticeItem? = _state.value.current

    private fun canAnswer(): Boolean = _state.value.feedback == null && _state.value.result == null

    private fun gradePick(
        item: PracticeItem,
        picked: Int,
        correctIndex: Int,
        rightWhy: String,
        wrongWhy: (Int) -> String
    ) {
        val correct = picked == correctIndex
        grade(item, response = "", correct = correct, rating = null, chosen = picked, why = if (correct) rightWhy else wrongWhy(picked))
    }

    private fun gradeFill(item: PracticeFillGap, word: String) {
        val correct = clozeMatches(item.answer, word)
        grade(item, response = word, correct = correct, rating = null, chosen = null, why = item.fullLine)
    }

    private fun gradeMatch(item: PracticeMatch, termToMeaning: Map<Int, Int>) {
        val correct = termToMeaning.size == item.terms.size && item.terms.indices.all { termToMeaning[it] == it }
        val why = if (correct) "Each term goes with its meaning." else
            item.terms.indices.joinToString("\n") { "${item.terms[it]}: ${item.meanings[it]}" }
        grade(item, response = "matched ${termToMeaning.size} of ${item.terms.size}", correct = correct, rating = null, chosen = null, why = why)
    }

    private fun gradeOrder(item: PracticeOrder, order: List<Int>) {
        val correct = order == item.steps.indices.toList()
        val why = if (correct) "That is the order in the lecture." else
            "The order in the lecture:\n" + item.steps.withIndex().joinToString("\n") { "${it.index + 1}. ${it.value}" }
        grade(item, response = order.joinToString(","), correct = correct, rating = null, chosen = null, why = why)
    }

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
                revealed = if (item is PracticeItem.Recall || isRateable(item)) true else it.revealed,
                feedback = AnswerFeedback(chosenIndex = chosen, correct = correct, why = why, nextLine = nextLine)
            )
        }
        persist(id, response, correct, rating, why)
    }

    private fun persist(activityId: String, response: String, correct: Boolean, rating: RecallRating?, why: String) {
        // The format goes first, so Learning's ladder and no-repeat rule see how this item was asked.
        val format = formats[activityId]
        val feedback = if (format != null) "${format.tag}; $why" else why
        viewModelScope.launch {
            persistLock.withLock {
                services.learning.recordAttempt(
                    activityId = activityId,
                    userResponse = response,
                    isCorrect = correct,
                    selfRating = rating,
                    feedback = feedback
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

    // ---------------------------------------------------------------- scope and candidates

    private fun sessionsIn(all: List<SessionSnapshot>): List<SessionSnapshot> = when (val s = scope) {
        PracticeScope.AllDue -> all
        is PracticeScope.OneSession -> all.filter { it.session.id == s.sessionId }
        is PracticeScope.OneCourse -> all.filter { it.session.courseName == s.courseName }
        is PracticeScope.OneConcept -> all.filter { it.session.id == s.sessionId }
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

    /** Overdue by more than a day, or needs review: these go first in an all-due round. */
    private fun isPriority(card: DueCard, now: Long): Boolean =
        card.mastery == LearningMasteryState.NEEDS_REVIEW || now - card.schedule.dueAt > DeterministicSpacedScheduler.DAY_MS

    private fun starsOf(snaps: List<SessionSnapshot>, now: Long): Map<String, StarState> {
        val attempts = snaps.flatMap { it.attemptsByConcept.entries }.associate { it.key to it.value }
        val schedules = snaps.flatMap { it.scheduleByConcept.entries }.associate { it.key to it.value }
        return snaps.flatMap { it.concepts }.associate { c ->
            c.id to SkyLayout.conceptStar(attempts[c.id].orEmpty(), schedules[c.id], now)
        }
    }

    // ---------------------------------------------------------------- display helpers

    /** Formats with a rating: recall, worked example, short answer and explain. */
    private fun isRateable(item: PracticeItem): Boolean =
        item is PracticeItem.Recall || item is PracticeWorked || item is PracticeShortAnswer || item is PracticeExplain

    /** The right answer as one line, shown with a miss. */
    private fun answerText(item: PracticeItem): String = when (item) {
        is PracticeItem.Recall -> item.answer
        is PracticeItem.Choice -> item.rightWhy
        is PracticeWhichIdea -> item.rightWhy
        is PracticeTrueFalse -> item.rightWhy
        is PracticeFillGap -> item.fullLine
        is PracticeMatch -> item.terms.indices.joinToString("\n") { "${item.terms[it]}: ${item.meanings[it]}" }
        is PracticeOrder -> item.steps.joinToString("\n")
        is PracticeWorked -> item.answer
        is PracticeShortAnswer -> item.modelAnswer
        is PracticeExplain -> item.modelAnswer
    }

    private fun hintsFor(item: PracticeItem): Map<RecallButton, String> {
        if (!isRateable(item)) return emptyMap()
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
        is PracticeScope.OneSession, is PracticeScope.OneCourse, is PracticeScope.OneConcept -> "There is nothing to practise here yet."
    }

    private companion object {
        /** Clip length when an item has no end time. */
        const val DEFAULT_CLIP_MS = 8_000L
    }
}
