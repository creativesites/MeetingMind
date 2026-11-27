package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** What the account card says. Pure, so the wording is tested. */
internal data class AccountCardCopy(val title: String, val body: String, val action: String?)

internal fun accountCardCopy(linked: Boolean, email: String?, earlier: Int, busy: Boolean): AccountCardCopy = when {
    linked -> AccountCardCopy(
        "Signed in${email?.let { " as $it" }.orEmpty()}",
        "Your circles follow you to any phone.",
        null
    )
    earlier > 0 -> AccountCardCopy(
        "Sign in to get your circles back",
        "${if (earlier == 1) "A circle" else "$earlier circles"} from an earlier sign-in ${if (earlier == 1) "isn't" else "aren't"} showing. " +
            "Sign in with the Google account you used before and ${if (earlier == 1) "it comes" else "they come"} back.",
        if (busy) null else "Sign in with Google"
    )
    else -> AccountCardCopy(
        "Sign in to Circles",
        "Bring your circles from another phone, and keep them if you change or lose this one.",
        if (busy) null else "Sign in with Google"
    )
}

/**
 * The account, up front on the Circles home: signed in (a quiet line), or signed out (a card with the Sign in button).
 * Someone who reinstalled, or is joining from a new phone, finds the way back here without starting a circle first.
 */
@Composable
internal fun CirclesAccountCard(
    account: AccountUiState,
    earlier: Int,
    onLink: (Context) -> Unit,
    onSwitch: () -> Unit,
    onDismissConflict: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val copy = accountCardCopy(account.linked, account.email, earlier, account.busy)
    if (account.linked) {
        Row(
            modifier.fillMaxWidth().padding(vertical = MM.space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Icon(Icons.Rounded.CloudDone, null, tint = MM.colors.accent, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(copy.title, style = MM.type.caption, color = MM.colors.ink)
                Text(copy.body, style = MM.type.caption, color = MM.colors.inkMuted)
            }
        }
    } else {
        Surface(
            shape = MM.radius.card,
            color = MM.colors.surfaceRaised,
            border = BorderStroke(MMSize.hairline, MM.colors.lineSoft),
            modifier = modifier.fillMaxWidth()
        ) {
            Column {
                Box(Modifier.fillMaxWidth().height(92.dp)) {
                    PackImage("photo_994", thumb = false, modifier = Modifier.fillMaxSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, MM.colors.scrim.copy(alpha = 0.72f)))))
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(MM.space.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Login, null, tint = CirclesFixedColors.White, modifier = Modifier.size(18.dp))
                        Text(
                            copy.title, style = MM.type.heading, color = CirclesFixedColors.White,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                }
                Column(Modifier.padding(MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text(copy.body, style = MM.type.secondary, color = MM.colors.inkSecondary)
                    com.craftflowtechnologies.meetingmind.core.ui.GoogleSignInButton(
                        onClick = { onLink(context.findHostActivity() ?: context) },
                        busy = account.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    account.message?.let { StatusLine(StatusKind.Info, it) }
                }
            }
        }
    }
    SwitchAccountDialog(account, onSwitch, onDismissConflict)
}

/** Circles with nothing in them yet: three pictures from the pack, one line, and the two ways in. */
@Composable
internal fun CirclesWelcome(onStart: () -> Unit, onJoin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = MM.space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Box(Modifier.height(150.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val tile = Modifier.size(width = 112.dp, height = 140.dp)
            WelcomeTile("photo_987", tile.offset(x = (-78).dp, y = 8.dp).rotate(-7f))
            WelcomeTile("photo_476", tile.offset(x = 78.dp, y = 8.dp).rotate(7f))
            WelcomeTile("photo_206", tile)
        }
        Text(
            "Your people, in one place", style = MM.type.title, color = MM.colors.ink,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "A circle is a small private space for your team, study group, family or church: updates, prayer, plans and chat, together.",
            style = MM.type.secondary, color = MM.colors.inkSecondary,
            modifier = Modifier.padding(horizontal = MM.space.m)
        )
        Spacer(Modifier.height(MM.space.xs))
        PrimaryButton("Start a circle", onStart, leadingIcon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Join with a code", onJoin, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WelcomeTile(photoId: String, modifier: Modifier) {
    Surface(
        shape = MM.radius.card,
        color = MM.colors.surfaceSunk,
        border = BorderStroke(2.dp, MM.colors.surface),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        PackImage(photoId, thumb = true, modifier = Modifier.fillMaxSize().clip(MM.radius.card))
    }
}

/**
 * The sign-in offer inside the create and join flows: one line on why, and the Google button. Shown only while the
 * person is signed out, so the circle they make or join follows them to any phone. Nothing here blocks the flow.
 */
@Composable
internal fun CirclesSignInPrompt(
    account: AccountUiState,
    body: String,
    onLink: (Context) -> Unit,
    onSwitch: () -> Unit,
    onDismissConflict: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (account.linked) return
    val context = LocalContext.current
    Surface(shape = MM.radius.card, color = MM.colors.surfaceSunk, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Icon(Icons.Rounded.CloudDone, null, tint = MM.colors.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                Text(body, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.weight(1f))
            }
            com.craftflowtechnologies.meetingmind.core.ui.GoogleSignInButton(
                onClick = { onLink(context.findHostActivity() ?: context) },
                busy = account.busy,
                modifier = Modifier.fillMaxWidth()
            )
            account.message?.let { StatusLine(StatusKind.Info, it) }
        }
    }
    SwitchAccountDialog(account, onSwitch, onDismissConflict)
}
