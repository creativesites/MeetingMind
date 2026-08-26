package com.example.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [WordDiff] backs every AI-tools review screen's "what actually changed" rendering — it was
 * already a real java-diff-utils word-level diff (not markdown strikethrough) but had no tests of
 * its own; these pin down the delta-cursor arithmetic across insert/delete/replace/no-op cases so
 * a future refactor can't silently reintroduce an off-by-one that drops or duplicates a word.
 */
class WordDiffTest {

    @Test
    fun `identical text produces all-unchanged runs`() {
        val runs = WordDiff.diffRuns("the deadline is Friday", "the deadline is Friday")

        assertEquals(listOf("the", "deadline", "is", "Friday"), runs.map { it.text })
        assertEquals(true, runs.all { !it.changed })
    }

    @Test
    fun `a single word replacement is tagged changed, surrounding words are not`() {
        val runs = WordDiff.diffRuns("the deadline is Thursday", "the deadline is Friday")

        assertEquals(listOf("the", "deadline", "is", "Friday"), runs.map { it.text })
        assertEquals(listOf(false, false, false, true), runs.map { it.changed })
    }

    @Test
    fun `an inserted word is tagged changed and appears once`() {
        val runs = WordDiff.diffRuns("ship it Friday", "ship it by Friday")

        assertEquals(listOf("ship", "it", "by", "Friday"), runs.map { it.text })
        assertEquals(listOf(false, false, true, false), runs.map { it.changed })
    }

    @Test
    fun `a pure deletion contributes nothing to the revised-side runs`() {
        // "please" is removed entirely — nothing in the revised text should represent it.
        val runs = WordDiff.diffRuns("please ship it Friday", "ship it Friday")

        assertEquals(listOf("ship", "it", "Friday"), runs.map { it.text })
        assertEquals(true, runs.all { !it.changed })
    }

    @Test
    fun `multiple separate edits are each tagged independently`() {
        val runs = WordDiff.diffRuns("the old deadline is Thursday please", "the new deadline is Friday")

        assertEquals(listOf("the", "new", "deadline", "is", "Friday"), runs.map { it.text })
        assertEquals(listOf(false, true, false, false, true), runs.map { it.changed })
    }

    @Test
    fun `changedWordCount counts only the changed runs`() {
        assertEquals(0, WordDiff.changedWordCount("same text here", "same text here"))
        assertEquals(1, WordDiff.changedWordCount("the deadline is Thursday", "the deadline is Friday"))
        assertEquals(2, WordDiff.changedWordCount("the old deadline is Thursday please", "the new deadline is Friday"))
    }

    @Test
    fun `blank input produces no runs rather than a run of empty strings`() {
        assertEquals(emptyList<WordDiff.Run>(), WordDiff.diffRuns("", ""))
        assertEquals(listOf("hello"), WordDiff.diffRuns("", "hello").map { it.text })
    }
}
