package com.craftflowtechnologies.meetingmind.feature.learning.space

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.core.navigation.AppMenuButton
import com.craftflowtechnologies.meetingmind.core.navigation.MenuEntry
import com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon
import com.craftflowtechnologies.meetingmind.core.navigation.MenuSection
import com.craftflowtechnologies.meetingmind.core.navigation.MenuTarget
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar
import com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryBar
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryGlyph
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryLegend
import com.craftflowtechnologies.meetingmind.feature.learning.components.plainReason
import com.craftflowtechnologies.meetingmind.ui.theme.LearningAccent
import com.craftflowtechnologies.meetingmind.ui.theme.LearningTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The three destinations of the Learning space (decision D1: Practice is a mode, not a tab). */
enum class SpaceTab(val label: String, val icon: ImageVector) {
    LEARN("Learn", Icons.Rounded.WbSunny),
    LIBRARY("Library", Icons.Rounded.LocalLibrary),
    PROGRESS("Progress", Icons.Rounded.Insights)
}

/** Navigation out of the space. The host (MainActivity) owns the routes. */
data class LearningSpaceActions(
    val onBack: () -> Unit,
    val onOpenSpace: () -> Unit,
    val onOpenSession: (sessionIdOrNoteId: String) -> Unit,
    val onStartPractice: () -> Unit,
    val onRecordLecture: () -> Unit,
    val onWriteStudyNote: () -> Unit,
    val onImport: () -> Unit = {},
    val onOpenNote: (noteId: String) -> Unit = {}
)

/**
 * The Learning space: Learn (today's plan), Library (courses and sessions) and Progress, with its
 * own bar. When Learning is the chosen Home style ([onNavigateBottomNav] is set), Home shows the
 * Learn pane with the app's global bar and an entry into the space; Home never hosts the space bar
 * (decision D2).
 */
@Composable
fun LearningSpaceScreen(
    viewModel: LearnViewModel,
    actions: LearningSpaceActions,
    onNavigateBottomNav: ((BottomNavDestination) -> Unit)? = null
) {
    LearningAccent {
        val isHome = onNavigateBottomNav != null
        var tab by rememberSaveable { mutableStateOf(SpaceTab.LEARN) }
        var openCourseId by rememberSaveable { mutableStateOf<String?>(null) }
        var creating by rememberSaveable { mutableStateOf(false) }
        var createCourseId by rememberSaveable { mutableStateOf<String?>(null) }
        var askOpen by rememberSaveable { mutableStateOf(false) }
        val startCreate: (String?) -> Unit = { preset -> createCourseId = preset; creating = true }
        val openAsk: () -> Unit = { viewModel.openAsk(null); askOpen = true }
        // Learning's own menu, on the main app menu as its base (like Work's): read when the menu opens.
        val menu: () -> MenuSection = {
            learningMenu(
                hasSessions = viewModel.sessions.value.isNotEmpty(),
                onNewSession = { startCreate(null) },
                onAsk = openAsk,
                onDailyRecall = { viewModel.startDailyRecall(false) { actions.onStartPractice() } },
                onLibrary = { openCourseId = null; tab = SpaceTab.LIBRARY },
                onProgress = { openCourseId = null; tab = SpaceTab.PROGRESS }
            )
        }

        if (creating) {
            CreateSessionFlow(
                viewModel = viewModel,
                presetCourseId = createCourseId,
                onClose = { creating = false },
                onRecord = actions.onRecordLecture,
                onImport = actions.onImport,
                onOpenNote = actions.onOpenNote,
                onOpenSession = actions.onOpenSession
            )
            return@LearningAccent
        }

        if (!isHome && openCourseId != null) BackHandler { openCourseId = null }
        else if (!isHome && tab != SpaceTab.LEARN) BackHandler { tab = SpaceTab.LEARN }

        Scaffold(
            modifier = Modifier.testTag("learn_space"),
            containerColor = MM.colors.background,
            // The header pads for the status bar and each bar pads for the navigation bar, so the
            // scaffold itself adds no window insets (no double padding under edge-to-edge).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (isHome) {
                    AppBottomNavigationBar(
                        current = BottomNavDestination.HOME,
                        onNavigate = { if (it != BottomNavDestination.HOME) onNavigateBottomNav?.invoke(it) }
                    )
                } else if (openCourseId == null) {
                    LearningSpaceBar(selected = tab, onSelect = { tab = it })
                }
            }
        ) { padding ->
            // Status bar padding sits outside the scrolling list, so content never slides under the clock.
            Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
                val course = openCourseId?.let { id -> viewModel.courses.collectAsState().value.firstOrNull { it.notebook.id == id } }
                when {
                    isHome -> LearnPane(viewModel, actions, isHome = true, onAsk = openAsk, menu = menu)
                    course != null -> CoursePane(course, viewModel, actions, onBack = { openCourseId = null }, onAddSession = { startCreate(course.notebook.id) })
                    tab == SpaceTab.LEARN -> LearnPane(viewModel, actions, isHome = false, onAsk = openAsk, menu = menu)
                    tab == SpaceTab.LIBRARY -> LibraryPane(viewModel, actions, onOpenCourse = { openCourseId = it }, onNewSession = { startCreate(null) }, menu = menu)
                    else -> ProgressPane(viewModel, actions, menu = menu)
                }
            }
        }

        if (askOpen) {
            val concepts by viewModel.allConcepts.collectAsState()
            AskLearningSheet(
                viewModel = viewModel,
                sessionId = null,
                hasCourse = false,
                concepts = concepts,
                onDismiss = { askOpen = false },
                onStartPractice = actions.onStartPractice,
                onShowInTranscript = null
            )
        }
    }
}

