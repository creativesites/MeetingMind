package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AI replies wrapped in JSON never reach a note as JSON (design/notes-blocks-and-math-plan.md, A). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiMarkdownTest {

    // The exact shapes seen on the phone: a made-up key, escaped newlines and quotes.
    private val sermonReply = """{ "sermon_notes": "## Main Theme\nBelievers are urged to keep prayer **simple**.\n\n## Key Points\n- Talk to God \"like a friend\"\n- Pray all day" }"""
    private val lectureReply = """{
  "notes": "## Overview\nFactoring a trinomial: ${'$'}2x^2 + 5x + 2 = (2x+1)(x+2)${'$'}\n\n### Steps\n1. Multiply ${'$'}a \\cdot c${'$'}\n2. Split the middle term"
}"""

    private fun texts(blocks: List<NoteBlock>) = blocks.joinToString("\n") { it.content.text }

    private fun assertClean(blocks: List<NoteBlock>) {
        val all = texts(blocks)
        assertFalse("no braces: $all", all.contains("{ \"") || all.trimStart().startsWith("{"))
        assertFalse("no literal \\n: $all", all.contains("\\n"))
        assertFalse("no escaped quotes: $all", all.contains("\\\""))
        assertFalse("no raw headings: $all", all.contains("##"))
    }

    @Test fun `sermon_notes key unwraps into headings and lists`() {
        val blocks = MarkdownImport.parse(sermonReply, "n", BlockSource.AI)
        assertClean(blocks)
        assertTrue(blocks.any { it.type == NoteBlockType.HEADING_2 && it.content.text == "Main Theme" })
        assertTrue(blocks.any { it.type == NoteBlockType.BULLET && it.content.text == "Talk to God \"like a friend\"" })
        val para = blocks.first { it.type == NoteBlockType.PARAGRAPH }
        assertTrue(para.content.spans.any { it.style == InlineStyle.BOLD })
    }

    @Test fun `notes key unwraps and keeps the maths`() {
        val blocks = MarkdownImport.parse(lectureReply, "n", BlockSource.AI)
        assertClean(blocks)
        assertTrue(blocks.any { it.type == NoteBlockType.HEADING_2 && it.content.text == "Overview" })
        assertTrue(blocks.any { it.type == NoteBlockType.HEADING_3 && it.content.text == "Steps" })
        assertTrue(texts(blocks).contains("\$a \\cdot c\$"))
        assertTrue(texts(blocks).contains("(2x+1)(x+2)"))
    }

    @Test fun `unknown keys and several values join in order`() {
        val md = AiMarkdown.unwrapJson("""{"title_text": "## One", "body_v2": "Two\nlines", "count": 3, "ok": true}""", strict = false)
        assertEquals("## One\n\nTwo\nlines", md)
    }

    @Test fun `nested escapes decode`() {
        val md = AiMarkdown.clean("""{"x": "Say \"hi\" été\nPath a\/b and back\\slash and ${'$'}\\frac{1}{2}${'$'}"}""")
        assertEquals("Say \"hi\" été\nPath a/b and back\\slash and \$\\frac{1}{2}\$", md)
    }

    @Test fun `fenced JSON reply unwraps`() {
        val md = AiMarkdown.clean("```json\n$sermonReply\n```")
        assertTrue(md.startsWith("## Main Theme\nBelievers"))
    }

    @Test fun `reply cut off mid string still unwraps`() {
        val md = AiMarkdown.clean("""{ "sermon_notes": "## Main Theme\nBelievers are urged to pr""")
        assertEquals("## Main Theme\nBelievers are urged to pr", md)
    }

    @Test fun `nested objects and string lists become sections and bullets`() {
        val md = AiMarkdown.clean("""{"notes": {"main_theme": "Prayer is simple", "points": ["Talk", "Listen"]}}""")
        assertEquals("**Main theme:** Prayer is simple\n\n## Points\n\n- Talk\n- Listen", md)
    }

    @Test fun `plain Markdown is left alone`() {
        val md = "## Heading\n\n- one\n- two\n\nA {curly} word."
        assertSame(md, AiMarkdown.clean(md))
        assertEquals(MarkdownImport.parse(md, "n").map { it.type to it.content.text }, MarkdownImport.parse(md, "n", BlockSource.AI).map { it.type to it.content.text })
    }

    @Test fun `a real JSON code block stays a code block`() {
        val sample = "```json\n{\"name\": \"Bob\", \"age\": 3}\n```"
        val blocks = MarkdownImport.parse(sample, "n", BlockSource.AI)
        assertEquals(listOf(NoteBlockType.CODE), blocks.map { it.type })
        assertEquals("{\"name\": \"Bob\", \"age\": 3}", blocks.single().content.text)
        val inDoc = MarkdownImport.parse("## Config\n\n$sample\n\nDone.", "n", BlockSource.AI)
        assertTrue(inDoc.any { it.type == NoteBlockType.CODE && it.content.text.startsWith("{\"name\"") })
    }

    @Test fun `a person's JSON is never unwrapped`() {
        val mine = MarkdownImport.parse(sermonReply, "n", BlockSource.USER)
        assertEquals(1, mine.size)
        assertTrue(mine.single().content.text.startsWith("{"))
        assertSame(mine, AiMarkdown.repairBlocks(mine))
        assertNull(AiMarkdown.unwrapJson("""{"name": "Bob"}"""))
    }

    // ---------------------------------------------------------------- notes already on phones

    /** What the old code saved: the whole reply as one AI paragraph, after inline Markdown ran over it. */
    private fun savedBroken(reply: String) = MarkdownImport.parse(reply, "n", BlockSource.USER)
        .map { it.copy(source = BlockSource.AI, sectionKey = "ai_polished_notes", payload = mapOf(NoteBlock.PAYLOAD_MEETING_ID to "m1", NoteBlock.PAYLOAD_START_MS to "1000")) }

    @Test fun `a saved broken paragraph repairs at render time`() {
        val saved = savedBroken(sermonReply)
        assertEquals(1, saved.size)
        val fixed = AiMarkdown.repairBlocks(saved)
        assertClean(fixed)
        assertTrue(fixed.any { it.type == NoteBlockType.HEADING_2 && it.content.text == "Key Points" })
        // Bold went into a span when the JSON was saved; it comes back.
        assertTrue(fixed.first { it.type == NoteBlockType.PARAGRAPH }.content.spans.any { it.style == InlineStyle.BOLD })
        assertEquals(saved.single().id, fixed.first().id)
        assertTrue(fixed.all { it.sectionKey == "ai_polished_notes" && it.payload[NoteBlock.PAYLOAD_MEETING_ID] == "m1" })
        assertEquals("1000", fixed.first().payload[NoteBlock.PAYLOAD_START_MS])
        // Stable ids: the same note opened twice repairs the same way.
        assertEquals(fixed.map { it.id }, AiMarkdown.repairBlocks(saved).map { it.id })
    }

    @Test fun `LaTeX in a saved broken paragraph survives`() {
        val fixed = AiMarkdown.repairBlocks(savedBroken(lectureReply))
        val all = texts(fixed)
        assertTrue(all, all.contains("\$a \\cdot c\$"))
        val times = AiMarkdown.repairBlocks(savedBroken("""{"notes": "## Rule\n${'$'}a \\times b \\neq 0${'$'}"}"""))
        assertTrue(texts(times), texts(times).contains("\$a \\times b \\neq 0\$"))
    }

    @Test fun `expand reads the repaired blocks for export and search`() {
        val note = listOf(NoteBlock("h", "n", 0, NoteBlockType.HEADING_1, RichText.plain("Joyce Meyer"))) + savedBroken(sermonReply)
        val expanded = MarkdownImport.expand(note)
        assertClean(expanded.drop(1))
        val broken = note[1].id
        assertTrue(expanded.any { it.type == NoteBlockType.HEADING_2 && it.content.text == "Main Theme" && it.id == "$broken${MarkdownImport.EXPANDED_SEP}0" })
        val text = NoteText.plain(note)
        assertFalse(text, text.contains("sermon_notes") || text.contains("\\n"))
    }

    @Test fun `AI Markdown block saved as JSON shows its Markdown`() {
        val block = NoteBlock("b", "n", 0, NoteBlockType.MARKDOWN, RichText.plain(sermonReply), source = BlockSource.AI)
        assertTrue(AiMarkdown.repairMarkdown(block).startsWith("## Main Theme\n"))
        assertEquals(sermonReply, AiMarkdown.repairMarkdown(block.copy(source = BlockSource.USER)))
    }

    @Test fun `list preview of a saved broken note is clean`() {
        val plain = "Scripture references\n" + savedBroken(sermonReply).single().content.text + "\nTranscript"
        val preview = NotePreview.lead(plain, 400)
        assertFalse(preview, preview.contains("{") || preview.contains("\\n") || preview.contains("#") || preview.contains("sermon_notes"))
        assertTrue(preview, preview.contains("Main Theme Believers are urged"))
    }
}
