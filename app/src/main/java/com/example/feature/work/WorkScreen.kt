package com.example.feature.work

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.work.ItemKind
import com.example.core.work.ItemStatus
import com.example.core.work.Person
import com.example.core.work.WorkItem
import com.example.core.work.WorkView
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.SurfaceSunk
import kotlinx.coroutines.launch

enum class WorkSegment(val label: String) { TASKS("Tasks"), PEOPLE("People"), DECISIONS("Decisions") }

/**
 * The Work tab (docs/PLAN_PROFESSIONAL.md §7.4): what you owe, what you're owed, who you work
 * with, and what was decided. Core, not Professional-only; anyone can turn it on.
 */
@Composable
fun WorkScreen(
    viewModel: WorkViewModel,
    onOpenPerson: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val open by viewModel.open.collectAsState()
    val decisions by viewModel.decisions.collectAsState()
    val everyone by viewModel.everyone.collectAsState()
    val orgs by viewModel.organisations.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val self by viewModel.self.collectAsState()
    val duplicates by viewModel.duplicates.collectAsState()
    var segment by rememberSaveable { mutableStateOf(WorkSegment.TASKS) }
    var view by rememberSaveable { mutableStateOf(WorkView.MY_TASKS) }
    var editing by remember { mutableStateOf<WorkItem?>(null) }
    var nudging by remember { mutableStateOf<WorkItem?>(null) }
    var adding by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val terms = settings.terms

    fun context(item: WorkItem) = item.meetingId?.let { titles[it] }
    fun open(item: WorkItem) { editing = item }

    Scaffold(containerColor = Color.White, bottomBar = bottomBar, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Work", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Tune, "Work settings", tint = InkSecondary) }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkSegment.entries.forEach { s ->
                        Chip(if (s == WorkSegment.PEOPLE) terms.people.let { if (terms.person == "Contact") "People" else it } else s.label, segment == s, Ink) { segment = s }
                    }
                }
            }
            when (segment) {
                WorkSegment.TASKS -> {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(WorkView.MY_TASKS, WorkView.WAITING_ON, WorkView.OPEN_QUESTIONS)) { v ->
                                val n = viewModel.view(open, v).size
                                Chip(v.label + if (n > 0) "  $n" else "", view == v) { view = v }
                            }
                        }
                    }
                    if (view == WorkView.MY_TASKS) item {
                        OutlinedTextField(
                            value = adding, onValueChange = { adding = it }, placeholder = { Text("Add a task — “Send the proposal Friday”") },
                            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            leadingIcon = { Icon(Icons.Filled.Add, null, tint = InkMuted) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                // "by Friday" in the words becomes the due day, the same reader extraction uses.
                                viewModel.addTask(adding, due = adding.takeIf { com.example.core.work.DueDates.parse(it, System.currentTimeMillis()) != null }, ownerPersonId = self?.id)
                                adding = ""
                            })
                        )
                    }
                    val list = viewModel.view(open, view)
                    val now = System.currentTimeMillis()
                    val groups = list.groupBy { item ->
                        val due = item.dueAt
                        when {
                            due == null -> "Someday"
                            item.isOverdue(now) -> "Overdue"
                            due < com.example.core.work.DueDates.startOfDay(now) + 24L * 60 * 60 * 1000 -> "Today"
                            due < com.example.core.work.DueDates.startOfDay(now) + 7 * 24L * 60 * 60 * 1000 -> "This week"
                            else -> "Later"
                        }
                    }
                    if (list.isEmpty()) item {
                        EmptyLine(
                            when (view) {
                                WorkView.MY_TASKS -> "Nothing on your plate. Tasks from your meetings land here, with the day they're due."
                                WorkView.WAITING_ON -> "Nobody owes you anything yet. When someone agrees to do something in a meeting, it shows here."
                                WorkView.OPEN_QUESTIONS -> "No open questions. Questions left unanswered in your meetings gather here."
                                WorkView.DECISIONS -> ""
                            }
                        )
                    }
                    listOf("Overdue", "Today", "This week", "Later", "Someday").forEach { g ->
                        val inGroup = groups[g].orEmpty()
                        if (inGroup.isNotEmpty()) {
                            item(key = "g-$g") { WorkSectionTitle(g, "${inGroup.size}", top = 10.dp) }
                            items(inGroup, key = { it.id }) { item ->
                                ItemRow(
                                    item,
                                    onToggle = if (item.kind == ItemKind.TASK) ({ viewModel.toggle(item) }) else null,
                                    onClick = { open(item) },
                                    context = context(item),
                                    onPlay = item.meetingId?.let { m -> { onOpenMeeting(m, item.sourceStartMs) } },
                                    action = if (view == WorkView.WAITING_ON) ({ TextButton(onClick = { nudging = item }) { Text("Nudge") } }) else null
                                )
                            }
                        }
                    }
                }
                WorkSegment.PEOPLE -> {
                    if (duplicates.isNotEmpty()) item {
                        val (a, b) = duplicates.first()
                        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Same person?", fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("“${a.name}” and “${b.name}”", color = InkSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Keep the fuller name.
                                    val (from, into) = if (a.name.length >= b.name.length) b to a else a to b
                                    Chip("Yes, merge", true) { viewModel.merge(from, into) }
                                    Chip("Different people") { viewModel.markDifferent(a, b) }
                                }
                            }
                        }
                    }
                    val people = everyone.filter { !it.isSelf }
                    if (people.isEmpty()) item {
                        EmptyLine("People you meet appear here on their own: name a speaker in a recording, or record from a calendar event with guests.")
                    }
                    items(people, key = { it.id }) { p -> PersonRow(p, orgs.firstOrNull { it.id == p.orgId }?.name) { onOpenPerson(p.id) } }
                    if (orgs.isNotEmpty()) {
                        item { WorkSectionTitle(terms.organisations) }
                        items(orgs, key = { "o-" + it.id }) { o -> PersonRow(o, "${everyone.count { it.orgId == o.id }} people") { onOpenPerson(o.id) } }
                    }
                }
                WorkSegment.DECISIONS -> {
                    if (decisions.isEmpty()) item { EmptyLine("Decisions from your meetings build up here, newest first, each with the moment it was made.") }
                    val byDay = decisions.groupBy { dayLabel(it.createdAt) }
                    byDay.forEach { (day, list) ->
                        item(key = "d-$day") { WorkSectionTitle(day, top = 10.dp) }
                        items(list, key = { it.id }) { d ->
                            ItemRow(d, onToggle = null, onClick = { open(d) }, context = context(d),
                                onPlay = d.meetingId?.let { m -> { onOpenMeeting(m, d.sourceStartMs) } })
                        }
                    }
                }
            }
        }
    }

    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { current ->
        val live = (open + decisions).firstOrNull { it.id == current.id } ?: current
        ItemEditSheet(
            item = live,
            owners = ownerChoices(self, emptyList(), everyone),
            onDismiss = { editing = null },
            onText = { viewModel.setText(live, it) },
            onKind = { viewModel.setKind(live, it) },
            onOwner = { viewModel.setOwner(live, it) },
            onDue = { at, text -> viewModel.setDue(live, at, text) },
            onAnswer = { viewModel.setAnswer(live, it) },
            onDelete = {
                editing = null
                viewModel.delete(live)
                scope.launch {
                    if (snackbar.showSnackbar("Removed", actionLabel = "Undo") == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
            },
            onPlay = live.meetingId?.let { m -> { editing = null; onOpenMeeting(m, live.sourceStartMs) } }
        )
    }
}

@Composable
internal fun PersonRow(person: Person, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(person.initials, tint = if (person.kind == com.example.core.work.PersonKind.ORG) DecisionColor else Accent)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(person.name, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(person.role, subtitle, person.lastSeenAt?.let { "Last ${dayLabel(it).lowercase()}" }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (person.confidential) Text("🔒", fontSize = 12.sp)
    }
}
