package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.craftflowtechnologies.meetingmind.core.circles2.CHAT_EMOJI
import com.craftflowtechnologies.meetingmind.core.circles2.ChainState
import com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.MediaKind
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.MessageKind
import com.craftflowtechnologies.meetingmind.core.circles2.MessageReaction
import com.craftflowtechnologies.meetingmind.core.circles2.PollState
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
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
    /** A photo, video, file or voice note from the device, sent as a message. */
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
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val file = readPicked(context, uri)
            if (file == null) actions.onNotice("That video couldn't be read or exceeds 50 MB.") else actions.onSendAttachment(MediaKind.Video, file)
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
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.xs),
                shape = RoundedCornerShape(14.dp),
                color = CirclesPalette.WarmBg,
                border = BorderStroke(1.dp, CirclesPalette.WarmBorder)
            ) {
                Row(
                    Modifier.padding(horizontal = MM.space.m, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📌", fontSize = 15.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Pinned by ${p.authorName}", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = CirclesPalette.WarmInk)
                        Text((p.card?.text ?: p.text).ifBlank { "Pinned message" }, style = MM.type.secondary, color = MM.colors.ink, maxLines = 2)
                    }
                    if (state.isAdmin) IconButton(onClick = actions.onUnpin) { Icon(Icons.Rounded.Close, "Unpin", tint = CirclesPalette.WarmInk) }
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
            Text(typingText(state.typing), style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.padding(horizontal = MM.space.l, vertical = 2.dp))
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
            onAttachVideo = { pickVideo.launch("video/*") },
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
            m, mine = m.authorUid == state.myUid, isAdmin = state.isAdmin, canModerate = state.canModerate || state.isAdmin, actions = actions,
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
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MM.colors.surface,
            border = BorderStroke(1.dp, MM.colors.line)
        ) {
            Text(
                label,
                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = MM.colors.inkSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun UnreadDivider(count: Int) {
    Text(
        if (count == 1) "1 new message" else "$count new messages",
        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.Accent, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs)
    )
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun MessageItem(m: ChatMessage, state: ChatUiState, now: Long, actions: ChatActions, continued: Boolean = false, seen: Int = 0, onLongPress: () -> Unit) {
    val c = MM.colors
    val mine = m.authorUid == state.myUid
    val isAccentBubble = mine && (m.kind == MessageKind.Text || m.kind == MessageKind.Reply || m.kind == MessageKind.Image)
    val bubbleBg = if (isAccentBubble) CirclesPalette.Accent else c.surface
    val bubbleFg = if (isAccentBubble) CirclesPalette.White else c.ink
    val bubbleShape = if (mine) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

    when {
        m.deleted -> Text(
            "Message removed", style = MM.type.caption.copy(fontStyle = FontStyle.Italic), color = c.inkMuted,
            modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs)
        )
        m.kind == MessageKind.Celebration -> CelebrationMessage(m, onLongPress)
        else -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!mine) {
                if (!continued) {
                    val authorMember = state.members.firstOrNull { it.uid == m.authorUid }
                    MemberAvatar(circleId = state.circleId, uid = m.authorUid, name = m.authorName, avatarVersion = authorMember?.avatarVersion, size = 32.dp)
                } else {
                    Spacer(Modifier.width(32.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                if (!continued) {
                    Text(
                        (if (mine) "You" else m.authorName) + " · " + relativeTime(now, m.createdAt) + (if (m.editedAt != null) " · edited" else ""),
                        style = MM.type.caption, color = c.inkSecondary,
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
                    )
                }
                Surface(
                    shape = bubbleShape,
                    color = bubbleBg,
                    border = if (isAccentBubble) null else BorderStroke(1.dp, c.line),
                    modifier = Modifier.widthIn(max = MM.space.xxl * 10).combinedClickable(
                        onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Message actions"
                    )
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        val quoted = state.byId(m.replyTo)
                        if (m.kind == MessageKind.Reply && m.replyTo != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isAccentBubble) CirclesPalette.White.copy(alpha = 0.16f) else c.surfaceSunk
                            ) {
                                Row(Modifier.height(IntrinsicSize.Min)) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(if (isAccentBubble) CirclesPalette.White else CirclesPalette.Accent)
                                    )
                                    Text(
                                        quoted?.let { "${it.authorName}: ${if (it.deleted) "Message removed" else it.text.ifBlank { "Card" }}" } ?: "Earlier message",
                                        style = MM.type.caption,
                                        color = if (isAccentBubble) CirclesPalette.White.copy(alpha = 0.92f) else c.inkSecondary,
                                        maxLines = 2,
                                        modifier = Modifier.padding(horizontal = MM.space.s, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        when (m.kind) {
                            MessageKind.Image -> {
                                m.attachment?.let { a ->
                                    MediaImage(
                                        state.circleId, a.path,
                                        Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)),
                                        "Photo from ${m.authorName}",
                                        allowFullscreen = true
                                    )
                                }
                                if (m.text.isNotBlank()) Text(m.text, style = MM.type.body, color = bubbleFg)
                            }
                            MessageKind.Video -> {
                                m.attachment?.let { VideoBubble(state.circleId, it) }
                                if (m.text.isNotBlank()) Text(m.text, style = MM.type.body, color = bubbleFg)
                            }
                            MessageKind.Voice -> m.attachment?.let { VoiceBubble(state.circleId, it) }
                            MessageKind.File -> m.attachment?.let { FileBubble(state.circleId, it) }
                            MessageKind.Card -> m.card?.let { ChatCard(it) }
                            MessageKind.Poll -> m.pollId?.let { PollCard(m, actions) } ?: Text(m.text, style = MM.type.body, color = bubbleFg)
                            MessageKind.Chain -> m.chainId?.let { ChainCard(m, actions) } ?: Text(m.text, style = MM.type.body, color = bubbleFg)
                            else -> Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                if (m.forwardedFrom != null) Text("Forwarded", style = MM.type.caption.copy(fontStyle = FontStyle.Italic), color = if (isAccentBubble) CirclesPalette.White.copy(alpha = 0.75f) else c.inkSecondary)
                                Text(mentionText(m.text, state.members, isAccentBubble), style = MM.type.body, color = bubbleFg)
                            }
                        }
                        if (isAccentBubble) {
                            Text(
                                (if (m.editedAt != null) "Edited · " else "") + relativeTime(now, m.createdAt) + " ✓✓",
                                style = MM.type.caption.copy(fontSize = 10.sp),
                                color = CirclesPalette.White.copy(alpha = 0.72f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
                ReactionRow(m.id, actions, mine)
                if (seen > 0) Text(if (seen == 1) "Seen" else "Seen by $seen", style = MM.type.caption, color = c.inkSecondary)
            }
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
private fun ChatInput(
    state: ChatUiState,
    draft: String,
    actions: ChatActions,
    recording: Boolean,
    onAttachPhoto: () -> Unit,
    onAttachVideo: () -> Unit,
    onAttachFile: () -> Unit,
    onMicStart: () -> Unit,
    onMicStop: () -> Unit
) {
    val c = MM.colors
    var plus by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val canSend = draft.isNotBlank()
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.9f, label = "send scale")
    val inputBorder by animateColorAsState(if (focused) CirclesPalette.Accent else c.line, label = "chat input border")

    Column(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().imePadding()) {
        val bar = state.editing?.let { Triple("Editing your message", it.text.take(60), actions.onCancelEdit) }
            ?: state.forwarding?.let { Triple("Forwarding ${it.authorName}'s message", it.text.take(60), actions.onCancelForward) }
            ?: state.replyTo?.let { Triple("Replying to ${it.authorName}", it.text.take(60), actions.onCancelReply) }
        if (bar != null) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = c.surface,
                border = BorderStroke(1.dp, c.line)
            ) {
                Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(CirclesPalette.Accent))
                    Column(Modifier.weight(1f).padding(horizontal = MM.space.m, vertical = 6.dp)) {
                        Text(bar.first, style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = CirclesPalette.Accent, maxLines = 1)
                        if (bar.second.isNotBlank()) {
                            Text(bar.second, style = MM.type.caption, color = c.inkSecondary, maxLines = 1)
                        }
                    }
                    IconButton(onClick = bar.third) { Icon(Icons.Rounded.Close, "Cancel", tint = c.inkSecondary) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MM.space.m, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = c.surfaceSunk,
                    border = BorderStroke(1.dp, c.line),
                    modifier = Modifier.size(44.dp).clickable { plus = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, "More options", tint = CirclesPalette.Accent, modifier = Modifier.size(22.dp))
                    }
                }
                DropdownMenu(plus, { plus = false }, containerColor = c.surfaceRaised) {
                    PlusItem("Send a photo") { plus = false; onAttachPhoto() }
                    PlusItem("Send a video") { plus = false; onAttachVideo() }
                    PlusItem("Send a PDF") { plus = false; onAttachFile() }
                    PlusItem("Start a poll") { plus = false; actions.onNewPoll() }
                    PlusItem("Share a card") { plus = false; actions.onShareCard() }
                    PlusItem("Start a prayer chain") { plus = false; actions.onStartChain() }
                    PlusItem("Celebrate something") { plus = false; actions.onCelebrate() }
                }
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = c.surfaceSunk,
                border = BorderStroke(if (focused) 1.5.dp else 1.dp, inputBorder),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    if (draft.isEmpty()) {
                        Text("Message…", style = MM.type.body, color = c.inkMuted)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = actions.onDraft,
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(CirclesPalette.Accent),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                    )
                }
            }
            if (canSend) {
                Surface(
                    shape = CircleShape,
                    color = CirclesPalette.Accent,
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = actions.onSend)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Send, "Send", tint = CirclesPalette.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = if (recording) CirclesPalette.RoseBg else c.surfaceSunk,
                    border = BorderStroke(1.dp, if (recording) CirclesPalette.RoseInk else c.line),
                    modifier = Modifier.size(44.dp).clickable(onClick = if (recording) onMicStop else onMicStart)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (recording) "Stop recording" else "Record voice note",
                            tint = if (recording) CirclesPalette.RoseInk else CirclesPalette.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlusItem(text: String, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text, style = MM.type.body, color = MM.colors.ink) }, onClick = onClick)

/** Long-press sheet (`message_actions.html`): circular emoji bar + icon action rows + destructive Delete. */
@Composable
fun MessageActionsSheet(
    m: ChatMessage,
    mine: Boolean,
    isAdmin: Boolean,
    actions: ChatActions,
    reactions: Flow<List<MessageReaction>>,
    pinnedId: String? = null,
    canModerate: Boolean = isAdmin,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val current by remember(m.id) { reactions }.collectAsState(emptyList())
    val text = m.card?.text ?: m.text
    CircleSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CHAT_EMOJI.forEach { e ->
                val selected = current.any { it.mine && it.emoji == e }
                Surface(
                    shape = CircleShape,
                    color = if (selected) CirclesPalette.AccentSoft else MM.colors.surfaceSunk,
                    border = if (selected) BorderStroke(1.5.dp, CirclesPalette.Accent) else null,
                    modifier = Modifier.size(44.dp).clickable { actions.onToggleReaction(m.id, e, current); onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 20.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = MM.colors.line)
        ActionSheetRow(Icons.Rounded.Reply, "Reply") { actions.onReply(m); onDismiss() }
        if (text.isNotBlank()) ActionSheetRow(Icons.Rounded.ContentCopy, "Copy text") { clipboard.setText(AnnotatedString(text)); onDismiss() }
        if (mine && (m.kind == MessageKind.Text || m.kind == MessageKind.Reply)) ActionSheetRow(Icons.Rounded.Edit, "Edit") { actions.onEdit(m); onDismiss() }
        if (m.kind == MessageKind.Text || m.kind == MessageKind.Reply) ActionSheetRow(Icons.Rounded.Forward, "Forward") { actions.onForward(m); onDismiss() }
        if (isAdmin) ActionSheetRow(Icons.Rounded.PushPin, if (pinnedId == m.id) "Unpin" else "Pin to top") { if (pinnedId == m.id) actions.onUnpin() else actions.onPin(m); onDismiss() }
        if (!mine) ActionSheetRow(Icons.Rounded.Flag, "Report") { actions.onReport(m); onDismiss() }
        if (mine || canModerate) {
            ActionSheetRow(Icons.Rounded.DeleteOutline, "Delete", destructive = true) { actions.onDelete(m); onDismiss() }
        }
    }
}

@Composable
private fun ActionSheetRow(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val fg = if (destructive) CirclesPalette.RoseInk else MM.colors.ink
    val iconBg = if (destructive) CirclesPalette.RoseBg else MM.colors.surfaceSunk
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = iconBg, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            }
        }
        Text(label, style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Medium), color = fg)
    }
}

/** "@Name" in a message, highlighted when it names a member. */
@Composable
private fun mentionText(text: String, members: List<Member>, onAccent: Boolean = false): androidx.compose.ui.text.AnnotatedString {
    val names = members.map { it.displayName }.filter { it.isNotBlank() }.sortedByDescending { it.length }
    val highlight = if (onAccent) CirclesPalette.White else CirclesPalette.Accent
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val at = text.indexOf('@', i)
            if (at < 0) { append(text.substring(i)); break }
            append(text.substring(i, at))
            val hit = names.firstOrNull { text.regionMatches(at + 1, it, 0, it.length, ignoreCase = true) }
            if (hit != null) {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = highlight))
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

