package com.craftflowtechnologies.meetingmind.feature.circles2

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.CHAT_EMOJI
import com.craftflowtechnologies.meetingmind.core.circles2.ChainState
import com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.MediaKind
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.MessageKind
import com.craftflowtechnologies.meetingmind.core.circles2.MessageReaction
import com.craftflowtechnologies.meetingmind.core.circles2.PollState
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    val onNotice: (String) -> Unit = {},
    val onPromoteToPost: (ChatMessage, PostType) -> Unit = { _, _ -> }
) { companion object { val None = ChatActions() } }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatTab(
    state: ChatUiState,
    draft: String,
    now: Long,
    actions: ChatActions,
    modifier: Modifier = Modifier,
    isFaith: Boolean = true,
    space: String = "faith"
) {
    val listState = rememberLazyListState()
    var sheetFor by remember { mutableStateOf<ChatMessage?>(null) }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var stagedAttachment by remember { mutableStateOf<Pair<MediaKind, PickedFile>?>(null) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val file = readPickedAsync(context, uri)
                if (file == null) actions.onNotice("That photo couldn't be read.") else stagedAttachment = Pair(MediaKind.Image, file)
            }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val file = readPickedAsync(context, uri)
                if (file == null) {
                    actions.onNotice("That video couldn't be read or exceeds 50 MB.")
                } else if (file.name.orEmpty().endsWith(".mov", ignoreCase = true) || file.name.orEmpty().endsWith(".3gp", ignoreCase = true) || file.mime.contains("quicktime") || file.mime.contains("3gpp")) {
                    actions.onNotice("MOV and 3GPP formats are not supported yet. Please select an MP4 or WebM video.")
                } else {
                    stagedAttachment = Pair(MediaKind.Video, file)
                }
            }
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val file = readPickedAsync(context, uri)
                if (file == null) actions.onNotice("That file couldn't be read.") else stagedAttachment = Pair(MediaKind.File, file)
            }
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
                shape = MM.radius.card,
                color = CirclesPalette.WarmBg,
                border = BorderStroke(MMSize.hairline, CirclesPalette.WarmBorder)
            ) {
                Row(
                    Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📌", style = MM.type.body)
                    Column(Modifier.weight(1f)) {
                        Text("Pinned by ${p.authorName}", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = CirclesPalette.WarmInk)
                        Text((p.card?.text ?: p.text).ifBlank { "Pinned message" }, style = MM.type.secondary, color = MM.colors.ink, maxLines = 2)
                    }
                    if (state.isAdmin) IconButton(onClick = actions.onUnpin) { Icon(Icons.Rounded.Close, "Unpin", tint = CirclesPalette.WarmInk) }
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MM.colors.canvas)
        ) {
            if (state.messages.isEmpty() && !state.loading) {
                val emptyBody = when (space) {
                    "work" -> "Say hello, share an update, or start a poll to align with the team."
                    "learning", "study" -> "Say hello, ask a study question, or start a poll to schedule a study session."
                    "faith" -> "Say hello, ask a question, or start a poll to pick a night for your next meeting."
                    else -> "Say hello, share a thought, or start a poll to pick a time for your next hangout."
                }
                Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        title = "No messages yet",
                        body = emptyBody,
                        modifier = Modifier
                            .padding(horizontal = MM.space.l)
                            .align(Alignment.Center)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Messages are oldest first. A day label starts each day; a divider marks where new messages begin.
                    val firstNew = if (state.unread > 0) state.messages.indexOfFirst { it.createdAt > state.readAt && it.authorUid != state.myUid && !it.deleted } else -1
                    val lastOwn = state.messages.indexOfLast { it.authorUid == state.myUid && !it.deleted }
                    itemsIndexed(state.messages, key = { _, m -> m.id }) { i, m ->
                        val prev = state.messages.getOrNull(i - 1)
                        val isNewDay = prev == null || !sameDay(prev.createdAt, m.createdAt)
                        val isUnread = (i == firstNew)
                        val isContinued = prev != null && continues(prev, m) && !isNewDay && !isUnread
                        val topSpacing = when {
                            prev == null -> 0.dp
                            isNewDay || isUnread -> 10.dp
                            isContinued -> 2.dp
                            else -> 10.dp
                        }
                        val highlightOverlay by animateColorAsState(
                            targetValue = if (m.id == highlightedId) MM.colors.accentWash.copy(alpha = 0.35f) else Color.Transparent,
                            animationSpec = tween(300),
                            label = "highlight_${m.id}"
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .background(highlightOverlay, shape = MM.radius.card)
                                .padding(top = topSpacing)
                        ) {
                            if (isNewDay) {
                                DayDivider(dayLabel(m.createdAt, now))
                                Spacer(Modifier.height(4.dp))
                            }
                            if (isUnread) {
                                UnreadDivider(state.unread)
                                Spacer(Modifier.height(4.dp))
                            }
                            // "Seen by" is shown under my latest message: members who have read the chat since it was sent.
                            val seen = if (i == lastOwn) state.members.count { it.uid != m.authorUid && it.lastReadAt > 0L && it.lastReadAt >= m.createdAt } else 0
                            MessageItem(
                                m = m,
                                state = state,
                                now = now,
                                actions = actions,
                                continued = isContinued,
                                seen = seen,
                                isHighlighted = (m.id == highlightedId),
                                onScrollToMessage = { targetId ->
                                    val targetIdx = state.messages.indexOfFirst { it.id == targetId }
                                    if (targetIdx >= 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(targetIdx)
                                            highlightedId = targetId
                                            delay(1200L)
                                            if (highlightedId == targetId) highlightedId = null
                                        }
                                    }
                                },
                                onLongPress = { sheetFor = m }
                            )
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = listState.canScrollForward,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = MM.space.l, bottom = MM.space.m)
            ) {
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            if (state.messages.isNotEmpty()) {
                                listState.animateScrollToItem(state.messages.lastIndex)
                            }
                        }
                    },
                    shape = MM.radius.pill,
                    color = MM.colors.surfaceRaised,
                    border = BorderStroke(MMSize.hairline, MM.colors.lineSoft),
                    shadowElevation = MM.elevation.floating,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Jump to latest",
                            tint = MM.colors.ink,
                            modifier = Modifier.size(MMSize.icon)
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.typing.isNotEmpty(),
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MM.colors.canvas)
            ) {
                TypingIndicatorBubble(names = state.typing)
            }
        }
        if (state.uploading) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MM.colors.canvas)
                    .padding(horizontal = MM.space.l)
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uploading…", style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.weight(1f))
                    TextAction("Cancel", actions.onCancelUpload)
                }
            }
        }
        ChatInput(
            state = state,
            draft = draft,
            actions = actions,
            recording = recording,
            isFaith = isFaith && space == "faith",
            space = space,
            stagedAttachment = stagedAttachment,
            onSampleAmplitude = { recorder.amplitude() },
            onClearAttachment = { stagedAttachment = null },
            onAttachPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAttachVideo = { pickVideo.launch("video/*") },
            onAttachFile = { pickFile.launch("application/pdf") },
            onMicStart = { micPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
            onMicStop = {
                val file = recorder.stop()
                recording = false
                if (file == null) actions.onNotice("That was too short to keep.") else actions.onSendAttachment(MediaKind.Voice, file)
            },
            onMicCancel = {
                recorder.cancel()
                recording = false
            }
        )
    }
    sheetFor?.let { m ->
        MessageActionsSheet(
            m, mine = m.authorUid == state.myUid, isAdmin = state.isAdmin, canModerate = state.canModerate || state.isAdmin, actions = actions,
            reactions = actions.reactionsFor(m.id), pinnedId = state.pinned?.id, space = space, onDismiss = { sheetFor = null }
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
            shape = MM.radius.pill,
            color = MM.colors.surface,
            border = BorderStroke(MMSize.hairline, MM.colors.lineSoft)
        ) {
            Text(
                label,
                style = MM.type.overline,
                color = MM.colors.inkSecondary,
                modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
            )
        }
    }
}

