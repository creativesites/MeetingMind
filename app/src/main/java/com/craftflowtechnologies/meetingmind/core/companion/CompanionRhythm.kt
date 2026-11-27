package com.craftflowtechnologies.meetingmind.core.companion

/**
 * Calm rhythms (ZURI_EXPERIENCE §5.7, §9): a quiet line that counts what happened this week, such as "3 days with
 * the Word this week". It only ever counts up within the week and starts afresh on Monday without comment: there
 * is no streak, nothing to lose, and no line at all for a quiet week ("You haven't…" copy is never written).
 *
 * Pure: callers pass the times things happened (devotional reads, study reviews, notes kept), never content.
 */
object CompanionRhythm {

    /** Below this there is no line: one day isn't a rhythm, and zero must never be shown. */
    const val MinimumToShow = 2

    enum class Kind {
        /** Distinct days a devotional or Bible reading was opened. */
        FAITH_DAYS,
        /** Distinct days with at least one study review. */
        STUDY_DAYS,
        /** Notes kept (every finished note counts, several a day included). */
        NOTES_KEPT
    }

    /** What the line says: a [kind] and the [count] this week, always at least [MinimumToShow]. */
    data class Line(val kind: Kind, val count: Int)

    /**
     * This week's line for [kind], or null when it would be under [MinimumToShow]. The week is the local calendar
     * week, Monday to Sunday; times from the future (a clock change) are ignored.
     */
    fun thisWeek(kind: Kind, happenedAtMs: Collection<Long>, clock: LocalClock): Line? {
        val week = CivilDate.weekOf(clock.localDay)
        val inWeek = happenedAtMs.filter { it <= clock.nowMs && CivilDate.weekOf(clock.dayOf(it)) == week }
        val count = when (kind) {
            Kind.FAITH_DAYS, Kind.STUDY_DAYS -> inWeek.map(clock::dayOf).toSet().size
            Kind.NOTES_KEPT -> inWeek.size
        }
        return if (count >= MinimumToShow) Line(kind, count) else null
    }
}
