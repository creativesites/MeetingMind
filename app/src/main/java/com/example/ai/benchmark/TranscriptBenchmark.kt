package com.example.ai.benchmark

import com.example.ai.transcript.AsrWindowReconciler
import com.example.ai.transcript.CanonicalTranscript
import com.example.ai.transcript.CanonicalWord
import kotlin.math.abs

/**
 * A hand-checked reference for one benchmark recording: what was actually said, by whom, when.
 *
 * Reference speaker ids are arbitrary labels ("A", "B"); they are matched to the pipeline's own
 * ids by the benchmark, since no engine can be expected to guess the names a human annotator used.
 */
data class ReferenceTranscript(
    val recordingId: String,
    val words: List<ReferenceWord>,
    /** Which of the brief's §32 scenarios this recording covers, for grouping results. */
    val scenarios: Set<String> = emptySet()
)

data class ReferenceWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val speakerId: String?
)

/** What one pipeline scored on one recording. */
data class BenchmarkResult(
    val recordingId: String,
    val pipelineName: String,

    /** Word error rate: (substitutions + deletions + insertions) / reference words. */
    val wer: Double,
    /** Character error rate, over the concatenated transcripts. */
    val cer: Double,

    /** Share of correctly-recognized words given to the wrong speaker. Null when the reference
     * has no speaker labels, or when no word was matched at all. */
    val speakerAttributionError: Double?,
    /** Distinct reference speakers that the pipeline split across several of its own. */
    val speakerFragmentation: Int,
    /** Distinct reference speakers the pipeline merged into one of its own. */
    val speakerConfusion: Int,

    /** Mean absolute start-time error, in ms, over correctly-recognized words. Null when none matched. */
    val timestampErrorMs: Double?,

    /** Adjacent repeated runs per 1000 words — a failed overlap reconciliation's fingerprint. */
    val duplicateRatePer1000: Double,
    /** Share of paragraphs at most four words long. */
    val fragmentationRate: Double,

    val referenceWordCount: Int,
    val hypothesisWordCount: Int
) {
    fun toLogLine(): String =
        "$pipelineName/$recordingId wer=${"%.3f".format(wer)} cer=${"%.3f".format(cer)} " +
            "spkErr=${speakerAttributionError?.let { "%.3f".format(it) } ?: "n/a"} " +
            "spkFrag=$speakerFragmentation spkConf=$speakerConfusion " +
            "tsErr=${timestampErrorMs?.let { "%.0f".format(it) } ?: "n/a"}ms " +
            "dup/1k=${"%.1f".format(duplicateRatePer1000)} frag=${"%.2f".format(fragmentationRate)}"
}

/**
 * Scores a produced transcript against a hand-checked reference.
 *
 * ### What this is, and what is missing
 *
 * These are the metrics the brief's §32 asks for, implemented and unit-tested. What is **not**
 * here is the corpus — the recordings listed in §32 (crosstalk, accented English, code switching,
 * 30+ minute meetings, and the rest) and their reference transcripts. Without audio there is
 * nothing to measure, and a harness written with nothing to run on is a harness that gets shaped
 * to fit whatever it is first pointed at.
 *
 * So this is deliberately the half that can be got right in advance: correct alignment, correct
 * arithmetic, tested on synthetic cases with known answers. Adding a recording means adding a
 * [ReferenceTranscript] and calling [score]; nothing here has to change.
 *
 * ### Why alignment is shared with fusion
 *
 * Scoring and fusion are the same problem — line up two transcripts of the same speech that
 * disagree by insertion, deletion and substitution — so the same Levenshtein alignment is used for
 * both. A metric computed by a different alignment than the pipeline uses measures the difference
 * between two aligners as much as the difference between two transcripts.
 */
object TranscriptBenchmark {

    fun score(
        reference: ReferenceTranscript,
        transcript: CanonicalTranscript,
        pipelineName: String
    ): BenchmarkResult {
        val hypothesis = transcript.words
        val refKeys = reference.words.map { AsrWindowReconciler.normalize(it.text) }
        val hypKeys = hypothesis.map { AsrWindowReconciler.normalize(it.text) }
        val ops = align(refKeys, hypKeys)

        val substitutions = ops.count { it is EditOp.Substitute }
        val deletions = ops.count { it is EditOp.Delete }
        val insertions = ops.count { it is EditOp.Insert }
        val matches = ops.filterIsInstance<EditOp.Match>()

        val wer = if (refKeys.isEmpty()) {
            if (hypKeys.isEmpty()) 0.0 else 1.0
        } else {
            (substitutions + deletions + insertions).toDouble() / refKeys.size
        }

        val speakerStats = scoreSpeakers(reference.words, hypothesis, matches)
        val timestampError = matches
            .map { abs(hypothesis[it.hypIndex].startMs - reference.words[it.refIndex].startMs).toDouble() }
            .takeIf { it.isNotEmpty() }
            ?.average()

        val paragraphWordCounts = transcript.paragraphs.map { it.wordIds.size }

        return BenchmarkResult(
            recordingId = reference.recordingId,
            pipelineName = pipelineName,
            wer = wer,
            cer = characterErrorRate(
                reference.words.joinToString(" ") { it.text },
                hypothesis.joinToString(" ") { it.text }
            ),
            speakerAttributionError = speakerStats.attributionError,
            speakerFragmentation = speakerStats.fragmentation,
            speakerConfusion = speakerStats.confusion,
            timestampErrorMs = timestampError,
            duplicateRatePer1000 = if (hypothesis.isEmpty()) 0.0 else {
                com.example.ai.transcript.TranscriptQualityEvaluator.countDuplicatePhrases(hypothesis) * 1000.0 / hypothesis.size
            },
            fragmentationRate = if (paragraphWordCounts.isEmpty()) 0.0 else {
                paragraphWordCounts.count { it <= 4 }.toDouble() / paragraphWordCounts.size
            },
            referenceWordCount = refKeys.size,
            hypothesisWordCount = hypKeys.size
        )
    }

