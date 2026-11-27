package com.craftflowtechnologies.meetingmind.feature.personal

import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenQuestions
import com.craftflowtechnologies.meetingmind.feature.personal.lantern.LanternRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRulesTest {

    @Test fun `every bundled question has its own follow-up`() {
        assertTrue(LanternRules.coversAllQuestions())
        (GardenQuestions.morning + GardenQuestions.evening).forEach { q ->
            val f = LanternRules.followUp(q)
            assertTrue("\"$f\" is a question", f.endsWith("?"))
            assertFalse(f.equals(q, ignoreCase = true))
        }
        assertEquals("Say a little more about how that felt.", LanternRules.followUp("A question we don't know"))
    }

    @Test fun `the follow-up waits for the first thought, and typing is offered only early`() {
        assertFalse(LanternRules.showFollowUp(39_999))
        assertTrue(LanternRules.showFollowUp(40_000))
        assertTrue(LanternRules.canTypeInstead(9_999))
        assertFalse(LanternRules.canTypeInstead(10_000))
    }

    @Test fun `the light grows with the voice and stays calm at rest`() {
        assertEquals(1f, LanternRules.orbScale(0f), 0.0001f)
        assertEquals(1.18f, LanternRules.orbScale(1f), 0.0001f)
        assertEquals(1.18f, LanternRules.orbScale(4f), 0.0001f)
        assertEquals(1f, LanternRules.orbScale(-1f), 0.0001f)
    }

    @Test fun `seed packets take the try-this lines and nothing else`() {
        val blocks = listOf(
            SeedPackets.Line("core_mindset", "PARAGRAPH", "Guard one quiet hour."),
            SeedPackets.Line("daily_habits", "HEADING_2", "Try this week"),
            SeedPackets.Line("daily_habits", "CHECKLIST", "Protect the first hour."),
            SeedPackets.Line("daily_habits", "CHECKLIST", "  protect the first hour "),
            SeedPackets.Line("daily_habits", "CHECKLIST", ""),
            SeedPackets.Line("try_this", "CHECKLIST", "Write tomorrow's three before you close the laptop;"),
            SeedPackets.Line("try_this", "CHECKLIST", "x".repeat(200)),
            SeedPackets.Line(null, "CHECKLIST", "A stray line")
        )
        assertEquals(
            listOf("Protect the first hour", "Write tomorrow's three before you close the laptop"),
            SeedPackets.lines(blocks)
        )
    }

    @Test fun `a planted line is matched however it is cased or spaced`() {
        assertEquals(SeedPackets.key("Protect the  first hour."), SeedPackets.key(" protect the first hour"))
    }

    @Test fun `a stored reminder reads back as the sheet's choice`() {
        assertEquals(PlantNudge.NONE, PlantNudges.of(null))
        assertEquals(PlantNudge.MORNING, PlantNudges.of(8 * 60))
        assertEquals(PlantNudge.EVENING, PlantNudges.of(17 * 60 + 30))
        assertEquals(PlantNudge.MORNING, PlantNudges.of(6 * 60 + 45))
        assertEquals(PlantNudge.EVENING, PlantNudges.of(21 * 60))
    }

    @Test fun `mapping between the sheet's rhythms and stored rhythms is lossless`() {
        PlantRhythm.entries.forEach { assertEquals(it, PlantMapping.plantRhythm(PlantMapping.rhythm(it))) }
    }
}