/** Learn · Library · Progress. The selected item sits in an accent-wash pill. */
@Composable
private fun LearningSpaceBar(selected: SpaceTab, onSelect: (SpaceTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MM.colors.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(MMSize.hairline).background(MM.colors.line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.s).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SpaceTab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(MM.radius.card)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(t) })
                        .testTag("learn_space_tab_${t.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.clip(MM.radius.pill)
                            .background(if (on) MM.colors.accentWash else MM.colors.surface)
                            .padding(horizontal = MM.space.l, vertical = MM.space.xs)
                    ) {
                        Icon(t.icon, contentDescription = null, tint = if (on) MM.colors.accent else MM.colors.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                    }
                    Text(t.label, style = MM.type.caption, color = if (on) MM.colors.ink else MM.colors.inkSecondary)
                }
            }
        }
    }
}

@Composable
private fun PaneList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = LearningTheme.readingGutter, end = LearningTheme.readingGutter, top = MM.space.l, bottom = MM.space.xxl),
        verticalArrangement = Arrangement.spacedBy(MM.space.xl),
        content = content
    )
}

@Composable
private fun PaneHeader(title: String, subtitle: String?, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(end = MM.space.xs)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MM.colors.ink)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.display, color = MM.colors.ink, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
        trailing()
    }
}

// ───────────────────────────── Learn ─────────────────────────────

