package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.asr.SherpaParakeetSpeechRecognizer
import com.example.ai.diarization.SherpaSpeakerDiarizer
import com.example.ai.llm.RealMeetingIntelligenceEngine
import com.example.ai.pipeline.MeetingProcessingPipeline
import com.example.ai.vad.SileroVadDetector
import com.example.core.database.MeetMindDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards against the core local processing pipeline silently regaining a cloud AI dependency.
 *
 * Since the canonical-transcript overhaul MeetingMind does have a cloud path again, but a
 * fundamentally different one: reachable only from `ProcessingProfile.INTERNET`, which the user
 * chooses; never reachable from `ProcessingProfile.OFFLINE`, which is not given a network route at
 * all; and backed by no credential in the APK. These tests pin all three, plus the original
 * property they were written for — that the pipeline's own defaults are on-device implementations.
 *
 * 1. No class named `com.example.ai.gemini.GeminiApiClient` exists in the app at all — it was
 *    removed entirely rather than merely disconnected (see docs/AUDIT.md / AI_ARCHITECTURE.md).
 * 2. [MeetingProcessingPipeline], constructed the exact way every real call site in the app
 *    constructs it (context + database only, everything else defaulted), really ends up holding
 *    [SileroVadDetector] / [SherpaParakeetSpeechRecognizer] instances for VAD/ASR — both local,
 *    on-device implementations. As of Phase 2, diarization and meeting intelligence are also
 *    real local implementations — [SherpaSpeakerDiarizer] (sherpa-onnx) and
 *    [RealMeetingIntelligenceEngine] (MediaPipe LlmInference) — and neither reaches a cloud
 *    endpoint; both honestly self-report AiResult.ModelUnavailable until their models are
 *    installed. See docs/AI_ARCHITECTURE.md.
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
    fun `MeetingProcessingPipeline really defaults to local sherpa-onnx and MediaPipe implementations`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val pipeline = MeetingProcessingPipeline(context, database)

        val vadField = MeetingProcessingPipeline::class.java.getDeclaredField("vad").apply { isAccessible = true }
        val asrField = MeetingProcessingPipeline::class.java.getDeclaredField("speechRecognizer").apply { isAccessible = true }
        val diarizerField = MeetingProcessingPipeline::class.java.getDeclaredField("diarizer").apply { isAccessible = true }
        val llmField = MeetingProcessingPipeline::class.java.getDeclaredField("intelligenceEngine").apply { isAccessible = true }

        val vadInstance = vadField.get(pipeline)
        val asrInstance = asrField.get(pipeline)
        val diarizerInstance = diarizerField.get(pipeline)
        val llmInstance = llmField.get(pipeline)

        assertEquals(SileroVadDetector::class.java, vadInstance?.javaClass)
        assertEquals(SherpaParakeetSpeechRecognizer::class.java, asrInstance?.javaClass)
        assertEquals(SherpaSpeakerDiarizer::class.java, diarizerInstance?.javaClass)
        assertEquals(RealMeetingIntelligenceEngine::class.java, llmInstance?.javaClass)

        // Belt-and-suspenders: none of the real default implementations may be a cloud/Gemini class.
        assertFalse(vadInstance!!.javaClass.name.contains("gemini", ignoreCase = true))
        assertFalse(asrInstance!!.javaClass.name.contains("gemini", ignoreCase = true))
        assertFalse(diarizerInstance!!.javaClass.name.contains("gemini", ignoreCase = true))
        assertFalse(llmInstance!!.javaClass.name.contains("gemini", ignoreCase = true))
    }

    @Test
    fun `the removed unconsented Gemini client is still absent`() {
        // The original P0 finding: a cloud client that ran unconditionally, with no profile, no
        // consent and no way to turn it off. Its classes must stay gone. This says nothing about
        // ai/cloud, which is the consented replacement and is covered by the tests below.
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

    @Test
    fun `a fresh install holds no Gemini credential, so no key ships in the app`() {
        // The repository and its published releases are public, and a key compiled into an APK is
        // recoverable from that APK. The only credential the app can ever hold is one the user
        // typed into this device, so on a fresh install there is none and Internet mode reports
        // itself unavailable.
        val context: Context = ApplicationProvider.getApplicationContext()
        val pipeline = MeetingProcessingPipeline(context, database)

        val transport = MeetingProcessingPipeline::class.java
            .getDeclaredField("geminiTransport").apply { isAccessible = true }
            .get(pipeline) as com.example.ai.cloud.GeminiTransport

        assertFalse("no key has been entered, so nothing may be sent", transport.isConfigured())
        assertNull(
            "a key must never be readable from anywhere but this device's own storage",
            kotlinx.coroutines.runBlocking { com.example.ai.cloud.GeminiCredentialStore(context).getApiKey() }
        )
    }

    @Test
    fun `no Gemini API key is embedded in the built application`() {
        // A literal key in any compiled class would defeat the whole arrangement above. BuildConfig
        // is where such a thing would conventionally be put, so it is checked explicitly. The one
        // key that is meant to ship in the app, YOUVERSION_APP_KEY (a public, rate-limited app
        // identifier for Bible text, per YouVersion's design), is not a Gemini credential and is
        // deliberately not matched here.
        val buildConfigFields = Class.forName("com.example.BuildConfig").declaredFields
        for (field in buildConfigFields) {
            field.isAccessible = true
            val name = field.name.lowercase()
            assertFalse(
                "BuildConfig.${field.name} looks like an embedded credential",
                name.contains("apikey") || name.contains("api_key") || name.contains("gemini") || name.contains("secret")
            )
        }
    }

    @Test
    fun `the offline profile is given no route that requires the network`() {
        // Structural, not a runtime flag: an offline run cannot reach a cloud engine by taking a
        // wrong branch, because it is never handed a route it could use.
        val offlineRoutes = com.example.ai.routing.DefaultAiModelRouter
            .routesFor(com.example.core.model.ProcessingProfile.OFFLINE)

        assertFalse(
            offlineRoutes.any { com.example.ai.routing.DefaultAiModelRouter.route(it).requiresNetwork }
        )
    }

    @Test
    fun `the offline profile is the default everywhere a processing profile is resolved`() {
        assertEquals(
            com.example.core.model.ProcessingProfile.OFFLINE,
            com.example.core.datastore.AppPreferencesState().processingProfile
        )
        assertEquals(
            com.example.core.model.ProcessingProfile.OFFLINE,
            com.example.core.model.ProcessingProfile.fromNameOrDefault(null)
        )
        assertEquals(
            com.example.core.model.ProcessingProfile.OFFLINE,
            com.example.core.model.ProcessingProfile.fromNameOrDefault("CORRUPTED")
        )
    }
}