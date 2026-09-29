package com.example.core.work

import com.example.core.notes.InlineSpan
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerNamesTest {

    @Test fun `generic labels are recognised`() {
        listOf("Speaker 1", "SPEAKER_01", "speaker one", "Speaker A", "Unknown speaker").forEach {
            assertTrue(it, SpeakerNames.isGenericLabel(it))
        }
        listOf("Sarah", "Speaker Chen", "Mark").forEach { assertFalse(it, SpeakerNames.isGenericLabel(it)) }
    }

    @Test fun `a label is replaced in every form a model writes it`() {
        val text = "Speaker 1 will send the docs. SPEAKER_01 agreed; speaker one also asked Speaker 10."
        assertEquals(
            "Sarah Chen will send the docs. Sarah Chen agreed; Sarah Chen also asked Speaker 10.",
            SpeakerNames.replaceIn(text, "Speaker 1", "Sarah Chen")
        )
    }

    @Test fun `a real name is replaced only as a whole word with its own case`() {
        val text = "Mark will mark the date. Marketing is Mark's call."
        assertEquals("Marcus will mark the date. Marketing is Marcus's call.", SpeakerNames.replaceIn(text, "Mark", "Marcus"))
    }

    @Test fun `styles stay on the right characters`() {
        // "Speaker 2 owns **the budget**" with the bold after the name.
        val text = "Speaker 2 owns the budget"
        val rich = RichText.create(text, listOf(InlineSpan(15, 25, InlineStyle.BOLD), InlineSpan(0, 9, InlineStyle.ITALIC)))
        val out = SpeakerNames.replaceIn(rich, "Speaker 2", "Ana")
        assertEquals("Ana owns the budget", out.text)
        val bold = out.spans.first { it.style == InlineStyle.BOLD }
        assertEquals("the budget", out.text.substring(bold.start, bold.end))
        val italic = out.spans.first { it.style == InlineStyle.ITALIC }
        assertEquals("Ana", out.text.substring(italic.start, italic.end))
    }

    @Test fun `nothing changes when the name is the same or absent`() {
        assertEquals("Hello", SpeakerNames.replaceIn("Hello", "Sarah", "Sarah"))
        assertEquals("Hello", SpeakerNames.replaceIn("Hello", "", "Sarah"))
    }
}
