package com.craftflowtechnologies.meetingmind.core.billing

import android.app.Activity
import com.craftflowtechnologies.meetingmind.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Closed testing with tiers off (founder decision 2026-11-17).
 *
 * While MeetingMind is in Play closed testing, a release build made with
 * `meetingmind.closedTestingAllAccess=true` gives every tester everything, as if on Pro lifetime:
 * - every [Capability] is open ([FeatureGate]);
 * - nothing is sold, so no paywall or purchase screen appears ([ClosedTestingBillingGateway]);
 * - [TieringModeStore] reports [TieringMode.CLOSED_TESTING].
 *
 * The Gemini proxy's free allowance is raised to Pro's for the same period. Turn both back for the
 * production launch (agents/PLAY_STORE.md).
 */
object ClosedTesting {
    /** From the build. Tests may set it and must restore it. */
    @Volatile var allAccess: Boolean = BuildConfig.CLOSED_TESTING_ALL_ACCESS

    const val SUMMARY = "Closed testing: everything is included"
    private const val NOTHING_TO_BUY = "Everything is included while MeetingMind is in testing."

    /** What a tester sees as their plan: Pro, active, with no end date. */
    val TESTER_ENTITLEMENTS = Entitlements(
        plan = Plan.PRO,
        state = EntitlementState.ACTIVE,
        expiresAtMs = null,
        willRenew = false,
        // Never goes stale: nothing needs to confirm it.
        verifiedAtMs = Long.MAX_VALUE
    )

    internal fun purchaseRefused() = PurchaseResult.Failed(NOTHING_TO_BUY)
}

/** The gateway for a closed-testing build: everything included, nothing to buy. */
object ClosedTestingBillingGateway : BillingGateway {
    override val configured = false
    override val entitlements: StateFlow<Entitlements> = MutableStateFlow(ClosedTesting.TESTER_ENTITLEMENTS).asStateFlow()
    override suspend fun offers(): List<PlanOffer> = emptyList()
    override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult = ClosedTesting.purchaseRefused()
    override suspend fun restore(): PurchaseResult = ClosedTesting.purchaseRefused()
    override suspend fun refresh() = Unit
    override fun appUserId(): String? = null
}
