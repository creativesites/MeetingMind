package com.example.core.export

import com.example.core.common.Formatters
import com.example.core.model.ActionItem
import com.example.core.model.TranscriptSegment

/**
 * Builds RFC-4180-style CSV. Every field goes through [escape] — a transcript line or task
 * description containing a comma, quote, or newline must never corrupt the row structure.
 */
object CsvExporter {

    fun transcript(segments: List<TranscriptSegment>): String = buildString {
        appendLine(listOf("timestamp", "speaker", "text", "confidence").joinToString(",") { escape(it) })
        segments.forEach { seg ->
            val row = listOf(
                Formatters.formatDurationHms(seg.startMs),
                seg.speakerName ?: "",
                seg.text,
                seg.confidence?.toString() ?: ""
            )
            appendLine(row.joinToString(",") { escape(it) })
        }
    }.trimEnd() + "\n"

    fun actionItems(items: List<ActionItem>): String = buildString {
        appendLine(listOf("task", "assignee", "deadline", "confidence").joinToString(",") { escape(it) })
        items.forEach { item ->
            val row = listOf(
                item.task,
                item.assigneeName ?: "",
                item.deadline ?: "",
                item.confidence?.toString() ?: ""
            )
            appendLine(row.joinToString(",") { escape(it) })
        }
    }.trimEnd() + "\n"

    private fun escape(field: String): String {
        val needsQuoting = field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")
        return if (needsQuoting) "\"${field.replace("\"", "\"\"")}\"" else field
    }
}
