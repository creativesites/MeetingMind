package com.craftflowtechnologies.meetingmind.feature.personal.seeds

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenTime
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/** The two tabs of the seed jar. */
enum class SeedTab { IDEAS, TO_SORT }

/** One idea in the jar: what the seed shows and what the card under the jar says. */
data class SeedIdeaUi(val id: String, val title: String, val createdAt: Long)

/**
 * One note in To sort. [kind] is the workflow's name ("Voice Memo"); [suggestion] is the primary
 * action when a keyword matched; [chipSpaces] are the other spaces the person uses, for chips.
 */
data class SeedSortUi(
    val id: String,
    val kind: String,
    val createdAt: Long,
    val title: String,
    val suggestion: Suggestion?,
    val chipSpaces: List<NotebookSpace>
)

/** Everything the seed jar screen draws. [selectedIdeaId] null means the newest idea. */
data class SeedJarUiState(
    val phase: GardenPhase = GardenPhase.DAY,
    val tab: SeedTab = SeedTab.IDEAS,
    val ideas: List<SeedIdeaUi> = emptyList(),
    val selectedIdeaId: String? = null,
    val toSort: List<SeedSortUi> = emptyList()
)

class SeedJarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SeedJarRepository(app, MeetMindDatabase.getInstance(app))
    private val identity = UserPreferencesManager(app).preferencesFlow.map { it.identity }

    /** Ticks each minute so the garden's sky follows the clock. */
    private val clock = flow {
        while (true) {
            emit(LocalTime.now())
            delay(60_000)
        }
    }

    val state: StateFlow<SeedJarUiState> = combine(repo.observeIdeas(), repo.observeToSort(), identity, clock) { ideas, sortable, who, now ->
        val untitled = app.getString(R.string.seeds_untitled)
        SeedJarUiState(
            phase = GardenTime.phase(now),
            ideas = ideas.map { SeedIdeaUi(it.id, SeedJarRepository.titleOf(it, untitled), it.createdAt) },
            toSort = sortable.map { note ->
                val suggestion = SeedSort.suggest(listOf(note.title, note.plainText).joinToString(" "), who.spaces)
                val target = (suggestion as? Suggestion.MoveTo)?.space
                SeedSortUi(
                    id = note.id,
                    kind = note.workflow.displayName,
                    createdAt = note.createdAt,
                    title = SeedJarRepository.titleOf(note, untitled),
                    suggestion = suggestion,
                    chipSpaces = otherSpaces(who, target)
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeedJarUiState())

    fun moveTo(noteId: String, space: NotebookSpace) = viewModelScope.launch { repo.moveTo(noteId, space) }

    fun keepHere(noteId: String) = viewModelScope.launch { repo.keepHere(noteId) }

    fun makeTask(noteId: String) = viewModelScope.launch { repo.makeTask(noteId) }

    /** The spaces to offer as chips: the person's spaces, minus Personal (the note is there) and the suggested one. */
    private fun otherSpaces(who: AppIdentity, suggested: NotebookSpace?): List<NotebookSpace> =
        NotebookSpace.entries.filter { it in who.spaces && it != NotebookSpace.PERSONAL && it != suggested }
}
