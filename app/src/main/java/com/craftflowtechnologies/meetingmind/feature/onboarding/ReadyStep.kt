package com.craftflowtechnologies.meetingmind.feature.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.setup.SetupGuide
import com.craftflowtechnologies.meetingmind.core.ui.GoogleSignInButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.circles2.AccountUiState
import com.craftflowtechnologies.meetingmind.feature.circles2.SwitchAccountDialog
import com.craftflowtechnologies.meetingmind.feature.circles2.findHostActivity
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * "You're all set": the last step of the example route. The companion greets the person by name, one card sums up
 * what they chose (spaces, how notes are made, who keeps them company), and an optional Sign in with Google brings
 * their circles. The first actions sit in the pinned bar ([ReadyActions]), so the first note is one tap away.
 *
 * Nothing here is asked again later: the Home first-note card picks up from whichever action was taken.
 */
@Composable
internal fun ReadyStep(
    companion: CompanionForm?,
    userName: String,
    spaces: Set<NotebookSpace>,
    setup: SetupChoice,
    pack: PhonePack,
    account: AccountUiState,
    onLink: (Context) -> Unit,
    onSwitchAccount: () -> Unit,
    onDismissConflict: () -> Unit
) {
    val c = MM.colors
    val compact = LocalConfiguration.current.screenHeightDp < 720
    val companionName = companion?.let { stringResource(CompanionRoster.displayName(it)) }
    Column(Modifier.fillMaxWidth().testTag("onboarding_ready"), horizontalAlignment = Alignment.CenterHorizontally) {
        CompanionHalo(
            form = companion, size = if (compact) 112.dp else 152.dp,
            state = CompanionState.CELEBRATING, contentDescription = companionName,
            modifier = Modifier.padding(top = if (compact) MM.space.xs else MM.space.l)
        )
        val name = userName.trim()
        Text(
            if (name.isNotEmpty()) stringResource(R.string.onboarding_ready_title_named, name) else stringResource(R.string.onboarding_ready_title),
            style = MM.type.display, color = c.ink, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MM.space.m).testTag("onboarding_ready_title")
        )
        Text(
            companionName?.let { stringResource(R.string.onboarding_ready_line_companion, it) } ?: stringResource(R.string.onboarding_ready_line_plain),
            style = MM.type.body, color = c.inkSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MM.space.s, start = MM.space.s, end = MM.space.s)
        )
    }
    ReadySummary(companionName, spaces, setup, pack, Modifier.padding(top = MM.space.xl))
    CirclesSignIn(account, onLink, onSwitchAccount, onDismissConflict, Modifier.padding(top = MM.space.xl, bottom = MM.space.l))
}

