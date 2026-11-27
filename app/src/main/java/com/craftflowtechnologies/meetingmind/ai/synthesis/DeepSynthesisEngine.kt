package com.craftflowtechnologies.meetingmind.ai.synthesis

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory
import com.craftflowtechnologies.meetingmind.ai.routing.ResilientLanguageModel
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.FeatureGate
import com.craftflowtechnologies.meetingmind.core.billing.TieringMode
import com.craftflowtechnologies.meetingmind.core.billing.TieringModeStore
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DeepSynthesisMode(val label: String, val tagline: String) {
    EXECUTIVE_PACK(
        "Executive Pack",
        "Evolution across sessions, stalled commitments, blind spots & ready-to-send update"
    ),
    PRE_SESSION_BRIEF(
        "Prep Brief",
        "Walk into your next conversation knowing what shifted and what remains unresolved"
    ),
    SERIES_SYNTHESIS(
        "Series Arc",
        "Connect teachings, Scripture threads & open questions across your study series"
    ),
    EXAM_MASTER_CRAM(
        "Exam Master",
        "High-yield concept map, exam traps & cross-lecture synthesis"
    )
}

data class DeepSynthesisSource(
    val id: String,
    val title: String,
    val dateLabel: String,
    val kindLabel: String,
    val snippet: String,
    val meetingId: String? = null,
    val noteId: String? = null,
    val startMs: Long? = null,
    val wordCount: Int = 0,
    val updatedAtMs: Long = 0L
)

data class DeepSynthesisPreview(
    val anchorTitle: String,
    val relatedSessionCount: Int,
    val openCommitmentCount: Int,
    val recurringThemes: List<String>,
    val suggestedMode: DeepSynthesisMode,
    val isConfidential: Boolean
)

data class CrossSessionInsight(
    val heading: String,
    val detail: String,
    val sourceIds: List<String>
)

data class CommitmentDriftItem(
    val commitment: String,
    val owner: String?,
    val statusLabel: String,
    val evidence: String,
    val sourceIds: List<String>
)

data class DeepSynthesisDossier(
    val mode: DeepSynthesisMode,
    val headline: String,
    val executiveSummary: String,
    val evolution: List<CrossSessionInsight>,
    val commitmentDrift: List<CommitmentDriftItem>,
    val blindSpots: List<CrossSessionInsight>,
    val deliverableTitle: String,
    val deliverableMarkdown: String,
    val sourcesById: Map<String, DeepSynthesisSource>,
    val engineLabel: String,
    val generatedAtMs: Long
)

data class GatheredDeepContext(
    val preview: DeepSynthesisPreview,
    val sources: List<DeepSynthesisSource>,
    val openTasks: List<String>,
    val targetNotebookId: String?,
    val workflow: RecordingType
)

/**
 * Pure Cross-Session Deep Intelligence engine (`Capability.DEEP_SYNTHESIS`).
 *
 * Unlike single-session summarization, this synthesizes evolution, drifting commitments,
 * contradictions/blind spots, and a ready-to-send deliverable across an anchor recording/note plus
 * its related historical sessions, validating every source reference back to [sources].
 */
