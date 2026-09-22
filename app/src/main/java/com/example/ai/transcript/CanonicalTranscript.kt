package com.example.ai.transcript

/**
 * The canonical transcript layer.
 *
 * MeetingMind's transcript is built bottom-up from words and time, never top-down from whatever
 * the audio happened to be chunked into:
 *
 * ```
 * audio -> SpeechRegion -> AsrWindow -> CanonicalWord -> SpeakerTurn -> Utterance -> paragraph
 * ```
 *
 * The rule every type in this file exists to enforce: **a word is the smallest trustworthy
 * transcript unit.** Everything above it (turns, utterances, paragraphs) is a *view* derived from
 * words plus timestamps plus speaker attribution, and every view records which words it was
 * derived from, so an action item can always be traced back to the audio that produced it.
 *
 * Nothing in this package touches Android or a native library — it is all pure data and pure
 * functions, so the entire structural layer is unit-testable on the JVM.
 */

/** Which engine produced a piece of transcript. Recorded per word so a fused transcript can say
 * honestly which layer each word came from. */
enum class TranscriptSource {
    /** On-device ASR (Parakeet TDT via sherpa-onnx). */
    LOCAL_ASR,

    /** Gemini transcription, verbatim mode — the fidelity layer (timestamps + diarization). */
    GEMINI_VERBATIM,

    /** Gemini transcription, smart mode — the readability layer. Never a timestamp source. */
    GEMINI_SMART,

    /** A correction typed by the user. Always wins over every engine. */
    USER_EDIT
}

/**
 * How much a word's speaker attribution can be trusted.
 *
 * Kept explicit, and never collapsed into "we picked someone", because a silently wrong speaker
 * is worse than a visibly uncertain one: the UI can render LOW differently, the intelligence layer
 * can decline to name an owner from a LOW-attributed word, and the quality evaluator can measure
 * how much of a transcript rests on guesses.
 */
enum class AttributionConfidence {
    /** The word sits well inside exactly one diarization turn. */
    HIGH,

    /** The word is mostly inside one turn, but a competing turn also covers part of it. */
    MEDIUM,

    /** The word only clips a turn, or was attached to the nearest turn across a small gap. */
    LOW,

    /** No diarization evidence at all. [CanonicalWord.speakerId] is null in this case. */
    NONE
}

/**
 * An acoustic region of speech, as reported by VAD.
 *
 * This answers exactly one question — *where is speech?* — and deliberately does not answer
 * "where does a sentence end". Nothing downstream may treat a region boundary as a transcript
 * boundary; regions are grouped into [AsrWindow]s first (see [AsrContextBuilder]).
 */
