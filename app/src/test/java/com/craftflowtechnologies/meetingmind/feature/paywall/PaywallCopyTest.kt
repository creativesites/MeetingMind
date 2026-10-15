package com.craftflowtechnologies.meetingmind.feature.paywall

import com.craftflowtechnologies.meetingmind.core.billing.Plan
import com.craftflowtechnologies.meetingmind.core.billing.OfferKind
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.feature.onboarding.CaptureGoal
import com.craftflowtechnologies.meetingmind.feature.onboarding.DesiredOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaywallCopyTest {
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
