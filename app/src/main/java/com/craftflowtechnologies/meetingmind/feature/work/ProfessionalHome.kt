package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem
import com.craftflowtechnologies.meetingmind.core.ui.work.RibbonBlock
import com.craftflowtechnologies.meetingmind.core.ui.work.RibbonDot
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.feature.today.HeroTile
import com.craftflowtechnologies.meetingmind.feature.today.HomeQuickAccessRow
import com.craftflowtechnologies.meetingmind.feature.today.ModeHome
import com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel
import com.craftflowtechnologies.meetingmind.feature.today.home.FaithAndStories
import com.craftflowtechnologies.meetingmind.feature.today.home.HomeModeHeader
import com.craftflowtechnologies.meetingmind.feature.today.home.faithBentoData
import com.craftflowtechnologies.meetingmind.feature.today.home.Reveal
import com.craftflowtechnologies.meetingmind.feature.today.itemIcon
import com.craftflowtechnologies.meetingmind.feature.today.timeFormat
import com.craftflowtechnologies.meetingmind.feature.work.home.UpNextChoice
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkGlanceTile
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkHomeCopy
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkPeopleStrip
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkProjectsStrip
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkQuickAccess
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkTodayTaskFormat
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkTodayTasksCard
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkUpNext
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkUpNextTile
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import java.time.LocalTime
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch

/**
 * The Work home (HomeStyle.PROFESSIONAL), in the same sky-hero language as the Today home. Top to bottom:
 * the shared header and mode switch with the sky hero; a floating "Up next" tile over the hero's edge (the
 * next meeting in the morning, what to review from evening); quick access; "Your day" (the Briefing card with
 * the day ribbon and at most four things that need the person); today's tasks; projects; people; then the
 * Circles card, the glance figures and the faith and stories sections. The rules live in feature/work/home.
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
    onOpenLearn: () -> Unit = {},
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
    /** Quick access "Brief". Defaults to Work until the caller passes the brief screen. */
    onOpenBrief: () -> Unit = onOpenWork,
    /** Quick access "Review" (the weekly review). Defaults to Work until the caller passes it. */
    onOpenWeeklyReview: () -> Unit = onOpenWork,
    onNavigateBottomNav: (com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit
) {
    val create = com.craftflowtechnologies.meetingmind.feature.create.rememberCreateController()
    com.craftflowtechnologies.meetingmind.feature.create.CreateHost(create)
    val identity by today.identity.collectAsState()
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
    val failed by work.failed.collectAsState()
    val followUps by work.followUps.collectAsState()
    val decisions by work.decisions.collectAsState()
    val projects by work.projects.collectAsState()
    val people by work.everyone.collectAsState()
    val titles by work.titles.collectAsState()
    val listState = rememberLazyListState()
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
    val colors = MM.colors
    val startOfToday = DueDates.startOfDay(now)
    val endOfToday = startOfToday + DAY_MS
    val dueToday = myTasks.filter { it.dueAt != null && it.dueAt < endOfToday }
    val overdue = myTasks.count { it.isOverdue(now) }
    val meetingsToday = todayItems.count { it.kind == ItemKind.EVENT }
    val weekDecisions = decisions.count { it.createdAt > now - 7 * DAY_MS }
    val schedule = todayItems.sortedBy { it.start }

    // The next meeting that has not ended. The Up next tile and the day ribbon both read from it.
    val nextItem = schedule.firstOrNull { item ->
        (item.target as? DeepTarget.Event)?.event?.let { ev -> !ev.allDay && ev.end >= now } == true
    }
    val nextEvent = (nextItem?.target as? DeepTarget.Event)?.event
    val fmtNext = nextItem?.let { ni -> fmt.format(Date(ni.start)) }
    // The header's up-next line, from the same next item the tile uses.
    val nextTile = nextItem?.let { ni ->
        HeroTile(
            label = "Up next", title = ni.title.ifBlank { "Untitled" }, subtitle = fmtNext.orEmpty(),
            icon = itemIcon(ni), accent = Color(ni.accent), onClick = { open(ni, today, onOpenNote, onOpenProcessing) }
        )
    } ?: HeroTile(label = "Up next", title = "Nothing scheduled", subtitle = "Record something", icon = androidx.compose.material.icons.Icons.Filled.Event, accent = Accent, onClick = onRecord)
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val upNextChoice = WorkUpNext.choose(hour, hasNextMeeting = nextEvent != null, reviews = toReview.size, followUps = followUps.size)
    val firstReview = toReview.firstOrNull()
    val firstFollowUp = followUps.firstOrNull()

    // The day ribbon: today's meetings as blocks, and today's timed tasks as priority dots.
    val ribbonBlocks = todayItems.mapNotNull { item ->
        val ev = (item.target as? DeepTarget.Event)?.event ?: return@mapNotNull null
        if (ev.allDay) return@mapNotNull null
        val endMs = if (ev.end > ev.begin) ev.end else ev.begin + 30 * 60_000L
        RibbonBlock(
            start = localTimeOf(ev.begin), end = localTimeOf(endMs), title = ev.title.ifBlank { "Untitled" },
            onClick = { open(item, today, onOpenNote, onOpenProcessing) }, past = endMs < now
        )
    }
    val ribbonDots = myTasks.mapNotNull { t ->
        val due = t.dueAt ?: return@mapNotNull null
        if (due < startOfToday || due >= endOfToday || !WorkTodayTaskFormat.isTimed(due)) return@mapNotNull null
        RibbonDot(time = localTimeOf(due), color = if (t.isOverdue(now)) colors.danger else colors.accent, onClick = { editing = t })
    }

    // Your day: the Pulse, with the recordings that need the person and the counts for the sentence.
    val attentionTotal = failed.size + toReview.size + followUps.size
    val ui = pulse.copy(
        summary = WorkHomeCopy.summarySentence(meetingsToday, dueToday.size, overdue, attentionTotal),
        failed = failed.map { PulseRecording(it.meetingId, it.title, "Recording didn't process · " + dayLabel(it.at, now)) },
        toReview = toReview.map { PulseRecording(it.meetingId, it.title, "${it.type.displayName} · ready to wrap up") },
        followUps = followUps.map { PulseRecording(it.meetingId, it.title, "Follow-up not sent yet · " + dayLabel(it.at, now)) },
        primaryLabel = when (upNextChoice) {
            UpNextChoice.MEETING -> "Prepare"
            UpNextChoice.REVIEW, UpNextChoice.FOLLOW_UP -> "Review"
            UpNextChoice.NONE -> null
        },
        dayBlocks = ribbonBlocks,
        dayDots = ribbonDots,
        dayNow = localTimeOf(now)
    )

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
            // 1. The header every home shares: avatar and date, the dock, the mode switch, and the sky hero.
            item {
                HomeModeHeader(
                    today = today, home = ModeHome.WORK, tile = nextTile, listState = listState,
                    inboxCount = inboxCount, onSearch = onSearch, onInbox = onOpenInbox, onOpenDevotional = onOpenDevotional
                )
            }

            // 2. The Up next tile, under the mode switch at its full height. (It used to float 38 dp up over the
            //    hero in a 54 dp slot, which covered the switch caption and squeezed the tile and its button.)
            if (upNextChoice != UpNextChoice.NONE) item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)) {
                    val meeting = nextItem
                    val review = firstReview
                    val followUp = firstFollowUp
                    val floating = Modifier
                    when {
                        upNextChoice == UpNextChoice.MEETING && meeting != null && nextEvent != null -> WorkUpNextTile(
                            overline = "UP NEXT · " + fmt.format(Date(meeting.start)).uppercase(),
                            title = meeting.title.ifBlank { "Untitled" },
                            subtitle = meeting.subtitle,
                            icon = Icons.Filled.Event,
                            buttonLabel = "Prepare",
                            onTile = { preparing = PrepTarget.Event(pulseEventOf(nextEvent)) },
                            onButton = { preparing = PrepTarget.Event(pulseEventOf(nextEvent)) },
                            index = 0,
                            modifier = floating
                        )
                        upNextChoice == UpNextChoice.REVIEW && review != null -> WorkUpNextTile(
                            overline = WorkUpNext.reviewLine(toReview.size).uppercase(),
                            title = review.title.ifBlank { "Untitled" },
                            subtitle = "${review.type.displayName} · ready to wrap up",
                            icon = Icons.Filled.Insights,
                            buttonLabel = "Review",
                            onTile = { onOpenWrapUp(review.meetingId, false) },
                            onButton = { onOpenWrapUp(review.meetingId, false) },
                            index = 0,
                            modifier = floating
                        )
                        upNextChoice == UpNextChoice.FOLLOW_UP && followUp != null -> WorkUpNextTile(
                            overline = "TO SEND",
                            title = followUp.title.ifBlank { "Untitled" },
                            subtitle = "Follow-up not sent yet · " + dayLabel(followUp.at, now),
                            icon = Icons.AutoMirrored.Filled.Send,
                            buttonLabel = "Review",
                            onTile = { onOpenWrapUp(followUp.meetingId, true) },
                            onButton = { onOpenWrapUp(followUp.meetingId, true) },
                            index = 0,
                            modifier = floating
                        )
                    }
                }
            }

            // 3. Quick access: Record, Task, Inbox, Ask, Brief, Review, and New (Note and Import are in New).
            item {
                WorkQuickAccess(
                    onRecord = onRecord,
                    onTask = { PendingWorkSegment.request.value = WorkSegment.TASKS; onOpenWork() },
                    onInbox = onOpenInbox,
                    onAsk = { askOpen = true },
                    onBrief = onOpenBrief,
                    onReview = onOpenWeeklyReview,
                    onNew = { create.open() },
                    inboxCount = inboxCount,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            // The rest of the quick access: faith, devotional, prayer, word, testimonies, learn.
            item {
                HomeQuickAccessRow(
                    onOpenFaith = onOpenFaith,
                    onOpenWork = onOpenWork,
                    onOpenDevotional = onOpenDevotional,
                    onOpenPrayer = onOpenPrayer,
                    onOpenWord = onOpenWord,
                    onOpenTestimonies = onOpenTestimonies,
                    onOpenCreate = { create.open() },
                    onOpenLearn = onOpenLearn,
                    showLearn = true,
                    modifier = Modifier.padding(top = 10.dp)
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

            // 4. Your day: the Briefing card, with the ribbon, what needs you, what changed, and the buttons.
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Reveal(2) {
                        PulseCard(
                            ui = ui,
                            timeFormat = fmt,
                            actions = PulseActions(
                                onNudge = { r -> scope.launch { nudging = work.asTask(r) } },
                                onPrepareRow = { r -> r.entityType?.let { t -> r.entityId?.let { id -> preparing = PrepTarget.Entity(t, id) } } },
                                onOpenRow = { r -> if (r.entityType != null && r.entityId != null) onOpenContext(r.entityType, r.entityId) else onOpenAll(WorkTab.MINE) },
                                onDone = { r ->
                                    if (r.taskId != null) scope.launch { work.toggle(work.asTask(r)) }
                                    else if (r.entityType != null && r.entityId != null) onOpenContext(r.entityType, r.entityId)
                                    else onOpenAll(WorkTab.MINE)
                                },
                                onPlayChange = { l -> scope.launch { l.itemId?.let { work.evidenceOf(it) }?.let { (m, at) -> onOpenMeeting(m, at) } } },
                                onPrepareEvent = { e -> preparing = PrepTarget.Event(e) },
                                onRecordEvent = { e ->
                                    (todayItems.firstNotNullOfOrNull { (it.target as? DeepTarget.Event)?.event?.takeIf { ev -> ev.key == e.key } })
                                        ?.let { ev -> today.noteForEvent(ev) { id, type -> onRecordEvent(id, type, e.title, UpNext.speakerCount(ev)) } }
                                },
                                onAsk = { askOpen = true },
                                onRecord = onRecord,
                                onTemplates = onOpenWork,
                                onOpenInbox = onOpenInbox,
                                onPrimary = {
                                    when (upNextChoice) {
                                        UpNextChoice.MEETING -> nextEvent?.let { preparing = PrepTarget.Event(pulseEventOf(it)) }
                                        UpNextChoice.REVIEW -> firstReview?.let { onOpenWrapUp(it.meetingId, false) }
                                        UpNextChoice.FOLLOW_UP -> firstFollowUp?.let { onOpenWrapUp(it.meetingId, true) }
                                        UpNextChoice.NONE -> Unit
                                    }
                                },
                                onWrapUp = { r, followUp -> onOpenWrapUp(r.meetingId, followUp) },
                                onRetry = { r -> onOpenProcessing(r.meetingId) },
                                onOpenPulse = onOpenWork
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 5. Today's tasks: Mine, swipeable, with Waiting on below. "All" opens Tasks.
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    WorkTodayTasksCard(
                        today = dueToday.sortedBy { it.dueAt ?: Long.MAX_VALUE },
                        totalMine = myTasks.size,
                        waiting = waitingOn,
                        now = now,
                        timeFormat = fmt,
                        contextOf = { t -> t.meetingId?.let { titles[it] } },
                        onToggle = { t -> work.toggle(t) },
                        onOpen = { t -> editing = t },
                        onNudge = { t -> nudging = t },
                        onAll = { onOpenAll(WorkTab.MINE) },
                        index = 5
                    )
                }
            }

            // The figures for the week; each one opens its list.
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
                        index = 6
                    )
                }
            }

            // 6. Projects, as a carousel. "All" opens the Projects segment of Work.
            if (projects.isNotEmpty()) item {
                WorkSectionTitle(settings.terms.projects, "All", onTrailing = {
                    PendingWorkSegment.request.value = WorkSegment.PROJECTS
                    onOpenWork()
                })
                WorkProjectsStrip(projects = projects, now = now, onOpen = onOpenProject)
            }

            // 7. People, with "owes" and "you owe" badges.
            if (people.isNotEmpty()) item {
                WorkSectionTitle(if (settings.terms.person == "Contact") "People" else settings.terms.people, "All", onTrailing = { onOpenAll(WorkTab.PEOPLE) })
                WorkPeopleStrip(
                    people = people,
                    owedToMe = waitingOn.mapNotNull { it.personId }.toSet(),
                    iOwe = myTasks.mapNotNull { it.personId }.toSet(),
                    onOpen = onOpenPerson
                )
            }

            // 8. The Circles card, then Customize home, then faith and stories, all kept from before.
            item {
                com.craftflowtechnologies.meetingmind.feature.today.home.CirclesCard(
                    circles = homeCircles,
                    onOpenCircles = onOpenCircles,
                    onOpenCircle = onOpenCircle,
                    onStartCircle = onStartCircle,
                    onRetry = onRetryCircles,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp).testTag("professional_circles")
                )
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    QuietLink("Customize home", onCustomize, modifier = Modifier.testTag("professional_customize"))
                }
            }
            item {
                FaithAndStories(
                    showsFaith = identity.showsFaith, data = faithData, stories = stories,
                    onFaith = onOpenFaith, onDevotional = onOpenDevotional, onPrayer = onOpenPrayer,
                    onOpenStories = onOpenStories, index = 9
                )
            }
        }
    }
    nudging?.let { NudgeSheet(it, work) { nudging = null } }
    editing?.let { t -> WorkTaskSheet(t, work, onDismiss = { editing = null }, onOpenSource = t.meetingId?.let { m -> { editing = null; onOpenMeeting(m, t.startMs) } }) }
    preparing?.let { PrepareSheet(it, work, onOpenMeeting, onDismiss = { preparing = null }) }
    // "Ask about my work": grounded in the record, limited to Work, and never uncited.
    if (askOpen) ScopedAskSheet(com.craftflowtechnologies.meetingmind.ai.assistant.AskScope(), work, onOpenMeeting, onOpenNote, onDismiss = { askOpen = false })
}

private const val DAY_MS = 86_400_000L

private fun pulseEventOf(e: com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent) =
    PulseEvent(e.key, e.title, e.begin, e.end, e.otherPeople, e.attendees.filter { a -> !a.isSelf }.mapNotNull { a -> a.email })

/** The local clock time of [ms], for the day ribbon. */
private fun localTimeOf(ms: Long): LocalTime {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

private fun open(item: TimelineItem, today: TodayViewModel, onOpenNote: (String) -> Unit, onOpenProcessing: (String) -> Unit) = when (val t = item.target) {
    is DeepTarget.Note -> onOpenNote(t.noteId)
    is DeepTarget.Recording -> onOpenProcessing(t.meetingId)
    is DeepTarget.Event -> { today.noteForEvent(t.event) { id, _ -> onOpenNote(id) }; Unit }
}
