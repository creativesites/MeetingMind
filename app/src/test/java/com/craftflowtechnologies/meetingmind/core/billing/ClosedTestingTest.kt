package com.craftflowtechnologies.meetingmind.core.billing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.BuildConfig
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClosedTestingTest {
    private val savedGateway = Billing.gateway
    private val savedAllAccess = ClosedTesting.allAccess

    @Before
    fun setUp() {
        Billing.gateway = DisabledBillingGateway
    }

    @After
    fun tearDown() {
        ClosedTesting.allAccess = savedAllAccess
        Billing.gateway = savedGateway
        TieringModeStore.setForTest(TieringMode.PRODUCTION)
    }

    @Test
    fun `a debug build never turns tiers off by itself`() {
        assertFalse(BuildConfig.CLOSED_TESTING_ALL_ACCESS)
    }

    @Test
    fun `with tiers off every capability is open even with no plan`() {
        ClosedTesting.allAccess = true
        Capability.entries.forEach { assertTrue(it.name, FeatureGate.can(it)) }
    }

    @Test
    fun `with tiers on a free person gets no paid capability`() {
        ClosedTesting.allAccess = false
        Capability.entries.forEach { assertFalse(it.name, FeatureGate.can(it)) }
    }

    @Test
    fun `with tiers off the tier mode is closed testing whatever was saved`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        TieringModeStore.set(app, TieringMode.PRODUCTION)
        ClosedTesting.allAccess = true
        assertEquals(TieringMode.CLOSED_TESTING, TieringModeStore.current(app))
    }

    @Test
    fun `the tester gateway sells nothing and shows Pro that never lapses`() = runTest {
        val gateway = ClosedTestingBillingGateway
        assertFalse(gateway.configured)
        assertTrue(gateway.offers().isEmpty())
        val owned = gateway.entitlements.value
        assertEquals(Plan.PRO, owned.plan)
        val farFuture = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000
        assertTrue(owned.effective(farFuture).isPaid)
        assertTrue(gateway.restore() is PurchaseResult.Failed)
    }
}
