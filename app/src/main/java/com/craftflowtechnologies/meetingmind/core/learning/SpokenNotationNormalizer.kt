package com.craftflowtechnologies.meetingmind.core.learning

/**
 * Deterministic, fast post-ASR and note text normalizer that upgrades spoken technical
 * notation (mathematics, engineering, programming Big-O, chemistry, accounting) into clean
 * symbolic or inline LaTeX notation while preserving surrounding prose readability.
 */
object SpokenNotationNormalizer {

    private val SUBSCRIPT_DIGITS = charArrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')

    private val BIG_O_RULES: List<Pair<Regex, String>> = listOf(
        Regex("\\bbig\\s+o\\s+of\\s+n\\s+log\\s+n\\b", RegexOption.IGNORE_CASE) to "O(n log n)",
        Regex("\\bbig\\s+o\\s+of\\s+n\\s+squared\\b", RegexOption.IGNORE_CASE) to "O(n^2)",
        Regex("\\bbig\\s+o\\s+of\\s+n\\s+cubed\\b", RegexOption.IGNORE_CASE) to "O(n^3)",
        Regex("\\bbig\\s+o\\s+of\\s+(?:2|two)\\s+to\\s+the\\s+(?:power\\s+of\\s+)?n\\b", RegexOption.IGNORE_CASE) to "O(2^n)",
        Regex("\\bbig\\s+o\\s+of\\s+log\\s+n\\b", RegexOption.IGNORE_CASE) to "O(log n)",
        Regex("\\bbig\\s+o\\s+of\\s+(?:1|one)\\b", RegexOption.IGNORE_CASE) to "O(1)",
        Regex("\\bbig\\s+o\\s+of\\s+v\\s+plus\\s+e\\b", RegexOption.IGNORE_CASE) to "O(V + E)",
        Regex("\\bbig\\s+o\\s+of\\s+m\\s+plus\\s+n\\b", RegexOption.IGNORE_CASE) to "O(m + n)",
        Regex("\\bbig\\s+o\\s+of\\s+n\\s+factorial\\b", RegexOption.IGNORE_CASE) to "O(n!)",
        Regex("\\bbig\\s+o\\s+of\\s+n\\b", RegexOption.IGNORE_CASE) to "O(n)"
    )

    private val COMMON_CHEM_SPOKEN: List<Pair<Regex, String>> = listOf(
        Regex("\\bh\\s+two\\s+s\\s+o\\s+four\\b", RegexOption.IGNORE_CASE) to "H₂SO₄",
        Regex("\\bh\\s+two\\s+o\\b", RegexOption.IGNORE_CASE) to "H₂O",
        Regex("\\bc\\s+o\\s+two\\b", RegexOption.IGNORE_CASE) to "CO₂",
        Regex("\\bn\\s+h\\s+three\\b", RegexOption.IGNORE_CASE) to "NH₃",
        Regex("\\bc\\s+h\\s+four\\b", RegexOption.IGNORE_CASE) to "CH₄",
        Regex("\\bn\\s+a\\s+c\\s+l\\b", RegexOption.IGNORE_CASE) to "NaCl",
        Regex("\\bH2SO4\\b") to "H₂SO₄",
        Regex("\\bC6H12O6\\b") to "C₆H₁₂O₆",
        Regex("\\bH2O\\b") to "H₂O",
        Regex("\\bCO2\\b") to "CO₂",
        Regex("\\bNH3\\b") to "NH₃",
        Regex("\\bCH4\\b") to "CH₄"
    )

    private val CHEM_DOMAIN_SPOKEN: List<Pair<Regex, String>> = listOf(
        Regex("\\bo\\s+two\\b", RegexOption.IGNORE_CASE) to "O₂",
        Regex("\\bh\\s+two\\b", RegexOption.IGNORE_CASE) to "H₂",
        Regex("\\bn\\s+two\\b", RegexOption.IGNORE_CASE) to "N₂",
        Regex("\\bc\\s+l\\s+two\\b", RegexOption.IGNORE_CASE) to "Cl₂"
    )

