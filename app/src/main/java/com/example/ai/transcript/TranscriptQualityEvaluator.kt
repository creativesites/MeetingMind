package com.example.ai.transcript

/**
 * Engineering diagnostics for one assembled transcript.
 *
 * Deliberately **not** a user-facing score. Every figure here is a property the pipeline can
 * actually measure about its own output without a reference transcript — none of them is accuracy,
 * and presenting them as "97% accurate" would be exactly the fake precision the product must not
 * show. They exist so a regression is visible in logs, so the benchmark suite has something to
 * compare across pipeline versions, and so the model router can notice that a local run came out
 * badly enough to be worth offering a cloud re-run.
 */
data class TranscriptQualityReport(
    val wordCount: Int,
    val paragraphCount: Int,
    val utteranceCount: Int,
    val speakerTurnCount: Int,
    val speakerCount: Int,

    /** Mean words per paragraph. Very low values are the fragmentation defect this overhaul exists
     * to remove. */
    val meanParagraphWords: Double,

    /** Share of paragraphs with at most [FRAGMENT_WORD_THRESHOLD] words. */
    val fragmentationRate: Double,

    /** Share of paragraphs whose text does not end in terminal punctuation — a proxy for thoughts
     * cut mid-sentence. */
    val incompleteSentenceRate: Double,

    /** Share of words attributed with [AttributionConfidence.HIGH]. */
    val highConfidenceAttributionRate: Double,

    /** Share of words carrying no speaker at all. */
    val unattributedWordRate: Double,

    /** Speaker turns per minute of speech. A conversation runs in single digits; double digits
     * means diarization is flickering between labels. */
    val speakerTurnsPerMinute: Double,

    /** Repeated word n-grams at paragraph seams — the fingerprint of failed overlap reconciliation. */
    val duplicatePhraseCount: Int,

    /** Words whose timing is non-monotonic, zero-length or negative. Should always be zero. */
    val timestampAnomalyCount: Int
) {
    /**
     * A coarse verdict used only for routing and logging. Never shown as a number to the user.
     */
    val verdict: Verdict
        get() = when {
            wordCount == 0 -> Verdict.EMPTY
            timestampAnomalyCount > 0 || duplicatePhraseCount > 0 -> Verdict.SUSPECT
            fragmentationRate > 0.5 || speakerTurnsPerMinute > 40 -> Verdict.SUSPECT
            fragmentationRate > 0.25 || unattributedWordRate > 0.3 -> Verdict.MIXED
            else -> Verdict.SOUND
        }

    enum class Verdict { EMPTY, SUSPECT, MIXED, SOUND }

    /** A one-line, transcript-text-free summary safe to write to logcat. */
    fun toLogLine(): String =
        "words=$wordCount paragraphs=$paragraphCount speakers=$speakerCount " +
            "meanParaWords=${"%.1f".format(meanParagraphWords)} " +
            "fragRate=${"%.2f".format(fragmentationRate)} " +
            "highAttr=${"%.2f".format(highConfidenceAttributionRate)} " +
            "unattr=${"%.2f".format(unattributedWordRate)} " +
            "turnsPerMin=${"%.1f".format(speakerTurnsPerMinute)} " +
            "dupPhrases=$duplicatePhraseCount tsAnomalies=$timestampAnomalyCount " +
            "verdict=$verdict"

    companion object {
        const val FRAGMENT_WORD_THRESHOLD = 4
    }
}

object TranscriptQualityEvaluator {

    /** Shortest repeated run treated as a duplicate phrase. Three words is long enough that a
     * genuine repetition ("no, no, no") is rare and a reconciliation failure is not. */
    private const val MIN_DUPLICATE_RUN = 3

    /** Longest repeated run looked for. A failed overlap reconciliation repeats a phrase, not a
     * paragraph, and scanning further costs time for nothing. */
    private const val MAX_DUPLICATE_RUN = 12

