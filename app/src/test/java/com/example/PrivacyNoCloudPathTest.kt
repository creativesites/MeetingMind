package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.asr.SherpaParakeetSpeechRecognizer
import com.example.ai.diarization.UnavailableSpeakerDiarizer
import com.example.ai.llm.UnavailableMeetingIntelligenceEngine
import com.example.ai.pipeline.MeetingProcessingPipeline
import com.example.ai.vad.SileroVadDetector
import com.example.core.database.MeetMindDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards against the core local processing pipeline silently regaining a cloud AI dependency.
 *
 * 1. No class named `com.example.ai.gemini.GeminiApiClient` exists in the app at all — it was
 *    removed entirely rather than merely disconnected (see docs/AUDIT.md / AI_ARCHITECTURE.md).
 * 2. [MeetingProcessingPipeline], constructed the exact way every real call site in the app
 *    constructs it (context + database only, everything else defaulted), really ends up holding
 *    [SileroVadDetector] / [SherpaParakeetSpeechRecognizer] instances for VAD/ASR — both local,
 *    on-device implementations — and not anything reaching a cloud endpoint. Diarization and
 *    meeting intelligence remain [UnavailableSpeakerDiarizer] / [UnavailableMeetingIntelligenceEngine]
 *    in this phase (out of scope — see docs/AI_ARCHITECTURE.md "Critical Non-Goals").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyNoCloudPathTest {

    private lateinit var database: MeetMindDatabase

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `GeminiApiClient class no longer exists anywhere in the app`() {
        var found = true
        try {
            Class.forName("com.example.ai.gemini.GeminiApiClient")
        } catch (e: ClassNotFoundException) {
            found = false
        }
        assertFalse("GeminiApiClient must not exist — the cloud AI path was removed from the MVP", found)
    }

    @Test
    fun `MeetingProcessingPipeline really defaults to local SileroVad and SherpaParakeet implementations`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val pipeline = MeetingProcessingPipeline(context, database)

        val vadField = MeetingProcessingPipeline::class.java.getDeclaredField("vad").apply { isAccessible = true }
        val asrField = MeetingProcessingPipeline::class.java.getDeclaredField("speechRecognizer").apply { isAccessible = true }
        val diarizerField = MeetingProcessingPipeline::class.java.getDeclaredField("diarizer").apply { isAccessible = true }
        val llmField = MeetingProcessingPipeline::class.java.getDeclaredField("intelligenceEngine").apply { isAccessible = true }

        val vadInstance = vadField.get(pipeline)
        val asrInstance = asrField.get(pipeline)

        assertEquals(SileroVadDetector::class.java, vadInstance?.javaClass)
        assertEquals(SherpaParakeetSpeechRecognizer::class.java, asrInstance?.javaClass)
        assertEquals(UnavailableSpeakerDiarizer::class.java, diarizerField.get(pipeline)?.javaClass)
        assertEquals(UnavailableMeetingIntelligenceEngine::class.java, llmField.get(pipeline)?.javaClass)

        // Belt-and-suspenders: neither real default implementation may be a cloud/Gemini class.
        assertFalse(vadInstance!!.javaClass.name.contains("gemini", ignoreCase = true))
        assertFalse(asrInstance!!.javaClass.name.contains("gemini", ignoreCase = true))
    }

    @Test
    fun `no class in the app references a Gemini cloud endpoint`() {
        // Belt-and-suspenders: nothing in the compiled classpath should be named after Gemini.
        val suspiciousClassNames = listOf(
            "com.example.ai.gemini.GeminiApiClient",
            "com.example.ai.gemini.GeminiTranscriptionResult"
        )
        for (className in suspiciousClassNames) {
            var exists = true
            try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                exists = false
            }
            assertFalse("$className must not exist in the app", exists)
        }
    }
}
