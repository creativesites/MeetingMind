package com.craftflowtechnologies.meetingmind.ai.voice.read.smart

import com.craftflowtechnologies.meetingmind.core.model.LearningConcept

/**
 * Deterministic, zero-network natural-speech shaper for academic and technical notes.
 *
 * Converts LaTeX formulas, chemical notation, Big-O complexity, accounting journal shorthand,
 * IFRS/GAAP financial ratios, fenced code blocks, and Markdown pipe tables into clear spoken
 * phrasing so Read-to-Me and Audio Cram never read raw backslashes, pipes, or backticks aloud.
 */
object AcademicSpokenForm {

    private val fencedCodeRegex = Regex("```([a-zA-Z0-9+#_-]*)\\s*\\n([\\s\\S]*?)```")
    private val inlineCodeRegex = Regex("`([^`\\n]+)`")
    private val displayMathDollarRegex = Regex("\\$\\$([\\s\\S]+?)\\$\\$")
    private val inlineMathDollarRegex = Regex("(?<!\\$)\\$([^$\\n]+?)\\$(?!\\$)")
    private val displayMathBracketRegex = Regex("\\\\\\[([\\s\\S]+?)\\\\\\]")
    private val inlineMathParenRegex = Regex("\\\\\\(([\\s\\S]+?)\\\\\\)")

    private val ceRegex = Regex("\\\\ce\\{([^{}]+)\\}")
    private val textCmdRegex = Regex("\\\\(?:text|mathrm|mathbf|mathit|mathsf|operatorname)\\{([^{}]*)\\}")
    private val sqrtIndexedRegex = Regex("\\\\sqrt\\[([^\\[\\]]+)\\]\\{([^{}]+)\\}")
    private val sqrtRegex = Regex("\\\\sqrt\\{([^{}]+)\\}")
    private val fracRegex = Regex("\\\\(?:d|t)?frac\\{([^{}]+)\\}\\{([^{}]+)\\}")

    private val sumBoundsRegex = Regex("\\\\sum_\\{([^{}]+)\\}\\^\\{([^{}]+)\\}")
    private val sumLowerOnlyRegex = Regex("\\\\sum_\\{([^{}]+)\\}")
    private val prodBoundsRegex = Regex("\\\\prod_\\{([^{}]+)\\}\\^\\{([^{}]+)\\}")
    private val intBoundsRegex = Regex("\\\\int_\\{([^{}]+)\\}\\^\\{([^{}]+)\\}")
    private val intSimpleBoundsRegex = Regex("\\\\int_([A-Za-z0-9]+)\\^([A-Za-z0-9]+)")
    private val limRegex = Regex("\\\\lim_\\{([^{}]+)\\}")

    private val bigORegex = Regex("(?:\\\\mathcal\\{O\\}|\\bO)\\(([^()]+)\\)")
    private val bigThetaRegex = Regex("(?:\\\\Theta|Θ)\\(([^()]+)\\)")
    private val bigOmegaRegex = Regex("(?:\\\\Omega|Ω)\\(([^()]+)\\)")

    private val powerBracedRegex = Regex("([A-Za-z0-9)\\]}]+)\\^\\{([^{}]+)\\}")
    private val powerSimpleRegex = Regex("([A-Za-z0-9)\\]}]+)\\^([A-Za-z0-9]+)")
    private val subBracedRegex = Regex("([A-Za-z0-9)\\]}]+)_\\{([^{}]+)\\}")
    private val subSimpleRegex = Regex("\\b([A-Za-z])_([A-Za-z0-9])\\b")

    private val tableSeparatorLineRegex = Regex("^\\|?[\\s:-]+(\\|[\\s:-]+)+\\|?$")

    private val languageNames = mapOf(
        "py" to "Python",
        "python" to "Python",
        "kt" to "Kotlin",
        "kotlin" to "Kotlin",
        "java" to "Java",
        "js" to "JavaScript",
        "javascript" to "JavaScript",
        "ts" to "TypeScript",
        "typescript" to "TypeScript",
        "sql" to "SQL",
        "c" to "C",
        "cpp" to "C plus plus",
        "c++" to "C plus plus",
        "cs" to "C sharp",
        "csharp" to "C sharp",
        "go" to "Go",
        "rust" to "Rust",
        "r" to "R",
        "bash" to "Bash",
        "sh" to "Shell"
    )

