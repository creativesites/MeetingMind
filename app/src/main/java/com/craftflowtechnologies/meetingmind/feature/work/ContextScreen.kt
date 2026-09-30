package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.work.ContextHeader
import com.craftflowtechnologies.meetingmind.core.work.ContextRepository
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.work.SavedFilter
import com.craftflowtechnologies.meetingmind.core.work.TimelineEntry
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

/** What a context page shows beyond the person, organisation or project's own screen. */
data class ContextState(
    val header: ContextHeader,
    val history: List<List<ItemEntity>>,
    val risks: List<ItemEntity>,
    val timeline: List<TimelineEntry>,
    /** An organisation's projects: id and name. */
    val projects: List<Pair<String, String>> = emptyList(),
    /** A project's members: person id, name, role. */
    val members: List<Triple<String, String, String>> = emptyList(),
    val org: PersonEntity? = null,
    val projectProps: String? = null,
    val openItems: Int = 0
)

/**
 * The context page (docs/PLAN_PROFESSIONAL.md D5.2): one screen for a person, an organisation or a
 * project. It opens with the pulse header and "Prepare for conversation", then carries the page's
 * own lists, its decision history, risks and a timeline. The existing person and project screens
 * stay as its body, so nothing they did is lost; their routes open this screen.
 */
@Composable
fun ContextScreen(
    type: ContextType,
    id: String,
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenContext: (ContextType, String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onRecordInto: (noteId: String, type: RecordingType, title: String) -> Unit
) {
    val state by remember(type, id) { viewModel.contextState(type, id) }.collectAsState(initial = null)
    var preparing by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf<SavedFilter?>(null) }
    var editing by remember { mutableStateOf(false) }
    var monthOnly by rememberSaveable { mutableStateOf(false) }
    var addingMember by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val everyone by viewModel.everyone.collectAsState()

    val top: LazyListScope.() -> Unit = {
        state?.let { st ->
            contextHeaderItems(st, onPrepare = { preparing = true })
            item { SavedFilterRow { chip = it } }
            when (type) {
                ContextType.ORG -> orgItems(st, onEdit = { editing = true }, onOpenProject = { onOpenContext(ContextType.PROJECT, it) })
                ContextType.PROJECT -> projectItems(st, onEdit = { editing = true }, onAddMember = { addingMember = true },
                    onRemoveMember = { p -> scope.launch { viewModel.context.removeMember(id, p) } }, onOpenPerson = { onOpenContext(ContextType.PERSON, it) })
                else -> Unit
            }
        }
    }
    val bottom: LazyListScope.() -> Unit = {
        state?.let { st -> contextBottomItems(st, viewModel, onOpenMeeting, monthOnly) { monthOnly = !monthOnly } }
    }

    if (type == ContextType.PROJECT) ProjectScreen(viewModel, id, onNavigateBack, onOpenNote, onOpenMeeting, onRecordInto, extraTop = top, extraBottom = bottom)
    else WorkPersonScreen(viewModel, id, onNavigateBack, onOpenPerson = { onOpenContext(ContextType.PERSON, it) }, onOpenNote = onOpenNote, onOpenMeeting = onOpenMeeting, extraTop = top, extraBottom = bottom)

    if (preparing) PrepareSheet(PrepTarget.Entity(type, id), viewModel, onOpenMeeting, onDismiss = { preparing = false })
    chip?.let { f ->
        val answer by produceState<com.craftflowtechnologies.meetingmind.core.work.SavedAnswer?>(null, f) { value = viewModel.savedViews.answer(f, type to id) }
        SavedAnswerSheet(answer, f.label, viewModel, onOpenMeeting, onOpenPerson = { onOpenContext(ContextType.PERSON, it) }, onDismiss = { chip = null })
    }
    if (editing) state?.let { st ->
        if (type == ContextType.ORG && st.org != null) OrgDetailsDialog(st.org, onDismiss = { editing = false }) { domains, description, urls, props ->
            editing = false; scope.launch { viewModel.context.setOrgDetails(id, domains, description, urls, props) }
        }
        if (type == ContextType.PROJECT) ProjectDetailsDialog(st.projectProps.orEmpty(), onDismiss = { editing = false }) { status, start, end, props ->
            editing = false; scope.launch { viewModel.context.setProjectDetails(id, status, start, end, props) }
        }
    }
    if (addingMember) PickPersonDialog(everyone.map { it.id to it.name }, onDismiss = { addingMember = false }) { pid ->
        addingMember = false; scope.launch { viewModel.context.addMember(id, pid) }
    }
}

