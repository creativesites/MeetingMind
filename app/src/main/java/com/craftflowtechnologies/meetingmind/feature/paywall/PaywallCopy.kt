package com.craftflowtechnologies.meetingmind.feature.paywall

import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.Entitlements
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.feature.onboarding.CaptureGoal
import com.craftflowtechnologies.meetingmind.feature.onboarding.DesiredOutcome

/** The outcome the first paywall page leads with, chosen from what the person said they want. */
data class OutcomeCopy(val headline: String, val line: String)

/**
 * One line of the included table. [capability] is set on a paid-only row so a test can prove the
 * claim is something [com.craftflowtechnologies.meetingmind.core.billing.FeatureGate] enforces.
 */
data class IncludedRow(val label: String, val free: Boolean, val paid: Boolean, val capability: Capability? = null)

enum class PaywallCtaKind { BUY, MANAGE, RETRY, UNAVAILABLE }

data class PaywallCta(val label: String, val kind: PaywallCtaKind, val enabled: Boolean)

object PaywallCopy {
    /** The free features 1.0 gives everyone; repeated on the included page so no paid row implies otherwise. */
    const val ALWAYS_FREE_LINE =
        "Ask across meetings, Speaker ID, Read-to-Me and Circles are included on every plan in 1.0."

    /**
     * Only two things are sold in 1.0: a larger managed-cloud allowance and the right to use a
     * personal Gemini key. No number appears here: allowances come from the server, not the copy.
     */
    val includedRows: List<IncludedRow> = listOf(
        IncludedRow("Record, browse, search and export your notes", free = true, paid = true),
        IncludedRow("On-device transcription and notes", free = true, paid = true),
        IncludedRow("Managed cloud AI, with a monthly allowance on every plan", free = true, paid = true),
        IncludedRow("A larger managed cloud AI allowance", free = false, paid = true, capability = Capability.MANAGED_CLOUD_AI),
        IncludedRow("Use your own Gemini API key (BYOK)", free = false, paid = true, capability = Capability.BYOK_KEY)
    )

    /**
     * The outcome the first page leads with, chosen from what the person said they want in
     * onboarding. It never promises a gated feature: every line here is true on the free plan too,
     * and the page says so. Learning is not offered (out of MVP scope).
     */
    fun outcome(goals: Set<String>, outcomes: Set<String>): OutcomeCopy = when {
        DesiredOutcome.ACTION_ITEMS.name in outcomes ->
            OutcomeCopy("Never lose an action item", "Every commitment from every conversation, found and listed for you.")
        CaptureGoal.SERMONS.name in goals ->
            OutcomeCopy("Your sermons, searchable", "Come back to a message, a verse or a challenge with a question instead of a rewind.")
        DesiredOutcome.SEARCHABLE.name in outcomes ->
            OutcomeCopy("Find anything you've ever said", "Search every conversation by what was said, not when it happened.")
        else ->
            OutcomeCopy("Get more from every recording", "More of MeetingMind's AI assistance for your meetings, study and ideas.")
    }

    /** What the person can count on to happen with the money and the plan. No dates or prices are invented. */
    fun timeline(offer: PlanOffer): List<Pair<String, String>> = when {
        offer.isLifetime -> listOf(
            "Today" to "You're charged ${offer.priceText} once, and ${offer.plan.label} is yours from today.",
            "Later" to "Nothing renews. There is no further payment.",
            "Refunds" to "Google Play handles refund requests for one-time purchases. Your notes always stay yours."
        )
        offer.trialDays != null && offer.trialDays > 0 -> listOf(
            "Today" to "0 due today. Your ${offer.trialDays}-day free trial of ${offer.plan.label} starts right away.",
            "Before day ${offer.trialDays}" to "Cancel any time in Google Play and you won't be charged.",
            "After day ${offer.trialDays}" to "Google Play renews it at ${offer.priceText} every ${offer.period} until you cancel. Your notes always stay yours."
        )
        else -> listOf(
            "Today" to "You're charged ${offer.priceText} and your plan starts right away.",
            "Every ${offer.period}" to "Google Play renews it at ${offer.priceText} until you cancel.",
            "Any time" to "Cancel in Google Play. You keep your plan until the period you paid for ends, and your notes always stay yours."
        )
    }

    /** Why there is nothing to buy. A build with billing off must never blame the person's connection. */
    fun plansUnavailableLine(configured: Boolean): String =
        if (configured) "We couldn't load the plans from Google Play. Please try again later. Your free app keeps working."
        else "Plans aren't available in this build. Your free app keeps working."

    /**
     * The one button on the last page. Someone who already pays is never offered the same plan again;
     * a pending purchase becomes a retry, not a dead end.
     */
    fun cta(owned: Entitlements, offer: PlanOffer?, busy: Boolean, pending: Boolean): PaywallCta = when {
        owned.isPaid -> PaywallCta("Manage subscription", PaywallCtaKind.MANAGE, enabled = true)
        busy -> PaywallCta("One moment…", PaywallCtaKind.UNAVAILABLE, enabled = false)
        pending -> PaywallCta("Check purchase status", PaywallCtaKind.RETRY, enabled = true)
        offer == null -> PaywallCta("Plans unavailable", PaywallCtaKind.UNAVAILABLE, enabled = false)
        offer.trialDays != null && offer.trialDays > 0 ->
            PaywallCta("Start ${offer.trialDays}-day free trial", PaywallCtaKind.BUY, enabled = true)
        else -> PaywallCta("Start ${offer.plan.label}", PaywallCtaKind.BUY, enabled = true)
    }
}
