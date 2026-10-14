package com.craftflowtechnologies.meetingmind.feature.faith

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day of the week in faith: the date, and whether a faith note was written that day. */
data class WeekDay(val date: LocalDate, val kept: Boolean)

/** Pure: the last seven days in faith, so the week card and its counts are unit-tested on the JVM. */
object FaithWeek {

    /** The last seven days, oldest first, ending today. A day is kept when a faith note was written on it. */
    fun lastSevenDays(createdAt: List<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<WeekDay> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val written = createdAt.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
        return (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            WeekDay(day, kept = day in written)
        }
    }

    /** How many of the days are kept, for "4 of 7 days". */
    fun keptCount(days: List<WeekDay>): Int = days.count { it.kept }
}
