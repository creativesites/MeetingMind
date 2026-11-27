package com.craftflowtechnologies.meetingmind.ai.automation

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory
import com.craftflowtechnologies.meetingmind.ai.routing.ResilientLanguageModel
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.FeatureGate
import com.craftflowtechnologies.meetingmind.core.billing.TieringMode
import com.craftflowtechnologies.meetingmind.core.billing.TieringModeStore
import com.craftflowtechnologies.meetingmind.core.database.ActionItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import com.craftflowtechnologies.meetingmind.core.work.WorkPrivacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AutopilotRecipe(
    val id: String,
    val label: String,
    val badge: String,
    val subtitle: String,
    val deliverableLabel: String,
    val preferredTaskKind: TaskKind
) {
    EXECUTIVE_DEBRIEF(
        id = "executive_debrief",
        label = "Executive Debrief",
        badge = "Work & Team",
        subtitle = "TL;DR, binding decisions, risks, follow-up message & task queue",
        deliverableLabel = "Follow-Up Message",
        preferredTaskKind = TaskKind.FOLLOW_UP
    ),
    CLIENT_CRM_LOG(
        id = "client_crm_log",
        label = "Client & Deal Sync",
        badge = "CRM & Sales",
        subtitle = "Stakeholder signals, objections, next steps & recap note",
        deliverableLabel = "Client Follow-Up Draft",
        preferredTaskKind = TaskKind.FOLLOW_UP
    ),
    STUDY_FLASHCARD_DRILL(
        id = "study_flashcard_drill",
        label = "Study Recall Pack",
        badge = "Learning",
        subtitle = "Core concepts, likely exam traps, review sheet & study tasks",
        deliverableLabel = "Active Recall Sheet",
        preferredTaskKind = TaskKind.TASK
    ),
    SERMON_SMALL_GROUP_PACK(
        id = "sermon_small_group_pack",
        label = "Small Group Pack",
        badge = "Faith & Church",
        subtitle = "Big Idea, 4 discussion questions, Scripture refs & weekly steps",
        deliverableLabel = "Group Leader Guide",
        preferredTaskKind = TaskKind.APPLY
    );

    companion object {
        /** The recipes offered inside Work (spec §10 Autopilot): the others stay in their own spaces. */
        val WORK_RECIPES: List<AutopilotRecipe> = listOf(EXECUTIVE_DEBRIEF, CLIENT_CRM_LOG)

        fun recommendedFor(workflow: RecordingType): AutopilotRecipe = when (workflow) {
            RecordingType.SERMON,
            RecordingType.BIBLE_STUDY,
            RecordingType.DEVOTIONAL,
            RecordingType.PRAYER_REQUEST,
            RecordingType.TESTIMONY -> SERMON_SMALL_GROUP_PACK

            RecordingType.LECTURE,
            RecordingType.RESEARCH -> STUDY_FLASHCARD_DRILL

            RecordingType.MEETING,
            RecordingType.INTERVIEW -> CLIENT_CRM_LOG

            else -> EXECUTIVE_DEBRIEF
        }
    }
}

data class AutopilotActionDraft(
    val title: String,
    val owner: String? = null,
    val dueHint: String? = null,
    val kind: TaskKind = TaskKind.TASK
)

data class AutopilotPreview(
    val sessionTitle: String,
    val workflow: RecordingType,
    val recommendedRecipe: AutopilotRecipe,
    val wordCount: Int,
    val existingOpenTasks: Int,
    val speakerCount: Int,
    val noteId: String?,
    val meetingId: String?,
    val notebookId: String?,
    val stayOnDeviceOnly: Boolean
)

data class AutopilotRunResult(
    val recipe: AutopilotRecipe,
    val headline: String,
    val summary: String,
    val keyOutcomes: List<String>,
    val actions: List<AutopilotActionDraft>,
    val deliverableTitle: String,
    val deliverableMarkdown: String,
    val targetNoteTitle: String,
    val engineLabel: String,
    val generatedAtMs: Long
)

