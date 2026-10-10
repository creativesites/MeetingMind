package com.craftflowtechnologies.meetingmind.ai.live

import com.craftflowtechnologies.meetingmind.ai.live.ReconnectMachine.Decision
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
class LiveReconnectTest {
    @Test fun `setup asks for session resumption and a sliding context window`() {
        val setup = GeminiLiveVoice.setupMessage("x", "Kore").getJSONObject("setup")
        assertTrue(setup.has("sessionResumption"))
        assertTrue(setup.getJSONObject("contextWindowCompression").has("slidingWindow"))
        // Prayer-friendly pauses are untouched.
        assertEquals(1100, setup.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection").getInt("silenceDurationMs"))
    }

    @Test fun `a reconnect setup carries the handle and leaves the original alone`() {
        val base = GeminiLiveVoice.setupMessage("x", "Kore")
        val resumed = GeminiLiveVoice.withResumption(base, "h-1")
        assertEquals("h-1", resumed.getJSONObject("setup").getJSONObject("sessionResumption").getString("handle"))
        assertFalse(base.getJSONObject("setup").getJSONObject("sessionResumption").has("handle"))
    }

    @Test fun `goAway and resumption updates are read from the server message`() {
        val go = GeminiLiveVoice.parse("""{"goAway":{"timeLeft":"50s"}}""") {}
        assertEquals(listOf<LiveVoiceEvent>(LiveVoiceEvent.GoAway(50_000)), go)
        val up = GeminiLiveVoice.parse("""{"sessionResumptionUpdate":{"newHandle":"abc","resumable":true}}""") {}
        assertEquals(listOf<LiveVoiceEvent>(LiveVoiceEvent.ResumptionHandle("abc", true)), up)
    }

    @Test fun `goAway reconnects at once with the latest resumable handle`() {
        val m = ReconnectMachine()
        m.onReady()
        m.onHandle("h-1", resumable = true)
        m.onHandle("h-2", resumable = false) // not a resumable point: keep the last good one
        assertEquals(Decision.Reconnect(0, "h-1", 0), m.onGoAway())
    }

    @Test fun `abnormal closes retry a bounded number of times then give up, and a restore resets the count`() {
        val m = ReconnectMachine(maxAttempts = 3)
        m.onReady(); m.onHandle("h", true)
        assertTrue(m.onDropped(1006, null) is Decision.Reconnect)
        assertTrue(m.onDropped(1011, "internal") is Decision.Reconnect)
        m.onReady() // restored
        assertEquals(0, m.attempts)
        repeat(3) { assertTrue(m.onDropped(1006, null) is Decision.Reconnect) }
        assertTrue(m.onDropped(1006, null) is Decision.GiveUp)
    }

    @Test fun `a stale handle is dropped for a fresh session on late attempts`() {
        val m = ReconnectMachine()
        m.onReady(); m.onHandle("h", true)
        assertEquals("h", (m.onDropped(1006, null) as Decision.Reconnect).handle)
        assertEquals("h", (m.onDropped(1006, null) as Decision.Reconnect).handle)
        assertNull((m.onDropped(1006, null) as Decision.Reconnect).handle)
    }

    @Test fun `ending the session never reconnects`() {
        val m = ReconnectMachine()
        m.onReady()
        m.onUserEnd()
        assertEquals(Decision.Ignore, m.onGoAway())
        assertEquals(Decision.Ignore, m.onDropped(1000, "Amen"))
    }

    @Test fun `bad keys, no quota and a failed start are not retried`() {
        val m = ReconnectMachine()
        assertTrue(m.onDropped(1006, null) is Decision.GiveUp) // never became ready
        m.onReady()
        assertTrue(m.onDropped(1007, "API key not valid") is Decision.GiveUp)
        assertTrue(m.onDropped(1011, "Resource has been exhausted (e.g. check quota).") is Decision.GiveUp)
    }

    @Test fun `loudspeaker defaults to calm, a headset to voice, and a saved choice wins`() {
        assertFalse(InterruptPolicy.voiceInterrupt(null, headset = false))
        assertTrue(InterruptPolicy.voiceInterrupt(null, headset = true))
        assertTrue(InterruptPolicy.voiceInterrupt(true, headset = false))
        assertFalse(InterruptPolicy.voiceInterrupt(false, headset = true))
    }

    @Test fun `the mic always streams when the companion is quiet, and in calm mode never while it talks`() {
        assertTrue(InterruptPolicy.micOpen(voiceInterrupt = false, companionAudible = false, bargeGateOpen = false))
        assertFalse(InterruptPolicy.micOpen(voiceInterrupt = false, companionAudible = true, bargeGateOpen = true))
        assertFalse(InterruptPolicy.micOpen(voiceInterrupt = true, companionAudible = true, bargeGateOpen = false))
        assertTrue(InterruptPolicy.micOpen(voiceInterrupt = true, companionAudible = true, bargeGateOpen = true))
    }
}
