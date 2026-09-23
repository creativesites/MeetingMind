package com.example.ai.pipeline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.model.MeetingStatus
import kotlinx.coroutines.flow.first
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
import java.io.File
import java.util.concurrent.Executor

/**
 * The rules behind "transcription never starts over by itself": work is queued once, a finished
 * recording is never queued again, and a recording left PROCESSING with nothing behind it is
 * picked up again. Work is queued but never executed here (a no-op executor), since the native
 * models can't run under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessingSchedulerTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase
    private lateinit var audio: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(Executor { }).setTaskExecutor(Executor { it.run() }).build()
        )
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(database)
        audio = File(context.filesDir, "a.wav").apply { writeBytes(ByteArray(10)) }
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        database.close()
    }

    private fun meeting(id: String, status: MeetingStatus) = runBlocking {
        database.meetingDao().insertMeeting(
            MeetingEntity(id, "Sunday service", 0L, 60_000L, "LOCAL_RECORDING", audio.path, status.name, 1, "en", null)
        )
    }

    private fun work(id: String) = runBlocking {
        WorkManager.getInstance(context).getWorkInfosByTagFlow(MeetingProcessingWorker.meetingWorkTag(id)).first()
    }

    @Test
    fun `enqueue marks the recording processing and never queues it twice`() = runBlocking {
        meeting("m1", MeetingStatus.SAVED)
        val first = ProcessingScheduler.enqueue(context, "m1")
        val second = ProcessingScheduler.enqueue(context, "m1")

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(1, work("m1").size)
        assertEquals(MeetingStatus.PROCESSING.name, database.meetingDao().getMeetingById("m1")!!.status)
    }

    @Test
    fun `interrupted recordings are resumed and finished ones are left alone`() = runBlocking {
        meeting("stuck", MeetingStatus.PROCESSING)
        meeting("done", MeetingStatus.READY)

        val resumed = ProcessingScheduler.resumeInterrupted(context)

        assertEquals(listOf("stuck"), resumed)
        assertTrue(work("stuck").single().state == WorkInfo.State.ENQUEUED || work("stuck").single().state == WorkInfo.State.RUNNING)
        assertTrue(work("done").isEmpty())
        // Calling again while its work is queued does nothing.
        assertTrue(ProcessingScheduler.resumeInterrupted(context).isEmpty())
    }

    @Test
    fun `a stuck recording whose audio is gone is marked failed, not retried forever`() = runBlocking {
        database.meetingDao().insertMeeting(
            MeetingEntity("lost", "Gone", 0L, 0L, "LOCAL_RECORDING", "/nope.wav", MeetingStatus.PROCESSING.name, 1, "en", null)
        )
        assertTrue(ProcessingScheduler.resumeInterrupted(context).isEmpty())
        assertEquals(MeetingStatus.ERROR.name, database.meetingDao().getMeetingById("lost")!!.status)
    }
}
