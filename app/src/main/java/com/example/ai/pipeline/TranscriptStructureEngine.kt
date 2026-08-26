package com.example.ai.pipeline

import com.example.core.model.RecordingType
import com.example.core.model.TranscriptMergePolicy
import com.example.core.model.TranscriptSegment
import java.util.UUID

/**
 * Turns raw, VAD-fragmented ASR output into a professional, readable transcript — the boundary
 * between "what the ASR engine happened to chunk audio into" and "what a person would recognize
 * as a paragraph." Replaces the old `TranscriptParagraphBuilder`, whose single fixed gap threshold
 * (with two hard caps) couldn't express recording-type-aware behavior, single-speaker-mode
 * leniency, or sentence-completion awareness without turning into an unreviewable pile of ad hoc
 * conditionals — this interface exists so that logic has a real home and each signal it uses can
 * be tested in isolation.
 *
 * What this engine is emphatically NOT: it never invents, reorders, or drops a single word of the
 * real ASR text. Every output segment's text is the exact concatenation of the input fragments it
 * was built from (only whitespace/terminal-punctuation may be added between fragments, mirroring
 * the join logic this replaces) — see [DeterministicTranscriptStructureEngine.joinFragmentTexts].
 */
interface TranscriptStructureEngine {
    /**
     * @param segments Speaker-labeled (or, if diarization was skipped, still-speakerless) ASR
     *   fragments, any order.
     * @param recordingType Selects the merge policy — see [RecordingType.transcriptMergePolicy].
     * @param singleSpeakerMode True when diarization was skipped for a confirmed single speaker
     *   (see [MeetingProcessingPipeline]). Boosts gap tolerance on top of the recording type's own
     *   policy and ignores speakerId as a boundary signal, since every fragment already carries no
     *   speakerId in this mode — natural pauses alone must never fragment a solo recording.
     */
    fun structure(
        segments: List<TranscriptSegment>,
        recordingType: RecordingType,
        singleSpeakerMode: Boolean
    ): List<TranscriptSegment>
}

/**
 * The only implementation: purely rule-based, no model involved (per the standing "make the
 * deterministic layer excellent first" constraint). Decides whether to extend the current
 * paragraph with the next fragment using every signal a person would actually use when reading a
 * transcript — speaker identity, recording type, pause length, sentence punctuation, incomplete-
 * sentence wording, fragment length — never gap duration alone.
 */
object DeterministicTranscriptStructureEngine : TranscriptStructureEngine {

    override fun structure(
        segments: List<TranscriptSegment>,
        recordingType: RecordingType,
        singleSpeakerMode: Boolean
    ): List<TranscriptSegment> {
        if (segments.isEmpty()) return segments
        val sorted = segments.sortedBy { it.startMs }
        if (sorted.size == 1) return listOf(sorted.first().withSelfSourceIds())

        val policy = recordingType.transcriptMergePolicy().let { base ->
            if (singleSpeakerMode) base.boostedForSingleSpeaker() else base
        }

        val paragraphs = mutableListOf<TranscriptSegment>()
        var group = mutableListOf(sorted.first())

        for (segment in sorted.drop(1)) {
            if (canExtend(policy, group, segment, singleSpeakerMode)) {
                group.add(segment)
            } else {
                paragraphs += mergeGroup(group)
                group = mutableListOf(segment)
            }
        }
        paragraphs += mergeGroup(group)
        return paragraphs
    }

    /**
     * The core decision, re-evaluated for every candidate fragment against the paragraph being
     * built so far:
     *
     * 1. A hand-correction is never silently absorbed into — or merged away by — a structuring
     *    pass; it always starts/ends its own paragraph boundary.
     * 2. A genuine speaker change always breaks the paragraph, UNLESS [singleSpeakerMode] is true
     *    (in which case every fragment already carries no speakerId, so there is no signal to read
     *    — a solo recording is one continuous stream by definition, not by boundary-guessing).
     * 3. The pause since the paragraph's last fragment is compared against one of two thresholds:
     *    the policy's base [TranscriptMergePolicy.maxGapMs] if the accumulated text already reads
     *    as a complete thought, or the more generous [TranscriptMergePolicy.extendedGapMs] if it
     *    doesn't (no terminal punctuation, a trailing conjunction/preposition, or either fragment
     *    being a one/two-word sliver — all real signs the speaker is still forming the sentence,
     *    not signs a pause is "long" in some abstract sense).
     * 4. Two hard ceilings — paragraph duration and character length — apply regardless of how
     *    strong the merge signal is, so this never blindly merges an entire recording into one
     *    block.
     */
    private fun canExtend(
        policy: TranscriptMergePolicy,
        group: List<TranscriptSegment>,
        next: TranscriptSegment,
        singleSpeakerMode: Boolean
    ): Boolean {
        val last = group.last()
        if (last.isUserEdited || next.isUserEdited) return false
        if (!singleSpeakerMode && last.speakerId != next.speakerId) return false

        val gapMs = next.startMs - last.endMs
        val accumulatedText = joinFragmentTexts(group.map { it.text })
        val thoughtStillOpen = !endsWithTerminalPunctuation(accumulatedText) ||
            endsWithIncompleteSentenceCue(accumulatedText) ||
            isVeryShortFragment(last.text) ||
            isVeryShortFragment(next.text)
        val effectiveMaxGapMs = if (thoughtStillOpen) policy.extendedGapMs else policy.maxGapMs
        if (gapMs >= effectiveMaxGapMs) return false

        if (next.endMs - group.first().startMs > policy.maxParagraphDurationMs) return false
        val joinedLength = accumulatedText.length + 1 + next.text.length
        if (joinedLength > policy.maxParagraphChars) return false

        return true
    }

