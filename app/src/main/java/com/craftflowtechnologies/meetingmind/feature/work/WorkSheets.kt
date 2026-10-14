package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** A way to begin a work note. [recordable] ones start a recording; the rest open a written note. */
internal data class WorkStart(val type: RecordingType, val title: String, val line: String, val icon: ImageVector)

internal val WorkStarts = listOf(
    WorkStart(RecordingType.MEETING, "Meeting notes", "Agenda, decisions, next steps", Icons.Filled.Groups),
    WorkStart(RecordingType.ONE_ON_ONE, "1:1", "Updates, blockers, feedback", Icons.Filled.Person),
    WorkStart(RecordingType.CLIENT_CALL, "Client call", "What they need, what's next", Icons.Filled.Handshake),
    WorkStart(RecordingType.STANDUP, "Standup", "Person by person", Icons.Filled.ViewDay),
    WorkStart(RecordingType.INTERVIEW, "Interview", "Questions and answers", Icons.Filled.RecordVoiceOver),
    WorkStart(RecordingType.CONSULTATION, "Consultation", "As said, kept on your phone", Icons.Filled.MedicalServices),
    WorkStart(RecordingType.PROJECT_BRIEF, "Project brief", "Goal, scope, people, dates", Icons.Filled.Description),
    WorkStart(RecordingType.DECISION_RECORD, "Decision record", "What, why, and what else", Icons.Filled.Rule),
    WorkStart(RecordingType.WEEKLY_REVIEW, "Weekly review", "Done, slipping, next", Icons.AutoMirrored.Filled.EventNote)
)

/** The order templates appear in for a kind of work: its own meeting types first. */
internal fun startsFor(profile: WorkProfile): List<WorkStart> {
    val first = profile.recordTypes
    val hideConsultation = profile != WorkProfile.CLINICAL && profile != WorkProfile.LEGAL
    return WorkStarts.filter { !(hideConsultation && it.type == RecordingType.CONSULTATION) }
        .sortedBy { s -> first.indexOf(s.type).let { if (it < 0) 99 else it } }
}

@Composable
internal fun tintFor(type: RecordingType): Color {
    val c = MM.colors
    return when (type) {
        RecordingType.MEETING, RecordingType.STANDUP -> c.accent
        RecordingType.ONE_ON_ONE, RecordingType.INTERVIEW -> c.speaker3
        RecordingType.CLIENT_CALL, RecordingType.CONSULTATION -> c.speaker4
        RecordingType.PROJECT_BRIEF, RecordingType.WEEKLY_REVIEW -> c.speaker2
        else -> c.inkSecondary
    }
}

/** A pill-shaped action. [filled] is the ink one; the other is outlined. Other Work screens use it. */
@Composable
internal fun Pill(label: String, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MM.colors
    Box(
        modifier.heightIn(min = MMSize.minTouch).clip(MM.radius.pill)
            .then(if (filled) Modifier.background(c.ink) else Modifier.border(MMSize.hairline, c.line, MM.radius.pill))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = MM.space.l, vertical = MM.space.s),
        contentAlignment = Alignment.Center
    ) { Text(label, style = MM.type.bodyStrong, color = if (filled) c.onInk else c.ink) }
}

/** A row that asks for one thing, with its one verb: "Wrap up →". */
@Composable
internal fun ActionCard(title: String, line: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clickable(onClick = onClick).padding(vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(action, style = MM.type.bodyStrong, color = MM.colors.accent)
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MM.colors.accent, modifier = Modifier.padding(start = MM.space.xs).size(MMSize.iconSmall))
    }
}

/** Which kind of conversation to record or write, work types first. This is where the templates live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordTypeSheet(starts: List<WorkStart>, onPick: (RecordingType) -> Unit, onDismiss: () -> Unit, title: String = "What are you recording?") {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.navigationBarsPadding().padding(bottom = MM.space.l)) {
            Text(title, style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s))
            starts.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clickable { onPick(s.type) }.padding(horizontal = MM.space.l, vertical = MM.space.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(MMSize.minTouch).background(MM.colors.surfaceSunk, MM.radius.small), contentAlignment = Alignment.Center) {
                        Icon(s.icon, null, tint = tintFor(s.type))
                    }
                    Column(Modifier.padding(start = MM.space.m)) {
                        Text(s.title, style = MM.type.bodyStrong, color = MM.colors.ink)
                        Text(s.line, style = MM.type.secondary, color = MM.colors.inkSecondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewProjectSheet(projectWord: String, orgWord: String, keepOnDevice: Boolean, onDismiss: () -> Unit, onCreate: (String, String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var confidential by remember { mutableStateOf(keepOnDevice) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Text("New ${projectWord.lowercase()}", style = MM.type.heading, color = MM.colors.ink)
            Text("Its meetings, notes, people, tasks and decisions will live together.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("project_name"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = MM.radius.small)
            OutlinedTextField(org, { org = it }, label = { Text("$orgWord (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = MM.radius.small)
            Row(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Confidential", style = MM.type.body, color = MM.colors.ink)
                    Text("Its recordings and notes are processed only on this phone", style = MM.type.caption, color = MM.colors.inkMuted)
                }
                Switch(confidential, { confidential = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Cancel", onDismiss)
                TextAction("Create", { onCreate(name, org, confidential) }, enabled = name.isNotBlank())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPersonSheet(orgWord: String, onDismiss: () -> Unit, onCreate: (String, String?, String?, String?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Text("Add someone", style = MM.type.heading, color = MM.colors.ink)
            Text("Kept on this phone. Only the name is needed.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MM.radius.small, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            OutlinedTextField(role, { role = it }, label = { Text("Role (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MM.radius.small, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(org, { org = it }, label = { Text("$orgWord (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MM.radius.small, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MM.radius.small, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone, with country code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MM.radius.small, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Cancel", onDismiss)
                TextAction("Add", { onCreate(name, role.ifBlank { null }, email.ifBlank { null }, phone.ifBlank { null }, org.ifBlank { null }) }, enabled = name.isNotBlank())
            }
        }
    }
}

/** Edits a work task with the app's own task editor, so tasks behave the same everywhere. */
@Composable
internal fun WorkTaskSheet(t: WorkTask, viewModel: WorkViewModel, onDismiss: () -> Unit, onOpenSource: (() -> Unit)?) {
    val tasksVm: com.craftflowtechnologies.meetingmind.feature.tasks.TasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val people by tasksVm.people.collectAsState()
    val task by androidx.compose.runtime.produceState<com.craftflowtechnologies.meetingmind.core.tasks.Task?>(null, t.id) {
        value = com.craftflowtechnologies.meetingmind.core.tasks.TaskReminders.repository(viewModel.getApplication()).get(t.id)
    }
    task?.let { full ->
        com.craftflowtechnologies.meetingmind.feature.tasks.TaskEditorSheet(
            task = full, people = people,
            onSave = { saved, newPerson -> tasksVm.save(saved, newPerson); onDismiss() },
            onDelete = { tasksVm.delete(full); onDismiss() },
            onOpenSource = onOpenSource,
            onDismiss = onDismiss
        )
    }
}
