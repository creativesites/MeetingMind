package com.craftflowtechnologies.meetingmind.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.OfferKind
import com.craftflowtechnologies.meetingmind.core.billing.PaywallPolicy
import com.craftflowtechnologies.meetingmind.core.billing.PaywallState
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.ui.AppIconTile
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.onboarding.AlwaysLight
import com.craftflowtechnologies.meetingmind.feature.onboarding.ChoiceMark
import com.craftflowtechnologies.meetingmind.feature.onboarding.CompanionHalo
import com.craftflowtechnologies.meetingmind.feature.onboarding.GlowButton
import com.craftflowtechnologies.meetingmind.feature.onboarding.IconTile
import com.craftflowtechnologies.meetingmind.feature.onboarding.LightBackdrop
import com.craftflowtechnologies.meetingmind.feature.onboarding.choiceCard
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/**
 * The plans, on one light page (founder request, 2026-11-19; launch audit §8): the app tile and what a plan
 * is for, the four things it adds, the plans with the yearly one lifted, exactly what happens next, then the
 * full Free and Plan comparison on request. The one button, the cancel-any-time line, Restore and the legal
 * links stay pinned at the bottom. Closing it is always one tap, and nothing about the free app depends on
 * it. Prices and periods are the store's; nothing is hardcoded.
 */
@Composable
fun PaywallScreen(trigger: PaywallTrigger, onClose: () -> Unit, vm: PaywallViewModel = viewModel()) {
    AlwaysLight { PaywallContent(trigger, onClose, vm) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaywallContent(trigger: PaywallTrigger, onClose: () -> Unit, vm: PaywallViewModel) {
    val state by vm.state.collectAsState()
    val owned by vm.entitlements.collectAsState()
    val activity = LocalContext.current.findActivity()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var compare by rememberSaveable { mutableStateOf(false) }
    /** Set once the person has looked at or touched the plans: only then is the monthly offer shown on leaving. */
    var engaged by rememberSaveable { mutableStateOf(false) }
    var exitShown by rememberSaveable { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val c = MM.colors

    // Nothing to sell in this build: say so plainly instead of a page that cannot complete.
    if (!vm.configured) {
        PaywallUnavailable(onClose = { vm.dismissed(); onClose() })
        return
    }

    LaunchedEffect(Unit) { vm.shown(trigger) }
    // A plan that arrives (a purchase, or a restore) ends the flow.
    LaunchedEffect(state.purchased) { if (state.purchased) { delay(1200); onClose() } }

    fun close() {
        // Only once, and only after the plans were looked at: offer monthly to someone about to leave a yearly plan.
        val monthly = state.offers.firstOrNull { it.kind == OfferKind.MONTHLY && it.plan == state.selected?.plan }
        if ((engaged || scroll.value > 0) && state.selected?.isYearly == true && monthly != null && !exitShown) {
            exitShown = true; showExit = true; vm.exitSheetShown()
        } else { vm.dismissed(); onClose() }
    }
    BackHandler { close() }

    if (showExit) {
        val monthly = state.offers.firstOrNull { it.kind == OfferKind.MONTHLY && it.plan == state.selected?.plan }
        AlertDialog(
            onDismissRequest = { showExit = false; vm.dismissed(); onClose() },
            title = { Text(stringResource(R.string.paywall_exit_title)) },
            text = { Text(monthly?.let { stringResource(R.string.paywall_exit_body_price, it.priceText) } ?: stringResource(R.string.paywall_exit_body)) },
            confirmButton = { TextButton(onClick = { showExit = false; monthly?.let { vm.select(it.id) } }) { Text(stringResource(R.string.paywall_exit_see_monthly)) } },
            dismissButton = { TextButton(onClick = { showExit = false; vm.dismissed(); onClose() }) { Text(stringResource(R.string.paywall_exit_no_thanks)) } }
        )
    }

    Box(Modifier.fillMaxSize().testTag("paywall")) {
        LightBackdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.xs, vertical = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { close() }, modifier = Modifier.testTag("paywall_close")) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = c.inkSecondary)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = MM.space.xl)) {
                PaywallHero(state.outcome)
                Benefits()
                PlansSection(state, showAll, vm.configured, onToggleAll = { showAll = !showAll }) { id -> engaged = true; vm.select(id) }
                CompareSection(compare) { compare = !compare }
                Spacer(Modifier.height(MM.space.xl))
            }
            // Pinned: the one button, what it commits to, and the ways out. The surface runs under the navigation bar.
            Box(Modifier.fillMaxWidth().height(MMSize.hairline).background(c.lineSoft))
            Column(
                Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().imePadding()
                    .padding(horizontal = MM.space.xl).padding(top = MM.space.l, bottom = MM.space.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.message?.let {
                    Text(it, style = MM.type.secondary, color = c.inkSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = MM.space.s).testTag("paywall_message"))
                }
                if (owned.isPaid) {
                    Text(stringResource(R.string.paywall_current_plan, owned.plan.label), style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.padding(bottom = MM.space.s).testTag("paywall_current_plan"))
                }
                val cta = PaywallCopy.cta(owned, state.selected, state.busy, state.pending)
                GlowButton(
                    cta.label,
                    "paywall_buy",
                    Modifier.fillMaxWidth(),
                    enabled = cta.enabled && (cta.kind != PaywallCtaKind.BUY || activity != null),
                    showArrow = false
                ) {
                    when (cta.kind) {
                        PaywallCtaKind.MANAGE -> manageSubscription(activity)
                        PaywallCtaKind.RETRY -> vm.retryPending()
                        PaywallCtaKind.BUY -> activity?.let(vm::buy)
                        PaywallCtaKind.UNAVAILABLE -> Unit
                    }
                }
                Row(Modifier.padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(MM.space.xs))
                    Text(
                        stringResource(if (cta.kind == PaywallCtaKind.BUY) R.string.paywall_no_commitment else R.string.paywall_change_any_time),
                        style = MM.type.caption, color = c.inkMuted
                    )
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SmallLink(stringResource(R.string.paywall_continue_free), "paywall_continue_free") { close() }
                    SmallLink(stringResource(R.string.paywall_restore), "paywall_restore") { vm.restore() }
                    if (owned.isPaid) SmallLink(stringResource(R.string.paywall_manage), "paywall_manage") { manageSubscription(activity) }
                    if (vm.isTestStore) SmallLink(stringResource(R.string.paywall_reset_test_store), "paywall_reset_test") { vm.resetTestStore() }
                    // Google Play requires these for subscriptions; they appear once the URLs are configured.
                    if (BuildConfig.TERMS_OF_USE_URL.isNotBlank()) SmallLink(stringResource(R.string.paywall_terms), "paywall_terms") { openLink(activity, BuildConfig.TERMS_OF_USE_URL) }
                    if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) SmallLink(stringResource(R.string.paywall_privacy), "paywall_privacy") { openLink(activity, BuildConfig.PRIVACY_POLICY_URL) }
                }
            }
        }
    }
}