data class AutopilotContext(
    val preview: AutopilotPreview,
    val sourceText: String,
    val unlocked: Boolean
)

data class AutopilotExecutionReport(
    val createdNoteId: String?,
    val createdTasks: Int,
    val result: AutopilotRunResult
)

/**
 * What Autopilot does with the tasks it finds (spec F-3). REVIEW (the default) puts them in the
 * recording's Wrap-up as unreviewed findings; nothing becomes a task until the person confirms it.
 * CREATE makes them tasks straight away, as it did before.
 */
enum class AutopilotTasksMode { REVIEW, CREATE }

/** Persistent trigger toggles for post-recording AI automation workflows. */
object AutopilotPreferences {
    private const val PREFS_NAME = "mm_autopilot_prefs"
    private const val KEY_AUTO_RUN = "auto_run_enabled"
    private const val KEY_TASKS_MODE = "tasks_mode"
    private const val KEY_AUTO_NOTE = "auto_create_note"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoRunEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_RUN, false)
    fun setAutoRunEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RUN, enabled).apply()
    }

    /** Defaults to REVIEW for everyone, including people who never opened the setting. */
    fun tasksMode(context: Context): AutopilotTasksMode =
        prefs(context).getString(KEY_TASKS_MODE, null)
            ?.let { runCatching { AutopilotTasksMode.valueOf(it) }.getOrNull() }
            ?: AutopilotTasksMode.REVIEW

    fun setTasksMode(context: Context, mode: AutopilotTasksMode) {
        prefs(context).edit().putString(KEY_TASKS_MODE, mode.name).apply()
    }

    /** True only when the person opted into CREATE. */
    fun shouldAutoCreateTasks(context: Context): Boolean = tasksMode(context) == AutopilotTasksMode.CREATE
    fun setAutoCreateTasks(context: Context, enabled: Boolean) {
        setTasksMode(context, if (enabled) AutopilotTasksMode.CREATE else AutopilotTasksMode.REVIEW)
    }

    fun shouldAutoSaveNote(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_NOTE, true)
    fun setAutoSaveNote(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_NOTE, enabled).apply()
    }
}

/**
 * Pure post-session workflow execution engine that runs across cloud Gemini and on-device local
 * LLMs via [LanguageModel].
 */
