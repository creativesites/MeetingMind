package com.example.ai.transcript

/** A raw diarization turn: who was speaking between two timestamps, before any words are involved. */
data class DiarizationTurn(
    val speakerId: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Maps words onto diarization turns.
 *
 * This replaces the old `reconcileTranscriptWithSpeakers`, which gave every word of an ASR segment
 * to whichever speaker overlapped that segment the most. When a segment straddled a real speaker
 * change — which happens constantly, because people answer without leaving a 700 ms gap first —
 * every word in it was handed to the louder half of the overlap, confidently and with no record
 * that the call had been close. That is the direct cause of "one speaker split into several
 * labels" and "speaker identities flip".
 *
 * Here the unit of attribution is the word, and every attribution carries an
 * [AttributionConfidence]. Nothing is ever silently reassigned: the one smoothing rule below fires
 * only on words the attributor itself already marked as uncertain.
 */
object WordSpeakerAttributor {

    /** A word this far outside every turn is still attached to the nearest one, as LOW. Beyond it,
     * the word honestly gets no speaker at all. */
    private const val NEAREST_TURN_TOLERANCE_MS = 400L

    /** Overlap share of a word's own duration needed for [AttributionConfidence.HIGH]. */
    private const val HIGH_CONFIDENCE_SHARE = 0.8

    /** …and for [AttributionConfidence.MEDIUM]. */
    private const val MEDIUM_CONFIDENCE_SHARE = 0.5

    /** A competing speaker covering more than this share of the word downgrades HIGH to MEDIUM:
     * the word sits on a speaker change and we should say so. */
    private const val CONTESTED_SHARE = 0.2

    /**
     * @param words Chronological word stream from [AsrWindowReconciler].
     * @param turns Acoustic diarization turns. Empty means diarization was unavailable — every word
     *   comes back with a null speaker and [AttributionConfidence.NONE] rather than a fabricated one.
     */
    fun attribute(words: List<CanonicalWord>, turns: List<DiarizationTurn>): List<CanonicalWord> {
        if (words.isEmpty()) return words
        if (turns.isEmpty()) {
            return words.map { it.copy(speakerId = null, attribution = AttributionConfidence.NONE) }
        }
        val sortedTurns = turns.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        if (sortedTurns.isEmpty()) {
            return words.map { it.copy(speakerId = null, attribution = AttributionConfidence.NONE) }
        }

        val attributed = words.map { word -> attributeOne(word, sortedTurns) }
        return smoothIsolatedUncertainWords(attributed)
    }

    private fun attributeOne(word: CanonicalWord, turns: List<DiarizationTurn>): CanonicalWord {
        // A zero-length word (possible when an engine reports a token boundary rather than a
        // span) is measured against a nominal window so it can still be placed.
        val wordDurationMs = maxOf(word.durationMs, 1L)

        val overlapBySpeaker = HashMap<String, Long>()
        for (turn in turns) {
            if (turn.startMs >= word.endMs && turn.startMs - word.endMs > NEAREST_TURN_TOLERANCE_MS) break
            val overlap = minOf(word.endMs, turn.endMs) - maxOf(word.startMs, turn.startMs)
            if (overlap > 0) overlapBySpeaker.merge(turn.speakerId, overlap, Long::plus)
        }

        if (overlapBySpeaker.isEmpty()) {
            val nearest = turns.minByOrNull { distanceMs(word, it) } ?: return word.copy(
                speakerId = null, attribution = AttributionConfidence.NONE
            )
            return if (distanceMs(word, nearest) <= NEAREST_TURN_TOLERANCE_MS) {
                word.copy(speakerId = nearest.speakerId, attribution = AttributionConfidence.LOW)
            } else {
                word.copy(speakerId = null, attribution = AttributionConfidence.NONE)
            }
        }

        val (bestSpeaker, bestOverlap) = overlapBySpeaker.maxByOrNull { it.value }!!
        val bestShare = bestOverlap.toDouble() / wordDurationMs
        val contestedShare = overlapBySpeaker
            .filterKeys { it != bestSpeaker }
            .values.maxOrNull()?.toDouble()?.div(wordDurationMs) ?: 0.0

        val confidence = when {
            bestShare >= HIGH_CONFIDENCE_SHARE && contestedShare <= CONTESTED_SHARE -> AttributionConfidence.HIGH
            bestShare >= MEDIUM_CONFIDENCE_SHARE -> AttributionConfidence.MEDIUM
            else -> AttributionConfidence.LOW
        }
        return word.copy(speakerId = bestSpeaker, attribution = confidence)
    }

    private fun distanceMs(word: CanonicalWord, turn: DiarizationTurn): Long = when {
        word.endMs < turn.startMs -> turn.startMs - word.endMs
        word.startMs > turn.endMs -> word.startMs - turn.endMs
        else -> 0L
    }

    /**
     * The single smoothing rule, kept deliberately narrow.
     *
     * A short run of words that the attributor itself marked LOW, sitting between two confidently
     * attributed runs of the *same* other speaker, is diarization boundary jitter — the embedding
     * model wobbling across a turn edge — not a third participant who spoke two words and vanished.
     * Those words are moved to the surrounding speaker and recorded as MEDIUM, never HIGH: the
     * transcript should still say the evidence was indirect.
     *
     * Everything else is left exactly as attributed. In particular a HIGH- or MEDIUM-attributed
     * word is never reassigned, however isolated it looks — a genuine one-word backchannel
     * ("Yeah.") from another speaker is exactly that shape, and merging it away is how the old
     * pipeline lost short responses.
     */
    internal fun smoothIsolatedUncertainWords(words: List<CanonicalWord>): List<CanonicalWord> {
        if (words.size < 3) return words
        val out = words.toMutableList()

        var i = 0
        while (i < out.size) {
            if (out[i].attribution != AttributionConfidence.LOW) { i++; continue }
            var end = i
            while (end + 1 < out.size && out[end + 1].attribution == AttributionConfidence.LOW) end++
            if (end - i + 1 > MAX_SMOOTHED_RUN_WORDS) { i = end + 1; continue }

            val before = out.getOrNull(i - 1)?.takeIf { it.attribution.isConfident() }
            val after = out.getOrNull(end + 1)?.takeIf { it.attribution.isConfident() }
            if (before != null && after != null && before.speakerId != null && before.speakerId == after.speakerId) {
                for (j in i..end) {
                    out[j] = out[j].copy(speakerId = before.speakerId, attribution = AttributionConfidence.MEDIUM)
                }
            }
            i = end + 1
        }
        return out
    }

    private const val MAX_SMOOTHED_RUN_WORDS = 3

    private fun AttributionConfidence.isConfident(): Boolean =
        this == AttributionConfidence.HIGH || this == AttributionConfidence.MEDIUM
}

/**
 * Groups the attributed word stream into [SpeakerTurn]s.
 *
 * A turn breaks only at a *confident* change of speaker. Words carrying no speaker at all
 * ([AttributionConfidence.NONE]) extend the turn in progress rather than starting one of their
 * own — the turn is a container for a stretch of time, and a word with no diarization evidence
 * does not become evidence of a new speaker by being placed inside someone's turn. Its own
 * `speakerId` stays null, so nothing downstream can mistake containment for attribution.
 */
object SpeakerTurnBuilder {

    /** Below this, a turn made entirely of unconfident words is treated as clustering jitter. */
    private const val MIN_JITTER_TURN_MS = 700L
    private const val MIN_JITTER_TURN_WORDS = 2

    fun build(words: List<CanonicalWord>): List<SpeakerTurn> {
        if (words.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<CanonicalWord>>()
        var current = mutableListOf(words.first())
        var currentSpeaker = words.first().speakerId

        for (word in words.drop(1)) {
            val speaker = word.speakerId
            val isChange = speaker != null && currentSpeaker != null && speaker != currentSpeaker
            val isFirstIdentity = speaker != null && currentSpeaker == null
            if (isChange) {
                groups += current
                current = mutableListOf(word)
                currentSpeaker = speaker
            } else {
                current += word
                if (isFirstIdentity) currentSpeaker = speaker
            }
        }
        groups += current

        val turns = groups.mapIndexed { index, group -> toTurn(index, group) }
        return absorbJitterTurns(turns, words.associateBy { it.id })
    }

    private fun toTurn(index: Int, group: List<CanonicalWord>): SpeakerTurn {
        val attributed = group.filter { it.speakerId != null }
        return SpeakerTurn(
            id = "turn$index",
            speakerId = group.firstNotNullOfOrNull { it.speakerId },
            startMs = group.minOf { it.startMs },
            endMs = group.maxOf { it.endMs },
            wordIds = group.map { it.id },
            confidence = if (attributed.isEmpty()) null else {
                attributed.count { it.attribution == AttributionConfidence.HIGH }.toFloat() / attributed.size
            }
        )
    }

    /**
     * Removes a turn that is short, made only of unconfident words, and bordered on both sides by
     * the same other speaker — the turn-level counterpart of
     * [WordSpeakerAttributor.smoothIsolatedUncertainWords], and subject to the same restraint: a
     * turn containing even one HIGH-attributed word is a real turn and is never absorbed, so
     * genuine short responses survive.
     */
    private fun absorbJitterTurns(turns: List<SpeakerTurn>, wordsById: Map<String, CanonicalWord>): List<SpeakerTurn> {
        if (turns.size < 3) return turns
        val keep = turns.toMutableList()
        var index = 1
        while (index < keep.size - 1) {
            val turn = keep[index]
            val words = turn.wordIds.mapNotNull { wordsById[it] }
            val isJitter = turn.endMs - turn.startMs < MIN_JITTER_TURN_MS &&
                words.size <= MIN_JITTER_TURN_WORDS &&
                words.none { it.attribution == AttributionConfidence.HIGH } &&
                keep[index - 1].speakerId != null &&
                keep[index - 1].speakerId == keep[index + 1].speakerId &&
                keep[index - 1].speakerId != turn.speakerId
            if (!isJitter) { index++; continue }

            val previous = keep[index - 1]
            val next = keep[index + 1]
            keep[index - 1] = previous.copy(
                endMs = next.endMs,
                wordIds = previous.wordIds + turn.wordIds + next.wordIds,
                confidence = weightedConfidence(listOf(previous, turn, next))
            )
            keep.removeAt(index + 1)
            keep.removeAt(index)
        }
        return keep.mapIndexed { i, turn -> turn.copy(id = "turn$i") }
    }

    private fun weightedConfidence(turns: List<SpeakerTurn>): Float? {
        val scored = turns.filter { it.confidence != null }
        if (scored.isEmpty()) return null
        val totalWords = scored.sumOf { it.wordIds.size }
        if (totalWords == 0) return null
        return scored.sumOf { (it.confidence!! * it.wordIds.size).toDouble() }.toFloat() / totalWords
    }
}
