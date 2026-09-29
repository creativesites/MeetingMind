package com.craftflowtechnologies.meetingmind.ai.assistant

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.faith.Prompts
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import org.json.JSONArray
import org.json.JSONObject

/**
 * The notes and Faith assistant (PLAN M2; Faith spec slice D): a chat that can read the note, its
 * recording, the Bible and the person's other notes, and change things — inserts and new items at
 * once with Undo, replacements and deletions only after the person confirms.
 */
enum class AssistantTool(val id: String, val kind: Kind, val signature: String, val doc: String) {
    READ_NOTE("read_note", Kind.READ, "{}", "The open note's blocks, with ids."),
    SEARCH_NOTES("search_notes", Kind.READ, "{\"query\": string}", "Search the person's other notes; titles and excerpts."),
    READ_TRANSCRIPT("read_transcript", Kind.READ, "{\"query\": string|null}", "Passages of the note's recording with [mm:ss]; the most relevant to query, or an overview when null."),
    GET_VERSES("get_verses", Kind.READ, "{\"reference\": string, \"translation\": string|null}", "The text of a Bible passage from the app's Bible."),
    CROSS_REFERENCES("get_cross_references", Kind.READ, "{\"reference\": string}", "Cross references for a verse (Open Bible)."),
    COMMENTARY("get_commentary", Kind.READ, "{\"reference\": string, \"source\": \"matthew-henry\"|\"jamieson-fausset-brown\"|\"adam-clarke\"|\"john-gill\"|\"tyndale\"|null}", "Public-domain commentary on a passage."),
    COMPARE_TRANSLATIONS("compare_translations", Kind.READ, "{\"reference\": string}", "The passage in the translations on this phone."),
    ORIGINAL("get_original", Kind.READ, "{\"reference\": string}", "The verse's Hebrew or Greek words, each with transliteration, lemma, grammar and dictionary meaning (from STEPBible, if the person has downloaded it)."),
    INSERT_BLOCKS("insert_blocks", Kind.WRITE, "{\"markdown\": string, \"after_block_id\": string|null}", "Add Markdown to the note (at the end, or after a block). Never verse text."),
    INSERT_SCRIPTURE("insert_scripture", Kind.WRITE, "{\"reference\": string}", "Add a Bible passage block; the app fills in the real text."),
    REPLACE_BLOCKS("replace_blocks", Kind.CONFIRM, "{\"block_ids\": [string], \"markdown\": string}", "Replace blocks with new Markdown. The person confirms first."),
    DELETE_BLOCKS("delete_blocks", Kind.CONFIRM, "{\"block_ids\": [string]}", "Remove blocks. The person confirms first."),
    CREATE_TASK("create_task", Kind.WRITE, "{\"title\": string, \"due\": \"YYYY-MM-DD\"|null, \"remind_at\": \"YYYY-MM-DDTHH:MM\"|null, \"person\": string|null, \"kind\": \"task\"|\"apply\"|\"prayer\"|\"follow_up\"|null}", "Add a task (with an optional reminder)."),
    CREATE_NOTE("create_note", Kind.WRITE, "{\"title\": string, \"markdown\": string}", "Create a new note.");

    enum class Kind { READ, WRITE, CONFIRM }

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id.trim().lowercase() }

        fun describe(tools: Collection<AssistantTool>) = tools.joinToString("\n") { "- ${it.id} ${it.signature}: ${it.doc}" }
    }
}

data class ToolCall(val tool: AssistantTool, val args: JSONObject) {
    fun str(key: String): String? = args.optString(key).takeIf { args.has(key) && !args.isNull(key) && it.isNotBlank() }
    fun list(key: String): List<String> = args.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
}

data class ModelTurn(val say: String, val calls: List<ToolCall>, val unknownTools: List<String> = emptyList())

