package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.citedAnnotatedString
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustKind
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustMark
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.ItemStatus
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.itemStatus
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase

/*
 * The person and organisation tabs of the context page. The page itself is ContextScreen.kt; these
 * are its bodies. Nothing here writes except through the actions the page passes in.
 */

/** Conversations: what each side owes, what was decided together, and every conversation. */
internal fun LazyListScope.personConversationsItems(page: PageData, actions: PageActions) {
    val first = page.name.trim().substringBefore(' ').ifBlank { "They" }
    val theyOwe = page.tasks.filter { !it.done && it.waitingOn }
    val youOwe = page.tasks.filter { !it.done && !it.waitingOn }

    if (theyOwe.isNotEmpty()) {
        item(key = "pc-they") { WorkSectionTitle("$first owes you", "${theyOwe.size}") }
        items(theyOwe, key = { "t-" + it.id }) { t ->
            TaskLine(
                t, null, { actions.onToggle(t) }, { actions.onEdit(t) },
                t.meetingId?.let { m -> { actions.onOpenMeeting(m, t.startMs) } },
                action = { TextButton(onClick = { actions.onNudge(t) }) { Text("Nudge") } }
            )
        }
    }
    if (youOwe.isNotEmpty()) {
        item(key = "pc-you") { WorkSectionTitle("You owe", "${youOwe.size}") }
        items(youOwe, key = { "y-" + it.id }) { t -> TaskLine(t, null, { actions.onToggle(t) }, { actions.onEdit(t) }) }
    }

    val chains = page.state.history.filter { it.last().itemStatus == ItemStatus.ACTIVE }
    if (chains.isNotEmpty()) {
        item(key = "pc-decided") { WorkSectionTitle("Decided together", "${chains.size}") }
        items(chains.take(10), key = { "pd-" + it.last().id }) { chain ->
            val latest = chain.last()
            PageRow(title = latest.text, detail = Pulse.shortDate(latest.createdAt)) {
                actions.onOpenSource(SourceRef(itemId = latest.id, meetingId = latest.meetingId))
            }
        }
    }

    item(key = "pc-convos") { WorkSectionTitle("Conversations", if (page.notes.isEmpty()) null else "${page.notes.size}") }
    if (page.notes.isEmpty()) item(key = "pc-convos-empty") { EmptyLine("Recordings and notes with $first will appear here.") }
    items(page.notes, key = { "n-" + it.id }) { n -> WorkNoteRow(n) { actions.onOpenNote(n.id) } }
}

/** Projects: the projects this person's items are filed under. */
internal fun LazyListScope.projectsListItems(page: PageData, actions: PageActions) {
    item(key = "pj-head") { WorkSectionTitle("Projects", if (page.projects.isEmpty()) null else "${page.projects.size}") }
    if (page.projects.isEmpty()) item(key = "pj-empty") { EmptyLine("Nothing filed under a project yet.") }
    items(page.projects, key = { "pj-" + it.first }) { (pid, pname) ->
        PageRow(title = pname, detail = null) { actions.onOpenContext(ContextType.PROJECT, pid) }
    }
}

/**
 * "How MeetingMind knows" a person: what it has on record for them, where their contact details
 * came from, and the meetings it draws on.
 */
internal fun LazyListScope.personHowWeKnowItems(page: PageData, actions: PageActions) {
    val first = page.name.trim().substringBefore(' ').ifBlank { "this person" }
    item(key = "hk-text") {
        Column(Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.m)) {
            Text(
                "${page.meetings.size} meetings and ${page.notes.size} notes with $first.",
                style = MM.type.bodyStrong,
                color = MM.colors.ink
            )
            Text(
                "Their email and phone are the ones on calendar invites or that you added yourself.",
                style = MM.type.secondary,
                color = MM.colors.inkSecondary,
                modifier = Modifier.padding(top = MM.space.xs)
            )
            page.person?.lastSeenAt?.let { seen ->
                Text("Last seen ${Pulse.shortDate(seen)}.", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
            }
        }
    }
    item(key = "hk-meetings") { WorkSectionTitle("Meetings", if (page.meetings.isEmpty()) null else "${page.meetings.size}") }
    if (page.meetings.isEmpty()) item(key = "hk-empty") { EmptyLine("No meetings with $first yet.") }
    items(page.meetings.take(12), key = { "hm-" + it.id }) { m ->
        PageRow(title = m.title, detail = Pulse.shortDate(m.at)) { actions.onOpenMeeting(m.id, null) }
    }
}