    private val greekAndMathSymbols: List<Pair<Regex, String>> = listOf(
        Regex("\\\\alpha(?![A-Za-z])") to " alpha ",
        Regex("\\\\beta(?![A-Za-z])") to " beta ",
        Regex("\\\\gamma(?![A-Za-z])") to " gamma ",
        Regex("\\\\delta(?![A-Za-z])") to " delta ",
        Regex("\\\\(?:varepsilon|epsilon)(?![A-Za-z])") to " epsilon ",
        Regex("\\\\zeta(?![A-Za-z])") to " zeta ",
        Regex("\\\\eta(?![A-Za-z])") to " eta ",
        Regex("\\\\(?:vartheta|theta)(?![A-Za-z])") to " theta ",
        Regex("\\\\iota(?![A-Za-z])") to " iota ",
        Regex("\\\\kappa(?![A-Za-z])") to " kappa ",
        Regex("\\\\lambda(?![A-Za-z])") to " lambda ",
        Regex("\\\\mu(?![A-Za-z])") to " mu ",
        Regex("\\\\nu(?![A-Za-z])") to " nu ",
        Regex("\\\\xi(?![A-Za-z])") to " xi ",
        Regex("\\\\pi(?![A-Za-z])") to " pi ",
        Regex("\\\\rho(?![A-Za-z])") to " rho ",
        Regex("\\\\sigma(?![A-Za-z])") to " sigma ",
        Regex("\\\\tau(?![A-Za-z])") to " tau ",
        Regex("\\\\upsilon(?![A-Za-z])") to " upsilon ",
        Regex("\\\\(?:varphi|phi)(?![A-Za-z])") to " phi ",
        Regex("\\\\chi(?![A-Za-z])") to " chi ",
        Regex("\\\\psi(?![A-Za-z])") to " psi ",
        Regex("\\\\omega(?![A-Za-z])") to " omega ",
        Regex("\\\\Gamma(?![A-Za-z])") to " Gamma ",
        Regex("\\\\Delta(?![A-Za-z])") to " Delta ",
        Regex("\\\\Theta(?![A-Za-z])") to " Theta ",
        Regex("\\\\Lambda(?![A-Za-z])") to " Lambda ",
        Regex("\\\\Pi(?![A-Za-z])") to " Pi ",
        Regex("\\\\Sigma(?![A-Za-z])") to " Sigma ",
        Regex("\\\\Phi(?![A-Za-z])") to " Phi ",
        Regex("\\\\Psi(?![A-Za-z])") to " Psi ",
        Regex("\\\\Omega(?![A-Za-z])") to " Omega ",
        Regex("\\\\partial(?![A-Za-z])") to " partial ",
        Regex("\\\\nabla(?![A-Za-z])") to " del ",
        Regex("\\\\infty(?![A-Za-z])") to " infinity ",
        Regex("\\\\pm(?![A-Za-z])|±") to " plus or minus ",
        Regex("\\\\mp(?![A-Za-z])") to " minus or plus ",
        Regex("\\\\(?:leq|le)(?![A-Za-z])|≤") to " is less than or equal to ",
        Regex("\\\\(?:geq|ge)(?![A-Za-z])|≥") to " is greater than or equal to ",
        Regex("\\\\(?:neq|ne)(?![A-Za-z])|≠") to " is not equal to ",
        Regex("\\\\approx(?![A-Za-z])|≈") to " is approximately ",
        Regex("\\\\equiv(?![A-Za-z])|≡") to " is equivalent to ",
        Regex("\\\\(?:iff|Leftrightarrow)(?![A-Za-z])|⇔") to " if and only if ",
        Regex("\\\\Rightarrow(?![A-Za-z])|⇒") to " implies ",
        Regex("\\\\rightarrow(?![A-Za-z])") to " yields ",
        Regex("\\\\to(?![A-Za-z])|→") to " approaches ",
        Regex("\\\\times(?![A-Za-z])") to " times ",
        Regex("\\\\cdot(?![A-Za-z])") to " times ",
        Regex("\\\\div(?![A-Za-z])") to " divided by ",
        Regex("\\\\notin(?![A-Za-z])|∉") to " not in ",
        Regex("\\\\in(?![A-Za-z])|∈") to " in ",
        Regex("\\\\subseteq(?![A-Za-z])|⊆") to " is a subset of or equal to ",
        Regex("\\\\subset(?![A-Za-z])|⊂") to " is a subset of ",
        Regex("\\\\cup(?![A-Za-z])|∪") to " union ",
        Regex("\\\\cap(?![A-Za-z])|∩") to " intersection ",
        Regex("\\\\forall(?![A-Za-z])|∀") to " for all ",
        Regex("\\\\exists(?![A-Za-z])|∃") to " there exists ",
        Regex("\\\\sum(?![A-Za-z_])") to " the sum of ",
        Regex("\\\\int(?![A-Za-z_])") to " the integral of ",
        Regex("\\\\log(?![A-Za-z])") to " log ",
        Regex("\\\\ln(?![A-Za-z])") to " natural log of ",
        Regex("\\\\sin(?![A-Za-z])") to " sine ",
        Regex("\\\\cos(?![A-Za-z])") to " cosine ",
        Regex("\\\\tan(?![A-Za-z])") to " tangent ",
        Regex("α") to " alpha ",
        Regex("β") to " beta ",
        Regex("γ") to " gamma ",
        Regex("δ") to " delta ",
        Regex("θ") to " theta ",
        Regex("λ") to " lambda ",
        Regex("μ") to " mu ",
        Regex("π") to " pi ",
        Regex("σ") to " sigma ",
        Regex("ω") to " omega ",
        Regex("Δ") to " Delta ",
        Regex("Σ") to " Sigma ",
        Regex("∞") to " infinity "
    )

