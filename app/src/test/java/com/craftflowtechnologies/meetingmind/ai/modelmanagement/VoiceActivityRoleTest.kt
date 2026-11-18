package com.craftflowtechnologies.meetingmind.ai.modelmanagement

import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Silero finds speech; it never turns speech into text, so it must never be offered as a speech model. */
class VoiceActivityRoleTest {
    @Test fun sileroIsAHelperNotASpeechModel() {
        val vad = ModelCatalog.sileroVad
        assertTrue(ModelCapability.VOICE_ACTIVITY in vad.capability)
        assertFalse(ModelCapability.TRANSCRIPTION in vad.capability)
    }

    @Test fun everySpeechModelIsAlsoTheOnlyKindListedAsSpeech() {
        val speech = ModelCatalog.entries.filter { ModelCapability.TRANSCRIPTION in it.capability }.map { it.id }
        assertFalse(ModelCatalog.sileroVad.id in speech)
        assertEquals(5, speech.size)
    }
}