private fun LazyListScope.contextHeaderItems(st: ContextState, onPrepare: () -> Unit) {
    item(key = "ctx-header") {
        val h = st.header
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp).testTag("context_header")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Fig("${h.youOwe}", "you owe"); Fig("${h.theyOwe}", "they owe"); Fig("${h.open}", "open"); Fig("${h.decided}", "decided")
            }
            h.next?.let { Text("Next: $it" + (h.nextAt?.let { d -> " · " + Pulse.shortDate(d) } ?: ""), fontSize = 13.sp, color = InkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)) }
            Row(Modifier.padding(top = 12.dp)) { Pill("Prepare for conversation", filled = true, onClick = onPrepare) }
        }
    }
}

private fun LazyListScope.orgItems(st: ContextState, onEdit: () -> Unit, onOpenProject: (String) -> Unit) {
    val org = st.org ?: return
    val domains = ContextRepository.list(org.domainsJson); val urls = ContextRepository.list(org.urlsJson); val props = ContextRepository.map(org.propertiesJson)
    item(key = "ctx-org") {
        WorkSectionTitle("About", "Edit", onTrailing = onEdit)
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (org.description.isNotBlank()) Text(org.description, fontSize = 14.sp, color = Ink)
            (domains.map { "@$it" } + urls).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString("  ·  "), fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp)) }
            props.forEach { (k, v) -> Text("$k: $v", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp)) }
            if (org.description.isBlank() && domains.isEmpty() && urls.isEmpty() && props.isEmpty()) Text("Add a domain, a description or links.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.clickable(onClick = onEdit))
        }
    }
    if (st.projects.isNotEmpty()) {
        item(key = "ctx-projects") { WorkSectionTitle("Projects", "${st.projects.size}") }
        items(st.projects, key = { "cp-" + it.first }) { (pid, name) ->
            Text(name, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().clickable { onOpenProject(pid) }.padding(horizontal = 20.dp, vertical = 10.dp))
        }
    }
}

private fun LazyListScope.projectItems(st: ContextState, onEdit: () -> Unit, onAddMember: () -> Unit, onRemoveMember: (String) -> Unit, onOpenPerson: (String) -> Unit) {
    val json = runCatching { JSONObject(st.projectProps.orEmpty()) }.getOrDefault(JSONObject())
    val dates = listOf(json.optString("start"), json.optString("end")).filter { it.isNotBlank() }
    val custom = json.optJSONObject("custom")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } }.orEmpty()
    item(key = "ctx-project") {
        WorkSectionTitle("Details", "Edit", onTrailing = onEdit)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(listOfNotNull(json.optString("status").ifBlank { null }, dates.takeIf { it.isNotEmpty() }?.joinToString(" → ")).joinToString("  ·  ").ifBlank { "Add status and dates" },
                fontSize = 14.sp, color = if (json.optString("status").isBlank() && dates.isEmpty()) InkMuted else Ink, modifier = Modifier.clickable(onClick = onEdit))
            custom.forEach { (k, v) -> Text("$k: $v", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp)) }
        }
        WorkSectionTitle("Members", "Add", onTrailing = onAddMember)
    }
    if (st.members.isEmpty()) item(key = "ctx-nomembers") { EmptyLine("Nobody added yet.") }
    items(st.members, key = { "cm-" + it.first }) { (pid, name, role) ->
        Row(Modifier.fillMaxWidth().clickable { onOpenPerson(pid) }.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name + if (role.isNotBlank()) " · $role" else "", fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = { onRemoveMember(pid) }) { Text("Remove") }
        }
    }
}

