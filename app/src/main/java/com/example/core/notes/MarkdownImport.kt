package com.example.core.notes

import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.repository.NoteRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Markdown → note blocks (docs/PLAN_V3.md W1).
 *
 * Pasting an answer from ChatGPT, Claude or Gemini — or any Markdown — gives a formatted note:
 * headings, lists (nested, numbered, checklists), quotes, code, tables, dividers, pictures from
 * the web and YouTube videos, with bold, italic, strikethrough, highlight, code and links inside
 * the text. A paragraph stays one block, however many lines it was wrapped over.
 *
 * A block's text never contains a line break: the editor treats one as Enter.
 */
object MarkdownImport {

    /** Whether pasted [text] is worth reading as Markdown rather than as plain lines. */
    fun looksLikeMarkdown(text: String): Boolean {
        val lines = text.lines()
        return lines.any { l ->
            HEADING.matches(l) || LIST_ITEM.matches(l) || FENCE.matches(l) || QUOTE.matches(l) || HR.matches(l) ||
                (l.contains('|') && TABLE_SEPARATOR.matches(l))
        } || INLINE_HINT.containsMatchIn(text) || lines.any { YouTube.idOf(it.trim()) != null || IMAGE_LINE.matches(it.trim()) }
    }

    /** A note's blocks with every Markdown block opened out into the blocks it shows. */
    fun expand(blocks: List<NoteBlock>): List<NoteBlock> = blocks.flatMap { b ->
        if (b.type != NoteBlockType.MARKDOWN) listOf(b)
        else parse(b.content.text, b.noteId, b.source).map { it.copy(sectionKey = b.sectionKey) }
    }

    fun parse(markdown: String, noteId: String, source: BlockSource = BlockSource.USER): List<NoteBlock> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").split('\n')
        val out = mutableListOf<NoteBlock>()
        val paragraph = mutableListOf<String>()

