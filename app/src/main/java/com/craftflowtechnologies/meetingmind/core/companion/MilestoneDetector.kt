package com.craftflowtechnologies.meetingmind.core.companion

/**
 * Which Proud milestone (ZURI_EXPERIENCE §3.1 Proud, §5.7, §9 "Proud milestones") a screen has just reached.
 *
 * Pure and counts-only: callers pass what they already know (how many notes are kept, how many cards are due, the
 * days a devotional was read), never content. Each function answers "is this milestone reached now?"; whether it
 * has already been celebrated is the [MomentLedger]'s job ([Moment.Proud] is once ever), so a screen can ask on
 * every open without counting anything itself.
 *
 * [MilestoneKind.FIRST_NOTE] is never returned: the First Keep peak (§5.2) already celebrates the first note.
 */
object MilestoneDetector {

    /**
     * The tenth note fires while the count is in this window, so a note made while the app was busy still counts,
     * and someone who already had dozens of notes when this shipped isn't told "ten notes" at fifty.
     */
    val TenthNoteWindow: IntRange = 10..14

    /** Distinct days with a devotional, in one calendar month, for [MilestoneKind.SEVEN_DEVOTIONALS_IN_MONTH]. */
    const val DevotionalDaysInMonth = 7

    /**
     * [MilestoneKind.TENTH_NOTE] when [notesKept] (finished notes from real recordings, imports or typing; never
     * the onboarding example) is in [TenthNoteWindow].
     */
    fun forNotesKept(notesKept: Int): MilestoneKind? =
        if (notesKept in TenthNoteWindow) MilestoneKind.TENTH_NOTE else null

    /**
     * [MilestoneKind.ALL_CARDS_CLEARED] when nothing is due and the person reviewed at least one card today: an
     * empty deck on a day nothing was studied is not an achievement.
     */
    fun forStudy(dueNow: Int, reviewedToday: Int): MilestoneKind? =
        if (dueNow == 0 && reviewedToday > 0) MilestoneKind.ALL_CARDS_CLEARED else null

    /** [MilestoneKind.READING_PLAN_DONE] when the plan's last day is read. */
    fun forReadingPlan(daysRead: Int, planDays: Int): MilestoneKind? =
        if (planDays > 0 && daysRead >= planDays) MilestoneKind.READING_PLAN_DONE else null

    /**
     * [MilestoneKind.SEVEN_DEVOTIONALS_IN_MONTH] when devotionals were read on [DevotionalDaysInMonth] different
     * local days of the current calendar month. Several readings on one day count once.
     */
    fun forDevotionals(readAtMs: Collection<Long>, clock: LocalClock): MilestoneKind? {
        val month = CivilDate.monthOf(clock.localDay)
        val days = readAtMs.asSequence().map(clock::dayOf).filter { CivilDate.monthOf(it) == month }.toSet()
        return if (days.size >= DevotionalDaysInMonth) MilestoneKind.SEVEN_DEVOTIONALS_IN_MONTH else null
    }
}

/**
 * Calendar arithmetic on epoch days, without java.time (minSdk 24 has no desugaring here; see [LocalClock]).
 * Howard Hinnant's days-to-civil algorithm, proleptic Gregorian.
 */
internal object CivilDate {

    /** A month as one number, year × 12 + month index (0 = January), so months compare and subtract simply. */
    fun monthOf(epochDay: Long): Long {
        val (year, month) = yearMonth(epochDay)
        return year * 12 + (month - 1)
    }

    /** The year and month (1–12) of [epochDay]. */
    fun yearMonth(epochDay: Long): Pair<Long, Int> {
        val z = epochDay + 719_468
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        return year to month
    }

    /** Monday-based week number: epoch day 0 (1 January 1970) was a Thursday. */
    fun weekOf(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)
}
