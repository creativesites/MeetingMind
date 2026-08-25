package com.example.ai.pipeline

import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptParagraphBuilderTest {

    private fun seg(
        id: String,
        startMs: Long,
        endMs: Long,
        text: String,
        speakerId: String? = null,
        confidence: Float? = null,
        isUserEdited: Boolean = false
    ) = TranscriptSegment(
        id = id,
        meetingId = "m1",
        speakerId = speakerId,
        speakerName = speakerId?.let { "Speaker $it" },
        startMs = startMs,
        endMs = endMs,
        text = text,
        confidence = confidence,
        isUserEdited = isUserEdited
    )

    @Test
    fun `merges short same-speaker fragments separated by breath-length pauses`() {
        val fragments = listOf(
            seg("1", 0, 1200, "So the thing is"),
            seg("2", 1500, 2600, "we need to ship this"),
            seg("3", 2900, 4000, "before Friday.")
        )

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertEquals(1, paragraphs.size)
        assertEquals("So the thing is we need to ship this before Friday.", paragraphs[0].text)
        assertEquals(0L, paragraphs[0].startMs)
        assertEquals(4000L, paragraphs[0].endMs)
    }

    @Test
    fun `starts a new paragraph after a long pause`() {
        val fragments = listOf(
            seg("1", 0, 1200, "First thought."),
            seg("2", 5000, 6200, "Completely separate thought.")
        )

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertEquals(2, paragraphs.size)
        assertEquals("First thought.", paragraphs[0].text)
        assertEquals("Completely separate thought.", paragraphs[1].text)
    }

    @Test
    fun `never merges across a speaker change even with no pause`() {
        val fragments = listOf(
            seg("1", 0, 1200, "Are we agreed?", speakerId = "spk_0"),
            seg("2", 1250, 2000, "Yes.", speakerId = "spk_1")
        )

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertEquals(2, paragraphs.size)
        assertEquals("spk_0", paragraphs[0].speakerId)
        assertEquals("spk_1", paragraphs[1].speakerId)
    }

    @Test
    fun `treats an undiarized transcript as one speaker and merges freely`() {
        val fragments = (0 until 6).map { i ->
            seg("$i", i * 1000L, i * 1000L + 800L, "part $i")
        }

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs[0].text.startsWith("Part 0") || paragraphs[0].text.startsWith("part 0"))
    }

    @Test
    fun `breaks a long monologue at the duration ceiling instead of building one wall of text`() {
        // 60 x 1s fragments back to back with no real pause = 60s of continuous speech, which is
        // past MAX_PARAGRAPH_DURATION_MS and must not become a single segment.
        val fragments = (0 until 60).map { i ->
            seg("$i", i * 1000L, i * 1000L + 900L, "word$i")
        }

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertTrue("Expected more than one paragraph, got ${paragraphs.size}", paragraphs.size > 1)
        paragraphs.forEach { paragraph ->
            assertTrue(
                "Paragraph spans ${paragraph.endMs - paragraph.startMs}ms",
                paragraph.endMs - paragraph.startMs <= TranscriptParagraphBuilder.MAX_PARAGRAPH_DURATION_MS
            )
        }
    }

    @Test
    fun `respects the character ceiling`() {
        val longText = "x".repeat(400)
        val fragments = listOf(
            seg("1", 0, 1000, longText),
            seg("2", 1100, 2000, longText)
        )

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertEquals(2, paragraphs.size)
    }

    @Test
    fun `adds the sentence break ASR omitted between two unpunctuated fragments`() {
        val fragments = listOf(
            seg("1", 0, 1000, "That is done"),
            seg("2", 1100, 2000, "Next we review the budget")
        )

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertEquals(1, paragraphs.size)
        assertEquals("That is done. Next we review the budget", paragraphs[0].text)
    }

    @Test
    fun `preserves every word - no content is dropped when merging`() {
        val fragments = listOf(
            seg("1", 0, 1000, "alpha"),
            seg("2", 1100, 2000, "bravo"),
            seg("3", 2100, 3000, "charlie")
        )

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        val merged = paragraphs.joinToString(" ") { it.text }.lowercase()
        listOf("alpha", "bravo", "charlie").forEach {
            assertTrue("Lost '$it' during merge", merged.contains(it))
        }
    }

    @Test
    fun `a merged paragraph keeps the user-edited flag if any source segment had it`() {
        val fragments = listOf(
            seg("1", 0, 1000, "First part", isUserEdited = false),
            seg("2", 1100, 2000, "second part.", isUserEdited = true)
        )

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs[0].isUserEdited)
    }

    @Test
    fun `confidence stays null when any source segment lacked a real score`() {
        val fragments = listOf(
            seg("1", 0, 1000, "First part", confidence = 0.9f),
            seg("2", 1100, 2000, "second part.", confidence = null)
        )

        val paragraphs = TranscriptParagraphBuilder.buildParagraphs(fragments)

        assertEquals(1, paragraphs.size)
        assertEquals(null, paragraphs[0].confidence)
    }

    @Test
    fun `single segment and empty input pass through untouched`() {
        assertEquals(emptyList<TranscriptSegment>(), TranscriptParagraphBuilder.buildParagraphs(emptyList()))
        val one = listOf(seg("1", 0, 1000, "Only one."))
        assertEquals(one, TranscriptParagraphBuilder.buildParagraphs(one))
    }
}
