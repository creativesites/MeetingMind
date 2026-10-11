package com.craftflowtechnologies.meetingmind.ai.biblestudy

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier
import kotlinx.coroutines.withTimeoutOrNull

/** What asking for suggestions came to. */
sealed interface SuggestOutcome {
    data class Got(val suggestions: Suggestions, val modelId: String, val cloud: Boolean) : SuggestOutcome
    /** No model, no key, offline or a bad answer: the session carries on with its curated questions and says why in one line. */
    data class Unavailable(val line: String) : SuggestOutcome
}

/** Asks a model for suggestions. An interface so the session can be tested without one. */
fun interface SessionAi {
    suspend fun suggest(brief: SessionBrief): SuggestOutcome
}

/**
 * The model for guided sessions: the same factory the devotionals use. Gemini first whenever a key is
 * set, then the on-device model; with neither, [SuggestOutcome.Unavailable].
 */
class ModelSessionAi(private val context: Context) : SessionAi {

    override suspend fun suggest(brief: SessionBrief): SuggestOutcome {
        val candidates = candidates()
        if (candidates.isEmpty()) return SuggestOutcome.Unavailable(NO_MODEL)
        var failed = false
        for ((model, id, cloud) in candidates) {
            val raw = withTimeoutOrNull(if (cloud) CLOUD_TIMEOUT_MS else DEVICE_TIMEOUT_MS) {
                runCatching { model.generate(SessionPrompt.prompt(brief), maxOutputTokens = 700) }.getOrNull()
            }
            val text = (raw as? AiResult.Success)?.value
            val parsed = text?.let { SessionPrompt.parse(it) }
            if (parsed != null) return SuggestOutcome.Got(parsed, id, cloud)
            failed = true
        }
        return SuggestOutcome.Unavailable(if (failed) FAILED else NO_MODEL)
    }

    private data class Candidate(val model: LanguageModel, val id: String, val cloud: Boolean)

    private suspend fun candidates(): List<Candidate> {
        val factory = com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory(
            context = context,
            modelStorage = com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage(context),
            geminiTransport = com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(context)
        )
        val cloud = runCatching { factory.resolve(com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.INTERNET, ModelCapability.SUMMARIZATION) }
            .getOrNull()?.takeIf { it.isCloud }
        val local = runCatching { factory.resolveLocal(ModelCapability.SUMMARIZATION, ModelTier.RECOMMENDED) }.getOrNull()
        return listOfNotNull(cloud, local).map { Candidate(it.languageModel, it.modelId, it.isCloud) }
    }

    /** Frees the on-device model once a session is over. */
    suspend fun release() { runCatching { com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager.release() } }

    companion object {
        const val CLOUD_TIMEOUT_MS = 45_000L
        const val DEVICE_TIMEOUT_MS = 90_000L
        const val NO_MODEL = "Zuri's suggestions need an internet connection or an on-device model. These are our own questions."
        const val FAILED = "Zuri couldn't suggest questions just now. These are our own questions."
    }
}
