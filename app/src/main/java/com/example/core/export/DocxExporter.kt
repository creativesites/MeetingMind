package com.example.core.export

import com.example.core.common.Formatters
import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.Meeting
import com.example.core.model.TranscriptSegment
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hand-writes a minimal, valid OOXML (.docx) package — no external library. A .docx is just a ZIP
 * with a few required XML parts ([Content_Types].xml, _rels/.rels, word/document.xml); Word,
 * Google Docs, and LibreOffice all open a package built from exactly these three parts. Formatting
 * uses direct run properties (bold/size/color) rather than named styles, since defining a full
 * styles.xml isn't necessary for a document this simple to render correctly.
 */
object DocxExporter {

    private fun writeHeader(doc: DocxBody, meeting: Meeting) {
        doc.title(meeting.title)
        doc.meta(
            "MeetMind  •  ${Formatters.formatDateRelative(meeting.createdAt)}  •  " +
                "${Formatters.formatDurationHms(meeting.durationMs)}  •  ${meeting.recordingType.displayName}"
        )
    }

    fun summary(meeting: Meeting, summaryText: String?, decisions: List<Decision>, actionItems: List<ActionItem>, out: OutputStream) {
        val doc = DocxBody()
        writeHeader(doc, meeting)
        doc.heading("Summary")
        doc.body(summaryText?.takeIf { it.isNotBlank() } ?: "No summary is available for this recording yet.")
        if (decisions.isNotEmpty()) {
            doc.heading("Decisions")
            decisions.forEach { doc.bullet("[${it.type.name}] ${it.text}") }
        }
        if (actionItems.isNotEmpty()) {
            doc.heading("Action Items")
            actionItems.forEach { doc.bullet("${if (it.isCompleted) "☑" else "☐"} ${actionItemSummary(it)}") }
        }
        doc.writeTo(out)
    }

    fun actionItems(meeting: Meeting, actionItems: List<ActionItem>, out: OutputStream) {
        val doc = DocxBody()
        writeHeader(doc, meeting)
        doc.heading("Action Items")
        if (actionItems.isEmpty()) {
            doc.body("No action items were found in this recording.")
        } else {
            actionItems.forEach { doc.bullet("${if (it.isCompleted) "☑" else "☐"} ${actionItemSummary(it)}") }
        }
        doc.writeTo(out)
    }

    fun transcript(meeting: Meeting, segments: List<TranscriptSegment>, out: OutputStream) {
        val doc = DocxBody()
        writeHeader(doc, meeting)
        doc.heading("Transcript")
        if (segments.isEmpty()) {
            doc.body("No transcript is available for this recording yet.")
        } else {
            var lastSpeaker: String? = null
            segments.forEach { seg ->
                val speaker = seg.speakerName ?: "Unknown speaker"
                if (speaker != lastSpeaker) {
                    doc.heading("$speaker — ${Formatters.formatDurationHms(seg.startMs)}")
                    lastSpeaker = speaker
                }
                doc.body(seg.text)
            }
        }
        doc.writeTo(out)
    }

    private fun actionItemSummary(item: ActionItem): String {
        val assignee = item.assigneeName?.let { " — $it" } ?: ""
        val deadline = item.deadline?.let { " (due $it)" } ?: ""
        return "${item.task}$assignee$deadline"
    }

    /** Accumulates paragraphs and serializes the three required OOXML parts. */
    private class DocxBody {
        private val paragraphs = StringBuilder()

        fun title(text: String) = paragraph(text, bold = true, sizeHalfPoints = 40)
        fun meta(text: String) = paragraph(text, color = "666666", sizeHalfPoints = 18)
        fun heading(text: String) = paragraph(text, bold = true, sizeHalfPoints = 28)
        fun body(text: String) = paragraph(text, sizeHalfPoints = 22)
        fun bullet(text: String) = paragraph("•  $text", sizeHalfPoints = 22)

        private fun paragraph(text: String, bold: Boolean = false, color: String? = null, sizeHalfPoints: Int) {
            val rPr = buildString {
                append("<w:rPr>")
                if (bold) append("<w:b/>")
                if (color != null) append("<w:color w:val=\"$color\"/>")
                append("<w:sz w:val=\"$sizeHalfPoints\"/>")
                append("</w:rPr>")
            }
            paragraphs.append("<w:p>$rPr<w:r>$rPr<w:t xml:space=\"preserve\">${xmlEscape(text)}</w:t></w:r></w:p>")
        }

        fun writeTo(out: OutputStream) {
            val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr/></w:body></w:document>"""

            val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

            val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(contentTypesXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(relsXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(documentXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        private fun xmlEscape(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
