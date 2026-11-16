package com.craftflowtechnologies.meetingmind.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which recordings need the person under Needs you: the predicate behind WorkDao.observeFailed. */
class FailedRecordingsTest {
    private val now = 10_000_000_000L
    private val minute = 60_000L

    @Test fun `an error and a recording that needs a model both need the person`() {
        assertTrue(needsRecovery("ERROR", updatedAt = now, now = now))
        assertTrue(needsRecovery("MODEL_REQUIRED", updatedAt = now, now = now))
    }

    @Test fun `a queued recording counts only once it has waited thirty minutes`() {
        assertTrue(needsRecovery("QUEUED", updatedAt = now - 31 * minute, now = now))
        assertFalse(needsRecovery("QUEUED", updatedAt = now - 29 * minute, now = now))
        assertEquals(30 * minute, QUEUED_STALE_MS)
    }

    @Test fun `ready, recording and processing recordings are not failures`() {
        listOf("READY", "RECORDING", "PROCESSING", "SAVED").forEach { status ->
            assertFalse(status, needsRecovery(status, updatedAt = now - 90 * minute, now = now))
        }
    }
}
