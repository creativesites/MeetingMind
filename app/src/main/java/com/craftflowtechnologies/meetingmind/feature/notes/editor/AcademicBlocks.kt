package com.craftflowtechnologies.meetingmind.feature.notes.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.InlineStyle
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import org.json.JSONArray
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// 1. PURE-COMPOSE NATIVE MATH TYPOGRAPHER & LATEX AST PARSER
// ─────────────────────────────────────────────────────────────────────────────

enum class MatrixBracket { SQUARE, PAREN, PIPE, BRACE, NONE }

enum class MathAccentKind { HAT, BAR, VEC, TILDE, DOT }

data class AlignedStep(
    val lhs: LatexNode?,
    val rhs: LatexNode,
    val reason: String? = null
)

sealed interface LatexNode {
    data class Group(val children: List<LatexNode>) : LatexNode
    data class Text(val text: String, val italicVariable: Boolean = false) : LatexNode
    data class Symbol(val symbol: String, val command: String = "") : LatexNode
    data class Fraction(val num: LatexNode, val den: LatexNode) : LatexNode
    data class Sqrt(val radicand: LatexNode, val degree: LatexNode? = null) : LatexNode
    data class SubSup(val base: LatexNode, val sub: LatexNode? = null, val sup: LatexNode? = null) : LatexNode
    data class BigOp(
        val op: String,
        val lower: LatexNode? = null,
        val upper: LatexNode? = null,
        val body: LatexNode? = null
    ) : LatexNode
    data class Matrix(
        val rows: List<List<LatexNode>>,
        val bracketKind: MatrixBracket = MatrixBracket.SQUARE
    ) : LatexNode
    data class Aligned(val steps: List<AlignedStep>) : LatexNode
    data class Accent(val kind: MathAccentKind, val base: LatexNode) : LatexNode
    data class Chemical(val raw: String, val children: List<LatexNode>) : LatexNode
}

object LatexMathParser {

    private val SYMBOL_COMMANDS: Map<String, String> = mapOf(
        // Lowercase Greek
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ϵ", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "varsigma" to "ς",
        "tau" to "τ", "upsilon" to "υ", "phi" to "ϕ", "varphi" to "φ",
        "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        // Uppercase Greek
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        // Calculus, algebra, logic, set theory, relations & arrows
        "partial" to "∂", "nabla" to "∇", "infty" to "∞",
        "pm" to "±", "mp" to "∓", "times" to "×", "cdot" to "·", "div" to "÷",
        "ast" to "∗", "star" to "⋆", "circ" to "∘", "bullet" to "∙",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
        "ne" to "≠", "neq" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
        "in" to "∈", "notin" to "∉", "ni" to "∋",
        "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆", "supseteq" to "⊇",
        "cup" to "∪", "cap" to "∩", "emptyset" to "∅", "varnothing" to "∅", "setminus" to "∖",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄",
        "neg" to "¬", "lnot" to "¬", "land" to "∧", "wedge" to "∧", "lor" to "∨", "vee" to "∨",
        "therefore" to "∴", "because" to "∵",
        "Rightarrow" to "⇒", "implies" to "⇒", "Leftarrow" to "⇐",
        "Leftrightarrow" to "⇔", "iff" to "⇔",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "leftrightarrow" to "↔",
        "mapsto" to "↦", "rightleftharpoons" to "⇌",
        "langle" to "⟨", "rangle" to "⟩", "dots" to "…", "ldots" to "…", "cdots" to "⋯"
    )

    private val BIG_OPS: Map<String, String> = mapOf(
        "sum" to "∑",
        "int" to "∫",
        "iint" to "∬",
        "iiint" to "∭",
        "oint" to "∮",
        "prod" to "∏",
        "lim" to "lim",
        "bigcup" to "⋃",
        "bigcap" to "⋂"
    )

