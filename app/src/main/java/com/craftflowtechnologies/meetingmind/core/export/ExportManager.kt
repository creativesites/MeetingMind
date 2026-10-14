package com.craftflowtechnologies.meetingmind.core.export

import com.craftflowtechnologies.meetingmind.core.model.ActionItem
import com.craftflowtechnologies.meetingmind.core.model.Decision
import com.craftflowtechnologies.meetingmind.core.model.Meeting
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import java.io.OutputStream

/**
 * Single dispatch point from (format, content type) to the right exporter. Writes directly to
 * whatever [OutputStream] the caller provides — in practice, the destination the user picked via
 * the system's "Create Document" picker (Storage Access Framework), so exporting genuinely creates
 * a persistent file the user chose, distinct from [com.craftflowtechnologies.meetingmind.core.share.ShareHelper]'s "send to
 * another app" flow.
 */
object ExportManager {

    /** Writes a finished [ExportDocument] (a brief) as Markdown, PDF or Word. */
    fun writeDocument(format: ExportFormat, document: ExportDocument, out: OutputStream) {
        when (format) {
            ExportFormat.MARKDOWN -> out.write(MarkdownDocumentRenderer.render(document).toByteArray(Charsets.UTF_8))
            ExportFormat.PDF -> PdfDocumentRenderer.render(document, out)
            ExportFormat.DOCX -> DocxDocumentRenderer.render(document, out)
            else -> error("A brief can be exported as Markdown, PDF or Word")
        }
    }

    fun write(
        format: ExportFormat,
        contentType: ExportContentType,
        meeting: Meeting,
        segments: List<TranscriptSegment>,
        decisions: List<Decision>,
        actionItems: List<ActionItem>,
        summaryText: String?,
        out: OutputStream
    ) {
        when (format) {
            ExportFormat.MARKDOWN -> {
                val text = when (contentType) {
                    ExportContentType.TRANSCRIPT -> MarkdownExporter.transcript(meeting, segments)
                    ExportContentType.SUMMARY -> MarkdownExporter.summary(meeting, summaryText, decisions, actionItems)
                    ExportContentType.ACTION_ITEMS -> MarkdownExporter.actionItems(meeting, actionItems)
                }
                out.write(text.toByteArray(Charsets.UTF_8))
            }
            ExportFormat.CSV -> {
                val text = when (contentType) {
                    ExportContentType.TRANSCRIPT -> CsvExporter.transcript(segments)
                    ExportContentType.ACTION_ITEMS -> CsvExporter.actionItems(actionItems)
                    ExportContentType.SUMMARY -> error("CSV export is not supported for Summary")
                }
                out.write(text.toByteArray(Charsets.UTF_8))
            }
            ExportFormat.PDF -> when (contentType) {
                ExportContentType.TRANSCRIPT -> PdfExporter.transcript(meeting, segments, out)
                ExportContentType.SUMMARY -> PdfExporter.summary(meeting, summaryText, decisions, actionItems, out)
                ExportContentType.ACTION_ITEMS -> PdfExporter.actionItems(meeting, actionItems, out)
            }
            ExportFormat.DOCX -> when (contentType) {
                ExportContentType.TRANSCRIPT -> DocxExporter.transcript(meeting, segments, out)
                ExportContentType.SUMMARY -> DocxExporter.summary(meeting, summaryText, decisions, actionItems, out)
                ExportContentType.ACTION_ITEMS -> DocxExporter.actionItems(meeting, actionItems, out)
            }
            ExportFormat.SRT -> out.write(SubtitleExporter.srt(segments).toByteArray(Charsets.UTF_8))
            ExportFormat.VTT -> out.write(SubtitleExporter.vtt(segments).toByteArray(Charsets.UTF_8))
        }
    }
}
