package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType

/**
 * AI replies made safe to show as note Markdown.
 *
 * A model asked for "Markdown only" sometimes answers with a JSON object instead, under a key it
 * made up: `{ "sermon_notes": "## Main Theme\nBelievers are urged…" }`. Shown as it is, the note
 * reads as braces, quotes, literal `\n` and `##`. This unwraps such a reply (any key; its string
 * values joined in order) and decodes the escapes, so the Markdown inside is what the note shows.
 *
 * Used in two places:
 * - **At the source.** [MarkdownImport.parse] calls [clean] on everything an AI wrote
 *   ([BlockSource.AI]), which covers note polish, the YouTube import (it is polished the same way),
 *   sermon notes and the assistant.
 * - **At render time.** [repairBlocks] and [repairMarkdown] fix notes already saved with the JSON
 *   in them, so they read correctly without a database migration.
 *
 * A person's own JSON is never touched: a fenced ```json block is a code block, and blocks a person
 * wrote ([BlockSource.USER]) are left exactly as they are.
 */
object AiMarkdown {

    /**
     * The rule every note-writing prompt gives the model, so maths and chemistry come out in the
     * form the editor renders inline (`InlineMath`) and as equation blocks.
     */
    const val MATH_AND_CHEMISTRY_RULE =
        "Write mathematics in LaTeX: \$...\$ inside a sentence (for example \$(2x+1)(x+2)\$), and \$\$...\$\$ on lines of their own " +
            "for an equation shown by itself. Write chemical formulas and reactions with \\ce{...}, for example \\ce{H2SO4} or \\ce{2H2 + O2 -> 2H2O}."

    /** The sources whose text a model wrote, and so may arrive wrapped in JSON. */
    private val AI_SOURCES = setOf(BlockSource.AI, BlockSource.ENRICHED)

    fun isAiSource(source: BlockSource): Boolean = source in AI_SOURCES

