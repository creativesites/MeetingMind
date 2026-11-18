package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.craftflowtechnologies.meetingmind.core.circles2.InvitePreview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

fun circleSpace(c: Circle): String = when {
    c.isWork -> "Work"
    c.isLearning -> "Study"
    c.space.equals("community", ignoreCase = true) || c.space.equals("general", ignoreCase = true) -> "Community"
    else -> "Faith"
}

@Composable
fun spaceTint(c: Circle): Color = when (circleSpace(c)) {
    "Work" -> MM.colors.accent
    "Study" -> MM.colors.speaker2
    "Faith" -> MM.colors.gold
    else -> MM.colors.speaker3
}

fun spaceIcon(c: Circle): ImageVector = when (circleSpace(c)) {
    "Work" -> Icons.Rounded.BusinessCenter
    "Study" -> Icons.Rounded.School
    "Faith" -> Icons.Rounded.VolunteerActivism
    else -> Icons.Rounded.Groups
}

/** Entry point: wires the view model. [initialCode] comes from a tapped invite link. */
@Composable
fun CirclesListScreen(
    onBack: () -> Unit,
    onOpenCircle: (String) -> Unit,
    onCreate: () -> Unit,
    initialCode: String? = null,
    onInitialCodeHandled: () -> Unit = {},
    onCreateInSpace: ((String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val vm: CirclesListViewModel = viewModel(factory = CirclesListViewModel.Factory(repo))
    val list by vm.list.collectAsState()
    val join by vm.join.collectAsState()
    val account: AccountViewModel = viewModel(factory = AccountViewModel.Factory(repo))
    val accountState by account.state.collectAsState()
    val askNotifications = rememberNotificationAsk()
    var showJoin by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf("Not run") }
    var checking by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.refreshEngagement() }
    // Circles uses the app's profile photo until the person picks one in Circles.
    LaunchedEffect(Unit) {
        val path = runCatching {
            com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(context).preferencesFlow.first().identity.avatarPath
        }.getOrNull()
        val file = path?.let { java.io.File(it) }?.takeIf { it.isFile } ?: return@LaunchedEffect
        repo.syncAppAvatar("${file.absolutePath}@${file.lastModified()}") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { avatarJpeg(file.readBytes()) }.getOrNull() }
        }
    }
    LaunchedEffect(initialCode) {
        if (initialCode != null) { vm.onPaste(initialCode); showJoin = true; onInitialCodeHandled() }
    }
    CirclesListContent(
        state = list, join = join, showJoin = showJoin,
        onBack = onBack, onOpen = onOpenCircle, onCreate = onCreate,
        onShowJoin = { showJoin = true }, onHideJoin = { showJoin = false; vm.resetJoin() },
        onPaste = vm::onPaste, onJoinName = vm::onJoinName,
        // The first circle is when "tell me when something happens" makes sense, so that is when we ask.
        onJoin = { vm.join { id -> showJoin = false; vm.resetJoin(); if (repo.isFirstCircle()) askNotifications { onOpenCircle(id) } else onOpenCircle(id) } },
        onRefresh = vm::refresh,
        account = accountState, onLink = account::link, onSwitch = account::switchAccount, onDismissConflict = account::dismissConflict,
        onDiagnostics = { showDiagnostics = true },
        onCreateInSpace = onCreateInSpace
    )
    if (showDiagnostics) {
        val firebaseProject = remember { runCatching { com.google.firebase.FirebaseApp.getInstance().options.projectId }.getOrNull() ?: "Firebase not initialized" }
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("Circles diagnostics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text("API: ${com.craftflowtechnologies.meetingmind.BuildConfig.CIRCLES_API_URL.ifBlank { "not configured" }}")
                    Text("Firebase project: $firebaseProject")
                    Text("Account: ${repo.auth.currentUid?.let { if (repo.auth.isAnonymous) "anonymous · ${it.take(8)}…" else "signed in · ${it.take(8)}…" } ?: "not signed in"}")
                    Text("Check: $diagnostics")
                }
            },
            confirmButton = {
                TextButton(enabled = !checking, onClick = {
                    checking = true; diagnostics = "Checking Firebase sign-in and Circles API…"
                    scope.launch {
                        diagnostics = when (val signed = repo.uid()) {
                            is com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult.Err -> "Firebase sign-in failed: ${signed.failure.code ?: signed.failure.kind}: ${signed.failure.message}"
                            is com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult.Ok -> when (val result = repo.api.myCircles()) {
                                is com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult.Ok -> "PASS · authenticated API responded (${result.value.size} circles)"
                                is com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult.Err -> "API failed: ${result.failure.code ?: result.failure.kind}: ${result.failure.message}${result.failure.httpStatus?.let { " (HTTP $it)" } ?: ""}${result.failure.requestId?.let { " · request $it" } ?: ""}"
                            }
                        }
                        checking = false
                    }
                }) { Text(if (checking) "Checking…" else "Run check") }
            },
            dismissButton = { TextButton(onClick = { showDiagnostics = false }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CirclesListContent(
    state: CirclesListState,
    join: JoinUiState,
    showJoin: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onShowJoin: () -> Unit,
    onHideJoin: () -> Unit,
    onPaste: (String) -> Unit,
    onJoinName: (String) -> Unit,
    onJoin: () -> Unit,
    onRefresh: () -> Unit,
    account: AccountUiState? = null,
    onLink: (android.content.Context) -> Unit = {},
    onSwitch: () -> Unit = {},
    onDismissConflict: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onCreateInSpace: ((String?) -> Unit)? = null
) {
    val canAct = state !is CirclesListState.NotConnected
    val hasManyCircles = (state as? CirclesListState.Ready)?.circles?.let { it.size >= 3 } == true
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedSpace by rememberSaveable { mutableStateOf("All") }
    val createForActiveSpace: () -> Unit = {
        val mapped = when (selectedSpace) {
            "Work" -> "work"
            "Study" -> "learning"
            "Faith" -> "faith"
            "Community" -> "general"
            else -> null
        }
        if (onCreateInSpace != null) onCreateInSpace(mapped) else onCreate()
    }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ready = state as? CirclesListState.Ready
    Column(Modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding()) {
        CirclesTopBar(
            title = "Circles",
            onBack = onBack,
            subtitle = ready?.circles?.takeIf { it.isNotEmpty() }?.let { if (it.size == 1) "1 circle" else "${it.size} circles" },
            actions = {
                if (canAct) {
                    if (hasManyCircles) {
                        CirclesHeaderAction(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, if (searchOpen) "Close search" else "Search circles") {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        }
                    }
                    TextAction("Join", onShowJoin)
                    CirclesHeaderAction(Icons.Rounded.Add, "Start a circle", createForActiveSpace)
                }
            }
        )
        AnimatedVisibility(
            visible = searchOpen && hasManyCircles,
            enter = fadeIn(MM.motion.quick()) + expandVertically(MM.motion.standard()),
            exit = fadeOut(MM.motion.quick()) + shrinkVertically(MM.motion.standard())
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.xs)) {
                CircleField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search circles",
                    placeholder = "Name or description",
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MM.colors.inkMuted) }
                )
            }
        }
        when (state) {
            CirclesListState.NotConnected -> EmptyState(
                title = "Circles isn't connected yet",
                body = "Circles lets small groups share updates, resources, and chat together. This build isn't linked to the Circles service yet, so there's nothing to join or create. Everything else in MeetingMind works as normal.",
                modifier = Modifier.padding(horizontal = MM.space.l).navigationBarsPadding(),
                illustration = { Icon(Icons.Rounded.Groups, null, tint = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.xl)) }
            )
            CirclesListState.Loading -> Column(
                Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(MM.space.l)
            ) {
                if (account != null) CirclesAccountCard(account, 0, onLink, onSwitch, onDismissConflict)
                CircleListSkeleton()
            }
            is CirclesListState.Ready -> if (state.circles.isEmpty()) {
                // Signed out and empty is where a reinstall or a new phone lands: sign-in comes first, then the welcome.
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = MM.space.l).padding(top = MM.space.s, bottom = circlesBottomInset()),
                    verticalArrangement = Arrangement.spacedBy(MM.space.l)
                ) {
                    state.note?.let { Note(it, onRefresh) }
                    if (account != null) CirclesReveal(0) { CirclesAccountCard(account, state.earlier, onLink, onSwitch, onDismissConflict) }
                    CirclesReveal(1) { CirclesWelcome(onStart = createForActiveSpace, onJoin = onShowJoin) }
                }
            } else {
                val filteredCircles = remember(state.circles, selectedSpace, searchQuery) {
                    val q = searchQuery.trim()
                    val spaceFiltered = if (selectedSpace == "All") state.circles
                    else state.circles.filter { circleSpace(it) == selectedSpace }
                    if (q.isBlank()) spaceFiltered
                    else spaceFiltered.filter { c ->
                        c.name.contains(q, ignoreCase = true) || c.description.contains(q, ignoreCase = true)
                    }
                }
                val spacesInUse = remember(state.circles) { listOf("Work", "Study", "Faith", "Community").filter { sp -> state.circles.any { circleSpace(it) == sp } } }
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            onRefresh()
                            kotlinx.coroutines.delay(600)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = circlesListPadding(),
                        verticalArrangement = Arrangement.spacedBy(MM.space.l)
                    ) {
                        state.note?.let { n -> item { Note(n, onRefresh) } }
                        if (account != null) item(key = "account") {
                            CirclesReveal(0) { CirclesAccountCard(account, state.earlier, onLink, onSwitch, onDismissConflict) }
                        }
                        if (spacesInUse.size > 1) {
                            item(key = "spaces") {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                                ) {
                                    (listOf("All") + spacesInUse).forEach { spaceName ->
                                        MMChip(spaceName, selected = selectedSpace == spaceName, onClick = { selectedSpace = spaceName })
                                    }
                                }
                            }
                        }
                        if (filteredCircles.isEmpty()) {
                            item(key = "empty_circles_filter") {
                                Box(Modifier.animateItem()) {
                                    if (searchQuery.isNotBlank()) {
                                        EmptyState(
                                            title = "No circles found",
                                            body = "Nothing matches \"$searchQuery\". Check the spelling or clear the search.",
                                            modifier = Modifier.padding(vertical = MM.space.l)
                                        )
                                    } else {
                                        EmptyState(
                                            title = "No $selectedSpace circles",
                                            body = "You're not in a $selectedSpace circle yet. Start one, or join with an invite code.",
                                            modifier = Modifier.padding(vertical = MM.space.l)
                                        )
                                    }
                                }
                            }
                        } else {
                            val featured = filteredCircles.first()
                            item(key = "featured_${featured.id}") {
                                CirclesReveal(1, Modifier.animateItem()) { FeaturedCircleCard(featured) { onOpen(featured.id) } }
                            }
                            if (filteredCircles.size > 1) {
                                item(key = "all_circles") {
                                    CirclesReveal(2, Modifier.animateItem()) {
                                        Column {
                                            CirclesSectionTitle(if (selectedSpace == "All") "All circles" else "$selectedSpace circles")
                                            MMCard(contentPadding = 0.dp) {
                                                filteredCircles.drop(1).forEachIndexed { i, c ->
                                                    if (i > 0) CirclesRowDivider(startInset = MM.space.l + 44.dp + MM.space.m)
                                                    CircleRow(c) { onOpen(c.id) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showJoin) JoinSheet(
        join, onHideJoin, onPaste, onJoinName, onJoin,
        account = account, onLink = onLink, onSwitch = onSwitch, onDismissConflict = onDismissConflict
    )
}

@Composable
private fun Note(f: CirclesFailure, onRefresh: () -> Unit) {
    StatusLine(StatusKind.Warning, f.message, actionLabel = "Retry", onAction = onRefresh)
}

/** While the list loads: a static cover block and three row shapes (no shimmer, no idle animation). */
@Composable
private fun CircleListSkeleton() {
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = "Loading your circles" },
        verticalArrangement = Arrangement.spacedBy(MM.space.l)
    ) {
        Box(Modifier.fillMaxWidth().height(184.dp).background(MM.colors.surfaceSunk, MM.radius.card))
        repeat(3) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                Box(Modifier.size(44.dp).background(MM.colors.surfaceSunk, MM.radius.small))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Box(Modifier.fillMaxWidth(0.5f).height(MM.space.m).background(MM.colors.surfaceSunk, MM.radius.small))
                    Box(Modifier.fillMaxWidth(0.3f).height(MM.space.s + MM.space.xs).background(MM.colors.surfaceSunk, MM.radius.small))
                }
            }
        }
    }
}

/**
 * The circle you were last in, as a living card: its cover edge to edge with the name and one quiet line over it.
 * No coloured band, no extra pills; the whole card opens the circle.
 */
@Composable
private fun FeaturedCircleCard(c: Circle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val memberLabel = "${circleSpace(c)} · ${c.groupWord} · ${if (c.memberCount == 1) "1 member" else "${c.memberCount} members"}"
    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = MM.colors.surfaceSunk,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { }
    ) {
        Box(Modifier.fillMaxWidth().height(196.dp)) {
            CircleCoverImage(c.coverPath, c.template, c.space, c.id, Modifier.fillMaxSize(), contentDescription = null)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to MM.colors.scrim.copy(alpha = 0.85f))
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Text(
                    c.name, style = MM.type.title, color = FixedWhite,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(memberLabel, style = MM.type.caption, color = FixedWhite.copy(alpha = 0.86f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.description.isNotBlank()) {
                    Text(c.description, style = MM.type.secondary, color = FixedWhite.copy(alpha = 0.86f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One circle in the grouped list: its cover, the name, one quiet line, and a chevron. */
@Composable
private fun CircleRow(c: Circle, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 68.dp)
            .padding(horizontal = MM.space.l, vertical = MM.space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        CircleCoverImage(c.coverPath, c.template, c.space, c.id, Modifier.size(44.dp).clip(MM.radius.small), thumb = true)
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${circleSpace(c)} · ${if (c.memberCount == 1) "1 member" else "${c.memberCount} members"}",
                style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkFaint, modifier = Modifier.size(MMSize.iconSmall))
    }
}

/** What a code leads to, shown before the person joins. A pack cover shows as is; an uploaded one needs membership, so the type's picture stands in. */
@Composable
private fun JoinPreviewCard(p: InvitePreview) {
    MMCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            CircleCoverImage(p.coverPath, p.template, p.space, circleId = null, modifier = Modifier.size(56.dp).clip(MM.radius.card), thumb = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                Text(p.name, style = MM.type.heading, color = MM.colors.ink)
                Text(
                    "${spaceLabel(p.space)} · ${p.memberCount} ${if (p.memberCount == 1) "member" else "members"}",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
                p.inviterName?.let { Text("Invited by $it", style = MM.type.secondary, color = MM.colors.inkSecondary) }
            }
        }
    }
}

@Composable
fun JoinSheet(
    join: JoinUiState,
    onDismiss: () -> Unit,
    onPaste: (String) -> Unit,
    onName: (String) -> Unit,
    onJoin: () -> Unit,
    account: AccountUiState? = null,
    onLink: (android.content.Context) -> Unit = {},
    onSwitch: () -> Unit = {},
    onDismissConflict: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    CircleSheet(onDismiss) {
        Text("Join a circle", style = MM.type.title, color = MM.colors.ink, modifier = Modifier.semantics { heading() })
        Text("Paste the code, the link, or the whole message you were sent.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        if (account != null && !account.linked) {
            CirclesSignInPrompt(account, "Joining from a new phone? Sign in first and your other circles come with you.", onLink, onSwitch, onDismissConflict)
        }
        CircleField(
            join.input, onPaste, "Invite code or message", placeholder = "GRACE-7K2Q", minLines = 2, maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction("Paste from clipboard", { clipboard.getText()?.text?.let(onPaste) })
        }
        if (join.input.isNotBlank()) {
            if (join.code != null) StatusLine(StatusKind.Info, "Found the code ${join.code}.")
            else StatusLine(StatusKind.Warning, "No code found yet. A code looks like GRACE-7K2Q.")
        }
        join.preview?.let { JoinPreviewCard(it) }
        join.previewError?.let { StatusLine(StatusKind.Warning, it) }
        CircleField(join.name, onName, "Your name in this circle", placeholder = "How should people see you?")
        join.error?.let { StatusLine(StatusKind.Error, it) }
        if (join.pending) StatusLine(StatusKind.Info, "Request sent. An admin will let you in once they've checked it.")
        if (!join.pending) PrimaryButton(if (join.busy) "Joining..." else "Join", onJoin, enabled = join.canJoin, modifier = Modifier.fillMaxWidth())
    }
}
