package com.craftflowtechnologies.meetingmind.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.PaywallPolicy
import com.craftflowtechnologies.meetingmind.core.billing.PaywallState
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.ui.AppIconTile
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/**
 * The plans, as a short flow rather than one wall of text: what you get, what's included, then the
 * price and exactly what happens next. Closing it is always one tap, and nothing about the free app
 * depends on it. Prices and periods are the store's; nothing is hardcoded.
 */
@Composable
fun PaywallScreen(trigger: PaywallTrigger, onClose: () -> Unit, vm: PaywallViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val owned by vm.entitlements.collectAsState()
    val activity = LocalContext.current.findActivity()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var exitShown by rememberSaveable { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.shown(trigger) }
    // A plan that arrives (a purchase, or a restore) ends the flow.
    LaunchedEffect(state.purchased) { if (state.purchased) { delay(1200); onClose() } }

    fun close() {
        // Only on the last page, and only once: offer monthly to someone about to leave a yearly plan.
        val monthly = state.offers.firstOrNull { !it.isYearly && it.plan == state.selected?.plan }
        if (page == 2 && state.selected?.isYearly == true && monthly != null && !exitShown) {
            exitShown = true; showExit = true; vm.exitSheetShown()
        } else { vm.dismissed(); onClose() }
    }
    BackHandler { close() }

    if (showExit) {
        val monthly = state.offers.firstOrNull { !it.isYearly && it.plan == state.selected?.plan }
        AlertDialog(
            onDismissRequest = { showExit = false; vm.dismissed(); onClose() },
            title = { Text("Not ready for a year?") },
            text = { Text("You can pay monthly instead${monthly?.let { " (${it.priceText} a month)" } ?: ""}, and cancel any time.") },
            confirmButton = { TextButton(onClick = { showExit = false; monthly?.let { vm.select(it.id) } }) { Text("See monthly") } },
            dismissButton = { TextButton(onClick = { showExit = false; vm.dismissed(); onClose() }) { Text("No thanks") } }
        )
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.Navy, Brand.NavyLift, Brand.Navy))).testTag("paywall")) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i ->
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= page) Brush.horizontalGradient(Brand.sweep) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.15f)))))
                    }
                }
                Box(Modifier.padding(start = 12.dp).size(40.dp).clip(CircleShape).clickable { close() }.testTag("paywall_close"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f))
                }
            }
            AnimatedContent(
                targetState = page, modifier = Modifier.weight(1f), label = "paywall_page",
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    when (p) {
                        0 -> OutcomePage(state.outcome)
                        1 -> IncludedPage()
                        else -> PlansPage(state, showAll, { showAll = !showAll }, vm::select)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                state.message?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp).testTag("paywall_message")) }
                if (page < 2) {
                    PrimaryButton("Continue", "paywall_next", enabled = true) { page++ }
                } else {
                    val offer = state.selected
                    PrimaryButton(
                        if (state.busy) "One moment…" else if (offer != null) "Start ${offer.plan.label}" else "Plans unavailable",
                        "paywall_buy", enabled = offer != null && !state.busy && !state.pending && activity != null
                    ) { activity?.let(vm::buy) }
                    Text("No commitment — cancel any time in Google Play", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallLink("Continue free", "paywall_continue_free") { close() }
                    SmallLink("Restore purchases", "paywall_restore") { vm.restore() }
                    if (owned.isPaid) SmallLink("Manage", "paywall_manage") { manageSubscription(activity) }
                }
            }
        }
    }
}

private fun manageSubscription(activity: Activity?) {
    runCatching { activity?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))) }
}

@Composable
private fun OutcomePage(outcome: OutcomeCopy) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIconTile(size = 84.dp)
        Text(outcome.headline, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 28.dp).testTag("paywall_headline"))
        Text(outcome.line, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Text("You can keep using MeetingMind free. This just adds more.", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
    }
}

/** What stays free and what a plan adds. Allowance sizes are the plan's published ones, shown in the plan details, not guessed here. */
@Composable
private fun IncludedPage() {
    Text("What's included", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Spacer(Modifier.weight(1f)); HeaderCell("Free"); HeaderCell("Plan")
    }
    TableRow("Record, browse, search and export your notes", free = true, paid = true)
    TableRow("On-device transcription and notes", free = true, paid = true)
    TableRow("Cloud transcription and AI notes from MeetingMind, with a monthly allowance", free = false, paid = true)
    TableRow("Bigger, published monthly allowances for cloud summaries and questions", free = false, paid = true)
    Text("Your notes and recordings are yours on every plan, and stay yours if a plan ends.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun HeaderCell(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
}

@Composable
private fun TableRow(label: String, free: Boolean, paid: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceBase.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
        Mark(free); Mark(paid)
    }
}

