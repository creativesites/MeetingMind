package com.example.ai.llm

import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.core.common.FillerWordCleaner
import com.example.core.model.ChatMessage
import com.example.core.model.IntelligenceProfile
import com.example.core.model.MeetingSummary
import com.example.core.model.RecordingType
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

    override suspend fun processMeeting(
        transcript: Transcript,
        meetingTitle: String,
        recordingType: RecordingType,
        customContext: String?
    ): AiResult<MeetingSummary> {
        if (transcript.segments.isEmpty()) {
            return AiResult.Failed("No transcript is available to analyze.")
        }
        val chunks = TranscriptChunker.chunk(transcript.segments, contextLengthTokens)
        val speakerNameToId = transcript.segments
            .mapNotNull { seg -> seg.speakerName?.let { it.lowercase() to (seg.speakerId ?: return@mapNotNull null) } }
            .toMap()

        // The user's own focus guidance for CUSTOM always takes precedence over the generic
        // per-type guidance when both could apply; the grounding rules in buildExtractionPrompt
        // are never affected by either.
        val focusGuidance = if (recordingType == RecordingType.CUSTOM && !customContext.isNullOrBlank()) {
            "The user described what to focus on: \"${customContext.trim()}\". Use this to decide what matters most, but it never overrides the grounding rule above — still only extract what the transcript actually supports."
        } else {
            recordingType.focusGuidance()
        }

        // What kind of output actually makes sense for this recording — MeetingMind is not a
        // meeting-only tool, so a Lecture or Idea is never asked for decisions/action items in
        // the first place, rather than asked and told (unreliably, via prose alone) to leave
        // them empty. See IntelligenceProfile's doc for why this exists.
        val profile = recordingType.intelligenceProfile()

        val decisions = mutableListOf<com.example.core.model.Decision>()
        val actionItems = mutableListOf<com.example.core.model.ActionItem>()
        val questions = mutableListOf<com.example.core.model.Question>()
        val followUps = mutableListOf<com.example.core.model.FollowUp>()
        val chunkSummaries = mutableListOf<String>()
        var anyChunkSucceeded = false

        for (chunk in chunks) {
            val validSegmentIds = chunk.segments.map { it.id }.toSet()
            val prompt = buildExtractionPrompt(chunk.segments, focusGuidance, profile)
            val result = languageModel.generate(prompt, maxOutputTokens = EXTRACTION_OUTPUT_TOKENS)
            val rawText = (result as? AiResult.Success)?.value ?: continue
            anyChunkSucceeded = true
            val rawExtraction = MeetingIntelligenceJsonParser.parseExtraction(rawText, transcript.meetingId, validSegmentIds, speakerNameToId)
            // Defense in depth: the prompt above never asks for a disabled category, but a model
            // that ignores instructions must never be trusted to have actually left it out —
            // dropped here rather than assumed, the same discipline already applied to
            // hallucinated source-segment-id citations.
            val extraction = rawExtraction.copy(
                decisions = if (profile.extractDecisions) rawExtraction.decisions else emptyList(),
                actionItems = if (profile.extractActionItems) rawExtraction.actionItems else emptyList(),
                questions = if (profile.extractQuestions) rawExtraction.questions else emptyList(),
                followUps = if (profile.extractFollowUps) rawExtraction.followUps else emptyList()
            )
            decisions += extraction.decisions
            actionItems += extraction.actionItems
            questions += extraction.questions
            followUps += extraction.followUps
            if (extraction.briefSummary.isNotBlank()) {
                chunkSummaries += extraction.briefSummary
            } else {
                // The model answered but its JSON didn't parse (a very common failure mode for
                // small on-device models faced with a nested schema). Its prose is still real,
                // transcript-grounded output — salvaging it as this chunk's summary is strictly
                // better than discarding a real answer and reporting nothing.
                MeetingIntelligenceJsonParser.salvagePlainSummary(rawText)
                    ?.let { chunkSummaries += it }
            }
        }

        if (!anyChunkSucceeded) {
            return AiResult.Failed("Local meeting intelligence could not analyze this transcript (the local language model produced no usable output).")
        }

        // Synthesis is shown the REAL transcript, not just the extracted items. Deriving the
        // summary purely from extraction output was the single biggest quality bug in this
        // engine: a short or casual recording legitimately contains no decisions and no action
        // items, so the evidence block came back empty and the model — having never been shown
        // what was actually said — could only produce "nothing specific was discussed" about a
        // recording with perfectly good content in it.
        val synthesisPrompt = buildSynthesisPrompt(transcript.segments, chunkSummaries, decisions, actionItems, focusGuidance, profile)
        val synthesis = when (val result = languageModel.generate(synthesisPrompt, maxOutputTokens = SYNTHESIS_OUTPUT_TOKENS)) {
            is AiResult.Success -> MeetingIntelligenceJsonParser.parseSynthesis(result.value, meetingTitle)
            else -> SynthesisResult(meetingTitle, "", emptyList())
        }

        // If synthesis itself failed or returned an empty summary, the per-chunk summaries are
        // still real model output grounded in the transcript — use them rather than showing the
        // user an empty summary for a recording that was analyzed successfully.
        val summaryText = synthesis.summary.ifBlank { chunkSummaries.joinToString(" ").trim() }

        return AiResult.Success(
            MeetingSummary(
                title = synthesis.title,
                summary = summaryText,
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

    /**
     * The extraction schema is built to match [profile] — a Lecture or Idea is never asked for
     * "decisions"/"actionItems" in the first place, rather than asked for them and relied on to
     * leave the arrays empty via prose instruction alone (a small model following a schema it was
     * actually given is far more reliable than one asked to selectively ignore part of it). See
     * [MeetingIntelligenceGroundingTest] for the parsing side of this contract.
     */
    private fun buildExtractionPrompt(segments: List<TranscriptSegment>, focusGuidance: String, profile: IntelligenceProfile): String {
        val transcriptText = renderSegments(segments, includeIds = true)
        val focusLine = if (focusGuidance.isNotBlank()) "\n            $focusGuidance\n" else ""

        val schemaFields = mutableListOf("\"briefSummary\":string")
        if (profile.extractDecisions) schemaFields += "\"decisions\":[{\"text\":string,\"type\":\"DECISION\"|\"SUGGESTION\"|\"DISCUSSION\"|\"POSSIBILITY\",\"sourceSegmentIds\":[string]}]"
        if (profile.extractActionItems) schemaFields += "\"actionItems\":[{\"task\":string,\"assigneeName\":string|null,\"deadline\":string|null,\"sourceSegmentIds\":[string]}]"
        if (profile.extractQuestions) schemaFields += "\"questions\":[{\"question\":string,\"askedBy\":string|null,\"sourceSegmentIds\":[string]}]"
        if (profile.extractFollowUps) schemaFields += "\"followUps\":[{\"description\":string,\"owner\":string|null,\"deadline\":string|null,\"sourceSegmentIds\":[string]}]"
        val schema = "{${schemaFields.joinToString(",")}}"

        val listCategories = listOfNotNull(
            "decisions".takeIf { profile.extractDecisions },
            "action items".takeIf { profile.extractActionItems },
            "questions".takeIf { profile.extractQuestions },
            "follow-ups".takeIf { profile.extractFollowUps }
        )
        val listsGuidance = if (listCategories.isNotEmpty()) {
            "\n            The ${listCategories.joinToString(", ")} list(s) above may legitimately be empty — many recordings have none, and an empty array is the correct, honest answer there. Never invent an item to fill a list.\n"
        } else ""

        val decisionGuidance = if (profile.extractDecisions) {
            """

            Classify every candidate decision carefully:
            - DECISION: the group explicitly agreed on or finalized something.
            - SUGGESTION: something proposed but not confirmed as final.
            - DISCUSSION: a topic discussed without resolution.
            - POSSIBILITY: a tentative idea, guess, or "maybe"/"probably" statement.
            A statement like "I think we should launch around the 15th" is a POSSIBILITY, never a DECISION.
            """.trimIndent()
        } else ""

        val sourceIdGuidance = if (schemaFields.size > 1) {
            "\n            For every item, list the transcript line ids it came from in \"sourceSegmentIds\", copied exactly from the [id] markers below — never invent an id.\n"
        } else ""

        return """
            You are analyzing a real transcript excerpt. Extract ONLY information explicitly supported by the transcript below. Never invent names, dates, deadlines, decisions, or commitments that are not actually stated.
            $focusLine
            "briefSummary" is REQUIRED and must always be filled in: 2-3 plain sentences saying what this excerpt is actually about, in the speaker's own subject matter. Write it even when nothing was decided and nothing was assigned — a personal note, a passing idea, or a casual chat still has real content to describe. Never write that nothing was discussed.
            $listsGuidance$decisionGuidance$sourceIdGuidance
            Respond with ONLY a single JSON object, no markdown, no commentary, matching exactly this shape:
            $schema

            Transcript excerpt:
            $transcriptText
        """.trimIndent()
    }

    /**
     * Renders transcript segments into prompt text. Filler words are always stripped here (see
     * [FillerWordCleaner]) regardless of the user's display preference: hesitation noise costs
     * prompt tokens that a small context window cannot spare and measurably degrades a small
     * model's ability to follow the surrounding instructions. This never touches stored text.
     */
    private fun renderSegments(segments: List<TranscriptSegment>, includeIds: Boolean): String =
        segments.joinToString("\n") { seg ->
            val speaker = seg.speakerName ?: "Unknown speaker"
            val text = FillerWordCleaner.clean(seg.text)
            if (includeIds) "[${seg.id}] $speaker: $text" else "$speaker: $text"
        }

    private fun buildSynthesisPrompt(
        allSegments: List<TranscriptSegment>,
        chunkSummaries: List<String>,
        decisions: List<com.example.core.model.Decision>,
        actionItems: List<com.example.core.model.ActionItem>,
        focusGuidance: String,
        profile: IntelligenceProfile
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
            if (isEmpty()) appendLine("(Summarize from the transcript below.)")
        }
        val transcriptExcerpt = renderTranscriptWithinBudget(allSegments, evidence.length)
        val focusLine = if (focusGuidance.isNotBlank()) "\n            $focusGuidance\n" else ""
        return """
            Write a concise, specific title and summary of the recording below. Use ONLY what the transcript and evidence actually contain — never your own general knowledge, and never invented facts.
            $focusLine
            The summary must describe what was actually said, in its real subject matter. A recording with no decisions and no assigned tasks is still summarizable — a quick note, an idea, or a casual conversation all have real content. Never respond that nothing specific was discussed, and never pad with filler like "the meeting was productive". If the recording is short, a single accurate sentence is the right answer.

            The title must be under 10 words, describe what this recording was actually about, and contain no surrounding quotes. Do not use a generic title like "Team Meeting" or "Meeting Summary" unless nothing more specific is supported — a caller-provided fallback title is used automatically when this isn't possible, so an unhelpful generic title here is worse than a short, honest one.

            "keyPoints" should be the few real ${profile.topicsLabel.lowercase()} actually raised, or an empty array if the recording is too short to have distinct points.

            Respond with ONLY a JSON object, no markdown: {"title":string,"summary":string,"keyPoints":[string]}

            Evidence:
            $evidence
            Transcript:
            $transcriptExcerpt
        """.trimIndent()
    }

    /**
     * Renders as much of the real transcript into the synthesis prompt as the model's actual
     * context window allows, after accounting for the instruction text, the evidence block, and
     * the model's own generated output. Short recordings — by far the most common case, and the
     * ones the old evidence-only prompt failed hardest on — fit whole.
     *
     * When a long transcript doesn't fit, the beginning and end are kept and the middle is
     * elided with an explicit marker, so the model is never misled into thinking it has been
     * shown the complete recording.
     */
    private fun renderTranscriptWithinBudget(segments: List<TranscriptSegment>, evidenceChars: Int): String {
        val availableChars = ((contextLengthTokens - SYNTHESIS_OUTPUT_TOKENS - SYNTHESIS_PROMPT_OVERHEAD_TOKENS)
            .coerceAtLeast(0) * APPROX_CHARS_PER_TOKEN).toInt() - evidenceChars
        if (availableChars < MIN_TRANSCRIPT_EXCERPT_CHARS) return ""

        val full = renderSegments(segments, includeIds = false)
        if (full.length <= availableChars) return full

        val half = (availableChars - ELISION_MARKER.length) / 2
        if (half <= 0) return full.take(availableChars)
        return full.take(half) + ELISION_MARKER + full.takeLast(half)
    }

    private fun buildAskPrompt(question: String, segments: List<TranscriptSegment>): String {
        val excerpt = renderSegments(segments, includeIds = false)
        return """
            Answer the question below using ONLY the real meeting transcript excerpt provided. If the transcript does not contain the answer, say so plainly instead of guessing.

            Transcript excerpt:
            $excerpt

            Question: $question
        """.trimIndent()
    }

    private companion object {
        const val EXTRACTION_OUTPUT_TOKENS = 700
        const val SYNTHESIS_OUTPUT_TOKENS = 500
        const val ASK_OUTPUT_TOKENS = 300
        const val MAX_SOURCE_QUOTES = 3
        /** Rough token cost of the synthesis prompt's fixed instruction text. */
        const val SYNTHESIS_PROMPT_OVERHEAD_TOKENS = 400
        /** Same conservative estimate [TranscriptChunker] uses — no tokenizer is reachable
         * outside a loaded engine instance, so both places approximate the same way. */
        const val APPROX_CHARS_PER_TOKEN = 3.5
        /** Below this there isn't enough room for a transcript excerpt to be worth including at
         * all; the evidence block alone is used instead of a misleading few words. */
        const val MIN_TRANSCRIPT_EXCERPT_CHARS = 200
        const val ELISION_MARKER = "\n[...transcript continues...]\n"
    }
}
