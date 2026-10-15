package com.craftflowtechnologies.meetingmind.feature.work

import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.calendar.UpNext
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.timeline.DeepTarget
import com.craftflowtechnologies.meetingmind.core.timeline.ItemKind
import com.craftflowtechnologies.meetingmind.core.timeline.TimeOfDaySky
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.feature.today.HomeQuickAccessRow
import com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel
import com.craftflowtechnologies.meetingmind.feature.today.HeroTile
import com.craftflowtechnologies.meetingmind.feature.today.HomeModes
import com.craftflowtechnologies.meetingmind.feature.today.ModeHome
import com.craftflowtechnologies.meetingmind.feature.today.TodayFocus
import com.craftflowtechnologies.meetingmind.feature.today.itemIcon
import com.craftflowtechnologies.meetingmind.feature.today.home.FaithAndStories
import com.craftflowtechnologies.meetingmind.feature.today.home.HomeModeHeader
import com.craftflowtechnologies.meetingmind.feature.today.home.faithBentoData
import com.craftflowtechnologies.meetingmind.feature.today.timeFormat
import com.craftflowtechnologies.meetingmind.feature.work.home.NeedLine
import com.craftflowtechnologies.meetingmind.feature.work.home.ScheduleAgendaRow
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkCaptureRow
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkGlanceTile
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkHero
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkHeroCard
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkHomeLayout
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkListCard
import com.craftflowtechnologies.meetingmind.feature.today.home.Reveal
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import java.util.Calendar
import java.util.Date

