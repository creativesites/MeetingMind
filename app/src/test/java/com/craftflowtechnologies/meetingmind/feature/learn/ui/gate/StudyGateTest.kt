package com.craftflowtechnologies.meetingmind.feature.learn.ui.gate

import android.app.Activity
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.BillingGateway
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.ClosedTesting
import com.craftflowtechnologies.meetingmind.core.billing.EntitlementState
import com.craftflowtechnologies.meetingmind.core.billing.Entitlements
import com.craftflowtechnologies.meetingmind.core.billing.Plan
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.core.billing.PurchaseResult
import com.craftflowtechnologies.meetingmind.feature.learn.ui.share.CircleSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Each paid Study extra is gated through [StudyGate]: locked when billing is configured and the learner is not
 * entitled; open when entitled, under closed testing, or when there is nothing to buy.
 */
class StudyGateTest {
    private class FakeGateway(configured: Boolean, entitlements: Entitlements) : BillingGateway {
        override val configured: Boolean = configured
        private val state = MutableStateFlow(entitlements)
        override val entitlements: StateFlow<Entitlements> = state
        override suspend fun offers(): List<PlanOffer> = emptyList()
        override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult = PurchaseResult.Failed("test")
        override suspend fun restore(): PurchaseResult = PurchaseResult.Failed("test")
        override suspend fun refresh() = Unit
        override fun appUserId(): String? = null
    }

    private val paid = Entitlements(
        plan = Plan.PRO,
        state = EntitlementState.ACTIVE,
        expiresAtMs = null,
        willRenew = false,
        verifiedAtMs = Long.MAX_VALUE
    )

    private lateinit var originalGateway: BillingGateway
    private var originalAllAccess = false

    @Before fun saveBilling() {
        originalGateway = Billing.gateway
        originalAllAccess = ClosedTesting.allAccess
        ClosedTesting.allAccess = false
    }

    @After fun restoreBilling() {
        Billing.gateway = originalGateway
        ClosedTesting.allAccess = originalAllAccess
    }

    @Test
    fun `with billing configured and no paid plan, the exam plan and the lecture switch are locked`() {
        Billing.gateway = FakeGateway(configured = true, entitlements = Entitlements())

        assertFalse(StudyGate.open(Capability.DEEP_SYNTHESIS))
        assertFalse(StudyGate.open(Capability.AUTOMATION_RECIPES))
        assertFalse(StudyGate.open(Capability.CHURCH_ORG_WORKFLOWS))
    }

    @Test
    fun `with a paid plan the extras are open`() {
        Billing.gateway = FakeGateway(configured = true, entitlements = paid)

        assertTrue(StudyGate.open(Capability.DEEP_SYNTHESIS))
        assertTrue(StudyGate.open(Capability.AUTOMATION_RECIPES))
    }

    @Test
    fun `under closed testing everything is open, even without a paid plan`() {
        Billing.gateway = FakeGateway(configured = true, entitlements = Entitlements())
        ClosedTesting.allAccess = true

        assertTrue(StudyGate.open(Capability.DEEP_SYNTHESIS))
        assertTrue(StudyGate.open(Capability.CHURCH_ORG_WORKFLOWS))
    }

    @Test
    fun `when there is nothing to buy, everything stays open as it is today`() {
        Billing.gateway = FakeGateway(configured = false, entitlements = Entitlements())

        assertTrue(StudyGate.open(Capability.DEEP_SYNTHESIS))
        assertTrue(StudyGate.open(Capability.CHURCH_ORG_WORKFLOWS))
    }

    @Test
    fun `one Circle is free, and a second at once is refused without the paid capability`() {
        val one = CircleSelection.toggle(emptySet(), "a", multiCircleOpen = false)
        assertEquals(setOf("a"), one.selected)
        assertFalse(one.blocked)

        val second = CircleSelection.toggle(one.selected, "b", multiCircleOpen = false)
        assertEquals(setOf("a"), second.selected)
        assertTrue(second.blocked)
    }

    @Test
    fun `several Circles at once are allowed when the capability is open, and a chosen Circle can be unchosen`() {
        val two = CircleSelection.toggle(setOf("a"), "b", multiCircleOpen = true)
        assertEquals(setOf("a", "b"), two.selected)
        assertFalse(two.blocked)

        val back = CircleSelection.toggle(two.selected, "a", multiCircleOpen = false)
        assertEquals(setOf("b"), back.selected)
        assertFalse(back.blocked)
    }
}