private fun LazyListScope.contextBottomItems(st: ContextState, viewModel: WorkViewModel, onOpenMeeting: (String, Long?) -> Unit, monthOnly: Boolean, onToggleMonth: () -> Unit) {
    if (st.history.isNotEmpty()) {
        item(key = "ctx-history") { WorkSectionTitle("Decision history", "${st.history.size}") }
        items(st.history.take(12), key = { "ch-" + it.last().id }) { chain ->
            val latest = chain.last()
            val text = buildString {
                append(latest.text).append(" (").append(Pulse.shortDate(latest.createdAt)).append(')')
                chain.dropLast(1).asReversed().forEach { append(", replacing ").append(it.text).append(" (").append(Pulse.shortDate(it.createdAt)).append(')') }
            }
            HistoryLine(text, latest.id, viewModel, onOpenMeeting)
        }
    }
    if (st.risks.isNotEmpty()) {
        item(key = "ctx-risks") { WorkSectionTitle("Risks", "${st.risks.size}") }
        items(st.risks, key = { "cr-" + it.id }) { r -> HistoryLine(r.text, r.id, viewModel, onOpenMeeting) }
    }
    val entries = if (monthOnly) st.timeline.filter { it.at >= startOfMonth() } else st.timeline
    item(key = "ctx-timeline") { TimelineHeader(entries.size, monthOnly, onToggleMonth) }
    items(entries.take(60), key = { "tl-${it.at}-${it.text.hashCode()}-${it.itemId}" }) { e ->
        Row(Modifier.fillMaxWidth().clickable {
            e.meetingId?.let { m -> onOpenMeeting(m, null) }
        }.padding(horizontal = 20.dp, vertical = 7.dp)) {
            Text(Pulse.shortDate(e.at), fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
            Text(e.text, fontSize = 14.sp, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimelineHeader(count: Int, monthOnly: Boolean, onToggleMonth: () -> Unit) {
    WorkSectionTitle("Timeline", if (count == 0) null else "$count")
    Row(Modifier.padding(horizontal = 16.dp)) { Chip("This month", monthOnly, Ink, onClick = onToggleMonth) }
    if (count == 0) EmptyLine(if (monthOnly) "Nothing this month." else "Meetings and changes will build a timeline here.")
}

private fun startOfMonth(now: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

@Composable
private fun HistoryLine(text: String, itemId: String, viewModel: WorkViewModel, onOpenMeeting: (String, Long?) -> Unit) {
    val scope = rememberCoroutineScope()
    Text(text, fontSize = 15.sp, color = Ink, maxLines = 4, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable { scope.launch { viewModel.evidenceOf(itemId)?.let { (m, at) -> onOpenMeeting(m, at) } } }.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun Fig(value: String, label: String) {
    Column {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 11.sp, color = InkMuted)
    }
}

@Composable
private fun OrgDetailsDialog(org: PersonEntity, onDismiss: () -> Unit, onSave: (List<String>, String, List<String>, Map<String, String>) -> Unit) {
    var domains by remember { mutableStateOf(ContextRepository.list(org.domainsJson).joinToString(", ")) }
    var description by remember { mutableStateOf(org.description) }
    var urls by remember { mutableStateOf(ContextRepository.list(org.urlsJson).joinToString("\n")) }
    var props by remember { mutableStateOf(ContextRepository.map(org.propertiesJson).entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase, title = { Text("About ${org.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(domains, { domains = it }, label = { Text("Email domains (comma separated)") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2)
                OutlinedTextField(urls, { urls = it }, label = { Text("Links (one per line)") }, minLines = 2)
                OutlinedTextField(props, { props = it }, label = { Text("Properties (name: value, one per line)") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(domains.split(',', ' ').filter { it.isNotBlank() }, description, urls.lines(), parseProps(props)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ProjectDetailsDialog(propsJson: String, onDismiss: () -> Unit, onSave: (String, String, String, Map<String, String>) -> Unit) {
    val json = remember { runCatching { JSONObject(propsJson) }.getOrDefault(JSONObject()) }
    var status by remember { mutableStateOf(json.optString("status", "Active")) }
    var start by remember { mutableStateOf(json.optString("start")) }
    var end by remember { mutableStateOf(json.optString("end")) }
    var props by remember { mutableStateOf(json.optJSONObject("custom")?.let { o -> o.keys().asSequence().joinToString("\n") { "$it: ${o.optString(it)}" } }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase, title = { Text("Project details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(status, { status = it }, label = { Text("Status") }, singleLine = true)
                OutlinedTextField(start, { start = it }, label = { Text("Start (e.g. 2026-10-01)") }, singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("End") }, singleLine = true)
                OutlinedTextField(props, { props = it }, label = { Text("Properties (name: value, one per line)") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(status.trim(), start.trim(), end.trim(), parseProps(props)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PickPersonDialog(people: List<Pair<String, String>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase, title = { Text("Add a member") },
        text = { Column { people.take(30).forEach { (id, name) -> Text(name, fontSize = 16.sp, color = Ink, modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp)) } } },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun parseProps(text: String): Map<String, String> =
    text.lines().mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0].trim() to it[1].trim() } }.toMap()

/** The old person route: an organisation gets the organisation page, anyone else the person page. */
@Composable
fun PersonContextScreen(
    viewModel: WorkViewModel,
    personId: String,
    onNavigateBack: () -> Unit,
    onOpenContext: (ContextType, String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit
) {
    val type by produceState<ContextType?>(null, personId) {
        value = if (viewModel.people.get(personId)?.kind == com.craftflowtechnologies.meetingmind.core.work.PersonKind.ORG) ContextType.ORG else ContextType.PERSON
    }
    type?.let { ContextScreen(it, personId, viewModel, onNavigateBack, onOpenContext, onOpenNote, onOpenMeeting, onRecordInto = { _, _, _ -> }) }
}
