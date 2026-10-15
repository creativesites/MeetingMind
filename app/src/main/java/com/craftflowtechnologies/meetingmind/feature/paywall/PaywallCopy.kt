package com.craftflowtechnologies.meetingmind.feature.paywall

import com.craftflowtechnologies.meetingmind.feature.onboarding.CaptureGoal
import com.craftflowtechnologies.meetingmind.feature.onboarding.DesiredOutcome

/** The outcome the first paywall page leads with, chosen from what the person said they want. */
data class OutcomeCopy(val headline: String, val line: String)

object PaywallCopy {
    fun outcome(goals: Set<String>, outcomes: Set<String>): OutcomeCopy = when {
        DesiredOutcome.ACTION_ITEMS.name in outcomes ->
            OutcomeCopy("Never lose an action item", "Every commitment from every conversation, found and listed for you.")
        CaptureGoal.SERMONS.name in goals ->
            OutcomeCopy("Your sermons, searchable", "Come back to a message, a verse or a challenge with a question instead of a rewind.")
        CaptureGoal.LEARNING.name in goals ->
            OutcomeCopy("Remember what you learn", "Lectures and research turned into notes you can ask questions of.")
        DesiredOutcome.SEARCHABLE.name in outcomes ->
            OutcomeCopy("Find anything you've ever said", "Search every conversation by what was said, not when it happened.")
        else ->
            OutcomeCopy("Get more from every recording", "More of MeetingMind's AI assistance for your meetings, learning and ideas.")
    }

    /** What the person can count on to happen with the money and the plan. No dates or prices are invented. */
    fun timeline(offer: com.craftflowtechnologies.meetingmind.core.billing.PlanOffer): List<Pair<String, String>> = if (offer.isLifetime) listOf(
        "Today" to "You're charged ${offer.priceText} once, and MeetingMind Pro is yours from today.",
        "Later" to "Nothing renews. There is no further payment.",
        "Refunds" to "Google Play handles refund requests for one-time purchases. Your notes always stay yours."
    ) else listOf(
        "Today" to "You're charged ${offer.priceText} and your plan starts right away.",
        "Every ${offer.period}" to "Google Play renews it at ${offer.priceText} until you cancel.",
        "Any time" to "Cancel in Google Play. You keep your plan until the period you paid for ends, and your notes always stay yours."
    )
}
