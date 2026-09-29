package com.example.ai.faith

/**
 * Prompts are versioned files in `assets/prompts/` (PLAN_V3; Faith spec §37), not strings in
 * code. Each file starts with a front-matter block (id, version, purpose, input, output) followed
 * by `## Section` bodies: Theological contract, Grounding rules, Citation rules, Forbidden, Prompt.
 * The files are also on the classpath, so the pure engines and their tests read the same text.
 */
data class PromptAsset(val id: String, val version: Int, val fields: Map<String, String>, val sections: Map<String, String>) {
    fun section(name: String): String = sections[name.lowercase()] ?: error("Prompt '$id' has no section '$name'")

    /** Everything a model must be told: the contract sections, then the prompt itself. */
    fun system(): String = listOf("theological contract", "grounding rules", "citation rules", "forbidden", "prompt")
        .mapNotNull { sections[it]?.trim()?.takeIf { s -> s.isNotEmpty() } }
        .joinToString("\n\n")

    companion object {
        val REQUIRED_FIELDS = listOf("id", "version", "purpose", "input", "output")
        val REQUIRED_SECTIONS = listOf("theological contract", "grounding rules", "citation rules", "forbidden", "prompt")

        fun parse(text: String): PromptAsset {
            val lines = text.replace("\r\n", "\n").lines()
            require(lines.firstOrNull()?.trim() == "---") { "Prompt must start with front matter" }
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" } + 1
            require(end > 0) { "Unclosed front matter" }
            val fields = lines.subList(1, end).mapNotNull { l ->
                val i = l.indexOf(':'); if (i <= 0) null else l.substring(0, i).trim().lowercase() to l.substring(i + 1).trim()
            }.toMap()
            val sections = linkedMapOf<String, String>()
            var current: String? = null
            val body = StringBuilder()
            fun flush() { current?.let { sections[it] = body.toString().trim() }; body.clear() }
            for (l in lines.drop(end + 1)) {
                if (l.startsWith("## ")) { flush(); current = l.removePrefix("## ").trim().lowercase() } else body.append(l).append('\n')
            }
            flush()
            return PromptAsset(fields["id"].orEmpty(), fields["version"]?.toIntOrNull() ?: 0, fields, sections)
        }
    }
}

object Prompts {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, PromptAsset>()

    val ALL = listOf("faith_contract", "devotional", "sermon_study", "ask_sermon", "scene_classifier", "faith_assistant")

    fun get(id: String): PromptAsset = cache.getOrPut(id) {
        val text = Prompts::class.java.getResourceAsStream("/$id.md")?.bufferedReader()?.use { it.readText() }
            ?: error("Missing prompt asset assets/prompts/$id.md")
        PromptAsset.parse(text)
    }

    /** The shared Faith theology contract, prefixed to every Faith prompt. */
    val faithContract: String get() = get("faith_contract").system()
}
