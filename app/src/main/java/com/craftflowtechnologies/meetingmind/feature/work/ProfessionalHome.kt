package com.craftflowtechnologies.meetingmind.feature.work

import com.craftflowtechnologies.meetingmind.ui.theme.Briefing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.calendar.UpNext
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.timeline.DeepTarget
import com.craftflowtechnologies.meetingmind.core.timeline.ItemKind
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import kotlinx.coroutines.launch
import com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel
import com.craftflowtechnologies.meetingmind.feature.today.UpNextTile
import com.craftflowtechnologies.meetingmind.feature.today.itemIcon
import com.craftflowtechnologies.meetingmind.feature.today.timeFormat
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import com.craftflowtechnologies.meetingmind.core.timeline.Greetings
import com.craftflowtechnologies.meetingmind.core.timeline.TimeOfDaySky
import com.craftflowtechnologies.meetingmind.feature.today.HomeQuickAccessRow
import java.util.Calendar

/** The briefing card's own palette: a deep executive ink, the same in light and dark. */
private val BriefTop = Briefing.Top
private val BriefBottom = Briefing.Bottom
private val BriefLine = Briefing.Lavender

/**
 * The fourth home: Professional (docs/PLAN_PROFESSIONAL.md §7). A briefing for the working day —
 * what's next and what to remember, what needs you, the day's schedule, what you owe and are owed,
 * projects and people — with every other part of home still here: capture, search, the timeline,
 * stories, the devotional for those who use Faith, and work in progress.
 */
