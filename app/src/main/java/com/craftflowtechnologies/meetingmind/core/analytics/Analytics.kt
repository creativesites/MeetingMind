package com.craftflowtechnologies.meetingmind.core.analytics

import android.util.Log

/**
 * Funnel events with no content in them: names and short, fixed-vocabulary properties only. Never a
 * title, transcript, prompt, note text, passage, key or any free text. Nothing here touches the
 * network; a vendor sink is added only after the privacy copy and the opt-out are in place.
 */
interface AnalyticsSink {
    fun track(event: String, properties: Map<String, String>)
}

object Analytics {
    @Volatile var sink: AnalyticsSink = LogSink

    fun track(event: String, vararg properties: Pair<String, String>) {
        // A sink must never break the app, and a property must never carry free text.
        runCatching { sink.track(event, properties.associate { (k, v) -> k to v.take(MAX_VALUE) }) }
    }

    private const val MAX_VALUE = 40
}

/** Local debug log only. */
object LogSink : AnalyticsSink {
    override fun track(event: String, properties: Map<String, String>) {
        Log.d("MM-Analytics", "$event $properties")
    }
}

/** The event names the paywall flow emits. */
object Events {
    const val PAYWALL_VIEWED = "paywall_viewed"
    const val PAYWALL_DISMISSED = "paywall_dismissed"
    const val EXIT_SHEET_SHOWN = "exit_sheet_shown"
    const val PLAN_SELECTED = "plan_selected"
    const val PURCHASE_STARTED = "trial_checkout_started"
    const val PURCHASE_PENDING = "purchase_pending"
    const val PURCHASE_SUCCEEDED = "purchase_succeeded"
    const val PURCHASE_FAILED = "purchase_failed"
    const val RESTORE_TAPPED = "restore_tapped"
    const val USAGE_WARNING = "usage_limit_warning"
    const val USAGE_LIMIT_REACHED = "usage_limit_reached"
}