    private val accountingAndRatioRules: List<Pair<Regex, String>> = listOf(
        // Journal entry shorthand: "Dr Cash" / "Cr Revenue" (never "Dr. Smith" with a period)
        Regex("(?<!\\.)\\bDr(?!\\.)\\s+(?=[A-Z0-9$£€R])") to "Debit ",
        Regex("(?<!\\.)\\bCr(?!\\.)\\s+(?=[A-Z0-9$£€R])") to "Credit ",
        Regex("\\bP/E\\s+ratio\\b", RegexOption.IGNORE_CASE) to "price to earnings ratio",
        Regex("\\bP/E\\b") to "price to earnings",
        Regex("\\bD/E\\s+ratio\\b", RegexOption.IGNORE_CASE) to "debt to equity ratio",
        Regex("\\bD/E\\b") to "debt to equity",
        Regex("\\bA/R\\b") to "accounts receivable",
        Regex("\\bA/P\\b") to "accounts payable",
        Regex("\\bCOGS\\b") to "cost of goods sold",
        Regex("\\bROE\\b") to "return on equity",
        Regex("\\bROA\\b") to "return on assets",
        Regex("\\bROCE\\b") to "return on capital employed",
        Regex("\\bWACC\\b") to "weighted average cost of capital",
        Regex("\\bNPV\\b") to "net present value",
        Regex("\\bIRR\\b") to "internal rate of return",
        Regex("\\bEPS\\b") to "earnings per share",
        Regex("\\bIFRS\\b") to "I F R S",
        Regex("\\bGAAP\\b") to "G A A P",
        Regex("\\bEBITDA\\b") to "E B I T D A"
    )

