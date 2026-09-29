package com.craftflowtechnologies.meetingmind.feature.notes.editor

import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.HtmlToMarkdown
import com.craftflowtechnologies.meetingmind.core.notes.InlineStyle
import com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PasteMarkdownTest {

    private fun p(id: String, text: String = "", type: NoteBlockType = NoteBlockType.PARAGRAPH) =
        NoteBlock(id = id, noteId = "n", position = 0, type = type, content = RichText.plain(text))

    private fun paste(blocks: List<NoteBlock>, index: Int, newText: String, html: String? = null) =
        BlockEditing.pasteMarkdown(blocks, index, BlockEditing.insertion(blocks[index].content.text, newText), html?.let(HtmlToMarkdown::convert))

    @Test
    fun `finds what was inserted`() {
        assertEquals(BlockEditing.Insertion(3, 3, "XY"), BlockEditing.insertion("abcdef", "abcXYdef"))
        assertEquals(BlockEditing.Insertion(1, 3, "Z"), BlockEditing.insertion("abcd", "aZd"))
    }

    @Test
    fun `a long paste becomes one Markdown block, however many lines it has`() {
        val answer = (1..300).joinToString("\n") { "Line $it of the answer" }
        val blocks = listOf(p("a", "Before"), p("b"), p("c", "After"))
        val r = paste(blocks, 1, answer)!!
        assertEquals(listOf(NoteBlockType.PARAGRAPH, NoteBlockType.MARKDOWN, NoteBlockType.PARAGRAPH, NoteBlockType.PARAGRAPH), r.blocks.map { it.type })
        assertEquals("b", r.blocks[1].id)
        // Plain lines stay separate lines when shown.
        assertEquals(300, MarkdownImport.parse(r.blocks[1].content.text, "n").size)
        assertEquals(FocusTarget(r.blocks[2].id, 0), r.focus)
    }

    @Test
    fun `Markdown text is kept as Markdown and shows formatted`() {
        val r = paste(listOf(p("a")), 0, "## Plan\n\n- **One**\n- Two\n\nDone.")!!
        val md = r.blocks.first()
        assertEquals(NoteBlockType.MARKDOWN, md.type)
        val shown = MarkdownImport.parse(md.content.text, "n")
        assertEquals(listOf(NoteBlockType.HEADING_2, NoteBlockType.BULLET, NoteBlockType.BULLET, NoteBlockType.PARAGRAPH), shown.map { it.type })
        assertTrue(shown[1].content.hasStyle(InlineStyle.BOLD, 0, 3))
    }

    @Test
    fun `a paste mid-sentence keeps the words on both sides`() {
        val r = paste(listOf(p("a", "Start end")), 0, "Start one\ntwo\nend".let { "Start " + "one\ntwo\n" + "end" })!!
        assertEquals(listOf("Start", "one\n\ntwo", "end"), r.blocks.map { it.content.text.trim() })
        assertEquals(NoteBlockType.MARKDOWN, r.blocks[1].type)
    }

    @Test
    fun `formatting copied from a web page comes through the clipboard's HTML`() {
        val plain = "Launch plan\nShip by Friday\nWrite the API\nReview it"
        val html = "<h2>Launch plan</h2><p>Ship by <strong>Friday</strong></p><ul><li>Write the API</li><li>Review it</li></ul>"
        val md = paste(listOf(p("a")), 0, plain, html)!!.blocks.first().content.text
        assertEquals("## Launch plan\n\nShip by **Friday**\n\n- Write the API\n- Review it", md)
    }

    @Test
    fun `one line keeps its bold and links inline`() {
        val r = paste(listOf(p("a", "Hi ")), 0, "Hi see **this** and [docs](https://x.io)")!!
        val c = r.blocks.single().content
        assertEquals("Hi see this and docs", c.text)
        assertTrue(c.hasStyle(InlineStyle.BOLD, 7, 11))
        assertEquals("https://x.io", c.linkAt(17)!!.url)
    }

    @Test
    fun `a lone YouTube link becomes a video`() {
        val r = paste(listOf(p("a")), 0, "https://youtu.be/dQw4w9WgXcQ")!!
        assertEquals(NoteBlockType.EMBED, r.blocks[0].type)
        assertEquals(NoteBlock.EMBED_YOUTUBE, r.blocks[0].payload[NoteBlock.PAYLOAD_EMBED_KIND])
    }

    @Test
    fun `typing, Enter and plain one-line pastes are left to the normal rules`() {
        assertNull(paste(listOf(p("a", "Hello")), 0, "Hello!"))
        assertNull(paste(listOf(p("a", "Hello")), 0, "Hello\n"))
        assertNull(paste(listOf(p("a")), 0, "Call John tomorrow"))
    }

    @Test
    fun `tidying turns runs of text into single blocks and leaves the rest`() {
        val image = p("img", type = NoteBlockType.IMAGE)
        val blocks = listOf(p("1", "Title", NoteBlockType.HEADING_1), p("2", "one", NoteBlockType.BULLET), p("3", "two", NoteBlockType.BULLET), image, p("4", "After"), p("5"))
        val r = BlockEditing.combineIntoMarkdown(blocks, 0, blocks.lastIndex)
        assertEquals(listOf(NoteBlockType.MARKDOWN, NoteBlockType.IMAGE, NoteBlockType.MARKDOWN), r.map { it.type })
        assertEquals("# Title\n\n- one\n- two", r[0].content.text)
    }

    @Test
    fun `a folded heading hides its section up to the next heading of its level`() {
        val h1 = p("h1", "A", NoteBlockType.HEADING_2).copy(payload = mapOf(NoteBlock.PAYLOAD_FOLDED to "1"))
        val blocks = listOf(h1, p("x", "in A"), p("s", "sub", NoteBlockType.HEADING_3), p("y", "in sub"), p("h2", "B", NoteBlockType.HEADING_2), p("z", "in B"))
        assertEquals(listOf("h1", "h2", "z"), BlockEditing.visibleBlocks(blocks).map { it.id })
    }
}