@Composable
private fun LearnPane(viewModel: LearnViewModel, actions: LearningSpaceActions, isHome: Boolean, onAsk: () -> Unit, menu: () -> MenuSection) {
    val brief by viewModel.dailyBrief.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val lectures by viewModel.lectureNotes.collectAsState()
    val practiceError by viewModel.practiceError.collectAsState()
    val busy by viewModel.isLoadingPractice.collectAsState()
    val activities by viewModel.practiceActivities.collectAsState()
    val index by viewModel.currentActivityIndex.collectAsState()
    val practiceTitle by viewModel.practiceTitle.collectAsState()
    var quick by rememberSaveable { mutableStateOf(false) }

    val plan = remember(brief, sessions, lectures, quick) { buildTodayPlan(brief, sessions, lectures, quick) }
    val reviewedToday = brief?.sevenDayAttemptCounts?.lastOrNull() ?: 0
    val now = remember { Calendar.getInstance() }
    val greeting = greetingFor(now.get(Calendar.HOUR_OF_DAY))
    val date = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()) }
    val canResume = activities.isNotEmpty() && index in 1 until activities.size

    fun start(item: PlanItem) {
        when (item.kind) {
            PlanKind.RECALL -> viewModel.startDailyRecall(quick) { actions.onStartPractice() }
            PlanKind.STRENGTHEN -> {
                val sid = item.sessionId ?: return
                val cid = item.concept?.id ?: return
                viewModel.startConceptPractice(sid, cid) { actions.onStartPractice() }
            }
            PlanKind.DIAGNOSTIC -> item.sessionId?.let { sid -> viewModel.startDiagnostic(sid) { actions.onStartPractice() } }
        }
    }

    PaneList {
        item(key = "header") {
            PaneHeader(
                title = greeting,
                subtitle = date,
                onBack = if (isHome) null else actions.onBack,
                trailing = {
                    if (isHome) TextAction("Open Learning", onClick = actions.onOpenSpace, modifier = Modifier.testTag("learn_open_space"))
                    else {
                        if (sessions.isNotEmpty()) TextAction("Ask", onClick = onAsk, modifier = Modifier.testTag("learn_ask"))
                        // The app menu with Learning's own section first: the other spaces, Notes, Settings, one tap away.
                        AppMenuButton(focus = NotebookSpace.LEARNING, local = menu)
                    }
                }
            )
        }
        if (practiceError != null) item(key = "error") { StatusLine(StatusKind.Error, practiceError!!) }
        if (canResume) item(key = "resume") {
            MMCard(Modifier.fillMaxWidth().testTag("learn_resume"), onClick = actions.onStartPractice) {
                Text("PICK UP WHERE YOU STOPPED", style = MM.type.overline, color = MM.colors.accent)
                Text(practiceTitle, style = MM.type.heading, color = MM.colors.ink)
                Text("$index of ${activities.size} done · your answers are saved", style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
        }
        when {
            sessions.isEmpty() && lectures.isEmpty() -> item(key = "first") { FirstRun(actions) }
            !plan.isEmpty -> item(key = "plan") {
                PlanHero(plan = plan, quick = quick, busy = busy, onStart = ::start, onToggleQuick = { quick = !quick })
            }
            else -> item(key = "done") { DoneForToday(reviewedToday) }
        }
        // Lecture notes from YouTube: the same shared card as every home, with Lecture preset.
        item(key = "youtube") {
            val youTube = com.craftflowtechnologies.meetingmind.feature.importing.LocalYouTubeSheet.current
            com.craftflowtechnologies.meetingmind.core.ui.YouTubeNotesCard(
                onClick = { youTube.open(from = com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.LEARNING) },
                modifier = Modifier.testTag("learn_youtube"),
                title = "Lecture from a YouTube video",
                benefit = "Paste a link, get lecture notes with timestamps"
            )
        }
        val recent = sessions.filter { !it.isPaused }.sortedByDescending { it.lastStudiedAt ?: it.updatedAt }.take(2)
        if (recent.isNotEmpty()) {
            item(key = "continue_h") { SectionHeader("Continue", count = null) }
            items(recent, key = { "c_${it.id}" }) { s -> SessionRow(s, lectures, onClick = { actions.onOpenSession(s.noteId) }) }
        }
        val week = brief?.sevenDayActivity.orEmpty()
        if (sessions.isNotEmpty() && week.size == 7) item(key = "week") { ThisWeek(week, brief?.sevenDayAttemptCounts.orEmpty()) }
        if (sessions.isNotEmpty()) item(key = "create") {
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(EntryPoints.learnRecordLecture.label, onClick = actions.onRecordLecture, modifier = Modifier.testTag("learn_record_lecture"))
                TextAction(EntryPoints.learnWriteStudyNote.label, onClick = actions.onWriteStudyNote, modifier = Modifier.testTag("learn_write_study_note"))
            }
        }
    }
}

@Composable
private fun PlanHero(plan: TodayPlan, quick: Boolean, busy: Boolean, onStart: (PlanItem) -> Unit, onToggleQuick: () -> Unit) {
    val first = plan.items.first()
    HeroCard(Modifier.fillMaxWidth().testTag("learn_plan_hero")) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Text(if (quick) "THE FIVE-MINUTE VERSION" else "TODAY'S PLAN", style = MM.type.overline, color = MM.colors.accent)
            Text(
                "About ${plan.minutes} ${if (plan.minutes == 1) "minute" else "minutes"}",
                style = MM.type.display.copy(fontSize = MM.type.title.fontSize, lineHeight = MM.type.title.lineHeight),
                color = MM.colors.ink
            )
            if (plan.reason.isNotBlank()) Text(plan.reason, style = MM.type.secondary, color = MM.colors.inkSecondary)
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                plan.items.forEachIndexed { i, item -> PlanRow(i + 1, item, onClick = { onStart(item) }) }
            }
            PrimaryButton(
                text = if (busy) "Getting ready…" else when (first.kind) {
                    PlanKind.RECALL -> EntryPoints.learnDailyRecall.label
                    PlanKind.STRENGTHEN -> first.title
                    PlanKind.DIAGNOSTIC -> "Take the quick check"
                },
                enabled = !busy,
                onClick = { onStart(first) },
                modifier = Modifier.fillMaxWidth().testTag("learn_plan_start")
            )
            TextAction(
                if (quick) "Show the full plan" else "Only have 5 minutes?",
                onClick = onToggleQuick,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("learn_quick_plan")
            )
        }
    }
}

