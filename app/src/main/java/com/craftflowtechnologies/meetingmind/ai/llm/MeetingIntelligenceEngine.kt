package com.craftflowtechnologies.meetingmind.ai.llm

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.model.AskPersonalizationContext
import com.craftflowtechnologies.meetingmind.core.model.ChatMessage
import com.craftflowtechnologies.meetingmind.core.model.MeetingSummary
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment

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
    /**
     * [meetingTitle] is used as the title fallback if the model's own synthesis doesn't produce
     * a usable one — see [MeetingSummary.title] and
     * [com.craftflowtechnologies.meetingmind.core.common.MeetingTitleGenerator].
     *
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

    /**
     * A dedicated, deliberately small title pass — separate from [processMeeting]'s synthesis so
     * a title survives a model that answered the big structured-JSON prompt badly, and so a
     * recording with a transcript is never left with a date-only title just because synthesis
     * failed.
     *
     * Implementations must use one short prompt over the first/last part of the transcript plus
     * [recordingType]'s focus, validate the candidate through
     * [com.craftflowtechnologies.meetingmind.core.common.MeetingTitleGenerator.sanitizeAndValidate],
     * and may retry exactly once with a stricter prompt. Never a second full synthesis.
     *
     * The default returns an honest "unavailable" so existing test doubles and the unavailable
     * engine do not need to implement a capability they do not have.
     */
    suspend fun suggestTitle(
        transcript: Transcript,
        recordingType: RecordingType = RecordingType.GENERAL
    ): AiResult<String> = AiResult.ModelUnavailable(
        modelId = "local-llm",
        message = "No title model is available."
    )

    suspend fun askMeeting(
        question: String,
        transcript: Transcript,
        relevantSegments: List<TranscriptSegment>,
        /** Phase 15 §8 — deliberately narrow, see [AskPersonalizationContext]'s own doc: never a
         * dump of everything the app knows about the user, only their name and whatever learned
         * vocabulary is relevant to this one question. */
        personalization: AskPersonalizationContext = AskPersonalizationContext()
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
        relevantSegments: List<TranscriptSegment>,
        personalization: AskPersonalizationContext
    ): AiResult<ChatMessage> =
        AiResult.ModelUnavailable(modelId = "local-llm", message = "No local meeting intelligence model is installed on this device.")
}
