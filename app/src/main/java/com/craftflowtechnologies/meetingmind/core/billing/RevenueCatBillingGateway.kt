package com.craftflowtechnologies.meetingmind.core.billing

import android.app.Activity
import android.app.Application
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * RevenueCat over Google Play Billing. Every call into the SDK lives in this file, using its callback
 * API, so a change of SDK version touches one place. Entitlement identifiers and package-id prefixes
 * come from [Plan]; prices come from the store through the current offering.
 */
class RevenueCatBillingGateway(app: Application, apiKey: String) : BillingGateway {
    private val cache = EntitlementCache(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow(Entitlements())
    private var packages: Map<String, Package> = emptyMap()

    override val configured = true
    override val entitlements: StateFlow<Entitlements> = state.asStateFlow()

    init {
        Purchases.configure(PurchasesConfiguration.Builder(app, apiKey).build())
        scope.launch {
            // Start from what the store last confirmed, so the plan is known offline; it lapses on its own.
            state.value = cache.read().effective(System.currentTimeMillis())
            refresh()
        }
        Purchases.sharedInstance.updatedCustomerInfoListener = com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { info ->
            scope.launch { apply(info) }
        }
    }

    override suspend fun offers(): List<PlanOffer> {
        val offerings = suspendCancellableCoroutine<Offerings?> { c ->
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) { if (c.isActive) c.resume(offerings) }
                override fun onError(error: PurchasesError) { if (c.isActive) c.resume(null) }
            })
        }
        val available = offerings?.current?.availablePackages.orEmpty()
        packages = available.associateBy { it.identifier }
        return available.mapNotNull { p ->
            val plan = Plan.fromPackageId(p.identifier) ?: return@mapNotNull null
            val yearly = p.packageType == PackageType.ANNUAL
            if (!yearly && p.packageType != PackageType.MONTHLY) return@mapNotNull null
            PlanOffer(p.identifier, plan, p.product.price.formatted, if (yearly) "year" else "month", yearly)
        }
    }

    override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult {
        val pkg = packages[offerId] ?: return PurchaseResult.Failed("That plan is no longer available. Please try again.")
        return suspendCancellableCoroutine { c ->
            Purchases.sharedInstance.purchase(
                PurchaseParams.Builder(activity, pkg).build(),
                object : PurchaseCallback {
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                        // Access follows the store's customer info, not the fact that the sheet closed.
                        val e = map(customerInfo)
                        scope.launch { apply(customerInfo) }
                        if (c.isActive) c.resume(if (e.isPaid) PurchaseResult.Success(e) else PurchaseResult.Pending)
                    }
                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        if (c.isActive) c.resume(if (userCancelled) PurchaseResult.Cancelled else PurchaseResult.Failed(error.message))
                    }
                }
            )
        }
    }

    override suspend fun restore(): PurchaseResult = suspendCancellableCoroutine { c ->
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                val e = map(customerInfo)
                scope.launch { apply(customerInfo) }
                if (c.isActive) c.resume(if (e.isPaid) PurchaseResult.Success(e) else PurchaseResult.Failed("No active subscription was found for this Google account."))
            }
            override fun onError(error: PurchasesError) {
                if (c.isActive) c.resume(PurchaseResult.Failed("Couldn't check your purchases right now. Please try again."))
            }
        })
    }

    override suspend fun refresh() {
        suspendCancellableCoroutine<Unit> { c ->
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) { scope.launch { apply(customerInfo) }; if (c.isActive) c.resume(Unit) }
                // Offline or store trouble: keep what we had; it lapses by itself if it goes unconfirmed.
                override fun onError(error: PurchasesError) { if (c.isActive) c.resume(Unit) }
            })
        }
    }

    override fun appUserId(): String? = runCatching { Purchases.sharedInstance.appUserID }.getOrNull()

    private suspend fun apply(info: CustomerInfo) {
        val e = map(info)
        cache.write(e)
        state.value = e.effective(System.currentTimeMillis())
    }

    /** The best active entitlement wins; a billing problem keeps access during the store's grace period. */
    private fun map(info: CustomerInfo): Entitlements {
        val now = System.currentTimeMillis()
        val best = info.entitlements.active.values
            .mapNotNull { ent -> Plan.fromEntitlementId(ent.identifier)?.let { it to ent } }
            .maxByOrNull { it.first.rank } ?: return Entitlements(verifiedAtMs = now)
        val (plan, ent) = best
        val state = when {
            ent.billingIssueDetectedAt != null -> EntitlementState.GRACE
            ent.periodType == PeriodType.TRIAL -> EntitlementState.TRIAL
            else -> EntitlementState.ACTIVE
        }
        return Entitlements(plan, state, ent.expirationDate?.time, ent.willRenew, now)
    }
}
