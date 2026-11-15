package com.craftflowtechnologies.meetingmind.ai.routing

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.AiStreamEvent
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLanguageModel
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.AiCoreLanguageModel
import com.craftflowtechnologies.meetingmind.ai.llm.AiCoreRuntime
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.llm.MediaPipeLanguageModel
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmModelResolver
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A language model chosen for one job, and the facts about it the caller needs. */
data class ResolvedLanguageModel(
    val languageModel: LanguageModel,
    /** What actually ran, for the honest "produced by" line every AI result carries. */
    val modelId: String,
    /** How much text the caller may send at once. Chunking decisions depend on it. */
    val contextLengthTokens: Int,
    val isCloud: Boolean
)

/**
 * The one place that decides which language model does a piece of work.
 *
 * Before this existed, each AI stage reached for a model on its own, and three of them — AI
 * transcript cleanup, speaker reconciliation and Ask — always reached for the on-device one. So
 * "Internet mode" meant cloud transcription and cloud summaries, and quietly local everything
 * else, which is not what the setting says.
 *
 * The rule, in one place:
 *
 * - **INTERNET with a Gemini key entered** → Gemini.
 * - **Otherwise** → the best installed on-device model for the capability and tier.
 * - **Neither available** → null, and the caller reports that honestly.
 *
 * The fallback runs one way only. An OFFLINE request never considers Gemini, whatever is
 * configured: that is the privacy guarantee, and it is structural here rather than something each
 * caller has to remember.
 */
class LanguageModelFactory(
    private val context: Context,
    private val modelStorage: ModelStorage,
    private val geminiTransport: GeminiTransport,
    private val router: AiModelRouter = DefaultAiModelRouter
) {

    suspend fun resolve(
        profile: ProcessingProfile,
        capability: ModelCapability,
        preferredTier: ModelTier = ModelTier.RECOMMENDED
    ): ResolvedLanguageModel? {
        if (profile == ProcessingProfile.INTERNET && geminiTransport.refreshConfigured()) {
            val modelId = router.route(AiRoute.GEMINI_INTELLIGENCE).modelId
            if (modelId != null) {
                return ResolvedLanguageModel(
                    languageModel = GeminiLanguageModel(geminiTransport, router),
                    modelId = modelId,
                    contextLengthTokens = GEMINI_CONTEXT_BUDGET_TOKENS,
                    isCloud = true
                )
            }
        }
        return resolveLocal(capability, preferredTier)
    }

    /**
     * Resolves a model with automatic one-way fallback from cloud to on-device LLM when the device
     * is offline, unconfigured, or a free-tier cloud allowance is exhausted.
     *
     * An [ProcessingProfile.OFFLINE] request still never touches the network.
     */
    suspend fun resolveWithFallback(
        profile: ProcessingProfile,
        capability: ModelCapability,
        preferredTier: ModelTier = ModelTier.RECOMMENDED
    ): ResolvedLanguageModel? {
        if (profile == ProcessingProfile.INTERNET && geminiTransport.refreshConfigured()) {
            val modelId = router.route(AiRoute.GEMINI_INTELLIGENCE).modelId
            if (modelId != null) {
                val resilient = ResilientLanguageModel(
                    primary = GeminiLanguageModel(geminiTransport, router),
                    primaryModelId = modelId,
                    resolveLocalFallback = { resolveLocal(capability, preferredTier) }
                )
                return ResolvedLanguageModel(
                    languageModel = resilient,
                    modelId = modelId,
                    contextLengthTokens = GEMINI_CONTEXT_BUDGET_TOKENS,
                    isCloud = true
                )
            }
        }
        return resolveLocal(capability, preferredTier)
    }

    /** The on-device model only — for callers that must never use the network. */
    fun resolveLocal(capability: ModelCapability, preferredTier: ModelTier): ResolvedLanguageModel? {
        val modelId = LlmModelResolver.resolveForModeOrNull(modelStorage, capability, preferredTier)
        if (modelId != null) {
            return ResolvedLanguageModel(
                languageModel = MediaPipeLanguageModel(context, modelStorage, modelId = modelId),
                modelId = modelId,
                contextLengthTokens = ModelCatalog.entries.find { it.id == modelId }?.contextLengthTokens
                    ?: DEFAULT_LOCAL_CONTEXT_TOKENS,
                isCloud = false
            )
        }
        if (AiCoreRuntime.isReady(context)) {
            return ResolvedLanguageModel(
                languageModel = AiCoreLanguageModel(context),
                modelId = AiCoreLanguageModel.MODEL_ID,
                contextLengthTokens = AiCoreLanguageModel.CONTEXT_TOKENS,
                isCloud = false
            )
        }
        return null
    }

    companion object {
        /**
         * How much transcript is sent to Gemini in one request.
         *
         * Far below what the model accepts. The limit here is not capacity but quality: output
         * that must cite and preserve every name and number degrades well before the context
         * window fills, and a smaller chunk also bounds what one failed request costs.
         */
        const val GEMINI_CONTEXT_BUDGET_TOKENS = 32_000
        private const val DEFAULT_LOCAL_CONTEXT_TOKENS = 4096
    }
}

