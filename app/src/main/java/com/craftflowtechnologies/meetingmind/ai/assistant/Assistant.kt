package com.craftflowtechnologies.meetingmind.ai.assistant

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiFunctionCall
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.cloud.AiStreamEvent
import com.craftflowtechnologies.meetingmind.ai.cloud.ThinkingLevel
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.common.isRetryable
import com.craftflowtechnologies.meetingmind.ai.faith.Prompts
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.ai.routing.ResolvedLanguageModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    LIST_TASKS("list_tasks", Kind.READ, "{}", "Open tasks from the person's task list, with ids, due dates and people."),
    LIST_UPCOMING_REMINDERS("list_upcoming_reminders", Kind.READ, "{}", "Upcoming scheduled task reminders and due items sorted chronologically."),
    CREATE_TASK("create_task", Kind.WRITE, "{\"title\": string, \"due\": \"YYYY-MM-DD\"|null, \"remind_at\": \"YYYY-MM-DDTHH:MM\"|null, \"person\": string|null, \"kind\": \"task\"|\"apply\"|\"prayer\"|\"follow_up\"|null}", "Add a task (with an optional reminder)."),
    COMPLETE_TASK("complete_task", Kind.WRITE, "{\"task_id\": string}", "Mark an open task (an id from list_tasks) as completed."),
    CREATE_NOTE("create_note", Kind.WRITE, "{\"title\": string, \"markdown\": string}", "Create a new note."),
    LIST_PRAYER_REQUESTS("list_prayer_requests", Kind.READ, "{}", "The person's open prayer requests from their prayer list, with ids."),
    CREATE_PRAYER_REQUEST("create_prayer_request", Kind.WRITE, "{\"title\": string, \"markdown\": string|null}", "Add a new prayer request to the person's prayer list."),
    MARK_PRAYER_ANSWERED("mark_prayer_answered", Kind.WRITE, "{\"request_id\": string}", "Mark an open prayer request (an id from list_prayer_requests) as answered."),
    LIST_COURSES("list_courses", Kind.READ, "{}", "The student's learning courses, subject domains, lecture counts, and due reviews."),
    GET_STUDY_CONCEPTS("get_study_concepts", Kind.READ, "{\"query\": string|null}", "Key concepts, definitions, formulas, exam importance, and mastery states from the open lecture or across study sessions."),
    CREATE_FLASHCARDS("create_flashcards", Kind.WRITE, "{\"cards\": [{\"prompt\": string, \"answer\": string, \"type\": \"RECALL\"|\"WORKED_PROBLEM\"|\"TEACH_BACK\"}], \"topic\": string|null}", "Create active-recall flashcards or practice questions in the open lecture or a topic deck."),
    CREATE_COURSE("create_course", Kind.WRITE, "{\"name\": string, \"code\": string|null, \"domain\": string|null}", "Create a new learning course (and link the open lecture note if any)."),
    CREATE_EXAM_GOAL("create_exam_goal", Kind.WRITE, "{\"title\": string, \"days_from_now\": string|null, \"target_grade\": string|null}", "Create an upcoming exam goal countdown.");

    enum class Kind { READ, WRITE, CONFIRM }

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id.trim().lowercase() }

        fun describe(tools: Collection<AssistantTool>) = tools.joinToString("\n") { "- ${it.id} ${it.signature}: ${it.doc}" }

        /** Official Gemini `tools: [{ functionDeclarations: [...] }]` JSON payload for native tool calling. */
        fun nativeDeclarationsJson(tools: Collection<AssistantTool>): String {
            val decls = JSONArray()
            tools.forEach { t -> decls.put(declarationFor(t)) }
            return JSONArray().put(JSONObject().put("functionDeclarations", decls)).toString()
        }

        private fun declarationFor(tool: AssistantTool): JSONObject {
            val props = JSONObject()
            val required = JSONArray()
            fun str(name: String, desc: String, req: Boolean = false) {
                props.put(name, JSONObject().put("type", "string").put("description", desc))
                if (req) required.put(name)
            }
            fun strArr(name: String, desc: String, req: Boolean = false) {
                props.put(name, JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", desc))
                if (req) required.put(name)
            }
            when (tool) {
                READ_NOTE, LIST_PRAYER_REQUESTS, LIST_COURSES, LIST_TASKS, LIST_UPCOMING_REMINDERS -> Unit
                SEARCH_NOTES -> str("query", "Keywords to search in the user's other notes.", req = true)
                READ_TRANSCRIPT -> str("query", "Topic or question to focus on, or omit for an overview.")
                GET_STUDY_CONCEPTS -> str("query", "Optional topic, concept name, or course filter.")
                GET_VERSES -> {
                    str("reference", "Scripture reference such as John 15:1-8.", req = true)
                    str("translation", "Optional Bible translation abbreviation.")
                }
                CROSS_REFERENCES, COMPARE_TRANSLATIONS, ORIGINAL, INSERT_SCRIPTURE ->
                    str("reference", "Scripture reference such as Romans 8:28.", req = true)
                COMMENTARY -> {
                    str("reference", "Scripture reference.", req = true)
                    str("source", "Optional commentary id: matthew-henry, jamieson-fausset-brown, adam-clarke, john-gill, or tyndale.")
                }
                INSERT_BLOCKS -> {
                    str("markdown", "Clean Markdown blocks to insert (never full quoted Bible verse text).", req = true)
                    str("after_block_id", "Optional target block id to insert after; omit to append.")
                }
                REPLACE_BLOCKS -> {
                    strArr("block_ids", "Existing block ids to replace.", req = true)
                    str("markdown", "Replacement Markdown content.", req = true)
                }
                DELETE_BLOCKS -> strArr("block_ids", "Existing block ids to remove.", req = true)
                CREATE_TASK -> {
                    str("title", "Short actionable task title.", req = true)
                    str("due", "Optional due date YYYY-MM-DD.")
                    str("remind_at", "Optional reminder ISO timestamp YYYY-MM-DDTHH:MM.")
                    str("person", "Optional owner or person involved.")
                    str("kind", "Optional task kind: task, apply, prayer, or follow_up.")
                }
                COMPLETE_TASK -> str("task_id", "Id returned by list_tasks.", req = true)
                CREATE_NOTE -> {
                    str("title", "Title for the new note.", req = true)
                    str("markdown", "Body content in clean Markdown.", req = true)
                }
                CREATE_PRAYER_REQUEST -> {
                    str("title", "Short prayer request title.", req = true)
                    str("markdown", "Optional details in Markdown.")
                }
                MARK_PRAYER_ANSWERED -> str("request_id", "Id returned by list_prayer_requests.", req = true)
                CREATE_FLASHCARDS -> {
                    val cardProps = JSONObject()
                        .put("prompt", JSONObject().put("type", "string").put("description", "Question or active-recall prompt."))
                        .put("answer", JSONObject().put("type", "string").put("description", "Model answer or worked explanation."))
                        .put("type", JSONObject().put("type", "string").put("description", "RECALL, WORKED_PROBLEM, or TEACH_BACK."))
                    props.put(
                        "cards",
                        JSONObject()
                            .put("type", "array")
                            .put("description", "Flashcards or practice questions to add.")
                            .put("items", JSONObject().put("type", "object").put("properties", cardProps))
                    )
                    str("topic", "Optional topic title if no lecture note is open.")
                }
                CREATE_COURSE -> {
                    str("name", "Course name, e.g. Linear Algebra.", req = true)
                    str("code", "Optional course code, e.g. MATH 204.")
                    str("domain", "Optional subject domain: MATH_QUANT, CS_PROGRAMMING, SCIENCE_LAB, MEDICINE_HEALTH, LAW_CASE, BUSINESS_FINANCE, HUMANITIES_ESSAY, LANGUAGE_FLUENCY, EXAM_CERT, or GENERAL_ACADEMIC.")
                }
                CREATE_EXAM_GOAL -> {
                    str("title", "Exam title, e.g. Midterm Exam.", req = true)
                    str("days_from_now", "Optional days until the exam, e.g. 14.")
                    str("target_grade", "Optional target grade, e.g. A.")
                }
            }
            val schema = JSONObject().put("type", "object").put("properties", props)
            if (required.length() > 0) schema.put("required", required)
            return JSONObject()
                .put("name", tool.id)
                .put("description", tool.doc)
                .put("parameters", schema)
        }
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
    val today: String = "",
    val learning: Boolean = false,
    val subjectDomain: String? = null
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

/** Live execution telemetry for a single tool invocation shown as a status pill. */
data class AssistantToolStep(
    val id: String,
    val label: String,
    val state: State = State.RUNNING
) {
    enum class State { RUNNING, DONE, FAILED }
}

data class AssistantMessage(
    val fromUser: Boolean,
    val text: String,
    val activity: List<String> = emptyList(),
    val actions: List<AssistantAction> = emptyList(),
    val error: Boolean = false,
    /** Only meaningful when [error]: whether asking again could plausibly help. False means the
     * person needs to change something (a key, a quota, a setting) before trying again. */
    val retryable: Boolean = true,
    /** Which service answered ("Gemini" or "DeepSeek"), for subtle provenance. */
    val provider: String? = null,
    /** Collapsible reasoning trace when the model emits thinking deltas. */
    val thinking: String? = null,
    /** Live tool execution telemetry pills (with running/done/failed indicators). */
    val toolSteps: List<AssistantToolStep> = emptyList(),
    /** True while SSE response tokens are still arriving. */
    val streaming: Boolean = false
)

/** Live UI snapshot emitted by [AssistantEngine.replyStream]. */
data class AssistantTurnState(
    val message: AssistantMessage,
    val statusLabel: String = "Thinking",
    val done: Boolean = false
)

class AssistantEngine(
    private val transport: GeminiTransport,
    private val host: AssistantHost,
    private val maxRounds: Int = 5,
    private val model: String = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
    private val localModelProvider: (suspend () -> ResolvedLanguageModel?)? = null,
    private val forceLocal: Boolean = false
) {
    fun scopedTools(): List<AssistantTool> {
        val scope = host.scope
        return AssistantTool.entries.filter { t ->
            when (t) {
                AssistantTool.READ_NOTE, AssistantTool.INSERT_BLOCKS, AssistantTool.REPLACE_BLOCKS, AssistantTool.DELETE_BLOCKS -> scope.noteId != null
                AssistantTool.INSERT_SCRIPTURE -> scope.noteId != null && !scope.learning
                AssistantTool.READ_TRANSCRIPT -> scope.meetingId != null
                AssistantTool.LIST_PRAYER_REQUESTS, AssistantTool.CREATE_PRAYER_REQUEST, AssistantTool.MARK_PRAYER_ANSWERED -> scope.faith
                AssistantTool.GET_VERSES, AssistantTool.CROSS_REFERENCES, AssistantTool.COMMENTARY, AssistantTool.COMPARE_TRANSLATIONS, AssistantTool.ORIGINAL -> !scope.learning || scope.faith
                AssistantTool.LIST_COURSES, AssistantTool.GET_STUDY_CONCEPTS, AssistantTool.CREATE_FLASHCARDS, AssistantTool.CREATE_COURSE, AssistantTool.CREATE_EXAM_GOAL -> scope.learning
                else -> true
            }
        }
    }

    fun systemPrompt(): String = buildSystemPrompt(nativeTools = false)

    private fun buildSystemPrompt(nativeTools: Boolean): String {
        val scope = host.scope
        val parts = mutableListOf<String>()
        if (scope.faith) parts += Prompts.faithContract
        parts += Prompts.get(if (scope.faith) "faith_assistant" else "notes_assistant").system()
        if (scope.sermon) parts += Prompts.get("sermon_study").system()
        if (scope.learning) {
            val domainHint = scope.subjectDomain?.let { "Subject domain: $it. " }.orEmpty()
            parts += "Learning Tutor Mode:\n" +
                "${domainHint}You are an expert Socratic academic tutor and study partner grounded in the student's recorded lectures, courses, study concepts, and formulas.\n" +
                "- Always ground your answers in the student's actual lecture notes (`read_note`), recorded lecture transcript (`read_transcript`), courses (`list_courses`), and extracted concepts (`get_study_concepts`).\n" +
                "- When unpacking a concept or formula, explain: (1) core intuition in plain language, (2) step-by-step derivation or mechanism, (3) exam traps or edge cases, and (4) a quick active-recall check question.\n" +
                "- When asked to quiz the student or generate flashcards, use `create_flashcards` to save real flashcards into their Practice Arena, and ask one Socratic question at a time in chat.\n" +
                "- When asked to organize a recorded lesson into a course, use `create_course`."
        }
        val tools = scopedTools()
        parts += if (nativeTools) {
            "Execution rules:\n" +
                "- Chain independent read calls together in one turn to answer quickly.\n" +
                "- When a note outline isn't enough to answer accurately, read the note or transcript first.\n" +
                "- Never mention internal block ids or raw JSON in your answer; format replies in clean Markdown.\n" +
                "- ${com.craftflowtechnologies.meetingmind.core.notes.AiMarkdown.MATH_AND_CHEMISTRY_RULE}\n" +
                "- Cite timestamps like [mm:ss] when referencing recordings."
        } else {
            "Tools:\n" + AssistantTool.describe(tools) +
                "\n\nExecution rules:\n" +
                "- Chain independent read calls together in one turn to answer quickly.\n" +
                "- When a note outline isn't enough to answer accurately, read the note or transcript first.\n" +
                "- Never mention internal block ids or raw JSON in \"say\"; format \"say\" in clean Markdown.\n" +
                "- In Markdown you write: ${com.craftflowtechnologies.meetingmind.core.notes.AiMarkdown.MATH_AND_CHEMISTRY_RULE}\n\n" +
                "Reply with one JSON object only: {\"say\": string, \"calls\": [{\"tool\": string, \"args\": object}]}. " +
                "Use \"calls\": [] when you are done. Results of read tools come back to you; write tools are applied by the app."
        }
        return parts.joinToString("\n\n")
    }

    /**
     * Streams one assistant turn: live thinking trace, token deltas, concurrent read-tool telemetry,
     * write action cards, and automatic fallback for legacy JSON-envelope responders.
     */
    fun replyStream(history: List<AssistantMessage>, message: String): Flow<AssistantTurnState> = flow {
        val scope = host.scope
        val tools = scopedTools()
        val toolsJson = AssistantTool.nativeDeclarationsJson(tools)
        val system = buildSystemPrompt(nativeTools = true)
        val outline = if (scope.noteId != null) host.outline() else ""

        val legacyContext = buildString {
            append("Today: ${scope.today}\n")
            append("Scope: ${scope.kindLabel} — \"${scope.title}\"\n")
            if (scope.noteId != null) append("Note outline:\n$outline\n")
            if (history.isNotEmpty()) {
                append("\nConversation so far:\n")
                history.takeLast(12).forEach { m -> append(if (m.fromUser) "Person: " else "You: ").append(m.text.take(1500)).append('\n') }
            }
            append("\nPerson: $message\n")
        }

        val contents = JSONArray()
        history.takeLast(12).forEach { m ->
            contents.put(
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text.take(1500))))
            )
        }
        val initialUserText = buildString {
            append("Today: ${scope.today}\n")
            append("Scope: ${scope.kindLabel} — \"${scope.title}\"\n")
            if (scope.noteId != null) append("Note outline:\n$outline\n\n")
            append(message)
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", initialUserText))))

        val activity = mutableListOf<String>()
        val actions = mutableListOf<AssistantAction>()
        val toolSteps = mutableListOf<AssistantToolStep>()
        val thinkingAccum = StringBuilder()
        val results = StringBuilder()
        var finalSay = ""
        var currentStatus = "Thinking"

        fun snapshot(
            text: String = finalSay,
            streaming: Boolean = true,
            done: Boolean = false,
            error: Boolean = false,
            retryable: Boolean = true,
            providerOverride: String? = null
        ): AssistantTurnState {
            val shownText = if (done && !error) {
                val guarded = if (scope.faith) FaithGuard.clean(text) else text
                guarded.ifBlank { if (actions.isNotEmpty()) "Done." else "I'm not sure how to help with that — could you say a bit more?" }
            } else text
            return AssistantTurnState(
                message = AssistantMessage(
                    fromUser = false,
                    text = shownText,
                    activity = activity.toList(),
                    actions = actions.toList(),
                    error = error,
                    retryable = retryable,
                    provider = providerOverride ?: com.craftflowtechnologies.meetingmind.ai.cloud.DeepSeek.lastProvider,
                    thinking = thinkingAccum.toString().takeIf { it.isNotBlank() },
                    toolSteps = toolSteps.toList(),
                    streaming = streaming
                ),
                statusLabel = currentStatus,
                done = done
            )
        }

        emit(snapshot(text = "", streaming = true))

        val canUseCloud = !forceLocal && transport.refreshConfigured()
        if (!canUseCloud) {
            val local = localModelProvider?.invoke()
            if (local == null) {
                emit(
                    snapshot(
                        text = if (forceLocal) {
                            "This note stays on your device, and no on-device language model is installed yet. Download one in Settings → AI Engine."
                        } else {
                            "No AI model is ready yet. Enable Internet mode or download an on-device model in Settings → AI Engine."
                        },
                        streaming = false,
                        done = true,
                        error = true,
                        retryable = false
                    )
                )
                return@flow
            }
            runLocalRounds(
                local = local,
                tools = tools,
                scope = scope,
                outline = outline,
                history = history,
                message = message,
                activity = activity,
                actions = actions,
                toolSteps = toolSteps,
                results = results,
                onStatus = { currentStatus = it },
                onSay = { finalSay = it },
                emitSnapshot = { text, streaming, done, err ->
                    emit(snapshot(text = text, streaming = streaming, done = done, error = err, providerOverride = "On-device (${local.modelId})"))
                }
            )
            return@flow
        }

        for (round in 1..maxRounds) {
            val isLastRound = round == maxRounds
            val closingNote = if (isLastRound && results.isNotEmpty()) {
                "\nTool results so far:\n$results\nThis is your final turn — answer the person directly now using what you gathered, with \"calls\": []."
            } else if (results.isNotEmpty()) {
                "\nTool results so far:\n$results\nContinue."
            } else ""
            val req = GeminiRequest(
                modelId = model,
                systemInstruction = system,
                prompt = legacyContext + closingNote,
                jsonOutput = false,
                timeoutMs = 60_000L,
                temperature = 0.3f,
                thinkingLevel = ThinkingLevel.LOW,
                includeThoughts = true,
                contentsJson = contents.toString(),
                toolsJson = if (isLastRound) null else toolsJson,
                toolMode = if (isLastRound) "NONE" else "AUTO"
            )

            var streamFailure: AiResult<Nothing>? = null
            var rawRoundText = ""
            var nativeCalls = emptyList<GeminiFunctionCall>()
            val roundVisibleText = StringBuilder()
            var suppressedEnvelope = false

            transport.stream(req).collect { ev ->
                when (ev) {
                    is AiStreamEvent.ThinkingDelta -> {
                        thinkingAccum.append(ev.text)
                        emit(snapshot(text = if (roundVisibleText.isNotEmpty()) roundVisibleText.toString() else finalSay))
                    }
                    is AiStreamEvent.TextDelta -> {
                        rawRoundText += ev.text
                        if (!suppressedEnvelope) {
                            if (roundVisibleText.isEmpty() && ev.text.trimStart().startsWith("{")) {
                                suppressedEnvelope = true
                            } else {
                                roundVisibleText.append(ev.text)
                                emit(snapshot(text = roundVisibleText.toString()))
                            }
                        }
                    }
                    is AiStreamEvent.FunctionCalls -> {
                        nativeCalls = nativeCalls + ev.calls
                    }
                    is AiStreamEvent.Completed -> {
                        if (rawRoundText.isEmpty()) rawRoundText = ev.text
                        if (ev.functionCalls.isNotEmpty()) nativeCalls = ev.functionCalls
                    }
                    is AiStreamEvent.Failed -> streamFailure = ev.error
                }
            }

            if (streamFailure != null) {
                val err = streamFailure!!
                val local = if (round == 1 && finalSay.isBlank() && actions.isEmpty()) localModelProvider?.invoke() else null
                if (local != null) {
                    runLocalRounds(
                        local = local,
                        tools = tools,
                        scope = scope,
                        outline = outline,
                        history = history,
                        message = message,
                        activity = activity,
                        actions = actions,
                        toolSteps = toolSteps,
                        results = results,
                        onStatus = { currentStatus = it },
                        onSay = { finalSay = it },
                        emitSnapshot = { text, streaming, done, errFlag ->
                            emit(snapshot(text = text, streaming = streaming, done = done, error = errFlag, providerOverride = "On-device (${local.modelId})"))
                        }
                    )
                    return@flow
                }
                emit(
                    snapshot(
                        text = err.describeFailure() ?: "The assistant couldn't answer just now.",
                        streaming = false,
                        done = true,
                        error = true,
                        retryable = err.isRetryable()
                    )
                )
                return@flow
            }

            // Interpret either native functionCalls OR legacy {"say":...,"calls":[...]} envelope
            val parsedCalls: List<Pair<ToolCall, String?>>
            val unknownNames = mutableListOf<String>()
            if (nativeCalls.isNotEmpty()) {
                if (roundVisibleText.isNotBlank()) finalSay = roundVisibleText.toString().trim()
                val mapped = mutableListOf<Pair<ToolCall, String?>>()
                for (nc in nativeCalls.take(6)) {
                    val t = AssistantTool.byId(nc.name)
                    if (t == null) unknownNames += nc.name
                    else mapped += ToolCall(t, runCatching { JSONObject(nc.argsJson) }.getOrDefault(JSONObject())) to nc.id
                }
                parsedCalls = mapped
            } else if (suppressedEnvelope || rawRoundText.trimStart().startsWith("{")) {
                val legacy = AssistantProtocol.parse(rawRoundText)
                if (legacy.say.isNotBlank()) finalSay = legacy.say
                unknownNames += legacy.unknownTools
                parsedCalls = legacy.calls.take(6).map { it to null }
            } else {
                if (rawRoundText.isNotBlank()) finalSay = rawRoundText.trim()
                parsedCalls = emptyList()
            }

            unknownNames.forEach { results.append("[error] unknown tool '$it'\n") }
            if (parsedCalls.isEmpty()) break

            // Record model turn in structured conversation history
            val modelParts = JSONArray()
            if (finalSay.isNotBlank()) modelParts.put(JSONObject().put("text", finalSay))
            parsedCalls.forEach { (c, callId) ->
                val fc = JSONObject().put("name", c.tool.id).put("args", c.args)
                callId?.let { fc.put("id", it) }
                modelParts.put(JSONObject().put("functionCall", fc))
            }
            contents.put(JSONObject().put("role", "model").put("parts", modelParts))

            // Add running chips before execution
            val pendingStepIds = parsedCalls.mapIndexed { idx, (c, _) ->
                val stepId = "step_${round}_$idx"
                toolSteps += AssistantToolStep(stepId, labelForRunningTool(c), AssistantToolStep.State.RUNNING)
                stepId
            }
            currentStatus = toolSteps.lastOrNull()?.label ?: "Running tools"
            emit(snapshot(text = finalSay))

            // Execute read tools concurrently, then write/confirm tools sequentially
            val readIndices = parsedCalls.indices.filter { parsedCalls[it].first.tool.kind == AssistantTool.Kind.READ }
            val readOutcomes: Map<Int, ToolResult> = if (readIndices.isEmpty()) emptyMap() else coroutineScope {
                readIndices.map { i ->
                    async {
                        val call = parsedCalls[i].first
                        i to runCatching { host.read(call) }.getOrElse { ToolResult("Couldn't ${call.tool.id}", "error: ${it.message}", ok = false) }
                    }
                }.awaitAll().toMap()
            }

            val functionResponseParts = JSONArray()
            var needsAnotherRound = false
            for (i in parsedCalls.indices) {
                val (call, callId) = parsedCalls[i]
                val stepId = pendingStepIds[i]
                val (resTool, action) = if (call.tool.kind == AssistantTool.Kind.READ) {
                    needsAnotherRound = true
                    readOutcomes.getValue(i) to null
                } else {
                    val pair = runCatching { host.write(call) }.getOrElse {
                        ToolResult("Couldn't ${call.tool.id}", "error: ${it.message}", ok = false) to
                            AssistantAction("err", "Couldn't ${call.tool.id.replace('_', ' ')}", state = AssistantAction.State.DISCARDED)
                    }
                    if (!pair.first.ok) needsAnotherRound = true
                    pair
                }

                if (action != null && resTool.ok) actions += action
                activity += resTool.chip
                currentStatus = resTool.chip
                val sIdx = toolSteps.indexOfFirst { it.id == stepId }
                if (sIdx >= 0) {
                    toolSteps[sIdx] = AssistantToolStep(
                        id = stepId,
                        label = resTool.chip,
                        state = if (resTool.ok) AssistantToolStep.State.DONE else AssistantToolStep.State.FAILED
                    )
                }

                results.append("[${call.tool.id} ${call.args}]\n${resTool.text.take(6000)}\n\n")
                val respObj = JSONObject().put("name", call.tool.id).put("response", JSONObject().put("ok", resTool.ok).put("result", resTool.text.take(6000)))
                callId?.let { respObj.put("id", it) }
                functionResponseParts.put(JSONObject().put("functionResponse", respObj))
            }
            contents.put(JSONObject().put("role", "user").put("parts", functionResponseParts))
            emit(snapshot(text = finalSay))

            if (!needsAnotherRound && finalSay.isNotBlank()) break
        }

        emit(snapshot(text = finalSay, streaming = false, done = true))
    }

    private suspend fun runLocalRounds(
        local: ResolvedLanguageModel,
        tools: List<AssistantTool>,
        scope: AssistantScope,
        outline: String,
        history: List<AssistantMessage>,
        message: String,
        activity: MutableList<String>,
        actions: MutableList<AssistantAction>,
        toolSteps: MutableList<AssistantToolStep>,
        results: StringBuilder,
        onStatus: (String) -> Unit,
        onSay: (String) -> Unit,
        emitSnapshot: suspend (text: String, streaming: Boolean, done: Boolean, error: Boolean) -> Unit
    ) {
        val localMaxRounds = maxRounds.coerceAtMost(3)
        val systemPrompt = buildString {
            if (scope.faith) appendLine(Prompts.faithContract.trim())
            appendLine(Prompts.get(if (scope.faith) "faith_assistant" else "notes_assistant").system())
            if (scope.learning) appendLine("Tutor mode: ground answers in lecture notes/concepts and explain clearly.")
            appendLine("Available tools (JSON):")
            tools.forEach { t -> appendLine("- ${t.id} ${t.signature}: ${t.doc}") }
            appendLine("Reply ONLY with JSON: {\"say\": string, \"calls\": [{\"tool\": string, \"args\": object}]}")
            append("When no tool is needed, reply with \"calls\": [] and your answer in \"say\".")
        }
        val compactHistory = history.takeLast(4).joinToString("\n") { m ->
            (if (m.fromUser) "User: " else "Assistant: ") + m.text.take(350)
        }
        val compactOutline = outline.lines().take(30).joinToString("\n").take(2400)
        var latestSay = ""

        try {
            for (round in 1..localMaxRounds) {
                val isLast = round == localMaxRounds
                val prompt = buildString {
                    appendLine(systemPrompt)
                    appendLine("\nToday: ${scope.today}")
                    appendLine("Scope: ${scope.kindLabel} — \"${scope.title}\"")
                    if (scope.noteId != null && compactOutline.isNotBlank()) {
                        appendLine("Note outline:\n$compactOutline")
                    }
                    if (compactHistory.isNotBlank()) appendLine("\nRecent conversation:\n$compactHistory")
                    appendLine("\nUser: $message")
                    if (results.isNotEmpty()) {
                        appendLine("\nTool results so far:\n${results.toString().take(3200)}")
                        if (isLast) appendLine("Final turn: answer the user directly in \"say\" with \"calls\": [].")
                    }
                }
                val gen = local.languageModel.generate(prompt, maxOutputTokens = 640)
                if (gen !is AiResult.Success) {
                    val errText = gen.describeFailure() ?: "On-device model couldn't answer just now."
                    emitSnapshot(errText, false, true, true)
                    return
                }
                val turn = AssistantProtocol.parse(gen.value)
                if (turn.say.isNotBlank()) {
                    latestSay = turn.say
                    onSay(latestSay)
                }
                if (turn.calls.isEmpty() || isLast) break

                val calls = turn.calls.take(4)
                val stepIds = calls.mapIndexed { idx, c ->
                    val stepId = "local_${round}_$idx"
                    toolSteps += AssistantToolStep(stepId, labelForRunningTool(c), AssistantToolStep.State.RUNNING)
                    stepId
                }
                onStatus(toolSteps.lastOrNull()?.label ?: "Running tools")
                emitSnapshot(latestSay, true, false, false)

                val readIndices = calls.indices.filter { calls[it].tool.kind == AssistantTool.Kind.READ }
                val readOutcomes: Map<Int, ToolResult> = if (readIndices.isEmpty()) emptyMap() else coroutineScope {
                    readIndices.map { i ->
                        async {
                            val call = calls[i]
                            i to runCatching { host.read(call) }.getOrElse { ToolResult("Couldn't ${call.tool.id}", "error: ${it.message}", ok = false) }
                        }
                    }.awaitAll().toMap()
                }
                var needsAnother = false
                for (i in calls.indices) {
                    val call = calls[i]
                    val stepId = stepIds[i]
                    val (resTool, action) = if (call.tool.kind == AssistantTool.Kind.READ) {
                        needsAnother = true
                        readOutcomes.getValue(i) to null
                    } else {
                        val pair = runCatching { host.write(call) }.getOrElse {
                            ToolResult("Couldn't ${call.tool.id}", "error: ${it.message}", ok = false) to
                                AssistantAction("err", "Couldn't ${call.tool.id.replace('_', ' ')}", state = AssistantAction.State.DISCARDED)
                        }
                        if (!pair.first.ok) needsAnother = true
                        pair
                    }
                    if (action != null && resTool.ok) actions += action
                    activity += resTool.chip
                    onStatus(resTool.chip)
                    val sIdx = toolSteps.indexOfFirst { it.id == stepId }
                    if (sIdx >= 0) {
                        toolSteps[sIdx] = AssistantToolStep(stepId, resTool.chip, if (resTool.ok) AssistantToolStep.State.DONE else AssistantToolStep.State.FAILED)
                    }
                    results.append("[${call.tool.id}]\n${resTool.text.take(2000)}\n\n")
                }
                emitSnapshot(latestSay, true, false, false)
                if (!needsAnother && latestSay.isNotBlank()) break
            }
            emitSnapshot(latestSay, false, true, false)
        } finally {
            runCatching { com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager.release() }
        }
    }

    private fun labelForRunningTool(call: ToolCall): String = when (call.tool) {
        AssistantTool.READ_NOTE -> "Reading note…"
        AssistantTool.SEARCH_NOTES -> "Searching notes…"
        AssistantTool.READ_TRANSCRIPT -> "Reading transcript…"
        AssistantTool.GET_VERSES -> "Reading ${call.str("reference") ?: "passage"}…"
        AssistantTool.CROSS_REFERENCES -> "Checking cross references…"
        AssistantTool.COMMENTARY -> "Checking commentary…"
        AssistantTool.COMPARE_TRANSLATIONS -> "Comparing translations…"
        AssistantTool.ORIGINAL -> "Reading original language…"
        AssistantTool.LIST_PRAYER_REQUESTS -> "Checking prayer list…"
        AssistantTool.INSERT_BLOCKS -> "Updating note…"
        AssistantTool.INSERT_SCRIPTURE -> "Adding scripture…"
        AssistantTool.REPLACE_BLOCKS -> "Preparing edit…"
        AssistantTool.DELETE_BLOCKS -> "Preparing removal…"
        AssistantTool.LIST_TASKS -> "Checking tasks…"
        AssistantTool.LIST_UPCOMING_REMINDERS -> "Checking reminders…"
        AssistantTool.CREATE_TASK -> "Adding task…"
        AssistantTool.COMPLETE_TASK -> "Completing task…"
        AssistantTool.CREATE_NOTE -> "Creating note…"
        AssistantTool.CREATE_PRAYER_REQUEST -> "Adding prayer…"
        AssistantTool.MARK_PRAYER_ANSWERED -> "Updating prayer…"
        AssistantTool.LIST_COURSES -> "Checking courses…"
        AssistantTool.GET_STUDY_CONCEPTS -> "Reading study concepts…"
        AssistantTool.CREATE_FLASHCARDS -> "Creating flashcards…"
        AssistantTool.CREATE_COURSE -> "Creating course…"
        AssistantTool.CREATE_EXAM_GOAL -> "Setting exam goal…"
    }

    /**
     * One reply to [message]: collects [replyStream] for synchronous callers & tests.
     * [onProgress] gets each tool/status label as it happens.
     */
    suspend fun reply(history: List<AssistantMessage>, message: String, onProgress: (String) -> Unit = {}): AssistantMessage {
        var latest = AssistantMessage(fromUser = false, text = "")
        var lastStatus = ""
        replyStream(history, message).collect { snap ->
            latest = snap.message
            if (snap.statusLabel != lastStatus) {
                lastStatus = snap.statusLabel
                onProgress(snap.statusLabel)
            }
        }
        return latest
    }
}

/** Removes sentences in the assistant's own voice that claim to speak for God. The actual check
 * lives in [com.craftflowtechnologies.meetingmind.ai.faith.FaithAuthorityGuard], shared with
 * [com.craftflowtechnologies.meetingmind.ai.faith.AskSermon]'s sermon-recording grounding, so the
 * two never drift apart on which phrasing counts as a violation. */
object FaithGuard {
    fun violations(text: String): List<String> = com.craftflowtechnologies.meetingmind.ai.faith.FaithAuthorityGuard.violations(text)
    fun clean(text: String): String = com.craftflowtechnologies.meetingmind.ai.faith.FaithAuthorityGuard.clean(text)
}
