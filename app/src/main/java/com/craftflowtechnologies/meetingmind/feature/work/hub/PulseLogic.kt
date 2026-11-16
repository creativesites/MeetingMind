package com.craftflowtechnologies.meetingmind.feature.work.hub

import com.craftflowtechnologies.meetingmind.core.work.WorkSettings
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/*
 * Work Pulse, the pure decisions (design/work-vertical-design-spec.md §5.1): which hero the moment calls for,
 * which "Needs you" rows win the four places, and whether the weekly review is due. No Android and no database,
 * so each rule has a plain unit test.
 */

/** The hero the Pulse shows. The order of the rule in [pulseMoment] is the order of the spec's table. */
enum class PulseMoment { FIRST_DAY, BEFORE_MEETING, AFTER_MEETINGS, WEEKLY_REVIEW, QUIET }

/** What the hero rule reads. Times are epoch milliseconds. */
data class PulseFacts(
    val now: Long,
    /** Meetings today, in any state. */
    val meetingsToday: Int,
    /** Meetings today that have already ended. */
    val meetingsEndedToday: Int,
    /** Start of the next meeting that has not ended yet, or null when none is left today. */
    val nextMeetingStart: Long?,
    /** How long before a meeting the "Next" hero takes over (the prep window, default 15 minutes). */
    val prepLeadMs: Long,
    /** Recordings with findings that nobody has reviewed yet. */
    val unreviewed: Int,
    /** Friday after the review time, and the review is not snoozed. */
    val weeklyReviewDue: Boolean,
    /** Nothing reviewed in the record yet: a calendar-led hero and three ways to start. */
    val coldStart: Boolean
)

/**
 * The hero by moment (spec §5.1 table):
 * 1. day one (nothing in the record) → calendar-led hero;
 * 2. before a meeting: the next one is inside the prep window, or no meeting has ended yet today (the first meeting
 *    of the day owns the morning) → Next;
 * 3. after meetings: a finished recording waits for its review → To review;
 * 4. Friday after the review time → weekly review;
 * 5. otherwise quiet: no hero, and the headline says nothing needs the person today.
 */
fun pulseMoment(f: PulseFacts): PulseMoment = when {
    f.coldStart -> PulseMoment.FIRST_DAY
    f.nextMeetingStart != null && (f.nextMeetingStart - f.now <= f.prepLeadMs || f.meetingsEndedToday == 0) -> PulseMoment.BEFORE_MEETING
    f.unreviewed > 0 -> PulseMoment.AFTER_MEETINGS
    f.weeklyReviewDue -> PulseMoment.WEEKLY_REVIEW
    else -> PulseMoment.QUIET
}

/** True on the weekly review day (from [WorkSettings.weeklyReviewDay]) at or after [WorkSettings.weeklyReviewMinute]. */
fun weeklyReviewDue(settings: WorkSettings, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val at = Instant.ofEpochMilli(now).atZone(zone)
    val calendarDay = if (at.dayOfWeek == DayOfWeek.SUNDAY) 1 else at.dayOfWeek.value + 1
    val minuteOfDay = at.hour * 60 + at.minute
    return calendarDay == settings.weeklyReviewDay && minuteOfDay >= settings.weeklyReviewMinute
}

/**
 * The kinds of row that compete for the four places in "Needs you", in the order they win (Pulse ranking):
 * a failed recording, what I owe that is due or past, what people owe that is past, a decision still proposed,
 * the Inbox, then what people owe that is merely open, then quiet relationships.
 */
enum class NeedRank(val order: Int) {
    FAILED(0), YOU_OWE_DUE(1), THEY_OWE_PAST(2), DECISION(3), INBOX(4), THEY_OWE_OPEN(5), QUIET(6)
}

/** A candidate row for "Needs you". [value] is whatever the screen draws (an attention row, a failed recording). */
data class NeedCandidate<T>(val rank: NeedRank, val value: T)

/**
 * Ranks candidates by [NeedRank], keeps the order they came in within a rank (Pulse already sorts by due date),
 * and keeps at most [limit] rows. Empty input gives an empty list, so an empty section renders nothing.
 */
fun <T> rankNeeds(candidates: List<NeedCandidate<T>>, limit: Int = 4): List<NeedCandidate<T>> =
    candidates.withIndex()
        .sortedWith(compareBy<IndexedValue<NeedCandidate<T>>> { it.value.rank.order }.thenBy { it.index })
        .map { it.value }
        .take(limit)
