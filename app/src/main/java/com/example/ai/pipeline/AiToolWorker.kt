package com.example.ai.pipeline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.database.AiJobEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.domain.FixTerminologyUseCase
import com.example.core.domain.ReprocessTranscriptCleanupUseCase
import com.example.core.model.AiJobStatus
import com.example.core.model.TranscriptAiToolType
import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.modelmanagement.LlmEngineManager
import com.example.ai.tools.DeterministicTranscriptTools
import com.example.ai.tools.ToolFinding
import com.example.ai.tools.ToolOutcome
import com.example.ai.tools.ToolResultJson
import com.example.ai.tools.ToolRunResult
import com.example.ai.tools.ToolScopeJson
import com.example.ai.tools.TranscriptToolEngine
import com.example.core.model.TranscriptCleanupMode
import kotlinx.coroutines.flow.first
import com.example.core.repository.MeetingRepository
import com.example.core.repository.TranscriptRepository
import com.example.core.repository.VocabularyRepository
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Generic background runner for the "✨ AI Tools" menu (Phase 15 §5) — reads one persisted
 * [AiJobEntity] by id and dispatches on its [AiJobEntity.toolType]. This is infrastructure plus,
 * as of Phase 15 §6, two real wired tools: [TranscriptAiToolType.CLEAN_TRANSCRIPT] (delegates to
 * the pre-existing [ReprocessTranscriptCleanupUseCase]) and [TranscriptAiToolType.FIX_TERMINOLOGY]
 * ([FixTerminologyUseCase], built new this phase — no LLM prompt contract needed, since it's a
 * deterministic exact-match replace driven by [com.example.core.repository.VocabularyRepository]'s
 * learned corrections). Every other tool type fails the job honestly with "isn't wired up yet"
 * rather than fabricating a result — the same discipline [MeetingProcessingPipeline] uses for a
 * model that isn't installed.
 *
 * Mirrors [MeetingProcessingWorker]'s shape (persisted, process-death-safe, real progress) but is
 * not a copy-paste of it: that worker is permanently specific to the meeting-processing pipeline,
 * this one is the generic dispatcher every future AI Tools run goes through.
 */
class AiToolWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure(workDataOf(KEY_ERROR to "Missing jobId"))
        val database = MeetMindDatabase.getInstance(applicationContext)
        val aiJobDao = database.aiJobDao()
        val job = aiJobDao.getById(jobId) ?: return Result.failure(workDataOf(KEY_ERROR to "Job $jobId not found"))

        val toolType = runCatching { TranscriptAiToolType.valueOf(job.toolType) }.getOrNull()
            ?: return failJob(aiJobDao, job, "Unknown tool type: ${job.toolType}")

        aiJobDao.insertOrUpdate(job.copy(status = AiJobStatus.RUNNING.name, progressPercent = 0, updatedAt = System.currentTimeMillis()))
        setProgress(workDataOf(KEY_PROGRESS_PERCENT to 0))

        return try {
            when (toolType) {
                // Two tools that pre-date the tool engine and have their own real use cases;
                // routing them through a language model would replace deterministic behaviour
                // with a less reliable version of it.
                TranscriptAiToolType.CLEAN_TRANSCRIPT -> runCleanTranscript(database, job)
                TranscriptAiToolType.FIX_TERMINOLOGY -> runFixTerminology(database, job)

                // Reads back what the processing pipeline already extracted and persisted. Asking
                // a model to find decisions that are already sitting in a table would be slower,
                // cost more, and disagree with what the rest of the app shows.
                TranscriptAiToolType.FIND_DECISIONS,
                TranscriptAiToolType.FIND_QUESTIONS,
                TranscriptAiToolType.FIND_ACTION_ITEMS,
                TranscriptAiToolType.IDENTIFY_TOPICS -> runStoredFindings(database, job, toolType)

                // Arithmetic over segment order — no model involved.
                TranscriptAiToolType.EXPAND_CONTEXT -> runExpandContext(database, job, toolType)

                // Everything else goes through the shared tool engine.
                else -> runModelTool(database, job, toolType)
            }
        } catch (e: CancellationException) {
            // Real cancellation (via WorkManager or AiJobRepository.cancel) — record it honestly
            // rather than as a generic failure.
            aiJobDao.insertOrUpdate(job.copy(status = AiJobStatus.CANCELLED.name, updatedAt = System.currentTimeMillis()))
            Result.failure(workDataOf(KEY_ERROR to "Cancelled"))
        } catch (e: Exception) {
            failJob(aiJobDao, job, e.message ?: "Unknown error running ${toolType.label}")
        }
    }

    /** The one real, wired tool today: re-runs [ReprocessTranscriptCleanupUseCase] through this
     * generic, persisted job queue instead of the ad-hoc `viewModelScope` launch it previously
     * only had (see [com.example.feature.meetingdetail.MeetingDetailScreen]'s `reprocessCleanup`
     * — that call site is not yet switched over; doing so is Phase 6 UI work, this worker is
     * ready for it). */
    private suspend fun runCleanTranscript(database: MeetMindDatabase, job: AiJobEntity): Result {
        val meetingRepository = MeetingRepository(applicationContext, database)
        val transcriptRepository = TranscriptRepository(database)
        val pipeline = MeetingProcessingPipeline(applicationContext, database)
        val useCase = ReprocessTranscriptCleanupUseCase(pipeline, meetingRepository, transcriptRepository)

        val input = runCatching { JSONObject(job.inputPayloadJson) }.getOrDefault(JSONObject())
        val mode = input.optString("cleanupMode", TranscriptCleanupMode.MODERATE.name).let {
            runCatching { TranscriptCleanupMode.valueOf(it) }.getOrDefault(TranscriptCleanupMode.MODERATE)
        }

        setProgress(workDataOf(KEY_PROGRESS_PERCENT to 50))
        val profile = com.example.core.datastore.UserPreferencesManager(applicationContext)
            .preferencesFlow.first().processingProfile
        useCase(job.meetingId, mode, processingProfile = profile)

        val resultJson = JSONObject().put("cleanupMode", mode.name).toString()
        database.aiJobDao().insertOrUpdate(
            job.copy(status = AiJobStatus.SUCCEEDED.name, progressPercent = 100, resultPayloadJson = resultJson, updatedAt = System.currentTimeMillis())
        )
        return Result.success(workDataOf(KEY_RESULT_JOB_ID to job.id))
    }

    /** Applies every learned correction to this meeting's transcript in one pass — see
     * [FixTerminologyUseCase]. */
    private suspend fun runFixTerminology(database: MeetMindDatabase, job: AiJobEntity): Result {
        val transcriptRepository = TranscriptRepository(database)
        val vocabularyRepository = VocabularyRepository(database)
        val useCase = FixTerminologyUseCase(transcriptRepository, vocabularyRepository)

        setProgress(workDataOf(KEY_PROGRESS_PERCENT to 50))
        val changes = useCase(job.meetingId)

        val resultJson = JSONObject().put("segmentsChanged", changes.size).toString()
        database.aiJobDao().insertOrUpdate(
            job.copy(status = AiJobStatus.SUCCEEDED.name, progressPercent = 100, resultPayloadJson = resultJson, updatedAt = System.currentTimeMillis())
        )
        return Result.success(workDataOf(KEY_RESULT_JOB_ID to job.id))
    }

    /**
     * Runs one of the model-backed tools through [TranscriptToolEngine].
     *
     * Which model that is follows the user's processing profile: Internet mode reasons with
     * Gemini, offline mode with whatever local model is installed. Both go through the same engine
     * and the same prompt contract, so a tool does not quietly mean something different depending
     * on which one ran.
     */
    private suspend fun runModelTool(
        database: MeetMindDatabase,
        job: AiJobEntity,
        toolType: TranscriptAiToolType
    ): Result {
        val transcriptRepository = TranscriptRepository(database)
        val transcript = transcriptRepository.getTranscriptDirect(job.meetingId)
        val input = runCatching { JSONObject(job.inputPayloadJson) }.getOrDefault(JSONObject())
        val scope = ToolScopeJson.decode(input)
        val scoped = scope.apply(transcript.segments)

        if (scoped.isEmpty()) {
            return failJob(database.aiJobDao(), job, "There is nothing in the selected part of the transcript to work on.")
        }

        val preferences = com.example.core.datastore.UserPreferencesManager(applicationContext)
            .preferencesFlow.first()
        val meeting = database.meetingDao().getMeetingById(job.meetingId)
        val recordingType = runCatching {
            com.example.core.model.RecordingType.valueOf(meeting?.recordingType ?: "")
        }.getOrDefault(com.example.core.model.RecordingType.GENERAL)

        val engine = buildToolEngine(preferences)
            ?: return failJob(
                database.aiJobDao(), job,
                "No AI model is available for \"${toolType.label}\". Install a local model, or turn on Internet mode in Settings."
            )

        setProgress(workDataOf(KEY_PROGRESS_PERCENT to 40))
        val outcome = engine.run(
            tool = toolType,
            segments = scoped,
            cleanupProfile = recordingType.transcriptCleanupProfile(preferences.transcriptCleanupMode)
        )
        // A tool engine allocation is as heavy as any other LLM allocation; release it before
        // anything else loads, exactly as the processing pipeline does between its stages.
        LlmEngineManager.release()

        return when (outcome) {
            is AiResult.Success -> succeed(
                database, job,
                ToolRunResult(toolType, scope.describe(), outcome.value, engine.engineId())
            )
            else -> failJob(
                database.aiJobDao(), job,
                outcome.describeFailure() ?: "\"${toolType.label}\" could not run."
            )
        }
    }

    /**
     * Picks the model that backs a tool run.
     *
     * Internet mode uses Gemini when a key has actually been entered; without one it falls back to
     * the local model rather than failing, which is the same one-directional fallback the
     * processing pipeline uses. Returns null only when there is genuinely nothing to run with.
     */
    private suspend fun buildToolEngine(
        preferences: com.example.core.datastore.AppPreferencesState
    ): TranscriptToolEngine? {
        val factory = com.example.ai.routing.LanguageModelFactory(
            context = applicationContext,
            modelStorage = com.example.ai.modelmanagement.LocalModelStorage(applicationContext),
            geminiTransport = com.example.ai.cloud.GeminiHttpTransport(
                com.example.ai.cloud.GeminiCredentialStore(applicationContext)
            )
        )
        val resolved = factory.resolve(
            profile = preferences.processingProfile,
            capability = com.example.core.model.ModelCapability.TRANSCRIPT_CLEANUP,
            preferredTier = com.example.core.model.RecordingType.GENERAL
                .transcriptCleanupProfile(preferences.transcriptCleanupMode).preferredModelTier
        ) ?: return null
        return TranscriptToolEngine(languageModel = resolved.languageModel, engineName = resolved.modelId)
    }

    /** Reads back findings the processing pipeline already extracted and persisted. */
    private suspend fun runStoredFindings(
        database: MeetMindDatabase,
        job: AiJobEntity,
        toolType: TranscriptAiToolType
    ): Result {
        val meetingId = job.meetingId
        val findings: List<ToolFinding> = when (toolType) {
            TranscriptAiToolType.FIND_DECISIONS -> database.decisionDao().getDecisionsForMeetingDirect(meetingId)
                .map { ToolFinding(text = it.text, sourceSegmentIds = it.sourceSegmentIdsJson.toIdList(), detail = it.type) }

            TranscriptAiToolType.FIND_QUESTIONS -> database.questionDao().getQuestionsForMeetingDirect(meetingId)
                .map { ToolFinding(text = it.text, sourceSegmentIds = it.sourceSegmentIdsJson.toIdList()) }

            TranscriptAiToolType.FIND_ACTION_ITEMS -> database.actionItemDao().getActionItemsForMeetingDirect(meetingId)
                .map {
                    ToolFinding(
                        text = it.task,
                        sourceSegmentIds = it.sourceSegmentIdsJson.toIdList(),
                        detail = it.assigneeName
                    )
                }

            TranscriptAiToolType.IDENTIFY_TOPICS -> database.topicDao().getTopicsForMeetingDirect(meetingId)
                .mapNotNull { t -> com.example.core.common.Labels.clean(t.name)?.let { ToolFinding(text = it) } }

            else -> emptyList()
        }

        // Resolved against the real transcript so a stored citation that no longer matches a
        // segment - possible after the user split or merged paragraphs - is dropped rather than
        // offered as a jump that goes nowhere.
        val segments = TranscriptRepository(database).getTranscriptDirect(meetingId).segments.associateBy { it.id }
        val resolved = findings.map { finding ->
            val valid = finding.sourceSegmentIds.filter { segments.containsKey(it) }
            finding.copy(
                sourceSegmentIds = valid,
                startMs = valid.mapNotNull { segments[it]?.startMs }.minOrNull()
            )
        }

        return succeed(
            database, job,
            ToolRunResult(toolType, "Whole transcript", ToolOutcome.Findings(resolved), "stored")
        )
    }

    private suspend fun runExpandContext(
        database: MeetMindDatabase,
        job: AiJobEntity,
        toolType: TranscriptAiToolType
    ): Result {
        val segments = TranscriptRepository(database).getTranscriptDirect(job.meetingId).segments
        val input = runCatching { JSONObject(job.inputPayloadJson) }.getOrDefault(JSONObject())
        val scope = ToolScopeJson.decode(input)
        val selected = scope.apply(segments).map { it.id }
        val outcome = DeterministicTranscriptTools.expandContext(segments, selected)
        return succeed(database, job, ToolRunResult(toolType, scope.describe(), outcome, "deterministic"))
    }

    private suspend fun succeed(database: MeetMindDatabase, job: AiJobEntity, result: ToolRunResult): Result {
        database.aiJobDao().insertOrUpdate(
            job.copy(
                status = AiJobStatus.SUCCEEDED.name,
                progressPercent = 100,
                resultPayloadJson = ToolResultJson.encode(result),
                updatedAt = System.currentTimeMillis()
            )
        )
        return Result.success(workDataOf(KEY_RESULT_JOB_ID to job.id))
    }

    private fun String.toIdList(): List<String> = try {
        val array = org.json.JSONArray(this)
        (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    } catch (e: org.json.JSONException) {
        emptyList()
    }

    private suspend fun failJob(aiJobDao: com.example.core.database.AiJobDao, job: AiJobEntity, message: String): Result {
        aiJobDao.insertOrUpdate(job.copy(status = AiJobStatus.FAILED.name, errorMessage = message, updatedAt = System.currentTimeMillis()))
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    companion object {
        /** One unique work chain per job id — independent AI Tools runs (even for the same
         * meeting) don't queue behind each other the way meeting processing intentionally does. */
        fun jobWorkName(jobId: String): String = "meetmind_ai_tool_$jobId"

        const val KEY_JOB_ID = "jobId"
        const val KEY_PROGRESS_PERCENT = "percent"
        const val KEY_RESULT_JOB_ID = "resultJobId"
        const val KEY_ERROR = "error"
    }
}
