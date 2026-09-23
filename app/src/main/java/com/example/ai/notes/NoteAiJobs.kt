package com.example.ai.notes

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.core.database.MeetMindDatabase
import com.example.core.database.NoteAiJobEntity
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.ModelCapability
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.model.Workflows
import com.example.core.repository.NoteCodec.toDomain
import com.example.core.repository.TranscriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class NoteAiTarget { NOTE, NOTEBOOK }

/** Every passage id an outcome points at. */
fun NoteAiOutcome.citedIds(): Set<String> = when (this) {
    is NoteAiOutcome.Points -> items.flatMap { it.sourceIds }.toSet()
    is NoteAiOutcome.Answer -> sourceIds.toSet()
    is NoteAiOutcome.Sections -> sections.flatMap { s -> s.items.flatMap { it.sourceIds } }.toSet()
}
enum class NoteAiStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/** A finished run: what it produced, what it read, and what it left out. */
data class NoteAiResult(
    val outcome: NoteAiOutcome,
    /** Every passage the result cites, so a citation can show its words and open its note. */
    val sources: Map<String, SourcePassage>,
    /** Plain words about what was read: "Whole note", "12 of 30 notes, most recent first". */
    val scope: String
)

data class NoteAiJob(
    val id: String,
    val target: NoteAiTarget,
    val targetId: String,
    val tool: NoteAiTool,
    val status: NoteAiStatus,
    val question: String?,
    val result: NoteAiResult?,
    val error: String?,
    val engine: String?,
    val createdAt: Long
)

/**
 * Note AI runs, kept in the database so a result survives the app closing mid-run, and queued on
 * WorkManager so they finish in the background.
 */
class NoteAiRepository(private val context: Context, database: MeetMindDatabase = MeetMindDatabase.getInstance(context)) {
    private val dao = database.noteAiJobDao()

    fun observe(targetId: String): Flow<List<NoteAiJob>> =
        dao.observeForTarget(targetId).map { list -> list.mapNotNull { NoteAiCodec.toDomain(it) } }.flowOn(Dispatchers.IO)

    suspend fun run(target: NoteAiTarget, targetId: String, tool: NoteAiTool, question: String? = null): String = withContext(Dispatchers.IO) {
        val id = "noteai_" + UUID.randomUUID().toString().take(12)
        val now = System.currentTimeMillis()
        dao.deleteFinished(targetId)
        dao.upsert(
            NoteAiJobEntity(
                id, target.name, targetId, tool.name, NoteAiStatus.QUEUED.name,
                JSONObject().apply { question?.let { put("question", it) } }.toString(),
                null, null, null, now, now
            )
        )
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id), ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NoteAiWorker>().setInputData(workDataOf(NoteAiWorker.KEY_JOB to id)).build()
        )
        id
    }

    suspend fun cancel(jobId: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(jobId))
        dao.getById(jobId)?.let { if (it.status == "QUEUED" || it.status == "RUNNING") dao.upsert(it.copy(status = NoteAiStatus.CANCELLED.name, updatedAt = System.currentTimeMillis())) }
    }

    /** Once a result has been used or dismissed it isn't kept: results are a delivery, not a history. */
    suspend fun dismiss(jobId: String) = withContext(Dispatchers.IO) { dao.delete(jobId) }

    companion object {
        fun workName(jobId: String) = "meetmind_note_ai_$jobId"
    }
}

/**
 * Runs one note AI job: gathers the passages, picks the model from the processing profile,
 * runs [NoteAiEngine], and stores the validated result.
 *
 * Privacy: in Internet mode a notebook run leaves out private notes (prayers, journals) — they are
 * only ever sent when the person runs a tool on that one note themselves.
 */
class NoteAiWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB) ?: return Result.failure()
        val database = MeetMindDatabase.getInstance(applicationContext)
        val dao = database.noteAiJobDao()
        val job = dao.getById(jobId) ?: return Result.failure()
        if (job.status == NoteAiStatus.CANCELLED.name) return Result.success()
        val tool = runCatching { NoteAiTool.valueOf(job.tool) }.getOrNull() ?: return fail(job, "Unknown tool.")
        dao.upsert(job.copy(status = NoteAiStatus.RUNNING.name, updatedAt = System.currentTimeMillis()))

        return try {
            val prefs = UserPreferencesManager(applicationContext).preferencesFlow.first()
            val factory = com.example.ai.routing.LanguageModelFactory(
                context = applicationContext,
                modelStorage = com.example.ai.modelmanagement.LocalModelStorage(applicationContext),
                geminiTransport = com.example.ai.cloud.GeminiHttpTransport(com.example.ai.cloud.GeminiCredentialStore(applicationContext))
            )
            val model = factory.resolve(prefs.processingProfile, ModelCapability.SUMMARIZATION)
                ?: return fail(job, "No AI model is available. Install one in Settings → AI Engine, or turn on Internet mode.")

            val question = runCatching { JSONObject(job.inputJson).optString("question") }.getOrNull()?.takeIf { it.isNotBlank() }
            val gathered = NoteAiGatherer(database).gather(
                NoteAiTarget.valueOf(job.targetKind), job.targetId, tool, question,
                budgetChars = NoteSources.budgetChars(model.contextLengthTokens, MAX_OUTPUT),
                sendingToCloud = model.isCloud
            ) ?: return fail(job, "That note or notebook no longer exists.")

            val outcome = NoteAiEngine(model.languageModel, MAX_OUTPUT).run(tool, gathered.passages, gathered.faith, question, gathered.sections)
            if (!model.isCloud) com.example.ai.modelmanagement.LlmEngineManager.release()
            when (outcome) {
                is AiResult.Success -> {
                    val cited = outcome.value.citedIds()
                    val result = NoteAiResult(outcome.value, gathered.passages.filter { it.id in cited }.associateBy { it.id }, gathered.scope)
                    dao.upsert(
                        job.copy(
                            status = NoteAiStatus.SUCCEEDED.name, resultJson = NoteAiCodec.encode(result).toString(),
                            engine = model.modelId, updatedAt = System.currentTimeMillis()
                        )
                    )
                    Result.success()
                }
                else -> fail(job, outcome.describeFailure() ?: "\"${tool.label}\" could not run.")
            }
        } catch (e: CancellationException) {
            dao.upsert(job.copy(status = NoteAiStatus.CANCELLED.name, updatedAt = System.currentTimeMillis()))
            throw e
        } catch (e: Exception) {
            fail(job, e.message ?: "\"${tool.label}\" could not run.")
        }
    }

    private suspend fun fail(job: NoteAiJobEntity, message: String): Result {
        MeetMindDatabase.getInstance(applicationContext).noteAiJobDao()
            .upsert(job.copy(status = NoteAiStatus.FAILED.name, errorMessage = message, updatedAt = System.currentTimeMillis()))
        return Result.failure()
    }

    companion object {
        const val KEY_JOB = "noteAiJob"
        const val MAX_OUTPUT = 1536
    }
}

/** What a run reads, decided in one testable place. */
class NoteAiGatherer(private val database: MeetMindDatabase) {

    data class Gathered(val passages: List<SourcePassage>, val faith: Boolean, val sections: List<SectionSpec>, val scope: String)

    suspend fun gather(
        target: NoteAiTarget,
        targetId: String,
        tool: NoteAiTool,
        question: String?,
        budgetChars: Int,
        sendingToCloud: Boolean
    ): Gathered? = withContext(Dispatchers.IO) {
        val noteDao = database.noteDao()
        when (target) {
            NoteAiTarget.NOTE -> {
                val note = noteDao.getById(targetId)?.toDomain() ?: return@withContext null
                val blocks = noteDao.getBlocks(targetId).map { it.toDomain() }
                var passages = NoteSources.passagesOf(note, blocks)
                // Questions can also be answered from the note's recordings.
                if (tool == NoteAiTool.ASK) {
                    val transcripts = noteDao.getMeetingsForNote(targetId).flatMap { m ->
                        NoteSources.transcriptPassagesOf(note, TranscriptRepository(database).getTranscriptDirect(m.id).segments)
                    }
                    passages = passages + transcripts
                }
                val chosen = if (tool == NoteAiTool.ASK && question != null) NoteSources.relevant(passages, question, budgetChars)
                else NoteSources.fit(passages, budgetChars).first
                val left = passages.size - chosen.size
                Gathered(
                    chosen,
                    faith = Workflows.space(note.workflow) == NotebookSpace.FAITH,
                    sections = organizeSections(note.workflow),
                    scope = if (left == 0) "This note" else "This note — the first ${chosen.size} of ${passages.size} passages fit"
                )
            }
            NoteAiTarget.NOTEBOOK -> {
                val notebook = database.notebookDao().getById(targetId) ?: return@withContext null
                val all = noteDao.getAll().filter { it.notebookId == targetId && it.archivedAt == null }.sortedByDescending { it.updatedAt }
                val usable = if (sendingToCloud) all.filter { !it.isPrivate } else all
                val skippedPrivate = all.size - usable.size
                val perNote = usable.map { e ->
                    val note = e.toDomain()
                    note to NoteSources.passagesOf(note, noteDao.getBlocks(e.id).map { it.toDomain() })
                }
                val flat = perNote.flatMap { it.second }
                val chosen = if (tool == NoteAiTool.ASK && question != null) NoteSources.relevant(flat, question, budgetChars)
                else NoteSources.fit(flat, budgetChars).first
                val notesRead = chosen.mapNotNull { it.noteId }.toSet().size
                val scope = buildString {
                    append(if (notesRead == usable.size) "All ${usable.size} notes in ${notebook.name}" else "$notesRead of ${usable.size} notes in ${notebook.name}" + if (tool == NoteAiTool.ASK) ", the most relevant" else ", most recent first")
                    if (skippedPrivate > 0) append(" · $skippedPrivate private ${if (skippedPrivate == 1) "note" else "notes"} kept on this phone")
                }
                Gathered(
                    chosen,
                    faith = notebook.space == NotebookSpace.FAITH.name || perNote.any { Workflows.space(it.first.workflow) == NotebookSpace.FAITH },
                    sections = emptyList(),
                    scope = scope
                )
            }
        }
    }