private fun openLink(activity: Activity?, url: String) {
    runCatching { activity?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun manageSubscription(activity: Activity?) {
    runCatching { activity?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))) }
}

/** The honest end of the road when billing is off in this build: no prices, no false network blame. */
@Composable
private fun PaywallUnavailable(onClose: () -> Unit) {
    val c = MM.colors
    Box(Modifier.fillMaxSize().testTag("paywall"), contentAlignment = Alignment.Center) {
        LightBackdrop()
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().navigationBarsPadding().padding(horizontal = MM.space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIconTile(size = 84.dp)
            Text(
                stringResource(R.string.paywall_unavailable_title),
                style = MM.type.display, color = c.ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MM.space.xl).testTag("paywall_unavailable")
            )
            Text(
                stringResource(R.string.paywall_unavailable_body),
                style = MM.type.body, color = c.inkSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MM.space.m)
            )
            TextAction(stringResource(R.string.paywall_unavailable_continue), onClick = onClose, modifier = Modifier.padding(top = MM.space.xl).testTag("paywall_close"))
        }
    }
}

/** The app's tile in its halo, what the plan is for (from the person's spaces), and that the free app stays free. */
@Composable
private fun PaywallHero(outcome: OutcomeCopy) {
    val c = MM.colors
    val motion = MM.motion
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val rise by animateFloatAsState(if (appeared) 1f else 0f, motion.enter(), label = "hero")
    Column(
        Modifier.fillMaxWidth().graphicsLayer { alpha = rise; translationY = (1f - rise) * 24.dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Never the companion on a paywall (ZURI_EXPERIENCE §9): the halo holds the app's own tile. On shorter
        // phones (a 360 x 780 dp S20-class screen) it shrinks, so the plans start closer to the first screen.
        val compact = LocalConfiguration.current.screenHeightDp < 800
        CompanionHalo(form = null, size = if (compact) 96.dp else 152.dp)
        Text(stringResource(R.string.paywall_screen_title), style = MM.type.overline, color = c.accent, modifier = Modifier.padding(top = MM.space.m))
        Text(
            outcome.headline, style = MM.type.display, color = c.ink, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MM.space.s).testTag("paywall_headline")
        )
        Text(outcome.line, style = MM.type.body, color = c.inkSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.m))
        Text(
            stringResource(R.string.paywall_free_stays_free),
            style = MM.type.caption, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.s)
        )
    }
}