@Composable
private fun PlanRow(number: Int, item: PlanItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Box(
            Modifier.size(24.dp).clip(MM.radius.pill).background(MM.colors.surface),
            contentAlignment = Alignment.Center
        ) { Text("$number", style = MM.type.caption, color = MM.colors.accent) }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.detail, style = MM.type.caption, color = MM.colors.inkSecondary)
        }
        Text("${item.minutes} min", style = MM.type.caption, color = MM.colors.inkSecondary)
    }
}

@Composable
private fun DoneForToday(reviewedToday: Int) {
    MMCard(Modifier.fillMaxWidth().testTag("learn_done_today")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Box(Modifier.size(36.dp).clip(MM.radius.pill).background(LearningTheme.colors.successWash), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MM.colors.success)
            }
            Column(Modifier.weight(1f)) {
                Text(if (reviewedToday > 0) "You're done for today" else "Nothing due today", style = MM.type.heading, color = MM.colors.ink)
                Text(
                    if (reviewedToday > 0) "$reviewedToday ${if (reviewedToday == 1) "item" else "items"} practised today. Your next reviews are scheduled."
                    else "Reviews are spaced out so they stick. Open a session if you'd like to practise ahead.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
            }
        }
    }
}

@Composable
private fun FirstRun(actions: LearningSpaceActions) {
    Column(Modifier.testTag("learn_first_run"), verticalArrangement = Arrangement.spacedBy(MM.space.l)) {
        EmptyState(
            title = "Your study space",
            body = "Record a lecture or write study notes. MeetingMind turns them into a study guide and practice, with every point linked to the moment it was said.",
            action = { PrimaryButton(EntryPoints.learnRecordLecture.label, onClick = actions.onRecordLecture, modifier = Modifier.testTag("learn_record_lecture")) }
        )
        InsetPanel(Modifier.fillMaxWidth()) {
            Text("HOW IT WORKS", style = MM.type.overline, color = MM.colors.inkMuted)
            listOf(
                "Record or write. Your recordings are saved on this phone.",
                "Get a study guide with the key concepts and where they came from.",
                "Practise a few minutes a day. Reviews are spaced so you remember."
            ).forEachIndexed { i, line ->
                Row(Modifier.padding(top = MM.space.s), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text("${i + 1}", style = MM.type.bodyStrong, color = MM.colors.accent)
                    Text(line, style = MM.type.secondary, color = MM.colors.ink)
                }
            }
        }
        TextAction(EntryPoints.learnWriteStudyNote.label, onClick = actions.onWriteStudyNote, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("learn_write_study_note"))
    }
}

/** Days studied this week. Momentum without streaks: a missed day is never a loss. */
@Composable
private fun ThisWeek(active: List<Boolean>, counts: List<Int>) {
    val days = active.count { it }
    val labels = remember {
        val c = Calendar.getInstance()
        (6 downTo 0).map { back ->
            val d = c.clone() as Calendar
            d.add(Calendar.DAY_OF_YEAR, -back)
            SimpleDateFormat("EEEEE", Locale.getDefault()).format(d.time)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("This week", count = null)
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = "You studied on $days of the last 7 days"
            },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            active.forEachIndexed { i, on ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Box(
                        Modifier.size(28.dp).clip(MM.radius.pill)
                            .background(if (on) MM.colors.accent else MM.colors.track),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Text("${counts.getOrNull(i) ?: ""}", style = MM.type.caption, color = MM.colors.onAccent)
                    }
                    Text(labels.getOrElse(i) { "" }, style = MM.type.caption, color = if (i == 6) MM.colors.ink else MM.colors.inkMuted)
                }
            }
        }
        Text(
            when (days) {
                0 -> "No practice yet this week. A few minutes is enough to start."
                1 -> "You studied on 1 day this week."
                else -> "You studied on $days days this week."
            },
            style = MM.type.secondary,
            color = MM.colors.inkSecondary
        )
    }
}

