package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.share.ShareHelper
import com.craftflowtechnologies.meetingmind.feature.scripture.rememberPassage
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.ChecklistItem
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.PendingPost
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostMeta
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.circles2.WorkPriority
import com.craftflowtechnologies.meetingmind.core.circles2.WorkStatus
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
    val onPhotoRefused: (String) -> Unit = {},
    /** Restores the locally saved draft for this circle. */
    val onResumeDraft: () -> Unit = {},
    /** Discards the locally saved draft for this circle. */
    val onDiscardDraft: () -> Unit = {},
    val onSetWorkStatus: (Post, WorkStatus) -> Unit = { _, _ -> },
    val onToggleBookmark: (Post) -> Unit = {},
    val onDismissCatchUp: () -> Unit = {},
    val onDismissPrompt: () -> Unit = {},
    val onToggleChecklistItem: (Post, ChecklistItem) -> Unit = { _, _ -> },
    /** Decisions (D5): the author or a moderator sets Proposed or Confirmed. */
    val onSetDecisionStatus: (Post, com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus) -> Unit = { _, _ -> },
    /** Starts a new decision that replaces this one; the old one stays on record, marked replaced. */
    val onReplaceDecision: (Post) -> Unit = {}
) { companion object { val None = FeedActions() } }

@Composable
fun FeedTab(
    state: CircleHomeUiState,
    now: Long,
    actions: FeedActions,
    modifier: Modifier = Modifier,
    savedDraft: com.craftflowtechnologies.meetingmind.core.circles2.ComposerDraft? = null,
    /** False on the Updates tab: Home and the dock now carry the hero, catch-up, events, composer and work strips. */
    homeSections: Boolean = true,
    /** A filter another place asked for ("Prayer", "Decisions"); applied once, then [onFilterConsumed]. */
    filterRequest: String? = null,
    onFilterConsumed: () -> Unit = {}
) {
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
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
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
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val f = readPickedAsync(context, uri)
                if (f != null) {
                    actions.onPhoto(f)
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
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(filterRequest) {
        if (filterRequest != null) { selectedFilter = filterRequest; selectedTag = null; onFilterConsumed() }
    }

    val nextSyncEvent = remember(state.events, now) {
        state.events
            .filter { it.status != "cancelled" && it.endsAt > now }
            .minByOrNull { it.startsAt }
    }
    val openTasksCount = remember(state.posts) {
        state.posts.count { it.post.type == PostType.Task && !it.post.isResolved }
    }
    val myTasksCount = remember(state.posts, state.myUid) {
        state.posts.count {
            it.post.type == PostType.Task && !it.post.isResolved &&
                ((state.myUid != null && it.post.meta?.assigneeUid == state.myUid) || it.mine)
        }
    }
    val blockersCount = remember(state.posts) {
        state.posts.count { it.post.type == PostType.Blocker && !it.post.isResolved }
    }

    val typeFilteredPosts = remember(state.posts, selectedFilter, circle.isWork, state.myUid) {
        if (selectedFilter == "Saved") {
            state.posts.filter { it.saved }
        } else if (circle.isWork) {
            when (selectedFilter) {
                "All" -> state.posts
                "My Tasks" -> state.posts.filter {
                    it.post.type == PostType.Task && (
                        it.mine || (state.myUid != null && it.post.meta?.assigneeUid == state.myUid)
                    )
                }
                "Decisions" -> state.posts.filter { it.post.type == PostType.Decision }
                "Blockers" -> state.posts.filter { it.post.type == PostType.Blocker }
                "Tasks" -> state.posts.filter { it.post.type == PostType.Task }
                "Briefs" -> state.posts.filter { it.post.type == PostType.Brief }
                "Updates" -> state.posts.filter {
                    it.post.type == PostType.Standup || it.post.type == PostType.Announcement || it.post.counts.updates > 0
                }
                "Media" -> state.posts.filter { it.post.attachments.isNotEmpty() }
                else -> state.posts.filter { it.post.type.name == selectedFilter }
            }
        } else {
            when (selectedFilter) {
                "All" -> state.posts
                "Media" -> state.posts.filter { it.post.attachments.isNotEmpty() }
                else -> state.posts.filter { it.post.type.name == selectedFilter }
            }
        }
    }

    val filteredPosts = remember(typeFilteredPosts, selectedTag) {
        if (selectedTag == null) {
            typeFilteredPosts
        } else {
            val tagQuery = selectedTag!!.removePrefix("#")
            typeFilteredPosts.filter { fp ->
                fp.post.meta?.tags?.any { it.removePrefix("#").equals(tagQuery, ignoreCase = true) } == true
            }
        }
    }

    LazyColumn(
        modifier, contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xl),
        verticalArrangement = Arrangement.spacedBy(MM.space.l)
    ) {
        state.failure?.let { f -> item { StatusLine(StatusKind.Warning, f.message) } }
        // Updates opens on a calm prompt, the way Home and Notes start: your face and one line that opens the composer.
        if (!homeSections && canPost && state.me != null) {
            item(key = "share_prompt") {
                SharePrompt(circle, state, onClick = { actions.onStartPost(PostType.Note) })
            }
        }
        if (homeSections) item { CircleHero(circle, state, actions) }
        if (homeSections && state.unreadFeedCount > 0) {
            item(key = "catch_up_banner") {
                CatchUpCard(
                    state = state,
                    now = now,
                    onDismiss = actions.onDismissCatchUp,
                    onJumpIn = { selectedFilter = "All" },
                    modifier = Modifier.animateItem()
                )
            }
        }
        if (homeSections && !state.promptDismissedToday && state.circle != null) {
            item(key = "today_prompt_card") {
                TodayPromptCard(
                    circle = state.circle,
                    onRespond = actions.onStartPost,
                    onDismiss = actions.onDismissPrompt,
                    modifier = Modifier.animateItem()
                )
            }
        }
        if (savedDraft != null && savedDraft.hasContent()) {
            item {
                val label = com.craftflowtechnologies.meetingmind.core.circles2.PostType.entries
                    .firstOrNull { it.wire == savedDraft.typeWire }?.label ?: "Draft"
                Surface(
                    shape = MM.radius.card,
                    color = MM.colors.surfaceSunk,
                    modifier = Modifier.fillMaxWidth().clickable { actions.onResumeDraft() }
                ) {
                    Row(
                        Modifier.padding(start = MM.space.l, end = MM.space.xs, top = MM.space.s, bottom = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("DRAFT · ${label.uppercase()}", style = MM.type.overline, color = MM.colors.inkMuted)
                            Text(
                                savedDraft.body.lineSequence().firstOrNull()?.take(80).orEmpty().ifBlank { savedDraft.verse },
                                style = MM.type.body,
                                color = MM.colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextAction("Resume", onClick = actions.onResumeDraft)
                        TextAction("Discard", onClick = actions.onDiscardDraft)
                    }
                }
            }
        }
        if (homeSections && circle.isWork && nextSyncEvent != null) {
            item(key = "work_next_sync") {
                NextSyncMeetingStrip(nextSyncEvent, actions.onOpenEvent)
            }
        }
        if (homeSections) item {
            ComposerCard(
                circle = circle,
                state = state,
                actions = actions,
                onPhotoClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onVideoClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                onPdfClick = { pickPdf.launch("application/pdf") }
            )
        }
        if (homeSections && circle.isWork) {
            item(key = "work_pulse") {
                WorkPulseStrip(
                    openTasks = openTasksCount,
                    myTasks = myTasksCount,
                    blockers = blockersCount,
                    selectedFilter = selectedFilter,
                    onSelectFilter = { selectedFilter = it }
                )
            }
        }
        if (homeSections) item { EventsSection(state.events, now, actions.onOpenEvent, if (state.canModerate) actions.onNewEvent else null) }
        if (homeSections && allMedia.isNotEmpty()) {
            item { SharedMediaSection(circle.id, allMedia, onSeeAll = { showSharedMediaSheet = true }) }
        }
        item(key = "feed_filter_chips") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedTag != null) {
                    MMChip(
                        label = "#$selectedTag ✕",
                        selected = true,
                        onClick = { selectedTag = null }
                    )
                }
                if (circle.isWork) {
                    val workFilters = listOf("All", "My Tasks", "Decisions", "Blockers", "Tasks", "Briefs", "Updates")
                    workFilters.forEach { f ->
                        MMChip(
                            label = f,
                            selected = selectedFilter == f && selectedTag == null,
                            onClick = { selectedFilter = f }
                        )
                    }
                    if (state.savedCount > 0 || selectedFilter == "Saved") {
                        MMChip(
                            label = "Saved · ${state.savedCount}",
                            selected = selectedFilter == "Saved",
                            onClick = { selectedFilter = "Saved" }
                        )
                    }
                    if (allMedia.isNotEmpty()) {
                        MMChip(
                            label = "Media",
                            selected = selectedFilter == "Media",
                            onClick = { selectedFilter = "Media" }
                        )
                    }
                } else {
                    MMChip("All", selected = selectedFilter == "All" && selectedTag == null, onClick = { selectedFilter = "All" })
                    if (state.savedCount > 0 || selectedFilter == "Saved") {
                        MMChip(
                            label = "Saved · ${state.savedCount}",
                            selected = selectedFilter == "Saved",
                            onClick = { selectedFilter = "Saved" }
                        )
                    }
                    if (allMedia.isNotEmpty()) {
                        MMChip("Media", selected = selectedFilter == "Media", onClick = { selectedFilter = "Media" })
                    }
                    circle.settings.allowedTypes.forEach { type ->
                        MMChip(
                            type.label,
                            selected = selectedFilter == type.name && selectedTag == null,
                            onClick = { selectedFilter = type.name }
                        )
                    }
                }
            }
        }
        if (circle.isWork && selectedFilter == "Decisions") {
            item(key = "decision_log_header") {
                DecisionLogHeader(filteredPosts.size)
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
                    title = when {
                        selectedTag != null -> "No posts tagged #$selectedTag"
                        selectedFilter == "Saved" -> "No saved posts"
                        selectedFilter == "Decisions" -> "No decisions recorded yet"
                        selectedFilter == "All" -> "Nothing shared yet"
                        else -> "No posts found"
                    },
                    body = when {
                        selectedTag != null -> "No posts match the #$selectedTag tag filter."
                        selectedFilter == "Saved" -> "Bookmark posts with the bookmark icon to save them for quick reference later."
                        selectedFilter == "Decisions" -> "Record key team decisions and their context to keep everyone aligned."
                        selectedFilter != "All" -> "No posts match the \"${selectedFilter}\" filter."
                        circle.isWork -> "Share tasks, decisions, briefs and updates. Only members of this team can see it."
                        else -> "Share a prayer request, a testimony or a word of encouragement. Only members of this ${circle.groupWord.lowercase()} can see it."
                    },
                    action = when {
                        selectedTag != null -> ({ SecondaryButton("Clear tag filter", { selectedTag = null }) })
                        canPost && selectedFilter == "Decisions" -> ({ PrimaryButton("Record decision", { actions.onStartPost(PostType.Decision) }, leadingIcon = Icons.Rounded.Add) })
                        canPost && selectedFilter == "All" -> ({ PrimaryButton("New post", actions.onNewPost, leadingIcon = Icons.Rounded.Add) })
                        else -> null
                    }
                )
            }
        } else {
            // The pinned post leads the feed; it is not repeated below.
            val pinnedPost = filteredPosts.firstOrNull { it.post.id == state.pinnedPostId }
            if (pinnedPost != null) {
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
                        modifier = Modifier.animateItem(),
                        onSelectTag = { tag ->
                            val clean = tag.removePrefix("#")
                            selectedTag = if (selectedTag?.equals(clean, ignoreCase = true) == true) null else clean
                        }
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
                    modifier = Modifier.animateItem(),
                    onSelectTag = { tag ->
                        val clean = tag.removePrefix("#")
                        selectedTag = if (selectedTag?.equals(clean, ignoreCase = true) == true) null else clean
                    }
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
                    .height(148.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(CirclesPalette.CoverStart, CirclesPalette.Accent, CirclesPalette.CoverEnd)
                        )
                    )
            ) {
                if (circle.coverPath != null) {
                    MediaImage(circle.id, circle.coverPath, Modifier.fillMaxSize(), "Cover photo for ${circle.name}")
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )
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
                                Text("+${state.members.size - 5}", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors.White)
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
                        color = com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors.White,
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
                                Icon(Icons.Rounded.PersonAdd, null, tint = com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors.White, modifier = Modifier.size(16.dp))
                                Text("Invite", style = MM.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold), color = com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors.White)
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
private fun ComposerCard(
    circle: Circle,
    state: CircleHomeUiState,
    actions: FeedActions,
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit = {},
    onPdfClick: () -> Unit = {}
) {
    val c = MM.colors
    val myName = state.me?.displayName?.ifBlank { "You" } ?: "You"
    MMCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            MemberAvatar(circle.id, state.myUid, myName, state.me?.avatarVersion, size = 40.dp)
            Text(
                "Share an update with ${circle.name}…",
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
                        Icon(Icons.Rounded.Image, "Add photo", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Surface(
                    shape = MM.radius.pill,
                    color = c.surfaceSunk,
                    modifier = Modifier.size(36.dp).clickable(onClick = onVideoClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Videocam, "Add video", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Surface(
                    shape = MM.radius.pill,
                    color = c.surfaceSunk,
                    modifier = Modifier.size(36.dp).clickable(onClick = onPdfClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PictureAsPdf, "Add PDF", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
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
                    color = MM.colors.onAccent,
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

/**
 * One post, content first. The header is calm (avatar, name, time and the ⋯ menu); the type and status sit in one quiet
 * line above the words; media runs edge to edge; the footer carries the quiet verbs (react or pray, comment, save,
 * share). Roles, titles and every other action live in the member sheet and the ⋯ menu.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier,
    onSelectTag: ((String) -> Unit)? = null
) {
    val post = item.post
    // AI-written briefs can be reported (Play AI-content policy). Held outside the menu so the sheet outlives it.
    val aiReport = com.craftflowtechnologies.meetingmind.core.aireport.rememberAiReportState()
    com.craftflowtechnologies.meetingmind.core.aireport.AiReportSheetHost(aiReport)
    val c = MM.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    val who = when {
        post.anonymous -> "Anonymous"
        item.mine -> "You"
        else -> post.authorName ?: "Member"
    }
    val authorMember = remember(post.authorUid, members) { members.firstOrNull { it.uid == post.authorUid } }
    val share: () -> Unit = {
        val rawText = buildString {
            append(post.body)
            if (!post.verseRef.isNullOrBlank()) { append("\n\n— "); append(post.verseRef) }
        }
        val shareText = com.craftflowtechnologies.meetingmind.core.share.AppLinks.withAppLink(
            rawText,
            if (circle.isWork) "Shared from ${circle.name} on MeetingMind" else "Pray & grow together on MeetingMind"
        )
        ShareHelper.shareText(context, "${circle.name} · ${post.type.label}", shareText, "Share Post")
    }
    val gutter = MM.space.l
    Surface(
        shape = MM.radius.card,
        color = c.surface,
        border = if (c.isDark) null else BorderStroke(MMSize.hairline, c.lineFaint),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize(MM.motion.standard())) {
            if (pinned) {
                Row(
                    Modifier.padding(start = gutter, end = gutter, top = MM.space.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Icon(Icons.Rounded.PushPin, null, tint = c.inkMuted, modifier = Modifier.size(14.dp))
                    Text("Pinned", style = MM.type.caption, color = c.inkMuted)
                }
            }
            // ---- header: avatar, name, time, ⋯ ----
            Row(
                Modifier.fillMaxWidth().padding(start = gutter, end = MM.space.xs, top = if (pinned) MM.space.s else MM.space.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (post.anonymous) {
                    Surface(shape = MM.radius.pill, color = c.surfaceSunk, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = c.inkMuted, modifier = Modifier.size(MMSize.iconSmall)) }
                    }
                } else {
                    MemberAvatar(
                        circleId = circle.id, uid = post.authorUid, name = who, avatarVersion = authorMember?.avatarVersion, size = 40.dp,
                        modifier = Modifier.clip(MM.radius.pill).clickable { actions.onMemberClick(post.authorUid ?: who) }
                    )
                }
                Spacer(Modifier.width(MM.space.m))
                Column(
                    Modifier.weight(1f).then(
                        if (!post.anonymous) Modifier.clickable { actions.onMemberClick(post.authorUid ?: who) } else Modifier
                    )
                ) {
                    Text(who, style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        relativeTime(now, post.createdAt) + if (post.editedAt != null) " · edited" else "",
                        style = MM.type.caption, color = c.inkMuted, maxLines = 1
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "More for this post", tint = c.inkMuted) }
                    DropdownMenu(menu, { menu = false }, containerColor = c.surfaceRaised, shape = MM.radius.card) {
                        if (post.body.isNotBlank()) MenuItem("Copy text") { clipboard.setText(AnnotatedString(post.body)); menu = false }
                        MenuItem("Share post") { menu = false; share() }
                        if (item.mine && post.type == PostType.Prayer && !post.answered) {
                            MenuItem("Post an update") { menu = false; actions.onUpdate(post) }
                            MenuItem("Mark answered") { menu = false; actions.onMarkAnswered(post) }
                        }
                        if ((post.type == PostType.Task || post.type == PostType.Blocker) && !post.isResolved && (item.mine || canModerate)) {
                            MenuItem("Mark as Done") {
                                menu = false
                                actions.onSetWorkStatus(post, WorkStatus.Done)
                                actions.onMarkAnswered(post)
                            }
                        }
                        if (post.type == PostType.Task && !post.answered && (item.mine || canModerate)) {
                            MenuItem("Mark completed") { menu = false; actions.onMarkAnswered(post) }
                        }
                        if (post.type == PostType.Decision && post.meta?.decisionStatus != com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Replaced) {
                            val ds = post.meta?.decisionStatus
                            if (item.mine || canModerate) {
                                if (ds != com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Confirmed) MenuItem("Mark confirmed") { menu = false; actions.onSetDecisionStatus(post, com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Confirmed) }
                                if (ds != com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Proposed) MenuItem("Mark as proposed") { menu = false; actions.onSetDecisionStatus(post, com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Proposed) }
                            }
                            MenuItem("Replace with a new decision") { menu = false; actions.onReplaceDecision(post) }
                        }
                        if (post.type == PostType.Brief && post.meta?.sourceKind == "meeting") {
                            com.craftflowtechnologies.meetingmind.core.aireport.ReportAiMenuItem {
                                menu = false
                                aiReport.open(com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource(com.craftflowtechnologies.meetingmind.core.aireport.AiFeature.CIRCLE_BRIEF, post.body))
                            }
                        }
                        if (isAdmin) MenuItem(if (pinned) "Unpin" else "Pin to top") { menu = false; actions.onPinPost(if (pinned) null else post) }
                        if (post.type == PostType.Prayer) MenuItem("Start a prayer chain") { menu = false; actions.onStartChain(post) }
                        if (item.mine) MenuItem("Edit") { menu = false; actions.onEdit(post) }
                        if (item.mine || canModerate) MenuItem("Delete") { menu = false; actions.onDelete(post) }
                        if (!item.mine) MenuItem("Report") { menu = false; actions.onReport(post) }
                    }
                }
            }

            // ---- the kind of post, in one quiet line: dot and word, never colour alone ----
            postKindLine(post)?.let { (label, tone) ->
                Row(
                    Modifier.padding(start = gutter, end = gutter, top = MM.space.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    val toneColor = when (tone) {
                        KindTone.Gold -> c.goldInk
                        KindTone.Success -> c.success
                        KindTone.Danger -> c.danger
                        KindTone.Accent -> c.accent
                        KindTone.Muted -> c.inkMuted
                    }
                    Box(Modifier.size(6.dp).background(toneColor, MM.radius.pill))
                    Text(
                        label.uppercase(),
                        style = MM.type.overline,
                        color = if (tone == KindTone.Muted) c.inkMuted else toneColor,
                        textDecoration = if (post.meta?.decisionStatus == com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Replaced) TextDecoration.LineThrough else null
                    )
                    if (post.type == PostType.Decision && post.meta?.replacesPostId != null) {
                        Text("· replaces an earlier decision", style = MM.type.caption, color = c.inkMuted)
                    }
                }
            }

            // ---- the words ----
            if (post.body.isNotBlank()) {
                SelectionContainer {
                    Text(
                        post.body,
                        style = MM.type.body,
                        color = c.ink,
                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { r -> if (!expanded && r.hasVisualOverflow) hasOverflow = true },
                        modifier = Modifier.padding(start = gutter, end = gutter, top = MM.space.s)
                    )
                }
                if (hasOverflow || expanded) {
                    Text(
                        if (expanded) "Show less" else "Read more",
                        style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = c.inkSecondary,
                        modifier = Modifier
                            .padding(horizontal = gutter - MM.space.xs)
                            .clip(MM.radius.small)
                            .clickable { expanded = !expanded }
                            .padding(horizontal = MM.space.xs, vertical = MM.space.xs)
                    )
                }
            }
            post.verseRef?.let {
                Box(Modifier.padding(horizontal = gutter)) { PostVerseCard(it, actions.onOpenVerse) }
            }
            if (post.type == PostType.Task || post.type == PostType.Blocker) {
                WorkStatusToggle(
                    currentStatus = post.workStatus,
                    onStatusChange = { next ->
                        actions.onSetWorkStatus(post, next)
                        if (next == WorkStatus.Done && !post.answered) actions.onMarkAnswered(post)
                    },
                    modifier = Modifier.padding(start = gutter, end = gutter, top = MM.space.s)
                )
            }
            post.meta?.let { meta ->
                if (!meta.isEmpty) PostMetaChips(meta, Modifier.padding(start = gutter, end = gutter, top = MM.space.s))
                if (meta.checklist.isNotEmpty()) {
                    PostChecklist(meta.checklist, Modifier.padding(start = gutter, end = gutter, top = MM.space.m)) { ci ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onToggleChecklistItem(post, ci)
                    }
                }
            }

            // ---- media, edge to edge ----
            if (post.attachments.isNotEmpty()) {
                PostMediaGrid(circle.id, post.attachments, who, Modifier.padding(top = MM.space.m), edgeToEdge = true)
            }

            // ---- tags: quiet words under the content; tapping one filters ----
            val tags = post.meta?.tags.orEmpty().take(4)
            if (tags.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = gutter - MM.space.xs, end = gutter, top = MM.space.s),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    tags.forEach { tag ->
                        val clean = tag.removePrefix("#")
                        Text(
                            "#$clean",
                            style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                            color = c.accent,
                            modifier = Modifier
                                .clip(MM.radius.small)
                                .clickable(enabled = onSelectTag != null) { onSelectTag?.invoke(clean) }
                                .padding(horizontal = MM.space.xs, vertical = 2.dp)
                        )
                    }
                }
            }

            // ---- footer: the quiet verbs ----
            Row(
                Modifier.fillMaxWidth().padding(start = MM.space.s, end = MM.space.xs, top = MM.space.xs, bottom = MM.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (post.type == PostType.Prayer) {
                    PrayButton(post.counts.prayed, item.prayedByMe) { actions.onPray(post) }
                } else {
                    ReactionButton(item, circle) { k -> actions.onReact(post, k) }
                }
                FooterAction(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    label = if (post.counts.comments > 0) "${post.counts.comments}" else "Comment",
                    description = if (post.counts.comments > 0) "${post.counts.comments} comments" else "Comment",
                    onClick = { actions.onOpenThread(post) }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onToggleBookmark(post)
                }) {
                    Icon(
                        if (item.saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (item.saved) "Remove bookmark" else "Bookmark post",
                        tint = if (item.saved) c.ink else c.inkMuted,
                        modifier = Modifier.size(MMSize.iconSmall)
                    )
                }
                IconButton(onClick = share) {
                    Icon(Icons.Rounded.IosShare, "Share", tint = c.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
                }
            }

            if (post.counts.updates > 0 || (post.type == PostType.Prayer && post.answered && item.mine)) {
                HorizontalDivider(Modifier.padding(horizontal = gutter), thickness = MMSize.hairline, color = c.lineFaint)
                Row(
                    Modifier.fillMaxWidth().padding(start = gutter - MM.space.m, end = gutter, top = MM.space.xs, bottom = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (post.type == PostType.Prayer && post.answered && item.mine) {
                        TextAction(if (post.anonymous) "Share a testimony" else "Turn into testimony", { actions.onTestimony(post) })
                        // Never offered for anonymous requests: a named celebration would unmask the requester.
                        if (!post.anonymous) TextAction("Celebrate", { actions.onCelebrateAnswered(post) })
                    }
                    Spacer(Modifier.weight(1f))
                    if (post.counts.updates > 0) {
                        Text(
                            "${post.counts.updates} update${if (post.counts.updates == 1) "" else "s"} from the author",
                            style = MM.type.caption, color = c.inkMuted
                        )
                    }
                }
            }
        }
    }
}

private enum class KindTone { Gold, Success, Danger, Accent, Muted }

/** The one quiet line above a post's words: what kind of post it is, and its state. Null for a plain note. */
private fun postKindLine(post: Post): Pair<String, KindTone>? = when {
    post.type == PostType.Prayer && post.answered -> "Prayer answered" to KindTone.Success
    post.type == PostType.Prayer -> "Prayer request" to KindTone.Gold
    post.type == PostType.Decision -> when (post.meta?.decisionStatus) {
        com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Proposed -> "Decision · proposed" to KindTone.Accent
        com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Confirmed -> "Decision · confirmed" to KindTone.Success
        com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Replaced -> "Decision · replaced" to KindTone.Muted
        null -> "Decision" to KindTone.Accent
    }
    post.type == PostType.Blocker -> (if (post.isResolved) "Blocker · resolved" else "Blocker") to (if (post.isResolved) KindTone.Success else KindTone.Danger)
    post.type == PostType.Task -> (if (post.isResolved) "Action · done" else "Action") to (if (post.isResolved) KindTone.Success else KindTone.Accent)
    post.type == PostType.Brief -> "Project brief" to KindTone.Gold
    post.answered -> "${post.type.label} · answered" to KindTone.Success
    post.type == PostType.Note -> null
    else -> post.type.label to KindTone.Muted
}

/** A footer verb: a small icon and a word or count, in muted ink, with a 48 dp target. */
@Composable
private fun FooterAction(icon: ImageVector, label: String, description: String, active: Boolean = false, onClick: () -> Unit) {
    val tint = if (active) MM.colors.ink else MM.colors.inkMuted
    Row(
        Modifier
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.pill)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = MM.space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs + 2.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, style = MM.type.secondary.copy(fontWeight = FontWeight.Medium), color = tint)
    }
}

/**
 * Reactions, quietly: the emojis people used (up to three) and the total, or a plain add-reaction icon when there are
 * none. Tapping opens the circle's reactions; your own reaction is marked there. The pop on your own pick only.
 */
@Composable
private fun ReactionButton(item: FeedPost, circle: Circle, onReact: (ReactionKind) -> Unit) {
    val post = item.post
    val c = MM.colors
    var open by remember { mutableStateOf(false) }
    val used = remember(post.counts.reactions, item.myReaction) {
        post.counts.reactions.filter { it.value > 0 }.entries.sortedByDescending { it.value }.map { it.key }
            .let { list -> item.myReaction?.let { mine -> if (mine in list) list else listOf(mine) + list } ?: list }
    }
    val total = post.counts.reactions.values.sum().coerceAtLeast(if (item.myReaction != null) 1 else 0)
    val pop = remember { Animatable(1f) }
    val motion = MM.motion
    // Pops only when your own reaction changes to something, never on first show or on other people's counts.
    var lastMine by remember { mutableStateOf(item.myReaction) }
    LaunchedEffect(item.myReaction) {
        val changed = item.myReaction != lastMine
        lastMine = item.myReaction
        if (changed && item.myReaction != null && !motion.reduced) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
        }
    }
    Box {
        Row(
            Modifier
                .heightIn(min = MMSize.minTouch)
                .clip(MM.radius.pill)
                .clickable(onClickLabel = "React") { open = true }
                .padding(horizontal = MM.space.s)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (total > 0) "$total reactions${item.myReaction?.let { ", yours: ${it.label}" }.orEmpty()}. React" else "React"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs + 2.dp)
        ) {
            if (used.isEmpty()) {
                Icon(Icons.Rounded.AddReaction, null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
                Text("React", style = MM.type.secondary.copy(fontWeight = FontWeight.Medium), color = c.inkMuted)
            } else {
                Text(
                    used.take(3).joinToString("") { it.emoji },
                    style = MM.type.secondary,
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                )
                Text(
                    "$total",
                    style = MM.type.secondary.copy(fontWeight = if (item.myReaction != null) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (item.myReaction != null) c.ink else c.inkMuted
                )
            }
        }
        DropdownMenu(open, { open = false }, containerColor = c.surfaceRaised, shape = MM.radius.pill) {
            Row(Modifier.padding(horizontal = MM.space.s), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                circle.settings.reactions.forEach { k ->
                    val mine = item.myReaction == k
                    Box(
                        Modifier
                            .size(MMSize.minTouch)
                            .clip(MM.radius.pill)
                            .background(if (mine) c.accentWash else Color.Transparent)
                            .clickable(onClickLabel = k.label) { onReact(k); open = false }
                            .semantics { contentDescription = if (mine) "${k.label}, your reaction" else k.label },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(k.emoji, style = if (k.emoji.length > 3) MM.type.caption.copy(fontWeight = FontWeight.SemiBold) else MM.type.heading, color = c.ink)
                    }
                }
            }
        }
    }
}

/** A shared checklist on a post: a slim meter, then the items. Anyone can tick it (as on the server). */
@Composable
private fun PostChecklist(items: List<ChecklistItem>, modifier: Modifier, onToggle: (ChecklistItem) -> Unit) {
    val c = MM.colors
    val done = items.count { it.done }
    val progress by animateFloatAsState(if (items.isEmpty()) 0f else done.toFloat() / items.size, MM.motion.standard(), label = "checklistProgress")
    Column(modifier.fillMaxWidth().background(c.surfaceSunk, MM.radius.small).padding(MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shared list", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.inkSecondary, modifier = Modifier.weight(1f))
            Text(if (done == items.size) "All done" else "$done of ${items.size}", style = MM.type.caption, color = if (done == items.size) c.success else c.inkMuted)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(MM.radius.pill).background(c.track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(MM.radius.pill).background(if (progress >= 1f) c.success else c.ink))
        }
        items.forEach { ci ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(MM.radius.small).clickable { onToggle(ci) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Icon(
                    if (ci.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (ci.done) "Completed: ${ci.text}" else "Mark done: ${ci.text}",
                    tint = if (ci.done) c.success else c.inkMuted,
                    modifier = Modifier.size(MMSize.iconSmall)
                )
                Text(
                    ci.text,
                    style = if (ci.done) MM.type.body.copy(textDecoration = TextDecoration.LineThrough) else MM.type.body,
                    color = if (ci.done) c.inkMuted else c.ink,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MM.type.body, color = MM.colors.ink) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = MMSize.minTouch)
    )
}

/** "Pray", then "Praying · 7" once you have: the one verb on a prayer request, quiet until it is yours. */
@Composable
private fun PrayButton(count: Int, prayed: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    val fill by animateColorAsState(if (prayed) c.goldWash else Color.Transparent, MM.motion.quick(), label = "prayFill")
    val label = when {
        prayed && count > 0 -> "Praying · $count"
        prayed -> "Praying"
        count > 0 -> "Pray · $count"
        else -> "Pray"
    }
    Row(
        Modifier
            .heightIn(min = MMSize.minTouch)
            .padding(vertical = MM.space.xs)
            .clip(MM.radius.pill)
            .background(fill)
            .clickable(onClickLabel = if (prayed) "Stop praying" else "I'm praying", onClick = onClick)
            .padding(horizontal = MM.space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs + 2.dp)
    ) {
        Icon(
            if (prayed) Icons.Rounded.Check else Icons.Rounded.VolunteerActivism, null,
            tint = if (prayed) c.goldInk else c.inkMuted, modifier = Modifier.size(18.dp)
        )
        Text(label, style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold), color = if (prayed) c.goldInk else c.inkSecondary)
    }
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

private fun formatEventWhen(startMs: Long, zone: String): String =
    java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone(zone)
    }.format(java.util.Date(startMs))

@Composable
private fun NextSyncMeetingStrip(
    event: CircleEvent,
    onOpen: (CircleEvent) -> Unit
) {
    val context = LocalContext.current
    MMCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MM.radius.pill,
                    color = CirclesPalette.AccentSoft,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = CirclesPalette.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "NEXT SYNC",
                        style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                        color = CirclesPalette.Accent
                    )
                    Text(
                        event.title,
                        style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold),
                        color = MM.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatEventWhen(event.startsAt, event.timeZone),
                        style = MM.type.caption,
                        color = MM.colors.inkSecondary
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                if (!event.onlineUrl.isNullOrBlank()) {
                    Surface(
                        shape = MM.radius.pill,
                        color = CirclesPalette.Accent,
                        modifier = Modifier.clickable {
                            runCatching {
                                val uri = Uri.parse(event.onlineUrl)
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    ) {
                        Text(
                            "Join link",
                            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = CirclesPalette.White,
                            modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                        )
                    }
                }
                Surface(
                    shape = MM.radius.pill,
                    color = MM.colors.surfaceSunk,
                    border = BorderStroke(1.dp, MM.colors.line),
                    modifier = Modifier.clickable { onOpen(event) }
                ) {
                    Text(
                        "RSVP",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = MM.colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkPulseStrip(
    openTasks: Int,
    myTasks: Int,
    blockers: Int,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        WorkPulseTile(
            title = "Open tasks",
            count = openTasks,
            selected = selectedFilter == "Tasks",
            accentColor = CirclesPalette.Accent,
            modifier = Modifier.weight(1f),
            onClick = { onSelectFilter(if (selectedFilter == "Tasks") "All" else "Tasks") }
        )
        WorkPulseTile(
            title = "My tasks",
            count = myTasks,
            selected = selectedFilter == "My Tasks",
            accentColor = CirclesPalette.WarmInk,
            modifier = Modifier.weight(1f),
            onClick = { onSelectFilter(if (selectedFilter == "My Tasks") "All" else "My Tasks") }
        )
        WorkPulseTile(
            title = "Blockers",
            count = blockers,
            selected = selectedFilter == "Blockers",
            accentColor = CirclesPalette.RoseInk,
            isWarning = blockers > 0,
            modifier = Modifier.weight(1f),
            onClick = { onSelectFilter(if (selectedFilter == "Blockers") "All" else "Blockers") }
        )
    }
}

@Composable
private fun WorkPulseTile(
    title: String,
    count: Int,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
    onClick: () -> Unit
) {
    val c = MM.colors
    val bg = when {
        selected -> accentColor.copy(alpha = 0.12f)
        isWarning -> CirclesPalette.RoseBg
        else -> c.surfaceSunk
    }
    val border = when {
        selected -> accentColor
        isWarning -> CirclesPalette.RoseInk.copy(alpha = 0.35f)
        else -> c.line
    }
    Surface(
        shape = MM.radius.card,
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier.clip(MM.radius.card).clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(vertical = MM.space.m, horizontal = MM.space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                count.toString(),
                style = MM.type.title.copy(fontWeight = FontWeight.Bold),
                color = if (isWarning || selected) accentColor else c.ink
            )
            Text(
                title,
                style = MM.type.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) accentColor else c.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DecisionLogHeader(count: Int) {
    Surface(
        shape = MM.radius.card,
        color = CirclesPalette.RoseBg.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, CirclesPalette.RoseInk.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(MM.space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Surface(
                shape = MM.radius.pill,
                color = CirclesPalette.RoseBg,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📋", style = MM.type.bodyStrong)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Decision Log",
                    style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold),
                    color = CirclesPalette.RoseInk
                )
                Text(
                    "$count recorded ${if (count == 1) "decision" else "decisions"} for this team",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
            }
        }
    }
}

@Composable
private fun PostMetaChips(meta: PostMeta, modifier: Modifier = Modifier) {
    if (meta.isEmpty) return
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        meta.priority?.let { p ->
            val (pBg, pFg) = when (p) {
                WorkPriority.Urgent -> CirclesPalette.RoseBg to CirclesPalette.RoseInk
                WorkPriority.High -> CirclesPalette.WarmBg to CirclesPalette.WarmInk
                WorkPriority.Normal -> CirclesPalette.AccentSoft to CirclesPalette.AccentDeep
                WorkPriority.Low -> MM.colors.surfaceSunk to MM.colors.inkSecondary
            }
            Surface(
                shape = MM.radius.pill,
                color = pBg,
                border = BorderStroke(1.dp, pFg.copy(alpha = 0.25f))
            ) {
                Text(
                    p.label,
                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = pFg,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = 3.dp)
                )
            }
        }
        if (!meta.assigneeName.isNullOrBlank()) {
            Surface(
                shape = MM.radius.pill,
                color = CirclesPalette.AccentSoft,
                border = BorderStroke(1.dp, CirclesPalette.Accent.copy(alpha = 0.2f))
            ) {
                Text(
                    "@${meta.assigneeName}",
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = CirclesPalette.AccentDeep,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = 3.dp)
                )
            }
        }
        if (!meta.dueText.isNullOrBlank()) {
            Surface(
                shape = MM.radius.pill,
                color = MM.colors.surfaceSunk,
                border = BorderStroke(1.dp, MM.colors.line)
            ) {
                Text(
                    "Due ${meta.dueText}",
                    style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = 3.dp)
                )
            }
        }
        if (!meta.blockedOn.isNullOrBlank()) {
            Surface(
                shape = MM.radius.pill,
                color = CirclesPalette.RoseBg,
                border = BorderStroke(1.dp, CirclesPalette.RoseInk.copy(alpha = 0.25f))
            ) {
                Text(
                    "Blocked: ${meta.blockedOn}",
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = CirclesPalette.RoseInk,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = 3.dp)
                )
            }
        }
        if (!meta.decisionOwner.isNullOrBlank()) {
            Surface(
                shape = MM.radius.pill,
                color = CirclesPalette.WarmBg,
                border = BorderStroke(1.dp, CirclesPalette.WarmBorder)
            ) {
                Text(
                    "Owner: ${meta.decisionOwner}",
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = CirclesPalette.WarmInk,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = 3.dp)
                )
            }
        }
        if (!meta.sourceKind.isNullOrBlank()) {
            val sourceText = buildString {
                append("From ")
                append(meta.sourceKind)
                if (!meta.meetingDate.isNullOrBlank()) {
                    append(" · ")
                    append(meta.meetingDate)
                }
            }
            Surface(
                shape = MM.radius.pill,
                color = MM.colors.surfaceSunk,
                border = BorderStroke(1.dp, MM.colors.line)
            ) {
                Text(
                    sourceText,
                    style = MM.type.caption,
                    color = MM.colors.inkMuted,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkStatusToggle(
    currentStatus: WorkStatus,
    onStatusChange: (WorkStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        WorkStatus.entries.forEach { status ->
            val isSelected = status == currentStatus
            val (bg, fg, border) = when (status) {
                WorkStatus.Open -> if (isSelected) Triple(CirclesPalette.AccentSoft, CirclesPalette.AccentDeep, CirclesPalette.Accent.copy(alpha = 0.3f))
                                   else Triple(c.surfaceSunk, c.inkMuted, c.line)
                WorkStatus.InProgress -> if (isSelected) Triple(CirclesPalette.WarmBg, CirclesPalette.WarmInk, CirclesPalette.WarmBorder)
                                         else Triple(c.surfaceSunk, c.inkMuted, c.line)
                WorkStatus.Done -> if (isSelected) Triple(CirclesPalette.GreenBg, CirclesPalette.GreenInk, CirclesPalette.GreenInk.copy(alpha = 0.35f))
                                   else Triple(c.surfaceSunk, c.inkMuted, c.line)
            }
            Surface(
                shape = MM.radius.pill,
                color = bg,
                border = BorderStroke(1.dp, border),
                modifier = Modifier.clickable {
                    if (!isSelected) {
                        onStatusChange(status)
                    }
                }
            ) {
                Row(
                    Modifier.padding(horizontal = MM.space.m, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (status == WorkStatus.Done && isSelected) {
                        Icon(Icons.Rounded.Check, null, tint = fg, modifier = Modifier.size(13.dp))
                    }
                    Text(
                        status.label,
                        style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                        color = fg
                    )
                }
            }
        }
    }
}

@Composable
private fun CatchUpCard(
    state: CircleHomeUiState,
    now: Long,
    onDismiss: () -> Unit,
    onJumpIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isWork = state.circle?.isWork == true
    val openTasksOrRequests = remember(state.posts, isWork) {
        if (isWork) {
            state.posts.count { it.post.type == PostType.Task && !it.post.isResolved }
        } else {
            state.posts.count { it.post.type == PostType.Prayer && !it.post.answered }
        }
    }
    val activeEventsCount = remember(state.events, now) {
        state.events.count { it.status != "cancelled" && it.endsAt > now }
    }

    MMCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = MM.radius.pill,
                    color = MM.colors.accentWash
                ) {
                    Text(
                        "Catch me up · ${state.unreadFeedCount} new",
                        style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = MM.colors.accent,
                        modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse digest" else "Expand digest",
                    tint = MM.colors.inkSecondary
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = MM.space.xs),
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    val summaryLine = buildString {
                        append("${state.unreadFeedCount} new ${if (state.unreadFeedCount == 1) "post" else "posts"}")
                        if (openTasksOrRequests > 0) {
                            val label = if (isWork) "open ${if (openTasksOrRequests == 1) "task" else "tasks"}"
                            else "open prayer ${if (openTasksOrRequests == 1) "request" else "requests"}"
                            append(" · $openTasksOrRequests $label")
                        }
                        if (activeEventsCount > 0) {
                            append(" · $activeEventsCount upcoming ${if (activeEventsCount == 1) "event" else "events"}")
                        }
                    }
                    Text(
                        summaryLine,
                        style = MM.type.body,
                        color = MM.colors.ink
                    )
                    Text(
                        "Stay up to speed with what your circle shared since your last visit.",
                        style = MM.type.secondary,
                        color = MM.colors.inkSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MM.radius.pill,
                    color = MM.colors.accent,
                    modifier = Modifier.clickable(onClick = onJumpIn)
                ) {
                    Text(
                        "Jump in",
                        style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = MM.colors.onAccent,
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                    )
                }
                Surface(
                    shape = MM.radius.pill,
                    color = MM.colors.surfaceSunk,
                    border = BorderStroke(MMSize.hairline, MM.colors.lineSoft),
                    modifier = Modifier.clickable(onClick = onDismiss)
                ) {
                    Text(
                        "Mark caught up",
                        style = MM.type.secondary,
                        color = MM.colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayPromptCard(
    circle: Circle,
    onRespond: (PostType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val space = circleSpace(circle)
    val (promptQuestion, defaultType) = when (space) {
        "Work" -> "What's your top ship or blocker today?" to (if (PostType.Standup in circle.settings.allowedTypes) PostType.Standup else PostType.Note)
        "Study" -> "Share one takeaway or tricky concept from today's study" to (if (PostType.Study in circle.settings.allowedTypes) PostType.Study else PostType.Note)
        "Community" -> "Spark a conversation — what's on your mind today?" to PostType.Note
        else -> "One gratitude or prayer request to lift up today?" to (if (PostType.Prayer in circle.settings.allowedTypes) PostType.Prayer else PostType.Note)
    }

    MMCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = MM.radius.pill,
                    color = MM.colors.surfaceSunk,
                    border = BorderStroke(MMSize.hairline, MM.colors.lineSoft)
                ) {
                    Text(
                        "Today's prompt",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = MM.colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss prompt",
                        tint = MM.colors.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                promptQuestion,
                style = MM.type.bodyStrong,
                color = MM.colors.ink
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = MM.radius.pill,
                    color = MM.colors.accentWash,
                    border = BorderStroke(MMSize.hairline, MM.colors.accent),
                    modifier = Modifier.clickable { onRespond(defaultType) }
                ) {
                    Text(
                        "Respond",
                        style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = MM.colors.accent,
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                    )
                }
            }
        }
    }
}

/** The top of Updates: your face and "Share with the Fellowship…", which opens the composer. One tap, no form. */
@Composable
private fun SharePrompt(circle: Circle, state: CircleHomeUiState, onClick: () -> Unit) {
    val me = state.me ?: return
    Row(
        Modifier.fillMaxWidth().clip(MM.radius.pill).background(MM.colors.surfaceSunk)
            .clickable(onClickLabel = "Write a post", onClick = onClick)
            .padding(start = MM.space.xs + 2.dp, end = MM.space.l, top = MM.space.xs + 2.dp, bottom = MM.space.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        MemberAvatar(circle.id, me.uid, me.displayName, me.avatarVersion, size = 36.dp)
        Text(
            if (circle.isWork) "Share an update with the ${circle.groupWord.lowercase()}…" else "Share with the ${circle.groupWord.lowercase()}…",
            style = MM.type.body, color = MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.Image, null, tint = MM.colors.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
    }
}
