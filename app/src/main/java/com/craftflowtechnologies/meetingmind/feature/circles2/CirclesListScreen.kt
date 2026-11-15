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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
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
    val streak by vm.streak.collectAsState()
    val account: AccountViewModel = viewModel(factory = AccountViewModel.Factory(repo))
    val accountState by account.state.collectAsState()
    val askNotifications = rememberNotificationAsk()
    var showJoin by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf("Not run") }
    var checking by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.refreshEngagement() }
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
        onCreateInSpace = onCreateInSpace,
        streak = streak
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
    onCreateInSpace: ((String?) -> Unit)? = null,
    streak: CircleStreakUi = CircleStreakUi()
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
    Column(Modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding()) {
        ScreenHeader(
            "Circles", Modifier.padding(horizontal = MM.space.s),
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.inkSecondary) } },
            actions = {
                if (canAct) {
                    if (hasManyCircles) {
                        Surface(
                            shape = CircleShape,
                            color = if (searchOpen) MM.colors.accentWash else MM.colors.surfaceRaised,
                            border = BorderStroke(MMSize.hairline, if (searchOpen) MM.colors.accent else MM.colors.lineSoft),
                            modifier = Modifier.size(34.dp).clickable {
                                searchOpen = !searchOpen
                                if (!searchOpen) searchQuery = ""
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Search,
                                    "Search circles",
                                    tint = if (searchOpen) MM.colors.accent else MM.colors.inkSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Surface(
                        shape = MM.radius.pill,
                        color = MM.colors.accentWash,
                        modifier = Modifier.clickable(onClick = onShowJoin)
                    ) {
                        Text(
                            "Join",
                            style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                            color = MM.colors.accent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = MM.radius.pill,
                        color = MM.colors.accent,
                        modifier = Modifier.size(34.dp).clickable(onClick = createForActiveSpace)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, "Start a circle", tint = MM.colors.onAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        )
        AnimatedVisibility(
            visible = searchOpen && hasManyCircles,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.xs)) {
                CircleField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search circles",
                    placeholder = "Filter by name or description…",
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MM.colors.inkSecondary) }
                )
            }
        }
        when (state) {
            CirclesListState.NotConnected -> EmptyState(
                title = "Circles isn't connected yet",
                body = "Circles lets small groups share updates, resources, and chat together. This build isn't linked to the Circles service yet, so there's nothing to join or create. Everything else in MeetingMind works as normal.",
                modifier = Modifier.padding(horizontal = MM.space.l),
                illustration = { Icon(Icons.Rounded.Groups, null, tint = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.xl)) }
            )
            CirclesListState.Loading -> Column(Modifier.padding(MM.space.l)) { StatusLine(StatusKind.Info, "Loading your circles...") }
            is CirclesListState.Ready -> if (state.circles.isEmpty()) {
                Column {
                    state.note?.let { Note(it, onRefresh) }
                    EmptyState(
                        title = "No circles yet",
                        body = "A circle is a small private space for your team, study group, or faith community to share updates, resources, and chat.",
                        modifier = Modifier.padding(horizontal = MM.space.l),
                        action = {
                            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s), horizontalAlignment = Alignment.CenterHorizontally) {
                                PrimaryButton("Start a circle", createForActiveSpace, leadingIcon = Icons.Rounded.Add)
                                SecondaryButton("Join with a code", onShowJoin)
                            }
                        }
                    )
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
                        contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s),
                        verticalArrangement = Arrangement.spacedBy(MM.space.m)
                    ) {
                        state.note?.let { n -> item { Note(n, onRefresh) } }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                Text("Your circles", style = MM.type.title.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                                Text(
                                    "Small groups, teams, and family spaces where you share and stay close.",
                                    style = MM.type.secondary, color = MM.colors.inkSecondary
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val distinctSpacesCount = remember(state.circles) {
                                        state.circles.map { circleSpace(it) }.distinct().size
                                    }
                                    Surface(
                                        shape = MM.radius.pill,
                                        color = MM.colors.surfaceSunk,
                                        border = BorderStroke(MMSize.hairline, MM.colors.lineSoft)
                                    ) {
                                        Text(
                                            "${state.circles.size} ${if (state.circles.size == 1) "circle" else "circles"}",
                                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                            color = MM.colors.inkSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = MM.radius.pill,
                                        color = MM.colors.surfaceSunk,
                                        border = BorderStroke(MMSize.hairline, MM.colors.lineSoft)
                                    ) {
                                        Text(
                                            "$distinctSpacesCount ${if (distinctSpacesCount == 1) "space" else "spaces"}",
                                            style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                            color = MM.colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (state.circles.size > 1) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                                ) {
                                    listOf("All", "Work", "Study", "Faith", "Community").forEach { spaceName ->
                                        val count = if (spaceName == "All") state.circles.size else state.circles.count { circleSpace(it) == spaceName }
                                        val label = if (count > 0 && spaceName != "All") "$spaceName · $count" else spaceName
                                        MMChip(
                                            label,
                                            selected = selectedSpace == spaceName,
                                            onClick = { selectedSpace = spaceName }
                                        )
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
                                            body = "No circles match \"$searchQuery\". Try checking the spelling or clearing the search filter.",
                                            modifier = Modifier.padding(vertical = MM.space.l)
                                        )
                                    } else {
                                        EmptyState(
                                            title = "No $selectedSpace circles",
                                            body = "You don't have any $selectedSpace circles yet. You can create one or join with an invite code.",
                                            modifier = Modifier.padding(vertical = MM.space.l)
                                        )
                                    }
                                }
                            }
                        } else {
                            val featured = filteredCircles.first()
                            item(key = "featured_${featured.id}") {
                                FeaturedCircleCard(featured, modifier = Modifier.animateItem()) { onOpen(featured.id) }
                            }
                            item(key = "daily_circle_pulse") {
                                DailyCirclePulseCard(
                                    streak = streak,
                                    onOpenCircle = { onOpen(featured.id) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                            if (filteredCircles.size > 1) {
                                item(key = "all_circles_section_header") {
                                    Box(Modifier.animateItem()) {
                                        SectionHeader("All circles", count = filteredCircles.size)
                                    }
                                }
                                items(filteredCircles.drop(1), key = { it.id }) { c ->
                                    CircleRow(c, modifier = Modifier.animateItem()) { onOpen(c.id) }
                                }
                            }
                        }
                        if (account != null) item { KeepCirclesOffer(account, onLink, onSwitch, onDismissConflict) }
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(top = MM.space.s),
                                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PrimaryButton("Start a circle", createForActiveSpace, leadingIcon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                                SecondaryButton("Join with code", onShowJoin, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showJoin) JoinSheet(join, onHideJoin, onPaste, onJoinName, onJoin)
}

@Composable
private fun Note(f: CirclesFailure, onRefresh: () -> Unit) {
    Column(Modifier.padding(horizontal = MM.space.l)) { StatusLine(StatusKind.Warning, f.message, actionLabel = "Retry", onAction = onRefresh) }
}

/** Featured Circle hero card matching `circles_home.html` (`170dp` cover + space-adapted lower banner + white `Open` pill). */
@Composable
private fun FeaturedCircleCard(c: Circle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val memberLabel = "${c.groupWord} · ${if (c.memberCount == 1) "1 member" else "${c.memberCount} members"}"
    val spaceLabel = circleSpace(c)
    val tint = spaceTint(c)
    Surface(
        shape = MM.radius.card,
        color = tint,
        modifier = modifier.fillMaxWidth().clip(MM.radius.card).clickable(onClick = onClick)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(CirclesPalette.CoverStart, tint, CirclesPalette.CoverEnd)
                        )
                    )
            ) {
                if (c.coverPath != null) {
                    MediaImage(c.id, c.coverPath, Modifier.fillMaxSize(), "Cover photo for ${c.name}")
                } else {
                    Box(Modifier.fillMaxSize().padding(MM.space.l), contentAlignment = Alignment.BottomStart) {
                        InitialsAvatar(c.name, size = 52.dp)
                    }
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MM.colors.scrim.copy(alpha = 0.75f))
                            )
                        )
                )
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MM.radius.pill,
                        color = MM.colors.scrim
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(spaceIcon(c), contentDescription = null, tint = FixedWhite, modifier = Modifier.size(13.dp))
                            Text(
                                spaceLabel,
                                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                color = FixedWhite
                            )
                        }
                    }
                    Surface(
                        shape = MM.radius.pill,
                        color = MM.colors.scrim
                    ) {
                        Text(
                            memberLabel,
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = FixedWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                Text(c.name, style = MM.type.title.copy(fontWeight = FontWeight.Bold), color = MM.colors.onAccent)
                if (c.description.isNotBlank()) {
                    Text(c.description, style = MM.type.secondary, color = MM.colors.onAccent.copy(alpha = 0.90f), maxLines = 2)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.CenterVertically) {
                        InitialsAvatar(c.name, size = 28.dp)
                    }
                    Surface(
                        shape = MM.radius.pill,
                        color = MM.colors.onAccent,
                        modifier = Modifier.clickable(onClick = onClick)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Open", style = MM.type.secondary.copy(fontWeight = FontWeight.Bold), color = tint)
                            Icon(Icons.Rounded.ChevronRight, null, tint = tint, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleRow(c: Circle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val memberLabel = "${c.groupWord} · ${if (c.memberCount == 1) "1 member" else "${c.memberCount} members"}"
    val spaceLabel = circleSpace(c)
    val tint = spaceTint(c)
    MMCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            if (c.coverPath != null) {
                MediaImage(
                    c.id, c.coverPath,
                    Modifier.size(48.dp).clip(MM.radius.card),
                    "Cover photo for ${c.name}"
                )
            } else {
                InitialsAvatar(c.name, size = 48.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Text(
                        c.name,
                        style = MM.type.bodyStrong,
                        color = MM.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = MM.radius.pill,
                        color = tint.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(spaceIcon(c), null, tint = tint, modifier = Modifier.size(11.dp))
                            Text(
                                spaceLabel,
                                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = tint
                            )
                        }
                    }
                }
                Text(memberLabel, style = MM.type.caption, color = MM.colors.inkSecondary)
                if (c.description.isNotBlank()) {
                    Text(
                        c.description,
                        style = MM.type.caption,
                        color = MM.colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun JoinSheet(join: JoinUiState, onDismiss: () -> Unit, onPaste: (String) -> Unit, onName: (String) -> Unit, onJoin: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    CircleSheet(onDismiss) {
        Text("Join a circle", style = MM.type.title, color = MM.colors.ink)
        Text("Paste the code, the link, or the whole message you were sent.", style = MM.type.secondary, color = MM.colors.inkSecondary)
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
        CircleField(join.name, onName, "Your name in this circle", placeholder = "How should people see you?")
        join.error?.let { StatusLine(StatusKind.Error, it) }
        if (join.pending) StatusLine(StatusKind.Info, "Request sent. An admin will let you in once they've checked it.")
        if (!join.pending) PrimaryButton(if (join.busy) "Joining..." else "Join", onJoin, enabled = join.canJoin, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DailyCirclePulseCard(
    streak: CircleStreakUi,
    onOpenCircle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fireTint = if (streak.checkedInToday) MM.colors.gold else MM.colors.accent
    MMCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = MM.space.s),
                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = fireTint,
                        modifier = Modifier.size(MMSize.iconSmall)
                    )
                    Text(
                        if (streak.days > 0) "${streak.days}-day streak" else "Start your streak today",
                        style = MM.type.bodyStrong,
                        color = MM.colors.ink
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    if (streak.checkedInToday) {
                        Surface(
                            shape = MM.radius.pill,
                            color = MM.colors.accentWash,
                            border = BorderStroke(MMSize.hairline, MM.colors.accent.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MM.colors.accent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    "Checked in today",
                                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = MM.colors.accent
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = MM.radius.pill,
                            color = MM.colors.surfaceSunk,
                            border = BorderStroke(MMSize.hairline, MM.colors.lineSoft)
                        ) {
                            Text(
                                "Open a circle to check in",
                                style = MM.type.caption,
                                color = MM.colors.inkSecondary,
                                modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                            )
                        }
                    }
                }
            }
            Surface(
                shape = MM.radius.pill,
                color = MM.colors.accentWash,
                border = BorderStroke(MMSize.hairline, MM.colors.accent),
                modifier = Modifier.clickable(onClick = onOpenCircle)
            ) {
                Text(
                    if (streak.checkedInToday) "Continue" else "Check in now",
                    style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                    color = MM.colors.accent,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                )
            }
        }
    }
}
