package com.example.ai.cloud

/** One slice of a recording sent to Gemini as a single transcription request. */
data class AudioChunk(
    val index: Int,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = endMs - startMs
}

/** Tuning for [GeminiChunkPlanner]. */
data class ChunkPlanConfig(
    /**
     * Longest audio sent in one request.
     *
     * Gemini's transcription endpoint constrains audio duration more tightly when speaker
     * diarization and word-level timestamps are enabled, which is exactly the configuration the
     * verbatim pass uses, so this is sized for that stricter case rather than for the permissive
     * one — a chunk plan that works for verbatim works for smart, and one plan for both keeps the
     * two passes describing the same audio.
     */
    val maxChunkMs: Long = 8 * 60 * 1_000L,

    /**
     * Audio each chunk re-reads from the one before it.
     *
     * This is the raw material for *both* cross-chunk reconciliations: overlapping words are how
     * [com.example.ai.transcript.AsrWindowReconciler] finds a join point, and overlapping speech
     * from the same people is how [GlobalSpeakerResolver] works out that chunk 2's SPEAKER_1 is
     * chunk 1's SPEAKER_0. Without it, chunks could only be concatenated, and concatenation is
     * exactly what produces duplicated sentences and speaker identities that reset every few
     * minutes.
     */
    val overlapMs: Long = 20 * 1_000L,

    /** A final chunk shorter than this is folded into its predecessor instead of being uploaded
     * on its own — a few seconds of audio is not enough for diarization to say anything useful. */
    val minTailChunkMs: Long = 30 * 1_000L
) {
    init {
        require(maxChunkMs > 0) { "maxChunkMs must be positive" }
        require(overlapMs in 0 until maxChunkMs) { "overlapMs must be >= 0 and < maxChunkMs" }
    }
}

/**
 * Plans how a recording is cut up for cloud transcription.
 *
 * Pure and deterministic, so the plan can be unit-tested, logged, resumed and compared across runs
 * without uploading anything. Chunk boundaries are decided here and nowhere else, which is what
 * lets the two Gemini passes and both reconcilers agree on what "chunk 3" means.
 */
object GeminiChunkPlanner {

    fun plan(totalDurationMs: Long, config: ChunkPlanConfig = ChunkPlanConfig()): List<AudioChunk> {
        if (totalDurationMs <= 0L) return emptyList()
        if (totalDurationMs <= config.maxChunkMs) {
            return listOf(AudioChunk(0, 0L, totalDurationMs))
        }

        val step = config.maxChunkMs - config.overlapMs
        val bounds = mutableListOf<Pair<Long, Long>>()
        var start = 0L
        while (start < totalDurationMs) {
            val end = minOf(start + config.maxChunkMs, totalDurationMs)
            bounds += start to end
            if (end >= totalDurationMs) break
            start += step
        }

        // A short tail chunk carries too little audio for diarization to be meaningful, and its
        // speakers would then be impossible to resolve against the rest of the recording. Pulling
        // its start back keeps it a full-length chunk; the extra overlap costs nothing, because
        // overlap is reconciled either way.
        val last = bounds.last()
        if (bounds.size > 1 && last.second - last.first < config.minTailChunkMs) {
            bounds[bounds.lastIndex] = maxOf(0L, last.second - config.maxChunkMs) to last.second
        }

        return bounds.mapIndexed { index, (from, to) -> AudioChunk(index, from, to) }
    }

    /** The time range two consecutive chunks share, or null when they do not overlap. */
    fun overlapBetween(earlier: AudioChunk, later: AudioChunk): LongRange? {
        val from = maxOf(earlier.startMs, later.startMs)
        val to = minOf(earlier.endMs, later.endMs)
        return if (to > from) from..to else null
    }
}
