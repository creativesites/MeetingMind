package com.craftflowtechnologies.meetingmind.feature.learn.exam

import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState

/**
 * The exam plan's arithmetic, kept apart from the screen so it can be tested. Calm by design: no streaks, no
 * "you are behind", no counts of what was missed. A plan is a short list of days, each with a few ideas and a time.
 */
object ExamPlanner {
    const val DAY_MS = 24L * 3600 * 1000
    /** The plan shows at most this many days ahead; the rest of the time is not planned yet. */
    const val MAX_PLANNED_DAYS = 14

    /** Whole days from [nowMs] to [examDateMs], never negative. */
    fun daysUntil(nowMs: Long, examDateMs: Long): Int = ((examDateMs - nowMs).coerceAtLeast(0L) / DAY_MS).toInt()

    /**
     * Builds the days from today until the day before the exam. Ideas due for review come first, then the ones still
     * being learned, dealt out round by round so every day has a little of each. [doneByMs] is the start of the day
     * before the exam, so the last day is for review, not new material. Null when there is less than two days.
     */
    fun build(nowMs: Long, examDateMs: Long, dailyMinutes: Int, ideas: List<PlanIdea>): ExamPlan {
        val daysLeft = daysUntil(nowMs, examDateMs)
        val studyDays = minOf(daysLeft, MAX_PLANNED_DAYS)
        val queue = ideas.filter { it.state == LearningMasteryState.NEEDS_REVIEW } + ideas.filter {
            it.state == LearningMasteryState.NEW || it.state == LearningMasteryState.LEARNING || it.state == LearningMasteryState.DEVELOPING
        }
        val todayStart = startOfDay(nowMs)
        val days = (0 until studyDays).map { day ->
            val mine = queue.filterIndexed { index, _ -> index % studyDays == day }
            PlanDay(
                dateMs = todayStart + day * DAY_MS,
                minutes = dailyMinutes.coerceAtLeast(5),
                reviewNames = mine.filter { it.state == LearningMasteryState.NEEDS_REVIEW }.map { it.name },
                newNames = mine.filter { it.state != LearningMasteryState.NEEDS_REVIEW }.map { it.name }
            )
        }
        return ExamPlan(
            examDateMs = examDateMs,
            daysLeft = daysLeft,
            days = days,
            doneByMs = if (daysLeft >= 2) startOfDay(examDateMs) - DAY_MS else null
        )
    }

    /** Local midnight of the day holding [ms]. */
    fun startOfDay(ms: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Readiness as a share of ideas: solid counts fully, getting there half, new and worth a look not at all. */
    fun readinessPercent(ideas: List<PlanIdea>): Int {
        if (ideas.isEmpty()) return 0
        val points = ideas.sumOf {
            when (it.state) {
                LearningMasteryState.STRONG -> 1.0
                LearningMasteryState.DEVELOPING, LearningMasteryState.LEARNING -> 0.5
                LearningMasteryState.NEW, LearningMasteryState.NEEDS_REVIEW -> 0.0
            }
        }
        return ((points / ideas.size) * 100).toInt().coerceIn(0, 100)
    }

    /** The word an idea's state is shown as, in the Study vocabulary (never a raw enum name). */
    fun stateWord(state: LearningMasteryState): String = when (state) {
        LearningMasteryState.STRONG -> "Solid"
        LearningMasteryState.DEVELOPING, LearningMasteryState.LEARNING -> "Getting there"
        LearningMasteryState.NEW -> "New"
        LearningMasteryState.NEEDS_REVIEW -> "Worth a look"
    }
}

/** An idea as the planner sees it: its name and where the learner is with it. */
data class PlanIdea(val name: String, val state: LearningMasteryState)

/** One planned day: its date (local midnight), its time, and the ideas to review and to learn. */
data class PlanDay(
    val dateMs: Long,
    val minutes: Int,
    val reviewNames: List<String>,
    val newNames: List<String>
) {
    val isEmpty: Boolean get() = reviewNames.isEmpty() && newNames.isEmpty()
}

data class ExamPlan(
    val examDateMs: Long,
    val daysLeft: Int,
    val days: List<PlanDay>,
    /** The day the learner aims to be done by (the day before the exam), when there is one. */
    val doneByMs: Long?
)
