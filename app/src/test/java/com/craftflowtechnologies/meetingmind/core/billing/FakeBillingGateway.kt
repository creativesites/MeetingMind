package com.craftflowtechnologies.meetingmind.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** For tests and previews: the offers and the next purchase result are whatever the test sets. */
class FakeBillingGateway(
    private val offered: List<PlanOffer> = listOf(
        PlanOffer("monthly", Plan.PRO, "$9.99", OfferKind.MONTHLY),
        PlanOffer("yearly", Plan.PRO, "$79.99", OfferKind.YEARLY),
        PlanOffer("lifetime", Plan.PRO, "$149.99", OfferKind.LIFETIME)
    ),
    override val configured: Boolean = true
) : BillingGateway {
    private val state = MutableStateFlow(Entitlements())
    override val entitlements: StateFlow<Entitlements> = state.asStateFlow()
    var nextResult: PurchaseResult = PurchaseResult.Cancelled
    val purchased = mutableListOf<String>()
    var restores = 0

    fun set(value: Entitlements) { state.value = value }

    override suspend fun offers(): List<PlanOffer> = offered
    override suspend fun purchase(activity: Activity, offerId: String): PurchaseResult {
        purchased += offerId
        (nextResult as? PurchaseResult.Success)?.let { state.value = it.entitlements }
        return nextResult
    }
    override suspend fun restore(): PurchaseResult { restores++; return nextResult }
    override suspend fun refresh() = Unit
    override fun appUserId(): String? = "fake-user"
}
