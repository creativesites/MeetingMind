package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CloudGateTest {
    private fun gate(allowed: Boolean = true, profile: ProcessingProfile = ProcessingProfile.INTERNET, entitled: Boolean = true) =
        CloudGate(allowed, { profile }, { entitled })

    @Test fun `open when the route allows, internet mode is on and the plan covers it`() {
        assertNull(runBlocking { gate().blockedReason() })
    }

    @Test fun `a route that chose the phone voice never opens`() {
        assertNotNull(runBlocking { gate(allowed = false).blockedReason() })
    }

    @Test fun `every mode but internet blocks`() {
        ProcessingProfile.entries.filter { it != ProcessingProfile.INTERNET }.forEach {
            assertNotNull(it.name, runBlocking { gate(profile = it).blockedReason() })
        }
    }

    @Test fun `losing the plan blocks`() {
        assertEquals("Your plan doesn't include the Gemini voice.", runBlocking { gate(entitled = false).blockedReason() })
    }

    @Test fun `the profile and plan are read fresh on every check`() {
        var profile = ProcessingProfile.INTERNET
        var entitled = true
        val g = CloudGate(true, { profile }, { entitled })
        assertNull(runBlocking { g.blockedReason() })
        profile = ProcessingProfile.OFFLINE
        assertNotNull(runBlocking { g.blockedReason() })
        profile = ProcessingProfile.INTERNET
        entitled = false
        assertNotNull(runBlocking { g.blockedReason() })
    }
}
