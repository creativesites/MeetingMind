package com.craftflowtechnologies.meetingmind.core.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingModelTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun paid(plan: Plan = Plan.PRO, state: EntitlementState = EntitlementState.ACTIVE, expires: Long? = now + 30 * day, verified: Long = now) =
        Entitlements(plan, state, expires, willRenew = true, verifiedAtMs = verified)

    @Test fun `the longest package prefix wins`() {
        assertEquals(Plan.PRO_PLUS, Plan.fromPackageId("pro_plus_yearly"))
        assertEquals(Plan.PRO, Plan.fromPackageId("pro_monthly"))
        assertEquals(Plan.CHURCH, Plan.fromPackageId("church_yearly"))
        assertNull(Plan.fromPackageId("something_else"))
        assertNull(Plan.fromPackageId("professional_monthly"))
    }

    @Test fun `entitlement ids map to plans and free has none`() {
        assertEquals(Plan.PRO_PLUS, Plan.fromEntitlementId("pro_plus"))
        assertNull(Plan.fromEntitlementId(""))
        assertNull(Plan.fromEntitlementId("free"))
    }

    @Test fun `only managed plans get managed cloud AI`() {
        assertFalse(paid(Plan.BYOK).has(Capability.MANAGED_CLOUD_AI))
        assertTrue(paid(Plan.PRO).has(Capability.MANAGED_CLOUD_AI))
        assertTrue(paid(Plan.CHURCH).has(Capability.MANAGED_CLOUD_AI))
        assertFalse(Entitlements().has(Capability.MANAGED_CLOUD_AI))
    }

    @Test fun `trial and grace count as paid, expired does not`() {
        assertTrue(paid(state = EntitlementState.TRIAL).isPaid)
        assertTrue(paid(state = EntitlementState.GRACE).isPaid)
        assertFalse(paid(state = EntitlementState.EXPIRED).isPaid)
        assertFalse(paid(state = EntitlementState.NONE).isPaid)
    }

    @Test fun `a lapsed entitlement falls back to free`() {
        val lapsed = paid(expires = now - 1)
        val e = lapsed.effective(now)
        assertEquals(Plan.FREE, e.plan)
        assertFalse(e.isPaid)
    }

    @Test fun `an unconfirmed entitlement stops being trusted after a week offline`() {
        val old = paid(verified = now - Entitlements.MAX_UNVERIFIED_MS - 1)
        assertFalse(old.effective(now).isPaid)
        assertTrue(paid(verified = now - day).effective(now).isPaid)
    }

    @Test fun `a current confirmed entitlement is kept as it is`() {
        val e = paid()
        assertEquals(e, e.effective(now))
    }

    @Test fun `no expiry date means it stays until the store says otherwise`() {
        assertTrue(paid(expires = null).effective(now).isPaid)
    }
}
