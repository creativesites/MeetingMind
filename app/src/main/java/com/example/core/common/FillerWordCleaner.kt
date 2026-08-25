package com.example.core.common

/**
 * Removes speech disfluencies from transcript text for readability.
 *
 * This is deliberately a *transform*, never a rewrite of stored data: the verbatim ASR text stays
 * in Room untouched, and this runs (a) at display time when the user has the "Clean up filler
 * words" setting on, and (b) always when building a prompt for the local LLM, where hesitation
 * noise costs tokens and actively degrades a small model's extraction quality. Turning the setting
 * off restores the exact original text with no reprocessing, because nothing was ever destroyed.
 *
 * The word list is intentionally narrow. Only non-lexical hesitation sounds — noises that carry no
 * meaning in any context — are removed unconditionally. Words that merely *often* act as filler
 * are not: "like" is usually lexical ("it works like a cache"), and "kind of"/"sort of" are real
 * hedges whose removal changes what the speaker committed to. Backchannel agreement tokens
 * ("uh-huh", "mm-hmm") are explicitly preserved — they mean "yes", and deleting them would silently
 * flip the meaning of an answer.
 */
object FillerWordCleaner {

    /**
     * Non-lexical hesitation sounds. Every one of these is a vocalized pause with no meaning in
     * any context, which is what makes unconditional removal safe. Note the deliberate absence of
     * "uh-huh"/"mm-hmm": the `\b`-delimited matching below stops at the hyphen, so they survive.
     */
    private val HESITATION_SOUNDS = listOf(
        "uh", "uhh", "uhm", "um", "umm", "erm", "er", "ah", "ahh", "eh"
    )

    /**
     * Discourse markers that are filler *only* when parenthetical. These are removed solely when
     * set off by commas ("it's, you know, complicated"), never in their lexical use ("you know the
     * answer") — the comma is what distinguishes the two, so it is required rather than assumed.
     */
    private val PARENTHETICAL_MARKERS = listOf("you know", "i mean")

    /**
     * Function words whose immediate repetition is a stutter rather than intent. Deliberately a
     * closed list: blanket repeat-collapsing would mangle real English ("I know that that works",
     * "she had had enough") and intentional emphasis ("very very slow"), so only words that have
     * no grammatical reason to double are included.
     */
    private val STUTTER_PRONE_WORDS = listOf(
        "i", "the", "a", "an", "and", "but", "so", "to", "of", "it", "we", "they",
        "he", "she", "you", "my", "is", "was", "in", "on", "for", "this", "with"
    )

    // Alternations are sorted longest-first so a shorter option can never win a prefix match on a
    // longer one; the boundaries below make that airtight rather than order-dependent.
    private fun alternation(options: List<String>): String =
        options.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    // Plain \b is not enough here: it treats a hyphen as a boundary, so "\buh\b" happily matches
    // the "uh" inside "uh-huh" and would turn an agreement token into "-huh". Requiring that
    // neither neighbour is a word character *or a hyphen* keeps hyphenated tokens intact.
    private const val NOT_WORD_BEFORE = """(?<![\w-])"""
    private const val NOT_WORD_AFTER = """(?![\w-])"""

    private val hesitationLeading = Regex(
        """^\s*$NOT_WORD_BEFORE(?:${alternation(HESITATION_SOUNDS)})$NOT_WORD_AFTER\s*,?\s*""",
        RegexOption.IGNORE_CASE
    )
    private val hesitationCommaFlanked = Regex(
        """\s*,\s*$NOT_WORD_BEFORE(?:${alternation(HESITATION_SOUNDS)})$NOT_WORD_AFTER\s*,""",
        RegexOption.IGNORE_CASE
    )
    private val hesitationStandalone = Regex(
        """\s*$NOT_WORD_BEFORE(?:${alternation(HESITATION_SOUNDS)})$NOT_WORD_AFTER\s*,?""",
        RegexOption.IGNORE_CASE
    )
    private val markerCommaFlanked = Regex(
        """\s*,\s*$NOT_WORD_BEFORE(?:${alternation(PARENTHETICAL_MARKERS)})$NOT_WORD_AFTER\s*,""",
        RegexOption.IGNORE_CASE
    )
    private val markerLeading = Regex(
        """^\s*$NOT_WORD_BEFORE(?:${alternation(PARENTHETICAL_MARKERS)})$NOT_WORD_AFTER\s*,\s*""",
        RegexOption.IGNORE_CASE
    )
    private val stutterRepeat = Regex(
        """\b(${alternation(STUTTER_PRONE_WORDS)})(\s+\1\b)+""",
        RegexOption.IGNORE_CASE
    )

    private val repeatedComma = Regex(""",(\s*,)+""")
    private val spaceBeforePunctuation = Regex("""\s+([,.;:!?])""")
    private val collapsedWhitespace = Regex("""\s{2,}""")

    /**
     * Returns [text] with hesitation sounds, parenthetical discourse markers, and stuttered word
     * repeats removed, then whitespace/punctuation tidied and sentence starts re-capitalized.
     *
     * A comma-flanked filler ("one full, uh, train of thought") collapses to a plain space rather
     * than a comma: those commas exist only to bracket the hesitation, so keeping one would leave
     * a comma splice the speaker never intended ("one full, train of thought").
     *
     * If cleaning would empty out a non-blank input — e.g. a segment whose entire content really
     * was "Uh." — the original text is returned unchanged. Blanking a real transcript line would
     * misrepresent the recording as containing nothing at all.
     */
    fun clean(text: String): String {
        if (text.isBlank()) return text

        var result = text
        result = markerCommaFlanked.replace(result, " ")
        result = markerLeading.replace(result, "")
        result = hesitationCommaFlanked.replace(result, " ")
        result = hesitationLeading.replace(result, "")
        result = hesitationStandalone.replace(result, " ")
        result = stutterRepeat.replace(result) { match -> match.groupValues[1] }
        result = repeatedComma.replace(result, ",")
        result = spaceBeforePunctuation.replace(result, "$1")
        result = collapsedWhitespace.replace(result, " ")
        result = result.trim().trimStart(',').trim()

        // "Blank" has to mean "no words left", not just "no characters left": a segment whose
        // entire content was "Uh." cleans down to a bare ".", which is just as much a loss of the
        // real recording as an empty string would be.
        if (result.none { it.isLetterOrDigit() }) return text
        return capitalizeSentences(result)
    }

    /** Applies [clean] only when [enabled]; the identity transform otherwise. Keeps call sites
     * from repeating the same `if` around a user preference. */
    fun cleanIf(enabled: Boolean, text: String): String = if (enabled) clean(text) else text

    /**
     * Re-capitalizes the first letter of the text and of each sentence following terminal
     * punctuation. Removing a leading "Uh, " leaves the next word lowercase, and a transcript full
     * of lowercase sentence starts reads as broken rather than cleaned.
     */
    private fun capitalizeSentences(text: String): String {
        val builder = StringBuilder(text)
        var capitalizeNext = true
        for (index in builder.indices) {
            val ch = builder[index]
            if (capitalizeNext && ch.isLetter()) {
                builder[index] = ch.uppercaseChar()
                capitalizeNext = false
            } else if (ch == '.' || ch == '?' || ch == '!') {
                capitalizeNext = true
            }
        }
        return builder.toString()
    }
}
