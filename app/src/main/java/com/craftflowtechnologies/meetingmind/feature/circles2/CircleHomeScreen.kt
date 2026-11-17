package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.create.CreateChatCard
import com.craftflowtechnologies.meetingmind.core.create.CreateSeed
import com.craftflowtechnologies.meetingmind.feature.create.CreateHost
import com.craftflowtechnologies.meetingmind.feature.create.rememberCreateController
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.MessageKind
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostMeta
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

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
    val savedDraft by home.savedDraft.collectAsState()
    val commentDraft by home.commentDraft.collectAsState()
    val thread by home.thread.collectAsState()
    val invite by home.invite.collectAsState()
    val pollDraft by chat.pollDraft.collectAsState()
    val settings by rememberCompanionSettings()
    val companionId = settings.form?.name?.lowercase()
    val account: AccountViewModel = viewModel(factory = AccountViewModel.Factory(repo))
    val accountState by account.state.collectAsState()
    val create = rememberCreateController()

    var tab by rememberSaveable { mutableStateOf(CircleTab.Home) }
    var updatesFilter by remember { mutableStateOf<String?>(null) }
    var showRhythm by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var deckCards by remember { mutableStateOf<List<CircleHomeDigest.DeckCard>?>(null) }
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
    var showSearch by remember { mutableStateOf(false) }
    var targetChatMessageId by remember { mutableStateOf<String?>(null) }
    val eventRsvps by home.eventRsvps.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val now = remember(state.posts, chatState.messages) { System.currentTimeMillis() }

    LaunchedEffect(home) { home.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(chat) { chat.notices.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(circleId) {
        com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier.foregroundAlerts.collect { alert ->
            if (alert.circleId == circleId) {
                val res = snackbar.showSnackbar(
                    message = "${alert.title}: ${alert.body}",
                    actionLabel = "View"
                )
                if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    tab = when {
                        alert.destination == com.craftflowtechnologies.meetingmind.core.circles2.CircleDestination.Chat -> CircleTab.Chat
                        alert.postId != null -> CircleTab.Updates
                        else -> CircleTab.Home
                    }
                    alert.postId?.let(home::openThread)
                }
            }
        }
    }
    // A tapped notification for this circle: land on the right tab, and open the post it is about.
    val target by com.craftflowtechnologies.meetingmind.core.circles2.CircleDeepLinks.pendingTarget.collectAsState()
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (t.circleId != circleId) return@LaunchedEffect
        showSettings = false
        tab = when {
            t.destination == com.craftflowtechnologies.meetingmind.core.circles2.CircleDestination.Chat -> CircleTab.Chat
            t.postId != null -> CircleTab.Updates
            else -> CircleTab.Home
        }
        t.postId?.let(home::openThread)
        com.craftflowtechnologies.meetingmind.core.circles2.CircleDeepLinks.consumeTarget()
    }
    LaunchedEffect(tab, chatState.messages.size) { if (tab == CircleTab.Chat) chat.markRead() }
    DisposableEffect(circleId, tab) {
        com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier.activeCircleId = circleId
        com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier.activeDestination = when (tab) {
            CircleTab.Chat -> com.craftflowtechnologies.meetingmind.core.circles2.CircleDestination.Chat
            CircleTab.Home, CircleTab.Updates, CircleTab.People -> com.craftflowtechnologies.meetingmind.core.circles2.CircleDestination.Feed
        }
        onDispose {
            if (com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier.activeCircleId == circleId) {
                com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier.activeCircleId = null
                com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier.activeDestination = null
            }
        }
    }
    BackHandler(showSettings) { showSettings = false }

    val circle = state.circle
    if (showSettings && circle != null) {
        CircleSettingsContent(
            circle = circle, me = state.me, onBack = { showSettings = false },
            onSave = home::saveSettings, onCover = home::setCover, onMute = home::setMuted, onLeave = { home.leave { CircleRhythm.clear(context, circleId); onBack() } },
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
            onOpenChat = { tab = CircleTab.Chat },
            onStartPost = home::openComposer,
            onPhoto = home::addPhoto,
            onInvite = { home.createInvite(); showInvite = true },
            onOpenVerse = { activeVerse = it },
            onMemberClick = { idOrName ->
                activeMemberInfo = state.members.firstOrNull { it.uid == idOrName || it.displayName.equals(idOrName, ignoreCase = true) }
                    ?: Member(uid = idOrName, displayName = idOrName, role = com.craftflowtechnologies.meetingmind.core.circles2.Role.Member)
            },
            onPhotoRefused = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
            onResumeDraft = home::resumeSavedDraft,
            onDiscardDraft = home::discardDraft,
            onSetWorkStatus = home::setWorkStatus,
            onToggleBookmark = home::toggleBookmark,
            onDismissCatchUp = home::markCatchUpSeen,
            onDismissPrompt = home::dismissTodayPrompt,
            onToggleChecklistItem = home::toggleChecklistItem,
            onSetDecisionStatus = home::setDecisionStatus,
            onReplaceDecision = home::replaceDecision
        )
        val chatActions = ChatActions(
            onDraft = chat::setDraft, onSend = chat::send, onReply = chat::startReply, onCancelReply = chat::cancelReply,
            onEdit = chat::startEdit, onCancelEdit = chat::cancelEdit, onDelete = chat::delete, onReport = { reportingMessage = it },
            reactionsFor = chat::reactionsFor, onToggleReaction = chat::toggleReaction,
            pollFor = chat::pollFor, onVote = chat::vote, onClosePoll = chat::closePoll,
            chainFor = chat::chainFor, onClaim = chat::claimSlot, onRelease = chat::releaseSlot,
            onNewPoll = chat::openPollDraft, onShareCard = { showCard = true }, onStartChain = { chainPost = null; chainTitle = "" }, onCelebrate = { showCelebrate = true },
            onForward = chat::startForward, onCancelForward = chat::cancelForward,
            onPin = chat::pin, onUnpin = chat::unpin,
            onSendAttachment = chat::sendAttachment, onCancelUpload = chat::cancelUpload,
            onNotice = chat::notice,
            onPromoteToPost = { msg, type ->
                home.openWorkComposer(
                    type,
                    msg.text,
                    com.craftflowtechnologies.meetingmind.core.circles2.PostMeta(sourceKind = "chat", sourceId = msg.id)
                )
            }
        )
        CircleHomeContent(
            state = state, chat = chatState, draft = draft, tab = tab, onTab = { tab = it }, now = now,
            feed = feed, chatActions = chatActions,
            members = MemberActions(
                onInvite = { home.createInvite(); showInvite = true }, onSetRole = home::setRole, onRemove = home::remove,
                onSetTitle = { titling = it }, onDecideJoin = home::decideJoin,
                onMemberClick = { activeMemberInfo = it },
                onUpdatePhoto = home::setProfilePhoto,
                onRemovePhoto = home::removeProfilePhoto,
                onSetExternal = home::setExternal
            ),
            snackbar = snackbar, onBack = onBack, onSettings = { showSettings = true },
            onInvite = { home.createInvite(); showInvite = true }, onSearch = { showSearch = true }, onRetry = { },
            savedDraft = savedDraft,
            targetChatMessageId = targetChatMessageId,
            onScrollHandled = { targetChatMessageId = null },
            // The dock's ＋ opens the composer straight away; the type follows the words (D6).
            onCompose = if (state.me != null) ({ home.openComposer(PostType.Note) }) else null,
            homeNav = HomeNav(
                onOpenUpdates = { f -> updatesFilter = f; tab = CircleTab.Updates },
                onOpenPeople = { tab = CircleTab.People },
                onCatchUp = { deckCards = CircleHomeDigest.catchUp(state, System.currentTimeMillis()) }
            ),
            updatesFilter = updatesFilter,
            onFilterConsumed = { updatesFilter = null },
            onNotifications = { showRhythm = true },
            onMedia = { showMedia = true },
            onDecisions = { updatesFilter = "Decisions"; tab = CircleTab.Updates }
        )
    }

    // ---- sheets and dialogs ----
    if (showTypes && circle != null) PostTypeSheet(circle, state.isAdmin, onPick = { showTypes = false; home.openComposer(it) }, onDismiss = { showTypes = false })
    if (composer.open && circle != null) ComposerSheet(
        circle = circle,
        state = composer,
        onBody = home::setBody,
        onVerse = home::setVerse,
        onAnonymous = home::setAnonymous,
        onSend = home::submitPost,
        onDismiss = home::closeComposer,
        onRemovePhoto = home::removePhoto,
        onTypeChange = home::openComposer,
        onPhoto = home::addPhoto,
        onPhotoRefused = { scope.launch { snackbar.showSnackbar("That file couldn't be read or is too large.") } },
        onDiscardDraft = home::discardDraft,
        uploading = composer.uploading,
        isAdmin = state.isAdmin,
        members = state.members,
        meta = composer.meta,
        onMetaChange = home::setMeta
    )
    val threadPost = thread?.let { t -> state.posts.firstOrNull { it.post.id == t.postId }?.post }
    if (thread != null) ThreadSheet(
        post = threadPost,
        thread = thread,
        myUid = state.myUid,
        isAdmin = state.canModerate,
        now = now,
        // Read the thread when the tap lands: it may have closed in between, and a null here must not crash.
        onSend = { body -> thread?.postId?.let { home.comment(it, body) } },
        onDeleteComment = { c -> thread?.postId?.let { home.deleteComment(it, c.id) } },
        onDismiss = home::closeThread,
        onMemberClick = { idOrName ->
            activeMemberInfo = state.members.firstOrNull { it.uid == idOrName || it.displayName.equals(idOrName, ignoreCase = true) }
                ?: Member(uid = idOrName, displayName = idOrName, role = com.craftflowtechnologies.meetingmind.core.circles2.Role.Member)
        },
        circleId = circle?.id,
        members = state.members,
        isWorkOrLearning = circle?.let { it.isWork || it.settings.space == "learning" } ?: false,
        draft = commentDraft,
        onDraftChange = home::setCommentDraft
    )
    activeMemberInfo?.let { m ->
        MemberInfoSheet(
            member = m,
            isMe = m.uid == state.myUid,
            me = state.me,
            onDismiss = { activeMemberInfo = null },
            onMessage = { _ ->
                activeMemberInfo = null
                tab = CircleTab.Chat
                chat.setDraft("@${m.displayName} ")
            },
            onSetTitle = { targetMember -> titling = targetMember; activeMemberInfo = null },
            onSetRole = { mem, role -> home.setRole(mem, role); activeMemberInfo = null },
            onRemove = { mem -> home.remove(mem); activeMemberInfo = null },
            circleId = circle?.id,
            onUpdatePhoto = home::setProfilePhoto,
            onRemovePhoto = home::removeProfilePhoto,
            isWork = circle?.isWork == true,
            onSetExternal = if (state.isAdmin && circle?.isWork == true) ({ mem, ext -> home.setExternal(mem, ext); activeMemberInfo = null }) else null
        )
    }
    activeVerse?.let { ref ->
        com.craftflowtechnologies.meetingmind.feature.scripture.VerseSheet(
            reference = ref,
            onDismiss = { activeVerse = null }
        )
    }
    if (showInvite) InviteSheet(
        state = invite,
        onCreate = home::createInvite,
        onDismiss = { showInvite = false; home.closeInvite() },
        onCreateEmailInvite = home::createEmailInvite
    )
    editing?.let { p -> TextEditSheet("Edit post", p.body, "Save", { home.editPost(p, it) }, { editing = null }) }
    updating?.let { p -> TextEditSheet("Post an update", "", "Post update", { home.addUpdate(p, it) }, { updating = null }) }
    reporting?.let { p -> ReportSheet({ home.report(p, it) }, { reporting = null }) }
    reportingMessage?.let { m -> ReportSheet({ chat.reportMessage(m, it) }, { reportingMessage = null }) }
    pollDraft?.let { d ->
        PollSheet(
            d,
            PollSheetActions(
                chat::setPollQuestion,
                chat::setPollOption,
                chat::addPollOption,
                chat::removePollOption,
                chat::setPollMulti,
                chat::createPoll,
                onCorrectOption = chat::setPollCorrectOption,
                onExplanation = chat::setPollExplanation
            ),
            chat::closePollDraft,
            space = circle?.settings?.space ?: "faith",
            onClosesAt = chat::setPollClosesAt
        )
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
            onDismiss = { openingEvent = null; home.openEvent(null) },
            onClaimBringItem = { itemId, claim -> home.claimBringItem(e.id, itemId, claim) },
            isWork = circle?.isWork == true
        )
    }
    if (showEventForm && circle != null) {
        EventFormSheet(
            editingEvent, now,
            onSave = { d -> home.saveEvent(editingEvent?.id, d); showEventForm = false; editingEvent = null },
            onDismiss = { showEventForm = false; editingEvent = null },
            space = circle.settings.space
        )
    }
    if (showCard) ChatCardComposerSheet(space = circle?.settings?.space ?: "faith", onShare = { chat.shareCard(it) }, onDismiss = { showCard = false })
    chainTitle?.let { t -> ChainSheet(t, { chat.startChain(it, chainPost) }, { chainTitle = null; chainPost = null }) }
    if (showCelebrate) CelebrateSheet(companionId, { k, text -> chat.celebrate(k, text, companionId) }, { showCelebrate = false }, space = circle?.settings?.space ?: "faith")
    if (showSearch) {
        CircleSearchSheet(
            state = state,
            chat = chatState,
            now = now,
            onOpenPost = { post ->
                tab = CircleTab.Updates
                home.openThread(post.id)
            },
            onOpenChat = {
                tab = CircleTab.Chat
            },
            onOpenChatMessage = { messageId ->
                tab = CircleTab.Chat
                targetChatMessageId = messageId
            },
            onDismiss = { showSearch = false }
        )
    }
    if (showRhythm && circle != null) CircleRhythmSheet(
        circleId = circle.id, name = circle.name, isWork = circle.isWork, muted = state.me?.muted == true,
        onMute = home::setMuted, onDismiss = { showRhythm = false }
    )
    if (showMedia && circle != null) {
        val allMedia = remember(state.posts) { state.posts.flatMap { it.post.attachments } }
        SharedMediaSheet(circle.id, allMedia, onDismiss = { showMedia = false })
    }
    val cards = deckCards
    if (cards != null && circle != null) {
        val closeDeck = { deckCards = null; home.markCatchUpSeen() }
        CatchUpDeckSheet(
            cards = cards, circle = circle, now = now,
            actions = FeedActions(
                onPray = home::pray,
                onReact = { p, k -> home.react(p, k) },
                onOpenThread = { p -> closeDeck(); tab = CircleTab.Updates; home.openThread(p.id) },
                onOpenEvent = { e -> closeDeck(); openingEvent = e; home.openEvent(e.id) }
            ),
            onDone = closeDeck
        )
    }
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