@Composable
private fun SessionRow(session: LearningSession, lectures: List<LearningRepository.LectureNoteSummary>, onClick: () -> Unit) {
    val lecture = lectures.firstOrNull { it.noteId == session.noteId }
    val meta = listOfNotNull(
        session.courseName?.takeIf { it.isNotBlank() },
        lecture?.conceptCount?.takeIf { it > 0 }?.let { "$it concepts" },
        if (session.isPaused) "Reviews paused" else null
    ).joinToString(" · ")
    MMCard(Modifier.fillMaxWidth().testTag("learn_session_row"), onClick = onClick, contentPadding = MM.space.m) {
        Text(session.title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (meta.isNotBlank()) Text(meta, style = MM.type.caption, color = MM.colors.inkSecondary)
    }
}

// ───────────────────────────── Library ─────────────────────────────

@Composable
private fun LibraryPane(
    viewModel: LearnViewModel,
    actions: LearningSpaceActions,
    onOpenCourse: (String) -> Unit,
    onNewSession: () -> Unit,
    menu: () -> MenuSection
) {
    val courses by viewModel.courses.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val lectures by viewModel.lectureNotes.collectAsState()
    val concepts by viewModel.allConcepts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val shownCourses = courses.filter { q.isEmpty() || it.notebook.name.contains(q, true) || it.properties.courseCode.contains(q, true) }
    val shownSessions = sessions.filter { q.isEmpty() || it.title.contains(q, true) || (it.courseName ?: "").contains(q, true) }
        .sortedByDescending { it.updatedAt }

    PaneList {
        item(key = "header") {
            PaneHeader("Library", subtitle = "${courses.size} courses · ${sessions.size} sessions", onBack = null) {
                AppMenuButton(focus = NotebookSpace.LEARNING, local = menu)
            }
        }
        if (sessions.isNotEmpty() || courses.isNotEmpty()) item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MM.colors.inkMuted) },
                placeholder = { Text("Search courses and sessions", style = MM.type.body, color = MM.colors.inkMuted) },
                textStyle = MM.type.body.copy(color = MM.colors.ink),
                shape = MM.radius.pill,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MM.colors.accent, unfocusedBorderColor = MM.colors.line),
                modifier = Modifier.fillMaxWidth().testTag("learn_library_search")
            )
        }
        item(key = "create") {
            PrimaryButton("New study session", onClick = onNewSession, modifier = Modifier.fillMaxWidth().testTag("learn_new_session"))
        }
        if (shownCourses.isNotEmpty()) {
            item(key = "courses_h") { SectionHeader("Courses", count = shownCourses.size) }
            items(shownCourses, key = { "course_${it.notebook.id}" }) { c ->
                val courseConcepts = concepts.filter { it.sessionId in c.sessionIds }
                CourseCard(c, courseConcepts, onClick = { onOpenCourse(c.notebook.id) })
            }
        }
        if (shownSessions.isNotEmpty()) {
            item(key = "sessions_h") { SectionHeader("Sessions", count = shownSessions.size) }
            items(shownSessions, key = { "s_${it.id}" }) { s -> SessionRow(s, lectures, onClick = { actions.onOpenSession(s.noteId) }) }
        }
        if (q.isNotEmpty() && shownCourses.isEmpty() && shownSessions.isEmpty()) item(key = "nomatch") {
            Text("Nothing matches “$q”.", style = MM.type.body, color = MM.colors.inkSecondary)
        }
        if (q.isEmpty() && courses.isEmpty() && sessions.isEmpty()) item(key = "empty") {
            EmptyState(
                title = "Nothing here yet",
                body = "Lectures you record and study notes you write appear here, grouped by course."
            )
        }
    }
}

