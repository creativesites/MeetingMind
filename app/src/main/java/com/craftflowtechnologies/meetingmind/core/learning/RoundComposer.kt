package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.feature.learn.NextStep
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.RoundResult
import com.craftflowtechnologies.meetingmind.feature.learn.SkyState
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/** One due activity, with everything a practice item and its source label need. */
data class DueCard(
    val activity: LearningActivity,
    val schedule: ReviewSchedule,
    val concept: LearningConcept?,
    val sessionId: String,
    /** e.g. "BIO 201 · Inhibition", or the session title when there is no course. */
    val sourceLabel: String,
    val meetingId: String?,
    val mastery: LearningMasteryState? = null
)

/** One answer in a finished round, first try or not. */
data class RoundAnswer(
    val activityId: String,
    val conceptId: String?,
    val conceptName: String?,
    val sessionId: String,
    val firstTryCorrect: Boolean,
    /** Misses on this activity during the round, including the first try. */
    val misses: Int,
    /** When the activity is next due, after the round's attempts were recorded. */
    val nextDueAt: Long?
)

/**
 * Builds a practice round and summarises it (PLAN_LEARNING §1.2, §4). Pure.
 *
 * A round holds at most [MAX_ITEMS] due items. Overdue and worth-a-look items come first; within
 * each priority tier the round takes turns between lessons, so one lesson does not fill the round.
 */
object RoundComposer {
    const val MAX_ITEMS = 8

    /** A missed item is asked again this many places later in the round, once. */
    const val REQUEUE_OFFSET = 3

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Overdue means more than a day past its due time. */
    private const val OVERDUE_MS = DAY_MS

    /** Items whose next due time is at least this far away count as "back tomorrow". */
    private const val BACK_LATER_MS = 12 * 60 * 60 * 1000L

    /** Pure: the round for [due], most important first. */
    fun compose(due: List<DueCard>, now: Long): List<DueCard> {
        val ordered = due.sortedWith(compareBy({ it.schedule.dueAt }, { it.activity.id }))
        val tier0 = ordered.filter { isPriority(it, now) }
        val tier1 = ordered.filterNot { isPriority(it, now) }
        return (interleaveBySession(tier0) + interleaveBySession(tier1)).take(MAX_ITEMS)
    }

    private fun isPriority(card: DueCard, now: Long): Boolean =
        card.mastery == LearningMasteryState.NEEDS_REVIEW || now - card.schedule.dueAt > OVERDUE_MS

    /** Round-robin across sessions, keeping each session's own order and first-appearance order. */
    private fun interleaveBySession(cards: List<DueCard>): List<DueCard> {
        val buckets = LinkedHashMap<String, ArrayDeque<DueCard>>()
        cards.forEach { buckets.getOrPut(it.sessionId) { ArrayDeque() }.addLast(it) }
        val out = mutableListOf<DueCard>()
        while (buckets.values.any { it.isNotEmpty() }) {
            for (bucket in buckets.values) bucket.removeFirstOrNull()?.let { out += it }
        }
        return out
    }

    /**
     * Pure: puts a missed item back [REQUEUE_OFFSET] places after [currentIndex]. Each item is
     * re-queued at most once; pass [alreadyRequeued] = true when it has been before.
     */
    fun requeue(queue: List<PracticeItem>, currentIndex: Int, item: PracticeItem, alreadyRequeued: Boolean): List<PracticeItem> {
        if (alreadyRequeued) return queue
        val at = (currentIndex + REQUEUE_OFFSET).coerceAtMost(queue.size)
        return queue.toMutableList().apply { add(at, item) }
    }

