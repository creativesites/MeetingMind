package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiError
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingRecoveryTest {

    @Test fun `every failure offers an explicit switch to the phone voice and a message`() {
        GeminiErrorKind.entries.forEach { kind ->
            val plan = ReadingRecovery.forGemini(GeminiError(kind, null, "reason"))
            assertTrue(kind.name, Recovery.SWITCH_TO_PHONE_VOICE in plan.options)
            assertTrue(kind.name, plan.message.isNotBlank())
        }
    }

    @Test fun `a policy block can never be retried`() {
        val plan = ReadingRecovery.forGemini(GeminiError(GeminiErrorKind.POLICY_BLOCKED, null, "Offline mode"))
        assertEquals("Offline mode", plan.message)
        assertEquals(listOf(Recovery.SWITCH_TO_PHONE_VOICE), plan.options)
    }

    @Test fun `errors the person must fix point at the fix`() {
        assertTrue(Recovery.FIX_API_KEY in ReadingRecovery.forGemini(GeminiError(GeminiErrorKind.NO_KEY, null, "")).options)
        assertTrue(Recovery.FIX_API_KEY in ReadingRecovery.forGemini(GeminiError(GeminiErrorKind.AUTH, null, "")).options)
        assertTrue(Recovery.CHECK_PLAN in ReadingRecovery.forGemini(GeminiError(GeminiErrorKind.QUOTA_EXHAUSTED, null, "")).options)
        assertFalse(Recovery.RETRY in ReadingRecovery.forGemini(GeminiError(GeminiErrorKind.AUTH, null, "")).options)
    }

    @Test fun `transient errors offer a retry`() {
        listOf(GeminiErrorKind.SERVER, GeminiErrorKind.TIMEOUT, GeminiErrorKind.NETWORK, GeminiErrorKind.RATE_LIMITED).forEach {
            assertTrue(it.name, Recovery.RETRY in ReadingRecovery.forGemini(GeminiError(it, null, "")).options)
        }
        assertTrue(Recovery.CHECK_CONNECTION in ReadingRecovery.forGemini(GeminiError(GeminiErrorKind.NETWORK, null, "")).options)
    }
}
