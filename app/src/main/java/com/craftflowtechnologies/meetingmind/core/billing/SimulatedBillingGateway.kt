package com.craftflowtechnologies.meetingmind.core.billing

import android.app.Activity
import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A stand-in store for debug builds that have no RevenueCat key yet. It lets the paywall, its copy,
 * purchase, restore and every gate be tried on a device without Google Play.
 *
 * [com.craftflowtechnologies.meetingmind.MeetingMindApp] creates it only when BuildConfig.DEBUG is true
 * and no key is configured. A release build never creates it, so no simulated purchase can happen there.
 * The prices are placeholders marked as test prices; the store's own prices replace them once the
 * RevenueCat key and the Play products exist.
 */
class SimulatedBillingGateway(app: Application) : BillingGateway {
    private val cache = EntitlementCache(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow(Entitlements())

    override val configured = true
    override val entitlements: StateFlow<Entitlements> = state.asStateFlow()

    init {
        scope.launch { state.value = cache.read().effective(System.currentTimeMillis()) }
    }

    override suspend fun offers(): List<PlanOffer> = TEST_PRICES.map { (kind, price) ->
        PlanOffer(kind.packageId, kind.defaultPlan, price, kind, trialDays = null)
    }

    override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult {
        val kind = OfferKind.fromPackageId(offerId) ?: return PurchaseResult.Failed("That plan is not available in this test build.")
        val now = System.currentTimeMillis()
        val expires = when (kind) {
            OfferKind.LIFETIME -> null
            OfferKind.YEARLY, OfferKind.BYOK_YEARLY, OfferKind.CHURCH_YEARLY -> now + YEAR_MS
            else -> now + MONTH_MS
        }
        val granted = Entitlements(
            plan = kind.defaultPlan,
            state = EntitlementState.ACTIVE,
            expiresAtMs = expires,
            willRenew = kind != OfferKind.LIFETIME,
            verifiedAtMs = now
        )
        publish(granted)
        return PurchaseResult.Success(granted)
    }

    override suspend fun restore(): PurchaseResult {
        val owned = state.value.effective(System.currentTimeMillis())
        return if (owned.isPaid) PurchaseResult.Success(owned)
        else PurchaseResult.Failed("No test purchase was found on this device.")
    }

    override suspend fun refresh() {
        state.value = cache.read().effective(System.currentTimeMillis())
    }

    override fun appUserId(): String = "test-store-user"

    /** Returns the simulated account to the free plan. Used to test the paywall again. */
    suspend fun reset() = publish(Entitlements(verifiedAtMs = System.currentTimeMillis()))

    private suspend fun publish(value: Entitlements) {
        cache.write(value)
        state.value = value.effective(System.currentTimeMillis())
    }

    companion object {
        private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
        private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000

        /** Placeholder test prices, one per offer. Not the launch prices. */
        val TEST_PRICES: List<Pair<OfferKind, String>> = listOf(
            OfferKind.BYOK_MONTHLY to "\$4.99 (test)",
            OfferKind.BYOK_YEARLY to "\$49.99 (test)",
            OfferKind.MONTHLY to "\$12.99 (test)",
            OfferKind.YEARLY to "\$119.99 (test)",
            OfferKind.LIFETIME to "\$249.99 (test)",
            OfferKind.CHURCH_MONTHLY to "\$49.99 (test)",
            OfferKind.CHURCH_YEARLY to "\$499.99 (test)"
        )
    }
}
