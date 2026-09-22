package com.example.ai.routing

import androidx.test.core.app.ApplicationProvider
import com.example.ai.cloud.GeminiLanguageModel
import com.example.ai.cloud.GeminiRequest
import com.example.ai.cloud.GeminiTransport
import com.example.ai.common.AiResult
import com.example.ai.llm.MediaPipeLanguageModel
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelStorage
import com.example.core.model.ModelCapability
import com.example.core.model.ModelTier
import com.example.core.model.ProcessingProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * "Internet mode" must mean every AI stage uses Gemini, and "offline" must mean none of them do.
 * Every stage now gets its model from [LanguageModelFactory], so these cases cover all of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageModelFactoryTest {

    private class FakeModelStorage(private val installedIds: Set<String>) : ModelStorage {
        override fun getModelDirectory(modelId: String): File = File("/nonexistent/$modelId")
        override fun isInstalled(modelId: String): Boolean = modelId in installedIds
        override fun installedSizeBytes(modelId: String): Long = 0L
        override fun delete(modelId: String): Boolean = false
    }

    /** Counts how often the factory asked, so a test can prove OFFLINE never even looked. */
    private class FakeTransport(private val configured: Boolean) : GeminiTransport {
        var refreshCalls = 0
        override suspend fun execute(request: GeminiRequest): AiResult<String> = AiResult.Failed("unused")
        override fun isConfigured(): Boolean = false // stale cache, as in the v18 bug
        override suspend fun refreshConfigured(): Boolean { refreshCalls++; return configured }
    }

    private val localModel = ModelCatalog.qwen25_1_5bInstruct.id

    private fun factory(transport: GeminiTransport, installed: Set<String> = setOf(localModel)) =
        LanguageModelFactory(ApplicationProvider.getApplicationContext(), FakeModelStorage(installed), transport)

    @Test
    fun `internet with a key picks Gemini even when the cached flag is stale`() = runBlocking {
        val resolved = factory(FakeTransport(configured = true))
            .resolve(ProcessingProfile.INTERNET, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.RECOMMENDED)!!

        assertTrue(resolved.isCloud)
        assertTrue(resolved.languageModel is GeminiLanguageModel)
        assertEquals(DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL, resolved.modelId)
        assertEquals(LanguageModelFactory.GEMINI_CONTEXT_BUDGET_TOKENS, resolved.contextLengthTokens)
    }

    @Test
    fun `offline never consults Gemini even with a key entered`() = runBlocking {
        val transport = FakeTransport(configured = true)
        val resolved = factory(transport)
            .resolve(ProcessingProfile.OFFLINE, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.RECOMMENDED)!!

        assertFalse(resolved.isCloud)
        assertTrue(resolved.languageModel is MediaPipeLanguageModel)
        assertEquals(0, transport.refreshCalls)
    }

    @Test
    fun `internet without a key falls back to the local model`() = runBlocking {
        val resolved = factory(FakeTransport(configured = false))
            .resolve(ProcessingProfile.INTERNET, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.RECOMMENDED)!!

        assertFalse(resolved.isCloud)
        assertTrue(resolved.languageModel is MediaPipeLanguageModel)
    }

    @Test
    fun `internet without a key and no local model resolves to nothing`() = runBlocking {
        assertNull(
            factory(FakeTransport(configured = false), installed = emptySet())
                .resolve(ProcessingProfile.INTERNET, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.RECOMMENDED)
        )
    }

    @Test
    fun `internet with a key needs no local model at all`() = runBlocking {
        val resolved = factory(FakeTransport(configured = true), installed = emptySet())
            .resolve(ProcessingProfile.INTERNET, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.RECOMMENDED)

        assertTrue(resolved!!.isCloud)
    }
}