    private val CHEM_FORMULA_TOKEN = Regex("\\b(?:[A-Z][a-z]?\\d*){2,}\\b|\\b(?:H|O|N|F|Cl|Br|I)\\d+\\b")
    private val CHEM_EXEMPT_TOKENS = setOf("SN1", "SN2", "E1", "E2", "B12", "G1", "G2", "S1", "S2")

    private val RELATIONAL_RULES: List<Pair<Regex, String>> = listOf(
        Regex("\\bgreater\\s+than\\s+or\\s+equal\\s+to\\b", RegexOption.IGNORE_CASE) to "≥",
        Regex("\\bless\\s+than\\s+or\\s+equal\\s+to\\b", RegexOption.IGNORE_CASE) to "≤",
        Regex("\\bapproximately\\s+equal\\s+to\\b", RegexOption.IGNORE_CASE) to "≈",
        Regex("\\bnot\\s+equal\\s+to\\b", RegexOption.IGNORE_CASE) to "≠",
        Regex("\\bplus\\s+or\\s+minus\\b", RegexOption.IGNORE_CASE) to "±"
    )

    private val GREEK_RULES: List<Pair<Regex, String>> = listOf(
        Regex("\\bcapital\\s+delta\\b", RegexOption.IGNORE_CASE) to "Δ",
        Regex("\\balpha\\b", RegexOption.IGNORE_CASE) to "α",
        Regex("\\bbeta\\b", RegexOption.IGNORE_CASE) to "β",
        Regex("\\bgamma\\b", RegexOption.IGNORE_CASE) to "γ",
        Regex("\\btheta\\b", RegexOption.IGNORE_CASE) to "θ",
        Regex("\\blambda\\b", RegexOption.IGNORE_CASE) to "λ",
        Regex("\\bsigma\\b", RegexOption.IGNORE_CASE) to "σ",
        Regex("\\bomega\\b", RegexOption.IGNORE_CASE) to "ω",
        Regex("\\bepsilon\\b", RegexOption.IGNORE_CASE) to "ε",
        Regex("\\bmu\\b", RegexOption.IGNORE_CASE) to "μ"
    )

    private val ACCOUNTING_PROSE_FOLLOWERS = setOf(
        "card", "cards", "balance", "balances", "risk", "risks", "score", "scores",
        "limit", "limits", "terms", "note", "notes", "side", "sides", "memo", "memos",
        "and", "or", "to", "of", "is", "are", "was", "were", "the", "a", "an", "that", "which"
    )