    /**
     * An AI reply as Markdown: a JSON wrapper (bare, or in a ```json fence) unwrapped, and a
     * ```markdown fence around the whole reply removed. Anything else comes back as it was.
     */
    fun clean(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("```") && t.endsWith("```") && t.length > 6) {
            val firstLine = t.substringBefore('\n')
            val language = firstLine.removePrefix("```").trim().lowercase()
            val inner = if ('\n' in t) t.substringAfter('\n').removeSuffix("```").trim() else ""
            when (language) {
                // Strict: a JSON code sample (`{"name": "Bob"}`) stays a code block; only Markdown wrapped in JSON unwraps.
                "json", "" -> unwrapJson(inner, strict = true)?.let { return it }
                "markdown", "md" -> return unwrapJson(inner, strict = false) ?: inner
            }
            return raw
        }
        return unwrapJson(t, strict = false) ?: raw
    }

    /**
     * The Markdown inside a JSON object [text], or null when [text] isn't one.
     *
     * Lenient: a reply cut off mid-string (the model ran out of tokens) still unwraps to what
     * arrived. Strings in arrays become a bulleted list; a nested object's keys become headings.
     *
     * [strict] also asks that the result actually reads as Markdown (has a line break or Markdown
     * markers), for places that can't tell who wrote the text, such as the Notes list preview.
     */
    fun unwrapJson(text: String, strict: Boolean = true): String? {
        val t = text.trim()
        if (!t.startsWith("{")) return null
        val scan = Scan(t)
        if (!scan.document()) return null
        val markdown = scan.out.joinToString("\n\n") { it.trim() }.trim()
        if (markdown.isEmpty() || !scan.sawString) return null
        if (strict && '\n' !in markdown && !MarkdownImport.looksLikeMarkdown(markdown)) return null
        return markdown
    }

    // ------------------------------------------------------------------ render-time repair

    /** A Markdown block's text as it should show: unwrapped when an AI wrote it as JSON. */
    fun repairMarkdown(block: NoteBlock): String {
        val text = block.content.text
        if (!isAiSource(block.source) || !looksWrapped(text)) return text
        return clean(text)
    }

    /**
     * [blocks] with every AI paragraph that holds a JSON-wrapped reply opened out into the blocks
     * its Markdown describes, and every AI Markdown block unwrapped. Returns [blocks] itself when
     * nothing needed fixing, so callers can compare by identity.
     */
    fun repairBlocks(blocks: List<NoteBlock>, idFor: (NoteBlock, Int) -> String = { b, i -> if (i == 0) b.id else "${b.id}-r$i" }): List<NoteBlock> {
        if (blocks.none { needsRepair(it) }) return blocks
        return blocks.flatMap { b -> repaired(b, idFor) ?: listOf(b) }
    }

    /** Whether [block] is an AI paragraph or Markdown block holding a JSON-wrapped reply. */
    fun needsRepair(block: NoteBlock): Boolean = unwrappedOf(block) != null

    /** The Markdown hidden in [block], when it is an AI block that holds a JSON-wrapped reply. */
    internal fun unwrappedOf(block: NoteBlock): String? {
        if (!isAiSource(block.source)) return null
        if (block.type != NoteBlockType.PARAGRAPH && block.type != NoteBlockType.MARKDOWN) return null
        if (!looksWrapped(block.content.text)) return null
        val source = if (block.type == NoteBlockType.MARKDOWN) block.content.text else rawMarkdown(block.content)
        return if (block.type == NoteBlockType.MARKDOWN) clean(source).takeIf { it != source }
        else unwrapJson(source, strict = false)
    }

    private fun repaired(b: NoteBlock, idFor: (NoteBlock, Int) -> String): List<NoteBlock>? {
        val md = unwrappedOf(b) ?: return null
        if (b.type == NoteBlockType.MARKDOWN) return listOf(b.copy(content = RichText.plain(md)))
        val parsed = MarkdownImport.parse(md, b.noteId, b.source)
        if (parsed.isEmpty()) return null
        val meeting = b.payload[NoteBlock.PAYLOAD_MEETING_ID]
        return parsed.mapIndexed { i, p ->
            p.copy(
                id = idFor(b, i),
                position = b.position,
                sectionKey = b.sectionKey,
                isUserEdited = b.isUserEdited,
                // The moment the whole reply was matched to belongs to its first piece only.
                payload = p.payload + (if (i == 0) b.payload else listOfNotNull(meeting?.let { NoteBlock.PAYLOAD_MEETING_ID to it }).toMap()),
                sourceSegmentIds = if (i == 0) b.sourceSegmentIds else emptyList()
            )
        }
    }

    /** Fast check before any parsing: the text opens like a JSON object, perhaps in a fence. */
    private fun looksWrapped(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("{") || t.startsWith("```")
    }

    /**
     * A stored block's text with its inline styles written back as Markdown markers, without the
     * escaping [RichText.toMarkdown] adds, so a JSON string that went through [MarkdownImport.inline]
     * reads as it arrived.
     */
    internal fun rawMarkdown(content: RichText): String = buildString {
        for (run in content.runs()) {
            if (run.text.isBlank() || run.styles.isEmpty()) { append(run.text); continue }
            val core = run.text.trim()
            val lead = run.text.substring(0, run.text.indexOf(core))
            val trail = run.text.substring(lead.length + core.length)
            var body = if (InlineStyle.CODE in run.styles) "`$core`" else core
            if (InlineStyle.HIGHLIGHT in run.styles) body = "==$body=="
            if (InlineStyle.STRIKETHROUGH in run.styles) body = "~~$body~~"
            if (InlineStyle.UNDERLINE in run.styles) body = "<u>$body</u>"
            if (InlineStyle.ITALIC in run.styles) body = "*$body*"
            if (InlineStyle.BOLD in run.styles) body = "**$body**"
            if (InlineStyle.LINK in run.styles && run.url != null && run.url != core) body = "[$body](${run.url})"
            append(lead).append(body).append(trail)
        }
    }

    // ------------------------------------------------------------------ the scanner

    /**
     * A forgiving JSON reader that keeps only the text. It accepts a reply cut off anywhere, and
     * it decodes `\n`, `\"`, `\\`, `\t` and `\uXXXX`. A backslash that starts a LaTeX command
     * (`\times`, `\neq`, `\frac`) stays a backslash, so maths inside the reply survives even when
     * an earlier pass already collapsed `\\` to `\`.
     */
    private class Scan(private val s: String) {
        var i = 0
        val out = mutableListOf<String>()
        var sawString = false

        /** The whole text is one object, possibly unfinished, with nothing after it. */
        fun document(): Boolean {
            if (!obj(depth = 0)) return false
            ws()
            return i >= s.length
        }

        private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun obj(depth: Int): Boolean {
            i++ // {
            while (true) {
                ws(); if (i >= s.length) return true
                when (s[i]) {
                    '}' -> { i++; return true }
                    ',' -> { i++; continue }
                    '"' -> Unit
                    else -> return false
                }
                val key = string()
                ws(); if (i >= s.length) return true
                if (s[i] != ':') return false
                i++
                ws(); if (i >= s.length) return true
                // Inside a nested object the keys name the sections: "main_theme" → "### Main theme".
                if (depth >= 1 && s[i] != '"') heading(key, depth)
                else if (depth >= 1) {
                    val before = out.size
                    if (!value(depth)) return false
                    if (out.size > before && !out[before].trimStart().startsWith("#") && '\n' !in out[before].trim()) {
                        out[before] = labelled(key, out[before])
                    }
                    continue
                }
                if (!value(depth)) return false
            }
        }

        private fun heading(key: String, depth: Int) {
            val title = humanise(key)
            if (title.isNotEmpty()) out += "#".repeat((depth + 1).coerceAtMost(4)) + " " + title
        }

        private fun labelled(key: String, value: String): String {
            val title = humanise(key)
            return if (title.isEmpty()) value else "**$title:** ${value.trim()}"
        }

        private fun value(depth: Int): Boolean {
            ws(); if (i >= s.length) return true
            return when (s[i]) {
                '"' -> { val v = string(); sawString = true; if (v.isNotBlank()) out += v; true }
                '{' -> obj(depth + 1)
                '[' -> arr(depth + 1)
                else -> literal()
            }
        }

        private fun arr(depth: Int): Boolean {
            i++ // [
            val items = mutableListOf<String>()
            while (true) {
                ws(); if (i >= s.length) break
                when (s[i]) {
                    ']' -> { i++; break }
                    ',' -> { i++; continue }
                    '"' -> { val v = string(); sawString = true; if (v.isNotBlank()) items += v }
                    '{', '[' -> {
                        flush(items)
                        if (!(if (s[i] == '{') obj(depth) else arr(depth))) return false
                    }
                    else -> if (!literal()) return false
                }
            }
            flush(items)
            return true
        }

        /** Short one-line strings in a list read as bullets; longer ones as their own paragraphs. */
        private fun flush(items: MutableList<String>) {
            if (items.isEmpty()) return
            if (items.all { '\n' !in it.trim() }) out += items.joinToString("\n") { "- " + it.trim().removePrefix("- ").trim() }
            else out += items
            items.clear()
        }

        private fun literal(): Boolean {
            val start = i
            while (i < s.length && s[i] !in ",}] \n\r\t") i++
            val token = s.substring(start, i)
            return token == "true" || token == "false" || token == "null" || NUMBER.matches(token) ||
                (i >= s.length && token.isNotEmpty() && ("true".startsWith(token) || "false".startsWith(token) || "null".startsWith(token) || token.all { it.isDigit() || it in "-+.eE" }))
        }

        /** A string from the opening quote; an unfinished one runs to the end of the text. */
        private fun string(): String {
            i++ // "
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                if (c == '"') { i++; return sb.toString() }
                if (c != '\\' || i + 1 >= s.length) { sb.append(c); i++; continue }
                val n = s[i + 1]
                when {
                    n == '"' || n == '\\' || n == '/' -> { sb.append(n); i += 2 }
                    n == 'u' && i + 5 < s.length + 0 && s.substring(i + 2, minOf(i + 6, s.length)).let { it.length == 4 && it.all { h -> h.isDigit() || h.lowercaseChar() in 'a'..'f' } } -> {
                        sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6
                    }
                    n in "ntrbf" && !startsLatexCommand(i + 1) -> {
                        when (n) { 'n' -> sb.append('\n'); 't' -> sb.append('\t') }
                        i += 2
                    }
                    else -> { sb.append('\\'); i++ }
                }
            }
            return sb.toString()
        }

        private fun startsLatexCommand(from: Int): Boolean {
            var j = from
            while (j < s.length && s[j].isLetter()) j++
            return s.substring(from, j) in LATEX_COMMANDS
        }
    }

    private fun humanise(key: String): String {
        val words = key.trim().replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').trim()
        return words.lowercase().replaceFirstChar { it.uppercaseChar() }
    }

    private val NUMBER = Regex("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")

    /** LaTeX commands that start with a letter JSON also uses as an escape (n, t, r, b, f). */
    private val LATEX_COMMANDS = setOf(
        "ne", "neq", "nabla", "not", "nu", "ni", "neg", "nexists", "notin", "nleq", "ngeq", "newline", "nolimits", "nonumber",
        "times", "theta", "tau", "tan", "tanh", "text", "textbf", "textit", "textrm", "textsf", "texttt", "to", "tfrac",
        "tilde", "top", "triangle", "therefore", "tag", "tbinom",
        "rightarrow", "rho", "rangle", "rm", "right", "rceil", "rfloor", "rightleftharpoons", "rbrace", "rvert",
        "beta", "bar", "bf", "binom", "big", "bigg", "boxed", "begin", "bullet", "because", "bot", "bmod", "bigcup", "bigcap",
        "bmatrix", "backslash", "boldsymbol",
        "frac", "forall", "flat", "footnotesize", "frak", "fbox"
    )
}
