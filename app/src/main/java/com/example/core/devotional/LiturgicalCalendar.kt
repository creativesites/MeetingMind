package com.example.core.devotional

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A season of the church year. */
enum class LiturgicalSeason(val label: String, val color: Long) {
    ADVENT("Advent", 0xFF5B3F8C),
    CHRISTMAS("Christmas", 0xFFC9A227),
    EPIPHANY("Epiphany", 0xFF2F7D5B),
    LENT("Lent", 0xFF6A3E8E),
    HOLY_WEEK("Holy Week", 0xFF8E1B2C),
    EASTER("Easter", 0xFFC9A227),
    PENTECOST("Pentecost", 0xFFC0392B),
    ORDINARY("Ordinary Time", 0xFF2F7D5B)
}

/** Where a date falls in the church year, and the feast it is, if any. */
data class LiturgicalDay(val season: LiturgicalSeason, val feast: String?) {
    /** "Lent · Ash Wednesday", "Ordinary Time". */
    fun describe(): String = listOfNotNull(season.label, feast).distinct().joinToString(" · ")
}

/**
 * The church year, computed — no table to go stale. Western dates use the Gregorian Easter; the
 * Orthodox tradition uses the Julian Easter (Pascha), converted to the civil calendar.
 */
object LiturgicalCalendar {

    /** Western Easter Sunday (the anonymous Gregorian algorithm). */
    fun westernEaster(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** Orthodox Pascha (Meeus' Julian algorithm), in the civil (Gregorian) calendar. */
    fun orthodoxEaster(year: Int): LocalDate {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = (d + e + 114) % 31 + 1
        val julian = LocalDate.of(year, month, day)
        // Julian → Gregorian: 13 days in 1900–2099.
        val offset = year / 100 - year / 400 - 2
        return julian.plusDays(offset.toLong())
    }

    fun dayOf(date: LocalDate, tradition: Tradition = Tradition.NON_DENOMINATIONAL): LiturgicalDay {
        val easter = if (tradition == Tradition.ORTHODOX) orthodoxEaster(date.year) else westernEaster(date.year)
        val fromEaster = ChronoUnit.DAYS.between(easter, date)
        val christmas = LocalDate.of(date.year, 12, 25)
        val adventStart = christmas.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).minusWeeks(3)

        val feast = when {
            fromEaster == 0L -> "Easter Sunday"
            fromEaster == -46L -> "Ash Wednesday"
            fromEaster == -7L -> "Palm Sunday"
            fromEaster == -3L -> "Maundy Thursday"
            fromEaster == -2L -> "Good Friday"
            fromEaster == -1L -> "Holy Saturday"
            fromEaster == 39L -> "Ascension Day"
            fromEaster == 49L -> "Pentecost Sunday"
            fromEaster == 56L -> "Trinity Sunday"
            date.monthValue == 12 && date.dayOfMonth == 25 -> "Christmas Day"
            date.monthValue == 12 && date.dayOfMonth == 24 -> "Christmas Eve"
            date.monthValue == 1 && date.dayOfMonth == 6 -> "Epiphany"
            date == adventStart -> "First Sunday of Advent"
            else -> null
        }
        val season = when {
            fromEaster in -46L..-8L -> LiturgicalSeason.LENT
            fromEaster in -7L..-1L -> LiturgicalSeason.HOLY_WEEK
            fromEaster in 0L..48L -> LiturgicalSeason.EASTER
            fromEaster == 49L -> LiturgicalSeason.PENTECOST
            !date.isBefore(adventStart) && date.isBefore(christmas) -> LiturgicalSeason.ADVENT
            !date.isBefore(christmas) -> LiturgicalSeason.CHRISTMAS
            date.monthValue == 1 && date.dayOfMonth <= 5 -> LiturgicalSeason.CHRISTMAS
            date.monthValue == 1 && date.dayOfMonth <= 12 -> LiturgicalSeason.EPIPHANY
            else -> LiturgicalSeason.ORDINARY
        }
        return LiturgicalDay(season, feast)
    }
}
