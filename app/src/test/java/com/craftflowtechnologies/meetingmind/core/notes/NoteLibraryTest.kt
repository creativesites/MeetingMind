package com.craftflowtechnologies.meetingmind.core.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/** The pure parts of the Notes library (U-2): search text, previews, highlights and short labels. */
class NoteLibraryTest {

    @Test fun `search words become prefix terms and quoted text a phrase`() {
        assertEquals("faith* hope*", NoteSearch.match("Faith  hope"))
        assertEquals("\"john 3\" grace*", NoteSearch.match("\"John 3\" grace"))
        assertNull(NoteSearch.match("   "))
        assertNull(NoteSearch.match("*** !!"))
    }

    @Test fun `FTS operators in what the person types are neutralised`() {
        // Only letters and digits survive, so NEAR, OR, quotes, parentheses and dashes cannot break the MATCH.
        assertEquals("a* or* b*", NoteSearch.match("a OR (b)"))
        assertEquals("c* d*", NoteSearch.match("c-d"))
    }

    @Test fun `terms are capped`() {
        val many = (1..30).joinToString(" ") { "w$it" }
        assertEquals(NoteSearch.MAX_TERMS, NoteSearch.match(many)!!.split(" ").size)
    }

    @Test fun `highlights start at word beginnings and merge`() {
        assertEquals(listOf(0..4, 11..15), NoteSearch.highlights("Faith and  faith", listOf("faith")))
        assertEquals(emptyList<IntRange>(), NoteSearch.highlights("unfaithful", listOf("faith")))
        assertEquals(listOf(0..7), NoteSearch.highlights("Faithful", listOf("faith", "faithful")))
    }

    @Test fun `preview drops Markdown symbols`() {
        val raw = "# Title\n- [x] **bold** and _soft_ item\n> quoted `code`\n1. first [link](http://x.y) ![alt](a.png)\n---\nsnake_case stays"
        assertEquals("Title bold and soft item quoted code first link alt snake_case stays", NotePreview.plain(raw))
    }

    @Test fun `lead cuts at a word`() {
        val text = "word ".repeat(100)
        val lead = NotePreview.lead(text, 42)
        assertTrue(lead.endsWith("…"))
        assertTrue(lead.length <= 43)
        assertEquals("short", NotePreview.lead("short"))
    }

    @Test fun `a body match shows the passage around it`() {
        val text = "Intro. ".repeat(40) + "The covenant was renewed at dawn. " + "Outro. ".repeat(40)
        val shown = NotePreview.around(text, listOf("covenant"), 120)
        assertTrue(shown, shown.startsWith("…"))
        assertTrue(shown, "covenant" in shown)
        // A hit near the start keeps the plain lead.
        assertEquals(NotePreview.lead("covenant first and then more"), NotePreview.around("covenant first and then more", listOf("covenant")))
    }

    @Test fun `relative dates`() {
        val zone = ZoneId.of("UTC")
        val now = java.time.ZonedDateTime.of(2026, 10, 11, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun at(minAgo: Long) = now - minAgo * 60_000
        fun label(ms: Long) = NoteLabels.relativeDate(ms, now, zone, Locale.ENGLISH)
        assertEquals("Just now", label(at(0)))
        assertEquals("12 min ago", label(at(12)))
        assertEquals("3 h ago", label(at(180)))
        assertEquals("Yesterday", label(at(16 * 60)))
        assertEquals("Thu", label(at(3 * 24 * 60)))
        assertEquals("1 Oct", label(at(10 * 24 * 60)))
        assertEquals("3 Mar 2025", label(java.time.ZonedDateTime.of(2025, 3, 3, 9, 0, 0, 0, zone).toInstant().toEpochMilli()))
        assertEquals("", label(0))
    }

    @Test fun `durations`() {
        assertEquals("45 s", NoteLabels.duration(45_000))
        assertEquals("42 min", NoteLabels.duration(42 * 60_000L))
        assertEquals("1 h 5 min", NoteLabels.duration((65 * 60_000L)))
        assertEquals("2 h", NoteLabels.duration(2 * 3_600_000L))
    }

    @Test fun `pinned notes float only in a plain date list`() {
        assertTrue(LibraryQuery().pinFirst)
        assertTrue(!LibraryQuery(search = "faith").pinFirst)
        assertTrue(!LibraryQuery(sort = NoteSort.TITLE).pinFirst)
        assertTrue(!LibraryQuery(mode = LibraryMode.TRASH).pinFirst)
    }
}
