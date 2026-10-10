package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPresence
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsRules
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsStore
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Where the companion settings come from. Null (the default) means the real DataStore-backed
 * [CompanionSettingsStore]; screenshot tests and previews provide a fake.
 */
val LocalCompanionSettingsSource = staticCompositionLocalOf<CompanionSettingsSource?> { null }

/** The settings source in effect: the one provided above, or the app's store. */
@Composable
fun rememberCompanionSettingsSource(): CompanionSettingsSource {
    val provided = LocalCompanionSettingsSource.current
    val context = LocalContext.current
    return provided ?: remember(context) { CompanionSettingsStore(context) }
}

/** The live companion settings, with the form already resolved against the roster. Defaults until the first read. */
@Composable
fun rememberCompanionSettings(): State<CompanionSettings> {
    val source = rememberCompanionSettingsSource()
    return produceState(CompanionSettings(), source) { source.settings.collect { value = it } }
}

/** The name to show: the user's, or the form's own. */
@Composable
fun CompanionSettings.displayName(form: CompanionForm): String = name ?: stringResource(CompanionRoster.displayName(form))

/**
 * Whether a slot on [page] would draw the companion now (the presence filter, §3.3). Host layouts use
 * it to collapse the space around a hidden companion.
 */
@Composable
fun rememberCompanionVisible(page: CompanionPage): Boolean {
    val settings by rememberCompanionSettings()
    val tick = rememberHiddenExpiryTick(settings.hiddenUntilMs)
    @Suppress("UNUSED_EXPRESSION") tick
    return settings.form != null && CompanionPresence.visibleOn(page, settings, LocalClock(System.currentTimeMillis()))
}

/** Bumps when "hide until tomorrow" runs out, so a screen that stays open brings the companion back at 07:00. */
@Composable
private fun rememberHiddenExpiryTick(hiddenUntilMs: Long?): Long {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(hiddenUntilMs) {
        val wait = (hiddenUntilMs ?: return@LaunchedEffect) - System.currentTimeMillis()
        if (wait > 0) { delay(wait); tick++ }
    }
    return tick
}

/** A tap earns a small nod, at most once every 10 s (§5.3). It never navigates. */
private const val NodGapMs = 10_000L
private const val NodMs = 600L

/**
 * What app screens use (§10.2): reads the settings, applies the presence filter and the hide-until
 * time, draws the companion, and opens the quick sheet on a long-press or the TalkBack action.
 *
 * Renders nothing when the companion is filtered out, so host layouts collapse cleanly. [endGap] is
 * space after the companion that exists only while it is drawn.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZuriSlot(
    page: CompanionPage,
    size: Dp,
    modifier: Modifier = Modifier,
    state: CompanionState = CompanionState.IDLE,
    level: () -> Float = { 0f },
    endGap: Dp = 0.dp,
    /** On Home, a small "Show {companion}" action after "Hide until tomorrow". */
    offerShow: Boolean = false
) {
    val source = rememberCompanionSettingsSource()
    val settings by rememberCompanionSettings()
    rememberHiddenExpiryTick(settings.hiddenUntilMs)
    val form = settings.form
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()

    if (form == null) return
    if (!CompanionPresence.visibleOn(page, settings, LocalClock(now))) {
        val hidden = settings.hiddenUntilMs
        if (offerShow && hidden != null && now < hidden && settings.presence != com.craftflowtechnologies.meetingmind.core.companion.Presence.OFF) {
            TextAction(stringResource(R.string.companion_quick_show, settings.displayName(form)), { scope.launch { source.setHiddenUntil(null) } }, modifier)
        }
        return
    }

    val name = settings.displayName(form)
    val haptic = LocalHapticFeedback.current
    var sheet by remember { mutableStateOf(false) }
    var nodAt by remember { mutableLongStateOf(0L) }
    var nodding by remember { mutableStateOf(false) }
    LaunchedEffect(nodAt) { if (nodAt != 0L) { nodding = true; delay(NodMs); nodding = false } }
    val optionsLabel = stringResource(R.string.companion_quick_options, name)
    val optionsAction = stringResource(R.string.companion_quick_options_action, name)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .semantics(mergeDescendants = true) {
                    contentDescription = name
                    customActions = listOf(CustomAccessibilityAction(optionsAction) { sheet = true; true })
                }
                .combinedClickable(
                    onClickLabel = null,
                    onLongClickLabel = optionsLabel,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sheet = true },
                    onClick = {
                        val t = System.currentTimeMillis()
                        if (t - nodAt >= NodGapMs) nodAt = t
                    }
                )
        ) {
            Companion(
                form = form, state = state, size = size,
                variant = if (nodding) CompanionVariant.Nod else CompanionVariant.Normal,
                level = level
            )
        }
        if (endGap > 0.dp) Spacer(Modifier.width(endGap))
    }
    if (sheet) CompanionQuickSheet(onDismiss = { sheet = false })
}
