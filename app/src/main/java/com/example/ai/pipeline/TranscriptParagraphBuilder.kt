package com.example.ai.pipeline

import com.example.core.model.TranscriptSegment
import java.util.UUID

/**
 * Groups the many short, VAD-sized ASR emissions into readable paragraphs.
 *
 * Why this exists: one VAD speech interval becomes exactly one [TranscriptSegment] (see
 * [com.example.ai.asr.SherpaParakeetSpeechRecognizer]), and Silero VAD ends an interval at every
 * pause longer than `SileroVadDetector.MIN_SILENCE_DURATION_SEC`. Natural speech is full of
 * sub-second pauses mid-thought, so the raw output is a stack of one-line fragments — sometimes a
 * few words — rather than anything a person would want to read. That fragmentation also makes
 * diarization noisier downstream: the more separate short spans exist, the more chances there are
 * for one real speaker to get split across several clusters.
 *
 * What it does NOT do: it never merges across a speaker change, never invents or reorders text,
 * and never drops a segment. Merging is purely a re-grouping of consecutive segments whose real
 * timestamps and real speaker assignment already say they belong together — no content is
 * synthesized and nothing is thrown away.
 */
object TranscriptParagraphBuilder {

    /**
     * A pause at least this long is treated as a real paragraph break rather than a breath. Below
     * it, two consecutive same-speaker segments are continuing one thought and get joined.
     */
    const val PARAGRAPH_BREAK_GAP_MS = 1_500L

    /** Hard ceiling on how much wall-clock time one paragraph may span, so a long uninterrupted
     * monologue still breaks into readable blocks (and stays seekable at a useful granularity). */
    const val MAX_PARAGRAPH_DURATION_MS = 45_000L

    /** Hard ceiling on paragraph length in characters, for the same readability reason. */
    const val MAX_PARAGRAPH_CHARS = 700

    /**
     * Merges consecutive segments belonging to the same speaker and separated by less than
     * [PARAGRAPH_BREAK_GAP_MS], subject to the duration/length ceilings above.
     *
     * [segments] is expected in ascending start order (guaranteed by the ASR stage, which walks
     * VAD intervals in order); it is sorted defensively anyway. Segments whose speaker differs —
     * including "one has a speaker, the other doesn't" — are never merged, so a transcript that
     * was never diarized (every `speakerId` null) merges freely, which is the correct behavior for
     * a single-person recording.
     */
    fun buildParagraphs(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        if (segments.size < 2) return segments
        val sorted = segments.sortedBy { it.startMs }

        val paragraphs = mutableListOf<TranscriptSegment>()
        var group = mutableListOf(sorted.first())

        for (segment in sorted.drop(1)) {
            if (canExtend(group, segment)) {
                group.add(segment)
            } else {
                paragraphs += mergeGroup(group)
                group = mutableListOf(segment)
            }
        }
        paragraphs += mergeGroup(group)
        return paragraphs
    }

    private fun canExtend(group: List<TranscriptSegment>, next: TranscriptSegment): Boolean {
        val last = group.last()
        if (last.speakerId != next.speakerId) return false
        if (next.startMs - last.endMs >= PARAGRAPH_BREAK_GAP_MS) return false
        if (next.endMs - group.first().startMs > MAX_PARAGRAPH_DURATION_MS) return false
        val joinedLength = group.sumOf { it.text.length + 1 } + next.text.length
        if (joinedLength > MAX_PARAGRAPH_CHARS) return false
        return true
    }

    private fun mergeGroup(group: List<TranscriptSegment>): TranscriptSegment {
        val first = group.first()
        if (group.size == 1) return first
        val last = group.last()
        return first.copy(
            id = UUID.randomUUID().toString(),
            startMs = first.startMs,
            endMs = last.endMs,
            text = joinText(group.map { it.text }),
            // Averaging is only meaningful if every source segment actually carried a real score;
            // one null anywhere means the paragraph's true confidence is unknown, and an invented
            // number is worse than an honest null.
            confidence = group.map { it.confidence }.takeIf { scores -> scores.all { it != null } }
                ?.let { scores -> scores.filterNotNull().average().toFloat() },
            isUserEdited = group.any { it.isUserEdited }
        )
    }

    /**
     * Joins fragment texts into one paragraph. ASR emits each interval without knowing it was
     * mid-sentence, so a fragment that doesn't end in terminal punctuation and is followed by a
     * lowercase continuation is genuinely one sentence split in two — joined with a plain space.
     * Anything else gets a sentence break. Never rewrites the words themselves.
     */
    private fun joinText(parts: List<String>): String {
        val builder = StringBuilder()
        for (raw in parts) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            if (builder.isEmpty()) {
                builder.append(part)
                continue
            }
            val previousEndsSentence = builder.last() in TERMINAL_PUNCTUATION
            val continuesSentence = !previousEndsSentence && part.firstOrNull()?.isLowerCase() == true
            if (!previousEndsSentence && !continuesSentence) {
                // A capitalized fragment after an unpunctuated one is a new sentence the ASR
                // simply didn't punctuate — add the break it omitted rather than running them
                // together into an unreadable line.
                builder.append('.')
            }
            builder.append(' ').append(part)
        }
        return builder.toString()
    }

    private val TERMINAL_PUNCTUATION = charArrayOf('.', '?', '!', ':', ';', ',', '—')
}
