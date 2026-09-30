package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.FindingKind
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

enum class WorkTab(val label: String) { MINE("You owe"), WAITING("They owe"), DECISIONS("Decisions"), QUESTIONS("Open questions"), PEOPLE("People") }

/**
 * Everything in Work, one list at a time (docs/PLAN_PROFESSIONAL.md §7.4): what you owe, what
 * you're owed, the decision log, open questions and the people you work with.
 */
@Composable
fun WorkAllScreen(
    viewModel: WorkViewModel,
    initial: WorkTab,
    onNavigateBack: () -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenPerson: (String) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(initial) }
    val youOwe by viewModel.youOwe.collectAsState()
    val theyOwe by viewModel.theyOwe.collectAsState()
    val decisions by viewModel.decisionLog.collectAsState()
    val questions by viewModel.openQuestions.collectAsState()
    val everyone by viewModel.everyone.collectAsState()
    val orgs by viewModel.organisations.collectAsState()
    val duplicates by viewModel.duplicates.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var nudging by remember { mutableStateOf<WorkTask?>(null) }
    var newPerson by remember { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }

    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Text("Work", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WorkTab.entries) { t -> Chip(if (t == WorkTab.PEOPLE && settings.terms.person != "Contact") settings.terms.people else t.label, tab == t, Ink) { tab = t } }
                }
            }
            when (tab) {
                WorkTab.MINE, WorkTab.WAITING -> {
                    val list = (if (tab == WorkTab.WAITING) theyOwe else youOwe).filter { showDone || !it.done }
                    val now = System.currentTimeMillis()
                    val groups = list.groupBy { t ->
                        when {
                            t.done -> "Done"
                            t.dueAt == null -> "No date"
                            t.isOverdue(now) -> "Overdue"
                            t.dueAt < DueDates.startOfDay(now) + 86_400_000L -> "Today"
                            t.dueAt < DueDates.startOfDay(now) + 7 * 86_400_000L -> "This week"
                            else -> "Later"
                        }
                    }
                    if (list.isEmpty()) item {
                        EmptyLine(if (tab == WorkTab.MINE) "Nothing on your plate. Tasks you agree to in meetings land here with the day they're due."
                        else "Nobody owes you anything yet. When someone agrees to do something in a meeting, it shows here — and you can nudge them.")
                    }
                    listOf("Overdue", "Today", "This week", "Later", "No date", "Done").forEach { g ->
                        val inGroup = groups[g].orEmpty()
                        if (inGroup.isNotEmpty()) {
                            item(key = "g-$g") { WorkSectionTitle(g, "${inGroup.size}", top = 12.dp) }
                            items(inGroup, key = { it.id }) { t ->
                                TaskLine(t, t.meetingId?.let { titles[it] }, onToggle = { viewModel.toggle(t) }, onClick = { editing = t },
                                    onPlay = t.meetingId?.let { m -> { onOpenMeeting(m, t.startMs) } },
                                    action = if (tab == WorkTab.WAITING && !t.done) ({ TextButton(onClick = { nudging = t }) { Text("Nudge") } }) else null)
                            }
                        }
                    }
                    item { TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(if (showDone) "Hide done" else "Show done") } }
                }
                WorkTab.DECISIONS -> {
                    if (decisions.isEmpty()) item { EmptyLine("Decisions from your meetings build up here, newest first, each linked to the moment it was made.") }
                    decisions.groupBy { dayLabel(it.createdAt) }.forEach { (day, list) ->
                        item(key = "d-$day") { WorkSectionTitle(day, top = 12.dp) }
                        items(list, key = { it.id }) { d -> FindingLine(FindingKind.DECISION, d.text, if (d.detail == "SUPERSEDED") "${d.meetingTitle} · Replaced later" else d.meetingTitle, onClick = { onOpenMeeting(d.meetingId, null) }) }
                    }
                }
                WorkTab.QUESTIONS -> {
                    if (questions.isEmpty()) item { EmptyLine("No open questions. Questions left unanswered in your meetings gather here until someone answers them.") }
                    items(questions, key = { it.id }) { q ->
                        FindingLine(FindingKind.QUESTION, q.text, "${q.meetingTitle} · ${dayLabel(q.createdAt)}", onClick = { onOpenMeeting(q.meetingId, null) },
                            trailing = { TextButton(onClick = { viewModel.resolveQuestion(q, null) }) { Text("Answered") } })
                    }
                }
                WorkTab.PEOPLE -> {
                    duplicates.firstOrNull()?.let { (a, b) ->
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Same person?", fontWeight = FontWeight.SemiBold, color = Ink)
                                    Text("“${a.name}” and “${b.name}”", color = InkSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val (from, into) = if (a.name.length >= b.name.length) b to a else a to b
                                        Chip("Yes, merge", true) { viewModel.merge(from, into) }
                                        Chip("Different people") { viewModel.markDifferent(a, b) }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            TextButton(onClick = { newPerson = true }) { Icon(Icons.Filled.Add, null); Text(" Add someone") }
                        }
                    }
                    if (everyone.isEmpty()) item { EmptyLine("People appear here on their own when you name a speaker or record from a calendar invite.") }
                    items(everyone, key = { it.id }) { p -> PersonRow(p, orgs.firstOrNull { it.id == p.orgId }?.name) { onOpenPerson(p.id) } }
                    if (orgs.isNotEmpty()) {
                        item { WorkSectionTitle(settings.terms.organisations) }
                        items(orgs, key = { "o-" + it.id }) { o -> PersonRow(o, "${everyone.count { it.orgId == o.id }} people") { onOpenPerson(o.id) } }
                    }
                }
            }
        }
    }
    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { t -> WorkTaskSheet(t, viewModel, onDismiss = { editing = null }, onOpenSource = { t.meetingId?.let { m -> editing = null; onOpenMeeting(m, t.startMs) } ?: t.noteId?.let { n -> editing = null; onOpenNote(n) } }) }
    if (newPerson) NewPersonSheet(settings.terms.organisation, onDismiss = { newPerson = false }) { name, role, email, phone, org ->
        newPerson = false; viewModel.addPerson(name, role, email, phone, org, onOpenPerson)
    }
}