@Composable
private fun UnreadDivider(count: Int) {
    Text(
        if (count == 1) "1 new message" else "$count new messages",
        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = MM.colors.accent,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs)
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageItem(
    m: ChatMessage,
    state: ChatUiState,
    now: Long,
    actions: ChatActions,
    continued: Boolean = false,
    seen: Int = 0,
    isHighlighted: Boolean = false,
    onScrollToMessage: (String) -> Unit = {},
    onLongPress: () -> Unit
) {
    val c = MM.colors
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxDragPx = with(density) { 64.dp.toPx() }
    val thresholdPx = with(density) { 48.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var hapticTriggered by remember { mutableStateOf(false) }

    val mine = m.authorUid == state.myUid
    val isAccentBubble = mine && (m.kind == MessageKind.Text || m.kind == MessageKind.Reply || m.kind == MessageKind.Image || m.kind == MessageKind.Video || m.kind == MessageKind.Voice)
    val normalBubbleBg = if (isAccentBubble) c.accent else c.surface
    val targetBubbleBg = if (isHighlighted) {
        if (isAccentBubble) c.accent.copy(alpha = 0.75f) else c.accentWash
    } else normalBubbleBg
    val bubbleBg by animateColorAsState(
        targetValue = targetBubbleBg,
        animationSpec = tween(300),
        label = "bubbleBg_${m.id}"
    )
    val bubbleFg = if (isAccentBubble) c.onAccent else c.ink
    val bubbleShape = if (mine) {
        RoundedCornerShape(topStart = MM.space.l, topEnd = MM.space.l, bottomStart = MM.space.l, bottomEnd = MM.space.xs)
    } else {
        RoundedCornerShape(topStart = MM.space.xs, topEnd = MM.space.l, bottomStart = MM.space.l, bottomEnd = MM.space.l)
    }

    val swipeModifier = if (m.deleted) Modifier else Modifier.pointerInput(m.id) {
        detectHorizontalDragGestures(
            onDragStart = { hapticTriggered = false },
            onDragEnd = {
                if (offsetX.value >= thresholdPx) {
                    actions.onReply(m)
                }
                coroutineScope.launch {
                    offsetX.animateTo(0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                }
                hapticTriggered = false
            },
            onDragCancel = {
                coroutineScope.launch {
                    offsetX.animateTo(0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                }
                hapticTriggered = false
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                val newOffset = (offsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                if (newOffset >= thresholdPx && !hapticTriggered) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    hapticTriggered = true
                } else if (newOffset < thresholdPx && hapticTriggered) {
                    hapticTriggered = false
                }
                coroutineScope.launch {
                    offsetX.snapTo(newOffset)
                }
            }
        )
    }

    when {
        m.deleted -> Text(
            "Message removed",
            style = MM.type.caption.copy(fontStyle = FontStyle.Italic),
            color = c.inkMuted,
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
                    val avatarVersion = state.members.firstOrNull { it.uid == m.authorUid }?.avatarVersion
                    MemberAvatar(state.circleId, m.authorUid, m.authorName, avatarVersion, size = 32.dp)
                } else {
                    Spacer(Modifier.width(32.dp))
                }
                Spacer(Modifier.width(MM.space.s))
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                if (!mine && !continued) {
                    val authorColor = if (c.isDark) c.accent else CirclesPalette.avatarColors(m.authorName).second
                    Text(
                        text = m.authorName,
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = authorColor,
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier.then(swipeModifier),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (offsetX.value > 0f) {
                        val progress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
                        Surface(
                            shape = CircleShape,
                            color = if (offsetX.value >= thresholdPx) c.accent else c.accentWash,
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer {
                                    scaleX = 0.5f + 0.5f * progress
                                    scaleY = 0.5f + 0.5f * progress
                                    alpha = progress
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Reply,
                                    contentDescription = "Reply",
                                    tint = if (offsetX.value >= thresholdPx) c.onAccent else c.accent,
                                    modifier = Modifier.size(MMSize.iconSmall)
                                )
                            }
                        }
                    }
                    Surface(
                        shape = bubbleShape,
                        color = bubbleBg,
                        border = if (isAccentBubble) null else BorderStroke(MMSize.hairline, c.lineSoft),
                        modifier = Modifier
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .widthIn(max = MM.space.xxl * 10)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = onLongPress,
                                onLongClickLabel = "Message actions"
                            )
                    ) {
                        if (m.kind == MessageKind.Image || m.kind == MessageKind.Video) {
                            Column {
                                val quoted = state.byId(m.replyTo)
                                if (m.replyTo != null && quoted != null) {
                                    Surface(
                                        shape = MM.radius.small,
                                        color = if (isAccentBubble) c.onAccent.copy(alpha = 0.16f) else c.surfaceSunk,
                                        modifier = Modifier
                                            .clickable { onScrollToMessage(quoted.id) }
                                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                                    ) {
                                        Row(Modifier.height(IntrinsicSize.Min)) {
                                            Box(
                                                Modifier
                                                    .width(3.dp)
                                                    .fillMaxHeight()
                                                    .background(if (isAccentBubble) c.onAccent else c.accent)
                                            )
                                            Text(
                                                "${quoted.authorName}: ${if (quoted.deleted) "Message removed" else quoted.text.ifBlank { "Card" }}",
                                                style = MM.type.caption,
                                                color = if (isAccentBubble) c.onAccent.copy(alpha = 0.92f) else c.inkSecondary,
                                                maxLines = 2,
                                                modifier = Modifier.padding(horizontal = MM.space.s, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                                m.attachment?.let { a ->
                                    if (m.kind == MessageKind.Image) {
                                        if (m.text.isBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 260.dp)
                                            ) {
                                                MediaImage(
                                                    circleId = state.circleId,
                                                    path = a.path,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentDescription = "Photo from ${m.authorName}",
                                                    allowFullscreen = true
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(MM.space.s)
                                                        .clip(MM.radius.pill)
                                                        .background(c.scrim.copy(alpha = 0.65f))
                                                        .padding(horizontal = MM.space.s, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = (if (m.editedAt != null) "Edited · " else "") + relativeTime(now, m.createdAt),
                                                        style = MM.type.caption,
                                                        color = FixedWhite
                                                    )
                                                }
                                            }
                                        } else {
                                            MediaImage(
                                                circleId = state.circleId,
                                                path = a.path,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 260.dp),
                                                contentDescription = "Photo from ${m.authorName}",
                                                allowFullscreen = true
                                            )
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                                            ) {
                                                Text(m.text, style = MM.type.body, color = bubbleFg)
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = (if (m.editedAt != null) "Edited · " else "") + relativeTime(now, m.createdAt),
                                                    style = MM.type.caption,
                                                    color = if (isAccentBubble) c.onAccent.copy(alpha = 0.75f) else c.inkMuted,
                                                    modifier = Modifier.align(Alignment.End)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            VideoBubble(state.circleId, a)
                                        }
                                        if (m.text.isNotBlank()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                                            ) {
                                                Text(m.text, style = MM.type.body, color = bubbleFg)
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = (if (m.editedAt != null) "Edited · " else "") + relativeTime(now, m.createdAt),
                                                    style = MM.type.caption,
                                                    color = if (isAccentBubble) c.onAccent.copy(alpha = 0.75f) else c.inkMuted,
                                                    modifier = Modifier.align(Alignment.End)
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)
                                            ) {
                                                Text(
                                                    text = (if (m.editedAt != null) "Edited · " else "") + relativeTime(now, m.createdAt),
                                                    style = MM.type.caption,
                                                    color = if (isAccentBubble) c.onAccent.copy(alpha = 0.75f) else c.inkMuted,
                                                    modifier = Modifier.align(Alignment.CenterEnd)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
                            ) {
                                val quoted = state.byId(m.replyTo)
                                if (m.kind == MessageKind.Reply && m.replyTo != null) {
                                    Surface(
                                        shape = MM.radius.small,
                                        color = if (isAccentBubble) c.onAccent.copy(alpha = 0.16f) else c.surfaceSunk,
                                        modifier = Modifier
                                            .clickable { onScrollToMessage(m.replyTo) }
                                            .padding(bottom = 6.dp)
                                    ) {
                                        Row(Modifier.height(IntrinsicSize.Min)) {
                                            Box(
                                                Modifier
                                                    .width(3.dp)
                                                    .fillMaxHeight()
                                                    .background(if (isAccentBubble) c.onAccent else c.accent)
                                            )
                                            Text(
                                                quoted?.let { "${it.authorName}: ${if (it.deleted) "Message removed" else it.text.ifBlank { "Card" }}" } ?: "Earlier message",
                                                style = MM.type.caption,
                                                color = if (isAccentBubble) c.onAccent.copy(alpha = 0.92f) else c.inkSecondary,
                                                maxLines = 2,
                                                modifier = Modifier.padding(horizontal = MM.space.s, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                                when (m.kind) {
                                    MessageKind.Voice -> m.attachment?.let { VoiceBubble(state.circleId, it, onAccent = isAccentBubble) }
                                    MessageKind.File -> m.attachment?.let { FileBubble(state.circleId, it) }
                                    MessageKind.Card -> m.card?.let { ChatCard(it) }
                                    MessageKind.Poll -> m.pollId?.let { PollCard(m, actions) } ?: Text(m.text, style = MM.type.body, color = bubbleFg)
                                    MessageKind.Chain -> m.chainId?.let { ChainCard(m, actions) } ?: Text(m.text, style = MM.type.body, color = bubbleFg)
                                    else -> Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                        if (m.forwardedFrom != null) {
                                            Text(
                                                "Forwarded",
                                                style = MM.type.caption.copy(fontStyle = FontStyle.Italic),
                                                color = if (isAccentBubble) c.onAccent.copy(alpha = 0.75f) else c.inkSecondary
                                            )
                                        }
                                        Text(mentionText(m.text, state.members, isAccentBubble), style = MM.type.body, color = bubbleFg)
                                    }
                                }
                                val isTextOrReplyOrMedia = m.kind == MessageKind.Text || m.kind == MessageKind.Reply ||
                                    m.kind == MessageKind.Voice || m.kind == MessageKind.File
                                if (isTextOrReplyOrMedia) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = (if (m.editedAt != null) "Edited · " else "") + relativeTime(now, m.createdAt),
                                        style = MM.type.caption,
                                        color = if (isAccentBubble) c.onAccent.copy(alpha = 0.75f) else c.inkMuted,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
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
    Row(
        modifier = Modifier.padding(top = MM.space.xs),
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        reactions.forEach { r ->
            val bg = if (r.mine) MM.colors.accentWash else MM.colors.surface
            val border = if (r.mine) BorderStroke(1.dp, MM.colors.accent) else BorderStroke(MMSize.hairline, MM.colors.line)
            val textColor = if (r.mine) MM.colors.accent else MM.colors.inkSecondary
            Surface(
                onClick = { actions.onToggleReaction(messageId, r.emoji, reactions) },
                shape = MM.radius.pill,
                color = bg,
                border = border
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${r.emoji} ${r.count}",
                        style = MM.type.caption.copy(fontWeight = if (r.mine) FontWeight.SemiBold else FontWeight.Normal),
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInput(
    state: ChatUiState,
    draft: String,
    actions: ChatActions,
    recording: Boolean,
    isFaith: Boolean = true,
    space: String = "faith",
    stagedAttachment: Pair<MediaKind, PickedFile>?,
    onSampleAmplitude: () -> Float = { 0f },
    onClearAttachment: () -> Unit,
    onAttachPhoto: () -> Unit,
    onAttachVideo: () -> Unit,
    onAttachFile: () -> Unit,
    onMicStart: () -> Unit,
    onMicStop: () -> Unit,
    onMicCancel: () -> Unit = {}
) {
    val c = MM.colors
    var plus by remember { mutableStateOf(false) }

    val mentionQuery = remember(draft) {
        val atIndex = draft.lastIndexOf('@')
        if (atIndex >= 0) {
            val query = draft.substring(atIndex + 1)
            if (!query.any { it.isWhitespace() } && (atIndex == 0 || draft[atIndex - 1].isWhitespace())) {
                query
            } else null
        } else null
    }

    val mentionMatches = remember(mentionQuery, state.members, state.myUid) {
        if (mentionQuery != null) {
            state.members.filter {
                it.uid != state.myUid && it.displayName.isNotBlank() &&
                    (mentionQuery.isEmpty() || it.displayName.contains(mentionQuery, ignoreCase = true))
            }.take(4)
        } else emptyList()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding()
            .imePadding()
    ) {
        HorizontalDivider(color = c.lineSoft)

        val bar = state.editing?.let { Triple("Editing your message", it.text.take(60), actions.onCancelEdit) }
            ?: state.forwarding?.let { Triple("Forwarding ${it.authorName}'s message", it.text.take(60), actions.onCancelForward) }
            ?: state.replyTo?.let { Triple("Replying to ${it.authorName}", it.text.take(60), actions.onCancelReply) }
        AnimatedVisibility(
            visible = bar != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (bar != null) {
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MM.space.m, vertical = MM.space.xs),
                    shape = MM.radius.card,
                    color = c.surfaceRaised,
                    border = BorderStroke(MMSize.hairline, c.lineSoft)
                ) {
                    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(c.accent))
                        Column(Modifier.weight(1f).padding(horizontal = MM.space.m, vertical = 6.dp)) {
                            Text(bar.first, style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent, maxLines = 1)
                            if (bar.second.isNotBlank()) {
                                Text(bar.second, style = MM.type.caption, color = c.inkSecondary, maxLines = 1)
                            }
                        }
                        IconButton(onClick = bar.third) { Icon(Icons.Rounded.Close, "Cancel", tint = c.inkSecondary) }
                    }
                }
            }
        }

        // Staged attachment preview card
        AnimatedVisibility(
            visible = stagedAttachment != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (stagedAttachment != null) {
                val (kind, file) = stagedAttachment
                val thumbBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, stagedAttachment) {
                    value = if (kind == MediaKind.Image) {
                        withContext(Dispatchers.Default) {
                            BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size)?.asImageBitmap()
                        }
                    } else null
                }
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MM.space.m, vertical = MM.space.xs),
                    shape = MM.radius.card,
                    color = c.surfaceRaised,
                    border = BorderStroke(MMSize.hairline, c.accent.copy(alpha = 0.4f))
                ) {
                    Row(
                        Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        if (thumbBitmap != null) {
                            Image(
                                bitmap = thumbBitmap!!,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(MM.radius.small),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                when (kind) {
                                    MediaKind.Image -> Icons.Rounded.Image
                                    MediaKind.Video -> Icons.Rounded.Videocam
                                    else -> Icons.Rounded.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(MMSize.icon)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(file.name.orEmpty().ifBlank { "Attachment" }, style = MM.type.bodyStrong, color = c.ink, maxLines = 1)
                            Text(
                                listOfNotNull(file.durationMs?.takeIf { it > 0 }?.let(::formatDuration), formatBytes(file.bytes.size.toLong())).joinToString(" · "),
                                style = MM.type.caption,
                                color = c.inkSecondary
                            )
                        }
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(MMSize.icon)) {
                            Icon(Icons.Rounded.Close, "Remove attachment", tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                        }
                    }
                }
            }
        }

        // @mention Autocomplete suggestions
        AnimatedVisibility(
            visible = mentionMatches.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.m, vertical = MM.space.xs),
                shape = MM.radius.card,
                color = c.surfaceRaised,
                border = BorderStroke(MMSize.hairline, c.lineSoft)
            ) {
                Column {
                    mentionMatches.forEachIndexed { index, member ->
                        if (index > 0) HorizontalDivider(color = c.lineSoft)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val atIndex = draft.lastIndexOf('@')
                                    if (atIndex >= 0) {
                                        val newDraft = draft.substring(0, atIndex) + "@${member.displayName} "
                                        actions.onDraft(newDraft)
                                    }
                                }
                                .padding(horizontal = MM.space.m, vertical = MM.space.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                        ) {
                            MemberAvatar(
                                circleId = state.circleId,
                                uid = member.uid,
                                name = member.displayName,
                                avatarVersion = member.avatarVersion,
                                size = 28.dp
                            )
                            Text(
                                member.displayName,
                                style = MM.type.bodyStrong,
                                color = c.ink,
                                modifier = Modifier.weight(1f)
                            )
                            member.title?.let { title ->
                                if (title.isNotBlank()) {
                                    Text(title, style = MM.type.caption, color = c.inkSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (recording) {
            var elapsedSeconds by remember(recording) { mutableIntStateOf(0) }
            LaunchedEffect(recording) {
                elapsedSeconds = 0
                while (true) {
                    kotlinx.coroutines.delay(1000L)
                    elapsedSeconds++
                }
            }
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            val timerText = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

            val infiniteTransition = rememberInfiniteTransition(label = "recording pulse")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            val amplitudes = remember { mutableStateListOf<Float>() }
            LaunchedEffect(recording) {
                amplitudes.clear()
                repeat(28) { amplitudes.add(0.08f) }
                while (true) {
                    kotlinx.coroutines.delay(70L)
                    val sample = onSampleAmplitude().coerceIn(0f, 1f)
                    if (amplitudes.size >= 28) {
                        amplitudes.removeAt(0)
                    }
                    amplitudes.add(sample)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.m, vertical = MM.space.s)
                    .heightIn(min = 38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Box(
                    Modifier
                        .size(MM.space.s)
                        .background(c.recording.copy(alpha = dotAlpha), CircleShape)
                )
                Text(timerText, style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = c.ink)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .padding(horizontal = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    amplitudes.forEach { amp ->
                        val barHeight = (4.dp + (20.dp * amp)).coerceIn(4.dp, 24.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(barHeight)
                                .clip(MM.radius.pill)
                                .background(c.accent.copy(alpha = (0.35f + amp * 0.65f).coerceIn(0.2f, 1f)))
                        )
                    }
                }
                IconButton(onClick = onMicCancel) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Cancel recording",
                        tint = c.inkSecondary
                    )
                }
                IconButton(
                    onClick = onMicStop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = c.onAccent,
                        modifier = Modifier.size(MMSize.iconSmall)
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.m, vertical = MM.space.s),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Surface(
                    onClick = { plus = true },
                    shape = MM.radius.pill,
                    color = c.surfaceSunk,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "More: photo, video, PDF, poll, card, prayer chain, celebrate",
                            tint = c.accent,
                            modifier = Modifier.size(MMSize.icon)
                        )
                    }
                }

                val placeholderText = if (stagedAttachment != null) "Add a caption…" else "Message…"
                Surface(
                    shape = MM.radius.card,
                    color = c.surfaceSunk,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                text = placeholderText,
                                style = MM.type.body,
                                color = c.inkMuted
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = actions.onDraft,
                            textStyle = MM.type.body.copy(color = c.ink),
                            cursorBrush = SolidColor(c.accent),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                val canSend = draft.isNotBlank() || stagedAttachment != null
                AnimatedContent(
                    targetState = canSend,
                    transitionSpec = {
                        (scaleIn(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150)))
                            .togetherWith(scaleOut(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150)))
                    },
                    label = "mic_send_toggle"
                ) { sendable ->
                    if (!sendable) {
                        Surface(
                            onClick = onMicStart,
                            shape = MM.radius.pill,
                            color = c.accentWash,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = "Record a voice note",
                                    tint = c.accent,
                                    modifier = Modifier.size(MMSize.iconSmall)
                                )
                            }
                        }
                    } else {
                        Surface(
                            onClick = {
                                if (stagedAttachment != null) {
                                    val (kind, file) = stagedAttachment
                                    onClearAttachment()
                                    actions.onSendAttachment(kind, file)
                                } else {
                                    actions.onSend()
                                }
                            },
                            shape = MM.radius.pill,
                            color = c.accent,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send",
                                    tint = c.onAccent,
                                    modifier = Modifier.size(MMSize.iconSmall)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (plus) {
            AttachmentTraySheet(
                isFaith = isFaith,
                onAttachPhoto = { plus = false; onAttachPhoto() },
                onAttachVideo = { plus = false; onAttachVideo() },
                onAttachFile = { plus = false; onAttachFile() },
                onNewPoll = { plus = false; actions.onNewPoll() },
                onShareCard = { plus = false; actions.onShareCard() },
                onCelebrate = { plus = false; actions.onCelebrate() },
                onStartChain = { plus = false; actions.onStartChain() },
                onDismiss = { plus = false }
            )
        }
    }
}

private class AttachmentOption(
    val label: String,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color,
    val onClick: () -> Unit
)

@Composable
private fun AttachmentTraySheet(
    isFaith: Boolean,
    onAttachPhoto: () -> Unit,
    onAttachVideo: () -> Unit,
    onAttachFile: () -> Unit,
    onNewPoll: () -> Unit,
    onShareCard: () -> Unit,
    onCelebrate: () -> Unit,
    onStartChain: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = MM.colors
    CircleSheet(onDismiss = onDismiss) {
        Text(
            "Share to circle",
            style = MM.type.title.copy(fontWeight = FontWeight.Bold),
            color = c.ink,
            modifier = Modifier.padding(bottom = MM.space.xs)
        )
        val roseBg = CirclesPalette.RoseBg
        val roseInk = CirclesPalette.RoseInk
        val greenBg = CirclesPalette.GreenBg
        val greenInk = CirclesPalette.GreenInk
        val warmBg = CirclesPalette.WarmBg
        val warmInk = CirclesPalette.WarmInk
        val items = remember(isFaith, roseBg, roseInk, greenBg, greenInk, warmBg, warmInk, c, onAttachPhoto, onAttachVideo, onAttachFile, onNewPoll, onShareCard, onCelebrate, onStartChain) {
            listOfNotNull(
                AttachmentOption("Photo", Icons.Rounded.Image, CirclesPalette.AvatarPairs[1].first, CirclesPalette.AvatarPairs[1].second, onAttachPhoto),
                AttachmentOption("Video", Icons.Rounded.Videocam, c.dangerWash, c.danger, onAttachVideo),
                AttachmentOption("PDF", Icons.Rounded.PictureAsPdf, roseBg, roseInk, onAttachFile),
                AttachmentOption("Poll", Icons.Rounded.Poll, greenBg, greenInk, onNewPoll),
                AttachmentOption("Card", Icons.Rounded.Style, c.goldWash, c.goldInk, onShareCard),
                AttachmentOption("Celebrate", Icons.Rounded.Celebration, c.accentWash, c.accent, onCelebrate),
                if (isFaith) AttachmentOption("Prayer Chain", Icons.Rounded.VolunteerActivism, warmBg, warmInk, onStartChain) else null
            )
        }

        items.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowItems.forEach { opt ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clip(MM.radius.card)
                            .clickable {
                                onDismiss()
                                opt.onClick()
                            }
                            .padding(vertical = MM.space.xs)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = opt.bg,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    opt.icon,
                                    contentDescription = opt.label,
                                    tint = opt.fg,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            opt.label,
                            style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                            color = c.ink,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(Modifier.width(72.dp))
                }
            }
        }
    }
}

@Composable
private fun TypingIndicatorBubble(
    names: List<String>,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val dot1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 0),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 140),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 280),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d3"
    )

    Surface(
        shape = MM.radius.pill,
        color = c.surfaceRaised,
        border = BorderStroke(MMSize.hairline, c.lineSoft),
        modifier = modifier.padding(horizontal = MM.space.l, vertical = MM.space.xs)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MM.space.m, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(dot1, dot2, dot3).forEach { offset ->
                    Box(
                        Modifier
                            .offset(y = offset.dp)
                            .size(6.dp)
                            .background(c.accent, CircleShape)
                    )
                }
            }
            Text(
                typingText(names),
                style = MM.type.caption,
                color = c.inkSecondary
            )
        }
    }
}

@Composable
private fun PlusItem(text: String, icon: ImageVector, onClick: () -> Unit) =
    DropdownMenuItem(
        text = { Text(text, style = MM.type.body, color = MM.colors.ink) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.iconSmall))
        },
        onClick = onClick
    )

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
    space: String = "faith",
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val current by remember(m.id) { reactions }.collectAsState(emptyList())
    val text = m.card?.text ?: m.text
    val quickEmojis = remember(space) {
        when (space) {
            "work" -> listOf("👍", "🙌", "🎯", "🚀", "💡", "🔥")
            "learning", "study" -> listOf("💡", "👏", "🎯", "📚", "👍", "🔥")
            "faith" -> CHAT_EMOJI
            else -> listOf("👍", "❤️", "😂", "🎉", "🙌", "🔥")
        }
    }
    CircleSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            quickEmojis.forEach { e ->
                val selected = current.any { it.mine && it.emoji == e }
                Surface(
                    shape = CircleShape,
                    color = if (selected) MM.colors.accentWash else MM.colors.surfaceSunk,
                    border = if (selected) BorderStroke(1.5.dp, MM.colors.accent) else null,
                    modifier = Modifier.size(44.dp).clickable { actions.onToggleReaction(m.id, e, current); onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(e, style = MM.type.title)
                    }
                }
            }
        }
        HorizontalDivider(color = MM.colors.line)
        ActionSheetRow(Icons.Rounded.Reply, "Reply") { actions.onReply(m); onDismiss() }
        if (text.isNotBlank()) ActionSheetRow(Icons.Rounded.ContentCopy, "Copy text") { clipboard.setText(AnnotatedString(text)); onDismiss() }
        if (mine && (m.kind == MessageKind.Text || m.kind == MessageKind.Reply)) ActionSheetRow(Icons.Rounded.Edit, "Edit") { actions.onEdit(m); onDismiss() }
        if (m.kind == MessageKind.Text || m.kind == MessageKind.Reply) ActionSheetRow(Icons.Rounded.Forward, "Forward") { actions.onForward(m); onDismiss() }
        if (space == "work" && !m.deleted && m.text.isNotBlank()) {
            ActionSheetRow(Icons.Rounded.CheckCircle, "Create action item") { actions.onPromoteToPost(m, PostType.Task); onDismiss() }
            ActionSheetRow(Icons.Rounded.CheckCircleOutline, "Log decision") { actions.onPromoteToPost(m, PostType.Decision); onDismiss() }
            ActionSheetRow(Icons.Rounded.WarningAmber, "Flag blocker") { actions.onPromoteToPost(m, PostType.Blocker); onDismiss() }
        }
        if (isAdmin) ActionSheetRow(Icons.Rounded.PushPin, if (pinnedId == m.id) "Unpin" else "Pin to top") { if (pinnedId == m.id) actions.onUnpin() else actions.onPin(m); onDismiss() }
        if (!mine) ActionSheetRow(Icons.Rounded.Flag, "Report") { actions.onReport(m); onDismiss() }
        if (mine || canModerate) {
            ActionSheetRow(Icons.Rounded.DeleteOutline, "Delete", destructive = true) { actions.onDelete(m); onDismiss() }
        }
    }
}

@Composable
private fun ActionSheetRow(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val fg = if (destructive) MM.colors.danger else MM.colors.ink
    val iconBg = if (destructive) MM.colors.dangerWash else MM.colors.surfaceSunk
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .clickable(onClick = onClick)
            .padding(horizontal = MM.space.s, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Surface(shape = MM.radius.small, color = iconBg, modifier = Modifier.size(34.dp)) {
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
    val highlight = if (onAccent) MM.colors.onAccent else MM.colors.accent
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
