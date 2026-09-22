package com.example.core.notes

import java.net.URLDecoder
import java.net.URLEncoder

enum class InlineStyle { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, HIGHLIGHT, CODE, LINK }

/** A style over the half-open character range [start, end). [url] is set only for [InlineStyle.LINK]. */
data class InlineSpan(val start: Int, val end: Int, val style: InlineStyle, val url: String? = null) {
    val length: Int get() = end - start
}

/** A stretch of text whose styling does not change — the unit exporters draw. */
data class TextRun(val text: String, val styles: Set<InlineStyle>, val url: String? = null)

/**
 * Styled text for one note block.
 *
 * MeetingMind's own format rather than HTML, and deliberately small: plain text plus a list of
 * style ranges. That keeps the editor's work to arithmetic on ranges, which is what can be tested
 * without a device, and lets every exporter walk the same [runs].
 *
 * Instances are always normalised: spans lie inside the text, are non-empty, and overlapping or
 * touching spans of the same style (and link target) are merged into one.
 */
class RichText private constructor(val text: String, val spans: List<InlineSpan>) {

    val isEmpty: Boolean get() = text.isEmpty()

    /**
     * Whether [style] covers every character of [start, end).
     *
     * A collapsed selection asks about the character before the cursor, which is what a toolbar
     * button should show: after typing a bold word, Bold reads as on.
     */
    fun hasStyle(style: InlineStyle, start: Int, end: Int): Boolean {
        val (s, e) = clampRange(start, end)
        if (s == e) {
            if (s == 0) return false
            return spans.any { it.style == style && it.start < s && it.end >= s }
        }
        var covered = s
        for (span in spans.filter { it.style == style }.sortedBy { it.start }) {
            if (span.start > covered) break
            if (span.end > covered) covered = span.end
            if (covered >= e) return true
        }
        return false
    }

    /** The link at [position], if any — for the editor's "edit link" affordance. */
    fun linkAt(position: Int): InlineSpan? =
        spans.firstOrNull { it.style == InlineStyle.LINK && position >= it.start && position < it.end }

    fun applyStyle(style: InlineStyle, start: Int, end: Int, url: String? = null): RichText {
        val (s, e) = clampRange(start, end)
        if (s == e) return this
        // A new link replaces any old one it overlaps; two links cannot share a character.
        val base = if (style == InlineStyle.LINK) removeStyle(InlineStyle.LINK, s, e).spans else spans
        return create(text, base + InlineSpan(s, e, style, if (style == InlineStyle.LINK) url else null))
    }

    fun removeStyle(style: InlineStyle, start: Int, end: Int): RichText {
        val (s, e) = clampRange(start, end)
        if (s == e) return this
        val result = mutableListOf<InlineSpan>()
        for (span in spans) {
            if (span.style != style || span.end <= s || span.start >= e) {
                result += span
                continue
            }
            if (span.start < s) result += span.copy(end = s)
            if (span.end > e) result += span.copy(start = e)
        }
        return create(text, result)
    }

    /** Word-processor toggle: if the whole range already has the style, remove it; else apply it. */
    fun toggleStyle(style: InlineStyle, start: Int, end: Int, url: String? = null): RichText =
        if (hasStyle(style, start, end)) removeStyle(style, start, end) else applyStyle(style, start, end, url)

