package com.example.ai.pipeline

import com.example.core.model.TranscriptCleanupProfile

/**
 * Guards every [TranscriptCleanupEngine]/[TranscriptAiCleanupEngine] candidate against semantic
 * drift before it is ever cached or shown: a cleanup pass must never be trusted blindly just
 * because it produced fluent-looking text. This never compares against anything but the real raw
 * text a candidate was derived from, and it never fabricates a replacement — a rejected candidate
 * simply means the caller keeps the raw text (or a prior, already-accepted cleanup) instead.
 *
 * Mode-aware, not mode-blind: [TranscriptCleanupProfile] supplies how much a given
 * [com.example.core.model.TranscriptCleanupMode] is allowed to shrink/grow the text and how much
 * vocabulary it's allowed to change — Aggressive mode is *expected* to differ substantially from
 * the raw text, so holding it to Conservative's thresholds would reject exactly the behavior it
 * exists to test. What never scales with mode: numbers, dates, and monetary amounts must survive
 * exactly, in every mode, no exception — and a capitalized name/term may only be swapped or
 * dropped when the raw text itself contains direct evidence it was a self-correction (see
 * [hasSelfCorrectionEvidence]), never merely because the mode is permissive.
 */
object TranscriptQualityValidator {

    data class Verdict(val accepted: Boolean, val reason: String? = null)

    /**
     * @param profile Supplies mode-scaled length-ratio/word-overlap thresholds. Omitted (or null)
     *   uses the original, unscaled thresholds this validator shipped with — i.e. Conservative-
     *   equivalent — so every pre-existing caller (deterministic rule-based cleanup, which has no
     *   concept of cleanup mode) keeps its exact original behavior with zero code change.
     */
    fun validate(raw: String, cleaned: String?, profile: TranscriptCleanupProfile? = null): Verdict {
        if (cleaned == null) return Verdict(false, "No cleaned candidate was produced.")
        if (cleaned.isBlank()) {
            return if (raw.isBlank()) Verdict(true) else Verdict(false, "Cleanup produced empty output for non-blank input.")
        }
        if (cleaned.none { it.isLetterOrDigit() }) {
            return Verdict(false, "Cleanup produced no readable content.")
        }

        val minLengthRatio = profile?.minLengthRatio ?: DEFAULT_MIN_LENGTH_RATIO
        val maxLengthRatio = profile?.maxLengthRatio ?: DEFAULT_MAX_LENGTH_RATIO
        val minWordOverlap = profile?.minWordOverlap ?: DEFAULT_MIN_WORD_OVERLAP

        // A cleanup pass trims/restructures; it does not rewrite the substance. A candidate
        // dramatically shorter or longer than the raw text it was derived from is far more likely
        // to be a hallucinated rewrite/summary than a genuine cleanup — the acceptable range
        // widens with mode (Aggressive is allowed real restructuring) but is never unbounded.
        val lengthRatio = cleaned.length.toDouble() / raw.length.coerceAtLeast(1)
        if (lengthRatio < minLengthRatio || lengthRatio > maxLengthRatio) {
            return Verdict(false, "Cleaned length ratio (%.2f) is outside the safe range for this mode.".format(lengthRatio))
        }

        // Every digit sequence AND every spelled-out cardinal number word in the raw text must
        // survive intact, in EVERY mode, no exception — a quantity is exactly the kind of fact no
        // cleanup pass may alter regardless of how permissive the mode is ("$15,000" -> "$50,000",
        // "3pm" -> "5pm", "three vehicles" -> "five vehicles"). Unlike capitalized names, a number
        // word never gets the self-correction exception below — a speaker correcting "three... no,
        // five" is real content that must survive, not evidence that either count can be dropped.
        val missingNumbers = extractNumbers(raw) - extractNumbers(cleaned)
        if (missingNumbers.isNotEmpty()) {
            return Verdict(false, "Cleanup changed or dropped a number from the raw text: $missingNumbers")
        }

        // Case-insensitive presence, not position: a capitalized word in the raw text (name, place,
        // weekday, month) must still appear *somewhere* in the cleaned text, after excluding words
        // that are only capitalized because they happen to open a sentence. A missing word is
        // rejected UNLESS the raw text itself contains direct evidence it was a self-correction —
        // see [hasSelfCorrectionEvidence]. This evidence check is intentionally mode-independent:
        // recognizing "Myavana ... Myavanna ... Myavana" as one entity the speaker corrected
        // themselves isn't "being more permissive," it's being more accurate — but it never lets a
        // mode substitute an entity with no such evidence ("John" -> "Peter" stays rejected in
        // every mode).
        val rawWords = extractCapitalizedWords(raw)
        val cleanedWords = extractCapitalizedWords(cleaned)
        val missingWords = (rawWords - cleanedWords).filterNot { missing ->
            hasSelfCorrectionEvidence(missing, rawWords, cleanedWords)
        }
        if (missingWords.isNotEmpty()) {
            return Verdict(false, "Cleanup changed or dropped a capitalized name/word from the raw text without evidence: $missingWords")
        }

        // Catches fluent-but-unrelated output that happens to pass the checks above — the hallmark
        // of a model paraphrasing or inventing content rather than cleaning it. The floor drops as
        // mode permissiveness rises: Aggressive mode is expected to swap many function words while
        // still preserving the actual content words.
        val overlap = wordOverlap(raw, cleaned)
        if (overlap < minWordOverlap) {
            return Verdict(false, "Cleaned text shares too little vocabulary (%.2f) with the raw text for this mode.".format(overlap))
        }

        return Verdict(true)
    }

