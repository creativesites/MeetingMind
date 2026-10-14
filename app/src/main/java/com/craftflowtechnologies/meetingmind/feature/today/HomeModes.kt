package com.craftflowtechnologies.meetingmind.feature.today

/** The homes that have modes. Today keeps its own focus chip, so it is not listed here. */
enum class ModeHome { WORK, CALM }

/**
 * The modes a home offers, and the words that say which one is showing and what a tap does next.
 * Pure, so the labels and the cycle are unit-tested on the JVM.
 *
 * Faith only appears when the person has a faith space, so a home without it never offers a mode
 * that would show nothing new.
 */
object HomeModes {

    /** The modes [home] offers, in the order a tap moves through them. */
    fun offered(home: ModeHome, showsFaith: Boolean): List<TodayFocus> = when (home) {
        ModeHome.WORK -> if (showsFaith) listOf(TodayFocus.ALL, TodayFocus.WORK) else listOf(TodayFocus.ALL)
        ModeHome.CALM -> if (showsFaith) listOf(TodayFocus.ALL, TodayFocus.FAITH, TodayFocus.WORK) else listOf(TodayFocus.ALL, TodayFocus.WORK)
    }

    /** The mode actually shown: [current] when the home offers it, else the home's first mode. */
    fun effective(current: TodayFocus, offered: List<TodayFocus>): TodayFocus =
        if (current in offered) current else offered.first()

    /** What a tap switches to. With one mode offered it stays on that mode. */
    fun next(current: TodayFocus, offered: List<TodayFocus>): TodayFocus {
        val index = offered.indexOf(effective(current, offered))
        return offered[(index + 1) % offered.size]
    }

    /** The mode's name, as the switch shows it. */
    fun name(mode: TodayFocus): String = when (mode) {
        TodayFocus.ALL -> "Everything"
        TodayFocus.FAITH -> "Faith"
        TodayFocus.WORK -> "Work"
    }

    /** What the mode includes on this home, so the person knows what they will see. */
    fun description(home: ModeHome, mode: TodayFocus): String = when (home) {
        ModeHome.WORK -> when (mode) {
            TodayFocus.ALL -> "Work and faith"
            else -> "Work only"
        }
        ModeHome.CALM -> when (mode) {
            TodayFocus.ALL -> "Faith, work and personal"
            TodayFocus.FAITH -> "Faith only"
            TodayFocus.WORK -> "Work only"
        }
    }

    /** "Showing Work · Work only". */
    fun showingLine(home: ModeHome, mode: TodayFocus): String = "Showing ${name(mode)} · ${description(home, mode)}"

    /** "Next: Faith", or null when there is only one mode and nothing to switch to. */
    fun nextLine(current: TodayFocus, offered: List<TodayFocus>): String? {
        if (offered.size < 2) return null
        return "Next: ${name(next(current, offered))}"
    }
}
