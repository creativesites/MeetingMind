package com.craftflowtechnologies.meetingmind.core.practice

import java.time.DayOfWeek

/**
 * How often a practice is meant to be kept. The same rhythm drives the repeating task in Tasks
 * (see [rrule]) and the week of dots on the plant ([PracticeGrowth.week]).
 */
enum class Rhythm(val label: String, val perWeek: Int) {
    DAILY("Every day", 7),
    WEEKDAYS("Weekdays", 5),
    THREE_A_WEEK("3 times a week", 3),
    /** Once a week, on the day the practice was started. */
    WEEKLY("Once a week", 1);

    /** The weekdays this rhythm asks for. [startDay] only matters for [WEEKLY]. */
    fun scheduledDays(startDay: DayOfWeek): Set<DayOfWeek> = when (this) {
        DAILY -> ALL_DAYS
        WEEKDAYS -> WORKDAYS
        THREE_A_WEEK -> setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        WEEKLY -> setOf(startDay)
    }

    /** The RRULE stored on the task for this rhythm. */
    fun rrule(startDay: DayOfWeek): String = when (this) {
        DAILY -> "FREQ=DAILY"
        WEEKDAYS -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
        THREE_A_WEEK -> "FREQ=WEEKLY;BYDAY=MO,WE,FR"
        WEEKLY -> "FREQ=WEEKLY;BYDAY=${code(startDay)}"
    }

    companion object {
        private val ALL_DAYS = DayOfWeek.values().toSet()
        private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        /** The stored name back to a rhythm. An unknown name falls back to every day. */
        fun parse(name: String?): Rhythm = values().firstOrNull { it.name == name } ?: DAILY

        private fun code(day: DayOfWeek): String = when (day) {
            DayOfWeek.MONDAY -> "MO"
            DayOfWeek.TUESDAY -> "TU"
            DayOfWeek.WEDNESDAY -> "WE"
            DayOfWeek.THURSDAY -> "TH"
            DayOfWeek.FRIDAY -> "FR"
            DayOfWeek.SATURDAY -> "SA"
            DayOfWeek.SUNDAY -> "SU"
        }
    }
}
