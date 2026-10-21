package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.Comment
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** A top-level comment and the replies under it. Replies are one level deep, as the Worker and the rules require. */
private class CommentNode(val comment: Comment, val replies: List<Comment>)

/**
 * Groups a flat comment list into threads, oldest first. A reply whose parent is missing (deleted or not loaded yet)
 * is shown at the top level so it isn't lost.
 */
private fun threadsOf(comments: List<Comment>): List<CommentNode> {
    val ids = comments.mapTo(HashSet()) { it.id }
    val replies = comments.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId!! }
    return comments
        .filter { it.parentId == null || it.parentId !in ids }
        .sortedBy { it.createdAt }
        .map { CommentNode(it, replies[it.id].orEmpty().sortedBy { r -> r.createdAt }) }
}

/**
 * A post with its updates and its comments as a conversation: top-level comments, with replies nested beneath, and a
 * reply target shown above the composer. Copy is deliberately absent; people can select text where the platform allows.
 */
@Composable
fun ThreadSheet(
    circleId: String,
    post: Post?,
    thread: ThreadUiState?,
    members: List<Member>,
    myUid: String?,
    isAdmin: Boolean,
    now: Long,
    onSend: (text: String, parentId: String?) -> Unit,
    onDeleteComment: (Comment) -> Unit,
    onDismiss: () -> Unit,
    onMemberClick: ((String) -> Unit)? = null
) {
    val membersByUid = remember(members) { members.associateBy { it.uid } }
    // The reply being written. Cleared when the thread changes, so it can't leak into another post.
    var replyTarget by remember(thread?.postId) { mutableStateOf<Comment?>(null) }
    val composerFocus = remember { FocusRequester() }
    val nodes = remember(thread?.comments) { threadsOf(thread?.comments.orEmpty()) }
    val total = thread?.comments?.size ?: 0

    LaunchedEffect(replyTarget) { if (replyTarget != null) composerFocus.requestFocus() }

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (total > 0) "Comments ($total)" else "Comments",
                style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = MM.colors.ink
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }

        if (post != null) PostSummary(post, now, onMemberClick)

        thread?.failure?.let { StatusLine(StatusKind.Warning, it.message) }

        if (thread != null) {
            thread.updates.forEach { u ->
                Surface(shape = RoundedCornerShape(14.dp), color = CirclesPalette.AccentSoft, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Author update · ${relativeTime(now, u.createdAt)}",
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = CirclesPalette.AccentDeep
                        )
                        Text(u.body, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (nodes.isEmpty() && !thread.loading) {
                Text(
                    "No comments yet. Be the first to share an encouraging word or a prayer.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(vertical = MM.space.m)
                )
            } else if (nodes.isNotEmpty()) {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 400.dp)
                ) {
                    itemsIndexed(nodes, key = { _, n -> n.comment.id }) { index, node ->
                        Column(Modifier.fillMaxWidth().animateItem()) {
                            if (index > 0) HorizontalDivider(color = MM.colors.line.copy(alpha = 0.6f))
                            CommentNodeView(
                                circleId = circleId,
                                node = node,
                                membersByUid = membersByUid,
                                myUid = myUid,
                                isAdmin = isAdmin,
                                now = now,
                                onReply = { replyTarget = it },
                                onDelete = onDeleteComment,
                                onMemberClick = onMemberClick
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = replyTarget != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ReplyingChip(
                name = replyTarget?.authorName.orEmpty(),
                onCancel = { replyTarget = null }
            )
        }

        CommentComposer(
            placeholder = if (replyTarget != null) "Write a reply…" else "Write an encouraging comment…",
            focusRequester = composerFocus,
            onSend = { text ->
                val target = replyTarget
                // Replies are one level deep: a reply to a reply goes under the same top-level comment, with the name kept in the text.
                val parentId = target?.let { it.parentId ?: it.id }
                val body = if (target?.parentId != null) "@${target.authorName} $text" else text
                onSend(body, parentId)
                replyTarget = null
            }
        )
    }
}

/** The post at the top of the thread: who wrote it, its type, and its text. */
@Composable
private fun PostSummary(post: Post, now: Long, onMemberClick: ((String) -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MM.colors.surfaceSunk,
        border = BorderStroke(1.dp, MM.colors.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InitialsAvatar(
                    post.authorName ?: "Member",
                    size = 28.dp,
                    modifier = Modifier.clickable { onMemberClick?.invoke(post.authorUid ?: post.authorName.orEmpty()) }
                )
                Text(
                    post.authorName ?: "Member",
                    style = MM.type.bodyStrong.copy(fontSize = 13.sp),
                    color = MM.colors.ink,
                    modifier = Modifier.clickable { onMemberClick?.invoke(post.authorUid ?: post.authorName.orEmpty()) }
                )
                PostTypeBadge(post.type)
                Spacer(Modifier.weight(1f))
                Text(relativeTime(now, post.createdAt), style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            Text(post.body, style = MM.type.body, color = MM.colors.ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (!post.verseRef.isNullOrBlank()) {
                Text(post.verseRef, style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.WarmInk)
            }
        }
    }
}

/** One top-level comment, then its replies. Replies fold away when there are more than two, so long threads stay readable. */
@Composable
private fun CommentNodeView(
    circleId: String,
    node: CommentNode,
    membersByUid: Map<String, Member>,
    myUid: String?,
    isAdmin: Boolean,
    now: Long,
    onReply: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onMemberClick: ((String) -> Unit)?
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val id = node.comment.id
    val open = expanded[id] ?: (node.replies.size <= 2)

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        CommentRow(circleId, node.comment, membersByUid, myUid, isAdmin, now, 38.dp, onReply, onDelete, onMemberClick)
        if (node.replies.isNotEmpty()) {
            Row(Modifier.padding(start = 48.dp, top = 4.dp)) {
                TextAction(
                    if (open) "Hide replies" else if (node.replies.size == 1) "Show 1 reply" else "Show ${node.replies.size} replies",
                    { expanded[id] = !open }
                )
            }
            AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                // A thin rule down the left marks the replies as belonging to the comment above.
                Row(Modifier.padding(start = 18.dp, top = 8.dp).height(IntrinsicSize.Min)) {
                    Box(
                        Modifier.width(2.dp).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(MM.colors.line)
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        node.replies.forEach { reply ->
                            CommentRow(circleId, reply, membersByUid, myUid, isAdmin, now, 30.dp, onReply, onDelete, onMemberClick)
                        }
                    }
                }
            }
        }
    }
}

/** A comment's author, text and actions. The same row is used for top-level comments and replies, at a different avatar size. */
@Composable
private fun CommentRow(
    circleId: String,
    comment: Comment,
    membersByUid: Map<String, Member>,
    myUid: String?,
    isAdmin: Boolean,
    now: Long,
    avatarSize: Dp,
    onReply: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onMemberClick: ((String) -> Unit)?
) {
    val isMine = comment.authorUid != null && comment.authorUid == myUid
    val author = comment.authorUid?.let { membersByUid[it] }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MemberAvatar(
            circleId = circleId,
            uid = comment.authorUid,
            name = comment.authorName,
            avatarVersion = author?.avatarVersion,
            size = avatarSize,
            modifier = Modifier.clickable { onMemberClick?.invoke(comment.authorUid ?: comment.authorName) }
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    comment.authorName,
                    style = MM.type.bodyStrong.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = MM.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable { onMemberClick?.invoke(comment.authorUid ?: comment.authorName) }
                )
                if (comment.byRequester) {
                    Text("Requester", style = MM.type.caption, color = CirclesPalette.AccentDeep)
                }
                Text("· ${relativeTime(now, comment.createdAt)}", style = MM.type.caption, color = MM.colors.inkMuted)
            }
            Text(
                comment.body,
                style = MM.type.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
                color = MM.colors.ink,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Reply", { onReply(comment) })
                if (isMine || isAdmin) TextAction("Delete", { onDelete(comment) })
            }
        }
    }
}

