package com.craftflowtechnologies.meetingmind.ai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiErrorsTest {

    private fun kind(code: Int, status: String = "", message: String = "m", retryDelay: String? = null, header: String? = null) =
        GeminiErrors.fromHttp(code, errorBody(code, status, message, retryDelay), header)

    @Test fun `status codes map to kinds`() {
        assertEquals(GeminiErrorKind.AUTH, kind(401).kind)
        assertEquals(GeminiErrorKind.AUTH, kind(403).kind)
        assertEquals(GeminiErrorKind.BILLING, kind(402).kind)
        assertEquals(GeminiErrorKind.TIMEOUT, kind(408).kind)
        assertEquals(GeminiErrorKind.NOT_FOUND, kind(404).kind)
        assertEquals(GeminiErrorKind.BAD_REQUEST, kind(400, "INVALID_ARGUMENT").kind)
        assertEquals(GeminiErrorKind.SERVER, kind(500).kind)
        assertEquals(GeminiErrorKind.SERVER, kind(503, "UNAVAILABLE").kind)
        assertEquals(GeminiErrorKind.SERVER, kind(504).kind)
        assertEquals(GeminiErrorKind.RATE_LIMITED, kind(429, "RESOURCE_EXHAUSTED").kind)
        assertEquals(GeminiErrorKind.BAD_REQUEST, kind(422).kind)
    }

    @Test fun `retryable and needs-user follow the kind`() {
        val retryable = setOf(GeminiErrorKind.RATE_LIMITED, GeminiErrorKind.SERVER, GeminiErrorKind.TIMEOUT, GeminiErrorKind.NETWORK, GeminiErrorKind.MALFORMED, GeminiErrorKind.EMPTY_AUDIO)
        val user = setOf(GeminiErrorKind.NO_KEY, GeminiErrorKind.AUTH, GeminiErrorKind.BILLING, GeminiErrorKind.QUOTA_EXHAUSTED)
        GeminiErrorKind.entries.forEach {
            val e = GeminiError(it, null, "m")
            assertEquals(it.name, it in retryable, e.retryable)
            assertEquals(it.name, it in user, e.needsUser)
            assertFalse("${it.name} is both", e.retryable && e.needsUser)
        }
    }

    @Test fun `retry delay is read from the header and from the error details`() {
        assertEquals(7000L, kind(429, header = "7").retryAfterMs)
        assertEquals(34500L, kind(429, retryDelay = "34.5s").retryAfterMs)
        assertNull(kind(429).retryAfterMs)
        assertEquals(GeminiErrorKind.RATE_LIMITED, kind(429, retryDelay = "30s").kind)
        assertEquals(GeminiErrorKind.QUOTA_EXHAUSTED, kind(429, retryDelay = "120s").kind)
    }

    @Test fun `a non-JSON body still yields an error with a message`() {
        val e = GeminiErrors.fromHttp(502, "Bad Gateway")
        assertEquals(GeminiErrorKind.SERVER, e.kind)
        assertTrue(e.message.isNotBlank())
        assertEquals("Gemini 502: request failed", e.legacyMessage)
    }
}
