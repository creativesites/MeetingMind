package com.example.core.notes

/**
 * Tidies an answer pasted from ChatGPT, Claude, Gemini or DeepSeek, without any AI: the chat
 * app's buttons and status lines go, code and JSON that lost their fences get them back, box
 * diagrams stay monospace, and space-aligned columns become a table. Prose is never rewritten.
 */
object AiPasteCleanup {

    data class Result(val markdown: String, val source: String?, val changes: List<String>)

    private val CHROME = listOf(
        Regex("^(Worked|Thought|Reasoned|Searched|Analyzed|Analysed) for \\d+[^\\n]{0,40}$", RegexOption.IGNORE_CASE),
        Regex("^(Copy code|Copy|Edit|Share|Retry|Regenerate|Sources|Show more|Show less|Read more|Done|Stop generating)$", RegexOption.IGNORE_CASE),
        Regex("^(You said|ChatGPT said|Claude said|Gemini said|DeepSeek said|Assistant|User):?$", RegexOption.IGNORE_CASE),
        Regex("^\\d+ ?/ ?\\d+$"),
        Regex("^(ChatGPT|Claude|Gemini|DeepSeek) (can make mistakes|is AI and can make mistakes).*$", RegexOption.IGNORE_CASE)
    )
    private val LANGS = setOf("kotlin", "java", "json", "javascript", "typescript", "python", "bash", "shell", "sh", "sql", "xml", "html",
        "css", "yaml", "yml", "swift", "go", "rust", "c", "cpp", "c++", "csharp", "c#", "ruby", "php", "dart", "text", "plaintext", "markdown", "diff", "gradle", "toml")
    private val CODE_START = Regex(
        "^\\s*(enum class|data class|sealed (class|interface)|(abstract |open |private |internal |public )?(class|interface|object) \\w|fun \\w|suspend fun|(val|var|const val) \\w+\\s*[:=]|import [\\w.]+|package [\\w.]+|def \\w+\\(|function \\w+\\(|(const|let) \\w+\\s*=|#include|SELECT |CREATE TABLE|@\\w+(\\(|$))"
    )
    private val BOX = Regex("[─│┌┐└┘├┤┬┴┼═║╔╗╚╝╠╣╦╩╬▼▲►◄]")
    private val ARROWS = Regex("^\\s*[→↓↑←⇩⇓]\\s*|[→↓]\\s*$")

    fun looksLikeAiAnswer(text: String): Boolean =
        text.lines().any { l -> CHROME.any { it.matches(l.trim()) } } || text.lines().any { BOX.containsMatchIn(it) } ||
            text.lines().count { CODE_START.containsMatchIn(it) } >= 2

