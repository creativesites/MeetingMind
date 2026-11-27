package com.craftflowtechnologies.meetingmind.core.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageChunkSelectorTest {

    private fun filler(words: Int, seed: String = "lorem"): String =
        (1..words).joinToString(" ") { "$seed$it" }

    @Test
    fun firstTwoPagesAreAlwaysIncluded() {
        val pages = (1..5).map { DocumentPageText(it, filler(30, "p${it}w")) }
        val selection = PageChunkSelector.select(pages)
        assertTrue(selection.pages.containsAll(listOf(1, 2)))
    }

    @Test
    fun pagesMentioningAnEntityAreIncluded() {
        val pages = listOf(
            DocumentPageText(1, "Cover page for the statement of work."),
            DocumentPageText(2, "Background and context."),
            DocumentPageText(3, "General boilerplate text."),
            DocumentPageText(9, "Acme Corporation will provide the integration services.")
        )
        val selection = PageChunkSelector.select(pages, entityNames = listOf("Acme Corporation"))
        assertTrue(selection.pages.contains(9))
        assertFalse(selection.pages.contains(3))
    }

    @Test
    fun cuePagesAreIncludedMostCuesFirst() {
        val pages = listOf(
            DocumentPageText(1, "Cover."),
            DocumentPageText(2, "Intro."),
            DocumentPageText(4, "The supplier shall integrate the platform."),
            DocumentPageText(6, "Nothing here at all about work."),
            DocumentPageText(7, "We agreed the deadline. It must be met, and the supplier shall comply.")
        )
        // Page 7 has four cues and page 4 has one, so 7 is taken first; page 6 has none and is left out.
        val selection = PageChunkSelector.select(pages, budgetChars = 600)
        assertTrue(selection.pages.contains(7))
        assertFalse(selection.pages.contains(6))
    }

    @Test
    fun outputIsInReadingOrderWithPageMarkers() {
        val pages = listOf(
            DocumentPageText(3, "The team decided the launch date."),
            DocumentPageText(1, "Opening."),
            DocumentPageText(2, "Second.")
        )
        val selection = PageChunkSelector.select(pages)
        assertEquals(listOf(1, 2, 3), selection.pages)
        val markerOrder = Regex("\\[p\\. (\\d+)\\]").findAll(selection.text).map { it.groupValues[1].toInt() }.toList()
        assertEquals(listOf(1, 2, 3), markerOrder)
    }

    @Test
    fun neverExceedsTheBudget() {
        val pages = (1..40).map { DocumentPageText(it, filler(400, "shall${it}x") + " must agreed") }
        val budget = 6000
        val selection = PageChunkSelector.select(pages, entityNames = listOf("lorem1"), budgetChars = budget)
        assertTrue("was ${selection.text.length}", selection.text.length <= budget)
        assertTrue(selection.pages.isNotEmpty())
    }

    @Test
    fun longPageIsClippedToPerPageLimit() {
        val pages = listOf(DocumentPageText(1, filler(2000, "word")))
        val selection = PageChunkSelector.select(pages)
        assertTrue(selection.text.length <= PageChunkSelector.PER_PAGE_CHARS + "[p. 1]\n".length)
    }

    @Test
    fun blankPagesAreSkipped() {
        val pages = listOf(
            DocumentPageText(1, "   "),
            DocumentPageText(2, "Real text that the proposer can read."),
            DocumentPageText(3, "")
        )
        val selection = PageChunkSelector.select(pages)
        assertEquals(listOf(2), selection.pages)
    }

    @Test
    fun noPagesGivesEmptySelection() {
        val selection = PageChunkSelector.select(emptyList(), entityNames = listOf("Anyone"))
        assertEquals("", selection.text)
        assertTrue(selection.pages.isEmpty())
    }

    @Test
    fun zeroBudgetGivesEmptySelection() {
        val selection = PageChunkSelector.select(listOf(DocumentPageText(1, "Text")), budgetChars = 0)
        assertTrue(selection.pages.isEmpty())
    }

    @Test
    fun shortNamesAreIgnoredAndNamesMatchWholeWordsOnly() {
        val pages = listOf(
            DocumentPageText(1, "Opening."),
            DocumentPageText(2, "Opening."),
            DocumentPageText(5, "We are Annually planning."),
            DocumentPageText(6, "Al will lead this."),
            DocumentPageText(7, "The Annual plan is approved.")
        )
        // "Al" is under three characters and ignored; "Annual" must not match inside "Annually".
        val selection = PageChunkSelector.select(pages, entityNames = listOf("Al", "Annual"))
        assertTrue(selection.pages.contains(7))
        assertFalse(selection.pages.contains(5))
        assertFalse(selection.pages.contains(6))
    }

    @Test
    fun nameMatchingIsCaseInsensitiveAndWholeWord() {
        val pages = listOf(
            DocumentPageText(1, "Opening."),
            DocumentPageText(2, "Opening."),
            DocumentPageText(8, "sarah chen sent the notes"),
            DocumentPageText(9, "Sarahs notes were late")
        )
        val selection = PageChunkSelector.select(pages, entityNames = listOf("Sarah Chen"))
        assertTrue(selection.pages.contains(8))
        assertFalse(selection.pages.contains(9))
    }
}
