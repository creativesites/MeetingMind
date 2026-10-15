package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingCompanionPolicyTest {
    private val on = CompanionSettings()
    private val off = CompanionSettings(quietSermons = false)

    @Test fun `only sermons start quiet`() {
        assertTrue(RecordingCompanionPolicy.startsQuiet(RecordingType.SERMON, on))
        RecordingType.entries.filter { it != RecordingType.SERMON }.forEach {
            assertFalse("$it", RecordingCompanionPolicy.startsQuiet(it, on))
        }
    }

    @Test fun `the global setting turns quiet sermons off`() {
        assertFalse(RecordingCompanionPolicy.startsQuiet(RecordingType.SERMON, off))
        assertFalse(RecordingCompanionPolicy.showsQuietChip(RecordingType.SERMON, off))
        assertTrue(RecordingCompanionPolicy.showsQuietChip(RecordingType.SERMON, on))
        assertFalse(RecordingCompanionPolicy.showsQuietChip(RecordingType.MEETING, on))
    }

    @Test fun `quiet is a 56 dp calm pose with no level reaction, even when paused`() {
        val p = RecordingCompanionPolicy.pose(recording = true, quiet = true, screenHeightDp = 891)
        assertEquals(RecordingCompanionPolicy.Pose(CompanionState.LISTENING, CompanionVariant.Quiet, 56, false), p)
        assertEquals(p, RecordingCompanionPolicy.pose(recording = false, quiet = true, screenHeightDp = 891))
    }

    @Test fun `listening is 96 dp and level driven, paused is idle`() {
        assertEquals(
            RecordingCompanionPolicy.Pose(CompanionState.LISTENING, CompanionVariant.Normal, 96, true),
            RecordingCompanionPolicy.pose(true, false, 891)
        )
        val paused = RecordingCompanionPolicy.pose(false, false, 891)
        assertEquals(CompanionState.IDLE, paused.state)
        assertFalse(paused.levelDriven)
        assertEquals(64, RecordingCompanionPolicy.pose(true, false, 640).sizeDp)
    }

    @Test fun `the chip changes only the local choice, a fresh recording starts quiet again`() {
        var quiet = RecordingCompanionPolicy.startsQuiet(RecordingType.SERMON, on)
        quiet = !quiet // the chip, this recording only
        assertFalse(quiet)
        assertTrue(RecordingCompanionPolicy.startsQuiet(RecordingType.SERMON, on)) // the next sermon
    }
}
