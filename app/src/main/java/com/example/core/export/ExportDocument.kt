package com.example.core.export

import com.example.core.notes.RichText

/**
 * A document ready to be written as Word, PDF or Markdown (docs/PLAN_V1.md §8).
 *
 * Every exporter reads this one model, so a note, a sermon and (later) a meeting summary all
 * come out looking like the same product, and a formatting fix lands in every format at once.
 * It holds only what a reader sees: no ids, no timestamps in milliseconds, no model names
 * beyond an optional provenance line.
 */
data class ExportDocument(
    val title: String,
    /** One line under the title, e.g. "Sunday 14 September · Pastor James · Grace Chapel". */
    val subtitle: String? = null,
    val blocks: List<ExportBlock>,
    /** Printed once at the end — e.g. every Bible version's copyright line. */
    val closingNotes: List<String> = emptyList(),
    /** Body text in a serif face. Faith notes use it (PLAN_V1 §7.8). */
    val serifBody: Boolean = false
)

enum class ListKind { BULLET, NUMBERED, CHECKLIST }

sealed interface ExportBlock {
    data class Heading(val level: Int, val text: RichText) : ExportBlock
    data class Paragraph(val text: RichText) : ExportBlock
    data class ListItem(
        val kind: ListKind,
        val text: RichText,
        val indent: Int = 0,
        val checked: Boolean = false
    ) : ExportBlock
    data class Quote(val text: RichText) : ExportBlock

    /**
     * A Bible passage. [text] is null when it could not be fetched (offline, or the translation
     * isn't licensed), and the exporters then print the reference with a note saying so rather
     * than leaving a hole.
     */
    data class Scripture(val reference: String, val text: String?, val versionAbbreviation: String?) : ExportBlock

    /** What someone said, from a transcript. [label] is e.g. "Pastor James · 12:04". */
    data class Excerpt(val label: String?, val text: String) : ExportBlock

    /** An image file already on disk. */
    /**
     * A picture, drawn across the full content width. [cropTop] and [cropBottom] are source pixels
     * to trim — a phone screenshot's status and navigation bars, which aren't part of what the
     * person meant to keep.
     */
    data class Image(val path: String, val caption: String?, val cropTop: Int = 0, val cropBottom: Int = 0) : ExportBlock

    /** A recording in the note: its title, length and summary. The audio itself is not exported. */
    data class Recording(val title: String, val detail: String?, val summary: String?) : ExportBlock

    /** "Label: value" lines, e.g. under a sermon's title. */
    data class Facts(val rows: List<Pair<String, String>>) : ExportBlock

    data object Divider : ExportBlock
}

/** Assigns 1, 2, 3… to consecutive numbered items, restarting after anything else. */
internal fun ExportDocument.numberedPositions(): Map<Int, Int> {
    val numbers = mutableMapOf<Int, Int>()
    val counters = mutableMapOf<Int, Int>()
    blocks.forEachIndexed { i, block ->
        if (block is ExportBlock.ListItem && block.kind == ListKind.NUMBERED) {
            // Deeper levels count on their own and reset when the list comes back up.
            counters.keys.filter { it > block.indent }.forEach { counters.remove(it) }
            val n = (counters[block.indent] ?: 0) + 1
            counters[block.indent] = n
            numbers[i] = n
        } else if (block !is ExportBlock.ListItem) {
            counters.clear()
        }
    }
    return numbers
}

object MarkdownDocumentRenderer {

    fun render(document: ExportDocument): String = buildString {
        append("# ").append(document.title.ifBlank { "Untitled" }).append("\n\n")
        document.subtitle?.let { append("_").append(it).append("_\n\n") }

        val numbers = document.numberedPositions()
        var previousWasList = false
        document.blocks.forEachIndexed { i, block ->
            val isList = block is ExportBlock.ListItem
            // Lists are written as one run of lines; everything else is its own paragraph.
            if (previousWasList && !isList) append("\n")
            when (block) {
                is ExportBlock.Heading -> append("#".repeat((block.level + 1).coerceIn(2, 4))).append(' ')
                    .append(block.text.toMarkdown()).append("\n\n")
                is ExportBlock.Paragraph -> if (block.text.isEmpty) Unit else append(block.text.toMarkdown()).append("\n\n")
                is ExportBlock.ListItem -> {
                    append("  ".repeat(block.indent))
                    append(
                        when (block.kind) {
                            ListKind.BULLET -> "- "
                            ListKind.NUMBERED -> "${numbers[i] ?: 1}. "
                            ListKind.CHECKLIST -> if (block.checked) "- [x] " else "- [ ] "
                        }
                    )
                    append(block.text.toMarkdown()).append("\n")
                }
                is ExportBlock.Quote -> append(quoteLines(block.text.toMarkdown())).append("\n\n")
                is ExportBlock.Scripture -> {
                    val version = block.versionAbbreviation?.let { " ($it)" } ?: ""
                    append("> **").append(block.reference).append(version).append("**")
                    if (block.text != null) append("  \n").append(quoteLines(block.text))
                    else append("  \n> _Verse text unavailable offline._")
                    append("\n\n")
                }
                is ExportBlock.Excerpt -> {
                    block.label?.let { append("> _").append(it).append("_  \n") }
                    append(quoteLines(block.text)).append("\n\n")
                }
                is ExportBlock.Image -> append("![").append(block.caption ?: "").append("](")
                    .append(java.io.File(block.path).name).append(")\n\n")
                is ExportBlock.Recording -> {
                    append("**🎙 ").append(block.title).append("**")
                    block.detail?.let { append(" · ").append(it) }
                    append("\n\n")
                    block.summary?.takeIf { it.isNotBlank() }?.let { append(it).append("\n\n") }
                }
                is ExportBlock.Facts -> {
                    block.rows.forEach { (k, v) -> append("**").append(k).append(":** ").append(v).append("  \n") }
                    append("\n")
                }
                ExportBlock.Divider -> append("---\n\n")
            }
            previousWasList = isList
        }
        if (previousWasList) append("\n")
        if (document.closingNotes.isNotEmpty()) {
            append("---\n\n")
            document.closingNotes.forEach { append("<sub>").append(it).append("</sub>\n\n") }
        }
    }.trimEnd() + "\n"

    private fun quoteLines(text: String): String = text.lines().joinToString("\n") { "> $it" }
}