@Composable
private fun CourseCard(course: LearningRepository.LearningCourse, concepts: List<LearningConcept>, onClick: () -> Unit) {
    val counts = masteryCounts(concepts)
    MMCard(Modifier.fillMaxWidth().testTag("learn_course_card"), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Text(course.notebook.name, style = MM.type.heading, color = MM.colors.ink)
            val meta = listOfNotNull(
                course.properties.courseCode.takeIf { it.isNotBlank() },
                course.properties.lecturer.takeIf { it.isNotBlank() },
                "${course.sessionCount} ${if (course.sessionCount == 1) "session" else "sessions"}"
            ).joinToString(" · ")
            Text(meta, style = MM.type.caption, color = MM.colors.inkSecondary)
            if (concepts.isNotEmpty()) {
                MasteryBar(counts)
                MasteryLegend(counts)
            }
            if (course.dueReviewCount > 0) {
                Text("${course.dueReviewCount} due for review", style = MM.type.caption, color = MM.colors.accent)
            }
        }
    }
}

@Composable
private fun CoursePane(
    course: LearningRepository.LearningCourse,
    viewModel: LearnViewModel,
    actions: LearningSpaceActions,
    onBack: () -> Unit,
    onAddSession: () -> Unit
) {
    val sessions by viewModel.sessions.collectAsState()
    val lectures by viewModel.lectureNotes.collectAsState()
    val concepts by viewModel.allConcepts.collectAsState()
    val courseSessions = sessions.filter { it.id in course.sessionIds }.sortedByDescending { it.createdAt }
    val courseConcepts = concepts.filter { it.sessionId in course.sessionIds }
    val counts = masteryCounts(courseConcepts)
    val attention = courseConcepts.filter { it.state == LearningMasteryState.NEEDS_REVIEW }.take(5)

    PaneList {
        item(key = "header") {
            PaneHeader(
                title = course.notebook.name,
                subtitle = listOfNotNull(
                    course.properties.courseCode.takeIf { it.isNotBlank() },
                    course.properties.lecturer.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifBlank { null },
                onBack = onBack
            )
        }
        if (courseConcepts.isNotEmpty()) item(key = "mastery") {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                MasteryBar(counts)
                MasteryLegend(counts)
            }
        }
        if (attention.isNotEmpty()) {
            item(key = "att_h") { SectionHeader("Needs review", count = attention.size) }
            items(attention, key = { "a_${it.id}" }) { c ->
                AttentionRow(c, sessionTitle = sessions.firstOrNull { it.id == c.sessionId }?.title, onStrengthen = {
                    viewModel.startConceptPractice(c.sessionId, c.id) { actions.onStartPractice() }
                })
            }
        }
        item(key = "sess_h") { SectionHeader("Sessions", count = courseSessions.size) }
        if (courseSessions.isEmpty()) item(key = "none") {
            Text("No sessions in this course yet.", style = MM.type.body, color = MM.colors.inkSecondary)
        }
        items(courseSessions, key = { "cs_${it.id}" }) { s -> SessionRow(s, lectures, onClick = { actions.onOpenSession(s.noteId) }) }
        item(key = "add") { TextAction("Add a session to this course", onClick = onAddSession, modifier = Modifier.testTag("learn_course_add")) }
    }
}

