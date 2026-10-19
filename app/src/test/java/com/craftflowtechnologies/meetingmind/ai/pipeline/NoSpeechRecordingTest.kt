package com.craftflowtechnologies.meetingmind.ai.pipeline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.asr.SpeechRecognizer
import com.craftflowtechnologies.meetingmind.ai.asr.TranscriptionOptions
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.diarization.SpeakerDiarizer
import com.craftflowtechnologies.meetingmind.ai.embeddings.LocalEmbeddingEngine
import com.craftflowtechnologies.meetingmind.ai.llm.MeetingIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalWord
import com.craftflowtechnologies.meetingmind.ai.transcript.DiarizationTurn
import com.craftflowtechnologies.meetingmind.ai.transcript.SpeechRegion
import com.craftflowtechnologies.meetingmind.ai.vad.SpeechInterval
import com.craftflowtechnologies.meetingmind.ai.vad.VoiceActivityDetector
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * A recording with no words in it (silence, room noise, or speech the recognizer could not hear)
 * must reach a real terminal state. Before this fix the job row stopped at the ASR/diarization
 * boundary (52%) with no terminal flag, so the bar sat there forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoSpeechRecordingTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase
    private lateinit var audioFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audioFile = File(context.cacheDir, "silent_recording.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun tearDown() {
        database.close()
        audioFile.delete()
    }

    private suspend fun insertRecordingMeeting(meetingId: String) {
        database.meetingDao().insertMeeting(
            MeetingEntity(
                id = meetingId,
                title = "Silent Recording",
                createdAt = System.currentTimeMillis(),
                durationMs = 0L,
                source = "LOCAL_RECORDING",
                audioFilePath = audioFile.absolutePath,
                status = MeetingStatus.PROCESSING.name,
                participantCount = 1,
                language = "en",
                summaryText = null
            )
        )
    }

    /** No speech anywhere: VAD finds nothing and the recognizer decodes nothing. */
    private val silentVad = object : VoiceActivityDetector {
        override suspend fun detectSpeechIntervals(audioFile: File, totalDurationMs: Long): AiResult<List<SpeechInterval>> =
            AiResult.Success(emptyList())
    }

    private val silentAsr = object : SpeechRecognizer {
        override suspend fun transcribe(
            audioFile: File,
            totalDurationMs: Long,
            meetingId: String,
            speechRegions: List<SpeechRegion>,
            options: TranscriptionOptions,
            onProgress: (progress: Float, statusText: String) -> Unit
        ): AiResult<List<CanonicalWord>> {
            onProgress(1f, "No speech to transcribe")
            return AiResult.Success(emptyList())
        }
    }

    /** Counts real invocations: a silent file has no words, so separating speakers is wasted work
     * that parks the progress bar at the ASR boundary (52) while the native call runs. */
    private val diarizeCalls = java.util.concurrent.atomic.AtomicInteger(0)

    private val silentDiarizer = object : SpeakerDiarizer {
        override suspend fun diarize(
            audioFile: File,
            totalDurationMs: Long,
            meetingId: String,
            knownSpeakers: List<Speaker>,
            expectedSpeakerCount: Int?
        ): AiResult<List<DiarizationTurn>> {
            diarizeCalls.incrementAndGet()
            return AiResult.Success(emptyList())
        }
    }

    private val noModelIntelligence = object : MeetingIntelligenceEngine {
        override suspend fun processMeeting(
            transcript: Transcript,
            meetingTitle: String,
            recordingType: com.craftflowtechnologies.meetingmind.core.model.RecordingType,
            customContext: String?
        ) = AiResult.ModelUnavailable("none", "No local meeting intelligence model is installed.")

        override suspend fun askMeeting(
            question: String,
            transcript: Transcript,
            relevantSegments: List<TranscriptSegment>,
            personalization: com.craftflowtechnologies.meetingmind.core.model.AskPersonalizationContext
        ) = AiResult.ModelUnavailable("none", "No local meeting intelligence model is installed.")
    }

    private fun buildPipeline(
        vad: VoiceActivityDetector = silentVad,
        asr: SpeechRecognizer = silentAsr,
        diarizer: SpeakerDiarizer = silentDiarizer
    ) = MeetingProcessingPipeline(
        context = context,
        database = database,
        modelStorage = LocalModelStorage(context),
        vad = vad,
        speechRecognizer = asr,
        diarizer = diarizer,
        intelligenceEngine = noModelIntelligence,
        embeddingEngine = LocalEmbeddingEngine()
    )

    @Test
    fun `a silent recording reaches a terminal COMPLETED state at 100 percent, not stuck at 52`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        val percents = mutableListOf<Int>()
        val steps = mutableListOf<String>()

        val meeting = withTimeout(60_000) {
            buildPipeline().processMeeting(meetingId, audioFile, 4000L) { step, percent, _ ->
                percents += percent
                steps += step
            }
        }

        val job = database.processingJobDao().getJobForMeeting(meetingId).first()
        assertNotNull("The job row must exist", job)
        assertTrue("A silent recording must be marked completed or failed, not running", job!!.isCompleted || job.isFailed)
        assertEquals(100, job.progressPercent)
        assertEquals(ProcessingStage.COMPLETED.name, job.stage)
        assertFalse("No speech is not a failure; the note still gets created", job.isFailed)
        assertEquals(MeetingStatus.READY.name, meeting.status)
        assertTrue("Reported percentages never go backwards", percents.zipWithNext().all { (a, b) -> b >= a })
        assertEquals("A silent file has no words, so speaker separation must not run", 0, diarizeCalls.get())
        assertTrue(
            "No step may claim to be identifying speakers when there is nothing to attribute",
            steps.none { it.contains("speakers", ignoreCase = true) }
        )
    }

    @Test
    fun `a silent recording says why it has no transcript, in the job row`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)

        withTimeout(60_000) { buildPipeline().processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> } }

        val job = database.processingJobDao().getJobForMeeting(meetingId).first()
        assertNotNull(job)
        assertTrue("The reason must be visible to the user", !job!!.errorMessage.isNullOrBlank())
        assertEquals(0, database.transcriptDao().getSegmentsForMeetingDirect(meetingId).size)
        assertTrue(
            "The reason must say there was no speech, not a generic line",
            job.errorMessage!!.contains("No speech", ignoreCase = true)
        )
    }
}
