package com.craftflowtechnologies.meetingmind.core.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The offline cache must roundtrip the store's answer without ever keeping access alive past its end. */
@RunWith(RobolectricTestRunner::class)
class EntitlementCacheTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test fun `a confirmed paid entitlement roundtrips`() {
        val raw = Entitlements(Plan.PRO, EntitlementState.ACTIVE, now + 30 * day, willRenew = true, verifiedAtMs = now)
        val decoded = EntitlementCache.decode(EntitlementCache.encode(raw))
        assertEquals(raw, decoded)
        assertTrue(decoded.effective(now).isPaid)
    }

    @Test fun `a trial and a lifetime purchase roundtrip`() {
        val trial = Entitlements(Plan.CHURCH, EntitlementState.TRIAL, now + 7 * day, willRenew = true, verifiedAtMs = now)
        assertEquals(trial, EntitlementCache.decode(EntitlementCache.encode(trial)))
        val lifetime = Entitlements(Plan.BYOK, EntitlementState.ACTIVE, expiresAtMs = null, willRenew = false, verifiedAtMs = now)
        assertEquals(lifetime, EntitlementCache.decode(EntitlementCache.encode(lifetime)))
    }

    @Test fun `a lapsed entitlement roundtrips but stops being paid`() {
        val lapsed = Entitlements(Plan.PRO, EntitlementState.ACTIVE, expiresAtMs = now - 1, willRenew = false, verifiedAtMs = now - day)
        val decoded = EntitlementCache.decode(EntitlementCache.encode(lapsed))
        assertEquals(lapsed, decoded)
        val effective = decoded.effective(now)
        assertFalse(effective.isPaid)
        assertEquals(Plan.FREE, effective.plan)
        assertEquals(EntitlementState.EXPIRED, effective.state)
        assertFalse(effective.has(Capability.BYOK_KEY))
    }

    @Test fun `an unreadable value is free, never paid`() {
        assertEquals(Entitlements(), EntitlementCache.decode(null))
        assertEquals(Entitlements(), EntitlementCache.decode(""))
        assertEquals(Entitlements(), EntitlementCache.decode("not json"))
        assertEquals(Entitlements(), EntitlementCache.decode("""{"plan":"BOGUS","state":"BOGUS"}"""))
    }
}
