package com.craftflowtechnologies.meetingmind.ai.voice.read.smart

/**
 * Deterministic checks on a Smart Read rewrite (docs/mvp/READ_TO_ME_PLAN.md §6.C, §16.6). It can only
 * accept or reject; it never edits. A rewrite that fails any check is thrown away and the original
 * wording is read as written.
 */
object ScriptValidator {

    data class Limits(val minRatio: Double, val maxRatio: Double) {
        companion object {
            /** Commentary, observation, interpretation, application: stay close to the original length. */
            val STUDY = Limits(0.85, 1.25)
            val GENERAL = Limits(0.7, 1.4)
        }
    }

    private val word = Regex("[\\p{L}\\p{N}'’]+")
    private val digits = Regex("\\d+")
    private val negations = setOf("not", "never", "no", "neither", "nor", "cannot", "without", "none", "nothing", "nobody")
    private val hedges = setOf("may", "might", "perhaps", "could", "possibly", "maybe", "probably", "likely", "seems", "seem", "appears", "appear", "somewhat")
    private val quotes = setOf('"', '“', '”', '„', '«', '»')
    private val forbidden = listOf(
        "thus says the lord", "thus saith the lord", "god told me", "the lord told me", "god is telling you", "i prophesy", "i declare over you",
        "the lord says", "god says to you", "god spoke to me"
    )
    /** Words a rewrite may start a sentence with that were not in the original. */
    private val allowedStarters = setOf(
        "next", "then", "now", "so", "and", "but", "first", "second", "third", "fourth", "fifth", "finally", "also", "in", "this", "that", "these", "those",
        "it", "we", "you", "here", "there", "the", "a", "an", "because", "when", "if", "as", "for", "one", "another", "remember", "lastly", "overall",
        "together", "today", "let", "think", "notice", "consider", "all", "each", "every", "many", "some", "most", "what", "why", "how", "who", "with", "by", "of", "to", "on", "at", "from"
    )

    /** Null when [output] is a safe rewrite of [source]; otherwise a short reason. */
    fun check(source: String, output: String, limits: Limits): String? {
        val out = output.trim()
        if (out.isEmpty()) return "empty"
        if (out.any { it == '*' || it == '#' || it == '|' || it == '`' || it == '⟦' || it == '⟧' } || out.contains("\n-") || out.contains("\n1.")) return "markup"
        val ratio = out.length.toDouble() / source.length.coerceAtLeast(1)
        if (ratio < limits.minRatio || ratio > limits.maxRatio) return "length"

        val srcNumbers = digits.findAll(source).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()
        val outNumbers = digits.findAll(out).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()
        if (srcNumbers != outNumbers) return "numbers"

        if (out.count { it in quotes } > source.count { it in quotes }) return "quotation marks"

        val srcTokens = tokens(source)
        val outTokens = tokens(out)
        if (count(srcTokens, negations) != count(outTokens, negations)) return "negation"
        if (count(srcTokens, hedges) != count(outTokens, hedges)) return "hedging"

        val lowerOut = out.lowercase()
        val lowerSrc = source.lowercase()
        if (forbidden.any { it in lowerOut && it !in lowerSrc }) return "prophetic phrase"

        val srcWords = srcTokens.map { it.lowercase() }.toSet()
        val srcNames = capitalised(source, includeInitial = false)
        val outAll = capitalised(out, includeInitial = true)
        val outNames = capitalised(out, includeInitial = false)
        // Nothing capitalised may appear that the original did not have (a new name, place or book).
        if (outNames.any { it !in srcWords }) return "new name"
        if (outAll.any { it !in srcWords && it !in allowedStarters }) return "new name"
        // And no name from the original may go missing (attribution is kept).
        val outWords = outTokens.map { it.lowercase() }.toSet()
        if (srcNames.any { it !in outWords }) return "dropped name"
        return null
    }

    private fun tokens(text: String): List<String> = word.findAll(text.replace('’', '\'')).map { it.value }.toList()

    private fun count(tokens: List<String>, set: Set<String>): Int =
        tokens.count { val t = it.lowercase(); t in set || t.endsWith("n't") }

    /** Lower-cased capitalised words; sentence-initial ones only when [includeInitial]. */
    private fun capitalised(text: String, includeInitial: Boolean): Set<String> {
        val found = mutableSetOf<String>()
        for (m in word.findAll(text.replace('’', '\''))) {
            val w = m.value
            if (w.length < 2 || !w.first().isUpperCase() || w.any { it.isDigit() }) continue
            val before = text.substring(0, m.range.first).trimEnd()
            val initial = before.isEmpty() || before.last() in ".!?:\n"
            if (initial && !includeInitial) continue
            found += w.lowercase()
        }
        return found
    }
}
