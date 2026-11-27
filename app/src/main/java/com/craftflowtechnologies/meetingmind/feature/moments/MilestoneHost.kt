package com.craftflowtechnologies.meetingmind.feature.moments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.MilestoneKind
import com.craftflowtechnologies.meetingmind.core.companion.Moment
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedger
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.companion.Presence
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.displayName
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MilestoneCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.flow.first
import java.util.TimeZone

/**
 * Whether a milestone card may show: never when the person turned the companion off (Presence OFF) or hid it for
 * now, and never twice for one milestone (the ledger's once-ever cap). "No companion" still gets the card, drawn
 * with the glyph: the milestone belongs to the person, not to the character.
 */
internal fun milestoneAllowed(kind: MilestoneKind, settings: CompanionSettings, ledger: MomentLedger, clock: LocalClock): Boolean {
    if (settings.presence == Presence.OFF) return false
    val hidden = settings.hiddenUntilMs
    if (hidden != null && clock.nowMs < hidden) return false
    return ledger.allows(Moment.Proud(kind), clock)
}

/**
 * Shows the [MilestoneCard] for [kind] once ever, rising from the bottom of the screen above the navigation bar.
 * Pass the result of a [MilestoneDetector][com.craftflowtechnologies.meetingmind.core.companion.MilestoneDetector]
 * check; null shows nothing. Put it last in a full-size `Box`, aligned to the bottom.
 *
 * The milestone is written to the ledger as soon as the card shows, so leaving the screen or closing the app never
 * brings it back. Never use it on a sensitive page (prayer requests, grief or memorial notes: §5.10); callers
 * decide that, as they do for every moment.
 *
 * @param onShare a share action for the "Share with your circle" suggestion (§5.9), or null to leave it out. It
 *   opens the share flow; nothing is posted without the person's own tap there.
 */
@Composable
fun MilestoneHost(
    kind: MilestoneKind?,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null
) {
    if (kind == null) return
    val context = LocalContext.current
    val provided = LocalMomentLedgerStore.current
    val store = remember(provided, context) { provided ?: MomentLedgerStore(context) }
    val source = rememberCompanionSettingsSource()
    val settings by rememberCompanionSettings()
    // Survives rotation: a card already shown on this screen stays until closed, and isn't re-decided.
    var showing by rememberSaveable(kind) { mutableStateOf(false) }
    var decided by rememberSaveable(kind) { mutableStateOf(false) }
    val motion = MM.motion

    LaunchedEffect(kind, source, store) {
        if (decided) return@LaunchedEffect
        // The stored settings, not the defaults shown before the first read: Presence OFF must never flash a card.
        // A store read that fails means no card, never a crash.
        val stored = runCatching { source.settings.first() }.getOrNull() ?: return@LaunchedEffect
        val ledger = runCatching { store.ledger.first() }.getOrNull() ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        val clock = LocalClock(now, TimeZone.getDefault().getOffset(now).toLong())
        decided = true
        if (milestoneAllowed(kind, stored, ledger, clock)) {
            runCatching { store.record(Moment.Proud(kind), clock) }
            showing = true
        }
    }

    AnimatedVisibility(
        visible = showing,
        modifier = modifier,
        enter = slideInVertically(motion.enter<IntOffset>()) { it / 2 } + fadeIn(motion.enter<Float>()),
        exit = slideOutVertically(motion.standard<IntOffset>()) { it / 2 } + fadeOut(motion.quick<Float>())
    ) {
        val form: CompanionForm? = settings.form
        MilestoneCard(
            kind = kind,
            form = form,
            companionName = form?.let { settings.displayName(it) }.orEmpty(),
            onDone = { showing = false },
            onShare = onShare?.let { share -> { showing = false; share() } },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = MM.space.l, vertical = MM.space.m)
        )
    }
}
