package com.example.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.work.ItemKind
import com.example.core.work.MeetingRow
import com.example.core.work.Person
import com.example.core.work.WorkItem
import com.example.core.work.WorkSection
import com.example.core.work.WorkSettings
import com.example.core.work.WorkView
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.SurfaceSunk
import java.util.Calendar

/** Everything the Work Home shows, read once per composition. */
@Immutable
data class WorkHomeState(
    val settings: WorkSettings,
    val toReview: List<MeetingRow>,
    val followUps: List<MeetingRow>,
    val myTasks: List<WorkItem>,
    val waitingOn: List<WorkItem>,
    val questions: List<WorkItem>,
    val people: List<Person>,
    val titles: Map<String, String>
) {
    val overdue: Int get() = myTasks.count { it.isOverdue(System.currentTimeMillis()) }

    /** "2 to review · 1 overdue": the hero's work line, only the parts that are true. */
    val line: String? get() = listOfNotNull(
        toReview.size.takeIf { it > 0 }?.let { "$it to review" },
        followUps.size.takeIf { it > 0 }?.let { "$it to send" },
        overdue.takeIf { it > 0 }?.let { "$it overdue" }
    ).joinToString(" · ").ifEmpty { null }

    val isEmpty: Boolean get() = toReview.isEmpty() && followUps.isEmpty() && myTasks.isEmpty() && waitingOn.isEmpty() && questions.isEmpty()
}

@Composable
fun rememberWorkHome(viewModel: WorkViewModel): WorkHomeState {
    val settings by viewModel.settings.collectAsState()
    val toReview by viewModel.toReview.collectAsState()
    val followUps by viewModel.followUps.collectAsState()
    val open by viewModel.open.collectAsState()
    val everyone by viewModel.everyone.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val self by viewModel.self.collectAsState()
    return remember(settings, toReview, followUps, open, everyone, titles, self) {
        val today = com.example.core.work.DueDates.startOfDay(System.currentTimeMillis())
        val week = today + 7L * 24 * 60 * 60 * 1000
        WorkHomeState(
            settings = settings,
            toReview = toReview,
            followUps = followUps,
            // Home shows what's due soon; the Work tab holds the rest.
            myTasks = viewModel.view(open, WorkView.MY_TASKS).filter { it.dueAt == null || it.dueAt < week }
                .sortedWith(compareBy({ it.dueAt == null }, { it.dueAt })),
            waitingOn = viewModel.view(open, WorkView.WAITING_ON),
            questions = viewModel.view(open, WorkView.OPEN_QUESTIONS),
            people = everyone.filter { !it.isSelf && it.lastSeenAt != null }.sortedByDescending { it.lastSeenAt }.take(10),
            titles = titles
        )
    }
}

/**
 * The order sections appear in: the person's own, nudged by the time of day unless they fixed it
 * (PLAN_PROFESSIONAL.md §7.2). After the working day, what's left to review and send comes first.
 */
fun orderedSections(settings: WorkSettings, now: Calendar = Calendar.getInstance()): List<WorkSection> {
    val base = settings.visibleSections
    val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val afterWork = minute >= settings.workEndMinute - 60
    return if (afterWork) base.sortedBy { if (it == WorkSection.TO_REVIEW || it == WorkSection.FOLLOW_UPS) 0 else 1 } else base
}

/**
 * The Work Home's sections, as list items inside Today (PLAN_PROFESSIONAL.md §7). Each hides when
 * it has nothing; at most three rows each, with the rest a tap away.
 */
