package com.craftflowtechnologies.meetingmind.ai.cloud

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/** Configurable reasoning effort for Gemini 3.8+ / Gemini 4 requests. */
enum class ThinkingLevel { MINIMAL, LOW, MEDIUM, HIGH }

/** One native function invocation emitted by a tool-capable model. */
data class GeminiFunctionCall(
    val name: String,
    val argsJson: String,
    val id: String? = null
)

/** Token and cache metrics returned by a cloud model invocation. */
data class GeminiUsage(
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val thoughtTokens: Int = 0,
    val totalTokens: Int = 0
)

/** Incremental streaming events emitted by `:streamGenerateContent` or DeepSeek SSE. */
sealed interface AiStreamEvent {
    data class ThinkingDelta(val text: String) : AiStreamEvent
    data class TextDelta(val text: String) : AiStreamEvent
    data class FunctionCalls(val calls: List<GeminiFunctionCall>) : AiStreamEvent
    data class Completed(
        val text: String,
        val functionCalls: List<GeminiFunctionCall> = emptyList(),
        val usage: GeminiUsage? = null,
        val ttftMs: Long? = null
    ) : AiStreamEvent
    data class Failed(val error: AiResult<Nothing>) : AiStreamEvent
}

/**
 * One request to a Gemini model.
 *
 * Deliberately transport-shaped rather than SDK-shaped: it names a model, some instructions, an
 * optional audio file and an optional response schema, and says nothing about how any of that is
 * encoded or authenticated.
 */
data class GeminiRequest(
    val modelId: String,
    val systemInstruction: String,
    val prompt: String,
    /** Audio to transcribe or reason over. Null for a text-only request. */
    val audioFile: File? = null,
    /** Milliseconds into [audioFile] this request covers — see [GeminiChunkPlanner]. */
    val audioStartMs: Long = 0L,
    val audioEndMs: Long = 0L,
    /** A JSON Schema the response must conform to. Null means free text unless [jsonOutput] is true. */
    val responseSchema: String? = null,
    /** Ask the model for `application/json` output even when no [responseSchema] is provided. */
    val jsonOutput: Boolean = false,
    /** Terms the model should prefer when it is unsure — names, products, jargon. */
    val vocabularyHints: List<String> = emptyList(),
    val temperature: Float = 0.0f,
    /**
     * Set for the dedicated transcription model. It takes the audio alone plus this config: no
     * prompt, schema, temperature or system instruction. The response is returned as
     * `{"turns":[{"speakerLabel":…,"words":[{"word","startOffset","endOffset"}]}], "text":…}`.
     */
    val transcription: AudioTranscriptionConfig? = null,
    /**
     * Upload [audioFile] exactly as it is instead of a decoded WAV slice. Only for a request that
     * covers the whole recording, in a format Gemini reads directly — no decoding, much faster.
     */
    val uploadWholeFile: Boolean = false,
    /** A hard limit for the whole request, overriding the transport's own; null to use those. */
    val timeoutMs: Long? = null,
    /** Optional reasoning effort level for interactive vs deep synthesis turns. */
    val thinkingLevel: ThinkingLevel? = null,
    /** When true, request thought summaries alongside final output. */
    val includeThoughts: Boolean = false,
    /** Optional output token ceiling. */
    val maxOutputTokens: Int? = null,
    /** Explicit Gemini context cache resource name (`cachedContents/...`). */
    val cachedContent: String? = null,
    /** Structured multi-turn `contents` JSON array overriding single-turn [prompt]. */
    val contentsJson: String? = null,
    /** Native Gemini `tools` JSON array containing `functionDeclarations`. */
    val toolsJson: String? = null,
    /** Optional `functionCallingConfig.mode` (`AUTO`, `ANY`, `NONE`). */
    val toolMode: String? = null
)

/** `generationConfig.audioTranscriptionConfig` for the Gemini transcription model. */
data class AudioTranscriptionConfig(
    /** "VERBATIM" or "SMART". SMART can't be combined with timestamps or diarization. */
    val mode: String = "VERBATIM",
    val wordTimestamp: Boolean = true,
    val diarization: Boolean = true,
    /** Empty means auto-detect. */
    val languageCodes: List<String> = emptyList(),
    /** Rejected by the API alongside timestamps or diarization, so only sent without them. */
    val customVocabulary: List<String> = emptyList()
)

/**
 * The single seam between MeetingMind and Google's servers.
 *
 * Everything cloud-related in the app goes through this one interface, for two reasons:
 *
 * 1. **It is the only place that can leak.** An offline run is prevented from reaching a Gemini
 *    engine by [com.craftflowtechnologies.meetingmind.ai.routing.AiModelRouter.routesFor], and the engines are the only
 *    things that hold a transport. Auditing "can offline mode make a network call?" means
 *    auditing implementations of this interface and nothing else.
 * 2. **The transport is going to change.** Shipping a production API key inside an APK is not an
 *    option (see docs/FUTURE_BACKEND.md and the overhaul brief §35); production traffic must go
 *    through an authenticated backend that holds the key and enforces quotas, rate limits and
 *    abuse protection. When that backend exists, a new implementation of this interface is the
 *    entire client-side change.
 */
interface GeminiTransport {
    suspend fun execute(request: GeminiRequest): AiResult<String>

    /**
     * Streams incremental output deltas (thoughts, text tokens, native function calls, and usage).
     * Defaults to wrapping [execute] so existing test doubles stay source-compatible.
     */
    fun stream(request: GeminiRequest): Flow<AiStreamEvent> = flow {
        val started = System.currentTimeMillis()
        when (val res = execute(request)) {
            is AiResult.Success -> {
                val ttft = (System.currentTimeMillis() - started).coerceAtLeast(0L)
                if (res.value.isNotEmpty()) emit(AiStreamEvent.TextDelta(res.value))
                emit(AiStreamEvent.Completed(text = res.value, ttftMs = ttft))
            }
            is AiResult.Failed -> emit(AiStreamEvent.Failed(res))
            is AiResult.ModelUnavailable -> emit(AiStreamEvent.Failed(res))
            is AiResult.DeviceUnsupported -> emit(AiStreamEvent.Failed(res))
            is AiResult.InsufficientMemory -> emit(AiStreamEvent.Failed(res))
            is AiResult.InsufficientStorage -> emit(AiStreamEvent.Failed(res))
        }
    }

    /** Whether this transport is configured to make calls at all. Checked before a profile offers
     * Internet mode, so the user is told up front rather than after a failed upload. */
    fun isConfigured(): Boolean

    /**
     * Re-reads whatever [isConfigured] depends on and returns the fresh answer.
     *
     * [isConfigured] is synchronous so the UI can ask it cheaply, which means an implementation
     * backed by storage may answer from a cached value. Anything about to decide *which model
     * runs* calls this instead, so a key entered a moment ago is honoured rather than missed.
     */
    suspend fun refreshConfigured(): Boolean = isConfigured()
}

/**
 * The default transport: honestly unavailable.
 *
 * MeetingMind ships with no embedded Gemini credential, so out of the box Internet mode reports
 * itself unconfigured rather than failing mid-upload — and, critically, rather than silently
 * falling back to a key someone committed. A development build supplies a real transport; a
 * production build will supply the backend-backed one.
 */
class UnconfiguredGeminiTransport : GeminiTransport {
    override suspend fun execute(request: GeminiRequest): AiResult<String> =
        AiResult.ModelUnavailable(
            modelId = request.modelId,
            message = "Internet mode isn't set up on this build. Offline processing is unaffected."
        )

    override fun isConfigured(): Boolean = false
}
