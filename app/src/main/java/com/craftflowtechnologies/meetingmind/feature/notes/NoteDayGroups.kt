package com.craftflowtechnologies.meetingmind.feature.notes

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The section a note sits in on the notes list. */
enum class DayGroup(val label: String) { TODAY("Today"), YESTERDAY("Yesterday"), THIS_WEEK("This week"), EARLIER("Earlier") }

/** Pure: which section a note's date falls in, so the list's headers are unit-tested on the JVM. */
object DayGroups {
    fun of(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): DayGroup {
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0 -> DayGroup.TODAY
            days == 1L -> DayGroup.YESTERDAY
            days < 7 -> DayGroup.THIS_WEEK
            else -> DayGroup.EARLIER
        }
    }
}