@Composable
private fun AttentionRow(concept: LearningConcept, sessionTitle: String?, onStrengthen: () -> Unit) {
    MMCard(Modifier.fillMaxWidth(), contentPadding = MM.space.m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            MasteryGlyph(concept.state, size = 18.dp)
            Column(Modifier.weight(1f)) {
                Text(concept.name, style = MM.type.bodyStrong, color = MM.colors.ink)
                Text(
                    listOfNotNull(sessionTitle, concept.state.plainReason()).joinToString(" · "),
                    style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            TextAction("Strengthen", onClick = onStrengthen)
        }
    }
}

// ───────────────────────────── Progress ─────────────────────────────

@Composable
private fun ProgressPane(viewModel: LearnViewModel, actions: LearningSpaceActions, menu: () -> MenuSection) {
    val concepts by viewModel.allConcepts.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val brief by viewModel.dailyBrief.collectAsState()
    val counts = masteryCounts(concepts)
    val attention = concepts.filter { it.state == LearningMasteryState.NEEDS_REVIEW }.take(6)
    val reviewedThisWeek = brief?.sevenDayAttemptCounts.orEmpty().sum()
    val daysThisWeek = brief?.sevenDayActivity.orEmpty().count { it }

    PaneList {
        item(key = "header") {
            PaneHeader("Progress", subtitle = "Built from your answers over time. No scores, no predictions.", onBack = null) {
                AppMenuButton(focus = NotebookSpace.LEARNING, local = menu)
            }
        }
        if (concepts.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "Nothing to show yet",
                    body = "Once you practise, each concept moves from New to Strong based on what you recall, and you'll see why here."
                )
            }
            return@PaneList
        }
        item(key = "all") {
            MMCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text("ALL CONCEPTS · ${concepts.size}", style = MM.type.overline, color = MM.colors.inkMuted)
                    MasteryBar(counts)
                    MasteryLegend(counts)
                }
            }
        }
        if (attention.isNotEmpty()) {
            item(key = "att_h") { SectionHeader("Needs review", count = attention.size) }
            items(attention, key = { "p_${it.id}" }) { c ->
                AttentionRow(c, sessionTitle = sessions.firstOrNull { it.id == c.sessionId }?.title, onStrengthen = {
                    viewModel.startConceptPractice(c.sessionId, c.id) { actions.onStartPractice() }
                })
            }
        }
        val withConcepts = courses.mapNotNull { course ->
            val cs = concepts.filter { it.sessionId in course.sessionIds }
            if (cs.isEmpty()) null else course to cs
        }
        if (withConcepts.isNotEmpty()) {
            item(key = "courses_h") { SectionHeader("By course", count = withConcepts.size) }
            items(withConcepts, key = { "pc_${it.first.notebook.id}" }) { (course, cs) ->
                val cc = masteryCounts(cs)
                MMCard(Modifier.fillMaxWidth(), contentPadding = MM.space.m) {
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        Text(course.notebook.name, style = MM.type.bodyStrong, color = MM.colors.ink)
                        MasteryBar(cc)
                        MasteryLegend(cc)
                    }
                }
            }
        }
        item(key = "week") {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                SectionHeader("This week", count = null)
                Text(
                    "$reviewedThisWeek ${if (reviewedThisWeek == 1) "answer" else "answers"} on $daysThisWeek ${if (daysThisWeek == 1) "day" else "days"}.",
                    style = MM.type.body,
                    color = MM.colors.ink
                )
                Text(
                    "Strong means recalled correctly over at least a week. Needs review means missed recently or a late review, and it's normal.",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
            }
        }
    }
}

/**
 * Learning's own menu section, shown first when the app menu is opened in Learning (the main menu
 * is the base; this replaces its short Learning group). Pure, so its rows are unit-tested.
 */
internal fun learningMenu(
    hasSessions: Boolean,
    onNewSession: () -> Unit,
    onAsk: () -> Unit,
    onDailyRecall: () -> Unit,
    onLibrary: () -> Unit,
    onProgress: () -> Unit
): MenuSection = MenuSection(
    "learn", "Learning", listOfNotNull(
        MenuEntry("learn.new", "New study session", MenuIcon.CREATE, MenuTarget.Run(onNewSession), "Record, use what you have, import or write"),
        if (hasSessions) MenuEntry("learn.ask", "Ask Learning", MenuIcon.AI, MenuTarget.Run(onAsk), "Answers from your lectures, with the moment each was said") else null,
        if (hasSessions) MenuEntry("learn.recall", "Daily recall", MenuIcon.PRACTICE, MenuTarget.Run(onDailyRecall), "Today's reviews, spaced so you remember") else null,
        MenuEntry("learn.library", "Library", MenuIcon.LIBRARY, MenuTarget.Run(onLibrary), "Courses and sessions"),
        MenuEntry("learn.progress", "Progress", MenuIcon.PULSE, MenuTarget.Run(onProgress), "What you know, built from your answers"),
        MenuEntry("learn.record", "Record a lecture", MenuIcon.RECORD, MenuTarget.Go(Routes.recordTypeRoute(RecordingType.LECTURE))),
        MenuEntry("learn.youtube", "Lecture from a YouTube video", MenuIcon.VIDEO, MenuTarget.YouTube(NotebookSpace.LEARNING), "Paste a link, get notes and practice cards"),
        MenuEntry("learn.import", "Learn from a file", MenuIcon.IMPORT, MenuTarget.Go(Routes.IMPORT), "Audio and documents")
    ), NotebookSpace.LEARNING
)