    /** Quick detector for whether [text] contains academic, formula, code, table, or accounting notation. */
    fun containsAcademicNotation(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.contains("```") || text.contains('\\') || text.contains('^') || text.contains("`")) return true
        if (text.contains('|') && text.lines().count { it.contains('|') } >= 2) return true
        if (displayMathDollarRegex.containsMatchIn(text) || inlineMathDollarRegex.containsMatchIn(text)) return true
        if (bigORegex.containsMatchIn(text) || bigThetaRegex.containsMatchIn(text) || bigOmegaRegex.containsMatchIn(text)) return true
        if (subBracedRegex.containsMatchIn(text) || subSimpleRegex.containsMatchIn(text)) return true
        if (accountingAndRatioRules.any { it.first.containsMatchIn(text) }) return true
        if (text.any { it in "±≤≥≠≈≡⇒⇔→∈∉⊂⊆∪∩∀∃αβγδθλμπσωΔΣΩ∞" }) return true
        return false
    }

    /** Deterministically converts academic notation in [text] into natural spoken English. */
    fun apply(text: String): String {
        if (text.isBlank()) return text.trim()

        // 1. Extract fenced code blocks first so pipes/underscores inside code aren't treated as tables/LaTeX.
        val codePlaceholders = mutableListOf<String>()
        var working = fencedCodeRegex.replace(text) { match ->
            val spoken = narrateCodeBlock(match.groupValues[1], match.groupValues[2])
            val idx = codePlaceholders.size
            codePlaceholders += spoken
            " @@ACADEMIC_CODE_$idx@@ "
        }

        // 2. Narrate Markdown pipe tables cleanly.
        working = narratePipeTables(working)

        // 3. Shape inline code spans (`...`).
        working = inlineCodeRegex.replace(working) { match ->
            narrateInlineCode(match.groupValues[1])
        }

        // 4. Unwrap explicit math blocks ($...$, $$...$$, \[...\], \(...\)) with full equation shaping.
        working = displayMathDollarRegex.replace(working) { " ${shapeMathExpression(it.groupValues[1], inMathMode = true)} " }
        working = displayMathBracketRegex.replace(working) { " ${shapeMathExpression(it.groupValues[1], inMathMode = true)} " }
        working = inlineMathParenRegex.replace(working) { " ${shapeMathExpression(it.groupValues[1], inMathMode = true)} " }
        working = inlineMathDollarRegex.replace(working) { " ${shapeMathExpression(it.groupValues[1], inMathMode = true)} " }

        // 5. Shape bare LaTeX, chemistry, Big-O, subscripts/superscripts, and accounting shorthand in prose.
        working = shapeInlineAcademic(working)

        // 6. Restore narrated code blocks.
        codePlaceholders.forEachIndexed { idx, narrated ->
            working = working.replace("@@ACADEMIC_CODE_$idx@@", narrated)
        }

        return working
            .replace(Regex("[ \\t]{2,}"), " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    /**
     * Builds a structured, ear-friendly Audio Cram script for pre-exam or commute listening.
     * Every concept, formula, and exam hint is shaped through [apply] and [SpokenForm.apply].
     */
    fun buildAudioCramScript(
        courseTitle: String,
        sessionTitle: String,
        concepts: List<LearningConcept>,
        examHints: List<String>
    ): String {
        val cleanCourse = courseTitle.trim()
        val cleanSession = sessionTitle.trim().ifBlank { "this lecture" }
        val activeConcepts = concepts.filter { !it.isDismissed }
        val cleanHints = examHints.map { it.trim() }.filter { it.isNotEmpty() }

        return buildString {
            if (cleanCourse.isNotEmpty()) {
                append("Audio cram for ")
                append(SpokenForm.apply(apply(cleanCourse)))
                append(": ")
                append(SpokenForm.apply(apply(cleanSession)))
                appendLine(".")
            } else {
                append("Audio cram for ")
                append(SpokenForm.apply(apply(cleanSession)))
                appendLine(".")
            }

            if (activeConcepts.isEmpty() && cleanHints.isEmpty()) {
                append("No extracted concepts or exam hints are stored for this session yet. Review your lecture notes to add key concepts.")
                return@buildString
            }

            if (activeConcepts.isNotEmpty()) {
                appendLine("Here are the core concepts you need to know.")
                activeConcepts.forEachIndexed { index, concept ->
                    val name = SpokenForm.apply(apply(concept.name)).trimEnd('.')
                    val def = SpokenForm.apply(apply(concept.definition)).trimEnd('.')
                    append("Concept ${index + 1}, $name: $def. ")
                    concept.emphasis?.trim()?.takeIf { it.isNotEmpty() }?.let { emp ->
                        val spokenEmp = SpokenForm.apply(apply(emp)).trimEnd('.')
                        append("Lecturer emphasis: $spokenEmp. ")
                    }
                    if (concept.relationships.isNotEmpty()) {
                        val rels = concept.relationships.joinToString(", ") { SpokenForm.apply(apply(it)).trimEnd('.') }
                        append("Connected to: $rels. ")
                    }
                    appendLine()
                }
            }

            if (cleanHints.isNotEmpty()) {
                appendLine("Pay special attention to these exam hints from your lecture.")
                cleanHints.forEachIndexed { index, hint ->
                    val spokenHint = SpokenForm.apply(apply(hint)).trimEnd('.')
                    appendLine("Exam alert ${index + 1}: $spokenHint.")
                }
            }

            val anchorName = activeConcepts.firstOrNull()?.let { SpokenForm.apply(apply(it.name)).trimEnd('.') }
            if (anchorName != null) {
                append("Quick self-check before you finish: pause and explain $anchorName in your own words.")
            } else {
                append("Quick self-check before you finish: pause and summarize the top exam alert in your own words.")
            }
        }.replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    private fun shapeInlineAcademic(input: String): String {
        var s = input

        // Chemistry \ce{...}
        s = ceRegex.replace(s) { " ${shapeChemistry(it.groupValues[1])} " }

        // Big-O, Theta, Omega complexity before general exponent rules
        s = bigORegex.replace(s) { "big O of ${shapeComplexityInner(it.groupValues[1])}" }
        s = bigThetaRegex.replace(s) { "big Theta of ${shapeComplexityInner(it.groupValues[1])}" }
        s = bigOmegaRegex.replace(s) { "big Omega of ${shapeComplexityInner(it.groupValues[1])}" }

        // Accounting & IFRS/GAAP ratios
        for ((re, replacement) in accountingAndRatioRules) {
            s = re.replace(s, replacement)
        }

        // Math constructs appearing directly in text
        val hasLatexOrMath = s.contains('\\') || s.contains('^') ||
            subBracedRegex.containsMatchIn(s) || subSimpleRegex.containsMatchIn(s) ||
            s.any { it in "±≤≥≠≈≡⇒⇔→∈∉⊂⊆∪∩∀∃αβγδθλμπσωΔΣΩ∞" }
        if (hasLatexOrMath) {
            s = shapeMathExpression(s, inMathMode = false)
        }

        return s
    }

    private fun shapeMathExpression(expr: String, inMathMode: Boolean): String {
        var s = expr

        // Chemistry inside math mode
        s = ceRegex.replace(s) { " ${shapeChemistry(it.groupValues[1])} " }

        // Unwrap \text{...}, \mathrm{...}, etc. repeatedly from innermost outward
        repeat(3) {
            val next = textCmdRegex.replace(s) { " ${it.groupValues[1]} " }
            if (next == s) return@repeat
            s = next
        }

        // Big-O inside math mode
        s = bigORegex.replace(s) { "big O of ${shapeComplexityInner(it.groupValues[1])}" }
        s = bigThetaRegex.replace(s) { "big Theta of ${shapeComplexityInner(it.groupValues[1])}" }
        s = bigOmegaRegex.replace(s) { "big Omega of ${shapeComplexityInner(it.groupValues[1])}" }

        // Sums, products, integrals, limits with bounds (before general _ and ^ rules!)
        s = sumBoundsRegex.replace(s) { m ->
            val lower = shapeBound(m.groupValues[1])
            val upper = shapeBound(m.groupValues[2])
            " the sum from $lower to $upper of "
        }
        s = sumLowerOnlyRegex.replace(s) { m ->
            " the sum over ${shapeBound(m.groupValues[1])} of "
        }
        s = prodBoundsRegex.replace(s) { m ->
            val lower = shapeBound(m.groupValues[1])
            val upper = shapeBound(m.groupValues[2])
            " the product from $lower to $upper of "
        }
        s = intBoundsRegex.replace(s) { m ->
            val lower = shapeBound(m.groupValues[1])
            val upper = shapeBound(m.groupValues[2])
            " the integral from $lower to $upper of "
        }
        s = intSimpleBoundsRegex.replace(s) { m ->
            " the integral from ${m.groupValues[1]} to ${m.groupValues[2]} of "
        }
        s = limRegex.replace(s) { m ->
            val cond = m.groupValues[1]
                .replace("\\rightarrow", " approaches ")
                .replace("\\to", " approaches ")
                .replace("->", " approaches ")
                .let { shapeBound(it) }
            " the limit as $cond of "
        }

        // Fractions and roots from innermost braces outward
        repeat(5) {
            val prev = s
            s = sqrtIndexedRegex.replace(s) { m ->
                val deg = m.groupValues[1].trim()
                val body = shapeBound(m.groupValues[2])
                val rootWord = when (deg) {
                    "2" -> "square root"
                    "3" -> "cube root"
                    "4" -> "fourth root"
                    else -> "$deg root"
                }
                " the $rootWord of $body "
            }
            s = sqrtRegex.replace(s) { m ->
                " the square root of ${shapeBound(m.groupValues[1])} "
            }
            s = fracRegex.replace(s) { m ->
                val num = shapeBound(m.groupValues[1])
                val den = shapeBound(m.groupValues[2])
                " $num over $den "
            }
            if (s == prev) return@repeat
        }

        // Subscripts (before powers so x_{i}^{2} -> "x sub i squared")
        s = subBracedRegex.replace(s) { m ->
            "${m.groupValues[1]} sub ${shapeBound(m.groupValues[2])}"
        }
        s = subSimpleRegex.replace(s) { m ->
            "${m.groupValues[1]} sub ${m.groupValues[2]}"
        }

        // Superscripts / exponents
        s = powerBracedRegex.replace(s) { m ->
            formatExponent(m.groupValues[1], m.groupValues[2])
        }
        s = powerSimpleRegex.replace(s) { m ->
            formatExponent(m.groupValues[1], m.groupValues[2])
        }

        // Greek letters and math operators
        for ((re, replacement) in greekAndMathSymbols) {
            s = re.replace(s, replacement)
        }

        if (inMathMode) {
            s = s.replace("=", " equals ")
            s = s.replace("+", " plus ")
            // Leading unary minus vs binary minus
            s = Regex("(?<=^|[=(])\\s*-\\s*(?=[A-Za-z0-9])").replace(s, " negative ")
            s = Regex("(?<=[A-Za-z0-9)\\]}])\\s*-\\s*(?=[A-Za-z0-9(\\[\\\\])").replace(s, " minus ")
        } else {
            // Even outside $...$, convert obvious algebraic `a = b` equations while avoiding URLs
            s = Regex("(?<=\\b[A-Za-z0-9)\\]}])\\s*=\\s*(?=[A-Za-z0-9(\\\\+-])").replace(s, " equals ")
        }

        // Clean leftover LaTeX spacing commands and braces
        s = s.replace(Regex("\\\\[,;:!]|\\\\quad|\\\\qquad|\\\\left|\\\\right"), " ")
        if (inMathMode) {
            s = s.replace("{", " ").replace("}", " ")
        }
        return s.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun shapeBound(raw: String): String {
        var b = raw.trim()
        b = fracRegex.replace(b) { "${it.groupValues[1].trim()} over ${it.groupValues[2].trim()}" }
        b = sqrtRegex.replace(b) { "the square root of ${it.groupValues[1].trim()}" }
        b = subBracedRegex.replace(b) { "${it.groupValues[1]} sub ${it.groupValues[2].trim()}" }
        b = subSimpleRegex.replace(b) { "${it.groupValues[1]} sub ${it.groupValues[2]}" }
        b = powerBracedRegex.replace(b) { formatExponent(it.groupValues[1], it.groupValues[2]) }
        b = powerSimpleRegex.replace(b) { formatExponent(it.groupValues[1], it.groupValues[2]) }
        for ((re, replacement) in greekAndMathSymbols) {
            b = re.replace(b, replacement)
        }
        b = b.replace("=", " equals ")
        b = b.replace("+", " plus ")
        b = Regex("(?<=^|[=(])\\s*-\\s*(?=[A-Za-z0-9])").replace(b, " negative ")
        b = Regex("(?<=[A-Za-z0-9)])\\s*-\\s*(?=[A-Za-z0-9(])").replace(b, " minus ")
        return b.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun formatExponent(base: String, rawExp: String): String {
        val exp = shapeBound(rawExp)
        return when (exp) {
            "2" -> "$base squared"
            "3" -> "$base cubed"
            "T", "\\top" -> "$base transpose"
            "-1", "negative 1" -> "$base inverse"
            else -> "$base to the power of $exp"
        }
    }

    private fun shapeComplexityInner(inner: String): String {
        var s = inner.trim()
        s = s.replace("\\log", "log")
        s = powerBracedRegex.replace(s) { formatExponent(it.groupValues[1], it.groupValues[2]) }
        s = powerSimpleRegex.replace(s) { formatExponent(it.groupValues[1], it.groupValues[2]) }
        s = s.replace("+", " plus ")
        s = Regex("(?<=[A-Za-z0-9)])\\s*-\\s*(?=[A-Za-z0-9(])").replace(s, " minus ")
        s = s.replace("*", " times ")
        for ((re, replacement) in greekAndMathSymbols) {
            s = re.replace(s, replacement)
        }
        return s.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun shapeChemistry(formula: String): String {
        var s = formula.trim()
        s = s.replace("<=>", " is in equilibrium with ")
        s = s.replace("\\rightleftharpoons", " is in equilibrium with ")
        s = s.replace("->", " yields ")
        s = s.replace("\\rightarrow", " yields ")
        // Ionic charges like SO4^2- or Fe^3+ or OH^-
        s = Regex("\\^\\{?(\\d*)([+-])\\}?").replace(s) { m ->
            val num = m.groupValues[1]
            val sign = if (m.groupValues[2] == "+") "plus" else "minus"
            if (num.isEmpty()) " $sign " else " $num $sign "
        }
        s = Regex("(?<=\\s)\\+(?=\\s)").replace(s, " plus ")
        // Tokenize chemical compounds into element symbols & stoichiometric digits (e.g., H2O -> H 2 O)
        val elementOrNum = Regex("[A-Z][a-z]?|\\d+")
        s = Regex("\\b[A-Z][A-Za-z0-9]*\\d[A-Za-z0-9]*\\b|\\b[A-Z]{2,}[a-z]?\\b").replace(s) { compound ->
            elementOrNum.findAll(compound.value).joinToString(" ") { it.value }
        }
        return s.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun narrateCodeBlock(langTag: String, body: String): String {
        val cleanTag = langTag.trim().lowercase()
        val langName = languageNames[cleanTag] ?: cleanTag.replaceFirstChar { it.uppercase() }
        val intro = if (langName.isNotEmpty()) "Code example in $langName." else "Code example."
        val spokenLines = body.lines().mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "{" || trimmed == "}" || trimmed == "};") return@mapNotNull null
            val withoutCommentPrefix = trimmed.removePrefix("//").removePrefix("#").trim()
            if (withoutCommentPrefix.isEmpty()) return@mapNotNull null
            var line = narrateInlineCode(withoutCommentPrefix)
                .replace(Regex("[{};]+$"), "")
                .trim()
            if (line.isEmpty()) return@mapNotNull null
            if (line.last() !in ".!?:") line += "."
            line
        }
        return if (spokenLines.isEmpty()) intro else "$intro ${spokenLines.joinToString(" ")}"
    }

    private fun narrateInlineCode(code: String): String {
        return code.trim()
            .replace("===", " strictly equals ")
            .replace("!==", " is strictly not equal to ")
            .replace("<=", " is less than or equal to ")
            .replace(">=", " is greater than or equal to ")
            .replace("!=", " is not equal to ")
            .replace("==", " equals ")
            .replace("->", " maps to ")
            .replace("=>", " maps to ")
            .replace("&&", " and ")
            .replace("||", " or ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun narratePipeTables(text: String): String {
        val lines = text.lines()
        if (lines.size < 2) return text
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            if (isPipeRow(lines[i]) && i + 1 < lines.size && isPipeRow(lines[i + 1])) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && isPipeRow(lines[i])) {
                    tableLines += lines[i]
                    i++
                }
                out += formatPipeTable(tableLines)
            } else {
                out += lines[i]
                i++
            }
        }
        return out.joinToString("\n")
    }

    private fun isPipeRow(line: String): Boolean {
        val t = line.trim()
        return t.count { it == '|' } >= 2 || (t.startsWith("|") && t.endsWith("|") && t.length > 2)
    }

    private fun parsePipeCells(line: String): List<String> {
        return line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split('|')
            .map { cell ->
                val t = cell.trim()
                when (t) {
                    "Dr" -> "Debit"
                    "Cr" -> "Credit"
                    else -> shapeInlineAcademic(t).trim()
                }
            }
    }

    private fun formatPipeTable(tableLines: List<String>): String {
        val contentRows = tableLines.filterNot { tableSeparatorLineRegex.matches(it.trim()) }
        if (contentRows.isEmpty()) return ""
        val headers = parsePipeCells(contentRows.first())
        val dataRows = contentRows.drop(1).map { parsePipeCells(it) }
        val nonEmptyHeaders = headers.filter { it.isNotBlank() }

        return buildString {
            if (nonEmptyHeaders.isNotEmpty()) {
                append("Table with columns ")
                append(nonEmptyHeaders.joinToString(", "))
                append(". ")
            }
            dataRows.forEachIndexed { idx, cells ->
                val pairs = cells.mapIndexedNotNull { colIdx, value ->
                    val cleanVal = value.trim()
                    if (cleanVal.isEmpty() || cleanVal == "-" || cleanVal == "—" || cleanVal == "–") return@mapIndexedNotNull null
                    val colName = headers.getOrNull(colIdx)?.takeIf { it.isNotBlank() }
                    if (colName != null) "$colName $cleanVal" else cleanVal
                }
                if (pairs.isNotEmpty()) {
                    append("Row ${idx + 1}: ")
                    append(pairs.joinToString(", "))
                    append(". ")
                }
            }
        }.trim()
    }
}
