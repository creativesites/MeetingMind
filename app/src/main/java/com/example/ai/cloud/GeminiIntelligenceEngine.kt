package com.example.ai.cloud

import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.llm.MeetingIntelligenceEngine
import com.example.ai.llm.MeetingIntelligenceJsonParser
import com.example.ai.routing.AiModelRouter
import com.example.ai.routing.AiRoute
import com.example.ai.routing.DefaultAiModelRouter
import com.example.core.common.MeetingTitleGenerator
import com.example.core.model.AskPersonalizationContext
import com.example.core.model.ChatMessage
import com.example.core.model.MeetingSummary
import com.example.core.model.RecordingType
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import java.util.UUID

/**
 * Internet-mode meeting intelligence: Gemini Flash reasoning over an already-built transcript.
 *
 * It is given a **transcript**, never audio. The transcription engine decided what was said and
 * who said it; this layer decides what it means. Handing the reasoning model the audio as well
 * would let it quietly re-transcribe and answer from its own reading of the recording, and the app
 * would have two disagreeing accounts of the same meeting with no way to tell which one an action
 * item came from.
 *
 * Implements the same [MeetingIntelligenceEngine] interface as the on-device engine, so every
 * caller — the pipeline, Ask Meeting, the AI tools sheet — is unchanged by which one is in use.
 *
 * ### Grounding
 *
 * Extraction is structured output validated against the real transcript:
 * [MeetingIntelligenceJsonParser.parseExtraction] drops any `sourceSegmentIds` value that is not an
 * id of a segment actually passed in, so an item the model attached to an invented paragraph loses
 * its provenance rather than gaining a fabricated one.
 */
