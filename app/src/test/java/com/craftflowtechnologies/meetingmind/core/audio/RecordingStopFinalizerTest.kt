package com.craftflowtechnologies.meetingmind.core.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The regression that mattered: a finished recording whose database row could not be written was
 * reported as saved and its recovery journal was cleared, so the audio existed but nothing could
 * find it (docs/quality/INITIAL_MISSION_AUDIT_2026-10-21.md, finding 1).
 *
 * These tests use fakes for the two side effects (the insert and the journal clear) and assert the
 * ordering contract the service now relies on: the journal is cleared **only** after the insert
 * succeeded, and a transient failure is retried before giving up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingStopFinalizerTest {

    private class Recorder {
        var persistCalls = 0
        var cleared = false
        fun clear() { cleared = true }
    }

    @Test
    fun `a successful save clears the journal`() = runBlocking {
        val recorder = Recorder()
        val persisted = RecordingStopFinalizer.finalize(
            backoffMs = { 0L },
            persist = { recorder.persistCalls++ },
            clearJournal = recorder::clear
        )

        assertTrue(persisted)
        assertEquals(1, recorder.persistCalls)
        assertTrue("the journal may only be cleared after a durable row", recorder.cleared)
    }

    @Test
    fun `a transient failure is retried and then clears the journal`() = runBlocking {
        val recorder = Recorder()
        val persisted = RecordingStopFinalizer.finalize(
            attempts = 3,
            backoffMs = { 0L },
            persist = {
                recorder.persistCalls++
                if (recorder.persistCalls < 3) throw IllegalStateException("database busy")
            },
            clearJournal = recorder::clear
        )

        assertTrue(persisted)
        assertEquals(3, recorder.persistCalls)
        assertTrue(recorder.cleared)
    }

    @Test
    fun `a save that never succeeds keeps the journal so the recording can be offered again`() = runBlocking {
        val recorder = Recorder()
        val persisted = RecordingStopFinalizer.finalize(
            attempts = 3,
            backoffMs = { 0L },
            persist = {
                recorder.persistCalls++
                throw IllegalStateException("disk full")
            },
            clearJournal = recorder::clear
        )

        assertFalse(persisted)
        assertEquals(3, recorder.persistCalls)
        assertFalse("the journal is the only pointer to the audio; it must survive a failed save", recorder.cleared)
    }

    @Test
    fun `cancellation is never retried and never clears the journal`() = runBlocking {
        val recorder = Recorder()
        var cancelled: Throwable? = null
        try {
            RecordingStopFinalizer.finalize(
                attempts = 3,
                backoffMs = { 0L },
                persist = {
                    recorder.persistCalls++
                    throw CancellationException("work was cancelled")
                },
                clearJournal = recorder::clear
            )
        } catch (e: CancellationException) {
            cancelled = e
        }

        assertTrue("cancellation must propagate instead of being swallowed", cancelled is CancellationException)
        assertEquals(1, recorder.persistCalls)
        assertFalse(recorder.cleared)
    }
}