    private val ACCOUNTING_ENTRY_REGEX = Regex(
        "\\b(debit|credit)\\s+([A-Za-z][A-Za-z0-9/&'-]*)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Upgrades spoken notation in [text] according to [domain] while preserving surrounding prose.
     */
    fun normalize(text: String, domain: SubjectDomain = SubjectDomain.GENERAL): String {
        if (text.isBlank()) return text
        var out = text

        // 1. Programming Big-O notation (unambiguous across all technical contexts)
        for ((pattern, replacement) in BIG_O_RULES) {
            out = pattern.replace(out, replacement)
        }

        // 2. Chemistry formulas and reaction arrows
        for ((pattern, replacement) in COMMON_CHEM_SPOKEN) {
            out = pattern.replace(out, replacement)
        }
        out = Regex("\\breversible\\s+reaction\\b", RegexOption.IGNORE_CASE).replace(out, "⇌")
        out = Regex("\\bis\\s+in\\s+equilibrium\\s+with\\b", RegexOption.IGNORE_CASE).replace(out, "⇌")
        out = Regex("\\breacts\\s+to\\s+form\\b", RegexOption.IGNORE_CASE).replace(out, "→")

        if (domain == SubjectDomain.CHEMISTRY) {
            for ((pattern, replacement) in CHEM_DOMAIN_SPOKEN) {
                out = pattern.replace(out, replacement)
            }
            out = Regex("\\byields\\b", RegexOption.IGNORE_CASE).replace(out, "→")
            out = CHEM_FORMULA_TOKEN.replace(out) { match ->
                val token = match.value
                if (token in CHEM_EXEMPT_TOKENS || !token.any { it.isDigit() }) {
                    token
                } else {
                    toSubscriptFormula(token)
                }
            }
        }

        // 3. Accounting Dr / Cr normalization (strictly scoped to ACCOUNTING_FINANCE)
        if (domain == SubjectDomain.ACCOUNTING_FINANCE) {
            out = ACCOUNTING_ENTRY_REGEX.replace(out) { match ->
                val verb = match.groupValues[1]
                val nextWord = match.groupValues[2]
                if (nextWord.lowercase() in ACCOUNTING_PROSE_FOLLOWERS) {
                    match.value
                } else {
                    val abbrev = if (verb.equals("debit", ignoreCase = true)) "Dr" else "Cr"
                    val formattedAccount = nextWord.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                    }
                    "$abbrev $formattedAccount"
                }
            }
        }

        // 4. Calculus, summations, derivatives, and relational operators
        for ((pattern, replacement) in RELATIONAL_RULES) {
            out = pattern.replace(out, replacement)
        }

        // "integral from a to b of" or "integral from a to b"
        out = Regex(
            "\\bintegral\\s+from\\s+(\\S+?)\\s+to\\s+(\\S+?)(?:\\s+of\\b)?",
            RegexOption.IGNORE_CASE
        ).replace(out) { m ->
            "\\int_{${m.groupValues[1]}}^{${m.groupValues[2]}}"
        }

        // "sum from i equals 1 to n" or "sum from i = 1 to n"
        out = Regex(
            "\\bsum\\s+from\\s+([a-zA-Z])\\s+(?:equals|=)\\s+(\\S+?)\\s+to\\s+(\\S+)",
            RegexOption.IGNORE_CASE
        ).replace(out) { m ->
            val upper = m.groupValues[3].trimEnd('.', ',', ';', ':')
            val trailing = m.groupValues[3].substring(upper.length)
            "\\sum_{${m.groupValues[1]}=${m.groupValues[2]}}^{$upper}$trailing"
        }

        // "delta y over delta x" -> "Δy/Δx", "partial y over partial x" -> "∂y/∂x"
        out = Regex(
            "\\bdelta\\s+([a-zA-Z])\\s+over\\s+delta\\s+([a-zA-Z])\\b",
            RegexOption.IGNORE_CASE
        ).replace(out) { m -> "Δ${m.groupValues[1]}/Δ${m.groupValues[2]}" }

        out = Regex(
            "\\bpartial\\s+([a-zA-Z])\\s+over\\s+partial\\s+([a-zA-Z])\\b",
            RegexOption.IGNORE_CASE
        ).replace(out) { m -> "∂${m.groupValues[1]}/∂${m.groupValues[2]}" }

        out = Regex(
            "\\bdelta\\s+([a-zA-Z])\\b",
            RegexOption.IGNORE_CASE
        ).replace(out) { m -> "Δ${m.groupValues[1]}" }

        // 5. Spoken Greek letters (skip in Humanities/Law & Accounting where prose uses "alpha"/"beta")
        if (domain != SubjectDomain.HUMANITIES_LAW && domain != SubjectDomain.ACCOUNTING_FINANCE) {
            for ((pattern, replacement) in GREEK_RULES) {
                out = pattern.replace(out, replacement)
            }
        }

        // 6. Roots, subscripts, and exponents (after Greek pass so "sigma squared" -> "σ^2")
        out = Regex(
            "\\bsquare\\s+root\\s+of\\s+(\\([^)]+\\)|[a-zA-Z0-9_α-ωΑ-Ω]+)",
            RegexOption.IGNORE_CASE
        ).replace(out) { m ->
            val inner = m.groupValues[1].removeSurrounding("(", ")")
            "√($inner)"
        }

        out = Regex(
            "\\b([a-zA-Zα-ωΑ-Ω])\\s+sub\\s+([a-zA-Z0-9]+)\\b",
            RegexOption.IGNORE_CASE
        ).replace(out) { m -> "${m.groupValues[1]}_${m.groupValues[2]}" }

        out = Regex(
            "(\\([^)]+\\)|\\b[a-zA-Z0-9α-ωΑ-Ω](?:_[a-zA-Z0-9]+)?)\\s+squared\\b",
            RegexOption.IGNORE_CASE
        ).replace(out) { m -> "${m.groupValues[1]}^2" }

        out = Regex(
            "(\\([^)]+\\)|\\b[a-zA-Z0-9α-ωΑ-Ω](?:_[a-zA-Z0-9]+)?)\\s+cubed\\b",
            RegexOption.IGNORE_CASE
        ).replace(out) { m -> "${m.groupValues[1]}^3" }

        out = Regex(
            "(\\b[a-zA-Z0-9α-ωΑ-Ω]+)\\s+to\\s+the\\s+power\\s+of\\s+([a-zA-Z0-9_+-]+)\\b",
            RegexOption.IGNORE_CASE
        ).replace(out) { m -> "${m.groupValues[1]}^${m.groupValues[2]}" }

        return out
    }

