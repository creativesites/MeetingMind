package com.craftflowtechnologies.meetingmind.core.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBoxesAndSearchTest {

    @Test
    fun boxesRoundTripThroughJson() {
        val boxes = listOf(
            PageBox(0.1, 0.2, 0.3, 0.05, "Staff authentication shall use SSO"),
            PageBox(0.5, 0.6, 0.2, 0.04)
        )
        val parsed = PageBoxes.parse(PageBoxes.encode(boxes))
        assertEquals(boxes, parsed)
    }

    @Test
    fun emptyBoxesEncodeToNull() {
        assertNull(PageBoxes.encode(emptyList()))
        assertTrue(PageBoxes.parse(null).isEmpty())
        assertTrue(PageBoxes.parse("   ").isEmpty())
    }

    @Test
    fun singleObjectAndJunkAreTolerated() {
        val single = PageBoxes.parse("""{"x":0.1,"y":0.1,"w":0.2,"h":0.1}""")
        assertEquals(1, single.size)
        assertTrue(PageBoxes.parse("not json at all").isEmpty())
        assertTrue(PageBoxes.parse("""[{"x":"nope"}]""").isEmpty())
    }

    @Test
    fun boxesAreClampedOntoThePage() {
        val box = PageBox.clamped(left = 0.9, top = 0.95, width = 0.5, height = 0.2)
        assertEquals(0.9, box!!.left, 1e-9)
        assertEquals(0.1, box.width, 1e-9)
        assertEquals(0.05, box.height, 1e-9)
        assertNull(PageBox.clamped(left = 1.2, top = 0.5, width = 0.1, height = 0.1))
    }

    @Test
    fun matchQueryUsesPrefixTermsAndDropsSyntax() {
        assertEquals("Azure* AD*", DocumentSearch.matchQuery("Azure AD"))
        assertEquals("DROP* TABLE*", DocumentSearch.matchQuery("\"DROP\" TABLE;"))
        // Operators are plain words here: FTS only treats uppercase AND/OR/NOT as operators, and the
        // prefix star keeps each word a term.
        assertEquals("near* AND* or*", DocumentSearch.matchQuery("near AND or"))
    }

    @Test
    fun blankOrPunctuationOnlyQueryIsNull() {
        assertNull(DocumentSearch.matchQuery(""))
        assertNull(DocumentSearch.matchQuery("  \"*()  "))
    }
}