object AssistantProtocol {
    /** Lenient: fenced or bare JSON, or plain prose (treated as a reply with no calls). */
    fun parse(raw: String): ModelTurn {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        val json = if (start >= 0 && end > start) runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() else null
        if (json == null || (!json.has("say") && !json.has("calls"))) return ModelTurn(raw.trim(), emptyList())
        val calls = mutableListOf<ToolCall>()
        val unknown = mutableListOf<String>()
        val arr = json.optJSONArray("calls") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val name = c.optString("tool")
            val tool = AssistantTool.byId(name)
            if (tool == null) unknown += name else calls += ToolCall(tool, c.optJSONObject("args") ?: JSONObject())
        }
        return ModelTurn(json.optString("say").trim(), calls, unknown)
    }
}

/** What the assistant is working on: shown in its header and given to the model. */
data class AssistantScope(
    val title: String,
    val faith: Boolean,
    /** "Sermon", "Meeting", "Your library"… */
    val kindLabel: String,
    val noteId: String? = null,
    val meetingId: String? = null,
    val sermon: Boolean = false,
    val today: String = ""
)

/** A change the assistant made or proposes, shown as a card under its reply. */
data class AssistantAction(
    val id: String,
    val label: String,
    /** What will change, for confirmation cards. */
    val preview: String? = null,
    val needsConfirmation: Boolean = false,
    val state: State = if (needsConfirmation) State.PENDING else State.APPLIED,
    /** Where it leads: a note or task id to open. */
    val openNoteId: String? = null,
    val openTaskId: String? = null
) {
    enum class State { PENDING, APPLIED, UNDONE, DISCARDED }
}

/** The app side: runs read tools and applies write tools against the real note, Bible and tasks. */
interface AssistantHost {
    val scope: AssistantScope
    /** A short outline of the note for the prompt: ids, types and first words. */
    suspend fun outline(): String
    suspend fun read(call: ToolCall): ToolResult
    suspend fun write(call: ToolCall): Pair<ToolResult, AssistantAction>
}

/** [chip] is what the person sees ("Read John 15:1–8"); [text] is what the model gets back. */
data class ToolResult(val chip: String, val text: String, val ok: Boolean = true)

data class AssistantMessage(
    val fromUser: Boolean,
    val text: String,
    val activity: List<String> = emptyList(),
    val actions: List<AssistantAction> = emptyList(),
    val error: Boolean = false
)

