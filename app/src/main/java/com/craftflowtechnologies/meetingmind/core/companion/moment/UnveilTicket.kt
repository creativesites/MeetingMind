package com.craftflowtechnologies.meetingmind.core.companion.moment

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/**
 * "This note just arrived" (signature moments §3.2). Written once when processing succeeds (or for the
 * onboarding sample note), consumed on the note's first open. With the flags as decided, the ticket gates
 * the **First Keep** (a note that finished in the background plays it on first open); with
 * `unveilOnEveryOpen` off it also gates the Unveil. It expires 24 h after [readyAtMs].
 *
 * The feature lane writes it on WorkManager `SUCCEEDED` with [MomentLedgerStore.issueTicket][com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore.issueTicket].
 *
 * @param firstEver the user's first real note ready (the READY note count was 0 before it).
 * @param hello the onboarding say-hello sample note (P-3 step 5): its own once-ever First Keep.
 * @param sermon a sermon recording (the quiet First Keep, or Dawn quiet).
 * @param partial the transcript is there but the summary isn't: never celebrated.
 * @param deferred a recording was running when it was opened: kept for later, then a nod (§3.3 row 4).
 * @param previousReadyAtMs when the note before this one became ready, for the back-to-back guard.
 */
data class UnveilTicket(
    val noteId: String,
    val readyAtMs: Long,
    val space: NotebookSpace? = null,
    val sermon: Boolean = false,
    val partial: Boolean = false,
    val firstEver: Boolean = false,
    val hello: Boolean = false,
    val deferred: Boolean = false,
    val previousReadyAtMs: Long? = null
) {
    init { require(noteId.isNotBlank()) { "A ticket needs a note id" } }

    fun isValid(nowMs: Long): Boolean = nowMs >= readyAtMs - ClockSkewMs && nowMs - readyAtMs < LifetimeMs

    /** The previous note-ready was under 15 minutes before this one (§8 back-to-back guard). */
    val backToBack: Boolean get() = previousReadyAtMs != null && readyAtMs - previousReadyAtMs in 0 until BackToBackMs

    /** A compact text form for DataStore (no note content, only ids, times and flags). */
    fun encode(): String = listOf(
        Version, readyAtMs, space?.name ?: "-", sermon.bit, partial.bit, firstEver.bit, hello.bit, deferred.bit,
        previousReadyAtMs ?: -1L
    ).joinToString("|")

    companion object {
        const val LifetimeMs: Long = 24 * 60 * 60 * 1000L
        const val BackToBackMs: Long = 15 * 60 * 1000L
        /** A ticket stamped a little in the future (clock change) still counts. */
        private const val ClockSkewMs: Long = 60 * 60 * 1000L
        private const val Version = 1

        /** The onboarding say-hello sample note's ticket. */
        fun hello(noteId: String, nowMs: Long, space: NotebookSpace? = null) =
            UnveilTicket(noteId, nowMs, space = space, hello = true)

        fun decode(noteId: String, raw: String?): UnveilTicket? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val p = raw.split('|')
                require(p[0].toInt() == Version)
                UnveilTicket(
                    noteId = noteId,
                    readyAtMs = p[1].toLong(),
                    space = p[2].takeIf { it != "-" }?.let { n -> NotebookSpace.entries.firstOrNull { it.name == n } },
                    sermon = p[3] == "1", partial = p[4] == "1", firstEver = p[5] == "1", hello = p[6] == "1",
                    deferred = p[7] == "1",
                    previousReadyAtMs = p[8].toLong().takeIf { it >= 0 }
                )
            }.getOrNull()
        }

        private val Boolean.bit: Int get() = if (this) 1 else 0
    }
}
