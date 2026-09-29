package com.example.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.work.ItemKind
import com.example.core.work.ItemStatus
import com.example.core.work.Person
import com.example.core.work.PersonKind
import com.example.core.work.WorkItem
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary

/**
 * A person's or organisation's page (docs/PLAN_PROFESSIONAL.md §6.3): what you owe each other,
 * what was decided, what's still open, and every conversation. Sections hide when empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    viewModel: WorkViewModel,
    personId: String,
    onNavigateBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit
) {
    val person by remember(personId) { viewModel.person(personId) }.collectAsState(initial = null)
    val items by remember(personId) { viewModel.itemsFor(personId) }.collectAsState(initial = emptyList())
    val notes by remember(personId) { viewModel.notesFor(personId) }.collectAsState(initial = emptyList())
    val members by remember(personId) { viewModel.members(personId) }.collectAsState(initial = emptyList())
    val everyone by viewModel.everyone.collectAsState()
    val orgs by viewModel.organisations.collectAsState()
    val self by viewModel.self.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf(false) }
    var orgEdit by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkItem?>(null) }
    var nudging by remember { mutableStateOf<WorkItem?>(null) }
    val p = person ?: run {
        Scaffold(containerColor = Color.White) { Column(Modifier.padding(it)) {} }
        return
    }
    val org = orgs.firstOrNull { it.id == p.orgId }
    val open = items.filter { it.status == ItemStatus.OPEN }
    val theyOwe = open.filter { it.kind == ItemKind.TASK }
    val decisions = items.filter { it.kind == ItemKind.DECISION }.take(8)
    val questions = open.filter { it.kind == ItemKind.QUESTION }
    val selfId = self?.id

    Scaffold(containerColor = Color.White) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Row(Modifier.weight(1f)) {}
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = InkSecondary) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        if (p.kind == PersonKind.PERSON) {
                            DropdownMenuItem(text = { Text("Email or phone") }, onClick = { menu = false; contact = true })
                            DropdownMenuItem(text = { Text(settings.terms.organisation) }, onClick = { menu = false; orgEdit = true })
                            DropdownMenuItem(text = { Text("Same person as…") }, onClick = { menu = false; merging = true })
                        }
                        DropdownMenuItem(
                            text = { Text(if (p.confidential) "Not confidential" else "Confidential — keep on this phone") },
                            onClick = { menu = false; viewModel.updatePerson(p.copy(confidential = !p.confidential)) }
                        )
                        DropdownMenuItem(text = { Text("Remove from People") }, onClick = { menu = false; viewModel.deletePerson(p); onNavigateBack() })
                    }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.initials, 56.dp, if (p.kind == PersonKind.ORG) DecisionColor else com.example.ui.theme.Accent)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(p.name + if (p.confidential) "  🔒" else "", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink,
                            modifier = Modifier.clickable { renaming = true })
                        val line = listOfNotNull(p.role, org?.name).joinToString(" · ")
                        if (line.isNotEmpty()) Text(line, fontSize = 14.sp, color = InkSecondary, modifier = Modifier.clickable { onOpenPerson(org?.id ?: return@clickable) })
                        val reach = (p.emails.take(1) + p.phones.take(1)).joinToString(" · ")
                        Text(reach.ifEmpty { "Add email or phone" }, fontSize = 13.sp, color = if (reach.isEmpty()) com.example.ui.theme.Accent else InkMuted,
                            modifier = Modifier.padding(top = 2.dp).clickable { contact = true }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (p.aliases.isNotEmpty()) Text("Also known as " + p.aliases.joinToString(", "), fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(horizontal = 20.dp))
            }

            if (p.kind == PersonKind.ORG && members.isNotEmpty()) {
                item { WorkSectionTitle("People", "${members.size}") }
                items(members, key = { "m-" + it.id }) { m -> PersonRow(m, m.role) { onOpenPerson(m.id) } }
            }

            val theirs = theyOwe.filter { !(it.ownerIsSelf || it.ownerPersonId == selfId) }
            val mine = open.filter { it.kind == ItemKind.TASK && (it.ownerIsSelf || it.ownerPersonId == selfId) }
            if (theirs.isNotEmpty()) {
                item { WorkSectionTitle(if (p.kind == PersonKind.ORG) "They owe you" else "${p.name.substringBefore(' ')} owes you", "${theirs.size}") }
                items(theirs, key = { "t-" + it.id }) { i ->
                    ItemRow(i, { viewModel.toggle(i) }, { editing = i }, i.meetingId?.let { titles[it] },
                        onPlay = i.meetingId?.let { m -> { onOpenMeeting(m, i.sourceStartMs) } },
                        action = { TextButton(onClick = { nudging = i }) { Text("Nudge") } })
                }
            }
            if (mine.isNotEmpty()) {
                item { WorkSectionTitle("You owe", "${mine.size}") }
                items(mine, key = { "y-" + it.id }) { i -> ItemRow(i, { viewModel.toggle(i) }, { editing = i }, i.meetingId?.let { titles[it] }) }
            }
            if (decisions.isNotEmpty()) {
                item { WorkSectionTitle("Decided") }
                items(decisions, key = { "d-" + it.id }) { i ->
                    ItemRow(i, null, { editing = i }, dayLabel(i.createdAt), onPlay = i.meetingId?.let { m -> { onOpenMeeting(m, i.sourceStartMs) } })
                }
            }
            if (questions.isNotEmpty()) {
                item { WorkSectionTitle("Still open", "${questions.size}") }
                items(questions, key = { "q-" + it.id }) { i -> ItemRow(i, null, { editing = i }, i.meetingId?.let { titles[it] }) }
            }
            item { WorkSectionTitle("Conversations", if (notes.isEmpty()) null else "${notes.size}") }
            if (notes.isEmpty()) item { EmptyLine("Recordings and notes with ${p.name.substringBefore(' ')} will appear here.") }
            items(notes, key = { "n-" + it.id }) { n ->
                Row(Modifier.fillMaxWidth().clickable { onOpenNote(n.id) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.title.ifBlank { n.workflow.displayName }, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dayLabel(n.eventDate ?: n.createdAt) + " · " + n.workflow.displayName, fontSize = 12.sp, color = InkMuted)
                    }
                }
            }
        }
    }

    if (renaming) TextDialog("Rename", p.name, "Everywhere ${p.name.substringBefore(' ')} appears — transcripts, summaries and tasks — will use the new name.",
        onDismiss = { renaming = false }) { viewModel.rename(p, it); renaming = false }
    if (orgEdit) TextDialog(settings.terms.organisation, org?.name.orEmpty(), null, onDismiss = { orgEdit = false }) { viewModel.setOrganisation(p, it); orgEdit = false }
    if (contact) ContactDialog(p, onDismiss = { contact = false }) { email, phone ->
        viewModel.updatePerson(p.copy(
            emails = (listOfNotNull(email?.trim()?.lowercase()?.takeIf { com.example.core.work.PeopleRepository.looksLikeEmail(it) }) + p.emails).distinct(),
            phones = (listOfNotNull(phone?.let(com.example.core.work.PeopleRepository::normalisePhone)) + p.phones).distinct()
        ))
        contact = false
    }
    if (merging) {
        ModalBottomSheet(onDismissRequest = { merging = false }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("${p.name} is the same person as…", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                everyone.filter { it.id != p.id && !it.isSelf }.take(30).forEach { other ->
                    PersonRow(other, null) { viewModel.merge(p, other); merging = false; onOpenPerson(other.id) }
                }
            }
        }
    }
    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { current ->
        val live = items.firstOrNull { it.id == current.id } ?: current
        ItemEditSheet(
            item = live, owners = ownerChoices(self, emptyList(), everyone), onDismiss = { editing = null },
            onText = { viewModel.setText(live, it) }, onKind = { viewModel.setKind(live, it) }, onOwner = { viewModel.setOwner(live, it) },
            onDue = { a, t -> viewModel.setDue(live, a, t) }, onAnswer = { viewModel.setAnswer(live, it) },
            onDelete = { viewModel.delete(live); editing = null },
            onPlay = live.meetingId?.let { m -> { editing = null; onOpenMeeting(m, live.sourceStartMs) } }
        )
    }
}

@Composable
internal fun TextDialog(title: String, initial: String, note: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                note?.let { Text(it, fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ContactDialog(person: Person, onDismiss: () -> Unit, onSave: (String?, String?) -> Unit) {
    var email by remember { mutableStateOf(person.emails.firstOrNull().orEmpty()) }
    var phone by remember { mutableStateOf(person.phones.firstOrNull().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Reach ${person.name.substringBefore(' ')}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (with country code)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Text("Used to address follow-ups. Kept on this phone.", fontSize = 12.sp, color = InkMuted)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(email.ifBlank { null }, phone.ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