    /**
     * Carries the styling across an edit made in a plain text field.
     *
     * The field reports only its new text, so the edit is recovered as the region between the
     * longest common prefix and suffix. Styles before the edit stay put, styles after it shift,
     * and styles inside a deleted region shrink.
     *
     * Text typed straight after a styled run continues that style, as it does in every word
     * processor, except for links and code, which stop where they end. [pendingStyles] are
     * styles the user switched on with no selection; they apply to the inserted text.
     * [suppressedStyles] were switched off the same way and do not continue.
     */
    fun withEditedText(
        newText: String,
        pendingStyles: Set<InlineStyle> = emptySet(),
        suppressedStyles: Set<InlineStyle> = emptySet()
    ): RichText {
        if (newText == text) return this
        val oldLen = text.length
        val newLen = newText.length
        var prefix = 0
        val maxPrefix = minOf(oldLen, newLen)
        while (prefix < maxPrefix && text[prefix] == newText[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(oldLen, newLen) - prefix
        while (suffix < maxSuffix && text[oldLen - 1 - suffix] == newText[newLen - 1 - suffix]) suffix++

        val deletedEnd = oldLen - suffix
        val inserted = newLen - prefix - suffix
        val delta = newLen - oldLen

        fun map(pos: Int): Int = when {
            pos <= prefix -> pos
            pos >= deletedEnd -> pos + delta
            else -> prefix
        }

        val moved = spans.map { span ->
            var start = map(span.start)
            var end = map(span.end)
            val continues = inserted > 0 && span.end == prefix && span.start < prefix &&
                span.style !in NON_CONTINUING && span.style !in suppressedStyles
            if (continues) end = prefix + inserted
            // Text inserted at the very start of a span does not take the span's style. Typing over
            // a selection that starts there does: the replacement keeps the look of what it replaced.
            if (span.start == prefix && deletedEnd == prefix && inserted > 0) start = prefix + inserted
            span.copy(start = start, end = end.coerceAtLeast(start))
        }.toMutableList()

        if (inserted > 0 && suppressedStyles.isNotEmpty()) {
            // A suppressed style must not cover the new characters even through a wider span.
            val cut = create(newText, moved).let { rt ->
                suppressedStyles.fold(rt) { acc, style -> acc.removeStyle(style, prefix, prefix + inserted) }
            }
            moved.clear(); moved += cut.spans
        }
        val withPending = pendingStyles.filter { it != InlineStyle.LINK }
            .map { InlineSpan(prefix, prefix + inserted, it) }
        return create(newText, moved + if (inserted > 0) withPending else emptyList())
    }

    /** Splits at [position], as pressing Enter in the middle of a block does. */
    fun splitAt(position: Int): Pair<RichText, RichText> {
        val p = position.coerceIn(0, text.length)
        val left = spans.filter { it.start < p }.map { it.copy(end = minOf(it.end, p)) }
        val right = spans.filter { it.end > p }.map { it.copy(start = maxOf(it.start, p) - p, end = it.end - p) }
        return create(text.substring(0, p), left) to create(text.substring(p), right)
    }

    /** Joins [other] onto the end, as Backspace at the start of a block does. */
    fun append(other: RichText): RichText {
        val offset = text.length
        return create(text + other.text, spans + other.spans.map { it.copy(start = it.start + offset, end = it.end + offset) })
    }

    /** The text cut into runs of identical styling, in order. Never empty for non-empty text. */
    fun runs(): List<TextRun> {
        if (text.isEmpty()) return emptyList()
        val boundaries = sortedSetOf(0, text.length)
        spans.forEach { boundaries += it.start; boundaries += it.end }
        val points = boundaries.toList()
        val runs = mutableListOf<TextRun>()
        for (i in 0 until points.size - 1) {
            val s = points[i]
            val e = points[i + 1]
            if (s == e) continue
            val active = spans.filter { it.start <= s && it.end >= e }
            runs += TextRun(
                text = text.substring(s, e),
                styles = active.map { it.style }.toSet(),
                url = active.firstOrNull { it.style == InlineStyle.LINK }?.url
            )
        }
        return runs
    }

    /** Markdown for export and for the plain-text index. Underline has no Markdown form and uses `<u>`. */
    fun toMarkdown(): String = buildString {
        for (run in runs()) {
            if (run.text.isBlank()) { append(run.text); continue }
            // Markers must hug the words, so leading and trailing spaces stay outside them.
            val core = run.text.trim()
            val lead = run.text.substring(0, run.text.indexOf(core))
            val trail = run.text.substring(lead.length + core.length)
            var body = if (InlineStyle.CODE in run.styles) "`${core.replace("`", "\\`")}`" else escapeMarkdown(core)
            if (InlineStyle.HIGHLIGHT in run.styles) body = "==$body=="
            if (InlineStyle.STRIKETHROUGH in run.styles) body = "~~$body~~"
            if (InlineStyle.UNDERLINE in run.styles) body = "<u>$body</u>"
            if (InlineStyle.ITALIC in run.styles) body = "*$body*"
            if (InlineStyle.BOLD in run.styles) body = "**$body**"
            if (InlineStyle.LINK in run.styles && run.url != null) body = "[$body](${run.url})"
            append(lead).append(body).append(trail)
        }
    }

    /** Compact storage form of [spans]: `STYLE,start,end[,url-encoded]` joined by `;`. */
    fun encodeSpans(): String = spans.joinToString(";") { span ->
        val base = "${span.style.name},${span.start},${span.end}"
        if (span.url != null) "$base,${URLEncoder.encode(span.url, "UTF-8")}" else base
    }

    private fun clampRange(start: Int, end: Int): Pair<Int, Int> {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        return s to e
    }

    override fun equals(other: Any?): Boolean =
        other is RichText && other.text == text && other.spans == spans

    override fun hashCode(): Int = 31 * text.hashCode() + spans.hashCode()

    override fun toString(): String = "RichText(text=$text, spans=$spans)"

    companion object {
        val EMPTY = RichText("", emptyList())

        private val NON_CONTINUING = setOf(InlineStyle.LINK, InlineStyle.CODE)
        private val MARKDOWN_SPECIALS = setOf('\\', '*', '_', '`', '[', ']', '~', '<', '>', '#', '=')

        fun plain(text: String): RichText = RichText(text, emptyList())

        fun create(text: String, spans: List<InlineSpan>): RichText = RichText(text, normalise(text.length, spans))

        /** Reads [encodeSpans] output. Malformed entries are dropped rather than failing the note. */
        fun decode(text: String, encodedSpans: String?): RichText {
            if (encodedSpans.isNullOrBlank()) return plain(text)
            val spans = encodedSpans.split(';').mapNotNull { entry ->
                val parts = entry.split(',', limit = 4)
                if (parts.size < 3) return@mapNotNull null
                val style = runCatching { InlineStyle.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                val start = parts[1].toIntOrNull() ?: return@mapNotNull null
                val end = parts[2].toIntOrNull() ?: return@mapNotNull null
                val url = parts.getOrNull(3)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                InlineSpan(start, end, style, url)
            }
            return create(text, spans)
        }

        private fun escapeMarkdown(text: String): String = buildString {
            text.forEach { c -> if (c in MARKDOWN_SPECIALS) append('\\'); append(c) }
        }

        private fun normalise(length: Int, spans: List<InlineSpan>): List<InlineSpan> {
            val clamped = spans.mapNotNull { span ->
                val s = span.start.coerceIn(0, length)
                val e = span.end.coerceIn(0, length)
                if (e <= s) null
                else if (span.style == InlineStyle.LINK && span.url.isNullOrBlank()) null
                else span.copy(start = s, end = e, url = if (span.style == InlineStyle.LINK) span.url else null)
            }
            val merged = mutableListOf<InlineSpan>()
            clamped.groupBy { it.style to it.url }.forEach { (_, group) ->
                var current: InlineSpan? = null
                for (span in group.sortedBy { it.start }) {
                    val c = current
                    current = when {
                        c == null -> span
                        span.start <= c.end -> c.copy(end = maxOf(c.end, span.end))
                        else -> { merged += c; span }
                    }
                }
                current?.let { merged += it }
            }
            // Two different links may not overlap; the one that starts later wins the overlap.
            val links = merged.filter { it.style == InlineStyle.LINK }.sortedBy { it.start }
            val trimmedLinks = links.mapIndexedNotNull { i, link ->
                val next = links.getOrNull(i + 1)
                val end = if (next != null && next.start < link.end) next.start else link.end
                if (end > link.start) link.copy(end = end) else null
            }
            return (merged.filter { it.style != InlineStyle.LINK } + trimmedLinks)
                .sortedWith(compareBy({ it.start }, { it.style.ordinal }, { it.end }))
        }
    }
}
