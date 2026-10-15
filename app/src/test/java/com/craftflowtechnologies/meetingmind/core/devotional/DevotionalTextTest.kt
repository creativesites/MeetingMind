package com.craftflowtechnologies.meetingmind.core.devotional

import com.craftflowtechnologies.meetingmind.core.scripture.Passage
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevotionalTextTest {
    private val ref = ScriptureReferenceParser.parse("Matthew 11:28-30")!!
    private val d = Devotional(
        day = LocalDay("2026-10-09"), origin = DevotionalOrigin.CLOUD_AI, title = "Rest", scripture = listOf(ref),
        reflection = listOf("One.", "Two."), application = listOf("Pause"), prayer = "Lord, amen.", motivation = "Rest.", question = "Where?", label = "AI-written devotional"
    )

    @Test fun `scripture copy carries reference version and attribution`() {
        val text = DevotionalText.scripture(ref, Passage(ref, "Come to me", 1, "NIV", "Scripture quotations from NIV"))
        assertTrue(text.contains("“Come to me”")); assertTrue(text.contains(ref.display())); assertTrue(text.contains("(NIV)")); assertTrue(text.contains("quotations from NIV"))
        assertEquals(ref.display(), DevotionalText.scripture(ref, null))
    }

    @Test fun `blocks copy on their own`() {
        assertEquals("One.\n\nTwo.", DevotionalText.block(d, DevotionalBlock.REFLECTION))
        assertEquals("• Pause", DevotionalText.block(d, DevotionalBlock.APPLICATION))
        assertEquals("Lord, amen.", DevotionalText.block(d, DevotionalBlock.PRAYER))
        assertNull(DevotionalText.block(d, DevotionalBlock.QUOTE))
    }

    @Test fun `whole devotional includes every block and the verse text`() {
        val all = DevotionalText.all(d, "Friday", mapOf(ref.display() to Passage(ref, "Come to me", 1, "NIV", "attr")))
        listOf("Rest", "SCRIPTURE", "Come to me", "(NIV)", "REFLECTION", "One.", "TODAY I WILL", "PRAYER", "A WORD FOR TODAY", "A QUESTION TO SIT WITH", "AI-written devotional").forEach { assertTrue(it, all.contains(it)) }
    }
}
