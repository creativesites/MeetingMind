package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.FindingKind
import com.craftflowtechnologies.meetingmind.core.work.PersonKind
import com.craftflowtechnologies.meetingmind.core.work.WorkPeople
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase

/**
 * A person's or organisation's page (docs/PLAN_PROFESSIONAL.md §6.3): what you owe each other,
 * what was decided with them, and every conversation. Renaming here renames them everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPersonScreen(
    viewModel: WorkViewModel,
    personId: String,
    onNavigateBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    /** Sections a context page adds after the masthead and at the end. */
    extraTop: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    extraBottom: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null
) {
    val person by remember(personId) { viewModel.person(personId) }.collectAsState(initial = null)
    val tasks by remember(personId) { viewModel.tasksWith(personId) }.collectAsState(initial = emptyList())
    val notes by remember(personId) { viewModel.notesWith(personId) }.collectAsState(initial = emptyList())
    val decisions by remember(personId) { viewModel.decisionsWith(personId) }.collectAsState(initial = emptyList())
    val members by remember(personId) { viewModel.members(personId) }.collectAsState(initial = emptyList())
    val everyone by viewModel.everyone.collectAsState()
    val orgs by viewModel.organisations.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf(false) }
    var orgEdit by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var nudging by remember { mutableStateOf<WorkTask?>(null) }
    val p = person ?: run { Scaffold(containerColor = SurfaceBase) { Column(Modifier.padding(it)) {} }; return }
    val org = orgs.firstOrNull { it.id == p.orgId }
    val theyOwe = tasks.filter { !it.done && it.waitingOn }
    val youOwe = tasks.filter { !it.done && !it.waitingOn }

    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Spacer(Modifier.weight(1f))
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
                        DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; viewModel.deletePerson(p); onNavigateBack() })
                    }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(p, 60.dp)
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(p.name + if (p.confidential) "  🔒" else "", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.clickable { renaming = true })
                        val line = listOfNotNull(p.role, org?.name).joinToString(" · ")
                        if (line.isNotEmpty()) Text(line, fontSize = 14.sp, color = InkSecondary, modifier = Modifier.clickable { org?.let { onOpenPerson(it.id) } })
                        val reach = (p.emails.take(1) + p.phones.take(1)).joinToString(" · ")
                        Text(reach.ifEmpty { "Add email or phone" }, fontSize = 13.sp, color = if (reach.isEmpty()) Accent else InkMuted,
                            modifier = Modifier.padding(top = 2.dp).clickable { contact = true }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (p.aliases.isNotEmpty()) Text("Also known as " + p.aliases.joinToString(", "), fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(horizontal = 20.dp))
            }
            extraTop?.invoke(this)
            if (p.kind == PersonKind.ORG && members.isNotEmpty()) {
                item { WorkSectionTitle("People", "${members.size}") }
                items(members, key = { "m-" + it.id }) { m -> PersonRow(m, null) { onOpenPerson(m.id) } }
            }
            if (theyOwe.isNotEmpty()) {
                item { WorkSectionTitle(if (p.kind == PersonKind.ORG) "They owe you" else "${p.firstName} owes you", "${theyOwe.size}") }
                items(theyOwe, key = { "t-" + it.id }) { t ->
                    TaskLine(t, null, { viewModel.toggle(t) }, { editing = t }, t.meetingId?.let { m -> { onOpenMeeting(m, t.startMs) } },
                        action = { TextButton(onClick = { nudging = t }) { Text("Nudge") } })
                }
            }
            if (youOwe.isNotEmpty()) {
                item { WorkSectionTitle("You owe", "${youOwe.size}") }
                items(youOwe, key = { "y-" + it.id }) { t -> TaskLine(t, null, { viewModel.toggle(t) }, { editing = t }) }
            }
            if (decisions.isNotEmpty()) {
                item { WorkSectionTitle("Decided together") }
                items(decisions.take(10), key = { "d-" + it.id }) { d -> FindingLine(FindingKind.DECISION, d.text, null, onClick = { onOpenMeeting(d.meetingId, null) }) }
            }
            item { WorkSectionTitle("Conversations", if (notes.isEmpty()) null else "${notes.size}") }
            if (notes.isEmpty()) item { EmptyLine("Recordings and notes with ${p.firstName} will appear here.") }
            items(notes, key = { "n-" + it.id }) { n ->
                Row(Modifier.fillMaxWidth().clickable { onOpenNote(n.id) }.padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Description, null, tint = tintFor(n.workflow))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(n.title.ifBlank { n.workflow.displayName }, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dayLabel(n.eventDate ?: n.createdAt) + " · " + n.workflow.displayName, fontSize = 12.sp, color = InkMuted)
                    }
                }
            }
            extraBottom?.invoke(this)
        }
    }

    if (renaming) TextDialog("Rename", p.name, "Everywhere ${p.firstName} appears — transcripts, summaries and tasks — will use the new name.",
        onDismiss = { renaming = false }) { viewModel.rename(p, it); renaming = false }
    if (orgEdit) TextDialog(settings.terms.organisation, org?.name.orEmpty(), null, onDismiss = { orgEdit = false }) { viewModel.setOrganisation(p, it); orgEdit = false }
    if (contact) ContactDialog(p, onDismiss = { contact = false }) { email, phone ->
        viewModel.updatePerson(p.copy(
            emails = (listOfNotNull(email?.trim()?.lowercase()?.takeIf { WorkPeople.looksLikeEmail(it) }) + p.emails).distinct(),
            phones = (listOfNotNull(phone?.let(WorkPeople::normalisePhone)) + p.phones).distinct()
        ))
        contact = false
    }
    if (merging) {
        ModalBottomSheet(onDismissRequest = { merging = false }, containerColor = SurfaceBase) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("${p.name} is the same person as…", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                everyone.filter { it.id != p.id }.take(30).forEach { other -> PersonRow(other, null) { viewModel.merge(p, other); merging = false; onOpenPerson(other.id) } }
            }
        }
    }
    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { t -> WorkTaskSheet(t, viewModel, onDismiss = { editing = null }, onOpenSource = t.meetingId?.let { m -> { editing = null; onOpenMeeting(m, t.startMs) } }) }
}

@Composable
internal fun ContactDialog(person: WorkPerson, onDismiss: () -> Unit, onSave: (String?, String?) -> Unit) {
    var email by remember { mutableStateOf(person.emails.firstOrNull().orEmpty()) }
    var phone by remember { mutableStateOf(person.phones.firstOrNull().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase,
        title = { Text("Reach ${person.firstName}") },
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
