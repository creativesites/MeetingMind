package com.craftflowtechnologies.meetingmind.feature.work.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.FilterChipRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.database.FindingRow
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.feature.work.WorkNoteItem
import com.craftflowtechnologies.meetingmind.feature.work.WorkPageViewModel
import com.craftflowtechnologies.meetingmind.feature.work.dayLabel
import com.craftflowtechnologies.meetingmind.feature.work.WorkTextField
import com.craftflowtechnologies.meetingmind.feature.work.WorkViewModel
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The kinds of result the Work search can narrow to. */
internal enum class WorkSearchScope(val label: String) { ALL("All of Work"), TASKS("Tasks"), DECISIONS("Decisions"), MEETINGS("Meetings & notes"), PEOPLE("People") }

private const val RESULTS_PER_KIND = 8
private const val SEARCH_DEBOUNCE_MS = 250L

/**
 * Work search (artifact #2f): scoped to Work. Results are grouped by kind (Promises, Decisions, In meetings & notes,
 * People). At the end: "Ask: <query>" opens the scoped Ask, and "Search everything" leaves Work for the app-wide search.
 * Tasks, decisions and people match on the lists Work already holds; meetings and notes use the notes full-text search.
 */
@Composable
internal fun WorkSearchPane(
    viewModel: WorkViewModel,
    page: WorkPageViewModel,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onAsk: (String) -> Unit,
    onSearchEverything: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var scopeChoice by rememberSaveable { mutableStateOf(WorkSearchScope.ALL) }
    var notes by remember { mutableStateOf<List<WorkNoteItem>>(emptyList()) }
    val tasks by viewModel.tasks.collectAsState()
    val decisions by viewModel.decisionLog.collectAsState()
    val people by viewModel.everyone.collectAsState()
    val scope = rememberCoroutineScope()
    val q = text.trim()

    LaunchedEffect(text) {
        delay(SEARCH_DEBOUNCE_MS)
        notes = page.searchNotes(q)
    }

    val promises = remember(q, tasks) {
        if (q.isEmpty()) emptyList<WorkTask>() else tasks.filter { !it.done && it.title.contains(q, ignoreCase = true) }.take(RESULTS_PER_KIND)
    }
    val matchingDecisions = remember(q, decisions) {
        if (q.isEmpty()) emptyList<FindingRow>() else decisions.filter { it.text.contains(q, ignoreCase = true) }.take(RESULTS_PER_KIND)
    }
    val matchingPeople = remember(q, people) {
        if (q.isEmpty()) emptyList<WorkPerson>() else people.filter { it.name.contains(q, ignoreCase = true) }.take(RESULTS_PER_KIND)
    }
    fun show(kind: WorkSearchScope) = scopeChoice == WorkSearchScope.ALL || scopeChoice == kind
    val nothing = q.isNotEmpty() && promises.isEmpty() && matchingDecisions.isEmpty() && matchingPeople.isEmpty() && notes.isEmpty()

    LazyColumn(
        modifier.fillMaxSize().testTag("work_search_list"),
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, bottom = MM.space.xxl),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        item(key = "field") {
            Row(Modifier.fillMaxWidth().padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search", tint = MM.colors.ink) }
                WorkTextField(
                    text, { text = it }, "Search Work", Icons.Rounded.Search,
                    modifier = Modifier.weight(1f).testTag("work_search_field"), imeAction = ImeAction.Search, clearLabel = "Clear search"
                )
            }
        }
        item(key = "scopes") {
            FilterChipRow(
                options = WorkSearchScope.entries,
                selected = setOf(scopeChoice),
                onToggle = { scopeChoice = it },
                label = { it.label }
            )
        }
        if (q.isEmpty()) item(key = "hint") {
            EmptyState(title = "Search your work", body = "Promises, decisions, meetings, notes and people, all in one place.")
        }
        if (nothing) item(key = "nothing") {
            EmptyState(title = "Nothing in Work matches “$q”", body = "Try another word, or search everything on the phone.")
        }
        if (show(WorkSearchScope.TASKS) && promises.isNotEmpty()) {
            item(key = "h-promises") { SectionHeader("Promises", count = promises.size) }
            items(promises, key = { "t-" + it.id }) { t ->
                TaskHit(t, onClick = { onOpenTask(t.id) })
            }
        }
        if (show(WorkSearchScope.DECISIONS) && matchingDecisions.isNotEmpty()) {
            item(key = "h-decisions") { SectionHeader("Decisions", count = matchingDecisions.size) }
            items(matchingDecisions, key = { "d-" + it.id }) { d ->
                ListRow(
                    d.text, subtitle = listOfNotNull(d.meetingTitle.takeIf { it.isNotBlank() }, dayLabel(d.createdAt)).joinToString(" · "),
                    onClick = {
                        scope.launch {
                            val evidence = viewModel.evidenceOf(d.id)
                            if (evidence != null) onOpenMeeting(evidence.first, evidence.second)
                        }
                    }
                )
            }
        }
        if (show(WorkSearchScope.MEETINGS) && notes.isNotEmpty()) {
            item(key = "h-notes") { SectionHeader("In meetings & notes", count = notes.size) }
            items(notes, key = { "n-" + it.id }) { n ->
                ListRow(n.displayTitle, subtitle = n.preview.takeIf { it.isNotBlank() }, onClick = { onOpenNote(n.id) })
            }
        }
        if (show(WorkSearchScope.PEOPLE) && matchingPeople.isNotEmpty()) {
            item(key = "h-people") { SectionHeader("People", count = matchingPeople.size) }
            items(matchingPeople, key = { "p-" + it.id }) { p ->
                ListRow(p.name, subtitle = p.role, onClick = { onOpenPerson(p.id) })
            }
        }
        if (q.isNotEmpty()) item(key = "fallbacks") {
            Column(Modifier.padding(top = MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                TextAction("Ask: $q", { onAsk(q) }, Modifier.heightIn(min = MMSize.minTouch))
                TextAction("Search everything, not only Work", onSearchEverything, Modifier.heightIn(min = MMSize.minTouch))
            }
        }
    }
}

/** A promise (an open task) in the results: its title, when it is due, and where it came from. */
@Composable
private fun TaskHit(t: WorkTask, onClick: () -> Unit) {
    val due = t.dueAt?.let { dayLabel(it) }
    val who = t.ownerName?.let { "from $it" }
    ListRow(t.title, subtitle = listOfNotNull(who, due).joinToString(" · ").ifEmpty { null }, onClick = onClick)
}
