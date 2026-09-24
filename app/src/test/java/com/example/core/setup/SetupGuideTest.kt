package com.example.core.setup

import com.example.ai.modelmanagement.ModelCatalog
import com.example.core.model.ProcessingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuideTest {
    private val all = SetupPart.entries.flatMap { SetupGuide.modelsFor(it, 8f) }.map { it.id }.toSet()

    @Test fun `a new phone needs all three jobs`() {
        val s = SetupGuide.compute(8f, { false })
        assertFalse(s.ready)
        assertTrue(s.needsAttention)
        assertEquals(0, s.doneCount)
        assertEquals(SetupPart.entries.flatMap { SetupGuide.modelsFor(it, 8f) }.sumOf { it.sizeBytes }, s.remainingBytes)
        assertEquals("Finish setting up MeetingMind", s.headline)
    }

    @Test fun `a language model alone is the classic mistake, and is called out`() {
        val s = SetupGuide.compute(8f, { it == ModelCatalog.phi4MiniInstruct.id })
        assertTrue(s.part(SetupPart.THINK).installed) // any thinker counts, not just the recommended one
        assertTrue(s.thinkingOnly)
        assertTrue(s.headline.contains("can't hear"))
        assertTrue(s.detail.contains("speech model"))
    }

    @Test fun `everything installed means ready and quiet`() {
        val s = SetupGuide.compute(8f, { it in all })
        assertTrue(s.offlineReady && s.ready)
        assertFalse(s.needsAttention)
        assertEquals(0L, s.remainingBytes)
    }

    @Test fun `internet mode with a key is ready without downloads, without a key it isn't`() {
        assertTrue(SetupGuide.compute(8f, { false }, profile = ProcessingProfile.INTERNET, hasGeminiKey = true).let { it.ready && !it.needsAttention })
        assertTrue(SetupGuide.compute(8f, { false }, profile = ProcessingProfile.INTERNET, hasGeminiKey = false).needsAttention)
    }

    @Test fun `smaller phones get the smaller thinker`() {
        assertEquals(ModelCatalog.qwen25_0_5bInstruct.id, SetupGuide.recommendedThinker(3f).id)
        assertEquals(ModelCatalog.qwen25_1_5bInstruct.id, SetupGuide.recommendedThinker(8f).id)
    }

    @Test fun `downloads show as progress for their job`() {
        val parakeet = ModelCatalog.parakeetTdtV3Int8
        val s = SetupGuide.compute(8f, { it == ModelCatalog.sileroVad.id }, mapOf(parakeet.id to DownloadProgress(parakeet.sizeBytes / 2, parakeet.sizeBytes, running = true)))
        val hear = s.part(SetupPart.HEAR)
        assertTrue(hear.downloading)
        assertEquals(0.5f, hear.progress, 0.02f)
        assertTrue(s.downloading && s.headline.startsWith("Setting up"))
    }

    @Test fun `sizes read like people talk`() {
        assertEquals("1.6 GB", SetupGuide.formatBytes(1_598_556_720L))
        assertEquals("652 MB", SetupGuide.formatBytes(652_184_281L))
    }
}