    private val UPRIGHT_FUNCTIONS: Set<String> = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
        "ln", "log", "exp", "min", "max", "det", "dim", "ker", "gcd", "sup", "inf", "mod"
    )

    private val BLACKBOARD_BOLD: Map<String, String> = mapOf(
        "R" to "ℝ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ", "E" to "𝔼", "P" to "ℙ"
    )

    private val RELATIONAL_STOP_SYMBOLS = setOf("=", "≠", "≤", "≥", "<", ">", "≈", "≡", "→", "⇒", "⇔", "⇌")

    fun parse(rawLatex: String, mathStepsPayload: String? = null): LatexNode {
        val stripped = stripMathDelimiters(rawLatex.trim())
        if (stripped.isEmpty()) return LatexNode.Text("")
        val parsed = ParserCursor(stripped).parseSequence()
        return mergeStepReasons(parsed, mathStepsPayload)
    }

    fun mergeStepReasons(node: LatexNode, mathStepsPayload: String?): LatexNode {
        if (mathStepsPayload.isNullOrBlank() || node !is LatexNode.Aligned) return node
        val extraReasons = parseStepReasonsList(mathStepsPayload)
        if (extraReasons.isEmpty()) return node
        return node.copy(
            steps = node.steps.mapIndexed { idx, step ->
                if (!step.reason.isNullOrBlank()) step
                else step.copy(reason = extraReasons.getOrNull(idx)?.takeIf { it.isNotBlank() })
            }
        )
    }

    private fun parseStepReasonsList(payload: String): List<String> {
        val trimmed = payload.trim()
        if (trimmed.startsWith("[")) {
            runCatching {
                val arr = JSONArray(trimmed)
                return (0 until arr.length()).map { arr.optString(it, "") }
            }
        }
        return trimmed.lines().map { it.trim() }
    }

    fun toPlainDisplay(node: LatexNode): String = when (node) {
        is LatexNode.Group -> node.children.joinToString("") { toPlainDisplay(it) }
        is LatexNode.Text -> node.text
        is LatexNode.Symbol -> node.symbol
        is LatexNode.Fraction -> "(${toPlainDisplay(node.num)})/(${toPlainDisplay(node.den)})"
        is LatexNode.Sqrt -> if (node.degree != null) "√[${toPlainDisplay(node.degree)}](${toPlainDisplay(node.radicand)})" else "√(${toPlainDisplay(node.radicand)})"
        is LatexNode.SubSup -> buildString {
            append(toPlainDisplay(node.base))
            node.sub?.let { append("_").append(toPlainDisplay(it)) }
            node.sup?.let { append("^").append(toPlainDisplay(it)) }
        }
        is LatexNode.BigOp -> buildString {
            append(node.op)
            node.lower?.let { append("_(").append(toPlainDisplay(it)).append(")") }
            node.upper?.let { append("^(").append(toPlainDisplay(it)).append(")") }
            node.body?.let { append(" ").append(toPlainDisplay(it)) }
        }
        is LatexNode.Matrix -> node.rows.joinToString("; ", prefix = "[", postfix = "]") { row ->
            row.joinToString(", ") { toPlainDisplay(it) }
        }
        is LatexNode.Aligned -> node.steps.joinToString("\n") { step ->
            val l = step.lhs?.let { toPlainDisplay(it) + " " }.orEmpty()
            val r = toPlainDisplay(step.rhs)
            val why = step.reason?.let { "  [$it]" }.orEmpty()
            "$l$r$why"
        }
        is LatexNode.Accent -> toPlainDisplay(node.base)
        is LatexNode.Chemical -> node.children.joinToString("") { toPlainDisplay(it) }
    }

    private fun stripMathDelimiters(s: String): String = when {
        s.startsWith("$$") && s.endsWith("$$") && s.length >= 4 -> s.substring(2, s.length - 2).trim()
        s.startsWith("\\[") && s.endsWith("\\]") && s.length >= 4 -> s.substring(2, s.length - 2).trim()
        s.startsWith("\\(") && s.endsWith("\\)") && s.length >= 4 -> s.substring(2, s.length - 2).trim()
        s.startsWith("$") && s.endsWith("$") && s.length >= 2 && s.substring(1, s.length - 1).none { it == '$' } ->
            s.substring(1, s.length - 1).trim()
        else -> s
    }

    /** Splits [input] by [delimiter] only at brace depth 0 and environment depth 0. */
    internal fun splitTopLevel(input: String, delimiter: String): List<String> {
        val result = mutableListOf<String>()
        var braceDepth = 0
        var envDepth = 0
        var lastSplit = 0
        var i = 0
        while (i < input.length) {
            if (input[i] == '\\' && input.startsWith("\\begin{", i)) {
                envDepth++
                val close = input.indexOf('}', i + 7)
                i = if (close > i) close + 1 else i + 7
                continue
            }
            if (input[i] == '\\' && input.startsWith("\\end{", i)) {
                envDepth = max(0, envDepth - 1)
                val close = input.indexOf('}', i + 5)
                i = if (close > i) close + 1 else i + 5
                continue
            }
            if (braceDepth == 0 && envDepth == 0 && input.startsWith(delimiter, i)) {
                // Ensure single '&' does not split in the middle of '&&' when delimiter == "&"
                if (delimiter == "&" && input.startsWith("&&", i)) {
                    i += 2
                    continue
                }
                if (delimiter == "\\" && !input.startsWith("\\\\", i)) {
                    i++
                    continue
                }
                result += input.substring(lastSplit, i)
                i += delimiter.length
                lastSplit = i
                continue
            }
            when (input[i]) {
                '\\' -> i += 2
                '{' -> { braceDepth++; i++ }
                '}' -> { braceDepth = max(0, braceDepth - 1); i++ }
                else -> i++
            }
        }
        result += input.substring(lastSplit)
        return result
    }

    private fun extractPlainReason(raw: String): String {
        var s = raw.trim()
        val textRegex = Regex("""\\(?:text|mathrm|textit|textbf)\{([^}]*)\}""")
        s = textRegex.replace(s) { it.groupValues[1] }
        s = s.replace("\\quad", " ").replace("\\qquad", " ").replace("\\,", " ").replace("\\;", " ")
        return s.trim()
    }

    internal fun parseChemical(formula: String): LatexNode.Chemical {
        val tokens = mutableListOf<LatexNode>()
        // Split around reaction arrows and standalone '+' signs (with surrounding spaces)
        val normalized = formula.trim()
        var idx = 0
        while (idx < normalized.length) {
            while (idx < normalized.length && normalized[idx].isWhitespace()) idx++
            if (idx >= normalized.length) break
            when {
                normalized.startsWith("<-->", idx) -> {
                    tokens += LatexNode.Symbol(" ⇌ ", "<-->")
                    idx += 4
                }
                normalized.startsWith("<=>", idx) -> {
                    tokens += LatexNode.Symbol(" ⇌ ", "<=>")
                    idx += 3
                }
                normalized.startsWith("-->", idx) -> {
                    tokens += LatexNode.Symbol(" → ", "-->")
                    idx += 3
                }
                normalized.startsWith("->", idx) -> {
                    tokens += LatexNode.Symbol(" → ", "->")
                    idx += 2
                }
                normalized.startsWith("<-", idx) -> {
                    tokens += LatexNode.Symbol(" ← ", "<-")
                    idx += 2
                }
                normalized[idx] == '+' && (idx == 0 || normalized[idx - 1].isWhitespace()) &&
                    (idx + 1 >= normalized.length || normalized[idx + 1].isWhitespace()) -> {
                    tokens += LatexNode.Symbol(" + ", "+")
                    idx++
                }
                else -> {
                    // Read one chemical species until whitespace before arrow or standalone '+'
                    val start = idx
                    while (idx < normalized.length && !normalized[idx].isWhitespace() &&
                        !normalized.startsWith("<=>", idx) && !normalized.startsWith("<-->", idx) &&
                        !normalized.startsWith("-->", idx) && !normalized.startsWith("->", idx) &&
                        !normalized.startsWith("<-", idx)
                    ) {
                        idx++
                    }
                    tokens += parseChemicalSpecies(normalized.substring(start, idx))
                }
            }
        }
        return LatexNode.Chemical(formula.trim(), tokens)
    }

    private fun parseChemicalSpecies(species: String): List<LatexNode> {
        val out = mutableListOf<LatexNode>()
        var i = 0
        // Leading stoichiometric coefficient (e.g., '2' in 2H2O)
        val coeffStart = i
        while (i < species.length && species[i].isDigit()) i++
        if (i > coeffStart) {
            out += LatexNode.Text(species.substring(coeffStart, i))
        }
        while (i < species.length) {
            val c = species[i]
            // State suffix like (aq), (s), (l), (g)
            if (c == '(' && species.indexOf(')', i) > i) {
                val close = species.indexOf(')', i)
                val inside = species.substring(i + 1, close)
                if (inside in setOf("aq", "s", "l", "g")) {
                    out += LatexNode.Text("($inside)")
                    i = close + 1
                    continue
                }
            }
            val base: LatexNode = when {
                c.isUpperCase() -> {
                    val elemEnd = if (i + 1 < species.length && species[i + 1].isLowerCase()) i + 2 else i + 1
                    val elem = species.substring(i, elemEnd)
                    i = elemEnd
                    LatexNode.Text(elem)
                }
                c == '(' || c == ')' || c == '[' || c == ']' || c == '·' || c == '.' -> {
                    i++
                    LatexNode.Text(if (c == '.') "·" else c.toString())
                }
                else -> {
                    i++
                    LatexNode.Text(c.toString())
                }
            }
            // Optional explicit or implicit subscript digits
            var sub: LatexNode? = null
            if (i < species.length && species[i] == '_') {
                i++
                if (i < species.length && species[i] == '{') {
                    val close = species.indexOf('}', i + 1).takeIf { it > i } ?: species.length
                    sub = LatexNode.Text(species.substring(i + 1, close))
                    i = (close + 1).coerceAtMost(species.length)
                } else {
                    val dStart = i
                    while (i < species.length && species[i].isDigit()) i++
                    if (i > dStart) sub = LatexNode.Text(species.substring(dStart, i))
                }
            } else {
                val dStart = i
                while (i < species.length && species[i].isDigit()) i++
                if (i > dStart) sub = LatexNode.Text(species.substring(dStart, i))
            }

            // Optional charge: ^2-, ^{2-}, ^+, or trailing +/- at very end of species
            var sup: LatexNode? = null
            if (i < species.length && species[i] == '^') {
                i++
                if (i < species.length && species[i] == '{') {
                    val close = species.indexOf('}', i + 1).takeIf { it > i } ?: species.length
                    sup = LatexNode.Text(species.substring(i + 1, close).replace('-', '−'))
                    i = (close + 1).coerceAtMost(species.length)
                } else {
                    val cStart = i
                    while (i < species.length && (species[i].isDigit() || species[i] == '+' || species[i] == '-')) i++
                    if (i > cStart) sup = LatexNode.Text(species.substring(cStart, i).replace('-', '−'))
                }
            } else if (i == species.length - 1 && (species[i] == '+' || species[i] == '-')) {
                sup = LatexNode.Text(if (species[i] == '-') "−" else "+")
                i++
            }

            out += if (sub != null || sup != null) LatexNode.SubSup(base, sub, sup) else base
        }
        return out
    }

    private class ParserCursor(private val src: String) {
        var pos: Int = 0

        fun parseSequence(): LatexNode {
            val items = mutableListOf<LatexNode>()
            while (pos < src.length) {
                skipWhitespace()
                if (pos >= src.length) break
                val node = parseAtomWithScripts() ?: break
                items += node
            }
            return when (items.size) {
                0 -> LatexNode.Text("")
                1 -> items[0]
                else -> LatexNode.Group(items)
            }
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun parseAtomWithScripts(): LatexNode? {
            val atom = parseAtom() ?: return null
            if (atom is LatexNode.Aligned || atom is LatexNode.Matrix) return atom

            var sub: LatexNode? = null
            var sup: LatexNode? = null
            while (pos < src.length) {
                skipWhitespace()
                if (pos >= src.length) break
                when (src[pos]) {
                    '_' -> {
                        pos++
                        sub = readArgumentNode()
                    }
                    '^' -> {
                        pos++
                        sup = readArgumentNode()
                    }
                    '\'' -> {
                        val primes = StringBuilder()
                        while (pos < src.length && src[pos] == '\'') {
                            primes.append('′')
                            pos++
                        }
                        val primeNode = LatexNode.Text(primes.toString())
                        sup = if (sup == null) primeNode else LatexNode.Group(listOf(sup, primeNode))
                    }
                    else -> break
                }
            }

            if (atom is LatexNode.BigOp) {
                // Capture integrand / summand body up to the next relational stop symbol
                val bodyItems = mutableListOf<LatexNode>()
                while (pos < src.length) {
                    skipWhitespace()
                    if (pos >= src.length) break
                    val savePos = pos
                    val next = parseAtomWithScripts() ?: break
                    if (next is LatexNode.Symbol && next.symbol in RELATIONAL_STOP_SYMBOLS ||
                        next is LatexNode.Text && next.text in RELATIONAL_STOP_SYMBOLS
                    ) {
                        pos = savePos
                        break
                    }
                    bodyItems += next
                }
                val bodyNode = when (bodyItems.size) {
                    0 -> null
                    1 -> bodyItems[0]
                    else -> LatexNode.Group(bodyItems)
                }
                return atom.copy(lower = sub ?: atom.lower, upper = sup ?: atom.upper, body = bodyNode)
            }

            return if (sub != null || sup != null) LatexNode.SubSup(atom, sub, sup) else atom
        }

        private fun readBraceContent(): String {
            skipWhitespace()
            if (pos >= src.length) return ""
            if (src[pos] != '{') {
                val start = pos
                if (src[pos] == '\\') {
                    pos++
                    while (pos < src.length && src[pos].isLetter()) pos++
                    return src.substring(start, pos)
                }
                pos++
                return src.substring(start, pos)
            }
            pos++ // skip '{'
            val start = pos
            var depth = 1
            while (pos < src.length && depth > 0) {
                when (src[pos]) {
                    '\\' -> pos += 2
                    '{' -> { depth++; pos++ }
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val inside = src.substring(start, pos)
                            pos++
                            return inside
                        }
                        pos++
                    }
                    else -> pos++
                }
            }
            return src.substring(start, pos.coerceAtMost(src.length))
        }

        private fun readOptionalBracketContent(): String? {
            skipWhitespace()
            if (pos >= src.length || src[pos] != '[') return null
            pos++ // skip '['
            val start = pos
            var depth = 1
            while (pos < src.length && depth > 0) {
                when (src[pos]) {
                    '[' -> { depth++; pos++ }
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            val inside = src.substring(start, pos)
                            pos++
                            return inside
                        }
                        pos++
                    }
                    else -> pos++
                }
            }
            return src.substring(start, pos.coerceAtMost(src.length))
        }

        private fun readArgumentNode(): LatexNode = ParserCursor(readBraceContent()).parseSequence()

        private fun parseAtom(): LatexNode? {
            skipWhitespace()
            if (pos >= src.length) return null
            val ch = src[pos]
            if (ch == '{') {
                return ParserCursor(readBraceContent()).parseSequence()
            }
            if (ch == '}') {
                pos++
                return parseAtom()
            }
            if (ch == '\\') {
                pos++
                if (pos >= src.length) return null
                // Escaped punctuation or spacing like \, \; \{ \} \|
                if (!src[pos].isLetter()) {
                    val esc = src[pos]
                    pos++
                    return when (esc) {
                        '{', '}' -> LatexNode.Text(esc.toString())
                        '|' -> LatexNode.Symbol("‖", "\\|")
                        ',', ';', ':', ' ' -> LatexNode.Text(" ")
                        '!' -> LatexNode.Text("")
                        else -> LatexNode.Text(esc.toString())
                    }
                }
                val cmdStart = pos
                while (pos < src.length && src[pos].isLetter()) pos++
                val cmd = src.substring(cmdStart, pos)
                return handleCommand(cmd)
            }

            // Operators, digits, variables
            return when {
                ch.isDigit() -> {
                    val start = pos
                    while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
                    LatexNode.Text(src.substring(start, pos))
                }
                ch.isLetter() -> {
                    pos++
                    LatexNode.Text(ch.toString(), italicVariable = true)
                }
                ch == '-' -> {
                    pos++
                    LatexNode.Symbol("−", "-")
                }
                ch in setOf('+', '=', '<', '>', '(', ')', '[', ']', ',', ':', ';', '|', '!', '/') -> {
                    pos++
                    if (ch in setOf('+', '=', '<', '>')) LatexNode.Symbol(ch.toString(), ch.toString())
                    else LatexNode.Text(ch.toString())
                }
                else -> {
                    pos++
                    LatexNode.Text(ch.toString())
                }
            }
        }

        private fun handleCommand(cmd: String): LatexNode? {
            return when (cmd) {
                "frac", "dfrac", "tfrac" -> {
                    val num = readArgumentNode()
                    val den = readArgumentNode()
                    LatexNode.Fraction(num, den)
                }
                "sqrt" -> {
                    val deg = readOptionalBracketContent()?.takeIf { it.isNotBlank() }?.let { ParserCursor(it).parseSequence() }
                    val rad = readArgumentNode()
                    LatexNode.Sqrt(rad, deg)
                }
                "ce" -> {
                    val chemRaw = readBraceContent()
                    parseChemical(chemRaw)
                }
                "begin" -> {
                    val env = readBraceContent().trim()
                    parseEnvironment(env)
                }
                "text", "mathrm", "textbf", "textit", "operatorname" -> {
                    LatexNode.Text(readBraceContent())
                }
                "mathbf" -> {
                    LatexNode.Text(readBraceContent())
                }
                "mathbb" -> {
                    val arg = readBraceContent().trim()
                    LatexNode.Symbol(BLACKBOARD_BOLD[arg] ?: arg, "\\mathbb{$arg}")
                }
                "hat", "widehat" -> LatexNode.Accent(MathAccentKind.HAT, readArgumentNode())
                "bar", "overline" -> LatexNode.Accent(MathAccentKind.BAR, readArgumentNode())
                "vec", "overrightarrow" -> LatexNode.Accent(MathAccentKind.VEC, readArgumentNode())
                "tilde", "widetilde" -> LatexNode.Accent(MathAccentKind.TILDE, readArgumentNode())
                "dot" -> LatexNode.Accent(MathAccentKind.DOT, readArgumentNode())
                "left", "right" -> {
                    skipWhitespace()
                    if (pos < src.length) {
                        if (src[pos] == '.') {
                            pos++
                            LatexNode.Text("")
                        } else if (src[pos] == '\\') {
                            parseAtom()
                        } else {
                            val d = src[pos]
                            pos++
                            LatexNode.Text(d.toString())
                        }
                    } else LatexNode.Text("")
                }
                "quad", "qquad" -> LatexNode.Text("  ")
                in BIG_OPS -> LatexNode.BigOp(op = BIG_OPS.getValue(cmd))
                in UPRIGHT_FUNCTIONS -> LatexNode.Text(cmd, italicVariable = false)
                in SYMBOL_COMMANDS -> LatexNode.Symbol(SYMBOL_COMMANDS.getValue(cmd), "\\$cmd")
                else -> LatexNode.Text(cmd)
            }
        }

        private fun parseEnvironment(env: String): LatexNode {
            val endTag = "\\end{$env}"
            val startBody = pos
            var depth = 1
            var cursor = pos
            while (cursor < src.length && depth > 0) {
                when {
                    src.startsWith("\\begin{$env}", cursor) -> {
                        depth++
                        cursor += "\\begin{$env}".length
                    }
                    src.startsWith(endTag, cursor) -> {
                        depth--
                        if (depth == 0) break
                        cursor += endTag.length
                    }
                    else -> cursor++
                }
            }
            val body = src.substring(startBody, cursor.coerceAtMost(src.length))
            pos = (cursor + endTag.length).coerceAtMost(src.length)

            return when (env) {
                "bmatrix", "pmatrix", "vmatrix", "Bmatrix", "matrix" -> {
                    val bracket = when (env) {
                        "bmatrix" -> MatrixBracket.SQUARE
                        "pmatrix" -> MatrixBracket.PAREN
                        "vmatrix" -> MatrixBracket.PIPE
                        "Bmatrix" -> MatrixBracket.BRACE
                        else -> MatrixBracket.NONE
                    }
                    val rawRows = splitTopLevel(body.trim(), "\\\\")
                        .map { it.trim() }
                        .filterIndexed { idx, r -> r.isNotEmpty() || idx == 0 }
                    val rows = rawRows.map { rowStr ->
                        splitTopLevel(rowStr, "&").map { cell ->
                            ParserCursor(cell.trim()).parseSequence()
                        }
                    }
                    LatexNode.Matrix(rows, bracket)
                }
                "aligned", "align", "align*", "eqnarray", "eqnarray*" -> {
                    val rawRows = splitTopLevel(body.trim(), "\\\\")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    val steps = rawRows.map { rowStr ->
                        val reasonSplit = splitTopLevel(rowStr, "&&")
                        val mathPart = reasonSplit.firstOrNull().orEmpty().trim()
                        val reasonPart = reasonSplit.getOrNull(1)?.let { extractPlainReason(it) }?.takeIf { it.isNotEmpty() }
                        val ampSplit = splitTopLevel(mathPart, "&")
                        if (ampSplit.size >= 2) {
                            val lhsRaw = ampSplit[0].trim()
                            val rhsRaw = ampSplit.drop(1).joinToString(" ").trim()
                            AlignedStep(
                                lhs = lhsRaw.takeIf { it.isNotEmpty() }?.let { ParserCursor(it).parseSequence() },
                                rhs = ParserCursor(rhsRaw).parseSequence(),
                                reason = reasonPart
                            )
                        } else {
                            AlignedStep(
                                lhs = null,
                                rhs = ParserCursor(mathPart).parseSequence(),
                                reason = reasonPart
                            )
                        }
                    }
                    LatexNode.Aligned(steps)
                }
                else -> ParserCursor(body).parseSequence()
            }
        }
    }
}

