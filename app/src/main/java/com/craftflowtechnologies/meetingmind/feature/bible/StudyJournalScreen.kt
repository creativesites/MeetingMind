package com.craftflowtechnologies.meetingmind.feature.bible

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.biblestudy.StudyJournal
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.feature.work.noteTimeLabel
import com.craftflowtechnologies.meetingmind.feature.work.previewOf
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StudyJournalViewModel(application: Application) : AndroidViewModel(application) {
    val entries: StateFlow<List<Note>?> = StudyJournal(NoteRepository(application, MeetMindDatabase.getInstance(application)))
        .observe().stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** Every Bible-study journal entry, newest first. Each opens as an ordinary note. */
@Composable
fun StudyJournalScreen(viewModel: StudyJournalViewModel, onBack: () -> Unit, onOpenNote: (String) -> Unit) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val c = MM.colors
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().testTag("bible_journal")) {
        ScreenHeader(
            title = "My study journal", modifier = Modifier.padding(horizontal = MM.space.l),
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.ink) } }
        )
        val list = entries
        if (list != null && list.isEmpty()) {
            EmptyState("Nothing here yet", "When you write while studying a passage, it's kept here, with the passage.")
        } else if (list != null) {
            LazyColumn(contentPadding = PaddingValues(MM.space.l)) {
                item {
                    Text(
                        "${list.size} ${if (list.size == 1) "study" else "studies"} · every one linked to its passage",
                        style = MM.type.secondary, color = c.inkSecondary,
                        modifier = Modifier.padding(bottom = MM.space.m)
                    )
                }
                items(list, key = { it.id }) { n ->
                    NoteRow(
                        NoteRowModel(
                            title = n.title.ifBlank { "Bible study" }, preview = previewOf(n.plainText),
                            spaceColor = c.gold, timeLabel = noteTimeLabel(n.updatedAt, now)
                        ),
                        onClick = { onOpenNote(n.id) }
                    )
                }
            }
        }
    }
}
