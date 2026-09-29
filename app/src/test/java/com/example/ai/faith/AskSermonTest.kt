package com.example.ai.faith

import com.example.core.model.RecordingType
import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eval cases for Ask Sermon (Faith spec §36): invented timestamps, voice-of-God, missing citations. */
class AskSermonTest {
    private fun seg(id: String, ms: Long, text: String) = TranscriptSegment(id, "m", speakerName = "Pastor", startMs = ms, endMs = ms + 5000, text = text)

    private val passages = listOf(
        seg("a", 65_000, "Grace comes before effort."),
        seg("b", 760_000, "Abide in the vine, John 15."),
        seg("c", 3_725_000, "Let us pray.")
    )

    @Test fun rendersTheSameMarkersTheTranscriptShows() {
        val text = AskSermon.render(passages)
        assertTrue(text.contains("[01:05] Pastor: Grace comes before effort."))
        assertTrue(text.contains("[12:40]"))
        assertTrue(text.contains("[01:02:05]"))
    }

    @Test fun keepsRealCitationsAndAcceptsOneDigitMinutes() {
        val g = AskSermon.ground("Grace comes first [1:05]. He pointed to John 15 [12:40].", passages)
        assertEquals(listOf("a", "b"), g.cited.map { it.id })
        assertTrue(g.text.contains("[01:05]"))
        assertFalse(g.unverified)
    }

    @Test fun removesInventedTimestamps() {
        val g = AskSermon.ground("He talked about fasting [33:10]. Grace comes first [01:05].", passages)
        assertEquals(listOf("33:10"), g.dropped)
        assertFalse(g.text.contains("33:10"))
        assertEquals("He talked about fasting. Grace comes first [01:05].", g.text)
    }

    @Test fun flagsAnAnswerWithNoValidCitation() {
        assertTrue(AskSermon.ground("He said to rest in God.", passages).unverified)
        assertFalse(AskSermon.ground("The recording doesn't seem to cover that.", passages).unverified)
    }

    @Test fun dropsVoiceOfGodButKeepsReporting() {
        val g = AskSermon.ground("The preacher said God told him to plant a church [01:05]. God is telling you to trust Him today.", passages)
        assertEquals(1, g.authorityViolations.size)
        assertTrue(g.text.contains("plant a church"))
        assertFalse(g.text.contains("telling you"))
        assertTrue(AskSermon.ground("Thus says the Lord: rest.", passages).authorityViolations.isNotEmpty())
    }

    @Test fun faithTypesUseTheSermonContract() {
        assertTrue(AskSermon.isFaith(RecordingType.SERMON))
        assertFalse(AskSermon.isFaith(RecordingType.GENERAL))
        assertFalse(AskSermon.isFaith(null))
        val p = Prompts.get("ask_sermon")
        assertTrue(p.system().contains("[mm:ss]"))
        assertTrue(p.section("Forbidden").contains("Invented timestamps"))
    }
}
