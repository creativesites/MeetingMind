package com.example.ai.pipeline

import android.content.Context
import com.example.ai.asr.RealSpeechRecognizer
import com.example.ai.asr.SpeechRecognizer
import com.example.ai.asr.TranscriptionOptions
import com.example.ai.diarization.AcousticClusterDiarizer
import com.example.ai.diarization.SpeakerDiarizer
import com.example.ai.embeddings.EmbeddingEngine
import com.example.ai.embeddings.LocalEmbeddingEngine
import com.example.ai.llm.MeetingIntelligenceEngine
import com.example.ai.llm.RealMeetingIntelligenceEngine
import com.example.ai.vad.EnergyAndSpectralVad
import com.example.ai.vad.VoiceActivityDetector
import com.example.core.audio.AudioPreprocessor
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
import com.example.core.model.Transcript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MeetingProcessingPipeline(
    private val context: Context,
    private val database: MeetMindDatabase,
    private val audioPreprocessor: AudioPreprocessor = AudioPreprocessor(),
    private val vad: VoiceActivityDetector = EnergyAndSpectralVad(),
    private val speechRecognizer: SpeechRecognizer = RealSpeechRecognizer(),
    private val diarizer: SpeakerDiarizer = AcousticClusterDiarizer(),
    private val intelligenceEngine: MeetingIntelligenceEngine = RealMeetingIntelligenceEngine(),
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
            // STEP 1: Audio Preprocessing & Chunking (10% - 25%)
            onProgress("Preparing audio...", 15)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Preparing audio...", progressPercent = 15))
            val audioChunks = audioPreprocessor.analyzeAndSegment(audioFile, totalDurationMs)

            // STEP 2: Voice Activity Detection (25% - 35%)
            onProgress("Detecting speech intervals (VAD)...", 30)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Detecting speech intervals...", progressPercent = 30))
            val speechIntervals = vad.detectSpeechIntervals(audioFile, totalDurationMs)

            // STEP 3: Local Offline ASR Transcription (35% - 65%)
            onProgress("Transcribing with local AI...", 40)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Transcribing with local AI...", progressPercent = 40))
            val rawSegments = speechRecognizer.transcribe(
                audioFile = audioFile,
                totalDurationMs = totalDurationMs,
                meetingId = meetingId,
                speechIntervals = speechIntervals,
                options = TranscriptionOptions(modelId = modelId),
                onProgress = { prog, status ->
                    val overall = (35 + (prog * 30)).toInt()
                    onProgress(status, overall)
                }
            )

            // STEP 4: Speaker Diarization (65% - 75%)
            onProgress("Identifying distinct speakers...", 70)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Identifying distinct speakers...", progressPercent = 70))
            val diarizedSegments = diarizer.diarize(rawSegments)

            // STEP 5: Meeting Intelligence Synthesis (75% - 90%)
            onProgress("Generating summary, action items & decisions...", 80)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Extracting decisions and action items...", progressPercent = 80))
            val transcriptDomain = Transcript(
                meetingId = meetingId,
                segments = diarizedSegments,
                language = "en",
                createdAt = existingMeeting.createdAt
            )

            val lastGemini = (speechRecognizer as? RealSpeechRecognizer)?.getLastGeminiResult()
            val generatedTitle = if (lastGemini?.title?.isNotBlank() == true) {
                lastGemini.title
            } else {
                intelligenceEngine.generateTitle(transcriptDomain, existingMeeting.title)
            }

            val summary = if (lastGemini != null && lastGemini.summary.isNotBlank()) {
                com.example.core.model.MeetingSummary(
                    title = generatedTitle,
                    summary = lastGemini.summary,
                    topics = if (lastGemini.topics.isNotEmpty()) lastGemini.topics else listOf("Meeting Discussion"),
                    decisions = lastGemini.decisions,
                    actionItems = lastGemini.actionItems,
                    questions = lastGemini.questions,
                    followUps = emptyList()
                )
            } else {
                intelligenceEngine.processMeeting(transcriptDomain, generatedTitle)
            }

            // STEP 6: Semantic Vector Embedding Indexing (90% - 98%)
            onProgress("Indexing transcript for semantic search...", 92)
            jobDao.insertOrUpdateJob(initialJob.copy(currentStep = "Indexing for semantic search...", progressPercent = 92))

            val embeddingEntities = mutableListOf<EmbeddingEntity>()
            for (seg in diarizedSegments) {
                val vec = embeddingEngine.embed(seg.text)
                val vecString = vec.joinToString(",")
                embeddingEntities.add(
                    EmbeddingEntity(
                        id = UUID.randomUUID().toString(),
                        meetingId = meetingId,
                        segmentId = seg.id,
                        textChunk = seg.text,
                        vectorData = vecString,
                        startMs = seg.startMs,
                        endMs = seg.endMs
                    )
                )
            }

            // Also embed the summary for holistic meeting matching
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

            // Persist all components in Room database
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

            // Speakers
            val uniqueSpeakers = diarizedSegments.distinctBy { it.speakerId }.mapIndexed { idx, seg ->
                SpeakerEntity(
                    id = seg.speakerId,
                    meetingId = meetingId,
                    originalLabel = seg.speakerName,
                    customName = seg.speakerName,
                    colorHex = "#3B82F6"
                )
            }
            speakerDao.insertSpeakers(uniqueSpeakers)

            // Action Items
            val actionItemEntities = summary.actionItems.map {
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
            actionItemDao.insertActionItems(actionItemEntities)

            // Decisions
            val decisionEntities = summary.decisions.map {
                DecisionEntity(
                    id = it.id,
                    meetingId = meetingId,
                    text = it.text,
                    confidence = it.confidence,
                    timestampMs = it.timestampMs
                )
            }
            decisionDao.insertDecisions(decisionEntities)

            // Questions
            val questionEntities = summary.questions.map {
                QuestionEntity(
                    id = it.id,
                    meetingId = meetingId,
                    text = it.text,
                    resolved = it.resolved,
                    answer = it.answer,
                    timestampMs = it.timestampMs
                )
            }
            questionDao.insertQuestions(questionEntities)

            // Topics
            val topicEntities = summary.topics.map {
                TopicEntity(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    name = it,
                    relevance = 1.0f
                )
            }
            topicDao.insertTopics(topicEntities)

            // Embeddings
            embeddingDao.insertEmbeddings(embeddingEntities)

            // Update Meeting State to READY
            val updatedMeeting = existingMeeting.copy(
                title = generatedTitle,
                status = MeetingStatus.READY.name,
                durationMs = totalDurationMs,
                participantCount = uniqueSpeakers.size.coerceAtLeast(1),
                summaryText = summary.summary,
                updatedAt = System.currentTimeMillis()
            )
            meetingDao.updateMeeting(updatedMeeting)

            // Mark job as completed
            jobDao.insertOrUpdateJob(
                initialJob.copy(
                    currentStep = "Completed",
                    progressPercent = 100,
                    isCompleted = true
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
}
