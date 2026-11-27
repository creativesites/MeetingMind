package com.craftflowtechnologies.meetingmind.feature.personal.sky

import com.craftflowtechnologies.meetingmind.core.billing.TieringMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyReadTest {

    @Test fun `at most twelve of the newest reflections are sent`() {
        val entries = (1..15).map { ReflectionEntry("e$it", "reflection $it", createdAt = it.toLong()) }
        val sent = SkyReadInput.select(entries)
        assertEquals(12, sent.size)
        assertEquals("reflection 15", sent.first())
        assertEquals("reflection 4", sent.last())
    }

    @Test fun `each reflection is cut to twelve hundred characters`() {
        val long = "x".repeat(3_000)
        val sent = SkyReadInput.select(listOf(ReflectionEntry("a", long, 1)))
        assertEquals(SkyReadInput.MAX_CHARS, sent.single().length)
    }

    @Test fun `the prompt names the theme and asks for no advice`() {
        val prompt = SkyReadPrompt.prompt("Inbox", listOf("first", "second"))
        assertTrue(prompt.contains("\"Inbox\""))
        assertTrue(prompt.contains("Entry 1: first"))
        assertTrue(prompt.contains("Entry 2: second"))
        assertTrue(prompt.contains("Do not give advice"))
    }

    @Test fun `a blank reply is not a read`() {
        assertNull(SkyReadPrompt.parse("   "))
        assertEquals("You kept coming back to it.", SkyReadPrompt.parse("  \"You kept coming back to it.\"  "))
    }

    @Test fun `the paid read is refused without a paid plan`() {
        assertFalse(SkyAccess.allows(canDeepSynthesis = false, mode = TieringMode.PRODUCTION))
    }

    @Test fun `the paid read is allowed with the paid capability`() {
        assertTrue(SkyAccess.allows(canDeepSynthesis = true, mode = TieringMode.PRODUCTION))
    }

    @Test fun `the paid read is allowed in closed testing, as Deep Synthesis allows it`() {
        assertTrue(SkyAccess.allows(canDeepSynthesis = false, mode = TieringMode.CLOSED_TESTING))
    }
}
