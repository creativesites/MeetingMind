package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.craftflowtechnologies.meetingmind.core.ui.mm.citedAnnotatedString
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustKind
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustMark
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.ItemKind
import com.craftflowtechnologies.meetingmind.core.work.ItemStatus
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.work.itemKind
import com.craftflowtechnologies.meetingmind.core.work.itemStatus
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase

/*
 * The project tabs of the context page: Overview, Timeline and Notes & files. The page itself
 * (identity, status, actions, pulse header, tab switch) is ContextScreen.kt.
 */

/** A plain text dialog: rename a project or a person, or edit an organisation's name. */
@Composable
internal fun TextDialog(title: String, initial: String, note: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                note?.let { Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.s)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Overview: where it stands, what is owed both ways, decisions with the history they replaced,
 * what is proposed, risks and requirements, open questions, the people, and the meetings. Every
 * decision, risk and question opens its source, with the moment it was said.
 */
internal fun LazyListScope.projectOverviewItems(page: PageData, actions: PageActions) {
    val mine = page.tasks.filter { !it.done && !it.waitingOn }
    val theirs = page.tasks.filter { !it.done && it.waitingOn }

    item(key = "ov-where") { WhereItStands(page) }

    item(key = "ov-you") { WorkSectionTitle("You owe", if (mine.isNotEmpty()) "${mine.size}" else null) }
    item(key = "ov-add") { AddTaskField(page.name, actions.onAddTask) }
    items(mine, key = { "m-" + it.id }) { t ->
        TaskLine(t, null, { actions.onToggle(t) }, { actions.onEdit(t) }, t.meetingId?.let { m -> { actions.onOpenMeeting(m, t.startMs) } })
    }
    if (theirs.isNotEmpty()) {
        item(key = "ov-they") { WorkSectionTitle("Waiting on", "${theirs.size}") }
        items(theirs, key = { "w-" + it.id }) { t ->
            TaskLine(t, null, { actions.onToggle(t) }, { actions.onEdit(t) }, action = { TextButton(onClick = { actions.onNudge(t) }) { Text("Nudge") } })
        }
    }

    // Decided lists the decisions in force; proposed ones are listed under "To confirm" below.
    val chains = page.state.history.filter { it.last().itemStatus == ItemStatus.ACTIVE }
    item(key = "ov-decided") { WorkSectionTitle("Decided", if (chains.isEmpty()) null else "${chains.size}") }
    if (chains.isEmpty()) item(key = "ov-decided-empty") { EmptyLine("No decisions in force yet.") }
    items(chains.take(12), key = { "dh-" + it.last().id }) { chain ->
        val latest = chain.last()
        val replaced = chain.dropLast(1).asReversed().joinToString(", ") { "replaced ${it.text} (${Pulse.shortDate(it.createdAt)})" }
        PageRow(
            title = latest.text,
            detail = listOfNotNull(Pulse.shortDate(latest.createdAt), replaced.ifBlank { null }).joinToString(" · ")
        ) { actions.onOpenSource(SourceRef(itemId = latest.id, meetingId = latest.meetingId)) }
    }

    val proposed = page.items.filter { it.itemKind == ItemKind.DECISION && it.itemStatus == ItemStatus.PROPOSED }
    if (proposed.isNotEmpty()) {
        item(key = "ov-confirm") {
            Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.l), verticalAlignment = Alignment.CenterVertically) {
                Text("To confirm", style = MM.type.overline, color = MM.colors.inkMuted)
                Spacer(Modifier.width(MM.space.s))
                TrustMark(TrustKind.SUGGESTED)
            }
        }
        items(proposed.take(8), key = { "dp-" + it.id }) { d ->
            PageRow(title = d.text, detail = dayLabel(d.createdAt)) { actions.onOpenSource(SourceRef(itemId = d.id, meetingId = d.meetingId)) }
        }
    }

    val requirements = page.items.filter { it.itemKind == ItemKind.REQUIREMENT && ContextStatus.isOpen(it.itemStatus) }
    val risks = (page.state.risks + requirements).distinctBy { it.id }
    if (risks.isNotEmpty()) {
        item(key = "ov-risks") { WorkSectionTitle("Risks & requirements", "${risks.size}") }
        items(risks.take(12), key = { "rk-" + it.id }) { r ->
            PageRow(title = r.text, detail = dayLabel(r.createdAt)) { actions.onOpenSource(SourceRef(itemId = r.id, meetingId = r.meetingId)) }
        }
    }

    val questions = page.items.filter { it.itemKind == ItemKind.QUESTION && ContextStatus.isOpen(it.itemStatus) }
    if (questions.isNotEmpty()) {
        item(key = "ov-questions") { WorkSectionTitle("Still open", "${questions.size}") }
        items(questions.take(12), key = { "q-" + it.id }) { q ->
            PageRow(title = q.text, detail = dayLabel(q.createdAt)) { actions.onOpenSource(SourceRef(itemId = q.id, meetingId = q.meetingId)) }
        }
    }

    item(key = "ov-people") { WorkSectionTitle("People", "Add", onTrailing = actions.onAddMember) }
    if (page.state.members.isEmpty()) item(key = "ov-people-empty") { EmptyLine("Nobody added yet.") }
    items(page.state.members, key = { "pm-" + it.first }) { (pid, pname, role) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PageRow(title = pname, detail = role.ifBlank { null }, modifier = Modifier.weight(1f)) { actions.onOpenContext(ContextType.PERSON, pid) }
            TextButton(onClick = { actions.onRemoveMember(pid) }) { Text("Remove") }
        }
    }

    item(key = "ov-meetings") { WorkSectionTitle("Meetings", if (page.meetings.isEmpty()) null else "${page.meetings.size}") }
    if (page.meetings.isEmpty()) item(key = "ov-meetings-empty") { EmptyLine("Meetings filed to this project list here, newest first.") }
    items(page.meetings.take(12), key = { "mt-" + it.id }) { m ->
        PageRow(title = m.title, detail = Pulse.shortDate(m.at)) { actions.onOpenMeeting(m.id, null) }
    }
}

