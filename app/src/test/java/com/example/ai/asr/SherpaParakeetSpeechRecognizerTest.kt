package com.example.ai.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.LocalModelStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * With no Parakeet TDT model installed (the state of a fresh app install, and of this test's
 * Robolectric environment), [SherpaParakeetSpeechRecognizer] must report
 * [AiResult.ModelUnavailable] — never a fabricated transcript — without touching any
 * sherpa-onnx native class, which is also what makes this test runnable on the JVM. Real
 * inference against the actual model requires a device/emulator; see
 * docs/AI_ARCHITECTURE.md "Known Limitations".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SherpaParakeetSpeechRecognizerTest {

    @Test
    fun `reports model unavailable and produces no segments when no ASR model is installed`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val recognizer = SherpaParakeetSpeechRecognizer(LocalModelStorage(context))

        val result = recognizer.transcribe(
            audioFile = File(context.cacheDir, "does-not-matter.m4a"),
            totalDurationMs = 10_000L,
            meetingId = "m1",
            speechIntervals = emptyList(),
            options = TranscriptionOptions(),
            onProgress = { _, _ -> }
        )

        assertTrue(result is AiResult.ModelUnavailable)
    }
}
