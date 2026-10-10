package com.craftflowtechnologies.meetingmind.core.companion

/**
 * Hard frequency caps for companion moments (§5). The ledger is an immutable snapshot of when each
 * moment last happened; [MomentLedgerStore] persists it. Pure, so the reducer can consult it.
 *
 * Days and hours are local: callers pass the zone offset of "now" ([LocalClock]), which keeps this
 * free of java.time (minSdk 24 has no desugaring here).
 */
sealed class Moment(val key: String, val cap: Cap) {
    /** The first-recording peak (§5.2). */
    data object FirstRecordingPeak : Moment("peak.first", Cap.OnceEver)
    /** The full Celebrating hop (§5.4); later notes that day get the nod. */
    data object FullCelebration : Moment("celebrate.full", Cap.OncePerDay)
    /** Proud, once per milestone, ever (§3.1). */
    data class Proud(val kind: MilestoneKind) : Moment("proud.${kind.name}", Cap.OnceEver)
    /** A tap says "hi" at most once per 10 s (§5.3). */
    data object TapHi : Moment("tap.hi", Cap.MinInterval(10_000))
    /** A companion-voiced notification: one a day, none in quiet hours or during a Faith recording (§5.8). */
    data object CompanionNotification : Moment("notify", Cap.OncePerDay)
    /** "Share it with your circle?" once per achievement (§5.9). */
    data class AchievementSuggestion(val achievementId: String) : Moment("achievement.$achievementId", Cap.OnceEver)

    sealed interface Cap {
        data object OnceEver : Cap
        data object OncePerDay : Cap
        data class MinInterval(val ms: Long) : Cap
    }
}

/** "Now" in epoch milliseconds plus the local zone offset, so days and hours can be local. */
data class LocalClock(val nowMs: Long, val utcOffsetMs: Long = 0) {
    val localDay: Long get() = Math.floorDiv(nowMs + utcOffsetMs, DayMs)
    val hourOfDay: Int get() = Math.floorMod(Math.floorDiv(nowMs + utcOffsetMs, HourMs), 24L).toInt()

    fun dayOf(epochMs: Long): Long = Math.floorDiv(epochMs + utcOffsetMs, DayMs)

    companion object {
        const val HourMs = 3_600_000L
        const val DayMs = 86_400_000L
    }
}

data class MomentLedger(val last: Map<String, Long> = emptyMap()) {

    /** Whether [moment] may happen now. */
    fun allows(moment: Moment, clock: LocalClock, faithRecording: Boolean = false): Boolean {
        if (moment == Moment.CompanionNotification) {
            if (faithRecording || clock.hourOfDay in QuietHours) return false
        }
        val previous = last[moment.key] ?: return true
        return when (val cap = moment.cap) {
            Moment.Cap.OnceEver -> false
            Moment.Cap.OncePerDay -> clock.dayOf(previous) != clock.localDay
            is Moment.Cap.MinInterval -> clock.nowMs - previous >= cap.ms
        }
    }

    fun record(moment: Moment, clock: LocalClock): MomentLedger = copy(last = last + (moment.key to clock.nowMs))

    companion object {
        /** 22:00–07:00 by default (§5.8). */
        private val QuietHours: Set<Int> = (22..23).toSet() + (0..6).toSet()
    }
}
