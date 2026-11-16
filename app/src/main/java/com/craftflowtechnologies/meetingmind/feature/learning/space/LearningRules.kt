package com.craftflowtechnologies.meetingmind.feature.learning.space

import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import kotlin.math.ceil

/**
 * The rules behind the Learning space (design/learning-vertical-design-spec.md). They are kept
 * apart from the UI so they can be unit-tested: a bounded daily plan, the session's next step, and
 * honest answer checking.
 */

/** Release 1 practice formats. Teach-Back is Release 1.1 and is never offered here (decision D3). */
val Release1PracticeTypes: Set<LearningActivityType> = setOf(
    LearningActivityType.RECALL,
    LearningActivityType.MULTIPLE_CHOICE,
    LearningActivityType.APPLICATION,
    LearningActivityType.WORKED_PROBLEM
)

fun LearningActivity.isRelease1Practice(): Boolean =
    type in Release1PracticeTypes && !isDismissed && !isStale

/** Seconds a student typically spends on one item, by format. Shown only as rounded minutes. */
fun LearningActivity.estimatedSeconds(): Int = when (type) {
    LearningActivityType.RECALL -> 50
    LearningActivityType.MULTIPLE_CHOICE -> 60
    else -> 90
}

/**
 * A session's practice items, without those on concepts the student hid or reported. Reported
 * concepts are held back until they are edited or regenerated.
 */
fun practiceItems(detail: LearningRepository.LearningSessionDetail): List<LearningActivity> {
    val heldBack = detail.concepts.filter { it.isDismissed || it.isStale }.map { it.id }.toSet()
    return detail.activities.filter { it.isRelease1Practice() && it.conceptId !in heldBack }
}

fun minutesFor(activities: List<LearningActivity>): Int =
    ceil(activities.sumOf { it.estimatedSeconds() } / 60.0).toInt().coerceAtLeast(if (activities.isEmpty()) 0 else 1)

// ───────────────────────────── Daily plan ─────────────────────────────

enum class PlanKind { RECALL, STRENGTHEN, DIAGNOSTIC }

data class PlanItem(
    val kind: PlanKind,
    val title: String,
    val detail: String,
    val minutes: Int,
    val sessionId: String? = null,
    val noteId: String? = null,
    val concept: LearningConcept? = null
)

