package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.create.CreateChatCard
import com.craftflowtechnologies.meetingmind.core.create.CreateSeed
import com.craftflowtechnologies.meetingmind.feature.create.CreateHost
import com.craftflowtechnologies.meetingmind.feature.create.rememberCreateController
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MM

enum class HomeTab(val label: String) { Feed("Feed"), Chat("Chat"), Members("Members") }

/** Entry point: wires the two view models and the sheets around [CircleHomeContent]. */
@Composable
fun CircleHomeScreen(circleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val home: CircleHomeViewModel = viewModel(key = "circle_home_$circleId", factory = CircleHomeViewModel.Factory(circleId, repo))
    val chat: ChatViewModel = viewModel(key = "circle_chat_$circleId", factory = ChatViewModel.Factory(circleId, repo))
    val state by home.state.collectAsState()
    val chatState by chat.state.collectAsState()
    val draft by chat.draft.collectAsState()
    val composer by home.composer.collectAsState()
    val thread by home.thread.collectAsState()
    val invite by home.invite.collectAsState()
    val pollDraft by chat.pollDraft.collectAsState()
    val settings by rememberCompanionSettings()
    val companionId = settings.form?.name?.lowercase()
    val account: AccountViewModel = viewModel(factory = AccountViewModel.Factory(repo))
    val accountState by account.state.collectAsState()
    val create = rememberCreateController()

    var tab by rememberSaveable { mutableStateOf(HomeTab.Feed) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTypes by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    var showCelebrate by remember { mutableStateOf(false) }
    var chainTitle by remember { mutableStateOf<String?>(null) }
    var chainPost by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Post?>(null) }
    var updating by remember { mutableStateOf<Post?>(null) }
    var reporting by remember { mutableStateOf<Post?>(null) }
    var reportingMessage by remember { mutableStateOf<com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage?>(null) }
    var deleting by remember { mutableStateOf<Post?>(null) }
    var titling by remember { mutableStateOf<Member?>(null) }
    var openingEvent by remember { mutableStateOf<CircleEvent?>(null) }
    var showEventForm by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CircleEvent?>(null) }
    var activeVerse by remember { mutableStateOf<com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference?>(null) }
    var activeMemberInfo by remember { mutableStateOf<Member?>(null) }
    val eventRsvps by home.eventRsvps.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val now = remember(state.posts, chatState.messages) { System.currentTimeMillis() }

    LaunchedEffect(home) { home.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(chat) { chat.notices.collect { snackbar.showSnackbar(it) } }
    // A tapped notification for this circle: land on the right tab, and open the post it is about.
    val target by com.craftflowtechnologies.meetingmind.core.circles2.CircleDeepLinks.pendingTarget.collectAsState()
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (t.circleId != circleId) return@LaunchedEffect
        showSettings = false
        tab = if (t.destination == com.craftflowtechnologies.meetingmind.core.circles2.CircleDestination.Chat) HomeTab.Chat else HomeTab.Feed
        t.postId?.let(home::openThread)
        com.craftflowtechnologies.meetingmind.core.circles2.CircleDeepLinks.consumeTarget()
    }
    LaunchedEffect(tab, chatState.messages.size) { if (tab == HomeTab.Chat) chat.markRead() }
    BackHandler(showSettings) { showSettings = false }

    val circle = state.circle
    if (showSettings && circle != null) {
        CircleSettingsContent(
            circle = circle, me = state.me, onBack = { showSettings = false },
            onSave = home::saveSettings, onCover = home::setCover, onMute = home::setMuted, onLeave = { home.leave(onBack) },
            account = accountState, onLink = account::link, onSwitch = account::switchAccount, onDismissConflict = account::dismissConflict
        )
    } else {
        val feed = FeedActions(
            onNewPost = { showTypes = true },
            onPray = home::pray, onReact = { p, k -> home.react(p, k) },
            onOpenThread = { home.openThread(it.id) },
            onMarkAnswered = { home.markAnswered(it) },
            onTestimony = home::startTestimonyFrom,
            onUpdate = { updating = it }, onEdit = { editing = it }, onDelete = { deleting = it }, onReport = { reporting = it },
            onStartChain = { chainPost = it.id; chainTitle = it.body.take(100) },
            onApprove = home::approve, onReject = home::reject,
            onCelebrateAnswered = { p -> home.celebrateAnswered(p, companionId) },
            onPinPost = home::pinFeedPost,
            onOpenEvent = { openingEvent = it; home.openEvent(it.id) },
            onNewEvent = { editingEvent = null; showEventForm = true },
            onOpenChat = { tab = HomeTab.Chat },
            onStartPost = home::openComposer,
            onPhoto = home::addPhoto,
            onInvite = { home.createInvite(); showInvite = true },
            onOpenVerse = { activeVerse = it },
            onMemberClick = { idOrName ->
                activeMemberInfo = state.members.firstOrNull { it.uid == idOrName || it.displayName.equals(idOrName, ignoreCase = true) }
                    ?: Member(uid = idOrName, displayName = idOrName, role = com.craftflowtechnologies.meetingmind.core.circles2.Role.Member)
            }
        )
        val chatActions = ChatActions(
            onDraft = chat::setDraft, onSend = chat::send, onReply = chat::startReply, onCancelReply = chat::cancelReply,
            onEdit = chat::startEdit, onCancelEdit = chat::cancelEdit, onDelete = chat::delete, onReport = { reportingMessage = it },
            reactionsFor = chat::reactionsFor, onToggleReaction = chat::toggleReaction,
            pollFor = chat::pollFor, onVote = chat::vote, onClosePoll = chat::closePoll,
            chainFor = chat::chainFor, onClaim = chat::claimSlot, onRelease = chat::releaseSlot,
            onNewPoll = chat::openPollDraft, onShareCard = {
                // The real Create studio; the finished card is posted into this chat.
                create.open(CreateSeed(), label = circle?.name ?: "the chat") { r ->
                    CreateChatCard.toPayload(r.card, r.scripture)?.let(chat::shareCard)
                }
            }, onStartChain = { chainPost = null; chainTitle = "" }, onCelebrate = { showCelebrate = true },
            onForward = chat::startForward, onCancelForward = chat::cancelForward,
            onPin = chat::pin, onUnpin = chat::unpin,
            onSendAttachment = chat::sendAttachment, onCancelUpload = chat::cancelUpload,
            onNotice = chat::notice
        )
        CircleHomeContent(
            state = state, chat = chatState, draft = draft, tab = tab, onTab = { tab = it }, now = now,
            feed = feed, chatActions = chatActions,
            members = MemberActions(
                onInvite = { home.createInvite(); showInvite = true }, onSetRole = home::setRole, onRemove = home::remove,
                onSetTitle = { titling = it }, onDecideJoin = home::decideJoin,
                onMemberClick = { activeMemberInfo = it }
            ),
            snackbar = snackbar, onBack = onBack, onSettings = { showSettings = true },
            onInvite = { home.createInvite(); showInvite = true }, onRetry = { }
        )
    }

    // ---- sheets and dialogs ----
    if (showTypes && circle != null) PostTypeSheet(circle, state.isAdmin, onPick = { showTypes = false; home.openComposer(it) }, onDismiss = { showTypes = false })
    if (composer.open && circle != null) ComposerSheet(
        circle, composer, home::setBody, home::setVerse, home::setAnonymous, home::submitPost, home::closeComposer,
        home::removePhoto, onTypeChange = home::openComposer, onPhoto = home::addPhoto
    )
    val threadPost = thread?.let { t -> state.posts.firstOrNull { it.post.id == t.postId }?.post }
    if (thread != null) ThreadSheet(
        threadPost, thread, state.myUid, state.canModerate, now,
        onSend = { home.comment(thread!!.postId, it) },
        onDeleteComment = { home.deleteComment(thread!!.postId, it.id) },
        onDismiss = home::closeThread,
        onMemberClick = { idOrName ->
            activeMemberInfo = state.members.firstOrNull { it.uid == idOrName || it.displayName.equals(idOrName, ignoreCase = true) }
                ?: Member(uid = idOrName, displayName = idOrName, role = com.craftflowtechnologies.meetingmind.core.circles2.Role.Member)
        }
    )
    activeMemberInfo?.let { m ->
        MemberInfoSheet(
            member = m,
            isMe = m.uid == state.myUid,
            me = state.me,
            onDismiss = { activeMemberInfo = null },
            onMessage = {
                activeMemberInfo = null
                tab = HomeTab.Chat
                chat.setDraft("@${m.displayName} ")
            },
            onSetTitle = { titling = it; activeMemberInfo = null },
            onSetRole = { mem, role -> home.setRole(mem, role); activeMemberInfo = null },
            onRemove = { mem -> home.remove(mem); activeMemberInfo = null }
        )
    }
    activeVerse?.let { ref ->
        com.craftflowtechnologies.meetingmind.feature.scripture.VerseSheet(
            reference = ref,
            onDismiss = { activeVerse = null }
        )
    }
    if (showInvite) InviteSheet(invite, home::createInvite) { showInvite = false; home.closeInvite() }
    editing?.let { p -> TextEditSheet("Edit post", p.body, "Save", { home.editPost(p, it) }, { editing = null }) }
    updating?.let { p -> TextEditSheet("Post an update", "", "Post update", { home.addUpdate(p, it) }, { updating = null }) }
    reporting?.let { p -> ReportSheet({ home.report(p, it) }, { reporting = null }) }
    reportingMessage?.let { m -> ReportSheet({ chat.reportMessage(m, it) }, { reportingMessage = null }) }
    pollDraft?.let { d ->
        PollSheet(d, PollSheetActions(chat::setPollQuestion, chat::setPollOption, chat::addPollOption, chat::removePollOption, chat::setPollMulti, chat::createPoll), chat::closePollDraft)
    }
    CreateHost(create)
    titling?.let { m -> TextEditSheet("Title for ${m.displayName}", m.title.orEmpty(), "Save", { home.setTitle(m, it); titling = null }, { titling = null }) }
    val liveEvent = openingEvent?.let { o -> state.events.firstOrNull { it.id == o.id } ?: o }
    liveEvent?.let { e ->
        EventSheet(
            e, eventRsvps, state.myUid, state.canModerate,
            onRsvp = { home.rsvp(e.id, it) },
            onEdit = { openingEvent = null; home.openEvent(null); editingEvent = e; showEventForm = true },
            onCancel = { home.cancelEvent(e.id); openingEvent = null; home.openEvent(null) },
            onDismiss = { openingEvent = null; home.openEvent(null) }
        )
    }
    if (showEventForm && circle != null) {
        EventFormSheet(
            editingEvent, now,
            onSave = { d -> home.saveEvent(editingEvent?.id, d); showEventForm = false; editingEvent = null },
            onDismiss = { showEventForm = false; editingEvent = null }
        )
    }
    // The simple card sheet is only a fallback for builds where the studio can't open.
    if (showCard) CardShareSheet({ chat.shareCard(it) }, { showCard = false })
    chainTitle?.let { t -> ChainSheet(t, { chat.startChain(it, chainPost) }, { chainTitle = null; chainPost = null }) }
    if (showCelebrate) CelebrateSheet(companionId, { k, text -> chat.celebrate(k, text, companionId) }, { showCelebrate = false })
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = MM.colors.surfaceRaised,
            title = { Text("Remove this post?", style = MM.type.heading, color = MM.colors.ink) },
            text = { Text("It disappears for everyone in the circle.", style = MM.type.body, color = MM.colors.inkSecondary) },
            confirmButton = { TextAction("Remove", { home.deletePost(p); deleting = null }) },
            dismissButton = { TextAction("Keep", { deleting = null }) }
        )
    }
}

