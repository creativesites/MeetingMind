package com.craftflowtechnologies.meetingmind.feature.bible

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.biblestudy.CitedLine
import com.craftflowtechnologies.meetingmind.ai.biblestudy.ModelSessionAi
import com.craftflowtechnologies.meetingmind.ai.biblestudy.SessionAi
import com.craftflowtechnologies.meetingmind.ai.biblestudy.SessionBrief
import com.craftflowtechnologies.meetingmind.ai.biblestudy.SuggestOutcome
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackController
import com.craftflowtechnologies.meetingmind.core.biblestudy.JournalSession
import com.craftflowtechnologies.meetingmind.core.biblestudy.Need
import com.craftflowtechnologies.meetingmind.core.biblestudy.QuestionBank
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionKind
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionPlan
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionRequest
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionState
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionStep
import com.craftflowtechnologies.meetingmind.core.biblestudy.StepType
import com.craftflowtechnologies.meetingmind.core.biblestudy.WhyLines
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.faith.FaithStore
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterAudio
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterResult
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterVerse
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What an AI suggestion added to a step. [line] is the one honest sentence when it could not. */
data class StepHelp(
    val loading: Boolean = false,
    val context: List<CitedLine> = emptyList(),
    val questions: List<String> = emptyList(),
    val line: String? = null,
    val fromAi: Boolean = false
)

sealed interface SessionLoad {
    data object Loading : SessionLoad
    /** The passage text could not be loaded; [message] says why and the screen offers Retry. */
    data class Missing(val message: String) : SessionLoad
    data class Ready(val passageText: String, val verses: List<ChapterVerse>, val attribution: String) : SessionLoad
}

data class GuidedUi(
    val request: SessionRequest? = null,
    val load: SessionLoad = SessionLoad.Loading,
    /** A "Walk me through it" session waiting for SOAP or Verse by verse. */
    val choosingMethod: Boolean = false,
    val session: SessionState? = null,
    val help: Map<String, StepHelp> = emptyMap(),
    val canListen: Boolean = false,
    val savedNoteId: String? = null,
    val addedTo: String? = null
)

/**
 * One guided session (ONE_PLACE §3.2). The passage is pinned, the steps come from [SessionPlan], the
 * model suggests questions and cited background but never writes an answer, and whatever the person
 * writes saves itself into the study journal on the first keystroke.
 */
class GuidedSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val library = ScriptureService.library(application)
    private val notes = NoteRepository(application, MeetMindDatabase.getInstance(application))
    private val prefs = UserPreferencesManager(application)

    /** Replaceable in tests. */
    internal var ai: SessionAi = ModelSessionAi(application)

    private val _ui = MutableStateFlow(GuidedUi())
    val ui: StateFlow<GuidedUi> = _ui.asStateFlow()

    private var journal: JournalSession? = null
    private var chapterAudio: List<ChapterAudio> = emptyList()
    private var bibleId = 0
    private var profile = DevotionalProfile()
    private var started = false

    // Journal saves run on their own scope, in order, and drain when the screen goes: nothing typed is lost.
    private val saver = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = Channel<SessionState>(Channel.CONFLATED)

    init {
        saver.launch { for (s in pending) runCatching { journal?.onChanged(s) } }
    }

    /** Starts the session for [request]. [walk] means the person chooses SOAP or Verse by verse first. */
    fun start(request: SessionRequest, walk: Boolean) {
        if (started) return
        started = true
        journal = JournalSession(notes, request.passage, request.kind, request.need)
        _ui.value = GuidedUi(request = request, choosingMethod = walk)
        load(request, walk)
    }

    fun retry() { _ui.value.request?.let { load(it, _ui.value.choosingMethod) } }

    private fun load(request: SessionRequest, walk: Boolean) {
        _ui.value = _ui.value.copy(load = SessionLoad.Loading)
        viewModelScope.launch {
            profile = runCatching { prefs.devotionalProfile.first() }.getOrDefault(DevotionalProfile())
            bibleId = prefs.preferencesFlow.first().bibleVersionId
            val p = request.passage
            when (val r = library.chapter(bibleId, p.book, p.chapter)) {
                is ChapterResult.Unavailable -> _ui.value = _ui.value.copy(load = SessionLoad.Missing(r.message))
                is ChapterResult.Found -> {
                    val from = p.verseStart ?: 1
                    val to = p.verseEnd ?: p.verseStart ?: Int.MAX_VALUE
                    val verses = r.content.verses.filter { it.number in from..to }
                    _ui.value = _ui.value.copy(
                        load = SessionLoad.Ready(r.content.text(p.verseStart, p.verseEnd ?: p.verseStart), verses, "${r.content.abbreviation} · ${r.content.attribution}")
                    )
                    if (!walk && _ui.value.session == null) begin(request.kind)
                    runCatching { library.audio(bibleId, p.book, p.chapter) }.getOrDefault(emptyList()).let {
                        chapterAudio = it
                        _ui.value = _ui.value.copy(canListen = it.isNotEmpty())
                    }
                }
            }
        }
    }

    /** The person chose SOAP or Verse by verse after "Walk me through it". */
    fun chooseMethod(kind: SessionKind) {
        val request = _ui.value.request ?: return
        journal = JournalSession(notes, request.passage, kind, request.need)
        _ui.value = _ui.value.copy(request = request.copy(kind = kind), choosingMethod = false)
        begin(kind)
    }

    private fun begin(kind: SessionKind) {
        val ready = _ui.value.load as? SessionLoad.Ready
        val verses = ready?.verses?.map { it.number }.orEmpty()
        val session = SessionState(kind, SessionPlan.steps(kind, verses))
        _ui.value = _ui.value.copy(session = session)
        enter(session.current)
    }

    // ---------------------------------------------------------------- moving

    fun next() = move { it.next() }
    fun back() = move { it.back() }

    private fun move(change: (SessionState) -> SessionState) {
        val s = _ui.value.session ?: return
        val moved = change(s)
        _ui.value = _ui.value.copy(session = moved)
        enter(moved.current)
    }

    /** Fixes the question shown on [step] (so the journal records what was asked) and asks the model for help. */
    private fun enter(step: SessionStep) {
        val s = _ui.value.session ?: return
        if (step.answerable && step.id !in s.prompts && step.type in setOf(StepType.OBSERVE, StepType.APPLY, StepType.VERSE)) {
            val text = QuestionBank.question(step.type, _ui.value.request?.need, QuestionBank.seedFor(_ui.value.request!!.passage) + (step.verse ?: 0), step.ordinal)
            if (text.isNotEmpty()) _ui.value = _ui.value.copy(session = s.withPrompt(step.id, text))
        }
        if (step.type in setOf(StepType.WHY, StepType.OBSERVE, StepType.APPLY, StepType.VERSE, StepType.SCRIPTURE)) askForHelp(step)
    }

    // ---------------------------------------------------------------- the model

    private fun helpKey(step: SessionStep) = "${step.type}-${step.verse ?: 0}"

    private fun askForHelp(step: SessionStep) {
        val key = helpKey(step)
        if (_ui.value.help.containsKey(key)) return
        val request = _ui.value.request ?: return
        val ready = _ui.value.load as? SessionLoad.Ready ?: return
        setHelp(key, StepHelp(loading = true))
        viewModelScope.launch {
            val text = step.verse?.let { v -> ready.verses.firstOrNull { it.number == v }?.text } ?: ready.passageText
            val brief = SessionBrief(request.passage, text, step.type, step.verse, request.need, profile, LocalDate.now())
            val outcome = withContext(Dispatchers.Default) { ai.suggest(brief) }
            setHelp(
                key, when (outcome) {
                    is SuggestOutcome.Got -> StepHelp(context = outcome.suggestions.context, questions = outcome.suggestions.questions, fromAi = true)
                    is SuggestOutcome.Unavailable -> StepHelp(line = outcome.line)
                }
            )
        }
    }

    private fun setHelp(key: String, help: StepHelp) { _ui.value = _ui.value.copy(help = _ui.value.help + (key to help)) }

    fun helpFor(step: SessionStep): StepHelp? = _ui.value.help[helpKey(step)]

    /** The person picked one of Zuri's questions for this step in place of the curated one. */
    fun useQuestion(step: SessionStep, question: String) {
        val s = _ui.value.session ?: return
        _ui.value = _ui.value.copy(session = s.withPrompt(step.id, question))
    }

    // ---------------------------------------------------------------- writing

    /** Called on every change to an answer. The first non-blank one creates the journal entry. */
    fun answer(stepId: String, text: String) {
        val s = _ui.value.session ?: return
        val updated = s.answer(stepId, text)
        _ui.value = _ui.value.copy(session = updated)
        pending.trySend(updated)
        // The note id becomes known a moment after the first keystroke.
        viewModelScope.launch {
            repeat(20) {
                journal?.noteId?.let { id -> if (_ui.value.savedNoteId != id) _ui.value = _ui.value.copy(savedNoteId = id); return@launch }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    /** Recent notes to add this session to. */
    suspend fun recentNotes(): List<Note> =
        notes.observeNotes().first().filter { it.deletedAt == null && it.id != journal?.noteId }.sortedByDescending { it.updatedAt }.take(40)

    fun addToNote(noteId: String, title: String) {
        val s = _ui.value.session ?: return
        viewModelScope.launch {
            val ok = journal?.addToExisting(noteId, s) == true
            if (ok) _ui.value = _ui.value.copy(addedTo = title.ifBlank { "your note" })
        }
    }

    /** Everything written, ready for the clipboard. */
    fun notesText(): String = _ui.value.session?.written()?.joinToString("\n\n") { "${it.heading}\n${it.text}" }.orEmpty()

    fun passageCopyText(): String {
        val request = _ui.value.request ?: return ""
        val ready = _ui.value.load as? SessionLoad.Ready ?: return request.passage.display()
        return "“${ready.passageText}”\n— ${request.passage.display()} (${ready.attribution.substringBefore(" · ")})"
    }

    /** Marks a reading-plan day read when the person finishes its session. */
    fun finish() {
        val r = _ui.value.request ?: return
        if (r.planId != null && r.planDay != null) viewModelScope.launch(Dispatchers.IO) { runCatching { FaithStore.get(app).setDone(r.planId, r.planDay, true) } }
    }

    // ---------------------------------------------------------------- listening

    private fun playbackId() = "bible:$bibleId:${_ui.value.request?.passage?.usfm}:${_ui.value.request?.passage?.chapter}"

    val listening: StateFlow<Boolean> = PlaybackController.state.map { it.recordingId == playbackId() && it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Plays the chapter's narration from the passage's first verse; pressing again pauses. Uses the reader's own audio. */
    fun listen() {
        val request = _ui.value.request ?: return
        val p = request.passage
        val narrator = app.getSharedPreferences("bible_reader", android.content.Context.MODE_PRIVATE).getString("narrator", null)
        val audio = chapterAudio.firstOrNull { it.narrator == narrator } ?: chapterAudio.firstOrNull() ?: return
        val state = PlaybackController.state.value
        if (state.recordingId == playbackId() && state.isPlaying) { PlaybackController.pause(); return }
        val title = "${p.display()}"
        PlaybackController.playUriAt(app, playbackId(), title, Uri.parse(audio.url), p.verseStart?.let { audio.startOf(it) } ?: if (state.recordingId == playbackId()) null else 0L)
    }

    override fun onCleared() {
        pending.close()
        // Let the saver finish the last save; it ends on its own when the channel drains.
        (ai as? ModelSessionAi)?.let { m -> CoroutineScope(Dispatchers.IO).launch { m.release() } }
        super.onCleared()
    }

}
