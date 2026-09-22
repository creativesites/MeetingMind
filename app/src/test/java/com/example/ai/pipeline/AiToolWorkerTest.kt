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
            // A real transcript, so a tool run gets as far as needing a model rather than
            // stopping at "nothing in scope" — which is a different failure entirely.
            database.transcriptDao().insertSegments(
                listOf(
                    com.example.core.database.TranscriptSegmentEntity(
                        id = "s1", meetingId = "m1", speakerId = "spk_m1_0", speakerName = "Speaker 1",
                        startMs = 0L, endMs = 5_000L, text = "We agreed to ship on Friday.", confidence = null
                    ),
                    com.example.core.database.TranscriptSegmentEntity(
                        id = "s2", meetingId = "m1", speakerId = "spk_m1_1", speakerName = "Speaker 2",
                        startMs = 5_000L, endMs = 9_000L, text = "Sounds good to me.", confidence = null
                    )
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
    fun `a model-backed tool with no model available fails honestly instead of fabricating a result`() = runBlocking {
        // No local model is installed in this environment and no Gemini key is set, so there is
        // genuinely nothing to run the tool with. That must be said, not papered over: the job
        // fails, the reason names what to do about it, and no result payload is written.
        val jobId = seedJob(TranscriptAiToolType.FIX_TRANSCRIPTION_ERRORS)
        val worker = TestListenableWorkerBuilder<AiToolWorker>(context)
            .setInputData(workDataOf(AiToolWorker.KEY_JOB_ID to jobId))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val error = (result as ListenableWorker.Result.Failure).outputData.getString(AiToolWorker.KEY_ERROR)
        assertTrue("the error should say what to do: $error", error != null && error.contains("Install a local model"))

        val persisted = database.aiJobDao().getById(jobId)
        assertEquals(AiJobStatus.FAILED.name, persisted?.status)
        assertEquals(error, persisted?.errorMessage)
        // Never a fabricated result payload for a tool that didn't actually run.
        assertEquals(null, persisted?.resultPayloadJson)
    }

    @Test
    fun `a deterministic tool runs with no model at all`() = runBlocking {
        // Expand context is arithmetic over segment order. It must work on a device with nothing
        // installed and no network, which is exactly this test's environment.
        val jobId = seedJob(TranscriptAiToolType.EXPAND_CONTEXT)
        val worker = TestListenableWorkerBuilder<AiToolWorker>(context)
            .setInputData(workDataOf(AiToolWorker.KEY_JOB_ID to jobId))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val persisted = database.aiJobDao().getById(jobId)
        assertEquals(AiJobStatus.SUCCEEDED.name, persisted?.status)
        val decoded = com.example.ai.tools.ToolResultJson.decode(persisted?.resultPayloadJson)
        assertEquals("deterministic", decoded?.engine)
        assertTrue(decoded?.outcome is com.example.ai.tools.ToolOutcome.ContextExpansion)
    }

    @Test
    fun `a stored-data tool reads back what processing already found, without a model`() = runBlocking {
        val jobId = seedJob(TranscriptAiToolType.FIND_DECISIONS)
        val worker = TestListenableWorkerBuilder<AiToolWorker>(context)
            .setInputData(workDataOf(AiToolWorker.KEY_JOB_ID to jobId))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val decoded = com.example.ai.tools.ToolResultJson.decode(database.aiJobDao().getById(jobId)?.resultPayloadJson)
        assertEquals("stored", decoded?.engine)
        assertTrue(decoded?.outcome is com.example.ai.tools.ToolOutcome.Findings)
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
