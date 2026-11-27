package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.ui.work.SourceChip
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustKind
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustMark
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.PrepPack
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prepare as a full screen. It reads the same [PrepPack] as the compact [PrepareSheet] peek, with
 * a sticky Record button. Every line opens its source; the agenda is labelled as a suggestion and
 * is built only from open items. With nothing on record yet, it shows the first-meeting state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareScreen(
    target: PrepTarget,
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenNote: (String) -> Unit,
    onRecordInto: (noteId: String, type: RecordingType, title: String) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val pack by produceState<PrepPack?>(null, target) {
        value = when (target) {
            is PrepTarget.Event -> viewModel.prepare.forEvent(target.event)
            is PrepTarget.Entity -> viewModel.prepare.forEntity(target.type, target.id)
        }
    }
    val projectId by produceState<String?>(null, target) {
        value = when (target) {
            is PrepTarget.Entity -> if (target.type == ContextType.PROJECT) target.id else null
            is PrepTarget.Event -> viewModel.context.resolveEvent(target.event).projectId
        }
    }
    val materialFlow: Flow<List<Note>> = remember(target, projectId) {
        when (target) {
            is PrepTarget.Entity -> when (target.type) {
                ContextType.PERSON -> viewModel.notesWith(target.id)
                ContextType.PROJECT -> viewModel.notesIn(target.id)
                ContextType.ORG -> flowOf(emptyList<Note>())
            }
            is PrepTarget.Event -> projectId?.let { viewModel.notesIn(it) } ?: flowOf(emptyList<Note>())
        }
    }
    val material by materialFlow.collectAsState(initial = emptyList())
    var source by remember { mutableStateOf<SourceRef?>(null) }
    var recordPicker by remember { mutableStateOf(false) }

    val headline = when (target) {
        is PrepTarget.Event -> target.event.title.ifBlank { "Meeting" }
        is PrepTarget.Entity -> pack?.title?.takeIf { it.isNotBlank() } ?: "Prepare"
    }
    val clock = when (target) {
        is PrepTarget.Event -> clockTime(target.event.begin)
        is PrepTarget.Entity -> null
    }
    val notebookId = when (target) {
        is PrepTarget.Entity -> if (target.type == ContextType.PROJECT) target.id else null
        is PrepTarget.Event -> projectId
    }

    Scaffold(
        containerColor = SurfaceBase,
        bottomBar = {
            Row(Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.s)) {
                Button(
                    onClick = { recordPicker = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).testTag("prepare_record"),
                    colors = ButtonDefaults.buttonColors(containerColor = MM.colors.recording, contentColor = MM.colors.onAccent)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.padding(end = MM.space.xs))
                    Text("Record", style = MM.type.bodyStrong)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().testTag("prepare_screen"),
            contentPadding = PaddingValues(bottom = MM.space.xl)
        ) {
            item(key = "prep-top") {
                Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.ink) }
                    Spacer(Modifier.weight(1f))
                }
                Column(Modifier.padding(horizontal = MM.space.l)) {
                    Text("PREPARE", style = MM.type.overline, color = MM.colors.inkMuted)
                    Text(headline, style = MM.type.title, color = MM.colors.ink, modifier = Modifier.padding(top = MM.space.xs))
                    val meta = listOfNotNull(clock, (target as? PrepTarget.Event)?.let { dayLabel(it.event.begin) }).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
                }
            }

            val p = pack ?: return@LazyColumn
            if (p.isEmpty) {
                // First meeting: nothing on record yet, so say what is known and what to bring.
                firstMeetingItems(target, material, onOpenNote)
            } else {
                p.lastMeeting?.let { last ->
                    item(key = "prep-last") {
                        SectionHead("Last time", dayLabel(last.at))
                        Column(Modifier.fillMaxWidth().padding(horizontal = MM.space.l)) {
                            Text(
                                last.title,
                                style = MM.type.bodyStrong,
                                color = MM.colors.ink,
                                modifier = Modifier.clickable { onOpenMeeting(last.meetingId, last.startMs) }
                            )
                            last.quote?.let { quote ->
                                Text(
                                    "“$quote”",
                                    style = MM.type.body.copy(fontStyle = FontStyle.Italic),
                                    color = MM.colors.inkSecondary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = MM.space.xs)
                                )
                                Row(Modifier.padding(top = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    SourceChip(
                                        label = last.startMs?.let { formatTime(it) } ?: "Open",
                                        onClick = {
                                            source = SourceRef(itemId = last.itemId, meetingId = last.meetingId, startMs = last.startMs, title = last.title, quote = last.quote)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                itemSection("You owe", "prep-owe", p.youOwe) { source = SourceRef(itemId = it.id, meetingId = it.meetingId) }
                itemSection("They owe", "prep-they", p.theyOwe) { source = SourceRef(itemId = it.id, meetingId = it.meetingId) }
                itemSection("Decided", "prep-decided", p.decisions) { source = SourceRef(itemId = it.id, meetingId = it.meetingId) }
                itemSection("Still open", "prep-open", p.questions) { source = SourceRef(itemId = it.id, meetingId = it.meetingId) }

                if (p.agenda.isNotEmpty()) {
                    item(key = "prep-agenda") {
                        SectionHead("Suggested agenda", "from open items")
                        Row(Modifier.padding(horizontal = MM.space.l).padding(bottom = MM.space.xs)) { TrustMark(TrustKind.SUGGESTED) }
                    }
                    items(p.agenda.size, key = { "prep-ag-" + p.agenda[it].itemId }) { i ->
                        val line = p.agenda[i]
                        PrepRow(text = "${i + 1}. ${line.text}", detail = line.kind.label) {
                            source = SourceRef(itemId = line.itemId)
                        }
                    }
                }
            }

            if (material.isNotEmpty()) {
                item(key = "prep-material") { SectionHead("Material", "${material.size}") }
                items(material, key = { "prep-m-" + it.id }) { n ->
                    PrepRow(text = n.title.ifBlank { n.workflow.displayName }, detail = n.workflow.displayName + " · " + dayLabel(n.eventDate ?: n.createdAt)) {
                        onOpenNote(n.id)
                    }
                }
            }
        }
    }

    source?.let { s ->
        SourceSheet(
            source = s,
            onOpenInTranscript = { meetingId, at -> source = null; onOpenMeeting(meetingId, at) },
            onDismiss = { source = null }
        )
    }
    if (recordPicker) RecordTypeSheet(
        startsFor(settings.profile).filter { Workflows.isRecordable(it.type) },
        onPick = { type ->
            recordPicker = false
            viewModel.newNote(type, notebookId) { id -> onRecordInto(id, type, "${type.displayName} · $headline") }
        },
        onDismiss = { recordPicker = false }
    )
}

/** A first meeting: who is coming from the invite, what is already filed, and a plain structure to cover. */
private fun androidx.compose.foundation.lazy.LazyListScope.firstMeetingItems(
    target: PrepTarget,
    material: List<Note>,
    onOpenNote: (String) -> Unit
) {
    item(key = "prep-first") {
        Column(Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.s)) {
            Text("This is a first meeting with nothing on record yet.", style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(
                "Record it, and next time this page shows what was said, what is owed and what to decide.",
                style = MM.type.secondary,
                color = MM.colors.inkSecondary,
                modifier = Modifier.padding(top = MM.space.xs)
            )
        }
    }
    val event = (target as? PrepTarget.Event)?.event
    if (event != null) {
        val attendees = (event.people + event.emails).filter { it.isNotBlank() }.distinct()
        item(key = "prep-who") { SectionHead("Who's coming", "from the invite") }
        if (attendees.isEmpty()) {
            item(key = "prep-who-empty") { PrepRow(text = "No guests on the invite.", detail = null) {} }
        } else {
            itemsIndexed(attendees, key = { i, a -> "prep-who-$i-$a" }) { _, a -> PrepRow(text = a, detail = null) {} }
        }
    }
    item(key = "prep-what") { SectionHead("What you have", if (material.isEmpty()) null else "${material.size}") }
    if (material.isEmpty()) {
        item(key = "prep-what-empty") { PrepRow(text = "Nothing filed yet.", detail = "Files and notes you add here show up in this list.") {} }
    } else {
        items(material, key = { "prep-what-${it.id}" }) { n ->
            PrepRow(text = n.title.ifBlank { n.workflow.displayName }, detail = dayLabel(n.eventDate ?: n.createdAt)) { onOpenNote(n.id) }
        }
    }
    item(key = "prep-structure") {
        SectionHead("Suggested structure", "not from your record")
        Column(Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(bottom = MM.space.m)) {
            Text(
                "Background · goals · current process · who decides · timeline · next step.",
                style = MM.type.secondary,
                color = MM.colors.inkSecondary
            )
        }
    }
}

/** A section heading with an optional count or caption on the right. */
@Composable
private fun SectionHead(title: String, trailing: String?) {
    Row(
        Modifier.fillMaxWidth().padding(start = MM.space.l, end = MM.space.l, top = MM.space.l, bottom = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MM.type.overline, color = MM.colors.inkMuted)
        if (trailing != null) {
            Spacer(Modifier.width(MM.space.s))
            Text(trailing, style = MM.type.caption, color = MM.colors.inkMuted)
        }
    }
}

/** One line of a section: the text, an optional detail, and the whole row opens its source. */
@Composable
private fun PrepRow(text: String, detail: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = MM.space.l, vertical = MM.space.s)
    ) {
        Text(text, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemSection(
    title: String,
    key: String,
    list: List<ItemEntity>,
    onOpen: (ItemEntity) -> Unit
) {
    if (list.isEmpty()) return
    item(key = key) { SectionHead(title, "${list.size}") }
    items(list.take(8), key = { "$key-${it.id}" }) { i ->
        PrepRow(text = i.text, detail = i.detailLine()) { onOpen(i) }
    }
}

private fun ItemEntity.detailLine(): String =
    listOfNotNull(dueAt?.let { "due ${Pulse.shortDate(it)}" }, dayLabel(createdAt)).joinToString(" · ")

private fun clockTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
