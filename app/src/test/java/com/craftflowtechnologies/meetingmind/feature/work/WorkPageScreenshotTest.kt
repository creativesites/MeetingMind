package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.notes.PagedState
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/** Each Work segment in light and dark, empty and populated, so the look can be reviewed and guarded. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class WorkPageScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val startOfToday = Calendar.getInstance().apply { clear(); set(2025, Calendar.OCTOBER, 9) }.timeInMillis
    private val now = startOfToday + 11 * hour

    private fun shoot(name: String, dark: Boolean, segment: WorkSegment, content: @Composable () -> Unit) {
        rule.setContent {
            MMPreviewFrame(dark) {
                Column(Modifier.fillMaxSize().background(MM.colors.background)) {
                    Column(Modifier.padding(horizontal = MM.space.l)) {
                        WorkPageHeader(
                            contextLine = "Thursday 9 Oct · 3 meetings · 2 need you",
                            addActions = listOf(WorkMenuAction("Record meeting") {}),
                            moreActions = listOf(WorkMenuAction("Inbox", badge = if (segment == WorkSegment.TODAY) 3 else 0) {}),
                            onSearch = {}
                        )
                        SegmentedControl(WorkSegment.entries.map { it.label }, segment.ordinal, {})
                    }
                    Box(Modifier.weight(1f)) { content() }
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

    private fun project(id: String, name: String, org: String?, notes: Int, tasks: Int, ago: Long?, confidential: Boolean = false) = ProjectCard(
        Notebook(id, name, NotebookSpace.WORK, null, null, 0L, 0L, isProject = true,
            propertiesJson = org.let { "{\"status\":\"Active\"" + (if (confidential) ",\"confidential\":true" else "") + "}" }),
        notes, tasks, org, ago?.let { now - it }
    )

    private val projects = listOf(
        project("p1", "Acme website relaunch", "Acme", 14, 5, hour),
        project("p2", "Q4 board pack", null, 6, 1, 3 * day, confidential = true),
        project("p3", "Hiring", null, 3, 0, 12 * day)
    )

    private fun task(id: String, title: String, due: Long?, meeting: String? = null, owner: String? = null, waiting: Boolean = false) =
        WorkTaskRow(WorkTask(id, title, due, null, waiting, owner, null, meeting, null, 0L, TaskKind.TASK), Cursor(due ?: Long.MAX_VALUE, id))

    private val taskRows = listOf(
        task("t1", "Send the revised quote to Acme", startOfToday - day, "m1"),
        task("t2", "Review the migration plan", startOfToday + 2 * hour),
        task("t3", "Book the offsite venue", startOfToday + 3 * day, "m2"),
        task("t4", "Draft the Q4 narrative", startOfToday + 12 * day),
        task("t5", "Reply to the recruiter", null)
    )
    private val titles = mapOf("m1" to "Acme kickoff", "m2" to "Weekly 1:1 with Priya")

    private fun todayModel() = TodayModel(
        hero = TodayHero.Meeting("in 25 min", "Acme design review", "With Priya, Sam +1", "Last time: Acme kickoff · 2 open items", {}, {}),
        needs = listOf(
            NeedsItem("1", "Q3 planning", "Meeting · yesterday · ready to wrap up", "Review") {},
            NeedsItem("2", "Acme kickoff", "Meeting · Tue · follow-up not sent", "Send") {},
            NeedsItem("3", "Send the revised quote", "Overdue · Acme kickoff", "Done") {}
        ),
        upcoming = listOf(ScheduleRow("a", "Acme design review", "Priya, Sam", "11:30 – 12:00") {}, ScheduleRow("b", "Board prep", "Alex", "15:00 – 15:45") {}),
        earlier = listOf(ScheduleRow("c", "Standup", null, "09:30 – 09:45") {}),
        recent = notes.take(3).map { n -> RecentNote(n.id, NoteRowModel(n.displayTitle, n.preview, androidx.compose.ui.graphics.Color.Gray, n.hasRecording, n.transcript, n.openTasks, 0, "10:30", n.pinned)) {} }
    )

    // ---------------------------------------------------------------- tests

    @Composable private fun TodayCase(m: TodayModel) = TodayContent(m, rememberLazyListState(), {}, {}, {})
    @Composable private fun NotesCase(q: NotesQuery, s: PagedState<WorkNoteItem>) =
        NotesContent(q, s, now, projects, emptySet(), rememberLazyListState(), NotesActions())
    @Composable private fun TasksCase(waiting: Boolean, s: PagedState<WorkTaskRow>) = TasksContent(waiting, s, now, titles, rememberLazyListState(), TasksActions())
    @Composable private fun ProjectsCase(p: List<ProjectCard>) = ProjectsContent(p, "Project", "Projects", now, rememberLazyListState(), {}, {})

    @Test fun today_populated_light() = shoot("today_populated", false, WorkSegment.TODAY) { TodayCase(todayModel()) }
    @Test fun today_populated_dark() = shoot("today_populated", true, WorkSegment.TODAY) { TodayCase(todayModel()) }
    @Test fun today_empty_light() = shoot("today_empty", false, WorkSegment.TODAY) { TodayCase(TodayModel()) }
    @Test fun today_empty_dark() = shoot("today_empty", true, WorkSegment.TODAY) { TodayCase(TodayModel()) }

    @Test fun notes_populated_light() = shoot("notes_populated", false, WorkSegment.NOTES) { NotesCase(NotesQuery(), PagedState(notes, endReached = true)) }
    @Test fun notes_populated_dark() = shoot("notes_populated", true, WorkSegment.NOTES) { NotesCase(NotesQuery(), PagedState(notes, endReached = true)) }
    @Test fun notes_empty_light() = shoot("notes_empty", false, WorkSegment.NOTES) { NotesCase(NotesQuery(), PagedState(endReached = true)) }
    @Test fun notes_empty_dark() = shoot("notes_empty", true, WorkSegment.NOTES) { NotesCase(NotesQuery(), PagedState(endReached = true)) }
    @Test fun notes_loading_light() = shoot("notes_loading", false, WorkSegment.NOTES) { NotesCase(NotesQuery(), PagedState(isLoadingFirst = true)) }
    @Test fun notes_loading_dark() = shoot("notes_loading", true, WorkSegment.NOTES) { NotesCase(NotesQuery(), PagedState(isLoadingFirst = true)) }
    @Test fun notes_appending_light() = shoot("notes_appending", false, WorkSegment.NOTES) { NotesCase(NotesQuery(setOf(NoteChip.MEETINGS)), PagedState(notes, isAppending = true)) }
    @Test fun notes_error_dark() = shoot("notes_error", true, WorkSegment.NOTES) { NotesCase(NotesQuery(), PagedState(notes, error = RuntimeException("offline"))) }

    @Test fun tasks_populated_light() = shoot("tasks_populated", false, WorkSegment.TASKS) { TasksCase(false, PagedState(taskRows, endReached = true)) }
    @Test fun tasks_populated_dark() = shoot("tasks_populated", true, WorkSegment.TASKS) { TasksCase(false, PagedState(taskRows, endReached = true)) }
    @Test fun tasks_empty_light() = shoot("tasks_empty", false, WorkSegment.TASKS) { TasksCase(false, PagedState(endReached = true)) }
    @Test fun tasks_empty_dark() = shoot("tasks_empty", true, WorkSegment.TASKS) { TasksCase(true, PagedState(endReached = true)) }

    @Test fun projects_populated_light() = shoot("projects_populated", false, WorkSegment.PROJECTS) { ProjectsCase(projects) }
    @Test fun projects_populated_dark() = shoot("projects_populated", true, WorkSegment.PROJECTS) { ProjectsCase(projects) }
    @Test fun projects_empty_light() = shoot("projects_empty", false, WorkSegment.PROJECTS) { ProjectsCase(emptyList()) }
    @Test fun projects_empty_dark() = shoot("projects_empty", true, WorkSegment.PROJECTS) { ProjectsCase(emptyList()) }
}
