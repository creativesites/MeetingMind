package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.Prepare
import com.craftflowtechnologies.meetingmind.core.work.PrepPack
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.SavedAnswer
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import kotlinx.coroutines.launch

/** What to prepare for: a calendar event, or a person, organisation or project. */
sealed interface PrepTarget {
    data class Event(val event: PulseEvent) : PrepTarget
    data class Entity(val type: ContextType, val id: String) : PrepTarget
}

/**
 * Prepare (docs/PLAN_PROFESSIONAL.md D5.4): last time, what's still open, what you owe and are
 * owed, decisions in force, questions to settle, and a suggested agenda. All from the database, so
 * it works with no model. Every line opens the moment it came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareSheet(target: PrepTarget, viewModel: WorkViewModel, onOpenMeeting: (String, Long?) -> Unit, onDismiss: () -> Unit) {
    val pack by produceState<PrepPack?>(null, target) {
        value = when (target) {
            is PrepTarget.Event -> viewModel.prepare.forEvent(target.event)
            is PrepTarget.Entity -> viewModel.prepare.forEntity(target.type, target.id)
        }
    }
    // The model's optional prose: one line about last time and agenda wording, each citing its evidence.
    val proseScope by produceState<com.craftflowtechnologies.meetingmind.core.work.PackScope?>(null, target) {
        value = when (target) {
            is PrepTarget.Entity -> com.craftflowtechnologies.meetingmind.core.work.PackScope.Entity(target.type, target.id)
            is PrepTarget.Event -> viewModel.context.resolveEvent(target.event).let { ref ->
                ref.projectId?.let { com.craftflowtechnologies.meetingmind.core.work.PackScope.Entity(ContextType.PROJECT, it) }
                    ?: ref.ids.firstOrNull()?.let { com.craftflowtechnologies.meetingmind.core.work.PackScope.Entity(ContextType.PERSON, it) }
            }
        }
    }
    val prose by produceState<com.craftflowtechnologies.meetingmind.core.work.PrepProse?>(null, pack, proseScope) {
        val p = pack; val sc = proseScope
        if (p != null && sc != null) value = viewModel.prepareWriter.write(sc, p)
    }
    val scope = rememberCoroutineScope()
    fun open(itemId: String?) { if (itemId != null) scope.launch { viewModel.evidenceOf(itemId)?.let { (m, at) -> onDismiss(); onOpenMeeting(m, at) } } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        val p = pack
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).testTag("prepare_sheet")) {
            Text("PREPARE", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(horizontal = 20.dp))
            Text(p?.title?.ifBlank { null } ?: "Prepare", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            when {
                p == null -> Unit
                p.isEmpty -> EmptyLine("This will be a first meeting with nothing on record yet. Record it, and next time this shows what was said, what's owed and what to decide.")
                else -> LazyColumn {
                    p.lastMeeting?.let { last ->
                        item {
                            WorkSectionTitle("Last time", dayLabel(last.at))
                            prose?.lastTime?.let { line ->
                                Text("✨ ${line.text}", fontSize = 15.sp, color = Ink, modifier = Modifier.fillMaxWidth().clickable { open(line.cites.firstOrNull()) }.padding(horizontal = 20.dp, vertical = 4.dp).testTag("prepare_last_time"))
                            }
                            Column(Modifier.fillMaxWidth().clickable { onOpenMeeting(last.meetingId, last.startMs).also { onDismiss() } }.padding(horizontal = 20.dp, vertical = 4.dp)) {
                                Text(last.title.ifBlank { "Meeting" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                                last.quote?.let { Text("“$it”", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                                last.startMs?.let { PlayChip(it) { onOpenMeeting(last.meetingId, it); onDismiss() } }
                            }
                        }
                    }
                    if (p.agenda.isNotEmpty()) {
                        item { WorkSectionTitle("Suggested agenda") }
                        prose?.agenda?.takeIf { it.isNotEmpty() }?.let { lines ->
                            items(lines.size, key = { "aw-$it" }) { i ->
                                Text("✨ ${lines[i].text}", fontSize = 14.sp, color = InkSecondary, modifier = Modifier.fillMaxWidth().clickable { open(lines[i].cites.firstOrNull()) }.padding(horizontal = 20.dp, vertical = 4.dp))
                            }
                        }
                        items(p.agenda.size, key = { "a-" + p.agenda[it].itemId }) { i ->
                            val a = p.agenda[i]
                            Row(Modifier.fillMaxWidth().clickable { open(a.itemId) }.padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.Top) {
                                Text("${i + 1}.", fontSize = 14.sp, color = InkMuted, modifier = Modifier.padding(end = 10.dp))
                                Column {
                                    Text(a.text, fontSize = 15.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(a.kind.label, fontSize = 12.sp, color = InkMuted)
                                }
                            }
                        }
                    }
                    itemSection("You owe", p.youOwe, ::open)
                    itemSection("They owe", p.theyOwe, ::open)
                    itemSection("Decided", p.decisions, ::open)
                    itemSection("Questions to settle", p.questions, ::open)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemSection(title: String, list: List<ItemEntity>, open: (String?) -> Unit) {
    if (list.isEmpty()) return
    item(key = "h-$title") { WorkSectionTitle(title, "${list.size}") }
    items(list.take(5), key = { "$title-${it.id}" }) { i ->
        Text(i.text, fontSize = 15.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { open(i.id) }.padding(horizontal = 20.dp, vertical = 6.dp))
    }
}

/** A saved-filter chip's answer: specific lines, each opening its evidence. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAnswerSheet(answer: SavedAnswer?, title: String, viewModel: WorkViewModel, onOpenMeeting: (String, Long?) -> Unit, onOpenPerson: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).testTag("saved_answer")) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            when {
                answer == null -> Unit
                answer.lines.isEmpty() -> EmptyLine("Nothing here right now.")
                else -> LazyColumn {
                    items(answer.lines.size) { i ->
                        val line = answer.lines[i]
                        Column(Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                val ev = line.itemId?.let { viewModel.evidenceOf(it) }
                                if (ev != null) { onDismiss(); onOpenMeeting(ev.first, ev.second) } else line.personId?.let { onDismiss(); onOpenPerson(it) }
                            }
                        }.padding(horizontal = 20.dp, vertical = 7.dp)) {
                            Text(line.text, fontSize = 15.sp, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            line.detail?.let { Text(it, fontSize = 12.sp, color = InkMuted) }
                        }
                    }
                }
            }
        }
    }
}

/** The four saved filters as chips, for the Work space and context pages. */
@Composable
fun SavedFilterRow(onPick: (com.craftflowtechnologies.meetingmind.core.work.SavedFilter) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 12.dp).testTag("saved_filters")
    ) {
        items(com.craftflowtechnologies.meetingmind.core.work.SavedFilter.entries) { f -> Chip(f.label, false, Ink) { onPick(f) } }
    }
}

/** "Prepare" on a note made from a calendar event, built from the note's own record of the event. */
@Composable
fun NotePrepareRow(title: String, metadata: Map<String, String>, onOpenMeeting: (String, Long?) -> Unit) {
    val key = metadata[com.craftflowtechnologies.meetingmind.core.repository.NoteRepository.CALENDAR_EVENT_KEY] ?: return
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity ?: return
    val work: WorkViewModel = androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = activity)
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val begin = metadata["eventStart"]?.toLongOrNull() ?: 0L
    val event = PulseEvent(
        key, title, begin, metadata["eventEnd"]?.toLongOrNull() ?: begin,
        people = metadata["participants"]?.split(", ")?.filter { it.isNotBlank() }.orEmpty(),
        emails = metadata[com.craftflowtechnologies.meetingmind.core.work.WorkPeople.ATTENDEE_EMAILS]
            ?.let { runCatching { org.json.JSONObject(it).let { o -> o.keys().asSequence().map { k -> o.optString(k) }.toList() } }.getOrNull() }.orEmpty()
    )
    Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) { Pill("Prepare", onClick = { open = true }) }
    if (open) PrepareSheet(PrepTarget.Event(event), work, onOpenMeeting, onDismiss = { open = false })
}
