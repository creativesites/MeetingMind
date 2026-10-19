package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.share.ShareHelper
import com.craftflowtechnologies.meetingmind.feature.scripture.rememberPassage
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.PendingPost
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
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
    val onReject: (PendingPost) -> Unit = {},
    /** Pin this post to the top, or unpin it with null. Admins only. */
    val onPinPost: (Post?) -> Unit = {},
    val onOpenEvent: (CircleEvent) -> Unit = {},
    val onNewEvent: () -> Unit = {},
    /** Switches to the chat tab. */
    val onOpenChat: () -> Unit = {},
    /** Opens the composer for a type: free-form, prayer request, testimony and so on. */
    val onStartPost: (PostType) -> Unit = {},
    /** A photo picked for the next post. */
    val onPhoto: (PickedFile) -> Unit = {},
    /** Opens the invite sheet for this circle. */
    val onInvite: () -> Unit = {},
    /** Opens scripture passage reader bottom sheet. */
    val onOpenVerse: (ScriptureReference) -> Unit = {},
    /** Opens member profile info sheet. */
    val onMemberClick: (String) -> Unit = {}
) { companion object { val None = FeedActions() } }

@Composable
fun FeedTab(state: CircleHomeUiState, now: Long, actions: FeedActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) readPicked(context, uri)?.let { actions.onPhoto(it) }
    }
    val circle = state.circle ?: return
    val canPost = circle.settings.allowedTypes.any { !it.adminOnly || state.isAdmin }
    val sharedPhotos = remember(state.posts) { state.posts.flatMap { it.post.attachments }.take(6) }
    LazyColumn(
        modifier, contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        state.failure?.let { f -> item { StatusLine(StatusKind.Warning, f.message) } }
        item { CircleHero(circle, state, actions) }
        item { ComposerCard(circle, state, actions, onPhotoClick = { pickPhoto.launch("image/*") }) }
        item { EventsSection(state.events, now, actions.onOpenEvent, if (state.canModerate) actions.onNewEvent else null) }
        if (sharedPhotos.isNotEmpty()) {
            item { SharedMediaSection(circle.id, sharedPhotos.map { it.path }) }
        }
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
            // The pinned post leads the feed; it is not repeated below.
            state.posts.firstOrNull { it.post.id == state.pinnedPostId }?.let { pinned ->
                item { SectionHeader("Pinned") }
                item { PostCard(pinned, circle, state.isAdmin, now, actions, pinned = true, canModerate = state.canModerate) }
            }
            items(state.posts.filter { it.post.id != state.pinnedPostId }, key = { it.post.id }) { PostCard(it, circle, state.isAdmin, now, actions, canModerate = state.canModerate) }
        }
    }
}

