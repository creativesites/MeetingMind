package com.example.core.export

import android.content.Context
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NoteDocument
import com.example.core.model.ScriptureRef
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A recording as an export shows it. */
data class RecordingExportInfo(val title: String, val durationMs: Long, val summary: String?)

/**
 * A Bible passage ready to print. [text] is null when it couldn't be fetched; [attribution] is
 * the version's copyright line, which YouVersion's terms require wherever its text appears.
 */
data class ExportPassage(
    val reference: String,
    val text: String?,
    val versionAbbreviation: String?,
    val attribution: String?
)

/** Fetches passage text at export time. Text is never stored (docs/PLAN_V1.md §6). */
fun interface PassageSource {
    suspend fun passage(ref: ScriptureRef): ExportPassage?
}

data class NoteExportOptions(
    /** Private sections (a devotional's prayer, say) are left out unless the user opts in. */
    val includePrivateSections: Boolean = false,
    /** Which [NoteBlock.sectionKey]s count as private. Comes from the workflow's template. */
    val privateSectionKeys: Set<String> = emptySet(),
    val serifBody: Boolean = false
)

/** Turns a note into an [ExportDocument]. Pure, so every rule here is unit-tested. */
object NoteExportMapper {

    fun map(
        doc: NoteDocument,
        recordings: Map<String, RecordingExportInfo> = emptyMap(),
        passages: Map<String, ExportPassage> = emptyMap(),
        options: NoteExportOptions = NoteExportOptions(),
        locale: Locale = Locale.getDefault()
    ): ExportDocument {
        val attachments = doc.attachments.associateBy { it.id }
        val refs = doc.scriptureRefs.associateBy { it.id }
        val blocks = doc.blocks
            .sortedBy { it.position }
            .filter { options.includePrivateSections || it.sectionKey == null || it.sectionKey !in options.privateSectionKeys }
            .mapNotNull { block -> mapBlock(block, attachments, refs, recordings, passages) }

        val subtitle = buildList {
            (doc.note.eventDate ?: doc.note.createdAt).let {
                add(SimpleDateFormat("EEEE d MMMM yyyy", locale).format(Date(it)))
            }
            listOf("speaker", "church", "location").forEach { key ->
                doc.note.metadata[key]?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }.joinToString("  ·  ")

        val attributions = passages.values.mapNotNull { it.attribution?.takeIf { a -> a.isNotBlank() } }.distinct()
        return ExportDocument(
            title = doc.note.title.ifBlank { "Untitled note" },
            subtitle = subtitle,
            blocks = trimEmptyEdges(blocks),
            closingNotes = attributions,
            serifBody = options.serifBody
        )
    }

    private fun mapBlock(
        block: NoteBlock,
        attachments: Map<String, com.example.core.model.Attachment>,
        refs: Map<String, ScriptureRef>,
        recordings: Map<String, RecordingExportInfo>,
        passages: Map<String, ExportPassage>
    ): ExportBlock? = when (block.type) {
        NoteBlockType.PARAGRAPH -> ExportBlock.Paragraph(block.content)
        NoteBlockType.HEADING_1 -> ExportBlock.Heading(1, block.content)
        NoteBlockType.HEADING_2 -> ExportBlock.Heading(2, block.content)
        NoteBlockType.HEADING_3 -> ExportBlock.Heading(3, block.content)
        NoteBlockType.BULLET -> ExportBlock.ListItem(ListKind.BULLET, block.content, block.indent)
        NoteBlockType.NUMBERED -> ExportBlock.ListItem(ListKind.NUMBERED, block.content, block.indent)
        NoteBlockType.CHECKLIST -> ExportBlock.ListItem(ListKind.CHECKLIST, block.content, block.indent, block.checked)
        NoteBlockType.QUOTE -> ExportBlock.Quote(block.content)
        NoteBlockType.DIVIDER -> ExportBlock.Divider
        NoteBlockType.SCRIPTURE -> {
            val refId = block.payload[NoteBlock.PAYLOAD_SCRIPTURE_REF_ID]
            val passage = refId?.let { passages[it] }
            val fallbackReference = block.payload["reference"] ?: refId?.let { refs[it] }?.let { describe(it) }
            when {
                passage != null -> ExportBlock.Scripture(passage.reference, passage.text, passage.versionAbbreviation)
                fallbackReference != null -> ExportBlock.Scripture(fallbackReference, null, null)
                else -> null
            }
        }
        NoteBlockType.IMAGE -> block.payload[NoteBlock.PAYLOAD_ATTACHMENT_ID]?.let { attachments[it] }
            ?.let { ExportBlock.Image(it.path, it.caption ?: block.content.text.takeIf { t -> t.isNotBlank() }) }
        NoteBlockType.VIDEO, NoteBlockType.AUDIO -> {
            val attachment = block.payload[NoteBlock.PAYLOAD_ATTACHMENT_ID]?.let { attachments[it] }
            val kind = if (block.type == NoteBlockType.VIDEO) "Video" else "Audio clip"
            val label = attachment?.caption ?: block.content.text.takeIf { it.isNotBlank() }
            val length = attachment?.durationMs?.let { " · ${Formatters.formatDurationHms(it)}" } ?: ""
            ExportBlock.Paragraph(RichText.plain("[$kind${label?.let { ": $it" } ?: ""}$length]").let {
                it.applyStyle(com.example.core.notes.InlineStyle.ITALIC, 0, it.text.length)
            })
        }
        NoteBlockType.RECORDING -> block.payload[NoteBlock.PAYLOAD_MEETING_ID]?.let { recordings[it] }?.let {
            ExportBlock.Recording(
                title = it.title,
                detail = it.durationMs.takeIf { d -> d > 0 }?.let { d -> Formatters.formatDurationHms(d) },
                summary = it.summary
            )
        }
        NoteBlockType.TRANSCRIPT_EXCERPT -> {
            val speaker = block.payload[NoteBlock.PAYLOAD_SPEAKER]
            val at = block.payload[NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()?.let { Formatters.formatDurationHms(it) }
            val label = listOfNotNull(speaker, at).joinToString(" · ").takeIf { it.isNotBlank() }
            ExportBlock.Excerpt(label, block.content.text)
        }
        NoteBlockType.NOTE_LINK -> {
            val title = block.payload["title"] ?: block.content.text.takeIf { it.isNotBlank() } ?: "Linked note"
            ExportBlock.Paragraph(RichText.plain("→ $title"))
        }
    }

    /** Leading and trailing empty paragraphs are editor scaffolding, not content. */
    private fun trimEmptyEdges(blocks: List<ExportBlock>): List<ExportBlock> {
        fun ExportBlock.isBlank() = this is ExportBlock.Paragraph && text.text.isBlank()
        return blocks.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
    }

    /** "John 3:16–17"; the raw USFM form only for a book code the app doesn't know. */
    fun describe(ref: ScriptureRef): String {
        com.example.core.scripture.BibleBooks.byUsfm(ref.bookUsfm)?.let {
            return com.example.core.scripture.ScriptureReference(it, ref.chapter, ref.verseStart, ref.verseEnd).display()
        }
        val verses = when {
            ref.verseStart == null -> ""
            ref.verseEnd != null && ref.verseEnd != ref.verseStart -> ":${ref.verseStart}-${ref.verseEnd}"
            else -> ":${ref.verseStart}"
        }
        return "${ref.bookUsfm} ${ref.chapter}$verses"
    }
}

/** Gathers what a note's export needs and writes it in the chosen format. */
class NoteExportService(
    private val context: Context,
    private val database: MeetMindDatabase = MeetMindDatabase.getInstance(context),
    /** Verse text for scripture blocks, fetched at export time with its attribution. */
    private val passageSource: PassageSource? = com.example.core.scripture.ScriptureService(context).passageSource()
) {
    suspend fun export(
        noteId: String,
        format: ExportFormat,
        out: OutputStream,
        options: NoteExportOptions = NoteExportOptions()
    ): Boolean {
        val doc = NoteRepository(context, database).getDocument(noteId) ?: return false
        val document = build(doc, options)
        when (format) {
            ExportFormat.PDF -> PdfDocumentRenderer.render(document, out)
            ExportFormat.DOCX -> DocxDocumentRenderer.render(document, out)
            ExportFormat.MARKDOWN -> out.write(MarkdownDocumentRenderer.render(document).toByteArray(Charsets.UTF_8))
            else -> error("${format.displayName} is not a note export format")
        }
        return true
    }

    suspend fun build(doc: NoteDocument, options: NoteExportOptions): ExportDocument {
        val meetingIds = doc.blocks.filter { it.type == NoteBlockType.RECORDING }
            .mapNotNull { it.payload[NoteBlock.PAYLOAD_MEETING_ID] }
        val recordings = meetingIds.mapNotNull { id ->
            database.meetingDao().getMeetingById(id)?.let { id to RecordingExportInfo(it.title, it.durationMs, it.summaryText) }
        }.toMap()
        val passages = passageSource?.let { source ->
            doc.scriptureRefs.mapNotNull { ref -> runCatching { source.passage(ref) }.getOrNull()?.let { ref.id to it } }.toMap()
        } ?: emptyMap()
        return NoteExportMapper.map(doc, recordings, passages, options)
    }

    companion object {
        /** The formats a note can be exported as, in the order the export sheet lists them. */
        val NOTE_FORMATS = listOf(ExportFormat.PDF, ExportFormat.DOCX, ExportFormat.MARKDOWN)
    }
}
