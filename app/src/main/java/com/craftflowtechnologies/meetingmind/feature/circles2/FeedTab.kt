package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.share.ShareHelper
import com.craftflowtechnologies.meetingmind.feature.scripture.rememberPassage
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.Member
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
    val onMemberClick: (String) -> Unit = {},
    /** Called when an attachment couldn't be read or is refused. */
    val onPhotoRefused: (String) -> Unit = {}
) { companion object { val None = FeedActions() } }

@Composable
fun FeedTab(state: CircleHomeUiState, now: Long, actions: FeedActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val f = readPickedAsync(context, uri)
                if (f != null) {
                    if (f.name.orEmpty().endsWith(".mov", ignoreCase = true) ||
                        f.name.orEmpty().endsWith(".3gp", ignoreCase = true) ||
                        f.mime.contains("quicktime") ||
                        f.mime.contains("3gpp")
                    ) {
                        actions.onPhotoRefused("MOV and 3GPP formats are not supported yet. Please select an MP4 or WebM video.")
                    } else {
                        actions.onPhoto(f)
                    }
                } else {
                    actions.onPhotoRefused("That file couldn't be read or is too large.")
                }
            }
        }
    }
    val circle = state.circle ?: return
    val canPost = circle.settings.allowedTypes.any { !it.adminOnly || state.isAdmin }
    val allMedia = remember(state.posts) { state.posts.flatMap { it.post.attachments } }
    var showSharedMediaSheet by remember { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    val filteredPosts = remember(state.posts, selectedFilter) {
        when (selectedFilter) {
            "All" -> state.posts
            "Media" -> state.posts.filter { it.post.attachments.isNotEmpty() }
            else -> state.posts.filter { it.post.type.name == selectedFilter }
        }
    }

    LazyColumn(
        modifier, contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        state.failure?.let { f -> item { StatusLine(StatusKind.Warning, f.message) } }
        item { CircleHero(circle, state, actions) }
        item { ComposerCard(circle, state, actions, onPhotoClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) }
        item { EventsSection(state.events, now, actions.onOpenEvent, if (state.canModerate) actions.onNewEvent else null) }
        if (allMedia.isNotEmpty()) {
            item { SharedMediaSection(circle.id, allMedia, onSeeAll = { showSharedMediaSheet = true }) }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                MMChip("All", selected = selectedFilter == "All", onClick = { selectedFilter = "All" })
                MMChip("Media", selected = selectedFilter == "Media", onClick = { selectedFilter = "Media" })
                circle.settings.allowedTypes.forEach { type ->
                    MMChip(
                        type.label,
                        selected = selectedFilter == type.name,
                        onClick = { selectedFilter = type.name }
                    )
                }
            }
        }
        if (selectedFilter == "All") {
            if (state.isAdmin && state.pending.isNotEmpty()) {
                item { SectionHeader("Waiting for approval", count = state.pending.size) }
                items(state.pending, key = { "p_" + it.id }) { PendingCard(it, actions) }
                item { SectionHeader("Shared") }
            } else if (!state.isAdmin && state.myPendingCount > 0) {
                item { StatusLine(StatusKind.Info, "Your request is with the admins. It will appear here once approved.") }
            }
        }
        if (filteredPosts.isEmpty()) {
            item {
                EmptyState(
                    title = if (selectedFilter == "All") "Nothing shared yet" else "No posts found",
                    body = if (selectedFilter != "All") "No posts match the \"${selectedFilter}\" filter."
                    else if (circle.isWork) "Share tasks, decisions, briefs and updates. Only members of this team can see it."
                    else "Share a prayer request, a testimony or a word of encouragement. Only members of this ${circle.groupWord.lowercase()} can see it.",
                    action = if (canPost && selectedFilter == "All") { { PrimaryButton("New post", actions.onNewPost, leadingIcon = Icons.Rounded.Add) } } else null
                )
            }
        } else {
            // The pinned post leads the feed; it is not repeated below.
            val pinnedPost = filteredPosts.firstOrNull { it.post.id == state.pinnedPostId }
            if (pinnedPost != null) {
                item(key = "section_pinned") { SectionHeader("Pinned") }
                item(key = "pinned_" + pinnedPost.post.id) {
                    PostCard(
                        pinnedPost,
                        circle,
                        state.isAdmin,
                        now,
                        actions,
                        pinned = true,
                        canModerate = state.canModerate,
                        members = state.members,
                        modifier = Modifier.animateItem()
                    )
                }
            }
            items(filteredPosts.filter { it.post.id != state.pinnedPostId }, key = { it.post.id }) {
                PostCard(
                    it,
                    circle,
                    state.isAdmin,
                    now,
                    actions,
                    canModerate = state.canModerate,
                    members = state.members,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    if (showSharedMediaSheet) {
        SharedMediaSheet(circle.id, allMedia, onDismiss = { showSharedMediaSheet = false })
    }
}

/** The top of the Circle home (`circle_home.html`): cover photo, overlapping member avatars, rounded lower banner, description, and action buttons. */
@Composable
private fun CircleHero(circle: Circle, state: CircleHomeUiState, actions: FeedActions) {
    val canInvite = state.me != null && (state.isAdmin || circle.settings.whoCanInvite == WhoCanInvite.Members)
    Surface(
        shape = MM.radius.card,
        color = MM.colors.accent,
        modifier = Modifier.fillMaxWidth().clip(MM.radius.card)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
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
                    modifier = Modifier.align(Alignment.BottomStart).padding(MM.space.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)
                ) {
                    state.members.take(5).forEach { m ->
                        MemberAvatar(circle.id, m.uid, m.displayName, m.avatarVersion, size = 34.dp)
                    }
                    if (state.members.size > 5) {
                        Surface(shape = MM.radius.pill, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+${state.members.size - 5}", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.padding(horizontal = MM.space.l, vertical = MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                if (circle.description.isNotBlank()) {
                    Text(circle.description, style = MM.type.body, color = Color.White.copy(alpha = 0.95f))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MM.radius.pill,
                        color = Color.White,
                        modifier = Modifier.weight(1f).clickable(onClick = actions.onOpenChat)
                    ) {
                        Row(
                            Modifier.padding(vertical = MM.space.m, horizontal = MM.space.m),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Open conversation",
                                style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold),
                                color = MM.colors.accent
                            )
                            Spacer(Modifier.width(MM.space.xs))
                            Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (canInvite) {
                        Surface(
                            shape = MM.radius.pill,
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.clickable(onClick = actions.onInvite)
                        ) {
                            Row(
                                Modifier.padding(vertical = MM.space.m, horizontal = MM.space.m),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                Icon(Icons.Rounded.PersonAdd, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Invite", style = MM.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
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
            MemberAvatar(circle.id, state.myUid, myName, state.me?.avatarVersion, size = 38.dp)
            Text(
                if (circle.isWork) "Share an update, task or decision with ${circle.name}…"
                else "Share anything with ${circle.name}…",
                style = MM.type.body,
                color = c.inkMuted,
                modifier = Modifier.weight(1f).clickable { actions.onStartPost(PostType.Note) }
            )
        }
        val quickTypes = if (circle.isWork) {
            listOf(PostType.Task, PostType.Decision, PostType.Brief, PostType.Standup, PostType.Blocker)
                .filter { it in circle.settings.allowedTypes }
        } else {
            listOf(PostType.Devotional, PostType.Study, PostType.Prayer, PostType.Testimony, PostType.Encouragement)
                .filter { it in circle.settings.allowedTypes }
        }
        Row(
            Modifier.padding(top = MM.space.m).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            MMChip("Free-form", selected = true, onClick = { actions.onStartPost(PostType.Note) })
            quickTypes.forEach { t ->
                MMChip(t.label, selected = false, onClick = { actions.onStartPost(t) })
            }
        }
        HorizontalDivider(Modifier.padding(top = MM.space.m, bottom = MM.space.s), color = c.line)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MM.radius.pill,
                    color = c.surfaceSunk,
                    modifier = Modifier.size(36.dp).clickable(onClick = onPhotoClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Image, "Add a photo", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                if (state.canModerate) {
                    Surface(
                        shape = MM.radius.pill,
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
                shape = MM.radius.pill,
                color = CirclesPalette.Accent,
                modifier = Modifier.clickable { actions.onStartPost(PostType.Note) }
            ) {
                Text(
                    "Post",
                    style = MM.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SharedMediaSection(circleId: String, media: List<MediaAttachment>, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("Shared media", count = media.size)
            if (media.size > 3) {
                TextAction("See all (${media.size})", onSeeAll)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            media.take(3).forEach { a ->
                AttachmentTile(
                    circleId = circleId,
                    a = a,
                    modifier = Modifier.weight(1f).height(96.dp).clip(MM.radius.card),
                    contentDescription = if (a.isVideo) "Shared video" else "Shared photo"
                )
            }
        }
    }
}

@Composable
fun SharedMediaSheet(circleId: String, media: List<MediaAttachment>, onDismiss: () -> Unit) {
    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Shared media (${media.size})", style = MM.type.title, color = MM.colors.ink)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            items(media.chunked(2)) { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    pair.forEach { a ->
                        AttachmentTile(
                            circleId = circleId,
                            a = a,
                            modifier = Modifier.weight(1f).height(140.dp).clip(MM.radius.card),
                            contentDescription = if (a.isVideo) "Shared video" else "Shared photo"
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
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
fun PostCard(
    item: FeedPost,
    circle: Circle,
    isAdmin: Boolean,
    now: Long,
    actions: FeedActions,
    pinned: Boolean = false,
    canModerate: Boolean = isAdmin,
    members: List<Member> = emptyList(),
    modifier: Modifier = Modifier
) {
    val post = item.post
    val c = MM.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    val who = when {
        post.anonymous -> "Anonymous"
        item.mine -> "You"
        else -> post.authorName ?: "Member"
    }
    val authorVersion = remember(post.authorUid, members) {
        members.firstOrNull { it.uid == post.authorUid }?.avatarVersion
    }
    MMCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.anonymous) {
                Surface(shape = MM.radius.pill, color = c.surfaceSunk, modifier = Modifier.size(MMSize.minTouch)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = c.inkMuted) }
                }
            } else {
                MemberAvatar(
                    circleId = circle.id,
                    uid = post.authorUid,
                    name = who,
                    avatarVersion = authorVersion,
                    size = 42.dp,
                    modifier = Modifier.clickable { actions.onMemberClick(post.authorUid ?: who) }
                )
            }
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
                    if (post.type == PostType.Task && !post.answered && (item.mine || canModerate)) {
                        MenuItem("Mark completed") { menu = false; actions.onMarkAnswered(post) }
                    }
                    if (isAdmin) MenuItem(if (pinned) "Unpin" else "Pin to top") { menu = false; actions.onPinPost(if (pinned) null else post) }
                    if (post.type == PostType.Prayer) MenuItem("Start a prayer chain") { menu = false; actions.onStartChain(post) }
                    if (item.mine) MenuItem("Edit") { menu = false; actions.onEdit(post) }
                    if (item.mine || canModerate) MenuItem("Delete") { menu = false; actions.onDelete(post) }
                    if (!item.mine) MenuItem("Report") { menu = false; actions.onReport(post) }
                }
            }
        }
        if (post.type == PostType.Task) {
            Surface(
                shape = MM.radius.pill,
                color = if (post.answered) CirclesPalette.GreenBg else CirclesPalette.AccentSoft,
                modifier = Modifier.padding(top = MM.space.s)
            ) {
                Row(
                    Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (post.answered) Icon(Icons.Rounded.Check, null, tint = CirclesPalette.GreenInk, modifier = Modifier.size(13.dp))
                    Text(
                        if (post.answered) "Completed ✅" else "Action item",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (post.answered) CirclesPalette.GreenInk else CirclesPalette.AccentDeep
                    )
                }
            }
        } else if (post.type == PostType.Decision) {
            Surface(shape = MM.radius.pill, color = CirclesPalette.RoseBg, modifier = Modifier.padding(top = MM.space.s)) {
                Text("Decision record 📋", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.RoseInk, modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs))
            }
        } else if (post.answered) {
            Surface(shape = MM.radius.pill, color = CirclesPalette.GreenBg, modifier = Modifier.padding(top = MM.space.s)) {
                Text("Answered 🎉", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.GreenInk, modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs))
            }
        }
        SelectionContainer {
            Column {
                if (post.body.isNotBlank()) {
                    Text(
                        post.body,
                        style = MM.type.body,
                        color = c.ink,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { textLayoutResult ->
                            if (!expanded && textLayoutResult.hasVisualOverflow) {
                                hasOverflow = true
                            }
                        },
                        modifier = Modifier.padding(top = MM.space.m)
                    )
                    if (hasOverflow || expanded) {
                        Text(
                            if (expanded) "Show less" else "Read more",
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = c.accent,
                            modifier = Modifier
                                .clickable { expanded = !expanded }
                                .padding(top = MM.space.xs)
                        )
                    }
                }
                post.verseRef?.let {
                    PostVerseCard(it, actions.onOpenVerse)
                }
            }
        }
        if (post.attachments.isNotEmpty()) {
            if (post.attachments.size == 1) {
                val a = post.attachments.first()
                AttachmentTile(
                    circleId = circle.id,
                    a = a,
                    modifier = Modifier.padding(top = MM.space.s).fillMaxWidth().heightIn(min = 140.dp, max = 260.dp).clip(MM.radius.card),
                    contentDescription = if (a.isVideo) "Video from $who" else "Photo from $who"
                )
            } else {
                Column(Modifier.padding(top = MM.space.s), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.attachments.chunked(2).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { a ->
                                AttachmentTile(
                                    circleId = circle.id,
                                    a = a,
                                    modifier = Modifier.weight(1f).height(140.dp).clip(MM.radius.card),
                                    contentDescription = if (a.isVideo) "Video from $who" else "Photo from $who"
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
                    val isMyReaction = item.myReaction == k
                    val targetBg = when {
                        isMyReaction -> c.accentWash
                        n > 0 -> CirclesPalette.AccentSoft
                        else -> c.surfaceSunk
                    }
                    val targetBorder = when {
                        isMyReaction -> c.accent
                        n > 0 -> CirclesPalette.Accent.copy(alpha = 0.25f)
                        else -> c.line
                    }
                    val targetText = when {
                        isMyReaction -> c.accent
                        n > 0 -> CirclesPalette.AccentDeep
                        else -> c.inkSecondary
                    }
                    val animatedBg by animateColorAsState(
                        targetValue = targetBg,
                        animationSpec = MM.motion.quick(),
                        label = "reactionBg"
                    )
                    val animatedBorder by animateColorAsState(
                        targetValue = targetBorder,
                        animationSpec = MM.motion.quick(),
                        label = "reactionBorder"
                    )
                    val animatedText by animateColorAsState(
                        targetValue = targetText,
                        animationSpec = MM.motion.quick(),
                        label = "reactionText"
                    )
                    Surface(
                        shape = MM.radius.pill,
                        color = animatedBg,
                        border = BorderStroke(1.dp, animatedBorder),
                        modifier = Modifier.clickable { actions.onReact(post, k) }
                    ) {
                        Text(
                            if (n > 0) "${k.emoji} $n" else k.emoji,
                            style = MM.type.caption.copy(fontWeight = if (isMyReaction) FontWeight.Bold else FontWeight.SemiBold),
                            color = animatedText,
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
        if (post.type == PostType.Prayer && post.answered && item.mine) {
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
            shape = MM.radius.pill,
            color = CirclesPalette.AccentSoft,
            border = BorderStroke(1.dp, CirclesPalette.Accent.copy(alpha = 0.3f))
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Check, null, tint = CirclesPalette.AccentDeep, modifier = Modifier.size(MMSize.iconSmall))
                Spacer(Modifier.width(MM.space.xs))
                Text(label, style = MM.type.bodyStrong, color = CirclesPalette.AccentDeep)
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
        shape = MM.radius.card,
        color = CirclesPalette.WarmBg,
        border = BorderStroke(1.dp, CirclesPalette.WarmBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MM.space.s)
            .clip(MM.radius.card)
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
                        style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold),
                        color = CirclesPalette.WarmInk
                    )
                }
                (passageResult as? PassageResult.Found)?.let { found ->
                    Text(
                        found.passage.versionAbbreviation,
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = CirclesPalette.WarmInk.copy(alpha = 0.8f)
                    )
                }
            }

            when (passageResult) {
                is PassageResult.Found -> {
                    Text(
                        "“${passageResult.passage.text.trim().trim('“', '”')}”",
                        style = MM.type.scripture,
                        color = MM.colors.ink,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (passageResult.passage.attribution.isNotBlank()) {
                        Text(
                            passageResult.passage.attribution,
                            style = MM.type.caption,
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
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = CirclesPalette.WarmInk
                    )
                }
            }
        }
    }
}
