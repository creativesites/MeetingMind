package com.example.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for the design spec's §3.8 thresholds — no Robolectric needed, same
 * pattern as `PlaybackControllerLogicTest`.
 */
class RecordingCapacityTest {

    @Test
    fun `formatStorageLine reports GB and an hours estimate that never overstates capacity`() {
        // 11 GB free at 128kbps (16000 B/s) is a little over 200 hours of headroom; assert the
        // exact arithmetic rather than a hand-picked round number, so this pins the formula.
        val elevenGbMb = 11L * 1024
        val expectedHours = (elevenGbMb * 1024L * 1024L) / ((128_000L / 8) * 3600L)
        val line = RecordingCapacity.formatStorageLine(elevenGbMb)
        assertEquals("11 GB free, enough for about $expectedHours hours", line)
    }

    @Test
    fun `formatStorageLine handles sub-1GB free space without claiming a negative or absurd value`() {
        val line = RecordingCapacity.formatStorageLine(300L)
        assertTrue(line.startsWith("0.3 GB free"))
    }

    @Test
    fun `formatStorageLine never goes negative for zero free space`() {
        assertEquals("0.0 GB free, enough for about 0 hours", RecordingCapacity.formatStorageLine(0L))
    }

    @Test
    fun `shouldRefuseToStart is true only below the 200MB floor`() {
        assertTrue(RecordingCapacity.shouldRefuseToStart(199L))
        assertFalse(RecordingCapacity.shouldRefuseToStart(200L))
        assertFalse(RecordingCapacity.shouldRefuseToStart(500L))
    }

    @Test
    fun `inFlightWarning is null when both storage and battery are healthy`() {
        assertNull(RecordingCapacity.inFlightWarning(availableStorageMb = 2000L, batteryPercent = 80))
    }

    @Test
    fun `inFlightWarning flags low storage before checking battery`() {
        val warning = RecordingCapacity.inFlightWarning(availableStorageMb = 500L, batteryPercent = 90)
        assertEquals("Storage is running low. Recording may stop.", warning)
    }

    @Test
    fun `inFlightWarning flags low battery when storage is fine`() {
        val warning = RecordingCapacity.inFlightWarning(availableStorageMb = 2000L, batteryPercent = 15)
        assertEquals("Battery low — recording may stop soon.", warning)
    }

    @Test
    fun `inFlightWarning skips the battery check when the percentage is unknown`() {
        assertNull(RecordingCapacity.inFlightWarning(availableStorageMb = 2000L, batteryPercent = null))
    }
}
