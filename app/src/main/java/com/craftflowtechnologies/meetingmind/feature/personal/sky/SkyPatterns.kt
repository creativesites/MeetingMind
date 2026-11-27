package com.craftflowtechnologies.meetingmind.feature.personal.sky

import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The plain lines under a constellation, and the month's name. Pure and tested on the JVM.
 *
 * These lines are English only, like the rest of Personal's inline copy; they are not in the string
 * resources, so the tests can read them. They describe and never judge: no streaks, no praise, no
 * warnings.
 */
object SkyPatterns {
    /** A share of the reflections, from 60 %, counts as "most often". */
    private const val MOST_OFTEN_NUMERATOR = 6
    private const val MOST_OFTEN_DENOMINATOR = 10

    /**
     * "You've mentioned inbox in 5 reflections since 3 November." then, when the reflections lean
     * one way, "Most often on weekdays." and "Most often in the evening.". [createdAts] are the
     * reflections' times in milliseconds, one per reflection.
     */
    fun lines(name: String, createdAts: List<Long>, zone: ZoneId): List<String> {
        if (createdAts.isEmpty()) return emptyList()
        val times = createdAts.map { Instant.ofEpochMilli(it).atZone(zone) }
        val since = times.minOf { it.toLocalDate() }.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))
        val out = mutableListOf("You've mentioned ${name.lowercase()} in ${createdAts.size} reflections since $since.")

        val n = times.size
        val weekend = times.count { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
        val weekday = n - weekend
        val days = when {
            weekday.isMostOften(n) -> "weekday"
            weekend.isMostOften(n) -> "weekend"
            else -> null
        }
        val buckets = listOf(
            "morning" to times.count { it.hour in 5..11 },
            "afternoon" to times.count { it.hour in 12..16 },
            "evening" to times.count { it.hour in 17..23 }
        )
        val part = buckets.firstOrNull { (_, count) -> count.isMostOften(n) }?.first
        // One line, not two: "Most often on weekday evenings." reads as a person would say it.
        when {
            days != null && part != null -> out += "Most often on $days ${part}s."
            days == "weekday" -> out += "Most often on weekdays."
            days == "weekend" -> out += "Most often at weekends."
            part != null -> out += "Most often in the $part."
        }
        return out
    }

    /** "November", or "November 2025" when the month is not in the current year. */
    fun monthName(month: YearMonth, now: YearMonth): String {
        val name = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return if (month.year == now.year) name else "$name ${month.year}"
    }

    private fun Int.isMostOften(total: Int): Boolean =
        total > 0 && this * MOST_OFTEN_DENOMINATOR >= total * MOST_OFTEN_NUMERATOR
}
