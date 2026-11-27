package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.work.AttentionRow
import com.craftflowtechnologies.meetingmind.core.ui.work.DayRibbon
import com.craftflowtechnologies.meetingmind.core.ui.work.RibbonBlock
import com.craftflowtechnologies.meetingmind.core.ui.work.TaskCheck
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.time.LocalTime

/**
 * The Work page's home: the "Today" pane of the in-vertical bar, the landing of Work. Top to bottom, every part
 * answering one question:
 *  1. Now: what is next today (the next meeting, with Record and Prepare), or a way to start when the day is free.
 *  2. Recent: the latest Work recordings in every state, so a meeting just recorded is there straight away
 *     (drawn by the shared [com.craftflowtechnologies.meetingmind.feature.work.home.WorkRecentRecordings]).
 *  3. Needs you: at most four rows, each with one verb.
 *  4. Tasks: due today and overdue, with a check; the Waiting-on count; All opens Tasks.
 *  5. Projects and People, compact, each with All.
 *  6. Ask.
 * An empty part draws nothing, so a quiet day is a short page, never a page of empty boxes. MM tokens only.
 */
internal class WorkHomeModel(
    val now: WorkNow,
    val needs: List<PulseNeed>,
    val needsTotal: Int,
    val tasks: List<WorkHomeTask>,
    val tasksTotal: Int,
    /** "Waiting on 3 people" or null when nobody owes anything. */
    val waitingLine: String?,
    val onAllTasks: () -> Unit,
    val onWaiting: () -> Unit,
    val projectsTitle: String,
    val projects: List<WorkHomeLink>,
    val onAllProjects: () -> Unit,
    val peopleTitle: String,
    val people: List<WorkHomeLink>,
    val onAllPeople: () -> Unit,
    val onAsk: () -> Unit
)

/** The Now card: the next meeting today, or nothing left on the calendar. */
internal sealed interface WorkNow {
    class Next(
        /** "In 20 min", "Now", "At 14:00". */
        val whenLabel: String,
        val title: String,
        /** "With Sarah, Tom" or the place; null when neither is known. */
        val withLine: String?,
        val blocks: List<RibbonBlock>,
        val now: LocalTime?,
        /** "Then: Design review · 15:00", the meeting after this one, or null. */
        val thenLine: String?,
        val onRecord: () -> Unit,
        val onPrepare: () -> Unit
    ) : WorkNow

    /** Nothing more on the calendar today. [doneLine] says what the day held ("2 meetings earlier today"). */
    class Free(val doneLine: String?, val onRecord: () -> Unit, val onWrite: () -> Unit) : WorkNow
}

/** One task row: its check completes it; the row opens it. */
internal class WorkHomeTask(val id: String, val title: String, val line: String, val overdue: Boolean, val onToggle: () -> Unit, val onOpen: () -> Unit)

/** A project or a person on the home: a name, one line, and where it opens. */
internal class WorkHomeLink(val id: String, val name: String, val line: String?, val onOpen: () -> Unit)

/** The rules behind the home, as plain functions so they are unit-tested on the JVM. */
internal object WorkHomeRules {
    /** Most task rows the home lists before "All". */
    const val TASKS_SHOWN = 4
    const val PROJECTS_SHOWN = 3
    const val PEOPLE_SHOWN = 8
    const val NEEDS_SHOWN = 4

    /** The tasks the home lists: overdue first, then due today, earliest first. Tasks with no date, or due later, wait in Tasks. */
    fun todayTasks(mine: List<WorkTask>, now: Long, endOfToday: Long): List<WorkTask> =
        mine.asSequence()
            .filter { !it.done && it.dueAt != null && it.dueAt < endOfToday }
            .sortedWith(compareBy<WorkTask>({ !it.isOverdue(now) }, { it.dueAt }))
            .toList()

    /** "Waiting on Sarah", "Waiting on 3 people", or null. Counts people, not tasks; a task with no owner counts once. */
    fun waitingLine(waiting: List<WorkTask>): String? {
        if (waiting.isEmpty()) return null
        val names = waiting.map { it.ownerName?.trim()?.takeIf { n -> n.isNotEmpty() } ?: "\u0000${it.id}" }.distinct()
        val one = names.singleOrNull()
        return if (one != null && !one.startsWith("\u0000")) "Waiting on $one" else "Waiting on ${names.size} " + if (names.size == 1) "person" else "people"
    }

    /** What the Free card says about the day so far, or null before the first meeting. */
    fun doneLine(meetingsEnded: Int): String? = when {
        meetingsEnded <= 0 -> null
        meetingsEnded == 1 -> "1 meeting earlier today"
        else -> "$meetingsEnded meetings earlier today"
    }
}

