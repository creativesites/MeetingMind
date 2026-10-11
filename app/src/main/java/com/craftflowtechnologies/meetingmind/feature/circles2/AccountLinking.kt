package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesRepository
import com.craftflowtechnologies.meetingmind.core.circles2.LinkResult
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Keep your circles on any phone": whether this Circles account is tied to Google, and what's in progress. */
data class AccountUiState(
    val linked: Boolean = false,
    val email: String? = null,
    val busy: Boolean = false,
    /** One plain line after an attempt (success or why not). */
    val message: String? = null,
    /** That Google account already has circles of its own (from another phone): offer to switch to it. */
    val conflict: Boolean = false,
    val conflictEmail: String? = null
)

class AccountViewModel(private val repo: CirclesRepository) : ViewModel() {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AccountUiState> = _state

    private fun read() = AccountUiState(linked = !repo.auth.isAnonymous && repo.auth.currentUid != null, email = repo.auth.linkedEmail)

    /** [activityContext] must be an Activity (the Google picker needs one). */
    fun link(activityContext: Context) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch { apply(repo.auth.linkGoogle(activityContext)) }
    }

    /** The person agreed to switch to the existing account. This phone's circles are replaced by that account's (found with myCircles). */
    fun switchAccount() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, conflict = false) }
        viewModelScope.launch {
            val r = repo.auth.switchToLinkedAccount()
            if (r is LinkResult.Linked) repo.onAccountSwitched()
            apply(r, switched = true)
        }
    }

    fun dismissConflict() = _state.update { it.copy(conflict = false) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun apply(r: LinkResult, switched: Boolean = false) {
        _state.value = when (r) {
            is LinkResult.Linked -> AccountUiState(
                linked = true, email = r.email,
                message = if (switched) "Switched. Your circles from that account are here." else "Done. Your circles are saved to your Google account."
            )
            is LinkResult.AlreadyInUse -> read().copy(conflict = true, conflictEmail = r.email)
            LinkResult.Cancelled -> read()
            is LinkResult.Unavailable -> read().copy(message = r.message)
            is LinkResult.Failed -> read().copy(message = r.message)
        }
    }

    class Factory(private val repo: CirclesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repo) as T
    }
}

/** Walks up wrapped contexts to the Activity the Google picker needs. */
tailrec fun Context.findHostActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/**
 * The quiet offer on the Circles list: shown only to someone in a circle on an anonymous account. Once linked it
 * disappears (the linked state lives in the circle's settings).
 */
@Composable
fun KeepCirclesOffer(state: AccountUiState, onLink: (Context) -> Unit, onSwitch: () -> Unit, onDismissConflict: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!state.linked) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            StatusLine(
                StatusKind.Info,
                state.message ?: "Keep your circles on any phone. Sign in with Google so they come with you if you change or lose this one.",
                actionLabel = if (state.busy) null else "Sign in with Google",
                onAction = { onLink(context.findHostActivity() ?: context) }
            )
        }
    }
    SwitchAccountDialog(state, onSwitch, onDismissConflict)
}

/** The "Your account" block in a circle's settings: linked state, or the way to link. */
@Composable
fun AccountSection(state: AccountUiState, onLink: (Context) -> Unit, onSwitch: () -> Unit, onDismissConflict: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        if (state.linked) {
            Text(
                "Signed in with Google${state.email?.let { " as $it" }.orEmpty()}. Your circles follow you to any phone.",
                style = MM.type.secondary, color = MM.colors.inkSecondary
            )
        } else {
            Text(
                "This phone holds your circles right now. Sign in with Google to keep them if you change or lose it.",
                style = MM.type.secondary, color = MM.colors.inkSecondary
            )
            SecondaryButton(if (state.busy) "Opening Google..." else "Keep your circles on any phone", { onLink(context.findHostActivity() ?: context) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
        state.message?.let { StatusLine(StatusKind.Info, it) }
    }
    SwitchAccountDialog(state, onSwitch, onDismissConflict)
}

@Composable
private fun SwitchAccountDialog(state: AccountUiState, onSwitch: () -> Unit, onDismiss: () -> Unit) {
    if (!state.conflict) return
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised,
        title = { Text("That Google account already has circles", style = MM.type.heading, color = MM.colors.ink) },
        text = {
            Text(
                "${state.conflictEmail ?: "That account"} is already used by Circles on another phone. You can switch to it and see those circles here. The circles on this phone stay with this phone's guest account, so keep the invite codes if you want to rejoin them.",
                style = MM.type.body, color = MM.colors.inkSecondary
            )
        },
        confirmButton = { TextAction("Switch to that account", onSwitch) },
        dismissButton = { TextAction("Not now", onDismiss) }
    )
}
