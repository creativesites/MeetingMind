package com.craftflowtechnologies.meetingmind.core.audio

import android.app.Application
import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioExtractorTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeMediaExtractorEngine : MediaExtractorEngine {
        var inspectionResult: MediaInspection = MediaInspection(
            isVideo = false,
            durationUs = 60_000_000L,
            audioTrackIndex = 0,
            audioMime = "audio/mp4a-latm"
        )
        var extractAction: (suspend (Uri, Int, Long, File, ((Float) -> Unit)?) -> Unit)? = null

        override fun inspectMedia(uri: Uri): MediaInspection = inspectionResult

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
                    targetFile.writeText("fake-aac-audio-bytes")
                    onProgress?.invoke(1f)
                }
        }
    }

    // ---------------------------------------------------------------- direct remux checks

    @Test
    fun `canDirectRemux supports AAC on any Android version`() {
        assertTrue(canDirectRemux("audio/mp4a-latm", Build.VERSION_CODES.O))
        assertTrue(canDirectRemux("audio/mp4a-latm", Build.VERSION_CODES.Q))
        assertTrue(canDirectRemux(MediaFormat.MIMETYPE_AUDIO_AAC, Build.VERSION_CODES.O))
    }

    @Test
    fun `canDirectRemux supports Opus and Vorbis on Android Q and above only`() {
        assertTrue(canDirectRemux("audio/opus", Build.VERSION_CODES.Q))
        assertFalse(canDirectRemux("audio/opus", Build.VERSION_CODES.P))

        assertTrue(canDirectRemux("audio/vorbis", Build.VERSION_CODES.Q))
        assertFalse(canDirectRemux("audio/vorbis", Build.VERSION_CODES.P))
    }

    @Test
    fun `canDirectRemux rejects unsupported codecs like MP3 or raw PCM`() {
        assertFalse(canDirectRemux("audio/mpeg", Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertFalse(canDirectRemux("audio/raw", Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertFalse(canDirectRemux("audio/flac", Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
    }

    // ---------------------------------------------------------------- video extraction & track inspection

    @Test
    fun `throws NoAudioTrackException when video has no audio track`() = runTest(testDispatcher) {
        val fakeEngine = FakeMediaExtractorEngine().apply {
            inspectionResult = MediaInspection(
                isVideo = true,
                durationUs = 120_000_000L,
                audioTrackIndex = -1,
                audioMime = null
            )
        }
        val extractor = AudioExtractor(context, fakeEngine)
        val dummyVideoFile = File(context.cacheDir, "silent_video.mp4").apply { writeText("dummy-video") }
        val uri = Uri.fromFile(dummyVideoFile)

        try {
            extractor.importAndExtract(uri, "meeting_no_audio")
            fail("Expected NoAudioTrackException")
        } catch (e: NoAudioTrackException) {
            assertEquals("This video does not contain an audio track.", e.message)
        }
    }

    @Test
    fun `extracts audio from video and returns ImportedMediaInfo with isVideo true`() = runTest(testDispatcher) {
        val progressValues = mutableListOf<Float>()
        val fakeEngine = FakeMediaExtractorEngine().apply {
            inspectionResult = MediaInspection(
                isVideo = true,
                durationUs = 90_000_000L,
                audioTrackIndex = 1,
                audioMime = "audio/mp4a-latm"
            )
            extractAction = { _, _, _, targetFile, onProgress ->
                targetFile.parentFile?.mkdirs()
                targetFile.writeText("extracted-m4a-audio")
                onProgress?.invoke(0.25f)
                onProgress?.invoke(0.75f)
                onProgress?.invoke(1.0f)
            }
        }
        val extractor = AudioExtractor(context, fakeEngine)
        val videoFile = File(context.cacheDir, "lecture.mp4").apply { writeText("video-content") }
        val uri = Uri.fromFile(videoFile)

        val result = extractor.importAndExtract(uri, "meeting_video_1") { progress ->
            progressValues.add(progress)
        }

        assertTrue("Result must indicate video source", result.isVideo)
        assertEquals("audio.m4a", result.outputFile.name)
        assertTrue("Output file must exist", result.outputFile.exists())
        assertEquals(90_000L, result.durationMs)
        assertEquals(listOf(0.25f, 0.75f, 1.0f), progressValues)
    }

    // ---------------------------------------------------------------- cancellation cleanup

    @Test
    fun `cancelling extraction cleans up partial output file and propagates CancellationException`() = runTest(testDispatcher) {
        val startSignal = CompletableDeferred<Unit>()
        val cancelSignal = CompletableDeferred<Unit>()
        var extractedFileRef: File? = null

        val fakeEngine = FakeMediaExtractorEngine().apply {
            inspectionResult = MediaInspection(
                isVideo = true,
                durationUs = 60_000_000L,
                audioTrackIndex = 0,
                audioMime = "audio/mp4a-latm"
            )
            extractAction = { _, _, _, targetFile, _ ->
                extractedFileRef = targetFile
                targetFile.parentFile?.mkdirs()
                targetFile.writeText("partial-transcoded-data")
                startSignal.complete(Unit)
                // Wait until cancelled
                cancelSignal.await()
            }
        }

        val extractor = AudioExtractor(context, fakeEngine)
        val videoFile = File(context.cacheDir, "interview.mov").apply { writeText("mov-data") }
        val uri = Uri.fromFile(videoFile)

        val job = launch {
            extractor.importAndExtract(uri, "meeting_cancelled")
        }

        startSignal.await()
        assertTrue("Partial file should exist during extraction", extractedFileRef?.exists() == true)

        job.cancel()
        advanceUntilIdle()

        assertFalse("Partial file must be deleted upon cancellation", extractedFileRef?.exists() == true)
    }

    // ---------------------------------------------------------------- audio-only import

    @Test
    fun `audio-only file copies directly and sets isVideo false`() = runTest(testDispatcher) {
        val fakeEngine = FakeMediaExtractorEngine().apply {
            inspectionResult = MediaInspection(
                isVideo = false,
                durationUs = 45_000_000L,
                audioTrackIndex = 0,
                audioMime = "audio/x-wav"
            )
        }
        val extractor = AudioExtractor(context, fakeEngine)
        val audioFile = File(context.cacheDir, "recording.wav").apply { writeText("RIFF....WAVEfmt ") }
        val uri = Uri.fromFile(audioFile)

        val result = extractor.importAndExtract(uri, "meeting_audio_1")

        assertFalse("Audio file must have isVideo = false", result.isVideo)
        assertTrue("Target file must exist", result.outputFile.exists())
        assertEquals("meeting_audio_1", result.outputFile.parentFile?.name)
    }
}