@Composable
private fun Mark(on: Boolean) {
    Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Filled.Check, contentDescription = "Included", tint = Brand.Cyan, modifier = Modifier.size(20.dp))
        else Text("—", color = Color.White.copy(alpha = 0.35f), fontSize = 16.sp)
    }
}

@Composable
private fun PlansPage(state: PaywallUiState, showAll: Boolean, onToggleAll: () -> Unit, onSelect: (String) -> Unit) {
    Text("Choose your plan", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))
    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand.Cyan) }
        state.offers.isEmpty() -> Text("Plans aren't available right now. Check your connection and try again later. Your free app keeps working.", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, lineHeight = 21.sp)
        else -> {
            val shown = if (showAll) state.offers else state.headlineOffers
            shown.forEach { PlanCard(it, selected = it.id == state.selectedId) { onSelect(it.id) } }
            if (state.offers.size > shown.size || showAll) {
                Text(if (showAll) "Show fewer plans" else "View all plans", color = Brand.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggleAll).padding(vertical = 8.dp).testTag("paywall_view_all"))
            }
            state.selected?.let { offer ->
                Text("What happens next", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                PaywallCopy.timeline(offer).forEach { (whenText, what) ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text(whenText, color = Brand.Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(84.dp))
                        Text(what, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun PlanCard(offer: PlanOffer, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp))
            .background(SurfaceBase.copy(alpha = if (selected) 0.12f else 0.05f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Brand.Cyan else Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(16.dp).testTag("plan_${offer.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(offer.plan.label + if (offer.isYearly) " · yearly" else " · monthly", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Billed every ${offer.period}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp)
        }
        Text("${offer.priceText} / ${offer.period}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrimaryButton(label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50), color = Color.Transparent, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Box(
            Modifier.background(Brush.horizontalGradient(if (enabled) listOf(Brand.Cyan, Brand.Indigo, Brand.Violet) else listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.18f)))).padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) { Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun SmallLink(label: String, tag: String, onClick: () -> Unit) {
    Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp).testTag(tag))
}

/** A short, dismissible explanation in front of a paid feature or a limit. "Not now" just closes it. */
@Composable
fun UpgradeDialog(title: String, body: String, onSeePlans: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onSeePlans, modifier = Modifier.testTag("upgrade_see_plans")) { Text("See plans") } },
        dismissButton = { TextButton(onClick = onNotNow, modifier = Modifier.testTag("upgrade_not_now")) { Text("Not now") } }
    )
}

/**
 * The first-result offer: after a real recording has become notes (never after the sample), once,
 * and never more than weekly. It explains the plans and lets the person walk straight on.
 */
@Composable
fun PostResultOfferHost(onResultScreen: Boolean, onOpenPlans: (PaywallTrigger) -> Unit) {
    val context = LocalContext.current
    val prefsManager = remember { UserPreferencesManager(context) }
    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    val billing = Billing.gateway
    val owned by billing.entitlements.collectAsState()
    var show by remember { mutableStateOf(false) }
    val saved = prefs

    LaunchedEffect(onResultScreen, saved?.firstResultOfferShown) {
        if (!onResultScreen) { show = false; return@LaunchedEffect }
        if (saved == null || show) return@LaunchedEffect
        delay(2500) // let them read their notes first
        val now = System.currentTimeMillis()
        val state = PaywallState(saved.paywallLastShownAt, saved.paywallDismissals, saved.firstResultOfferShown)
        if (PaywallPolicy.shouldShow(PaywallTrigger.FIRST_RESULT, billing, owned.effective(now), state, now)) {
            prefsManager.markPaywallShown(now, firstResult = true)
            com.craftflowtechnologies.meetingmind.core.analytics.Analytics.track(com.craftflowtechnologies.meetingmind.core.analytics.Events.PAYWALL_VIEWED, "placement" to "first_result_sheet")
            show = true
        }
    }
    if (show) UpgradeDialog(
        title = "Get more from every recording",
        body = "MeetingMind is free to keep using. A plan adds cloud transcription and AI notes with a monthly allowance. Your notes stay yours either way.",
        onSeePlans = { show = false; onOpenPlans(PaywallTrigger.FIRST_RESULT) },
        onNotNow = { show = false; com.craftflowtechnologies.meetingmind.core.analytics.Analytics.track(com.craftflowtechnologies.meetingmind.core.analytics.Events.PAYWALL_DISMISSED, "placement" to "first_result_sheet") }
    )
}
