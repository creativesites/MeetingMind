package com.craftflowtechnologies.meetingmind.feature.work.home

/** What the Work home leads with: the next meeting, else the most urgent thing that needs the person, else nothing. */
enum class WorkHero { MEETING, ATTENTION, NONE }

/** Where a schedule row sits against the clock. */
enum class ScheduleState { PAST, LIVE, UPCOMING }

/**
 * The Work home's rules, as plain functions so they are unit-tested on the JVM.
 * They take counts and times, not app models, so the tests need no Android types.
 */
object WorkHomeLayout {
    /** Most rows the Needs card shows before "All N". */
    const val NEEDS_CAP = 4

    /** Most rows the My tasks and Waiting on lists show before "All N". */
    const val TASKS_CAP = 4
    const val WAITING_CAP = 3

    /** The hero leads with the next meeting when there is one; otherwise the first item that needs the person. */
    fun hero(hasNextMeeting: Boolean, needsCount: Int): WorkHero = when {
        hasNextMeeting -> WorkHero.MEETING
        needsCount > 0 -> WorkHero.ATTENTION
        else -> WorkHero.NONE
    }

    /**
     * How many rows the Needs card shows from each kind: recordings to wrap up come first,
     * and follow-ups not yet sent fill whatever room the cap leaves. Returns (reviews, followUps).
     */
    fun needsShown(reviews: Int, followUps: Int, cap: Int = NEEDS_CAP): Pair<Int, Int> {
        val shownReviews = reviews.coerceIn(0, cap.coerceAtLeast(0))
        val shownFollowUps = followUps.coerceIn(0, (cap - shownReviews).coerceAtLeast(0))
        return shownReviews to shownFollowUps
    }

    /**
     * Where an agenda row sits. An event with no end counts as ending at its start, so it is
     * "live" only at that exact instant, matching the Today calendar.
     */
    fun scheduleState(start: Long, end: Long?, now: Long): ScheduleState {
        val finish = end ?: start
        return when {
            finish < now -> ScheduleState.PAST
            start <= now && finish >= now -> ScheduleState.LIVE
            else -> ScheduleState.UPCOMING
        }
    }
}
