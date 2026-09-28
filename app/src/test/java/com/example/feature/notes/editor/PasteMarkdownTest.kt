package com.example.feature.notes.editor

import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
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

    private fun p(id: String, text: String = "") =
        NoteBlock(id = id, noteId = "n", position = 0, type = NoteBlockType.PARAGRAPH, content = RichText.plain(text))

    private fun paste(blocks: List<NoteBlock>, index: Int, newText: String) =
        BlockEditing.pasteMarkdown(blocks, index, BlockEditing.insertion(blocks[index].content.text, newText))

    @Test
    fun `finds what was inserted`() {
        assertEquals(BlockEditing.Insertion(3, 3, "XY"), BlockEditing.insertion("abcdef", "abcXYdef"))
        assertEquals(BlockEditing.Insertion(1, 3, "Z"), BlockEditing.insertion("abcd", "aZd"))
    }

    @Test
    fun `an AI answer pasted into an empty line becomes blocks in its place`() {
        val blocks = listOf(p("a", "Before"), p("b"), p("c", "After"))
        val r = paste(blocks, 1, "## Plan\n\n- **One**\n- Two\n\nDone.")!!
        assertEquals(listOf("Before", "Plan", "One", "Two", "Done.", "After"), r.blocks.map { it.content.text })
        assertEquals(NoteBlockType.HEADING_2, r.blocks[1].type)
        assertEquals("b", r.blocks[1].id)
        assertTrue(r.blocks[2].content.hasStyle(InlineStyle.BOLD, 0, 3))
        assertEquals(FocusTarget(r.blocks[4].id, 5), r.focus)
    }

    @Test
    fun `a paste mid-sentence splits around the caret`() {
        val blocks = listOf(p("a", "Start end"))
        val r = paste(blocks, 0, "Start - x\n- y\nend".let { "Start " + "- x\n- y\n" + "end" })!!
        assertEquals(listOf("Start", "x", "y", "end"), r.blocks.map { it.content.text.trim() })
        assertEquals(NoteBlockType.BULLET, r.blocks[1].type)
        assertEquals(FocusTarget(r.blocks[3].id, 0), r.focus)
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
        assertEquals(NoteBlockType.PARAGRAPH, r.blocks[1].type)
    }

    @Test
    fun `plain text and typing are left to the normal rules`() {
        assertNull(paste(listOf(p("a", "Hello")), 0, "Hello!"))
        assertNull(paste(listOf(p("a")), 0, "Call John\nBuy milk"))
    }

    @Test
    fun `a link pasted into a sentence stays text, as a link`() {
        val r = paste(listOf(p("a", "x")), 0, "x https://youtu.be/dQw4w9WgXcQ")!!
        assertEquals(NoteBlockType.PARAGRAPH, r.blocks.single().type)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", r.blocks.single().content.linkAt(5)!!.url)
    }
}
