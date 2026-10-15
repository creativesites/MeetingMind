package com.craftflowtechnologies.meetingmind.ai.assistant

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.faith.Prompts
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import org.json.JSONObject

enum class SourceKind(val label: String) { NOTE("Note"), RECORDING("Recording"), TASK("Task"), SCRIPTURE("Scripture"), ITEM("Item") }

/** One thing Ask can cite: a note, a moment in a recording, a task. [key] is its number in the prompt. */
data class AskSource(
    val key: Int,
    val kind: SourceKind,
    val title: String,
    val text: String,
    val date: Long? = null,
    val noteId: String? = null,
    val meetingId: String? = null,
    val startMs: Long? = null,
    val taskId: String? = null,
    val faith: Boolean = false
)

data class AskAnswer(val text: String, val cited: List<AskSource>, val sources: List<AskSource>, val dropped: List<Int>) {
    val unverified get() = cited.isEmpty() && !Regex("couldn'?t find|could not find|don'?t (see|have)", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

/**
 * Ask across everything (Faith spec slice F): on-device search finds the sources, the model only
 * answers from them, and every [n] it writes is checked against the list it was given.
 */
object AskEverything {
    private val STOP = setOf(
        "a", "an", "the", "and", "or", "but", "of", "to", "in", "on", "at", "for", "with", "about", "from", "by", "is", "are", "was", "were",
        "be", "been", "do", "did", "does", "what", "when", "where", "who", "whom", "which", "why", "how", "i", "me", "my", "we", "our", "you",
        "your", "it", "its", "this", "that", "these", "those", "there", "have", "has", "had", "say", "said", "tell", "any", "all", "can", "could",
        "would", "should", "will", "just", "so", "if", "than", "then", "into", "out", "up", "down", "again", "ever", "last", "time", "anything",
        "something", "things", "thing", "notes", "note", "recording", "recordings", "find", "show", "give", "list"
    )

    /** Content words, for when there's no model to widen the search. */
    fun terms(question: String): List<String> =
        Regex("[\\p{L}\\p{N}']+").findAll(question.lowercase()).map { it.value.trim('\'') }
            .filter { it.length > 2 && it !in STOP }.distinct().take(6).toList()

    fun render(sources: List<AskSource>, dateLabel: (Long) -> String): String = sources.joinToString("\n\n") { s ->
        val head = listOfNotNull(s.kind.label, s.title.takeIf { it.isNotBlank() }, s.date?.let(dateLabel)).joinToString(" · ")
        "[${s.key}] $head:\n${s.text.trim()}"
    }

    private val MARKER = Regex("\\[(\\d{1,2})]")

    fun parse(answer: String, sources: List<AskSource>): AskAnswer {
        val byKey = sources.associateBy { it.key }
        val cited = LinkedHashMap<Int, AskSource>()
        val dropped = mutableListOf<Int>()
        val text = MARKER.replace(answer) { m ->
            val k = m.groupValues[1].toInt()
            val s = byKey[k]
            if (s != null) { cited[k] = s; m.value } else { dropped += k; "" }
        }.replace(Regex("[ \\t]+([.,;:!?])"), "$1").trim()
        return AskAnswer(text, cited.values.toList(), sources, dropped)
    }

    fun parseQueries(raw: String): List<String> = runCatching {
        val arr = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")).optJSONArray("queries") ?: return emptyList()
        (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() && it.length < 60 }.distinct().take(5)
    }.getOrDefault(emptyList())
}

class AskEverythingEngine(
    private val transport: GeminiTransport,
    /** On-device search: one query in, candidate sources out (keys are reassigned here). */
    private val search: suspend (String) -> List<AskSource>,
    private val dateLabel: (Long) -> String,
    private val maxSources: Int = 14
) {
    suspend fun ask(question: String): AiResult<AskAnswer> {
        if (!transport.refreshConfigured()) {
            return AiResult.ModelUnavailable(DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL, "Ask needs Internet mode or the backup AI. Search still works offline.")
        }
        val model = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL
        // 1. Widen the question into searches (synonyms, names, references) — the recall step.
        val expanded = (transport.execute(
            GeminiRequest(
                model,
                "You turn a question about someone's personal notes and recordings into 2 to 5 short keyword searches (1-3 words each): the key nouns, names, synonyms and any Bible references. Reply with JSON only: {\"queries\":[...]}",
                question, responseSchema = QUERIES_SCHEMA, timeoutMs = 20_000
            )
        ) as? AiResult.Success)?.value?.let(AskEverything::parseQueries).orEmpty()
        val queries = (listOf(question) + expanded + AskEverything.terms(question)).distinct()
        // 2. Search on the phone, keep the best few, number them.
        val found = LinkedHashMap<String, AskSource>()
        outer@ for (q in queries) for (s in search(q)) {
            val id = s.noteId?.let { "n$it" } ?: s.taskId?.let { "t$it" } ?: "m${s.meetingId}@${s.startMs}"
            found.putIfAbsent(id, s)
            if (found.size >= maxSources) break@outer
        }
        val sources = found.values.mapIndexed { i, s -> s.copy(key = i + 1) }
        if (sources.isEmpty()) {
            return AiResult.Success(AskAnswer("I couldn't find anything about that in your notes, recordings or tasks.", emptyList(), emptyList(), emptyList()))
        }
        // 3. Answer from those sources only.
        val system = (if (sources.any { it.faith }) Prompts.faithContract + "\n\n" else "") + Prompts.get("ask_everything").system()
        val res = transport.execute(GeminiRequest(model, system, "Sources:\n\n${AskEverything.render(sources, dateLabel)}\n\nQuestion: $question", timeoutMs = 45_000))
        if (res !is AiResult.Success) return AiResult.Failed(res.describeFailure() ?: "Ask failed.")
        return AiResult.Success(AskEverything.parse(res.value, sources))
    }

    private companion object {
        const val QUERIES_SCHEMA = """{"type":"object","properties":{"queries":{"type":"array","items":{"type":"string"}}},"required":["queries"]}"""
    }
}
