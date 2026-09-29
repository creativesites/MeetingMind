package com.example.core.notes

/**
 * The clipboard's HTML turned into Markdown.
 *
 * Copying a selection from ChatGPT, Claude, Gemini, a web page or Docs puts two things on the
 * clipboard: plain text with every bit of formatting gone, and HTML with all of it. A text field
 * only ever receives the plain text, so the editor reads the HTML itself and keeps the headings,
 * lists, bold, links, code and tables the person actually copied.
 */
object HtmlToMarkdown {

    fun convert(html: String): String {
        val cleaned = html
            .replace(Regex("(?is)<(script|style|head|title|button|svg)[^>]*>.*?</\\1>"), "")
            .replace(Regex("(?s)<!--.*?-->"), "")
        val out = StringBuilder()
        val lists = ArrayDeque<IntArray>() // [ordered 0/1, next number]
        val quoteStarts = ArrayDeque<Int>()
        var pre = 0
        var preLanguage = ""
        var link: String? = null
        var linkStart = -1
        var tableRows: MutableList<MutableList<String>>? = null
        var cell: StringBuilder? = null
        val pendingOpen = StringBuilder()

        fun sink(): StringBuilder = cell ?: out
        fun blank() {
            if (cell != null) { cell!!.append(' '); return }
            while (out.endsWith(" ")) out.setLength(out.length - 1)
            if (out.isEmpty()) return
            if (!out.endsWith("\n")) out.append('\n')
            if (!out.endsWith("\n\n")) out.append('\n')
        }
        fun newline() {
            if (cell != null) { cell!!.append(' '); return }
            while (out.endsWith(" ")) out.setLength(out.length - 1)
            if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
        }
        fun open(marker: String) { pendingOpen.append(marker) }
        fun close(marker: String) {
            val s = sink()
            if (pendingOpen.endsWith(marker)) { pendingOpen.setLength(pendingOpen.length - marker.length); return }
            var trailing = 0
            while (s.endsWith(" ")) { s.setLength(s.length - 1); trailing++ }
            s.append(marker)
            if (trailing > 0) s.append(' ')
        }
        fun text(raw: String) {
            var t = decode(raw)
            if (pre == 0) {
                t = t.replace(Regex("\\s+"), " ")
                val s = sink()
                if (s.isEmpty() || s.endsWith("\n") || s.endsWith(" ")) t = t.trimStart()
                if (t.isEmpty()) return
                if (pendingOpen.isNotEmpty()) {
                    if (t.startsWith(" ")) { s.append(' '); t = t.trimStart() }
                    s.append(pendingOpen); pendingOpen.setLength(0)
                }
                s.append(t)
            } else sink().append(t)
        }

        for (m in TOKEN.findAll(cleaned)) {
            val tag = m.groups[2]?.value?.lowercase()
            if (tag == null) { text(m.value); continue }
            val closing = m.groups[1]?.value == "/"
            val attrs = m.groups[3]?.value.orEmpty()
            when (tag) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    blank()
                    if (!closing) out.append("#".repeat(minOf(tag[1].digitToInt(), 3))).append(' ')
                }
                // Inside a list item a paragraph is just the item's text.
                "p", "section", "article", "header", "footer" -> if (lists.isEmpty()) blank() else if (cell == null && !closing && !out.endsWith(" ") && !out.endsWith("\n")) out.append(' ')
                "div" -> if (lists.isEmpty()) newline()
                "br" -> if (pre > 0) sink().append('\n') else if (lists.isNotEmpty() || cell != null) sink().append(' ') else newline()
                "strong", "b" -> if (pre == 0) { if (closing) close("**") else open("**") }
                "em", "i" -> if (pre == 0) { if (closing) close("*") else open("*") }
                "del", "s", "strike" -> if (pre == 0) { if (closing) close("~~") else open("~~") }
                "mark" -> if (pre == 0) { if (closing) close("==") else open("==") }
                "code" -> if (pre == 0) { if (closing) close("`") else open("`") }
                    else if (!closing && preLanguage.isEmpty()) preLanguage = attr(attrs, "class")?.let { Regex("language-([\\w+#-]+)").find(it)?.groupValues?.get(1) }.orEmpty()
                "a" -> if (closing) {
                    val href = link
                    if (href != null && linkStart >= 0 && linkStart <= sink().length) {
                        val label = sink().substring(linkStart).trim()
                        sink().setLength(linkStart)
                        sink().append(if (label.isEmpty() || label == href) href else "[$label]($href)")
                    }
                    link = null; linkStart = -1
                } else {
                    link = attr(attrs, "href")?.takeIf { it.startsWith("http") || it.startsWith("mailto:") }
                    if (pendingOpen.isNotEmpty()) { sink().append(pendingOpen); pendingOpen.setLength(0) }
                    linkStart = sink().length
                }
                "ul", "ol" -> if (closing) { lists.removeLastOrNull(); if (lists.isEmpty()) blank() } else {
                    if (lists.isEmpty()) blank() else newline()
                    lists.addLast(intArrayOf(if (tag == "ol") 1 else 0, attr(attrs, "start")?.toIntOrNull() ?: 1))
                }
                "li" -> if (!closing) {
                    newline()
                    val level = lists.lastOrNull()
                    out.append("  ".repeat((lists.size - 1).coerceAtLeast(0)))
                    if (level != null && level[0] == 1) { out.append(level[1]).append(". "); level[1]++ } else out.append("- ")
                } else newline()
                "input" -> if (attr(attrs, "type") == "checkbox") {
                    if (out.endsWith("- ")) out.append(if (Regex("\\bchecked\\b").containsMatchIn(attrs)) "[x] " else "[ ] ")
                }
                "blockquote" -> if (!closing) { blank(); quoteStarts.addLast(out.length) } else {
                    val start = quoteStarts.removeLastOrNull() ?: 0
                    val body = out.substring(start).trim('\n')
                    out.setLength(start)
                    out.append(body.lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" })
                    blank()
                }
                "pre" -> if (!closing) {
                    blank(); pre++; preLanguage = ""
                    out.append("```\n")
                } else {
                    pre = (pre - 1).coerceAtLeast(0)
                    // The language comes from the <code> inside, known only once it has been read.
                    val fence = out.lastIndexOf("```\n")
                    if (fence >= 0 && preLanguage.isNotEmpty()) out.insert(fence + 3, preLanguage)
                    if (!out.endsWith("\n")) out.append('\n')
                    out.append("```")
                    blank()
                }
                "hr" -> { blank(); out.append("---"); blank() }
                "img" -> attr(attrs, "src")?.takeIf { it.startsWith("http") }?.let { src ->
                    blank(); out.append("![").append(attr(attrs, "alt").orEmpty()).append("](").append(src).append(")"); blank()
                }
                "table" -> if (!closing) { blank(); tableRows = mutableListOf() } else {
                    val rows = tableRows.orEmpty().filter { r -> r.any { it.isNotBlank() } }
                    tableRows = null
                    if (rows.isNotEmpty()) {
                        val width = rows.maxOf { it.size }
                        fun row(r: List<String>) = "| " + (0 until width).joinToString(" | ") { r.getOrElse(it) { "" }.replace("|", "\\|") } + " |"
                        out.append(row(rows.first())).append('\n')
                        out.append("| ").append(List(width) { "---" }.joinToString(" | ")).append(" |")
                        rows.drop(1).forEach { out.append('\n').append(row(it)) }
                    }
                    blank()
                }
                "tr" -> if (!closing) tableRows?.add(mutableListOf())
                "td", "th" -> if (!closing) cell = StringBuilder() else {
                    cell?.let { c -> tableRows?.lastOrNull()?.add(c.toString().trim()) }
                    cell = null
                }
            }
        }
        return out.toString()
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private val TOKEN = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>|[^<]+|<")

    private fun attr(attrs: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(attrs)?.let { m ->
            decode(m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value.orEmpty())
        }

    private fun decode(s: String): String {
        if ('&' !in s) return s
        return Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> ENTITIES[e] ?: m.value
            }
        }
    }

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
        "bull" to "•", "copy" to "©", "reg" to "®", "trade" to "™", "rarr" to "→", "larr" to "←", "times" to "×"
    )
}
