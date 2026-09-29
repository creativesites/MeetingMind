package com.example.ai.notes

import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.MarkdownImport
import com.example.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AI tools work on pasted Markdown blocks: their parts are readable, citable, and never lost. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PastedBlockAiTest {
    private val md = "# Plan\n\nFirst paragraph about grace.\n\n- alpha\n- beta\n\nLast paragraph about rest."
    private val pasted = NoteBlock("p1", "n", 0, NoteBlockType.MARKDOWN, RichText.plain(md), source = BlockSource.USER)
    private val mine = NoteBlock("m1", "n", 1, NoteBlockType.PARAGRAPH, RichText.plain("My own line"), source = BlockSource.USER)

    @Test fun partsGetStableIdsTracedToTheirBlock() {
        val a = MarkdownImport.expand(listOf(pasted)).map { it.id }
        val b = MarkdownImport.expand(listOf(pasted)).map { it.id }
        assertEquals(a, b)
        assertTrue(a.all { MarkdownImport.sourceBlockId(it) == "p1" })
    }

    @Test fun aiSeesEachPartOfAPastedBlock() {
        val note = com.example.core.model.Note("n", "T", com.example.core.model.RecordingType.GENERAL, null, 0, 0, null, false, false, com.example.core.model.NoteStatus.OPEN, null, emptyMap())
        val passages = NoteSources.passagesOf(note, listOf(pasted, mine))
        assertTrue(passages.any { it.text.contains("First paragraph") })
        assertTrue(passages.any { it.text.contains("alpha") })
        assertTrue(passages.first { it.text.contains("alpha") }.label.contains("Plan"))
    }

    private fun sections(vararg ids: String) = NoteAiOutcome.Sections(listOf(SectionDraft("k", "Key", ids.map { CitedItem("x $it", listOf(it)) })), emptyList())

    @Test fun organisingOpensAPastedBlockThatTheSectionsUsed() {
        val parts = MarkdownImport.expand(listOf(pasted)).filter { it.type != NoteBlockType.HEADING_1 }.map { it.id }
        val out = NoteAiApply.organized(listOf(pasted, mine), "n", sections(*parts.toTypedArray()))
        assertFalse(out.any { it.type == NoteBlockType.MARKDOWN })
        assertTrue(out.any { it.content.text == "x ${parts.first()}" })
        assertTrue(out.any { it.content.text == "My own line" })
    }

    @Test fun aBlockBarelyTouchedStaysWhole() {
        val parts = MarkdownImport.expand(listOf(pasted)).filter { it.type != NoteBlockType.HEADING_1 }.map { it.id }
        val out = NoteAiApply.organized(listOf(pasted, mine), "n", sections(parts.first()))
        assertTrue(out.any { it.type == NoteBlockType.MARKDOWN && it.id == "p1" })
    }

    @Test fun partsTheSectionsSkippedAreKeptOneByOne() {
        val parts = MarkdownImport.expand(listOf(pasted)).filter { it.type != NoteBlockType.HEADING_1 }.map { it.id }
        val out = NoteAiApply.organized(listOf(pasted), "n", sections(parts[0], parts[1]))
        val text = out.joinToString("\n") { it.content.text }
        assertTrue(text.contains("beta").not() || text.contains("Other notes"))
        assertTrue(text.contains("Last paragraph about rest"))
    }
}
