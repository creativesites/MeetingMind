package com.craftflowtechnologies.meetingmind.feature.moments

import com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The facts the note screen gives the planner (signature moments §4) and the snackbar's line. */
class NoteReadyRulesTest {
    private val ready = MeetingStatus.READY.name

    @Test fun `outcome follows the record, never a guess`() {
        assertEquals(NoteOutcome.COMPLETE, NoteFactsRules.outcome(ready, "A summary", 120, "OFFLINE", "parakeet"))
        assertEquals(NoteOutcome.PARTIAL, NoteFactsRules.outcome(ready, null, 120, "OFFLINE", "parakeet"))
        assertEquals(NoteOutcome.NO_SPEECH, NoteFactsRules.outcome(ready, null, 0, "OFFLINE", "parakeet"))
        assertEquals(NoteOutcome.AI_FALLBACK, NoteFactsRules.outcome(ready, "A summary", 120, "INTERNET", "parakeet-tdt"))
        assertEquals(NoteOutcome.COMPLETE, NoteFactsRules.outcome(ready, "A summary", 120, "INTERNET", "gemini-transcribe"))
        assertEquals(NoteOutcome.FAILED, NoteFactsRules.outcome(MeetingStatus.ERROR.name, null, 0, null, null))
        assertEquals(NoteOutcome.MODEL_REQUIRED, NoteFactsRules.outcome(MeetingStatus.MODEL_REQUIRED.name, null, 0, null, null))
    }

    @Test fun `prayer requests and grief tags are sensitive`() {
        assertTrue(NoteFactsRules.sensitive(RecordingType.PRAYER_REQUEST, emptyList()))
        assertTrue(NoteFactsRules.sensitive(RecordingType.SERMON, listOf(" Funeral ")))
        assertTrue(NoteFactsRules.sensitive(RecordingType.GENERAL, listOf("memorial")))
        assertFalse(NoteFactsRules.sensitive(RecordingType.SERMON, listOf("easter")))
    }

    @Test fun `Good Friday in either calendar`() {
        assertTrue(NoteFactsRules.goodFriday(LocalDate.of(2026, 4, 3)))   // Western Easter 5 April 2026
        assertTrue(NoteFactsRules.goodFriday(LocalDate.of(2026, 4, 10)))  // Orthodox Easter 12 April 2026
        assertFalse(NoteFactsRules.goodFriday(LocalDate.of(2026, 10, 13)))
    }

    @Test fun `on-device covers the offline choice and the fallback`() {
        assertTrue(NoteFactsRules.onDevice("OFFLINE", null))
        assertTrue(NoteFactsRules.onDevice("INTERNET", "whisper-small"))
        assertFalse(NoteFactsRules.onDevice("INTERNET", "gemini-transcribe"))
    }

    @Test fun `word count ignores blank runs`() = assertEquals(5, NoteFactsRules.words(listOf("one  two", "", " three four five ")))

    private fun event(id: String) = NoteReadyEvent(id, "Note $id", sermon = false, sensitive = false, atMs = 0L)

    @Test fun `one note gets its title, several waiting notes get one line`() {
        assertEquals(ReadyLine.One(event("a")), ReadyLine.of(listOf(event("a")), suppressed = { false }))
        assertEquals(ReadyLine.Many(2), ReadyLine.of(listOf(event("a"), event("b")), suppressed = { false }))
    }

    @Test fun `the note being watched is not announced`() {
        assertNull(ReadyLine.of(listOf(event("a")), suppressed = { it == "a" }))
        assertEquals(ReadyLine.One(event("b")), ReadyLine.of(listOf(event("a"), event("b")), suppressed = { it == "a" }))
    }

    @Test fun `events queue, a re-emit replaces, and an open dismisses`() {
        NoteReadyEvents.drain()
        NoteReadyEvents.emit(event("a"), isFirstEver = true)
        NoteReadyEvents.emit(event("b"), isFirstEver = false)
        NoteReadyEvents.emit(event("a"), isFirstEver = false)
        assertEquals(listOf("b", "a"), NoteReadyEvents.pending.value.map { it.noteId })
        assertEquals("a", NoteReadyEvents.companion.value?.noteId)
        NoteReadyEvents.dismiss("b")
        assertEquals(listOf("a"), NoteReadyEvents.drain().map { it.noteId })
        assertTrue(NoteReadyEvents.pending.value.isEmpty())
    }
}
