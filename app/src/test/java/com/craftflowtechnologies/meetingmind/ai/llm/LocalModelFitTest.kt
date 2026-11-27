package com.craftflowtechnologies.meetingmind.ai.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelFitTest {
    private val gb = 1024L * 1024 * 1024

    @Test
    fun `a model up to half the phone's memory may load`() {
        assertTrue(LocalModelFit.fits(modelBytes = 1_598_556_720L, totalRamBytes = 4 * gb))
        assertTrue(LocalModelFit.fits(modelBytes = 3 * gb, totalRamBytes = 6 * gb))
    }

    @Test
    fun `a model bigger than half the phone's memory is refused`() {
        assertFalse(LocalModelFit.fits(modelBytes = 3_910_050_199L, totalRamBytes = 6 * gb))
        // Half of 2 GB is 1,073,741,824 bytes, less than this 1.6 GB model.
        assertFalse(LocalModelFit.fits(modelBytes = 1_598_556_720L, totalRamBytes = 2 * gb))
    }

    @Test
    fun `unknown memory does not block loading`() {
        assertTrue(LocalModelFit.fits(modelBytes = 8 * gb, totalRamBytes = 0L))
    }
}
