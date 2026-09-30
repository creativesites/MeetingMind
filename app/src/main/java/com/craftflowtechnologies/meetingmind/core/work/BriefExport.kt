package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.export.ExportBlock
import com.craftflowtechnologies.meetingmind.core.export.ExportDocument
import com.craftflowtechnologies.meetingmind.core.export.ExportFormat
import com.craftflowtechnologies.meetingmind.core.export.ExportManager
import com.craftflowtechnologies.meetingmind.core.export.ExportTheme
import com.craftflowtechnologies.meetingmind.core.export.ListKind
import com.craftflowtechnologies.meetingmind.core.export.StatusChip
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A brief as a document for PDF, Word and Markdown (docs/PLAN_PROFESSIONAL.md D5.3). Every line
 * that has evidence carries a number, and the numbers lead to an appendix of the quotes, with the
 * date and time of the recording they were said in. Confidential material prints a
 * confidentiality line at the top.
 */
object BriefExport {
    const val CONFIDENTIAL_LINE = "Confidential. Prepared from material that stays on the device it was recorded on. Please don't forward."

    fun toDocument(brief: Brief, locale: Locale = Locale.getDefault()): ExportDocument {
        // Evidence gets a number the first time it is cited, in reading order.
        val numbers = LinkedHashMap<String, Int>()
        brief.evidence.forEachIndexed { i, e -> numbers[e.itemId] = i + 1; numbers[e.evidenceId] = i + 1 }
        fun marks(ids: List<String>) = ids.mapNotNull { numbers[it] }.distinct().sorted().joinToString("") { " [$it]" }
        fun plain(t: String) = RichText.plain(t)

        val blocks = mutableListOf<ExportBlock>()
        if (brief.confidential) blocks += ExportBlock.Quote(plain(CONFIDENTIAL_LINE))
        brief.progress?.let { (done, total) -> blocks += ExportBlock.Facts(listOf("Commitments" to "$done of $total completed")) }

        fun prose(title: String, sentences: List<BriefSentence>) {
            if (sentences.isEmpty()) return
            blocks += ExportBlock.Heading(1, plain(title))
            sentences.forEach { blocks += ExportBlock.Paragraph(plain(it.text + marks(it.cites))) }
        }
        fun lines(title: String, list: List<BriefLine>, kind: ListKind = ListKind.BULLET) {
            if (list.isEmpty()) return
            blocks += ExportBlock.Heading(1, plain(title))
            list.forEach { l -> blocks += ExportBlock.ListItem(kind, plain(l.text + (l.detail?.let { " ($it)" } ?: "") + marks(listOfNotNull(l.itemId)))) }
        }
        prose("Executive picture", brief.executive)
        lines("What changed", brief.changes)
        lines("Decisions", brief.decisions)
        lines("You owe", brief.youOwe)
        lines("They owe", brief.theyOwe)
        lines("Risks", brief.risks)
        lines("Open questions", brief.questions)
        lines("Next 7 days", brief.next7)
        lines("Decisions required", brief.decisionsRequired)
        prose("Recommended next conversation", brief.recommended)

        if (brief.evidence.isNotEmpty()) {
            blocks += ExportBlock.PageBreak
            blocks += ExportBlock.Heading(1, plain("Evidence"))
            val date = SimpleDateFormat("d MMM yyyy, HH:mm", locale)
            brief.evidence.forEachIndexed { i, e ->
                val label = listOfNotNull(
                    "[${i + 1}]", e.meetingTitle?.ifBlank { null }, e.meetingAt?.let { date.format(Date(it)) },
                    e.startMs?.let { "at ${com.craftflowtechnologies.meetingmind.core.common.Formatters.formatDurationHms(it)}" }
                ).joinToString(" · ")
                blocks += ExportBlock.Excerpt(label, e.quote)
            }
        }
        val generated = SimpleDateFormat("d MMM yyyy, HH:mm", locale).format(Date(brief.generatedAt))
        return ExportDocument(
            title = brief.title, subtitle = "${brief.target.kind.label} · $generated", blocks = blocks,
            closingNotes = listOf("Made with MeetingMind from what was said and decided. Every numbered line points to its quote."),
            theme = ExportTheme.BRIEF, statusChip = StatusChip(brief.status.label, attention = brief.status == BriefStatus.ATTENTION)
        )
    }

    fun write(brief: Brief, format: ExportFormat, out: OutputStream) = ExportManager.writeDocument(format, toDocument(brief), out)

    /** The brief as plain Markdown, for sharing as text. */
    fun markdown(brief: Brief): String = com.craftflowtechnologies.meetingmind.core.export.MarkdownDocumentRenderer.render(toDocument(brief))
}
