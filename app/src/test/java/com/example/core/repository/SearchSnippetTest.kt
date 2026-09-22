package com.example.core.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSnippetTest {

    @Test
    fun `short text is returned whole`() {
        assertEquals("Faith and patience", SearchRepository.snippetAround("Faith and patience", "patience"))
    }

    @Test
    fun `no match gives null`() {
        assertNull(SearchRepository.snippetAround("Faith and patience", "hope"))
    }

    @Test
    fun `long text is cut around the match at word boundaries`() {
        val text = "word ".repeat(60) + "GRACE abounds " + "tail ".repeat(60)
        val snippet = SearchRepository.snippetAround(text, "grace")!!
        assertTrue(snippet.startsWith("…word"))
        assertTrue(snippet.endsWith("tail…"))
        assertTrue(snippet.contains("GRACE abounds"))
        assertTrue(snippet.length <= 164)
    }

    @Test
    fun `line breaks become spaces`() {
        assertEquals("one two", SearchRepository.snippetAround("one\ntwo", "two"))
    }
}