/** The screen without view models: header, the active place and the thumb dock. Screenshot tests render this directly. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CircleHomeContent(
    state: CircleHomeUiState,
    chat: ChatUiState,
    draft: String,
    tab: CircleTab,
    onTab: (CircleTab) -> Unit,
    now: Long,
    feed: FeedActions,
    chatActions: ChatActions,
    members: MemberActions,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onInvite: () -> Unit,
    onRetry: () -> Unit = {},
    onSearch: () -> Unit = {},
    savedDraft: com.craftflowtechnologies.meetingmind.core.circles2.ComposerDraft? = null,
    targetChatMessageId: String? = null,
    onScrollHandled: () -> Unit = {},
    /** The dock's ＋. Null hides it (not a member yet, or still loading). */
    onCompose: (() -> Unit)? = null,
    homeNav: HomeNav = HomeNav(),
    updatesFilter: String? = null,
    onFilterConsumed: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onMedia: () -> Unit = {},
    onDecisions: () -> Unit = {}
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    // While typing, the dock steps aside so the message bar rides the keyboard.
    val showDock = !WindowInsets.isImeVisible
    val circle = state.circle
    val canInvite = circle != null && state.me != null && (state.isAdmin || circle.settings.whoCanInvite == WhoCanInvite.Members)
    var notifyDismissed by rememberSaveable(circle?.id) { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier.canPost(context)) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted || com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier.canPost(context)
        if (!granted) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
    Box(Modifier.fillMaxSize().background(MM.colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                circle?.name ?: "Circle", Modifier.padding(horizontal = MM.space.s),
                leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.inkSecondary) } },
                actions = {
                    if (circle != null) {
                        Surface(
                            onClick = onSearch,
                            shape = CircleShape,
                            color = MM.colors.surfaceRaised,
                            border = BorderStroke(MMSize.hairline, MM.colors.lineSoft),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Search, "Search in circle", tint = MM.colors.inkSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (canInvite) {
                        Surface(
                            onClick = onInvite,
                            shape = CircleShape,
                            color = MM.colors.surfaceRaised,
                            border = BorderStroke(MMSize.hairline, MM.colors.lineSoft),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PersonAdd, "Invite people", tint = MM.colors.inkSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (circle != null) {
                        Box {
                            Surface(
                                onClick = { menuOpen = true },
                                shape = CircleShape,
                                color = MM.colors.surfaceRaised,
                                border = BorderStroke(MMSize.hairline, MM.colors.lineSoft),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.MoreVert, "More for this ${circle.groupWord.lowercase()}", tint = MM.colors.inkSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = MM.colors.surfaceRaised) {
                                DropdownMenuItem(text = { Text("Notifications and daily rhythm", color = MM.colors.ink) }, onClick = { menuOpen = false; onNotifications() })
                                DropdownMenuItem(text = { Text("Photos and files", color = MM.colors.ink) }, onClick = { menuOpen = false; onMedia() })
                                if (circle.isWork) DropdownMenuItem(text = { Text("Decision ledger", color = MM.colors.ink) }, onClick = { menuOpen = false; onDecisions() })
                                DropdownMenuItem(text = { Text("Settings", color = MM.colors.ink) }, onClick = { menuOpen = false; onSettings() })
                            }
                        }
                    }
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.l),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        val spaceLabel = circleSpace(circle)
                        Surface(
                            shape = MM.radius.pill,
                            color = MM.colors.accentWash
                        ) {
                            Text(
                                spaceLabel,
                                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = MM.colors.accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            "${circle.groupWord} · ${if (circle.memberCount == 1) "1 member" else "${circle.memberCount} members"}",
                            style = MM.type.secondary,
                            color = MM.colors.inkSecondary
                        )
                        if (state.isAdmin) {
                            Surface(
                                shape = MM.radius.pill,
                                color = MM.colors.surfaceSunk,
                                border = BorderStroke(MMSize.hairline, MM.colors.lineSoft)
                            ) {
                                Text(
                                    "Admin",
                                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = MM.colors.inkSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (circle.template == "client_engagement") {
                            Surface(
                                shape = MM.radius.pill,
                                color = MM.colors.surfaceSunk,
                                border = BorderStroke(MMSize.hairline, MM.colors.line)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = MM.space.s, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .background(MM.colors.accent, CircleShape)
                                    )
                                    Text(
                                        "External / Client space",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                        color = MM.colors.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                    if (!notificationsEnabled && !notifyDismissed && state.me != null && !state.me.muted) {
                        Box(Modifier.padding(horizontal = MM.space.l, vertical = MM.space.xs)) {
                            StatusLine(
                                StatusKind.Warning,
                                "Notifications are off, so you may miss new posts here.",
                                actionLabel = "Enable",
                                onAction = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { context.startActivity(intent) }
                                    }
                                    notifyDismissed = true
                                }
                            )
                        }
                    }
                    when (tab) {
                        CircleTab.Home -> CircleHomeTab(state, now, feed, homeNav, Modifier.weight(1f))
                        CircleTab.Updates -> FeedTab(
                            state, now, feed, Modifier.weight(1f), savedDraft = savedDraft,
                            homeSections = false, filterRequest = updatesFilter, onFilterConsumed = onFilterConsumed
                        )
                        CircleTab.Chat -> ChatTab(
                            state = chat,
                            draft = draft,
                            now = now,
                            actions = chatActions,
                            modifier = Modifier.weight(1f).then(if (showDock) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
                            isFaith = circle?.let { !it.isWork && it.settings.space != "learning" } ?: true,
                            space = circle?.settings?.space ?: "faith",
                            scrollToMessageId = targetChatMessageId,
                            onScrollHandled = onScrollHandled
                        )
                        CircleTab.People -> MembersTab(state, members, Modifier.weight(1f))
                    }
                    if (showDock) CircleDock(
                        tab = tab,
                        isWork = circle.isWork,
                        chatUnread = if (tab == CircleTab.Chat) 0 else chat.unread,
                        onTab = onTab,
                        onCompose = onCompose
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = MM.space.l, end = MM.space.l, bottom = 76.dp))
    }
}

@Composable
fun CircleSearchSheet(
    state: CircleHomeUiState,
    chat: ChatUiState,
    now: Long,
    onOpenPost: (Post) -> Unit,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
    onOpenChatMessage: ((String) -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val q = query.trim()

    val matchingPosts = remember(q, state.posts, selectedCategory) {
        val matchesCategory: (Post) -> Boolean = { p ->
            when (selectedCategory) {
                "All" -> true
                "Posts" -> true
                "Messages" -> false
                "Tasks" -> p.type == PostType.Task ||
                    p.type == PostType.Blocker ||
                    p.type == PostType.Decision ||
                    p.type.name.equals("Action", ignoreCase = true) ||
                    p.type.name.equals("Question", ignoreCase = true) ||
                    (p.meta?.checklist?.isNotEmpty() == true)
                "Media" -> p.attachments.isNotEmpty()
                else -> true
            }
        }
        val matchesQuery: (Post) -> Boolean = { p ->
            if (q.isBlank()) true
            else {
                p.body.contains(q, ignoreCase = true) ||
                (p.authorName?.contains(q, ignoreCase = true) == true) ||
                p.type.label.contains(q, ignoreCase = true) ||
                (p.meta?.tags?.any { it.contains(q, ignoreCase = true) } == true) ||
                (p.meta?.checklist?.any { it.text.contains(q, ignoreCase = true) } == true) ||
                p.attachments.any { it.name?.contains(q, ignoreCase = true) == true }
            }
        }
        if (q.isBlank() && selectedCategory == "All") emptyList()
        else state.posts.map { it.post }.filter { matchesCategory(it) && matchesQuery(it) }
    }

    val matchingMessages = remember(q, chat.messages, selectedCategory) {
        val matchesCategory: (com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage) -> Boolean = { m ->
            when (selectedCategory) {
                "All" -> true
                "Posts" -> false
                "Messages" -> true
                "Tasks" -> false
                "Media" -> m.attachment != null || m.kind in listOf(MessageKind.Image, MessageKind.Video, MessageKind.Voice, MessageKind.File)
                else -> true
            }
        }
        val matchesQuery: (com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage) -> Boolean = { m ->
            if (q.isBlank()) true
            else {
                m.text.contains(q, ignoreCase = true) ||
                m.authorName.contains(q, ignoreCase = true) ||
                (m.attachment?.name?.contains(q, ignoreCase = true) == true)
            }
        }
        if (q.isBlank() && selectedCategory == "All") emptyList()
        else chat.messages.filter { matchesCategory(it) && matchesQuery(it) }
    }

    CircleSheet(onDismiss) {
        Text(
            "Search in ${state.circle?.name ?: "Circle"}",
            style = MM.type.title.copy(fontWeight = FontWeight.Bold),
            color = MM.colors.ink
        )
        CircleField(
            value = query,
            onValueChange = { query = it },
            label = "Search",
            placeholder = "Type keyword, person, or topic…",
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MM.colors.inkSecondary) }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("All", "Posts", "Messages", "Tasks", "Media").forEach { cat ->
                MMChip(
                    label = cat,
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat }
                )
            }
        }
        val hasActiveSearch = q.isNotBlank() || selectedCategory != "All"
        if (hasActiveSearch) {
            if (matchingPosts.isEmpty() && matchingMessages.isEmpty()) {
                Text(
                    if (q.isNotBlank()) "No results found for \"$q\"" else "No $selectedCategory found",
                    style = MM.type.body,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(vertical = MM.space.m)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    if (matchingPosts.isNotEmpty()) {
                        item { SectionHeader("Feed Posts", count = matchingPosts.size) }
                        items(matchingPosts, key = { "post_${it.id}" }) { post ->
                            MMCard(onClick = { onOpenPost(post); onDismiss() }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(post.authorName ?: "Member", style = MM.type.bodyStrong, color = MM.colors.ink)
                                        Text(post.body.take(120), style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 2)
                                    }
                                    PostTypeBadge(post.type)
                                }
                            }
                        }
                    }
                    if (matchingMessages.isNotEmpty()) {
                        item { SectionHeader("Chat Messages", count = matchingMessages.size) }
                        items(matchingMessages, key = { "msg_${it.id}" }) { msg ->
                            MMCard(onClick = {
                                if (onOpenChatMessage != null) {
                                    onOpenChatMessage(msg.id)
                                } else {
                                    onOpenChat()
                                }
                                onDismiss()
                            }) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(msg.authorName, style = MM.type.bodyStrong, color = MM.colors.ink, modifier = Modifier.weight(1f))
                                        Text(relativeTime(now, msg.createdAt), style = MM.type.caption, color = MM.colors.inkMuted)
                                    }
                                    Text(msg.text.take(120), style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

