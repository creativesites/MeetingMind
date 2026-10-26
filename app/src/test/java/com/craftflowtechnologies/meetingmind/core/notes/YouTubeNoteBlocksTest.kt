package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.ai.youtube.CondensedVideo
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeNoteBlocksTest {

    private val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    private val video = CondensedVideo(
        title = "Pricing for small teams",
        summary = "A talk on pricing.",
        keyPoints = listOf("Charge for outcomes"),
        transcript = listOf(CondensedVideo.TranscriptLine("00:31", "Today, pricing."))
    )

    @Test
    fun `the transcript and its heading are never shared, and nothing else is`() {
        val blocks = YouTubeNoteBlocks.blocks(url, video)
        val hidden = blocks.filter { !it.isShareable }
        // The heading goes with its lines, so a shared note never shows an empty Transcript section.
        assertEquals(listOf(NoteBlockType.HEADING_2, NoteBlockType.PARAGRAPH), hidden.map { it.type })
        assertEquals("Transcript", hidden.first().content.text)
        assertTrue(hidden.last().source == BlockSource.TRANSCRIPT)
        assertTrue(blocks.filter { it.isShareable }.none { it.source == BlockSource.TRANSCRIPT })
    }

    @Test
    fun `the video is embedded and its summary and key points are kept`() {
        val blocks = YouTubeNoteBlocks.blocks(url, video)
        val embed = blocks.first { it.type == NoteBlockType.EMBED }
        assertEquals(NoteBlock.EMBED_YOUTUBE, embed.payload[NoteBlock.PAYLOAD_EMBED_KIND])
        assertEquals(url, embed.payload[NoteBlock.PAYLOAD_URL])
        assertTrue(blocks.any { it.type == NoteBlockType.PARAGRAPH && it.content.text == "A talk on pricing." })
        assertTrue(blocks.any { it.type == NoteBlockType.BULLET && it.content.text == "Charge for outcomes" })
    }

    @Test
    fun `sharing, exporting and copying leave the transcript out and keep the rest`() {
        val blocks = YouTubeNoteBlocks.blocks(url, video)
        val markdown = NoteText.markdown(blocks, "Pricing for small teams")
        assertTrue(markdown.contains("A talk on pricing."))
        assertTrue(markdown.contains("Charge for outcomes"))
        assertFalse(markdown.contains("Today, pricing."))
        assertFalse(markdown.contains("## Transcript"))

        assertFalse(NoteText.plain(blocks).contains("Today, pricing."))
        assertFalse(NoteText.whatsApp(blocks).contains("Today, pricing."))
        assertFalse(NoteText.html(blocks).contains("Today, pricing."))
    }
}
