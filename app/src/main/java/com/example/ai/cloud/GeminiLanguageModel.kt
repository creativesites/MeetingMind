package com.example.ai.cloud

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.ai.routing.AiModelRouter
import com.example.ai.routing.AiRoute
import com.example.ai.routing.DefaultAiModelRouter

/**
 * Presents Gemini as a [LanguageModel], the same primitive the on-device model implements.
 *
 * This is what lets one [com.example.ai.tools.TranscriptToolEngine] serve both processing
 * profiles. The alternative — a cloud tool engine beside a local one — would have meant two copies
 * of every prompt contract and every validation rule, which is exactly how the cloud path and the
 * local path end up quietly disagreeing about what a tool does.
 *
 * Text only: no audio goes through here. Audio reaches Gemini through
 * [GeminiTranscriptionEngine], which is the only component that should be deciding what was said.
 */
class GeminiLanguageModel(
    private val transport: GeminiTransport,
    private val router: AiModelRouter = DefaultAiModelRouter,
    private val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION
) : LanguageModel {

    override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
        if (!transport.isConfigured()) {
            return AiResult.ModelUnavailable(
                modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                message = "No Gemini API key is set. Add one in Settings to use Internet mode."
            )
        }
        val modelId = router.route(AiRoute.GEMINI_INTELLIGENCE).modelId
            ?: return AiResult.Failed("No Gemini reasoning model is configured.")

        return transport.execute(
            GeminiRequest(
                modelId = modelId,
                systemInstruction = systemInstruction,
                prompt = prompt
                // The schema is carried inside the prompt by TranscriptToolPrompts rather than set
                // here: the tool engine owns the output contract, and passing it twice, in two
                // different places, is how the two drift apart.
            )
        )
    }

    private companion object {
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "You work with transcripts of real recordings. Answer only from the transcript you are " +
                "given, cite the paragraph ids that support what you report, and reply with JSON " +
                "matching the schema in the request and nothing else."
    }
}
