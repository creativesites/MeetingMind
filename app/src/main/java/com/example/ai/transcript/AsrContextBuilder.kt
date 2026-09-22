package com.example.ai.transcript

/**
 * How speech regions are combined into ASR decode windows.
 *
 * The defaults are a starting point for benchmarking, not a claim of optimality — every value is
 * exposed so the benchmark suite can sweep them. See docs/TRANSCRIPTION_OVERHAUL.md.
 */
data class AsrWindowConfig(
    /** Preferred decode length. Long enough that a sentence and its subordinate clause land in
     * one decode; short enough to stay well inside Parakeet's stable attention span. */
    val targetWindowMs: Long = 25_000L,

    /** Hard ceiling on one decode. */
    val maxWindowMs: Long = 30_000L,

    /** How much audio a window re-reads from the previous one. The duplicated words this produces
     * are removed by [AsrWindowReconciler]; the point is that no word is ever decoded with silence
     * on both sides of it just because a window boundary fell there. */
    val overlapMs: Long = 3_000L,

    /**
     * The longest silence that may sit *inside* one decode window.
     *
     * This is the single most important value in the whole structural change. Below it, a pause is
     * treated as what it usually is — someone thinking mid-sentence — and the words on both sides
     * reach the ASR engine together. Above it, the pause is long enough to be a genuine break in
     * the audio and there is nothing to gain from decoding across it.
     *
     * It is emphatically NOT a sentence or paragraph threshold: a window boundary is an acoustic
     * decision only, and the utterance/paragraph layers are free to put boundaries anywhere.
     */
    val maxInternalGapMs: Long = 2_000L,

    /** Windows shorter than this are merged into their neighbour rather than decoded alone — a
     * half-second of audio gives the model almost no context. */
    val minWindowMs: Long = 1_000L,

    /** Audio kept on each side of a window's speech content, so the engine hears a word's onset
     * and release rather than a hard cut through it. */
    val paddingMs: Long = 200L
) {
    init {
        require(targetWindowMs in 1..maxWindowMs) { "targetWindowMs must be > 0 and <= maxWindowMs" }
        require(overlapMs >= 0 && overlapMs < targetWindowMs) { "overlapMs must be >= 0 and < targetWindowMs" }
    }
}

/**
 * Turns VAD speech regions into ASR decode windows.
 *
 * This component exists because of the single defect that caused most of MeetingMind's transcript
 * fragmentation: the old pipeline decoded **one VAD region per ASR call and emitted one transcript
 * segment per region**, so every breath the speaker took was simultaneously an acoustic context
 * boundary and a transcript boundary. A sentence split by a 700 ms pause was decoded as two
 * unrelated utterances — costing real recognition accuracy, not just formatting.
 *
 * Here, VAD only says *where speech is*. This builder decides *what the ASR engine hears at once*,
 * and nothing about its output constrains where a sentence or paragraph may end.
 */
object AsrContextBuilder {

    /**
     * @param regions VAD speech regions, any order. Empty means "VAD unavailable or found
     *   nothing" — the caller's audio is then windowed end to end rather than skipped, so a
     *   missing VAD model degrades transcription quality but never silences it.
     * @param totalDurationMs Length of the recording; every window is clamped to it.
     */
    fun buildWindows(
        regions: List<SpeechRegion>,
        totalDurationMs: Long,
        config: AsrWindowConfig = AsrWindowConfig()
    ): List<AsrWindow> {
        if (totalDurationMs <= 0L) return emptyList()

        val spans = if (regions.isEmpty()) {
            listOf(0L to totalDurationMs)
        } else {
            mergeIntoSpans(regions, config.maxInternalGapMs)
        }

        val raw = mutableListOf<Pair<Long, Long>>()
        for ((spanStart, spanEnd) in spans) {
            val paddedStart = (spanStart - config.paddingMs).coerceAtLeast(0L)
            val paddedEnd = (spanEnd + config.paddingMs).coerceAtMost(totalDurationMs)
            if (paddedEnd <= paddedStart) continue
            raw += splitSpan(paddedStart, paddedEnd, config)
        }

        val merged = absorbUndersizedWindows(raw, config.minWindowMs)
        return merged.mapIndexed { index, (start, end) -> AsrWindow(index, start, end) }
    }

    /**
     * Groups regions separated by no more than [maxInternalGapMs] into one contextual span. This is
     * where "…move the deadline to Friday" and "…because we still need to finish the API
     * integration" become a single acoustic unit instead of two.
     *
     * Overlapping or out-of-order regions are handled by sorting and taking a running maximum end,
     * so a VAD implementation that emits overlapping regions can never produce a span that goes
     * backwards in time.
     */
    internal fun mergeIntoSpans(regions: List<SpeechRegion>, maxInternalGapMs: Long): List<Pair<Long, Long>> {
        val sorted = regions.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        if (sorted.isEmpty()) return emptyList()

        val spans = mutableListOf<Pair<Long, Long>>()
        var start = sorted.first().startMs
        var end = sorted.first().endMs
        for (region in sorted.drop(1)) {
            if (region.startMs - end <= maxInternalGapMs) {
                end = maxOf(end, region.endMs)
            } else {
                spans += start to end
                start = region.startMs
                end = region.endMs
            }
        }
        spans += start to end
        return spans
    }

    /**
     * Cuts one span into overlapping windows. A span that already fits in [AsrWindowConfig.maxWindowMs]
     * is one window — the common case for ordinary conversational turns. A longer one advances by
     * `targetWindowMs - overlapMs` each step, so consecutive windows share exactly `overlapMs` of
     * audio.
     */
    private fun splitSpan(spanStart: Long, spanEnd: Long, config: AsrWindowConfig): List<Pair<Long, Long>> {
        if (spanEnd - spanStart <= config.maxWindowMs) return listOf(spanStart to spanEnd)

        val step = config.targetWindowMs - config.overlapMs
        val windows = mutableListOf<Pair<Long, Long>>()
        var start = spanStart
        while (start < spanEnd) {
            val end = minOf(start + config.targetWindowMs, spanEnd)
            windows += start to end
            if (end >= spanEnd) break
            start += step
        }
        // The final window can end up very short when the span length lands just past a step
        // boundary; pulling its start back keeps it a full-length decode rather than a sliver,
        // and the extra overlap is removed by the reconciler like any other.
        val last = windows.last()
        if (last.second - last.first < config.targetWindowMs && windows.size > 1) {
            windows[windows.lastIndex] = maxOf(spanStart, last.second - config.targetWindowMs) to last.second
        }
        return windows
    }

    /** Folds a too-short window into the window before it (or after it, for the first one). */
    private fun absorbUndersizedWindows(windows: List<Pair<Long, Long>>, minWindowMs: Long): List<Pair<Long, Long>> {
        if (windows.size <= 1) return windows
        val out = mutableListOf<Pair<Long, Long>>()
        for (window in windows) {
            val tooShort = window.second - window.first < minWindowMs
            if (tooShort && out.isNotEmpty()) {
                val prev = out.removeAt(out.lastIndex)
                out += prev.first to maxOf(prev.second, window.second)
            } else {
                out += window
            }
        }
        // A too-short first window can only be fixed by merging forward.
        if (out.size > 1 && out.first().second - out.first().first < minWindowMs) {
            val first = out.removeAt(0)
            val second = out.removeAt(0)
            out.add(0, first.first to second.second)
        }
        return out
    }
}