class DeepSynthesisEngine(
    private val languageModel: LanguageModel,
    private val contextBudgetChars: Int = 42_000,
    private val clock: () -> Long = System::currentTimeMillis
) {

    suspend fun synthesize(
        mode: DeepSynthesisMode,
        anchorTitle: String,
        sources: List<DeepSynthesisSource>,
        openTasks: List<String>,
        engineLabel: String
    ): AiResult<DeepSynthesisDossier> {
        if (sources.isEmpty()) {
            return AiResult.Failed("Not enough notes or transcript content yet to synthesize.")
        }
        val fittedSources = fitToBudget(sources, contextBudgetChars)
        val prompt = buildPrompt(mode, anchorTitle, fittedSources, openTasks)
        val raw = when (val res = languageModel.generateStructured(prompt, RESPONSE_SCHEMA, maxOutputTokens = 2048)) {
            is AiResult.Success -> res.value
            else -> return AiResult.Failed(res.describeFailure() ?: "Deep Synthesis could not run right now.")
        }
        val sourcesById = fittedSources.associateBy { it.id }
        val dossier = parseAndValidate(mode, raw, sourcesById, engineLabel)
            ?: return AiResult.Failed("Deep Synthesis returned unreadable output. Try again.")
        return AiResult.Success(dossier)
    }

    internal fun fitToBudget(sources: List<DeepSynthesisSource>, budgetChars: Int): List<DeepSynthesisSource> {
        if (sources.isEmpty()) return emptyList()
        var used = 0
        val out = mutableListOf<DeepSynthesisSource>()
        // Guarantee anchor (index 0) fits at least partially even on small 4K local models.
        val perSourceCap = if (budgetChars <= 10_000) 1_400 else 6_500
        for ((index, src) in sources.withIndex()) {
            val trimmedSnippet = src.snippet.trim().take(perSourceCap)
            val cost = src.title.length + trimmedSnippet.length + 64
            if (index > 0 && used + cost > budgetChars) break
            out += src.copy(snippet = trimmedSnippet)
            used += cost
        }
        return out
    }

    internal fun buildPrompt(
        mode: DeepSynthesisMode,
        anchorTitle: String,
        sources: List<DeepSynthesisSource>,
        openTasks: List<String>
    ): String = buildString {
        appendLine("You are MeetingMind Deep Intelligence — a rigorous cross-session analyst.")
        appendLine("Goal: ${modeDirective(mode)}")
        appendLine()
        appendLine("ANCHOR SESSION: ${anchorTitle.ifBlank { "Untitled" }}")
        appendLine()
        appendLine("STRICT GROUNDING RULES:")
        appendLine("- Cite only exact source IDs from SOURCES below (e.g. [\"s1\", \"s2\"]). Never invent a source ID.")
        appendLine("- Never invent numbers, dates, metrics, or people not present in SOURCES or OPEN_TASKS.")
        appendLine("- Compare across sources to surface shifts, contradictions, stalled tasks, and concrete patterns.")
        appendLine()
        if (openTasks.isNotEmpty()) {
            appendLine("OPEN COMMITMENTS & TASKS IN WORKSPACE:")
            openTasks.take(18).forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("SOURCES (${sources.size} chronologically/contextually related items):")
        sources.forEach { src ->
            appendLine("[${src.id}] Title: ${src.title} | Date: ${src.dateLabel} | Type: ${src.kindLabel}")
            appendLine(src.snippet)
            appendLine("---")
        }
        appendLine()
        appendLine("Return ONLY a single valid JSON object matching this exact schema:")
        appendLine(
            """
            {
              "headline": "Sharp 1-line synthesis headline summarizing what changed or matters most",
              "executive_summary": "2-4 sentence cross-session synthesis connecting the anchor with prior sessions",
              "evolution": [
                {"heading": "Decision/Theme evolution", "detail": "How thinking or facts evolved across sessions", "sources": ["s1","s2"]}
              ],
              "commitment_drift": [
                {"commitment": "Task or promise", "owner": "Person if stated or empty", "status_label": "Carried Over | At Risk | Unassigned | On Track", "evidence": "Why it matters or where it appeared", "sources": ["s1"]}
              ],
              "blind_spots": [
                {"heading": "Contradiction, risk or open loop", "detail": "What conflicts or remains unanswered across sessions", "sources": ["s2"]}
              ],
              "deliverable_title": "Ready-to-send document title",
              "deliverable_markdown": "Clean markdown deliverable ready to copy or share (bulleted headings, owners, next steps)"
            }
            """.trimIndent()
        )
    }

    internal fun parseAndValidate(
        mode: DeepSynthesisMode,
        rawResponse: String,
        sourcesById: Map<String, DeepSynthesisSource>,
        engineLabel: String
    ): DeepSynthesisDossier? {
        val json = extractJsonObject(rawResponse) ?: return null
        val headline = json.optString("headline").trim().ifEmpty { "${mode.label} Dossier" }
        val summary = json.optString("executive_summary").trim()
        val deliverableTitle = json.optString("deliverable_title").trim().ifEmpty { "${mode.label}: $headline" }
        val deliverableMarkdown = json.optString("deliverable_markdown").trim().ifEmpty { summary }
        if (summary.isEmpty() && deliverableMarkdown.isEmpty()) return null

        val evolution = parseInsights(json.optJSONArray("evolution"), sourcesById)
        val blindSpots = parseInsights(json.optJSONArray("blind_spots"), sourcesById)
        val drift = parseCommitments(json.optJSONArray("commitment_drift"), sourcesById)

        return DeepSynthesisDossier(
            mode = mode,
            headline = headline,
            executiveSummary = summary.ifEmpty { deliverableMarkdown.take(320) },
            evolution = evolution,
            commitmentDrift = drift,
            blindSpots = blindSpots,
            deliverableTitle = deliverableTitle,
            deliverableMarkdown = deliverableMarkdown,
            sourcesById = sourcesById,
            engineLabel = engineLabel,
            generatedAtMs = clock()
        )
    }

    private fun parseInsights(
        array: JSONArray?,
        sourcesById: Map<String, DeepSynthesisSource>
    ): List<CrossSessionInsight> = buildList {
        for (i in 0 until (array?.length() ?: 0)) {
            val obj = array?.optJSONObject(i) ?: continue
            val heading = obj.optString("heading").trim()
            val detail = obj.optString("detail").trim()
            if (heading.isEmpty() && detail.isEmpty()) continue
            val refs = validRefs(obj.optJSONArray("sources"), sourcesById)
            if (refs.isEmpty()) continue
            add(CrossSessionInsight(heading.ifEmpty { "Cross-session pattern" }, detail.ifEmpty { heading }, refs))
        }
    }

    private fun parseCommitments(
        array: JSONArray?,
        sourcesById: Map<String, DeepSynthesisSource>
    ): List<CommitmentDriftItem> = buildList {
        for (i in 0 until (array?.length() ?: 0)) {
            val obj = array?.optJSONObject(i) ?: continue
            val commitment = obj.optString("commitment").trim()
            if (commitment.isEmpty()) continue
            val refs = validRefs(obj.optJSONArray("sources"), sourcesById)
            if (refs.isEmpty()) continue
            val owner = obj.optString("owner").trim().takeIf { it.isNotEmpty() && it != "null" }
            val status = obj.optString("status_label").trim().ifEmpty { "Open" }
            val evidence = obj.optString("evidence").trim()
            add(CommitmentDriftItem(commitment, owner, status, evidence, refs))
        }
    }

    private fun validRefs(array: JSONArray?, sourcesById: Map<String, DeepSynthesisSource>): List<String> = buildList {
        for (i in 0 until (array?.length() ?: 0)) {
            val raw = array?.optString(i)?.trim()?.removePrefix("[")?.removeSuffix("]")?.trim().orEmpty()
            val key = if (raw in sourcesById) raw else raw.lowercase()
            if (key in sourcesById && key !in this) add(key)
        }
    }

    private fun extractJsonObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        runCatching { return JSONObject(trimmed) }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching { return JSONObject(trimmed.substring(start, end + 1)) }
        }
        return null
    }

    private fun modeDirective(mode: DeepSynthesisMode): String = when (mode) {
        DeepSynthesisMode.EXECUTIVE_PACK ->
            "Produce a high-leverage Executive Action Pack: identify how decisions shifted across sessions, flag stalled or recurring commitments, surface contradictions/risks, and draft a crisp copy-ready executive update email/post."
        DeepSynthesisMode.PRE_SESSION_BRIEF ->
            "Produce a Pre-Session Brief so the user walks into their next session completely prepared: summarize current baseline, surface unresolved follow-ups to press on, and draft a structured agenda brief."
        DeepSynthesisMode.SERIES_SYNTHESIS ->
            "Synthesize this teaching/faith series across sessions: trace the overarching theological or thematic arc, weave together Scripture references, surface open study questions, and write a share-ready small-group series guide."
        DeepSynthesisMode.EXAM_MASTER_CRAM ->
            "Synthesize across these study lectures into an Exam Master Pack: map interconnected concepts across sessions, highlight recurring exam/high-yield emphasis, flag tricky distinctions, and draft a master revision sheet."
    }

    companion object {
        const val RESPONSE_SCHEMA =
            """{"type":"object","properties":{"headline":{"type":"string"},"executive_summary":{"type":"string"},"evolution":{"type":"array","items":{"type":"object","properties":{"heading":{"type":"string"},"detail":{"type":"string"},"sources":{"type":"array","items":{"type":"string"}}},"required":["heading","detail","sources"]}},"commitment_drift":{"type":"array","items":{"type":"object","properties":{"commitment":{"type":"string"},"owner":{"type":"string"},"status_label":{"type":"string"},"evidence":{"type":"string"},"sources":{"type":"array","items":{"type":"string"}}},"required":["commitment","status_label","sources"]}},"blind_spots":{"type":"array","items":{"type":"object","properties":{"heading":{"type":"string"},"detail":{"type":"string"},"sources":{"type":"array","items":{"type":"string"}}},"required":["heading","detail","sources"]}},"deliverable_title":{"type":"string"},"deliverable_markdown":{"type":"string"}},"required":["headline","executive_summary","deliverable_markdown"]}"""
    }
}