/** The top of the Circle home (`circle_home.html`): cover photo, overlapping member avatars, `#5B5BD6` rounded lower banner, description, and action buttons. */
@Composable
private fun CircleHero(circle: Circle, state: CircleHomeUiState, actions: FeedActions) {
    val canInvite = state.me != null && (state.isAdmin || circle.settings.whoCanInvite == WhoCanInvite.Members)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = CirclesPalette.Accent,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(CirclesPalette.CoverStart, CirclesPalette.Accent, CirclesPalette.CoverEnd)
                        )
                    )
            ) {
                if (circle.coverPath != null) {
                    MediaImage(circle.id, circle.coverPath, Modifier.fillMaxSize(), "Cover photo for ${circle.name}")
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)
                ) {
                    state.members.take(5).forEachIndexed { idx, m ->
                        InitialsAvatar(m.displayName, size = 34.dp, online = idx < 2)
                    }
                    if (state.members.size > 5) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+${state.members.size - 5}", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = CirclesPalette.White)
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(circle.name, style = MM.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = CirclesPalette.White)
                Text(
                    "${circle.groupWord} · ${state.members.size} ${if (state.members.size == 1) "member" else "members"}",
                    style = MM.type.secondary, color = CirclesPalette.White.copy(alpha = 0.8f)
                )
                if (circle.description.isNotBlank()) {
                    Text(circle.description, style = MM.type.body, color = CirclesPalette.White.copy(alpha = 0.9f))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = CirclesPalette.White,
                        modifier = Modifier.weight(1f).clickable(onClick = actions.onOpenChat)
                    ) {
                        Row(
                            Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Open conversation",
                                style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold),
                                color = CirclesPalette.Accent
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ChevronRight, null, tint = CirclesPalette.Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (canInvite) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = CirclesPalette.White.copy(alpha = 0.18f),
                            modifier = Modifier.clickable(onClick = actions.onInvite)
                        ) {
                            Row(
                                Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.PersonAdd, null, tint = CirclesPalette.White, modifier = Modifier.size(16.dp))
                                Text("Invite", style = MM.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The composer at the top of the feed (`circle_feed.html`): avatar + prompt, type chips, and bottom icon bar + Post pill. */
@Composable
private fun ComposerCard(circle: Circle, state: CircleHomeUiState, actions: FeedActions, onPhotoClick: () -> Unit) {
    val c = MM.colors
    val myName = state.me?.displayName?.ifBlank { "You" } ?: "You"
    MMCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            InitialsAvatar(myName, size = 38.dp)
            Text(
                "Share anything with ${circle.name}…",
                style = MM.type.body,
                color = c.inkMuted,
                modifier = Modifier.weight(1f).clickable { actions.onStartPost(PostType.Note) }
            )
        }
        Row(
            Modifier.padding(top = MM.space.m).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            MMChip("Free-form", selected = true, onClick = { actions.onStartPost(PostType.Note) })
            if (PostType.Prayer in circle.settings.allowedTypes) MMChip("Prayer request", selected = false, onClick = { actions.onStartPost(PostType.Prayer) })
            if (PostType.Testimony in circle.settings.allowedTypes) MMChip("Testimony", selected = false, onClick = { actions.onStartPost(PostType.Testimony) })
            if (PostType.Encouragement in circle.settings.allowedTypes) MMChip("Encouragement", selected = false, onClick = { actions.onStartPost(PostType.Encouragement) })
        }
        HorizontalDivider(Modifier.padding(top = MM.space.m, bottom = MM.space.s), color = c.line)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = c.surfaceSunk,
                    modifier = Modifier.size(36.dp).clickable(onClick = onPhotoClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Image, "Add a photo", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                if (state.canModerate) {
                    Surface(
                        shape = CircleShape,
                        color = c.surfaceSunk,
                        modifier = Modifier.size(36.dp).clickable(onClick = actions.onNewEvent)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CalendarMonth, "New event", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = CirclesPalette.Accent,
                modifier = Modifier.clickable { actions.onStartPost(PostType.Note) }
            ) {
                Text(
                    "Post",
                    style = MM.type.bodyStrong.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = CirclesPalette.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SharedMediaSection(circleId: String, paths: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Shared media", count = paths.size)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            paths.take(3).forEach { path ->
                MediaImage(
                    circleId = circleId,
                    path = path,
                    modifier = Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(14.dp)),
                    contentDescription = "Shared photo",
                    allowFullscreen = true
                )
            }
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
fun PostCard(item: FeedPost, circle: Circle, isAdmin: Boolean, now: Long, actions: FeedActions, pinned: Boolean = false, canModerate: Boolean = isAdmin) {
    val post = item.post
    val c = MM.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
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
            } else InitialsAvatar(
                who,
                size = 42.dp,
                modifier = Modifier.clickable { actions.onMemberClick(post.authorUid ?: who) }
            )
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    who,
                    style = MM.type.bodyStrong,
                    color = c.ink,
                    modifier = if (!post.anonymous) Modifier.clickable { actions.onMemberClick(post.authorUid ?: who) } else Modifier
                )
                Text("${post.type.label} · ${relativeTime(now, post.createdAt)}${if (post.editedAt != null) " · edited" else ""}", style = MM.type.caption, color = c.inkSecondary)
            }
            PostTypeBadge(post.type)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions", tint = c.inkSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = c.surfaceRaised) {
                    if (post.body.isNotBlank()) MenuItem("Copy text") { clipboard.setText(AnnotatedString(post.body)); menu = false }
                    MenuItem("Share post") {
                        menu = false
                        val shareText = buildString {
                            append(post.body)
                            if (!post.verseRef.isNullOrBlank()) {
                                append("\n\n— ")
                                append(post.verseRef)
                            }
                        }
                        ShareHelper.shareText(context, "${circle.name} · ${post.type.label}", shareText, "Share Post")
                    }
                    if (item.mine && post.type == PostType.Prayer && !post.answered) {
                        MenuItem("Post an update") { menu = false; actions.onUpdate(post) }
                        MenuItem("Mark answered") { menu = false; actions.onMarkAnswered(post) }
                    }
                    if (isAdmin) MenuItem(if (pinned) "Unpin" else "Pin to top") { menu = false; actions.onPinPost(if (pinned) null else post) }
                    if (post.type == PostType.Prayer) MenuItem("Start a prayer chain") { menu = false; actions.onStartChain(post) }
                    if (item.mine) MenuItem("Edit") { menu = false; actions.onEdit(post) }
                    if (item.mine || canModerate) MenuItem("Delete") { menu = false; actions.onDelete(post) }
                    if (!item.mine) MenuItem("Report") { menu = false; actions.onReport(post) }
                }
            }
        }
        if (post.answered) {
            Surface(shape = RoundedCornerShape(999.dp), color = CirclesPalette.GreenBg, modifier = Modifier.padding(top = MM.space.s)) {
                Text("Answered 🎉", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.GreenInk, modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs))
            }
        }
        SelectionContainer {
            Column {
                if (post.body.isNotBlank()) Text(post.body, style = MM.type.body, color = c.ink, modifier = Modifier.padding(top = MM.space.m))
                post.verseRef?.let {
                    PostVerseCard(it, actions.onOpenVerse)
                }
            }
        }
        if (post.attachments.isNotEmpty()) {
            if (post.attachments.size == 1) {
                val a = post.attachments.first()
                MediaImage(
                    circleId = circle.id,
                    path = a.path,
                    modifier = Modifier.padding(top = MM.space.s).fillMaxWidth().heightIn(min = 140.dp, max = 260.dp).clip(RoundedCornerShape(16.dp)),
                    contentDescription = "Photo from $who",
                    allowFullscreen = true
                )
            } else {
                Column(Modifier.padding(top = MM.space.s), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.attachments.chunked(2).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { a ->
                                MediaImage(
                                    circleId = circle.id,
                                    path = a.path,
                                    modifier = Modifier.weight(1f).height(140.dp).clip(RoundedCornerShape(14.dp)),
                                    contentDescription = "Photo from $who",
                                    allowFullscreen = true
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            if (post.type == PostType.Prayer) {
                PrayButton(post.counts.prayed, item.prayedByMe) { actions.onPray(post) }
            } else {
                circle.settings.reactions.forEach { k ->
                    val n = post.counts.reactions[k] ?: 0
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (n > 0) CirclesPalette.AccentSoft else c.surfaceSunk,
                        border = BorderStroke(1.dp, if (n > 0) CirclesPalette.Accent.copy(alpha = 0.25f) else c.line),
                        modifier = Modifier.clickable { actions.onReact(post, k) }
                    ) {
                        Text(
                            if (n > 0) "${k.emoji} $n" else k.emoji,
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = if (n > 0) CirclesPalette.AccentDeep else c.inkSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable { actions.onOpenThread(post) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, tint = c.inkSecondary, modifier = Modifier.size(16.dp))
                Text(
                    if (post.counts.comments > 0) "${post.counts.comments} comments" else "Comment",
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.inkSecondary
                )
            }
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
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = CirclesPalette.AccentSoft,
            border = BorderStroke(1.dp, CirclesPalette.Accent.copy(alpha = 0.3f))
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Check, null, tint = CirclesPalette.AccentDeep, modifier = Modifier.size(MMSize.iconSmall))
                Spacer(Modifier.width(MM.space.xs))
                Text(label, style = MM.type.bodyStrong.copy(fontSize = 13.sp), color = CirclesPalette.AccentDeep)
            }
        }
    } else SecondaryButton(label, onClick)
}

/**
 * A scripture verse on a post: displays the reference, translation abbreviation,
 * the actual passage text in serif, and provides chapter navigation on click.
 */
@Composable
fun PostVerseCard(
    verseRef: String,
    onOpenVerse: (ScriptureReference) -> Unit,
    modifier: Modifier = Modifier
) {
    val ref = remember(verseRef) { ScriptureReferenceParser.parse(verseRef.trim()) }
    val passageResult = ref?.let { rememberPassage(it) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CirclesPalette.WarmBg,
        border = BorderStroke(1.dp, CirclesPalette.WarmBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MM.space.s)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = ref != null) { ref?.let(onOpenVerse) }
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = CirclesPalette.WarmInk,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        ref?.display() ?: verseRef,
                        style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                        color = CirclesPalette.WarmInk
                    )
                }
                (passageResult as? PassageResult.Found)?.let { found ->
                    Text(
                        found.passage.versionAbbreviation,
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = CirclesPalette.WarmInk.copy(alpha = 0.8f)
                    )
                }
            }

            when (passageResult) {
                is PassageResult.Found -> {
                    Text(
                        "“${passageResult.passage.text.trim().trim('“', '”')}”",
                        style = MM.type.scripture.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = MM.colors.ink,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (passageResult.passage.attribution.isNotBlank()) {
                        Text(
                            passageResult.passage.attribution,
                            style = MM.type.caption.copy(fontSize = 10.5.sp),
                            color = MM.colors.inkMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                null -> {
                    if (ref != null) {
                        Text(
                            "Loading scripture…",
                            style = MM.type.caption,
                            color = MM.colors.inkMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                else -> Unit
            }

            if (ref != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Read chapter →",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = CirclesPalette.WarmInk
                    )
                }
            }
        }
    }
}
