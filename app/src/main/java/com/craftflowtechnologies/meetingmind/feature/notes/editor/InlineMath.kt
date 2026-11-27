package com.craftflowtechnologies.meetingmind.feature.notes.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.craftflowtechnologies.meetingmind.ai.voice.read.smart.AcademicSpokenForm
import com.craftflowtechnologies.meetingmind.core.notes.NoteCitations

/**
 * Maths and chemistry inside ordinary note text: paragraphs, list items, quotes, headings, callouts
 * and the pieces of a Markdown block.
 *
 * - `$…$` and `\(…\)` are inline maths; `$$…$$` and `\[…\]` are display maths (an equation on lines
 *   of its own becomes an equation block when Markdown is read; inside a sentence it shows inline).
 * - `\ce{…}` is chemistry, inside dollars or on its own: `\ce{2H2 + O2 -> 2H2O}`.
 *
 * **Renderer decision (2026-11-16).** Inline maths reuses the native [LatexMathParser] from
 * AcademicBlocks and turns its tree into styled text (italic variables, raised and lowered scripts,
 * Unicode operators, `a⁄b` fractions). That lets a formula sit inside a line of text, wrap with it,
 * take the theme's ink in light and dark, and stay real text for screen readers and selection.
 * Offline KaTeX was weighed and not used: it needs a WebView per formula (tens of MB of memory and
 * 100–300 ms each to start, too slow for a scrolling list of blocks), about 1 MB of fonts and
 * scripts in the APK, and its output can't flow inside a Compose text field. The cost of the native
 * route is typography: inline fractions are slashed rather than stacked, and roots have no bar.
 * Display maths keeps the stacked 2D renderer ([MathExpressionView]) in equation blocks.
 *
 * Rules that keep ordinary text safe: a `$` only opens maths when a non-space follows it, and only
 * closes when a non-space comes before it and no digit after it, so "$5 and $10" stays money. A
 * formula that won't parse shows as readable plain text, never as broken LaTeX.
 */
object InlineMath {

    data class Segment(val start: Int, val end: Int, val latex: String, val display: Boolean)