/** An organisation's people, each opening their own page. */
internal fun LazyListScope.orgPeopleItems(page: PageData, actions: PageActions) {
    item(key = "op-head") { WorkSectionTitle("People", if (page.people.isEmpty()) null else "${page.people.size}") }
    if (page.people.isEmpty()) item(key = "op-empty") { EmptyLine("No people are linked to this organisation yet.") }
    items(page.people, key = { "op-" + it.id }) { m: WorkPerson ->
        PersonRow(m, m.role) { actions.onOpenContext(ContextType.PERSON, m.id) }
    }
}

/**
 * An organisation's history: the counts, the monthly story (cited, marked suggested), what matters
 * most, what is still open, and the recent changes. A story line opens the first item it cites.
 */
internal fun LazyListScope.orgHistoryItems(page: PageData, actions: PageActions) {
    val history = page.story
    if (history != null) {
        val c = history.counts
        item(key = "oh-counts") {
            Text(
                listOf("${c.meetings} meetings", "${c.decisions} decisions", "${c.commitments} promises", "${c.questions} questions", "${c.risks} risks")
                    .joinToString("  ·  "),
                style = MM.type.caption,
                color = MM.colors.inkSecondary,
                modifier = Modifier.padding(horizontal = MM.space.l)
            )
        }
    }
    val told = history?.months?.filter { !it.text.isNullOrBlank() }.orEmpty()
    if (told.isNotEmpty()) {
        item(key = "oh-story") { WorkSectionTitle("The story") }
        items(told, key = { "st-" + it.month }) { m ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { m.cites.firstOrNull()?.let { actions.onOpenSource(SourceRef(itemId = it)) } }
                    .padding(horizontal = MM.space.l, vertical = MM.space.s)
            ) {
                Text(monthLabel(m.month), style = MM.type.caption, color = MM.colors.inkMuted)
                Text(citedAnnotatedString(m.text.orEmpty(), MM.colors.accent), style = MM.type.body, color = MM.colors.ink, modifier = Modifier.padding(top = MM.space.xs))
                Row(Modifier.padding(top = MM.space.xs)) { TrustMark(TrustKind.SUGGESTED) }
            }
        }
    }
    val important = history?.mostImportant.orEmpty()
    if (important.isNotEmpty()) {
        item(key = "oh-important") { WorkSectionTitle("Most important") }
        items(important, key = { "oi-" + it.id }) { i ->
            PageRow(title = i.text, detail = dayLabel(i.createdAt)) { actions.onOpenSource(SourceRef(itemId = i.id, meetingId = i.meetingId)) }
        }
    }
    history?.stillOpen.orEmpty().filter { i -> important.none { it.id == i.id } }.takeIf { it.isNotEmpty() }?.let { list ->
        item(key = "oh-open") { WorkSectionTitle("Still open", "${list.size}") }
        items(list.take(10), key = { "so-" + it.id }) { i ->
            PageRow(title = i.text, detail = dayLabel(i.createdAt)) { actions.onOpenSource(SourceRef(itemId = i.id, meetingId = i.meetingId)) }
        }
    }
    if (page.state.timeline.isNotEmpty()) {
        item(key = "oh-changes") { WorkSectionTitle("Recent changes", "${page.state.timeline.size}") }
        itemsIndexed(page.state.timeline.take(12), key = { i, it -> "oc-$i-${it.at}" }) { _, e ->
            PageRow(title = e.text, detail = Pulse.shortDate(e.at)) {
                val itemId = e.itemId
                if (itemId != null) actions.onOpenSource(SourceRef(itemId = itemId, meetingId = e.meetingId))
                else e.meetingId?.let { actions.onOpenMeeting(it, null) }
            }
        }
    }
}

private fun monthLabel(month: String): String = runCatching {
    java.time.YearMonth.parse(month).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.getDefault()))
}.getOrDefault(month)

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
                Text("Used to address follow-ups. Kept on this phone.", style = MM.type.caption, color = MM.colors.inkMuted)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(email.ifBlank { null }, phone.ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