@Composable
internal fun MathExpressionView(
    node: LatexNode,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    inkColor: Color = Ink
) {
    val revealedReasons = remember(node) { mutableStateMapOf<Int, Boolean>() }
    if (node is LatexNode.Aligned) {
        AlignedDerivationCard(
            aligned = node,
            fontSize = fontSize,
            inkColor = inkColor,
            revealed = revealedReasons,
            onToggleStep = { idx -> revealedReasons[idx] = !(revealedReasons[idx] ?: false) },
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.horizontalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) {
            MathNodeRender(node = node, fontSize = fontSize, inkColor = inkColor)
        }
    }
}

@Composable
private fun AlignedDerivationCard(
    aligned: LatexNode.Aligned,
    fontSize: TextUnit,
    inkColor: Color,
    revealed: Map<Int, Boolean>,
    onToggleStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LearningFixedColors.FormulaAccent
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        aligned.steps.forEachIndexed { index, step ->
            val hasReason = !step.reason.isNullOrBlank()
            val isOpen = revealed[index] == true
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceBase.copy(alpha = 0.65f))
                    .then(if (hasReason) Modifier.clickable { onToggleStep(index) } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (step.lhs != null) {
                        MathNodeRender(step.lhs, fontSize = fontSize, inkColor = inkColor)
                        Spacer(Modifier.width(4.dp))
                    }
                    MathNodeRender(step.rhs, fontSize = fontSize, inkColor = inkColor)
                    if (hasReason) {
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isOpen) accent.copy(alpha = 0.18f) else AccentWash
                        ) {
                            Text(
                                if (isOpen) "Hide why" else "Why?",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                if (hasReason) {
                    AnimatedVisibility(visible = isOpen) {
                        Text(
                            text = step.reason.orEmpty(),
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = InkSecondary,
                            modifier = Modifier.padding(start = 30.dp, top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MathNodeRender(
    node: LatexNode,
    fontSize: TextUnit,
    inkColor: Color
) {
    when (node) {
        is LatexNode.Group -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                node.children.forEach { child ->
                    MathNodeRender(child, fontSize = fontSize, inkColor = inkColor)
                }
            }
        }
        is LatexNode.Chemical -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                node.children.forEach { child ->
                    MathNodeRender(child, fontSize = fontSize, inkColor = inkColor)
                }
            }
        }
        is LatexNode.Text -> {
            if (node.text.isEmpty()) return
            Text(
                text = node.text,
                fontSize = fontSize,
                fontFamily = FontFamily.Serif,
                fontStyle = if (node.italicVariable) FontStyle.Italic else FontStyle.Normal,
                color = inkColor,
                softWrap = false
            )
        }
        is LatexNode.Symbol -> {
            val padH = if (node.symbol in setOf("+", "−", "=", "≠", "≤", "≥", "≈", "≡", "→", "⇒", "⇔", "⇌", "×", "·", "±")) 3.dp else 0.5.dp
            Text(
                text = node.symbol,
                fontSize = fontSize,
                fontFamily = FontFamily.Serif,
                color = inkColor,
                softWrap = false,
                modifier = Modifier.padding(horizontal = padH)
            )
        }
        is LatexNode.Fraction -> {
            val childSize = (fontSize.value * 0.85f).sp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Box(Modifier.padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                    MathNodeRender(node.num, fontSize = childSize, inkColor = inkColor)
                }
                Spacer(Modifier.height(1.5.dp))
                Box(Modifier.fillMaxWidth().height(1.2.dp).background(inkColor))
                Spacer(Modifier.height(1.5.dp))
                Box(Modifier.padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                    MathNodeRender(node.den, fontSize = childSize, inkColor = inkColor)
                }
            }
        }
        is LatexNode.Sqrt -> {
            val degSize = (fontSize.value * 0.62f).sp
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(IntrinsicSize.Min).padding(horizontal = 2.dp)) {
                if (node.degree != null) {
                    Box(Modifier.align(Alignment.Top).offset(x = 2.dp, y = (-1).dp)) {
                        MathNodeRender(node.degree, fontSize = degSize, inkColor = inkColor)
                    }
                }
                Canvas(Modifier.width(11.dp).fillMaxHeight()) {
                    val strokePx = 1.3.dp.toPx()
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(0f, h * 0.60f)
                        lineTo(w * 0.25f, h * 0.52f)
                        lineTo(w * 0.55f, h - strokePx)
                        lineTo(w, strokePx * 0.5f)
                    }
                    drawPath(path, color = inkColor, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Box(
                    modifier = Modifier
                        .drawBehind {
                            val strokePx = 1.2.dp.toPx()
                            drawLine(
                                color = inkColor,
                                start = Offset(0f, strokePx * 0.5f),
                                end = Offset(size.width, strokePx * 0.5f),
                                strokeWidth = strokePx
                            )
                        }
                        .padding(top = 2.5.dp, start = 1.dp, end = 2.dp)
                ) {
                    MathNodeRender(node.radicand, fontSize = fontSize, inkColor = inkColor)
                }
            }
        }
        is LatexNode.SubSup -> {
            val scriptSize = (fontSize.value * 0.72f).sp
            Row(verticalAlignment = Alignment.CenterVertically) {
                MathNodeRender(node.base, fontSize = fontSize, inkColor = inkColor)
                when {
                    node.sup != null && node.sub != null -> {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.padding(start = 1.dp)
                        ) {
                            Box(Modifier.offset(y = (-2).dp)) {
                                MathNodeRender(node.sup, fontSize = scriptSize, inkColor = inkColor)
                            }
                            Box(Modifier.offset(y = 2.dp)) {
                                MathNodeRender(node.sub, fontSize = scriptSize, inkColor = inkColor)
                            }
                        }
                    }
                    node.sup != null -> {
                        Box(Modifier.padding(start = 0.5.dp).offset(y = (-5).dp)) {
                            MathNodeRender(node.sup, fontSize = scriptSize, inkColor = inkColor)
                        }
                    }
                    node.sub != null -> {
                        Box(Modifier.padding(start = 0.5.dp).offset(y = 4.dp)) {
                            MathNodeRender(node.sub, fontSize = scriptSize, inkColor = inkColor)
                        }
                    }
                }
            }
        }
        is LatexNode.BigOp -> {
            val limitSize = (fontSize.value * 0.68f).sp
            val opSize = if (node.op == "lim") fontSize else (fontSize.value * 1.32f).sp
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (node.upper != null) {
                        MathNodeRender(node.upper, fontSize = limitSize, inkColor = inkColor)
                    }
                    Text(
                        text = node.op,
                        fontSize = opSize,
                        fontFamily = FontFamily.Serif,
                        color = inkColor,
                        softWrap = false
                    )
                    if (node.lower != null) {
                        MathNodeRender(node.lower, fontSize = limitSize, inkColor = inkColor)
                    }
                }
                if (node.body != null) {
                    Spacer(Modifier.width(3.dp))
                    MathNodeRender(node.body, fontSize = fontSize, inkColor = inkColor)
                }
            }
        }
        is LatexNode.Matrix -> {
            val cellSize = (fontSize.value * 0.90f).sp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min).padding(horizontal = 3.dp, vertical = 2.dp)
            ) {
                if (node.bracketKind != MatrixBracket.NONE) {
                    MatrixBracketEdge(kind = node.bracketKind, isLeft = true, color = inkColor)
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    node.rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { cell ->
                                Box(Modifier.widthIn(min = 18.dp), contentAlignment = Alignment.Center) {
                                    MathNodeRender(cell, fontSize = cellSize, inkColor = inkColor)
                                }
                            }
                        }
                    }
                }
                if (node.bracketKind != MatrixBracket.NONE) {
                    MatrixBracketEdge(kind = node.bracketKind, isLeft = false, color = inkColor)
                }
            }
        }
        is LatexNode.Accent -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(IntrinsicSize.Max)) {
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    val strokePx = 1.2.dp.toPx()
                    val w = size.width
                    val h = size.height
                    when (node.kind) {
                        MathAccentKind.BAR -> drawLine(inkColor, Offset(1f, h * 0.7f), Offset(w - 1f, h * 0.7f), strokePx)
                        MathAccentKind.VEC -> {
                            drawLine(inkColor, Offset(1f, h * 0.65f), Offset(w - 1f, h * 0.65f), strokePx)
                            drawLine(inkColor, Offset(w - 4.dp.toPx(), h * 0.2f), Offset(w - 1f, h * 0.65f), strokePx)
                        }
                        MathAccentKind.HAT -> {
                            val p = Path().apply {
                                moveTo(w * 0.2f, h * 0.85f)
                                lineTo(w * 0.5f, h * 0.2f)
                                lineTo(w * 0.8f, h * 0.85f)
                            }
                            drawPath(p, inkColor, style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        MathAccentKind.TILDE -> drawLine(inkColor, Offset(w * 0.15f, h * 0.6f), Offset(w * 0.85f, h * 0.5f), strokePx)
                        MathAccentKind.DOT -> drawCircle(inkColor, radius = 1.4.dp.toPx(), center = Offset(w * 0.5f, h * 0.6f))
                    }
                }
                MathNodeRender(node.base, fontSize = fontSize, inkColor = inkColor)
            }
        }
        is LatexNode.Aligned -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                node.steps.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        step.lhs?.let { MathNodeRender(it, fontSize = fontSize, inkColor = inkColor) }
                        MathNodeRender(step.rhs, fontSize = fontSize, inkColor = inkColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixBracketEdge(kind: MatrixBracket, isLeft: Boolean, color: Color) {
    Canvas(Modifier.width(6.dp).fillMaxHeight()) {
        val stroke = 1.4.dp.toPx()
        val w = size.width
        val h = size.height
        when (kind) {
            MatrixBracket.SQUARE -> {
                val xSpine = if (isLeft) stroke * 0.5f else w - stroke * 0.5f
                val xTip = if (isLeft) w else 0f
                drawLine(color, Offset(xSpine, 0f), Offset(xSpine, h), stroke)
                drawLine(color, Offset(xSpine, stroke * 0.5f), Offset(xTip, stroke * 0.5f), stroke)
                drawLine(color, Offset(xSpine, h - stroke * 0.5f), Offset(xTip, h - stroke * 0.5f), stroke)
            }
            MatrixBracket.PAREN, MatrixBracket.BRACE -> {
                val p = Path().apply {
                    if (isLeft) {
                        moveTo(w, 0f)
                        quadraticTo(0f, h * 0.5f, w, h)
                    } else {
                        moveTo(0f, 0f)
                        quadraticTo(w, h * 0.5f, 0f, h)
                    }
                }
                drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            MatrixBracket.PIPE -> {
                val x = w * 0.5f
                drawLine(color, Offset(x, 0f), Offset(x, h), stroke)
            }
            MatrixBracket.NONE -> Unit
        }
    }
}

@Composable
internal fun MathBlock(
    block: NoteBlock,
    onEdit: (String) -> Unit,
    onUpdatePayload: ((Map<String, String>) -> Unit)? = null,
    onCite: ((meetingId: String, startMs: Long) -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    var showComposer by remember { mutableStateOf(false) }
    val rawLatex = block.content.text
    val mathStepsPayload = block.payload[NoteBlock.PAYLOAD_MATH_STEPS]
    val ast = remember(rawLatex, mathStepsPayload) { LatexMathParser.parse(rawLatex, mathStepsPayload) }
    val isAligned = ast is LatexNode.Aligned
    val meetingId = block.payload[NoteBlock.PAYLOAD_MEETING_ID]
    val startMs = block.payload[NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()
    val accent = LearningFixedColors.FormulaAccent

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceSunk,
        border = BorderStroke(1.dp, LineSoft),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("math_block")
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Functions, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isAligned) "Step-by-step derivation" else "Equation",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Spacer(Modifier.weight(1f))
                if (meetingId != null && startMs != null && onCite != null) {
                    CitationJumpChip(startMs = startMs) { onCite(meetingId, startMs) }
                    Spacer(Modifier.width(6.dp))
                }
                if (rawLatex.isNotBlank()) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { clipboard.setText(AnnotatedString(rawLatex)) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy LaTeX", tint = InkSecondary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy LaTeX", fontSize = 11.5.sp, color = InkSecondary)
                    }
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showComposer = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit equation", tint = InkSecondary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Edit", fontSize = 11.5.sp, color = InkSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))
            if (rawLatex.isBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showComposer = true }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tap to compose an equation or chemical formula…", fontSize = 14.sp, color = InkMuted)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!isAligned) Modifier.clickable { showComposer = true } else Modifier)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = if (isAligned) Alignment.CenterStart else Alignment.Center
                ) {
                    MathExpressionView(node = ast, fontSize = 18.sp, inkColor = Ink)
                }
            }
        }
    }

    if (showComposer) {
        MathComposerDialog(
            initialLatex = rawLatex,
            onSave = { updated ->
                onEdit(updated)
                showComposer = false
            },
            onDismiss = { showComposer = false }
        )
    }
}

