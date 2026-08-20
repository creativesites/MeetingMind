package com.example.ai.pipeline

import android.content.Context
import com.example.ai.asr.SpeechRecognizer
import com.example.ai.asr.TranscriptionOptions
import com.example.ai.asr.UnavailableSpeechRecognizer
import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.diarization.SpeakerDiarizer
import com.example.ai.diarization.UnavailableSpeakerDiarizer
import com.example.ai.embeddings.EmbeddingEngine
import com.example.ai.embeddings.LocalEmbeddingEngine
import com.example.ai.llm.MeetingIntelligenceEngine
import com.example.ai.llm.UnavailableMeetingIntelligenceEngine
import com.example.ai.vad.UnavailableVoiceActivityDetector
import com.example.ai.vad.VoiceActivityDetector
import com.example.core.database.ActionItemEntity
import com.example.core.database.DecisionEntity
import com.example.core.database.EmbeddingEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.ProcessingJobEntity
import com.example.core.database.QuestionEntity
import com.example.core.database.SpeakerEntity
import com.example.core.database.TopicEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.model.MeetingStatus
import com.example.core.model.MeetingSummary
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
 */
class MeetingProcessingPipeline(
    private val context: Context,
    private val database: MeetMindDatabase,
    private val vad: VoiceActivityDetector = UnavailableVoiceActivityDetector(),
    private val speechRecognizer: SpeechRecognizer = UnavailableSpeechRecognizer(),
    private val diarizer: SpeakerDiarizer = UnavailableSpeakerDiarizer(),
    private val intelligenceEngine: MeetingIntelligenceEngine = UnavailableMeetingIntelligenceEngine(),
    private val embeddingEngine: EmbeddingEngine = LocalEmbeddingEngine()
) {

    suspend fun processMeeting(
        meetingId: String,
        audioFile: File,
        totalDurationMs: Long,
        modelId: String = "whisper_tiny",
        onProgress: (step: String, percent: Int) -> Unit
    ): MeetingEntity = withContext(Dispatchers.Default) {
        val meetingDao = database.meetingDao()
        val transcriptDao = database.transcriptDao()
        val speakerDao = database.speakerDao()
        val actionItemDao = database.actionItemDao()
        val decisionDao = database.decisionDao()
        val questionDao = database.questionDao()
        val topicDao = database.topicDao()
        val embeddingDao = database.embeddingDao()
        val jobDao = database.processingJobDao()

        val existingMeeting = meetingDao.getMeetingById(meetingId)
            ?: throw IllegalArgumentException("Meeting $meetingId not found")

        val jobId = "job_$meetingId"
        val initialJob = ProcessingJobEntity(
            id = jobId,
            meetingId = meetingId,
            meetingTitle = existingMeeting.title,
            currentStep = "Preparing audio...",
            progressPercent = 10,
            isCompleted = false,
            isFailed = false,
            errorMessage = null,
            startedAt = System.currentTimeMillis()
        )
        jobDao.insertOrUpdateJob(initialJob)

        try {
            // STEP 1: Voice Activity Detection (best-effort — unavailable degrades to no filtering)
            onProgress("Detecting speech intervals (VAD)...", 20)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Detecting speech intervals...", progressPercent = 20))
            val speechIntervals = when (val vadResult = vad.detectSpeechIntervals(audioFile, totalDurationMs)) {
                is AiResult.Success -> vadResult.value
                else -> emptyList() // No VAD model installed: ASR will process the whole clip.
            }

            // STEP 2: Local Speech Recognition — the required gate. No model = no fabricated transcript.
            onProgress("Transcribing with local AI...", 35)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Transcribing with local AI...", progressPercent = 35))
            val asrResult = speechRecognizer.transcribe(
                audioFile = audioFile,
                totalDurationMs = totalDurationMs,
                meetingId = meetingId,
                speechIntervals = speechIntervals,
                options = TranscriptionOptions(modelId = modelId),
                onProgress = { prog, status ->
                    val overall = (35 + (prog * 25)).toInt()
                    onProgress(status, overall)
                }
            )

            val rawSegments: List<TranscriptSegment> = when (asrResult) {
                is AiResult.Success -> asrResult.value
                else -> {
                    // No local ASR model installed (or the device/memory can't run it): stop here.
                    // The audio recording itself is untouched and remains fully accessible.
                    val message = asrResult.describeFailure() ?: "Local speech recognition is unavailable."
                    val isCrash = asrResult is AiResult.Failed
                    jobDao.insertOrUpdateJob(
                        initialJob.copy(
                            currentStep = if (isCrash) "Failed" else "Speech recognition model required",
                            progressPercent = 100,
                            isCompleted = !isCrash,
                            isFailed = isCrash,
                            errorMessage = message
                        )
                    )
                    val updatedMeeting = existingMeeting.copy(
                        status = (if (isCrash) MeetingStatus.ERROR else MeetingStatus.MODEL_REQUIRED).name,
                        durationMs = totalDurationMs,
                        summaryText = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    meetingDao.updateMeeting(updatedMeeting)
                    onProgress(message, 100)
                    return@withContext updatedMeeting
                }
            }

            // STEP 3: Speaker Diarization (best-effort — unavailable keeps ASR's own speaker labels)
            onProgress("Identifying distinct speakers...", 65)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Identifying distinct speakers...", progressPercent = 65))
            val diarizedSegments = when (val diarizeResult = diarizer.diarize(rawSegments)) {
                is AiResult.Success -> diarizeResult.value
                else -> rawSegments // No diarization model installed: keep ASR's segments as-is.
            }

            // STEP 4: Meeting Intelligence (best-effort — unavailable leaves summary/insights empty)
            onProgress("Generating summary, action items & decisions...", 80)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Extracting decisions and action items...", progressPercent = 80))
            val transcriptDomain = Transcript(
                meetingId = meetingId,
                segments = diarizedSegments,
                language = "en",
                createdAt = existingMeeting.createdAt
            )

            val titleResult = intelligenceEngine.generateTitle(transcriptDomain, existingMeeting.title)
            val generatedTitle = (titleResult as? AiResult.Success)?.value ?: existingMeeting.title

            val summaryResult = intelligenceEngine.processMeeting(transcriptDomain, generatedTitle)
            val summary = (summaryResult as? AiResult.Success)?.value

            // STEP 5: Local Embeddings (real, on-device — only computed over the real transcript text)
            onProgress("Indexing transcript for semantic search...", 92)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Indexing for semantic search...", progressPercent = 92))

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
                    confidence = it.confidence
                )
            }
            transcriptDao.insertSegments(segmentEntities)

            val uniqueSpeakers = diarizedSegments.distinctBy { it.speakerId }.map { seg ->
                SpeakerEntity(
                    id = seg.speakerId,
                    meetingId = meetingId,
                    originalLabel = seg.speakerName,
                    customName = seg.speakerName,
                    colorHex = "#3B82F6"
                )
            }
            speakerDao.insertSpeakers(uniqueSpeakers)

            // Only persist intelligence output when it's real (summary != null)
            if (summary != null) {
                persistIntelligence(meetingId, summary, actionItemDao, decisionDao, questionDao, topicDao)
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

            jobDao.insertOrUpdateJob(
                initialJob.copy(
                    currentStep = "Completed",
                    progressPercent = 100,
                    isCompleted = true,
                    errorMessage = if (summary == null) {
                        "Transcript ready. " + (summaryResult.describeFailure() ?: "No local meeting intelligence model is installed.")
                    } else null
                )
            )
            onProgress("Complete", 100)

            updatedMeeting
        } catch (e: CancellationException) {
            jobDao.insertOrUpdateJob(
                initialJob.copy(
                    currentStep = "Cancelled",
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "Processing was cancelled by user"
                )
            )
            meetingDao.updateMeeting(existingMeeting.copy(status = MeetingStatus.ERROR.name))
            throw e
        } catch (e: Exception) {
            jobDao.insertOrUpdateJob(
                initialJob.copy(
                    currentStep = "Failed",
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = e.localizedMessage ?: "Unknown processing error"
                )
            )
            meetingDao.updateMeeting(existingMeeting.copy(status = MeetingStatus.ERROR.name))
            throw e
        }
    }

    private suspend fun persistIntelligence(
        meetingId: String,
        summary: MeetingSummary,
        actionItemDao: com.example.core.database.ActionItemDao,
        decisionDao: com.example.core.database.DecisionDao,
        questionDao: com.example.core.database.QuestionDao,
        topicDao: com.example.core.database.TopicDao
    ) {
        actionItemDao.insertActionItems(
            summary.actionItems.map {
                ActionItemEntity(
                    id = it.id,
                    meetingId = meetingId,
                    task = it.task,
                    assignee = it.assignee,
                    deadline = it.deadline,
                    confidence = it.confidence,
                    isCompleted = it.isCompleted,
                    sourceTimestampMs = it.sourceTimestampMs
                )
            }
        )
        decisionDao.insertDecisions(
            summary.decisions.map {
                DecisionEntity(
                    id = it.id,
                    meetingId = meetingId,
                    text = it.text,
                    confidence = it.confidence,
                    timestampMs = it.timestampMs
                )
            }
        )
        questionDao.insertQuestions(
            summary.questions.map {
                QuestionEntity(
                    id = it.id,
                    meetingId = meetingId,
                    text = it.text,
                    resolved = it.resolved,
                    answer = it.answer,
                    timestampMs = it.timestampMs
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
}
