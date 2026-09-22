package com.example.ai.routing

import android.content.Context
import com.example.ai.cloud.GeminiLanguageModel
import com.example.ai.cloud.GeminiTransport
import com.example.ai.llm.LanguageModel
import com.example.ai.llm.MediaPipeLanguageModel
import com.example.ai.modelmanagement.LlmModelResolver
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelStorage
import com.example.core.model.ModelCapability
import com.example.core.model.ModelTier
import com.example.core.model.ProcessingProfile

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

    /** The on-device model only — for callers that must never use the network. */
    fun resolveLocal(capability: ModelCapability, preferredTier: ModelTier): ResolvedLanguageModel? {
        val modelId = LlmModelResolver.resolveForModeOrNull(modelStorage, capability, preferredTier)
            ?: return null
        return ResolvedLanguageModel(
            languageModel = MediaPipeLanguageModel(context, modelStorage, modelId = modelId),
            modelId = modelId,
            contextLengthTokens = ModelCatalog.entries.find { it.id == modelId }?.contextLengthTokens
                ?: DEFAULT_LOCAL_CONTEXT_TOKENS,
            isCloud = false
        )
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
