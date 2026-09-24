package com.example.feature.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.calendar.CalendarEvent
import com.example.core.calendar.UpNext
import com.example.core.identity.LocalAppLook
import com.example.core.model.RecordingType
import com.example.core.timeline.DeepTarget
import com.example.core.timeline.Greetings
import com.example.core.timeline.ItemKind
import com.example.core.timeline.Rhythms
import com.example.core.timeline.TimelineDays
import com.example.core.timeline.TimelineItem
import com.example.core.timeline.TimelineLayer
import java.util.Date

private val InkNavy = Color(0xFF0F172A)
private val Slate = Color(0xFF64748B)
private val Hairline = Color(0xFFE2E8F0)

/**
 * Home as the Today hub (PLAN_V2 F1): the hero, "for you" cards, the week strip and the calendar
 * in five views. Every card opens what it stands for.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenNote: (String) -> Unit,
    onOpenProcessing: (String) -> Unit,
    onRecord: () -> Unit,
    onRecordType: (RecordingType) -> Unit,
    onRecordEvent: (noteId: String, type: RecordingType, title: String, speakers: Int?) -> Unit,
    onSearch: () -> Unit,
    onNavigateBottomNav: (com.example.core.ui.BottomNavDestination) -> Unit,
    onOpenDevotional: () -> Unit = {}
) {
    val devotional by viewModel.devotional.collectAsState()
    val identity by viewModel.identity.collectAsState()
    val look = LocalAppLook.current
    val view by viewModel.view.collectAsState()
    val items by viewModel.items.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val activity by viewModel.activity.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val upNext by viewModel.upNext.collectAsState()
    val rhythm by viewModel.rhythm.collectAsState()
    val weekReview by viewModel.weekReview.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val contextLine by viewModel.contextLine.collectAsState()
    val jobs by viewModel.activeJobs.collectAsState()
    val focus by viewModel.focus.collectAsState()
    val layers by viewModel.layers.collectAsState()
    val calendarOn by viewModel.calendarOn.collectAsState()
    val promptDismissed by viewModel.calendarPromptDismissed.collectAsState()
    val now by viewModel.now.collectAsState()

    val listState = rememberLazyListState()
    var quick by remember { mutableStateOf<TimelineItem?>(null) }
    var showInbox by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.setAvatar(uri) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.setCalendarEnabled(true) }

    // Refresh each minute while Home is showing.
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); viewModel.tick() } }

    fun openEvent(e: CalendarEvent) = viewModel.noteForEvent(e) { id, _ -> onOpenNote(id) }
    fun recordEvent(e: CalendarEvent) = viewModel.noteForEvent(e) { id, type -> onRecordEvent(id, type, e.title, UpNext.speakerCount(e)) }
    fun open(item: TimelineItem) = when (val t = item.target) {
        is DeepTarget.Note -> onOpenNote(t.noteId)
        is DeepTarget.Recording -> onOpenProcessing(t.meetingId)
        is DeepTarget.Event -> openEvent(t.event)
    }

    val fmt = timeFormat()
    val tile = when (val u = upNext) {
        is UpNextTile.Event -> HeroTile(
            label = "Up next · " + UpNext.whenLabel(u.event, now, fmt),
            title = u.event.title,
            subtitle = u.prep?.let { p -> "Last time: ${p.lastTitle ?: "with " + p.sharedPeople.joinToString()}" }
                ?: listOfNotNull(u.event.otherPeople.takeIf { it.isNotEmpty() }?.let { if (it.size <= 2) it.joinToString() else "${it.first()} +${it.size - 1}" }, u.event.location).joinToString(" · ").ifBlank { "Tap to open or record" },
            icon = HeroTiles.eventIcon(), accent = u.event.color?.let { Color(it) } ?: look.accent,
            onClick = { if (u.prep?.lastNoteId != null && u.event.begin - now > 5 * 60_000) onOpenNote(u.prep.lastNoteId) else openEvent(u.event) }
        )
        is UpNextTile.Item -> HeroTile(
            label = "Today", title = u.item.title, subtitle = u.item.subtitle ?: timeLabel(u.item, fmt),
            icon = itemIcon(u.item), accent = Color(u.item.accent), onClick = { open(u.item) }
        )
        UpNextTile.Nothing -> if (identity.faithFirst) HeroTile(
            label = "Today's devotional", title = devotional?.devotional?.title ?: "Start today with God",
            subtitle = devotional?.devotional?.scripture?.firstOrNull()?.display() ?: "Scripture, a reflection and a prayer",
            icon = Icons.Filled.WbSunny, accent = Color(0xFFB7791F), onClick = onOpenDevotional
        ) else HeroTile(
            label = "Nothing scheduled", title = "Capture something",
            subtitle = "Tap to record", icon = HeroTiles.recordIcon(), accent = look.accent, onClick = onRecord
        )
    }
    val inboxCount = jobs.size + (if (rhythm != null) 1 else 0) + (if ((upNext as? UpNextTile.Event)?.prep != null) 1 else 0) + (if (memories.isNotEmpty()) 1 else 0)
    val switchTo = when (focus) { TodayFocus.ALL -> TodayFocus.FAITH; TodayFocus.FAITH -> TodayFocus.WORK; TodayFocus.WORK -> TodayFocus.ALL }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            com.example.core.ui.AppBottomNavigationBar(
                current = com.example.core.ui.BottomNavDestination.HOME,
                onNavigate = { if (it != com.example.core.ui.BottomNavDestination.HOME) onNavigateBottomNav(it) }
            )
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp)) {
            item(key = "hero") {
                HomeHeroHeader(
                    identity = identity,
                    greeting = remember(identity, now / 3_600_000) { Greetings.pick(identity) },
                    contextLine = contextLine,
                    streakLabel = stats.streakLabel,
                    weekLabel = stats.weekLabel,
                    inboxCount = inboxCount,
                    showSwitch = viewModel.canSwitchFocus(identity),
                    switchLabel = switchTo.label,
                    tile = tile,
                    listState = listState,
                    onAvatar = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onSearch = onSearch,
                    onInbox = { showInbox = true },
                    onSwitch = { viewModel.setFocus(switchTo) }
                )
            }
            if (focus != TodayFocus.ALL) item(key = "focus") {
                Surface(onClick = { viewModel.setFocus(TodayFocus.ALL) }, shape = CircleShape, color = look.accentSoft, modifier = Modifier.padding(start = 20.dp, top = 6.dp)) {
                    Text("Showing ${focus.label} · tap for everything", color = look.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }

            // For you: rhythms, memories, the week, recordings in progress, the calendar invitation.
            val forYou = buildList<@Composable () -> Unit> {
                if (identity.showsFaith && focus != TodayFocus.WORK) add {
                    val d = devotional?.devotional
                    ForYouCard(
                        Icons.Filled.WbSunny, Color(0xFFB7791F), "Today's devotional",
                        d?.title ?: "A word for your day",
                        d?.let { listOfNotNull(it.scripture.firstOrNull()?.display(), if (devotional?.note?.metadata?.get(com.example.core.devotional.DevotionalNotes.META_OPENED) == null) "New" else "Read").joinToString(" · ") }
                            ?: "Scripture, a reflection and a prayer"
                    ) { onOpenDevotional() }
                }
                jobs.forEach { job -> add { ForYouCard(Icons.Filled.Sync, look.accent, "Processing · ${job.progressPercent}%", job.meetingTitle, job.currentStep) { onOpenProcessing(job.meetingId) } } }
                rhythm?.let { r -> add { ForYouCard(Icons.Filled.Mic, Color(0xFFE11D48), "Your rhythm", "Record ${r.type.displayName.lowercase()}?", Rhythms.describe(r)) { onRecordType(r.type) } } }
                memories.firstOrNull()?.let { m -> add { ForYouCard(Icons.Filled.History, Color(0xFF8B5CF6), "On this day", m.title, m.subtitle ?: "") { open(m) } } }
                weekReview?.let { w -> add {
                    ForYouCard(Icons.Filled.Insights, look.accent, "Your week",
                        listOfNotNull(w.recordings.takeIf { it > 0 }?.let { "$it recordings" }, w.notes.takeIf { it > 0 }?.let { "$it notes" }, w.faith.takeIf { it > 0 }?.let { "$it faith moments" }).joinToString(" · "),
                        listOfNotNull(w.minutesRecorded.takeIf { it > 0 }?.let { "$it minutes captured" }, w.answered.takeIf { it > 0 }?.let { "$it answered prayers 🙌" }).joinToString(" · ")
                    ) { viewModel.setView(CalendarView.WEEK) }
                } }
                if (calendarOn == false && !promptDismissed) add {
                    ForYouCard(Icons.Filled.Event, Color(0xFF4F46E5), "Your calendar", "See what's up next", "Show events from your phone's calendar. Read only.",
                        onDismiss = { viewModel.dismissCalendarPrompt() }) {
                        calendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
                    }
                }
            }
            if (forYou.isNotEmpty()) item(key = "foryou") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    items(forYou.size) { i -> forYou[i]() }
                }
            }

            // Calendar controls.
            item(key = "controls") {
                Column(Modifier.padding(top = 18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.shift(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Earlier", tint = InkNavy) }
                        Text(
                            java.text.SimpleDateFormat(if (view == CalendarView.DAY) "EEEE d MMMM" else "MMMM yyyy", java.util.Locale.getDefault()).format(Date(selectedDay)),
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFamily, color = InkNavy,
                            modifier = Modifier.weight(1f).clickable { showJump = true }.testTag("today_period")
                        )
                        if (TimelineDays.key(selectedDay) != TimelineDays.key(System.currentTimeMillis())) {
                            TextButton(onClick = { viewModel.goToday() }) { Text("Today", color = look.accent) }
                        }
                        IconButton(onClick = { viewModel.shift(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Later", tint = InkNavy) }
                        IconButton(onClick = { showLayers = true }, modifier = Modifier.testTag("today_layers")) { Icon(Icons.Filled.Layers, contentDescription = "What shows", tint = InkNavy) }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(CalendarView.entries) { v ->
                            val on = v == view
                            Surface(onClick = { viewModel.setView(v) }, shape = CircleShape, color = if (on) InkNavy else Color.White, border = if (on) null else BorderStroke(1.dp, Hairline),
                                modifier = Modifier.testTag("view_${v.name.lowercase()}")) {
                                Text(v.label, color = if (on) Color.White else InkNavy, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                            }
                        }
                    }
                    if (view == CalendarView.AGENDA || view == CalendarView.DAY || view == CalendarView.WEEK) {
                        WeekStrip(selectedDay, activity, look.accent, onSelect = { viewModel.select(it) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                    } else Spacer(Modifier.height(12.dp))
                }
            }

            when (view) {
                CalendarView.AGENDA -> item(key = "agenda") {
                    AgendaView(items, selectedDay, ::open, { quick = it }, onPlan = { day -> viewModel.planNote(day, onOpenNote) })
                }
                CalendarView.DAY -> item(key = "day") { DayView(items, selectedDay, ::open, { quick = it }, onPlan = { day -> viewModel.planNote(day, onOpenNote) }) }
                CalendarView.WEEK -> item(key = "week") {
                    WeekView(items, TimelineDays.startOfWeek(selectedDay), onDay = { viewModel.select(it); viewModel.setView(CalendarView.DAY) }, onOpen = ::open)
                }
                CalendarView.MONTH -> {
                    item(key = "month") {
                        MonthView(items, TimelineDays.startOfWeek(TimelineDays.startOfMonth(selectedDay)), selectedDay, selectedDay) { viewModel.select(it) }
                    }
                    val dayItems = items.filter { it.dayKey == TimelineDays.key(selectedDay) }
                    item(key = "month-day") {
                        Text(java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(Date(selectedDay)),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkNavy, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
                    }
                    if (dayItems.isEmpty()) item(key = "month-empty") { Text("Nothing on this day.", color = Slate, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
                    items(dayItems, key = { "m-" + it.id }) { TimelineCard(it, ::open, { q -> quick = q }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                }
                CalendarView.RIVER -> {
                    // Newest first, grouped by month, with big picture cards.
                    val byMonth = items.filter { it.start <= now + 14 * 86_400_000L }.sortedByDescending { it.start }
                        .groupBy { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(Date(it.start)) }
                    byMonth.forEach { (month, list) ->
                        stickyHeader(key = "h-$month") {
                            Text(month, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFamily, color = InkNavy,
                                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.96f)).padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        items(list, key = { "r-" + it.id }) { item ->
                            TimelineCard(item, ::open, { quick = it }, Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateItem(),
                                height = if (item.coverPath != null) 190.dp else 132.dp, showDate = true)
                        }
                    }
                    item(key = "river-more") {
                        TextButton(onClick = { viewModel.loadEarlier() }, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Show earlier", color = look.accent) }
                    }
                }
            }
        }
    }

    quick?.let { item ->
        QuickActions(
            item = item,
            onOpen = { quick = null; open(item) },
            onRecord = {
                quick = null
                when (val t = item.target) {
                    is DeepTarget.Event -> recordEvent(t.event)
                    else -> item.noteId?.let { id -> onRecordEvent(id, item.workflow ?: RecordingType.GENERAL, item.title, null) }
                }
            },
            onDismiss = { quick = null }
        )
    }
    if (showInbox) InboxSheet(
        jobs = jobs.map { it.meetingId to "${it.meetingTitle} · ${it.progressPercent}%" },
        prep = (upNext as? UpNextTile.Event)?.let { u -> u.prep?.let { p -> Triple(u.event.title, p, u.event) } },
        rhythm = rhythm, memories = memories,
        onJob = { showInbox = false; onOpenProcessing(it) },
        onNote = { showInbox = false; onOpenNote(it) },
        onRecordType = { showInbox = false; onRecordType(it) },
        onDismiss = { showInbox = false }
    )
    if (showLayers) LayersSheet(
        layers = layers, allowed = TimelineLayer.defaultsFor(identity), calendarOn = calendarOn == true,
        onLayer = { l, on -> viewModel.setLayer(l, on) },
        onCalendar = { on -> if (on) calendarPermission.launch(android.Manifest.permission.READ_CALENDAR) else viewModel.setCalendarEnabled(false) },
        onDismiss = { showLayers = false }
    )
    if (showJump) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDay)
        DatePickerDialog(
            onDismissRequest = { showJump = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { viewModel.select(it) }; showJump = false }) { Text("Go") } },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
}

@Composable
private fun ForYouCard(icon: ImageVector, tint: Color, label: String, title: String, subtitle: String, onDismiss: (() -> Unit)? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, Hairline), shadowElevation = 2.dp,
        modifier = Modifier.width(250.dp).height(118.dp).testTag("for_you_card")) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.6f)))), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1)
                if (onDismiss != null) Icon(
                    androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Not now", tint = Slate,
                    modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onDismiss)
                )
            }
            Text(title, color = InkNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFamily, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(subtitle, color = Slate, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActions(item: TimelineItem, onOpen: () -> Unit, onRecord: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(item.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = InkNavy, maxLines = 2)
            item.subtitle?.let { Text(it, fontSize = 13.sp, color = Slate) }
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Filled.OpenInNew, "Open", onOpen)
            if (item.kind == ItemKind.EVENT || item.noteId != null) ActionRow(Icons.Filled.Mic, if (item.kind == ItemKind.EVENT) "Record this" else "Record into this note", onRecord)
            if (item.kind == ItemKind.EVENT) ActionRow(Icons.Filled.EditNote, "Take notes", onOpen)
            ActionRow(Icons.Filled.Share, "Share") {
                val text = listOfNotNull(item.title, item.subtitle, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(Date(item.start))).joinToString("\n")
                runCatching { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Share")) }
                onDismiss()
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = InkNavy, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 16.sp, color = InkNavy, modifier = Modifier.padding(start = 14.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxSheet(
    jobs: List<Pair<String, String>>,
    prep: Triple<String, com.example.core.timeline.MeetingPrep.Prep, CalendarEvent>?,
    rhythm: Rhythms.Rhythm?,
    memories: List<TimelineItem>,
    onJob: (String) -> Unit,
    onNote: (String) -> Unit,
    onRecordType: (RecordingType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text("For you", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = InkNavy)
            if (jobs.isEmpty() && prep == null && rhythm == null && memories.isEmpty()) {
                Text("You're all caught up.", color = Slate, modifier = Modifier.padding(vertical = 16.dp))
            }
            jobs.forEach { (id, line) -> InboxRow(Icons.Filled.Sync, "Processing", line) { onJob(id) } }
            prep?.let { (title, p, _) ->
                InboxRow(Icons.Filled.Event, "Before $title", "Last time with ${p.sharedPeople.joinToString()}: ${p.lastTitle.orEmpty()}") { p.lastNoteId?.let(onNote) }
            }
            rhythm?.let { r -> InboxRow(Icons.Filled.Mic, "Your rhythm", Rhythms.describe(r)) { onRecordType(r.type) } }
            memories.forEach { m -> InboxRow(Icons.Filled.History, m.subtitle ?: "On this day", m.title) { m.noteId?.let(onNote) } }
        }
    }
}

@Composable
private fun InboxRow(icon: ImageVector, label: String, line: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = InkNavy, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(label, fontSize = 12.sp, color = Slate)
            Text(line, fontSize = 15.sp, color = InkNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(
    layers: Set<TimelineLayer>,
    allowed: Set<TimelineLayer>,
    calendarOn: Boolean,
    onLayer: (TimelineLayer, Boolean) -> Unit,
    onCalendar: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text("What shows on your calendar", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = InkNavy)
            Text("Spaces you don't use (Settings → Personalize) never appear.", fontSize = 13.sp, color = Slate, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            TimelineLayer.entries.filter { it in allowed }.forEach { layer ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(layerColor(layer)))
                    Text(layer.label, fontSize = 16.sp, color = InkNavy, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Switch(checked = layer in layers, onCheckedChange = { onLayer(layer, it) }, modifier = Modifier.testTag("layer_${layer.name.lowercase()}"))
                }
            }
            HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Phone calendar events", fontSize = 16.sp, color = InkNavy)
                    Text("Read only, never leaves the phone", fontSize = 12.sp, color = Slate)
                }
                Switch(checked = calendarOn, onCheckedChange = onCalendar)
            }
        }
    }
}