    companion object {
        /** A note's own template sections, or a plain set for notes without one. */
        fun organizeSections(workflow: RecordingType): List<SectionSpec> {
            val template = Workflows.template(workflow).sections
            return if (template.isNotEmpty()) template.map { SectionSpec(it.key, it.title, it.hint) }
            else listOf(
                SectionSpec("key_points", "Key points", "the main ideas"),
                SectionSpec("details", "Details", "supporting facts and examples"),
                SectionSpec("questions", "Questions", "open questions"),
                SectionSpec("next_steps", "Next steps", "things to do")
            )
        }
    }
}

/** Results to and from JSON, for the job row. */
object NoteAiCodec {

    fun encode(result: NoteAiResult): JSONObject = JSONObject().apply {
        put("scope", result.scope)
        put("sources", JSONArray().apply {
            result.sources.values.forEach { p -> put(JSONObject().put("id", p.id).put("text", p.text).put("label", p.label).put("noteId", p.noteId ?: "")) }
        })
        when (val o = result.outcome) {
            is NoteAiOutcome.Points -> { put("type", "points"); put("items", items(o.items)) }
            is NoteAiOutcome.Answer -> { put("type", "answer"); put("answer", o.text); put("found", o.found); put("cites", JSONArray(o.sourceIds)) }
            is NoteAiOutcome.Sections -> {
                put("type", "sections")
                put("sections", JSONArray().apply { o.sections.forEach { s -> put(JSONObject().put("key", s.key).put("title", s.title).put("items", items(s.items))) } })
                put("leftOver", JSONArray(o.leftOver))
            }
        }
    }

    fun decode(json: JSONObject): NoteAiResult {
        val sources = json.optJSONArray("sources").objects().associate { o ->
            o.optString("id") to SourcePassage(o.optString("id"), o.optString("text"), o.optString("label"), o.optString("noteId").ifBlank { null })
        }
        val outcome = when (json.optString("type")) {
            "answer" -> NoteAiOutcome.Answer(json.optString("answer"), json.optJSONArray("cites").strings(), json.optBoolean("found"))
            "sections" -> NoteAiOutcome.Sections(
                json.optJSONArray("sections").objects().map { s -> SectionDraft(s.optString("key"), s.optString("title"), items(s.optJSONArray("items"))) },
                json.optJSONArray("leftOver").strings()
            )
            else -> NoteAiOutcome.Points(items(json.optJSONArray("items")))
        }
        return NoteAiResult(outcome, sources, json.optString("scope"))
    }

    fun toDomain(e: NoteAiJobEntity): NoteAiJob? {
        val tool = runCatching { NoteAiTool.valueOf(e.tool) }.getOrNull() ?: return null
        return NoteAiJob(
            e.id, runCatching { NoteAiTarget.valueOf(e.targetKind) }.getOrDefault(NoteAiTarget.NOTE), e.targetId, tool,
            runCatching { NoteAiStatus.valueOf(e.status) }.getOrDefault(NoteAiStatus.FAILED),
            runCatching { JSONObject(e.inputJson).optString("question") }.getOrNull()?.takeIf { it.isNotBlank() },
            e.resultJson?.let { runCatching { decode(JSONObject(it)) }.getOrNull() },
            e.errorMessage, e.engine, e.createdAt
        )
    }

    private fun items(list: List<CitedItem>) = JSONArray().apply {
        list.forEach { i -> put(JSONObject().put("text", i.text).put("sources", JSONArray(i.sourceIds)).apply { i.detail?.let { put("detail", it) } }) }
    }

    private fun items(array: JSONArray?) = array.objects().map { o ->
        CitedItem(o.optString("text"), o.optJSONArray("sources").strings(), o.optString("detail").takeIf { it.isNotBlank() })
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}
