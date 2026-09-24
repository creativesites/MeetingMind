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

object PrayLines {
    /**
     * Adds a streamed transcript piece. Pieces arrive a word or so at a time, spaces included
     * (sometimes a piece is only a space); they're joined as they come and tidied, and a change of
     * speaker starts a new line.
     */
    fun append(lines: List<PrayLine>, mine: Boolean, piece: String): List<PrayLine> {
        if (piece.isEmpty()) return lines
        val last = lines.lastOrNull()
        return if (last != null && last.mine == mine) lines.dropLast(1) + last.copy(text = tidy(last.text + piece))
        else if (piece.isBlank()) lines
        else lines + PrayLine(mine, tidy(piece).trimStart())
    }

    private fun tidy(s: String) = s.replace(Regex("[ \\t]+"), " ").replace(Regex(" ([,.!?;:])"), "$1")
}

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
    val muted: Boolean = false,
    /** While connecting: where it's got to. */
    val stage: String = "",
    /** When the conversation began (for the timer). */
    val startedAt: Long = 0,
    /** Singing together right now (from "Sing with me" until the song's turn ends). */
    val singing: Boolean = false,
    val mode: PrayMode = PrayMode.TOGETHER,
    /** The hymn picked at the start, offered again by "Sing". */
    val song: String? = null
)

class PrayWithMeViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(PrayUi())
    val ui: StateFlow<PrayUi> = _ui.asStateFlow()
    private var session: GeminiLiveVoice? = null
    private var jobs = mutableListOf<Job>()
    // Mirrored from the session so the screen can collect them before the session exists.
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private val _userLevel = MutableStateFlow(0f)
    val userLevel: StateFlow<Float> = _userLevel.asStateFlow()
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
                // Live prayer is online by nature and chosen here, so a key is enough; recordings can stay offline.
                available = key != null,
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
            val live = GeminiLiveVoice(key, GeminiLiveVoice.setupMessage(PrayerCompanion.systemInstruction(full), voice), getApplication())
            session = live
            _ui.value = _ui.value.copy(started = true, lines = emptyList(), error = null, savedNoteId = null, startedAt = System.currentTimeMillis(),
                mode = full.mode, song = full.worship, singing = full.worship != null)
            jobs += launch { live.level.collect { _level.value = it } }
            jobs += launch { live.userLevel.collect { _userLevel.value = it } }
            jobs += launch { live.state.collect { s -> _ui.value = _ui.value.copy(state = s) } }
            jobs += launch { live.stage.collect { s -> _ui.value = _ui.value.copy(stage = s) } }
            jobs += launch {
                live.events.collect { e ->
                    when (e) {
                        LiveVoiceEvent.Ready -> live.say(PrayerCompanion.opening(full))
                        is LiveVoiceEvent.Heard -> append(true, e.text)
                        is LiveVoiceEvent.Said -> append(false, e.text)
                        LiveVoiceEvent.TurnDone -> if (_ui.value.singing && _ui.value.lines.lastOrNull()?.mine == false) _ui.value = _ui.value.copy(singing = false)
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
        _ui.value = _ui.value.copy(lines = PrayLines.append(_ui.value.lines, mine, piece))
    }

    /** Typed words: shown as the person's own line, and sent as their turn. */
    fun say(text: String) {
        val t = text.trim().takeIf { it.isNotEmpty() } ?: return
        _ui.value = _ui.value.copy(lines = _ui.value.lines + PrayLine(true, t))
        session?.say(t)
    }

    /** "Sing with me": a hymn now, the one chosen at the start, or following the person's own song. */
    fun sing(song: String? = _ui.value.song) {
        _ui.value = _ui.value.copy(singing = true)
        session?.say(PrayerCompanion.singNow(song))
    }

    /** The quick asks under the conversation. */
    fun ask(prompt: String) { session?.say(prompt) }

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

    fun reset() {
        session?.end(); session = null
        jobs.forEach { it.cancel() }; jobs.clear()
        _ui.value = _ui.value.copy(started = false, lines = emptyList(), error = null, savedNoteId = null, muted = false)
    }

    override fun onCleared() { end(); super.onCleared() }
}