/**
 * The Work home (HomeStyle.PROFESSIONAL). It is a sibling of the Today home: the same header, the
 * same sky hero and illustrated tiles, but laid out for the working day. It leads with the next
 * meeting (or what needs the person), shows the figures for the week, then the lists that matter:
 * what needs you, the day's schedule, what you owe and are owed, projects and people. The rules for
 * which rows show are in [WorkHomeLayout]; the figures come from data this home already loads.
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
    onOpenCircles: () -> Unit = {},
    /** What the Circles card shows; the caller loads it (see HomeCirclesViewModel). */
    homeCircles: com.craftflowtechnologies.meetingmind.feature.today.home.HomeCircles = com.craftflowtechnologies.meetingmind.feature.today.home.HomeCircles.Loading,
    onOpenCircle: (String) -> Unit = {},
    onStartCircle: () -> Unit = {},
    onRetryCircles: () -> Unit = {},
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
    val create = com.craftflowtechnologies.meetingmind.feature.create.rememberCreateController()
    com.craftflowtechnologies.meetingmind.feature.create.CreateHost(create)
    val identity by today.identity.collectAsState()
    val upNext by today.upNext.collectAsState()
    val now by today.now.collectAsState()
    val todayItems by today.today.collectAsState()
    val jobs by today.activeJobs.collectAsState()
    val stories by today.stories.collectAsState()
    val devotional by today.devotional.collectAsState()
    val stats by today.stats.collectAsState()
    val faithData = faithBentoData(stats.weekCount, devotional)
    val settings by work.settings.collectAsState()
    val myTasks by work.myTasks.collectAsState()
    val waitingOn by work.waitingOn.collectAsState()
    val toReview by work.toReview.collectAsState()
    val followUps by work.followUps.collectAsState()
    val decisions by work.decisions.collectAsState()
    val projects by work.projects.collectAsState()
    val people by work.everyone.collectAsState()
    val titles by work.titles.collectAsState()
    val focus by today.focus.collectAsState()
    val listState = rememberLazyListState()
    val offeredModes = HomeModes.offered(ModeHome.WORK, identity.showsFaith)
    val mode = HomeModes.effective(focus, offeredModes)
    val inboxCount by work.inboxCount.collectAsState()
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
    val schedule = todayItems.sortedBy { it.start }

    // The hero: the next meeting that has not ended, else the first item that needs the person.
    val nextItem = schedule.firstOrNull { item ->
        (item.target as? DeepTarget.Event)?.event?.let { ev -> !ev.allDay && ev.end >= now } == true
    }
    val nextEvent = (nextItem?.target as? DeepTarget.Event)?.event
    // The header's up-next line, from the same next item the hero uses.
    val nextTile = nextItem?.let { ni ->
        HeroTile(
            label = "Up next", title = ni.title.ifBlank { "Untitled" }, subtitle = fmt.format(Date(ni.start)),
            icon = itemIcon(ni), accent = Color(ni.accent), onClick = { open(ni, today, onOpenNote, onOpenProcessing) }
        )
    } ?: HeroTile(label = "Up next", title = "Nothing scheduled", subtitle = "Record something", icon = Icons.Filled.Mic, accent = Accent, onClick = onRecord)
    val hero = WorkHomeLayout.hero(hasNextMeeting = nextEvent != null, needsCount = toReview.size + followUps.size)
    val (reviewsShown, followUpsShown) = WorkHomeLayout.needsShown(toReview.size, followUps.size)
    val sky = remember(now / 300_000) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        TimeOfDaySky.at(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
    val lateNight = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY).let { it >= 22 || it < 5 }

    Scaffold(
        containerColor = SurfaceBase,
        bottomBar = {
            com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar(
                current = com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME,
                onNavigate = { if (it != com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME) onNavigateBottomNav(it) }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("professional_home"), state = listState, contentPadding = PaddingValues(bottom = 36.dp)) {
            // The header every home shares: the Today header and sky hero, then the mode switch.
            item {
                HomeModeHeader(
                    today = today, home = ModeHome.WORK, tile = nextTile, listState = listState,
                    inboxCount = inboxCount, onSearch = onSearch, onInbox = onOpenInbox, onOpenDevotional = onOpenDevotional
                )
            }
            // The same Circles card as the Today home, in place of the old text link.
            item {
                com.craftflowtechnologies.meetingmind.feature.today.home.CirclesCard(
                    circles = homeCircles,
                    onOpenCircles = onOpenCircles,
                    onOpenCircle = onOpenCircle,
                    onStartCircle = onStartCircle,
                    onRetry = onRetryCircles,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("professional_circles")
                )
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    QuietLink("Customize home", onCustomize, modifier = Modifier.testTag("professional_customize"))
                }
            }

            // Quick Access icons for Faith, Work, Devotional, Prayer, Word, Testimonies.
            item {
                HomeQuickAccessRow(
                    onOpenFaith = onOpenFaith,
                    onOpenWork = onOpenWork,
                    onOpenDevotional = onOpenDevotional,
                    onOpenPrayer = onOpenPrayer,
                    onOpenWord = onOpenWord,
                    onOpenTestimonies = onOpenTestimonies,
                    onOpenCreate = { create.open() },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // The hero: the next meeting, or what needs the person. Omitted when there is nothing to lead with.
            if (hero != WorkHero.NONE) item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    if (hero == WorkHero.MEETING && nextItem != null && nextEvent != null) {
                        WorkHeroCard(
                            sky = sky, lateNight = lateNight,
                            overline = "NEXT UP · ${fmt.format(Date(nextItem.start)).uppercase()}",
                            title = nextItem.title.ifBlank { "Untitled" },
                            line = nextItem.subtitle,
                            primaryLabel = "Prepare",
                            onPrimary = { preparing = PrepTarget.Event(pulseEventOf(nextEvent)) },
                            secondaryLabel = "Record",
                            onSecondary = {
                                today.noteForEvent(nextEvent) { id, type -> onRecordEvent(id, type, nextEvent.title, UpNext.speakerCount(nextEvent)) }
                            },
                            index = 0
                        )
                    } else {
                        val first = toReview.firstOrNull()
                        val followUp = followUps.firstOrNull()
                        if (first != null) WorkHeroCard(
                            sky = sky, lateNight = lateNight, overline = "NEEDS YOU",
                            title = first.title, line = "${first.type.displayName} · ready to wrap up",
                            primaryLabel = "Wrap up", onPrimary = { onOpenWrapUp(first.meetingId, false) },
                            secondaryLabel = null, onSecondary = {}, index = 0
                        ) else if (followUp != null) WorkHeroCard(
                            sky = sky, lateNight = lateNight, overline = "NEEDS YOU",
                            title = followUp.title, line = "Follow-up not sent yet · ${dayLabel(followUp.at)}",
                            primaryLabel = "Send", onPrimary = { onOpenWrapUp(followUp.meetingId, true) },
                            secondaryLabel = null, onSecondary = {}, index = 0
                        )
                    }
                }
            }

            // Capture, one tap each.
            item {
                Box(Modifier.padding(top = 16.dp)) {
                    WorkCaptureRow(
                        onRecord = onRecord,
                        onNote = onNewNote,
                        onTask = { onOpenAll(WorkTab.MINE) },
                        onImport = onImport,
                        onWork = onOpenWork
                    )
                }
            }

            // At a glance: the week's figures, each one opens its list.
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    WorkGlanceTile(
                        dueToday = dueToday.size,
                        overdue = overdue,
                        waiting = waitingOn.size,
                        toSend = followUps.size,
                        decided = weekDecisions,
                        onDue = { onOpenAll(WorkTab.MINE) },
                        onWaiting = { onOpenAll(WorkTab.WAITING) },
                        onSend = { onOpenWork() },
                        onDecided = { onOpenAll(WorkTab.DECISIONS) },
                        onOpenWork = onOpenWork,
                        index = 1
                    )
                }
            }

            // The Work Pulse: what changed, and today with what's still open (D5.1).
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Reveal(2) {
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (mode == TodayFocus.ALL) item {
                FaithAndStories(
                    showsFaith = identity.showsFaith, data = faithData, stories = stories,
                    onFaith = onOpenFaith, onDevotional = onOpenDevotional, onPrayer = onOpenPrayer,
                    onOpenStories = onOpenStories, index = 7
                )
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

            // Needs you: recordings to wrap up, and follow-ups not sent yet. Reviews first.
            if (toReview.isNotEmpty() || followUps.isNotEmpty()) item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    WorkListCard(
                        title = "Needs you", trailing = null, onTrailing = null,
                        tag = "work_home_needs", index = 3
                    ) {
                        toReview.take(reviewsShown).forEach { r ->
                            NeedLine(r.title, "${r.type.displayName} · ready to wrap up", "Wrap up") { onOpenWrapUp(r.meetingId, false) }
                        }
                        followUps.take(followUpsShown).forEach { r ->
                            NeedLine(r.title, "Follow-up not sent yet · ${dayLabel(r.at)}", "Send") { onOpenWrapUp(r.meetingId, true) }
                        }
                    }
                }
            }

            // The day's schedule, as an agenda. Past items recede; the live one is marked.
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    WorkListCard(
                        title = "Today's schedule",
                        trailing = if (schedule.isEmpty()) null else "${schedule.size}",
                        onTrailing = null, tag = "work_home_schedule", index = 4
                    ) {
                        if (schedule.isEmpty()) {
                            EmptyLine("Nothing on the calendar today. Turn on your phone's calendar in the Today home to see meetings here, with prep and one-tap record.")
                        }
                        schedule.forEachIndexed { i, entry ->
                            ScheduleAgendaRow(
                                item = entry, fmt = fmt,
                                state = WorkHomeLayout.scheduleState(entry.start, entry.end, now),
                                last = i == schedule.lastIndex,
                                onPrepare = (entry.target as? DeepTarget.Event)?.event?.takeIf { it.end >= now }?.let { ev -> { preparing = PrepTarget.Event(pulseEventOf(ev)) } }
                            ) { open(entry, today, onOpenNote, onOpenProcessing) }
                        }
                    }
                }
            }

            // What you owe.
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    WorkListCard(
                        title = "My tasks",
                        trailing = if (myTasks.isNotEmpty()) "All ${myTasks.size}" else null,
                        onTrailing = { onOpenAll(WorkTab.MINE) }, tag = "work_home_tasks", index = 5
                    ) {
                        if (myTasks.isEmpty()) EmptyLine("Nothing due. What you agree to in meetings lands here with its date.")
                        myTasks.take(WorkHomeLayout.TASKS_CAP).forEach { t ->
                            TaskLine(t, t.meetingId?.let { titles[it] }, { work.toggle(t) }, { editing = t }, t.meetingId?.let { m -> { onOpenMeeting(m, t.startMs) } })
                        }
                    }
                }
            }

            // What you are owed.
            if (waitingOn.isNotEmpty()) item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    WorkListCard(
                        title = "Waiting on", trailing = "All ${waitingOn.size}",
                        onTrailing = { onOpenAll(WorkTab.WAITING) }, tag = "work_home_waiting", index = 6
                    ) {
                        waitingOn.take(WorkHomeLayout.WAITING_CAP).forEach { t ->
                            TaskLine(t, null, { work.toggle(t) }, { editing = t }, action = { TextButton(onClick = { nudging = t }) { Text("Nudge") } })
                        }
                    }
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

            // Work mode keeps the same faith and stories at the end of the page, so they stay reachable.
            if (mode != TodayFocus.ALL) item {
                FaithAndStories(
                    showsFaith = identity.showsFaith, data = faithData, stories = stories,
                    onFaith = onOpenFaith, onDevotional = onOpenDevotional, onPrayer = onOpenPrayer,
                    onOpenStories = onOpenStories, index = 7
                )
            }

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
