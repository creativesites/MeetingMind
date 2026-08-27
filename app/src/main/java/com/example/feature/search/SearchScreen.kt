package com.example.feature.search

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.domain.SearchMeetingsUseCase
import com.example.core.repository.SearchMatchType
import com.example.core.repository.SearchResultItem
import com.example.core.repository.SearchRepository
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineFaint
import com.example.ui.theme.LineSoft
import com.example.ui.theme.Speaker3
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _results.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(150)
            val matched = searchUseCase(newQuery)
            _results.value = matched
            _isSearching.value = false
        }
    }

    fun clearQuery() {
        onQueryChange("")
    }
}

/**
 * Search (Phase 15 §Part 2 / design `#5b`) — restyled onto the Ink/Accent flat-row token system.
 * `#5b`'s mockup additionally shows a grounded "Answer" block synthesized across every recording,
 * with inline citation chips, above the literal keyword/vector matches — that is a genuinely new
 * capability (cross-recording RAG synthesis), not a visual change to this existing hybrid search,
 * so it's not built here; this pass keeps every real feature this screen already had (debounced
 * hybrid keyword+semantic search, match-type badges, tap-to-open at the matched timestamp,
 * suggestion chips) and only restyles it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMeeting: (meetingId: String, startAtMs: Long?) -> Unit,
    onNavigateBottomNav: (com.example.core.ui.BottomNavDestination) -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val suggestions = listOf(
        "Quarterly budget",
        "Product launch",
        "Frontend refactor",
        "Sprint deadline",
        "Agreed decisions"
    )

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            com.example.core.ui.AppBottomNavigationBar(
                current = com.example.core.ui.BottomNavDestination.SEARCH,
                onNavigate = onNavigateBottomNav
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("search_back_btn").size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = InkSecondary)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = { Text("Search by topic, speaker, decision, or keyword…", color = InkFaint, fontSize = 14.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { viewModel.clearQuery() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = InkMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = LineSoft,
                        cursorColor = Accent,
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input_field")
                )
            }

            if (isSearching || (query.isNotBlank() && results.isNotEmpty())) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearching) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Accent)
                            Text("Searching this phone…", fontSize = 12.5.sp, color = InkMuted)
                        }
                    } else {
                        Text("${results.size} matches", fontSize = 12.5.sp, color = InkMuted)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 22.dp)
            ) {
                if (query.isBlank()) {
                    item {
                        Column(modifier = Modifier.padding(top = 26.dp, start = 22.dp, end = 22.dp)) {
                            Text("HYBRID SEARCH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = InkMuted)
                            Text(
                                text = "Searches both exact transcript words and semantic embeddings across your local recordings. No internet required.",
                                fontSize = 13.5.sp,
                                color = InkSecondary,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = "Try searching for",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp,
                                color = InkMuted,
                                modifier = Modifier.padding(top = 22.dp)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                suggestions.forEach { sug ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(LineSoft)
                                            .clickable { viewModel.onQueryChange(sug) }
                                            .padding(horizontal = 13.dp, vertical = 9.dp)
                                    ) {
                                        Text(sug, fontSize = 13.sp, color = InkSecondary)
                                    }
                                }
                            }
                        }
                    }
                } else if (results.isEmpty() && !isSearching) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 60.dp, start = 22.dp, end = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No matching moments found", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(
                                "Try a broader question or different wording.",
                                fontSize = 13.sp,
                                color = InkMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    items(results) { item ->
                        Column {
                            SearchResultRow(item = item, onClick = { onNavigateToMeeting(item.meetingId, item.timestampMs) })
                            HorizontalDivider(color = LineFaint, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
                        }
                    }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.meetingTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (item.matchType) {
                    SearchMatchType.SEMANTIC_VECTOR -> "Vector"
                    SearchMatchType.KEYWORD_TRANSCRIPT -> "Keyword"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (item.matchType == SearchMatchType.SEMANTIC_VECTOR) Speaker3 else Accent,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = Formatters.formatDurationHms(item.timestampMs), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Accent, fontWeight = FontWeight.SemiBold)
            if (item.speakerName != null) {
                Text(" · ${item.speakerName}", fontSize = 12.sp, color = InkMuted, fontWeight = FontWeight.Medium)
            }
        }

        Text(
            text = "“${item.matchSnippet}”",
            fontSize = 13.5.sp,
            color = InkSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.recordingType.displayName} · ${Formatters.formatDateRelative(item.meetingDate)}",
                fontSize = 12.sp,
                color = InkMuted
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    // "Recording" reads better than "General" as a verb-phrase object —
                    // every other type's own name (Meeting, Interview, ...) already works here.
                    text = "Open " + if (item.recordingType == com.example.core.model.RecordingType.GENERAL) "Recording" else item.recordingType.displayName,
                    fontSize = 12.5.sp,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Accent, modifier = Modifier.size(13.dp))
            }
        }
    }
}
