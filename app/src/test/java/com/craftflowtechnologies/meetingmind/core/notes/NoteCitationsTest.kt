package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `[n]` markers in polished notes (design/notes-blocks-and-math-plan.md, C). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteCitationsTest {

    private val s1 = TranscriptSegment("s1", "m1", startMs = 0, endMs = 10_000, text = "Prayer is simply talking to God.")
    private val s2 = TranscriptSegment("s2", "m1", startMs = 72_000, endMs = 80_000, text = "Pray without ceasing, all day long.")
    private val numbered = mapOf(1 to s1, 2 to s2)

    @Test fun `markers come out of the text and become moments`() {
        val content = MarkdownImport.inline("Keep it **simple** [1]. Pray all day [2][9].")
        val (clean, cites) = NoteCitations.resolve(content, numbered)
        assertEquals("Keep it simple. Pray all day.", clean.text)
        // The bold survives the cut.
        assertEquals("simple", clean.text.substring(clean.spans.single().start, clean.spans.single().end))
        assertEquals(listOf(1 to 0L, 2 to 72_000L), cites.map { it.n to it.startMs })
        assertEquals("s2", cites[1].segmentId)
        assertEquals(80_000L, cites[1].endMs)
    }

    @Test fun `a list of numbers and unknown numbers`() {
        val (clean, cites) = NoteCitations.resolve(RichText.plain("Both said so [1, 2, 7]."), numbered)
        assertEquals("Both said so.", clean.text)
        assertEquals(listOf(1, 2), cites.map { it.n })
    }

    @Test fun `links, indexes and footnote labels are not markers`() {
        assertTrue(NoteCitations.markers("See [the notes](https://x.y) and a[0] and `b[1]` and [2]: def").isEmpty())
        assertEquals(1, NoteCitations.markers("Grace [0].").size)
    }

    @Test fun `speaker tags are dropped with the markers and hidden in old notes`() {
        val (clean, cites) = NoteCitations.resolve(RichText.plain("Pray simply [Speaker 3]. God hears [2] [speaker 3]."), numbered)
        assertEquals("Pray simply. God hears.", clean.text)
        assertEquals(listOf(2), cites.map { it.n })
        val old = "Pray simply [Speaker 3]. God hears [0]."
        val shown = NoteCitations.hidden(old).asReversed().fold(old) { acc, r -> acc.removeRange(r) }
        assertEquals("Pray simply. God hears.", shown)
    }

    @Test fun `payload round trip`() {
        val (_, cites) = NoteCitations.resolve(RichText.plain("x [2]"), numbered)
        assertEquals(cites, NoteCitations.decode(NoteCitations.encode(cites)))
        assertTrue(NoteCitations.decode("not json").isEmpty())
        assertTrue(NoteCitations.decode(null).isEmpty())
    }
}
