package com.example.core.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.core.common.Formatters
import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.Meeting
import com.example.core.model.TranscriptSegment
import java.io.OutputStream

/**
 * Renders a professional, paginated PDF via [PdfDocument] — Android's own PDF writer, no external
 * library needed. A single reusable page-flowing writer handles headings/body/page breaks so every
 * export (summary, action items, transcript) gets consistent MeetingMind-branded formatting instead
 * of an unformatted wall of text.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595 // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val BRAND_COLOR = -0xd2b25e // IndigoPrimary-ish, ARGB

    private class PageWriter(private val document: PdfDocument) {
        private var pageNumber = 1
        private var page: PdfDocument.Page = newPage()
        private var canvas: Canvas = page.canvas
        private var y = MARGIN

        private fun newPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            return document.startPage(info)
        }

        private fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = newPage()
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun title(text: String) {
            val paint = TextPaint().apply { color = BRAND_COLOR; textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (PAGE_WIDTH - 2 * MARGIN).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            ensureSpace(layout.height + 8f)
            canvas.save(); canvas.translate(MARGIN, y); layout.draw(canvas); canvas.restore()
            y += layout.height + 8f
        }

        fun metaLine(text: String) {
            val paint = TextPaint().apply { color = Color.DKGRAY; textSize = 10f }
            ensureSpace(14f)
            canvas.drawText(text, MARGIN, y + 10f, paint)
            y += 14f
        }

        fun heading(text: String) {
            y += 10f
            val paint = TextPaint().apply { color = Color.BLACK; textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
            ensureSpace(20f)
            canvas.drawText(text, MARGIN, y + 14f, paint)
            y += 22f
        }

        fun body(text: String) {
            val paint = TextPaint().apply { color = Color.BLACK; textSize = 11f }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (PAGE_WIDTH - 2 * MARGIN).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            ensureSpace(layout.height.toFloat())
            canvas.save(); canvas.translate(MARGIN, y); layout.draw(canvas); canvas.restore()
            y += layout.height + 6f
        }

        fun finish() {
            document.finishPage(page)
        }
    }

    private fun writeHeader(writer: PageWriter, meeting: Meeting) {
        writer.title(meeting.title)
        writer.metaLine(
            "MeetingMind  •  ${Formatters.formatDateRelative(meeting.createdAt)}  •  " +
                "${Formatters.formatDurationHms(meeting.durationMs)}  •  ${meeting.recordingType.displayName}"
        )
    }

    fun summary(meeting: Meeting, summaryText: String?, decisions: List<Decision>, actionItems: List<ActionItem>, out: OutputStream) {
        val document = PdfDocument()
        val writer = PageWriter(document)
        writeHeader(writer, meeting)
        writer.heading("Summary")
        writer.body(summaryText?.takeIf { it.isNotBlank() } ?: "No summary is available for this recording yet.")
        if (decisions.isNotEmpty()) {
            writer.heading("Decisions")
            decisions.forEach { writer.body("• [${it.type.name}] ${it.text}") }
        }
        if (actionItems.isNotEmpty()) {
            writer.heading("Action Items")
            actionItems.forEach { writer.body("${if (it.isCompleted) "[x]" else "[ ]"} ${actionItemSummary(it)}") }
        }
        writer.finish()
        document.writeTo(out)
        document.close()
    }

    fun actionItems(meeting: Meeting, actionItems: List<ActionItem>, out: OutputStream) {
        val document = PdfDocument()
        val writer = PageWriter(document)
        writeHeader(writer, meeting)
        writer.heading("Action Items")
        if (actionItems.isEmpty()) {
            writer.body("No action items were found in this recording.")
        } else {
            actionItems.forEach { writer.body("${if (it.isCompleted) "[x]" else "[ ]"} ${actionItemSummary(it)}") }
        }
        writer.finish()
        document.writeTo(out)
        document.close()
    }

    fun transcript(meeting: Meeting, segments: List<TranscriptSegment>, out: OutputStream) {
        val document = PdfDocument()
        val writer = PageWriter(document)
        writeHeader(writer, meeting)
        writer.heading("Transcript")
        if (segments.isEmpty()) {
            writer.body("No transcript is available for this recording yet.")
        } else {
            var lastSpeaker: String? = null
            segments.forEach { seg ->
                val speaker = seg.speakerName ?: "Unknown speaker"
                if (speaker != lastSpeaker) {
                    writer.heading("$speaker — ${Formatters.formatDurationHms(seg.startMs)}")
                    lastSpeaker = speaker
                }
                writer.body(seg.text)
            }
        }
        writer.finish()
        document.writeTo(out)
        document.close()
    }

    private fun actionItemSummary(item: ActionItem): String {
        val assignee = item.assigneeName?.let { " — $it" } ?: ""
        val deadline = item.deadline?.let { " (due $it)" } ?: ""
        return "${item.task}$assignee$deadline"
    }
}
