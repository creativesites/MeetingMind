package com.example.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.model.AiJobStatus
import com.example.core.model.TranscriptAiToolType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [AiJobRepository] is the persisted half of Phase 15 §5's "background AI jobs must survive
 * process death" requirement — these tests pin down the Room side of that contract (a job is
 * written as QUEUED before WorkManager is ever touched, cancel/retry transition status correctly)
 * without depending on a real WorkManager execution, which [AiToolWorkerTest] already covers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiJobRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase
    private lateinit var repository: AiJobRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AiJobRepository(context, database)
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
        database.close()
    }

    @Test
    fun `enqueue writes a QUEUED job immediately, visible before any worker runs`() = runBlocking {
        val jobId = repository.enqueue("m1", TranscriptAiToolType.CLEAN_TRANSCRIPT)

        val jobs = repository.getJobsForMeeting("m1").first()
        assertEquals(1, jobs.size)
        assertEquals(jobId, jobs[0].id)
        assertEquals(AiJobStatus.QUEUED, jobs[0].status)
        assertEquals(TranscriptAiToolType.CLEAN_TRANSCRIPT, jobs[0].toolType)
        assertTrue(jobs[0].isActive)
    }

    @Test
    fun `cancel marks a queued job CANCELLED immediately`() = runBlocking {
        val jobId = repository.enqueue("m1", TranscriptAiToolType.CLEAN_TRANSCRIPT)

        repository.cancel(jobId)

        val jobs = repository.getJobsForMeeting("m1").first()
        assertEquals(AiJobStatus.CANCELLED, jobs[0].status)
        assertTrue(!jobs[0].isActive)
    }

    @Test
    fun `cancel on an already-finished job is a safe no-op`() = runBlocking {
        val jobId = repository.enqueue("m1", TranscriptAiToolType.CLEAN_TRANSCRIPT)
        // Simulate the worker having already succeeded, bypassing WorkManager entirely.
        val succeeded = database.aiJobDao().getById(jobId)!!.copy(status = AiJobStatus.SUCCEEDED.name)
        database.aiJobDao().insertOrUpdate(succeeded)

        repository.cancel(jobId)

        val jobs = repository.getJobsForMeeting("m1").first()
        assertEquals(AiJobStatus.SUCCEEDED, jobs[0].status)
    }

    @Test
    fun `retry requeues a FAILED job and increments retryCount`() = runBlocking {
        val jobId = repository.enqueue("m1", TranscriptAiToolType.CLEAN_TRANSCRIPT)
        val failed = database.aiJobDao().getById(jobId)!!.copy(status = AiJobStatus.FAILED.name, errorMessage = "boom")
        database.aiJobDao().insertOrUpdate(failed)

        repository.retry(jobId)

        val jobs = repository.getJobsForMeeting("m1").first()
        assertEquals(AiJobStatus.QUEUED, jobs[0].status)
        assertEquals(null, jobs[0].errorMessage)
        assertEquals(1, jobs[0].retryCount)
    }

    @Test
    fun `retry on a job that is still queued or running is a safe no-op`() = runBlocking {
        val jobId = repository.enqueue("m1", TranscriptAiToolType.CLEAN_TRANSCRIPT)

        repository.retry(jobId)

        val jobs = repository.getJobsForMeeting("m1").first()
        assertEquals(0, jobs[0].retryCount)
    }

    @Test
    fun `jobs for a different meeting are not returned`() = runBlocking {
        database.meetingDao().insertMeeting(
            MeetingEntity(
                id = "m2", title = "Other Meeting", createdAt = System.currentTimeMillis(), durationMs = 0L,
                source = "LOCAL_RECORDING", audioFilePath = null, status = "READY", participantCount = 1, language = "en", summaryText = null
            )
        )
        repository.enqueue("m1", TranscriptAiToolType.CLEAN_TRANSCRIPT)
        repository.enqueue("m2", TranscriptAiToolType.CLEAN_TRANSCRIPT)

        val jobs = repository.getJobsForMeeting("m1").first()
        assertEquals(1, jobs.size)
        assertEquals("m1", jobs[0].meetingId)
    }
}
