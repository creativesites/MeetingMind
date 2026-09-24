package com.example.feature.prayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.cloud.GeminiCredentialStore
import com.example.ai.live.GeminiLiveVoice
import com.example.ai.live.LiveVoiceEvent
import com.example.ai.live.LiveVoiceState
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.devotional.DevotionalRepository
import com.example.core.devotional.LocalDay
import com.example.core.model.NoteStatus
import com.example.core.model.ProcessingProfile
import com.example.core.model.RecordingType
import com.example.core.prayer.PrayMode
import com.example.core.prayer.PraySetup
import com.example.core.prayer.PrayerCompanion
import com.example.core.repository.NoteCodec
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One line of the conversation: who, and what. */
data class PrayLine(val mine: Boolean, val text: String)

data class PrayUi(
    /** Null until checked; false when Internet mode or a key is missing. */
    val available: Boolean? = null,
    val requests: List<Pair<String, String>> = emptyList(),
    val devotional: String? = null,
    val devotionalPrayer: String? = null,
    val started: Boolean = false,
    val state: LiveVoiceState = LiveVoiceState.CONNECTING,
    val lines: List<PrayLine> = emptyList(),
    val error: String? = null,
    val savedNoteId: String? = null,
    val muted: Boolean = false
)

class PrayWithMeViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(PrayUi())
    val ui: StateFlow<PrayUi> = _ui.asStateFlow()
    private var session: GeminiLiveVoice? = null
    private var jobs = mutableListOf<Job>()
    val level: StateFlow<Float> get() = session?.level ?: MutableStateFlow(0f)
    private var current: PraySetup? = null

    init {
        viewModelScope.launch {
            val prefs = UserPreferencesManager(app).preferencesFlow.first()
            val key = GeminiCredentialStore(app).getApiKey()
            val db = MeetMindDatabase.getInstance(app)
            val requests = runCatching {
                db.noteDao().getUpdatedSince(0, 400).map { with(NoteCodec) { it.toDomain() } }
                    .filter { it.workflow == RecordingType.PRAYER_REQUEST && it.status == NoteStatus.OPEN && it.archivedAt == null }
                    .map { it.id to it.title.ifBlank { "A prayer request" } }
            }.getOrDefault(emptyList())
            val today = runCatching { DevotionalRepository(app).find(LocalDay.today())?.devotional }.getOrNull()
            _ui.value = _ui.value.copy(
                available = prefs.processingProfile == ProcessingProfile.INTERNET && key != null,
                requests = requests,
                devotional = today?.let { d -> listOfNotNull(d.title, d.scripture.firstOrNull()?.display(), d.reflection.joinToString(" "), d.question).joinToString("\n") },
                devotionalPrayer = today?.prayer
            )
        }
    }

    fun start(setup: PraySetup, voice: String) {
        if (session != null) return
        viewModelScope.launch {
            val key = GeminiCredentialStore(getApplication()).getApiKey() ?: run { _ui.value = _ui.value.copy(error = "Add your Gemini key in Settings first."); return@launch }
            val name = UserPreferencesManager(getApplication()).preferencesFlow.first().identity.displayName?.substringBefore(' ')
            val full = setup.copy(name = name)
            current = full
            val live = GeminiLiveVoice(key, GeminiLiveVoice.setupMessage(PrayerCompanion.systemInstruction(full), voice))
            session = live
            _ui.value = _ui.value.copy(started = true, lines = emptyList(), error = null, savedNoteId = null)
            jobs += launch { live.state.collect { s -> _ui.value = _ui.value.copy(state = s) } }
            jobs += launch {
                live.events.collect { e ->
                    when (e) {
                        LiveVoiceEvent.Ready -> live.say(PrayerCompanion.opening(full))
                        is LiveVoiceEvent.Heard -> append(true, e.text)
                        is LiveVoiceEvent.Said -> append(false, e.text)
                        is LiveVoiceEvent.Failed -> _ui.value = _ui.value.copy(error = e.message)
                        else -> Unit
                    }
                }
            }
            live.start()
        }
    }

    /** Joins streamed transcript pieces into lines, one per speaker turn. */
    private fun append(mine: Boolean, piece: String) {
        val lines = _ui.value.lines.toMutableList()
        val last = lines.lastOrNull()
        if (last != null && last.mine == mine) lines[lines.size - 1] = last.copy(text = (last.text + piece).replace(Regex("\\s+"), " "))
        else lines += PrayLine(mine, piece.trim())
        _ui.value = _ui.value.copy(lines = lines)
    }

    fun say(text: String) { if (text.isNotBlank()) { append(true, text.trim()); session?.say(text.trim()) } }

    fun toggleMute() {
        val m = !_ui.value.muted
        session?.setMuted(m)
        _ui.value = _ui.value.copy(muted = m)
    }

    fun end() {
        session?.end()
        session = null
        jobs.forEach { it.cancel() }; jobs.clear()
        _ui.value = _ui.value.copy(state = LiveVoiceState.ENDED)
    }

    /** Keeps the prayer as a Prayer note, if the person wants to. */
    fun save() = viewModelScope.launch {
        val lines = _ui.value.lines.ifEmpty { return@launch }
        val notes = NoteRepository(getApplication(), MeetMindDatabase.getInstance(getApplication()))
        val mode = current?.mode ?: PrayMode.TOGETHER
        val workflow = if (mode == PrayMode.TALK_IT_THROUGH) RecordingType.REFLECTION else RecordingType.PRAYER
        val note = notes.createNote(workflow = workflow, title = if (mode == PrayMode.TALK_IT_THROUGH) "Talking it through" else "Prayed with a companion", useTemplate = false)
        val blocks = lines.map { l ->
            com.example.core.model.NoteBlock(
                NoteRepository.newId("block"), note.id, 0, com.example.core.model.NoteBlockType.PARAGRAPH,
                com.example.core.notes.RichText.plain((if (l.mine) "Me: " else "Companion: ") + l.text)
            )
        }
        notes.saveBlocks(note.id, blocks)
        _ui.value = _ui.value.copy(savedNoteId = note.id)
    }

    fun reset() { _ui.value = _ui.value.copy(started = false, lines = emptyList(), error = null, savedNoteId = null, muted = false) }

    override fun onCleared() { end(); super.onCleared() }
}
