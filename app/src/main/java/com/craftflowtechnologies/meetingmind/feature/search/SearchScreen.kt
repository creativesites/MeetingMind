package com.craftflowtechnologies.meetingmind.feature.search

import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import android.app.Application
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiRecoveryBanner
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiThinkingSkeleton
import com.craftflowtechnologies.meetingmind.core.ui.mm.citedAnnotatedString
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.Success
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.domain.SearchMeetingsUseCase
import com.craftflowtechnologies.meetingmind.core.repository.SearchMatchType
import com.craftflowtechnologies.meetingmind.core.repository.SearchResultItem
import com.craftflowtechnologies.meetingmind.core.repository.SearchRepository
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineFaint
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.Speaker3
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchFilter(val label: String) { ALL("All"), NOTES("Notes"), RECORDINGS("Recordings"), TASKS("Tasks") }

sealed interface AskState {
    data object Idle : AskState
    data object Thinking : AskState
    data class Answered(val question: String, val answer: com.craftflowtechnologies.meetingmind.ai.assistant.AskAnswer) : AskState
    data class Failed(val message: String) : AskState
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val searchRepository = SearchRepository(database)
    private val searchUseCase = SearchMeetingsUseCase(searchRepository)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val results: StateFlow<List<SearchResultItem>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val filter = MutableStateFlow(SearchFilter.ALL)

    private val _ask = MutableStateFlow<AskState>(AskState.Idle)
    val ask: StateFlow<AskState> = _ask.asStateFlow()

    private var searchJob: Job? = null
    private var askJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (_ask.value !is AskState.Thinking) _ask.value = AskState.Idle