        fun block(type: NoteBlockType, content: RichText = RichText.EMPTY, payload: Map<String, String> = emptyMap(), indent: Int = 0, checked: Boolean = false) {
            out += NoteBlock(NoteRepository.newId("block"), noteId, 0, type, content, payload, source, indent = indent, checked = checked)
        }
        fun flush() {
            if (paragraph.isEmpty()) return
            val text = paragraph.joinToString(" ") { it.trim() }.trim()
            paragraph.clear()
            if (text.isNotEmpty()) block(NoteBlockType.PARAGRAPH, inline(text))
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flush()
                FENCE.matches(line) -> {
                    flush()
                    val fence = trimmed.takeWhile { it == '`' || it == '~' }
                    val language = trimmed.drop(fence.length).trim()
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) { code += lines[i]; i++ }
                    block(NoteBlockType.CODE, RichText.plain(code.joinToString("\n").trimEnd('\n')), if (language.isNotEmpty()) mapOf(NoteBlock.PAYLOAD_LANGUAGE to language) else emptyMap())
                }
                HEADING.matches(line) -> {
                    flush()
                    val m = HEADING.matchEntire(line)!!
                    val type = when (m.groupValues[1].length) { 1 -> NoteBlockType.HEADING_1; 2 -> NoteBlockType.HEADING_2; else -> NoteBlockType.HEADING_3 }
                    block(type, inline(m.groupValues[2].trim().trimEnd('#').trim()))
                }
                HR.matches(line) && paragraph.isEmpty() -> { flush(); block(NoteBlockType.DIVIDER) }
                line.contains('|') && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1]) -> {
                    flush()
                    val header = cells(line)
                    val rows = mutableListOf<List<String>>()
                    i += 2
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) { rows += cells(lines[i]); i++ }
                    i--
                    block(NoteBlockType.TABLE, RichText.plain((listOf(header) + rows).joinToString(" · ") { r -> r.joinToString(" ") { stripInline(it) } }),
                        mapOf(NoteBlock.PAYLOAD_TABLE to Table(header, rows).toJson()))
                }
                QUOTE.matches(line) -> {
                    flush()
                    val parts = mutableListOf<String>()
                    while (i < lines.size && QUOTE.matches(lines[i])) {
                        val body = lines[i].trim().removePrefix(">").removePrefix(">").trimStart()
                        if (body.isBlank()) {
                            if (parts.isNotEmpty()) { block(NoteBlockType.QUOTE, inline(parts.joinToString(" "))); parts.clear() }
                        } else parts += body.trim()
                        i++
                    }
                    i--
                    if (parts.isNotEmpty()) block(NoteBlockType.QUOTE, inline(parts.joinToString(" ")))
                }
                LIST_ITEM.matches(line) -> {
                    flush()
                    val m = LIST_ITEM.matchEntire(line)!!
                    val spaces = m.groupValues[1].length
                    val marker = m.groupValues[2]
                    val box = m.groupValues[3]
                    val parts = mutableListOf(m.groupValues[4].trim())
                    // Lines that continue this item: indented, not blank, not a new item or block.
                    while (i + 1 < lines.size) {
                        val next = lines[i + 1]
                        if (next.isBlank() || LIST_ITEM.matches(next) || HEADING.matches(next) || FENCE.matches(next) || QUOTE.matches(next)) break
                        if (next.takeWhile { it == ' ' }.length <= spaces && !next.startsWith(" ")) break
                        parts += next.trim(); i++
                    }
                    val previous = out.lastOrNull()?.takeIf { it.type.isListItem }
                    val depth = (spaces / 2).coerceAtMost((previous?.indent ?: -1) + 1).coerceIn(0, MAX_INDENT)
                    val type = when {
                        box.isNotEmpty() -> NoteBlockType.CHECKLIST
                        marker.first().isDigit() -> NoteBlockType.NUMBERED
                        else -> NoteBlockType.BULLET
                    }
                    block(type, inline(parts.joinToString(" ")), indent = depth, checked = box.contains('x', ignoreCase = true))
                }
                IMAGE_LINE.matches(trimmed) -> {
                    flush()
                    val m = IMAGE_LINE.matchEntire(trimmed)!!
                    block(NoteBlockType.EMBED, RichText.plain(m.groupValues[1]), mapOf(
                        NoteBlock.PAYLOAD_EMBED_KIND to NoteBlock.EMBED_IMAGE, NoteBlock.PAYLOAD_URL to m.groupValues[2], NoteBlock.PAYLOAD_ALT to m.groupValues[1]
                    ))
                }
                embedUrl(trimmed) != null && paragraph.isEmpty() -> {
                    flush()
                    val (url, label) = embedUrl(trimmed)!!
                    val kind = when {
                        YouTube.idOf(url) != null -> NoteBlock.EMBED_YOUTUBE
                        IMAGE_URL.containsMatchIn(url) -> NoteBlock.EMBED_IMAGE
                        else -> null
                    }
                    if (kind == null) paragraph += trimmed
                    else block(NoteBlockType.EMBED, RichText.plain(label.orEmpty()), buildMap {
                        put(NoteBlock.PAYLOAD_EMBED_KIND, kind); put(NoteBlock.PAYLOAD_URL, url); label?.let { put(NoteBlock.PAYLOAD_ALT, it) }
                    })
                }
                else -> {
                    // A line ending in two spaces or a backslash is a hard break: its own paragraph.
                    paragraph += trimmed.removeSuffix("\\")
                    if (line.endsWith("  ") || line.endsWith("\\")) flush()
                }
            }
            i++
        }
        flush()
        return out
    }

    /** A line that is only a URL (or a link to one): the URL and the link text, if any. */
    private fun embedUrl(line: String): Pair<String, String?>? {
        BARE_URL.matchEntire(line)?.let { return it.groupValues[1] to null }
        LINK_LINE.matchEntire(line)?.let { return it.groupValues[2] to it.groupValues[1] }
        return null
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }

    /** A table's rows, kept as each cell's Markdown so its styling survives. */
    data class Table(val header: List<String>, val rows: List<List<String>>) {
        fun toJson(): String = JSONObject().apply {
            put("header", JSONArray(header))
            put("rows", JSONArray(rows.map { JSONArray(it) }))
        }.toString()

        companion object {
            fun fromJson(raw: String?): Table? = runCatching {
                val o = JSONObject(raw ?: return null)
                val h = o.getJSONArray("header")
                val r = o.getJSONArray("rows")
                Table((0 until h.length()).map { h.getString(it) }, (0 until r.length()).map { i -> r.getJSONArray(i).let { a -> (0 until a.length()).map { a.getString(it) } } })
            }.getOrNull()
        }
    }

    // ---------------------------------------------------------------- inline

    /**
     * Inline Markdown → styled text: **bold**, __bold__, *italic*, _italic_, ~~strike~~,
     * ==highlight==, `code`, [link](url), <u>underline</u>, bare links, and backslash escapes.
     */
    fun inline(markdown: String): RichText {
        val text = StringBuilder()
        val spans = mutableListOf<InlineSpan>()
        val open = mutableListOf<Pair<String, Int>>() // delimiter to start offset
        var i = 0
        val s = markdown
        fun styleOf(d: String) = when (d) {
            "**", "__" -> InlineStyle.BOLD
            "*", "_" -> InlineStyle.ITALIC
            "~~" -> InlineStyle.STRIKETHROUGH
            "==" -> InlineStyle.HIGHLIGHT
            "<u>" -> InlineStyle.UNDERLINE
            else -> null
        }
        while (i < s.length) {
            val c = s[i]
            // Escapes.
            if (c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) { text.append(s[i + 1]); i += 2; continue }
            // Code: taken literally.
            if (c == '`') {
                val ticks = s.substring(i).takeWhile { it == '`' }
                val end = s.indexOf(ticks, i + ticks.length)
                if (end > 0) {
                    val start = text.length
                    text.append(s.substring(i + ticks.length, end).trim())
                    spans += InlineSpan(start, text.length, InlineStyle.CODE)
                    i = end + ticks.length; continue
                }
            }
            // Links and inline images (an image mid-sentence becomes a link to it).
            if (c == '[' || (c == '!' && i + 1 < s.length && s[i + 1] == '[')) {
                val m = INLINE_LINK.matchAt(s, i)
                if (m != null) {
                    val inner = inline(m.groupValues[1])
                    val start = text.length
                    text.append(inner.text)
                    inner.spans.forEach { spans += it.copy(start = it.start + start, end = it.end + start) }
                    spans += InlineSpan(start, text.length, InlineStyle.LINK, m.groupValues[2])
                    i = m.range.last + 1; continue
                }
            }
            if (c == '<') {
                AUTOLINK.matchAt(s, i)?.let { m ->
                    val start = text.length
                    text.append(m.groupValues[1])
                    spans += InlineSpan(start, text.length, InlineStyle.LINK, m.groupValues[1])
                    i = m.range.last + 1
                }?.let { continue }
                if (s.startsWith("<u>", i) || s.startsWith("</u>", i)) {
                    val closing = s.startsWith("</u>", i)
                    val idx = open.indexOfLast { it.first == "<u>" }
                    if (closing && idx >= 0) {
                        spans += InlineSpan(open[idx].second, text.length, InlineStyle.UNDERLINE); open.removeAt(idx); i += 4; continue
                    } else if (!closing && s.indexOf("</u>", i) > 0) { open += "<u>" to text.length; i += 3; continue }
                }
            }
            if ((c == 'h') && (s.startsWith("http://", i) || s.startsWith("https://", i)) && (i == 0 || !s[i - 1].isLetterOrDigit())) {
                val m = BARE_LINK.matchAt(s, i)
                if (m != null) {
                    val url = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')')
                    val start = text.length
                    text.append(url)
                    spans += InlineSpan(start, text.length, InlineStyle.LINK, url)
                    i += url.length; continue
                }
            }
            // Emphasis delimiters.
            val d = when {
                s.startsWith("**", i) -> "**"
                s.startsWith("__", i) -> "__"
                s.startsWith("~~", i) -> "~~"
                s.startsWith("==", i) -> "=="
                c == '*' -> "*"
                c == '_' -> "_"
                else -> null
            }
            if (d != null) {
                val before = s.getOrNull(i - 1)
                val after = s.getOrNull(i + d.length)
                val idx = open.indexOfLast { it.first == d }
                val canClose = idx >= 0 && before != null && !before.isWhitespace()
                // Underscores only count at word edges, so snake_case stays as it is.
                val wordy = d.startsWith("_") && ((before?.isLetterOrDigit() == true) && (after?.isLetterOrDigit() == true))
                if (!wordy && canClose) {
                    spans += InlineSpan(open[idx].second, text.length, styleOf(d)!!)
                    open.removeAt(idx); i += d.length; continue
                }
                val canOpen = !wordy && after != null && !after.isWhitespace() && closesLater(s, i + d.length, d)
                if (canOpen) { open += d to text.length; i += d.length; continue }
            }
            text.append(c); i++
        }
        return RichText.create(text.toString(), spans)
    }

    private fun closesLater(s: String, from: Int, d: String): Boolean {
        var j = s.indexOf(d, from)
        while (j > from) {
            if (!s[j - 1].isWhitespace()) return true
            j = s.indexOf(d, j + d.length)
        }
        return false
    }

    /** Markdown with its markers taken out, for search text. */
    fun stripInline(markdown: String): String = inline(markdown).text

    private fun Regex.matchAt(s: String, index: Int): MatchResult? = find(s, index)?.takeIf { it.range.first == index }

    const val MAX_INDENT = 4
    private val ESCAPABLE = setOf('\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '|', '~', '=', '<', '>')
    private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
    private val HR = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val FENCE = Regex("^\\s{0,3}(```|~~~).*$")
    private val QUOTE = Regex("^\\s{0,3}>.*$")
    private val LIST_ITEM = Regex("^(\\s*)([-*+•]|\\d{1,3}[.)])\\s+(\\[[ xX]\\]\\s+)?(.*)$")
    private val TABLE_SEPARATOR = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")
    private val IMAGE_LINE = Regex("^!\\[([^\\]]*)]\\((\\S+?)(?:\\s+\"[^\"]*\")?\\)$")
    private val LINK_LINE = Regex("^\\[([^\\]]+)]\\((https?://\\S+?)\\)$")
    private val BARE_URL = Regex("^<?(https?://\\S+?)>?$")
    private val IMAGE_URL = Regex("\\.(png|jpe?g|gif|webp|avif|svg)(\\?.*)?$", RegexOption.IGNORE_CASE)
    private val INLINE_LINK = Regex("!?\\[((?:[^\\[\\]]|\\[[^\\]]*])*)]\\((\\S+?)(?:\\s+\"[^\"]*\")?\\)")
    private val AUTOLINK = Regex("<(https?://[^>\\s]+)>")
    private val BARE_LINK = Regex("https?://[^\\s<>()\\[\\]]+")
    private val INLINE_HINT = Regex("\\*\\*[^*]+\\*\\*|__[^_]+__|`[^`]+`|\\[[^\\]]+]\\(https?://|~~[^~]+~~|==[^=]+==")
}

