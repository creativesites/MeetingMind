package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.voice.read.SpeechProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPurgeTest {
    private val gemini = SpeechProvider.GEMINI.name
    private val local = SpeechProvider.LOCAL.name

    @Test fun `a cloud reading is purged once the note stays on the phone`() {
        assertTrue(ReadingPurge.mustPurge(gemini, stayOnPhone = true))
    }

    @Test fun `a cloud reading is kept while the note may use the cloud`() {
        assertFalse(ReadingPurge.mustPurge(gemini, stayOnPhone = false))
    }

    @Test fun `a phone-voice reading is never purged`() {
        assertFalse(ReadingPurge.mustPurge(local, stayOnPhone = true))
        assertFalse(ReadingPurge.mustPurge(null, stayOnPhone = true))
    }

    @Test fun `the decision is deterministic`() {
        assertEquals(ReadingPurge.mustPurge(gemini, true), ReadingPurge.mustPurge(gemini, true))
    }
}
