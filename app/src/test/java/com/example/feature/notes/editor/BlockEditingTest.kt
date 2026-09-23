package com.example.feature.notes.editor

import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NoteBlockType.BULLET
import com.example.core.model.NoteBlockType.CHECKLIST
import com.example.core.model.NoteBlockType.HEADING_1
import com.example.core.model.NoteBlockType.IMAGE
import com.example.core.model.NoteBlockType.NUMBERED
import com.example.core.model.NoteBlockType.PARAGRAPH
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEditingTest {

    private fun b(id: String, type: NoteBlockType = PARAGRAPH, text: String = "", indent: Int = 0, source: BlockSource = BlockSource.USER) =
        NoteBlock(id = id, noteId = "n", position = 0, type = type, content = RichText.plain(text), indent = indent, source = source)

    @Test
    fun `plain typing replaces the content and keeps the caret`() {
        val r = BlockEditing.applyTextEdit(listOf(b("a", text = "Hi")), 0, RichText.plain("Hi!"), 3)
        assertEquals("Hi!", r.blocks.single().content.text)
        assertEquals(FocusTarget("a", 3), r.focus)
    }

    @Test
    fun `enter in the middle splits the block and keeps styling on both sides`() {
        val styled = RichText.plain("Hello\nworld").applyStyle(InlineStyle.BOLD, 3, 9)
        val r = BlockEditing.applyTextEdit(listOf(b("a", text = "Helloworld")), 0, styled, 6)
        assertEquals(listOf("Hello", "world"), r.blocks.map { it.content.text })
        assertEquals(3, r.blocks[0].content.spans.single().start)
        assertEquals(0 to 3, r.blocks[1].content.spans.single().let { it.start to it.end })
        assertEquals(FocusTarget(r.blocks[1].id, 0), r.focus)
    }

    @Test
    fun `enter on a list item continues the list at the same depth`() {
        val r = BlockEditing.applyTextEdit(listOf(b("a", BULLET, "Milk", indent = 1)), 0, RichText.plain("Milk\n"), 5)
        assertEquals(listOf(BULLET, BULLET), r.blocks.map { it.type })
        assertEquals(1, r.blocks[1].indent)
    }

    @Test
    fun `enter after a heading starts a paragraph`() {
        val r = BlockEditing.applyTextEdit(listOf(b("a", HEADING_1, "Title")), 0, RichText.plain("Title\n"), 6)
        assertEquals(listOf(HEADING_1, PARAGRAPH), r.blocks.map { it.type })
    }

    @Test
    fun `enter on an empty list item ends the list`() {
        val r = BlockEditing.applyTextEdit(listOf(b("a", CHECKLIST)), 0, RichText.plain("\n"), 1)
        assertEquals(PARAGRAPH, r.blocks.single().type)
        assertEquals("", r.blocks.single().content.text)
    }

    @Test
    fun `pasting several lines makes several blocks with the caret at the end of the paste`() {
        val r = BlockEditing.applyTextEdit(listOf(b("a", text = "xy")), 0, RichText.plain("xone\ntwo\nthreey"), 14)
        assertEquals(listOf("xone", "two", "threey"), r.blocks.map { it.content.text })
        assertEquals(FocusTarget(r.blocks[2].id, 5), r.focus)
    }

    @Test
    fun `editing an ai block marks it user edited`() {
        val r = BlockEditing.applyTextEdit(listOf(b("a", text = "Key point", source = BlockSource.AI)), 0, RichText.plain("Key points"), 10)
        assertTrue(r.blocks.single().isUserEdited)
    }

    @Test
    fun `backspace at the start of a list item first outdents, then clears the format`() {
        val nested = BlockEditing.backspaceAtStart(listOf(b("a", BULLET, "x", indent = 1)), 0)
        assertEquals(0, nested.blocks.single().indent)
        val plain = BlockEditing.backspaceAtStart(nested.blocks, 0)
        assertEquals(PARAGRAPH, plain.blocks.single().type)
    }

    @Test
    fun `backspace at the start of a paragraph joins it to the text above`() {
        val r = BlockEditing.backspaceAtStart(listOf(b("a", text = "Hello "), b("b", text = "world")), 1)
        assertEquals(listOf("Hello world"), r.blocks.map { it.content.text })
        assertEquals(FocusTarget("a", 6), r.focus)
    }

    @Test
    fun `backspace never deletes a picture`() {
        val blocks = listOf(b("t", text = "above"), b("img", IMAGE), b("p", text = "caption words"))
        val kept = BlockEditing.backspaceAtStart(blocks, 2)
        assertEquals(3, kept.blocks.size)

        val empty = BlockEditing.backspaceAtStart(listOf(b("t", text = "above"), b("img", IMAGE), b("p")), 2)
        assertEquals(listOf("t", "img"), empty.blocks.map { it.id })
        assertEquals(FocusTarget("t", 5), empty.focus)
    }

    @Test
    fun `backspace in the first block does nothing`() {
        val r = BlockEditing.backspaceAtStart(listOf(b("a", text = "x")), 0)
        assertEquals("x", r.blocks.single().content.text)
    }

    @Test
    fun `toggling a type turns it on and back off`() {
        val on = BlockEditing.toggleType(listOf(b("a", text = "x")), 0, NUMBERED)
        assertEquals(NUMBERED, on.single().type)
        assertEquals(PARAGRAPH, BlockEditing.toggleType(on, 0, NUMBERED).single().type)
    }

    @Test
    fun `indent is limited to one level deeper than the item above`() {
        val blocks = listOf(b("a", BULLET, "1"), b("b", BULLET, "2"))
        val once = BlockEditing.indent(blocks, 1, 1)
        assertEquals(1, once[1].indent)
        assertEquals(1, BlockEditing.indent(once, 1, 1)[1].indent)
        assertEquals(0, BlockEditing.indent(blocks, 0, 1)[0].indent)
    }

    @Test
    fun `numbers count consecutive items at a level, skipping deeper ones`() {
        val blocks = listOf(
            b("h", HEADING_1, "x"),
            b("1", NUMBERED), b("1a", BULLET, indent = 1), b("2", NUMBERED),
            b("p"), b("again", NUMBERED)
        )
        assertEquals(1, BlockEditing.numberFor(blocks, 1))
        assertEquals(2, BlockEditing.numberFor(blocks, 3))
        assertEquals(1, BlockEditing.numberFor(blocks, 5))
    }

    @Test
    fun `inserting into an empty paragraph replaces it and keeps a paragraph after media`() {
        val r = BlockEditing.insertAfter(listOf(b("t", text = "x"), b("empty")), 1, listOf(b("img", IMAGE)))
        assertEquals(listOf("t", "img"), r.blocks.take(2).map { it.id })
        assertEquals(PARAGRAPH, r.blocks.last().type)
        assertEquals(r.blocks.last().id, r.focus?.blockId)
    }

    @Test
    fun `move, duplicate and delete`() {
        val blocks = listOf(b("a"), b("b"), b("c"))
        assertEquals(listOf("b", "c", "a"), BlockEditing.move(blocks, 0, 2).map { it.id })
        assertEquals(4, BlockEditing.duplicate(blocks, 1).size)
        assertEquals(1, BlockEditing.delete(listOf(b("only")), 0).size)
    }

    @Test
    fun `word count only counts text blocks`() {
        assertEquals(3, BlockEditing.wordCount(listOf(b("a", text = "one two"), b("b", IMAGE, "caption"), b("c", text = " three "))))
    }

    @Test
    fun `undo coalesces typing in one block and redo restores`() {
        val history = UndoHistory()
        val v0 = listOf(b("a", text = ""))
        val v1 = listOf(b("a", text = "h"))
        history.record(v0, typingBlockId = "a", now = 0)
        history.record(v1, typingBlockId = "a", now = 100)
        val undone = history.undo(listOf(b("a", text = "hi")))
        assertEquals(v0, undone)
        assertFalse(history.canUndo)
        assertEquals("hi", history.redo(undone!!)!!.single().content.text)
        assertNull(history.redo(v0))
    }

    @Test
    fun `typing a markdown prefix turns a paragraph into that block`() {
        val bullet = BlockEditing.applyShortcut(listOf(b("a", text = "- ")), 0, 2)!!
        assertEquals(BULLET, bullet.blocks.single().type)
        assertEquals("", bullet.blocks.single().content.text)
        assertEquals(NoteBlockType.HEADING_2, BlockEditing.applyShortcut(listOf(b("a", text = "## ")), 0, 3)!!.blocks.single().type)
        assertEquals(CHECKLIST, BlockEditing.applyShortcut(listOf(b("a", text = "[] ")), 0, 3)!!.blocks.single().type)
    }

    @Test
    fun `a shortcut does not fire mid-line or on non paragraphs`() {
        assertNull(BlockEditing.applyShortcut(listOf(b("a", text = "- already here")), 0, 14))
        assertNull(BlockEditing.applyShortcut(listOf(b("a", BULLET, "- ")), 0, 2))
        assertNull(BlockEditing.applyShortcut(listOf(b("a", text = "-x")), 0, 2))
    }
}
