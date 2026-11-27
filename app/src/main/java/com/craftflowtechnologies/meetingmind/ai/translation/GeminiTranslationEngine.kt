package com.craftflowtechnologies.meetingmind.ai.translation

import com.craftflowtechnologies.meetingmind.ai.asr.SupportedLanguages
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import org.json.JSONObject

class GeminiTranslationEngine(
    private val transport: GeminiTransport,
    private val fallbackModelProvider: (suspend () -> LanguageModel?)? = null,
    private val preferLocal: Boolean = false
) : TranslationEngine {

    override suspend fun translateTranscript(
        meetingTitle: String,
        summaryText: String?,
        segments: List<TranscriptSegment>,
        sourceLanguage: String,
        targetLanguage: String
    ): AiResult<TranslatedTranscript> {
        if (segments.isEmpty() && meetingTitle.isBlank() && summaryText.isNullOrBlank()) {
            return AiResult.Success(
                TranslatedTranscript(
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    translatedTitle = meetingTitle,
                    translatedSummary = summaryText,
                    translatedSegments = emptyList()
                )
            )
        }

        if (!preferLocal && transport.refreshConfigured()) {
            val prompt = buildTranslationPrompt(meetingTitle, summaryText, segments, sourceLanguage, targetLanguage)
            val response = transport.execute(
                GeminiRequest(
                    modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                    systemInstruction = TRANSLATION_SYSTEM_INSTRUCTION,
                    prompt = prompt,
                    responseSchema = TRANSLATION_SCHEMA
                )
            )
            if (response is AiResult.Success) {
                return parseTranslationResponse(response.value, meetingTitle, summaryText, sourceLanguage, targetLanguage)
            }
            val local = fallbackModelProvider?.invoke()
                ?: return AiResult.Failed(response.describeFailure() ?: "Transcript translation failed.")
            return translateWithLocalModel(local, meetingTitle, summaryText, segments, sourceLanguage, targetLanguage)
        }

        val local = fallbackModelProvider?.invoke()
            ?: return AiResult.ModelUnavailable(
                modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                message = "Translation requires Internet mode or an installed on-device language model."
            )
        return translateWithLocalModel(local, meetingTitle, summaryText, segments, sourceLanguage, targetLanguage)
    }

    private suspend fun translateWithLocalModel(
        model: LanguageModel,
        meetingTitle: String,
        summaryText: String?,
        segments: List<TranscriptSegment>,
        sourceLanguage: String,
        targetLanguage: String
    ): AiResult<TranslatedTranscript> {
        val batches = if (segments.isEmpty()) listOf(emptyList()) else segments.chunked(LOCAL_SEGMENT_BATCH_SIZE)
        var resolvedTitle: String? = meetingTitle
        var resolvedSummary: String? = summaryText
        val translatedSegments = mutableListOf<TranslatedSegment>()

        for ((index, batch) in batches.withIndex()) {
            val titleForChunk = if (index == 0) meetingTitle else ""
            val summaryForChunk = if (index == 0) summaryText else null
            val prompt = "$TRANSLATION_SYSTEM_INSTRUCTION\n\n" +
                buildTranslationPrompt(titleForChunk, summaryForChunk, batch, sourceLanguage, targetLanguage) +
                "\n\nRespond ONLY with JSON matching this schema:\n$TRANSLATION_SCHEMA"
            val res = model.generateStructured(prompt, TRANSLATION_SCHEMA, maxOutputTokens = 1024)
            if (res !is AiResult.Success) {
                if (translatedSegments.isEmpty()) {
                    return AiResult.Failed(res.describeFailure() ?: "On-device transcript translation failed.")
                }
                break
            }
            val parsed = parseTranslationResponse(
                raw = res.value,
                meetingTitle = titleForChunk,
                summaryText = summaryForChunk,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
            if (parsed is AiResult.Success) {
                if (index == 0) {
                    resolvedTitle = parsed.value.translatedTitle
                    resolvedSummary = parsed.value.translatedSummary
                }
                translatedSegments += parsed.value.translatedSegments
            }
        }
        return AiResult.Success(
            TranslatedTranscript(
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                translatedTitle = resolvedTitle,
                translatedSummary = resolvedSummary,
                translatedSegments = translatedSegments
            )
        )
    }

    private fun parseTranslationResponse(
        raw: String,
        meetingTitle: String,
        summaryText: String?,
        sourceLanguage: String,
        targetLanguage: String
    ): AiResult<TranslatedTranscript> = try {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        val jsonStr = if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
        val json = JSONObject(jsonStr)
        val translatedTitle = json.optString("translatedTitle").takeIf { it.isNotBlank() } ?: meetingTitle
        val translatedSummary = json.optString("translatedSummary").takeIf { it.isNotBlank() } ?: summaryText
        val segmentsArray = json.optJSONArray("translatedSegments") ?: org.json.JSONArray()
        val translatedSegments = mutableListOf<TranslatedSegment>()
        for (i in 0 until segmentsArray.length()) {
            val obj = segmentsArray.getJSONObject(i)
            val segmentId = obj.getString("segmentId")
            val text = obj.getString("translatedText")
            translatedSegments += TranslatedSegment(segmentId = segmentId, translatedText = text)
        }
        AiResult.Success(
            TranslatedTranscript(
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                translatedTitle = translatedTitle,
                translatedSummary = translatedSummary,
                translatedSegments = translatedSegments
            )
        )
    } catch (e: Exception) {
        AiResult.Failed("Failed to parse translated transcript response: ${e.message}")
    }

    internal fun buildTranslationPrompt(
        meetingTitle: String,
        summaryText: String?,
        segments: List<TranscriptSegment>,
        sourceLanguage: String,
        targetLanguage: String
    ): String = buildString {
        appendLine("Source language: ${SupportedLanguages.displayName(sourceLanguage)} ($sourceLanguage)")
        appendLine("Target language: ${SupportedLanguages.displayName(targetLanguage)} ($targetLanguage)")
        if (SupportedLanguages.isAfricanLanguage(sourceLanguage) || SupportedLanguages.isAfricanLanguage(targetLanguage)) {
            appendLine("Regional Englishification note: For Southern African languages (Shona, Ndebele, Bemba, Nyanja, etc.), keep commonly borrowed English words (such as 'meeting', 'budget', 'deadline', 'computer', 'system') natural and intact rather than forcing archaic or obscure translations.")
        }
        appendLine()
        appendLine("Title to translate: $meetingTitle")
        if (!summaryText.isNullOrBlank()) {
            appendLine("Summary to translate: $summaryText")
        }
        appendLine()
        appendLine("Transcript segments to translate. Preserve the exact segmentId for each line:")
        for (seg in segments) {
            val text = seg.cleanedText ?: seg.text
            appendLine("[${seg.id}] $text")
        }
    }

    companion object {
        private const val LOCAL_SEGMENT_BATCH_SIZE = 8
        const val TRANSLATION_SYSTEM_INSTRUCTION =
            "You are an expert multilingual translator specializing in conversational meetings, lectures, and voice notes. " +
            "Translate accurately while maintaining natural phrasing and the speaker's original intent. " +
            "Preserve common technical, administrative, and code-switched English loanwords where appropriate for African languages."

        const val TRANSLATION_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "translatedTitle": { "type": "string" },
            "translatedSummary": { "type": "string" },
            "translatedSegments": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "segmentId": { "type": "string" },
                  "translatedText": { "type": "string" }
                },
                "required": ["segmentId", "translatedText"]
              }
            }
          },
          "required": ["translatedTitle", "translatedSegments"]
        }
        """
    }
}
