package com.example.core.scripture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptureReferenceParserTest {

    private fun one(text: String): String? = ScriptureReferenceParser.findAll(text).singleOrNull()?.reference?.display()
    private fun all(text: String): List<String> = ScriptureReferenceParser.findAll(text).map { it.reference.display() }

    // ---- written

    @Test fun `plain chapter and verse`() = assertEquals("John 3:16", one("For God so loved — John 3:16."))
    @Test fun `verse range with hyphen and en dash`() {
        assertEquals("John 3:16–18", one("John 3:16-18"))
        assertEquals("1 Corinthians 13:4–7", one("1 Cor 13:4–7"))
    }
    @Test fun `abbreviations with and without full stops`() {
        assertEquals("Romans 8:28", one("Rom 8:28"))
        assertEquals("Romans 8:28", one("Rom. 8:28"))
        assertEquals("Genesis 1:1", one("Gen 1:1"))
        assertEquals("Matthew 5:3", one("Mt 5:3"))
        assertEquals("Psalm 23", one("Ps 23"))
        assertEquals("Philippians 4:13", one("Phil 4:13"))
        assertEquals("Ezekiel 37:1", one("Ezek 37:1"))
    }
    @Test fun `whole chapters of unambiguous books`() {
        assertEquals("Hebrews 11", one("Read Hebrews 11 this week"))
        assertEquals("Romans 8", one("Romans 8 is the high point"))
        assertEquals("Psalm 119", one("Psalm 119 is long"))
    }
    @Test fun `numbered books in every form`() {
        assertEquals("1 Corinthians 13:4", one("1 Corinthians 13:4"))
        assertEquals("2 Timothy 3:16", one("2 Tim 3:16"))
        assertEquals("1 John 4:8", one("1 John 4:8"))
        assertEquals("2 Kings 5:14", one("II Kings 5:14"))
        assertEquals("1 Peter 5:7", one("1st Peter 5:7"))
        assertEquals("1 Samuel 17:45", one("I Samuel 17:45"))
    }
    @Test fun `multi word names`() {
        assertEquals("Song of Songs 2:4", one("Song of Songs 2:4"))
        assertEquals("Song of Songs 2:4", one("Song of Solomon 2:4"))
    }
    @Test fun `several references in one sentence`() {
        assertEquals(listOf("Ephesians 2:8", "Ephesians 2:9", "Romans 3:23"), all("Eph 2:8, Eph 2:9 and Rom 3:23"))
    }
    @Test fun `single chapter books take the number as a verse`() {
        assertEquals("Jude 1:3", one("read Jude 3"))
        assertNull(one("Jude 3")) // Jude is also a name: a bare number isn't enough
        assertEquals("Philemon 1:6", one("Philemon 6"))
        assertEquals("3 John 1:2", one("3 John 2"))
    }
    @Test fun `chapter spanning ranges keep their start`() = assertEquals("John 3:16", one("John 3:16-4:2"))

    // ---- spoken, as speech recognition writes it

    @Test fun `john three sixteen`() = assertEquals("John 3:16", one("and Jesus said in john three sixteen"))
    @Test fun `with chapter and verse words`() = assertEquals("John 3:16", one("john chapter three verse sixteen"))
    @Test fun `first corinthians chapter thirteen verses four through seven`() =
        assertEquals("1 Corinthians 13:4–7", one("first corinthians chapter thirteen verses four through seven"))
    @Test fun `compound numbers`() {
        assertEquals("Romans 8:28", one("romans eight twenty eight"))
        assertEquals("Psalm 23", one("psalm twenty three"))
        assertEquals("Psalm 119:105", one("psalm one hundred and nineteen verse one hundred five"))
        assertEquals("Jeremiah 29:11", one("Jeremiah twenty-nine eleven"))
    }
    @Test fun `and joins the next verse`() = assertEquals("2 Timothy 3:16–17", one("second timothy three sixteen and seventeen"))
    @Test fun `to and through make ranges`() {
        assertEquals("Matthew 5:3–12", one("Matthew 5 verse 3 to 12"))
        assertEquals("Isaiah 53", one("the book of Isaiah chapter fifty three"))
    }
    @Test fun `digits as speech recognition may write them`() = assertEquals("John 3:16", one("John 3 16"))

    // ---- names and words that aren't references

    @Test fun `people named John or Mark are not books`() {
        assertTrue(all("John said three things to Mark").isEmpty())
        assertTrue(all("Mark 10 people came").isEmpty())
        assertTrue(all("my friend Luke 2 days ago").isEmpty())
        assertTrue(all("the numbers 5 and 6").isEmpty())
        assertTrue(all("Jude 3 people").isEmpty())
    }
    @Test fun `ambiguous books are accepted with a verse, chapter word, or cue`() {
        assertEquals("Mark 10:45", one("Mark 10:45"))
        assertEquals("Luke 15", one("turn to Luke 15"))
        assertEquals("Acts 2", one("in Acts chapter 2"))
        assertEquals("Job 1", one("the book of Job 1"))
    }
    @Test fun `the pronoun I is not first`() = assertTrue(all("i john have seen it").isEmpty())

    // ---- versification

    @Test fun `references that don't exist are rejected, not corrected`() {
        assertNull(one("John 3:99"))
        assertNull(one("Romans 17:1"))
        assertNull(one("Psalm 151"))
        assertNull(one("Jude 26"))
    }
    @Test fun `a range past the chapter's end is trimmed`() = assertEquals("Psalm 23:4–6", one("Psalm 23:4-9"))
    @Test fun `translation differences are allowed`() {
        assertEquals("3 John 1:15", one("3 John 1:15"))
        assertEquals("Revelation 12:18", one("Revelation 12:18"))
    }

    // ---- typed input and ids

    @Test fun `typed input is taken at its word`() {
        assertEquals("John 3", ScriptureReferenceParser.parse("jn 3")?.display())
        assertEquals("Acts 2:38", ScriptureReferenceParser.parse("acts 2 38")?.display())
        assertNull(ScriptureReferenceParser.parse("hello there"))
    }
    @Test fun `passage ids use usfm`() {
        assertEquals("JHN.3.16", ScriptureReferenceParser.parse("John 3:16")!!.passageId())
        assertEquals("1CO.13.4-1CO.13.7", ScriptureReferenceParser.parse("1 Cor 13:4-7")!!.passageId())
        assertEquals("PSA.23", ScriptureReferenceParser.parse("Psalm 23")!!.passageId())
    }
    @Test fun `match offsets cover the reference text`() {
        val text = "Today: Rom 8:28 says"
        val m = ScriptureReferenceParser.findAll(text).single()
        assertEquals("Rom 8:28", m.text)
        assertEquals(text.indexOf("Rom"), m.start)
    }

    // ---- typed lists

    private fun list(text: String) = ScriptureReferenceParser.parseList(text).map { it.display() }

    @Test fun `a list separated by semicolons and new lines`() =
        assertEquals(listOf("John 3:16–21", "Psalm 23", "Romans 8:28"), list("John 3:16-21; Ps 23\nRom 8:28"))

    @Test fun `parts without a book continue the one before`() {
        assertEquals(listOf("Romans 8:28", "Romans 8:31–39"), list("Rom 8:28, 31-39"))
        assertEquals(listOf("John 3:16", "John 4:1–6"), list("John 3:16; 4:1-6"))
    }

    @Test fun `whole chapters and junk parts`() =
        assertEquals(listOf("Isaiah 53", "Isaiah 54"), list("Isaiah 53; hello; 54"))

    @Test fun `nothing typed is nothing`() = assertTrue(list("  ;; ").isEmpty())
}