        if (newQuery.isBlank()) {
            _results.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(150)
            val passage = com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser.findAll(newQuery).firstOrNull()?.reference
            val scripture = passage?.let {
                SearchResultItem(
                    meetingId = "", meetingTitle = it.display(), meetingDate = 0L, matchSnippet = "Open in the Bible",
                    timestampMs = 0L, matchType = SearchMatchType.SCRIPTURE, relevanceScore = 1f, reference = it.passageId()
                )
            }
            _results.value = listOfNotNull(scripture) + searchUseCase(newQuery)
            _isSearching.value = false
        }
    }

    fun clearQuery() {
        onQueryChange("")
    }

    /** Ask across everything: search finds the sources on the phone, the model answers from them. */
    fun askEverything() {
        val q = _query.value.trim().ifEmpty { return }
        askJob?.cancel()
        _ask.value = AskState.Thinking
        askJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val engine = com.craftflowtechnologies.meetingmind.ai.assistant.AskEverythingEngine(
                com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(app),
                search = { sources(it) },
                dateLabel = { Formatters.formatDateRelative(it) }
            )
            _ask.value = when (val r = engine.ask(q)) {
                is com.craftflowtechnologies.meetingmind.ai.common.AiResult.Success -> AskState.Answered(q, r.value)
                else -> AskState.Failed(r.describeFailure() ?: "Ask didn't work this time.")
            }
        }
    }

    fun dismissAnswer() { askJob?.cancel(); _ask.value = AskState.Idle }

    fun cancelAsk() { askJob?.cancel(); _ask.value = AskState.Idle }

    private suspend fun sources(query: String): List<com.craftflowtechnologies.meetingmind.ai.assistant.AskSource> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val fts = com.craftflowtechnologies.meetingmind.core.scripture.BibleStore.ftsQuery(query) ?: return@withContext emptyList()
        val out = mutableListOf<com.craftflowtechnologies.meetingmind.ai.assistant.AskSource>()
        val terms = com.craftflowtechnologies.meetingmind.ai.assistant.AskEverything.terms(query).ifEmpty { listOf(query) }
        runCatching { database.searchDao().notes(fts, 6) }.getOrDefault(emptyList()).forEach { hit ->
            val note = database.noteDao().getById(hit.id) ?: return@forEach
            if (note.isPrivate) return@forEach
            val excerpt = terms.firstNotNullOfOrNull { SearchRepository.snippetAround(note.plainText, it, 700) } ?: note.plainText.take(700)
            out += com.craftflowtechnologies.meetingmind.ai.assistant.AskSource(
                0, com.craftflowtechnologies.meetingmind.ai.assistant.SourceKind.NOTE, note.title.ifBlank { "Untitled note" }, excerpt,
                date = note.eventDate ?: note.createdAt, noteId = note.id, faith = com.craftflowtechnologies.meetingmind.ai.faith.AskSermon.isFaith(runCatching { com.craftflowtechnologies.meetingmind.core.model.RecordingType.valueOf(note.workflow) }.getOrNull())
            )
        }
        runCatching { database.searchDao().segments(fts, 8) }.getOrDefault(emptyList()).forEach { hit ->
            val seg = database.transcriptDao().getSegmentById(hit.id) ?: return@forEach
            val type = runCatching { com.craftflowtechnologies.meetingmind.core.model.RecordingType.valueOf(hit.recordingType) }.getOrNull()
            out += com.craftflowtechnologies.meetingmind.ai.assistant.AskSource(
                0, com.craftflowtechnologies.meetingmind.ai.assistant.SourceKind.RECORDING, "${hit.meetingTitle} at ${Formatters.formatDurationHms(hit.startMs)}",
                listOfNotNull(hit.speakerName?.let { "$it:" }, seg.cleanedText ?: seg.text).joinToString(" "),
                meetingId = hit.meetingId, startMs = hit.startMs, faith = com.craftflowtechnologies.meetingmind.ai.faith.AskSermon.isFaith(type)
            )
        }
        database.taskDao().exportAll().filter { it.deletedAt == null && terms.any { t -> (it.title + " " + it.notes).contains(t, ignoreCase = true) } }.take(4).forEach { t ->
            out += com.craftflowtechnologies.meetingmind.ai.assistant.AskSource(
                0, com.craftflowtechnologies.meetingmind.ai.assistant.SourceKind.TASK, t.title,
                listOfNotNull(if (t.doneAt != null) "Done" else "Open", t.dueAt?.let { "due " + Formatters.formatDateRelative(it) }, t.notes.takeIf { it.isNotBlank() }).joinToString(" · "),
                taskId = t.id, noteId = t.noteId
            )
        }
        out
    }
}