data class SpeechRegion(
    val startMs: Long,
    val endMs: Long,
    /** Null when the VAD engine reports a binary boundary rather than a score (Silero does). */
    val confidence: Float? = null
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * One contiguous span of audio handed to the ASR engine as a single decode.
 *
 * Windows deliberately overlap ([AsrWindowConfig.overlapMs]) so that a word spoken across a window
 * boundary is decoded with real context on at least one side; the duplicate text that overlap
 * produces is removed deterministically afterwards by [AsrWindowReconciler].
 */
data class AsrWindow(
    val index: Int,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * The canonical low-level transcript unit: one word, when it was said, who said it, and which
 * engine heard it.
 *
 * [id] is unique within one transcript and is what every higher layer stores for provenance.
 */
data class CanonicalWord(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val speakerId: String? = null,
    val attribution: AttributionConfidence = AttributionConfidence.NONE,
    /** Null when the engine gives no per-word score. sherpa-onnx genuinely gives none — this is
     * left null there rather than filled with an invented number. */
    val confidence: Float? = null,
    val source: TranscriptSource = TranscriptSource.LOCAL_ASR
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * A contiguous stretch of one speaker talking, derived from [CanonicalWord]s — never from VAD or
 * ASR segment boundaries.
 *
 * [speakerId] may be null for a run of words no diarization evidence covered; that is an honest
 * "we don't know who this was", not a speaker.
 */
data class SpeakerTurn(
    val id: String,
    val speakerId: String?,
    val startMs: Long,
    val endMs: Long,
    val wordIds: List<String>,
    /** Share of this turn's words attributed with [AttributionConfidence.HIGH], 0f..1f. Null when
     * the turn has no attributed words at all. */
    val confidence: Float? = null
)

/**
 * A sentence-sized unit within one speaker turn: the level at which a transcript starts reading
 * like language rather than like timing data.
 *
 * Utterances — not VAD fragments — are what the paragraph layer groups.
 */
data class Utterance(
    val id: String,
    val turnId: String,
    val speakerId: String?,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val wordIds: List<String>,
    /**
     * A short acknowledgement ("yeah", "right", "exactly") that is this speaker's whole turn.
     * Flagged so downstream layers can treat it as the conversational punctuation it is instead
     * of as an unfinished thought to be glued onto its neighbour.
     */
    val isBackchannel: Boolean = false
)

/**
 * A paragraph of the finished transcript, with full provenance back down to words.
 *
 * This is what a person reads, what intelligence cites, and what search returns.
 */
data class CanonicalParagraph(
    val id: String,
    val speakerId: String?,
    val speakerName: String?,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val wordIds: List<String>,
    val utteranceIds: List<String>,
    val confidence: Float? = null
)

/** Which processing profile produced a transcript — see `com.example.core.model.ProcessingProfile`. */
data class TranscriptMetadata(
    val meetingId: String,
    val language: String = "en",
    /** Name of the `ProcessingProfile` entry this run used. */
    val processingMode: String,
    /** Human-readable engine name, e.g. "parakeet-tdt-0.6b-v3" or "gemini-3.5-transcribe". */
    val transcriptionEngine: String,
    val transcriptionModelId: String? = null,
    /** Bumped whenever the structural pipeline changes in a way that makes stored output
     * non-comparable with a fresh run. Stored per meeting so a later version can tell which
     * transcripts predate a fix. */
    val processingVersion: Int = CANONICAL_PIPELINE_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val audioDurationMs: Long = 0L,
    /** Number of cloud chunks this transcript was assembled from; 1 for a single-pass run. */
    val chunkCount: Int = 1,
    val stageDurationsMs: Map<String, Long> = emptyMap(),
    val quality: TranscriptQualityReport? = null
)

/**
 * The single source of truth for one meeting's transcript.
 *
 * Every downstream MeetingMind feature — transcript UI, search, embeddings, Ask Meeting,
 * summaries, action items, exports — reads from this, directly or via
 * [CanonicalTranscriptAssembler.projectToSegments], which renders it as the
 * `TranscriptSegment` list the existing persistence and UI layers already speak.
 */
data class CanonicalTranscript(
    val meetingId: String,
    val words: List<CanonicalWord>,
    val speakerTurns: List<SpeakerTurn>,
    val utterances: List<Utterance>,
    val paragraphs: List<CanonicalParagraph>,
    val metadata: TranscriptMetadata
) {
    val isEmpty: Boolean get() = words.isEmpty()

    /** Distinct real speaker identities, in first-appearance order. Never includes null. */
    val speakerIds: List<String>
        get() = speakerTurns.mapNotNull { it.speakerId }.distinct()

    fun wordsFor(wordIds: Collection<String>): List<CanonicalWord> {
        val wanted = wordIds.toSet()
        return words.filter { it.id in wanted }
    }
}

/**
 * Incremented when the word/turn/utterance/paragraph construction changes semantically. Version 1
 * is the first word-centric pipeline; anything persisted before it carries version 0.
 */
const val CANONICAL_PIPELINE_VERSION: Int = 1

/** Word ids are positional within one transcript: stable for a given run, cheap, and readable in logs. */
internal fun canonicalWordId(index: Int): String = "w$index"
