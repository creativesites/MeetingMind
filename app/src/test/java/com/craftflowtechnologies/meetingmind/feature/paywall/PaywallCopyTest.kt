package com.craftflowtechnologies.meetingmind.feature.paywall

import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.EntitlementState
import com.craftflowtechnologies.meetingmind.core.billing.Entitlements
import com.craftflowtechnologies.meetingmind.core.billing.OfferKind
import com.craftflowtechnologies.meetingmind.core.billing.Plan
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.feature.onboarding.CaptureGoal
import com.craftflowtechnologies.meetingmind.feature.onboarding.DesiredOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaywallCopyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()
    private val yearly = PlanOffer("yearly", Plan.PRO, "\$79.99", OfferKind.YEARLY)
    private val ownedPro = Entitlements(Plan.PRO, EntitlementState.ACTIVE, now + 30 * day, willRenew = true, verifiedAtMs = now)

    @Test fun `the headline follows what the person asked for`() {
        assertEquals("Never lose an action item", PaywallCopy.outcome(emptySet(), setOf(DesiredOutcome.ACTION_ITEMS.name)).headline)
        assertEquals("Your sermons, searchable", PaywallCopy.outcome(setOf(CaptureGoal.SERMONS.name), emptySet()).headline)
        assertEquals("Get more from every recording", PaywallCopy.outcome(emptySet(), emptySet()).headline)
    }

    @Test fun `the timeline only states the store's price and period`() {
        val lines = PaywallCopy.timeline(PlanOffer("yearly", Plan.PRO, "\$79.99", OfferKind.YEARLY))
        assertTrue(lines.any { it.second.contains("\$79.99") })
        assertTrue(lines.any { it.first == "Every year" })
        assertTrue(lines.any { it.second.contains("Cancel in Google Play") })

        val trialLines = PaywallCopy.timeline(PlanOffer("yearly", Plan.PRO, "\$79.99", OfferKind.YEARLY, trialDays = 7))
        assertTrue(trialLines.any { it.second.contains("7-day free trial") })
        assertTrue(trialLines.any { it.first == "Before day 7" })
    }

    @Test fun `the included table only claims capabilities the code enforces`() {
        val paidRows = PaywallCopy.includedRows.filter { !it.free }
        assertTrue("the paid column must sell something", paidRows.isNotEmpty())
        val expectedCapabilities = setOf(
            Capability.MANAGED_CLOUD_AI,
            Capability.BYOK_KEY,
            Capability.CONNECTED_INTEGRATIONS,
            Capability.AUTOMATION_RECIPES,
            Capability.CHURCH_ORG_WORKFLOWS
        )
        paidRows.forEach { row ->
            assertTrue("a paid row needs a capability: ${row.label}", row.paid)
            val capability = row.capability
            assertNotNull("every paid row maps to a gate: ${row.label}", capability)
            assertTrue("${row.label} claims ${capability?.name}", capability in expectedCapabilities)
        }
        // The gates are exactly what the paid column claims; nothing invented, nothing missing.
        assertEquals(expectedCapabilities, paidRows.mapNotNull { it.capability }.toSet())
    }

    @Test fun `the table does not sell features that are free for everyone in 1_0`() {
        val table = PaywallCopy.includedRows.joinToString(" ") { it.label }
        listOf("Ask across", "Speaker ID", "Read-to-Me", "Circles", "unlimited").forEach {
            assertFalse("the table must not claim $it", table.contains(it, ignoreCase = true))
        }
        listOf("Ask across", "Speaker ID", "Read-to-Me", "Circles").forEach {
            assertTrue("the always-free line must name $it", PaywallCopy.ALWAYS_FREE_LINE.contains(it))
        }
    }

    @Test fun `no allowance number is hardcoded in the included table`() {
        PaywallCopy.includedRows.forEach { row ->
            assertFalse("${row.label} hardcodes a number", row.label.contains("\\d".toRegex()))
            assertFalse("${row.label} hardcodes hours", row.label.contains("hr", ignoreCase = true))
        }
    }

    @Test fun `a build with billing off never blames the connection`() {
        val off = PaywallCopy.plansUnavailableLine(configured = false)
        assertFalse(off.contains("connection", ignoreCase = true))
        assertTrue(off.contains("build"))
        val failed = PaywallCopy.plansUnavailableLine(configured = true)
        assertFalse(failed.contains("connection", ignoreCase = true))
    }

    @Test fun `an owned plan is managed, not sold again`() {
        val cta = PaywallCopy.cta(ownedPro, yearly, busy = false, pending = false)
        assertEquals(PaywallCtaKind.MANAGE, cta.kind)
        assertFalse(cta.label.startsWith("Start"))
        assertTrue(cta.enabled)
    }

    @Test fun `a pending purchase offers a retry instead of a dead button`() {
        val cta = PaywallCopy.cta(Entitlements(), yearly, busy = false, pending = true)
        assertEquals(PaywallCtaKind.RETRY, cta.kind)
        assertTrue(cta.enabled)
    }

    @Test fun `a free person can buy the selected plan`() {
        val cta = PaywallCopy.cta(Entitlements(), yearly, busy = false, pending = false)
        assertEquals(PaywallCtaKind.BUY, cta.kind)
        assertTrue(cta.enabled)
        assertEquals("Start MeetingMind Pro", cta.label)
        val trial = PaywallCopy.cta(Entitlements(), yearly.copy(trialDays = 7), busy = false, pending = false)
        assertEquals("Start 7-day free trial", trial.label)
    }

    @Test fun `the yearly individual plan is preselected`() {
        val offers = listOf(
            PlanOffer("monthly", Plan.PRO, "\$9.99", OfferKind.MONTHLY),
            PlanOffer("yearly", Plan.PRO, "\$79.99", OfferKind.YEARLY),
            PlanOffer("lifetime", Plan.PRO, "\$149.99", OfferKind.LIFETIME)
        )
        assertEquals("yearly", defaultOffer(offers)?.id)
        assertEquals("monthly", defaultOffer(listOf(offers[0]))?.id)
    }
}