class PostSessionAutopilotEngine(
    private val model: LanguageModel,
    private val contextBudgetChars: Int = 24_000,
    private val clock: () -> Long = System::currentTimeMillis
) {

    suspend fun execute(
        recipe: AutopilotRecipe,
        sessionTitle: String,
        workflow: RecordingType,
        sourceText: String,
        engineLabel: String = "Autopilot"
    ): AiResult<AutopilotRunResult> {
        val clipped = sourceText.trim().take(contextBudgetChars)
        if (clipped.isBlank()) {
            return AiResult.Failed("Nothing to run Autopilot on yet — record or write a few notes first.")
        }
        val prompt = buildPrompt(recipe, sessionTitle, workflow, clipped)
        val raw = when (val res = model.generate(prompt, maxOutputTokens = 1600)) {
            is AiResult.Success -> res.value
            is AiResult.ModelUnavailable,
            is AiResult.DeviceUnsupported,
            is AiResult.InsufficientMemory,
            is AiResult.InsufficientStorage,
            is AiResult.Failed -> return res
        }
        val parsed = parseRunResult(recipe, raw, sessionTitle, engineLabel)
            ?: return AiResult.Failed("Autopilot returned an unreadable response. Try running the workflow again.")
        return AiResult.Success(parsed)
    }

    internal fun buildPrompt(
        recipe: AutopilotRecipe,
        sessionTitle: String,
        workflow: RecordingType,
        clippedSource: String
    ): String {
        val recipeInstructions = when (recipe) {
            AutopilotRecipe.EXECUTIVE_DEBRIEF -> """
                Recipe: Executive Debrief & Follow-Up Automation
                Produce:
                - headline: 1 crisp executive headline for what was decided or shifted
                - summary: 2-3 sentence executive summary grounded strictly in the session
                - key_outcomes: up to 5 concrete decisions, blockers, or alignments explicitly stated
                - action_items: concrete follow-up tasks from the session, each with owner and due_hint if stated
                - deliverable_title: ready-to-send team follow-up message subject
                - deliverable_markdown: crisp Slack/email-ready status update (TL;DR, Decisions, Owners & Next Steps)
                - note_title: concise archive note title
            """.trimIndent()

            AutopilotRecipe.CLIENT_CRM_LOG -> """
                Recipe: Client & Deal Sync Automation
                Produce:
                - headline: 1-line deal/stakeholder status headline
                - summary: 2-3 sentence stakeholder brief covering sentiment, needs, and trajectory
                - key_outcomes: up to 5 client signals, objections, requirements, or commercial agreements stated
                - action_items: concrete next steps owed by team or client, with owner and due_hint if mentioned
                - deliverable_title: client follow-up email subject line
                - deliverable_markdown: polished, factual client recap email ready to paste and send
                - note_title: concise CRM sync note title
            """.trimIndent()

            AutopilotRecipe.STUDY_FLASHCARD_DRILL -> """
                Recipe: Study & Active Recall Automation
                Produce:
                - headline: 1-line core thesis of the lecture or study session
                - summary: 2-3 sentence synthesis of the foundational concepts covered
                - key_outcomes: up to 5 core definitions, mechanisms, formulas, or likely exam traps mentioned
                - action_items: concrete study drills or weak-area reviews to complete next
                - deliverable_title: Active Recall Drill Sheet title
                - deliverable_markdown: 4 concise Q&A active-recall prompts with worked answers in clean Markdown (${com.craftflowtechnologies.meetingmind.core.notes.AiMarkdown.MATH_AND_CHEMISTRY_RULE})
                - note_title: concise Study Drill Pack note title
            """.trimIndent()

            AutopilotRecipe.SERMON_SMALL_GROUP_PACK -> """
                Recipe: Sermon & Small Group Leader Pack
                Faith Contract: cite Bible references (book chapter:verse) mentioned in the session only; NEVER invent or fabricate Bible verse wording from memory.
                Produce:
                - headline: The Big Idea of the message in one memorable sentence
                - summary: 2-3 sentence theological & pastoral summary grounded in the message
                - key_outcomes: up to 5 Scripture references, core truths, and pastor emphases stated
                - action_items: concrete weekly application steps or prayer commitments from the message
                - deliverable_title: Small Group Discussion Guide title
                - deliverable_markdown: Small Group guide with 4 discussion questions, Scripture references, and 2 prayer focuses
                - note_title: concise Small Group Pack title
            """.trimIndent()
        }

        return """
            $recipeInstructions

            STRICT GROUNDING RULE:
            Use ONLY facts, names, dates, decisions, Scripture references, and tasks actually present in the session material below. Never invent facts or assign people who were not named. Return valid JSON ONLY with NO markdown fences around the JSON.

            Session title: $sessionTitle (${workflow.displayName})
            Session material:
            $clippedSource

            Required JSON shape:
            {
              "headline": "...",
              "summary": "...",
              "key_outcomes": ["..."],
              "action_items": [{"title": "...", "owner": "", "due_hint": ""}],
              "deliverable_title": "...",
              "deliverable_markdown": "...",
              "note_title": "..."
            }
        """.trimIndent()
    }

    internal fun parseRunResult(
        recipe: AutopilotRecipe,
        rawResponse: String,
        sessionTitle: String,
        engineLabel: String
    ): AutopilotRunResult? {
        val json = extractJsonObject(rawResponse) ?: return null
        val headline = json.optString("headline").trim().ifEmpty { "${recipe.label}: $sessionTitle" }
        val summary = json.optString("summary").trim()
        val deliverableTitle = json.optString("deliverable_title").trim().ifEmpty { "${recipe.deliverableLabel} — $sessionTitle" }
        val deliverableMarkdown = json.optString("deliverable_markdown").trim().ifEmpty { summary }
        if (summary.isEmpty() && deliverableMarkdown.isEmpty()) return null

        val outcomes = parseStringList(json.optJSONArray("key_outcomes"))
        val actions = parseActionItems(json.optJSONArray("action_items"), recipe.preferredTaskKind)
        val noteTitle = json.optString("note_title").trim().ifEmpty { "${recipe.label}: $sessionTitle" }

        return AutopilotRunResult(
            recipe = recipe,
            headline = headline,
            summary = summary.ifEmpty { deliverableMarkdown.take(320) },
            keyOutcomes = outcomes,
            actions = actions,
            deliverableTitle = deliverableTitle,
            deliverableMarkdown = deliverableMarkdown,
            targetNoteTitle = noteTitle.take(96),
            engineLabel = engineLabel,
            generatedAtMs = clock()
        )
    }

    private fun parseStringList(array: JSONArray?): List<String> = buildList {
        for (i in 0 until (array?.length() ?: 0)) {
            val item = array?.optString(i)?.trim().orEmpty()
            if (item.isNotEmpty() && item != "null") add(item)
        }
    }

    private fun parseActionItems(array: JSONArray?, defaultKind: TaskKind): List<AutopilotActionDraft> = buildList {
        for (i in 0 until (array?.length() ?: 0)) {
            val obj = array?.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isEmpty() || title == "null") continue
            val owner = obj.optString("owner").trim().takeIf { it.isNotEmpty() && it != "null" }
            val dueHint = obj.optString("due_hint").trim().takeIf { it.isNotEmpty() && it != "null" }
            add(AutopilotActionDraft(title = title, owner = owner, dueHint = dueHint, kind = defaultKind))
        }
    }

    private fun extractJsonObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(trimmed.substring(start, end + 1)) }.getOrNull()
    }
}