    fun evaluate(transcript: CanonicalTranscript): TranscriptQualityReport {
        val words = transcript.words
        val paragraphs = transcript.paragraphs
        if (words.isEmpty()) {
            return TranscriptQualityReport(
                wordCount = 0, paragraphCount = 0, utteranceCount = 0, speakerTurnCount = 0,
                speakerCount = 0, meanParagraphWords = 0.0, fragmentationRate = 0.0,
                incompleteSentenceRate = 0.0, highConfidenceAttributionRate = 0.0,
                unattributedWordRate = 0.0, speakerTurnsPerMinute = 0.0,
                duplicatePhraseCount = 0, timestampAnomalyCount = 0
            )
        }

        val paragraphWordCounts = paragraphs.map { it.wordIds.size }
        val speechMs = words.maxOf { it.endMs } - words.minOf { it.startMs }
        val speechMinutes = (speechMs / 60_000.0).coerceAtLeast(1.0 / 60.0)

        return TranscriptQualityReport(
            wordCount = words.size,
            paragraphCount = paragraphs.size,
            utteranceCount = transcript.utterances.size,
            speakerTurnCount = transcript.speakerTurns.size,
            speakerCount = transcript.speakerIds.size,
            meanParagraphWords = if (paragraphs.isEmpty()) 0.0 else paragraphWordCounts.average(),
            fragmentationRate = rate(
                paragraphWordCounts.count { it <= TranscriptQualityReport.FRAGMENT_WORD_THRESHOLD },
                paragraphs.size
            ),
            incompleteSentenceRate = rate(
                paragraphs.count { !UtteranceBuilder.endsSentence(it.text) },
                paragraphs.size
            ),
            highConfidenceAttributionRate = rate(
                words.count { it.attribution == AttributionConfidence.HIGH },
                words.size
            ),
            unattributedWordRate = rate(words.count { it.speakerId == null }, words.size),
            speakerTurnsPerMinute = transcript.speakerTurns.size / speechMinutes,
            duplicatePhraseCount = countDuplicatePhrases(words),
            timestampAnomalyCount = countTimestampAnomalies(words)
        )
    }

    private fun rate(count: Int, total: Int): Double = if (total == 0) 0.0 else count.toDouble() / total

    /**
     * Counts back-to-back repeated runs of [MIN_DUPLICATE_RUN]..[MAX_DUPLICATE_RUN] words.
     *
     * Only *adjacent* repeats count: a phrase legitimately recurring later in a meeting is not a
     * defect, whereas the same run appearing twice in immediate succession is very nearly always an
     * overlap that was not reconciled. Runs of several lengths are checked because the duplicated
     * span is whatever the two decode windows happened to share — "we still need to we still need
     * to" repeats with a period of four, and a fixed three-word probe would miss it entirely.
     */
    internal fun countDuplicatePhrases(words: List<CanonicalWord>): Int {
        if (words.size < MIN_DUPLICATE_RUN * 2) return 0
        val keys = words.map { AsrWindowReconciler.normalize(it.text) }
        var count = 0
        var i = 0
        outer@ while (i + MIN_DUPLICATE_RUN * 2 <= keys.size) {
            // Longest first: a six-word repeat should be reported once, not as two overlapping
            // three-word ones.
            for (run in minOf(MAX_DUPLICATE_RUN, (keys.size - i) / 2) downTo MIN_DUPLICATE_RUN) {
                val a = keys.subList(i, i + run)
                if (a.any { it.isEmpty() }) continue
                if (a == keys.subList(i + run, i + run * 2)) {
                    count++
                    i += run * 2
                    continue@outer
                }
            }
            i++
        }
        return count
    }

    /** Words whose timing is impossible: negative length, or starting meaningfully before the
     * latest point already reached. */
    internal fun countTimestampAnomalies(words: List<CanonicalWord>): Int {
        var anomalies = 0
        // Nullable rather than a sentinel: `Long.MIN_VALUE - tolerance` silently overflows into a
        // large positive number, which made every first word look like an anomaly.
        var previousEnd: Long? = null
        for (word in words) {
            if (word.endMs < word.startMs) anomalies++
            val reached = previousEnd
            if (reached != null && word.startMs < reached - TIMESTAMP_TOLERANCE_MS) anomalies++
            previousEnd = maxOf(reached ?: word.endMs, word.endMs)
        }
        return anomalies
    }

    /** Words legitimately abut and occasionally overlap by a few milliseconds at a window seam. */
    private const val TIMESTAMP_TOLERANCE_MS = 50L
}

/**
 * Serializes a report for [com.example.core.database.MeetingEntity.qualityMetricsJson].
 *
 * Metrics only — this never contains transcript text, which is why it is safe to store alongside
 * the meeting and to include in a diagnostic export.
 */
fun TranscriptQualityReport.toJson(): String = org.json.JSONObject().apply {
    put("wordCount", wordCount)
    put("paragraphCount", paragraphCount)
    put("utteranceCount", utteranceCount)
    put("speakerTurnCount", speakerTurnCount)
    put("speakerCount", speakerCount)
    put("meanParagraphWords", meanParagraphWords)
    put("fragmentationRate", fragmentationRate)
    put("incompleteSentenceRate", incompleteSentenceRate)
    put("highConfidenceAttributionRate", highConfidenceAttributionRate)
    put("unattributedWordRate", unattributedWordRate)
    put("speakerTurnsPerMinute", speakerTurnsPerMinute)
    put("duplicatePhraseCount", duplicatePhraseCount)
    put("timestampAnomalyCount", timestampAnomalyCount)
    put("verdict", verdict.name)
}.toString()
