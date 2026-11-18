package com.craftflowtechnologies.meetingmind.ai.assistant

import com.craftflowtechnologies.meetingmind.ai.cloud.AiStreamEvent
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.cloud.ThinkingLevel
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.faith.Prompts
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    private val MULTI_MARKER = Regex("\\[(\\d{1,2}(?:\\s*,\\s*\\d{1,2})+)]")
    private val MARKER = Regex("\\[(\\d{1,2})]")

    fun parse(answer: String, sources: List<AskSource>): AskAnswer {
        val byKey = sources.associateBy { it.key }
        val cited = LinkedHashMap<Int, AskSource>()
        val dropped = mutableListOf<Int>()
        val normalized = MULTI_MARKER.replace(answer) { m ->
            m.groupValues[1].split(',').joinToString("") { "[${it.trim()}]" }
        }
        val text = MARKER.replace(normalized) { m ->
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

sealed interface AskEverythingStreamEvent {
    data class Streaming(val partialText: String, val sources: List<AskSource>) : AskEverythingStreamEvent
    data class Complete(val result: AiResult<AskAnswer>) : AskEverythingStreamEvent
}

class AskEverythingEngine(
    private val transport: GeminiTransport,
    /** On-device search: one query in, candidate sources out (keys are reassigned here). */
    private val search: suspend (String) -> List<AskSource>,
    private val dateLabel: (Long) -> String,
    private val maxSources: Int = 14,
    private val localModelProvider: (suspend () -> LanguageModel?)? = null,
    private val preferLocal: Boolean = false,
    private val onLocalFinished: (() -> Unit)? = null,
    /** True when any found source must stay on the phone (a confidential project): the answer is then
     * written on the phone, never by a cloud model, whatever the processing profile. */
    private val keepOnDevice: (suspend (List<AskSource>) -> Boolean)? = null
) {
    private suspend fun cloudAllowedFor(sources: List<AskSource>): Boolean =
        keepOnDevice?.let { check -> runCatching { !check(sources) }.getOrDefault(false) } ?: true

    fun askStream(question: String): Flow<AskEverythingStreamEvent> = flow {
        val cloudReady = !preferLocal && transport.refreshConfigured()
        if (!cloudReady && localModelProvider == null) {
            emit(AskEverythingStreamEvent.Complete(AiResult.ModelUnavailable(DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL, "Ask needs Internet mode or an on-device language model. Search still works offline.")))
            return@flow
        }
        val sources = retrieveSources(question, allowCloud = cloudReady)
        if (sources.isEmpty()) {
            emit(AskEverythingStreamEvent.Complete(AiResult.Success(AskAnswer("I couldn't find anything about that in your notes, recordings or tasks.", emptyList(), emptyList(), emptyList()))))
            return@flow
        }
        val useCloud = cloudReady && cloudAllowedFor(sources)
        if (!useCloud && localModelProvider == null) {
            emit(AskEverythingStreamEvent.Complete(AiResult.ModelUnavailable("local-llm", CONFIDENTIAL_NEEDS_LOCAL)))
            return@flow
        }
        val model = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL
        val system = (if (sources.any { it.faith }) Prompts.faithContract + "\n\n" else "") + Prompts.get("ask_everything").system()
        val raw = StringBuilder()
        var doneText: String? = null
        var failureMsg: String? = null
        var emittedText = false

        if (useCloud) {
            transport.stream(
                GeminiRequest(
                    model,
                    system,
                    "Sources:\n\n${AskEverything.render(sources, dateLabel)}\n\nQuestion: $question",
                    thinkingLevel = ThinkingLevel.LOW,
                    timeoutMs = 60_000
                )
            ).collect { ev ->
                when (ev) {
                    is AiStreamEvent.TextDelta -> {
                        emittedText = true
                        raw.append(ev.text)
                        emit(AskEverythingStreamEvent.Streaming(raw.toString(), sources))
                    }
                    is AiStreamEvent.Completed -> doneText = ev.text
                    is AiStreamEvent.Failed -> failureMsg = ev.error.describeFailure()
                    else -> Unit
                }
            }
        }

        val cloudText = (doneText ?: raw.toString()).takeIf { it.isNotBlank() }
        if (cloudText != null) {
            emit(AskEverythingStreamEvent.Complete(AiResult.Success(AskEverything.parse(cloudText, sources))))
            return@flow
        }

        val local = if (!emittedText) localModelProvider?.invoke() else null
        if (local == null) {
            val message = failureMsg ?: if (preferLocal) {
                "No on-device language model is installed yet. Download one in Settings → AI Engine."
            } else {
                "Ask needs Internet mode or an on-device language model. Search still works offline."
            }
            emit(AskEverythingStreamEvent.Complete(AiResult.Failed(message)))
            return@flow
        }

        try {
            val localSources = capSourcesForLocal(sources)
            val localPrompt = buildString {
                appendLine(system)
                appendLine()
                appendLine("Sources:")
                appendLine(AskEverything.render(localSources, dateLabel))
                appendLine()
                append("Question: $question")
            }
            val localRaw = StringBuilder()
            var localDone: String? = null
            var localErr: String? = null
            local.generateStream(localPrompt, maxOutputTokens = 512).collect { ev ->
                when (ev) {
                    is AiStreamEvent.TextDelta -> {
                        localRaw.append(ev.text)
                        emit(AskEverythingStreamEvent.Streaming(localRaw.toString(), localSources))
                    }
                    is AiStreamEvent.Completed -> localDone = ev.text
                    is AiStreamEvent.Failed -> localErr = ev.error.describeFailure()
                    else -> Unit
                }
            }
            val finalLocal = (localDone ?: localRaw.toString()).takeIf { it.isNotBlank() }
            if (finalLocal != null) {
                emit(AskEverythingStreamEvent.Complete(AiResult.Success(AskEverything.parse(finalLocal, localSources))))
            } else {
                emit(AskEverythingStreamEvent.Complete(AiResult.Failed(localErr ?: "Ask failed on device.")))
            }
        } finally {
            onLocalFinished?.invoke()
        }
    }

    suspend fun ask(question: String): AiResult<AskAnswer> {
        val cloudReady = !preferLocal && transport.refreshConfigured()
        if (!cloudReady && localModelProvider == null) {
            return AiResult.ModelUnavailable(DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL, "Ask needs Internet mode or an on-device language model. Search still works offline.")
        }
        val model = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL
        val sources = retrieveSources(question, allowCloud = cloudReady)
        if (sources.isEmpty()) {
            return AiResult.Success(AskAnswer("I couldn't find anything about that in your notes, recordings or tasks.", emptyList(), emptyList(), emptyList()))
        }
        // 3. Answer from those sources only (on the phone when any of them must stay there).
        val useCloud = cloudReady && cloudAllowedFor(sources)
        val system = (if (sources.any { it.faith }) Prompts.faithContract + "\n\n" else "") + Prompts.get("ask_everything").system()
        if (useCloud) {
            val res = transport.execute(GeminiRequest(model, system, "Sources:\n\n${AskEverything.render(sources, dateLabel)}\n\nQuestion: $question", timeoutMs = 60_000))
            if (res is AiResult.Success) return AiResult.Success(AskEverything.parse(res.value, sources))
            val local = localModelProvider?.invoke() ?: return AiResult.Failed(res.describeFailure() ?: "Ask failed.")
            return runLocalAsk(local, system, question, sources)
        }
        val local = localModelProvider?.invoke()
            ?: return AiResult.ModelUnavailable("local-llm", "No on-device language model is installed yet. Download one in Settings → AI Engine.")
        return runLocalAsk(local, system, question, sources)
    }

    private suspend fun runLocalAsk(
        local: LanguageModel,
        system: String,
        question: String,
        sources: List<AskSource>
    ): AiResult<AskAnswer> = try {
        val localSources = capSourcesForLocal(sources)
        val prompt = "$system\n\nSources:\n\n${AskEverything.render(localSources, dateLabel)}\n\nQuestion: $question"
        when (val res = local.generate(prompt, maxOutputTokens = 512)) {
            is AiResult.Success -> AiResult.Success(AskEverything.parse(res.value, localSources))
            else -> AiResult.Failed(res.describeFailure() ?: "Ask failed.")
        }
    } finally {
        onLocalFinished?.invoke()
    }

    private fun capSourcesForLocal(sources: List<AskSource>): List<AskSource> {
        val out = mutableListOf<AskSource>()
        var chars = 0
        for (s in sources.take(6)) {
            val trimmed = s.copy(text = s.text.take(450))
            if (out.isNotEmpty() && chars + trimmed.text.length > 2600) break
            out += trimmed
            chars += trimmed.text.length
        }
        return out
    }

    private suspend fun retrieveSources(question: String, allowCloud: Boolean): List<AskSource> {
        val model = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL
        // 1. Widen the question into searches (synonyms, names, references) — the recall step.
        val expanded = if (allowCloud) {
            (transport.execute(
                GeminiRequest(
                    model,
                    "You turn a question about someone's personal notes and recordings into 2 to 5 short keyword searches (1-3 words each): the key nouns, names, synonyms and any Bible references. Reply with JSON only: {\"queries\":[...]}",
                    question, responseSchema = QUERIES_SCHEMA, timeoutMs = 30_000
                )
            ) as? AiResult.Success)?.value?.let(AskEverything::parseQueries).orEmpty()
        } else emptyList()
        val queries = (listOf(question) + expanded + AskEverything.terms(question)).distinct()
        // 2. Search on the phone, keep the best few, number them.
        val found = LinkedHashMap<String, AskSource>()
        outer@ for (q in queries) for (s in search(q)) {
            val id = s.noteId?.let { "n$it" } ?: s.taskId?.let { "t$it" } ?: "m${s.meetingId}@${s.startMs}"
            found.putIfAbsent(id, s)
            if (found.size >= maxSources) break@outer
        }
        return found.values.mapIndexed { i, s -> s.copy(key = i + 1) }
    }

    private companion object {
        const val CONFIDENTIAL_NEEDS_LOCAL = "Some of what this question touches belongs to a confidential project, so it can only be answered on this phone. Download a model for Summaries & Ask in Settings → AI Engine."
        const val QUERIES_SCHEMA = """{"type":"object","properties":{"queries":{"type":"array","items":{"type":"string"}}},"required":["queries"]}"""
    }
}