/** One card, one line per choice: the spaces, how notes are made, who keeps the person company. */
@Composable
private fun ReadySummary(companionName: String?, spaces: Set<NotebookSpace>, setup: SetupChoice, pack: PhonePack, modifier: Modifier = Modifier) {
    val c = MM.colors
    val spaceNames = OnboardingSpaces.filter { it in spaces }.map { stringResource(spaceLabel(it)) }.joinToString(", ")
    Column(
        modifier.fillMaxWidth().clip(MM.radius.card).background(c.surface)
            .border(MMSize.hairline, c.line, MM.radius.card)
            .padding(MM.space.l).testTag("onboarding_ready_summary"),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        SummaryRow(Icons.Filled.Dashboard, stringResource(R.string.onboarding_ready_spaces, spaceNames))
        when (setup) {
            SetupChoice.INTERNET -> SummaryRow(Icons.Filled.Cloud, stringResource(R.string.onboarding_ready_internet))
            SetupChoice.ON_DEVICE -> if (pack.ready) {
                SummaryRow(Icons.Filled.PhoneAndroid, stringResource(R.string.onboarding_ready_device_ready))
            } else SummaryRow(
                Icons.Filled.PhoneAndroid,
                stringResource(R.string.onboarding_ready_device, SetupGuide.formatBytes(pack.bytes)),
                // The room it leaves, or the same plain warning the Setup step gave when the phone is short of space.
                when (pack.fits) {
                    true -> pack.freeAfterBytes?.let { stringResource(R.string.onboarding_ready_device_free_after, SetupGuide.formatBytes(it)) }
                    false -> stringResource(R.string.onboarding_setup_device_tight, SetupGuide.formatBytes(pack.freeBytes ?: 0L))
                    null -> null
                }
            )
        }
        SummaryRow(
            Icons.Filled.Favorite,
            companionName?.let { stringResource(R.string.onboarding_ready_companion, it) } ?: stringResource(R.string.onboarding_ready_no_companion)
        )
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, text: String, detail: String? = null) {
    val c = MM.colors
    Row(verticalAlignment = Alignment.Top) {
        IconTile(icon, on = true)
        Spacer(Modifier.width(MM.space.m))
        Column(Modifier.weight(1f).padding(top = MM.space.xs)) {
            Text(text, style = MM.type.body, color = c.ink)
            detail?.let { Text(it, style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

/**
 * Optional: Sign in with Google for Circles, reusing the Circles account link (a guest account becomes a Google
 * one). Someone reinstalling gets their circles back through the same "switch to that account" question Circles asks.
 * Skipping it changes nothing.
 */
@Composable
private fun CirclesSignIn(
    account: AccountUiState,
    onLink: (Context) -> Unit,
    onSwitch: () -> Unit,
    onDismissConflict: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().testTag("onboarding_ready_circles"), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text(stringResource(R.string.onboarding_ready_circles_title), style = MM.type.heading, color = c.ink)
        if (account.linked) {
            StatusLine(
                StatusKind.Ok,
                account.email?.let { stringResource(R.string.onboarding_ready_circles_linked_email, it) }
                    ?: stringResource(R.string.onboarding_ready_circles_linked)
            )
        } else {
            Text(stringResource(R.string.onboarding_ready_circles_line), style = MM.type.secondary, color = c.inkSecondary)
            GoogleSignInButton(
                onClick = { onLink(context.findHostActivity() ?: context) },
                busy = account.busy,
                modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs).testTag("onboarding_ready_google")
            )
            account.message?.let { StatusLine(StatusKind.Info, it) }
        }
    }
    SwitchAccountDialog(account, onSwitch, onDismissConflict)
}

/** The pinned actions of the "You're set" step: the first as the main button, the rest as quiet text actions. */
@Composable
internal fun ReadyActions(actions: List<ReadyAction>, onAction: (ReadyAction) -> Unit) {
    val first = actions.firstOrNull() ?: return
    GlowButton(stringResource(readyLabel(first)), "onboarding_ready_${first.name.lowercase()}", Modifier.fillMaxWidth()) { onAction(first) }
    actions.drop(1).forEach { action ->
        TextAction(
            stringResource(readyLabel(action)), onClick = { onAction(action) },
            modifier = Modifier.padding(top = MM.space.xs).testTag("onboarding_ready_${action.name.lowercase()}")
        )
    }
}

private fun readyLabel(action: ReadyAction): Int = when (action) {
    ReadyAction.RECORD -> R.string.onboarding_ready_record
    ReadyAction.YOUTUBE -> R.string.onboarding_ready_youtube
    ReadyAction.SET_UP_PHONE -> R.string.onboarding_button_set_up_phone
    ReadyAction.LOOK_AROUND -> R.string.onboarding_ready_look_around
}

private fun spaceLabel(space: NotebookSpace): Int = when (space) {
    NotebookSpace.WORK -> R.string.onboarding_space_work
    NotebookSpace.LEARNING -> R.string.onboarding_space_learning
    NotebookSpace.FAITH -> R.string.onboarding_space_faith
    NotebookSpace.PERSONAL -> R.string.onboarding_space_personal
}