data class TodayPlan(
    val items: List<PlanItem>,
    val minutes: Int,
    /** Why this plan, in one plain sentence. */
    val reason: String
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/** At most this many review cards in one day's plan, so the plan never turns into a backlog. */
const val MaxPlanCards = 15
const val QuickPlanCards = 5

/**
 * Builds today's plan: due reviews first, then one weak concept to strengthen, then one new session
 * to diagnose. Bounded; [quick] gives the five-minute version (reviews only, at most five cards).
 */
fun buildTodayPlan(
    brief: LearningRepository.DailyCompanionBrief?,
    sessions: List<LearningSession>,
    lectures: List<LearningRepository.LectureNoteSummary>,
    quick: Boolean = false
): TodayPlan {
    val items = mutableListOf<PlanItem>()
    val due = brief?.dueReviews.orEmpty().filter { it.activity.isRelease1Practice() }
    val cardCap = if (quick) QuickPlanCards else MaxPlanCards
    val dueCards = due.take(cardCap).map { it.activity }
    if (dueCards.isNotEmpty()) {
        val courses = due.take(cardCap).mapNotNull { it.session.courseName?.takeIf(String::isNotBlank) }.distinct()
        val from = when {
            courses.size == 1 -> "From ${courses.first()}"
            courses.size > 1 -> "From ${courses.size} courses"
            else -> "From your sessions"
        }
        val more = (due.size - dueCards.size).takeIf { it > 0 }?.let { " · $it more tomorrow" } ?: ""
        items += PlanItem(
            kind = PlanKind.RECALL,
            title = "Daily Recall",
            detail = "${dueCards.size} ${if (dueCards.size == 1) "card" else "cards"} · $from$more",
            minutes = minutesFor(dueCards)
        )
    }

    if (!quick) {
        val weakest = brief?.weakHighYieldConcepts.orEmpty()
            .firstOrNull { it.state == LearningMasteryState.NEEDS_REVIEW }
            ?: brief?.weakHighYieldConcepts.orEmpty().firstOrNull { it.state == LearningMasteryState.LEARNING }
        if (weakest != null) {
            val session = sessions.firstOrNull { it.id == weakest.sessionId }
            items += PlanItem(
                kind = PlanKind.STRENGTHEN,
                title = "Strengthen ${weakest.name}",
                detail = if (weakest.state == LearningMasteryState.NEEDS_REVIEW) "Missed recently · 3 questions"
                else "Still early · 3 questions",
                minutes = 4,
                sessionId = weakest.sessionId,
                noteId = session?.noteId,
                concept = weakest
            )
        }

        val untested = lectures.firstOrNull { lecture ->
            val session = sessions.firstOrNull { it.id == lecture.sessionId }
            lecture.sessionId != null && lecture.activityCount > 0 && session != null &&
                !session.isPaused && session.lastStudiedAt == null
        }
        if (untested != null) {
            items += PlanItem(
                kind = PlanKind.DIAGNOSTIC,
                title = "Quick check: ${untested.title}",
                detail = "Not graded · finds what to focus on",
                minutes = 5,
                sessionId = untested.sessionId,
                noteId = untested.noteId
            )
        }
    }

    val minutes = items.sumOf { it.minutes }
    val reason = when {
        items.isEmpty() -> ""
        items.first().kind == PlanKind.RECALL && due.size > dueCards.size ->
            "Reviews are spaced so you remember for longer. The rest wait until tomorrow, no penalty."
        items.first().kind == PlanKind.RECALL ->
            "These cards are due today. Reviewing them now is what makes them stick."
        items.first().kind == PlanKind.STRENGTHEN -> "One concept needs a little more practice."
        else -> "A short, ungraded check shows what to focus on in your newest session."
    }
    return TodayPlan(items, minutes, reason)
}

/** Good morning / afternoon / evening, from the hour of day. Never assumes morning. */
fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

// ───────────────────────────── Session ─────────────────────────────

enum class NextStepKind { DIAGNOSTIC, STRENGTHEN, LEARN_NEW, REVIEW, DONE, EMPTY }

data class NextStep(
    val kind: NextStepKind,
    val title: String,
    val detail: String,
    val concept: LearningConcept? = null
)

/**
 * What the session's Next step strip offers. It follows the evidence: a diagnostic before any
 * practice, then the weakest concept, then a concept not tried yet.
 */
fun nextStepFor(detail: LearningRepository.LearningSessionDetail): NextStep {
    val practice = practiceItems(detail)
    val concepts = detail.concepts.filter { !it.isDismissed && !it.isStale }
    if (practice.isEmpty()) {
        return NextStep(NextStepKind.EMPTY, "Practice isn't ready yet", "Questions appear once the study guide is built.")
    }
    if (detail.attempts.isEmpty()) {
        val n = diagnosticSet(detail).size
        return NextStep(NextStepKind.DIAGNOSTIC, "Take a quick check", "$n questions · about ${minutesFor(diagnosticSet(detail))} min · not graded")
    }
    concepts.firstOrNull { it.state == LearningMasteryState.NEEDS_REVIEW }?.let {
        return NextStep(NextStepKind.STRENGTHEN, "Strengthen ${it.name}", "Missed recently · 3 questions", it)
    }
    concepts.firstOrNull { it.state == LearningMasteryState.LEARNING }?.let {
        return NextStep(NextStepKind.STRENGTHEN, "Strengthen ${it.name}", "Still early · 3 questions", it)
    }
    concepts.firstOrNull { it.state == LearningMasteryState.NEW && practice.any { a -> a.conceptId == it.id } }?.let {
        return NextStep(NextStepKind.LEARN_NEW, "Try ${it.name}", "Not tested yet · 3 questions", it)
    }
    val now = System.currentTimeMillis()
    val dueCount = detail.schedules.count { !it.isPaused && it.dueAt <= now && practice.any { a -> a.id == it.activityId } }
    if (dueCount > 0) {
        return NextStep(NextStepKind.REVIEW, "Review $dueCount due ${if (dueCount == 1) "card" else "cards"}", "Spaced reviews for this session")
    }
    return NextStep(NextStepKind.DONE, "You're up to date here", "Your next review is scheduled. Nothing to do now.")
}

/** The session's diagnostic: flagged items first, otherwise one question per concept; 5 to 8 items. */
fun diagnosticSet(detail: LearningRepository.LearningSessionDetail): List<LearningActivity> {
    val practice = practiceItems(detail)
    val flagged = practice.filter { it.isDiagnostic }
    if (flagged.size >= 5) return flagged.take(8)
    val perConcept = practice
        .filter { it.type != LearningActivityType.RECALL }
        .groupBy { it.conceptId }
        .values.map { it.first() }
    val picked = (flagged + perConcept + practice).distinctBy { it.id }
    return picked.take(8).let { if (it.size > 6 && flagged.size < 6) it.take(6) else it }
}

/** Up to three questions on one concept, harder formats after recall. */
fun conceptSet(detail: LearningRepository.LearningSessionDetail, conceptId: String): List<LearningActivity> =
    practiceItems(detail).filter { it.conceptId == conceptId }
        .sortedBy { if (it.type == LearningActivityType.RECALL) 0 else 1 }
        .take(3)

fun masteryCounts(concepts: List<LearningConcept>): Map<LearningMasteryState, Int> =
    concepts.filter { !it.isDismissed }.groupingBy { it.state }.eachCount()

/** The latest attempt per concept, for "last practised" lines. */
fun lastAttemptByConcept(attempts: List<ActivityAttempt>): Map<String, ActivityAttempt> =
    attempts.filter { it.conceptId != null }
        .groupBy { it.conceptId!! }
        .mapValues { (_, list) -> list.maxBy { it.createdAt } }

// ───────────────────────────── Answer checking ─────────────────────────────

/**
 * The index of the correct option, or null when the stored answer matches none of them (the item
 * is then marked by self-check instead of guessed). Accepts the option text or a letter ("B",
 * "B)", "b. ...").
 */
fun correctOptionIndex(activity: LearningActivity): Int? {
    val options = activity.options
    if (options.isEmpty()) return null
    val answer = activity.expectedAnswer.trim()
    if (answer.isEmpty()) return null
    options.indexOfFirst { it.trim().equals(answer, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
    if (answer.length >= 3) {
        options.indexOfFirst { opt ->
            val o = opt.trim()
            o.length >= 3 && (answer.startsWith(o, ignoreCase = true) || o.startsWith(answer, ignoreCase = true))
        }.takeIf { it >= 0 }?.let { return it }
    }
    // A bare letter: "B", "b.", "C)" or "c) Both change". "A protein" is a sentence, not option A.
    val letter = Regex("""^([A-Ha-h])(?:[).:]|\s*$)""").find(answer)?.groupValues?.get(1)?.uppercase()
    if (letter != null) {
        val index = letter[0] - 'A'
        if (index in options.indices) return index
    }
    return null
}

/**
 * Key points of a model answer, for the short-answer self-check: sentences or list lines, at most
 * four. The learner ticks what they covered; nothing is auto-graded (decision D9).
 */
fun keyPointsOf(expectedAnswer: String): List<String> {
    val lines = expectedAnswer.lines().map { it.trim().trimStart('-', '•', '*', ' ').trim() }.filter { it.length > 2 }
    val parts = if (lines.size >= 2) lines else expectedAnswer.split(Regex("""(?<=[.;])\s+""")).map { it.trim() }.filter { it.length > 2 }
    return parts.take(4)
}
