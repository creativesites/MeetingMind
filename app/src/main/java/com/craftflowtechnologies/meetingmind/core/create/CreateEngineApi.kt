package com.craftflowtechnologies.meetingmind.core.create

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import kotlinx.coroutines.flow.first

/** The text model Create writes with. A seam so tests can script it. */
interface CreateTextModel {
    /** False when neither a configured cloud key (in Internet mode) nor a local model is ready: the studio then offers the editor and starters. */
    suspend fun available(): Boolean
    suspend fun complete(system: String, prompt: String, timeoutMs: Long = 40_000L): AiResult<String>

    /** Schema-enforced completion when supported by the backing cloud transport or local model. */
    suspend fun completeStructured(
        system: String,
        prompt: String,
        responseSchema: String,
        timeoutMs: Long = 40_000L
    ): AiResult<String> = complete(system, prompt, timeoutMs)
}

/** Gemini (with DeepSeek fallback) in Internet mode, with one-way on-device LLM fallback for offline/free users. */
class GeminiCreateModel(private val context: Context) : CreateTextModel {
    private val appContext = context.applicationContext
    private val transport by lazy { CloudAi.transport(appContext) }
    private val modelStorage by lazy { LocalModelStorage(appContext) }
    private val prefs by lazy { UserPreferencesManager(appContext) }
    private val factory by lazy { LanguageModelFactory(appContext, modelStorage, transport) }

    override suspend fun available(): Boolean {
        val profile = runCatching { prefs.preferencesFlow.first().processingProfile }.getOrDefault(ProcessingProfile.INTERNET)
        val cloudReady = profile == ProcessingProfile.INTERNET && runCatching {
            transport.refreshConfigured()
        }.getOrDefault(false)
        if (cloudReady) return true
        return factory.resolveLocal(ModelCapability.SUMMARIZATION, ModelTier.RECOMMENDED) != null
    }

    override suspend fun complete(system: String, prompt: String, timeoutMs: Long): AiResult<String> =
        completeWithSchema(system, prompt, responseSchema = null, timeoutMs = timeoutMs)

    override suspend fun completeStructured(
        system: String,
        prompt: String,
        responseSchema: String,
        timeoutMs: Long
    ): AiResult<String> = completeWithSchema(system, prompt, responseSchema = responseSchema, timeoutMs = timeoutMs)

    private suspend fun completeWithSchema(
        system: String,
        prompt: String,
        responseSchema: String?,
        timeoutMs: Long
    ): AiResult<String> {
        val profile = runCatching { prefs.preferencesFlow.first().processingProfile }.getOrDefault(ProcessingProfile.INTERNET)
        var cloudFailure: AiResult<String>? = null
        if (profile == ProcessingProfile.INTERNET && runCatching { transport.refreshConfigured() }.getOrDefault(false)) {
            val cloudResult = transport.execute(
                GeminiRequest(
                    modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                    systemInstruction = system,
                    prompt = prompt,
                    responseSchema = responseSchema,
                    jsonOutput = true,
                    temperature = 0.9f,
                    timeoutMs = timeoutMs,
                    thinkingLevel = com.craftflowtechnologies.meetingmind.ai.cloud.ThinkingLevel.LOW
                )
            )
            if (cloudResult is AiResult.Success) return cloudResult
            cloudFailure = cloudResult
        }

        val local = factory.resolveLocal(ModelCapability.SUMMARIZATION, ModelTier.RECOMMENDED)
            ?: return cloudFailure ?: AiResult.ModelUnavailable(
                "ai_unavailable",
                "Connect to the internet or download an on-device AI model in Settings."
            )

        val combinedPrompt = buildString {
            if (system.isNotBlank()) append(system.trim()).append("\n\n")
            append(prompt)
        }
        return try {
            if (responseSchema != null) {
                local.languageModel.generateStructured(
                    prompt = combinedPrompt,
                    responseSchema = responseSchema,
                    maxOutputTokens = 1024
                )
            } else {
                local.languageModel.generate(
                    prompt = combinedPrompt,
                    maxOutputTokens = 1024
                )
            }
        } finally {
            LlmEngineManager.release()
        }
    }
}

/** Verse text that came from the Bible library, with the translation it came from. Never stored on a card. */
data class ResolvedScripture(
    val reference: String,
    val text: String,
    val versionId: Int,
    val versionAbbreviation: String,
    val attribution: String
)

sealed interface ScriptureFetch {
    data class Found(val scripture: ResolvedScripture) : ScriptureFetch
    /** The text is not a reference the Bible knows (John 3:99). */
    data class Invalid(val input: String) : ScriptureFetch
    /** A real reference whose text could not be loaded (offline, not licensed). */
    data class Unavailable(val reference: String, val message: String) : ScriptureFetch
}

/** Where scripture text comes from: the Bible provider, by reference. Create never accepts verse text from a model. */
interface CreateScriptureLookup {
    suspend fun fetch(reference: String, versionId: Int? = null): ScriptureFetch
}

class ProviderScriptureLookup(private val service: ScriptureService) : CreateScriptureLookup {
    constructor(context: Context) : this(ScriptureService(context))

    override suspend fun fetch(reference: String, versionId: Int?): ScriptureFetch {
        val parsed = ScriptureReferenceParser.parse(reference) ?: return ScriptureFetch.Invalid(reference)
        return when (val r = service.passage(parsed, versionId)) {
            is PassageResult.Found -> ScriptureFetch.Found(
                ResolvedScripture(parsed.display(), r.passage.text.trim(), r.passage.versionId, r.passage.versionAbbreviation, r.passage.attribution)
            )
            is PassageResult.Unavailable -> ScriptureFetch.Unavailable(parsed.display(), r.message)
        }
    }
}
