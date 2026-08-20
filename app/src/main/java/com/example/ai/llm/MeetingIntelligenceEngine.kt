package com.example.ai.llm

import com.example.ai.common.AiResult
import com.example.core.model.ChatMessage
import com.example.core.model.MeetingSummary
import com.example.core.model.RecordingType
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment

/**
 * Low-level local language model primitive: raw prompt in, generated text out.
 *
 * This is the interface a real on-device LLM runtime (e.g. llama.cpp/GGUF or the MediaPipe LLM
 * Inference API) implements. Higher-level features (summarization, Ask Meeting) are built on
 * top of it via [MeetingIntelligenceEngine], which is free to add prompt templates and JSON
 * parsing around a [LanguageModel] once a real one exists.
 */
interface LanguageModel {
    suspend fun generate(prompt: String, maxOutputTokens: Int = 512): AiResult<String>
}

/** Default [LanguageModel] until a real on-device LLM runtime is integrated. */
class UnavailableLanguageModel : LanguageModel {
    override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> =
        AiResult.ModelUnavailable(
            modelId = "local-llm",
            message = "No local language model is installed on this device."
        )
}

/**
 * Meeting-level intelligence: title, summary, topics, decisions, action items, questions, and
 * grounded Ask-Meeting answers, all derived from an actual transcript.
 *
 * No implementation of this interface may invent a title, summary, decision, action item, or
 * answer that isn't actually grounded in [Transcript.segments]. Until a real local LLM is
 * integrated, the app uses [UnavailableMeetingIntelligenceEngine]. See docs/AI_ARCHITECTURE.md.
 */
interface MeetingIntelligenceEngine {
    suspend fun generateTitle(transcript: Transcript, fallback: String): AiResult<String>
    /**
     * [recordingType] and [customContext] only ever narrow what the extraction prompt pays
     * attention to (see [RecordingType.focusGuidance]) — they never weaken the requirement that
     * every extracted item must be grounded in [transcript].
     */
    suspend fun processMeeting(
        transcript: Transcript,
        meetingTitle: String,
        recordingType: RecordingType = RecordingType.GENERAL,
        customContext: String? = null
    ): AiResult<MeetingSummary>
    suspend fun askMeeting(
        question: String,
        transcript: Transcript,
        relevantSegments: List<TranscriptSegment>
    ): AiResult<ChatMessage>
}

/**
 * Default meeting-intelligence implementation until a real local LLM (via [LanguageModel]) is
 * integrated. Deliberately does not call any cloud API and does not synthesize a summary,
 * decisions, action items, or answers from keyword rules.
 */
class UnavailableMeetingIntelligenceEngine(
    private val languageModel: LanguageModel = UnavailableLanguageModel()
) : MeetingIntelligenceEngine {

    override suspend fun generateTitle(transcript: Transcript, fallback: String): AiResult<String> =
        AiResult.ModelUnavailable(modelId = "local-llm", message = "No local language model is installed on this device.")

    override suspend fun processMeeting(
        transcript: Transcript,
        meetingTitle: String,
        recordingType: RecordingType,
        customContext: String?
    ): AiResult<MeetingSummary> =
        AiResult.ModelUnavailable(modelId = "local-llm", message = "No local meeting intelligence model is installed on this device.")

    override suspend fun askMeeting(
        question: String,
        transcript: Transcript,
        relevantSegments: List<TranscriptSegment>
    ): AiResult<ChatMessage> =
        AiResult.ModelUnavailable(modelId = "local-llm", message = "No local meeting intelligence model is installed on this device.")
}