@Composable
internal fun WorkHomePane(
    model: WorkHomeModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    /** Whether there is anything recent; false leaves the section out, so no empty gap is left. */
    hasRecent: Boolean = false,
    /** The Recent section, drawn by the shared card. */
    recent: @Composable () -> Unit = {}
) {
    LazyColumn(
        modifier.fillMaxSize().testTag("work_home_list"), state = listState,
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xxl),
        verticalArrangement = Arrangement.spacedBy(MM.space.xl)
    ) {
        item(key = "now") { NowCard(model.now) }
        if (hasRecent) item(key = "recent") { recent() }
        if (model.needs.isNotEmpty()) item(key = "needs") {
            Column {
                SectionHeader("Needs you", count = model.needsTotal)
                MMCard(contentPadding = MM.space.s) {
                    model.needs.forEach { n ->
                        AttentionRow(
                            kindLabel = n.kind, title = n.title, subtitle = n.line, verb = n.verb,
                            onVerb = n.onVerb, onClick = n.onOpen, leadingIcon = n.icon,
                            modifier = Modifier.testTag("work_need_${n.key}"), tone = n.tone
                        )
                    }
                }
            }
        }
        item(key = "tasks") { TasksSection(model) }
        if (model.projects.isNotEmpty()) item(key = "projects") {
            Column {
                SectionHeader(model.projectsTitle, actionLabel = "All", onAction = model.onAllProjects)
                MMCard(contentPadding = MM.space.s) {
                    model.projects.forEach { p ->
                        ListRow(
                            p.name, subtitle = p.line, onClick = p.onOpen,
                            modifier = Modifier.padding(horizontal = MM.space.s).testTag("work_home_project_${p.id}")
                        )
                    }
                }
            }
        }
        if (model.people.isNotEmpty()) item(key = "people") {
            Column {
                SectionHeader(model.peopleTitle, actionLabel = "All", onAction = model.onAllPeople)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                    items(model.people, key = { it.id }) { p -> PersonChip(p) }
                }
            }
        }
        item(key = "ask") { AskSection(model.onAsk) }
    }
}

@Composable
private fun NowCard(now: WorkNow) {
    when (now) {
        is WorkNow.Next -> HeroCard(Modifier.fillMaxWidth().testTag("work_home_next")) {
            Text("NEXT · ${now.whenLabel.uppercase()}", style = MM.type.overline, color = MM.colors.accent)
            Text(
                now.title, style = MM.type.title, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MM.space.xs)
            )
            now.withLine?.let { Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (now.blocks.isNotEmpty()) DayRibbon(modifier = Modifier.padding(top = MM.space.m), blocks = now.blocks, now = now.now)
            now.thenLine?.let { Text(it, style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.s)) }
            Row(Modifier.padding(top = MM.space.m), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                PrimaryButton("Record", now.onRecord, leadingIcon = Icons.Rounded.FiberManualRecord)
                SecondaryButton("Prepare", now.onPrepare)
            }
        }
        is WorkNow.Free -> HeroCard(Modifier.fillMaxWidth().testTag("work_home_free")) {
            Text("TODAY", style = MM.type.overline, color = MM.colors.accent)
            Text(
                "Nothing else on your calendar", style = MM.type.title, color = MM.colors.ink,
                modifier = Modifier.padding(top = MM.space.xs)
            )
            Text(
                now.doneLine?.let { "$it. Record a meeting or write a note, and it lands here." }
                    ?: "Record a meeting or write a note, and it lands here with its tasks and decisions.",
                style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs)
            )
            Row(Modifier.padding(top = MM.space.m), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                PrimaryButton("Record", now.onRecord, leadingIcon = Icons.Rounded.FiberManualRecord)
                SecondaryButton("Write a note", now.onWrite)
            }
        }
    }
}

@Composable
private fun TasksSection(model: WorkHomeModel) {
    Column(Modifier.testTag("work_home_tasks")) {
        SectionHeader(
            "Tasks", count = model.tasksTotal.takeIf { it > 0 },
            actionLabel = "All", onAction = model.onAllTasks
        )
        MMCard(contentPadding = MM.space.s) {
            if (model.tasks.isEmpty()) {
                Text(
                    "Nothing due today. What you agree to in a meeting lands here with its date.",
                    style = MM.type.secondary, color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.s)
                )
            }
            model.tasks.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).testTag("work_home_task_${t.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TaskCheck(checked = false, priority = if (t.overdue) 1 else 3, onToggle = { t.onToggle() })
                    Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = t.onOpen).padding(vertical = MM.space.xs)) {
                        Text(t.title, style = MM.type.body, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.line, style = MM.type.caption, color = if (t.overdue) MM.colors.danger else MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            model.waitingLine?.let { w ->
                Text(
                    w, style = MM.type.secondary, color = MM.colors.inkSecondary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
                        .clickable(role = Role.Button, onClick = model.onWaiting)
                        .padding(horizontal = MM.space.s, vertical = MM.space.m)
                        .testTag("work_home_waiting")
                )
            }
        }
    }
}

@Composable
private fun PersonChip(p: WorkHomeLink) {
    Column(
        Modifier.widthIn(max = MMSize.minTouch * 2).clip(MM.radius.small)
            .clickable(role = Role.Button, onClick = p.onOpen).padding(MM.space.xs)
            .testTag("work_home_person_${p.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        Box(
            Modifier.size(MMSize.minTouch).clip(CircleShape).background(MM.colors.accentWash),
            contentAlignment = Alignment.Center
        ) {
            Text(initials(p.name), style = MM.type.heading, color = MM.colors.accent)
        }
        Text(p.name, style = MM.type.caption, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        p.line?.let { Text(it, style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
