package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.work.Finding
import com.craftflowtechnologies.meetingmind.core.work.FindingKind
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.DangerWash
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
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
    val items by viewModel.findings.collectAsState()
    val speakers by viewModel.speakers.collectAsState()
    val note by viewModel.note.collectAsState()
    val notebooks by viewModel.projects.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val with by viewModel.with.collectAsState()
    val self by viewModel.self.collectAsState()
    var editing by remember { mutableStateOf<Finding?>(null) }
    var naming by remember { mutableStateOf(false) }
    var projectPicker by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(startComposing) }
    var contactFor by remember { mutableStateOf<com.craftflowtechnologies.meetingmind.core.work.WorkPerson?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val terms = settings.terms
    val selfId = self?.id

    val live = items
    val shown = if (showAll) live else live.filter { (it.confidence ?: 1f) >= SHOW_CONFIDENCE }
    val hidden = live.size - shown.size
    val decisions = shown.filter { it.kind == FindingKind.DECISION }
    val tasksAll = shown.filter { it.kind == FindingKind.ACTION || it.kind == FindingKind.FOLLOW_UP }
    val myTasks = tasksAll.filter { it.isMine }
    val theirs = tasksAll.filter { !it.isMine }
    val questions = shown.filter { it.kind == FindingKind.QUESTION }
    val unnamed = viewModel.unnamed(speakers)
    val project = notebooks.firstOrNull { it.id == note?.notebookId }

    Scaffold(
        containerColor = SurfaceBase,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = SurfaceBase, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { composing = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text("Draft follow-up", modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Button(
                        onClick = { viewModel.done(); onDone(meeting?.noteId) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = OnInk)
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
                        val keyMoments = note?.metadata?.let { m -> com.craftflowtechnologies.meetingmind.core.work.Marks.keyMoments(org.json.JSONObject(m as Map<*, *>).toString()) }.orEmpty()
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
            fun section(title: String, list: List<Finding>, key: String) {
                if (list.isEmpty()) return
                item(key = "h-$key") { WorkSectionTitle(title, "${list.size}") }
                items(list, key = { it.id }) { i ->
                    DismissibleItem(i, onDismiss = {
                        viewModel.dismiss(i)
                        scope.launch { if (snackbar.showSnackbar("Removed", "Undo") == SnackbarResult.ActionPerformed) viewModel.undoDismiss() }
                    }) {
                        FindingRow(i, onClick = { editing = i }, onPlay = { onPlay(i.startMs) })
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
                        val kind = if (adding.trim().endsWith("?")) FindingKind.QUESTION else FindingKind.ACTION
                        viewModel.add(adding, kind); adding = ""
                    })
                )
            }
        }
    }

    editing?.let { current ->
        val i = items.firstOrNull { it.id == current.id } ?: current
        FindingEditSheet(
            f = i, owners = ownerChoices(speakers, self?.name), onDismiss = { editing = null },
            onText = { viewModel.setText(i, it) }, onKind = { viewModel.setKind(i, it); editing = null }, onOwner = { viewModel.setOwner(i, it) },
            onDue = { viewModel.setDue(i, it) }, onAnswer = { viewModel.setAnswer(i, it) },
            onDelete = { editing = null; viewModel.dismiss(i) }, onPlay = { editing = null; onPlay(i.startMs) }
        )
    }
    if (naming) NameSpeakersSheet(speakers, with.map { it.name }, onDismiss = { naming = false },
        onRename = { s, n -> viewModel.renameSpeaker(s, n) }, onMe = { viewModel.speakerIsMe(it) }, onPlay = onPlay)
    if (projectPicker) {
        ModalBottomSheet(onDismissRequest = { projectPicker = false }, containerColor = SurfaceBase) {
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
        onSent = { c -> viewModel.markSent(c); composing = false; onDone(meeting?.noteId) },
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
private fun DismissibleItem(item: Finding, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) { onDismiss(); true } else false
    })
    SwipeToDismissBox(
        state = state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(DangerWash).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Remove", color = Danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    ) { Box(Modifier.background(SurfaceBase)) { content() } }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Text("Who's speaking?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Names update everywhere: tasks, the summary and the note.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            }
            items(speakers, key = { it.id }) { s ->
                var text by remember(s.id, s.customName) { mutableStateOf(if (com.craftflowtechnologies.meetingmind.core.work.SpeakerNames.isGenericLabel(s.customName)) "" else s.customName) }
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

/** One finding in the Wrap-up: what it is, the words, who and when, and the moment it was said. */
@Composable
private fun FindingRow(f: Finding, onClick: () -> Unit, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.text, fontSize = 15.sp, color = Ink, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val bits = listOfNotNull(
                if (f.kind == FindingKind.ACTION || f.kind == FindingKind.FOLLOW_UP) (if (f.ownerIsSelf) "You" else f.ownerName ?: "You") else null,
                dueLabel(f.dueAt, true)?.first ?: f.dueText,
                if ((f.confidence ?: 1f) >= 0.95f && f.id.startsWith("mark_")) "Marked" else null
            )
            if (bits.isNotEmpty()) Text(bits.joinToString("  ·  "), fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
        }
        f.startMs?.let { PlayChip(it, onPlay) }
    }
}