/** Coordinates session gathering, plan gating, privacy routing, local/cloud execution, and app writes. */
object AutopilotWorkspace {

    fun isUnlocked(context: Context): Boolean =
        FeatureGate.can(Capability.AUTOMATION_RECIPES) ||
            TieringModeStore.current(context) == TieringMode.CLOSED_TESTING

    suspend fun gatherForMeeting(context: Context, meetingId: String): AutopilotContext? = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val meeting = db.meetingDao().getMeetingById(meetingId) ?: return@withContext null
        val linkedNote = meeting.noteId?.let { db.noteDao().getById(it) }
        val segments = db.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        val speakerCount = segments.mapNotNull { it.speakerName?.takeIf(String::isNotBlank) ?: it.speakerId }.distinct().size
        val sourceText = buildString {
            linkedNote?.plainText?.takeIf { it.isNotBlank() }?.let { appendLine("Notes:\n${it.take(6_000)}") }
            meeting.summaryText?.takeIf { it.isNotBlank() }?.let { appendLine("Summary:\n$it") }
            if (segments.isNotEmpty()) {
                appendLine("Transcript:")
                segments.forEach { seg ->
                    val spk = seg.speakerName?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
                    appendLine(spk + (seg.cleanedText ?: seg.text))
                }
            }
        }.trim()
        val workflow = runCatching { RecordingType.valueOf(meeting.recordingType) }.getOrDefault(RecordingType.GENERAL)
        val title = meeting.title.ifBlank { linkedNote?.title.orEmpty().ifBlank { "Untitled recording" } }
        val wordCount = sourceText.split(Regex("\\s+")).count { it.isNotBlank() }
        val openTasks = TaskRepository(db.taskDao(), db.peopleDao()).openTasks().count {
            it.meetingId == meetingId || (linkedNote != null && it.noteId == linkedNote.id)
        }
        val stayOnDevice = WorkPrivacy.mustStayOnDevice(context, meetingId = meetingId, noteId = linkedNote?.id)

