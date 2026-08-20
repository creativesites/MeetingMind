package com.example.core.share

import com.example.core.common.Formatters
import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.Meeting
import com.example.core.model.TranscriptSegment

/**
 * Builds the plain-text bodies handed to the Android Sharesheet (see [ShareHelper]). Every
 * formatter only renders data actually present on the domain model — no placeholder speaker
 * names, no invented timestamps, no summary text when none was ever generated.
 */
object ShareContentFormatter {

    fun transcriptText(meeting: Meeting, segments: List<TranscriptSegment>): String = buildString {
        appendLine(meeting.title)
        appendLine(Formatters.formatDateRelative(meeting.createdAt))
        appendLine()
        if (segments.isEmpty()) {
            appendLine("No transcript is available for this recording yet.")
        } else {
            segments.forEach { seg ->
                val speaker = seg.speakerName ?: "Unknown speaker"
                appendLine("[${Formatters.formatDurationHms(seg.startMs)}] $speaker: ${seg.text}")
            }
        }
    }.trimEnd()

    fun summaryText(meeting: Meeting, summary: String?, decisions: List<Decision>, actionItems: List<ActionItem>): String = buildString {
        appendLine(meeting.title)
        appendLine(Formatters.formatDateRelative(meeting.createdAt))
        appendLine("Duration: ${Formatters.formatDurationHms(meeting.durationMs)}")
        appendLine()
        appendLine(summary?.takeIf { it.isNotBlank() } ?: "No summary is available for this recording yet.")
        if (decisions.isNotEmpty()) {
            appendLine()
            appendLine("Decisions:")
            decisions.forEach { appendLine("- ${it.text}") }
        }
        if (actionItems.isNotEmpty()) {
            appendLine()
            appendLine("Action Items:")
            actionItems.forEach { appendLine("- ${actionItemLine(it)}") }
        }
    }.trimEnd()

    fun actionItemsText(meeting: Meeting, items: List<ActionItem>): String = buildString {
        appendLine("Action Items — ${meeting.title}")
        appendLine()
        if (items.isEmpty()) {
            appendLine("No action items were found in this recording.")
        } else {
            items.forEach { appendLine(actionItemLine(it)) }
        }
    }.trimEnd()

    private fun actionItemLine(item: ActionItem): String {
        val status = if (item.isCompleted) "[x]" else "[ ]"
        val assignee = item.assigneeName?.let { " — $it" } ?: ""
        val deadline = item.deadline?.let { " (due $it)" } ?: ""
        return "$status ${item.task}$assignee$deadline"
    }
}
