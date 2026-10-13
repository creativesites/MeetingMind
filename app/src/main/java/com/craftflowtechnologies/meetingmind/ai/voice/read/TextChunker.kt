package com.craftflowtechnologies.meetingmind.ai.voice.read

/**
 * Splits text for a speech engine that accepts only so many characters per request (Android's
 * `TextToSpeech.getMaxSpeechInputLength()` is 4000). Pure.
 *
 * Nothing is dropped and the order never changes: every non-whitespace character of the input is in
 * exactly one piece, in order. Whitespace runs are normalised to single spaces. A break is made at the
 * last sentence end that fits, else the last clause mark, else the last space, and only a single
 * token longer than the limit is cut inside itself.
 */
object TextChunker {

    fun split(text: String, maxChars: Int): List<String> {
        require(maxChars >= 1) { "maxChars must be positive" }
        var rest = text.trim().replace(WHITESPACE, " ")
        if (rest.isEmpty()) return emptyList()
        val pieces = mutableListOf<String>()
        while (rest.length > maxChars) {
            val cut = breakPoint(rest, maxChars)
            pieces += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }

    /** The index just after the best place to end a piece of at most [max] characters. */
    private fun breakPoint(s: String, max: Int): Int {
        val window = s.substring(0, max + 1) // one extra so a break right at the limit is seen
        lastBreak(window, max, SENTENCE_END)?.let { return it }
        lastBreak(window, max, CLAUSE_END)?.let { return it }
        val space = window.lastIndexOf(' ', max)
        if (space > 0) return space
        // One token longer than the limit: cut inside it, never between the halves of a surrogate pair.
        var cut = max
        if (cut > 1 && Character.isHighSurrogate(s[cut - 1])) cut -= 1
        return cut
    }

    /** End of the last match of [marks] followed by a space (or the end of the window) that fits in [max]. */
    private fun lastBreak(window: String, max: Int, marks: Regex): Int? {
        var best: Int? = null
        for (m in marks.findAll(window)) {
            val end = m.range.last + 1
            if (end in 1..max) best = end
        }
        return best
    }

    private val WHITESPACE = Regex("""\s+""")
    private val SENTENCE_END = Regex("""[.!?…]+["'”’)\]]*(?= )""")
    private val CLAUSE_END = Regex("""[;:,—–](?= )""")
}
