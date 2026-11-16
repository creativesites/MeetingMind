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
import com.craftflowtechnologies.meetingmind.core.notes.NoteCitations
import com.craftflowtechnologies.meetingmind.core.work.Mark
import java.util.Locale

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
        summary: MeetingSummary? = null,
        userNotes: List<NoteBlock> = emptyList(),
        marks: List<Mark> = emptyList()
    ): AiResult<List<NoteBlock>> {
        if (segments.isEmpty()) return AiResult.Failed("There is no transcript to polish.")
        val prompt = buildPrompt(recordingType, segments, summary, userNotes, marks)
        val raw = when (val res = languageModel.generate(prompt, maxOutputTokens = MAX_OUTPUT_TOKENS)) {
            is AiResult.Success -> res.value
            else -> return AiResult.Failed("Note polish could not run.")
        }
        // A reply wrapped in a JSON object (any key) is unwrapped by MarkdownImport.parse for AI text.
        val cleaned = stripOuterFence(raw).trim()
        if (cleaned.isBlank()) return AiResult.Failed("Note polish returned empty output.")

        val numbered = numberedTranscript(segments)
        val parsed = MarkdownImport.parse(cleaned, noteId, BlockSource.AI)
            .filterNot { it.type == NoteBlockType.PARAGRAPH && it.content.text.isBlank() }
        if (parsed.isEmpty()) return AiResult.Failed("Note polish produced no usable blocks.")

        val withMeta = parsed.map { piece ->
            // [n] markers come out of the words and become the block's cited moments.
            val (content, cites) = if (piece.type.isText) NoteCitations.resolve(piece.content, numbered) else piece.content to emptyList<NoteCitations.Cite>()
            val block = piece.copy(content = content)
            val cited = cites.firstOrNull()?.segmentId?.let { id -> segments.firstOrNull { it.id == id } }
            val matchedSeg = cited ?: if (block.type != NoteBlockType.DIVIDER && block.type != NoteBlockType.HEADING_1 && block.type != NoteBlockType.HEADING_2) {
                findBestSegment(block.content.text, segments)
            } else null
            val payload = buildMap {
                putAll(block.payload)
                put(NoteBlock.PAYLOAD_MEETING_ID, meetingId)
                matchedSeg?.let { put(NoteBlock.PAYLOAD_START_MS, it.startMs.toString()) }
                if (cites.isNotEmpty()) put(NoteCitations.PAYLOAD, NoteCitations.encode(cites))
            }
            block.copy(
                sectionKey = KEY_POLISHED,
                payload = payload,
                sourceSegmentIds = (cites.mapNotNull { it.segmentId } + listOfNotNull(matchedSeg?.id)).distinct()
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
        summary: MeetingSummary?,
        userNotes: List<NoteBlock> = emptyList(),
        marks: List<Mark> = emptyList()
    ): String = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        if (recordingType in Workflows.faith) {
            appendLine()
            appendLine(FaithPrompts.FAITH_CONTRACT.trim())
        }
        appendLine()
        appendLine("Task: Transform this ${recordingType.displayName.lowercase()} transcript into a clean, well-structured, polished note.")
        appendLine("Formatting rules:")
        appendLine("- Output clean Markdown only (no intro or outro commentary). Write the Markdown itself: never wrap it in JSON, quotes or a code fence.")
        appendLine("- Use `##` for major sections and `###` for sub-topics.")
        appendLine("- Use `**bold**` for key terms, names, or emphasis; `- ` for bullet lists; `1. ` for ordered steps/points; `> ` for memorable verbatim quotes or key takeaways.")
        appendLine("- ${com.craftflowtechnologies.meetingmind.core.notes.AiMarkdown.MATH_AND_CHEMISTRY_RULE}")
        appendLine("- Do not tag sentences with speaker names or labels such as [Speaker 2]; name a speaker only where who said it matters.")
        appendLine("- Cite your sources: after a point taken from the transcript, add the number of the transcript line it came from in square brackets, for example [3] or [3, 4]. Use only line numbers shown below.")
        appendLine(workflowGuidance(recordingType))
        val authored = userNotes.filter {
            it.source == BlockSource.USER &&
                it.type != NoteBlockType.RECORDING &&
                it.type != NoteBlockType.DIVIDER &&
                it.content.text.isNotBlank()
        }
        if (authored.isNotEmpty() || marks.isNotEmpty()) {
            appendLine()
            appendLine("Co-authoring anchors written by the person during the session:")
            appendLine("- Keep and expand these exact priorities, topics, and questions using grounded facts from the transcript. Never invent facts not spoken in the transcript.")
            authored.take(40).forEach { block ->
                appendLine("- User note: ${block.content.text.trim().replace('\n', ' ')}")
            }
            marks.take(25).forEach { m ->
                val mm = (m.atMs / 60_000L).toInt()
                val ss = ((m.atMs / 1000L) % 60L).toInt()
                val stamp = String.format(Locale.US, "%02d:%02d", mm, ss)
                val note = m.text?.trim()?.takeIf { it.isNotEmpty() } ?: m.kind.name.lowercase()
                appendLine("- Marked moment [$stamp]: $note")
            }
        }
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
        RecordingType.MOTIVATION ->
            "- Organize for fast reading and high retention: `## Key Bytes (Quick Read)` (3–6 punchy, memorable takeaways), `## Core Mindset Shift`, `## Key Principles & Stories`, `## Memorable Quotes` (`> `), and `## Daily Habits & Challenge` (`- [ ]`)."
        RecordingType.LIFE_SKILLS ->
            "- Organize as an actionable playbook: `## Key Bytes (Quick Read)` (3–6 high-retention takeaways), `## Overview`, `## Step-by-Step Playbook` (`1. ` ordered steps), `## Do's & Mistakes to Avoid`, and `## Action Checklist` (`- [ ]`)."
        else ->
            "- Organize into `## Key Bytes (Quick Read)`, a brief Overview, Key Points (`- `), and Next Steps (`- [ ]`) if any were mentioned."
    }

    /** The transcript as the model sees it: numbered lines, as many as fit, so `[n]` cites a real moment. */
    private fun renderBoundedTranscript(segments: List<TranscriptSegment>): String {
        val named = manySpeakers(segments)
        return numberedTranscript(segments).entries.joinToString("") { (n, seg) -> lineFor(n, seg, named) }
    }

    /** Speaker names only help when more than one person speaks; one preacher's name on every line invites tags. */
    private fun manySpeakers(segments: List<TranscriptSegment>): Boolean =
        segments.mapNotNull { it.speakerName?.takeIf { n -> n.isNotBlank() } }.distinct().size > 1

    /** Line number (from 1) → segment, for the lines that fit in the model's context. */
    internal fun numberedTranscript(segments: List<TranscriptSegment>): Map<Int, TranscriptSegment> {
        val budgetChars = ((contextTokens - MAX_OUTPUT_TOKENS - PROMPT_OVERHEAD_TOKENS).coerceAtLeast(800)) * CHARS_PER_TOKEN
        val out = LinkedHashMap<Int, TranscriptSegment>()
        var used = 0
        val named = manySpeakers(segments)
        for ((i, seg) in segments.withIndex()) {
            val line = lineFor(i + 1, seg, named)
            if (used + line.length > budgetChars) break
            used += line.length
            out[i + 1] = seg
        }
        return out
    }

    private fun lineFor(n: Int, seg: TranscriptSegment, named: Boolean): String {
        val speaker = if (!named) "" else seg.speakerName?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
        return "[$n] $speaker${seg.cleanedText ?: seg.text}\n"
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
