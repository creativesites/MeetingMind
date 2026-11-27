package com.craftflowtechnologies.meetingmind.core.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * The last step of a recording: turn a finished audio file into a Room row, and only then forget
 * the recovery journal.
 *
 * The ordering here is the whole point. Before this existed, a single failed
 * [com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository.createInitialMeeting] was
 * caught and logged, the journal was cleared anyway, and the completion callback fired as if the
 * save had succeeded — leaving a complete audio file that nothing in the app could find, with the
 * one pointer to it (the journal) thrown away. See docs/quality/INITIAL_MISSION_AUDIT_2026-10-21.md
 * finding 1.
 *
 * Two rules, both tested in `RecordingStopFinalizerTest`:
 * 1. the journal is cleared **only** after a persist actually succeeded;
 * 2. a persist is retried a small, bounded number of times first — most failures here are a busy or
 *    momentarily unreadable database, not a permanently broken one, and re-running the insert is
 *    safe because the meeting id is fixed by the caller.
 *
 * A [CancellationException] is never retried or swallowed: the caller was cancelled, and retrying a
 * cancelled save would resurrect work nobody is waiting for.
 */
internal object RecordingStopFinalizer {
    const val DEFAULT_ATTEMPTS = 3

    /**
     * Runs [persist], then [clearJournal] once. Returns true when the row was written.
     *
     * [backoffMs] is injectable so tests never sleep; the service uses the default.
     */
    suspend fun finalize(
        attempts: Int = DEFAULT_ATTEMPTS,
        backoffMs: (attempt: Int) -> Long = { attempt -> 250L * attempt },
        persist: suspend () -> Unit,
        clearJournal: () -> Unit
    ): Boolean {
        require(attempts >= 1) { "attempts must be at least 1" }
        var lastFailure: Throwable? = null
        for (attempt in 1..attempts) {
            try {
                persist()
                // Only now is the recording findable. Clearing any earlier loses the pointer to it.
                clearJournal()
                return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                lastFailure = failure
                if (attempt < attempts) {
                    // A second chance after a short pause: a locked database usually unlocks.
                    delay(backoffMs(attempt))
                }
            }
        }
        android.util.Log.e(
            "RecordingStopFinalizer",
            "Recording could not be added to the database after $attempts attempt(s); its audio is kept and the recovery journal is retained so it can be offered again.",
            lastFailure
        )
        return false
    }
}