/** The screen without view models: header, tabs and the active tab. Screenshot tests render this directly. */
@Composable
fun CircleHomeContent(
    state: CircleHomeUiState,
    chat: ChatUiState,
    draft: String,
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    now: Long,
    feed: FeedActions,
    chatActions: ChatActions,
    members: MemberActions,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onInvite: () -> Unit,
    onRetry: () -> Unit
) {
    val circle = state.circle
    val canInvite = circle != null && state.me != null && (state.isAdmin || circle.settings.whoCanInvite == WhoCanInvite.Members)
    Box(Modifier.fillMaxSize().background(MM.colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                circle?.name ?: "Circle", Modifier.padding(horizontal = MM.space.s),
                leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.inkSecondary) } },
                actions = {
                    if (canInvite) IconButton(onClick = onInvite) { Icon(Icons.Rounded.PersonAdd, "Invite people", tint = MM.colors.inkSecondary) }
                    if (circle != null) IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Circle settings", tint = MM.colors.inkSecondary) }
                }
            )
            when {
                state.gone -> EmptyState(
                    "You're not in this circle any more", "You may have been removed, or left from another phone. Ask for a new invite code to join again.",
                    action = { PrimaryButton("Back to Circles", onBack) }
                )
                circle == null -> Column(Modifier.padding(MM.space.l)) {
                    if (state.failure != null) StatusLine(StatusKind.Warning, state.failure.message, actionLabel = "Back", onAction = onBack)
                    else StatusLine(StatusKind.Info, "Opening your circle...")
                }
                else -> {
                    Text("${circle.groupWord} · ${if (circle.memberCount == 1) "1 member" else "${circle.memberCount} members"}", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(horizontal = MM.space.l))
                    SegmentedControl(
                        listOf("Feed", if (chat.unread > 0 && tab != HomeTab.Chat) "Chat · ${chat.unread}" else "Chat", "Members"),
                        tab.ordinal, { onTab(HomeTab.entries[it]) }, Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s)
                    )
                    when (tab) {
                        HomeTab.Feed -> FeedTab(state, now, feed, Modifier.weight(1f))
                        HomeTab.Chat -> ChatTab(chat, draft, now, chatActions, Modifier.weight(1f))
                        HomeTab.Members -> MembersTab(state, members, Modifier.weight(1f))
                    }
                }
            }
        }
        val canPost = circle != null && circle.settings.allowedTypes.any { !it.adminOnly || state.isAdmin }
        if (tab == HomeTab.Feed && canPost) {
            Surface(
                onClick = feed.onNewPost,
                shape = RoundedCornerShape(999.dp),
                color = CirclesPalette.Accent,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp, end = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "New post",
                        tint = CirclesPalette.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "New post",
                        style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = CirclesPalette.White
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(MM.space.l))
    }
}
