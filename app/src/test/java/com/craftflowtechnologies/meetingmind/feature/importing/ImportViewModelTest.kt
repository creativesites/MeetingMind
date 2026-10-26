package com.craftflowtechnologies.meetingmind.feature.importing

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.audio.AudioExtractor
import com.craftflowtechnologies.meetingmind.core.audio.MediaExtractorEngine
import com.craftflowtechnologies.meetingmind.core.audio.MediaInspection
import com.craftflowtechnologies.meetingmind.core.audio.NoAudioTrackException
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.MeetingSource
import com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportViewModelTest {

    private lateinit var application: Application
    private lateinit var database: MeetMindDatabase
    private lateinit var repository: MeetingRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(testDispatcher)
        database = Room.inMemoryDatabaseBuilder(application, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MeetingRepository(application, database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private class FakeMediaEngine : MediaExtractorEngine {
        var inspection: MediaInspection = MediaInspection(
            isVideo = true,
            durationUs = 60_000_000L,
            audioTrackIndex = 0,
            audioMime = "audio/mp4a-latm"
        )
        var extractAction: (suspend (Uri, Int, Long, File, ((Float) -> Unit)?) -> Unit)? = null

        override fun inspectMedia(uri: Uri): MediaInspection = inspection

        override suspend fun extractAudioFromVideo(
            uri: Uri,
            audioTrackIndex: Int,
            durationUs: Long,
            targetFile: File,
            onProgress: ((Float) -> Unit)?
        ) {
            extractAction?.invoke(uri, audioTrackIndex, durationUs, targetFile, onProgress)
                ?: run {
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText("fake-aac-audio")
                    onProgress?.invoke(0.5f)
                    onProgress?.invoke(1.0f)
                }
        }
    }

    @Test
    fun `video import extracts audio, tracks progress and creates meeting with IMPORTED_VIDEO source`() = runTest(testDispatcher) {
        val fakeEngine = FakeMediaEngine().apply {
            inspection = MediaInspection(
                isVideo = true,
                durationUs = 120_000_000L,
                audioTrackIndex = 0,
                audioMime = "audio/mp4a-latm"
            )
        }
        val audioExtractor = AudioExtractor(application, fakeEngine)
        val viewModel = ImportViewModel(application, audioExtractor, repository)

        val videoFile = File(application.cacheDir, "presentation.mp4").apply { writeText("fake-video") }
        val uri = Uri.fromFile(videoFile)

        viewModel.handleSelectedUri(uri)
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertFalse("Extraction should be completed", state.isExtracting)
        assertNull("Error should be null", state.error)
        assertTrue("Media should be marked as video", state.isVideo)
        assertEquals(1.0f, state.extractionProgress, 0.01f)
        assertNotNull("Meeting ID should be populated", state.meetingId)

        val meeting = repository.getMeetingByIdDirect(state.meetingId!!)
        assertNotNull("Meeting must be persisted in database", meeting)
        assertEquals(MeetingSource.IMPORTED_VIDEO, meeting?.source)
        assertEquals("presentation", meeting?.title)
    }

    @Test
    fun `audio import creates meeting with IMPORTED_AUDIO source`() = runTest(testDispatcher) {
        val fakeEngine = FakeMediaEngine().apply {
            inspection = MediaInspection(
                isVideo = false,
                durationUs = 30_000_000L,
                audioTrackIndex = 0,
                audioMime = "audio/x-m4a"
            )
        }
        val audioExtractor = AudioExtractor(application, fakeEngine)
        val viewModel = ImportViewModel(application, audioExtractor, repository)

        val audioFile = File(application.cacheDir, "voice_memo.m4a").apply { writeText("fake-audio") }
        val uri = Uri.fromFile(audioFile)

        viewModel.handleSelectedUri(uri)
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertFalse(state.isExtracting)
        assertFalse(state.isVideo)
        assertNotNull(state.meetingId)

        val meeting = repository.getMeetingByIdDirect(state.meetingId!!)
        assertNotNull(meeting)
        assertEquals(MeetingSource.IMPORTED_AUDIO, meeting?.source)
    }

    @Test
    fun `cancelExtraction aborts in-flight extraction and resets state`() = runTest(testDispatcher) {
        val startDeferred = CompletableDeferred<Unit>()
        val blockDeferred = CompletableDeferred<Unit>()

        val fakeEngine = FakeMediaEngine().apply {
            extractAction = { _, _, _, _, onProgress ->
                onProgress?.invoke(0.2f)
                startDeferred.complete(Unit)
                blockDeferred.await()
            }
        }
        val audioExtractor = AudioExtractor(application, fakeEngine)
        val viewModel = ImportViewModel(application, audioExtractor, repository)

        val videoFile = File(application.cacheDir, "long_movie.mp4").apply { writeText("movie") }
        viewModel.handleSelectedUri(Uri.fromFile(videoFile))

        testScheduler.advanceTimeBy(10)
        startDeferred.await()

        assertTrue(viewModel.importState.value.isExtracting)

        viewModel.cancelExtraction()
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertFalse("isExtracting must be reset to false", state.isExtracting)
        assertEquals(0f, state.extractionProgress, 0.01f)
        assertNull("Meeting should not be created if cancelled", state.meetingId)
    }

    @Test
    fun `video with no audio track sets descriptive error message`() = runTest(testDispatcher) {
        val fakeEngine = FakeMediaEngine().apply {
            inspection = MediaInspection(
                isVideo = true,
                durationUs = 60_000_000L,
                audioTrackIndex = -1,
                audioMime = null
            )
        }
        val audioExtractor = AudioExtractor(application, fakeEngine)
        val viewModel = ImportViewModel(application, audioExtractor, repository)

        val videoFile = File(application.cacheDir, "screen_recording_no_mic.mp4").apply { writeText("silent") }
        viewModel.handleSelectedUri(Uri.fromFile(videoFile))
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertFalse(state.isExtracting)
        assertEquals("This video does not contain an audio track.", state.error)
        assertNull(state.meetingId)
    }
}
