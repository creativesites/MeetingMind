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
import com.craftflowtechnologies.meetingmind.core.model.ChatMessage
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.MeetingSummary
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TitleSource
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * The title a recording really ends up with, through the whole pipeline: provenance is persisted,
 * a person's own name survives a reprocess, and a recording with no speech gets the honest date.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessingTitleTest {
    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase
    private lateinit var audioFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audioFile = File(context.cacheDir, "title_test_recording.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun tearDown() {
        database.close()
        audioFile.delete()
    }

    private suspend fun insertMeeting(
        meetingId: String,
        title: String = "Untitled Meeting",
        titleSource: TitleSource = TitleSource.AI
    ) {
        database.meetingDao().insertMeeting(
            MeetingEntity(
                id = meetingId,
                title = title,
                createdAt = System.currentTimeMillis(),
                durationMs = 0L,
                source = "LOCAL_RECORDING",
                audioFilePath = audioFile.absolutePath,
                status = MeetingStatus.RECORDING.name,
                participantCount = 1,
                language = "en",
                summaryText = null,
                recordingType = RecordingType.GENERAL.name,
                titleSource = titleSource.name
            )
        )
    }

    private val fakeVad = object : VoiceActivityDetector {
        override suspend fun detectSpeechIntervals(audioFile: File, totalDurationMs: Long) =
            AiResult.Success(listOf(SpeechInterval(startMs = 0L, endMs = totalDurationMs)))
    }

    private fun words(sentence: String, startMs: Long, endMs: Long): List<CanonicalWord> {
        val tokens = sentence.split(" ")
        val step = (endMs - startMs) / tokens.size
        return tokens.mapIndexed { index, token ->
            CanonicalWord(
                id = "w_${startMs}_$index",
                text = token,
                startMs = startMs + index * step,
                endMs = startMs + (index + 1) * step
            )
        }
    }

    private fun recognizer(wordsFactory: () -> List<CanonicalWord>) = object : SpeechRecognizer {
        override suspend fun transcribe(
            audioFile: File,
            totalDurationMs: Long,
            meetingId: String,
            speechRegions: List<SpeechRegion>,
            options: TranscriptionOptions,
            onProgress: (progress: Float, statusText: String) -> Unit
        ): AiResult<List<CanonicalWord>> {
            onProgress(1f, "done")
            return AiResult.Success(wordsFactory())
        }
    }

    private fun diarizer(meetingId: String) = object : SpeakerDiarizer {
        override suspend fun diarize(
            audioFile: File,
            totalDurationMs: Long,
            meetingId: String,
            knownSpeakers: List<Speaker>,
            expectedSpeakerCount: Int?
        ): AiResult<List<DiarizationTurn>> = AiResult.Success(
            listOf(DiarizationTurn(speakerId = "spk_${meetingId}_0", startMs = 0L, endMs = totalDurationMs))
        )
    }

    private fun successfulEngine(title: String) = object : MeetingIntelligenceEngine {
        override suspend fun processMeeting(
            transcript: Transcript,
            meetingTitle: String,
            recordingType: RecordingType,
            customContext: String?
        ) = AiResult.Success(
            MeetingSummary(
                title = title,
                summary = "The team agreed to ship on Friday.",
                topics = listOf("Ship date"),
                decisions = emptyList(),
                actionItems = emptyList(),
                questions = emptyList()
            )
        )

        override suspend fun askMeeting(
            question: String,
            transcript: Transcript,
            relevantSegments: List<TranscriptSegment>,
            personalization: com.craftflowtechnologies.meetingmind.core.model.AskPersonalizationContext
        ) = AiResult.Success(ChatMessage(id = UUID.randomUUID().toString(), meetingId = transcript.meetingId, isUser = false, content = "n/a"))
    }

    @Test
    fun `a user title survives a reprocess and keeps its source`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertMeeting(meetingId, title = "My own name for this", titleSource = TitleSource.USER)
        val pipeline = MeetingProcessingPipeline(
            context = context,
            database = database,
            modelStorage = LocalModelStorage(context),
            vad = fakeVad,
            speechRecognizer = recognizer { words("We will ship on Friday.", 0L, 4000L) },
            diarizer = diarizer(meetingId),
            intelligenceEngine = successfulEngine("A Better Model Title"),
            embeddingEngine = LocalEmbeddingEngine()
        )

        val result = pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }

        assertEquals("My own name for this", result.title)
        assertEquals(TitleSource.USER.name, result.titleSource)
    }

    @Test
    fun `a recording with no speech gets a localized date title with DATE provenance`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertMeeting(meetingId)
        val pipeline = MeetingProcessingPipeline(
            context = context,
            database = database,
            modelStorage = LocalModelStorage(context),
            vad = fakeVad,
            speechRecognizer = recognizer { emptyList() },
            diarizer = diarizer(meetingId),
            intelligenceEngine = successfulEngine("Should never be used"),
            embeddingEngine = LocalEmbeddingEngine()
        )

        val result = pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }

        assertEquals(TitleSource.DATE.name, result.titleSource)
        assertTrue("Expected a date fallback, got ${result.title}", result.title.startsWith("General — "))
        assertEquals(MeetingStatus.READY.name, result.status)
    }
}
