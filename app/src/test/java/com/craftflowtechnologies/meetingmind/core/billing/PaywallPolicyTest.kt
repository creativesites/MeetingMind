package com.craftflowtechnologies.meetingmind.core.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaywallPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val on = FakeBillingGateway()
    private val free = Entitlements()
    private val paid = Entitlements(Plan.PRO, EntitlementState.ACTIVE, now + day, true, now)

    private fun show(t: PaywallTrigger, e: Entitlements = free, s: PaywallState = PaywallState(), g: BillingGateway = on) =
        PaywallPolicy.shouldShow(t, g, e, s, now)

    @Test fun `nothing is ever shown when the build has nothing to sell`() {
        PaywallTrigger.entries.forEach { assertFalse(it.name, show(it, g = DisabledBillingGateway)) }
    }

    @Test fun `opening plans from settings always works`() {
        assertTrue(show(PaywallTrigger.SETTINGS))
        assertTrue(show(PaywallTrigger.SETTINGS, e = paid))
        assertTrue(show(PaywallTrigger.SETTINGS, s = PaywallState(lastShownAtMs = now - 1000)))
    }

    @Test fun `paying people are not interrupted`() {
        listOf(PaywallTrigger.FIRST_RESULT, PaywallTrigger.PREMIUM_ACTION, PaywallTrigger.USAGE_EXHAUSTED).forEach {
            assertFalse(it.name, show(it, e = paid))
        }
    }

    @Test fun `the first-result offer shows once`() {
        assertTrue(show(PaywallTrigger.FIRST_RESULT))
        assertFalse(show(PaywallTrigger.FIRST_RESULT, s = PaywallState(firstResultOfferShown = true)))
    }

    @Test fun `one unasked offer a week`() {
        val recent = PaywallState(lastShownAtMs = now - 2 * day, firstResultOfferShown = true)
        assertFalse(show(PaywallTrigger.PREMIUM_ACTION, s = recent))
        assertFalse(show(PaywallTrigger.USAGE_EXHAUSTED, s = recent))
        val old = PaywallState(lastShownAtMs = now - 8 * day, firstResultOfferShown = true)
        assertTrue(show(PaywallTrigger.PREMIUM_ACTION, s = old))
    }

    @Test fun `a heads-up about usage waits until the first offer has been seen`() {
        assertFalse(show(PaywallTrigger.USAGE_NEAR_LIMIT))
        assertTrue(show(PaywallTrigger.USAGE_NEAR_LIMIT, s = PaywallState(firstResultOfferShown = true)))
    }

    @Test fun `an unknown trigger name opens plans as if from settings`() {
        assertTrue(PaywallTrigger.parse("nonsense") == PaywallTrigger.SETTINGS)
        assertTrue(PaywallTrigger.parse(null) == PaywallTrigger.SETTINGS)
    }

    @Test fun `production is the default and enforces the paywall`() {
        assertTrue(PaywallPolicy.shouldShow(PaywallTrigger.FIRST_RESULT, on, free, PaywallState(), now))
        assertFalse(PaywallPolicy.shouldShow(PaywallTrigger.FIRST_RESULT, on, free, PaywallState(), now, TieringMode.CLOSED_TESTING))
    }

    @Test fun `closed testing mode suppresses unasked paywalls but allows opening from settings`() {
        assertFalse(PaywallPolicy.shouldShow(PaywallTrigger.FIRST_RESULT, on, free, PaywallState(), now, TieringMode.CLOSED_TESTING))
        assertFalse(PaywallPolicy.shouldShow(PaywallTrigger.PREMIUM_ACTION, on, free, PaywallState(), now, TieringMode.CLOSED_TESTING))
        assertTrue(PaywallPolicy.shouldShow(PaywallTrigger.SETTINGS, on, free, PaywallState(), now, TieringMode.CLOSED_TESTING))
    }
}