/** The project's timeline: changes and meetings, with a "This month" filter. Changes open their source. */
internal fun LazyListScope.timelineItems(page: PageData, actions: PageActions) {
    val entries = if (page.monthOnly) page.state.timeline.filter { it.at >= monthStart(page.now) } else page.state.timeline
    item(key = "tl-head") {
        WorkSectionTitle("Timeline", if (entries.isEmpty()) null else "${entries.size}")
        Row(Modifier.padding(horizontal = MM.space.m)) { Chip("This month", page.monthOnly, MM.colors.ink) { actions.onToggleMonth() } }
    }
    if (entries.isEmpty()) item(key = "tl-empty") {
        EmptyLine(if (page.monthOnly) "Nothing this month." else "Meetings and changes will build a timeline here.")
    }
    itemsIndexed(entries.take(60), key = { i, it -> "tl-$i-${it.at}-${it.itemId}" }) { _, e ->
        PageRow(title = e.text, detail = Pulse.shortDate(e.at)) {
            val itemId = e.itemId
            if (itemId != null) actions.onOpenSource(SourceRef(itemId = itemId, meetingId = e.meetingId))
            else e.meetingId?.let { actions.onOpenMeeting(it, null) }
        }
    }
}

/** Notes & files: what is filed here, and the two ways to add to it. */
internal fun LazyListScope.projectNotesItems(page: PageData, actions: PageActions) {
    item(key = "nf-actions") {
        Row(Modifier.padding(horizontal = MM.space.l).padding(top = MM.space.m), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Pill("● Record", filled = true) { actions.onRecord() }
            Pill("Write a note") { actions.onWriteNote() }
        }
    }
    item(key = "nf-head") { WorkSectionTitle("Notes", if (page.notes.isEmpty()) null else "${page.notes.size}") }
    if (page.notes.isEmpty()) item(key = "nf-empty") {
        EmptyLine("Nothing filed here yet. Record or write from this page and it lands here.")
    }
    items(page.notes, key = { "n-" + it.id }) { n -> WorkNoteRow(n) { actions.onOpenNote(n.id) } }
}

/**
 * The cited "Where it stands" paragraph. It is the latest written story for this page, marked
 * suggested until someone reviews it. Without one, it says what is open in plain counts.
 */
@Composable
private fun WhereItStands(page: PageData) {
    val story = page.story?.months?.lastOrNull { !it.text.isNullOrBlank() }
    val h = page.state.header
    Column(Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.m)) {
        Text("WHERE IT STANDS", style = MM.type.overline, color = MM.colors.inkMuted)
        if (story != null) {
            Text(
                citedAnnotatedString(story.text.orEmpty(), MM.colors.accent),
                style = MM.type.body,
                color = MM.colors.ink,
                modifier = Modifier.padding(top = MM.space.xs).testTag("where_it_stands")
            )
            Row(Modifier.padding(top = MM.space.xs)) { TrustMark(TrustKind.SUGGESTED) }
        } else {
            Text(
                "${h.open} open · ${h.decided} decided. Nothing has been written up yet.",
                style = MM.type.body,
                color = MM.colors.inkSecondary,
                modifier = Modifier.padding(top = MM.space.xs)
            )
        }
    }
}

@Composable
private fun AddTaskField(name: String, onAdd: (String) -> Unit) {
    var adding by remember { mutableStateOf("") }
    OutlinedTextField(
        adding, { adding = it },
        placeholder = { Text("Add a task for $name") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Add, null, tint = MM.colors.inkMuted) },
        shape = MM.radius.card,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.m),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (adding.isNotBlank()) {
                onAdd(adding)
                adding = ""
            }
        })
    )
}
