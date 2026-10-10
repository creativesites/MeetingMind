package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Entry point: wires the view model. [initialCode] comes from a tapped invite link. */
@Composable
fun CirclesListScreen(
    onBack: () -> Unit,
    onOpenCircle: (String) -> Unit,
    onCreate: () -> Unit,
    initialCode: String? = null,
    onInitialCodeHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val vm: CirclesListViewModel = viewModel(factory = CirclesListViewModel.Factory(repo))
    val list by vm.list.collectAsState()
    val join by vm.join.collectAsState()
    var showJoin by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialCode) {
        if (initialCode != null) { vm.onPaste(initialCode); showJoin = true; onInitialCodeHandled() }
    }
    CirclesListContent(
        state = list, join = join, showJoin = showJoin,
        onBack = onBack, onOpen = onOpenCircle, onCreate = onCreate,
        onShowJoin = { showJoin = true }, onHideJoin = { showJoin = false; vm.resetJoin() },
        onPaste = vm::onPaste, onJoinName = vm::onJoinName,
        onJoin = { vm.join { id -> showJoin = false; vm.resetJoin(); onOpenCircle(id) } },
        onRefresh = vm::refresh
    )
}

@Composable
fun CirclesListContent(
    state: CirclesListState,
    join: JoinUiState,
    showJoin: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onShowJoin: () -> Unit,
    onHideJoin: () -> Unit,
    onPaste: (String) -> Unit,
    onJoinName: (String) -> Unit,
    onJoin: () -> Unit,
    onRefresh: () -> Unit
) {
    val canAct = state !is CirclesListState.NotConnected
    Column(Modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding()) {
        ScreenHeader(
            "Circles", Modifier.padding(horizontal = MM.space.s),
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.inkSecondary) } },
            actions = { if (canAct) TextAction("Join", onShowJoin) }
        )
        when (state) {
            CirclesListState.NotConnected -> EmptyState(
                title = "Circles isn't connected yet",
                body = "Circles lets small groups pray, share and chat together. This build isn't linked to the Circles service yet, so there's nothing to join or create. Everything else in MeetingMind works as normal.",
                modifier = Modifier.padding(horizontal = MM.space.l),
                illustration = { Icon(Icons.Rounded.Groups, null, tint = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.xl)) }
            )
            CirclesListState.Loading -> Column(Modifier.padding(MM.space.l)) { StatusLine(StatusKind.Info, "Loading your circles...") }
            is CirclesListState.Ready -> if (state.circles.isEmpty()) {
                Column {
                    state.note?.let { Note(it, onRefresh) }
                    EmptyState(
                        title = "No circles yet",
                        body = "A circle is a small private space where people pray for each other, celebrate together and chat. Start one for your group, or join with a code someone shared.",
                        modifier = Modifier.padding(horizontal = MM.space.l),
                        action = {
                            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s), horizontalAlignment = Alignment.CenterHorizontally) {
                                PrimaryButton("Start a circle", onCreate, leadingIcon = Icons.Rounded.Add)
                                SecondaryButton("Join with a code", onShowJoin)
                            }
                        }
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f), contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s),
                    verticalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    state.note?.let { n -> item { Note(n, onRefresh) } }
                    items(state.circles, key = { it.id }) { c -> CircleRow(c) { onOpen(c.id) } }
                    item { Row(Modifier.fillMaxWidth().padding(top = MM.space.s), horizontalArrangement = Arrangement.Center) { PrimaryButton("Start a circle", onCreate, leadingIcon = Icons.Rounded.Add) } }
                }
            }
        }
    }
    if (showJoin) JoinSheet(join, onHideJoin, onPaste, onJoinName, onJoin)
}

@Composable
private fun Note(f: CirclesFailure, onRefresh: () -> Unit) {
    Column(Modifier.padding(horizontal = MM.space.l)) { StatusLine(StatusKind.Warning, f.message, actionLabel = "Retry", onAction = onRefresh) }
}

@Composable
private fun CircleRow(c: Circle, onClick: () -> Unit) {
    MMCard(onClick = onClick) {
        ListRow(
            title = c.name,
            subtitle = "${c.groupWord} · ${if (c.memberCount == 1) "1 member" else "${c.memberCount} members"}",
            leading = { InitialsAvatar(c.name) },
            trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkMuted) }
        )
    }
}

@Composable
fun JoinSheet(join: JoinUiState, onDismiss: () -> Unit, onPaste: (String) -> Unit, onName: (String) -> Unit, onJoin: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    CircleSheet(onDismiss) {
        Text("Join a circle", style = MM.type.title, color = MM.colors.ink)
        Text("Paste the code, the link, or the whole message you were sent.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        CircleField(
            join.input, onPaste, "Invite code or message", placeholder = "GRACE-7K2Q", minLines = 2, maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction("Paste from clipboard", { clipboard.getText()?.text?.let(onPaste) })
        }
        if (join.input.isNotBlank()) {
            if (join.code != null) StatusLine(StatusKind.Info, "Found the code ${join.code}.")
            else StatusLine(StatusKind.Warning, "No code found yet. A code looks like GRACE-7K2Q.")
        }
        CircleField(join.name, onName, "Your name in this circle", placeholder = "How should people see you?")
        join.error?.let { StatusLine(StatusKind.Error, it) }
        PrimaryButton(if (join.busy) "Joining..." else "Join", onJoin, enabled = join.canJoin, modifier = Modifier.fillMaxWidth())
    }
}
