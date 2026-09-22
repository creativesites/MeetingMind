package com.example.ai.cloud

import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.routing.AiModelRouter
import com.example.ai.routing.AiRoute
import com.example.ai.routing.DefaultAiModelRouter
import com.example.ai.transcript.AsrWindowReconciler
import com.example.ai.transcript.AttributionConfidence
import com.example.ai.transcript.CanonicalWord
import com.example.ai.transcript.TranscriptSource
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/** What a cloud transcription run produced, and how it got there. */
data class CloudTranscriptionResult(
    val words: List<CanonicalWord>,
    val chunkCount: Int,
    /** True when the smart pass ran and was actually fused in. */
    val smartPassApplied: Boolean,
    val alignmentCoverage: Float?,
    /** Populated when a stage failed but the run continued with what it already had. */
    val degradedReason: String? = null
)

/**
 * Internet-mode transcription: two Gemini passes over the same chunk plan, fused.
 *
 * ```
 * chunks -> verbatim pass   (words, timestamps, diarization)  -- the fidelity layer
 *        -> global speaker resolution across chunks
 *        -> cross-chunk overlap reconciliation
 *        -> smart pass      (punctuation, paragraphs, cleanup) -- the readability layer
 *        -> fusion          (smart text onto verbatim timing)
 * ```
 *
 * ### Why two passes
 *
 * The transcription model cannot give diarization and word-level timestamps *and* polished,
 * disfluency-cleaned prose in one call — the configurations are mutually exclusive. Asking for the
 * polished version alone would mean a transcript with no timing and no speakers, which makes
 * provenance, playback sync, search-by-time and speaker attribution all impossible. So the app
 * asks for both, separately, and treats the first as truth and the second as presentation.
 *
 * ### Degradation
 *
 * Every stage after verbatim is optional. If the smart pass fails, the verbatim transcript is
 * returned. If fusion finds too little agreement to trust, the verbatim transcript is returned.
 * A failure downstream never discards a successful result upstream.
 */
