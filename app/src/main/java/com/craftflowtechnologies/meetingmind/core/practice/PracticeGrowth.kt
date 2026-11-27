package com.craftflowtechnologies.meetingmind.core.practice

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * How a practice looks and reads: its growth stage, whether it is resting, and the week of dots.
 * Pure, with no Android and no clock: every date is passed in.
 *
 * Nothing here counts a streak. A missed day never resets anything, and a plant that has not
 * been tended for a while is resting, never dead.
 */
object PracticeGrowth {
    enum class Stage { SEED, SPROUT, LEAFY, BUDDING, BLOOM }

    enum class DayState { KEPT, MISSED, TODAY, UPCOMING, OFF }

    /** One day in the week strip. [letter] is the first letter of the weekday. */
    data class DayDot(val date: LocalDate, val letter: Char, val scheduled: Boolean, val state: DayState)

    /** Days of quiet before a plant counts as resting. */
    private const val QUIET_DAYS_WEEKLY = 14
    private const val QUIET_DAYS = 7
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** A bloomed practice is always in bloom, whatever its count. */
    fun stage(keptCount: Int, bloomed: Boolean): Stage = when {
        bloomed -> Stage.BLOOM
        keptCount <= 0 -> Stage.SEED
        keptCount <= 2 -> Stage.SPROUT
        keptCount <= 5 -> Stage.LEAFY
        else -> Stage.BUDDING
    }

    /**
     * Resting when nothing was kept in the last 7 days (14 for a weekly practice). With no day kept
     * yet, the count starts from the day it was planted, so a plant planted today is not resting.
     */
    fun resting(lastKept: LocalDate?, startedOn: LocalDate, rhythm: Rhythm, today: LocalDate): Boolean {
        val quietDays = if (rhythm == Rhythm.WEEKLY) QUIET_DAYS_WEEKLY else QUIET_DAYS
        val reference = lastKept ?: startedOn
        // The window is the quietDays ending today; a kept day inside it keeps the plant awake.
        return reference.isBefore(today.minusDays(quietDays.toLong() - 1))
    }

    /**
     * Monday to Sunday of [today]'s week. A kept day always shows KEPT, even one the rhythm did not
     * ask for. Otherwise: today is TODAY; a later day is UPCOMING if scheduled; an earlier day is
     * MISSED if scheduled and on or after the start; everything else is OFF.
     */
    fun week(days: Set<LocalDate>, rhythm: Rhythm, startedOn: LocalDate, today: LocalDate): List<DayDot> {
        val monday = mondayOf(today)
        val scheduled = rhythm.scheduledDays(startedOn.dayOfWeek)
        return (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            val isScheduled = date.dayOfWeek in scheduled
            DayDot(date, letter(date.dayOfWeek), isScheduled, stateOf(date, days, isScheduled, startedOn, today))
        }
    }

    /** Days kept in Monday to Sunday of [today]'s week, scheduled or not. */
    fun keptThisWeek(days: Set<LocalDate>, today: LocalDate): Int {
        val monday = mondayOf(today)
        val sunday = monday.plusDays(6)
        return days.count { !it.isBefore(monday) && !it.isAfter(sunday) }
    }

    /**
     * Kept days out of scheduled days, from the start to today, both inclusive. A kept day counts
     * whether or not the rhythm asked for it, as long as it falls in that range.
     */
    fun window(days: Set<LocalDate>, rhythm: Rhythm, startedOn: LocalDate, today: LocalDate): Pair<Int, Int> {
        if (today.isBefore(startedOn)) return 0 to 0
        val scheduled = rhythm.scheduledDays(startedOn.dayOfWeek)
        var scheduledCount = 0
        var day = startedOn
        while (!day.isAfter(today)) {
            if (day.dayOfWeek in scheduled) scheduledCount++
            day = day.plusDays(1)
        }
        val kept = days.count { !it.isBefore(startedOn) && !it.isAfter(today) }
        return kept to scheduledCount
    }

    /** True once the check-in time has come. A null check-in never comes due. */
    fun checkInDue(checkInAt: Long?, now: Long): Boolean = checkInAt != null && now >= checkInAt

    /** The check-in time for a practice [weeks] weeks after [now]; null when it has no end. */
    fun checkInAt(now: Long, weeks: Int): Long? = if (weeks > 0) now + weeks * WEEK_MS else null

    /** The line under the plant. Never says "streak". */
    fun rhythmLine(keptThisWeek: Int): String = when {
        keptThisWeek <= 0 -> "Not yet this week"
        keptThisWeek == 1 -> "1 day this week"
        else -> "$keptThisWeek days this week"
    }

    private fun stateOf(date: LocalDate, days: Set<LocalDate>, scheduled: Boolean, startedOn: LocalDate, today: LocalDate): DayState = when {
        date in days -> DayState.KEPT
        date == today -> DayState.TODAY
        date.isAfter(today) -> if (scheduled) DayState.UPCOMING else DayState.OFF
        scheduled && !date.isBefore(startedOn) -> DayState.MISSED
        else -> DayState.OFF
    }

    private fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun letter(day: DayOfWeek): Char = when (day) {
        DayOfWeek.MONDAY -> 'M'
        DayOfWeek.TUESDAY -> 'T'
        DayOfWeek.WEDNESDAY -> 'W'
        DayOfWeek.THURSDAY -> 'T'
        DayOfWeek.FRIDAY -> 'F'
        DayOfWeek.SATURDAY -> 'S'
        DayOfWeek.SUNDAY -> 'S'
    }
}
