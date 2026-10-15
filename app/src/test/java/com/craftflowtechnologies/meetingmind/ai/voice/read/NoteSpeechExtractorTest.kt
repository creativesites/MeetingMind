package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSpeechExtractorTest {

    private val verseWords = john316.verses!!.joinToString(" ") { it.text }

    // --- scripture is the translation's words, untouched ---

    @Test fun `scripture is spoken as the translation's own words`() {
        val b = Blocks().apply { scripture("John 3:16-17") }
        val unit = extract(b, lookupOf("John 3:16-17" to john316)).sections.single().units.single()
        assertEquals(verseWords, unit.text)
        assertEquals(SpeechRole.SCRIPTURE, unit.role)
        assertTrue(unit.locked)
        assertEquals("Scripture. John chapter 3, verses 16 to 17. Berean Standard Bible.", unit.lead)
    }

    @Test fun `verse numbers are spoken only when asked for and only add tokens`() {
        val b = Blocks().apply { scripture("John 3:16-17") }
        val off = extract(b, lookupOf("John 3:16-17" to john316)).sections.single().units.single().text
        val on = extract(b, lookupOf("John 3:16-17" to john316), ExtractOptions(readVerseNumbers = true)).sections.single().units.single().text
        assertEquals(verseWords, off)
        assertTrue(on.startsWith("Verse 16. For God so loved"))
        assertEquals(verseWords, on.replace(Regex("""Verse \d+\. """), ""))
    }

    @Test fun `scripture that begins with a number keeps it`() {
        val matthew = ResolvedScripture("Matthew chapter 11, verse 28", text = "28 Come to Me, all you who are weary", translationName = "Test", openForCloud = true)
        val b = Blocks().apply { scripture("Matthew 11:28") }
        val unit = extract(b, lookupOf("Matthew 11:28" to matthew)).sections.single().units.single()
        assertEquals("28 Come to Me, all you who are weary", unit.text)
    }

    @Test fun `poetry line breaks become spaces and no word changes`() {
        val psalm = ResolvedScripture("Psalm chapter 23, verse 1", verses = listOf(ResolvedScripture.Verse(1, "The Lord is my shepherd,\nI shall not want.")), openForCloud = true)
        val b = Blocks().apply { scripture("Psalm 23:1") }
        assertEquals("The Lord is my shepherd, I shall not want.", extract(b, lookupOf("Psalm 23:1" to psalm)).sections.single().units.single().text)
    }

    @Test fun `typed verse text is read as the person's and never as the translation`() {
        val typed = ResolvedScripture("John chapter 3, verse 16", translationName = "BSB", text = "my own wording", userTyped = true)
        val b = Blocks().apply { scripture("John 3:16") }
        val unit = extract(b, lookupOf("John 3:16" to typed)).sections.single().units.single()
        assertTrue(unit.lead!!.endsWith("Your text."))
        assertFalse(unit.lead!!.contains("BSB"))
        assertFalse(unit.licenseRestricted)
    }

    @Test fun `translation is announced once while it stays the same`() {
        val b = Blocks().apply {
            scripture("John 3:16-17"); add(PARAGRAPH, "between", source = BlockSource.AI); scripture("John 3:16-17 again", extra = emptyMap())
        }
        val lookup = lookupOf("John 3:16-17" to john316, "John 3:16-17 again" to john316)
        val units = extract(b, lookup).sections.flatMap { it.units }.filter { it.role == SpeechRole.SCRIPTURE }
        assertTrue(units[0].lead!!.contains("Berean Standard Bible"))
        assertFalse(units[1].lead!!.contains("Berean Standard Bible"))
        val always = extract(b, lookup, ExtractOptions(announceTranslation = TranslationAnnouncement.ALWAYS)).sections.flatMap { it.units }.filter { it.role == SpeechRole.SCRIPTURE }
        assertTrue(always.all { it.lead!!.contains("Berean Standard Bible") })
        val never = extract(b, lookup, ExtractOptions(announceTranslation = TranslationAnnouncement.NEVER)).sections.flatMap { it.units }.filter { it.role == SpeechRole.SCRIPTURE }
        assertTrue(never.none { it.lead!!.contains("Berean") })
    }

    @Test fun `an unavailable passage says so and is never substituted`() {
        val b = Blocks().apply { scripture("John 3:16") }
        val unit = extract(b, ScriptureLookup { null }).sections.single().units.single()
        assertTrue(unit.unavailable)
        assertFalse(unit.locked)
        assertEquals("John 3:16. This passage isn't available right now.", unit.text)
    }

    @Test fun `licensed translations are flagged and open ones are not`() {
        val licensed = john316.copy(openForCloud = false)
        val b = Blocks().apply { scripture("John 3:16-17") }
        assertTrue(extract(b, lookupOf("John 3:16-17" to licensed)).containsLicensedScripture)
        assertFalse(extract(b, lookupOf("John 3:16-17" to john316)).containsLicensedScripture)
    }

    // --- Read Exactly: wording and order ---

    @Test fun `the note's words come through in order and unchanged`() {
        val b = Blocks().apply {
            add(HEADING_2, "Notes"); add(PARAGRAPH, "First thought."); add(BULLET, "Second, with a comma"); add(PARAGRAPH, "  Third.  ")
        }
        val units = extract(b).sections.flatMap { it.units }
        assertEquals(listOf("Notes", "First thought.", "Second, with a comma", "Third."), units.map { it.text })
    }

    @Test fun `extraction is deterministic and ignores list order`() {
        val b = Blocks().apply { add(HEADING_2, "A"); add(PARAGRAPH, "one"); add(PARAGRAPH, "two") }
        val shuffled = Blocks().also { it.all += b.all.reversed() }
        assertEquals(extract(b), extract(b))
        assertEquals(extract(b), extract(shuffled))
    }

    @Test fun `numbered lists count and restart, checklists say done`() {
        val b = Blocks().apply {
            add(NUMBERED, "a"); add(NUMBERED, "b"); add(NUMBERED, "c"); add(PARAGRAPH, "break"); add(NUMBERED, "d")
            add(CHECKLIST, "task", checked = true); add(CHECKLIST, "open")
        }
        assertEquals(listOf("1. a", "2. b", "3. c", "break", "1. d", "Done: task", "open"), extract(b).sections.flatMap { it.units }.map { it.text })
    }

    @Test fun `empty and unsupported blocks are skipped and reported, dividers are silent`() {
        val b = Blocks().apply {
            add(PARAGRAPH, "kept"); add(PARAGRAPH, "   ", payload = mapOf("hint" to "What stood out")); add(IMAGE, "x"); add(CODE, "val x"); add(DIVIDER)
        }
        val src = extract(b)
        assertEquals(listOf("kept"), src.sections.flatMap { it.units }.map { it.text })
        assertEquals(listOf(SkipReason.EMPTY, SkipReason.UNSUPPORTED_TYPE, SkipReason.UNSUPPORTED_TYPE), src.skipped.map { it.reason })
    }

    @Test fun `markdown loses its markers and keeps its words`() {
        val md = "# Title\n**bold** and [link](http://x.example)\n- item\n```\ncode\n```"
        assertEquals("Title. bold and link. item.", NoteSpeechExtractor.plainFromMarkdown(md))
    }

    @Test fun `an empty note has nothing to read`() {
        assertTrue(extract(Blocks().apply { add(PARAGRAPH, " "); add(HEADING_2, "") }).isEmpty)
    }

    // --- sections and source mapping ---

    @Test fun `sections follow headings and a passage under its heading shares the section`() {
        val b = Blocks().apply {
            add(PARAGRAPH, "intro", id = "p0")
            add(HEADING_2, "Passage", "passage", id = "h1"); scripture("John 3:16-17", "passage", id = "s1")
            add(HEADING_2, "Observations", "observations", id = "h2"); add(BULLET, "God loves", "observations", id = "o1")
        }
        val src = extract(b, lookupOf("John 3:16-17" to john316))
        assertEquals(listOf("My study", "Passage", "Observations"), src.sections.map { it.title })
        assertEquals(listOf("p0", "h1", "h2"), src.sections.map { it.id })
        assertEquals(listOf("John 3:16-17"), src.sections[1].references)
        assertEquals(listOf("h1", "s1"), src.sections[1].blockIds)
    }

    @Test fun `a second passage after content starts its own section titled by its reference`() {
        val b = Blocks().apply {
            add(HEADING_2, "Study"); add(PARAGRAPH, "words"); scripture("John 3:16-17", id = "s2")
        }
        val src = extract(b, lookupOf("John 3:16-17" to john316))
        assertEquals(listOf("Study", "John 3:16-17"), src.sections.map { it.title })
        assertEquals(listOf("s2"), src.sections[1].blockIds)
    }

    @Test fun `every unit points at real note blocks`() {
        val b = Blocks().apply { add(HEADING_2, "A"); add(PARAGRAPH, "x"); scripture("John 3:16-17"); add(BULLET, "y") }
        val ids = b.all.map { it.id }.toSet()
        extract(b, lookupOf("John 3:16-17" to john316)).sections.flatMap { it.units }.forEach {
            assertTrue(it.blockIds.isNotEmpty() && it.blockIds.all { id -> id in ids })
        }
    }

    // --- roles and labels in the spoken order ---

    @Test fun `a guided journal reads question then answer, each labelled once`() {
        val b = Blocks().apply {
            scripture("John 3:16-17", "passage")
            add(HEADING_3, "What stands out?", "q1"); add(PARAGRAPH, "God's love is for everyone", "q1", edited = true)
            add(HEADING_3, "What does it ask of you?", "q3"); add(PARAGRAPH, "Trust him", "q3", edited = true)
        }
        val units = extract(b, lookupOf("John 3:16-17" to john316)).sections.flatMap { it.units }
        assertEquals(
            listOf(SpeechRole.SCRIPTURE, SpeechRole.QUESTION, SpeechRole.PERSONAL, SpeechRole.QUESTION, SpeechRole.PERSONAL),
            units.map { it.role }
        )
        assertEquals("Question for reflection.", units[1].lead)
        assertEquals("Your note.", units[2].lead)
        assertTrue(units[2].locked)
    }

    @Test fun `consecutive units of one role are labelled once`() {
        val b = Blocks().apply { add(BULLET, "q one", "questions"); add(BULLET, "q two", "questions") }
        val units = extract(b).sections.single().units
        assertEquals("Question for reflection.", units[0].lead)
        assertNull(units[1].lead)
    }

    @Test fun `AI-written text is labelled and the person's edits are not`() {
        val b = Blocks().apply {
            add(PARAGRAPH, "model prose", "key_message", BlockSource.AI)
            add(PARAGRAPH, "my notes", "my_notes", edited = true)
        }
        val units = extract(b).sections.single().units
        assertEquals("AI-written.", units[0].lead)
        assertTrue(units[0].aiGenerated)
        assertEquals("Your note.", units[1].lead)
    }

    @Test fun `label style short and off`() {
        val b = Blocks().apply { add(BULLET, "q", "questions"); add(PARAGRAPH, "mine", "my_notes", edited = true) }
        val short = extract(b, options = ExtractOptions(labelStyle = LabelStyle.SHORT)).sections.single().units
        assertEquals("Question.", short[0].lead)
        assertNull(short[1].lead)
        assertTrue(extract(b, options = ExtractOptions(labelStyle = LabelStyle.OFF)).sections.single().units.all { it.lead == null })
    }

    @Test fun `transcript excerpts name their speaker`() {
        val b = Blocks().apply { add(TRANSCRIPT_EXCERPT, "Grace is enough", "quotes", BlockSource.TRANSCRIPT, payload = mapOf(NoteBlock.PAYLOAD_SPEAKER to "Pastor John")) }
        val unit = extract(b).sections.single().units.single()
        assertEquals("Pastor John:", unit.lead)
        assertEquals("Pastor John: Grace is enough", unit.spoken)
        assertTrue(unit.locked)
    }

    // --- privacy flags ---

    @Test fun `a private note marks every unit private`() {
        val b = Blocks().apply { add(PARAGRAPH, "a"); add(PARAGRAPH, "b") }
        assertTrue(extract(b, notePrivate = true).sections.flatMap { it.units }.all { it.isPrivate })
    }

    @Test fun `private template sections are marked and can be dropped`() {
        val b = Blocks().apply {
            add(HEADING_2, "Notes", "my_notes"); add(PARAGRAPH, "shareable", "my_notes", edited = true)
            add(HEADING_2, "My prayer", "my_prayer"); add(PARAGRAPH, "private words", "my_prayer", edited = true)
        }
        val src = extract(b, privateKeys = setOf("my_prayer"))
        assertTrue(src.containsPrivate)
        val rest = src.withoutPrivate()
        assertFalse(rest.containsPrivate)
        assertEquals(listOf("Notes"), rest.sections.map { it.title })
        assertFalse(rest.sections.flatMap { it.units }.any { it.text.contains("private words") })
    }
}
