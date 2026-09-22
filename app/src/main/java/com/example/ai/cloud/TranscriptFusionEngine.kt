package com.example.ai.cloud

import com.example.ai.transcript.AsrWindowReconciler
import com.example.ai.transcript.CanonicalWord
import com.example.ai.transcript.TranscriptSource

/** One readable sentence or paragraph from the smart pass, with no timing and no speaker. */
data class SmartSpan(val text: String)

/**
 * The result of fusing the verbatim and smart passes: the smart pass's words, carrying the
 * verbatim pass's timing and speakers.
 */
data class FusedTranscript(
    val words: List<CanonicalWord>,
    /** How much of the smart text the aligner could anchor to real verbatim words, 0f..1f. */
    val alignmentCoverage: Float
)

/**
 * Fuses the two Gemini transcription passes into one word stream.
 *
 * ```
 * verbatim  ->  truth: what was said, when, and by whom
 * smart     ->  readability: punctuation, casing, disfluency cleanup, self-corrections
 * ```
 *
 * The smart pass does **not** replace the verbatim pass. It cannot: run in smart mode, the model
 * does not return word-level timestamps or diarization, so a smart transcript has no idea when
 * anything was said or who said it. Taking its text wholesale would mean throwing away every
 * timestamp and speaker in the recording, and then either losing provenance entirely or asking a
 * language model to reconstruct it — which is asking it to invent, and is precisely what this
 * architecture exists to avoid.
 *
 * So the smart text is **aligned onto** the verbatim word stream, deterministically, by matching
 * normalized word forms. A smart word that matches a verbatim word inherits that word's start,
 * end, speaker and attribution and contributes only its surface form — the punctuation and casing
 * that make it readable. Smart words that match nothing are dropped rather than inserted with
 * guessed timing, and verbatim words the smart pass removed (fillers, false starts) simply do not
 * appear, which is the cleanup the smart pass was run for.
 *
 * No LLM is asked for a timestamp. No LLM is asked for a speaker identity. Every word in the
 * output is anchored to a word the fidelity pass actually heard.
 */
object TranscriptFusionEngine {

    /** Below this coverage the smart pass disagreed with the verbatim pass so broadly that
     * aligning it would produce a transcript resembling neither; the caller keeps verbatim. */
    const val MIN_USABLE_COVERAGE = 0.6f

    /**
     * @param verbatimWords The fidelity layer: real timestamps, real speakers.
     * @param smartText The readability layer's text, in recording order.
     * @return Fused words, or the verbatim words unchanged when alignment coverage is too low to
     *   trust. Either way the result is a usable transcript — a weak smart pass degrades the
     *   polish, never the transcript.
     */
    fun fuse(verbatimWords: List<CanonicalWord>, smartText: String): FusedTranscript {
        if (verbatimWords.isEmpty()) return FusedTranscript(emptyList(), 0f)
        val smartTokens = tokenize(smartText)
        if (smartTokens.isEmpty()) return FusedTranscript(verbatimWords, 0f)

        val alignment = align(verbatimWords.map { AsrWindowReconciler.normalize(it.text) }, smartTokens.map { it.key })
        val matched = alignment.count { it >= 0 }
        val coverage = matched.toFloat() / smartTokens.size

        if (coverage < MIN_USABLE_COVERAGE) {
            return FusedTranscript(verbatimWords, coverage)
        }

        val fused = mutableListOf<CanonicalWord>()
        for ((smartIndex, verbatimIndex) in alignment.withIndex()) {
            if (verbatimIndex < 0) continue
            val anchor = verbatimWords[verbatimIndex]
            fused += anchor.copy(
                // The surface form is the only thing taken from the smart pass. Timing, speaker
                // and attribution stay exactly as the fidelity pass reported them.
                text = smartTokens[smartIndex].surface,
                source = TranscriptSource.GEMINI_SMART
            )
        }
        // Ids are reassigned positionally so the fused stream is a well-formed canonical stream
        // in its own right, with no gaps where filler words were removed.
        return FusedTranscript(
            words = fused.mapIndexed { index, word -> word.copy(id = "w$index") },
            alignmentCoverage = coverage
        )
    }

    private data class SmartToken(val surface: String, val key: String)

    private fun tokenize(text: String): List<SmartToken> =
        text.split(Regex("\\s+"))
            .mapNotNull { raw ->
                val surface = raw.trim()
                if (surface.isEmpty()) return@mapNotNull null
                val key = AsrWindowReconciler.normalize(surface)
                if (key.isEmpty()) null else SmartToken(surface, key)
            }

    /**
     * Longest-common-subsequence alignment of smart tokens onto verbatim words.
     *
     * LCS is the right tool here precisely because the two passes legitimately disagree by
     * *deletion*: the smart pass removes fillers, repeated false starts and self-corrections, and
     * keeps everything else in order. A subsequence alignment expresses that exactly, and — unlike
     * a greedy nearest-match scan — it cannot cross itself, so the fused stream is guaranteed to
     * stay in chronological order.
     *
     * @return For each smart token, the index of the verbatim word it aligned to, or -1.
     */
    internal fun align(verbatim: List<String>, smart: List<String>): IntArray {
        val n = verbatim.size
        val m = smart.size
        // Guard against a pathological pairing (a very long recording against a very long smart
        // text) building an enormous table; above this the passes are not comparable anyway.
        if (n.toLong() * m.toLong() > MAX_ALIGNMENT_CELLS) return IntArray(m) { -1 }

        val lengths = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lengths[i][j] = if (verbatim[i] == smart[j]) {
                    lengths[i + 1][j + 1] + 1
                } else {
                    maxOf(lengths[i + 1][j], lengths[i][j + 1])
                }
            }
        }

        val result = IntArray(m) { -1 }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                verbatim[i] == smart[j] -> { result[j] = i; i++; j++ }
                lengths[i + 1][j] >= lengths[i][j + 1] -> i++
                else -> j++
            }
        }
        return result
    }

    private const val MAX_ALIGNMENT_CELLS = 40_000_000L
}