@Composable
fun ProfessionalHome(
    today: TodayViewModel,
    work: WorkViewModel,
    onRecord: () -> Unit,
    onRecordType: (RecordingType) -> Unit,
    onRecordEvent: (noteId: String, type: RecordingType, title: String, speakers: Int?) -> Unit,
    onNewNote: () -> Unit,
    onImport: () -> Unit,
    onSearch: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenProcessing: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenDevotional: () -> Unit,
    onOpenStories: (com.craftflowtechnologies.meetingmind.feature.stories.StoryKind?) -> Unit,
    onOpenWork: () -> Unit,
    onOpenFaith: () -> Unit = {},
    onOpenPrayer: () -> Unit = {},
    onOpenWord: () -> Unit = {},
    onOpenTestimonies: () -> Unit = {},
    onOpenAll: (WorkTab) -> Unit,
    onOpenWrapUp: (String, Boolean) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onCustomize: () -> Unit,
    onOpenContext: (com.craftflowtechnologies.meetingmind.core.work.ContextType, String) -> Unit = { _, _ -> },
    onOpenInbox: () -> Unit = {},
    onShare: (com.craftflowtechnologies.meetingmind.feature.share.ShareRequest) -> Unit = {},
    onNavigateBottomNav: (com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit
) {
    var showSparkStudio by remember { mutableStateOf(false) }
    if (showSparkStudio) {
        com.craftflowtechnologies.meetingmind.feature.faith.SparkStudioSheet(
            onDismiss = { showSparkStudio = false },
            onShareStory = { req ->
                showSparkStudio = false
                onShare(req)
            }
        )
    }
    val identity by today.identity.collectAsState()
    val upNext by today.upNext.collectAsState()
    val now by today.now.collectAsState()
    val todayItems by today.today.collectAsState()
    val jobs by today.activeJobs.collectAsState()
    val stories by today.stories.collectAsState()
    val devotional by today.devotional.collectAsState()
    val settings by work.settings.collectAsState()
    val myTasks by work.myTasks.collectAsState()
    val waitingOn by work.waitingOn.collectAsState()
    val toReview by work.toReview.collectAsState()
    val followUps by work.followUps.collectAsState()
    val decisions by work.decisions.collectAsState()
    val projects by work.projects.collectAsState()
    val people by work.everyone.collectAsState()
    val titles by work.titles.collectAsState()
    var nudging by remember { mutableStateOf<WorkTask?>(null) }
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var preparing by remember { mutableStateOf<PrepTarget?>(null) }
    var askOpen by remember { mutableStateOf(false) }
    val pulse by work.pulse.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); today.tick() } }
    // "What changed" is measured from the last time the Pulse was read, so it counts as read after two seconds.
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(2_000); work.markPulseSeen() }
    LaunchedEffect(todayItems, now / 60_000) {
        work.setPulseEvents(todayItems.mapNotNull { (it.target as? DeepTarget.Event)?.event }.filter { it.end >= now && !it.allDay }.map(::pulseEventOf))
    }
    val fmt = timeFormat()
    val dueToday = myTasks.filter { it.dueAt != null && it.dueAt < com.craftflowtechnologies.meetingmind.core.work.DueDates.startOfDay(now) + 86_400_000L }
    val overdue = myTasks.count { it.isOverdue(now) }
    val meetingsToday = todayItems.count { it.kind == ItemKind.EVENT }
    val weekDecisions = decisions.count { it.createdAt > now - 7 * 86_400_000L }

    Scaffold(
        containerColor = SurfaceBase,
        bottomBar = {
            com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar(
                current = com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME,
                onNavigate = { if (it != com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME) onNavigateBottomNav(it) }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("professional_home"), contentPadding = PaddingValues(bottom = 36.dp)) {
            // Masthead: executive sky header following time of day
            item {
                ProfessionalSkyHeader(
                    identity = identity,
                    now = now,
                    meetingsToday = meetingsToday,
                    dueTodayCount = dueToday.size,
                    onSearch = onSearch,
                    onCustomize = onCustomize,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp)
                )
            }

            // Quick Access icons for Faith, Work, Devotional, Prayer, Word, Testimonies
            item {
                HomeQuickAccessRow(
                    onOpenFaith = onOpenFaith,
                    onOpenWork = onOpenWork,
                    onOpenDevotional = onOpenDevotional,
                    onOpenPrayer = onOpenPrayer,
                    onOpenWord = onOpenWord,
                    onOpenTestimonies = onOpenTestimonies,
                    onOpenSpark = { showSparkStudio = true },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // The Work Pulse: what needs you, what changed, and today with what's still open (D5.1).
            item {
                PulseCard(
                    ui = pulse.copy(summary = listOfNotNull(
                        "$meetingsToday ${if (meetingsToday == 1) "meeting" else "meetings"} today",
                        toReview.size.takeIf { it > 0 }?.let { "$it to review" },
                        overdue.takeIf { it > 0 }?.let { "$it overdue" }
                    ).joinToString("  ·  ")),
                    timeFormat = fmt,
                    actions = PulseActions(
                        onPlay = { r -> r.meetingId?.let { onOpenMeeting(it, r.startMs) } },
                        onNudge = { r -> scope.launch { nudging = work.asTask(r) } },
                        onPrepareRow = { r -> r.entityType?.let { t -> r.entityId?.let { id -> preparing = PrepTarget.Entity(t, id) } } },
                        onOpenRow = { r -> if (r.entityType != null && r.entityId != null) onOpenContext(r.entityType, r.entityId) else onOpenAll(WorkTab.MINE) },
                        onPlayChange = { l -> scope.launch { l.itemId?.let { work.evidenceOf(it) }?.let { (m, at) -> onOpenMeeting(m, at) } } },
                        onPrepareEvent = { e -> preparing = PrepTarget.Event(e) },
                        onRecordEvent = { e ->
                            (todayItems.firstNotNullOfOrNull { (it.target as? DeepTarget.Event)?.event?.takeIf { ev -> ev.key == e.key } })
                                ?.let { ev -> today.noteForEvent(ev) { id, type -> onRecordEvent(id, type, e.title, UpNext.speakerCount(ev)) } }
                        },
                        onAsk = { askOpen = true }, onRecord = onRecord, onTemplates = onOpenWork, onOpenInbox = onOpenInbox
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)
                )
            }

            // Capture, one tap each.
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    QuickAction(Icons.Filled.Mic, "Record", LocalMMColors.current.recording, onRecord)
                    QuickAction(Icons.AutoMirrored.Filled.NoteAdd, "Note", Accent, onNewNote)
                    QuickAction(Icons.Filled.AddTask, "Task", LocalMMColors.current.speaker3) { onOpenAll(WorkTab.MINE) }
                    QuickAction(Icons.Filled.FileUpload, "Import", LocalMMColors.current.speaker4, onImport)
                    QuickAction(Icons.Filled.Work, "Work", LocalMMColors.current.speaker2, onOpenWork)
                }
            }

            // Figures that matter this week.
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("${dueToday.size}", "Due today", if (overdue > 0) LocalMMColors.current.danger else Accent, Modifier.weight(1f)) { onOpenAll(WorkTab.MINE) }
                    Metric("${waitingOn.size}", "Waiting on", LocalMMColors.current.speaker3, Modifier.weight(1f)) { onOpenAll(WorkTab.WAITING) }
                    Metric("${followUps.size}", "To send", LocalMMColors.current.speaker4, Modifier.weight(1f)) { onOpenWork() }
                    Metric("$weekDecisions", "Decided", DecisionTint, Modifier.weight(1f)) { onOpenAll(WorkTab.DECISIONS) }
                }
            }

            if (jobs.isNotEmpty()) item {
                val j = jobs.first()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(AccentWash)
                        .clickable { onOpenProcessing(j.meetingId) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Processing · ${j.progressPercent}%", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold)
                    Text(j.meetingTitle, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f))
                }
            }

            // Needs you.
            if (toReview.isNotEmpty() || followUps.isNotEmpty()) {
                item { WorkSectionTitle("Needs you") }
                items(toReview.take(3), key = { "r-" + it.meetingId }) { r -> ActionCard(r.title, "${r.type.displayName} · ready to wrap up", "Wrap up") { onOpenWrapUp(r.meetingId, false) } }
                items(followUps.take(3), key = { "f-" + it.meetingId }) { r -> ActionCard(r.title, "Follow-up not sent yet · ${dayLabel(r.at)}", "Send") { onOpenWrapUp(r.meetingId, true) } }
            }

            // The day's schedule, as a timeline.
            val schedule = todayItems.sortedBy { it.start }
            item { WorkSectionTitle("Schedule", if (schedule.isEmpty()) null else "${schedule.size}") }
            if (schedule.isEmpty()) item { EmptyLine("Nothing on the calendar today. Turn on your phone's calendar in the Today home to see meetings here, with prep and one-tap record.") }
            items(schedule.size, key = { "s-" + schedule[it].id }) { i ->
                ScheduleRow(
                    schedule[i], fmt, now, last = i == schedule.lastIndex,
                    onPrepare = (schedule[i].target as? DeepTarget.Event)?.event?.takeIf { it.end >= now }?.let { ev -> { preparing = PrepTarget.Event(pulseEventOf(ev)) } }
                ) { open(schedule[i], today, onOpenNote, onOpenProcessing) }
            }

            // What you owe, and are owed.
            item { WorkSectionTitle("My tasks", if (myTasks.isNotEmpty()) "All ${myTasks.size}" else null, onTrailing = { onOpenAll(WorkTab.MINE) }) }
            if (myTasks.isEmpty()) item { EmptyLine("Nothing due. What you agree to in meetings lands here with its date.") }
            items(myTasks.take(4), key = { "t-" + it.id }) { t ->
                TaskLine(t, t.meetingId?.let { titles[it] }, { work.toggle(t) }, { editing = t }, t.meetingId?.let { m -> { onOpenMeeting(m, t.startMs) } })
            }
            if (waitingOn.isNotEmpty()) {
                item { WorkSectionTitle("Waiting on", "All ${waitingOn.size}", onTrailing = { onOpenAll(WorkTab.WAITING) }) }
                items(waitingOn.take(3), key = { "w-" + it.id }) { t ->
                    TaskLine(t, null, { work.toggle(t) }, { editing = t }, action = { TextButton(onClick = { nudging = t }) { Text("Nudge") } })
                }
            }

            // Projects and people.
            if (projects.isNotEmpty()) item {
                WorkSectionTitle(settings.terms.projects, "Work space", onTrailing = onOpenWork)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(projects, key = { "p-" + it.notebook.id }) { p ->
                        Column(Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(18.dp)).clickable { onOpenProject(p.notebook.id) }.padding(14.dp)) {
                            Text(p.notebook.status.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, color = InkMuted)
                            Text(p.notebook.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            Text("${p.notes} notes", fontSize = 12.sp, color = InkSecondary)
                        }
                    }
                }
            }
            if (people.isNotEmpty()) item {
                WorkSectionTitle(if (settings.terms.person == "Contact") "People" else settings.terms.people, "All", onTrailing = { onOpenAll(WorkTab.PEOPLE) })
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(people.take(12), key = { "pp-" + it.id }) { p ->
                        Column(Modifier.width(60.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenPerson(p.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            PersonAvatar(p, 48.dp)
                            Text(p.firstName, fontSize = 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }

            // The rest of home, still here: Faith, stories.
            if (identity.showsFaith) item {
                val d = devotional?.devotional
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 22.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceRaised)
                        .border(1.dp, LineSoft, RoundedCornerShape(18.dp)).clickable(onClick = onOpenDevotional).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.WbSunny, null, tint = FaithGold, modifier = Modifier.size(20.dp))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("TODAY'S DEVOTIONAL", fontSize = 10.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                        Text(d?.title ?: "A word for your day", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (stories.isNotEmpty()) item { Spacer(Modifier.height(10.dp)); com.craftflowtechnologies.meetingmind.feature.today.StoryRings(stories, onOpenStories) }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 22.dp).clip(RoundedCornerShape(18.dp)).background(AccentWash).clickable(onClick = onOpenWork).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Work, null, tint = Accent)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Open Work", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("Notes, tasks, projects and decisions", fontSize = 12.sp, color = InkSecondary)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent)
                }
            }
        }
    }
    nudging?.let { NudgeSheet(it, work) { nudging = null } }
    editing?.let { t -> WorkTaskSheet(t, work, onDismiss = { editing = null }, onOpenSource = t.meetingId?.let { m -> { editing = null; onOpenMeeting(m, t.startMs) } }) }
    preparing?.let { PrepareSheet(it, work, onOpenMeeting, onDismiss = { preparing = null }) }
    // "Ask about my work": grounded in the record, limited to Work, and never uncited.
    if (askOpen) ScopedAskSheet(com.craftflowtechnologies.meetingmind.ai.assistant.AskScope(), work, onOpenMeeting, onOpenNote, onDismiss = { askOpen = false })
}