    /**
     * Extracts inline `$...$`, display `$$...$$`, `\ce{...}`, LaTeX operators (`\int`, `\sum`),
     * and standalone equation lines (`lhs = rhs`, reaction equations) from [text].
     */
    fun extractFormulas(text: String, domain: SubjectDomain = SubjectDomain.GENERAL): List<String> {
        if (text.isBlank()) return emptyList()
        val results = LinkedHashSet<String>()

        // 1. Display math $$...$$
        val displayRegex = Regex("\\$\\$(.+?)\\$\\$", RegexOption.DOT_MATCHES_ALL)
        var scrubbed = displayRegex.replace(text) { m ->
            val expr = m.groupValues[1].trim()
            if (expr.isNotEmpty()) results.add(expr)
            " "
        }

        // 2. Inline math $...$ (avoid matching currency like "$50 and $100")
        val inlineRegex = Regex("(?<!\\$)\\$(?!\\s)([^$\\n]+?)(?<!\\s)\\$(?!\\$)")
        scrubbed = inlineRegex.replace(scrubbed) { m ->
            val expr = m.groupValues[1].trim()
            val looksLikeCurrencySpan = expr.firstOrNull()?.isDigit() == true && expr.contains(" ") &&
                !expr.any { it in charArrayOf('=', '+', '-', '*', '/', '^', '_', '\\') }
            if (expr.isNotEmpty() && !looksLikeCurrencySpan) {
                results.add(expr)
                " "
            } else {
                m.value
            }
        }

        // 3. mhchem \ce{...} blocks
        val ceRegex = Regex("\\\\ce\\{[^}]+\\}")
        scrubbed = ceRegex.replace(scrubbed) { m ->
            results.add(m.value.trim())
            " "
        }

        // 4. Inline LaTeX \int or \sum expressions without dollar signs
        val latexOpRegex = Regex("\\\\(?:int|sum)_\\{[^}]+\\}\\^\\{[^}]+\\}(?:\\s*[^.,;\\n]*)?")
        latexOpRegex.findAll(scrubbed).forEach { m ->
            results.add(m.value.trim())
        }

        // 5. Equation lines / clauses (lhs = rhs, ≥, ≤, ≈, →, ⇌)
        val equationOperators = listOf("=", "≥", "≤", "≈", "→", "⇌")
        scrubbed.lines().flatMap { it.split(';') }.forEach { rawLine ->
            val line = rawLine.trim().removePrefix("- ").removePrefix("* ").trim()
            if (line.isBlank() || line.startsWith("http://") || line.startsWith("https://")) return@forEach
            val op = equationOperators.firstOrNull { line.contains(it) } ?: return@forEach
            val parts = line.split(op, limit = 2)
            if (parts.size == 2) {
                val lhs = parts[0].trim()
                val rhs = parts[1].trim()
                val lhsWords = lhs.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val rhsWords = rhs.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (lhs.isNotEmpty() && rhs.isNotEmpty() && lhsWords.size <= 6 && rhsWords.size <= 12) {
                    results.add(line)
                }
            }
        }

        return results.toList()
    }

    private fun toSubscriptFormula(token: String): String = buildString(token.length) {
        for (ch in token) {
            if (ch in '0'..'9') {
                append(SUBSCRIPT_DIGITS[ch - '0'])
            } else {
                append(ch)
            }
        }
    }
}
