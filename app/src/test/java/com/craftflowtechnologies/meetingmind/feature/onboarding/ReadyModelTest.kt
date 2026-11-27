package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.ai.modelmanagement.AsrModelResolver
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.core.common.DeviceCapabilityDetector
import com.craftflowtechnologies.meetingmind.core.setup.SetupGuide
import com.craftflowtechnologies.meetingmind.core.setup.SetupPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadyModelTest {
    @Test fun `internet starts with recording and offers a YouTube video's notes`() {
        val actions = readyActionsFor(SetupChoice.INTERNET)
        assertEquals(ReadyAction.RECORD, actions.first())
        assertTrue(ReadyAction.YOUTUBE in actions)
        assertTrue(ReadyAction.LOOK_AROUND in actions)
        assertFalse(ReadyAction.SET_UP_PHONE in actions)
    }

    @Test fun `on this phone starts with the download and never offers YouTube`() {
        val actions = readyActionsFor(SetupChoice.ON_DEVICE)
        assertEquals(ReadyAction.SET_UP_PHONE, actions.first())
        assertTrue(ReadyAction.RECORD in actions)
        assertFalse(ReadyAction.YOUTUBE in actions)
    }

    @Test fun `every choice keeps a way to just look around`() {
        SetupChoice.entries.forEach {
            assertTrue(ReadyAction.LOOK_AROUND in readyActionsFor(it))
            assertTrue(ReadyAction.LOOK_AROUND in readyActionsFor(it, phoneReady = true))
        }
    }

    @Test fun `a phone that is already set up records first and offers no download`() {
        assertEquals(listOf(ReadyAction.RECORD, ReadyAction.LOOK_AROUND), readyActionsFor(SetupChoice.ON_DEVICE, phoneReady = true))
        assertEquals(readyActionsFor(SetupChoice.INTERNET), readyActionsFor(SetupChoice.INTERNET, phoneReady = true))
    }

    @Test fun `the pack is the size the setup guide will download for that language`() {
        val ram = 8f
        val bemba = phonePackFor(ram, "bem", null)
        val expected = SetupPart.entries.sumOf { part -> SetupGuide.modelsFor(part, ram, "bem").sumOf { it.sizeBytes } }
        assertEquals(expected, bemba.bytes)
        // A Bemba speaker is never sized for the English-only speech model.
        assertNotEquals(ModelCatalog.parakeetTdtV3Int8.id, SetupGuide.recommendedHearer(ram, "bem").id)
        assertNotEquals(phonePackFor(ram, "en", null).bytes, bemba.bytes)
    }

    @Test fun `the pack fits only when it leaves room for recordings`() {
        val bytes = 1_000_000_000L
        assertEquals(true, PhonePack(bytes, bytes + PACK_HEADROOM_BYTES).fits)
        assertEquals(false, PhonePack(bytes, bytes + PACK_HEADROOM_BYTES - 1).fits)
        assertEquals(PACK_HEADROOM_BYTES, PhonePack(bytes, bytes + PACK_HEADROOM_BYTES).freeAfterBytes)
        assertEquals(0L, PhonePack(bytes, bytes / 2).freeAfterBytes)
    }

    @Test fun `unknown free storage makes no promise either way`() {
        val pack = PhonePack(1_000_000L, null)
        assertNull(pack.fits)
        assertNull(pack.freeAfterBytes)
    }

    @Test fun `the recording language follows the app language when it can be recorded in`() {
        assertEquals("bem", recordingLanguageFor("bem"))
        assertEquals("swa", recordingLanguageFor("sw"))
        assertEquals("zul", recordingLanguageFor("zu"))
        assertEquals("nya", recordingLanguageFor("ny"))
        assertEquals("en", recordingLanguageFor("en-US"))
        assertEquals("fr", recordingLanguageFor("fr"))
        assertEquals("pt", recordingLanguageFor("pt-BR"))
    }

    @Test fun `an unknown or missing language keeps the saved recording language`() {
        assertNull(recordingLanguageFor(null))
        assertNull(recordingLanguageFor(""))
        assertNull(recordingLanguageFor("xx"))
    }

    @Test fun `models already on the phone are never counted again`() {
        val ram = 8f
        val everything = SetupPart.entries.flatMap { SetupGuide.modelsFor(it, ram, "en") }.map { it.id }.toSet()
        val pack = phonePackFor(ram, "en", 24 * GB) { it in everything }
        assertEquals(0L, pack.bytes)
        assertTrue(pack.ready)
        assertFalse(phonePackFor(ram, "en", 24 * GB).ready)
    }

    @Test fun `an installed speech model is kept, so only the rest is sized`() {
        val ram = 8f
        val whisper = ModelCatalog.whisperTurboInt8.id
        val full = phonePackFor(ram, "en", null).bytes
        val withWhisper = phonePackFor(ram, "en", null) { it == whisper }.bytes
        assertEquals(full - SetupGuide.recommendedHearer(ram, "en").sizeBytes, withWhisper)
    }

    @Test fun `a phone with no speech model gets the one for its language`() {
        val ramMb = AsrModelResolver.ramMbOf(8f)
        assertEquals(AsrModelResolver.recommended(ramMb, "bem").id, onboardingSpeechModelId(emptySet(), PARAKEET, "bem", ramMb))
        assertNotEquals(PARAKEET, onboardingSpeechModelId(emptySet(), PARAKEET, "bem", ramMb))
    }

    @Test fun `onboarding never pushes Parakeet over another installed speech model`() {
        val ramMb = AsrModelResolver.ramMbOf(8f)
        val others = AsrModelResolver.speechModels.map { it.id }.filter { it != PARAKEET }
        val languages = listOf("en", "fr", "pt", "bem", "swa", "zul", "nya")
        others.forEach { installed ->
            languages.forEach { language ->
                // The saved selection is still the app's default (Parakeet), which isn't on the phone.
                val saved = onboardingSpeechModelId(setOf(installed), PARAKEET, language, ramMb)
                assertEquals("$installed in $language", installed, saved)
            }
        }
    }

    @Test fun `a speech model the person already chose stays chosen`() {
        val ramMb = AsrModelResolver.ramMbOf(8f)
        val whisper = ModelCatalog.whisperTurboInt8.id
        assertEquals(whisper, onboardingSpeechModelId(setOf(whisper, PARAKEET), whisper, "en", ramMb))
        assertEquals(PARAKEET, onboardingSpeechModelId(setOf(whisper, PARAKEET), PARAKEET, "en", ramMb))
    }

    @Test fun `a phone with no language model gets the one that suits its memory`() {
        val none = Installed(emptySet())
        assertEquals(DeviceCapabilityDetector.recommendedLlmModelId(8f), onboardingThinkerModelId(none, ModelCatalog.qwen25_1_5bInstruct.id, 8f))
        assertEquals(DeviceCapabilityDetector.recommendedLlmModelId(3f), onboardingThinkerModelId(none, ModelCatalog.qwen25_1_5bInstruct.id, 3f))
    }

    @Test fun `an installed language model is never replaced by onboarding`() {
        val small = ModelCatalog.qwen25_0_5bInstruct.id
        // The saved choice is the app's default, which isn't on the phone. The small one that is stays.
        assertEquals(small, onboardingThinkerModelId(Installed(setOf(small)), ModelCatalog.qwen25_1_5bInstruct.id, 8f))
        // A saved choice that is installed stays, even when the phone could run a bigger one.
        val big = ModelCatalog.qwen25_1_5bInstruct.id
        assertEquals(small, onboardingThinkerModelId(Installed(setOf(small, big)), small, 8f))
    }

    private class Installed(private val ids: Set<String>) : ModelStorage {
        override fun getModelDirectory(modelId: String): File = error("not used")
        override fun isInstalled(modelId: String): Boolean = modelId in ids
        override fun installedSizeBytes(modelId: String): Long = error("not used")
        override fun delete(modelId: String): Boolean = error("not used")
    }

    private companion object {
        const val GB = 1_000_000_000L
        val PARAKEET = ModelCatalog.parakeetTdtV3Int8.id
    }
}
