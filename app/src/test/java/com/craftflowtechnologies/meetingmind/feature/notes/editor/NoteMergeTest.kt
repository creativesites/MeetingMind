package com.craftflowtechnologies.meetingmind.feature.notes.editor

import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The editor's merge of unsaved edits with blocks written to the note meanwhile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteMergeTest {
    private fun b(id: String, text: String, type: NoteBlockType = NoteBlockType.PARAGRAPH) = NoteBlock(id, "n", 0, type, RichText.plain(text))
    private fun texts(list: List<NoteBlock>) = list.map { it.content.text }

    @Test fun `their new blocks and my edit both stay`() {
        val base = listOf(b("r", "rec"), b("a", "mine"))
        val ours = listOf(b("r", "rec"), b("a", "mine, edited"))
        val theirs = listOf(b("r", "rec"), b("x", "AI notes"), b("a", "mine"))
        assertEquals(listOf("rec", "AI notes", "mine, edited"), texts(NoteMerge.merge(base, ours, theirs)))
    }

    @Test fun `my new block goes after the block it followed`() {
        val base = listOf(b("r", "rec"), b("a", "one"))
        val ours = listOf(b("r", "rec"), b("a", "one"), b("n", "new line"))
        val theirs = listOf(b("r", "rec"), b("a", "one"), b("x", "AI notes"))
        assertEquals(listOf("rec", "one", "new line", "AI notes"), texts(NoteMerge.merge(base, ours, theirs)))
    }

    @Test fun `my delete holds unless they changed that block`() {
        val base = listOf(b("a", "one"), b("c", "two"))
        val ours = listOf(b("c", "two"))
        assertEquals(listOf("two"), texts(NoteMerge.merge(base, ours, listOf(b("a", "one"), b("c", "two")))))
        assertEquals(listOf("one, regenerated", "two"), texts(NoteMerge.merge(base, ours, listOf(b("a", "one, regenerated"), b("c", "two")))))
    }

    @Test fun `a block they replaced stays gone unless I edited it`() {
        val base = listOf(b("r", "rec"), b("old", "old AI"))
        val theirs = listOf(b("r", "rec"), b("new", "new AI"))
        assertEquals(listOf("rec", "new AI"), texts(NoteMerge.merge(base, base, theirs)))
        val edited = listOf(b("r", "rec"), b("old", "old AI, my words"))
        assertEquals(listOf("rec", "old AI, my words", "new AI"), texts(NoteMerge.merge(base, edited, theirs)))
    }

    @Test fun `the empty line to type on is not a change`() {
        val list = listOf(b("a", "one"), b("e", ""))
        assertEquals(listOf("one"), texts(NoteMerge.core(list)))
        assertEquals(true, NoteMerge.sameList(listOf(b("a", "one").copy(position = 3)), listOf(b("a", "one"))))
    }
}
