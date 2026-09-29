package com.example.core.work

import java.util.Calendar
import java.util.Locale

/**
 * Reads a spoken or written deadline ("by Friday", "tomorrow", "end of the month", "Oct 15") as a
 * day, relative to when it was said. Deterministic and offline: extraction gives us words, and the
 * calendar needs a date.
 *
 * Returns the start of the due day in local time, or null when the words don't name a day. Null is
 * the honest answer for "soon" or "after the launch": the original words are kept beside it.
 */
object DueDates {

    private val WEEKDAYS = mapOf(
        "monday" to Calendar.MONDAY, "mon" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "tue" to Calendar.TUESDAY, "tues" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "wed" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "thu" to Calendar.THURSDAY, "thurs" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "fri" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY, "sat" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY, "sun" to Calendar.SUNDAY
    )

    private val MONTHS = mapOf(
        "january" to 0, "jan" to 0, "february" to 1, "feb" to 1, "march" to 2, "mar" to 2,
        "april" to 3, "apr" to 3, "may" to 4, "june" to 5, "jun" to 5, "july" to 6, "jul" to 6,
        "august" to 7, "aug" to 7, "september" to 8, "sep" to 8, "sept" to 8, "october" to 9, "oct" to 9,
        "november" to 10, "nov" to 10, "december" to 11, "dec" to 11
    )

    private val NUMBER_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "couple of" to 2, "few" to 3
    )

    fun startOfDay(at: Long): Long = Calendar.getInstance().apply {
        timeInMillis = at
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun parse(text: String?, reference: Long): Long? {
        if (text.isNullOrBlank()) return null
        val t = text.lowercase(Locale.ROOT)
            .replace(Regex("[,.;!?]"), " ")
            .replace(Regex("\\b(by|before|on|due|until|till|no later than|the)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val base = Calendar.getInstance().apply { timeInMillis = startOfDay(reference) }
        fun plusDays(n: Int) = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, n) }.timeInMillis

        isoDate(t)?.let { return it }

        when {
            Regex("\\b(today|tonight|eod|end of (the )?day|this evening|this afternoon)\\b").containsMatchIn(t) -> return plusDays(0)
            Regex("\\bday after tomorrow\\b").containsMatchIn(t) -> return plusDays(2)
            Regex("\\b(tomorrow|tmrw|tmr)\\b").containsMatchIn(t) -> return plusDays(1)
            Regex("\\b(eow|end of (this )?week|this week)\\b").containsMatchIn(t) -> return weekday(base, Calendar.FRIDAY, allowToday = true)
            // "Next week" with no day: the Monday of next week.
            Regex("\\bnext week\\b").containsMatchIn(t) && WEEKDAYS.keys.none { Regex("\\b$it\\b").containsMatchIn(t) } ->
                return weekday(base, Calendar.MONDAY, allowToday = false)
            Regex("\\b(eom|end of (the |this )?month|this month)\\b").containsMatchIn(t) ->
                return (base.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }.timeInMillis
            Regex("\\bnext month\\b").containsMatchIn(t) ->
                return (base.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        }

        Regex("\\bin (\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten|a couple of|a few) (day|days|week|weeks)\\b").find(t)?.let { m ->
            val raw = m.groupValues[1].removePrefix("a ").trim()
            val n = raw.toIntOrNull() ?: NUMBER_WORDS[raw] ?: NUMBER_WORDS[m.groupValues[1]] ?: return null
            val days = if (m.groupValues[2].startsWith("week")) n * 7 else n
            return plusDays(days)
        }

        // A weekday: "Friday" means the coming Friday; "next Friday" the one after this week's.
        WEEKDAYS.entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(t) }?.let { (name, day) ->
            val next = Regex("\\bnext $name\\b").containsMatchIn(t)
            val coming = weekday(base, day, allowToday = false)
            if (!next) return coming
            // "Next Friday" said on Monday means the Friday of next week, not four days away.
            val sameWeek = daysBetween(base, coming) <= (7 - daysSinceMonday(base)) - 1
            return if (sameWeek) coming + 7L * DAY_MS else coming
        }

        monthDay(t, base)?.let { return it }
        return null
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun dayOfWeek(c: Calendar) = c.get(Calendar.DAY_OF_WEEK)

    private fun daysSinceMonday(c: Calendar) = (dayOfWeek(c) + 5) % 7

    private fun daysBetween(from: Calendar, to: Long) = ((to - from.timeInMillis + DAY_MS / 2) / DAY_MS).toInt()

    /** The next [day] after [base] (or [base] itself when [allowToday]). */
    private fun weekday(base: Calendar, day: Int, allowToday: Boolean): Long {
        var ahead = (day - dayOfWeek(base) + 7) % 7
        if (ahead == 0 && !allowToday) ahead = 7
        return (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, ahead) }.timeInMillis
    }

    private fun isoDate(t: String): Long? {
        val m = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b").find(t) ?: return null
        val (y, mo, d) = m.destructured
        return Calendar.getInstance().apply {
            clear(); set(y.toInt(), mo.toInt() - 1, d.toInt())
        }.takeIf { mo.toInt() in 1..12 && d.toInt() in 1..31 }?.timeInMillis
    }

    /** "Oct 15", "15 October", "October 15th". A date already past this year means next year. */
    private fun monthDay(t: String, base: Calendar): Long? {
        val monthNames = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
        val a = Regex("\\b($monthNames) (\\d{1,2})(st|nd|rd|th)?\\b").find(t)
        val b = Regex("\\b(\\d{1,2})(st|nd|rd|th)? (of )?($monthNames)\\b").find(t)
        val (month, day) = when {
            a != null -> MONTHS[a.groupValues[1]]!! to a.groupValues[2].toInt()
            b != null -> MONTHS[b.groupValues[4]]!! to b.groupValues[1].toInt()
            else -> return null
        }
        if (day !in 1..31) return null
        val c = Calendar.getInstance().apply { clear(); set(base.get(Calendar.YEAR), month, day) }
        if (c.timeInMillis < base.timeInMillis - 7 * DAY_MS) c.add(Calendar.YEAR, 1)
        return c.timeInMillis
    }
}