class GeminiIntelligenceEngine(
    private val transport: GeminiTransport,
    private val router: AiModelRouter = DefaultAiModelRouter,
    private val parser: MeetingIntelligenceJsonParser = MeetingIntelligenceJsonParser
) : MeetingIntelligenceEngine {

    override suspend fun processMeeting(
        transcript: Transcript,
        meetingTitle: String,
        recordingType: RecordingType,
        customContext: String?
    ): AiResult<MeetingSummary> {
        val model = modelIdOrNull() ?: return unavailable()
        if (transcript.segments.isEmpty()) {
            return AiResult.Failed("There is no transcript to analyze yet.")
        }

        val profile = recordingType.intelligenceProfile()
        val response = transport.execute(
            GeminiRequest(
                modelId = model,
                systemInstruction = EXTRACTION_SYSTEM_INSTRUCTION,
                prompt = buildExtractionPrompt(transcript, meetingTitle, recordingType, customContext),
                responseSchema = EXTRACTION_SCHEMA
            )
        )
        if (response !is AiResult.Success) {
            return AiResult.Failed(response.describeFailure() ?: "Cloud analysis failed.")
        }

        val validIds = transcript.segments.map { it.id }.toSet()
        val extraction = parser.parseExtraction(
            raw = response.value,
            meetingId = transcript.meetingId,
            validSegmentIds = validIds,
            speakerNameToId = transcript.segments
                .mapNotNull { segment -> segment.speakerName?.let { it to segment.speakerId } }
                .mapNotNull { (name, id) -> id?.let { name to it } }
                .toMap()
        )
        val synthesis = parser.parseSynthesis(response.value, fallbackTitle = meetingTitle)

        return AiResult.Success(
            MeetingSummary(
                title = MeetingTitleGenerator.sanitizeAndValidate(synthesis.title) ?: meetingTitle,
                summary = synthesis.summary,
                topics = synthesis.keyPoints,
                // The recording type decides what it even makes sense to extract; a lecture has no
                // action items to find, and reporting some anyway is overclaiming.
                decisions = if (profile.extractDecisions) extraction.decisions else emptyList(),
                actionItems = if (profile.extractActionItems) extraction.actionItems else emptyList(),
                questions = if (profile.extractQuestions) extraction.questions else emptyList(),
                followUps = if (profile.extractFollowUps) extraction.followUps else emptyList()
            )
        )
    }

    override suspend fun askMeeting(
        question: String,
        transcript: Transcript,
        relevantSegments: List<TranscriptSegment>,
        personalization: AskPersonalizationContext
    ): AiResult<ChatMessage> {
        val model = modelIdOrNull() ?: return unavailable()
        val passages = relevantSegments.ifEmpty { transcript.segments }
        if (passages.isEmpty()) {
            return AiResult.Failed("There is no transcript to answer from yet.")
        }

        val response = transport.execute(
            GeminiRequest(
                modelId = model,
                systemInstruction = ASK_SYSTEM_INSTRUCTION,
                prompt = buildAskPrompt(question, passages, personalization)
            )
        )
        if (response !is AiResult.Success) {
            return AiResult.Failed(response.describeFailure() ?: "Cloud analysis failed.")
        }

        return AiResult.Success(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                meetingId = transcript.meetingId,
                isUser = false,
                content = response.value.trim(),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun modelIdOrNull(): String? =
        if (!transport.isConfigured()) null else router.route(AiRoute.GEMINI_INTELLIGENCE).modelId

    private fun <T> unavailable(): AiResult<T> = AiResult.ModelUnavailable(
        modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
        message = "Internet mode isn't set up on this build. Offline processing is unaffected."
    )

    /**
     * Renders the transcript with the segment ids the model must cite. Ids are what make an action
     * item traceable back to a paragraph, a speaker turn, a set of word ids, a timestamp and
     * finally the audio — so they are in the prompt, and anything the model cites that is not one
     * of them is discarded by the parser.
     */
    internal fun buildExtractionPrompt(
        transcript: Transcript,
        meetingTitle: String,
        recordingType: RecordingType,
        customContext: String?
    ): String = buildString {
        appendLine("Recording title: $meetingTitle")
        val focus = customContext?.takeIf { it.isNotBlank() } ?: recordingType.focusGuidance()
        if (focus.isNotBlank()) appendLine("Focus: $focus")
        appendLine()
        appendLine("Transcript. Each paragraph is prefixed with the id you must cite for it:")
        for (segment in transcript.segments) {
            val speaker = segment.speakerName ?: "Unknown speaker"
            appendLine("[${segment.id}] $speaker: ${segment.cleanedText ?: segment.text}")
        }
    }

    internal fun buildAskPrompt(
        question: String,
        passages: List<TranscriptSegment>,
        personalization: AskPersonalizationContext
    ): String = buildString {
        personalization.userName?.takeIf { it.isNotBlank() }?.let {
            appendLine("The person asking is called $it.")
        }
        if (personalization.relevantVocabulary.isNotEmpty()) {
            appendLine("Terms this person uses: ${personalization.relevantVocabulary.joinToString(", ")}")
        }
        appendLine("Transcript passages, with their ids and start times in milliseconds:")
        for (passage in passages) {
            val speaker = passage.speakerName ?: "Unknown speaker"
            appendLine("[${passage.id} @${passage.startMs}] $speaker: ${passage.cleanedText ?: passage.text}")
        }
        appendLine()
        appendLine("Question: $question")
    }

    private companion object {
        const val EXTRACTION_SYSTEM_INSTRUCTION =
            "You analyse meeting transcripts. Every decision, action item, question and follow-up " +
                "you report must be supported by the transcript text you were given, and must cite " +
                "the ids of the paragraphs that support it. If the transcript does not support an " +
                "item, omit it — an empty list is a correct answer. Never infer an owner, a date or " +
                "a decision that was not actually stated. Do not use any outside knowledge here: " +
                "this task is about what this meeting contains, not about what is true in general."

        const val ASK_SYSTEM_INSTRUCTION =
            "You answer questions about a meeting using only the transcript passages supplied. If " +
                "the passages do not contain the answer, say so plainly rather than guessing. " +
                "Refer to what was said and when, so the answer can be checked against the " +
                "recording. If you mention anything that did not come from this meeting, label it " +
                "explicitly as outside information and keep it separate from what the meeting " +
                "actually said — never present the two as one."

        /**
         * Typed schema for extraction. Structured output rather than prose parsing is the point:
         * nothing downstream has to guess what an "action item" looked like in a paragraph of text.
         */
        const val EXTRACTION_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "title": { "type": "string" },
            "summary": { "type": "string" },
            "keyPoints": { "type": "array", "items": { "type": "string" } },
            "decisions": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "text": { "type": "string" },
                  "type": { "type": "string", "enum": ["DECISION", "SUGGESTION", "DISCUSSION", "POSSIBILITY"] },
                  "sourceSegmentIds": { "type": "array", "items": { "type": "string" } }
                },
                "required": ["text", "sourceSegmentIds"]
              }
            },
            "actionItems": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "task": { "type": "string" },
                  "assigneeName": { "type": "string" },
                  "deadline": { "type": "string" },
                  "sourceSegmentIds": { "type": "array", "items": { "type": "string" } }
                },
                "required": ["task", "sourceSegmentIds"]
              }
            },
            "questions": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "text": { "type": "string" },
                  "sourceSegmentIds": { "type": "array", "items": { "type": "string" } }
                },
                "required": ["text", "sourceSegmentIds"]
              }
            },
            "followUps": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "text": { "type": "string" },
                  "sourceSegmentIds": { "type": "array", "items": { "type": "string" } }
                },
                "required": ["text", "sourceSegmentIds"]
              }
            }
          },
          "required": ["title", "summary", "keyPoints"]
        }
        """
    }
}
