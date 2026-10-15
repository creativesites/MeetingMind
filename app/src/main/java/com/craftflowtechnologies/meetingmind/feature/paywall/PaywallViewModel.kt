package com.craftflowtechnologies.meetingmind.feature.paywall

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.analytics.Analytics
import com.craftflowtechnologies.meetingmind.core.analytics.Events
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.BillingGateway
import com.craftflowtechnologies.meetingmind.core.billing.Entitlements
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.core.billing.PurchaseResult
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallUiState(
    val loading: Boolean = true,
    val offers: List<PlanOffer> = emptyList(),
    val selectedId: String? = null,
    val busy: Boolean = false,
    /** The store is still processing a purchase: don't offer to start another. */
    val pending: Boolean = false,
    val message: String? = null,
    val outcome: OutcomeCopy = PaywallCopy.outcome(emptySet(), emptySet()),
    /** Set once the person owns a plan; the screen then thanks them and closes. */
    val purchased: Boolean = false
) {
    val selected: PlanOffer? get() = offers.firstOrNull { it.id == selectedId }
    /** Two plans only on the main screen: the individual plan, yearly first. The rest sit behind "View all plans". */
    val headlineOffers: List<PlanOffer> get() = offers.filter { it.plan == com.craftflowtechnologies.meetingmind.core.billing.Plan.PRO }.sortedByDescending { it.isYearly }
        .ifEmpty { offers.take(2) }
}

/** Picks what to preselect: the yearly individual plan if there is one, otherwise the first plan. */
fun defaultOffer(offers: List<PlanOffer>): PlanOffer? =
    offers.firstOrNull { it.plan == com.craftflowtechnologies.meetingmind.core.billing.Plan.PRO && it.isYearly }
        ?: offers.firstOrNull { it.isYearly } ?: offers.firstOrNull()

class PaywallViewModel @JvmOverloads constructor(application: Application, private val gateway: BillingGateway = Billing.gateway) : AndroidViewModel(application) {
    private val prefs = UserPreferencesManager(application)
    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()
    val entitlements: StateFlow<Entitlements> = gateway.entitlements
    val configured: Boolean get() = gateway.configured

    init {
        viewModelScope.launch {
            val saved = prefs.preferencesFlow.first()
            val outcome = PaywallCopy.outcome(saved.onboardingGoals, saved.onboardingOutcomes)
            val offers = gateway.offers()
            _state.update { it.copy(loading = false, offers = offers, selectedId = defaultOffer(offers)?.id, outcome = outcome) }
        }
    }

    fun select(id: String) {
        _state.update { it.copy(selectedId = id, message = null) }
        Analytics.track(Events.PLAN_SELECTED, "plan" to (_state.value.selected?.plan?.name ?: ""))
    }

    fun buy(activity: Activity) {
        val offer = _state.value.selected ?: return
        if (_state.value.busy || _state.value.pending) return
        _state.update { it.copy(busy = true, message = null) }
        Analytics.track(Events.PURCHASE_STARTED, "plan" to offer.plan.name, "period" to offer.period)
        viewModelScope.launch { handle(gateway.purchase(activity, offer.id)) }
    }

    fun restore() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        Analytics.track(Events.RESTORE_TAPPED)
        viewModelScope.launch { handle(gateway.restore()) }
    }

    private fun handle(result: PurchaseResult) {
        when (result) {
            is PurchaseResult.Success -> {
                Analytics.track(Events.PURCHASE_SUCCEEDED, "plan" to result.entitlements.plan.name)
                _state.update { it.copy(busy = false, pending = false, purchased = true) }
            }
            // Closing the store sheet is a choice, not an error.
            PurchaseResult.Cancelled -> _state.update { it.copy(busy = false) }
            PurchaseResult.Pending -> {
                Analytics.track(Events.PURCHASE_PENDING)
                _state.update { it.copy(busy = false, pending = true, message = "Your purchase is still being processed. Your plan will start as soon as Google Play confirms it.") }
            }
            is PurchaseResult.Failed -> {
                Analytics.track(Events.PURCHASE_FAILED)
                _state.update { it.copy(busy = false, message = result.message) }
            }
        }
    }

    /** Records that the paywall was put in front of the person unasked, for the weekly limit. */
    fun shown(trigger: PaywallTrigger) {
        Analytics.track(Events.PAYWALL_VIEWED, "placement" to trigger.name.lowercase())
        if (trigger != PaywallTrigger.SETTINGS) viewModelScope.launch { prefs.markPaywallShown(System.currentTimeMillis(), trigger == PaywallTrigger.FIRST_RESULT) }
    }

    fun dismissed() {
        Analytics.track(Events.PAYWALL_DISMISSED)
        viewModelScope.launch { prefs.recordPaywallDismissed() }
    }

    fun exitSheetShown() = Analytics.track(Events.EXIT_SHEET_SHOWN)
}