        AutopilotContext(
            preview = AutopilotPreview(
                sessionTitle = title,
                workflow = workflow,
                recommendedRecipe = AutopilotRecipe.recommendedFor(workflow),
                wordCount = wordCount,
                existingOpenTasks = openTasks,
                speakerCount = speakerCount,
                noteId = linkedNote?.id,
                meetingId = meeting.id,
                notebookId = linkedNote?.notebookId,
                stayOnDeviceOnly = stayOnDevice
            ),
            sourceText = sourceText,
            unlocked = isUnlocked(context)
        )
    }

    suspend fun gatherForNote(context: Context, noteId: String): AutopilotContext? = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val note = db.noteDao().getById(noteId) ?: return@withContext null
        val linkedMeeting = db.noteDao().getMeetingsForNote(noteId).firstOrNull()
        val segments = linkedMeeting?.let { db.transcriptDao().getSegmentsForMeetingDirect(it.id) }.orEmpty()
        val speakerCount = segments.mapNotNull { it.speakerName?.takeIf(String::isNotBlank) ?: it.speakerId }.distinct().size
        val sourceText = buildString {
            if (note.plainText.isNotBlank()) appendLine("Notes:\n${note.plainText}")
            linkedMeeting?.summaryText?.takeIf { it.isNotBlank() }?.let { appendLine("Summary:\n$it") }
            if (segments.isNotEmpty()) {
                appendLine("Transcript:")
                segments.forEach { seg ->
                    val spk = seg.speakerName?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
                    appendLine(spk + (seg.cleanedText ?: seg.text))
                }
            }
        }.trim()
        val workflow = runCatching { RecordingType.valueOf(note.workflow) }.getOrDefault(RecordingType.GENERAL)
        val title = note.title.ifBlank { linkedMeeting?.title.orEmpty().ifBlank { "Untitled note" } }
        val wordCount = sourceText.split(Regex("\\s+")).count { it.isNotBlank() }
        val openTasks = TaskRepository(db.taskDao(), db.peopleDao()).openTasks().count {
            it.noteId == noteId || (linkedMeeting != null && it.meetingId == linkedMeeting.id)
        }
        val stayOnDevice = WorkPrivacy.mustStayOnDevice(context, meetingId = linkedMeeting?.id, noteId = noteId)

        AutopilotContext(
            preview = AutopilotPreview(
                sessionTitle = title,
                workflow = workflow,
                recommendedRecipe = AutopilotRecipe.recommendedFor(workflow),
                wordCount = wordCount,
                existingOpenTasks = openTasks,
                speakerCount = speakerCount,
                noteId = note.id,
                meetingId = linkedMeeting?.id,
                notebookId = note.notebookId,
                stayOnDeviceOnly = stayOnDevice
            ),
            sourceText = sourceText,
            unlocked = isUnlocked(context)
        )
    }

    suspend fun gatherLatestSession(context: Context): AutopilotContext? = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val latestMeeting = db.meetingDao().getMeetingsWithStatus(MeetingStatus.READY.name)
            .maxByOrNull { it.updatedAt }
        if (latestMeeting != null) {
            return@withContext gatherForMeeting(context, latestMeeting.id)
        }
        val latestNote = db.noteDao().getActiveOnce()
            .firstOrNull { it.plainText.isNotBlank() && it.workflow != RecordingType.DEVOTIONAL.name }
        latestNote?.let { gatherForNote(context, it.id) }
    }

    suspend fun runRecipe(
        context: Context,
        gathered: AutopilotContext,
        recipe: AutopilotRecipe
    ): AiResult<AutopilotRunResult> {
        if (!isUnlocked(context)) {
            return AiResult.ModelUnavailable(
                "pro_required",
                "Autopilot is included with BYOK, Pro and Church plans."
            )
        }

        val appContext = context.applicationContext
        val prefs = UserPreferencesManager(appContext)
        val requestedProfile = withContext(Dispatchers.IO) { prefs.preferencesFlow.first().processingProfile }
        val effectiveProfile = withContext(Dispatchers.IO) {
            when {
                gathered.preview.meetingId != null ->
                    WorkPrivacy.forMeeting(appContext, gathered.preview.meetingId, requestedProfile)
                gathered.preview.noteId != null ->
                    WorkPrivacy.forNote(appContext, gathered.preview.noteId, requestedProfile)
                gathered.preview.stayOnDeviceOnly -> ProcessingProfile.OFFLINE
                else -> requestedProfile
            }
        }

        val factory = LanguageModelFactory(
            context = appContext,
            modelStorage = LocalModelStorage(appContext),
            geminiTransport = CloudAi.transport(appContext)
        )
        val resolved = withContext(Dispatchers.IO) {
            factory.resolveWithFallback(effectiveProfile, ModelCapability.SYNTHESIS)
        } ?: return AiResult.ModelUnavailable(
            "model_unavailable",
            "Connect in Settings or download an on-device model to run Autopilot offline."
        )

        return try {
            val budgetChars = if (resolved.contextLengthTokens <= 4096) 9_500 else 28_000
            val engine = PostSessionAutopilotEngine(resolved.languageModel, contextBudgetChars = budgetChars)
            val result = withContext(Dispatchers.Default) {
                engine.execute(
                    recipe = recipe,
                    sessionTitle = gathered.preview.sessionTitle,
                    workflow = gathered.preview.workflow,
                    sourceText = gathered.sourceText,
                    engineLabel = recipe.label
                )
            }
            if (result is AiResult.Success) {
                val actualEngine = when {
                    resolved.languageModel is ResilientLanguageModel && resolved.languageModel.lastRanLocally ->
                        "On-device (fallback)"
                    resolved.isCloud -> "Cloud (${resolved.modelId})"
                    else -> "On-device (${resolved.modelId})"
                }
                AiResult.Success(result.value.copy(engineLabel = actualEngine))
            } else {
                result
            }
        } finally {
            val ranLocal = !resolved.isCloud ||
                (resolved.languageModel as? ResilientLanguageModel)?.lastRanLocally == true
            if (ranLocal) LlmEngineManager.release()
        }
    }

    suspend fun saveRecapAsNote(
        context: Context,
        result: AutopilotRunResult,
        workflow: RecordingType,
        notebookId: String?
    ): String = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val notesRepo = NoteRepository(context, db)
        val blocks = buildList {
            fun block(type: NoteBlockType, text: String) = NoteBlock(
                id = NoteRepository.newId("block"),
                noteId = "",
                position = 0,
                type = type,
                content = RichText.plain(text),
                source = BlockSource.AI
            )
            add(block(NoteBlockType.CALLOUT, "${result.headline}\n\n${result.summary}"))
            if (result.keyOutcomes.isNotEmpty()) {
                add(block(NoteBlockType.HEADING_2, "Key Outcomes"))
                result.keyOutcomes.forEach { add(block(NoteBlockType.BULLET, it)) }
            }
            if (result.actions.isNotEmpty()) {
                add(block(NoteBlockType.HEADING_2, "Follow-Up Tasks"))
                result.actions.forEach { a ->
                    val suffix = listOfNotNull(a.owner, a.dueHint).joinToString(" · ")
                    add(block(NoteBlockType.CHECKLIST, a.title + if (suffix.isNotEmpty()) " ($suffix)" else ""))
                }
            }
            add(block(NoteBlockType.HEADING_2, result.deliverableTitle))
            add(block(NoteBlockType.MARKDOWN, result.deliverableMarkdown))
        }
        notesRepo.createNote(
            workflow = workflow,
            title = result.targetNoteTitle,
            notebookId = notebookId,
            initialBlocks = blocks,
            useTemplate = false
        ).id
    }

    suspend fun commitTasks(
        context: Context,
        result: AutopilotRunResult,
        noteId: String?,
        meetingId: String?
    ): Int = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val taskRepo = TaskRepository(db.taskDao(), db.peopleDao())
        val existingLower = taskRepo.openTasks().map { it.title.lowercase() }.toSet()
        var added = 0
        for (item in result.actions) {
            val clean = item.title.trim()
            if (clean.isBlank() || clean.lowercase() in existingLower) continue
            taskRepo.save(
                Task(
                    id = "",
                    title = clean,
                    notes = listOfNotNull(
                        item.owner?.let { "Owner: $it" },
                        item.dueHint?.let { "Due: $it" },
                        "Via ${result.recipe.label}"
                    ).joinToString(" · "),
                    kind = item.kind,
                    noteId = noteId,
                    meetingId = meetingId
                )
            )
            added++
        }
        added
    }

    /**
     * REVIEW mode (F-3): files the tasks as action items of the recording. The Wrap-up lists them as
     * findings nobody has confirmed, and only its Done turns the confirmed ones into tasks. Nothing
     * here touches the task list. Returns how many were added; duplicates of the recording's items are skipped.
     */
    suspend fun proposeForReview(
        context: Context,
        result: AutopilotRunResult,
        meetingId: String
    ): Int = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val dao = db.actionItemDao()
        val existingLower = dao.getActionItemsForMeetingDirect(meetingId).map { it.task.trim().lowercase() }.toSet()
        var added = 0
        for (item in result.actions) {
            val clean = item.title.trim()
            if (clean.isBlank() || clean.lowercase() in existingLower) continue
            dao.insertActionItem(
                ActionItemEntity(
                    id = "ap_${UUID.randomUUID()}",
                    meetingId = meetingId,
                    task = clean,
                    assigneeName = item.owner?.takeIf { it.isNotBlank() },
                    deadline = item.dueHint?.takeIf { it.isNotBlank() },
                    confidence = null,
                    isCompleted = false
                )
            )
            added++
        }
        added
    }

    /**
     * Best-effort automatic post-recording trigger: runs ONLY when the user holds
     * [Capability.AUTOMATION_RECIPES] and has opted into background Autopilot execution.
     * In REVIEW mode (the default) the tasks go to the Wrap-up; [commitTasks] runs only in CREATE mode.
     */
    suspend fun maybeAutoRunForReadyMeeting(context: Context, meetingId: String): AutopilotExecutionReport? {
        if (!isUnlocked(context) || !AutopilotPreferences.isAutoRunEnabled(context)) return null
        val gathered = gatherForMeeting(context, meetingId) ?: return null
        if (gathered.sourceText.length < 120) return null
        val res = runRecipe(context, gathered, gathered.preview.recommendedRecipe) as? AiResult.Success ?: return null
        val runResult = res.value
        val noteId = if (AutopilotPreferences.shouldAutoSaveNote(context)) {
            runCatching {
                saveRecapAsNote(context, runResult, gathered.preview.workflow, gathered.preview.notebookId)
            }.getOrNull()
        } else null
        val tasksAdded = when (AutopilotPreferences.tasksMode(context)) {
            AutopilotTasksMode.CREATE -> runCatching {
                commitTasks(context, runResult, noteId ?: gathered.preview.noteId, meetingId)
            }.getOrDefault(0)
            // Review mode only parks tasks where a Wrap-up will show them. Other recordings (a lecture, a
            // sermon) have no Wrap-up, so nothing is added automatically; the result sheet still offers it.
            AutopilotTasksMode.REVIEW -> if (hasWrapUp(context, meetingId)) runCatching {
                proposeForReview(context, runResult, meetingId)
            }.getOrDefault(0) else 0
        }
        return AutopilotExecutionReport(noteId, tasksAdded, runResult)
    }

    /** Mirrors WorkRepository.wantsWrapUp's type rule: Work recordings, plus General and Custom. */
    private suspend fun hasWrapUp(context: Context, meetingId: String): Boolean = withContext(Dispatchers.IO) {
        val type = MeetMindDatabase.getInstance(context).meetingDao().getMeetingById(meetingId)?.recordingType
            ?: return@withContext false
        type == "GENERAL" || type == "CUSTOM" ||
            runCatching { com.craftflowtechnologies.meetingmind.core.model.Workflows.space(RecordingType.valueOf(type)) == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK }.getOrDefault(false)
    }
}