    /**
     * True when [missing] (a capitalized word dropped from the cleaned text) shares a long
     * enough prefix with some OTHER raw capitalized word that DID survive into the cleaned text —
     * i.e. the raw text itself contains a near-variant spelling of the same apparent entity, and
     * cleanup picked one variant rather than silently swapping in an unrelated word. A lone,
     * un-varianted capitalized word ("John" with no "Jon"/"Jhon" anywhere nearby in the raw text)
     * never gets this exception, however permissive the mode.
     */
    private fun hasSelfCorrectionEvidence(missing: String, rawWords: Set<String>, cleanedWords: Set<String>): Boolean =
        rawWords.any { other -> other != missing && other in cleanedWords && sharesSignificantPrefix(missing, other) }

    private fun sharesSignificantPrefix(a: String, b: String, minLen: Int = 4): Boolean {
        val shorter = minOf(a.length, b.length)
        if (shorter < minLen) return false
        var i = 0
        while (i < shorter && a[i] == b[i]) i++
        return i >= minLen
    }

    private val NUMBER_REGEX = Regex("""\d+(?:[.,:]\d+)*""")
    private val WORD_REGEX = Regex("""[A-Za-z]+""")
    private val CAPITALIZED_WORD_REGEX = Regex("""\b[A-Z][a-z]+\b""")

    // Spelled-out cardinal numbers ("three vehicles" -> "five vehicles") are just as much a fact
    // as a digit sequence — a cleanup pass that "normalizes" spoken numbers must not be able to
    // silently change the quantity while doing so. Deliberately NOT exhaustive prose-English
    // coverage (no "a couple", "a dozen", etc.) — just the common cardinal number words a speaker
    // actually says.
    private val NUMBER_WORD_REGEX = Regex(
        """(?i)\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion)\b"""
    )

    /** Digit sequences (grouping punctuation stripped, so "15,000" and "15000" compare equal) plus
     * spelled-out cardinal number words — both are treated as the same kind of fact. */
    private fun extractNumbers(text: String): Set<String> =
        NUMBER_REGEX.findAll(text).map { it.value.replace(",", "").replace(":", "") }.toSet() +
            NUMBER_WORD_REGEX.findAll(text).map { it.value.lowercase() }.toSet()

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

    private const val DEFAULT_MIN_LENGTH_RATIO = 0.4
    private const val DEFAULT_MAX_LENGTH_RATIO = 1.3
    private const val DEFAULT_MIN_WORD_OVERLAP = 0.5
}
