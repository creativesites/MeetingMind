package com.craftflowtechnologies.meetingmind.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One plan the person can buy, as the store describes it. Nothing here is hardcoded. */
data class PlanOffer(
    val id: String,
    val plan: Plan,
    /** The store's formatted price, e.g. "$49.99". */
    val priceText: String,
    /** "month" or "year". */
    val period: String,
    val isYearly: Boolean
)

sealed interface PurchaseResult {
    data class Success(val entitlements: Entitlements) : PurchaseResult
    /** The person closed the store sheet. Not an error. */
    data object Cancelled : PurchaseResult
    /** The store is still processing (for example a slow payment method). Don't start another. */
    data object Pending : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/**
 * The only door to the store. The real implementation is [RevenueCatBillingGateway]; tests use
 * [FakeBillingGateway]; builds without a RevenueCat key use [DisabledBillingGateway], where the app
 * is simply free and no plan is offered.
 */
interface BillingGateway {
    /** False means there is nothing to buy, so no paywall is ever shown. */
    val configured: Boolean
    val entitlements: StateFlow<Entitlements>
    suspend fun offers(): List<PlanOffer>
    suspend fun purchase(activity: Activity, offerId: String): PurchaseResult
    suspend fun restore(): PurchaseResult
    /** Asks the store again; call on app resume. */
    suspend fun refresh()
    /** The id the store knows this person by; the managed-AI service uses it to check the plan. */
    fun appUserId(): String?
}

object DisabledBillingGateway : BillingGateway {
    override val configured = false
    override val entitlements: StateFlow<Entitlements> = MutableStateFlow(Entitlements()).asStateFlow()
    override suspend fun offers(): List<PlanOffer> = emptyList()
    override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult = PurchaseResult.Failed("Plans are not available in this build.")
    override suspend fun restore(): PurchaseResult = PurchaseResult.Failed("Plans are not available in this build.")
    override suspend fun refresh() = Unit
    override fun appUserId(): String? = null
}

/** The process-wide gateway. Set once in [com.craftflowtechnologies.meetingmind.MeetingMindApp]; tests swap it. */
object Billing {
    @Volatile var gateway: BillingGateway = DisabledBillingGateway
}