class GeminiTranscriptionEngine(
    private val transport: GeminiTransport,
    private val parser: GeminiTranscriptParser = GeminiTranscriptParser,
    private val router: AiModelRouter = DefaultAiModelRouter,
    private val chunkConfig: ChunkPlanConfig = ChunkPlanConfig()
) {

    suspend fun transcribe(
        audioFile: File,
        totalDurationMs: Long,
        vocabularyHints: List<String> = emptyList(),
        onProgress: (progress: Float, statusText: String) -> Unit = { _, _ -> }
    ): AiResult<CloudTranscriptionResult> {
        if (!transport.isConfigured()) {
            return AiResult.ModelUnavailable(
                modelId = router.route(AiRoute.GEMINI_TRANSCRIPTION_VERBATIM).modelId ?: "gemini",
                message = "Internet mode isn't set up on this build. Offline processing is unaffected."
            )
        }

        val chunks = GeminiChunkPlanner.plan(totalDurationMs, chunkConfig)
        if (chunks.isEmpty()) return AiResult.Success(CloudTranscriptionResult(emptyList(), 0, false, null))

        // --- Pass A: verbatim. This one is required; without it there is no transcript at all. ---
        val verbatimModel = router.route(AiRoute.GEMINI_TRANSCRIPTION_VERBATIM).modelId
            ?: return AiResult.Failed("No Gemini transcription model is configured.")

        val transcribedChunks = mutableListOf<ChunkTranscription>()
        for (chunk in chunks) {
            coroutineContext.ensureActive()
            onProgress(
                chunk.index.toFloat() / (chunks.size * 2),
                "Transcribing part ${chunk.index + 1} of ${chunks.size}..."
            )
            val response = transport.execute(
                GeminiRequest(
                    modelId = verbatimModel,
                    systemInstruction = VERBATIM_SYSTEM_INSTRUCTION,
                    prompt = VERBATIM_PROMPT,
                    audioFile = audioFile,
                    audioStartMs = chunk.startMs,
                    audioEndMs = chunk.endMs,
                    responseSchema = GeminiTranscriptParser.VERBATIM_SCHEMA,
                    vocabularyHints = vocabularyHints
                )
            )
            if (response !is AiResult.Success) {
                // Nothing usable has been produced yet for this chunk, and a transcript with a
                // silent hole in the middle is worse than an honest failure.
                return AiResult.Failed(
                    "Cloud transcription failed on part ${chunk.index + 1} of ${chunks.size}: " +
                        (response.describeFailure() ?: "unknown error")
                )
            }
            val words = parser.parseVerbatim(response.value, chunkStartMs = chunk.startMs)
                ?: return AiResult.Failed("Cloud transcription returned an unreadable result for part ${chunk.index + 1}.")
            transcribedChunks += ChunkTranscription(chunk, words)
        }

        // --- Cross-chunk resolution: speakers first, then duplicated words. ---
        val mapping = GlobalSpeakerResolver.resolve(transcribedChunks)
        val globalised = GlobalSpeakerResolver.applyMapping(transcribedChunks, mapping)
        val verbatimWords = AsrWindowReconciler.reconcile(globalised.map { it.words })

        if (verbatimWords.isEmpty()) {
            return AiResult.Success(CloudTranscriptionResult(emptyList(), chunks.size, false, null))
        }

        // --- Pass B: smart. Optional by design — a failure here costs polish, not the transcript. ---
        onProgress(0.6f, "Polishing the transcript...")
        val smartModel = router.route(AiRoute.GEMINI_TRANSCRIPTION_SMART).modelId
        val smartText = if (smartModel == null) null else buildString {
            for (chunk in chunks) {
                coroutineContext.ensureActive()
                val response = transport.execute(
                    GeminiRequest(
                        modelId = smartModel,
                        systemInstruction = SMART_SYSTEM_INSTRUCTION,
                        prompt = SMART_PROMPT,
                        audioFile = audioFile,
                        audioStartMs = chunk.startMs,
                        audioEndMs = chunk.endMs,
                        vocabularyHints = vocabularyHints
                    )
                )
                if (response !is AiResult.Success) return@buildString
                append(response.value.trim()).append(' ')
            }
        }.takeIf { !it.isNullOrBlank() }

        if (smartText == null) {
            return AiResult.Success(
                CloudTranscriptionResult(
                    words = verbatimWords,
                    chunkCount = chunks.size,
                    smartPassApplied = false,
                    alignmentCoverage = null,
                    degradedReason = "The readability pass didn't complete; the verbatim transcript is unaffected."
                )
            )
        }

        val fused = TranscriptFusionEngine.fuse(verbatimWords, smartText)
        val applied = fused.alignmentCoverage >= TranscriptFusionEngine.MIN_USABLE_COVERAGE
        onProgress(1f, "Transcript ready")
        return AiResult.Success(
            CloudTranscriptionResult(
                words = fused.words,
                chunkCount = chunks.size,
                smartPassApplied = applied,
                alignmentCoverage = fused.alignmentCoverage,
                degradedReason = if (applied) null else
                    "The readability pass disagreed too broadly with the verbatim transcript to be merged; the verbatim transcript is unaffected."
            )
        )
    }

    private companion object {
        const val VERBATIM_SYSTEM_INSTRUCTION =
            "You are a verbatim transcription engine. Transcribe exactly what is said, including " +
                "false starts, repetitions and filler words. Do not summarise, correct, reorder or " +
                "paraphrase. Attribute every word to a speaker and give every word a start and end " +
                "time in milliseconds relative to the start of the supplied audio."

        const val VERBATIM_PROMPT =
            "Transcribe this audio verbatim with speaker diarization and word-level timestamps."

        const val SMART_SYSTEM_INSTRUCTION =
            "You are a transcript editor. Produce a readable transcript of this audio: correct " +
                "punctuation and casing, remove filler words and false starts, and resolve " +
                "self-corrections to what the speaker settled on. Never add information that was " +
                "not spoken, never reorder what was said, and never invent a speaker label or a " +
                "timestamp."

        const val SMART_PROMPT = "Produce a clean, readable transcript of this audio."
    }
}

/** Parses Gemini's structured transcription responses. Separated so it is testable without a network. */
object GeminiTranscriptParser {

    /**
     * The schema the verbatim pass must answer in. Requesting structured output rather than prose
     * is what makes this parseable at all: there is no regex over free text anywhere in the cloud
     * path.
     */
    const val VERBATIM_SCHEMA: String = """
    {
      "type": "object",
      "properties": {
        "words": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "text": { "type": "string" },
              "startMs": { "type": "integer" },
              "endMs": { "type": "integer" },
              "speaker": { "type": "string" }
            },
            "required": ["text", "startMs", "endMs"]
          }
        }
      },
      "required": ["words"]
    }
    """

    /**
     * @param chunkStartMs Added to every timestamp, because the model reports times relative to the
     *   audio it was given, not to the recording.
     * @return Words, or null when the response is not readable — never a partial guess.
     */
    fun parseVerbatim(json: String, chunkStartMs: Long): List<CanonicalWord>? = try {
        val array = org.json.JSONObject(json).getJSONArray("words")
        (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val text = item.optString("text").trim()
            if (text.isEmpty()) return@mapNotNull null
            val startMs = chunkStartMs + item.getLong("startMs")
            val endMs = chunkStartMs + item.getLong("endMs")
            val speaker = item.optString("speaker", "").ifEmpty { null }
            CanonicalWord(
                id = "c${chunkStartMs}_$index",
                text = text,
                startMs = startMs,
                endMs = maxOf(endMs, startMs),
                speakerId = speaker,
                // The model reported a speaker for this word; that is direct evidence, but it is
                // the model's own clustering rather than a measured acoustic overlap, so it is
                // recorded as HIGH only when a speaker was actually given.
                attribution = if (speaker != null) AttributionConfidence.HIGH else AttributionConfidence.NONE,
                source = TranscriptSource.GEMINI_VERBATIM
            )
        }
    } catch (e: org.json.JSONException) {
        null
    }
}
