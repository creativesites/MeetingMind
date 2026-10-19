package com.craftflowtechnologies.meetingmind.ai.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [SherpaWhisperSpeechRecognizer].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SherpaWhisperSpeechRecognizerTest {

    @Test
    fun `reports model unavailable when no Whisper model is installed`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaWhisperSpeechRecognizer(LocalModelStorage(context))

        val result = recognizer.transcribe(
            audioFile = File(context.cacheDir, "test.m4a"),
            totalDurationMs = 5_000L,
            meetingId = "m1",
            speechRegions = emptyList(),
            options = TranscriptionOptions(modelId = ModelCatalog.whisperLargeV3Int8.id),
            onProgress = { _, _ -> }
        )

        assertTrue(result is AiResult.ModelUnavailable)
        assertEquals(ModelCatalog.whisperLargeV3Int8.id, (result as AiResult.ModelUnavailable).modelId)
    }

    @Test
    fun `buildWords groups BPE tokens into words with timestamps`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaWhisperSpeechRecognizer(LocalModelStorage(context))

        val tokens = arrayOf(" Welcome", " to", " the", " meeting")
        val timestamps = floatArrayOf(0.0f, 0.5f, 0.8f, 1.1f)

        val words = recognizer.buildWords(
            text = "Welcome to the meeting",
            tokens = tokens,
            timestamps = timestamps,
            windowStartMs = 1000L,
            windowEndMs = 3000L
        )

        assertEquals(4, words.size)
        assertEquals("Welcome", words[0].text)
        assertEquals(1000L, words[0].startMs)
        assertEquals("to", words[1].text)
        assertEquals(1500L, words[1].startMs)
        assertEquals("meeting", words[3].text)
        assertEquals(2100L, words[3].startMs)
        assertEquals(3000L, words[3].endMs)
    }

    @Test
    fun `buildWords ignores Whisper special timestamp tokens`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaWhisperSpeechRecognizer(LocalModelStorage(context))

        val tokens = arrayOf("<|transcribe|>", "<|0.00|>", " Hello", " world", "<|1.50|>")
        val timestamps = floatArrayOf(0.0f, 0.0f, 0.2f, 0.8f, 1.5f)

        val words = recognizer.buildWords(
            text = "Hello world",
            tokens = tokens,
            timestamps = timestamps,
            windowStartMs = 0L,
            windowEndMs = 2000L
        )

        assertEquals(2, words.size)
        assertEquals("Hello", words[0].text)
        assertEquals(200L, words[0].startMs)
        assertEquals("world", words[1].text)
        assertEquals(800L, words[1].startMs)
    }

    @Test
    fun `buildWords falls back to whitespace interpolation when tokens are empty`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaWhisperSpeechRecognizer(LocalModelStorage(context))

        val words = recognizer.buildWords(
            text = "Actions speak louder",
            tokens = emptyArray(),
            timestamps = floatArrayOf(),
            windowStartMs = 0L,
            windowEndMs = 3000L
        )

        assertEquals(3, words.size)
        assertEquals("Actions", words[0].text)
        assertEquals(0L, words[0].startMs)
        assertEquals(1000L, words[0].endMs)
        assertEquals("speak", words[1].text)
        assertEquals(1000L, words[1].startMs)
        assertEquals(2000L, words[1].endMs)
        assertEquals("louder", words[2].text)
        assertEquals(2000L, words[2].startMs)
        assertEquals(3000L, words[2].endMs)
    }

    @Test
    fun `buildWords returns empty for blank text`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaWhisperSpeechRecognizer(LocalModelStorage(context))

        val words = recognizer.buildWords(
            text = "   ",
            tokens = emptyArray(),
            timestamps = floatArrayOf(),
            windowStartMs = 0L,
            windowEndMs = 1000L
        )

        assertTrue(words.isEmpty())
    }
}
