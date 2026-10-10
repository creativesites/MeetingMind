package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.craftflowtechnologies.meetingmind.core.circles2.CHAT_EMOJI
import com.craftflowtechnologies.meetingmind.core.circles2.ChainState
import com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.MessageKind
import com.craftflowtechnologies.meetingmind.core.circles2.MessageReaction
import com.craftflowtechnologies.meetingmind.core.circles2.PollState
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ChatActions(
    val onDraft: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onReply: (ChatMessage) -> Unit = {},
    val onCancelReply: () -> Unit = {},
    val onEdit: (ChatMessage) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onDelete: (ChatMessage) -> Unit = {},
    val onReport: (ChatMessage) -> Unit = {},
    val reactionsFor: (String) -> Flow<List<MessageReaction>> = { flowOf(emptyList()) },
    val onToggleReaction: (String, String, List<MessageReaction>) -> Unit = { _, _, _ -> },
    val pollFor: (String) -> Flow<DataState<PollState>> = { flowOf(DataState.Loading) },
    val onVote: (PollState, String) -> Unit = { _, _ -> },
    val onClosePoll: (String) -> Unit = {},
    val chainFor: (String) -> Flow<DataState<ChainState>> = { flowOf(DataState.Loading) },
    val onClaim: (String, Int) -> Unit = { _, _ -> },
    val onRelease: (String, Int) -> Unit = { _, _ -> },
    val onNewPoll: () -> Unit = {},
    val onShareCard: () -> Unit = {},
    val onStartChain: () -> Unit = {},
    val onCelebrate: () -> Unit = {}
) { companion object { val None = ChatActions() } }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatTab(state: ChatUiState, draft: String, now: Long, actions: ChatActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var sheetFor by remember { mutableStateOf<ChatMessage?>(null) }
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex) }

    Column(modifier.fillMaxSize()) {
        state.failure?.let { Column(Modifier.padding(horizontal = MM.space.l)) { StatusLine(StatusKind.Warning, it.message) } }
        if (state.messages.isEmpty() && !state.loading) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    title = "No messages yet",
                    body = "Say hello, ask a question, or start a poll to pick a night for your next meeting.",
                    modifier = Modifier.padding(horizontal = MM.space.l)
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f), state = listState,
                contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                items(state.messages, key = { it.id }) { m ->
                    MessageItem(m, state, now, actions, onLongPress = { sheetFor = m })
                }
            }
        }
        ChatInput(state, draft, actions)
    }
    sheetFor?.let { m ->
        MessageActionsSheet(
            m, mine = m.authorUid == state.myUid, isAdmin = state.isAdmin, actions = actions,
            reactions = actions.reactionsFor(m.id), onDismiss = { sheetFor = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(m: ChatMessage, state: ChatUiState, now: Long, actions: ChatActions, onLongPress: () -> Unit) {
    val c = MM.colors
    val mine = m.authorUid == state.myUid
    when {
        m.deleted -> Text(
            "Message removed", style = MM.type.caption.copy(fontStyle = FontStyle.Italic), color = c.inkMuted,
            modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs)
        )
        m.kind == MessageKind.Celebration -> CelebrationMessage(m, onLongPress)
        else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Text(
                (if (mine) "You" else m.authorName) + " · " + relativeTime(now, m.createdAt) + (if (m.editedAt != null) " · edited" else ""),
                style = MM.type.caption, color = c.inkSecondary
            )
            Surface(
                shape = MM.radius.card, color = if (mine) c.accentWash else c.surface,
                modifier = Modifier.widthIn(max = MM.space.xxl * 10).combinedClickable(
                    onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Message actions"
                )
            ) {
                Column(Modifier.padding(MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    val quoted = state.byId(m.replyTo)
                    if (m.kind == MessageKind.Reply && m.replyTo != null) {
                        Surface(shape = MM.radius.small, color = c.surfaceSunk) {
                            Text(
                                quoted?.let { "${it.authorName}: ${if (it.deleted) "Message removed" else it.text.ifBlank { "Card" }}" } ?: "Earlier message",
                                style = MM.type.caption, color = c.inkSecondary, maxLines = 2,
                                modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                            )
                        }
                    }
                    when (m.kind) {
                        MessageKind.Card -> m.card?.let { SharedCard(it) }
                        MessageKind.Poll -> m.pollId?.let { PollCard(m, actions) } ?: Text(m.text, style = MM.type.body, color = c.ink)
                        MessageKind.Chain -> m.chainId?.let { ChainCard(m, actions) } ?: Text(m.text, style = MM.type.body, color = c.ink)
                        else -> Text(m.text, style = MM.type.body, color = c.ink)
                    }
                }
            }
            ReactionRow(m.id, actions, mine)
        }
    }
}

@Composable
private fun ReactionRow(messageId: String, actions: ChatActions, mine: Boolean) {
    val reactions by remember(messageId) { actions.reactionsFor(messageId) }.collectAsState(emptyList())
    if (reactions.isEmpty()) return
    Row(Modifier.padding(top = MM.space.xs), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        reactions.forEach { r ->
            MMChip("${r.emoji} ${r.count}", selected = r.mine, onClick = { actions.onToggleReaction(messageId, r.emoji, reactions) })
        }
    }
}

@Composable
private fun ChatInput(state: ChatUiState, draft: String, actions: ChatActions) {
    val c = MM.colors
    var plus by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().imePadding()) {
        val bar = state.editing?.let { "Editing your message" to actions.onCancelEdit }
            ?: state.replyTo?.let { "Replying to ${it.authorName}: ${it.text.take(60)}" to actions.onCancelReply }
        if (bar != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.l), verticalAlignment = Alignment.CenterVertically) {
                Text(bar.first, style = MM.type.caption, color = c.inkSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(onClick = bar.second) { Icon(Icons.Rounded.Close, "Cancel", tint = c.inkSecondary) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.s, vertical = MM.space.xs), verticalAlignment = Alignment.Bottom) {
            Box {
                IconButton(onClick = { plus = true }) { Icon(Icons.Rounded.Add, "More: poll, card, prayer chain, celebrate", tint = c.accent) }
                DropdownMenu(plus, { plus = false }, containerColor = c.surfaceRaised) {
                    PlusItem("Start a poll") { plus = false; actions.onNewPoll() }
                    PlusItem("Share a card") { plus = false; actions.onShareCard() }
                    PlusItem("Start a prayer chain") { plus = false; actions.onStartChain() }
                    PlusItem("Celebrate something") { plus = false; actions.onCelebrate() }
                }
            }
            CircleField(draft, actions.onDraft, "Message", modifier = Modifier.weight(1f), maxLines = 4)
            IconButton(onClick = actions.onSend, enabled = draft.isNotBlank()) {
                Icon(Icons.Rounded.Send, "Send", tint = if (draft.isNotBlank()) c.accent else c.inkMuted)
            }
        }
    }
}

@Composable
private fun PlusItem(text: String, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text, style = MM.type.body, color = MM.colors.ink) }, onClick = onClick)

/** Long-press sheet: react, reply, copy, edit, report, delete. */
@Composable
fun MessageActionsSheet(
    m: ChatMessage, mine: Boolean, isAdmin: Boolean, actions: ChatActions,
    reactions: Flow<List<MessageReaction>>, onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val current by remember(m.id) { reactions }.collectAsState(emptyList())
    val text = m.card?.text ?: m.text
    CircleSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CHAT_EMOJI.forEach { e ->
                MMChip(e, selected = current.any { it.mine && it.emoji == e }, onClick = { actions.onToggleReaction(m.id, e, current); onDismiss() })
            }
        }
        ListRow("Reply", onClick = { actions.onReply(m); onDismiss() })
        if (text.isNotBlank()) ListRow("Copy text", onClick = { clipboard.setText(AnnotatedString(text)); onDismiss() })
        if (mine && (m.kind == MessageKind.Text || m.kind == MessageKind.Reply)) ListRow("Edit", onClick = { actions.onEdit(m); onDismiss() })
        if (!mine) ListRow("Report", onClick = { actions.onReport(m); onDismiss() })
        if (mine || isAdmin) ListRow("Delete", onClick = { actions.onDelete(m); onDismiss() })
    }
}
