package com.craftflowtechnologies.meetingmind.ai.modelmanagement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModelResolverTest {
    private val parakeet = ModelCatalog.parakeetTdtV3Int8.id
    private val omni300 = ModelCatalog.omnilingualAsr300mInt8.id
    private val omni1b = ModelCatalog.omnilingualAsr1bInt8.id
    private val turbo = ModelCatalog.whisperTurboInt8.id
    private val large = ModelCatalog.whisperLargeV3Int8.id

    @Test
    fun `keeps the selected model when it is installed and handles the language`() {
        val picked = AsrModelResolver.resolve(setOf(parakeet, turbo), turbo, "en", 8192)
        assertEquals(turbo, picked?.id)
    }

    @Test
    fun `uses an installed Whisper when the selected Parakeet was never downloaded`() {
        val picked = AsrModelResolver.resolve(setOf(turbo), parakeet, "en", 8192)
        assertEquals(turbo, picked?.id)
    }

    @Test
    fun `an African recording uses the installed Omnilingual even when Parakeet is selected`() {
        val picked = AsrModelResolver.resolve(setOf(parakeet, omni300), parakeet, "sna", 8192)
        assertEquals(omni300, picked?.id)
    }

    @Test
    fun `returns null when nothing installed can handle the language`() {
        assertNull(AsrModelResolver.resolve(setOf(parakeet), parakeet, "bem", 8192))
        assertNull(AsrModelResolver.resolve(emptySet(), parakeet, "en", 8192))
    }

    @Test
    fun `a blank or unknown language accepts any installed speech model`() {
        assertEquals(omni300, AsrModelResolver.resolve(setOf(omni300), parakeet, "", null)?.id)
        assertEquals(omni300, AsrModelResolver.resolve(setOf(omni300), parakeet, "xx", null)?.id)
    }

    @Test
    fun `ignores ids that are not speech models`() {
        assertNull(AsrModelResolver.resolve(setOf(ModelCatalog.sileroVad.id, ModelCatalog.qwen25_1_5bInstruct.id), parakeet, "en", 8192))
    }

    @Test
    fun `prefers a model this phone can run when several are installed`() {
        // 3.5 GB: Whisper Large needs 4 GB, Turbo runs.
        assertEquals(turbo, AsrModelResolver.resolve(setOf(turbo, large), null, "ja", 3584)?.id)
        // 8 GB runs both, so the stronger one wins.
        assertEquals(large, AsrModelResolver.resolve(setOf(turbo, large), null, "ja", 8192)?.id)
    }

    @Test
    fun `phone locale codes are read as the app's language codes`() {
        assertEquals("sna", AsrModelResolver.normalizeLanguage("sn"))
        assertEquals("en", AsrModelResolver.normalizeLanguage("EN-us"))
        assertEquals(omni300, AsrModelResolver.resolve(setOf(parakeet, omni300), parakeet, "zu", 8192)?.id)
    }

    @Test
    fun `recommends the model that suits the phone and the language`() {
        assertEquals(parakeet, AsrModelResolver.recommended(8192, "en").id)
        assertEquals(omni300, AsrModelResolver.recommended(8192, "sna").id)
        assertEquals(turbo, AsrModelResolver.recommended(8192, "ja").id)
        // Too little memory for Parakeet, so the light multilingual model.
        assertEquals(omni300, AsrModelResolver.recommended(1800, "en").id)
        // Too little memory for Whisper, so the next model that still handles Japanese.
        assertEquals(omni300, AsrModelResolver.recommended(2560, "ja").id)
        // No language known means English.
        assertEquals(parakeet, AsrModelResolver.recommended(null, null).id)
    }

    @Test
    fun `never recommends the heaviest model when a balanced one fits`() {
        listOf("en", "sna", "ja", "fr", "").forEach { lang ->
            val id = AsrModelResolver.recommended(16384, lang).id
            assertTrue("$lang got $id", id != large && id != omni1b)
        }
    }

    @Test
    fun `saves a new selection only when the saved model is not installed`() {
        assertNull(AsrModelResolver.selectionToSave(setOf(parakeet, turbo), parakeet, "ja", 8192))
        assertEquals(turbo, AsrModelResolver.selectionToSave(setOf(turbo), parakeet, "en", 8192))
        assertNull(AsrModelResolver.selectionToSave(emptySet(), parakeet, "en", 8192))
        // Nothing installed fits the language, but something is installed: the selection still follows it.
        assertEquals(parakeet, AsrModelResolver.selectionToSave(setOf(parakeet), omni300, "bem", 8192))
    }

    @Test
    fun `every speech model has an engine name and plain guidance`() {
        AsrModelResolver.speechModels.forEach { model ->
            assertNotNull(model.id, AsrModelResolver.guide(model.id))
            assertTrue(AsrModelResolver.engineName(model.id) != model.id)
        }
        assertEquals("parakeet-tdt-0.6b-v3", AsrModelResolver.engineName(parakeet))
        assertNull(AsrModelResolver.guide(ModelCatalog.qwen25_1_5bInstruct.id))
    }
}