    private fun mergeGroup(group: List<TranscriptSegment>): TranscriptSegment {
        val first = group.first()
        if (group.size == 1) return first.withSelfSourceIds()
        val last = group.last()
        return first.copy(
            id = UUID.randomUUID().toString(),
            startMs = first.startMs,
            endMs = last.endMs,
            text = joinFragmentTexts(group.map { it.text }),
            // Averaging is only meaningful if every source segment actually carried a real score;
            // one null anywhere means the paragraph's true confidence is unknown, and an invented
            // number is worse than an honest null.
            confidence = group.map { it.confidence }.takeIf { scores -> scores.all { it != null } }
                ?.let { scores -> scores.filterNotNull().average().toFloat() },
            // canExtend already refuses to merge across a user-edited fragment on either side, so
            // a multi-fragment group here is guaranteed to contain none.
            isUserEdited = false,
            // Any prior cached cleanup was computed against different (pre-merge) text and is
            // stale the moment structuring changes what a paragraph actually contains.
            cleanedText = null,
            sourceSegmentIds = group.flatMap { it.sourceSegmentIds.ifEmpty { listOf(it.id) } }
        )
    }

    private fun TranscriptSegment.withSelfSourceIds(): TranscriptSegment =
        if (sourceSegmentIds.isNotEmpty()) this else copy(sourceSegmentIds = listOf(id))

    /**
     * Joins fragment texts into one paragraph. ASR emits each interval without knowing it was
     * mid-sentence, so a fragment that doesn't end in terminal punctuation and is followed by a
     * lowercase continuation is genuinely one sentence split in two — joined with a plain space.
     * Anything else gets a sentence break. Never rewrites the words themselves.
     */
    internal fun joinFragmentTexts(parts: List<String>): String {
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

    private fun endsWithTerminalPunctuation(text: String): Boolean {
        val trimmed = text.trimEnd()
        return trimmed.isNotEmpty() && trimmed.last() in TERMINAL_PUNCTUATION
    }

    /**
     * A trailing conjunction, preposition, or article is strong evidence the speaker's sentence
     * isn't actually finished, whatever the raw ASR text's punctuation (or lack of it) suggests —
     * "so we need to..." trailing into a pause is someone still mid-thought, not a paragraph break.
     */
    private fun endsWithIncompleteSentenceCue(text: String): Boolean {
        val lastWord = text.trim().substringAfterLast(' ').trimEnd(*TERMINAL_PUNCTUATION, ',').lowercase()
        return lastWord.isNotEmpty() && lastWord in INCOMPLETE_SENTENCE_TRAILING_WORDS
    }

    /** A one- or two-word fragment is far more likely to be a sliver of a larger thought (a
     * one-word ASR emission, a trailing "so", a leading "um") than a complete transcript block on
     * its own — treated as evidence to extend the gap tolerance, in either direction of a pause. */
    private fun isVeryShortFragment(text: String): Boolean =
        text.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }.size <= 2

    private val TERMINAL_PUNCTUATION = charArrayOf('.', '?', '!')
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val INCOMPLETE_SENTENCE_TRAILING_WORDS = setOf(
        "and", "but", "or", "so", "because", "if", "when", "while", "that", "which",
        "the", "a", "an", "to", "of", "in", "on", "with", "for", "as"
    )
}

/**
 * A confirmed solo recording is one continuous stream by definition — natural thinking pauses
 * must not read as transcript boundaries just because no diarization ran to say otherwise. Only
 * widens the gap tolerances (never the paragraph-length ceilings, which stay the recording type's
 * own — "don't blindly merge indefinitely" applies here too); a type whose own policy is already
 * more generous than this floor (Idea, Voice Memo, Journal, Dictation, Research) is unaffected.
 */
internal fun TranscriptMergePolicy.boostedForSingleSpeaker(): TranscriptMergePolicy = copy(
    maxGapMs = maxOf(maxGapMs, SINGLE_SPEAKER_MIN_GAP_MS),
    extendedGapMs = maxOf(extendedGapMs, SINGLE_SPEAKER_MIN_EXTENDED_GAP_MS)
)

private const val SINGLE_SPEAKER_MIN_GAP_MS = 4_000L
private const val SINGLE_SPEAKER_MIN_EXTENDED_GAP_MS = 9_000L
