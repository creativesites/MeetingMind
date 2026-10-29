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
import com.craftflowtechnologies.meetingmind.core.billing.SimulatedBillingGateway
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.core.billing.PurchaseResult
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import kotlinx.coroutines.delay
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

    /** True only in a debug build with no store key, where purchases are simulated. */
    val isTestStore: Boolean get() = gateway is SimulatedBillingGateway

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
        // Someone who already pays is shown Manage instead of a purchase button; this is the second guard.
        val owned = gateway.entitlements.value.effective(System.currentTimeMillis())
        if (owned.isPaid) {
            _state.update { it.copy(message = "You're already on ${owned.plan.label}. Manage your plan in Google Play.") }
            return
        }
        val offer = _state.value.selected ?: return
        if (_state.value.busy || _state.value.pending) return
        _state.update { it.copy(busy = true, message = null) }
        Analytics.track(Events.PURCHASE_STARTED, "plan" to offer.plan.name, "period" to offer.period)
        viewModelScope.launch { handle(gateway.purchase(activity, offer.id)) }
    }

    /** Debug builds without a store key only: returns the simulated account to Free so the paywall can be tried again. */
    fun resetTestStore() {
        val simulated = gateway as? SimulatedBillingGateway ?: return
        viewModelScope.launch { simulated.reset() }
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
                _state.update { it.copy(busy = false, pending = true, message = "Your purchase is still being processed. Your plan will start as soon as Google Play confirms it. Checking again in a moment…") }
                // A slow payment is not a dead end: ask the store again shortly, and leave a visible retry.
                viewModelScope.launch {
                    delay(PENDING_POLL_DELAY_MS)
                    if (_state.value.pending) pollPending()
                }
            }
            is PurchaseResult.Failed -> {
                Analytics.track(Events.PURCHASE_FAILED)
                _state.update { it.copy(busy = false, message = result.message) }
            }
        }
    }

    /** The visible retry for a pending purchase. */
    fun retryPending() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, pending = false, message = "Checking with Google Play…") }
        viewModelScope.launch { pollPending() }
    }

    private suspend fun pollPending() {
        runCatching { gateway.refresh() }
        val owned = gateway.entitlements.value.effective(System.currentTimeMillis())
        if (owned.isPaid) {
            Analytics.track(Events.PURCHASE_SUCCEEDED, "plan" to owned.plan.name)
            _state.update { it.copy(busy = false, pending = false, purchased = true, message = null) }
        } else {
            _state.update {
                it.copy(
                    busy = false,
                    pending = true,
                    message = "Google Play hasn't confirmed the purchase yet. You can check again, and your plan starts as soon as it does."
                )
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

    companion object {
        /** How long to wait before asking the store about a purchase it said was still pending. */
        const val PENDING_POLL_DELAY_MS = 4_000L
    }
}