/** What a plan adds, as four rows with icons. Every row is a capability the code enforces (PaywallCopyTest). */
@Composable
private fun Benefits() {
    val c = MM.colors
    Column(
        Modifier.fillMaxWidth().padding(top = MM.space.xl).clip(MM.radius.card).background(c.surface)
            .border(MMSize.hairline, c.line, MM.radius.card).padding(MM.space.l),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Text(stringResource(R.string.paywall_benefits_title), style = MM.type.overline, color = c.inkMuted)
        PaywallCopy.benefits.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("paywall_benefit_${row.capability?.name?.lowercase()}")) {
                IconTile(benefitIcon(row.capability), on = true)
                Spacer(Modifier.width(MM.space.m))
                Text(row.label, style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun benefitIcon(capability: Capability?): ImageVector = when (capability) {
    Capability.MANAGED_CLOUD_AI -> Icons.Filled.AutoAwesome
    Capability.BYOK_KEY -> Icons.Filled.Key
    Capability.CONNECTED_INTEGRATIONS -> Icons.Filled.Hub
    Capability.AUTOMATION_RECIPES -> Icons.Filled.Bolt
    Capability.DEEP_SYNTHESIS -> Icons.Filled.Insights
    Capability.CHURCH_ORG_WORKFLOWS -> Icons.Filled.Church
    null -> Icons.Filled.Check
}

@Composable
private fun PlansSection(state: PaywallUiState, showAll: Boolean, configured: Boolean, onToggleAll: () -> Unit, onSelect: (String) -> Unit) {
    val c = MM.colors
    Text(stringResource(R.string.paywall_choose_plan), style = MM.type.title, color = c.ink, modifier = Modifier.padding(top = MM.space.xxl, bottom = MM.space.s))
    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(MM.space.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
        state.offers.isEmpty() -> Text(PaywallCopy.plansUnavailableLine(configured), style = MM.type.body, color = c.inkSecondary)
        else -> {
            val shown = if (showAll) state.offers else state.headlineOffers
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                shown.forEach { offer ->
                    // The same plan's monthly price, to show what a year saves.
                    val monthly = state.offers.firstOrNull { it.kind.period == OfferKind.MONTHLY.period && it.plan == offer.plan }
                    PlanCard(offer, selected = offer.id == state.selectedId, monthly = monthly) { onSelect(offer.id) }
                }
            }
            if (state.offers.size > shown.size || showAll) {
                TextAction(
                    stringResource(if (showAll) R.string.paywall_show_fewer else R.string.paywall_view_all),
                    onClick = onToggleAll, modifier = Modifier.padding(top = MM.space.xs).testTag("paywall_view_all")
                )
            }
            state.selected?.let { offer ->
                Column(Modifier.fillMaxWidth().padding(top = MM.space.l).clip(MM.radius.card).background(c.surfaceSunk).padding(MM.space.l)) {
                    Text(stringResource(R.string.paywall_what_next), style = MM.type.heading, color = c.ink, modifier = Modifier.padding(bottom = MM.space.s))
                    PaywallCopy.timeline(offer).forEach { (whenText, what) ->
                        Row(Modifier.padding(vertical = MM.space.xs)) {
                            // A minimum, not a fixed width: at large text sizes a longer "when" grows instead of clipping.
                            Text(whenText, style = MM.type.caption, color = c.accent, modifier = Modifier.widthIn(min = 88.dp, max = 140.dp).padding(end = MM.space.s))
                            Text(what, style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * One plan. The yearly plan is the recommended one: lifted, with a "Best value" pill (only when it really is
 * cheaper than twelve months) and its price spread over a month. Prices are the store's own text.
 */
@Composable
private fun PlanCard(offer: PlanOffer, selected: Boolean, monthly: PlanOffer?, onClick: () -> Unit) {
    val c = MM.colors
    val shape = MM.radius.card
    val recommended = PaywallCopy.showBestValue(offer, monthly)
    val saving = if (offer.isYearly && monthly != null) PaywallCopy.yearlySavingPercent(offer.priceText, monthly.priceText) else null
    val perMonth = if (offer.isYearly) PaywallCopy.perMonth(offer.priceText) else null
    val kindLabel = when {
        offer.isLifetime -> stringResource(R.string.paywall_tier_lifetime)
        offer.isYearly -> stringResource(R.string.paywall_tier_annual)
        else -> stringResource(R.string.paywall_tier_monthly)
    }
    Box(Modifier.fillMaxWidth().padding(top = if (recommended) MM.space.m else 0.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (recommended && !selected) Modifier.shadow(MMSize.hairline * 4, shape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
                .choiceCard(c, shape, selected, MM.elevation.floating)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = MM.space.l, vertical = if (recommended) MM.space.xl else MM.space.l)
                .testTag("plan_${offer.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChoiceMark(selected)
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text("${offer.plan.label} · $kindLabel", style = MM.type.heading, color = c.ink)
                val billing = if (offer.isLifetime) stringResource(R.string.paywall_one_payment) else stringResource(R.string.paywall_billed_every, offer.period)
                val trial = offer.trialDays?.takeIf { it > 0 }?.let { stringResource(R.string.paywall_trial_days, it) }
                val sub = listOfNotNull(billing, trial).joinToString(" · ")
                Text(sub, style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.padding(top = 2.dp))
                saving?.let { Text(stringResource(R.string.paywall_save_percent, it), style = MM.type.caption, color = c.success, modifier = Modifier.padding(top = MM.space.xs)) }
            }
            Spacer(Modifier.width(MM.space.s))
            Column(horizontalAlignment = Alignment.End) {
                Text(if (offer.isLifetime) offer.priceText else stringResource(R.string.paywall_price_per_period, offer.priceText, offer.period), style = MM.type.heading, color = c.ink, textAlign = TextAlign.End)
                perMonth?.let { Text(stringResource(R.string.paywall_per_month, it), style = MM.type.caption, color = c.accent, modifier = Modifier.padding(top = 2.dp)) }
            }
        }
        if (recommended) {
            Text(
                stringResource(R.string.paywall_best_value),
                style = MM.type.overline, color = c.onAccent,
                modifier = Modifier.align(Alignment.TopStart).offset(x = MM.space.l, y = -MM.space.m)
                    .clip(MM.radius.pill)
                    .background(Brush.horizontalGradient(listOf(c.accent, lerp(c.accent, Brand.Violet, 0.4f))))
                    .padding(horizontal = MM.space.s + MM.space.xs, vertical = MM.space.xs)
            )
        }
    }
}

/**
 * What everyone gets, always visible, then the full Free and Plan table on request. Every paid row is a
 * capability the code enforces; the features everyone gets are stated separately instead of being sold back.
 */
@Composable
private fun CompareSection(open: Boolean, onToggle: () -> Unit) {
    val c = MM.colors
    val motion = MM.motion
    Text(PaywallCopy.ALWAYS_FREE_LINE, style = MM.type.secondary, color = c.accent, modifier = Modifier.padding(top = MM.space.xl).testTag("paywall_on_every_plan"))
    TextAction(
        stringResource(if (open) R.string.paywall_compare_hide else R.string.paywall_compare_show),
        onClick = onToggle, modifier = Modifier.padding(top = MM.space.xs).testTag("paywall_compare")
    )
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(motion.standard()) + expandVertically(motion.standard()),
        exit = fadeOut(motion.quick()) + shrinkVertically(motion.standard())
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = MM.space.xs)) {
                Spacer(Modifier.weight(1f)); HeaderCell(stringResource(R.string.paywall_col_free)); HeaderCell(stringResource(R.string.paywall_col_plan))
            }
            PaywallCopy.includedRows.forEach { row -> TableRow(row.label, free = row.free, paid = row.paid) }
        }
    }
    Text(
        stringResource(R.string.paywall_yours_always),
        style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = MM.space.s)
    )
}

@Composable
private fun HeaderCell(text: String) {
    Text(text, style = MM.type.caption, color = MM.colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
}

@Composable
private fun TableRow(label: String, free: Boolean, paid: Boolean) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = MM.space.xs).clip(MM.radius.small).background(c.surface)
            .border(MMSize.hairline, c.lineSoft, MM.radius.small)
            .padding(horizontal = MM.space.m, vertical = MM.space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MM.type.secondary, color = c.ink, modifier = Modifier.weight(1f))
        Mark(free); Mark(paid)
    }
}

@Composable
private fun Mark(on: Boolean) {
    val c = MM.colors
    Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.paywall_included), tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
        else {
            val none = stringResource(R.string.paywall_not_included)
            Text("—", style = MM.type.body, color = c.inkFaint, modifier = Modifier.semantics { contentDescription = none })
        }
    }
}

/** A quiet link under the button, with a full 48 dp touch target. */
@Composable
private fun SmallLink(label: String, tag: String, onClick: () -> Unit) {
    val c = MM.colors
    Box(
        Modifier.heightIn(min = MMSize.minTouch).clip(MM.radius.small).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MM.space.s).testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MM.type.caption, color = c.inkSecondary)
    }
}

