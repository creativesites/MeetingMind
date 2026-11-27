package com.craftflowtechnologies.meetingmind.core.setup

import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
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
        assertEquals("Get the speech model to transcribe offline", s.headline)
        assertEquals(s.part(SetupPart.HEAR).remainingBytes, s.essentialBytes)
    }

    @Test fun `a phone that can transcribe is never nagged for the optional extras`() {
        val hearing = SetupGuide.modelsFor(SetupPart.HEAR, 8f).map { it.id }.toSet()
        val s = SetupGuide.compute(8f, { it in hearing })
        assertTrue(s.canTranscribe)
        assertFalse(s.needsAttention)
        assertFalse(s.offlineReady) // Who spoke and Thinking are still missing, and that's fine
        assertEquals("Ready to transcribe on this phone", s.headline)
        assertEquals(0L, s.essentialBytes)
    }

    @Test fun `with internet mode a lone language model is not an alarm`() {
        val s = SetupGuide.compute(8f, { it == ModelCatalog.phi4MiniInstruct.id }, profile = ProcessingProfile.INTERNET, hasGeminiKey = true)
        assertFalse(s.thinkingOnly)
        assertFalse(s.needsAttention)
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

    @Test fun `any speech model counts as hearing, not only Parakeet`() {
        val have = setOf(ModelCatalog.sileroVad.id, ModelCatalog.whisperTurboInt8.id)
        val hear = SetupGuide.compute(8f, { it in have }).part(SetupPart.HEAR)
        assertTrue(hear.installed)
        assertFalse(hear.models.any { it.id == ModelCatalog.parakeetTdtV3Int8.id })
    }

    @Test fun `with a speech model but no helper only the small helper is left to fetch`() {
        val hear = SetupGuide.compute(8f, { it == ModelCatalog.omnilingualAsr300mInt8.id }).part(SetupPart.HEAR)
        assertFalse(hear.installed)
        assertEquals(ModelCatalog.sileroVad.sizeBytes, hear.models.filterNot { it.id == ModelCatalog.omnilingualAsr300mInt8.id }.sumOf { it.sizeBytes })
        assertTrue(hear.models.any { it.id == ModelCatalog.omnilingualAsr300mInt8.id })
    }

    @Test fun `with no speech model the one suggested follows the language`() {
        val shona = SetupGuide.compute(8f, { false }, language = "sna").part(SetupPart.HEAR)
        assertTrue(shona.models.any { it.id == ModelCatalog.omnilingualAsr300mInt8.id })
        val english = SetupGuide.compute(8f, { false }, language = "en").part(SetupPart.HEAR)
        assertTrue(english.models.any { it.id == ModelCatalog.parakeetTdtV3Int8.id })
    }

    @Test fun `a speech model other than the suggested one downloading shows as progress`() {
        val turbo = ModelCatalog.whisperTurboInt8
        val s = SetupGuide.compute(8f, { it == ModelCatalog.sileroVad.id }, mapOf(turbo.id to DownloadProgress(turbo.sizeBytes / 4, turbo.sizeBytes, running = true)))
        val hear = s.part(SetupPart.HEAR)
        assertTrue(hear.downloading)
        assertEquals(0.25f, hear.progress, 0.02f)
    }
}
