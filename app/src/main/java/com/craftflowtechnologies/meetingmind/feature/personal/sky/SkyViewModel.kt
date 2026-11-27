package com.craftflowtechnologies.meetingmind.feature.personal.sky

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.feature.personal.PersonalViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** The reflections the sky draws, as a stream. Room in the app; a fake in tests. */
fun interface ReflectionSource {
    fun observe(): Flow<List<ReflectionEntry>>
}

/** Reflections from Room: Journal, Reflection and Gratitude notes with their tags. Stays on the phone. */
class RoomReflectionSource(app: Application) : ReflectionSource {
    private val db = MeetMindDatabase.getInstance(app)
    private val notes = NoteRepository(app, db)

    override fun observe(): Flow<List<ReflectionEntry>> =
        notes.observeNotesForWorkflows(PersonalViewModel.REFLECTIONS).map { list ->
            val tags = db.noteDao().getAllNoteTags().groupBy({ it.noteId }, { it.name })
            list.map { note ->
                ReflectionEntry(
                    id = note.id,
                    text = listOf(note.title, note.plainText).filter { it.isNotBlank() }.joinToString(" "),
                    createdAt = note.createdAt,
                    tags = tags[note.id].orEmpty()
                )
            }
        }
}

/** Zuri's read for the selected constellation: idle, being written, written, needs Setup, or failed. */
enum class ZuriPhase { IDLE, READING, WRITTEN, NEEDS_SETUP, FAILED }

@Immutable
data class ZuriState(
    val name: String? = null,
    val phase: ZuriPhase = ZuriPhase.IDLE,
    val text: String = ""
)

/** Everything the sky shows for one month. */
@Immutable
data class SkyUiState(
    val loaded: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    /** The month's reflections, oldest first. */
    val entries: List<ReflectionEntry> = emptyList(),
    val constellations: List<Constellation> = emptyList(),
    /** The constellation shown in the card: the chosen one, else the strongest. */
    val selected: Constellation? = null,
    /** The selected constellation's reflections, oldest first. */
    val selectedEntries: List<ReflectionEntry> = emptyList(),
    val patternLines: List<String> = emptyList(),
    val zuri: ZuriState = ZuriState(),
    /** Whether Zuri's read can run on this phone. NONE means the on-device model is not installed. */
    val route: SkyRoute = SkyRoute.NONE,
    /** Whether this person may ask for Zuri's read. When false the locked card shows instead. */
    val zuriAllowed: Boolean = false
)

/**
 * The sky's state: the month's reflections, their constellations and the chosen one, and Zuri's
 * read. Everything runs on this phone. Reflections are private notes and never leave the device.
 */
class SkyViewModel(
    app: Application,
    private val source: ReflectionSource = RoomReflectionSource(app),
    private val reader: SkyReader = ModelSkyReader(DeviceSkyModel(app)),
    private val access: SkyAccess = SkyAccess.gate(app),
    private val zone: ZoneId = ZoneId.systemDefault(),
    initialMonth: YearMonth = YearMonth.now(zone)
) : AndroidViewModel(app) {

    /** Where the read can run, and whether the person may ask for it. */
    private data class Side(val route: SkyRoute, val allowed: Boolean)

    private val month = MutableStateFlow(initialMonth)
    private val selectedName = MutableStateFlow<String?>(null)
    private val zuri = MutableStateFlow(ZuriState())
    private val side = MutableStateFlow(Side(SkyRoute.NONE, access.allowsZuri()))

    val state: StateFlow<SkyUiState> = combine(source.observe(), month, selectedName, zuri, side) { all, ym, sel, z, s ->
        build(all, ym, sel, z, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SkyUiState(month = initialMonth))

    init {
        viewModelScope.launch { side.value = side.value.copy(route = reader.route()) }
    }

    fun select(name: String) {
        selectedName.value = name
    }

    fun previousMonth() = moveMonth(-1)

    fun nextMonth() = moveMonth(1)

    private fun moveMonth(by: Long) {
        month.value = month.value.plusMonths(by)
        selectedName.value = null
    }

    /**
     * Asks for Zuri's read across the selected constellation. Refused, without touching the model,
     * unless [access] allows it. Returns whether the read started.
     */
    fun requestZuri(): Boolean {
        val current = state.value
        val constellation = current.selected
        val allowedNow = access.allowsZuri()
        side.value = side.value.copy(allowed = allowedNow)
        if (!allowedNow || constellation == null) return false
        if (zuri.value.phase == ZuriPhase.READING) return true

        val texts = SkyReadInput.select(current.selectedEntries)
        zuri.value = ZuriState(name = constellation.name, phase = ZuriPhase.READING)
        viewModelScope.launch {
            zuri.value = when (val outcome = reader.read(constellation.name, texts)) {
                is SkyReadOutcome.Written -> ZuriState(constellation.name, ZuriPhase.WRITTEN, outcome.text)
                SkyReadOutcome.NoModel -> ZuriState(constellation.name, ZuriPhase.NEEDS_SETUP)
                is SkyReadOutcome.Failed -> ZuriState(constellation.name, ZuriPhase.FAILED, outcome.line)
            }
        }
        return true
    }

    private fun build(
        all: List<ReflectionEntry>,
        ym: YearMonth,
        sel: String?,
        z: ZuriState,
        s: Side
    ): SkyUiState {
        val inMonth = all
            .filter { YearMonth.from(Instant.ofEpochMilli(it.createdAt).atZone(zone)) == ym }
            .sortedBy { it.createdAt }
        val constellations = ReflectionThemes.find(inMonth)
        val selected = constellations.firstOrNull { it.name == sel } ?: constellations.firstOrNull()
        val byId = inMonth.associateBy { it.id }
        val selectedEntries = selected?.entryIds?.mapNotNull { byId[it] }.orEmpty()
        return SkyUiState(
            loaded = true,
            month = ym,
            entries = inMonth,
            constellations = constellations,
            selected = selected,
            selectedEntries = selectedEntries,
            patternLines = selected?.let { SkyPatterns.lines(it.name, selectedEntries.map { e -> e.createdAt }, zone) }.orEmpty(),
            zuri = if (selected != null && z.name == selected.name) z else ZuriState(),
            route = s.route,
            zuriAllowed = s.allowed
        )
    }

    companion object {
        /** Factory for the screen: the Application is the only thing the view model needs from the framework. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SkyViewModel(checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])) }
        }
    }
}