    /** The formulas in [text], in order, never overlapping. */
    fun find(text: String): List<Segment> {
        if ('$' !in text && '\\' !in text) return emptyList()
        val out = mutableListOf<Segment>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            var found: Segment? = null
            if (c == '\\' && next == '$') { i += 2; continue } // \$ is a dollar sign
            if (c == '\\' && next == '(') closeAt(text, i + 2, "\\)")?.let { e -> found = Segment(i, e + 2, text.substring(i + 2, e), false) }
            if (c == '\\' && next == '[') closeAt(text, i + 2, "\\]")?.let { e -> found = Segment(i, e + 2, text.substring(i + 2, e), true) }
            if (c == '\\' && text.startsWith("\\ce{", i)) braceEnd(text, i + 3)?.let { e -> found = Segment(i, e + 1, text.substring(i, e + 1), false) }
            if (c == '$' && next == '$') {
                val e = text.indexOf("$$", i + 2)
                if (e > i + 2 && text.substring(i + 2, e).isNotBlank()) found = Segment(i, e + 2, text.substring(i + 2, e), true)
            } else if (c == '$') {
                inlineDollarEnd(text, i)?.let { e -> found = Segment(i, e + 1, text.substring(i + 1, e), false) }
            }
            val segment = found
            if (segment != null) { out += segment; i = segment.end } else i++
        }
        return out
    }

    fun hasMath(text: String): Boolean = find(text).isNotEmpty()

    private fun closeAt(text: String, from: Int, close: String): Int? =
        text.indexOf(close, from).takeIf { it > from && text.substring(from, it).isNotBlank() }

    private fun braceEnd(text: String, open: Int): Int? {
        var depth = 0
        var j = open
        while (j < text.length) {
            when (text[j]) {
                '\\' -> j++
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return j }
            }
            j++
        }
        return null
    }

    /** Where a `$` at [start] closes, under the money-safe rules, or null. */
    private fun inlineDollarEnd(text: String, start: Int): Int? {
        val first = text.getOrNull(start + 1) ?: return null
        if (first.isWhitespace() || first == '$') return null
        var j = start + 1
        while (j < text.length) {
            val c = text[j]
            if (c == '\n') return null
            if (c == '\\') { j += 2; continue }
            if (c == '$') {
                val before = text[j - 1]
                val after = text.getOrNull(j + 1)
                return if (!before.isWhitespace() && (after == null || !after.isDigit())) j else null
            }
            j++
        }
        return null
    }

    // ------------------------------------------------------------------ rendering

    /** One formula as styled text; a formula that won't parse comes back as readable plain text. */
    fun render(latex: String): AnnotatedString {
        if (!balanced(latex)) return AnnotatedString(readable(latex))
        val node = runCatching { LatexMathParser.parse(latex) }.getOrNull() ?: return AnnotatedString(readable(latex))
        return runCatching { buildAnnotatedString { appendMath(node) } }.getOrElse { AnnotatedString(readable(latex)) }
    }

    /** Spoken form of text with maths in it, for screen readers. */
    fun spoken(text: String): String = runCatching { AcademicSpokenForm.apply(text) }.getOrDefault(text)

    /**
     * [styled] with each formula drawn as maths and, when [hideMarkers], `[n]` citation markers
     * hidden (an AI note's markers whose source isn't known). Offsets map both ways so the caret
     * and selection still land on the source text.
     */
    fun transform(styled: AnnotatedString, maths: Boolean = true, hideMarkers: Boolean = false): TransformedText {
        val text = styled.text
        val replacements = mutableListOf<Triple<Int, Int, AnnotatedString>>()
        if (maths) find(text).forEach { replacements += Triple(it.start, it.end, render(it.latex)) }
        if (hideMarkers) NoteCitations.hidden(text).forEach { r ->
            if (replacements.none { r.first < it.second && r.last + 1 > it.first }) replacements += Triple(r.first, r.last + 1, AnnotatedString(""))
        }
        if (replacements.isEmpty()) return TransformedText(styled, OffsetMapping.Identity)
        replacements.sortBy { it.first }
        val out = AnnotatedString.Builder()
        // (original start, original end, transformed start, transformed end) for each replacement.
        val map = mutableListOf<IntArray>()
        var at = 0
        for ((s, e, shown) in replacements) {
            if (s < at) continue
            out.append(styled.subSequence(at, s))
            val ts = out.length
            out.append(shown)
            map += intArrayOf(s, e, ts, out.length)
            at = e
        }
        out.append(styled.subSequence(at, text.length))
        val transformed = out.toAnnotatedString()
        return TransformedText(transformed, RangeMapping(map, text.length, transformed.length))
    }

    /** Offsets around replaced ranges: inside a replaced range, the caret sits at its end. */
    private class RangeMapping(private val map: List<IntArray>, private val originalLength: Int, private val transformedLength: Int) : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            var delta = 0
            for (m in map) {
                if (offset <= m[0]) break
                if (offset < m[1]) return m[3].coerceIn(0, transformedLength)
                delta += (m[3] - m[2]) - (m[1] - m[0])
            }
            return (offset + delta).coerceIn(0, transformedLength)
        }

        override fun transformedToOriginal(offset: Int): Int {
            var delta = 0
            for (m in map) {
                if (offset <= m[2]) break
                if (offset < m[3]) return m[1].coerceIn(0, originalLength)
                delta += (m[1] - m[0]) - (m[3] - m[2])
            }
            return (offset + delta).coerceIn(0, originalLength)
        }
    }

    // ------------------------------------------------------------------ tree → styled text

    private val MATH_FONT = SpanStyle(fontFamily = FontFamily.Serif)
    private val SUP = SpanStyle(baselineShift = BaselineShift(0.38f), fontSize = 0.72.em)
    private val SUB = SpanStyle(baselineShift = BaselineShift(-0.18f), fontSize = 0.72.em)
    private val SPACED = setOf("=", "≠", "≤", "≥", "<", ">", "≈", "≡", "→", "←", "⇒", "⇐", "⇔", "↔", "⇌", "+", "−", "×", "÷", "±", "∓", "∈", "∉", "⊂", "⊆", "∪", "∩", "·")

    private fun AnnotatedString.Builder.appendMath(root: LatexNode) {
        withStyle(MATH_FONT) { node(root) }
    }

    private fun AnnotatedString.Builder.node(n: LatexNode) {
        when (n) {
            is LatexNode.Group -> n.children.forEach { node(it) }
            is LatexNode.Chemical -> n.children.forEach { node(it) }
            is LatexNode.Text -> if (n.italicVariable) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(n.text) } else append(n.text)
            is LatexNode.Symbol -> {
                val sym = n.symbol.trim().ifEmpty { n.symbol }
                // Chemistry arrows already carry their spaces; maths operators get thin ones.
                if (sym in SPACED && n.symbol == sym) append(" $sym ") else append(n.symbol)
            }
            is LatexNode.Fraction -> {
                val num = LatexMathParser.toPlainDisplay(n.num)
                val den = LatexMathParser.toPlainDisplay(n.den)
                if (num.length <= 3 && den.length <= 3 && simple(n.num) && simple(n.den)) {
                    withStyle(SUP) { node(n.num) }
                    append("⁄")
                    withStyle(SUB) { node(n.den) }
                } else {
                    wrapped(n.num); append("/"); wrapped(n.den)
                }
            }
            is LatexNode.Sqrt -> {
                n.degree?.let { d -> withStyle(SUP) { node(d) } }
                append("√")
                wrapped(n.radicand)
            }
            is LatexNode.SubSup -> {
                node(n.base)
                n.sub?.let { withStyle(SUB) { node(it) } }
                n.sup?.let { withStyle(SUP) { node(it) } }
            }
            is LatexNode.BigOp -> {
                append(n.op)
                n.lower?.let { withStyle(SUB) { node(it) } }
                n.upper?.let { withStyle(SUP) { node(it) } }
                n.body?.let { append(" "); node(it) }
            }
            is LatexNode.Matrix -> {
                append("[")
                n.rows.forEachIndexed { r, row ->
                    if (r > 0) append("; ")
                    row.forEachIndexed { c, cell -> if (c > 0) append(", "); node(cell) }
                }
                append("]")
            }
            is LatexNode.Aligned -> n.steps.forEachIndexed { k, step ->
                if (k > 0) append(";  ")
                step.lhs?.let { node(it); append(" ") }
                node(step.rhs)
            }
            is LatexNode.Accent -> {
                node(n.base)
                append(
                    when (n.kind) {
                        MathAccentKind.HAT -> "̂"
                        MathAccentKind.BAR -> "̄"
                        MathAccentKind.VEC -> "⃗"
                        MathAccentKind.TILDE -> "̃"
                        MathAccentKind.DOT -> "̇"
                    }
                )
            }
        }
    }

    /** A part of a slashed fraction or a root: in brackets when it is more than one thing. */
    private fun AnnotatedString.Builder.wrapped(n: LatexNode) {
        if (atomic(n)) node(n) else { append("("); node(n); append(")") }
    }

    /** One symbol, number or variable (perhaps with a script): needs no brackets after "/" or "√". */
    private fun atomic(n: LatexNode): Boolean = when (n) {
        is LatexNode.Text, is LatexNode.Symbol -> true
        is LatexNode.SubSup -> atomic(n.base)
        is LatexNode.Accent -> atomic(n.base)
        is LatexNode.Group -> n.children.size == 1 && atomic(n.children[0])
        else -> false
    }

    private fun simple(n: LatexNode): Boolean = when (n) {
        is LatexNode.Text -> true
        is LatexNode.Symbol -> true
        is LatexNode.SubSup -> simple(n.base) && n.base !is LatexNode.Group
        is LatexNode.Group -> n.children.size == 1 && simple(n.children[0]) ||
            n.children.all { it is LatexNode.Text && !it.text.contains(' ') } // 2x, 10, ab
        else -> false
    }

    private fun balanced(latex: String): Boolean {
        var depth = 0
        var i = 0
        while (i < latex.length) {
            when (latex[i]) {
                '\\' -> i++
                '{' -> depth++
                '}' -> { depth--; if (depth < 0) return false }
            }
            i++
        }
        return depth == 0
    }

    /** LaTeX a person can still read: commands as words or symbols, no braces or backslashes. */
    internal fun readable(latex: String): String =
        latex.replace(Regex("""\\([A-Za-z]+)""")) { m -> READABLE[m.groupValues[1]] ?: m.groupValues[1] }
            .replace(Regex("""\\(.)"""), "$1")
            .replace("{", "").replace("}", "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val READABLE = mapOf(
        "frac" to "", "dfrac" to "", "sqrt" to "√", "times" to "×", "cdot" to "·", "pm" to "±", "le" to "≤", "leq" to "≤",
        "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠", "approx" to "≈", "infty" to "∞", "pi" to "π", "alpha" to "α",
        "beta" to "β", "theta" to "θ", "to" to "→", "rightarrow" to "→", "ce" to "", "text" to "", "mathrm" to "", "left" to "", "right" to ""
    )
}