class AssistantEngine(
    private val transport: GeminiTransport,
    private val host: AssistantHost,
    private val maxRounds: Int = 4,
    private val model: String = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL
) {
    fun systemPrompt(): String {
        val scope = host.scope
        val parts = mutableListOf<String>()
        if (scope.faith) parts += Prompts.faithContract
        parts += Prompts.get(if (scope.faith) "faith_assistant" else "notes_assistant").system()
        if (scope.sermon) parts += Prompts.get("sermon_study").system()
        val tools = AssistantTool.entries.filter { t ->
            when (t) {
                AssistantTool.READ_NOTE, AssistantTool.INSERT_BLOCKS, AssistantTool.INSERT_SCRIPTURE, AssistantTool.REPLACE_BLOCKS, AssistantTool.DELETE_BLOCKS -> scope.noteId != null
                AssistantTool.READ_TRANSCRIPT -> scope.meetingId != null
                else -> true
            }
        }
        parts += "Tools:\n" + AssistantTool.describe(tools) +
            "\n\nReply with one JSON object only: {\"say\": string, \"calls\": [{\"tool\": string, \"args\": object}]}. " +
            "Use \"calls\": [] when you are done. Results of read tools come back to you; write tools are applied by the app."
        return parts.joinToString("\n\n")
    }

    /**
     * One reply to [message]: up to [maxRounds] model calls, reading as needed, applying writes.
     * [onProgress] gets each tool chip as it happens, for the "thinking" line.
     */
    suspend fun reply(history: List<AssistantMessage>, message: String, onProgress: (String) -> Unit = {}): AssistantMessage {
        val scope = host.scope
        val context = buildString {
            append("Today: ${scope.today}\n")
            append("Scope: ${scope.kindLabel} — \"${scope.title}\"\n")
            if (scope.noteId != null) append("Note outline:\n${host.outline()}\n")
            if (history.isNotEmpty()) {
                append("\nConversation so far:\n")
                history.takeLast(12).forEach { m -> append(if (m.fromUser) "Person: " else "You: ").append(m.text.take(1500)).append('\n') }
            }
            append("\nPerson: $message\n")
        }
        val activity = mutableListOf<String>()
        val actions = mutableListOf<AssistantAction>()
        val results = StringBuilder()
        var say = ""
        for (round in 1..maxRounds) {
            val prompt = context + if (results.isNotEmpty()) "\nTool results so far:\n$results\nContinue." else ""
            val res = transport.execute(GeminiRequest(model, systemPrompt(), prompt, timeoutMs = 60_000, temperature = 0.3f))
            if (res !is AiResult.Success) {
                return AssistantMessage(false, res.describeFailure() ?: "The assistant couldn't answer just now.", activity, actions, error = true)
            }
            val turn = AssistantProtocol.parse(res.value)
            if (turn.say.isNotBlank()) say = turn.say
            turn.unknownTools.forEach { results.append("[error] unknown tool '$it'\n") }
            if (turn.calls.isEmpty()) break
            var needsAnotherRound = false
            for (call in turn.calls.take(6)) {
                if (call.tool.kind == AssistantTool.Kind.READ) {
                    val r = runCatching { host.read(call) }.getOrElse { ToolResult("Couldn't ${call.tool.id}", "error: ${it.message}", ok = false) }
                    activity += r.chip; onProgress(r.chip)
                    results.append("[${call.tool.id} ${call.args}]\n${r.text.take(6000)}\n\n")
                    needsAnotherRound = true
                } else {
                    val (r, action) = runCatching { host.write(call) }.getOrElse {
                        ToolResult("Couldn't ${call.tool.id}", "error: ${it.message}", ok = false) to AssistantAction("err", "Couldn't ${call.tool.id.replace('_', ' ')}", state = AssistantAction.State.DISCARDED)
                    }
                    if (r.ok) actions += action
                    activity += r.chip; onProgress(r.chip)
                    results.append("[${call.tool.id}] ${r.text}\n")
                    if (!r.ok) needsAnotherRound = true
                }
            }
            // Writes alone finish the turn — the model already said what it was doing.
            if (!needsAnotherRound && say.isNotBlank()) break
            if (round == maxRounds && say.isBlank()) say = "I've gathered what I found above."
        }
        val cleaned = if (scope.faith) FaithGuard.clean(say) else say
        return AssistantMessage(false, cleaned.ifBlank { if (actions.isNotEmpty()) "Done." else "I'm not sure how to help with that — could you say a bit more?" }, activity, actions)
    }
}

/** Removes sentences in the assistant's own voice that claim to speak for God. */
object FaithGuard {
    private val AUTHORITY = listOf(
        Regex("\\bthus says the lord\\b", RegexOption.IGNORE_CASE),
        Regex("\\bGod is (telling|saying to|calling) you\\b", RegexOption.IGNORE_CASE),
        Regex("\\bthe Lord (is )?(says|saying) to you\\b", RegexOption.IGNORE_CASE),
        Regex("\\bI (prophesy|declare) (over|that|to) you\\b", RegexOption.IGNORE_CASE),
        Regex("\\bGod (has revealed|wants me to tell you)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bGod told me\\b", RegexOption.IGNORE_CASE)
    )

    private fun reported(s: String) =
        !Regex("\\bthus says\\b", RegexOption.IGNORE_CASE).containsMatchIn(s) &&
            Regex("\\b(he|she|they|the (preacher|speaker|pastor)|your note|[A-Z][a-z]+) (said|says|told|shared|described|explained|wrote)\\b").containsMatchIn(s)

    fun violations(text: String): List<String> =
        Regex("[^.!?\\n]*(?:[.!?]+|\\n|$)").findAll(text).map { it.value }.filter { s -> AUTHORITY.any { it.containsMatchIn(s) } && !reported(s) }.map { it.trim() }.toList()

    fun clean(text: String): String {
        val bad = violations(text).toSet()
        if (bad.isEmpty()) return text
        return Regex("[^.!?\\n]*(?:[.!?]+|\\n|$)").findAll(text).map { it.value }.filterNot { it.trim() in bad }.joinToString("").replace(Regex("[ \\t]{2,}"), " ").trim()
    }
}
