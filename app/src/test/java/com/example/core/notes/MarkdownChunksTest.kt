package com.example.core.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownChunksTest {
    @Test fun splitsParagraphsAndHeadings() {
        assertEquals(listOf("# Title", "First paragraph\nstill first.", "Second."), MarkdownChunks.split("# Title\n\nFirst paragraph\nstill first.\n\n\n\nSecond.\n"))
    }

    @Test fun keepsALooseListTogether() {
        val md = "Intro\n\n1. one\n\n2. two\n\n3. three\n\nAfter"
        assertEquals(listOf("Intro", "1. one\n\n2. two\n\n3. three", "After"), MarkdownChunks.split(md).map { it })
    }

    @Test fun keepsCodeFencesWholeEvenWithBlankLines() {
        val md = "Before\n\n```kotlin\nval a = 1\n\nval b = 2\n```\n\nAfter"
        assertEquals(listOf("Before", "```kotlin\nval a = 1\n\nval b = 2\n```", "After"), MarkdownChunks.split(md))
    }

    @Test fun keepsTablesTogether() {
        val md = "| a | b |\n|---|---|\n| 1 | 2 |\n\nText"
        assertEquals(2, MarkdownChunks.split(md).size)
    }

    @Test fun replaceAndJoinRoundTrip() {
        val md = "# T\n\nOne.\n\nTwo."
        val chunks = MarkdownChunks.split(md)
        assertEquals(md, MarkdownChunks.join(chunks))
        assertEquals("# T\n\nOne edited.\n\nTwo.", MarkdownChunks.join(MarkdownChunks.replace(chunks, 1, "One edited.")))
        assertEquals("# T\n\nTwo.", MarkdownChunks.join(MarkdownChunks.replace(chunks, 1, "")))
    }
}
