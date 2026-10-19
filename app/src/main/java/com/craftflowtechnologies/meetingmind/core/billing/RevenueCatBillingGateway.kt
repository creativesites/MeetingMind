package com.craftflowtechnologies.meetingmind.core.billing

import android.app.Activity
import android.app.Application
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RevenueCat over Google Play Billing, using the SDK's coroutine API. Every call into the SDK lives in
 * this file. The paid plan is the entitlement [Plan.PRO] ("meetingmind_pro"); the products are the
 * offering's "monthly", "yearly" and "lifetime" packages ([OfferKind]). Prices come from the store.
 */
class RevenueCatBillingGateway(app: Application, apiKey: String) : BillingGateway {
    private val cache = EntitlementCache(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow(Entitlements())
    private var packages: Map<String, com.revenuecat.purchases.Package> = emptyMap()

    override val configured = true
    override val entitlements: StateFlow<Entitlements> = state.asStateFlow()

    init {
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(PurchasesConfiguration.Builder(app, apiKey).build())
        Purchases.sharedInstance.updatedCustomerInfoListener = com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { info ->
            scope.launch { publish(info) }
        }
        scope.launch {
            // Start from what the store last confirmed, so the plan is known offline; it lapses on its own.
            state.value = cache.read().effective(System.currentTimeMillis())
            refresh()
        }
    }

    override suspend fun offers(): List<PlanOffer> {
        val available = runCatching { Purchases.sharedInstance.awaitOfferings().current?.availablePackages }
            .getOrNull().orEmpty()
        packages = available.associateBy { it.identifier }
        return available.mapNotNull { p ->
            OfferKind.fromPackageId(p.identifier)?.let { kind ->
                val trialDays = p.product.subscriptionOptions?.freeTrial?.pricingPhases?.firstOrNull()?.billingPeriod?.let { bp ->
                    when (bp.unit.name) {
                        "DAY" -> bp.value
                        "WEEK" -> bp.value * 7
                        "MONTH" -> bp.value * 30
                        else -> null
                    }
                }
                PlanOffer(p.identifier, kind.defaultPlan, p.product.price.formatted, kind, trialDays)
            }
        }
    }

    override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult {
        val pkg = packages[offerId] ?: return PurchaseResult.Failed("That plan is no longer available. Please try again.")
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            // Access follows the store's customer info, not the fact that the sheet closed.
            publish(result.customerInfo)
            val e = map(result.customerInfo)
            if (e.isPaid) PurchaseResult.Success(e) else PurchaseResult.Pending
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseResult.Cancelled else PurchaseResult.Failed(e.error.message)
        } catch (e: PurchasesException) {
            PurchaseResult.Failed(e.error.message)
        }
    }

    override suspend fun restore(): PurchaseResult = try {
        val info = Purchases.sharedInstance.awaitRestore()
        publish(info)
        val e = map(info)
        if (e.isPaid) PurchaseResult.Success(e) else PurchaseResult.Failed("No active subscription was found for this Google account.")
    } catch (e: PurchasesException) {
        PurchaseResult.Failed("Couldn't check your purchases right now. Please try again.")
    }

    override suspend fun refresh() {
        // Offline or store trouble: keep what we had; it lapses by itself if it goes unconfirmed.
        runCatching { Purchases.sharedInstance.awaitCustomerInfo() }.onSuccess { publish(it) }
    }

    override fun appUserId(): String? = runCatching { Purchases.sharedInstance.appUserID }.getOrNull()

    private suspend fun publish(info: CustomerInfo) {
        val e = map(info)
        cache.write(e)
        state.value = e.effective(System.currentTimeMillis())
    }

    /**
     * Reads the highest-ranked active paid entitlement across Church, Pro and BYOK. A billing problem
     * keeps access during the store's grace period; a trial counts as paid. Lifetime purchases have no expiry.
     */
    private fun map(info: CustomerInfo): Entitlements {
        val now = System.currentTimeMillis()
        val matchedPlan = Plan.entries
            .filter { it != Plan.FREE }
            .sortedByDescending { it.rank }
            .firstOrNull { info.entitlements.active[it.entitlementId] != null }
            ?: return Entitlements(verifiedAtMs = now)
        val ent = info.entitlements.active[matchedPlan.entitlementId] ?: return Entitlements(verifiedAtMs = now)
        val state = when {
            ent.billingIssueDetectedAt != null -> EntitlementState.GRACE
            ent.periodType == PeriodType.TRIAL -> EntitlementState.TRIAL
            else -> EntitlementState.ACTIVE
        }
        return Entitlements(matchedPlan, state, ent.expirationDate?.time, ent.willRenew, now)
    }
}