/**
 * Search everything on the phone — notes, recordings (full-text, any word order, word prefixes),
 * tasks, and a Bible passage when the query names one — and Ask: a grounded answer across all of
 * it, each claim numbered to the note or moment it came from (Faith spec slice F).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMeeting: (meetingId: String, startAtMs: Long?) -> Unit,
    onNavigateToNote: (noteId: String) -> Unit = {},
    onNavigateBottomNav: (com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit = {},
    onOpenPassage: (String) -> Unit = {},
    onOpenTasks: () -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val ask by viewModel.ask.collectAsState()

    val suggestions = listOf(
        "What did the sermon say about grace?",
        "Romans 8",
        "Follow up",
        "Decisions this month",
        "Prayer for family"
    )
    val shown = results.filter { r ->
        when (filter) {
            SearchFilter.ALL -> true
            SearchFilter.NOTES -> r.matchType == SearchMatchType.NOTE
            SearchFilter.RECORDINGS -> r.matchType == SearchMatchType.KEYWORD_TRANSCRIPT || r.matchType == SearchMatchType.SEMANTIC_VECTOR
            SearchFilter.TASKS -> r.matchType == SearchMatchType.TASK
        }
    }
    val open: (SearchResultItem) -> Unit = { item ->
        when {
            item.matchType == SearchMatchType.SCRIPTURE -> item.reference?.let(onOpenPassage)
            item.matchType == SearchMatchType.TASK -> onOpenTasks()
            item.noteId != null -> onNavigateToNote(item.noteId)
            else -> onNavigateToMeeting(item.meetingId, item.timestampMs)
        }
    }

    Scaffold(
        containerColor = SurfaceBase,
        bottomBar = {
            com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar(
                // With Work in the fourth slot, Search is reached from the top of Home and no tab is current.
                current = if (com.craftflowtechnologies.meetingmind.core.ui.LocalTabSlot.current == com.craftflowtechnologies.meetingmind.core.work.TabSlot.WORK)
                    com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.NONE else com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SEARCH,
                onNavigate = onNavigateBottomNav,
                showNewAction = true
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("search_back_btn").size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = InkSecondary)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = { Text("Search or ask anything…", color = InkFaint, fontSize = 14.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { viewModel.clearQuery() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = InkMuted)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (looksLikeQuestion(query)) viewModel.askEverything() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = LineSoft, cursorColor = Accent,
                        focusedTextColor = Ink, unfocusedTextColor = Ink
                    ),
                    modifier = Modifier.weight(1f).testTag("search_input_field")
                )
            }

            if (query.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    AskChip(ask is AskState.Thinking) { viewModel.askEverything() }
                    SearchFilter.entries.forEach { f ->
                        val isSelected = f == filter
                        androidx.compose.material3.Surface(
                            onClick = { viewModel.filter.value = f },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) Ink else SurfaceSunk,
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                }
                        ) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(f.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) OnInk else InkSecondary)
                            }
                        }
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().animateContentSize(), contentPadding = PaddingValues(bottom = 22.dp)) {
                when (val a = ask) {
                    AskState.Idle -> Unit
                    AskState.Thinking -> item { AskThinking(onStop = viewModel::cancelAsk) }
                    is AskState.Failed -> item { AskFailed(a.message, onRetry = viewModel::askEverything, onClose = viewModel::dismissAnswer) }
                    is AskState.Answered -> item {
                        AnswerCard(a, onClose = viewModel::dismissAnswer, onOpen = { src ->
                            when {
                                src.meetingId != null -> onNavigateToMeeting(src.meetingId, src.startMs)
                                src.noteId != null -> onNavigateToNote(src.noteId)
                                src.taskId != null -> onOpenTasks()
                            }
                        })
                    }
                }
                if (query.isBlank()) {
                    item {
                        Column(modifier = Modifier.padding(top = 26.dp, start = 22.dp, end = 22.dp)) {
                            Text("SEARCH AND ASK", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = InkMuted)
                            Text(
                                text = "Finds words in your notes, recordings and tasks on this phone, and Bible passages by reference. Ask a question and the answer shows where each part came from.",
                                fontSize = 13.5.sp, color = InkSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp)
                            )
                            Text("Try", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = InkMuted, modifier = Modifier.padding(top = 22.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                                suggestions.forEach { sug ->
                                    androidx.compose.material3.Surface(
                                        onClick = { viewModel.onQueryChange(sug) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = LineSoft,
                                        modifier = Modifier.heightIn(min = 40.dp).semantics { role = Role.Button }
                                    ) {
                                        Box(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                            Text(sug, fontSize = 13.sp, color = InkSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (shown.isEmpty() && !isSearching) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 22.dp, end = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No exact matches", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(
                                "Try other words — or tap Ask, which searches with related words too.",
                                fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            if (isSearching) "Searching this phone…" else "${shown.size} matches",
                            fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 2.dp)
                        )
                    }
                    items(shown) { item ->
                        Column {
                            SearchResultRow(item = item, onClick = { open(item) })
                            HorizontalDivider(color = LineFaint, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
                        }
                    }
                }
            }
        }
    }
}

internal fun looksLikeQuestion(q: String): Boolean {
    val t = q.trim().lowercase()
    return t.endsWith("?") || Regex("^(what|when|where|who|why|how|did|do|does|is|are|was|were|which|can|should|summari[sz]e|list|find)\\b").containsMatchIn(t)
}

@Composable
private fun AskChip(busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 38.dp)
            .alpha(if (busy) 0.6f else 1f)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Accent, Speaker3)))
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics {
                contentDescription = if (busy) "Asking AI" else "Ask AI"
                stateDescription = if (busy) "Working" else "Ready"
            }
            .testTag("search_ask"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, null, tint = com.craftflowtechnologies.meetingmind.ui.theme.OnAccent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (busy) "Asking…" else "Ask", color = com.craftflowtechnologies.meetingmind.ui.theme.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AskThinking(onStop: () -> Unit) {
    AiThinkingSkeleton(
        label = "Looking through your notes and recordings…",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        onStop = onStop
    )
}

@Composable
private fun AskFailed(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    AiRecoveryBanner(
        message = message,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        onRetry = onRetry,
        onDismiss = onClose
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerCard(a: AskState.Answered, onClose: () -> Unit, onOpen: (com.craftflowtechnologies.meetingmind.ai.assistant.AskSource) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceRaised)
            .border(1.dp, LineSoft, RoundedCornerShape(18.dp)).padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 18.dp)
            .animateContentSize()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("search_answer")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, null, tint = Accent, modifier = Modifier.size(16.dp))
            Text("Answer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = Accent, modifier = Modifier.padding(start = 8.dp).weight(1f))
            AiCopyButton(text = a.answer.text)
            IconButton(
                onClick = onClose,
                modifier = Modifier.sizeIn(minWidth = MMSize.minTouch, minHeight = MMSize.minTouch)
            ) { Icon(Icons.Default.Close, "Close answer", tint = InkMuted, modifier = Modifier.size(18.dp)) }
        }
        Text(citedAnnotatedString(a.answer.text, Accent), fontSize = 15.sp, color = Ink, lineHeight = 23.sp, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
        if (a.answer.unverified) {
            Text("No source backs this answer — check it before relying on it.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
        }
        if (a.answer.cited.isNotEmpty()) {
            Text("SOURCES", fontSize = 10.5.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            a.answer.cited.forEach { src ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).padding(end = 8.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { onOpen(src) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(AccentWash), contentAlignment = Alignment.Center) {
                        Text("${src.key}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(src.title, fontSize = 13.5.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(src.kind.label + (src.date?.let { " · " + Formatters.formatDateRelative(it) } ?: ""), fontSize = 11.5.sp, color = InkMuted)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = InkFaint, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: SearchResultItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 15.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.meetingTitle, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (item.matchType) {
                    SearchMatchType.SEMANTIC_VECTOR -> "Related"
                    SearchMatchType.KEYWORD_TRANSCRIPT -> "Recording"
                    SearchMatchType.NOTE -> "Note"
                    SearchMatchType.SCRIPTURE -> "Bible"
                    SearchMatchType.TASK -> "Task"
                },
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = when (item.matchType) {
                    SearchMatchType.SEMANTIC_VECTOR -> Speaker3
                    SearchMatchType.SCRIPTURE -> FaithGold
                    SearchMatchType.TASK -> Success
                    else -> Accent
                },
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        if (item.matchType == SearchMatchType.KEYWORD_TRANSCRIPT || item.matchType == SearchMatchType.SEMANTIC_VECTOR) {
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = Formatters.formatDurationHms(item.timestampMs), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Accent, fontWeight = FontWeight.SemiBold)
                if (item.speakerName != null) Text(" · ${item.speakerName}", fontSize = 12.sp, color = InkMuted, fontWeight = FontWeight.Medium)
            }
        }
        if (item.matchSnippet.isNotBlank()) {
            Text(
                text = if (item.matchType == SearchMatchType.KEYWORD_TRANSCRIPT) "“${item.matchSnippet}”" else item.matchSnippet,
                fontSize = 13.5.sp, color = InkSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (item.matchType != SearchMatchType.SCRIPTURE) {
            Text(
                text = listOf(
                    if (item.matchType == SearchMatchType.TASK) "Task" else item.recordingType.displayName,
                    Formatters.formatDateRelative(item.meetingDate)
                ).joinToString(" · "),
                fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
