package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.audio.AudioRecorder
import com.example.core.audio.MeetingRecordingService
import com.example.core.audio.RecorderState
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.model.MeetingSource
import com.example.core.model.MeetingStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: MeetMindDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun read_appName_from_context() {
        val appName = context.getString(R.string.app_name)
        assertEquals("MeetMind", appName)
    }

    @Test
    fun test_audio_recorder_initial_state() {
        val recorder = AudioRecorder(context)
        assertEquals(RecorderState.IDLE, recorder.state.value)
        assertEquals(0f, recorder.amplitude.value, 0.01f)
        assertEquals(0L, recorder.durationMs.value)
    }

    @Test
    fun test_recording_service_actions() {
        assertEquals("meetmind_recording_channel", MeetingRecordingService.CHANNEL_ID)
        assertEquals(1001, MeetingRecordingService.NOTIFICATION_ID)
        assertEquals("com.example.meetmind.ACTION_START", MeetingRecordingService.ACTION_START)
        assertEquals("com.example.meetmind.ACTION_PAUSE", MeetingRecordingService.ACTION_PAUSE)
        assertEquals("com.example.meetmind.ACTION_RESUME", MeetingRecordingService.ACTION_RESUME)
        assertEquals("com.example.meetmind.ACTION_STOP", MeetingRecordingService.ACTION_STOP)
    }

    @Test
    fun test_database_insert_and_query_meeting() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        val meeting = MeetingEntity(
            id = meetingId,
            title = "Q3 Product Architecture",
            createdAt = System.currentTimeMillis(),
            source = MeetingSource.LOCAL_RECORDING.name,
            durationMs = 180000L,
            status = MeetingStatus.READY.name,
            audioFilePath = "/path/to/audio.m4a",
            participantCount = 3,
            language = "en",
            summaryText = "Team discussed edge AI inference on mobile."
        )

        database.meetingDao().insertMeeting(meeting)

        val retrieved = database.meetingDao().getMeetingById(meetingId)
        assertNotNull(retrieved)
        assertEquals("Q3 Product Architecture", retrieved?.title)
        assertEquals(MeetingStatus.READY.name, retrieved?.status)
    }

    @Test
    fun test_transcript_search() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        val meeting = MeetingEntity(
            id = meetingId,
            title = "Board Sync",
            createdAt = System.currentTimeMillis(),
            source = MeetingSource.LOCAL_RECORDING.name,
            durationMs = 60000L,
            status = MeetingStatus.READY.name,
            audioFilePath = null,
            participantCount = 2,
            language = "en",
            summaryText = null
        )
        database.meetingDao().insertMeeting(meeting)

        val segment = TranscriptSegmentEntity(
            id = UUID.randomUUID().toString(),
            meetingId = meetingId,
            speakerId = "spk_0",
            speakerName = "Sarah",
            startMs = 12000L,
            endMs = 24000L,
            text = "We decided to allocate fifty thousand dollars for on-device machine learning research.",
            confidence = 0.95f
        )
        database.transcriptDao().insertSegments(listOf(segment))

        val results = database.transcriptDao().searchTranscriptSegments("machine learning")
        assertEquals(1, results.size)
        assertTrue(results.first().text.contains("fifty thousand dollars"))
    }

    @Test
    fun test_formatters() {
        assertEquals("03:45", Formatters.formatDurationHms(225000L))
        assertEquals("01:15:30", Formatters.formatDurationHms(4530000L))
        assertEquals("3 min", Formatters.formatDurationSummary(180000L))
    }

    @Test
    fun test_device_capability_detector() {
        val caps = DeviceCapabilityDetector.detect(context)
        assertNotNull(caps.cpuArch)
        assertTrue(caps.totalRamGb > 0)
    }

    @Test
    fun test_real_speech_recognizer_offline_fallback() = runBlocking {
        val recognizer = com.example.ai.asr.RealSpeechRecognizer()
        val emptyFile = java.io.File(context.cacheDir, "test_empty.m4a").apply { writeBytes(ByteArray(0)) }
        val segments = recognizer.transcribe(
            audioFile = emptyFile,
            totalDurationMs = 5000L,
            meetingId = "test_meeting_1",
            speechIntervals = emptyList(),
            options = com.example.ai.asr.TranscriptionOptions(),
            onProgress = { _, _ -> }
        )
        assertNotNull(segments)
        assertTrue(segments.isNotEmpty())
        assertTrue(segments.first().text.contains("No audible voice") || segments.first().text.contains("No distinct"))
    }

    @Test
    fun test_real_meeting_intelligence_engine_nlp() = runBlocking {
        val engine = com.example.ai.llm.RealMeetingIntelligenceEngine()
        val segments = listOf(
            com.example.core.model.TranscriptSegment(
                id = "seg_1",
                meetingId = "m1",
                speakerId = "spk_1",
                speakerName = "Alice",
                startMs = 0L,
                endMs = 3000L,
                text = "We decided to ship the beta release on Wednesday.",
                confidence = 0.95f
            ),
            com.example.core.model.TranscriptSegment(
                id = "seg_2",
                meetingId = "m1",
                speakerId = "spk_2",
                speakerName = "Bob",
                startMs = 3500L,
                endMs = 6000L,
                text = "I will send the updated design assets tonight.",
                confidence = 0.92f
            )
        )
        val transcript = com.example.core.model.Transcript("m1", segments)
        val summary = engine.processMeeting(transcript, "Beta Planning")
        assertNotNull(summary)
        assertTrue(summary.decisions.any { it.text.contains("ship the beta release") })
        assertTrue(summary.actionItems.any { it.task.contains("send the updated design assets") })
    }
}