/** YouTube links in all their shapes. */
object YouTube {
    private val PATTERNS = listOf(
        Regex("(?:https?://)?(?:www\\.|m\\.|music\\.)?youtube\\.com/watch\\?(?:[^\\s#]*&)?v=([A-Za-z0-9_-]{11})"),
        Regex("(?:https?://)?youtu\\.be/([A-Za-z0-9_-]{11})"),
        Regex("(?:https?://)?(?:www\\.|m\\.)?youtube(?:-nocookie)?\\.com/(?:embed|shorts|live|v)/([A-Za-z0-9_-]{11})")
    )

    fun idOf(url: String): String? = PATTERNS.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }

    fun thumbnail(id: String) = "https://img.youtube.com/vi/$id/hqdefault.jpg"

    /** Start time from a `t=` or `start=` parameter, in seconds. */
    fun startSeconds(url: String): Int? {
        val raw = Regex("[?&#](?:t|start)=([0-9hms]+)").find(url)?.groupValues?.get(1) ?: return null
        raw.toIntOrNull()?.let { return it }
        val parts = Regex("(\\d+)([hms])").findAll(raw).associate { it.groupValues[2] to it.groupValues[1].toInt() }
        return (parts["h"] ?: 0) * 3600 + (parts["m"] ?: 0) * 60 + (parts["s"] ?: 0)
    }
}