sealed interface DeepSynthesisAccess {
    data object Granted : DeepSynthesisAccess
    data class RequiresUpgrade(val preview: DeepSynthesisPreview) : DeepSynthesisAccess
}

/**
 * Coordinates workspace discovery, billing gating, privacy checks, cloud+local model execution,
 * and end-to-end save actions for [DeepSynthesisEngine].
 */
object DeepSynthesisWorkspace {

    private val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())

    fun hasPaidAccess(context: Context): Boolean =
        FeatureGate.can(Capability.DEEP_SYNTHESIS) ||
            TieringModeStore.current(context) == TieringMode.CLOSED_TESTING

    suspend fun gatherForMeeting(context: Context, meetingId: String): GatheredDeepContext? = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val meeting = db.meetingDao().getMeetingById(meetingId) ?: return@withContext null
        val linkedNote = meeting.noteId?.let { db.noteDao().getById(it) }
        val segments = db.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        val anchorTranscript = segments.joinToString("\n") { seg ->
            val spk = seg.speakerName?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
            spk + (seg.cleanedText ?: seg.text)
        }.trim()
        val anchorText = buildString {
            linkedNote?.plainText?.takeIf { it.isNotBlank() }?.let { appendLine(it.take(6_000)) }
            meeting.summaryText?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            if (anchorTranscript.isNotBlank()) appendLine(anchorTranscript)
        }.trim().ifEmpty { meeting.title }

        val workflow = runCatching { RecordingType.valueOf(meeting.recordingType) }.getOrDefault(RecordingType.GENERAL)
        val anchorTitle = meeting.title.ifBlank { linkedNote?.title.orEmpty().ifBlank { "Untitled recording" } }
        val anchorSource = DeepSynthesisSource(
            id = "s1",
            title = anchorTitle,
            dateLabel = dateFmt.format(Date(meeting.createdAt)),
            kindLabel = "Current recording",
            snippet = anchorText,
            meetingId = meeting.id,
            noteId = meeting.noteId,
            startMs = segments.firstOrNull()?.startMs,
            wordCount = anchorText.split(Regex("\\s+")).count { it.isNotBlank() },
            updatedAtMs = meeting.updatedAt
        )

        gatherRelatedAndAssemble(
            context = context,
            db = db,
            anchorTitle = anchorTitle,
            anchorSource = anchorSource,
            anchorNotebookId = linkedNote?.notebookId,
            anchorWorkflow = workflow,
            excludeMeetingId = meeting.id,
            excludeNoteId = meeting.noteId
        )
    }

    suspend fun gatherForNote(context: Context, noteId: String): GatheredDeepContext? = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val note = db.noteDao().getById(noteId) ?: return@withContext null
        val linkedMeeting = db.noteDao().getMeetingsForNote(noteId).firstOrNull()
        val transcript = linkedMeeting?.let { m ->
            db.transcriptDao().getSegmentsForMeetingDirect(m.id).joinToString("\n") { s ->
                val spk = s.speakerName?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
                spk + (s.cleanedText ?: s.text)
            }
        }.orEmpty()
        val body = buildString {
            if (note.plainText.isNotBlank()) appendLine(note.plainText)
            if (transcript.isNotBlank()) appendLine(transcript)
        }.trim().ifEmpty { note.title }

        val workflow = runCatching { RecordingType.valueOf(note.workflow) }.getOrDefault(RecordingType.GENERAL)
        val anchorTitle = note.title.ifBlank { "Untitled note" }
        val anchorSource = DeepSynthesisSource(
            id = "s1",
            title = anchorTitle,
            dateLabel = dateFmt.format(Date(note.updatedAt)),
            kindLabel = "Current note",
            snippet = body,
            meetingId = linkedMeeting?.id,
            noteId = note.id,
            wordCount = body.split(Regex("\\s+")).count { it.isNotBlank() },
            updatedAtMs = note.updatedAt
        )

        gatherRelatedAndAssemble(
            context = context,
            db = db,
            anchorTitle = anchorTitle,
            anchorSource = anchorSource,
            anchorNotebookId = note.notebookId,
            anchorWorkflow = workflow,
            excludeMeetingId = linkedMeeting?.id,
            excludeNoteId = note.id
        )
    }

    suspend fun gatherForNotebook(context: Context, notebookId: String): GatheredDeepContext? = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val notebook = db.notebookDao().getById(notebookId) ?: return@withContext null
        val notesInNotebook = db.noteDao().getActiveOnce()
            .filter { it.notebookId == notebookId && it.plainText.isNotBlank() }
        if (notesInNotebook.isEmpty()) return@withContext null
        val meetingsByNote = db.noteDao().getMeetingsForNotes(notesInNotebook.map { it.id }).associateBy { it.noteId }

        val sources = notesInNotebook.take(9).mapIndexed { idx, n ->
            val m = meetingsByNote[n.id]
            val body = n.plainText.ifBlank { m?.summaryText.orEmpty() }
            DeepSynthesisSource(
                id = "s${idx + 1}",
                title = n.title.ifBlank { "Note ${idx + 1}" },
                dateLabel = dateFmt.format(Date(n.updatedAt)),
                kindLabel = "Notebook note",
                snippet = body,
                meetingId = m?.id,
                noteId = n.id,
                wordCount = body.split(Regex("\\s+")).count { it.isNotBlank() },
                updatedAtMs = n.updatedAt
            )
        }
        val firstWorkflow = runCatching { RecordingType.valueOf(notesInNotebook.first().workflow) }.getOrDefault(RecordingType.GENERAL)
        val openActionTexts = loadOpenCommitments(db, sources.mapNotNull { it.meetingId })
        val themes = extractTopKeywords(sources.joinToString(" ") { "${it.title} ${it.snippet.take(600)}" })
        val confidential = WorkPrivacy.forNote(context, notesInNotebook.first().id, ProcessingProfile.INTERNET) == ProcessingProfile.OFFLINE
        val preview = DeepSynthesisPreview(
            anchorTitle = notebook.name,
            relatedSessionCount = sources.size,
            openCommitmentCount = openActionTexts.size,
            recurringThemes = themes,
            suggestedMode = defaultModeFor(firstWorkflow),
            isConfidential = confidential
        )
        GatheredDeepContext(preview, sources, openActionTexts, notebookId, firstWorkflow)
    }

    private suspend fun gatherRelatedAndAssemble(
        context: Context,
        db: MeetMindDatabase,
        anchorTitle: String,
        anchorSource: DeepSynthesisSource,
        anchorNotebookId: String?,
        anchorWorkflow: RecordingType,
        excludeMeetingId: String?,
        excludeNoteId: String?
    ): GatheredDeepContext {
        val anchorTerms = extractTopKeywords("$anchorTitle ${anchorSource.snippet.take(1_200)}", max = 8).toSet()
        val candidates = db.noteDao().getActiveOnce()
            .filter { it.id != excludeNoteId && it.workflow != RecordingType.DEVOTIONAL.name }
        val scoredNotes = candidates.mapNotNull { n ->
            val text = n.plainText.trim()
            if (text.length < 30 && n.title.isBlank()) return@mapNotNull null
            var score = 0
            if (anchorNotebookId != null && n.notebookId == anchorNotebookId) score += 6
            if (n.workflow == anchorWorkflow.name) score += 3
            val lower = "${n.title} ${text.take(800)}".lowercase()
            score += anchorTerms.count { it in lower } * 2
            if (score <= 0) return@mapNotNull null
            n to score
        }.sortedByDescending { it.second * 1_000_000L + (it.first.updatedAt / 1_000_000L) }
            .take(7)
            .map { it.first }

        val linkedMeetings = if (scoredNotes.isNotEmpty()) {
            db.noteDao().getMeetingsForNotes(scoredNotes.map { it.id })
                .filter { it.id != excludeMeetingId }
                .associateBy { it.noteId }
        } else emptyMap()

        val allSources = buildList {
            add(anchorSource)
            scoredNotes.forEachIndexed { idx, n ->
                val m = linkedMeetings[n.id]
                val body = buildString {
                    if (n.plainText.isNotBlank()) appendLine(n.plainText)
                    if (m?.summaryText?.isNotBlank() == true) appendLine(m.summaryText)
                }.trim()
                if (body.isNotBlank()) {
                    add(
                        DeepSynthesisSource(
                            id = "s${idx + 2}",
                            title = n.title.ifBlank { m?.title.orEmpty().ifBlank { "Related session" } },
                            dateLabel = dateFmt.format(Date(n.updatedAt)),
                            kindLabel = if (n.notebookId == anchorNotebookId) "Same notebook" else "Related session",
                            snippet = body,
                            meetingId = m?.id,
                            noteId = n.id,
                            wordCount = body.split(Regex("\\s+")).count { it.isNotBlank() },
                            updatedAtMs = n.updatedAt
                        )
                    )
                }
            }
        }

        val meetingIds = allSources.mapNotNull { it.meetingId }.distinct()
        val openTasks = loadOpenCommitments(db, meetingIds)
        val themes = extractTopKeywords(allSources.joinToString(" ") { "${it.title} ${it.snippet.take(500)}" })
        val confidential = excludeMeetingId?.let {
            WorkPrivacy.forMeeting(context, it, ProcessingProfile.INTERNET) == ProcessingProfile.OFFLINE
        } ?: excludeNoteId?.let {
            WorkPrivacy.forNote(context, it, ProcessingProfile.INTERNET) == ProcessingProfile.OFFLINE
        } ?: false

        val preview = DeepSynthesisPreview(
            anchorTitle = anchorTitle,
            relatedSessionCount = allSources.size,
            openCommitmentCount = openTasks.size,
            recurringThemes = themes,
            suggestedMode = defaultModeFor(anchorWorkflow),
            isConfidential = confidential
        )
        return GatheredDeepContext(preview, allSources, openTasks, anchorNotebookId, anchorWorkflow)
    }

    suspend fun runSynthesis(
        context: Context,
        gathered: GatheredDeepContext,
        mode: DeepSynthesisMode
    ): AiResult<DeepSynthesisDossier> = withContext(Dispatchers.Default) {
        val prefs = UserPreferencesManager(context).preferencesFlow.first()
        val profile = if (gathered.preview.isConfidential) ProcessingProfile.OFFLINE else prefs.processingProfile
        val factory = LanguageModelFactory(
            context = context,
            modelStorage = LocalModelStorage(context),
            geminiTransport = CloudAi.transport(context)
        )
        val resolved = withContext(Dispatchers.IO) {
            if (profile.requiresNetwork) {
                factory.resolveWithFallback(profile, ModelCapability.SYNTHESIS, ModelTier.RECOMMENDED)
            } else {
                factory.resolveLocal(ModelCapability.SYNTHESIS, ModelTier.RECOMMENDED)
            }
        } ?: return@withContext AiResult.ModelUnavailable(
            "model_unavailable",
            "No AI model is ready. Connect in Settings or download an on-device model to run Deep Synthesis offline."
        )

        try {
            val budgetChars = if (resolved.contextLengthTokens <= 4096) 9_500 else 42_000
            val engine = DeepSynthesisEngine(resolved.languageModel, contextBudgetChars = budgetChars)
            val result = engine.synthesize(
                mode = mode,
                anchorTitle = gathered.preview.anchorTitle,
                sources = gathered.sources,
                openTasks = gathered.openTasks,
                engineLabel = "Deep Pack"
            )
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

    suspend fun saveAsNote(
        context: Context,
        dossier: DeepSynthesisDossier,
        workflow: RecordingType,
        notebookId: String?
    ): String = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val notesRepo = NoteRepository(context, db)
        val newNote = notesRepo.createNote(
            workflow = workflow,
            title = "${dossier.mode.label}: ${dossier.headline}".take(96),
            notebookId = notebookId,
            initialBlocks = listOf(
                NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = "",
                    position = 0,
                    type = NoteBlockType.CALLOUT,
                    content = RichText.plain(dossier.executiveSummary),
                    source = BlockSource.AI
                )
            ) + buildDossierBlocks(dossier),
            useTemplate = false
        )
        newNote.id
    }

    suspend fun addCommitmentsAsTasks(context: Context, dossier: DeepSynthesisDossier): Int = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val taskRepo = TaskRepository(db.taskDao(), db.peopleDao())
        val existingLower = taskRepo.openTasks().map { it.title.lowercase() }.toSet()
        var added = 0
        for (item in dossier.commitmentDrift) {
            val clean = item.commitment.trim()
            if (clean.isBlank() || clean.lowercase() in existingLower) continue
            val firstRef = item.sourceIds.firstOrNull()?.let { dossier.sourcesById[it] }
            taskRepo.save(
                Task(
                    id = "",
                    title = clean,
                    notes = listOfNotNull(
                        item.owner?.let { "Owner: $it" },
                        item.statusLabel.takeIf { it.isNotBlank() }?.let { "Status: $it" },
                        item.evidence.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    kind = TaskKind.FOLLOW_UP,
                    noteId = firstRef?.noteId,
                    meetingId = firstRef?.meetingId,
                    startMs = firstRef?.startMs
                )
            )
            added++
        }
        added
    }

    private fun buildDossierBlocks(dossier: DeepSynthesisDossier): List<NoteBlock> = buildList {
        fun heading(text: String) = NoteBlock(NoteRepository.newId("block"), "", 0, NoteBlockType.HEADING_2, RichText.plain(text), source = BlockSource.AI)
        fun bullet(text: String) = NoteBlock(NoteRepository.newId("block"), "", 0, NoteBlockType.BULLET, RichText.plain(text), source = BlockSource.AI)
        fun check(text: String) = NoteBlock(NoteRepository.newId("block"), "", 0, NoteBlockType.CHECKLIST, RichText.plain(text), source = BlockSource.AI)
        fun para(text: String) = NoteBlock(NoteRepository.newId("block"), "", 0, NoteBlockType.PARAGRAPH, RichText.plain(text), source = BlockSource.AI)

        if (dossier.evolution.isNotEmpty()) {
            add(heading("Cross-Session Evolution"))
            dossier.evolution.forEach { e ->
                val refs = e.sourceIds.mapNotNull { dossier.sourcesById[it]?.title }.joinToString(", ")
                add(bullet("${e.heading}: ${e.detail}" + if (refs.isNotEmpty()) " (Sources: $refs)" else ""))
            }
        }
        if (dossier.commitmentDrift.isNotEmpty()) {
            add(heading("Commitments & Drift"))
            dossier.commitmentDrift.forEach { c ->
                val meta = listOfNotNull(c.owner, c.statusLabel).joinToString(" · ")
                add(check(if (meta.isNotEmpty()) "${c.commitment} — [$meta] ${c.evidence}" else c.commitment))
            }
        }
        if (dossier.blindSpots.isNotEmpty()) {
            add(heading("Blind Spots & Open Loops"))
            dossier.blindSpots.forEach { b ->
                val refs = b.sourceIds.mapNotNull { dossier.sourcesById[it]?.title }.joinToString(", ")
                add(bullet("${b.heading}: ${b.detail}" + if (refs.isNotEmpty()) " ($refs)" else ""))
            }
        }
        if (dossier.deliverableMarkdown.isNotBlank()) {
            add(heading(dossier.deliverableTitle))
            add(para(dossier.deliverableMarkdown))
        }
    }

    private suspend fun loadOpenCommitments(db: MeetMindDatabase, meetingIds: List<String>): List<String> {
        val taskRepo = TaskRepository(db.taskDao(), db.peopleDao())
        val openTasks = runCatching { taskRepo.openTasks() }.getOrDefault(emptyList())
            .filter { t -> meetingIds.isEmpty() || t.meetingId in meetingIds || t.meetingId == null }
            .take(12)
            .map { t -> t.title + (t.notes.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") }
        val openActionItems = if (meetingIds.isNotEmpty()) {
            runCatching { db.actionItemDao().getOpenForMeetings(meetingIds) }.getOrDefault(emptyList())
                .take(12)
                .map { a -> a.task + (a.assigneeName?.let { " [$it]" } ?: "") }
        } else emptyList()
        return (openTasks + openActionItems).distinct().take(18)
    }

    internal fun defaultModeFor(workflow: RecordingType): DeepSynthesisMode = when {
        workflow in Workflows.faith -> DeepSynthesisMode.SERIES_SYNTHESIS
        workflow == RecordingType.LECTURE || workflow == RecordingType.RESEARCH -> DeepSynthesisMode.EXAM_MASTER_CRAM
        else -> DeepSynthesisMode.EXECUTIVE_PACK
    }

    internal fun extractTopKeywords(text: String, max: Int = 4): List<String> {
        val stop = setOf(
            "about", "after", "again", "already", "also", "always", "another", "because", "before",
            "being", "between", "could", "current", "discussed", "every", "first", "going", "having",
            "meeting", "might", "never", "other", "really", "recording", "section", "should", "since",
            "still", "summary", "their", "there", "these", "thing", "things", "think", "those", "today",
            "untitled", "where", "which", "while", "would"
        )
        return Regex("[A-Za-z][A-Za-z0-9]{3,}")
            .findAll(text)
            .map { it.value.lowercase() }
            .filter { it !in stop }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(max)
            .map { it.key.replaceFirstChar { c -> c.uppercase() } }
    }
}
