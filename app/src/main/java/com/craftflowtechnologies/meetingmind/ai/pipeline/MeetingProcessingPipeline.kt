package com.craftflowtechnologies.meetingmind.ai.pipeline

import com.craftflowtechnologies.meetingmind.ai.asr.AsrCheckpointStore

import android.content.Context
import android.util.Log
import com.craftflowtechnologies.meetingmind.ai.asr.SherpaParakeetSpeechRecognizer
import com.craftflowtechnologies.meetingmind.ai.asr.SpeechRecognizer
import com.craftflowtechnologies.meetingmind.ai.asr.TranscriptionOptions
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.diarization.SherpaSpeakerDiarizer
import com.craftflowtechnologies.meetingmind.ai.diarization.SpeakerDiarizer
import com.craftflowtechnologies.meetingmind.ai.diarization.DiarizationOutcome
import com.craftflowtechnologies.meetingmind.ai.diarization.SPEAKERS_NOT_SEPARATED_MESSAGE
import com.craftflowtechnologies.meetingmind.ai.diarization.cleanupBudgetMs
import com.craftflowtechnologies.meetingmind.ai.diarization.diarizationBudgetMs
import com.craftflowtechnologies.meetingmind.ai.diarization.reconciliationBudgetMs
import com.craftflowtechnologies.meetingmind.ai.diarization.runDiarizationWithBudget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.craftflowtechnologies.meetingmind.ai.embeddings.EmbeddingEngine
import com.craftflowtechnologies.meetingmind.ai.embeddings.LocalEmbeddingEngine
import com.craftflowtechnologies.meetingmind.ai.llm.MediaPipeLanguageModel
import com.craftflowtechnologies.meetingmind.ai.llm.MeetingIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.llm.RealMeetingIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmModelResolver
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.SherpaEngineManager
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTranscriptionEngine
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiHttpTransport
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.diarization.defaultSpeakerNameFor
import com.craftflowtechnologies.meetingmind.ai.diarization.speakerIndexOf
import com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalTranscriptAssembler
import com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalWord
import com.craftflowtechnologies.meetingmind.ai.transcript.DiarizationTurn
import com.craftflowtechnologies.meetingmind.ai.transcript.TranscriptMetadata
import com.craftflowtechnologies.meetingmind.ai.transcript.TranscriptQualityEvaluator
import com.craftflowtechnologies.meetingmind.ai.transcript.toJson
import com.craftflowtechnologies.meetingmind.ai.transcript.WordSpeakerAttributor
import com.craftflowtechnologies.meetingmind.ai.vad.SileroVadDetector
import com.craftflowtechnologies.meetingmind.ai.vad.VoiceActivityDetector
import com.craftflowtechnologies.meetingmind.core.database.ActionItemEntity
import com.craftflowtechnologies.meetingmind.core.database.DecisionEntity
import com.craftflowtechnologies.meetingmind.core.database.EmbeddingEntity
import com.craftflowtechnologies.meetingmind.core.database.FollowUpEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.ProcessingJobEntity
import com.craftflowtechnologies.meetingmind.core.database.QuestionEntity
import com.craftflowtechnologies.meetingmind.core.database.SpeakerEntity
import com.craftflowtechnologies.meetingmind.core.database.TopicEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.common.MeetingTitleGenerator
import com.craftflowtechnologies.meetingmind.core.repository.toWordsJson
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.MeetingSummary
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Orchestrates the full local meeting-processing pipeline:
 *
 * ```
 * Audio -> VoiceActivityDetector (speech regions)
 *       -> SpeechRecognizer        (words + timestamps, decoded over overlapping context windows)
 *       -> SpeakerDiarizer         (acoustic speaker turns)
 *       -> WordSpeakerAttributor   (word -> speaker, with a confidence per word)
 *       -> CanonicalTranscriptAssembler (turns -> utterances -> paragraphs)
 *       -> MeetingIntelligenceEngine -> EmbeddingEngine -> Meeting Memory (Room)
 *
 * The canonical transcript is the source of truth; the `TranscriptSegment` list the rest of the
 * app consumes is a projection of it. See docs/TRANSCRIPTION_OVERHAUL.md.
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
    private val speechRecognizer: SpeechRecognizer = SherpaParakeetSpeechRecognizer(
        modelStorage,
        checkpoints = AsrCheckpointStore(asrCheckpointDir(context))
    ),
    private val diarizer: SpeakerDiarizer = SherpaSpeakerDiarizer(modelStorage),
    private val intelligenceEngine: MeetingIntelligenceEngine = RealMeetingIntelligenceEngine(
        languageModel = MediaPipeLanguageModel(context, modelStorage),
        contextLengthTokens = ModelCatalog.qwen25_1_5bInstruct.contextLengthTokens ?: DEFAULT_LLM_CONTEXT_TOKENS
    ),
    private val embeddingEngine: EmbeddingEngine = LocalEmbeddingEngine(),
    private val cleanupEngine: TranscriptCleanupEngine = RuleBasedTranscriptCleanupEngine(),
    private val structureEngine: TranscriptStructureEngine = DeterministicTranscriptStructureEngine,
    /**
     * The one object in this pipeline that can reach the network, and only ever from
     * [com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.INTERNET].
     *
     * The real transport reads its credential from [GeminiCredentialStore] — the key the user
     * entered on this device. Nothing is embedded in the app: the repository and its published
     * releases are public, and a key compiled into an APK is recoverable from that APK. With no
     * key entered, [GeminiHttpTransport] reports Internet mode unavailable and the pipeline falls
     * back to on-device processing, exactly as it does for a quota error.
     */
    private val geminiTransport: GeminiTransport = com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(context),
    private val cloudTranscriptionEngine: GeminiTranscriptionEngine = GeminiTranscriptionEngine(geminiTransport),
    private val cloudIntelligenceEngine: MeetingIntelligenceEngine = GeminiIntelligenceEngine(geminiTransport),
    /** The single decision point for which language model does secondary AI work (cleanup,
     * speaker reconciliation). See [com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory]. */
    private val languageModelFactory: com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory =
        com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory(context, modelStorage, geminiTransport)
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
        // Defaults to the safest (Conservative-equivalent) behavior for any caller that doesn't
        // pass one explicitly (existing tests included) — a real caller passes the user's actual
        // preference (see ProcessingScreen), which defaults to Moderate (see
        // core/datastore/UserPreferences.kt's DEFAULT_TRANSCRIPT_CLEANUP_MODE).
        cleanupMode: com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode = com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.CONSERVATIVE,
        diarizationStrategy: com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy = com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AUTO,
        // Defaults to the private profile. A caller that wants cloud processing must ask for it
        // explicitly; nothing here can decide on the user's behalf that their audio should leave
        // the device.
        processingProfile: ProcessingProfile = ProcessingProfile.OFFLINE,
        onProgress: (step: String, percent: Int, stage: ProcessingStage) -> Unit
    ): MeetingEntity = withContext(Dispatchers.Default) {
        val localIntelligenceEngine = if (llmModelId != null) {
            RealMeetingIntelligenceEngine(
                languageModel = MediaPipeLanguageModel(context, modelStorage, modelId = llmModelId),
                contextLengthTokens = ModelCatalog.entries.find { it.id == llmModelId }?.contextLengthTokens
                    ?: DEFAULT_LLM_CONTEXT_TOKENS
            )
        } else {
            intelligenceEngine
        }
        val effectiveIntelligenceEngine = if (processingProfile == ProcessingProfile.INTERNET) {
            // Reasoning follows transcription: an Internet-mode meeting is analysed by the cloud
            // model. It is given the transcript, never the audio — the transcription engine
            // already decided what was said, and a reasoning model re-hearing the recording would
            // give the app two disagreeing accounts of the same meeting.
            //
            // With the local engine behind it: a cloud failure (no key yet, quota, no signal)
            // yields a local summary rather than none at all.
            com.craftflowtechnologies.meetingmind.ai.llm.FallbackMeetingIntelligenceEngine(
                primary = cloudIntelligenceEngine,
                fallback = localIntelligenceEngine
            )
        } else {
            localIntelligenceEngine
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
        database.signalDao().deleteForMeeting(meetingId)

        val jobId = "job_$meetingId"
        val jobStartedAt = System.currentTimeMillis()
        val monotonic = MonotonicProgress()
        // Set when speaker separation is abandoned; stays on the job row (as a non-failed note)
        // so the later stages' status lines don't hide it.
        var speakerNote: String? = null
        suspend fun updateJob(step: String, percent: Int, stage: ProcessingStage, failed: Boolean = false, completed: Boolean = false, error: String? = null) {
            val terminal = failed || completed
            val shown = if (terminal) percent else monotonic.next(percent)
            jobDao.insertOrUpdateJob(
                ProcessingJobEntity(
                    id = jobId,
                    meetingId = meetingId,
                    meetingTitle = existingMeeting.title,
                    currentStep = step,
                    progressPercent = shown,
                    isCompleted = completed,
                    isFailed = failed,
                    errorMessage = error ?: speakerNote.takeIf { !terminal },
                    startedAt = jobStartedAt,
                    stage = stage.name
                )
            )
            onProgress(step, shown, stage)
        }
        fun emit(step: String, rawPercent: Int, stage: ProcessingStage) = onProgress(step, monotonic.next(rawPercent), stage)

        try {
            updateJob("Preparing audio...", 10, ProcessingStage.PREPARING_AUDIO)

            // Terminology this recording is likely to contain, for engines that can bias
            // recognition toward known terms (brief §19). Built from things the app already knows
            // are real — what the user called this recording, the focus they typed, and the
            // corrections they have made before — never from guesses about the content. Engines
            // that cannot use hints ignore them; nothing downstream is allowed to rewrite a
            // recognized word to match a hint.
            val vocabularyHints = buildVocabularyHints(existingMeeting)

            // INTERNET profile: cloud transcription replaces STEPS 1-3 entirely — Gemini's
            // verbatim pass already returns words, timestamps and speakers, so running local VAD,
            // ASR and diarization first would be duplicated work whose output is thrown away.
            //
            // A cloud failure does not end the run: the router deliberately keeps the local routes
            // available inside the Internet profile (see AiModelRouter.routesFor) so a quota
            // error, a timeout or an unconfigured build falls through to on-device processing and
            // the user still gets a transcript. That is the only direction the fallback ever
            // runs — OFFLINE has no cloud route and can never fall the other way.
            var cloudWords: List<CanonicalWord>? = null
            var localRunWords: List<CanonicalWord>? = null
            var cloudChunkCount = 1
            var cloudDegradedReason: String? = null
            var effectiveProfile = processingProfile
            var vadCache: List<com.craftflowtechnologies.meetingmind.ai.transcript.SpeechRegion>? = null
            if (processingProfile == ProcessingProfile.INTERNET) {
                updateJob("Transcribing with Google's AI...", 30, ProcessingStage.TRANSCRIBING)
                val cloudStart = System.currentTimeMillis()
                // The length the importer guessed can be wrong (it falls back to a minute); the
                // container's own figure decides how the recording is split for Gemini.
                val measuredMs = com.craftflowtechnologies.meetingmind.core.audio.CloudAudio.durationMs(audioFile)
                com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.attach(context)
                com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.add(
                    "Processing \"${existingMeeting.title.take(60)}\": ${audioFile.name}, ${audioFile.length() / 1024} KB, " +
                        "format ${com.craftflowtechnologies.meetingmind.core.audio.CloudAudio.sniffMime(audioFile) ?: "unknown"}, length ${(measuredMs ?: totalDurationMs) / 1000}s" +
                        (if (measuredMs == null) " (from import; file length unreadable)" else "")
                )
                // Gemini first, the phone behind it — and either can take over from the other:
                // what's finished is saved region by region, so a switch (yours, or a dropped
                // connection) continues from there instead of starting again.
                val runner = com.craftflowtechnologies.meetingmind.ai.transcription.TranscriptionRunner(
                    store = com.craftflowtechnologies.meetingmind.ai.transcription.TranscriptionCheckpoints(transcriptionRegionsDir(context)),
                    gemini = cloudTranscriptionEngine,
                    local = { gaps, onRegion, onProg ->
                        val speech = vadCache ?: run {
                            updateJob("Detecting speech intervals (VAD)...", 20, ProcessingStage.DETECTING_SPEECH)
                            (when (val v = vad.detectSpeechIntervals(audioFile, totalDurationMs)) { is AiResult.Success -> v.value; else -> emptyList() }).also { vadCache = it }
                        }
                        val restricted = com.craftflowtechnologies.meetingmind.ai.transcription.Coverage.restrict(speech, gaps)
                        if (speech.isNotEmpty() && restricted.isEmpty()) AiResult.Success(emptyList())
                        else {
                            updateJob("Transcribing with local AI...", 35, ProcessingStage.TRANSCRIBING)
                            speechRecognizer.transcribe(
                                audioFile = audioFile, totalDurationMs = totalDurationMs, meetingId = meetingId,
                                speechRegions = restricted,
                                options = TranscriptionOptions(modelId = modelId, vocabularyHints = vocabularyHints, onWindowDone = onRegion),
                                onProgress = { prog, status -> onProg(prog, status); emit(status, ProcessingProgress.asr(prog.toFloat()), ProcessingStage.TRANSCRIBING) }
                            )
                        }
                    }
                )
                val outcome = try {
                    runner.run(
                        meetingId = meetingId, audioFile = audioFile, totalMs = measuredMs ?: totalDurationMs,
                        start = com.craftflowtechnologies.meetingmind.ai.transcription.TranscriptionRoute.GEMINI, vocabularyHints = vocabularyHints,
                        onProgress = { progress, status -> emit(status, ProcessingProgress.cloudAsr(progress.toFloat()), ProcessingStage.TRANSCRIBING) },
                        // Finished parts are readable at once: saved as provisional segments, replaced
                        // by the full transcript when it's done.
                        onPartial = { words, done, total ->
                            val type = runCatching { com.craftflowtechnologies.meetingmind.core.model.RecordingType.valueOf(existingMeeting.recordingType) }.getOrDefault(com.craftflowtechnologies.meetingmind.core.model.RecordingType.GENERAL)
                            val partial = CanonicalTranscriptAssembler.projectToSegments(
                                CanonicalTranscriptAssembler.assemble(
                                    meetingId = meetingId, words = words, recordingType = type, singleSpeakerMode = expectedSpeakerCount == 1,
                                    metadata = TranscriptMetadata(meetingId = meetingId, language = existingMeeting.language, processingMode = processingProfile.name,
                                        transcriptionEngine = com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter.GEMINI_TRANSCRIBE_MODEL),
                                    speakerNameFor = { id -> defaultSpeakerNameFor(speakerIndexOf(id)) },
                                    structureEngine = structureEngine
                                )
                            )
                            transcriptDao.deletePartialSegments(meetingId)
                            transcriptDao.insertSegments(partial.mapIndexed { i, seg ->
                                TranscriptSegmentEntity(
                                    id = "partial_${i}_${seg.id}", meetingId = meetingId, speakerId = seg.speakerId, speakerName = seg.speakerName,
                                    startMs = seg.startMs, endMs = seg.endMs, text = seg.text, confidence = seg.confidence
                                )
                            })
                            updateJob("Transcribed $done of $total parts — you can start reading", ProcessingProgress.cloudAsr(done.toFloat() / total), ProcessingStage.TRANSCRIBING)
                        }
                    )
                } finally {
                    com.craftflowtechnologies.meetingmind.ai.transcription.TranscriptionRoutes.publish(meetingId, null)
                }
                cloudDegradedReason = outcome.geminiFailure
                if (outcome.complete && outcome.usedGemini) {
                    cloudWords = outcome.words()
                    cloudChunkCount = outcome.regions.count { it.route == com.craftflowtechnologies.meetingmind.ai.transcription.TranscriptionRoute.GEMINI }.coerceAtLeast(1)
                    if (outcome.usedLocal) cloudDegradedReason = "Part of this recording was transcribed on this phone; those parts have no speaker labels."
                    Log.d(PERF_TAG, "Transcription: ${System.currentTimeMillis() - cloudStart}ms, gemini=${outcome.usedGemini}, local=${outcome.usedLocal}")
                } else if (outcome.complete) {
                    localRunWords = outcome.words()
                    effectiveProfile = ProcessingProfile.OFFLINE
                } else {
                    cloudDegradedReason = outcome.failure ?: outcome.geminiFailure
                    Log.d(PERF_TAG, "Transcription incomplete: $cloudDegradedReason")
                }
            }

            // STEP 1: Speech activity detection. Answers "where is speech?" and nothing else —
            // a region boundary is never a transcript boundary. Best-effort: with no VAD model
            // installed the recording is windowed end to end instead of being skipped.
            val vadStart = System.currentTimeMillis()
            val speechRegions = if (cloudWords != null || localRunWords != null) emptyList() else {
                updateJob("Detecting speech intervals (VAD)...", 20, ProcessingStage.DETECTING_SPEECH)
                when (val vadResult = vad.detectSpeechIntervals(audioFile, totalDurationMs)) {
                    is AiResult.Success -> vadResult.value
                    else -> emptyList()
                }
            }
            val vadDurationMs = System.currentTimeMillis() - vadStart
            Log.d(PERF_TAG, "VAD: ${vadDurationMs}ms, ${speechRegions.size} regions")

            // STEP 2: Speech recognition — the required gate. No model = no fabricated transcript.
            // Produces a single chronological word stream; nothing about ASR's decode windows
            // survives into the transcript's structure.
            val asrStart = System.currentTimeMillis()
            val asrResult: AiResult<List<CanonicalWord>> = if (cloudWords != null) {
                AiResult.Success(cloudWords)
            } else if (localRunWords != null) {
                AiResult.Success(localRunWords)
            } else if (processingProfile == ProcessingProfile.INTERNET) {
                // Both engines were tried and the recording is not fully transcribed.
                AiResult.Failed(cloudDegradedReason ?: "Transcription didn't finish.")
            } else {
                updateJob("Transcribing with local AI...", 35, ProcessingStage.TRANSCRIBING)
                speechRecognizer.transcribe(
                    audioFile = audioFile,
                    totalDurationMs = totalDurationMs,
                    meetingId = meetingId,
                    speechRegions = speechRegions,
                    options = TranscriptionOptions(modelId = modelId, vocabularyHints = vocabularyHints),
                    onProgress = { prog, status ->
                        emit(status, ProcessingProgress.asr(prog.toFloat()), ProcessingStage.TRANSCRIBING)
                    }
                )
            }
            val asrDurationMs = System.currentTimeMillis() - asrStart
            val rtf = if (totalDurationMs > 0) asrDurationMs.toDouble() / totalDurationMs.toDouble() else null
            Log.d(PERF_TAG, "ASR: ${asrDurationMs}ms for ${totalDurationMs}ms audio (RTF=${rtf?.let { "%.3f".format(it) } ?: "n/a"})")
            // Free the ~650MB Parakeet allocation before diarization's models load — never keep
            // two heavy model families resident at once.
            SherpaEngineManager.releaseAll()

            val rawWords: List<CanonicalWord> = when (asrResult) {
                is AiResult.Success -> asrResult.value
                else -> {
                    // No local ASR model installed (or the device/memory can't run it): stop here.
                    // The audio recording itself is untouched and remains fully accessible.
                    // In Internet mode the cloud is what failed; the missing offline model is only
                    // why there was nothing to fall back on. Say what actually went wrong.
                    val cloudFailed = processingProfile == ProcessingProfile.INTERNET && cloudWords == null
                    val message = if (cloudFailed) {
                        "Google's AI couldn't transcribe this recording. ${cloudDegradedReason ?: "No reason was given."}"
                    } else asrResult.describeFailure() ?: "Local speech recognition is unavailable."
                    val isCrash = asrResult is AiResult.Failed || cloudFailed
                    updateJob(
                        step = if (isCrash) "Failed" else "Speech recognition model required",
                        percent = 100,
                        stage = ProcessingStage.FAILED,
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

            // Resolved here because every layer below — structuring, cleanup, intelligence — is
            // shaped by what the user was actually recording.
            val recordingType = try {
                com.craftflowtechnologies.meetingmind.core.model.RecordingType.valueOf(existingMeeting.recordingType)
            } catch (e: Exception) {
                com.craftflowtechnologies.meetingmind.core.model.RecordingType.GENERAL
            }
            val singleSpeakerMode = expectedSpeakerCount == 1
            val soloSpeakerName = if (existingMeeting.source == com.craftflowtechnologies.meetingmind.core.model.MeetingSource.LOCAL_RECORDING.name) "You" else "Speaker 1"

            // STEP 3: Diarization — "who was speaking, when". Skipped entirely for a confirmed
            // single speaker: running multi-speaker clustering on a recording the user already
            // told MeetingMind is solo wastes two model loads for a result that would be thrown
            // away, and exposes the transcript to exactly the risk diarization exists to avoid —
            // one real speaker shredded into several by acoustic noise. The solo path still
            // assigns one real, persisted identity rather than leaving speakers empty.
            val diarizationStart = System.currentTimeMillis()
            val diarizationTurns: List<DiarizationTurn> = if (cloudWords != null) {
                // Gemini's verbatim pass diarized as it transcribed, and GlobalSpeakerResolver has
                // already made those labels recording-wide. Re-attributing here would overwrite
                // real evidence with a second opinion derived from nothing.
                emptyList()
            } else if (singleSpeakerMode) {
                updateJob("Single speaker confirmed — skipping speaker detection...", ProcessingProgress.SPEAKERS_START, ProcessingStage.DIARIZING)
                listOf(DiarizationTurn(speakerId = SOLO_SPEAKER_ID, startMs = 0L, endMs = maxOf(totalDurationMs, rawWords.lastOrNull()?.endMs ?: 0L)))
            } else {
                updateJob("Identifying speakers...", ProcessingProgress.SPEAKERS_START, ProcessingStage.DIARIZING)
                // Native diarization reports progress from its own thread; a ticker turns that
                // into a status line every few seconds so the bar visibly moves.
                val speakerFraction = java.util.concurrent.atomic.AtomicInteger(0)
                val outcome = kotlinx.coroutines.coroutineScope {
                    val ticker = launch {
                        var shown = -1
                        while (true) {
                            delay(PROGRESS_TICK_MS)
                            val pct = speakerFraction.get()
                            if (pct != shown) {
                                shown = pct
                                updateJob("Identifying speakers · $pct%", ProcessingProgress.speakers(pct / 100f), ProcessingStage.DIARIZING)
                            }
                        }
                    }
                    try {
                        runDiarizationWithBudget(diarizationBudgetMs(totalDurationMs)) {
                            diarizer.diarizeWithProgress(
                                audioFile, totalDurationMs, meetingId,
                                expectedSpeakerCount = expectedSpeakerCount,
                                onProgress = { speakerFraction.set((it * 100).toInt().coerceIn(0, 99)) }
                            )
                        }
                    } finally {
                        ticker.cancel()
                    }
                }
                when (outcome) {
                    is DiarizationOutcome.Separated -> outcome.turns
                    // No diarization model installed: every word stays honestly unattributed
                    // rather than being handed a fabricated identity.
                    is DiarizationOutcome.Unavailable -> emptyList()
                    // Timed out or crashed: same honest no-labels path (NOT the single-speaker
                    // path, which would label everyone as one person), and say so.
                    is DiarizationOutcome.Degraded -> {
                        Log.w(PERF_TAG, "Diarization degraded: ${outcome.reason}")
                        speakerNote = SPEAKERS_NOT_SEPARATED_MESSAGE
                        updateJob(SPEAKERS_NOT_SEPARATED_MESSAGE, ProcessingProgress.SPEAKERS_END, ProcessingStage.DIARIZING)
                        emptyList()
                    }
                }
            }
            val diarizationDurationMs = System.currentTimeMillis() - diarizationStart
            Log.d(PERF_TAG, "Diarization: ${diarizationDurationMs}ms, ${diarizationTurns.size} turns")
            // Free the segmentation+embedding models before any LLM allocation loads.
            SherpaEngineManager.releaseAll()

            // STEP 4: Word -> speaker attribution, then turns, utterances and paragraphs. Each
            // word carries its own AttributionConfidence; nothing is silently reassigned.
            val fusionStart = System.currentTimeMillis()
            val attributedWords = if (cloudWords != null) rawWords
                else WordSpeakerAttributor.attribute(rawWords, diarizationTurns)
            fun nameForSpeaker(speakerId: String): String =
                if (speakerId == SOLO_SPEAKER_ID) soloSpeakerName else defaultSpeakerNameFor(speakerIndexOf(speakerId))

            var canonical = CanonicalTranscriptAssembler.assemble(
                meetingId = meetingId,
                words = attributedWords,
                recordingType = recordingType,
                singleSpeakerMode = singleSpeakerMode,
                metadata = TranscriptMetadata(
                    meetingId = meetingId,
                    language = existingMeeting.language,
                    processingMode = effectiveProfile.name,
                    transcriptionEngine = if (cloudWords != null) {
                        com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter.GEMINI_TRANSCRIBE_MODEL
                    } else LOCAL_TRANSCRIPTION_ENGINE,
                    transcriptionModelId = if (cloudWords != null) {
                        com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter.GEMINI_TRANSCRIBE_MODEL
                    } else modelId,
                    audioDurationMs = totalDurationMs,
                    chunkCount = cloudChunkCount,
                    stageDurationsMs = mapOf("vad" to vadDurationMs, "asr" to asrDurationMs, "diarization" to diarizationDurationMs)
                ),
                speakerNameFor = ::nameForSpeaker,
                structureEngine = structureEngine
            )

            // Stage G: an optional second-opinion pass on top of the deterministic result — never
            // a replacement for it (see DiarizationReconciliationEngine's own doc for why a single
            // residual minor speaker is exactly the case pure numeric heuristics can't resolve).
            // It proposes speaker-id merges only; the merge is then applied at the word layer and
            // the transcript re-assembled, so turns, utterances and paragraphs all reflect it
            // rather than only the projected segments.
            if (!singleSpeakerMode && cloudWords == null) {
                val projected = CanonicalTranscriptAssembler.projectToSegments(canonical)
                val footprints = com.craftflowtechnologies.meetingmind.ai.diarization.computeSpeakerTranscriptFootprints(projected)
                if (com.craftflowtechnologies.meetingmind.ai.diarization.shouldAttemptAiReconciliation(footprints, diarizationStrategy)) {
                    updateJob("Refining speaker labels...", ProcessingProgress.RECONCILE_START, ProcessingStage.DIARIZING)
                    val merges = resolveSpeakerMerges(projected, processingProfile, totalDurationMs)
                    if (merges.isNotEmpty()) {
                        canonical = CanonicalTranscriptAssembler.assemble(
                            meetingId = meetingId,
                            words = canonical.words.map { word ->
                                val mapped = word.speakerId?.let { merges[it] }
                                if (mapped == null) word else word.copy(speakerId = mapped)
                            },
                            recordingType = recordingType,
                            singleSpeakerMode = false,
                            metadata = canonical.metadata,
                            speakerNameFor = ::nameForSpeaker,
                            structureEngine = structureEngine
                        )
                    }
                }
            }

            val quality = TranscriptQualityEvaluator.evaluate(canonical)
            canonical = canonical.copy(
                metadata = canonical.metadata.copy(
                    quality = quality,
                    stageDurationsMs = canonical.metadata.stageDurationsMs +
                        ("structure" to (System.currentTimeMillis() - fusionStart))
                )
            )
            // Metrics only — never transcript text. See docs/TRANSCRIPTION_OVERHAUL.md.
            Log.d(QUALITY_TAG, "Transcript quality: ${quality.toLogLine()}")

            val paragraphedSegments = CanonicalTranscriptAssembler.projectToSegments(canonical)
            Log.d(PERF_TAG, "Structuring: ${canonical.words.size} words -> ${canonical.utterances.size} utterances -> ${paragraphedSegments.size} paragraphs")

            // STEP 3.5/3.6: Transcript cleanup (rule-based, then a validated AI upgrade) — extracted
            // into cleanTranscript() below so a standalone reprocess flow (change cleanup mode,
            // re-clean an already-transcribed meeting) can call the EXACT same code path without a
            // second, special-cased implementation. Never run for an already-user-edited segment —
            // that can't happen on this first-run path (nothing has been edited yet at this point
            // in a brand-new meeting's life), but the check lives inside cleanTranscript so it's
            // correct on both paths.
            val diarizedSegments = cleanTranscript(
                structuredSegments = paragraphedSegments,
                recordingType = recordingType,
                cleanupMode = cleanupMode,
                singleSpeakerMode = expectedSpeakerCount == 1,
                onStatus = { step -> updateJob(step, if (step.startsWith("Refining")) ProcessingProgress.cleanup(0.5f) else ProcessingProgress.CLEANUP_START, ProcessingStage.CLEANING_TRANSCRIPT) },
                processingProfile = processingProfile,
                audioDurationMs = totalDurationMs
            )

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
                    sourceSegmentIdsJson = it.sourceSegmentIds.toJsonArrayString(),
                    // The single serializer in core.repository — never a second local copy; the two
                    // that existed before had already drifted apart on the provenance fields.
                    wordsJson = it.words.toWordsJson()
                )
            }
            transcriptDao.deletePartialSegments(meetingId)
            transcriptDao.insertSegments(segmentEntities)

            // Only real, non-null speaker IDs become Speaker rows. The single-speaker path above
            // always assigns one; a multi-speaker path with diarization unavailable is correctly
            // empty here rather than inventing per-speaker identities numeric heuristics can't
            // actually support.
            val speakerAttributionConfidence: Map<String, Float?> = canonical.words
                .filter { it.speakerId != null }
                .groupBy { it.speakerId!! }
                .mapValues { (_, words) ->
                    words.count { it.attribution == com.craftflowtechnologies.meetingmind.ai.transcript.AttributionConfidence.HIGH }
                        .toFloat() / words.size
                }
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
                        colorHex = com.craftflowtechnologies.meetingmind.core.model.SpeakerColors.forIndex(speakerIndex),
                        // sherpa-onnx's diarization API provides no per-speaker score of its own,
                        // but the attribution layer does: the share of this speaker's words the
                        // word/turn mapping was confident about. That is a real, measured figure
                        // about how well this identity is supported — not an invented one — and
                        // it is null when the speaker has no attributed words to measure.
                        confidence = speakerAttributionConfidence[seg.speakerId]
                    )
                }
            // A speaker already linked to a person stays linked when the recording is processed again.
            val linkedPeople = speakerDao.getSpeakersForMeetingDirect(meetingId).associate { it.id to it.personId }
            speakerDao.insertSpeakers(uniqueSpeakers.map { it.copy(personId = linkedPeople[it.id]) })

            // Only persist intelligence output when it's real (summary != null)
            if (summary != null) {
                persistIntelligence(meetingId, summary, actionItemDao, decisionDao, questionDao, followUpDao, topicDao)
                persistSignals(meetingId, summary)
            }
            // Marks tapped while recording meet what extraction found (docs/PLAN_PROFESSIONAL.md §4.2).
            runCatching {
                com.craftflowtechnologies.meetingmind.core.work.Marks.reconcile(database, meetingId, diarizedSegments.map {
                    com.craftflowtechnologies.meetingmind.core.work.Marks.Segment(it.id, it.startMs, it.endMs, it.speakerId, it.cleanedText ?: it.text)
                })
            }.onFailure { Log.w(PERF_TAG, "Marks not reconciled: ${it.message}") }

            embeddingDao.insertEmbeddings(embeddingEntities)

            // Faith recordings: the scripture heard in them, and for a sermon its notes, go into the
            // recording's note (docs/PLAN_V1.md §5). Best effort — a problem here must never fail a
            // recording whose transcript is already safe.
            if (recordingType in com.craftflowtechnologies.meetingmind.core.model.Workflows.faith) {
                runCatching {
                    writeFaithNotes(meetingId, recordingType, diarizedSegments, processingProfile) { step, percent ->
                        updateJob(step, percent, ProcessingStage.SAVING_RESULTS)
                    }
                }.onFailure { Log.w(PERF_TAG, "Faith notes skipped: ${it.message}") }
            }

            val updatedMeeting = existingMeeting.copy(
                title = generatedTitle,
                status = MeetingStatus.READY.name,
                durationMs = totalDurationMs,
                participantCount = uniqueSpeakers.size.coerceAtLeast(1),
                summaryText = summary?.summary,
                updatedAt = System.currentTimeMillis(),
                // How this meeting was actually processed. Recorded per meeting so a later build
                // can tell which transcripts predate a pipeline change without inferring it from
                // their contents, and so a support question about one meeting's quality can be
                // answered from data rather than from guesswork.
                processingProfile = canonical.metadata.processingMode,
                transcriptionEngine = canonical.metadata.transcriptionEngine,
                transcriptionModelId = canonical.metadata.transcriptionModelId,
                processingVersion = canonical.metadata.processingVersion,
                qualityMetricsJson = quality.toJson()
            )
            meetingDao.updateMeeting(updatedMeeting)
            // Finished: the resume checkpoint has done its job.
            AsrCheckpointStore(asrCheckpointDir(context)).clear(meetingId)
            com.craftflowtechnologies.meetingmind.ai.transcription.TranscriptionCheckpoints(transcriptionRegionsDir(context)).clear(meetingId)
            // The recording's note picks up its generated title and summary. A failure here must
            // never fail a recording that processed successfully.
            runCatching { com.craftflowtechnologies.meetingmind.core.repository.NoteRepository(context, database).syncFromRecording(meetingId) }

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
        } catch (e: Throwable) {
            withContext(kotlinx.coroutines.NonCancellable) {
                SherpaEngineManager.releaseAll()
                LlmEngineManager.release()
                updateJob("Failed", 0, ProcessingStage.FAILED, failed = true, error = e.localizedMessage ?: "Unknown processing error")
                meetingDao.updateMeeting(existingMeeting.copy(status = MeetingStatus.ERROR.name))
            }
            throw e
        }
    }

    /**
     * Runs rule-based cleanup, then a validated AI cleanup upgrade, against already-structured
     * paragraphs — never touches audio, VAD, ASR, or diarization. Used internally by
     * [processMeeting] for a fresh run's cleanup stage, AND directly by
     * [com.craftflowtechnologies.meetingmind.core.domain.ReprocessTranscriptCleanupUseCase] to let a user re-clean an
     * already-transcribed meeting with a different mode without re-recording — one real
     * implementation of "how cleanup runs," not a second special-cased pipeline.
     *
     * @param onStatus Plain status text only (no percent/stage) — [processMeeting] bridges this
     *   into its own job-progress persistence; a standalone reprocess call can drive a simple
     *   loading indicator directly from the same strings.
     */
    suspend fun cleanTranscript(
        structuredSegments: List<TranscriptSegment>,
        recordingType: com.craftflowtechnologies.meetingmind.core.model.RecordingType,
        cleanupMode: com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode,
        singleSpeakerMode: Boolean,
        onStatus: suspend (String) -> Unit = {},
        /** Reports which engine actually produced the returned segments — "rule-based" if no
         * capable model was installed or its output was rejected by validation, or the model id
         * that really ran. Callers that show an honest footprint line (recording page redesign
         * §2.7/§3.4 item 27 — "record model name, on-device flag, elapsed time... never let the
         * model self-report what it changed") need this instead of guessing from context. */
        onEngineUsed: (String) -> Unit = {},
        /** Which profile the refinement pass may use. Defaults to the private one: a caller that
         * wants cloud refinement must ask for it. */
        processingProfile: ProcessingProfile = ProcessingProfile.OFFLINE,
        /** Sizes the AI pass's time budget; 0 (unknown) gets the minimum budget. */
        audioDurationMs: Long = 0L
    ): List<TranscriptSegment> = withContext(Dispatchers.Default) {
        val profile = recordingType.transcriptCleanupProfile(cleanupMode)

        onStatus("Cleaning up your transcript...")
        val ruleCleanedSegments = applyTranscriptCleanup(structuredSegments, cleanupEngine)

        // A validated *upgrade* over the rule-based pass above, never a replacement for it: this
        // only ever overwrites a segment's cleanedText when it finds a real cleanup-capable model
        // installed AND that model's candidate passes TranscriptQualityValidator against the real
        // raw text. No model installed, no candidate accepted, or a user-edited segment (never
        // even offered) all fall back to exactly what the rule-based pass already produced — never
        // a fabricated status.
        // Through the factory, so Internet mode reaches this stage too: Gemini when a key is
        // entered, otherwise the best installed on-device model, otherwise rule-based only.
        val resolvedCleanupModel = languageModelFactory.resolve(
            profile = processingProfile,
            capability = ModelCapability.TRANSCRIPT_CLEANUP,
            preferredTier = profile.preferredModelTier
        )
        if (resolvedCleanupModel == null) {
            Log.d(PERF_TAG, "AI cleanup: skipped — no cloud model configured and no installed model has TRANSCRIPT_CLEANUP capability")
            onEngineUsed("rule-based")
            return@withContext ruleCleanedSegments
        }
        val aiCleanupModelId = resolvedCleanupModel.modelId

        onStatus(if (resolvedCleanupModel.isCloud) "Refining transcript with Google's AI..." else "Refining transcript with AI...")
        val aiStart = System.currentTimeMillis()
        val aiCleanupEngine = RealTranscriptAiCleanupEngine(
            languageModel = resolvedCleanupModel.languageModel,
            contextLengthTokens = resolvedCleanupModel.contextLengthTokens
        )
        // Budgeted: a hung or very slow model must not hold the whole job. On timeout (or a
        // thrown error) the rule-based result below is used, exactly as for an AiResult failure.
        val aiResult: AiResult<TranscriptAiCleanupResult> = try {
            withTimeoutOrNull(cleanupBudgetMs(audioDurationMs)) {
                aiCleanupEngine.clean(ruleCleanedSegments, profile, singleSpeakerMode)
            } ?: AiResult.Failed("AI cleanup exceeded its time budget")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AiResult.Failed(t.message ?: "AI cleanup failed", t)
        }
        // Free the cleanup model before the (possibly different) intelligence model loads — at
        // most one LLM allocation resident at a time, same discipline as every other stage.
        LlmEngineManager.release()
        when (aiResult) {
            is AiResult.Success -> {
                val r = aiResult.value
                Log.d(
                    PERF_TAG,
                    "AI cleanup: ${System.currentTimeMillis() - aiStart}ms, mode=${cleanupMode.name}, model=$aiCleanupModelId, " +
                        "${r.chunksAttempted} chunks, ${r.paragraphsAccepted} accepted, ${r.paragraphsFallback} fallback"
                )
                // paragraphsFallback > 0 alongside paragraphsAccepted == 0 means every candidate
                // was rejected by validation — real output is still all rule-based despite a model
                // having run, and the footprint line must say so, not claim an AI pass that made no
                // accepted change.
                onEngineUsed(if (r.paragraphsAccepted > 0) aiCleanupModelId else "rule-based")
                r.segments
            }
            else -> {
                Log.d(PERF_TAG, "AI cleanup: unavailable (${aiResult.describeFailure() ?: "no reason given"}) — using rule-based cleanup only")
                onEngineUsed("rule-based")
                ruleCleanedSegments
            }
        }
    }

    private suspend fun persistIntelligence(
        meetingId: String,
        summary: MeetingSummary,
        actionItemDao: com.craftflowtechnologies.meetingmind.core.database.ActionItemDao,
        decisionDao: com.craftflowtechnologies.meetingmind.core.database.DecisionDao,
        questionDao: com.craftflowtechnologies.meetingmind.core.database.QuestionDao,
        followUpDao: com.craftflowtechnologies.meetingmind.core.database.FollowUpDao,
        topicDao: com.craftflowtechnologies.meetingmind.core.database.TopicDao
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
            summary.topics.mapNotNull { com.craftflowtechnologies.meetingmind.core.common.Labels.clean(it) }.distinctBy { it.lowercase() }.map {
                TopicEntity(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    name = it,
                    relevance = 1.0f
                )
            }
        )
    }

    /** One row per paragraph a signal cites, so a paragraph can be filtered by what it holds. */
    private suspend fun persistSignals(meetingId: String, summary: MeetingSummary) {
        if (summary.signals.isEmpty()) return
        database.signalDao().insertAll(summary.signals.flatMap { s ->
            s.sourceSegmentIds.mapIndexed { i, segmentId ->
                com.craftflowtechnologies.meetingmind.core.database.SegmentSignalEntity(
                    id = "${s.id}_$i", segmentId = segmentId, meetingId = meetingId, kind = s.kind, entityId = s.speakerId,
                    value = s.value, confidence = s.confidence, text = s.text, signalId = s.id
                )
            }
        })
    }

    private fun List<String>.toJsonArrayString(): String {
        val array = org.json.JSONArray()
        forEach { array.put(it) }
        return array.toString()
    }


    /**
     * Runs the optional AI diarization second opinion and returns the speaker-id remapping it
     * proposes (`from` -> `into`), or an empty map when no capable model is installed, the model
     * is unavailable, or it proposed nothing it was confident about.
     *
     * The mapping — rather than the engine's own relabelled segments — is what the caller applies,
     * because the merge has to reach the word layer for turns, utterances and paragraphs to be
     * rebuilt from it. Applying it only to the projected segments would leave the canonical
     * transcript disagreeing with the transcript the user reads.
     */
    /**
     * Terms worth biasing recognition toward for one meeting, highest-signal first and capped, so
     * a long learned-vocabulary table cannot drown out the terms specific to this recording.
     *
     * A hint is a preference, never a substitution: no code path uses this list to replace a word
     * an engine actually recognized.
     */
    private suspend fun buildVocabularyHints(meeting: MeetingEntity): List<String> = try {
        val fromMeeting = (meeting.title + " " + (meeting.customContext ?: ""))
            .split(WORD_SPLIT_REGEX)
            .filter { it.length >= MIN_HINT_LENGTH && it.first().isUpperCase() }
        // getAllDirect() is already ordered by frequency then recency — the user's most-confirmed
        // corrections first.
        val learned = database.vocabularyDao().getAllDirect().map { it.canonicalForm }
        // The workflow's own words last: a sermon adds the Bible's book names, so "Habakkuk" and
        // "Philemon" aren't heard as something else.
        val workflow = runCatching { com.craftflowtechnologies.meetingmind.core.model.RecordingType.valueOf(meeting.recordingType) }.getOrNull()
            ?.let { com.craftflowtechnologies.meetingmind.core.model.Workflows.vocabularyHints(it) }.orEmpty()
        (fromMeeting + learned + workflow).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_VOCABULARY_HINTS)
    } catch (e: Exception) {
        // Vocabulary is an optimisation. Failing to read it must never fail a transcription.
        Log.d(PERF_TAG, "Vocabulary hints unavailable: ${e.message}")
        emptyList()
    }

    /**
     * Detects scripture in the transcript and, for a sermon, writes its notes — with Gemini in
     * Internet mode, the on-device model otherwise, and detection alone when there is no model.
     */
    private suspend fun writeFaithNotes(
        meetingId: String,
        recordingType: com.craftflowtechnologies.meetingmind.core.model.RecordingType,
        segments: List<TranscriptSegment>,
        processingProfile: ProcessingProfile,
        progress: suspend (String, Int) -> Unit
    ) {
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return
        val noteId = meeting.noteId ?: return
        // Scenes first: where the preaching, prayer, readings and worship are. Worship is kept
        // out of the notes and the Scripture search, so sung lyrics never become "points".
        val audio = meeting.audioFilePath?.let { java.io.File(it) }?.takeIf { it.exists() }
        val scenes = audio?.let { file ->
            progress("Mapping the service…", 93)
            runCatching {
                val transport = if (processingProfile == ProcessingProfile.INTERNET) geminiTransport else null
                com.craftflowtechnologies.meetingmind.ai.scene.SceneEngine(transport).build(file, segments, meeting.durationMs, recordingType.displayName)
                    .also { com.craftflowtechnologies.meetingmind.ai.scene.SceneMap.save(file, it) }
            }.onFailure { Log.w(PERF_TAG, "Scenes failed: ${it.message}") }.getOrNull()
        }
        val spoken = scenes?.let { map ->
            segments.filter { s -> map.at((s.startMs + s.endMs) / 2)?.activity != com.craftflowtechnologies.meetingmind.ai.scene.SemanticActivity.SONG }
        }?.ifEmpty { null } ?: segments
        progress("Finding scripture references...", 94)
        val detections = com.craftflowtechnologies.meetingmind.core.scripture.ScriptureDetector.detect(spoken)
        val extraction = if (recordingType == com.craftflowtechnologies.meetingmind.core.model.RecordingType.SERMON) {
            val resolved = languageModelFactory.resolve(processingProfile, ModelCapability.SYNTHESIS)
            resolved?.let { model ->
                progress(if (model.isCloud) "Writing sermon notes with Google's AI..." else "Writing sermon notes...", 96)
                try {
                    (com.craftflowtechnologies.meetingmind.ai.faith.SermonExtractionEngine(model.languageModel, model.contextLengthTokens)
                        .extract(spoken) { part, parts -> if (parts > 1) Log.d(PERF_TAG, "Sermon notes part $part of $parts") }
                        as? AiResult.Success)?.value
                } finally {
                    if (!model.isCloud) LlmEngineManager.release()
                }
            }
        } else null
        val generated = com.craftflowtechnologies.meetingmind.ai.faith.SermonNoteBuilder.build(noteId, meetingId, segments, extraction, detections)
        com.craftflowtechnologies.meetingmind.core.repository.NoteRepository(context, database)
            .applyGeneratedSections(noteId, generated.blocks, generated.refs, generated.keys)
    }

    private suspend fun resolveSpeakerMerges(
        segments: List<TranscriptSegment>,
        processingProfile: ProcessingProfile,
        audioDurationMs: Long
    ): Map<String, String> {
        val resolved = languageModelFactory.resolve(
            profile = processingProfile,
            capability = ModelCapability.DIARIZATION_RECONCILIATION,
            preferredTier = com.craftflowtechnologies.meetingmind.core.model.ModelTier.LIGHTWEIGHT
        )
        if (resolved == null) {
            Log.d(PERF_TAG, "Diarization reconciliation: skipped — no cloud model configured and no installed model has DIARIZATION_RECONCILIATION capability")
            return emptyMap()
        }
        val engine = com.craftflowtechnologies.meetingmind.ai.diarization.RealDiarizationReconciliationEngine(resolved.languageModel)
        val result: AiResult<com.craftflowtechnologies.meetingmind.ai.diarization.DiarizationReconciliationResult> = try {
            withTimeoutOrNull(reconciliationBudgetMs(audioDurationMs)) { engine.reconcile(segments) }
                ?: AiResult.Failed("Speaker reconciliation exceeded its time budget")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AiResult.Failed(t.message ?: "Speaker reconciliation failed", t)
        }
        LlmEngineManager.release()
        if (result !is AiResult.Success) {
            Log.d(PERF_TAG, "Diarization reconciliation: unavailable (${result.describeFailure() ?: "no reason given"}) — keeping deterministic result")
            return emptyMap()
        }
        val reconciliation = result.value
        if (reconciliation.mergedSpeakerIds.isEmpty()) {
            Log.d(PERF_TAG, "Diarization reconciliation: no confident merges proposed")
            return emptyMap()
        }
        // The engine reports the merge by rewriting each segment's speakerId; recovering the
        // mapping from before/after pairs keeps this pipeline independent of that representation.
        val before = segments.associateBy({ it.id }, { it.speakerId })
        val merges = reconciliation.segments
            .mapNotNull { after ->
                val original = before[after.id] ?: return@mapNotNull null
                if (original != null && after.speakerId != null && original != after.speakerId) {
                    original to after.speakerId
                } else null
            }
            .toMap()
        Log.d(PERF_TAG, "Diarization reconciliation: merged ${reconciliation.mergedSpeakerIds} — ${reconciliation.reasons.joinToString("; ")}")
        return merges
    }

    private companion object {
        const val PERF_TAG = "MeetMindPerf"
        const val PROGRESS_TICK_MS = 2_000L
        const val QUALITY_TAG = "MeetMindTranscriptQuality"
        /** Speaker id used when the user confirmed the recording is solo — no clustering ran. */
        const val SOLO_SPEAKER_ID = "speaker_0"
        const val LOCAL_TRANSCRIPTION_ENGINE = "parakeet-tdt-0.6b-v3"
        const val MAX_VOCABULARY_HINTS = 120
        const val MIN_HINT_LENGTH = 3
        val WORD_SPLIT_REGEX = Regex("[^\\p{L}\\p{N}'-]+")
        const val DEFAULT_LLM_CONTEXT_TOKENS = 4096
    }
}

/** Where interrupted local transcriptions keep their finished windows. */
internal fun transcriptionRegionsDir(context: android.content.Context) = java.io.File(context.filesDir, "transcription_regions")

internal fun asrCheckpointDir(context: android.content.Context) = java.io.File(context.filesDir, "asr_checkpoints")