private fun pulseEventOf(e: com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent) =
    PulseEvent(e.key, e.title, e.begin, e.end, e.otherPeople, e.attendees.filter { a -> !a.isSelf }.mapNotNull { a -> a.email })

private fun open(item: TimelineItem, today: TodayViewModel, onOpenNote: (String) -> Unit, onOpenProcessing: (String) -> Unit) = when (val t = item.target) {
    is DeepTarget.Note -> onOpenNote(t.noteId)
    is DeepTarget.Recording -> onOpenProcessing(t.meetingId)
    is DeepTarget.Event -> { today.noteForEvent(t.event) { id, _ -> onOpenNote(id) }; Unit }
}

@Composable
private fun BriefButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (primary) Briefing.OnBrief else Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (primary) BriefTop else Briefing.OnBrief) }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(tint.copy(alpha = if (LocalMMColors.current.isDark) 0.18f else 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(label, fontSize = 12.sp, color = InkSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Metric(value: String, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp)) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tint)
        Text(label, fontSize = 11.5.sp, color = InkSecondary, maxLines = 1)
    }
}

/** One entry in the day: a time column, a line with a dot, and the item. */
@Composable
private fun ScheduleRow(item: TimelineItem, fmt: java.text.DateFormat, now: Long, last: Boolean, onPrepare: (() -> Unit)? = null, onClick: () -> Unit) {
    val past = (item.end ?: item.start) < now
    val live = item.start <= now && (item.end ?: item.start) >= now
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
        Text(fmt.format(Date(item.start)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (past) InkMuted else Ink, modifier = Modifier.width(58.dp).padding(top = 12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
            Spacer(Modifier.height(15.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (live) LocalMMColors.current.recording else if (past) LineSoft else Color(item.accent)))
            if (!last) Box(Modifier.width(2.dp).height(44.dp).background(LineSoft))
        }
        Row(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(if (live) AccentWash else SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(itemIcon(item), null, tint = if (past) InkMuted else Color(item.accent), modifier = Modifier.size(18.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(item.title.ifBlank { "Untitled" }, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = if (past) InkSecondary else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let { Text(it, fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (live) Text("NOW", fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = LocalMMColors.current.recording)
            else if (onPrepare != null) Text("Prepare", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onPrepare).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun ProfessionalSkyHeader(
    identity: com.craftflowtechnologies.meetingmind.core.identity.AppIdentity,
    now: Long,
    meetingsToday: Int,
    dueTodayCount: Int,
    onSearch: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cal = remember(now / 60_000) { Calendar.getInstance().apply { timeInMillis = now } }
    val sky = remember(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE) / 5) {
        TimeOfDaySky.at(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
    val idle = rememberInfiniteTransition(label = "prof_idle")
    val float = idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "prof_orb_float"
    )
    val twinkle = idle.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "prof_twinkle")
    val stars = remember { List(20) { kotlin.Triple(kotlin.random.Random(it * 17 + 5).nextFloat(), kotlin.random.Random(it * 11 + 3).nextFloat() * 0.7f, kotlin.random.Random(it).nextFloat()) } }

    val top = Color(sky.skyTop)
    val bottom = Color(sky.skyBottom)
    val cardShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .shadow(16.dp, cardShape, ambientColor = bottom.copy(alpha = 0.25f), spotColor = bottom.copy(alpha = 0.4f))
            .clip(cardShape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))), cardShape)
    ) {
        // Sky Canvas background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(top, lerp(top, bottom, 0.6f), bottom), start = Offset.Zero, end = Offset(size.width * 0.6f, size.height)))
            if (sky.stars > 0f) {
                stars.forEach { (x, y, phase) ->
                    val t = ((twinkle.value + phase) % 1f)
                    val a = sky.stars * (0.35f + 0.65f * kotlin.math.abs(t * 2f - 1f))
                    drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), radius = (0.8f + phase * 1.2f).dp.toPx(), center = Offset(x * size.width, y * size.height))
                }
            }
            // Glow and celestial orb on the right
            val orbX = size.width * 0.82f
            val orbY = size.height * (0.38f + float.value * 0.06f)
            val orbCenter = Offset(orbX, orbY)
            val orbRadius = size.height * 0.22f
            drawCircle(
                Brush.radialGradient(listOf(Color(sky.glow).copy(alpha = if (sky.isMoon) 0.25f else 0.45f), Color.Transparent), center = orbCenter, radius = orbRadius * 2.5f),
                radius = orbRadius * 2.5f,
                center = orbCenter
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(sky.orbLight), Color(sky.orbBody), Color(sky.orbDeep)),
                    center = Offset(orbCenter.x - orbRadius * 0.25f, orbCenter.y - orbRadius * 0.25f),
                    radius = orbRadius
                ),
                radius = orbRadius,
                center = orbCenter
            )
        }

        // Executive translucent gradient overlay for high contrast and executive polish
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(SurfaceRaised.copy(alpha = 0.90f), SurfaceRaised.copy(alpha = 0.45f))))
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)).uppercase(),
                    fontSize = 10.5.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkMuted
                )
                Row {
                    IconButton(onClick = onSearch, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Search, "Search", tint = Ink, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = onCustomize, modifier = Modifier.size(32.dp).testTag("professional_customize")) {
                        Icon(Icons.Filled.Tune, "Customize home", tint = InkSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Column {
                Text(
                    text = Greetings.pick(identity),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Executive Briefing · $meetingsToday ${if (meetingsToday == 1) "meeting" else "meetings"} · $dueTodayCount due",
                    fontSize = 12.sp,
                    color = InkMuted,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