    fun detectSource(text: String): String? {
        val t = text.take(4000)
        return when {
            Regex("ChatGPT said|Worked for \\d+|Thought for \\d+", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "ChatGPT"
            Regex("Claude said|Claude can make mistakes", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "Claude"
            Regex("Gemini said|Gemini can make mistakes", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "Gemini"
            Regex("DeepSeek said|DeepThink", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "DeepSeek"
            else -> null
        }
    }

    fun clean(input: String): Result {
        val changes = linkedSetOf<String>()
        val lines = input.replace("\r\n", "\n").replace('\r', '\n').lines().map { it.trimEnd() }
        val out = mutableListOf<String>()
        var i = 0
        var fenced = false
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            if (t.startsWith("```")) { fenced = !fenced; out += line; i++; continue }
            if (fenced) { out += line; i++; continue }

            if (CHROME.any { it.matches(t) }) { changes += "Removed chat app buttons and status lines"; i++; continue }

            // "kotlin" on its own line (then maybe "Copy code") right before code: a lost fence.
            if (t.lowercase() in LANGS && i + 1 < lines.size) {
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || CHROME.any { it.matches(lines[j].trim()) })) j++
                if (j < lines.size && (CODE_START.containsMatchIn(lines[j]) || lines[j].trim().startsWith("{") || lines[j].trim().startsWith("["))) {
                    val (code, next) = takeCode(lines, j)
                    out += "```${t.lowercase()}"; out += code; out += "```"
                    changes += "Put code back in code boxes"
                    i = next; continue
                }
            }

            // Code or JSON with no fence at all.
            if (CODE_START.containsMatchIn(line) || isJsonStart(lines, i)) {
                val (code, next) = takeCode(lines, i)
                if (code.size >= 2) {
                    out += "```${guessLang(code)}"; out += code; out += "```"
                    changes += "Put code back in code boxes"
                    i = next; continue
                }
            }

            // Box-drawing diagrams stay monospace.
            if (BOX.containsMatchIn(line)) {
                val block = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].isNotBlank() && (BOX.containsMatchIn(lines[j]) || ARROWS.containsMatchIn(lines[j]) && lines[j].trim().length < 60)) { block += lines[j]; j++ }
                out += "```"; out += block; out += "```"
                changes += "Kept diagrams in a fixed-width box"
                i = j; continue
            }

            // Columns lined up with spaces: a table the copy flattened.
            val table = columnsAt(lines, i)
            if (table != null) {
                val (rows, next) = table
                val width = rows.first().size
                out += "| " + rows.first().joinToString(" | ") + " |"
                out += "| " + List(width) { "---" }.joinToString(" | ") + " |"
                rows.drop(1).forEach { r -> out += "| " + r.joinToString(" | ") + " |" }
                changes += "Turned aligned columns into a table"
                i = next
                continue
            }

            out += line
            i++
        }
        if (fenced) out += "```"
        val text = out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        if (text.length < input.trim().length && changes.isEmpty()) changes += "Tidied spacing"
        return Result(text, detectSource(input), changes.toList())
    }

    private fun isJsonStart(lines: List<String>, i: Int): Boolean {
        val t = lines[i].trim()
        if (t != "{" && t != "[" && !(t.startsWith("{") && t.contains("\":"))) return false
        return lines.getOrNull(i + 1)?.trim()?.let { it.startsWith("\"") || it.startsWith("{") || it.startsWith("}") } == true
    }

    /** Lines from [start] that belong to one piece of code: until braces balance and prose resumes. */
    private fun takeCode(lines: List<String>, start: Int): Pair<List<String>, Int> {
        val code = mutableListOf<String>()
        var depth = 0
        var i = start
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank()) {
                val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() }
                val continues = depth > 0 || next != null && (CODE_START.containsMatchIn(next) || next.startsWith("    ") || next.trim().startsWith("}"))
                if (!continues) break
                code += l; i++; continue
            }
            if (depth == 0 && code.isNotEmpty() && !CODE_START.containsMatchIn(l) && !l.startsWith(" ") && !l.trim().startsWith("}") &&
                !l.trim().startsWith("@") && !l.trim().startsWith(")") && !l.trim().startsWith("\"") && !l.trim().startsWith("//")) break
            code += l
            depth += l.count { it == '{' || it == '[' || it == '(' } - l.count { it == '}' || it == ']' || it == ')' }
            if (depth < 0) depth = 0
            i++
        }
        while (code.isNotEmpty() && code.last().isBlank()) code.removeAt(code.lastIndex)
        return code to i
    }

    private fun guessLang(code: List<String>): String {
        val first = code.first().trim()
        return when {
            first.startsWith("{") || first.startsWith("[") -> "json"
            code.any { Regex("\\b(fun|val|data class|enum class|sealed)\\b").containsMatchIn(it) } -> "kotlin"
            code.any { it.trim().startsWith("def ") || it.trim().startsWith("import ") && it.contains(" as ") } -> "python"
            code.any { Regex("\\b(const|let|function)\\b").containsMatchIn(it) } -> "javascript"
            code.any { it.trim().uppercase().startsWith("SELECT") || it.trim().uppercase().startsWith("CREATE TABLE") } -> "sql"
            else -> ""
        }
    }

    /** Two or more lines split into the same number (2+) of columns by runs of spaces. */
    private fun columnsAt(lines: List<String>, start: Int): Pair<List<List<String>>, Int>? {
        fun cols(l: String) = l.trim().split(Regex("\\s{2,}|\\t+")).filter { it.isNotEmpty() }
        val first = lines[start]
        if (first.isBlank() || first.trimStart().startsWith("-") || first.trimStart().startsWith("|")) return null
        val n = cols(first).size
        if (n < 2) return null
        val rows = mutableListOf(cols(first))
        var i = start + 1
        while (i < lines.size && lines[i].isNotBlank() && cols(lines[i]).size == n) { rows += cols(lines[i]); i++ }
        return if (rows.size >= 2 && rows.all { r -> r.all { it.length <= 40 } }) rows to i else null
    }
}
