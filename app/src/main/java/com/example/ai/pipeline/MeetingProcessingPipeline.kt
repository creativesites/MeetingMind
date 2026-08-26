package com.example.ai.pipeline

import android.content.Context
import android.util.Log
import com.example.ai.asr.SherpaParakeetSpeechRecognizer
import com.example.ai.asr.SpeechRecognizer
import com.example.ai.asr.TranscriptionOptions
import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.diarization.SherpaSpeakerDiarizer
import com.example.ai.diarization.SpeakerDiarizer
import com.example.ai.embeddings.EmbeddingEngine
import com.example.ai.embeddings.LocalEmbeddingEngine
import com.example.ai.llm.MediaPipeLanguageModel
import com.example.ai.llm.MeetingIntelligenceEngine
import com.example.ai.llm.RealMeetingIntelligenceEngine
import com.example.ai.modelmanagement.LlmEngineManager
import com.example.ai.modelmanagement.LlmModelResolver
import com.example.ai.modelmanagement.LocalModelStorage
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelStorage
import com.example.ai.modelmanagement.SherpaEngineManager
import com.example.core.model.ModelCapability
import com.example.ai.vad.SileroVadDetector
import com.example.ai.vad.VoiceActivityDetector
import com.example.core.database.ActionItemEntity
import com.example.core.database.DecisionEntity
import com.example.core.database.EmbeddingEntity
import com.example.core.database.FollowUpEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.ProcessingJobEntity
import com.example.core.database.QuestionEntity
import com.example.core.database.SpeakerEntity
import com.example.core.database.TopicEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.common.MeetingTitleGenerator
import com.example.core.model.MeetingStatus
import com.example.core.model.MeetingSummary
import com.example.core.model.ProcessingStage
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Orchestrates the full local meeting-processing pipeline:
 *
 * ```
 * Audio -> VoiceActivityDetector -> SpeechRecognizer -> SpeakerDiarizer
 *       -> MeetingIntelligenceEngine -> EmbeddingEngine -> Meeting Memory (Room)
 * ```
 *
 * Every AI stage is called through its interface and its [AiResult] is honored honestly:
 * - If speech recognition is unavailable (no local ASR model installed), the pipeline stops,
 *   the recorded audio is kept exactly as-is, and the meeting is marked
 *   [MeetingStatus.MODEL_REQUIRED] — never a fabricated transcript.
 * - Diarization and meeting intelligence are treated as best-effort refinements once a real
 *   transcript exists: if either is unavailable, the pipeline degrades gracefully (keeps the
 *   ASR-assigned segments as-is, leaves summary/decisions/action items/questions empty) rather
 *   than inventing content or blocking the whole meeting.
 *
 * Resource management: ASR, diarization, and LLM inference never run concurrently — each stage's
 * native models are released before the next stage's are loaded, so at most one "heavy" model
 * family (Parakeet, or the diarization pair, or the local LLM) is resident at a time. See
 * docs/AI_ARCHITECTURE.md "Resource Management".
 */
