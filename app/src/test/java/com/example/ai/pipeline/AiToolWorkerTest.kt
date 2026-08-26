package com.example.ai.pipeline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.core.database.AiJobEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.model.AiJobStatus
import com.example.core.model.TranscriptAiToolType
import com.example.core.model.VocabularySource
import com.example.core.repository.VocabularyRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * [AiToolWorker] is the generic dispatcher every "✨ AI Tools" background run goes through
 * (Phase 15 §5) — these tests pin down the contract that matters regardless of which tool
 * eventually runs: a missing/unknown job fails cleanly, and a tool that isn't wired up yet fails
 * honestly (persisted to Room as FAILED with a real message) rather than silently succeeding or
 * fabricating a result. Real tool execution (CLEAN_TRANSCRIPT, the one wired branch today) isn't
 * exercised here — it delegates to [com.example.core.domain.ReprocessTranscriptCleanupUseCase] /
 * [MeetingProcessingPipeline.cleanTranscript], already covered by their own tests; native
 * ASR/diarization/LLM can't run under Robolectric regardless (see
 * [MeetingProcessingWorkerTest]'s comment on the same limitation).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiToolWorkerTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Swap the singleton so AiToolWorker's own MeetMindDatabase.getInstance() call inside
        // doWork() resolves to this in-memory test database instead of a real on-device one.
        MeetMindDatabase.setInstanceForTest(database)
        runBlocking {
            database.meetingDao().insertMeeting(
                MeetingEntity(
                    id = "m1",
                    title = "Test Meeting",
                    createdAt = System.currentTimeMillis(),
                    durationMs = 0L,
                    source = "LOCAL_RECORDING",
                    audioFilePath = null,
                    status = "READY",
                    participantCount = 1,
                    language = "en",
                    summaryText = null
                )
            )
        }
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        database.close()
    }

    private fun seedJob(toolType: TranscriptAiToolType): String {
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        runBlocking {
            database.aiJobDao().insertOrUpdate(
                AiJobEntity(
                    id = jobId,
                    meetingId = "m1",
                    toolType = toolType.name,
                    status = AiJobStatus.QUEUED.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return jobId
    }

    @Test
    fun `fails cleanly when jobId is missing`() = runBlocking {
        val worker = TestListenableWorkerBuilder<AiToolWorker>(context).build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("Missing jobId", (result as ListenableWorker.Result.Failure).outputData.getString(AiToolWorker.KEY_ERROR))
    }

    @Test
    fun `fails cleanly when the job id does not resolve to a real job`() = runBlocking {
        val worker = TestListenableWorkerBuilder<AiToolWorker>(context)
            .setInputData(workDataOf(AiToolWorker.KEY_JOB_ID to "does-not-exist"))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val error = (result as ListenableWorker.Result.Failure).outputData.getString(AiToolWorker.KEY_ERROR)
        assertTrue(error != null && error.contains("not found"))
    }

    @Test
    fun `a tool that is not wired up yet fails honestly instead of fabricating a result`() = runBlocking {
        val jobId = seedJob(TranscriptAiToolType.FIX_TRANSCRIPTION_ERRORS)
        val worker = TestListenableWorkerBuilder<AiToolWorker>(context)
            .setInputData(workDataOf(AiToolWorker.KEY_JOB_ID to jobId))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val error = (result as ListenableWorker.Result.Failure).outputData.getString(AiToolWorker.KEY_ERROR)
        assertTrue("error should name the unwired tool honestly: $error", error != null && error.contains("isn't wired up yet"))

        val persisted = database.aiJobDao().getById(jobId)
        assertEquals(AiJobStatus.FAILED.name, persisted?.status)
        assertEquals(error, persisted?.errorMessage)
        // Never a fabricated result payload for a tool that didn't actually run.
        assertEquals(null, persisted?.resultPayloadJson)
    }

    @Test
    fun `FIX_TERMINOLOGY actually runs end-to-end and persists a real result`() = runBlocking {
        VocabularyRepository(database).recordCorrection("Sherpa Onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)
        database.transcriptDao().insertSegments(
            listOf(
                TranscriptSegmentEntity(
                    id = "s1", meetingId = "m1", speakerId = "spk_0", speakerName = "You",
                    startMs = 0L, endMs = 1000L, text = "We use Sherpa Onix for ASR.", confidence = 0.9f
                )
            )
        )
        val jobId = seedJob(TranscriptAiToolType.FIX_TERMINOLOGY)
        val worker = TestListenableWorkerBuilder<AiToolWorker>(context)
            .setInputData(workDataOf(AiToolWorker.KEY_JOB_ID to jobId))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val persisted = database.aiJobDao().getById(jobId)
        assertEquals(AiJobStatus.SUCCEEDED.name, persisted?.status)
        assertEquals(100, persisted?.progressPercent)
        val resultJson = JSONObject(persisted!!.resultPayloadJson!!)
        assertEquals(1, resultJson.getInt("segmentsChanged"))
        assertEquals("We use Sherpa-ONNX for ASR.", database.transcriptDao().getSegmentsForMeetingDirect("m1")[0].text)
    }

    @Test
    fun `an unknown persisted toolType string fails honestly rather than crashing`() = runBlocking {
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.aiJobDao().insertOrUpdate(
            AiJobEntity(id = jobId, meetingId = "m1", toolType = "SOME_FUTURE_TOOL_NOT_YET_KNOWN", status = AiJobStatus.QUEUED.name, createdAt = now, updatedAt = now)
        )
        val worker = TestListenableWorkerBuilder<AiToolWorker>(context)
            .setInputData(workDataOf(AiToolWorker.KEY_JOB_ID to jobId))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val persisted = database.aiJobDao().getById(jobId)
        assertEquals(AiJobStatus.FAILED.name, persisted?.status)
    }
}
