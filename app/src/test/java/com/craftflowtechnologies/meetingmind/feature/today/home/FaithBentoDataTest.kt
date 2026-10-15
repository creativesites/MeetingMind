package com.craftflowtechnologies.meetingmind.feature.today.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaithBentoDataTest {

    @Test fun withoutADevotionalTheTilesShowTheirPlainDefaults() {
        val data = faithBentoData(weekCount = 3, devotional = null)
        assertEquals(3, data.weekCount)
        assertEquals("A word for your day", data.devotionalTitle)
        assertEquals("Scripture, a reflection and a prayer", data.devotionalSub)
        assertTrue(data.devotionalNew)
    }

    @Test fun theWeekCountPassesThroughUnchanged() {
        assertEquals(0, faithBentoData(weekCount = 0, devotional = null).weekCount)
        assertEquals(7, faithBentoData(weekCount = 7, devotional = null).weekCount)
    }
}
