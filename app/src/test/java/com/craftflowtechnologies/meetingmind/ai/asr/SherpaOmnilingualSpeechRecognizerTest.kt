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
 * Unit tests for [SherpaOmnilingualSpeechRecognizer].
 *
 * Verifies that:
 * 1. Honest reporting: when no Omnilingual model is installed, reports [AiResult.ModelUnavailable]
 *    without touching native classes.
 * 2. Token-to-word grouping: [SherpaOmnilingualSpeechRecognizer.buildWords] correctly groups
 *    CTC subword/character tokens and SentencePiece word-boundary markers (' ') into timed words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SherpaOmnilingualSpeechRecognizerTest {

    @Test
    fun `reports model unavailable when no Omnilingual model is installed`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaOmnilingualSpeechRecognizer(LocalModelStorage(context))

        val result = recognizer.transcribe(
            audioFile = File(context.cacheDir, "test.m4a"),
            totalDurationMs = 5_000L,
            meetingId = "m1",
            speechRegions = emptyList(),
            options = TranscriptionOptions(modelId = ModelCatalog.omnilingualAsr300mInt8.id),
            onProgress = { _, _ -> }
        )

        assertTrue(result is AiResult.ModelUnavailable)
        assertEquals(ModelCatalog.omnilingualAsr300mInt8.id, (result as AiResult.ModelUnavailable).modelId)
    }

    @Test
    fun `buildWords groups tokens and timestamps into words with SentencePiece space markers`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaOmnilingualSpeechRecognizer(LocalModelStorage(context))

        // Example: Shona phrase "mangwanani akanaka" with SentencePiece tokens
        val tokens = arrayOf(
            " ", "m", "a", "n", "g", "w", "a", "n", "a", "n", "i",
            " ", "a", "k", "a", "n", "a", "k", "a"
        )
        val timestamps = floatArrayOf(
            0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f,
            1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 1.9f
        )

        val words = recognizer.buildWords(
            tokens = tokens,
            timestamps = timestamps,
            windowStartMs = 1000L,
            windowEndMs = 3500L
        )

        assertEquals(2, words.size)
        assertEquals("mangwanani", words[0].text)
        assertEquals(1100L, words[0].startMs) // windowStartMs (1000) + first character offset (100ms)
        assertEquals("akanaka", words[1].text)
        assertEquals(2300L, words[1].startMs)
    }

    @Test
    fun `buildWords filters out CTC blank and special tokens`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaOmnilingualSpeechRecognizer(LocalModelStorage(context))

        val tokens = arrayOf("<s>", "<pad>", " ", "b", "e", "m", "b", "a", "</s>", "<unk>")
        val timestamps = floatArrayOf(0.0f, 0.05f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)

        val words = recognizer.buildWords(
            tokens = tokens,
            timestamps = timestamps,
            windowStartMs = 0L,
            windowEndMs = 1000L
        )

        assertEquals(1, words.size)
        assertEquals("bemba", words[0].text)
    }

    @Test
    fun `buildWords handles empty token sequence honestly`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaOmnilingualSpeechRecognizer(LocalModelStorage(context))

        val words = recognizer.buildWords(
            tokens = emptyArray(),
            timestamps = floatArrayOf(),
            windowStartMs = 0L,
            windowEndMs = 1000L
        )

        assertTrue(words.isEmpty())
    }
}
