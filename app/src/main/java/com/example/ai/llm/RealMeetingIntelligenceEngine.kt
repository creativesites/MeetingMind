package com.example.ai.llm

import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.core.model.ChatMessage
import com.example.core.model.MeetingSummary
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import java.util.UUID

/**
 * Real structured Meeting Intelligence, built on top of a [LanguageModel] — never a generic
 * chatbot. Every extraction call instructs the model to only report what the transcript excerpt
 * it was shown actually supports, to classify decisions vs. suggestions vs. discussion vs.
 * possibility rather than collapsing that distinction, and to return empty arrays rather than pad
 * with invented content. See docs/AI_ARCHITECTURE.md "No-Hallucination Requirement" for the exact
 * failure modes this is designed to avoid.
 *
 * Long transcripts are split by [TranscriptChunker] into pieces sized to the real installed
 * model's context length (never an arbitrary constant), extracted independently per chunk, then
 * combined with one final synthesis call for the title/summary/key points.
 */
class RealMeetingIntelligenceEngine(
    private val languageModel: LanguageModel,
    private val contextLengthTokens: Int
) : MeetingIntelligenceEngine {

    override suspend fun generateTitle(transcript: Transcript, fallback: String): AiResult<String> {
        if (transcript.segments.isEmpty()) return AiResult.Success(fallback)
        val firstChunk = TranscriptChunker.chunk(transcript.segments, contextLengthTokens).firstOrNull()
            ?: return AiResult.Success(fallback)
        val prompt = buildTitlePrompt(firstChunk.segments)
        return when (val result = languageModel.generate(prompt, maxOutputTokens = TITLE_OUTPUT_TOKENS)) {
            is AiResult.Success -> {
                val title = result.value.trim().trim('"').take(MAX_TITLE_LENGTH)
                AiResult.Success(title.ifBlank { fallback })
            }
            else -> result
        }
    }

    override suspend fun processMeeting(transcript: Transcript, meetingTitle: String): AiResult<MeetingSummary> {
        if (transcript.segments.isEmpty()) {
            return AiResult.Failed("No transcript is available to analyze.")
        }
        val chunks = TranscriptChunker.chunk(transcript.segments, contextLengthTokens)
        val speakerNameToId = transcript.segments
            .mapNotNull { seg -> seg.speakerName?.let { it.lowercase() to (seg.speakerId ?: return@mapNotNull null) } }
            .toMap()

        val decisions = mutableListOf<com.example.core.model.Decision>()
        val actionItems = mutableListOf<com.example.core.model.ActionItem>()
        val questions = mutableListOf<com.example.core.model.Question>()
        val followUps = mutableListOf<com.example.core.model.FollowUp>()
        val chunkSummaries = mutableListOf<String>()
        var anyChunkSucceeded = false

        for (chunk in chunks) {
            val validSegmentIds = chunk.segments.map { it.id }.toSet()
            val prompt = buildExtractionPrompt(chunk.segments)
            val result = languageModel.generate(prompt, maxOutputTokens = EXTRACTION_OUTPUT_TOKENS)
            val rawText = (result as? AiResult.Success)?.value ?: continue
            anyChunkSucceeded = true
            val extraction = MeetingIntelligenceJsonParser.parseExtraction(rawText, transcript.meetingId, validSegmentIds, speakerNameToId)
            decisions += extraction.decisions
            actionItems += extraction.actionItems
            questions += extraction.questions
            followUps += extraction.followUps
            if (extraction.briefSummary.isNotBlank()) chunkSummaries += extraction.briefSummary
        }

        if (!anyChunkSucceeded) {
            return AiResult.Failed("Local meeting intelligence could not analyze this transcript (the local language model produced no usable output).")
        }

        val synthesisPrompt = buildSynthesisPrompt(chunkSummaries, decisions, actionItems)
        val synthesis = when (val result = languageModel.generate(synthesisPrompt, maxOutputTokens = SYNTHESIS_OUTPUT_TOKENS)) {
            is AiResult.Success -> MeetingIntelligenceJsonParser.parseSynthesis(result.value, meetingTitle)
            else -> SynthesisResult(meetingTitle, "", emptyList())
        }

        return AiResult.Success(
            MeetingSummary(
                title = synthesis.title,
                summary = synthesis.summary,
                topics = synthesis.keyPoints,
                decisions = decisions,
                actionItems = actionItems,
                questions = questions,
                followUps = followUps
            )
        )
    }

    override suspend fun askMeeting(
        question: String,
        transcript: Transcript,
        relevantSegments: List<TranscriptSegment>
    ): AiResult<ChatMessage> {
        val candidateSegments = relevantSegments.ifEmpty { transcript.segments }
        if (candidateSegments.isEmpty()) {
            return AiResult.Failed("No transcript is available to answer from.")
        }
        // Ask Meeting is grounded only in whatever fits one chunk of the real model's context
        // budget. For a long meeting with no pre-filtered relevantSegments this can miss content
        // outside that first chunk — see docs/AI_ARCHITECTURE.md "Known Limitations".
        val chunk = TranscriptChunker.chunk(candidateSegments, contextLengthTokens).firstOrNull()
            ?: return AiResult.Failed("Transcript is too short to analyze.")
        val prompt = buildAskPrompt(question, chunk.segments)
        return when (val result = languageModel.generate(prompt, maxOutputTokens = ASK_OUTPUT_TOKENS)) {
            is AiResult.Success -> AiResult.Success(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    meetingId = transcript.meetingId,
                    isUser = false,
                    content = result.value.trim(),
                    sourceTimestamps = chunk.segments.map { it.startMs },
                    sourceQuotes = chunk.segments.map { it.text }.take(MAX_SOURCE_QUOTES)
                )
            )
            else -> AiResult.Failed(result.describeFailure() ?: "Ask Meeting is unavailable.")
        }
    }

    private fun buildTitlePrompt(segments: List<TranscriptSegment>): String {
        val excerpt = segments.joinToString("\n") { "${it.speakerName ?: "Unknown"}: ${it.text}" }
        return """
            Read this real meeting transcript excerpt and write a short, specific title (under 10 words) describing what the meeting was actually about. Do not use a generic title like "Team Meeting" unless nothing more specific is supported by the text. Respond with only the title, no quotes, no extra text.

            Transcript excerpt:
            $excerpt
        """.trimIndent()
    }

    private fun buildExtractionPrompt(segments: List<TranscriptSegment>): String {
        val transcriptText = segments.joinToString("\n") { seg ->
            "[${seg.id}] ${seg.speakerName ?: "Unknown speaker"}: ${seg.text}"
        }
        return """
            You are analyzing a real meeting transcript excerpt. Extract ONLY information explicitly supported by the transcript below. Never invent names, dates, deadlines, decisions, or commitments that are not actually stated.

            Classify every candidate decision carefully:
            - DECISION: the group explicitly agreed on or finalized something.
            - SUGGESTION: something proposed but not confirmed as final.
            - DISCUSSION: a topic discussed without resolution.
            - POSSIBILITY: a tentative idea, guess, or "maybe"/"probably" statement.
            A statement like "I think we should launch around the 15th" is a POSSIBILITY, never a DECISION.

            For every item, list the transcript line ids it came from in "sourceSegmentIds", copied exactly from the [id] markers below — never invent an id.

            If there is no clear evidence for a category, return an empty array for it. Do not pad with generic filler.

            Respond with ONLY a single JSON object, no markdown, no commentary, matching exactly this shape:
            {"decisions":[{"text":string,"type":"DECISION"|"SUGGESTION"|"DISCUSSION"|"POSSIBILITY","sourceSegmentIds":[string]}],"actionItems":[{"task":string,"assigneeName":string|null,"deadline":string|null,"sourceSegmentIds":[string]}],"questions":[{"question":string,"askedBy":string|null,"sourceSegmentIds":[string]}],"followUps":[{"description":string,"owner":string|null,"deadline":string|null,"sourceSegmentIds":[string]}],"briefSummary":string}

            Transcript excerpt:
            $transcriptText
        """.trimIndent()
    }

    private fun buildSynthesisPrompt(
        chunkSummaries: List<String>,
        decisions: List<com.example.core.model.Decision>,
        actionItems: List<com.example.core.model.ActionItem>
    ): String {
        val evidence = buildString {
            if (chunkSummaries.isNotEmpty()) {
                appendLine("Section summaries:")
                chunkSummaries.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
            }
            if (decisions.isNotEmpty()) {
                appendLine("Decisions/suggestions found:")
                decisions.forEach { appendLine("- [${it.type}] ${it.text}") }
            }
            if (actionItems.isNotEmpty()) {
                appendLine("Action items found:")
                actionItems.forEach { item -> appendLine("- ${item.task}" + (item.assigneeName?.let { " (assignee: $it)" } ?: "")) }
            }
            if (isEmpty()) appendLine("(No decisions, action items, or notable sections were extracted from this transcript.)")
        }
        return """
            Based only on the real extracted meeting evidence below — not your own general knowledge — write a concise meeting title and executive summary. Do not invent facts not present below. If the evidence is sparse, keep the summary short rather than padding it with generic statements like "the meeting was productive".

            Respond with ONLY a JSON object, no markdown: {"title":string,"summary":string,"keyPoints":[string]}

            Evidence:
            $evidence
        """.trimIndent()
    }

    private fun buildAskPrompt(question: String, segments: List<TranscriptSegment>): String {
        val excerpt = segments.joinToString("\n") { "${it.speakerName ?: "Unknown"}: ${it.text}" }
        return """
            Answer the question below using ONLY the real meeting transcript excerpt provided. If the transcript does not contain the answer, say so plainly instead of guessing.

            Transcript excerpt:
            $excerpt

            Question: $question
        """.trimIndent()
    }

    private companion object {
        const val TITLE_OUTPUT_TOKENS = 24
        const val EXTRACTION_OUTPUT_TOKENS = 700
        const val SYNTHESIS_OUTPUT_TOKENS = 500
        const val ASK_OUTPUT_TOKENS = 300
        const val MAX_TITLE_LENGTH = 80
        const val MAX_SOURCE_QUOTES = 3
    }
}
