package com.craftflowtechnologies.meetingmind.ai.notes

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.faith.FaithPrompts
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.MeetingSummary
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport

/**
 * AI note-polishing pass run after transcription when [com.craftflowtechnologies.meetingmind.core.model.NotePolishMode.DEEP]
 * is enabled.
 *
 * Turns a transcript (plus any extracted summary) into richly formatted, reader-ready [NoteBlock]s
 * (headings, sub-headings, bullets, numbered lists, checklists, quotes, tables, code/math blocks)
 * tailored to the [RecordingType], while honoring [TranscriptToolPrompts.FIDELITY_CONTRACT] and
 * [FaithPrompts.FAITH_CONTRACT].
 */
class NotePolishEngine(
    private val languageModel: LanguageModel,
    private val contextTokens: Int = DEFAULT_CONTEXT_TOKENS
) {
    suspend fun polish(
        noteId: String,
        meetingId: String,
        recordingType: RecordingType,
        segments: List<TranscriptSegment>,
        summary: MeetingSummary? = null
    ): AiResult<List<NoteBlock>> {
        if (segments.isEmpty()) return AiResult.Failed("There is no transcript to polish.")
        val prompt = buildPrompt(recordingType, segments, summary)
        val raw = when (val res = languageModel.generate(prompt, maxOutputTokens = MAX_OUTPUT_TOKENS)) {
            is AiResult.Success -> res.value
            else -> return AiResult.Failed("Note polish could not run.")
        }
        val cleaned = stripOuterFence(raw).trim()
        if (cleaned.isBlank()) return AiResult.Failed("Note polish returned empty output.")

        val parsed = MarkdownImport.parse(cleaned, noteId, BlockSource.AI)
            .filterNot { it.type == NoteBlockType.PARAGRAPH && it.content.text.isBlank() }
        if (parsed.isEmpty()) return AiResult.Failed("Note polish produced no usable blocks.")

        val withMeta = parsed.map { block ->
            val matchedSeg = if (block.type != NoteBlockType.DIVIDER && block.type != NoteBlockType.HEADING_1 && block.type != NoteBlockType.HEADING_2) {
                findBestSegment(block.content.text, segments)
            } else null
            val payload = buildMap {
                putAll(block.payload)
                put(NoteBlock.PAYLOAD_MEETING_ID, meetingId)
                matchedSeg?.let { put(NoteBlock.PAYLOAD_START_MS, it.startMs.toString()) }
            }
            block.copy(
                sectionKey = KEY_POLISHED,
                payload = payload,
                sourceSegmentIds = listOfNotNull(matchedSeg?.id)
            )
        }
        return AiResult.Success(withMeta)
    }

    private fun findBestSegment(text: String, segments: List<TranscriptSegment>): TranscriptSegment? {
        val tokens = tokenize(text)
        if (tokens.size < 2) return null
        var best: TranscriptSegment? = null
        var bestScore = 0
        for (seg in segments) {
            val segTokens = tokenize(seg.cleanedText ?: seg.text)
            if (segTokens.isEmpty()) continue
            val overlap = tokens.count { it in segTokens }
            if (overlap >= 2 && overlap > bestScore) {
                bestScore = overlap
                best = seg
            }
        }
        return best
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 }
            .toSet()

    internal fun buildPrompt(
        recordingType: RecordingType,
        segments: List<TranscriptSegment>,
        summary: MeetingSummary?
    ): String = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        if (recordingType in Workflows.faith) {
            appendLine()
            appendLine(FaithPrompts.FAITH_CONTRACT.trim())
        }
        appendLine()
        appendLine("Task: Transform this ${recordingType.displayName.lowercase()} transcript into a clean, well-structured, polished note.")
        appendLine("Formatting rules:")
        appendLine("- Output clean Markdown only (no intro or outro commentary).")
        appendLine("- Use `##` for major sections and `###` for sub-topics.")
        appendLine("- Use `**bold**` for key terms, names, or emphasis; `- ` for bullet lists; `1. ` for ordered steps/points; `> ` for memorable verbatim quotes or key takeaways.")
        appendLine(workflowGuidance(recordingType))
        summary?.summary?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Context summary:")
            appendLine(it)
        }
        appendLine()
        appendLine("Transcript:")
        append(renderBoundedTranscript(segments))
    }

    private fun workflowGuidance(type: RecordingType): String = when (type) {
        RecordingType.SERMON ->
            "- Organize the sermon clearly: Main Theme, Scripture Readings, Sermon Outline (ordered points with sub-bullets), Memorable Quotes (`> `), and Practical Application as stated by the preacher."
        RecordingType.LECTURE, RecordingType.RESEARCH ->
            "- Organize as high-yield study notes: Overview, Core Concepts & Definitions (`**Term**: explanation`), Detailed Breakdown by topic (`###`), Worked Examples or Evidence, and Key Takeaways (`> `)."
        RecordingType.BIBLE_STUDY, RecordingType.DEVOTIONAL ->
            "- Organize into Passage(s), Key Observations, Discussion / Insights, and Questions Raised."
        RecordingType.MEETING, RecordingType.CLIENT_CALL, RecordingType.ONE_ON_ONE, RecordingType.STANDUP ->
            "- Organize into Overview, Key Discussion Points, Decisions Made, and Action Items (`- [ ] owner: task`)."
        RecordingType.INTERVIEW, RecordingType.CONSULTATION ->
            "- Organize into Context, Key Questions & Answers, Notable Quotes (`> `), and Agreed Next Steps (`- [ ]`)."
        else ->
            "- Organize into a brief Overview, Key Points (`- `), and Next Steps (`- [ ]`) if any were mentioned."
    }

    private fun renderBoundedTranscript(segments: List<TranscriptSegment>): String {
        val budgetChars = ((contextTokens - MAX_OUTPUT_TOKENS - PROMPT_OVERHEAD_TOKENS).coerceAtLeast(800)) * CHARS_PER_TOKEN
        val sb = StringBuilder()
        for (seg in segments) {
            val speaker = seg.speakerName?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
            val line = "$speaker${seg.cleanedText ?: seg.text}\n"
            if (sb.length + line.length > budgetChars) break
            sb.append(line)
        }
        return sb.toString()
    }

    private fun stripOuterFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        val body = if (firstNewline >= 0) trimmed.substring(firstNewline + 1) else trimmed.drop(3)
        return body.removeSuffix("```").trim()
    }

    companion object {
        const val KEY_POLISHED = "ai_polished_notes"
        private const val DEFAULT_CONTEXT_TOKENS = 8_192
        private const val MAX_OUTPUT_TOKENS = 1_800
        private const val PROMPT_OVERHEAD_TOKENS = 700
        private const val CHARS_PER_TOKEN = 4
    }
}