    private data class SpeakerStats(
        val attributionError: Double?,
        val fragmentation: Int,
        val confusion: Int
    )

    /**
     * Speaker metrics over correctly-recognized words only.
     *
     * Reference labels and pipeline labels are different vocabularies, so they are matched first:
     * each reference speaker is mapped to whichever pipeline speaker covers most of their words.
     * Attribution error is then the share of matched words whose pipeline speaker is not that one.
     *
     * *Fragmentation* counts reference speakers split across several pipeline speakers — the
     * "one person became Speaker 1, 3 and 5" defect. *Confusion* counts pipeline speakers that two
     * or more reference speakers were mapped onto — the "two people became one" defect. They are
     * reported separately because they have different causes and different fixes.
     */
    private fun scoreSpeakers(
        reference: List<ReferenceWord>,
        hypothesis: List<CanonicalWord>,
        matches: List<EditOp.Match>
    ): SpeakerStats {
        val labelled = matches.filter { reference[it.refIndex].speakerId != null }
        if (labelled.isEmpty()) return SpeakerStats(null, 0, 0)

        val pairs = labelled.map { reference[it.refIndex].speakerId!! to hypothesis[it.hypIndex].speakerId }
        val byReferenceSpeaker = pairs.groupBy({ it.first }, { it.second })

        val mapping = byReferenceSpeaker.mapValues { (_, hypSpeakers) ->
            hypSpeakers.filterNotNull().groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }

        val wrong = pairs.count { (refSpeaker, hypSpeaker) -> hypSpeaker != mapping[refSpeaker] }
        val fragmentation = byReferenceSpeaker.count { (_, hypSpeakers) -> hypSpeakers.distinct().size > 1 }
        val confusion = mapping.values.filterNotNull()
            .groupingBy { it }.eachCount()
            .count { it.value > 1 }

        return SpeakerStats(
            attributionError = wrong.toDouble() / pairs.size,
            fragmentation = fragmentation,
            confusion = confusion
        )
    }

    internal fun characterErrorRate(reference: String, hypothesis: String): Double {
        val ref = reference.lowercase().filter { !it.isWhitespace() }
        val hyp = hypothesis.lowercase().filter { !it.isWhitespace() }
        if (ref.isEmpty()) return if (hyp.isEmpty()) 0.0 else 1.0
        return levenshtein(ref.toList(), hyp.toList()).toDouble() / ref.length
    }

    /** One step of an alignment between a reference and a hypothesis. */
    internal sealed interface EditOp {
        data class Match(val refIndex: Int, val hypIndex: Int) : EditOp
        data class Substitute(val refIndex: Int, val hypIndex: Int) : EditOp
        data class Delete(val refIndex: Int) : EditOp
        data class Insert(val hypIndex: Int) : EditOp
    }

    /** Standard Levenshtein alignment with backtrace, at word level. */
    internal fun <T> align(reference: List<T>, hypothesis: List<T>): List<EditOp> {
        val n = reference.size
        val m = hypothesis.size
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][0] = i
        for (j in 0..m) cost[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val substitution = cost[i - 1][j - 1] + if (reference[i - 1] == hypothesis[j - 1]) 0 else 1
                cost[i][j] = minOf(substitution, cost[i - 1][j] + 1, cost[i][j - 1] + 1)
            }
        }

        val ops = mutableListOf<EditOp>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            val same = i > 0 && j > 0 && reference[i - 1] == hypothesis[j - 1]
            when {
                i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + if (same) 0 else 1 -> {
                    ops += if (same) EditOp.Match(i - 1, j - 1) else EditOp.Substitute(i - 1, j - 1)
                    i--; j--
                }
                i > 0 && cost[i][j] == cost[i - 1][j] + 1 -> { ops += EditOp.Delete(i - 1); i-- }
                else -> { ops += EditOp.Insert(j - 1); j-- }
            }
        }
        return ops.reversed()
    }

    private fun <T> levenshtein(a: List<T>, b: List<T>): Int {
        var previous = IntArray(b.size + 1) { it }
        for (i in 1..a.size) {
            val current = IntArray(b.size + 1)
            current[0] = i
            for (j in 1..b.size) {
                current[j] = minOf(
                    previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                    previous[j] + 1,
                    current[j - 1] + 1
                )
            }
            previous = current
        }
        return previous[b.size]
    }
}
