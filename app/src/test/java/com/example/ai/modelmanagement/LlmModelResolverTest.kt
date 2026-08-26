package com.example.ai.modelmanagement

import com.example.core.model.ModelCapability
import com.example.core.model.ModelTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LlmModelResolverTest {

    /** In-memory [ModelStorage] — only [isInstalled] matters to the resolver, and the other
     * members are never reached by it, so they fail loudly rather than pretending to work. */
    private class FakeModelStorage(private val installedIds: Set<String>) : ModelStorage {
        override fun getModelDirectory(modelId: String): File = error("not used by the resolver")
        override fun isInstalled(modelId: String): Boolean = modelId in installedIds
        override fun installedSizeBytes(modelId: String): Long = error("not used by the resolver")
        override fun delete(modelId: String): Boolean = error("not used by the resolver")
    }

    @Test
    fun `keeps the user's selection when that model is really installed`() {
        val storage = FakeModelStorage(
            setOf(ModelCatalog.qwen25_0_5bInstruct.id, ModelCatalog.qwen25_1_5bInstruct.id)
        )

        assertEquals(
            ModelCatalog.qwen25_0_5bInstruct.id,
            LlmModelResolver.resolve(ModelCatalog.qwen25_0_5bInstruct.id, storage)
        )
    }

    @Test
    fun `falls back to the best installed model when the selected one was deleted`() {
        val storage = FakeModelStorage(
            setOf(ModelCatalog.qwen25_0_5bInstruct.id, ModelCatalog.phi4MiniInstruct.id)
        )

        // Selected the 1.5B model, but it is not installed; Phi-4-mini is the highest tier present.
        assertEquals(
            ModelCatalog.phi4MiniInstruct.id,
            LlmModelResolver.resolve(ModelCatalog.qwen25_1_5bInstruct.id, storage)
        )
    }

    @Test
    fun `falls back to the only installed model even when it is the smallest tier`() {
        val storage = FakeModelStorage(setOf(ModelCatalog.qwen25_0_5bInstruct.id))

        assertEquals(
            ModelCatalog.qwen25_0_5bInstruct.id,
            LlmModelResolver.resolve(ModelCatalog.phi4MiniInstruct.id, storage)
        )
    }

    @Test
    fun `returns the selection unchanged when nothing is installed so the error names the real choice`() {
        val storage = FakeModelStorage(emptySet())

        assertEquals(
            ModelCatalog.qwen25_1_5bInstruct.id,
            LlmModelResolver.resolve(ModelCatalog.qwen25_1_5bInstruct.id, storage)
        )
    }

    @Test
    fun `never substitutes a non-summarization model`() {
        // Only the ASR model is installed — it cannot stand in for a Meeting Intelligence model.
        val storage = FakeModelStorage(setOf(ModelCatalog.parakeetTdtV3Int8.id))

        assertEquals(
            ModelCatalog.qwen25_1_5bInstruct.id,
            LlmModelResolver.resolve(ModelCatalog.qwen25_1_5bInstruct.id, storage)
        )
    }

    // --- resolveForModeOrNull: device/mode-aware tier selection for transcript cleanup ---

    @Test
    fun `resolveForModeOrNull returns the exact preferred tier when it is installed`() {
        val storage = FakeModelStorage(
            setOf(ModelCatalog.qwen25_0_5bInstruct.id, ModelCatalog.qwen25_1_5bInstruct.id, ModelCatalog.phi4MiniInstruct.id)
        )

        assertEquals(
            ModelCatalog.qwen25_1_5bInstruct.id,
            LlmModelResolver.resolveForModeOrNull(storage, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.RECOMMENDED)
        )
    }

    @Test
    fun `resolveForModeOrNull degrades to the closest installed tier when the preferred one is missing`() {
        // Aggressive mode prefers HIGH_QUALITY, but only LIGHTWEIGHT is installed — never assume
        // the biggest model is best, but also never silently fail when it simply isn't installed.
        val storage = FakeModelStorage(setOf(ModelCatalog.qwen25_0_5bInstruct.id))

        assertEquals(
            ModelCatalog.qwen25_0_5bInstruct.id,
            LlmModelResolver.resolveForModeOrNull(storage, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.HIGH_QUALITY)
        )
    }

    @Test
    fun `resolveForModeOrNull never returns a tier further away than the closest installed one`() {
        // Preferred RECOMMENDED is missing; both LIGHTWEIGHT and HIGH_QUALITY sit one tier away —
        // whichever it picks must never be a model further from RECOMMENDED than either of these,
        // i.e. it must never fall through to something even less appropriate.
        val storage = FakeModelStorage(setOf(ModelCatalog.qwen25_0_5bInstruct.id, ModelCatalog.phi4MiniInstruct.id))

        val resolved = LlmModelResolver.resolveForModeOrNull(storage, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.RECOMMENDED)
        assertTrue(resolved == ModelCatalog.qwen25_0_5bInstruct.id || resolved == ModelCatalog.phi4MiniInstruct.id)
    }

    @Test
    fun `resolveForModeOrNull returns null when nothing with the capability is installed`() {
        val storage = FakeModelStorage(setOf(ModelCatalog.parakeetTdtV3Int8.id))

        assertNull(
            LlmModelResolver.resolveForModeOrNull(storage, ModelCapability.TRANSCRIPT_CLEANUP, ModelTier.RECOMMENDED)
        )
    }
}
