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
        modifier, contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        state.failure?.let { f -> item { StatusLine(StatusKind.Warning, f.message) } }
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
                    color = CirclesPalette.WarmBg,
                    border = BorderStroke(1.dp, CirclesPalette.WarmBorder),
                    modifier = Modifier.fillMaxWidth().clickable { actions.onResumeDraft() }
                ) {
                    Row(
                        Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Unfinished $label saved", style = MM.type.bodyStrong, color = CirclesPalette.WarmInk)
                            Text(
                                savedDraft.body.lineSequence().firstOrNull()?.take(80).orEmpty().ifBlank { savedDraft.verse },
                                style = MM.type.caption,
                                color = MM.colors.inkSecondary,
                                maxLines = 1
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
                            label = "Saved (${state.savedCount})",
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
                            label = "Saved (${state.savedCount})",
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
    val authorMember = remember(post.authorUid, members) {
        members.firstOrNull { it.uid == post.authorUid }
    }
    val authorVersion = authorMember?.avatarVersion
    MMCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.anonymous) {
                Surface(shape = MM.radius.pill, color = c.surfaceSunk, modifier = Modifier.size(42.dp)) {
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Text(
                        who,
                        style = MM.type.bodyStrong,
                        color = c.ink,
                        modifier = if (!post.anonymous) Modifier.clickable { actions.onMemberClick(post.authorUid ?: who) } else Modifier
                    )
                    if (!post.anonymous && authorMember != null) {
                        authorMember.title?.takeIf { it.isNotBlank() }?.let { TitleTag(it) }
                        if (authorMember.role.canModerate) RoleTag(authorMember.role)
                    }
                }
                Text(
                    "${relativeTime(now, post.createdAt)}${if (post.editedAt != null) " · edited" else ""}",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
            PostTypeBadge(post.type)
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onToggleBookmark(post)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (item.saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (item.saved) "Remove bookmark" else "Bookmark post",
                    tint = if (item.saved) c.accent else c.inkSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions", tint = c.inkSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = c.surfaceRaised) {
                    if (post.body.isNotBlank()) MenuItem("Copy text") { clipboard.setText(AnnotatedString(post.body)); menu = false }
                    MenuItem(if (item.saved) "Remove from saved" else "Save post") {
                        menu = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onToggleBookmark(post)
                    }
                    MenuItem("Share post") {
                        menu = false
                        val rawText = buildString {
                            append(post.body)
                            if (!post.verseRef.isNullOrBlank()) {
                                append("\n\n— ")
                                append(post.verseRef)
                            }
                        }
                        val shareText = com.craftflowtechnologies.meetingmind.core.share.AppLinks.withAppLink(
                            rawText,
                            if (circle.isWork) "Shared from ${circle.name} on MeetingMind" else "Pray & grow together on MeetingMind"
                        )
                        ShareHelper.shareText(context, "${circle.name} · ${post.type.label}", shareText, "Share Post")
                    }
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
        if (post.type == PostType.Task) {
            Row(
                Modifier.padding(top = MM.space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Surface(
                    shape = MM.radius.pill,
                    color = CirclesPalette.AccentSoft
                ) {
                    Text(
                        "Action item",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = CirclesPalette.AccentDeep,
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                    )
                }
            }
            WorkStatusToggle(
                currentStatus = post.workStatus,
                onStatusChange = { nextStatus ->
                    actions.onSetWorkStatus(post, nextStatus)
                    if (nextStatus == WorkStatus.Done && !post.answered) {
                        actions.onMarkAnswered(post)
                    }
                },
                modifier = Modifier.padding(top = MM.space.xs)
            )
        } else if (post.type == PostType.Decision) {
            Surface(
                shape = MM.radius.card,
                color = CirclesPalette.RoseBg.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().padding(top = MM.space.s)
            ) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(CirclesPalette.RoseInk, MM.radius.pill)
                    )
                    Row(
                        Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val ds = post.meta?.decisionStatus
                        Text(
                            when (ds) {
                                com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Proposed -> "Decision · proposed"
                                com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Confirmed -> "Decision · confirmed"
                                com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Replaced -> "Decision · replaced by a newer one"
                                null -> "Decision record"
                            },
                            style = MM.type.caption.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (ds == com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Replaced) TextDecoration.LineThrough else null
                            ),
                            color = if (ds == com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus.Replaced) MM.colors.inkMuted else CirclesPalette.RoseInk
                        )
                        if (post.meta?.replacesPostId != null) {
                            Text("· replaces an earlier decision", style = MM.type.caption, color = MM.colors.inkSecondary)
                        }
                    }
                }
            }
        } else if (post.type == PostType.Blocker) {
            Surface(
                shape = MM.radius.card,
                color = CirclesPalette.RoseBg.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().padding(top = MM.space.s)
            ) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(CirclesPalette.RoseInk, MM.radius.pill)
                    )
                    Row(
                        Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Blocker ⚠️", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = CirclesPalette.RoseInk)
                    }
                }
            }
            WorkStatusToggle(
                currentStatus = post.workStatus,
                onStatusChange = { nextStatus ->
                    actions.onSetWorkStatus(post, nextStatus)
                    if (nextStatus == WorkStatus.Done && !post.answered) {
                        actions.onMarkAnswered(post)
                    }
                },
                modifier = Modifier.padding(top = MM.space.xs)
            )
        } else if (post.type == PostType.Brief) {
            Surface(
                shape = MM.radius.card,
                color = CirclesPalette.WarmBg.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(top = MM.space.s)
            ) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(CirclesPalette.WarmInk, MM.radius.pill)
                    )
                    Row(
                        Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Project brief 📄", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = CirclesPalette.WarmInk)
                    }
                }
            }
        } else if (post.answered) {
            Surface(shape = MM.radius.pill, color = CirclesPalette.GreenBg, modifier = Modifier.padding(top = MM.space.s)) {
                Text("Answered 🎉", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.GreenInk, modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs))
            }
        }
        post.meta?.let { meta ->
            if (!meta.isEmpty) {
                PostMetaChips(meta, Modifier.padding(top = MM.space.s))
            }
            if (meta.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MM.space.xs),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    meta.tags.take(4).forEach { tag ->
                        val cleanTag = tag.removePrefix("#")
                        Surface(
                            shape = MM.radius.pill,
                            color = c.surfaceSunk,
                            border = BorderStroke(1.dp, c.line),
                            modifier = Modifier.clickable(enabled = onSelectTag != null) {
                                onSelectTag?.invoke(cleanTag)
                            }
                        ) {
                            Text(
                                "#$cleanTag",
                                style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                color = c.accent,
                                modifier = Modifier.padding(horizontal = MM.space.m, vertical = 2.dp)
                            )
                        }
                    }
                }
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
        post.meta?.let { meta ->
            if (meta.checklist.isNotEmpty()) {
                val doneCount = meta.checklist.count { it.done }
                val progress = if (meta.checklist.isEmpty()) 0f else doneCount.toFloat() / meta.checklist.size
                Surface(
                    shape = MM.radius.card,
                    color = c.surfaceSunk,
                    border = BorderStroke(1.dp, c.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MM.space.s)
                ) {
                    Column(
                        modifier = Modifier.padding(MM.space.m),
                        verticalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Checklist · $doneCount/${meta.checklist.size}",
                                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = c.inkSecondary
                            )
                            if (doneCount == meta.checklist.size && meta.checklist.isNotEmpty()) {
                                Text(
                                    "Completed",
                                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                    color = c.success
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(MM.radius.pill)
                                .background(c.track)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(MM.radius.pill)
                                    .background(if (progress >= 1f) c.success else c.accent)
                            )
                        }
                        meta.checklist.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MM.radius.small)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        actions.onToggleChecklistItem(post, item)
                                    }
                                    .padding(vertical = MM.space.xs, horizontal = MM.space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                            ) {
                                Icon(
                                    imageVector = if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = if (item.done) "Completed: ${item.text}" else "Mark done: ${item.text}",
                                    tint = if (item.done) c.accent else c.inkSecondary,
                                    modifier = Modifier.size(MMSize.iconSmall)
                                )
                                Text(
                                    text = item.text,
                                    style = if (item.done) MM.type.body.copy(textDecoration = TextDecoration.LineThrough) else MM.type.body,
                                    color = if (item.done) c.inkMuted else c.ink,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
        PostMediaGrid(circle.id, post.attachments, who, Modifier.padding(top = MM.space.s))
        Row(Modifier.fillMaxWidth().padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            if (post.type == PostType.Prayer) {
                PrayButton(post.counts.prayed, item.prayedByMe) { actions.onPray(post) }
            } else {
                var showReactionPicker by remember { mutableStateOf(false) }
                val activeReactions = remember(circle.settings.reactions, post.counts.reactions, item.myReaction) {
                    circle.settings.reactions.filter { k ->
                        (post.counts.reactions[k] ?: 0) > 0 || item.myReaction == k
                    }
                }
                activeReactions.forEach { k ->
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
                Box {
                    Surface(
                        shape = MM.radius.pill,
                        color = c.surfaceSunk,
                        border = BorderStroke(1.dp, c.line),
                        modifier = Modifier.clickable { showReactionPicker = true }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Add, "Add reaction", tint = c.inkSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = showReactionPicker,
                        onDismissRequest = { showReactionPicker = false },
                        containerColor = c.surfaceRaised
                    ) {
                        Row(
                            Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs),
                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                        ) {
                            circle.settings.reactions.forEach { k ->
                                Surface(
                                    shape = MM.radius.pill,
                                    color = if (item.myReaction == k) c.accentWash else Color.Transparent,
                                    modifier = Modifier.clickable {
                                        actions.onReact(post, k)
                                        showReactionPicker = false
                                    }
                                ) {
                                    Text(
                                        k.emoji,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = MM.radius.pill,
                color = c.surfaceSunk,
                modifier = Modifier.clickable { actions.onOpenThread(post) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.ChatBubbleOutline, null, tint = c.inkSecondary, modifier = Modifier.size(15.dp))
                    Text(
                        if (post.counts.comments > 0) "${post.counts.comments}" else "Comment",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = c.inkSecondary
                    )
                }
            }
            IconButton(
                onClick = {
                    val rawText = buildString {
                        append(post.body)
                        if (!post.verseRef.isNullOrBlank()) {
                            append("\n\n— ")
                            append(post.verseRef)
                        }
                    }
                    val shareText = com.craftflowtechnologies.meetingmind.core.share.AppLinks.withAppLink(
                        rawText,
                        if (circle.isWork) "Shared from ${circle.name} on MeetingMind" else "Pray & grow together on MeetingMind"
                    )
                    ShareHelper.shareText(context, "${circle.name} · ${post.type.label}", shareText, "Share Post")
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.Share, "Share", tint = c.inkSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onToggleBookmark(post)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (item.saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (item.saved) "Remove bookmark" else "Bookmark post",
                    tint = if (item.saved) c.accent else c.inkSecondary,
                    modifier = Modifier.size(16.dp)
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
