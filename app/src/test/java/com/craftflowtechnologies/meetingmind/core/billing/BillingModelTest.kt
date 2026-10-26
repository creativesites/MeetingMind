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

    @Test fun `the products map to their package ids and plans`() {
        assertEquals(OfferKind.MONTHLY, OfferKind.fromPackageId("monthly"))
        assertEquals(OfferKind.YEARLY, OfferKind.fromPackageId("yearly"))
        assertEquals(OfferKind.LIFETIME, OfferKind.fromPackageId("lifetime"))
        assertEquals(OfferKind.BYOK_MONTHLY, OfferKind.fromPackageId("byok_monthly"))
        assertEquals(OfferKind.BYOK_YEARLY, OfferKind.fromPackageId("byok_yearly"))
        assertEquals(OfferKind.CHURCH_MONTHLY, OfferKind.fromPackageId("church_monthly"))
        assertEquals(OfferKind.CHURCH_YEARLY, OfferKind.fromPackageId("church_yearly"))
        assertNull(OfferKind.fromPackageId("monthly_promo"))
        assertNull(OfferKind.fromPackageId("pro_monthly"))
    }

    @Test fun `paid entitlements map to BYOK, Pro, and Church`() {
        assertEquals(Plan.BYOK, Plan.fromEntitlementId("meetingmind_byok"))
        assertEquals(Plan.PRO, Plan.fromEntitlementId("meetingmind_pro"))
        assertEquals(Plan.CHURCH, Plan.fromEntitlementId("meetingmind_church"))
        assertNull(Plan.fromEntitlementId("pro"))
        assertNull(Plan.fromEntitlementId(""))
    }

    @Test fun `Pro and Church get managed cloud AI while BYOK gets the personal key`() {
        assertTrue(paid(Plan.PRO).has(Capability.MANAGED_CLOUD_AI))
        assertTrue(paid(Plan.CHURCH).has(Capability.MANAGED_CLOUD_AI))
        assertFalse(paid(Plan.BYOK).has(Capability.MANAGED_CLOUD_AI))
        assertTrue(paid(Plan.BYOK).has(Capability.BYOK_KEY))
        assertFalse(Entitlements().has(Capability.MANAGED_CLOUD_AI))
        assertFalse(Entitlements().has(Capability.BYOK_KEY))
    }

    @Test fun `the tier switch is a debug tool only`() {
        assertTrue(showTierSwitch(isDebug = true))
        assertFalse(showTierSwitch(isDebug = false))
    }

    @Test fun `FeatureGate reads the effective entitlement, so a lapsed plan cannot use BYOK`() {
        val lapsed = paid(expires = now - 1)
        assertFalse(FeatureGate.can(lapsed, Capability.BYOK_KEY, now))
        assertFalse(FeatureGate.can(lapsed, Capability.MANAGED_CLOUD_AI, now))
        assertTrue(FeatureGate.can(paid(), Capability.BYOK_KEY, now))
        assertTrue(FeatureGate.can(paid(), Capability.MANAGED_CLOUD_AI, now))
        assertTrue(FeatureGate.can(paid(Plan.BYOK), Capability.BYOK_KEY, now))
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

    @Test fun `a lifetime purchase has no expiry and stays paid`() {
        assertTrue(paid(expires = null).effective(now + day).isPaid)
    }

    @Test fun `no expiry date means it stays until the store says otherwise`() {
        assertTrue(paid(expires = null).effective(now).isPaid)
    }
}
