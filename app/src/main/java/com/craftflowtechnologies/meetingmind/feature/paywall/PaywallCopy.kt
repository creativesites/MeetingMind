package com.craftflowtechnologies.meetingmind.feature.paywall

import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.Entitlements
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import java.math.BigDecimal
import java.math.RoundingMode

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
        IncludedRow("Use your own Gemini API key (BYOK)", free = false, paid = true, capability = Capability.BYOK_KEY),
        IncludedRow("Connected cloud integrations (Tasks, Notion, Slack/Teams, Pinned Vault)", free = false, paid = true, capability = Capability.CONNECTED_INTEGRATIONS),
        IncludedRow("Automation recipes and trigger workflows", free = false, paid = true, capability = Capability.AUTOMATION_RECIPES),
        IncludedRow("Cross-session deep synthesis, executive briefs and Zuri's read across your reflections", free = false, paid = true, capability = Capability.DEEP_SYNTHESIS),
        IncludedRow("Church and organization ministry workflows", free = false, paid = true, capability = Capability.CHURCH_ORG_WORKFLOWS)
    )

    /**
     * The outcome the first page leads with, chosen from the spaces the person kept in onboarding (one space
     * says what they came for; several, or all four, keep the general line). It never promises a gated
     * feature: every line here is true on the free plan too, and the page says so.
     */
    fun outcome(spaces: Set<NotebookSpace>): OutcomeCopy = when (spaces.singleOrNull()) {
        NotebookSpace.WORK ->
            OutcomeCopy("Never lose an action item", "Every commitment from every conversation, found and listed for you.")
        NotebookSpace.FAITH ->
            OutcomeCopy("Your sermons, searchable", "Come back to a message, a verse or a challenge with a question instead of a rewind.")
        NotebookSpace.LEARNING ->
            OutcomeCopy("Every lecture, ready to revise", "Come back to a class with a question instead of a rewind.")
        NotebookSpace.PERSONAL ->
            OutcomeCopy("Grow what you're learning", "Answer a question each day, out loud and in private, and turn the talks you take notes on into small practices.")
        null ->
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

    /** The paid rows the paywall's hero lists as benefits: the first four things a plan adds. Each is a gated capability. */
    val benefits: List<IncludedRow> get() = includedRows.filter { !it.free }.take(4)

    /** The number in a store price ("\$79.99" is 79.99, "1.234,56 €" is 1234.56), or null when there is none. */
    fun priceValue(priceText: String): BigDecimal? = StorePrice.parse(priceText)?.value

    /**
     * A yearly price spread over twelve months, written the way the store wrote the price ("\$79.99" gives "\$6.67",
     * "79,99 €" gives "6,67 €"). Null when the price can't be read, so nothing is ever guessed.
     */
    fun perMonth(yearlyPriceText: String): String? {
        val price = StorePrice.parse(yearlyPriceText) ?: return null
        if (price.value.signum() <= 0) return null
        return price.write(price.value.divide(BigDecimal(12), 6, RoundingMode.HALF_UP), yearlyPriceText)
    }

    /** What a year saves against twelve monthly payments, in whole percent; null when it saves nothing or can't be read. */
    fun yearlySavingPercent(yearlyPriceText: String, monthlyPriceText: String): Int? {
        val yearly = priceValue(yearlyPriceText) ?: return null
        val monthly = priceValue(monthlyPriceText) ?: return null
        val twelve = monthly * BigDecimal(12)
        if (twelve.signum() <= 0 || yearly >= twelve) return null
        val percent = (twelve - yearly).multiply(BigDecimal(100)).divide(twelve, 0, RoundingMode.DOWN).toInt()
        return percent.takeIf { it > 0 }
    }

    /**
     * "Best value" goes on a yearly plan only when it is: cheaper than twelve months of the same plan, or when there
     * is no monthly price to compare with (launch audit §8).
     */
    fun showBestValue(offer: PlanOffer, monthly: PlanOffer?): Boolean {
        if (!offer.isYearly) return false
        val yearly = priceValue(offer.priceText) ?: return true
        val month = monthly?.let { priceValue(it.priceText) } ?: return true
        return yearly < month * BigDecimal(12)
    }
}

/** The number inside a store's formatted price, and how the store wrote it, so a new amount can be written the same way. */
private class StorePrice(
    val range: IntRange,
    val value: BigDecimal,
    val decimals: Int,
    val decimalMark: Char?,
    val groupMark: Char?
) {
    fun write(amount: BigDecimal, original: String): String {
        val plain = amount.setScale(decimals, RoundingMode.HALF_UP).toPlainString()
        val whole = plain.substringBefore('.')
        val grouped = if (groupMark != null) whole.reversed().chunked(3).joinToString(groupMark.toString()).reversed() else whole
        val number = if (decimals > 0) grouped + (decimalMark ?: '.') + plain.substringAfter('.', "") else grouped
        return original.replaceRange(range, number)
    }

    companion object {
        private val number = Regex("""\d(?:[\d.,'\u00A0\u202F ]*\d)?""")

        fun parse(text: String): StorePrice? {
            val match = number.find(text) ?: return null
            val raw = match.value
            val lastSeparator = raw.indexOfLast { !it.isDigit() }
            val tail = if (lastSeparator >= 0) raw.length - lastSeparator - 1 else 0
            // A final "." or "," followed by one or two digits is the decimal mark; anything else groups thousands.
            val decimalMark = if (lastSeparator >= 0 && (raw[lastSeparator] == '.' || raw[lastSeparator] == ',') && tail in 1..2) raw[lastSeparator] else null
            val decimals = if (decimalMark != null) tail else 0
            val groupMark = raw.withIndex().firstOrNull { (i, ch) -> !ch.isDigit() && !(decimalMark != null && i == lastSeparator) }?.value
            val value = raw.filter { it.isDigit() }.toBigDecimalOrNull()?.movePointLeft(decimals) ?: return null
            return StorePrice(match.range, value, decimals, decimalMark, groupMark)
        }
    }
}