class MeetingProcessingPipeline(
    private val context: Context,
    private val database: MeetMindDatabase,
    private val modelStorage: ModelStorage = LocalModelStorage(context),
    // Every real implementation below honestly self-reports AiResult.ModelUnavailable when its
    // model isn't installed yet — there is deliberately no separate "Unavailable" default to
    // switch between: the real implementation *is* the unavailable-aware implementation.
    private val vad: VoiceActivityDetector = SileroVadDetector(modelStorage),
    private val speechRecognizer: SpeechRecognizer = SherpaParakeetSpeechRecognizer(modelStorage),
    private val diarizer: SpeakerDiarizer = SherpaSpeakerDiarizer(modelStorage),
    private val intelligenceEngine: MeetingIntelligenceEngine = RealMeetingIntelligenceEngine(
        languageModel = MediaPipeLanguageModel(context, modelStorage),
        contextLengthTokens = ModelCatalog.qwen25_1_5bInstruct.contextLengthTokens ?: DEFAULT_LLM_CONTEXT_TOKENS
    ),
    private val embeddingEngine: EmbeddingEngine = LocalEmbeddingEngine(),
    private val cleanupEngine: TranscriptCleanupEngine = RuleBasedTranscriptCleanupEngine(),
    private val structureEngine: TranscriptStructureEngine = DeterministicTranscriptStructureEngine
) {

    suspend fun processMeeting(
        meetingId: String,
        audioFile: File,
        totalDurationMs: Long,
        modelId: String = ModelCatalog.parakeetTdtV3Int8.id,
        expectedSpeakerCount: Int? = null,
        // Null (the default, and what every existing constructor-injected-fake test uses) keeps
        // the intelligenceEngine this pipeline was constructed with. A real caller that knows the
        // user's currently-selected LLM tier passes it explicitly so a lightweight-tier choice
        // (see ModelCatalog.qwen25_0_5bInstruct) is actually honored instead of always running
        // whichever model happened to be the constructor default.
        llmModelId: String? = null,
        onProgress: (step: String, percent: Int, stage: ProcessingStage) -> Unit
    ): MeetingEntity = withContext(Dispatchers.Default) {
        val effectiveIntelligenceEngine = if (llmModelId != null) {
            RealMeetingIntelligenceEngine(
                languageModel = MediaPipeLanguageModel(context, modelStorage, modelId = llmModelId),
                contextLengthTokens = ModelCatalog.entries.find { it.id == llmModelId }?.contextLengthTokens
                    ?: DEFAULT_LLM_CONTEXT_TOKENS
            )
        } else {
            intelligenceEngine
        }
        val meetingDao = database.meetingDao()
        val transcriptDao = database.transcriptDao()
        val speakerDao = database.speakerDao()
        val actionItemDao = database.actionItemDao()
        val decisionDao = database.decisionDao()
        val questionDao = database.questionDao()
        val followUpDao = database.followUpDao()
        val topicDao = database.topicDao()
        val embeddingDao = database.embeddingDao()
        val jobDao = database.processingJobDao()

        val existingMeeting = meetingDao.getMeetingById(meetingId)
            ?: throw IllegalArgumentException("Meeting $meetingId not found")

        // If the app/process was killed mid-run (e.g. by the OS while backgrounded), WorkManager
        // restarts this same worker from scratch — it has no way to resume partway through. Every
        // entity this pipeline writes uses a freshly-generated id, so without this the retry's
        // fresh writes would land ALONGSIDE whatever the killed run already committed (transcript
        // segments, decisions, action items, ...) instead of replacing it, silently doubling the
        // meeting's data. Clearing derived data up front makes every run — first attempt or retry
        // after a kill — write into a clean slate. The audio file itself is never touched here.
        transcriptDao.deleteSegmentsForMeeting(meetingId)
        actionItemDao.deleteActionItemsForMeeting(meetingId)
        decisionDao.deleteDecisionsForMeeting(meetingId)
        questionDao.deleteQuestionsForMeeting(meetingId)
        followUpDao.deleteFollowUpsForMeeting(meetingId)
        topicDao.deleteTopicsForMeeting(meetingId)
        embeddingDao.deleteEmbeddingsForMeeting(meetingId)

        val jobId = "job_$meetingId"
        val jobStartedAt = System.currentTimeMillis()
        suspend fun updateJob(step: String, percent: Int, stage: ProcessingStage, failed: Boolean = false, completed: Boolean = false, error: String? = null) {
            jobDao.insertOrUpdateJob(
                ProcessingJobEntity(
                    id = jobId,
                    meetingId = meetingId,
                    meetingTitle = existingMeeting.title,
                    currentStep = step,
                    progressPercent = percent,
                    isCompleted = completed,
                    isFailed = failed,
                    errorMessage = error,
                    startedAt = jobStartedAt,
                    stage = stage.name
                )
            )
            onProgress(step, percent, stage)
        }

        try {
            updateJob("Preparing audio...", 10, ProcessingStage.PREPARING_AUDIO)

            // STEP 1: Voice Activity Detection (best-effort — unavailable degrades to no filtering)
            updateJob("Detecting speech intervals (VAD)...", 20, ProcessingStage.DETECTING_SPEECH)
            val vadStart = System.currentTimeMillis()
            val speechIntervals = when (val vadResult = vad.detectSpeechIntervals(audioFile, totalDurationMs)) {
                is AiResult.Success -> vadResult.value
                else -> emptyList() // No VAD model installed: ASR will process the whole clip.
            }
            Log.d(PERF_TAG, "VAD: ${System.currentTimeMillis() - vadStart}ms, ${speechIntervals.size} intervals")

            // STEP 2: Local Speech Recognition — the required gate. No model = no fabricated transcript.
            updateJob("Transcribing with local AI...", 35, ProcessingStage.TRANSCRIBING)
            val asrStart = System.currentTimeMillis()
            val asrResult = speechRecognizer.transcribe(
                audioFile = audioFile,
                totalDurationMs = totalDurationMs,
                meetingId = meetingId,
                speechIntervals = speechIntervals,
                options = TranscriptionOptions(modelId = modelId),
                onProgress = { prog, status ->
                    val overall = (35 + (prog * 20)).toInt()
                    onProgress(status, overall, ProcessingStage.TRANSCRIBING)
                }
            )
            val asrDurationMs = System.currentTimeMillis() - asrStart
            val rtf = if (totalDurationMs > 0) asrDurationMs.toDouble() / totalDurationMs.toDouble() else null
            Log.d(PERF_TAG, "ASR: ${asrDurationMs}ms for ${totalDurationMs}ms audio (RTF=${rtf?.let { "%.3f".format(it) } ?: "n/a"})")
            // Free the ~650MB Parakeet allocation before diarization's models load — never keep
            // two heavy model families resident at once.
            SherpaEngineManager.releaseAll()

            val rawSegments: List<TranscriptSegment> = when (asrResult) {
                is AiResult.Success -> asrResult.value
                else -> {
                    // No local ASR model installed (or the device/memory can't run it): stop here.
                    // The audio recording itself is untouched and remains fully accessible.
                    val message = asrResult.describeFailure() ?: "Local speech recognition is unavailable."
                    val isCrash = asrResult is AiResult.Failed
                    updateJob(
                        step = if (isCrash) "Failed" else "Speech recognition model required",
                        percent = 100,
                        stage = if (isCrash) ProcessingStage.FAILED else ProcessingStage.FAILED,
                        failed = isCrash,
                        completed = !isCrash,
                        error = message
                    )
                    val updatedMeeting = existingMeeting.copy(
                        status = (if (isCrash) MeetingStatus.ERROR else MeetingStatus.MODEL_REQUIRED).name,
                        durationMs = totalDurationMs,
                        summaryText = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    meetingDao.updateMeeting(updatedMeeting)
                    return@withContext updatedMeeting
                }
            }

            // Resolved here (rather than at STEP 4, where it used to live) because the structure
            // engine below needs it too — recording type governs paragraph-merging behavior just
            // as much as it governs what the LLM is asked to extract.
            val recordingType = try {
                com.example.core.model.RecordingType.valueOf(existingMeeting.recordingType)
            } catch (e: Exception) {
                com.example.core.model.RecordingType.GENERAL
            }

            // STEP 3: Speaker Diarization — skipped entirely for a confirmed single speaker.
            // Running full multi-speaker clustering on a recording the user already told
            // MeetingMind is solo (Idea, Voice Memo, a "Just me" pick) wastes the segmentation +
            // embedding models' load/inference cost for a result that would only ever be thrown
            // away, and exposes the transcript to exactly the fragmentation risk diarization
            // exists to avoid: a real single-speaker recording getting split into several
            // fabricated "speakers" from acoustic noise alone. ASR's segments already carry no
            // speakerId, which correctly renders as no speaker label — nothing is invented here.
            val speakerLabelledSegments: List<TranscriptSegment>
            if (expectedSpeakerCount == 1) {
                updateJob("Single speaker confirmed — skipping speaker detection...", 55, ProcessingStage.DIARIZING)
                speakerLabelledSegments = rawSegments
                Log.d(PERF_TAG, "Diarization: skipped (confirmed single speaker)")
            } else {
                updateJob("Identifying distinct speakers...", 55, ProcessingStage.DIARIZING)
                val diarizeStart = System.currentTimeMillis()
                speakerLabelledSegments = when (val diarizeResult = diarizer.diarize(audioFile, totalDurationMs, rawSegments, expectedSpeakerCount = expectedSpeakerCount)) {
                    is AiResult.Success -> diarizeResult.value
                    else -> rawSegments // No diarization model installed: keep ASR's segments as-is.
                }
                Log.d(PERF_TAG, "Diarization: ${System.currentTimeMillis() - diarizeStart}ms")
            }

            // Regroup the VAD-sized fragments into readable paragraphs. This runs *after*
            // diarization on purpose: speaker identity is what decides where a paragraph may
            // legitimately continue, so grouping earlier would risk merging across a speaker
            // change that diarization hadn't reported yet. Recording type and single-speaker mode
            // both shape how aggressively fragments merge — see TranscriptStructureEngine.
            val paragraphedSegments = structureEngine.structure(speakerLabelledSegments, recordingType, singleSpeakerMode = expectedSpeakerCount == 1)
            Log.d(PERF_TAG, "Structuring: ${speakerLabelledSegments.size} fragments -> ${paragraphedSegments.size} paragraphs")
            // Free the segmentation+embedding models before the LLM's much larger allocation loads.
            SherpaEngineManager.releaseAll()

            // STEP 3.5: Transcript cleanup (best-effort — a rejected/unavailable candidate simply
            // leaves the real ASR/diarized text as-is). Every candidate is validated before it's
            // ever attached; the raw text a paragraph carries is never overwritten, only offered a
            // separate cleanedText alongside it. Never run for an already-user-edited segment —
            // that can't happen on this first-run path (nothing has been edited yet at this point
            // in a brand-new meeting's life), but the check is here so this stage stays correct if
            // this pipeline is ever reused for a reprocess-after-edit flow.
            updateJob("Cleaning up your transcript...", 58, ProcessingStage.CLEANING_TRANSCRIPT)
            val ruleCleanedSegments = applyTranscriptCleanup(paragraphedSegments, cleanupEngine)

            // STEP 3.6: AI transcript cleanup — a validated *upgrade* over the rule-based pass
            // above, never a replacement for it: this only ever overwrites a segment's cleanedText
            // when it finds a real cleanup-capable model installed AND that model's candidate
            // passes TranscriptQualityValidator against the real raw text. No model installed, no
            // candidate accepted, or a user-edited segment (never even offered) all fall back to
            // exactly what the rule-based pass already produced — never a fabricated status.
            val aiCleanupModelId = LlmModelResolver.resolveSmallestInstalled(modelStorage, ModelCapability.TRANSCRIPT_CLEANUP)
            val diarizedSegments = if (aiCleanupModelId != null) {
                updateJob("Refining transcript with AI...", 60, ProcessingStage.CLEANING_TRANSCRIPT)
                val aiStart = System.currentTimeMillis()
                val aiCleanupEngine = RealTranscriptAiCleanupEngine(
                    languageModel = MediaPipeLanguageModel(context, modelStorage, modelId = aiCleanupModelId),
                    contextLengthTokens = ModelCatalog.entries.find { it.id == aiCleanupModelId }?.contextLengthTokens
                        ?: DEFAULT_LLM_CONTEXT_TOKENS
                )
                val aiResult = aiCleanupEngine.clean(ruleCleanedSegments, recordingType, singleSpeakerMode = expectedSpeakerCount == 1)
                // Free the cleanup model before the (possibly different) intelligence model loads —
                // at most one LLM allocation resident at a time, same discipline as every other stage.
                LlmEngineManager.release()
                when (aiResult) {
                    is AiResult.Success -> {
                        val r = aiResult.value
                        Log.d(PERF_TAG, "AI cleanup: ${System.currentTimeMillis() - aiStart}ms, ${r.chunksAttempted} chunks, ${r.paragraphsAccepted} accepted, ${r.paragraphsFallback} fallback")
                        r.segments
                    }
                    else -> {
                        Log.d(PERF_TAG, "AI cleanup: unavailable (${aiResult.describeFailure() ?: "no reason given"}) — using rule-based cleanup only")
                        ruleCleanedSegments
                    }
                }
            } else {
                Log.d(PERF_TAG, "AI cleanup: skipped — no installed model has TRANSCRIPT_CLEANUP capability")
                ruleCleanedSegments
            }

            // STEP 4: Meeting Intelligence (best-effort — unavailable leaves summary/insights empty)
            val intelligenceProfile = recordingType.intelligenceProfile()
            // What this step is actually doing depends on what this recording type actually
            // produces — a Lecture never claims to be "extracting decisions & action items".
            updateJob(intelligenceProfile.analyzingStageLabel, 70, ProcessingStage.ANALYZING)
            val transcriptDomain = Transcript(
                meetingId = meetingId,
                segments = diarizedSegments,
                language = "en",
                createdAt = existingMeeting.createdAt
            )

            val llmStart = System.currentTimeMillis()
            // A deterministic, non-fabricated fallback built only from the recording's real type
            // and creation date — used both as processMeeting's own synthesis fallback and as the
            // final title whenever the model is unavailable or its title candidate doesn't
            // validate. Title generation is folded into this one structured-JSON call (see
            // RealMeetingIntelligenceEngine's synthesis prompt) instead of running a second,
            // separate LLM pass purely for a title.
            val fallbackTitle = MeetingTitleGenerator.deterministicFallbackTitle(recordingType, existingMeeting.createdAt)
            val summaryResult = effectiveIntelligenceEngine.processMeeting(transcriptDomain, fallbackTitle, recordingType, existingMeeting.customContext)
            val summary = (summaryResult as? AiResult.Success)?.value
            val generatedTitle = MeetingTitleGenerator.sanitizeAndValidate(summary?.title) ?: fallbackTitle
            Log.d(PERF_TAG, "LLM (intelligence incl. title): ${System.currentTimeMillis() - llmStart}ms")
            // Free the ~1.5GB local LLM allocation as soon as intelligence extraction is done.
            LlmEngineManager.release()

            // STEP 5: Local Embeddings (real, on-device — only computed over the real transcript text)
            updateJob("Indexing transcript for semantic search...", 92, ProcessingStage.SAVING_RESULTS)

            val embeddingEntities = mutableListOf<EmbeddingEntity>()
            for (seg in diarizedSegments) {
                val vec = embeddingEngine.embed(seg.text)
                embeddingEntities.add(
                    EmbeddingEntity(
                        id = UUID.randomUUID().toString(),
                        meetingId = meetingId,
                        segmentId = seg.id,
                        textChunk = seg.text,
                        vectorData = vec.joinToString(","),
                        startMs = seg.startMs,
                        endMs = seg.endMs
                    )
                )
            }
            if (summary != null && summary.summary.isNotBlank()) {
                val summaryVec = embeddingEngine.embed(summary.summary)
                embeddingEntities.add(
                    EmbeddingEntity(
                        id = UUID.randomUUID().toString(),
                        meetingId = meetingId,
                        segmentId = null,
                        textChunk = summary.summary,
                        vectorData = summaryVec.joinToString(","),
                        startMs = 0L,
                        endMs = totalDurationMs
                    )
                )
            }

            // Persist the real transcript (always — ASR succeeded to reach this point)
            val segmentEntities = diarizedSegments.map {
                TranscriptSegmentEntity(
                    id = it.id,
                    meetingId = meetingId,
                    speakerId = it.speakerId,
                    speakerName = it.speakerName,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    text = it.text,
                    confidence = it.confidence,
                    cleanedText = it.cleanedText,
                    sourceSegmentIdsJson = it.sourceSegmentIds.toJsonArrayString()
                )
            }
            transcriptDao.insertSegments(segmentEntities)

            // Only real, non-null speaker IDs become Speaker rows — with diarization unavailable
            // this is correctly empty rather than inventing a "Speaker 1" identity for a
            // single-track transcript.
            val uniqueSpeakers = diarizedSegments
                .filter { it.speakerId != null }
                .distinctBy { it.speakerId }
                .map { seg ->
                    val speakerIndex = seg.speakerId!!.substringAfterLast('_').toIntOrNull() ?: 0
                    SpeakerEntity(
                        id = seg.speakerId,
                        meetingId = meetingId,
                        speakerIndex = speakerIndex,
                        originalLabel = seg.speakerName ?: seg.speakerId,
                        customName = seg.speakerName ?: seg.speakerId,
                        colorHex = SPEAKER_COLORS[speakerIndex % SPEAKER_COLORS.size],
                        confidence = null // sherpa-onnx's diarization API doesn't provide a per-speaker confidence score.
                    )
                }
            speakerDao.insertSpeakers(uniqueSpeakers)

            // Only persist intelligence output when it's real (summary != null)
            if (summary != null) {
                persistIntelligence(meetingId, summary, actionItemDao, decisionDao, questionDao, followUpDao, topicDao)
            }

            embeddingDao.insertEmbeddings(embeddingEntities)

            val updatedMeeting = existingMeeting.copy(
                title = generatedTitle,
                status = MeetingStatus.READY.name,
                durationMs = totalDurationMs,
                participantCount = uniqueSpeakers.size.coerceAtLeast(1),
                summaryText = summary?.summary,
                updatedAt = System.currentTimeMillis()
            )
            meetingDao.updateMeeting(updatedMeeting)

            updateJob(
                step = "Completed",
                percent = 100,
                stage = ProcessingStage.COMPLETED,
                completed = true,
                error = if (summary == null) {
                    "Transcript ready. " + (summaryResult.describeFailure() ?: "No local meeting intelligence model is installed.")
                } else null
            )

            updatedMeeting
        } catch (e: CancellationException) {
            // Once a coroutine is cancelled, any further suspend call (Room writes included) can
            // throw CancellationException immediately instead of running — without NonCancellable
            // this cleanup could silently no-op and leave the meeting stuck mid-processing.
            withContext(kotlinx.coroutines.NonCancellable) {
                SherpaEngineManager.releaseAll()
                LlmEngineManager.release()
                updateJob("Cancelled", 0, ProcessingStage.CANCELLED, failed = true, error = "Processing was cancelled by user")
                meetingDao.updateMeeting(existingMeeting.copy(status = MeetingStatus.ERROR.name))
            }
            throw e
        } catch (e: Exception) {
            withContext(kotlinx.coroutines.NonCancellable) {
                SherpaEngineManager.releaseAll()
                LlmEngineManager.release()
                updateJob("Failed", 0, ProcessingStage.FAILED, failed = true, error = e.localizedMessage ?: "Unknown processing error")
                meetingDao.updateMeeting(existingMeeting.copy(status = MeetingStatus.ERROR.name))
            }
            throw e
        }
    }

    private suspend fun persistIntelligence(
        meetingId: String,
        summary: MeetingSummary,
        actionItemDao: com.example.core.database.ActionItemDao,
        decisionDao: com.example.core.database.DecisionDao,
        questionDao: com.example.core.database.QuestionDao,
        followUpDao: com.example.core.database.FollowUpDao,
        topicDao: com.example.core.database.TopicDao
    ) {
        actionItemDao.insertActionItems(
            summary.actionItems.map {
                ActionItemEntity(
                    id = it.id,
                    meetingId = meetingId,
                    task = it.task,
                    assigneeSpeakerId = it.assigneeSpeakerId,
                    assigneeName = it.assigneeName,
                    deadline = it.deadline,
                    confidence = it.confidence,
                    isCompleted = it.isCompleted,
                    sourceSegmentIdsJson = it.sourceSegmentIds.toJsonArrayString()
                )
            }
        )
        decisionDao.insertDecisions(
            summary.decisions.map {
                DecisionEntity(
                    id = it.id,
                    meetingId = meetingId,
                    text = it.text,
                    type = it.type.name,
                    confidence = it.confidence,
                    sourceSegmentIdsJson = it.sourceSegmentIds.toJsonArrayString()
                )
            }
        )
        questionDao.insertQuestions(
            summary.questions.map {
                QuestionEntity(
                    id = it.id,
                    meetingId = meetingId,
                    text = it.text,
                    askedBySpeakerId = it.askedBySpeakerId,
                    resolved = it.resolved,
                    answer = it.answer,
                    sourceSegmentIdsJson = it.sourceSegmentIds.toJsonArrayString()
                )
            }
        )
        followUpDao.insertFollowUps(
            summary.followUps.map {
                FollowUpEntity(
                    id = it.id,
                    meetingId = meetingId,
                    description = it.description,
                    ownerSpeakerId = it.ownerSpeakerId,
                    deadline = it.deadline,
                    sourceSegmentIdsJson = it.sourceSegmentIds.toJsonArrayString()
                )
            }
        )
        topicDao.insertTopics(
            summary.topics.map {
                TopicEntity(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    name = it,
                    relevance = 1.0f
                )
            }
        )
    }

    private fun List<String>.toJsonArrayString(): String {
        val array = org.json.JSONArray()
        forEach { array.put(it) }
        return array.toString()
    }

    private companion object {
        const val PERF_TAG = "MeetMindPerf"
        const val DEFAULT_LLM_CONTEXT_TOKENS = 4096
        val SPEAKER_COLORS = listOf("#3B82F6", "#8B5CF6", "#10B981", "#F59E0B", "#EF4444", "#06B6D4")
    }
}
