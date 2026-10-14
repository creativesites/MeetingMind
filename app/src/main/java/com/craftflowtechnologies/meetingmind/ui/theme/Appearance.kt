package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The accent the person picked; each has a colour for Graphite (dark) and one for Paper (light). */
enum class AccentChoice(val label: String, val dark: Color, val light: Color) {
    INDIGO("Indigo", Color(0xFF9B9CF6), Color(0xFF5B5BD6)),
    TEAL("Teal", Color(0xFF4FD1C5), Color(0xFF0F766E)),
    SKY("Sky", Color(0xFF7DD3FC), Color(0xFF0369A1)),
    GREEN("Green", Color(0xFF4ADE80), Color(0xFF15803D)),
    EMERALD("Emerald", Color(0xFF34D399), Color(0xFF047857)),
    CRIMSON("Crimson", Color(0xFFF87171), Color(0xFFB91C1C)),
    AMBER("Amber", Color(0xFFF2B447), Color(0xFFB45309)),
    ROSE("Rose", Color(0xFFF472B6), Color(0xFFBE185D)),
    SLATE("Slate", Color(0xFFCBD5E1), Color(0xFF475569));

    fun on(base: MMColors): MMColors {
        val c = if (base.isDark) dark else light
        return base.copy(accent = c, accentWash = c.copy(alpha = if (base.isDark) 0.15f else 0.10f))
    }
}

enum class TextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.92f), DEFAULT("Default", 1f), LARGE("Large", 1.12f), XLARGE("Extra large", 1.25f)
}

/** Which home to open: everything on one page, or one calm page with what matters now. */
enum class HomeStyle(val label: String, val description: String) {
    EVERYDAY("Everyday", "What's next, what needs you, your recent notes and today, built from the app's design system"),
    TODAY("Today", "The day, your calendar and timeline, and everything you've captured"),
    CALM("Calm", "Quiet and simple, with your stories and your recent recordings and notes"),
    FOCUS("Focus", "One page only: what's next, what's due, and Record"),
    PROFESSIONAL("Work", "A briefing for the working day: next meeting and prep, what needs you, your schedule, tasks, projects and people — with everything else still here");

    companion object {
        /** The home a new install opens. People who finished onboarding before it existed keep [TODAY]. */
        val DEFAULT_FOR_NEW_INSTALLS = EVERYDAY
    }
}

/** Parts of the Today home a person can hide. */
enum class HomeSection(val label: String, val description: String) {
    CAPTURE("Quick capture", "Record, note and import buttons"),
    STORIES("Stories", "Rings for verse, word and reflections"),
    FOR_YOU("For you", "Devotional, rhythms, memories, your week"),
    TIMELINE("Calendar and timeline", "Everything by day")
}

/** How the app looks and what home shows — the person's choices, kept as one small value. */
data class Appearance(
    val accent: AccentChoice = AccentChoice.INDIGO,
    val textSize: TextSize = TextSize.DEFAULT,
    val homeStyle: HomeStyle = HomeStyle.DEFAULT_FOR_NEW_INSTALLS,
    val hidden: Set<HomeSection> = emptySet()
) {
    fun shows(section: HomeSection) = section !in hidden

    fun encode(): String = listOf(accent.name, textSize.name, homeStyle.name, hidden.joinToString(",") { it.name }).joinToString("|")

    companion object {
        /**
         * Unreadable or future values fall back to the defaults, one field at a time.
         *
         * [existingInstall] is true when onboarding finished before the Everyday home existed: nothing
         * was stored for the home, so that person keeps Today. A new install gets Everyday.
         */
        fun decode(raw: String?, existingInstall: Boolean = false): Appearance {
            val homeFallback = if (existingInstall) HomeStyle.TODAY else HomeStyle.DEFAULT_FOR_NEW_INSTALLS
            val p = raw?.split("|").orEmpty()
            fun <T> at(i: Int, parse: (String) -> T?): T? = p.getOrNull(i)?.takeIf { it.isNotBlank() }?.let(parse)
            return Appearance(
                accent = at(0) { runCatching { AccentChoice.valueOf(it) }.getOrNull() } ?: AccentChoice.INDIGO,
                textSize = at(1) { runCatching { TextSize.valueOf(it) }.getOrNull() } ?: TextSize.DEFAULT,
                homeStyle = at(2) { runCatching { HomeStyle.valueOf(it) }.getOrNull() } ?: homeFallback,
                hidden = p.getOrNull(3)?.split(",")?.mapNotNull { runCatching { HomeSection.valueOf(it) }.getOrNull() }?.toSet().orEmpty()
            )
        }
    }
}

val LocalAppearance = staticCompositionLocalOf { Appearance() }
