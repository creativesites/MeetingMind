package com.craftflowtechnologies.meetingmind.ai.pipeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the WorkManager input-data contract on its own, without touching Room or the real AI
 * pipeline (native ASR/diarization/LLM can't run under Robolectric — see
 * [com.craftflowtechnologies.meetingmind.ai.llm.MediaPipeLanguageModelTest]'s comment on the same limitation). A missing
 * required key must fail cleanly with a real error message, never silently proceed or crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeetingProcessingWorkerTest {

    @Test
    fun `fails cleanly when meetingId is missing`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val worker = TestListenableWorkerBuilder<MeetingProcessingWorker>(context)
            .setInputData(androidx.work.workDataOf(MeetingProcessingWorker.KEY_AUDIO_PATH to "/tmp/a.wav"))
            .build()

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
        val error = (result as androidx.work.ListenableWorker.Result.Failure).outputData.getString(MeetingProcessingWorker.KEY_ERROR)
        assertEquals("Missing meetingId", error)
    }

    @Test
    fun `fails cleanly when audioPath is missing`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val worker = TestListenableWorkerBuilder<MeetingProcessingWorker>(context)
            .setInputData(androidx.work.workDataOf(MeetingProcessingWorker.KEY_MEETING_ID to "m1"))
            .build()

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
        val error = (result as androidx.work.ListenableWorker.Result.Failure).outputData.getString(MeetingProcessingWorker.KEY_ERROR)
        assertEquals("Missing audioPath", error)
    }

    @Test
    fun `a run for a recording that no longer exists fails cleanly and leaves no processing row behind`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val missingId = "deleted-${System.nanoTime()}"
        val worker = TestListenableWorkerBuilder<MeetingProcessingWorker>(context)
            .setInputData(
                androidx.work.workDataOf(
                    MeetingProcessingWorker.KEY_MEETING_ID to missingId,
                    MeetingProcessingWorker.KEY_AUDIO_PATH to "/nonexistent/audio.m4a"
                )
            )
            .build()

        val result = worker.doWork()

        assertTrue("A run with nothing to process must fail, not report success", result is androidx.work.ListenableWorker.Result.Failure)
        val job = MeetMindDatabase.getInstance(context).processingJobDao().getJobForMeeting(missingId).first()
        assertEquals(
            "A deleted recording must not leave a job row that shows as processing forever",
            null, job
        )
    }
}
