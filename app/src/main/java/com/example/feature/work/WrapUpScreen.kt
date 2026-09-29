package com.example.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Speaker
import com.example.core.work.ItemKind
import com.example.core.work.ItemStatus
import com.example.core.work.WorkItem
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.SurfaceSunk
import kotlinx.coroutines.launch

/** Findings below this confidence wait behind "N more found" (PLAN_PROFESSIONAL.md §1, principle 5). */
private const val SHOW_CONFIDENCE = 0.5f

/**
 * The Wrap-up (docs/PLAN_PROFESSIONAL.md §4.3). Everything found is already checked; swipe away
 * what's wrong, tap to fix, then send the follow-up. Nothing is lost if the person skips it: items
 * stay, marked unreviewed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WrapUpScreen(
    viewModel: WrapUpViewModel,
    onNavigateBack: () -> Unit,
    onDone: (noteId: String?) -> Unit,
    onPlay: (Long?) -> Unit,
    onOpenPerson: (String) -> Unit,
    startComposing: Boolean = false
) {
    val meeting by viewModel.meeting.collectAsState()
    val items by viewModel.items.collectAsState()
    val speakers by viewModel.speakers.collectAsState()
    val note by viewModel.note.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val with by viewModel.with.collectAsState()
    val everyone by viewModel.everyone.collectAsState()
    val self by viewModel.self.collectAsState()
    var editing by remember { mutableStateOf<WorkItem?>(null) }
    var naming by remember { mutableStateOf(false) }
    var projectPicker by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(startComposing) }
    var contactFor by remember { mutableStateOf<com.example.core.work.Person?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val terms = settings.terms
    val selfId = self?.id

    fun mine(i: WorkItem) = i.ownerIsSelf || (selfId != null && i.ownerPersonId == selfId) || (i.ownerSpeakerId == null && i.ownerPersonId == null && i.ownerName == null)
    val live = items.filter { it.status != ItemStatus.DROPPED }
    val shown = if (showAll) live else live.filter { (it.confidence ?: 1f) >= SHOW_CONFIDENCE || it.reviewed }
    val hidden = live.size - shown.size
    val decisions = shown.filter { it.kind == ItemKind.DECISION }
    val myTasks = shown.filter { it.kind == ItemKind.TASK && mine(it) }
    val theirs = shown.filter { it.kind == ItemKind.TASK && !mine(it) }
    val questions = shown.filter { it.kind == ItemKind.QUESTION }
    val unnamed = viewModel.unnamed(speakers)
    val project = notebooks.firstOrNull { it.id == note?.notebookId }

    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { composing = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text("Draft follow-up", modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Button(
                        onClick = { viewModel.done(); onDone(meeting?.noteId) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink)
                    ) { Text("Done", modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Text("Wrap-up", fontSize = 13.sp, color = InkMuted, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(meeting?.title.orEmpty(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    meeting?.let { m ->
                        Text(dayLabel(m.createdAt) + " · " + (m.durationMs / 60000).coerceAtLeast(1) + " min", fontSize = 13.sp, color = InkSecondary)
                    }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        MetaRow(terms.project) {
                            Chip(project?.name ?: "Choose", project != null) { projectPicker = true }
                        }
                        MetaRow("With") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                with.forEach { p -> Chip(p.name) { onOpenPerson(p.id) } }
                                if (with.isEmpty()) Text("Name the speakers to add people", fontSize = 13.sp, color = InkMuted)
                            }
                        }
                        val keyMoments = note?.metadata?.let { m -> com.example.core.work.Marks.keyMoments(org.json.JSONObject(m).toString()) }.orEmpty()
                        if (keyMoments.isNotEmpty()) MetaRow("Key moments") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                keyMoments.forEach { at -> Chip("⭐ " + formatTime(at)) { onPlay(at) } }
                            }
                        }
                        if (speakers.size > 1 || unnamed.isNotEmpty()) MetaRow("Speakers") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (unnamed.isEmpty()) "All ${speakers.size} named" else "${speakers.size - unnamed.size} named · ${unnamed.size} not yet",
                                    fontSize = 14.sp, color = if (unnamed.isEmpty()) InkSecondary else Ink, modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { naming = true }) { Text(if (unnamed.isEmpty()) "Edit" else "Name them") }
                            }
                        }
                    }
                }
                if (live.isEmpty()) {
                    EmptyLine("Nothing to confirm from this recording. Add anything you want to remember below, or tap Done.")
                } else {
                    Text("Everything is checked. Swipe away what's wrong, tap to fix.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            fun section(title: String, list: List<WorkItem>, key: String) {
                if (list.isEmpty()) return
                item(key = "h-$key") { WorkSectionTitle(title, "${list.size}") }
                items(list, key = { it.id }) { i ->
                    DismissibleItem(i, onDismiss = {
                        viewModel.dismiss(i)
                        scope.launch { if (snackbar.showSnackbar("Removed", "Undo") == SnackbarResult.ActionPerformed) viewModel.undoDismiss() }
                    }) {
                        ItemRow(
                            i,
                            onToggle = if (i.kind == ItemKind.TASK) ({ viewModel.toggle(i) }) else null,
                            onClick = { editing = i },
                            onPlay = { onPlay(i.sourceStartMs) }
                        )
                    }
                }
            }
            section("Decisions", decisions, "d")
            section("Your tasks", myTasks, "m")
            section("Waiting on", theirs, "w")
            section("Open questions", questions, "q")
            if (hidden > 0) item {
                TextButton(onClick = { showAll = true }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("+ $hidden more found, less certain") }
            }
            item {
                OutlinedTextField(
                    value = adding, onValueChange = { adding = it }, placeholder = { Text("Add a task, decision or question") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    leadingIcon = { Icon(Icons.Filled.Add, null, tint = InkMuted) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val kind = if (adding.trim().endsWith("?")) ItemKind.QUESTION else ItemKind.TASK
                        viewModel.add(adding, kind); adding = ""
                    })
                )
            }
        }
    }

    editing?.let { current ->
        val i = items.firstOrNull { it.id == current.id } ?: current
        ItemEditSheet(
            item = i, owners = ownerChoices(self, speakers, everyone), onDismiss = { editing = null },
            onText = { viewModel.setText(i, it) }, onKind = { viewModel.setKind(i, it) }, onOwner = { viewModel.setOwner(i, it) },
            onDue = { a, t -> viewModel.setDue(i, a, t) }, onAnswer = { viewModel.setAnswer(i, it) },
            onDelete = { editing = null; viewModel.dismiss(i) }, onPlay = { editing = null; onPlay(i.sourceStartMs) }
        )
    }
    if (naming) NameSpeakersSheet(speakers, with.map { it.name } + everyone.filter { !it.isSelf }.map { it.name }, onDismiss = { naming = false },
        onRename = { s, n -> viewModel.renameSpeaker(s, n) }, onMe = { viewModel.speakerIsMe(it) }, onPlay = onPlay)
    if (projectPicker) {
        ModalBottomSheet(onDismissRequest = { projectPicker = false }, containerColor = Color.White) {
            var name by remember { mutableStateOf("") }
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Text("File under a ${terms.project.lowercase()}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    notebooks.forEach { nb -> Chip(nb.name + if (nb.confidential) " 🔒" else "", nb.id == note?.notebookId) { viewModel.setProject(nb.id); projectPicker = false } }
                    if (note?.notebookId != null) Chip("None") { viewModel.setProject(null); projectPicker = false }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, placeholder = { Text("New ${terms.project.lowercase()}") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    trailingIcon = { if (name.isNotBlank()) TextButton(onClick = { viewModel.newProject(name); projectPicker = false }) { Text("Create") } }
                )
                if (settings.keepOnDevice) Text("New ${terms.projects.lowercase()} are confidential: their recordings stay on this phone.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    if (composing) FollowUpSheet(
        title = "Follow-up", recipients = with, settings = settings,
        compose = { c, t -> viewModel.followUp(c, t) },
        onDismiss = { composing = false },
        onSent = { c -> viewModel.markSent(c); viewModel.done(); composing = false; onDone(meeting?.noteId) },
        onAddContact = { contactFor = it }
    )
    contactFor?.let { p -> ContactDialog(p, onDismiss = { contactFor = null }) { e, ph -> viewModel.addContact(p, e, ph); contactFor = null } }
}

@Composable
private fun MetaRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(end = 12.dp).fillMaxWidth(0.24f))
        Box(Modifier.weight(1f)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleItem(item: WorkItem, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) { onDismiss(); true } else false
    })
    SwipeToDismissBox(
        state = state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(Color(0xFFFEE2E2)).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Remove", color = Overdue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    ) { Box(Modifier.background(Color.White)) { content() } }
}

/**
 * Names speakers. A name typed here becomes the person everywhere: their tasks, the summary and
 * the note update as it's saved (PLAN_PROFESSIONAL.md §5.5).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NameSpeakersSheet(
    speakers: List<Speaker>,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onRename: (Speaker, String) -> Unit,
    onMe: (Speaker) -> Unit,
    onPlay: (Long?) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Text("Who's speaking?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Names update everywhere: tasks, the summary and the note.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            }
            items(speakers, key = { it.id }) { s ->
                var text by remember(s.id, s.customName) { mutableStateOf(if (com.example.core.work.SpeakerNames.isGenericLabel(s.customName)) "" else s.customName) }
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(s.originalLabel, fontSize = 12.sp, color = InkMuted)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, placeholder = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onRename(s, text) }),
                        trailingIcon = { if (text.isNotBlank() && text != s.customName) TextButton(onClick = { onRename(s, text) }) { Text("Save") } }
                    )
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("That's me", color = Accent) { onMe(s) }
                        suggestions.distinct().filter { !it.equals(s.customName, true) }.take(5).forEach { n -> Chip(n) { text = n; onRename(s, n) } }
                    }
                }
            }
        }
    }
}
