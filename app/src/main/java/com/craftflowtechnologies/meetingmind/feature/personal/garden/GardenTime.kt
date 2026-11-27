package com.craftflowtechnologies.meetingmind.feature.personal.garden

import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The garden's clock: which time of day to paint, and the words that go with it. Pure, so the
 * hours are unit-tested on the JVM. The companion never assumes it is morning (agents/PRODUCT.md).
 */
object GardenTime {

    fun phase(time: LocalTime): GardenPhase = when (time.hour) {
        in 5..7 -> GardenPhase.DAWN
        in 8..16 -> GardenPhase.DAY
        in 17..19 -> GardenPhase.DUSK
        else -> GardenPhase.NIGHT
    }

    /** "Thursday, early", "Thursday afternoon", "Thursday evening", "Thursday night". */
    fun whenLine(date: LocalDate, time: LocalTime, locale: Locale = Locale.getDefault()): String {
        val day = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        return when (phase(time)) {
            GardenPhase.DAWN -> "$day, early"
            GardenPhase.DAY -> if (time.hour < 12) "$day morning" else "$day afternoon"
            GardenPhase.DUSK -> "$day evening"
            GardenPhase.NIGHT -> if (time.hour < 5) "$day, late" else "$day night"
        }
    }

    /** The question card's label: tonight's after five, today's before. */
    fun questionLabel(time: LocalTime): String = if (time.hour >= 17 || time.hour < 5) "TONIGHT'S QUESTION" else "TODAY'S QUESTION"
}

/**
 * The day's reflection question. Generic by default: no faith language and nothing that assumes
 * the person's life (agents/PRODUCT.md, "Generic by default"). One question per day, the same all
 * day, a morning set before five in the afternoon and an evening set after.
 */
object GardenQuestions {

    val morning: List<String> = listOf(
        "What would make today feel well spent?",
        "What are you carrying into today?",
        "What is one thing you want to protect today?",
        "Who could use a word from you today?",
        "What would you like to notice today?",
        "What are you looking forward to, even a little?",
        "What can wait, so that something else can't?",
        "How do you want to feel at the end of today?",
        "What is asking for your attention most?",
        "What small thing would make today kinder?",
        "What did you sleep on, and how does it look now?",
        "What is one thing you could finish today?"
    )

    val evening: List<String> = listOf(
        "What took more energy today than it should have?",
        "What gave you energy today?",
        "What would you do differently tomorrow morning?",
        "What surprised you today?",
        "Who made today better, and do they know?",
        "What are you glad you said, or didn't say?",
        "What did you learn about yourself today?",
        "What are you still carrying from today?",
        "What small thing went right?",
        "When did you feel most like yourself today?",
        "What can you let go of tonight?",
        "What are you grateful for from today?",
        "What did you avoid today, and why?",
        "What do you want to remember about today?"
    )

    fun forDay(date: LocalDate, time: LocalTime): String {
        val set = if (time.hour >= 17 || time.hour < 5) evening else morning
        val index = Math.floorMod(date.toEpochDay(), set.size.toLong()).toInt()
        return set[index]
    }
}