    /** Pure: a practice item for a due card, with its explanations taken from the kit when there is one. */
    fun toPracticeItem(card: DueCard, kit: StudyKit?): PracticeItem {
        val a = card.activity
        val start = a.evidence.firstOrNull()?.startMs
        val end = a.evidence.firstOrNull()?.endMs
        val why = kit?.rightWhys?.get(a.id)
        return when (a.type) {
            LearningActivityType.MULTIPLE_CHOICE -> {
                val correct = a.options.indexOf(a.expectedAnswer).coerceAtLeast(0)
                val wrong: Map<Int, String> = kit?.optionWhys
                    ?.filter { it.activityId == a.id }
                    ?.associate { it.optionIndex to it.why }
                    .orEmpty()
                PracticeItem.Choice(
                    activityId = a.id,
                    conceptId = a.conceptId,
                    sourceLabel = card.sourceLabel,
                    sourceStartMs = start,
                    sourceEndMs = end,
                    meetingId = card.meetingId,
                    prompt = a.prompt,
                    options = a.options,
                    correctIndex = correct,
                    rightWhy = why?.takeIf { it.isNotBlank() } ?: "The lecture's answer is: ${a.expectedAnswer}.",
                    wrongWhys = wrong
                )
            }
            else -> PracticeItem.Recall(
                activityId = a.id,
                conceptId = a.conceptId,
                sourceLabel = card.sourceLabel,
                sourceStartMs = start,
                sourceEndMs = end,
                meetingId = card.meetingId,
                prompt = a.prompt,
                answer = a.expectedAnswer
            )
        }
    }

    /** Minutes a round of [cards] takes, as shown on the home screen before the learner starts. */
    fun estimateMinutes(cards: Int): Int = if (cards <= 0) 0 else ceil(cards * 0.75).toInt().coerceAtLeast(1)

    /**
     * Pure: summarises a finished round.
     *
     * [dueRemaining] is the number of due activities still waiting after this round. The round is
     * "all cleared" when none remain. [studiedDaysThisWeek] comes from [StudyCalendar].
     */
    fun summarize(
        answers: List<RoundAnswer>,
        total: Int,
        newStars: List<String>,
        dueRemaining: Int,
        elapsedMs: Long,
        studiedDaysThisWeek: Int,
        sessionTitles: Map<String, String>,
        now: Long,
        sky: SkyState = SkyState.Empty
    ): RoundResult {
        val missedByConcept = answers
            .filter { it.misses > 0 && it.conceptId != null }
            .groupBy { it.conceptId!! }
            .mapValues { (_, list) -> list.sumOf { it.misses } }
        val worst = missedByConcept.maxByOrNull { it.value }
        val nextStep = worst?.let { (conceptId, misses) ->
            val sample = answers.first { it.conceptId == conceptId }
            val name = sample.conceptName ?: "this idea"
            NextStep(
                sessionId = sample.sessionId,
                conceptId = conceptId,
                title = "Practise $name",
                detail = if (misses == 1) {
                    "You missed this once in this round. Start with its lesson."
                } else {
                    "You missed it $misses times in this round. Start with its lesson."
                }
            )
        }
        val backTomorrow = answers.count { a -> a.nextDueAt?.let { it - now >= BACK_LATER_MS } == true }
        return RoundResult(
            total = total,
            firstTryCorrect = answers.count { it.firstTryCorrect },
            newStars = newStars,
            backTomorrow = backTomorrow,
            minutes = ((elapsedMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1),
            daysThisWeek = studiedDaysThisWeek,
            nextStep = nextStep,
            allCleared = dueRemaining <= 0,
            sky = sky
        )
    }
}

/** Calendar facts for the Study home: which days of this week the learner studied. */
object StudyCalendar {

    /** Monday = 0 … Sunday = 6, for [now] in [zone]. */
    fun todayIndex(now: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).dayOfWeek.value - 1

    /** Seven flags, Monday first: whether any of [timestamps] falls on that day of the current week. */
    fun studiedDays(timestamps: List<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Boolean> {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val days = timestamps.map { LocalDate.ofInstant(Instant.ofEpochMilli(it), zone) }.toSet()
        return List(7) { i -> days.contains(monday.plusDays(i.toLong())) }
    }

    fun daysThisWeek(timestamps: List<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        studiedDays(timestamps, now, zone).count { it }
}
