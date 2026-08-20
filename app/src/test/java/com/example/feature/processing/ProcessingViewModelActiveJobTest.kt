package com.example.feature.processing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.ai.pipeline.MeetingProcessingWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * The bug this guards against: a screen re-entered after the app was backgrounded/recreated must
 * never let the user re-trigger processing for a recording that already has a real WorkManager
 * job in flight — [ProcessingViewModel] must derive "is this recording already processing?" from
 * WorkManager's own persisted, per-meeting-tagged state, never from ephemeral Compose/ViewModel
 * memory alone.
 *
 * Every enqueued request here uses a 1-day initial delay so it deterministically stays ENQUEUED
 * for the duration of the test — it must never actually execute [MeetingProcessingWorker.doWork],
 * which needs real audio files, installed models, and Room data this test doesn't set up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessingViewModelActiveJobTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    private fun enqueuePermanentlyPendingJob(meetingId: String) {
        val request = OneTimeWorkRequestBuilder<MeetingProcessingWorker>()
            .addTag(MeetingProcessingWorker.meetingWorkTag(meetingId))
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniqueWork(MeetingProcessingWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    @Test
    fun `attachIfAlreadyRunning finds an existing job for this meeting and does not enqueue a second one`() = runBlocking {
        val meetingId = "m1"
        enqueuePermanentlyPendingJob(meetingId)

        val viewModel = ProcessingViewModel(ApplicationProvider.getApplicationContext())
        var completedId: String? = null
        val attached = viewModel.attachIfAlreadyRunning(meetingId) { completedId = it }

        assertTrue("Should report an active job was found", attached)
        val infosForTag = workManager.getWorkInfosByTag(MeetingProcessingWorker.meetingWorkTag(meetingId)).get()
        assertEquals("Attaching must not enqueue a duplicate job for the same meeting", 1, infosForTag.size)
        assertEquals(WorkInfo.State.ENQUEUED, infosForTag[0].state)
        assertNull("Should not have completed yet — the job is still pending", completedId)
    }

    @Test
    fun `attachIfAlreadyRunning returns false when this meeting has no active job`() = runBlocking {
        val viewModel = ProcessingViewModel(ApplicationProvider.getApplicationContext())

        val attached = viewModel.attachIfAlreadyRunning("meeting_with_no_job") { }

        assertFalse(attached)
    }

    @Test
    fun `an active job for one meeting does not falsely report as active for a different meeting`() = runBlocking {
        enqueuePermanentlyPendingJob("meeting_A")

        val viewModel = ProcessingViewModel(ApplicationProvider.getApplicationContext())
        val attachedToUnrelatedMeeting = viewModel.attachIfAlreadyRunning("meeting_B") { }

        assertFalse("Tag-based lookup must be per-meeting, not global", attachedToUnrelatedMeeting)
    }

    @Test
    fun `startPipeline refuses to enqueue a second job when one is already active for the same meeting`() = runBlocking {
        val meetingId = "m1"
        enqueuePermanentlyPendingJob(meetingId)
        val infosBefore = workManager.getWorkInfosByTag(MeetingProcessingWorker.meetingWorkTag(meetingId)).get()
        assertEquals(1, infosBefore.size)

        val viewModel = ProcessingViewModel(ApplicationProvider.getApplicationContext())
        // Deliberately calling the "start a fresh run" entry point on a meeting that already has
        // one queued — this must attach instead of enqueuing, even though the caller didn't
        // check first (belt-and-suspenders: the UI is expected to check via attachIfAlreadyRunning,
        // but startPipeline itself must never trust that alone).
        viewModel.startPipeline(meetingId, "/fake/audio.m4a", 1000L, expectedSpeakerCount = null) { }

        val infosAfter = workManager.getWorkInfosByTag(MeetingProcessingWorker.meetingWorkTag(meetingId)).get()
        assertEquals("Still exactly one job for this meeting, not two", 1, infosAfter.size)
    }

    @Test
    fun `retry before any pipeline has ever started is a safe no-op`() {
        val viewModel = ProcessingViewModel(ApplicationProvider.getApplicationContext())

        // Must not throw, and must not enqueue anything, since there is no meetingId to retry yet.
        viewModel.retry("/fake/audio.m4a", 1000L, expectedSpeakerCount = null) { }
    }
}
