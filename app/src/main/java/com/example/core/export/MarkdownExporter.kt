package com.example.core.export

import com.example.core.common.Formatters
import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.Meeting
import com.example.core.model.TranscriptSegment

/**
 * Builds clean Markdown documents for export — never a wall of unformatted text. Only sections
 * with real data are rendered; nothing here pads a missing summary or empty transcript with
 * placeholder content.
 */
object MarkdownExporter {

    private fun header(meeting: Meeting): String = buildString {
        appendLine("# ${meeting.title}")
        appendLine()
        appendLine("**Date**: ${Formatters.formatDateRelative(meeting.createdAt)}  ")
        appendLine("**Duration**: ${Formatters.formatDurationHms(meeting.durationMs)}  ")
        appendLine("**Type**: ${meeting.recordingType.displayName}")
        appendLine()
    }

    fun summary(meeting: Meeting, summaryText: String?, decisions: List<Decision>, actionItems: List<ActionItem>): String = buildString {
        append(header(meeting))
        appendLine("## Summary")
        appendLine()
        appendLine(summaryText?.takeIf { it.isNotBlank() } ?: "No summary is available for this recording yet.")
        if (decisions.isNotEmpty()) {
            appendLine()
            appendLine("## Decisions")
            appendLine()
            decisions.forEach { d ->
                val confidence = d.confidence?.let { " *(${(it * 100).toInt()}% confidence)*" } ?: ""
                appendLine("- **[${d.type.name}]** ${d.text}$confidence")
            }
        }
        if (actionItems.isNotEmpty()) {
            appendLine()
            appendLine("## Action Items")
            appendLine()
            actionItems.forEach { appendLine(actionItemMarkdownLine(it)) }
        }
    }.trimEnd() + "\n"

    fun actionItems(meeting: Meeting, actionItems: List<ActionItem>): String = buildString {
        append(header(meeting))
        appendLine("## Action Items")
        appendLine()
        if (actionItems.isEmpty()) {
            appendLine("No action items were found in this recording.")
        } else {
            actionItems.forEach { appendLine(actionItemMarkdownLine(it)) }
        }
    }.trimEnd() + "\n"

    fun transcript(meeting: Meeting, segments: List<TranscriptSegment>): String = buildString {
        append(header(meeting))
        appendLine("## Transcript")
        appendLine()
        if (segments.isEmpty()) {
            appendLine("No transcript is available for this recording yet.")
        } else {
            var lastSpeaker: String? = null
            segments.forEach { seg ->
                val speaker = seg.speakerName ?: "Unknown speaker"
                if (speaker != lastSpeaker) {
                    appendLine()
                    appendLine("**$speaker** — ${Formatters.formatDurationHms(seg.startMs)}")
                    lastSpeaker = speaker
                }
                appendLine(seg.text)
            }
        }
    }.trimEnd() + "\n"

    private fun actionItemMarkdownLine(item: ActionItem): String {
        val checkbox = if (item.isCompleted) "[x]" else "[ ]"
        val assignee = item.assigneeName?.let { " — **$it**" } ?: ""
        val deadline = item.deadline?.let { " *(due $it)*" } ?: ""
        return "- $checkbox ${item.task}$assignee$deadline"
    }
}
