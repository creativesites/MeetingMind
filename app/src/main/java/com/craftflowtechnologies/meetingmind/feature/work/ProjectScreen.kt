package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.work.FindingKind
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.craftflowtechnologies.meetingmind.core.work.KanbanBoardData
import com.craftflowtechnologies.meetingmind.core.work.TimelineSection
import com.craftflowtechnologies.meetingmind.core.work.XRayGraph

enum class ProjectViewTab(val label: String) {
    OVERVIEW("Overview"),
    KANBAN("Kanban"),
    TIMELINE("Timeline"),
    XRAY("X-Ray")
}

/**
 * A project hub (docs/PLAN_PROFESSIONAL.md §6.3, W14 Views): one client, matter or case with its meetings and
 * notes, what's owed, what was decided and what's still open. Recording or writing from here files
 * straight into it. Supports Overview, Kanban, Timeline, and X-Ray views.
 */
@Composable
fun ProjectScreen(
    viewModel: WorkViewModel,
    projectId: String,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onRecordInto: (noteId: String, type: RecordingType, title: String) -> Unit,
    /** Sections a context page adds after the masthead and at the end. */
    extraTop: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    extraBottom: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null
) {
    val project by remember(projectId) { viewModel.project(projectId) }.collectAsState(initial = null)
    val notes by remember(projectId) { viewModel.notesIn(projectId) }.collectAsState(initial = emptyList())
    val tasks by remember(projectId) { viewModel.tasksIn(projectId) }.collectAsState(initial = emptyList())
    val decisions by remember(projectId) { viewModel.decisionsIn(projectId) }.collectAsState(initial = emptyList())
    val questions by remember(projectId) { viewModel.questionsIn(projectId) }.collectAsState(initial = emptyList())
    val orgs by viewModel.organisations.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var recordPicker by remember { mutableStateOf(false) }
    var writePicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var nudging by remember { mutableStateOf<WorkTask?>(null) }
    var adding by remember { mutableStateOf("") }
    var currentView by rememberSaveable { mutableStateOf(ProjectViewTab.OVERVIEW) }
    var kanbanData by remember { mutableStateOf<KanbanBoardData?>(null) }
    var timelineSections by remember { mutableStateOf<List<TimelineSection>>(emptyList()) }
    var xrayGraph by remember { mutableStateOf<XRayGraph?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(projectId, currentView, refreshKey, tasks.size, decisions.size) {
        when (currentView) {
            ProjectViewTab.KANBAN -> kanbanData = viewModel.loadProjectKanban(projectId)
            ProjectViewTab.TIMELINE -> timelineSections = viewModel.loadProjectTimeline(projectId)
            ProjectViewTab.XRAY -> xrayGraph = viewModel.loadProjectXRay(projectId)
            ProjectViewTab.OVERVIEW -> {}
        }
    }

    val nb = project ?: run { Scaffold(containerColor = SurfaceBase) { Column(Modifier.padding(it)) {} }; return }
    val org = orgs.firstOrNull { it.id == nb.orgId }
    val mine = tasks.filter { !it.done && !it.waitingOn }
    val theirs = tasks.filter { !it.done && it.waitingOn }

    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = InkSecondary) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        listOf("Active", "On hold", "Done").filter { it != nb.status }.forEach { s ->
                            DropdownMenuItem(text = { Text("Mark $s") }, onClick = { menu = false; viewModel.updateProject(nb, status = s) })
                        }
                        DropdownMenuItem(
                            text = { Text(if (nb.confidential) "Not confidential" else "Confidential — keep on this phone") },
                            onClick = { menu = false; viewModel.updateProject(nb, confidential = !nb.confidential) }
                        )
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("${settings.terms.project.uppercase()} · ${nb.status.uppercase()}" + if (nb.confidential) " · 🔒" else "", fontSize = 11.sp, letterSpacing = 1.sp, color = InkMuted, fontWeight = FontWeight.SemiBold)
                    Text(nb.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 4.dp))
                    org?.let { Text(it.name, fontSize = 15.sp, color = InkSecondary) }
                }
                Row(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("● Record", filled = true) { recordPicker = true }
                    Pill("Write a note") { writePicker = true }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Fig("${notes.size}", "notes"); Fig("${mine.size}", "you owe"); Fig("${theirs.size}", "owed to you"); Fig("${decisions.size}", "decided")
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProjectViewTab.entries) { v ->
                        Chip(v.label, currentView == v, Ink) { currentView = v }
                    }
                }
            }

            when (currentView) {
                ProjectViewTab.OVERVIEW -> {
                    extraTop?.invoke(this)
                    item { WorkSectionTitle("You owe", if (mine.isNotEmpty()) "${mine.size}" else null) }
                    item {
                        OutlinedTextField(
                            adding, { adding = it }, placeholder = { Text("Add a task for ${nb.name}") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Add, null, tint = InkMuted) }, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                viewModel.addTask(adding, noteId = notes.firstOrNull()?.id); adding = ""
                            })
                        )
                    }
                    items(mine, key = { "m-" + it.id }) { t -> TaskLine(t, null, { viewModel.toggle(t) }, { editing = t }, t.meetingId?.let { m -> { onOpenMeeting(m, t.startMs) } }) }
                    if (theirs.isNotEmpty()) {
                        item { WorkSectionTitle("Waiting on", "${theirs.size}") }
                        items(theirs, key = { "w-" + it.id }) { t ->
                            TaskLine(t, null, { viewModel.toggle(t) }, { editing = t }, action = { androidx.compose.material3.TextButton(onClick = { nudging = t }) { Text("Nudge") } })
                        }
                    }
                    if (decisions.isNotEmpty()) {
                        item { WorkSectionTitle("Decided", "${decisions.size}") }
                        items(decisions, key = { "d-" + it.id }) { d -> FindingLine(FindingKind.DECISION, d.text, null, onClick = { onOpenMeeting(d.meetingId, null) }) }
                    }
                    if (questions.isNotEmpty()) {
                        item { WorkSectionTitle("Still open", "${questions.size}") }
                        items(questions, key = { "q-" + it.id }) { q -> FindingLine(FindingKind.QUESTION, q.text, null, onClick = { onOpenMeeting(q.meetingId, null) }) }
                    }
                    item { WorkSectionTitle("Meetings and notes", if (notes.isEmpty()) null else "${notes.size}") }
                    if (notes.isEmpty()) item {
                        EmptyLine("Nothing filed here yet. Record or write from this page and it lands here — or file a recording here from its Wrap-up.")
                    }
                    items(notes, key = { "n-" + it.id }) { n ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenNote(n.id) }.padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            WorkTypeTile(n.workflow, n.title)
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(n.title.ifBlank { n.workflow.displayName }, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(n.workflow.displayName + " · " + dayLabel(n.eventDate ?: n.createdAt), fontSize = 12.sp, color = InkMuted)
                            }
                        }
                    }
                    extraBottom?.invoke(this)
                }
                ProjectViewTab.KANBAN -> {
                    item {
                        kanbanData?.let { data ->
                            KanbanBoard(
                                boardData = data,
                                onMoveCard = { card, targetCol ->
                                    viewModel.moveKanbanCard(card, targetCol)
                                    refreshKey++
                                }
                            )
                        } ?: Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Loading Kanban board...", fontSize = 13.sp, color = InkMuted)
                        }
                    }
                }
                ProjectViewTab.TIMELINE -> {
                    item {
                        TimelineView(
                            sections = timelineSections,
                            onItemClick = { item ->
                                item.noteId?.let { onOpenNote(it) }
                            }
                        )
                    }
                }
                ProjectViewTab.XRAY -> {
                    item {
                        xrayGraph?.let { graph ->
                            XRayView(graph = graph)
                        } ?: Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Assembling X-Ray entity graph...", fontSize = 13.sp, color = InkMuted)
                        }
                    }
                }
            }
        }
    }

    if (renaming) TextDialog("Rename", nb.name, null, onDismiss = { renaming = false }) { viewModel.updateProject(nb, name = it); renaming = false }
    if (recordPicker) RecordTypeSheet(startsFor(settings.profile).filter { com.craftflowtechnologies.meetingmind.core.model.Workflows.isRecordable(it.type) },
        onPick = { type -> recordPicker = false; viewModel.newNote(type, nb.id) { id -> onRecordInto(id, type, "${type.displayName} · ${nb.name}") } }, onDismiss = { recordPicker = false })
    if (writePicker) RecordTypeSheet(startsFor(settings.profile), onPick = { type -> writePicker = false; viewModel.newNote(type, nb.id, onOpenNote) }, onDismiss = { writePicker = false })
    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { t -> WorkTaskSheet(t, viewModel, onDismiss = { editing = null }, onOpenSource = t.meetingId?.let { m -> { editing = null; onOpenMeeting(m, t.startMs) } }) }
}

@Composable
private fun Fig(value: String, label: String) {
    Column {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 11.sp, color = InkMuted)
    }
}

@Composable
internal fun TextDialog(title: String, initial: String, note: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                note?.let { Text(it, fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
