package com.craftflowtechnologies.meetingmind.feature.personal.garden

import com.craftflowtechnologies.meetingmind.feature.personal.PlantRhythm
import com.craftflowtechnologies.meetingmind.feature.personal.PlantSummary
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class GardenTimeTest {

    @Test fun `the garden follows the hour`() {
        assertEquals(GardenPhase.NIGHT, GardenTime.phase(LocalTime.of(4, 59)))
        assertEquals(GardenPhase.DAWN, GardenTime.phase(LocalTime.of(5, 0)))
        assertEquals(GardenPhase.DAWN, GardenTime.phase(LocalTime.of(7, 59)))
        assertEquals(GardenPhase.DAY, GardenTime.phase(LocalTime.of(8, 0)))
        assertEquals(GardenPhase.DAY, GardenTime.phase(LocalTime.of(16, 59)))
        assertEquals(GardenPhase.DUSK, GardenTime.phase(LocalTime.of(17, 0)))
        assertEquals(GardenPhase.DUSK, GardenTime.phase(LocalTime.of(19, 59)))
        assertEquals(GardenPhase.NIGHT, GardenTime.phase(LocalTime.of(20, 0)))
        assertEquals(GardenPhase.NIGHT, GardenTime.phase(LocalTime.MIDNIGHT))
    }

    @Test fun `the day line never assumes it is morning`() {
        val thu = LocalDate.of(2026, 11, 26)
        assertEquals("Thursday, early", GardenTime.whenLine(thu, LocalTime.of(6, 0), Locale.UK))
        assertEquals("Thursday morning", GardenTime.whenLine(thu, LocalTime.of(10, 0), Locale.UK))
        assertEquals("Thursday afternoon", GardenTime.whenLine(thu, LocalTime.of(14, 0), Locale.UK))
        assertEquals("Thursday evening", GardenTime.whenLine(thu, LocalTime.of(18, 0), Locale.UK))
        assertEquals("Thursday night", GardenTime.whenLine(thu, LocalTime.of(22, 0), Locale.UK))
        assertEquals("Thursday, late", GardenTime.whenLine(thu, LocalTime.of(2, 0), Locale.UK))
    }

    @Test fun `one question a day, from the evening set after five`() {
        val d = LocalDate.of(2026, 11, 27)
        val morning = GardenQuestions.forDay(d, LocalTime.of(9, 0))
        assertEquals(morning, GardenQuestions.forDay(d, LocalTime.of(16, 0)))
        assertTrue(morning in GardenQuestions.morning)
        val evening = GardenQuestions.forDay(d, LocalTime.of(21, 0))
        assertTrue(evening in GardenQuestions.evening)
        assertEquals("TONIGHT'S QUESTION", GardenTime.questionLabel(LocalTime.of(21, 0)))
        assertEquals("TODAY'S QUESTION", GardenTime.questionLabel(LocalTime.of(9, 0)))
        assertNotEquals(GardenQuestions.forDay(d, LocalTime.of(21, 0)), GardenQuestions.forDay(d.plusDays(1), LocalTime.of(21, 0)))
    }

    @Test fun `questions stay generic`() {
        val faithWords = listOf("god", "pray", "lord", "jesus", "scripture", "church", "bless")
        (GardenQuestions.morning + GardenQuestions.evening).forEach { q ->
            faithWords.forEach { w -> assertFalse("\"$q\" uses \"$w\"", q.lowercase().contains(w)) }
        }
    }

    @Test fun `the dark theme never paints a bright sky`() {
        GardenPhase.entries.forEach { phase ->
            val p = GardenColors.palette(phase, darkTheme = true)
            assertTrue("$phase is dark", p.darkScene)
            assertTrue("$phase sky top is dim", luminance(p.skyTop.red, p.skyTop.green, p.skyTop.blue) < 0.15f)
        }
        assertFalse(GardenColors.palette(GardenPhase.DAY, darkTheme = false).darkScene)
    }

    @Test fun `the plant summary talks in chances, never streaks`() {
        assertEquals(
            "About 10 chances before we look at how it grew together. Missed days rest the plant; nothing withers.",
            PlantSummary.line(PlantRhythm.WEEKDAYS, 2)
        )
        assertTrue(PlantSummary.line(PlantRhythm.DAILY, 0).startsWith("It grows for as long as you tend it"))
        PlantRhythm.entries.forEach { r -> listOf(0, 1, 2).forEach { w -> assertFalse(PlantSummary.line(r, w).contains("streak", ignoreCase = true)) } }
    }

    private fun luminance(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
}
