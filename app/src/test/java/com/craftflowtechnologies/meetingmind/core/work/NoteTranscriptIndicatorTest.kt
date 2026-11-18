package com.craftflowtechnologies.meetingmind.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteTranscriptIndicatorTest {
    private fun of(vararg s: String) = WorkRepository.transcriptIndicator(s.toList())

    @Test fun aNoteWithNoRecordingHasNoMarker() = assertNull(of())

    @Test fun aRecoveredRecordingIsNotShownAsReady() {
        // Auto-recovery saves the audio as SAVED; no notes have been made from it yet.
        assertEquals(WorkRepository.TRANSCRIPT_NOT_PROCESSED, of("SAVED"))
        assertEquals(WorkRepository.TRANSCRIPT_NOT_PROCESSED, of("QUEUED"))
        assertEquals(WorkRepository.TRANSCRIPT_NOT_PROCESSED, of("MODEL_REQUIRED"))
    }

    @Test fun readyErrorAndProcessingKeepTheirMeaning() {
        assertEquals("READY", of("READY"))
        assertEquals("READY", of("SAVED", "READY"))
        assertEquals("PROCESSING", of("READY", "RECORDING"))
        assertEquals("ERROR", of("PROCESSING", "ERROR"))
    }
}
