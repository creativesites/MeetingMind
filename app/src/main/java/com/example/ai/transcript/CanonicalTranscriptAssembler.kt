package com.example.ai.transcript

import com.example.ai.pipeline.DeterministicTranscriptStructureEngine
import com.example.ai.pipeline.TranscriptStructureEngine
import com.example.core.model.RecordingType
import com.example.core.model.TranscriptSegment
import com.example.core.model.TranscriptWord

/**
 * Assembles a [CanonicalTranscript] from a word stream and diarization turns, and projects it back
 * into the `TranscriptSegment` list the rest of MeetingMind already speaks.
 *
 * ### Why there is a projection rather than a rewrite
 *
 * `TranscriptSegment` is the unit of persistence, UI rendering, search, embeddings, playback sync,
 * export, user editing (split/merge/speaker reassignment) and intelligence provenance — roughly
 * forty files depend on it. Replacing it wholesale would have meant rewriting every one of those
 * to fix a defect that lives entirely in how segments are *produced*. So the canonical transcript
 * becomes the source of truth and the segment list becomes a view of it: each segment is exactly
 * one [CanonicalParagraph], carrying its words (with ids, speakers and confidence) and its source
 * utterance ids. Existing screens get better content without changing a line.
 *
 * ### Paragraphs
 *
 * Paragraph grouping is delegated to the existing [DeterministicTranscriptStructureEngine] rather
 * than reimplemented: its signal set (speaker identity, recording-type merge policy, pause length,
 * sentence completion, trailing conjunctions, duration and character ceilings) is sound and
 * well-tested. What changes is what it is asked to group. It used to receive VAD fragments, whose
 * boundaries were acoustic, so the only paragraph boundaries available to it were pause positions.
 * It now receives [Utterance]s, whose boundaries were decided with the words, the punctuation and
 * the speaker in hand — so a paragraph can break where a person would break it.
 */
object CanonicalTranscriptAssembler {

    /**
     * @param words The reconciled word stream, already speaker-attributed by
     *   [WordSpeakerAttributor].
     * @param turns Acoustic diarization turns; used only for the [CanonicalTranscript.speakerTurns]
     *   layer's timing. Word attribution has already happened and is not redone here.
     * @param speakerNameFor Resolves a speaker id to its display name. Returning null is legitimate
     *   and means "unnamed" — never a fabricated name.
     */
    fun assemble(
        meetingId: String,
        words: List<CanonicalWord>,
        recordingType: RecordingType,
        singleSpeakerMode: Boolean,
        metadata: TranscriptMetadata,
        speakerNameFor: (String) -> String? = { null },
        utteranceConfig: UtteranceConfig = UtteranceConfig(),
        structureEngine: TranscriptStructureEngine = DeterministicTranscriptStructureEngine
    ): CanonicalTranscript {
        if (words.isEmpty()) {
            return CanonicalTranscript(meetingId, emptyList(), emptyList(), emptyList(), emptyList(), metadata)
        }

        val ordered = words.sortedBy { it.startMs }
        val speakerTurns = SpeakerTurnBuilder.build(ordered)
        val utterances = UtteranceBuilder.build(speakerTurns, ordered, utteranceConfig)

        val fragments = utterances.map { it.toFragment(meetingId, ordered, speakerNameFor) }
        val structured = structureEngine.structure(fragments, recordingType, singleSpeakerMode)
        val paragraphs = structured.map { it.toParagraph(utterances) }

        return CanonicalTranscript(
            meetingId = meetingId,
            words = ordered,
            speakerTurns = speakerTurns,
            utterances = utterances,
            paragraphs = paragraphs,
            metadata = metadata
        )
    }

    /**
     * Renders the canonical transcript as the segment list the persistence and UI layers consume.
     * One segment per paragraph, with full downward provenance: [TranscriptSegment.words] carries
     * the real [CanonicalWord]s (ids included) and [TranscriptSegment.sourceSegmentIds] carries the
     * utterance ids the paragraph was built from.
     */
    fun projectToSegments(transcript: CanonicalTranscript): List<TranscriptSegment> {
        val wordsById = transcript.words.associateBy { it.id }
        return transcript.paragraphs.map { paragraph ->
            TranscriptSegment(
                id = paragraph.id,
                meetingId = transcript.meetingId,
                speakerId = paragraph.speakerId,
                speakerName = paragraph.speakerName,
                startMs = paragraph.startMs,
                endMs = paragraph.endMs,
                text = paragraph.text,
                confidence = paragraph.confidence,
                sourceSegmentIds = paragraph.utteranceIds,
                words = paragraph.wordIds.mapNotNull { wordsById[it]?.toDomainWord() }
            )
        }
    }

    private fun Utterance.toFragment(
        meetingId: String,
        words: List<CanonicalWord>,
        speakerNameFor: (String) -> String?
    ): TranscriptSegment {
        val wanted = wordIds.toSet()
        return TranscriptSegment(
            id = id,
            meetingId = meetingId,
            speakerId = speakerId,
            speakerName = speakerId?.let(speakerNameFor),
            startMs = startMs,
            endMs = endMs,
            text = text,
            confidence = null,
            sourceSegmentIds = listOf(id),
            words = words.filter { it.id in wanted }.map { it.toDomainWord() }
        )
    }

    /**
     * Rebuilds a [CanonicalParagraph] from a structured segment. The structure engine preserves
     * `sourceSegmentIds` (the utterance ids) and `words` across a merge, so both provenance chains
     * survive grouping without this layer having to re-derive them.
     */
    private fun TranscriptSegment.toParagraph(utterances: List<Utterance>): CanonicalParagraph {
        val utteranceIds = sourceSegmentIds.ifEmpty { listOf(id) }
        val sources = utterances.filter { it.id in utteranceIds.toSet() }
        return CanonicalParagraph(
            id = id,
            speakerId = speakerId,
            speakerName = speakerName,
            startMs = startMs,
            endMs = endMs,
            text = text,
            wordIds = if (words.isNotEmpty()) words.map { it.id } else sources.flatMap { it.wordIds },
            utteranceIds = utteranceIds,
            confidence = confidence
        )
    }
}

/** The persisted projection of a canonical word — see [TranscriptWord]'s own doc. */
fun CanonicalWord.toDomainWord(): TranscriptWord = TranscriptWord(
    text = text,
    startMs = startMs,
    endMs = endMs,
    id = id,
    speakerId = speakerId,
    attribution = attribution.name,
    confidence = confidence,
    source = source.name
)

/** The inverse of [toDomainWord], for reading a persisted transcript back into the canonical layer. */
fun TranscriptWord.toCanonicalWord(fallbackId: String): CanonicalWord = CanonicalWord(
    id = id.ifEmpty { fallbackId },
    text = text,
    startMs = startMs,
    endMs = endMs,
    speakerId = speakerId,
    attribution = attribution?.let { name ->
        AttributionConfidence.entries.firstOrNull { it.name == name }
    } ?: AttributionConfidence.NONE,
    confidence = confidence,
    source = source?.let { name ->
        TranscriptSource.entries.firstOrNull { it.name == name }
    } ?: TranscriptSource.LOCAL_ASR
)