private val MATH_COMPOSER_SNIPPETS: List<Pair<String, String>> = listOf(
    "a/b" to "\\frac{a}{b}",
    "√x" to "\\sqrt{x}",
    "x²" to "x^{2}",
    "xᵢ" to "x_{i}",
    "∑" to "\\sum_{i=1}^{n} ",
    "∫" to "\\int_{0}^{\\infty} ",
    "∂" to "\\frac{\\partial f}{\\partial x}",
    "α β θ" to "\\alpha + \\beta = \\theta",
    "⇌" to "\\ce{2H2 + O2 <=> 2H2O}",
    "Aligned steps" to "\\begin{aligned}\nf'(x) &= \\lim_{h \\to 0} \\frac{f(x+h)-f(x)}{h} && \\text{Definition} \\\\\n&= 2x && \\text{Power rule}\n\\end{aligned}",
    "Matrix" to "\\begin{bmatrix} a & b \\\\ c & d \\end{bmatrix}"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MathComposerDialog(
    initialLatex: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var field by remember {
        mutableStateOf(TextFieldValue(initialLatex, TextRange(initialLatex.length)))
    }
    val previewNode = remember(field.text) { LatexMathParser.parse(field.text) }

    fun insertSnippet(snippet: String) {
        val start = field.selection.min.coerceIn(0, field.text.length)
        val end = field.selection.max.coerceIn(0, field.text.length)
        val next = field.text.substring(0, start) + snippet + field.text.substring(end)
        val caret = start + snippet.length
        field = TextFieldValue(next, TextRange(caret))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        title = { Text("Compose Math & Chemistry", color = Ink, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quick symbols", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MATH_COMPOSER_SNIPPETS.forEach { (label, snippet) ->
                        Surface(
                            onClick = { insertSnippet(snippet) },
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceSunk,
                            border = BorderStroke(1.dp, Line)
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    minLines = 3,
                    maxLines = 7,
                    label = { Text("LaTeX or \\ce{...}") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Ink),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Live preview", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceSunk,
                    border = BorderStroke(1.dp, LineSoft),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                        if (field.text.isBlank()) {
                            Text("Your equation preview appears here", fontSize = 13.sp, color = InkMuted)
                        } else {
                            MathExpressionView(node = previewNode, fontSize = 17.sp, inkColor = Ink)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(field.text.trim()) }) { Text("Save", color = Accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = InkSecondary) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. SEMANTIC ACADEMIC CALLOUT BLOCK
// ─────────────────────────────────────────────────────────────────────────────

enum class CalloutKind(val key: String, val label: String) {
    DEFINITION("DEFINITION", "Definition"),
    THEOREM("THEOREM", "Theorem"),
    FORMULA("FORMULA", "Formula"),
    EXAM_TIP("EXAM_TIP", "Exam Tip"),
    PITFALL("PITFALL", "Common Pitfall"),
    WORKED_EXAMPLE("WORKED_EXAMPLE", "Worked Example"),
    OBJECTIVE("OBJECTIVE", "Learning Objective"),
    ENRICHMENT("ENRICHMENT", "Study Enrichment");

    fun accentColor(): Color = when (this) {
        DEFINITION -> LearningFixedColors.DefinitionAccent
        THEOREM -> LearningFixedColors.TheoremAccent
        FORMULA -> LearningFixedColors.FormulaAccent
        EXAM_TIP -> LearningFixedColors.ExamTipAccent
        PITFALL -> LearningFixedColors.PitfallAccent
        WORKED_EXAMPLE -> LearningFixedColors.WorkedExampleAccent
        OBJECTIVE -> LearningFixedColors.ObjectiveAccent
        ENRICHMENT -> LearningFixedColors.EnrichmentAccent
    }

    fun icon(): ImageVector = when (this) {
        DEFINITION -> Icons.Filled.MenuBook
        THEOREM -> Icons.Filled.School
        FORMULA -> Icons.Filled.Functions
        EXAM_TIP -> Icons.Filled.Lightbulb
        PITFALL -> Icons.Filled.Warning
        WORKED_EXAMPLE -> Icons.Filled.CheckCircle
        OBJECTIVE -> Icons.Rounded.Flag
        ENRICHMENT -> AiMark
    }

    companion object {
        fun fromKey(raw: String?): CalloutKind =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: DEFINITION
    }
}

@Composable
internal fun CalloutBlock(
    block: NoteBlock,
    onText: (String) -> Unit,
    onUpdatePayload: (Map<String, String>) -> Unit,
    onCite: ((meetingId: String, startMs: Long) -> Unit)? = null
) {
    val kind = CalloutKind.fromKey(block.payload[NoteBlock.PAYLOAD_CALLOUT_KIND])
    val accent = kind.accentColor()
    val savedTitle = block.payload[NoteBlock.PAYLOAD_CALLOUT_TITLE].orEmpty()
    var titleValue by remember(block.id) { mutableStateOf(savedTitle) }
    if (titleValue != savedTitle && savedTitle.isNotEmpty() && titleValue.isEmpty()) titleValue = savedTitle

    var bodyValue by remember(block.id) { mutableStateOf(block.content.text) }
    if (bodyValue != block.content.text && block.content.text.isNotEmpty() && bodyValue.isEmpty()) bodyValue = block.content.text

    var kindMenuOpen by remember { mutableStateOf(false) }
    // Worked examples with existing content let students attempt the problem before revealing steps.
    var solutionRevealed by remember(block.id, kind) {
        mutableStateOf(kind != CalloutKind.WORKED_EXAMPLE || block.content.text.isBlank())
    }

    val meetingId = block.payload[NoteBlock.PAYLOAD_MEETING_ID]
    val startMs = block.payload[NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = if (MM.colors.isDark) 0.13f else 0.08f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .testTag("callout_block_${kind.key.lowercase()}")
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f).padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box {
                    Surface(
                        onClick = { kindMenuOpen = true },
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(kind.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(kind.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
                            Icon(Icons.Filled.ExpandMore, contentDescription = "Change callout kind", tint = accent, modifier = Modifier.size(14.dp))
                        }
                    }
                    DropdownMenu(expanded = kindMenuOpen, onDismissRequest = { kindMenuOpen = false }, containerColor = SurfaceBase) {
                        CalloutKind.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label, color = Ink) },
                                leadingIcon = { Icon(option.icon(), contentDescription = null, tint = option.accentColor(), modifier = Modifier.size(17.dp)) },
                                onClick = {
                                    kindMenuOpen = false
                                    onUpdatePayload(mapOf(NoteBlock.PAYLOAD_CALLOUT_KIND to option.key))
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (meetingId != null && startMs != null && onCite != null) {
                    CitationJumpChip(startMs = startMs) { onCite(meetingId, startMs) }
                }
            }

            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth()) {
                if (titleValue.isEmpty()) {
                    Text("${kind.label} title…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkFaint)
                }
                BasicTextField(
                    value = titleValue,
                    onValueChange = { next ->
                        val clean = next.replace("\n", " ")
                        titleValue = clean
                        onUpdatePayload(mapOf(NoteBlock.PAYLOAD_CALLOUT_TITLE to clean))
                    },
                    textStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = Ink),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(5.dp))
            if (kind == CalloutKind.WORKED_EXAMPLE && !solutionRevealed && bodyValue.isNotBlank()) {
                val lines = bodyValue.lines()
                val promptLine = lines.firstOrNull()?.takeIf { lines.size > 1 }
                if (!promptLine.isNullOrBlank()) {
                    Text(promptLine, fontSize = 14.5.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.padding(bottom = 8.dp))
                }
                Surface(
                    onClick = { solutionRevealed = true },
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Try it first · Tap to reveal solution",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth()) {
                    if (bodyValue.isEmpty()) {
                        Text(
                            if (kind == CalloutKind.WORKED_EXAMPLE) "Problem statement on the first line, then step-by-step solution…"
                            else "Write ${kind.label.lowercase()} details…",
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = InkFaint
                        )
                    }
                    BasicTextField(
                        value = bodyValue,
                        onValueChange = { next ->
                            bodyValue = next
                            onText(next)
                        },
                        textStyle = TextStyle(fontSize = 14.5.sp, lineHeight = 22.sp, color = Ink),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (kind == CalloutKind.WORKED_EXAMPLE && bodyValue.isNotBlank()) {
                    Text(
                        "Hide solution",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { solutionRevealed = false }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CitationJumpChip(startMs: Long, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = AccentWash
    ) {
        Text(
            "[${Formatters.formatDurationHms(startMs)}]",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = Accent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. MERMAID DIAGRAM PARSER & PURE-COMPOSE DIAGRAM CANVAS
// ─────────────────────────────────────────────────────────────────────────────

enum class DiagramDirection { TD, LR }

enum class DiagramNodeShape { RECT, ROUNDED, DIAMOND, STADIUM, TERMINAL }

data class DiagramNode(
    val id: String,
    val label: String,
    val shape: DiagramNodeShape,
    val rank: Int = 0,
    val orderInRank: Int = 0
)

data class DiagramEdge(
    val fromId: String,
    val toId: String,
    val label: String? = null,
    val dashed: Boolean = false
)

data class ParsedDiagram(
    val direction: DiagramDirection,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>
)

internal const val DEFAULT_MERMAID_DIAGRAM = """flowchart TD
    A[Concept Input] --> B{Condition?}
    B -->|Yes| C(Primary Outcome)
    B -->|No| D(Alternative Path)"""

object MermaidDiagramParser {

    private val HEADER_FLOW = Regex("""^(?:flowchart|graph)\s+(TD|TB|LR|RL|BT)\b""", RegexOption.IGNORE_CASE)
    private val HEADER_STATE = Regex("""^stateDiagram(?:-v2)?\b""", RegexOption.IGNORE_CASE)
    private val DIRECTION_DIRECTIVE = Regex("""^direction\s+(TD|TB|LR|RL)\b""", RegexOption.IGNORE_CASE)
    private val INLINE_LABEL_ARROW = Regex("""--\s+([^-][^>]*?)\s+-->""")
    private val INLINE_DOTTED_ARROW = Regex("""-\.\s+([^.]*?)\s+\.->""")
    private val ARROW_TOKEN = Regex("""(-->|-.->|==>|--o|--x)(?:\|([^|]*)\|)?""")
    private val STATE_DESCRIPTION = Regex("""^([A-Za-z0-9_]+)\s*:\s*(.+)$""")

    fun parse(source: String): ParsedDiagram {
        val rawLines = source.replace("\r\n", "\n").lines()
        var direction = DiagramDirection.TD
        var isStateDiagram = false
        val nodeMap = linkedMapOf<String, DiagramNode>()
        val edges = mutableListOf<DiagramEdge>()

        fun upsertNode(spec: String, isTargetOfArrow: Boolean = false): String {
            val trimmed = spec.trim()
            if (trimmed == "[*]") {
                val id = if (isTargetOfArrow) "__end__" else "__start__"
                val label = if (isTargetOfArrow) "End" else "Start"
                if (id !in nodeMap) nodeMap[id] = DiagramNode(id, label, DiagramNodeShape.TERMINAL)
                return id
            }
            val parsed = parseNodeToken(trimmed)
            val existing = nodeMap[parsed.id]
            if (existing == null) {
                nodeMap[parsed.id] = DiagramNode(parsed.id, parsed.label, parsed.shape)
            } else if (parsed.hasExplicitShapeOrLabel) {
                nodeMap[parsed.id] = existing.copy(label = parsed.label, shape = parsed.shape)
            }
            return parsed.id
        }

        val statements = rawLines.flatMap { line ->
            val withoutComment = line.substringBefore("%%").trim()
            if (withoutComment.isEmpty()) emptyList()
            else withoutComment.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        }

        for (stmt in statements) {
            HEADER_FLOW.find(stmt)?.let { m ->
                val dir = m.groupValues[1].uppercase()
                direction = if (dir == "LR" || dir == "RL") DiagramDirection.LR else DiagramDirection.TD
                continue
            }
            if (HEADER_STATE.containsMatchIn(stmt)) {
                isStateDiagram = true
                direction = DiagramDirection.TD
                continue
            }
            DIRECTION_DIRECTIVE.find(stmt)?.let { m ->
                val dir = m.groupValues[1].uppercase()
                direction = if (dir == "LR" || dir == "RL") DiagramDirection.LR else DiagramDirection.TD
                continue
            }
            if (stmt.startsWith("classDef ", ignoreCase = true) ||
                stmt.startsWith("class ", ignoreCase = true) ||
                stmt.startsWith("style ", ignoreCase = true) ||
                stmt.startsWith("subgraph ", ignoreCase = true) ||
                stmt.equals("end", ignoreCase = true)
            ) {
                continue
            }

            // Normalize `-- label -->` and `-. label .->` into pipe-label arrows
            var normalized = stmt
                .replace(INLINE_LABEL_ARROW) { "-->|${it.groupValues[1].trim()}|" }
                .replace(INLINE_DOTTED_ARROW) { "-.->|${it.groupValues[1].trim()}|" }

            // Handle stateDiagram trailing `: label` on an edge line (e.g., `Idle --> Active : start`)
            var trailingStateEdgeLabel: String? = null
            if (ARROW_TOKEN.containsMatchIn(normalized) && ':' in normalized) {
                val lastArrowEnd = ARROW_TOKEN.findAll(normalized).last().range.last
                val colonIdx = normalized.indexOf(':', lastArrowEnd + 1)
                if (colonIdx > lastArrowEnd) {
                    trailingStateEdgeLabel = normalized.substring(colonIdx + 1).trim()
                    normalized = normalized.substring(0, colonIdx).trim()
                }
            }

            val matches = findArrowsOutsideBrackets(normalized)
            if (matches.isNotEmpty()) {
                val segments = mutableListOf<String>()
                var cursor = 0
                for (m in matches) {
                    segments += normalized.substring(cursor, m.start).trim()
                    cursor = m.end
                }
                segments += normalized.substring(cursor).trim()

                for (idx in matches.indices) {
                    val leftSpec = segments[idx]
                    val rightSpec = segments[idx + 1]
                    if (leftSpec.isEmpty() || rightSpec.isEmpty()) continue
                    val fromId = upsertNode(leftSpec, isTargetOfArrow = false)
                    val toId = upsertNode(rightSpec, isTargetOfArrow = true)
                    val arrow = matches[idx]
                    val label = arrow.label?.takeIf { it.isNotBlank() }
                        ?: trailingStateEdgeLabel?.takeIf { idx == matches.lastIndex && it.isNotBlank() }
                    edges += DiagramEdge(
                        fromId = fromId,
                        toId = toId,
                        label = label,
                        dashed = arrow.operator == "-.->"
                    )
                }
            } else if (isStateDiagram && STATE_DESCRIPTION.matches(normalized)) {
                val m = STATE_DESCRIPTION.matchEntire(normalized)!!
                val id = m.groupValues[1]
                val desc = m.groupValues[2].trim()
                nodeMap[id] = DiagramNode(id, "$id: $desc", DiagramNodeShape.ROUNDED)
            } else {
                // Standalone node declaration like `A[Load Dataset]`
                val token = parseNodeToken(normalized)
                if (token.id.isNotBlank() && token.id.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                    upsertNode(normalized)
                }
            }
        }

        val rankedNodes = assignRanksAndOrders(nodeMap.values.toList(), edges)
        return ParsedDiagram(direction = direction, nodes = rankedNodes, edges = edges)
    }

    private data class ArrowHit(val start: Int, val end: Int, val operator: String, val label: String?)

    private fun findArrowsOutsideBrackets(line: String): List<ArrowHit> {
        val blocked = BooleanArray(line.length)
        var depth = 0
        for (i in line.indices) {
            val c = line[i]
            if ((c == '[' || c == '(' || c == '{') && !line.startsWith("[*]", i)) depth++
            if (depth > 0) blocked[i] = true
            if ((c == ']' || c == ')' || c == '}') && (i < 2 || line.substring(i - 2, i + 1) != "[*]")) {
                depth = max(0, depth - 1)
            }
        }
        return ARROW_TOKEN.findAll(line)
            .filter { m -> (m.range.first..m.range.last).none { blocked[it] } }
            .map { m -> ArrowHit(m.range.first, m.range.last + 1, m.groupValues[1], m.groupValues[2].takeIf { it.isNotEmpty() }) }
            .toList()
    }

    private data class NodeToken(
        val id: String,
        val label: String,
        val shape: DiagramNodeShape,
        val hasExplicitShapeOrLabel: Boolean
    )

    private fun cleanLabel(raw: String): String =
        raw.trim().removeSurrounding("\"").removeSurrounding("'").replace("<br/>", " ").replace("<br>", " ").trim()

    private fun parseNodeToken(raw: String): NodeToken {
        val s = raw.trim()
        val idEnd = s.indexOfFirst { it == '[' || it == '(' || it == '{' }
        if (idEnd <= 0) {
            return NodeToken(s, cleanLabel(s), DiagramNodeShape.ROUNDED, false)
        }
        val id = s.substring(0, idEnd).trim()
        val rest = s.substring(idEnd).trim()
        return when {
            rest.startsWith("([") && rest.endsWith("])") ->
                NodeToken(id, cleanLabel(rest.substring(2, rest.length - 2)), DiagramNodeShape.STADIUM, true)
            rest.startsWith("((") && rest.endsWith("))") ->
                NodeToken(id, cleanLabel(rest.substring(2, rest.length - 2)), DiagramNodeShape.TERMINAL, true)
            rest.startsWith("[[") && rest.endsWith("]]") ->
                NodeToken(id, cleanLabel(rest.substring(2, rest.length - 2)), DiagramNodeShape.RECT, true)
            rest.startsWith("[") && rest.endsWith("]") ->
                NodeToken(id, cleanLabel(rest.substring(1, rest.length - 1)), DiagramNodeShape.RECT, true)
            rest.startsWith("{{") && rest.endsWith("}}") ->
                NodeToken(id, cleanLabel(rest.substring(2, rest.length - 2)), DiagramNodeShape.DIAMOND, true)
            rest.startsWith("{") && rest.endsWith("}") ->
                NodeToken(id, cleanLabel(rest.substring(1, rest.length - 1)), DiagramNodeShape.DIAMOND, true)
            rest.startsWith("(") && rest.endsWith(")") ->
                NodeToken(id, cleanLabel(rest.substring(1, rest.length - 1)), DiagramNodeShape.ROUNDED, true)
            else -> NodeToken(id, cleanLabel(id), DiagramNodeShape.ROUNDED, false)
        }
    }

    private fun assignRanksAndOrders(nodes: List<DiagramNode>, edges: List<DiagramEdge>): List<DiagramNode> {
        if (nodes.isEmpty()) return emptyList()
        val ids = nodes.map { it.id }.toSet()
        val outgoing = mutableMapOf<String, MutableList<String>>()
        val inDegree = mutableMapOf<String, Int>().apply { nodes.forEach { put(it.id, 0) } }
        for (edge in edges) {
            if (edge.fromId in ids && edge.toId in ids && edge.fromId != edge.toId) {
                outgoing.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
                inDegree[edge.toId] = (inDegree[edge.toId] ?: 0) + 1
            }
        }

        val ranks = mutableMapOf<String, Int>()
        val roots = nodes.filter { (inDegree[it.id] ?: 0) == 0 }.map { it.id }
            .ifEmpty { listOf(nodes.first().id) }

        fun dfs(u: String, currentRank: Int, path: Set<String>) {
            val prev = ranks[u]
            if (prev != null && prev >= currentRank) return
            ranks[u] = currentRank
            for (v in outgoing[u].orEmpty()) {
                if (v !in path) {
                    dfs(v, currentRank + 1, path + v)
                }
            }
        }
        roots.forEach { root -> dfs(root, 0, setOf(root)) }
        nodes.forEach { n ->
            if (n.id !in ranks) dfs(n.id, 0, setOf(n.id))
        }

        val byRank = nodes.groupBy { ranks[it.id] ?: 0 }.toSortedMap()
        val positioned = mutableListOf<DiagramNode>()
        for ((r, group) in byRank) {
            group.forEachIndexed { idx, node ->
                positioned += node.copy(rank = r, orderInRank = idx)
            }
        }
        return positioned
    }
}

@Composable
internal fun DiagramBlock(
    block: NoteBlock,
    onEdit: (String) -> Unit
) {
    val syntax = remember(block.payload[NoteBlock.PAYLOAD_DIAGRAM_SYNTAX], block.content.text) {
        block.payload[NoteBlock.PAYLOAD_DIAGRAM_SYNTAX]?.takeIf { it.isNotBlank() } ?: block.content.text
    }
    val parsed = remember(syntax) { MermaidDiagramParser.parse(syntax) }
    var editing by remember { mutableStateOf(false) }
    val accent = LearningFixedColors.SapphireMid

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceSunk,
        border = BorderStroke(1.dp, LineSoft),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("diagram_block")
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AccountTree, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Concept Diagram · ${if (parsed.direction == DiagramDirection.LR) "Left → Right" else "Top → Down"}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { editing = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Mermaid diagram", tint = InkSecondary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit source", fontSize = 11.5.sp, color = InkSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (parsed.nodes.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { editing = true }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tap to write a Mermaid flowchart or state diagram…", fontSize = 13.5.sp, color = InkMuted)
                }
            } else {
                DiagramCanvas(
                    diagram = parsed,
                    modifier = Modifier.fillMaxWidth().clickable { editing = true }
                )
            }
        }
    }

    if (editing) {
        DiagramEditorDialog(
            initialSyntax = syntax.ifBlank { DEFAULT_MERMAID_DIAGRAM },
            onSave = { updated ->
                onEdit(updated)
                editing = false
            },
            onDismiss = { editing = false }
        )
    }
}

@Composable
internal fun DiagramCanvas(
    diagram: ParsedDiagram,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val colors = MM.colors
    val nodeSurface = SurfaceBase
    val nodeBorder = LearningFixedColors.SapphireMid
    val diamondAccent = LearningFixedColors.AmberHighlight
    val terminalAccent = LearningFixedColors.TealAccent
    val edgeColor = InkSecondary
    val inkColor = Ink
    val badgeBg = SurfaceSunk

    val maxRank = (diagram.nodes.maxOfOrNull { it.rank } ?: 0) + 1
    val maxBreadth = (diagram.nodes.groupBy { it.rank }.maxOfOrNull { it.value.size } ?: 1).coerceAtLeast(1)

    val nodeW = 142.dp
    val nodeH = 46.dp
    val rankGap = 54.dp
    val siblingGap = 24.dp
    val pad = 16.dp

    val canvasWidth = if (diagram.direction == DiagramDirection.TD) {
        pad * 2 + nodeW * maxBreadth + siblingGap * (maxBreadth - 1)
    } else {
        pad * 2 + nodeW * maxRank + rankGap * (maxRank - 1)
    }
    val canvasHeight = if (diagram.direction == DiagramDirection.TD) {
        pad * 2 + nodeH * maxRank + rankGap * (maxRank - 1)
    } else {
        pad * 2 + nodeH * maxBreadth + siblingGap * (maxBreadth - 1)
    }

    val labelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = inkColor, textAlign = TextAlign.Center)
    val edgeLabelStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = inkColor)

    Box(modifier = modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(canvasWidth.coerceAtLeast(240.dp)).height(canvasHeight.coerceAtLeast(96.dp))) {
            val wPx = with(density) { nodeW.toPx() }
            val hPx = with(density) { nodeH.toPx() }
            val rankGapPx = with(density) { rankGap.toPx() }
            val sibGapPx = with(density) { siblingGap.toPx() }
            val padPx = with(density) { pad.toPx() }

            val grouped = diagram.nodes.groupBy { it.rank }
            val centers = mutableMapOf<String, Offset>()
            for ((rank, rankNodes) in grouped) {
                val count = rankNodes.size
                rankNodes.sortedBy { it.orderInRank }.forEachIndexed { idx, node ->
                    if (diagram.direction == DiagramDirection.TD) {
                        val totalRowW = count * wPx + (count - 1) * sibGapPx
                        val startX = ((size.width - totalRowW) * 0.5f).coerceAtLeast(padPx)
                        val cx = startX + idx * (wPx + sibGapPx) + wPx * 0.5f
                        val cy = padPx + rank * (hPx + rankGapPx) + hPx * 0.5f
                        centers[node.id] = Offset(cx, cy)
                    } else {
                        val totalColH = count * hPx + (count - 1) * sibGapPx
                        val startY = ((size.height - totalColH) * 0.5f).coerceAtLeast(padPx)
                        val cx = padPx + rank * (wPx + rankGapPx) + wPx * 0.5f
                        val cy = startY + idx * (hPx + sibGapPx) + hPx * 0.5f
                        centers[node.id] = Offset(cx, cy)
                    }
                }
            }

            // Draw directed edges first (behind nodes)
            val strokePx = 1.6.dp.toPx()
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
            for (edge in diagram.edges) {
                val fromCenter = centers[edge.fromId] ?: continue
                val toCenter = centers[edge.toId] ?: continue
                val start: Offset
                val end: Offset
                if (diagram.direction == DiagramDirection.TD) {
                    val goingDown = toCenter.y >= fromCenter.y
                    start = Offset(fromCenter.x, fromCenter.y + if (goingDown) hPx * 0.5f else -hPx * 0.5f)
                    end = Offset(toCenter.x, toCenter.y + if (goingDown) -hPx * 0.5f else hPx * 0.5f)
                } else {
                    val goingRight = toCenter.x >= fromCenter.x
                    start = Offset(fromCenter.x + if (goingRight) wPx * 0.5f else -wPx * 0.5f, fromCenter.y)
                    end = Offset(toCenter.x + if (goingRight) -wPx * 0.5f else wPx * 0.5f, toCenter.y)
                }

                val path = Path().apply {
                    moveTo(start.x, start.y)
                    if (diagram.direction == DiagramDirection.TD) {
                        val midY = (start.y + end.y) * 0.5f
                        cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                    } else {
                        val midX = (start.x + end.x) * 0.5f
                        cubicTo(midX, start.y, midX, end.y, end.x, end.y)
                    }
                }
                drawPath(
                    path = path,
                    color = edgeColor,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        pathEffect = if (edge.dashed) dashEffect else null
                    )
                )
                drawArrowHead(end = end, from = start, direction = diagram.direction, color = edgeColor)

                edge.label?.takeIf { it.isNotBlank() }?.let { lbl ->
                    val mid = Offset((start.x + end.x) * 0.5f, (start.y + end.y) * 0.5f)
                    val measured = measurer.measure(
                        text = AnnotatedString(lbl),
                        style = edgeLabelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = (wPx * 0.9f).toInt())
                    )
                    val pillW = measured.size.width + 12.dp.toPx()
                    val pillH = measured.size.height + 6.dp.toPx()
                    drawRoundRect(
                        color = badgeBg,
                        topLeft = Offset(mid.x - pillW * 0.5f, mid.y - pillH * 0.5f),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(pillH * 0.5f, pillH * 0.5f)
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(mid.x - measured.size.width * 0.5f, mid.y - measured.size.height * 0.5f)
                    )
                }
            }

            // Draw nodes
            for (node in diagram.nodes) {
                val c = centers[node.id] ?: continue
                val topLeft = Offset(c.x - wPx * 0.5f, c.y - hPx * 0.5f)
                val borderCol = when (node.shape) {
                    DiagramNodeShape.DIAMOND -> diamondAccent
                    DiagramNodeShape.TERMINAL, DiagramNodeShape.STADIUM -> terminalAccent
                    else -> if (colors.isDark) nodeBorder.copy(alpha = 0.85f) else nodeBorder
                }
                when (node.shape) {
                    DiagramNodeShape.DIAMOND -> {
                        val diamond = Path().apply {
                            moveTo(c.x, c.y - hPx * 0.56f)
                            lineTo(c.x + wPx * 0.54f, c.y)
                            lineTo(c.x, c.y + hPx * 0.56f)
                            lineTo(c.x - wPx * 0.54f, c.y)
                            close()
                        }
                        drawPath(diamond, color = nodeSurface)
                        drawPath(diamond, color = borderCol, style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
                    }
                    DiagramNodeShape.STADIUM, DiagramNodeShape.TERMINAL -> {
                        drawRoundRect(
                            color = nodeSurface,
                            topLeft = topLeft,
                            size = Size(wPx, hPx),
                            cornerRadius = CornerRadius(hPx * 0.5f, hPx * 0.5f)
                        )
                        drawRoundRect(
                            color = borderCol,
                            topLeft = topLeft,
                            size = Size(wPx, hPx),
                            cornerRadius = CornerRadius(hPx * 0.5f, hPx * 0.5f),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                    DiagramNodeShape.RECT -> {
                        val r = 4.dp.toPx()
                        drawRoundRect(nodeSurface, topLeft, Size(wPx, hPx), CornerRadius(r, r))
                        drawRoundRect(borderCol, topLeft, Size(wPx, hPx), CornerRadius(r, r), Stroke(1.5.dp.toPx()))
                    }
                    DiagramNodeShape.ROUNDED -> {
                        val r = 12.dp.toPx()
                        drawRoundRect(nodeSurface, topLeft, Size(wPx, hPx), CornerRadius(r, r))
                        drawRoundRect(borderCol, topLeft, Size(wPx, hPx), CornerRadius(r, r), Stroke(1.5.dp.toPx()))
                    }
                }

                val maxLabelW = (if (node.shape == DiagramNodeShape.DIAMOND) wPx * 0.68f else wPx - 16.dp.toPx()).toInt().coerceAtLeast(32)
                val measured = measurer.measure(
                    text = AnnotatedString(node.label),
                    style = labelStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = maxLabelW)
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(c.x - measured.size.width * 0.5f, c.y - measured.size.height * 0.5f)
                )
            }
        }
    }
}

private fun DrawScope.drawArrowHead(end: Offset, from: Offset, direction: DiagramDirection, color: Color) {
    val tangentOrigin = if (direction == DiagramDirection.TD) {
        Offset(end.x, (from.y + end.y) * 0.5f)
    } else {
        Offset((from.x + end.x) * 0.5f, end.y)
    }
    val angle = atan2(end.y - tangentOrigin.y, end.x - tangentOrigin.x)
    val headLen = 7.dp.toPx()
    val spread = 0.48f
    val p1 = Offset(end.x - headLen * cos(angle - spread), end.y - headLen * sin(angle - spread))
    val p2 = Offset(end.x - headLen * cos(angle + spread), end.y - headLen * sin(angle + spread))
    val head = Path().apply {
        moveTo(end.x, end.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(head, color = color)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagramEditorDialog(
    initialSyntax: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var source by remember { mutableStateOf(initialSyntax) }
    val preview = remember(source) { MermaidDiagramParser.parse(source) }
    val templates = listOf(
        "Flowchart TD" to DEFAULT_MERMAID_DIAGRAM,
        "Flowchart LR" to "flowchart LR\n    A[Input] --> B(Transform) --> C([Output])",
        "State diagram" to "stateDiagram-v2\n    [*] --> Idle\n    Idle --> Running : start\n    Running --> [*] : complete"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        title = { Text("Edit Concept Diagram", color = Ink, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    templates.forEach { (label, tpl) ->
                        Surface(
                            onClick = { source = tpl },
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceSunk,
                            border = BorderStroke(1.dp, Line)
                        ) {
                            Text(label, fontSize = 12.sp, color = Ink, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("Mermaid syntax") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = Ink),
                    modifier = Modifier.fillMaxWidth()
                )
                if (preview.nodes.isNotEmpty()) {
                    Text("Preview (${preview.nodes.size} nodes · ${preview.edges.size} edges)", fontSize = 11.5.sp, color = InkMuted)
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceSunk, border = BorderStroke(1.dp, LineSoft)) {
                        Box(Modifier.padding(8.dp)) {
                            DiagramCanvas(diagram = preview)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(source.trim()) }, enabled = source.isNotBlank()) {
                Text("Save", color = Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = InkSecondary) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. ENRICHED FOLDABLE CONTAINER (FOUNDER MANDATE: FOLDED BY DEFAULT)
// ─────────────────────────────────────────────────────────────────────────────

fun NoteBlock.isEnrichedUnpacked(): Boolean =
    payload[NoteBlock.PAYLOAD_UNPACKED] == "1"

fun NoteBlock.isEnrichedFoldedByDefault(): Boolean =
    source == BlockSource.ENRICHED && !isEnrichedUnpacked()

fun NoteBlock.withToggledUnpacked(): NoteBlock {
    val nextPayload = if (isEnrichedUnpacked()) {
        payload - NoteBlock.PAYLOAD_UNPACKED
    } else {
        payload + (NoteBlock.PAYLOAD_UNPACKED to "1")
    }
    return copy(payload = nextPayload)
}

@Composable
internal fun EnrichedFoldableContainer(
    block: NoteBlock,
    onToggleUnpacked: (blockId: String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (block.source != BlockSource.ENRICHED) {
        content()
        return
    }

    val companionForm = rememberCompanionSettings().value.form
    val enrichLabel = block.payload[NoteBlock.PAYLOAD_ENRICH_LABEL]?.takeIf { it.isNotBlank() }
        ?: "Step-by-step enrichment"
    val unpacked = block.isEnrichedUnpacked()
    val accent = LearningFixedColors.EnrichmentAccent

    if (!unpacked) {
        Surface(
            onClick = { onToggleUnpacked(block.id) },
            shape = RoundedCornerShape(50),
            color = accent.copy(alpha = if (MM.colors.isDark) 0.16f else 0.10f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
            modifier = modifier
                .padding(vertical = 5.dp)
                .testTag("enriched_unpack_pill_${block.id}")
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (companionForm != null) {
                    Companion(form = companionForm, state = CompanionState.IDLE, size = 18.dp)
                } else {
                    Icon(AiMark, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Unpack: $enrichLabel · Tap to expand",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = if (MM.colors.isDark) 0.11f else 0.06f))
                .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
                .testTag("enriched_unpacked_${block.id}")
        ) {
            Box(Modifier.width(3.5.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (companionForm != null) {
                        Companion(form = companionForm, state = CompanionState.IDLE, size = 18.dp)
                    } else {
                        Icon(AiMark, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = enrichLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = { onToggleUnpacked(block.id) },
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ExpandLess, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Fold", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. LESSON NOTE FILTER BAR (ALL · LECTURE ONLY · EXAM HIGH-YIELD)
// ─────────────────────────────────────────────────────────────────────────────

enum class LessonViewFilter(val label: String) {
    ALL("All notes"),
    LECTURE_ONLY("Lecture only"),
    EXAM_HIGH_YIELD("Exam high-yield");

    fun apply(blocks: List<NoteBlock>): List<NoteBlock> = when (this) {
        ALL -> blocks
        LECTURE_ONLY -> blocks.filter { it.source != BlockSource.ENRICHED }
        EXAM_HIGH_YIELD -> blocks.filter { block -> isExamHighYieldBlock(block) }
    }

    companion object {
        private val HIGH_YIELD_TYPES = setOf(
            NoteBlockType.HEADING_1,
            NoteBlockType.HEADING_2,
            NoteBlockType.HEADING_3,
            NoteBlockType.MATH,
            NoteBlockType.CALLOUT,
            NoteBlockType.TABLE,
            NoteBlockType.CODE,
            NoteBlockType.DIAGRAM,
            NoteBlockType.CHECKLIST
        )

        fun isExamHighYieldBlock(block: NoteBlock): Boolean {
            if (block.type in HIGH_YIELD_TYPES) return true
            if (block.payload["examHighYield"] == "1" || block.payload["marked"] == "1") return true
            return block.content.spans.any { it.style == InlineStyle.HIGHLIGHT }
        }
    }
}

internal fun shouldShowLessonFilterBar(
    workflow: RecordingType?,
    notebookSpace: NotebookSpace?,
    blocks: List<NoteBlock>
): Boolean {
    val isAcademicContext = workflow == RecordingType.LECTURE ||
        workflow == RecordingType.RESEARCH ||
        notebookSpace == NotebookSpace.LEARNING
    if (!isAcademicContext) return false
    return blocks.any { b ->
        b.source == BlockSource.ENRICHED ||
            b.source == BlockSource.AI ||
            b.type == NoteBlockType.MATH ||
            b.type == NoteBlockType.CALLOUT ||
            b.type == NoteBlockType.DIAGRAM ||
            b.type == NoteBlockType.CODE ||
            b.type == NoteBlockType.TABLE
    }
}

@Composable
internal fun LessonNoteFilterBar(
    selected: LessonViewFilter,
    onSelect: (LessonViewFilter) -> Unit,
    enrichedCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 16.dp, bottom = 10.dp)
            .testTag("lesson_note_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LessonViewFilter.entries.forEach { filter ->
            val active = selected == filter
            val labelText = if (filter == LessonViewFilter.ALL && enrichedCount > 0) {
                "${filter.label} · $enrichedCount"
            } else {
                filter.label
            }
            Surface(
                onClick = { onSelect(filter) },
                shape = RoundedCornerShape(50),
                color = if (active) Ink else SurfaceSunk,
                border = if (active) null else BorderStroke(1.dp, Line)
            ) {
                Text(
                    text = labelText,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) OnInk else InkSecondary,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                )
            }
        }
    }
}
