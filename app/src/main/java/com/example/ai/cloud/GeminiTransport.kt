package com.example.ai.cloud

import com.example.ai.common.AiResult
import java.io.File

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
    /** A JSON Schema the response must conform to. Null means free text. */
    val responseSchema: String? = null,
    /** Terms the model should prefer when it is unsure — names, products, jargon. */
    val vocabularyHints: List<String> = emptyList(),
    val temperature: Float = 0.0f
)

/**
 * The single seam between MeetingMind and Google's servers.
 *
 * Everything cloud-related in the app goes through this one interface, for two reasons:
 *
 * 1. **It is the only place that can leak.** An offline run is prevented from reaching a Gemini
 *    engine by [com.example.ai.routing.AiModelRouter.routesFor], and the engines are the only
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