/** The reply target: who the next comment answers, with a way to cancel. */
@Composable
private fun ReplyingChip(name: String, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CirclesPalette.AccentSoft)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Replying to $name",
            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = CirclesPalette.AccentDeep,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, "Cancel reply", tint = CirclesPalette.AccentDeep, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * The comment box: a rounded field that lights its border when focused, and a send button that fills in once there's
 * something to send. Enter sends.
 */
@Composable
private fun CommentComposer(placeholder: String, focusRequester: FocusRequester, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val canSend = text.isNotBlank()
    val border by animateColorAsState(
        if (focused) CirclesPalette.Accent else MM.colors.line,
        label = "composer border"
    )
    val sendFill by animateColorAsState(
        if (canSend) CirclesPalette.Accent else MM.colors.surfaceSunk,
        label = "send fill"
    )
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.9f, label = "send scale")

    fun send() {
        val t = text.trim()
        if (t.isEmpty()) return
        onSend(t.take(2000))
        text = ""
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MM.colors.surfaceSunk,
            border = BorderStroke(if (focused) 1.5.dp else 1.dp, border),
            modifier = Modifier.weight(1f)
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (text.isEmpty()) {
                    Text(placeholder, style = MM.type.body, color = MM.colors.inkMuted)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    textStyle = MM.type.body.copy(color = MM.colors.ink),
                    cursorBrush = SolidColor(CirclesPalette.Accent),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                )
            }
        }
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                .clip(CircleShape)
                .background(sendFill)
                .clickable(enabled = canSend, role = Role.Button) { send() }
                .semantics { contentDescription = "Send comment" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Send,
                contentDescription = null,
                tint = if (canSend) CirclesPalette.White else MM.colors.inkMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
