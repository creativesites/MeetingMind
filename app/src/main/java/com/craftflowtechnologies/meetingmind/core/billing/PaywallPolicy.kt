package com.craftflowtechnologies.meetingmind.core.billing

/** Why an offer might be shown. */
enum class PaywallTrigger {
    /** The first useful result from a real recording. */
    FIRST_RESULT,
    /** The person picked something that needs a plan. */
    PREMIUM_ACTION,
    USAGE_NEAR_LIMIT,
    USAGE_EXHAUSTED,
    /** Church or group use came up. */
    CHURCH,
    /** The person opened Plans themselves. */
    SETTINGS;

    companion object {
        fun parse(name: String?): PaywallTrigger = entries.firstOrNull { it.name == name } ?: SETTINGS
    }
}

data class PaywallState(
    val lastShownAtMs: Long = 0,
    val dismissals: Int = 0,
    val firstResultOfferShown: Boolean = false
)

/**
 * Decides whether an offer may appear without being asked for. Nothing here blocks the free path: a
 * "no" only means "don't interrupt now", and opening Plans from Settings always works.
 */
object PaywallPolicy {
    const val COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000

    fun shouldShow(trigger: PaywallTrigger, billing: BillingGateway, entitlements: Entitlements, state: PaywallState, nowMs: Long): Boolean {
        if (!billing.configured) return false
        if (trigger == PaywallTrigger.SETTINGS) return true
        if (entitlements.isPaid) return false
        // One uninvited full offer a week, however many triggers fire.
        if (state.lastShownAtMs != 0L && nowMs - state.lastShownAtMs < COOLDOWN_MS) return false
        return when (trigger) {
            PaywallTrigger.FIRST_RESULT -> !state.firstResultOfferShown
            // The person asked for, or ran into, something specific: explaining it is not an interruption.
            PaywallTrigger.PREMIUM_ACTION, PaywallTrigger.USAGE_EXHAUSTED, PaywallTrigger.CHURCH -> true
            // A gentle heads-up, and only once the person has seen the first-result offer.
            PaywallTrigger.USAGE_NEAR_LIMIT -> state.firstResultOfferShown
            PaywallTrigger.SETTINGS -> true
        }
    }
}
