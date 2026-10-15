package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.craftflowtechnologies.meetingmind.core.circles2.MediaKind
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
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
    val onCelebrate: () -> Unit = {},
    val onForward: (ChatMessage) -> Unit = {},
    val onCancelForward: () -> Unit = {},
    val onPin: (ChatMessage) -> Unit = {},
    val onUnpin: () -> Unit = {},
    /** A photo, file or voice note from the device, sent as a message. */
    val onSendAttachment: (MediaKind, PickedFile) -> Unit = { _, _ -> },
    val onCancelUpload: () -> Unit = {},
    /** A one-line notice for the snackbar. */
    val onNotice: (String) -> Unit = {}
) { companion object { val None = ChatActions() } }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatTab(state: ChatUiState, draft: String, now: Long, actions: ChatActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var sheetFor by remember { mutableStateOf<ChatMessage?>(null) }
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val file = readPicked(context, uri)
            if (file == null) actions.onNotice("That photo couldn't be read.") else actions.onSendAttachment(MediaKind.Image, file)
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val file = readPicked(context, uri)
            if (file == null) actions.onNotice("That file couldn't be read.") else actions.onSendAttachment(MediaKind.File, file)
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) actions.onNotice("Allow the microphone to record a voice note.")
        else if (recorder.start()) recording = true
        else actions.onNotice("The microphone isn't available right now.")
    }
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex) }

    Column(modifier.fillMaxSize()) {
        state.failure?.let { Column(Modifier.padding(horizontal = MM.space.l)) { StatusLine(StatusKind.Warning, it.message) } }
        state.pinned?.let { p ->
            Surface(Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.xs), shape = MM.radius.small, color = MM.colors.goldWash) {
                Row(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pinned by ${p.authorName}", style = MM.type.caption, color = MM.colors.goldInk)
                        Text((p.card?.text ?: p.text).ifBlank { "Pinned message" }, style = MM.type.secondary, color = MM.colors.ink, maxLines = 2)
                    }
                    if (state.isAdmin) IconButton(onClick = actions.onUnpin) { Icon(Icons.Rounded.Close, "Unpin", tint = MM.colors.inkSecondary) }
                }
            }
        }
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
                // Messages are oldest first. A day label starts each day; a divider marks where new messages begin.
                val firstNew = if (state.unread > 0) state.messages.indexOfFirst { it.createdAt > state.readAt && it.authorUid != state.myUid && !it.deleted } else -1
                val lastOwn = state.messages.indexOfLast { it.authorUid == state.myUid && !it.deleted }
                itemsIndexed(state.messages, key = { _, m -> m.id }) { i, m ->
                    val prev = state.messages.getOrNull(i - 1)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        if (prev == null || !sameDay(prev.createdAt, m.createdAt)) DayDivider(dayLabel(m.createdAt, now))
                        if (i == firstNew) UnreadDivider(state.unread)
                        // "Seen by" is shown under my latest message: members who have read the chat since it was sent.
                        val seen = if (i == lastOwn) state.members.count { it.uid != m.authorUid && it.lastReadAt > 0L && it.lastReadAt >= m.createdAt } else 0
                        MessageItem(m, state, now, actions, continued = prev != null && continues(prev, m), seen = seen, onLongPress = { sheetFor = m })
                    }
                }
            }
        }
        if (state.typing.isNotEmpty()) {
            Text(typingText(state.typing), style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.padding(horizontal = MM.space.l))
        }
        if (state.uploading) {
            Column(Modifier.fillMaxWidth().padding(horizontal = MM.space.l)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uploading…", style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.weight(1f))
                    TextAction("Cancel", actions.onCancelUpload)
                }
            }
        }
        ChatInput(
            state, draft, actions,
            recording = recording,
            onAttachPhoto = { pickPhoto.launch("image/*") },
            onAttachFile = { pickFile.launch("application/pdf") },
            onMicStart = { micPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
            onMicStop = {
                val file = recorder.stop()
                recording = false
                if (file == null) actions.onNotice("That was too short to keep.") else actions.onSendAttachment(MediaKind.Voice, file)
            }
        )
    }
    sheetFor?.let { m ->
        MessageActionsSheet(
            m, mine = m.authorUid == state.myUid, isAdmin = state.isAdmin, actions = actions,
            reactions = actions.reactionsFor(m.id), pinnedId = state.pinned?.id, onDismiss = { sheetFor = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private const val GROUP_MS = 5 * 60_000L
private const val DAY_MS = 86_400_000L

private fun dayKey(t: Long): String = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date(t))
private fun sameDay(a: Long, b: Long): Boolean = dayKey(a) == dayKey(b)

/** Same person, within a few minutes, same day, and neither is a celebration or a deleted message. */
private fun continues(prev: ChatMessage, m: ChatMessage): Boolean =
    prev.authorUid == m.authorUid && !prev.deleted && !m.deleted &&
        prev.kind != MessageKind.Celebration && m.kind != MessageKind.Celebration &&
        m.createdAt >= prev.createdAt && m.createdAt - prev.createdAt < GROUP_MS && sameDay(prev.createdAt, m.createdAt)

private fun dayLabel(t: Long, now: Long): String = when {
    sameDay(t, now) -> "Today"
    sameDay(t, now - DAY_MS) -> "Yesterday"
    else -> java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(java.util.Date(t))
}

@Composable
private fun DayDivider(label: String) {
    Text(label, style = MM.type.caption, color = MM.colors.inkSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs))
}

@Composable
private fun UnreadDivider(count: Int) {
    Text(
        if (count == 1) "1 new message" else "$count new messages",
        style = MM.type.caption, color = MM.colors.inkSecondary, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs)
    )
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun MessageItem(m: ChatMessage, state: ChatUiState, now: Long, actions: ChatActions, continued: Boolean = false, seen: Int = 0, onLongPress: () -> Unit) {
    val c = MM.colors
    val mine = m.authorUid == state.myUid
    when {
        m.deleted -> Text(
            "Message removed", style = MM.type.caption.copy(fontStyle = FontStyle.Italic), color = c.inkMuted,
            modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs)
        )
        m.kind == MessageKind.Celebration -> CelebrationMessage(m, onLongPress)
        else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            // A message that continues the same person's run within a few minutes shows no name line again.
            if (!continued) Text(
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
                        MessageKind.Image -> {
                            m.attachment?.let { a -> MediaImage(state.circleId, a.path, Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(MM.radius.card), "Photo from ${m.authorName}") }
                            if (m.text.isNotBlank()) Text(m.text, style = MM.type.body, color = c.ink)
                        }
                        MessageKind.Voice -> m.attachment?.let { VoiceBubble(state.circleId, it) }
                        MessageKind.File -> m.attachment?.let { FileBubble(state.circleId, it) }
                        MessageKind.Card -> m.card?.let { ChatCard(it) }
                        MessageKind.Poll -> m.pollId?.let { PollCard(m, actions) } ?: Text(m.text, style = MM.type.body, color = c.ink)
                        MessageKind.Chain -> m.chainId?.let { ChainCard(m, actions) } ?: Text(m.text, style = MM.type.body, color = c.ink)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                            if (m.forwardedFrom != null) Text("Forwarded", style = MM.type.caption.copy(fontStyle = FontStyle.Italic), color = c.inkSecondary)
                            Text(mentionText(m.text, state.members), style = MM.type.body, color = c.ink)
                        }
                    }
                }
            }
            ReactionRow(m.id, actions, mine)
            if (seen > 0) Text(if (seen == 1) "Seen" else "Seen by $seen", style = MM.type.caption, color = c.inkSecondary)
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
private fun ChatInput(state: ChatUiState, draft: String, actions: ChatActions, recording: Boolean, onAttachPhoto: () -> Unit, onAttachFile: () -> Unit, onMicStart: () -> Unit, onMicStop: () -> Unit) {
    val c = MM.colors
    var plus by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().imePadding()) {
        val bar = state.editing?.let { "Editing your message" to actions.onCancelEdit }
            ?: state.forwarding?.let { "Forwarding ${it.authorName}'s message" to actions.onCancelForward }
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
                    PlusItem("Send a photo") { plus = false; onAttachPhoto() }
                    PlusItem("Send a PDF") { plus = false; onAttachFile() }
                    PlusItem("Start a poll") { plus = false; actions.onNewPoll() }
                    PlusItem("Share a card") { plus = false; actions.onShareCard() }
                    PlusItem("Start a prayer chain") { plus = false; actions.onStartChain() }
                    PlusItem("Celebrate something") { plus = false; actions.onCelebrate() }
                }
            }
            CircleField(draft, actions.onDraft, "Message", modifier = Modifier.weight(1f), maxLines = 4)
            if (draft.isBlank()) {
                if (recording) IconButton(onClick = onMicStop) { Icon(Icons.Rounded.Stop, "Stop and send voice note", tint = c.accent) }
                else IconButton(onClick = onMicStart) { Icon(Icons.Rounded.Mic, "Record a voice note", tint = c.accent) }
            }
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
    reactions: Flow<List<MessageReaction>>, pinnedId: String? = null, onDismiss: () -> Unit
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
        if (m.kind == MessageKind.Text || m.kind == MessageKind.Reply) ListRow("Forward", onClick = { actions.onForward(m); onDismiss() })
        if (isAdmin) ListRow(if (pinnedId == m.id) "Unpin" else "Pin to top", onClick = { if (pinnedId == m.id) actions.onUnpin() else actions.onPin(m); onDismiss() })
        if (!mine) ListRow("Report", onClick = { actions.onReport(m); onDismiss() })
        if (mine || isAdmin) ListRow("Delete", onClick = { actions.onDelete(m); onDismiss() })
    }
}

/** "@Name" in a message, highlighted when it names a member. */
@Composable
private fun mentionText(text: String, members: List<Member>): androidx.compose.ui.text.AnnotatedString {
    val names = members.map { it.displayName }.filter { it.isNotBlank() }.sortedByDescending { it.length }
    val accent = MM.colors.accent
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val at = text.indexOf('@', i)
            if (at < 0) { append(text.substring(i)); break }
            append(text.substring(i, at))
            val hit = names.firstOrNull { text.regionMatches(at + 1, it, 0, it.length, ignoreCase = true) }
            if (hit != null) {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = accent))
                append(text.substring(at, at + 1 + hit.length))
                pop()
                i = at + 1 + hit.length
            } else {
                append("@")
                i = at + 1
            }
        }
    }
}

private fun typingText(names: List<String>): String = when (names.size) {
    1 -> "${names[0]} is typing…"
    2 -> "${names[0]} and ${names[1]} are typing…"
    else -> "${names.size} people are typing…"
}

