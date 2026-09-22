package com.example.ai.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overlapping decode windows are what give a word at a window boundary real acoustic context. The
 * price is duplicated text, and the defect users actually notice is a repeated phrase — so the one
 * property these tests hold above all others is **never duplicate**, even at the cost of a word.
 */
class AsrWindowReconcilerTest {

    private fun words(sentence: String, startMs: Long, stepMs: Long = 300L): List<CanonicalWord> =
        sentence.split(" ").mapIndexed { i, text ->
            CanonicalWord(
                id = "x$i",
                text = text,
                startMs = startMs + i * stepMs,
                endMs = startMs + (i + 1) * stepMs
            )
        }

    private fun textOf(result: List<CanonicalWord>) = result.joinToString(" ") { it.text }

    @Test
    fun `the overlap between two windows is reconciled rather than duplicated`() {
        val a = words("we still need to finish the API", startMs = 0L)
        val b = words("need to finish the API integration before Friday", startMs = 600L)

        val result = AsrWindowReconciler.reconcile(listOf(a, b))

        assertEquals("we still need to finish the API integration before Friday", textOf(result))
    }

    @Test
    fun `windows that do not overlap in time are simply concatenated`() {
        val a = words("first thought here", startMs = 0L)
        val b = words("second thought here", startMs = 30_000L)

        val result = AsrWindowReconciler.reconcile(listOf(a, b))

        assertEquals("first thought here second thought here", textOf(result))
    }

    @Test
    fun `capitalisation and punctuation differences between decodes are not treated as disagreement`() {
        val a = words("move the launch to Friday", startMs = 0L)
        val b = words("The launch to Friday, because the API isn't ready", startMs = 300L)

        val result = AsrWindowReconciler.reconcile(listOf(a, b))

        assertEquals(1, result.count { AsrWindowReconciler.normalize(it.text) == "launch" })
        assertTrue(textOf(result).endsWith("because the API isn't ready"))
    }

    @Test
    fun `when two windows agree on nothing the seam loses a word rather than repeating one`() {
        val a = words("alpha bravo charlie", startMs = 0L)
        val b = words("xigma yotta zebra delta", startMs = 600L)

        val result = AsrWindowReconciler.reconcile(listOf(a, b))

        val texts = result.map { AsrWindowReconciler.normalize(it.text) }
        assertEquals("no word may appear twice", texts.size, texts.toSet().size)
    }

    @Test
    fun `a single common function word is not accepted as a join anchor`() {
        // Splicing on "the" would silently drop or repeat everything around it.
        val a = words("we reviewed the budget", startMs = 0L)
        val b = words("the quarterly numbers look fine", startMs = 300L)

        val result = AsrWindowReconciler.reconcile(listOf(a, b))

        assertTrue(
            "the earlier window's own content must survive",
            textOf(result).startsWith("we reviewed the budget")
        )
    }

    @Test
    fun `three windows reconcile pairwise into one stream`() {
        val a = words("one two three four", startMs = 0L)
        val b = words("three four five six", startMs = 600L)
        val c = words("five six seven eight", startMs = 1_200L)

        val result = AsrWindowReconciler.reconcile(listOf(a, b, c))

        assertEquals("one two three four five six seven eight", textOf(result))
    }

    @Test
    fun `the result is chronological and carries transcript-wide ids`() {
        val a = words("alpha bravo", startMs = 0L)
        val b = words("charlie delta", startMs = 10_000L)

        val result = AsrWindowReconciler.reconcile(listOf(a, b))

        assertEquals(listOf("w0", "w1", "w2", "w3"), result.map { it.id })
        assertEquals(result.sortedBy { it.startMs }, result)
    }

    @Test
    fun `empty windows are ignored and an all-empty input yields nothing`() {
        assertTrue(AsrWindowReconciler.reconcile(emptyList()).isEmpty())
        assertTrue(AsrWindowReconciler.reconcile(listOf(emptyList(), emptyList())).isEmpty())
        assertEquals(
            "alpha bravo",
            textOf(AsrWindowReconciler.reconcile(listOf(emptyList(), words("alpha bravo", 0L), emptyList())))
        )
    }
}
