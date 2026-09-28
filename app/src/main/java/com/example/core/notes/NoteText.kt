package com.example.core.notes

import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType

/**
 * A note's blocks written out for other apps: Markdown (the inverse of [MarkdownImport]), HTML
 * for pasting into Docs or Gmail with its formatting, WhatsApp's own markup, and plain text.
 */
object NoteText {

    fun markdown(blocks: List<NoteBlock>, title: String? = null): String = buildString {
        title?.takeIf { it.isNotBlank() }?.let { append("# ").append(it.trim()).append("\n\n") }
        var previous: NoteBlock? = null
        blocks.forEachIndexed { index, b ->
            val line = markdownOf(b, blocks, index) ?: return@forEachIndexed
            if (previous != null) append(if (previous!!.type.isListItem && b.type.isListItem) "\n" else "\n\n")
            append(line)
            previous = b
        }
    }.trimEnd() + "\n"

    private fun markdownOf(b: NoteBlock, all: List<NoteBlock>, index: Int): String? {
        val md = b.content.toMarkdown()
        val pad = "  ".repeat(b.indent)
        return when (b.type) {
            NoteBlockType.PARAGRAPH -> md.takeIf { it.isNotBlank() }
            NoteBlockType.HEADING_1 -> "# $md"
            NoteBlockType.HEADING_2 -> "## $md"
            NoteBlockType.HEADING_3 -> "### $md"
            NoteBlockType.BULLET -> "$pad- $md"
            NoteBlockType.NUMBERED -> "$pad${numberFor(all, index)}. $md"
            NoteBlockType.CHECKLIST -> "$pad- [${if (b.checked) "x" else " "}] $md"
            NoteBlockType.QUOTE -> "> $md"
            NoteBlockType.DIVIDER -> "---"
            NoteBlockType.CODE -> "```${b.payload[NoteBlock.PAYLOAD_LANGUAGE].orEmpty()}\n${b.content.text}\n```"
            NoteBlockType.TABLE -> MarkdownImport.Table.fromJson(b.payload[NoteBlock.PAYLOAD_TABLE])?.let { t ->
                val width = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0)
                fun row(cells: List<String>) = "| " + (0 until width).joinToString(" | ") { cells.getOrElse(it) { "" }.replace("|", "\\|") } + " |"
                (listOf(row(t.header), "| " + List(width) { "---" }.joinToString(" | ") + " |") + t.rows.map(::row)).joinToString("\n")
            }
            NoteBlockType.EMBED -> {
                val url = b.payload[NoteBlock.PAYLOAD_URL] ?: return null
                val alt = b.payload[NoteBlock.PAYLOAD_ALT].orEmpty()
                when (b.payload[NoteBlock.PAYLOAD_EMBED_KIND]) {
                    NoteBlock.EMBED_IMAGE -> "![$alt]($url)"
                    NoteBlock.EMBED_YOUTUBE -> if (alt.isNotBlank()) "[$alt]($url)" else url
                    else -> "[${alt.ifBlank { url }}]($url)"
                }
            }
            NoteBlockType.SCRIPTURE -> b.payload["reference"]?.let { ref -> "> ${b.payload[NoteBlock.PAYLOAD_USER_TEXT] ?: ""}\n> — $ref".replace("> \n", "") }
            NoteBlockType.TRANSCRIPT_EXCERPT -> md.takeIf { it.isNotBlank() }?.let { "> $it" }
            else -> md.takeIf { it.isNotBlank() }
        }
    }

    /** Plain text, as WhatsApp formats it: *bold*, _italic_, ~strike~, ```code```. */
    fun whatsApp(blocks: List<NoteBlock>, title: String? = null): String = buildString {
        title?.takeIf { it.isNotBlank() }?.let { append("*").append(it.trim()).append("*\n\n") }
        blocks.forEachIndexed { index, b ->
            val text = whatsAppInline(b.content)
            val pad = "   ".repeat(b.indent)
            val line = when (b.type) {
                NoteBlockType.PARAGRAPH -> text
                NoteBlockType.HEADING_1, NoteBlockType.HEADING_2, NoteBlockType.HEADING_3 -> "*${b.content.text.trim()}*"
                NoteBlockType.BULLET -> "$pad• $text"
                NoteBlockType.NUMBERED -> "$pad${numberFor(blocks, index)}. $text"
                NoteBlockType.CHECKLIST -> "$pad${if (b.checked) "✅" else "☐"} $text"
                NoteBlockType.QUOTE -> "> $text"
                NoteBlockType.DIVIDER -> "———"
                NoteBlockType.CODE -> "```${b.content.text}```"
                NoteBlockType.TABLE -> MarkdownImport.Table.fromJson(b.payload[NoteBlock.PAYLOAD_TABLE])?.let { t ->
                    (listOf(t.header) + t.rows).joinToString("\n") { r -> r.joinToString(" | ") { MarkdownImport.stripInline(it) } }
                }
                NoteBlockType.EMBED -> b.payload[NoteBlock.PAYLOAD_URL]
                else -> text.takeIf { it.isNotBlank() }
            } ?: return@forEachIndexed
            if (isNotEmpty()) append(if (b.type.isListItem && blocks.getOrNull(index - 1)?.type?.isListItem == true) "\n" else "\n\n")
            append(line)
        }
    }

    private fun whatsAppInline(content: RichText): String = buildString {
        for (run in content.runs()) {
            if (run.text.isBlank()) { append(run.text); continue }
            val core = run.text.trim()
            val lead = run.text.substring(0, run.text.indexOf(core))
            val trail = run.text.substring(lead.length + core.length)
            var body = core
            if (InlineStyle.CODE in run.styles) body = "```$body```"
            if (InlineStyle.STRIKETHROUGH in run.styles) body = "~$body~"
            if (InlineStyle.ITALIC in run.styles) body = "_${body}_"
            if (InlineStyle.BOLD in run.styles) body = "*$body*"
            if (InlineStyle.LINK in run.styles && run.url != null && run.url != core) body = "$body (${run.url})"
            append(lead).append(body).append(trail)
        }
    }

    /** HTML for the clipboard, so pasting into Docs, Gmail or Word keeps the formatting. */
    fun html(blocks: List<NoteBlock>, title: String? = null): String = buildString {
        title?.takeIf { it.isNotBlank() }?.let { append("<h1>").append(esc(it)).append("</h1>") }
        var openList: String? = null
        fun closeList() { openList?.let { append("</$it>") }; openList = null }
        blocks.forEach { b ->
            val inner = htmlInline(b.content)
            val listTag = when (b.type) { NoteBlockType.BULLET, NoteBlockType.CHECKLIST -> "ul"; NoteBlockType.NUMBERED -> "ol"; else -> null }
            if (listTag != openList) { closeList(); listTag?.let { append("<$it>"); openList = it } }
            when (b.type) {
                NoteBlockType.PARAGRAPH -> if (inner.isNotBlank()) append("<p>").append(inner).append("</p>")
                NoteBlockType.HEADING_1 -> append("<h1>").append(inner).append("</h1>")
                NoteBlockType.HEADING_2 -> append("<h2>").append(inner).append("</h2>")
                NoteBlockType.HEADING_3 -> append("<h3>").append(inner).append("</h3>")
                NoteBlockType.BULLET, NoteBlockType.NUMBERED -> append("<li style=\"margin-left:${b.indent * 24}px\">").append(inner).append("</li>")
                NoteBlockType.CHECKLIST -> append("<li style=\"margin-left:${b.indent * 24}px;list-style:none\">").append(if (b.checked) "☑ " else "☐ ").append(inner).append("</li>")
                NoteBlockType.QUOTE -> append("<blockquote>").append(inner).append("</blockquote>")
                NoteBlockType.DIVIDER -> append("<hr>")
                NoteBlockType.CODE -> append("<pre><code>").append(esc(b.content.text)).append("</code></pre>")
                NoteBlockType.TABLE -> MarkdownImport.Table.fromJson(b.payload[NoteBlock.PAYLOAD_TABLE])?.let { t ->
                    append("<table border=\"1\" cellpadding=\"6\" style=\"border-collapse:collapse\"><tr>")
                    t.header.forEach { append("<th>").append(htmlInline(MarkdownImport.inline(it))).append("</th>") }
                    append("</tr>")
                    t.rows.forEach { r -> append("<tr>"); r.forEach { append("<td>").append(htmlInline(MarkdownImport.inline(it))).append("</td>") }; append("</tr>") }
                    append("</table>")
                }
                NoteBlockType.EMBED -> b.payload[NoteBlock.PAYLOAD_URL]?.let { url ->
                    if (b.payload[NoteBlock.PAYLOAD_EMBED_KIND] == NoteBlock.EMBED_IMAGE) append("<p><img src=\"").append(esc(url)).append("\" alt=\"").append(esc(b.payload[NoteBlock.PAYLOAD_ALT].orEmpty())).append("\"></p>")
                    else append("<p><a href=\"").append(esc(url)).append("\">").append(esc(b.payload[NoteBlock.PAYLOAD_ALT]?.ifBlank { null } ?: url)).append("</a></p>")
                }
                else -> if (inner.isNotBlank()) append("<p>").append(inner).append("</p>")
            }
        }
        closeList()
    }

    private fun htmlInline(content: RichText): String = buildString {
        for (run in content.runs()) {
            var body = esc(run.text)
            if (InlineStyle.CODE in run.styles) body = "<code>$body</code>"
            if (InlineStyle.HIGHLIGHT in run.styles) body = "<mark>$body</mark>"
            if (InlineStyle.STRIKETHROUGH in run.styles) body = "<s>$body</s>"
            if (InlineStyle.UNDERLINE in run.styles) body = "<u>$body</u>"
            if (InlineStyle.ITALIC in run.styles) body = "<em>$body</em>"
            if (InlineStyle.BOLD in run.styles) body = "<strong>$body</strong>"
            if (InlineStyle.LINK in run.styles && run.url != null) body = "<a href=\"${esc(run.url)}\">$body</a>"
            append(body)
        }
    }

    /** Plain text: what a search index or a basic paste should see. */
    fun plain(blocks: List<NoteBlock>, title: String? = null): String = VersionCodec.lines(blocks).let { lines ->
        (listOfNotNull(title?.takeIf { it.isNotBlank() }) + lines).joinToString("\n")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** The number a numbered item shows; mirrors the editor's own count. */
    private fun numberFor(blocks: List<NoteBlock>, index: Int): Int {
        val target = blocks[index]
        var n = 1
        var i = index - 1
        while (i >= 0) {
            val b = blocks[i]
            if (!b.type.isListItem || b.indent < target.indent) break
            if (b.indent == target.indent) { if (b.type != NoteBlockType.NUMBERED) break; n++ }
            i--
        }
        return n
    }
}