/**
 * Tries [primary] cloud model first, then seamlessly falls back to an installed on-device model
 * when cloud is unavailable, offline, or free-tier quota is exhausted. Tracks which model
 * actually served the last call so provenance and local-engine cleanup stay honest.
 */
class ResilientLanguageModel(
    private val primary: LanguageModel,
    private val primaryModelId: String,
    private val resolveLocalFallback: suspend () -> ResolvedLanguageModel?
) : LanguageModel {

    @Volatile var lastActualModelId: String = primaryModelId
        private set
    @Volatile var lastRanLocally: Boolean = false
        private set

    override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
        val cloud = primary.generate(prompt, maxOutputTokens)
        if (cloud is AiResult.Success) {
            lastActualModelId = primaryModelId
            lastRanLocally = false
            return cloud
        }
        val local = resolveLocalFallback() ?: return cloud
        GeminiLog.add("Falling back to on-device ${local.modelId} after cloud unavailability")
        lastActualModelId = local.modelId
        lastRanLocally = true
        return local.languageModel.generate(prompt, maxOutputTokens)
    }

    override suspend fun generateStructured(
        prompt: String,
        responseSchema: String,
        maxOutputTokens: Int
    ): AiResult<String> {
        val cloud = primary.generateStructured(prompt, responseSchema, maxOutputTokens)
        if (cloud is AiResult.Success) {
            lastActualModelId = primaryModelId
            lastRanLocally = false
            return cloud
        }
        val local = resolveLocalFallback() ?: return cloud
        GeminiLog.add("Falling back to on-device ${local.modelId} for structured generation")
        lastActualModelId = local.modelId
        lastRanLocally = true
        return local.languageModel.generateStructured(prompt, responseSchema, maxOutputTokens)
    }

    override fun generateStream(prompt: String, maxOutputTokens: Int): Flow<AiStreamEvent> = flow {
        var emittedText = false
        var completed = false
        var cloudFailure: AiResult<Nothing>? = null
        primary.generateStream(prompt, maxOutputTokens).collect { ev ->
            when (ev) {
                is AiStreamEvent.TextDelta, is AiStreamEvent.ThinkingDelta, is AiStreamEvent.FunctionCalls -> {
                    emittedText = true
                    emit(ev)
                }
                is AiStreamEvent.Completed -> {
                    lastActualModelId = primaryModelId
                    lastRanLocally = false
                    completed = true
                    emit(ev)
                }
                is AiStreamEvent.Failed -> cloudFailure = ev.error
            }
        }
        if (completed) return@flow
        if (!emittedText) {
            val local = resolveLocalFallback()
            if (local != null) {
                GeminiLog.add("Falling back to on-device ${local.modelId} stream")
                lastActualModelId = local.modelId
                lastRanLocally = true
                local.languageModel.generateStream(prompt, maxOutputTokens).collect { emit(it) }
                return@flow
            }
        }
        emit(AiStreamEvent.Failed(cloudFailure ?: AiResult.Failed("Cloud model stream interrupted.")))
    }
}