fun LazyListScope.workHomeItems(
    state: WorkHomeState,
    viewModel: WorkViewModel,
    onOpenWrapUp: (String) -> Unit,
    onFollowUp: (String) -> Unit,
    onOpenWork: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onEdit: (WorkItem) -> Unit,
    onNudge: (WorkItem) -> Unit
) {
    val terms = state.settings.terms
    orderedSections(state.settings).forEach { section ->
        when (section) {
            WorkSection.TO_REVIEW -> if (state.toReview.isNotEmpty()) {
                item(key = "w-review") { WorkSectionTitle("To review", "${state.toReview.size}") }
                items(state.toReview.take(3), key = { "wr-" + it.meetingId }) { row ->
                    MeetingCard(row, "${row.count} to confirm", "Wrap up") { onOpenWrapUp(row.meetingId) }
                }
            }
            WorkSection.FOLLOW_UPS -> if (state.followUps.isNotEmpty()) {
                item(key = "w-send") { WorkSectionTitle("Follow-ups to send", "${state.followUps.size}") }
                items(state.followUps.take(3), key = { "wf-" + it.meetingId }) { row ->
                    MeetingCard(row, dayLabel(row.at), "Send") { onFollowUp(row.meetingId) }
                }
            }
            WorkSection.MY_TASKS -> if (state.myTasks.isNotEmpty()) {
                item(key = "w-mine") { WorkSectionTitle("My tasks", if (state.myTasks.size > 3) "All ${state.myTasks.size}" else null, onTrailing = onOpenWork) }
                items(state.myTasks.take(3), key = { "wm-" + it.id }) { i ->
                    ItemRow(i, { viewModel.toggle(i) }, { onEdit(i) }, i.meetingId?.let { state.titles[it] })
                }
            }
            WorkSection.WAITING_ON -> if (state.waitingOn.isNotEmpty()) {
                item(key = "w-wait") { WorkSectionTitle("Waiting on", if (state.waitingOn.size > 3) "All ${state.waitingOn.size}" else null, onTrailing = onOpenWork) }
                items(state.waitingOn.take(3), key = { "ww-" + it.id }) { i ->
                    ItemRow(i, { viewModel.toggle(i) }, { onEdit(i) }, null, action = { TextButton(onClick = { onNudge(i) }) { Text("Nudge") } })
                }
            }
            WorkSection.OPEN_QUESTIONS -> if (state.questions.isNotEmpty()) {
                item(key = "w-q") { WorkSectionTitle("Open questions", if (state.questions.size > 3) "All ${state.questions.size}" else null, onTrailing = onOpenWork) }
                items(state.questions.take(3), key = { "wq-" + it.id }) { i ->
                    ItemRow(i, null, { onEdit(i) }, i.meetingId?.let { state.titles[it] }, onPlay = i.meetingId?.let { m -> { onOpenMeeting(m, i.sourceStartMs) } })
                }
            }
            WorkSection.PEOPLE -> if (state.people.isNotEmpty()) {
                item(key = "w-people") {
                    Column {
                        WorkSectionTitle(terms.people.let { if (terms.person == "Contact") "People" else it })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.people, key = { it.id }) { p ->
                                Column(Modifier.width(64.dp).clickable { onOpenPerson(p.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Avatar(p.initials, 48.dp)
                                    Text(p.name.substringBefore(' '), fontSize = 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetingCard(row: MeetingRow, line: String, action: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(line, "${(row.durationMs / 60000).coerceAtLeast(1)} min").joinToString(" · "), fontSize = 12.sp, color = InkSecondary)
            }
            Text(action, fontSize = 14.sp, color = Accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The edit and nudge sheets the Work Home opens, kept here so Today stays about the day. */
@Composable
fun WorkHomeSheets(viewModel: WorkViewModel, editing: WorkItem?, nudging: WorkItem?, onCloseEdit: () -> Unit, onCloseNudge: () -> Unit, onOpenMeeting: (String, Long?) -> Unit) {
    val open by viewModel.open.collectAsState()
    val everyone by viewModel.everyone.collectAsState()
    val self by viewModel.self.collectAsState()
    nudging?.let { NudgeSheet(it, viewModel, onCloseNudge) }
    editing?.let { current ->
        val live = open.firstOrNull { it.id == current.id } ?: current
        ItemEditSheet(
            item = live, owners = ownerChoices(self, emptyList(), everyone), onDismiss = onCloseEdit,
            onText = { viewModel.setText(live, it) }, onKind = { viewModel.setKind(live, it) }, onOwner = { viewModel.setOwner(live, it) },
            onDue = { a, t -> viewModel.setDue(live, a, t) }, onAnswer = { viewModel.setAnswer(live, it) },
            onDelete = { viewModel.delete(live); onCloseEdit() },
            onPlay = live.meetingId?.let { m -> { onCloseEdit(); onOpenMeeting(m, live.sourceStartMs) } }
        )
    }
}
