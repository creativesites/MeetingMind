package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.notes.PagedState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.ui.work.AttentionTone
import com.craftflowtechnologies.meetingmind.core.ui.work.RibbonBlock
import com.craftflowtechnologies.meetingmind.core.ui.work.VerticalBar
import com.craftflowtechnologies.meetingmind.core.ui.work.VerticalBarItem
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import java.util.Calendar

/** The Work panes in light and dark, populated, quiet and empty, so the look can be reviewed and guarded. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class WorkPageScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val startOfToday = Calendar.getInstance().apply { clear(); set(2025, Calendar.OCTOBER, 9) }.timeInMillis
    private val now = startOfToday + 11 * hour

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            MMPreviewFrame(dark) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas provides true,
                    com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion provides true
                ) {
                    Column(Modifier.fillMaxSize().background(MM.colors.background)) {
                        Box(Modifier.weight(1f)) { content() }
                        VerticalBar(
                            items = listOf(
                                VerticalBarItem("pulse", "Today", Icons.Filled.Warning),
                                VerticalBarItem("tasks", "Tasks", Icons.Filled.Warning),
                                VerticalBarItem("notes", "Notes", Icons.Filled.Warning),
                                VerticalBarItem("projects", "Projects", Icons.Filled.Warning),
                                VerticalBarItem("decisions", "Decisions", Icons.Filled.Warning)
                            ),
                            selected = name.substringBefore('_').let { if (it == "home") "pulse" else "notes" },
                            onSelect = {},
                            homeItem = {}
                        )
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/work/${name}_${if (dark) "dark" else "light"}.png")
    }

    // ---------------------------------------------------------------- fixtures

    private fun note(id: String, title: String, preview: String, at: Long, rec: Boolean = false, tasks: Int = 0, transcript: NoteTranscriptStatus = NoteTranscriptStatus.None, pinned: Boolean = false) =
        WorkNoteItem(id, title, preview, null, RecordingType.MEETING, at, pinned, false, rec, transcript, tasks, Cursor(at, id))

    private val notes = listOf(
        note("1", "Acme kickoff", "Agreed scope for phase one. Sam owns the migration plan and Priya sends the revised quote by Friday.", now - hour, rec = true, tasks = 3, transcript = NoteTranscriptStatus.Ready, pinned = true),
        note("2", "Weekly 1:1 with Priya", "Blockers: design handoff. Next: prototype review on Monday.", now - 2 * hour, rec = true, transcript = NoteTranscriptStatus.Processing),
        note("3", "Pricing options", "Three tiers, annual discount, and what to say to the Acme team about the legacy plan.", now - day - hour, tasks = 1),
        note("4", "Standup", "Shipped the export fix. Payments still flaky on Android 12.", now - 3 * day, rec = true, tasks = 2, transcript = NoteTranscriptStatus.Failed),
        note("5", "Board prep", "Narrative, numbers, asks.", now - 9 * day),
        note("6", "Hiring plan", "Two engineers, one designer, in that order.", now - 40 * day)
    )

    private fun needRow(key: String, title: String, line: String, verb: String, tone: AttentionTone) =
        PulseNeed(key, "Needs you", title, line, verb, tone, Icons.Filled.Warning, {}, {})

    private fun task(id: String, title: String, line: String, overdue: Boolean = false) = WorkHomeTask(id, title, line, overdue, {}, {})

    private fun homeModel(now: WorkNow, needs: List<PulseNeed> = emptyList(), tasks: List<WorkHomeTask> = emptyList(), full: Boolean = true) = WorkHomeModel(
        now = now,
        needs = needs,
        needsTotal = needs.size,
        tasks = tasks,
        tasksTotal = tasks.size,
        waitingLine = if (full) "Waiting on 2 people" else null,
        onAllTasks = {}, onWaiting = {},
        projectsTitle = "Projects",
        projects = if (full) listOf(WorkHomeLink("p1", "Acme launch", "Acme · 4 open tasks · Active Today") {}, WorkHomeLink("p2", "Hiring", "2 open tasks") {}) else emptyList(),
        onAllProjects = {},
        peopleTitle = "People",
        people = if (full) listOf(WorkHomeLink("a", "Priya", "owes you") {}, WorkHomeLink("b", "Sam", "you owe") {}, WorkHomeLink("c", "Alex", null) {}) else emptyList(),
        onAllPeople = {},
        onAsk = {}
    )

    private val next = WorkNow.Next(
        whenLabel = "in 25 min",
        title = "Acme design review",
        withLine = "With Priya, Sam +1",
        blocks = listOf(
            RibbonBlock(LocalTime.of(9, 30), LocalTime.of(10, 0), "Standup", {}, past = true),
            RibbonBlock(LocalTime.of(11, 30), LocalTime.of(12, 0), "Acme design review", {})
        ),
        now = LocalTime.of(11, 0),
        thenLine = "Then: Board prep · 15:00",
        onRecord = {},
        onPrepare = {}
    )

    private val recentRows = listOf(
        WorkRecent.Row("m1", null, "Weekly sync with Priya", WorkRecent.Kind.WRITING, "Writing your notes · 42%"),
        WorkRecent.Row("m2", "n2", "Acme kickoff", WorkRecent.Kind.READY, "Today · 38 min"),
        WorkRecent.Row("m3", null, "Client call", WorkRecent.Kind.SAVED, "Notes not made yet · tap to make them")
    )

    @Composable private fun RecentCase() = WorkRecentSection(recentRows, onOpen = {}, onAll = {})

    private val needs = listOf(
        needRow("1", "Commercial follow-up · 38 min", "Recording is safe. Transcription stopped at 61%.", "Try again", AttentionTone.DANGER),
        needRow("2", "SSO change-request estimate", "Due today · promised to Sarah", "Done", AttentionTone.ACCENT),
        needRow("3", "Sarah Chen · production credentials", "Said Fri 13 Nov · 4 days past", "Nudge", AttentionTone.WARNING)
    )

    // ---------------------------------------------------------------- tests

    @Composable private fun HomeCase(m: WorkHomeModel, recent: Boolean = true) =
        WorkHomePane(m, rememberLazyListState(), hasRecent = recent) { RecentCase() }
    @Composable private fun NotesCase(q: NotesQuery, s: PagedState<WorkNoteItem>) =
        NotesContent(q, s, now, emptyList(), emptySet(), rememberLazyListState(), NotesActions())

    @Test fun home_populated_light() = shoot("home_populated", false) { HomeCase(homeModel(next, needs, listOf(task("t1", "Send the revised quote to Acme", "Overdue · Yesterday · Acme kickoff", overdue = true), task("t2", "SSO change-request estimate", "Due 15:00")))) }
    @Test fun home_populated_dark() = shoot("home_populated", true) { HomeCase(homeModel(next, needs, listOf(task("t1", "Send the revised quote to Acme", "Overdue · Yesterday · Acme kickoff", overdue = true), task("t2", "SSO change-request estimate", "Due 15:00")))) }
    @Test fun home_free_day_light() = shoot("home_free_day", false) { HomeCase(homeModel(WorkNow.Free("2 meetings earlier today", {}, {}), emptyList(), listOf(task("t1", "Send the revised quote to Acme", "Overdue · Yesterday · Acme kickoff", overdue = true), task("t2", "SSO change-request estimate", "Due 15:00")))) }
    @Test fun home_free_day_dark() = shoot("home_free_day", true) { HomeCase(homeModel(WorkNow.Free("2 meetings earlier today", {}, {}), emptyList(), listOf(task("t1", "Send the revised quote to Acme", "Overdue · Yesterday · Acme kickoff", overdue = true), task("t2", "SSO change-request estimate", "Due 15:00")))) }
    @Test fun home_first_day_light() = shoot("home_first_day", false) { HomeCase(homeModel(WorkNow.Free(null, {}, {}), full = false), recent = false) }
    @Test fun home_first_day_dark() = shoot("home_first_day", true) { HomeCase(homeModel(WorkNow.Free(null, {}, {}), full = false), recent = false) }

    @Test fun notes_populated_light() = shoot("notes_populated", false) { NotesCase(NotesQuery(), PagedState(notes, endReached = true)) }
    @Test fun notes_populated_dark() = shoot("notes_populated", true) { NotesCase(NotesQuery(), PagedState(notes, endReached = true)) }
    @Test fun notes_empty_light() = shoot("notes_empty", false) { NotesCase(NotesQuery(), PagedState(endReached = true)) }
    @Test fun notes_empty_dark() = shoot("notes_empty", true) { NotesCase(NotesQuery(), PagedState(endReached = true)) }
    @Test fun notes_loading_light() = shoot("notes_loading", false) { NotesCase(NotesQuery(), PagedState(isLoadingFirst = true)) }
    @Test fun notes_error_dark() = shoot("notes_error", true) { NotesCase(NotesQuery(), PagedState(notes, error = RuntimeException("offline"))) }
}
