package com.example.core.notes

/**
 * A pasted Markdown document as the pieces a person edits: a paragraph, a whole list, a table,
 * a code fence, a heading with its line. Blank lines separate pieces — except inside a code
 * fence, and inside a list whose items are only spaced apart. Joining the pieces with a blank line
 * gives back the document (for what was there: runs of blank lines collapse to one).
 */
object MarkdownChunks {
    private val FENCE = Regex("^\\s*(```|~~~)")
    private val LIST_ITEM = Regex("^\\s*(?:[-*+]|\\d{1,3}[.)])\\s+")
    private val INDENTED = Regex("^\\s{2,}\\S")

    fun split(markdown: String): List<String> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = mutableListOf<MutableList<String>>()
        var cur = mutableListOf<String>()
        var fence: String? = null
        var pendingBlank = false
        fun flush() { if (cur.isNotEmpty()) out += cur; cur = mutableListOf() }
        for (line in lines) {
            val f = FENCE.find(line)?.groupValues?.get(1)
            if (fence != null) {
                cur += line
                if (f == fence) fence = null
                continue
            }
            if (line.isBlank()) { if (cur.isNotEmpty()) pendingBlank = true; continue }
            val startsList = LIST_ITEM.containsMatchIn(line) || INDENTED.containsMatchIn(line)
            val inList = cur.isNotEmpty() && (LIST_ITEM.containsMatchIn(cur.last()) || INDENTED.containsMatchIn(cur.last()))
            if (pendingBlank && !(startsList && inList)) flush() else if (pendingBlank) cur += ""
            pendingBlank = false
            if (f != null) { if (cur.isNotEmpty()) flush(); fence = f }
            cur += line
        }
        flush()
        return out.map { it.joinToString("\n").trimEnd() }.filter { it.isNotBlank() }
    }

    fun join(chunks: List<String>): String = chunks.filter { it.isNotBlank() }.joinToString("\n\n")

    /** The document with piece [index] replaced (an empty replacement removes it). */
    fun replace(chunks: List<String>, index: Int, text: String): List<String> =
        chunks.toMutableList().also { if (index in it.indices) it[index] = text }
}
