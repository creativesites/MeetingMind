package com.example.ai.pipeline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.model.MeetingStatus
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
 * With no local ASR model installed (the default state of this app today), processing a
 * recorded meeting must never fabricate a transcript. It must stop honestly, mark the meeting
 * MODEL_REQUIRED, and — critically — never delete or touch the original audio recording.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeetingProcessingPipelineTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase
    private lateinit var pipeline: MeetingProcessingPipeline
    private lateinit var audioFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pipeline = MeetingProcessingPipeline(context, database)
        audioFile = File(context.cacheDir, "test_recording.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    }

    @After
    fun tearDown() {
        database.close()
        audioFile.delete()
    }

    @Test
    fun `processing without a local ASR model marks the meeting MODEL_REQUIRED and fabricates nothing`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        database.meetingDao().insertMeeting(
            MeetingEntity(
                id = meetingId,
                title = "Untranscribed Meeting",
                createdAt = System.currentTimeMillis(),
                durationMs = 0L,
                source = "LOCAL_RECORDING",
                audioFilePath = audioFile.absolutePath,
                status = MeetingStatus.RECORDING.name,
                participantCount = 1,
                language = "en",
                summaryText = null
            )
        )

        val result = pipeline.processMeeting(
            meetingId = meetingId,
            audioFile = audioFile,
            totalDurationMs = 5000L,
            onProgress = { _, _ -> }
        )

        assertEquals(MeetingStatus.MODEL_REQUIRED.name, result.status)
        assertTrue("Original audio recording must be preserved", audioFile.exists())

        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        assertTrue("No transcript segments may be persisted when no ASR model is installed", segments.isEmpty())

        val stored = database.meetingDao().getMeetingById(meetingId)
        assertEquals(MeetingStatus.MODEL_REQUIRED.name, stored?.status)
    }
}
