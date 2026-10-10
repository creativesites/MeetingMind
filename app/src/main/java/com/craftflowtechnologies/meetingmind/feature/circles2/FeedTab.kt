package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
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
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Comment
import com.craftflowtechnologies.meetingmind.core.circles2.PendingPost
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Everything the feed can do. Screens pass real ones; previews and screenshots pass [None]. */
class FeedActions(
    val onNewPost: () -> Unit = {},
    val onPray: (Post) -> Unit = {},
    val onReact: (Post, ReactionKind) -> Unit = { _, _ -> },
    val onOpenThread: (Post) -> Unit = {},
    val onMarkAnswered: (Post) -> Unit = {},
    val onTestimony: (Post) -> Unit = {},
    val onUpdate: (Post) -> Unit = {},
    val onEdit: (Post) -> Unit = {},
    val onDelete: (Post) -> Unit = {},
    val onReport: (Post) -> Unit = {},
    val onStartChain: (Post) -> Unit = {},
    val onCelebrateAnswered: (Post) -> Unit = {},
    val onApprove: (PendingPost) -> Unit = {},
    val onReject: (PendingPost) -> Unit = {}
) { companion object { val None = FeedActions() } }

@Composable
fun FeedTab(state: CircleHomeUiState, now: Long, actions: FeedActions, modifier: Modifier = Modifier) {
    val circle = state.circle ?: return
    val canPost = circle.settings.allowedTypes.any { !it.adminOnly || state.isAdmin }
    LazyColumn(
        modifier, contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        state.failure?.let { f -> item { StatusLine(StatusKind.Warning, f.message) } }
        if (state.isAdmin && state.pending.isNotEmpty()) {
            item { SectionHeader("Waiting for approval", count = state.pending.size) }
            items(state.pending, key = { "p_" + it.id }) { PendingCard(it, actions) }
            item { SectionHeader("Shared") }
        } else if (!state.isAdmin && state.myPendingCount > 0) {
            item { StatusLine(StatusKind.Info, "Your request is with the admins. It will appear here once approved.") }
        }
        if (state.posts.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing shared yet",
                    body = "Share a prayer request, a testimony or a word of encouragement. Only members of this ${circle.groupWord.lowercase()} can see it.",
                    action = if (canPost) { { PrimaryButton("New post", actions.onNewPost, leadingIcon = Icons.Rounded.Add) } } else null
                )
            }
        } else {
            items(state.posts, key = { it.post.id }) { PostCard(it, circle, state.isAdmin, now, actions) }
            if (canPost) item { Row(Modifier.fillMaxWidth().padding(vertical = MM.space.s), horizontalArrangement = Arrangement.Center) { PrimaryButton("New post", actions.onNewPost, leadingIcon = Icons.Rounded.Add) } }
        }
    }
}

@Composable
private fun PendingCard(p: PendingPost, actions: FeedActions) {
    MMCard {
        Text(if (p.anonymous) "Anonymous prayer request" else p.type.label, style = MM.type.caption, color = MM.colors.inkSecondary)
        SelectionContainer { Text(p.body, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.padding(vertical = MM.space.s)) }
        p.verseRef?.let { Text(it, style = MM.type.caption, color = MM.colors.goldInk) }
        Text("Nobody, including you, can see who wrote an anonymous request.", style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.xs))
        Row(Modifier.fillMaxWidth().padding(top = MM.space.s), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            PrimaryButton("Approve", { actions.onApprove(p) }, Modifier.weight(1f))
            SecondaryButton("Not now", { actions.onReject(p) }, Modifier.weight(1f))
        }
    }
}

@Composable
fun PostCard(item: FeedPost, circle: Circle, isAdmin: Boolean, now: Long, actions: FeedActions) {
    val post = item.post
    val c = MM.colors
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val who = when {
        post.anonymous -> "Anonymous"
        item.mine -> "You"
        else -> post.authorName ?: "Member"
    }
    MMCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.anonymous) {
                Surface(shape = MM.radius.pill, color = c.surfaceSunk, modifier = Modifier.size(MMSize.minTouch)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = c.inkMuted) }
                }
            } else InitialsAvatar(who)
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text(who, style = MM.type.bodyStrong, color = c.ink)
                Text("${post.type.label} · ${relativeTime(now, post.createdAt)}${if (post.editedAt != null) " · edited" else ""}", style = MM.type.caption, color = c.inkSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions", tint = c.inkSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = c.surfaceRaised) {
                    MenuItem("Copy text") { clipboard.setText(AnnotatedString(post.body)); menu = false }
                    if (item.mine && post.type == PostType.Prayer && !post.answered) {
                        MenuItem("Post an update") { menu = false; actions.onUpdate(post) }
                        MenuItem("Mark answered") { menu = false; actions.onMarkAnswered(post) }
                    }
                    if (post.type == PostType.Prayer) MenuItem("Start a prayer chain") { menu = false; actions.onStartChain(post) }
                    if (item.mine) MenuItem("Edit") { menu = false; actions.onEdit(post) }
                    if (item.mine || isAdmin) MenuItem("Delete") { menu = false; actions.onDelete(post) }
                    if (!item.mine) MenuItem("Report") { menu = false; actions.onReport(post) }
                }
            }
        }
        if (post.answered) {
            Surface(shape = MM.radius.small, color = c.goldWash, modifier = Modifier.padding(top = MM.space.s)) {
                Text("Answered 🎉", style = MM.type.caption, color = c.goldInk, modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs))
            }
        }
        SelectionContainer {
            Column {
                Text(post.body, style = MM.type.body, color = c.ink, modifier = Modifier.padding(top = MM.space.m))
                post.verseRef?.let { Text(it, style = MM.type.scripture, color = c.goldInk, modifier = Modifier.padding(top = MM.space.s)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            if (post.type == PostType.Prayer) {
                PrayButton(post.counts.prayed, item.prayedByMe) { actions.onPray(post) }
            } else {
                circle.settings.reactions.forEach { k ->
                    val n = post.counts.reactions[k] ?: 0
                    MMChip(if (n > 0) "${k.emoji} $n" else k.emoji, selected = false, onClick = { actions.onReact(post, k) })
                }
            }
            Spacer(Modifier.weight(1f))
            TextAction(if (post.counts.comments > 0) "${post.counts.comments} comments" else "Comment", { actions.onOpenThread(post) })
        }
        if (post.answered && item.mine) {
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                TextAction(if (post.anonymous) "Share a testimony" else "Turn into testimony", { actions.onTestimony(post) })
                // Never offered for anonymous requests: a named celebration would unmask the requester.
                if (!post.anonymous) TextAction("Celebrate", { actions.onCelebrateAnswered(post) })
            }
        }
        if (post.counts.updates > 0) {
            Text("${post.counts.updates} update${if (post.counts.updates == 1) "" else "s"} from the author", style = MM.type.caption, color = c.inkSecondary)
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, style = MM.type.body, color = MM.colors.ink) }, onClick = onClick)
}

@Composable
private fun PrayButton(count: Int, prayed: Boolean, onClick: () -> Unit) {
    val label = (if (prayed) "I prayed" else "I prayed for this") + (if (count > 0) " · $count" else "")
    if (prayed) {
        Row(Modifier.heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, tint = MM.colors.success, modifier = Modifier.size(MMSize.iconSmall))
            Spacer(Modifier.width(MM.space.xs))
            Text(label, style = MM.type.bodyStrong, color = MM.colors.success)
        }
    } else SecondaryButton(label, onClick)
}
