package com.example.ai.cloud

import com.example.ai.transcript.CanonicalWord

/**
 * One chunk's transcription result, with the speaker labels that chunk's own diarization produced.
 *
 * Those labels are **local**: chunk 1's `SPEAKER_0` and chunk 2's `SPEAKER_0` are two unrelated
 * cluster indices that happen to share a name. Treating them as the same person is the classic way
 * a long cloud-transcribed recording ends up with speaker identities that reset every few minutes.
 */
data class ChunkTranscription(
    val chunk: AudioChunk,
    /** Words with timestamps absolute to the whole recording, carrying chunk-local speaker ids. */
    val words: List<CanonicalWord>
)

/** The mapping from one chunk's local speaker ids to recording-wide identities. */
data class GlobalSpeakerMapping(
    /** `chunkIndex -> (localSpeakerId -> globalSpeakerId)`. */
    val byChunk: Map<Int, Map<String, String>>,
    /** Every global identity the resolver created, in first-appearance order. */
    val globalSpeakerIds: List<String>
) {
    fun resolve(chunkIndex: Int, localSpeakerId: String?): String? =
        localSpeakerId?.let { byChunk[chunkIndex]?.get(it) }
}

/**
 * Resolves per-chunk speaker labels into recording-wide identities using the audio the chunks share.
 *
 * The evidence is the overlap region: both chunks transcribed the same seconds of audio, so
 * whoever chunk 1 heard at 7:40 is whoever chunk 2 heard at 7:40. Matching is by **time overlap
 * between the two chunks' own speaking intervals** inside that region — a physical fact about when
 * each voice was active — rather than by text similarity, which would confuse two people who said
 * similar things, or by label order, which is not meaningful at all.
 *
 * A local speaker with no usable evidence becomes a **new global identity**. That is the
 * conservative choice: inventing a link between two speakers is unrecoverable, while an extra
 * identity is visible, mergeable by the user, and honest about what was actually established.
 */
object GlobalSpeakerResolver {

    /** Minimum shared speaking time before two local speakers are considered the same person. */
    private const val MIN_EVIDENCE_MS = 1_500L

    /** The runner-up must be well behind the winner, or the evidence is too ambiguous to act on —
     * two people talking over each other in the overlap region must not produce a confident link. */
    private const val AMBIGUITY_RATIO = 0.6

    fun resolve(chunks: List<ChunkTranscription>): GlobalSpeakerMapping {
        if (chunks.isEmpty()) return GlobalSpeakerMapping(emptyMap(), emptyList())

        val ordered = chunks.sortedBy { it.chunk.index }
        val byChunk = LinkedHashMap<Int, MutableMap<String, String>>()
        val globalIds = mutableListOf<String>()

        fun newGlobalId(): String = "global_speaker_${globalIds.size}".also { globalIds += it }

        // The first chunk defines the initial identities; there is nothing earlier to match against.
        val first = ordered.first()
        byChunk[first.chunk.index] = localSpeakersOf(first)
            .associateWith { newGlobalId() }
            .toMutableMap()

        for (i in 1 until ordered.size) {
            val current = ordered[i]
            val previous = ordered[i - 1]
            val mapping = mutableMapOf<String, String>()
            val overlap = GeminiChunkPlanner.overlapBetween(previous.chunk, current.chunk)

            val claimed = mutableSetOf<String>()
            if (overlap != null) {
                val previousIntervals = speakingIntervals(previous, overlap)
                val currentIntervals = speakingIntervals(current, overlap)

                for ((localId, intervals) in currentIntervals) {
                    val scores = previousIntervals
                        .mapValues { (_, theirs) -> sharedMs(intervals, theirs) }
                        .filterValues { it >= MIN_EVIDENCE_MS }
                        .entries.sortedByDescending { it.value }

                    val best = scores.firstOrNull() ?: continue
                    val runnerUp = scores.getOrNull(1)?.value ?: 0L
                    if (runnerUp > best.value * AMBIGUITY_RATIO) continue

                    val globalId = byChunk[previous.chunk.index]?.get(best.key) ?: continue
                    // Two local speakers cannot both be the same person: whoever has the stronger
                    // evidence keeps the link and the other becomes a new identity.
                    if (!claimed.add(globalId)) continue
                    mapping[localId] = globalId
                }
            }

            for (localId in localSpeakersOf(current)) {
                mapping.getOrPut(localId) { newGlobalId() }
            }
            byChunk[current.chunk.index] = mapping
        }

        return GlobalSpeakerMapping(byChunk, globalIds)
    }

    /** Applies a mapping, producing one word stream with recording-wide speaker identities. */
    fun applyMapping(chunks: List<ChunkTranscription>, mapping: GlobalSpeakerMapping): List<ChunkTranscription> =
        chunks.map { chunk ->
            chunk.copy(
                words = chunk.words.map { word ->
                    word.copy(speakerId = mapping.resolve(chunk.chunk.index, word.speakerId))
                }
            )
        }

    private fun localSpeakersOf(chunk: ChunkTranscription): List<String> =
        chunk.words.mapNotNull { it.speakerId }.distinct()

    /**
     * When each local speaker was actually talking, restricted to the overlap region. Consecutive
     * words from one speaker are merged into an interval so that the comparison is about voice
     * activity in time, not about how many words each transcription happened to emit.
     */
    private fun speakingIntervals(
        chunk: ChunkTranscription,
        overlap: LongRange
    ): Map<String, List<LongRange>> {
        val result = mutableMapOf<String, MutableList<LongRange>>()
        for (word in chunk.words.sortedBy { it.startMs }) {
            val speaker = word.speakerId ?: continue
            val from = maxOf(word.startMs, overlap.first)
            val to = minOf(word.endMs, overlap.last)
            if (to <= from) continue
            val intervals = result.getOrPut(speaker) { mutableListOf() }
            val last = intervals.lastOrNull()
            if (last != null && from - last.last <= MERGE_GAP_MS) {
                intervals[intervals.lastIndex] = last.first..maxOf(last.last, to)
            } else {
                intervals += from..to
            }
        }
        return result
    }

    private fun sharedMs(a: List<LongRange>, b: List<LongRange>): Long {
        var total = 0L
        for (x in a) {
            for (y in b) {
                val from = maxOf(x.first, y.first)
                val to = minOf(x.last, y.last)
                if (to > from) total += to - from
            }
        }
        return total
    }

    /** Words closer together than this are one continuous stretch of the same voice. */
    private const val MERGE_GAP_MS = 400L
}
