package com.craftflowtechnologies.meetingmind.ai.voice.read

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiKeyTierPolicyTest {
    @Test fun `with the gate off every key is treated as paid, so testers are never held back`() {
        assertEquals(GeminiKeyTier.PAID, GeminiKeyTierPolicy.tier(gateOn = false, confirmedPaid = false))
        assertEquals(GeminiKeyTier.PAID, GeminiKeyTierPolicy.tier(gateOn = false, confirmedPaid = true))
    }

    @Test fun `with the gate on an unconfirmed key is unknown and a confirmed one is paid`() {
        assertEquals(GeminiKeyTier.UNKNOWN, GeminiKeyTierPolicy.tier(gateOn = true, confirmedPaid = false))
        assertEquals(GeminiKeyTier.PAID, GeminiKeyTierPolicy.tier(gateOn = true, confirmedPaid = true))
    }
}
