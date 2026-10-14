package com.craftflowtechnologies.meetingmind.ai.cloud

import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.feature.paywall.UsageWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStateTest {
    private fun headers(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get
    private val full = arrayOf(
        "X-Plan" to "pro", "X-Usage-Audio-Used" to "4800", "X-Usage-Audio-Limit" to "6000",
        "X-Usage-Actions-Used" to "10", "X-Usage-Actions-Limit" to "300", "X-Usage-Period-End" to "1000", "X-Founder-Until" to "5000"
    )

    @Test fun `a reply without usage headers changes nothing`() {
        assertNull(UsageState.fromHeaders(200, headers()))
    }

    @Test fun `usage headers are read as the server sent them`() {
        val s = UsageState.fromHeaders(200, headers(*full))!!
        assertEquals("pro", s.plan)
        assertEquals(4800L, s.audioUsedSeconds)
        assertEquals(6000L, s.audioLimitSeconds)
        assertEquals(300L, s.actionsLimit)
        assertEquals(5000L, s.founderUntilMs)
        assertFalse(s.exhausted)
    }

    @Test fun `exhausted needs a refusal and the allowance header`() {
        val h = headers(*full, "X-Allowance" to "exhausted")
        assertTrue(UsageState.fromHeaders(429, h)!!.exhausted)
        assertTrue(UsageState.fromHeaders(402, h)!!.exhausted)
        assertFalse(UsageState.fromHeaders(200, h)!!.exhausted)
        assertFalse(UsageState.fromHeaders(429, headers(*full))!!.exhausted)
    }

    @Test fun `the fullest meter decides, and a plan with no allowance on a meter ignores it`() {
        val s = UsageState.fromHeaders(200, headers(*full))!!
        assertEquals(0.8, s.fraction, 0.0001)
        assertTrue(s.nearLimit)
        val freeish = UsageState(known = true, audioLimitSeconds = 0, actionsUsed = 1, actionsLimit = 10)
        assertEquals(0.1, freeish.fraction, 0.0001)
        assertFalse(freeish.nearLimit)
        assertEquals(0.0, UsageState(known = true).fraction, 0.0)
    }

    @Test fun `the summary says only what the plan has`() {
        assertEquals("80 of 100 min cloud transcription · 10 of 300 AI actions", UsageState.fromHeaders(200, headers(*full))!!.summary())
        assertEquals("1 of 10 AI actions", UsageState(known = true, actionsUsed = 1, actionsLimit = 10).summary())
        assertEquals("", UsageState(known = true).summary())
    }

    @Test fun `each warning level shows once a period, for everyone, and only when plans exist`() {
        val near = UsageState.fromHeaders(200, headers(*full))!!
        assertEquals(PaywallTrigger.USAGE_NEAR_LIMIT, UsageWarning.next(near, true, null))
        assertNull(UsageWarning.next(near, true, UsageWarning.tag(near, PaywallTrigger.USAGE_NEAR_LIMIT)))
        assertNull(UsageWarning.next(near, false, null))
        assertNull(UsageWarning.next(UsageState(), true, null))
        val out = near.copy(exhausted = true)
        assertEquals(PaywallTrigger.USAGE_EXHAUSTED, UsageWarning.next(out, true, UsageWarning.tag(near, PaywallTrigger.USAGE_NEAR_LIMIT)))
        // A new period warns again.
        assertEquals(PaywallTrigger.USAGE_NEAR_LIMIT, UsageWarning.next(near.copy(periodEndMs = 2000), true, UsageWarning.tag(near, PaywallTrigger.USAGE_NEAR_LIMIT)))
    }

    @Test fun `the managed url must be https`() {
        assertEquals("https://proxy.example", GeminiRoutes.managedBase(" https://proxy.example/ "))
        assertNull(GeminiRoutes.managedBase("http://proxy.example"))
        assertNull(GeminiRoutes.managedBase(""))
    }

    @Test fun `own-key routes sign in the query, managed routes sign in headers and never send a key`() {
        val own = GeminiRoute("https://generativelanguage.googleapis.com", "K", emptyMap())
        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/a?key=K", own.url("/v1beta/files/a"))
        assertFalse(own.managed)
        val managed = GeminiRoute("https://proxy.example", null, mapOf("X-Install-Id" to "id", "X-RC-User" to "u"))
        val req = managed.builder("/v1beta/models/m:generateContent", operation = true).build()
        assertEquals("https://proxy.example/v1beta/models/m:generateContent", req.url.toString())
        assertEquals("id", req.header("X-Install-Id"))
        assertEquals("u", req.header("X-RC-User"))
        assertTrue(req.header("X-Operation-Id")!!.length >= 8)
        assertTrue(managed.managed)
        // Own-key requests never carry an operation id (Google would just ignore it, but it isn't ours to send).
        assertNull(own.builder("/x", operation = true).build().header("X-Operation-Id"))
    }
}