/** A short, dismissible explanation in front of a paid feature or a limit. "Not now" just closes it. */
@Composable
fun UpgradeDialog(title: String, body: String, onSeePlans: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onSeePlans, modifier = Modifier.testTag("upgrade_see_plans")) { Text(stringResource(R.string.paywall_upgrade_see_plans)) } },
        dismissButton = { TextButton(onClick = onNotNow, modifier = Modifier.testTag("upgrade_not_now")) { Text(stringResource(R.string.paywall_upgrade_not_now)) } }
    )
}

/**
 * The first-result offer: after a real recording has become notes (never after the sample), once,
 * and never more than weekly. It explains the plans and lets the person walk straight on.
 */
@Composable
fun PostResultOfferHost(onResultScreen: Boolean, onOpenPlans: (PaywallTrigger) -> Unit, meetingId: String? = null) {
    val context = LocalContext.current
    val prefsManager = remember { UserPreferencesManager(context) }
    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    val billing = Billing.gateway
    val owned by billing.entitlements.collectAsState()
    val tierMode by com.craftflowtechnologies.meetingmind.core.billing.TieringModeStore.mode.collectAsState()
    var show by remember { mutableStateOf(false) }
    val saved = prefs

    LaunchedEffect(onResultScreen, meetingId, saved?.firstResultOfferShown, tierMode) {
        com.craftflowtechnologies.meetingmind.core.billing.TieringModeStore.current(context)
        if (!onResultScreen || meetingId == null) { show = false; return@LaunchedEffect }
        if (saved == null || show) return@LaunchedEffect
        // Never over Zuri's moment: wait for the First Keep to finish, then let them read their notes.
        com.craftflowtechnologies.meetingmind.feature.moments.MomentOverlay.showing.first { !it }
        delay(2500)
        if (com.craftflowtechnologies.meetingmind.feature.moments.MomentOverlay.showing.value) return@LaunchedEffect
        // Only after a real recording that became notes: never a failed, partial, silent or still-processing one.
        val outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.craftflowtechnologies.meetingmind.feature.moments.readNoteFacts(context, meetingId)?.outcome }.getOrNull()
        }
        val succeeded = outcome == com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome.COMPLETE ||
            outcome == com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome.AI_FALLBACK
        if (!succeeded) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val state = PaywallState(saved.paywallLastShownAt, saved.paywallDismissals, saved.firstResultOfferShown)
        if (PaywallPolicy.shouldShow(PaywallTrigger.FIRST_RESULT, billing, owned.effective(now), state, now, tierMode)) {
            runCatching { prefsManager.markPaywallShown(now, firstResult = true) }
            // PAYWALL_VIEWED is tracked once, by PaywallViewModel when the paywall itself opens.
            show = true
        }
    }
    if (show) UpgradeDialog(
        title = androidx.compose.ui.res.stringResource(com.craftflowtechnologies.meetingmind.R.string.first_result_offer_title),
        body = androidx.compose.ui.res.stringResource(com.craftflowtechnologies.meetingmind.R.string.first_result_offer_body),
        onSeePlans = { show = false; onOpenPlans(PaywallTrigger.FIRST_RESULT) },
        onNotNow = { show = false; com.craftflowtechnologies.meetingmind.core.analytics.Analytics.track(com.craftflowtechnologies.meetingmind.core.analytics.Events.PAYWALL_DISMISSED, "placement" to "first_result_sheet") }
    )
}
