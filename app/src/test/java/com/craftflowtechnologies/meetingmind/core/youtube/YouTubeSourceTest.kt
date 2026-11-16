package com.craftflowtechnologies.meetingmind.core.youtube

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.InlineStyle
import com.craftflowtechnologies.meetingmind.core.notes.NoteText
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/** Notes made from a YouTube link: play at a moment, and the source line at the bottom. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeSourceTest {

    private val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    private val parsed = YouTubeUrlParser.parse(url)!!
    private val imported = Calendar.getInstance(TimeZone.getDefault()).apply { set(2026, Calendar.NOVEMBER, 16, 12, 0, 0) }.timeInMillis
    private val info = YouTubeSource.Info(url, "Joyce Meyer Teaching on Simple Prayer", "Joyce Meyer Ministries", imported)

    private fun para(id: String, text: String, source: BlockSource = BlockSource.AI) =
        NoteBlock(id, "n", 0, NoteBlockType.PARAGRAPH, RichText.plain(text), source = source)

    @Test fun `links open at the start or at a moment`() {
        assertEquals(url, YouTubeSource.watchUrlAt(parsed, null))
        assertEquals(url, YouTubeSource.watchUrlAt(parsed, 400))
        assertEquals("$url&t=125s", YouTubeSource.watchUrlAt(parsed, 125_900))
    }

    @Test fun `source line names the video, channel, link and date`() {
        val text = YouTubeSource.sourceText(info)
        assertEquals(
            "Source: “Joyce Meyer Teaching on Simple Prayer”, Joyce Meyer Ministries (YouTube), $url, imported 16 Nov 2026. Notes written by AI from the video.",
            text.text
        )
        val link = text.spans.single { it.style == InlineStyle.LINK }
        assertEquals(url, text.text.substring(link.start, link.end))
        assertEquals("Source: YouTube, $url, imported 16 Nov 2026. Notes written by AI from the video.", YouTubeSource.sourceText(info.copy(title = null, channel = null)).text)
    }

    @Test fun `source line goes last, before the empty line kept for typing, and stays put`() {
        val blocks = listOf(para("a", "Main theme"), para("t", "Transcript"), para("e", "", BlockSource.USER))
        var n = 0
        val ensured = YouTubeSource.ensure(blocks, "n", info) { "new${n++}" }
        assertEquals(listOf("a", "t", "new0", "e"), ensured.map { it.id })
        assertEquals(YouTubeSource.SECTION_KEY, ensured[2].sectionKey)
        assertEquals(BlockSource.IMPORTED, ensured[2].source)
        // Already in place: nothing changes, nothing is saved.
        assertSame(ensured, YouTubeSource.ensure(ensured, "n", info) { "never" })
        // Re-processing put the transcript after it: it moves back to the bottom, keeping its id and words.
        val edited = ensured[2].copy(content = RichText.plain("Source: my own words"), isUserEdited = true)
        val reprocessed = listOf(ensured[0], edited, para("t2", "New transcript"))
        val moved = YouTubeSource.ensure(reprocessed, "n", info) { "never" }
        assertEquals(listOf("a", "t2", "new0"), moved.map { it.id })
        assertEquals("Source: my own words", moved.last().content.text)
    }

    @Test fun `only YouTube notes get one`() {
        val blocks = listOf(para("a", "x"))
        assertSame(blocks, YouTubeSource.ensure(blocks, "n", null) { "never" })
        assertNull(YouTubeSource.infoFor(emptyMap(), "/data/audio/rec.m4a", "Rec", 0))
        val fromRecording = YouTubeSource.infoFor(emptyMap(), "https://youtu.be/dQw4w9WgXcQ", "Video title", 5)!!
        assertEquals(url, fromRecording.url)
        assertEquals("Video title", fromRecording.title)
        assertEquals(5L, fromRecording.importedAt)
        val fromMeta = YouTubeSource.infoFor(mapOf(YouTubeSource.META_URL to url, YouTubeSource.META_CHANNEL to "Ch", YouTubeSource.META_IMPORTED_AT to "7"), null, null, 0)!!
        assertEquals("Ch", fromMeta.channel)
        assertEquals(7L, fromMeta.importedAt)
    }

    @Test fun `exports carry the source line`() {
        val withSource = YouTubeSource.ensure(listOf(para("a", "Notes")), "n", info) { "s" }
        val md = NoteText.markdown(withSource)
        assertTrue(md, md.contains("Joyce Meyer Ministries (YouTube)") && md.contains("watch?v=dQw4w9WgXcQ"))
    }
}
