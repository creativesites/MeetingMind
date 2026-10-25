package com.craftflowtechnologies.meetingmind.feature.paywall

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.ai.cloud.UsageMeter
import com.craftflowtechnologies.meetingmind.ai.cloud.UsageState
import com.craftflowtechnologies.meetingmind.core.analytics.Analytics
import com.craftflowtechnologies.meetingmind.core.analytics.Events
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager

/**
 * A usage heads-up is information, not an upsell: it is shown to everyone, once for each level
 * (nearly used, used up) in each period, and never stops anything. It is separate from the weekly
 * paywall limit so someone is never left guessing why cloud processing has paused.
 */
object UsageWarning {
    /** The warning to show now, or null if there is none or this level was already shown this period. */
    fun next(usage: UsageState, warnedTag: String?): PaywallTrigger? {
        if (!usage.known) return null
        val level = when {
            usage.atLimit -> PaywallTrigger.USAGE_EXHAUSTED
            usage.nearLimit -> PaywallTrigger.USAGE_NEAR_LIMIT
            else -> return null
        }
        return level.takeIf { tag(usage, it) != warnedTag }
    }

    fun tag(usage: UsageState, level: PaywallTrigger): String = "${usage.periodEndMs}:${level.name}"

    fun title(level: PaywallTrigger) =
        if (level == PaywallTrigger.USAGE_EXHAUSTED) "This month's cloud allowance is used up" else "Most of this month's cloud allowance is used"

    /** Never promises a plan this build cannot sell. */
    fun body(level: PaywallTrigger, usage: UsageState, paid: Boolean, plansAvailable: Boolean = true): String {
        val change = when {
            !plansAvailable -> ""
            paid -> " You can switch to a plan with a larger allowance."
            else -> " A plan with a larger allowance is available."
        }
        return if (level == PaywallTrigger.USAGE_EXHAUSTED)
            "Cloud processing resumes when it resets.$change Your notes, and processing on this phone, aren't affected."
        else
            "You've used ${(usage.fraction * 100).toInt().coerceAtMost(99)}%. You can keep going until it runs out.$change Your notes, and processing on this phone, aren't affected."
    }
}

@Composable
fun UsageAlertHost(onOpenPlans: (PaywallTrigger) -> Unit) {
    val context: Context = LocalContext.current
    val prefsManager = remember { UserPreferencesManager(context) }
    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    val usage by UsageMeter.state.collectAsState()
    val billing = Billing.gateway
    val owned by billing.entitlements.collectAsState()
    var showing by remember { mutableStateOf<PaywallTrigger?>(null) }
    val saved = prefs

    LaunchedEffect(usage, saved?.usageWarned, saved != null) {
        if (saved == null || showing != null) return@LaunchedEffect
        // The heads-up is information for everyone, including a build with no plans to sell.
        val level = UsageWarning.next(usage, saved.usageWarned) ?: return@LaunchedEffect
        prefsManager.markUsageWarned(UsageWarning.tag(usage, level))
        Analytics.track(if (level == PaywallTrigger.USAGE_EXHAUSTED) Events.USAGE_LIMIT_REACHED else Events.USAGE_WARNING, "plan" to usage.plan)
        showing = level
    }
    showing?.let { level ->
        if (billing.configured) {
            UpgradeDialog(
                title = UsageWarning.title(level),
                body = UsageWarning.body(level, usage, owned.isPaid, plansAvailable = true),
                onSeePlans = { showing = null; onOpenPlans(level) },
                onNotNow = { showing = null }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showing = null },
                title = { Text(UsageWarning.title(level)) },
                text = { Text(UsageWarning.body(level, usage, owned.isPaid, plansAvailable = false)) },
                confirmButton = {
                    TextButton(onClick = { showing = null }, modifier = Modifier.testTag("usage_ok")) { Text("OK") }
                }
            )
        }
    }
}
