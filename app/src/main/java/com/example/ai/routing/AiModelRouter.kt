package com.example.ai.routing

import com.example.core.model.ProcessingProfile

/**
 * The single place any model identifier is allowed to exist.
 *
 * Model strings scattered through a codebase are how an app ends up quietly calling three
 * different models for the same job and being unable to say which one produced a given result.
 * `ModelCatalog` already plays this role for on-device models; this plays it for remote ones and
 * for the routing decision itself.
 */
enum class AiRoute {
    /** On-device Parakeet TDT via sherpa-onnx. */
    LOCAL_TRANSCRIPTION,

    /** On-device LLM via MediaPipe. */
    LOCAL_INTELLIGENCE,

    /** Cloud transcription, fidelity pass: verbatim + diarization + word timestamps. */
    GEMINI_TRANSCRIPTION_VERBATIM,

    /** Cloud transcription, readability pass: punctuation, paragraphs, disfluency cleanup. */
    GEMINI_TRANSCRIPTION_SMART,

    /** Cloud reasoning over an already-built transcript. */
    GEMINI_INTELLIGENCE,

    /** Low-latency bidirectional voice session. Reserved — see docs/TRANSCRIPTION_OVERHAUL.md §6. */
    GEMINI_LIVE,

    /** Live with extended background reasoning. Reserved. */
    GEMINI_LIVE_EXTENDED
}

/** Everything the app knows about one routable model. */
data class ModelRoute(
    val route: AiRoute,
    /** The provider's model identifier, or null for a route served by an on-device catalog entry. */
    val modelId: String?,
    /** True when running this route sends audio or transcript text off the device. */
    val requiresNetwork: Boolean
)

/**
 * Resolves a capability to a concrete model.
 *
 * Deliberately an interface with a trivial default implementation: the moment MeetingMind gains an
 * authenticated backend (see docs/FUTURE_BACKEND.md and §35 of the overhaul brief), routing gains
 * server-side policy — per-tier model selection, quota-aware downgrades, regional pinning — and
 * only this layer changes.
 */
interface AiModelRouter {
    fun route(route: AiRoute): ModelRoute

    /** Which routes a profile is permitted to use. Enforced at the call site, so an offline run
     * cannot reach a network route even by mistake. */
    fun routesFor(profile: ProcessingProfile): Set<AiRoute>
}

/**
 * The shipped routing table.
 *
 * Cloud model ids live here and nowhere else. The Gemini transcription model is used for
 * transcription and the Flash model for reasoning — never the other way round: a general
 * reasoning model asked to transcribe produces fluent text that is not what was said, which is
 * precisely the failure mode this architecture exists to prevent.
 */
object DefaultAiModelRouter : AiModelRouter {

    const val GEMINI_TRANSCRIBE_MODEL = "gemini-3.5-transcribe"
    const val GEMINI_INTELLIGENCE_MODEL = "gemini-3.6-flash"
    const val GEMINI_LIVE_MODEL = "gemini-3.8-live"
    const val GEMINI_LIVE_EXTENDED_MODEL = "gemini-3.8-live-extended-thinking"

    override fun route(route: AiRoute): ModelRoute = when (route) {
        AiRoute.LOCAL_TRANSCRIPTION -> ModelRoute(route, null, requiresNetwork = false)
        AiRoute.LOCAL_INTELLIGENCE -> ModelRoute(route, null, requiresNetwork = false)
        AiRoute.GEMINI_TRANSCRIPTION_VERBATIM -> ModelRoute(route, GEMINI_TRANSCRIBE_MODEL, requiresNetwork = true)
        AiRoute.GEMINI_TRANSCRIPTION_SMART -> ModelRoute(route, GEMINI_TRANSCRIBE_MODEL, requiresNetwork = true)
        AiRoute.GEMINI_INTELLIGENCE -> ModelRoute(route, GEMINI_INTELLIGENCE_MODEL, requiresNetwork = true)
        AiRoute.GEMINI_LIVE -> ModelRoute(route, GEMINI_LIVE_MODEL, requiresNetwork = true)
        AiRoute.GEMINI_LIVE_EXTENDED -> ModelRoute(route, GEMINI_LIVE_EXTENDED_MODEL, requiresNetwork = true)
    }

    override fun routesFor(profile: ProcessingProfile): Set<AiRoute> = when (profile) {
        // Offline is offline. There is no cloud route in this set, so an offline run cannot make a
        // network call by taking a wrong branch — it would have to be given a route it cannot ask
        // for. Privacy here is structural, not a runtime flag.
        ProcessingProfile.OFFLINE -> setOf(AiRoute.LOCAL_TRANSCRIPTION, AiRoute.LOCAL_INTELLIGENCE)
        ProcessingProfile.INTERNET -> setOf(
            AiRoute.GEMINI_TRANSCRIPTION_VERBATIM,
            AiRoute.GEMINI_TRANSCRIPTION_SMART,
            AiRoute.GEMINI_INTELLIGENCE,
            // Kept available so a cloud failure can fall back to on-device processing rather than
            // leaving the user with nothing — see the overhaul brief §38.
            AiRoute.LOCAL_TRANSCRIPTION,
            AiRoute.LOCAL_INTELLIGENCE
        )
        ProcessingProfile.LIVE -> setOf(AiRoute.GEMINI_LIVE)
        ProcessingProfile.LIVE_ADVANCED -> setOf(AiRoute.GEMINI_LIVE_EXTENDED)
    }
}
