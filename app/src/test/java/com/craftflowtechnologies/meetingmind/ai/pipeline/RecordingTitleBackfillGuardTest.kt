package com.craftflowtechnologies.meetingmind.ai.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTitleBackfillGuardTest {

    @Test
    fun `waits while a pipeline runs or a recording is being captured`() {
        assertTrue(RecordingTitleBackfill.shouldWait(processingRows = 1, capturing = false))
        assertTrue(RecordingTitleBackfill.shouldWait(processingRows = 0, capturing = true))
    }

    @Test
    fun `a stopped recording left unprocessed does not block the backfill`() {
        // Its row may still say RECORDING, but nothing is capturing and nothing is processing.
        assertFalse(RecordingTitleBackfill.shouldWait(processingRows = 0, capturing = false))
    }
}
