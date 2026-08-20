package com.example.ai.vad

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
 * With no Silero VAD model installed (the state of a fresh app install, and of this test's
 * Robolectric environment, which has no real model file on disk), [SileroVadDetector] must
 * report [AiResult.ModelUnavailable] without ever touching a sherpa-onnx native class — which
 * is also what makes this test able to run at all on the JVM (see
 * docs/AI_ARCHITECTURE.md "Known Limitations" for why real inference cannot be unit tested here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SileroVadDetectorTest {

    @Test
    fun `reports model unavailable when no VAD model is installed`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val detector = SileroVadDetector(LocalModelStorage(context))

        val result = detector.detectSpeechIntervals(File(context.cacheDir, "does-not-matter.m4a"), 5000L)

        assertTrue(result is AiResult.ModelUnavailable)
    }
}
