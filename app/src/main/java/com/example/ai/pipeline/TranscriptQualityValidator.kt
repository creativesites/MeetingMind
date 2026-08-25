package com.example.ai.pipeline

/**
 * Guards every [TranscriptCleanupEngine] candidate against semantic drift before it is ever
 * cached or shown: a cleanup pass — especially a future SLM-backed one — must never be trusted
 * blindly just because it produced fluent-looking text. This never compares against anything but
 * the real raw text a candidate was derived from, and it never fabricates a replacement — a
 * rejected candidate simply means the caller keeps the raw text instead.
 *
 * Deliberately conservative and purely deterministic (no model of its own): every check here is a
 * cheap, explainable proxy for "did this rewrite change what was actually said," tuned to reject
 * a genuinely dangerous edit (a changed number, a swapped name, an invented sentence) without
 * being so strict that it rejects [RuleBasedTranscriptCleanupEngine]'s own legitimate,
 * filler-only output.
 */
object TranscriptQualityValidator {

    data class Verdict(val accepted: Boolean, val reason: String? = null)

    fun validate(raw: String, cleaned: String?): Verdict {
        if (cleaned == null) return Verdict(false, "No cleaned candidate was produced.")
        if (cleaned.isBlank()) {
            return if (raw.isBlank()) Verdict(true) else Verdict(false, "Cleanup produced empty output for non-blank input.")
        }
        if (cleaned.none { it.isLetterOrDigit() }) {
            return Verdict(false, "Cleanup produced no readable content.")
        }

        // A real cleanup pass trims filler/disfluency; it does not rewrite the substance. A
        // candidate dramatically shorter or longer than the raw text it was derived from is far
        // more likely to be a hallucinated rewrite/summary than a genuine cleanup.
        val lengthRatio = cleaned.length.toDouble() / raw.length.coerceAtLeast(1)
        if (lengthRatio < MIN_LENGTH_RATIO || lengthRatio > MAX_LENGTH_RATIO) {
            return Verdict(false, "Cleaned length ratio (%.2f) is outside the safe range.".format(lengthRatio))
        }

        // Every digit sequence in the raw text must survive intact — a number is exactly the kind
        // of fact a cleanup pass must never alter ("$15,000" -> "$50,000", "3pm" -> "5pm").
        val missingNumbers = extractNumbers(raw) - extractNumbers(cleaned)
        if (missingNumbers.isNotEmpty()) {
            return Verdict(false, "Cleanup changed or dropped a number from the raw text: $missingNumbers")
        }

        // Case-insensitive presence, not position: a capitalized word in the raw text (name, place,
        // weekday, month) must still appear *somewhere* in the cleaned text, after excluding words
        // that are only capitalized because they happen to open a sentence — a hesitation sound
        // like "Uh, John said..." is capitalized at the raw text's own start purely by virtue of
        // sentence position, and legitimately disappearing is exactly what a real cleanup pass is
        // for; a real name in that same spot ("John said...") is not. This deliberately does not
        // require the same capitalization or sentence position either — filler removal can
        // legitimately move a real name to the start of a sentence — only that the word itself was
        // not swapped or dropped ("Monday" -> "Friday", "John" -> "Peter").
        val missingWords = extractCapitalizedWords(raw) - extractCapitalizedWords(cleaned)
        if (missingWords.isNotEmpty()) {
            return Verdict(false, "Cleanup changed or dropped a capitalized name/word from the raw text: $missingWords")
        }

        // Catches fluent-but-unrelated output that happens to pass the checks above — the hallmark
        // of a model paraphrasing or inventing content rather than cleaning it.
        val overlap = wordOverlap(raw, cleaned)
        if (overlap < MIN_WORD_OVERLAP) {
            return Verdict(false, "Cleaned text shares too little vocabulary (%.2f) with the raw text.".format(overlap))
        }

        return Verdict(true)
    }

    private val NUMBER_REGEX = Regex("""\d+(?:[.,:]\d+)*""")
    private val WORD_REGEX = Regex("""[A-Za-z]+""")
    private val CAPITALIZED_WORD_REGEX = Regex("""\b[A-Z][a-z]+\b""")

    /** Digit sequences with grouping punctuation stripped, so "15,000" and "15000" compare equal. */
    private fun extractNumbers(text: String): Set<String> =
        NUMBER_REGEX.findAll(text).map { it.value.replace(",", "").replace(":", "") }.toSet()

    private fun extractCapitalizedWords(text: String): Set<String> =
        CAPITALIZED_WORD_REGEX.findAll(text).map { it.value.lowercase() }.toSet() - SENTENCE_OPENER_WORDS

    // Words that are routinely capitalized purely because they open a sentence, not because
    // they're a name/place/proper noun — including every hesitation sound FillerWordCleaner
    // actually removes (see com.example.core.common.FillerWordCleaner.HESITATION_SOUNDS), so a
    // legitimate "Uh, John said..." -> "John said..." cleanup is never mistaken for a dropped
    // name. Deliberately NOT exhaustive prose-English coverage — just common enough sentence
    // openers that a real cleanup pass might reasonably drop or reposition without touching any
    // actual fact.
    private val SENTENCE_OPENER_WORDS = setOf(
        "uh", "uhh", "uhm", "um", "umm", "erm", "er", "ah", "ahh", "eh",
        "well", "now", "okay", "right", "actually", "basically", "yeah", "yes", "no",
        "the", "this", "that", "these", "those", "and", "but", "so"
    )

    private fun wordOverlap(raw: String, cleaned: String): Double {
        val rawWords = WORD_REGEX.findAll(raw).map { it.value.lowercase() }.toSet()
        if (rawWords.isEmpty()) return 1.0
        val cleanedWords = WORD_REGEX.findAll(cleaned).map { it.value.lowercase() }.toSet()
        return (rawWords intersect cleanedWords).size.toDouble() / rawWords.size
    }

    private const val MIN_LENGTH_RATIO = 0.4
    private const val MAX_LENGTH_RATIO = 1.3
    private const val MIN_WORD_OVERLAP = 0.5
}
