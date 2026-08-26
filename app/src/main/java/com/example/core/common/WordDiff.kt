package com.example.core.common

import com.github.difflib.DiffUtils

/**
 * Real word-level diffing (docs/recording-page-implementation.md §3.4 item 25: "Use
 * java-diff-utils; do not write a diff") — backs the AI-tools result review screen's green
 * underlines and "Show original" strikethrough. Never invents which words changed; every run here
 * traces back to a real java-diff-utils delta.
 */
object WordDiff {

    /** One word from a diff, tagged with whether it's part of a changed span. */
    data class Run(val text: String, val changed: Boolean)

    private val WHITESPACE = Regex("\\s+")

    /** Word-level diff between [original] and [revised], returned as ordered runs over
     * [revised]'s words only — i.e. what a reader should see as "the proposed text," with
     * inserted/changed words tagged [Run.changed]. Pure deletions (a delta with no target words)
     * contribute nothing here since there is no revised text to show for them; they still show up
     * via [original]'s own words when the caller renders the "Show original" side. */
    fun diffRuns(original: String, revised: String): List<Run> {
        val originalWords = original.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val revisedWords = revised.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val patch = DiffUtils.diff(originalWords, revisedWords)

        val runs = mutableListOf<Run>()
        var revisedCursor = 0
        for (delta in patch.deltas) {
            val targetPos = delta.target.position
            for (i in revisedCursor until targetPos) {
                runs += Run(revisedWords[i], changed = false)
            }
            for (word in delta.target.lines) {
                runs += Run(word, changed = true)
            }
            revisedCursor = targetPos + delta.target.lines.size
        }
        for (i in revisedCursor until revisedWords.size) {
            runs += Run(revisedWords[i], changed = false)
        }
        return runs
    }

    /** True counts of changed vs. unchanged words — feeds the plain-English change summary
     * (docs/recording-page-implementation.md §3.4 item 26: "never let the model self-report what
     * it changed"). */
    fun changedWordCount(original: String, revised: String): Int =
        diffRuns(original, revised).count { it.changed }
}
