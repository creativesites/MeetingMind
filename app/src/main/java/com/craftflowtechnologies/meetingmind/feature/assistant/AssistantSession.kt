package com.craftflowtechnologies.meetingmind.feature.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantAction
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantEngine
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantMessage
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantScope
import com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi
import com.craftflowtechnologies.meetingmind.ai.faith.AskSermon
import com.craftflowtechnologies.meetingmind.ai.live.LiveSessionState
import com.craftflowtechnologies.meetingmind.ai.live.LiveVoiceAudioBridge
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One suggested prompt on the empty assistant. */
data class AssistantSuggestion(val label: String, val prompt: String)

/**
 * One assistant conversation: in a note (with [bridge]) or across the library (without). Lives in
 * the owner's view-model scope, so it survives the sheet closing and reopening.
 */
class AssistantSession(
    private val app: Application,
    private val scope: CoroutineScope,
    private val noteId: String?,
    private val bridge: EditorBridge?,
    private val libraryLabel: String = "Your library",
    private val faithLibrary: Boolean = false
) {
    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    /** Non-null while working: what it's doing right now. */
    private val _working = MutableStateFlow<String?>(null)
    val working: StateFlow<String?> = _working.asStateFlow()

    private val _scope = MutableStateFlow<AssistantScope?>(null)
    val assistantScope: StateFlow<AssistantScope?> = _scope.asStateFlow()

    /** Action ids currently executing confirm/undo, to lock against rapid double-taps. */
    private val _busyActionIds = MutableStateFlow<Set<String>>(emptySet())
    val busyActionIds: StateFlow<Set<String>> = _busyActionIds.asStateFlow()

    private val _liveState = MutableStateFlow(LiveSessionState.DISCONNECTED)
    val liveState: StateFlow<LiveSessionState> = _liveState.asStateFlow()

    private val _liveMuted = MutableStateFlow(false)
    val liveMuted: StateFlow<Boolean> = _liveMuted.asStateFlow()

    private val _liveAudioLevel = MutableStateFlow(0f)
    val liveAudioLevel: StateFlow<Float> = _liveAudioLevel.asStateFlow()

    private val _liveInputTranscript = MutableStateFlow("")
    val liveInputTranscript: StateFlow<String> = _liveInputTranscript.asStateFlow()

    private val _liveError = MutableStateFlow<String?>(null)
    val liveError: StateFlow<String?> = _liveError.asStateFlow()

    private var host: AppAssistantHost? = null
    private var voiceBridge: LiveVoiceAudioBridge? = null
    private var voiceCollectJob: Job? = null
    private var job: Job? = null
    private var lastPrompt: String? = null

    init { scope.launch { _scope.value = runCatching { resolveScope() }.getOrElse { fallbackScope() } } }

    private fun fallbackScope(): AssistantScope {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
        val isLearning = libraryLabel.equals("Learning", ignoreCase = true)
        return AssistantScope(libraryLabel, faithLibrary, libraryLabel, today = today, learning = isLearning)
    }

    private suspend fun resolveScope(): AssistantScope {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
        val isLearningLibrary = libraryLabel.equals("Learning", ignoreCase = true)
        if (noteId == null) {
            return AssistantScope(libraryLabel, faithLibrary, libraryLabel, today = today, learning = isLearningLibrary)
        }
        val db = MeetMindDatabase.getInstance(app)
        val directSession = db.learningSessionDao().getById(noteId)
        val resolvedNoteId = directSession?.noteId ?: noteId
        val note = db.noteDao().getById(resolvedNoteId)
        val workflow = note?.workflow?.let { runCatching { RecordingType.valueOf(it) }.getOrNull() } ?: RecordingType.GENERAL
        val meeting = db.noteDao().getMeetingsForNote(resolvedNoteId).firstOrNull()
        val meetingType = meeting?.recordingType?.let { runCatching { RecordingType.valueOf(it) }.getOrNull() }
        val learningSession = directSession ?: db.learningSessionDao().getByNoteId(resolvedNoteId)
        val notebook = note?.notebookId?.let { nbId -> db.notebookDao().getById(nbId) }
        val isLearning = isLearningLibrary ||
            workflow in setOf(RecordingType.LECTURE, RecordingType.RESEARCH) ||
            meetingType in setOf(RecordingType.LECTURE, RecordingType.RESEARCH) ||
            notebook?.space == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.LEARNING.name ||
            learningSession != null
        val domainDisplay = learningSession?.subjectDomain?.let {
            com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain.fromName(it).displayName
        }
        return AssistantScope(
            title = note?.title?.ifBlank { learningSession?.title ?: "Untitled lecture" } ?: learningSession?.title ?: "This lecture",
            faith = AskSermon.isFaith(workflow) || AskSermon.isFaith(meetingType),
            kindLabel = if (isLearning) (learningSession?.courseName ?: domainDisplay ?: "Lecture") else workflow.displayName,
            noteId = resolvedNoteId,
            meetingId = meeting?.id ?: learningSession?.meetingId,
            sermon = meetingType in setOf(RecordingType.SERMON, RecordingType.BIBLE_STUDY, RecordingType.DEVOTIONAL, RecordingType.TESTIMONY),
            today = today,
            learning = isLearning,
            subjectDomain = domainDisplay
        )
    }

    private suspend fun host(): AppAssistantHost =
        host ?: AppAssistantHost(app, _scope.value ?: runCatching { resolveScope() }.getOrElse { fallbackScope() }.also { _scope.value = it }, bridge).also { host = it }

    fun suggestions(): List<AssistantSuggestion> {
        val s = _scope.value
        return when {
            s == null -> emptyList()
            s.learning && s.noteId != null -> listOf(
                AssistantSuggestion("Generate 5 flashcards", "Read this lecture note and create 5 high-yield active-recall flashcards using create_flashcards."),
                AssistantSuggestion("Unpack hardest concepts", "Read this lecture and unpack the hardest concepts and formulas step-by-step with intuition and exam tips."),
                AssistantSuggestion("Quiz me Socratic-style", "Read the concepts in this lecture and quiz me Socratic-style one question at a time."),
                AssistantSuggestion("Add exam cram summary", "Summarize the top exam takeaways, definitions, and formulas from this lecture and add them to the note."),
                AssistantSuggestion("Create course from lecture", "Create a learning course from this lecture note using create_course."),
                AssistantSuggestion("What did the lecturer say about…", "What did the lecturer say in the recording about ")
            )
            s.learning -> listOf(
                AssistantSuggestion("What should I study today?", "Check my courses and study concepts and tell me the highest-priority topics to review today."),
                AssistantSuggestion("Quiz me on weak concepts", "Check my study concepts that need review or are high-yield and quiz me on them one by one."),
                AssistantSuggestion("Create flashcards on a topic…", "Create a study flashcard deck using create_flashcards on "),
                AssistantSuggestion("Build an exam study plan", "Check my courses and study concepts and build a focused exam study plan."),
                AssistantSuggestion("Explain a concept or formula…", "Explain the intuition, step-by-step derivation, and exam application of ")
            )
            s.sermon -> listOf(
                AssistantSuggestion("Outline the message", "Outline this message — big idea, main points and application — with timestamps, and add it to the note."),
                AssistantSuggestion("Add the key passages", "Add the main Bible passages this sermon used to the note."),
                AssistantSuggestion("Small group questions", "Write 6 discussion questions for a small group on this sermon (observation, interpretation, application) and add them."),
                AssistantSuggestion("Turn applications into tasks", "Find the practical applications in this sermon and add each as a task for this week."),
                AssistantSuggestion("Cross references", "Add a few cross references for the main passage, with one line on how each connects."),
                AssistantSuggestion("What did they say about…", "What did the preacher say about ")
            )
            s.noteId != null && s.faith -> listOf(
                AssistantSuggestion("Study the passage", "Help me study the main passage in this note: what it says, its context, and cross references."),
                AssistantSuggestion("What do commentators say?", "What do Matthew Henry and one other commentator say about the main passage here?"),
                AssistantSuggestion("Compare translations", "Compare the main verse here across the translations on my phone."),
                AssistantSuggestion("Write a prayer from this", "Write a short prayer based on this note and add it at the end."),
                AssistantSuggestion("Tidy this note", "Tidy this note into clear sections without changing my words.")
            )
            s.noteId != null -> listOf(
                AssistantSuggestion("Summarise", "Summarise this note in 5 bullet points and add them at the top under a Summary heading."),
                AssistantSuggestion("Find action items", "Find the action items and add each as a task, with owners and dates if they're stated."),
                AssistantSuggestion("Tidy into sections", "Organise this note into clear sections without changing my words."),
                AssistantSuggestion("Related notes", "Find my other notes related to this one."),
                AssistantSuggestion("Draft a follow-up", "Draft a short follow-up message based on this note.")
            )
            else -> listOf(
                AssistantSuggestion("Plan a Bible study", "Help me plan a 4-week Bible study for a small group and create a note for it."),
                AssistantSuggestion("What have I learned about…", "What have my notes and sermons said about "),
                AssistantSuggestion("Explain a passage", "Explain Romans 8:28-39 with its context and two cross references."),
                AssistantSuggestion("Add a prayer request", "Add a prayer request: "),
                AssistantSuggestion("Mark a prayer answered", "What's on my prayer list? I'd like to mark one answered.")
            )
        }
    }

    fun send(text: String) {
        val prompt = text.trim().ifEmpty { return }
        if (_working.value != null) return
        lastPrompt = prompt
        val history = _messages.value
        val placeholderIndex = history.size + 1
        _messages.value = history + AssistantMessage(true, prompt) + AssistantMessage(false, "", streaming = true)
        _working.value = "Thinking"
        job = scope.launch {
            try {
                // One transport instance for both the configured-check and the engine, so they can
                // never disagree about whether Gemini or the DeepSeek backup is reachable.
                val transport = CloudAi.transport(app)
                val prefs = com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(app).preferencesFlow.first()
                val effectiveProfile = if (noteId != null) {
                    com.craftflowtechnologies.meetingmind.core.work.WorkPrivacy.forNote(app, noteId, prefs.processingProfile)
                } else {
                    prefs.processingProfile
                }
                val forceLocal = effectiveProfile == com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.OFFLINE
                val localProvider: suspend () -> com.craftflowtechnologies.meetingmind.ai.routing.ResolvedLanguageModel? = {
                    com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory(
                        context = app,
                        modelStorage = com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage(app),
                        geminiTransport = transport
                    ).resolveLocal(
                        com.craftflowtechnologies.meetingmind.core.model.ModelCapability.SUMMARIZATION,
                        com.craftflowtechnologies.meetingmind.core.model.ModelTier.RECOMMENDED
                    )
                }
                val engine = AssistantEngine(
                    transport = transport,
                    host = host(),
                    localModelProvider = localProvider,
                    forceLocal = forceLocal
                )
                var lastUiEmitMs = 0L
                var lastToolCount = -1
                var lastTextLen = 0
                engine.replyStream(history.filterNot { it.error }, prompt).collect { snap ->
                    _working.value = if (snap.done) null else snap.statusLabel
                    val now = System.currentTimeMillis()
                    val toolsChanged = snap.message.toolSteps != _messages.value.getOrNull(placeholderIndex)?.toolSteps ||
                        snap.message.actions.size != _messages.value.getOrNull(placeholderIndex)?.actions?.size
                    val textChanged = snap.message.text.length != lastTextLen
                    if (snap.done || toolsChanged || (textChanged && now - lastUiEmitMs >= 45L) || now - lastUiEmitMs >= 100L) {
                        lastUiEmitMs = now
                        lastToolCount = snap.message.toolSteps.size
                        lastTextLen = snap.message.text.length
                        replaceAt(placeholderIndex, snap.message)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything that escapes the engine must still end in a visible, retryable error — never
                // a silently cleared spinner with nothing to retry.
                replaceAt(placeholderIndex, AssistantMessage(false, "Something went wrong before I could answer. Try again.", error = true))
            } finally {
                _working.value = null
            }
        }
    }

    private fun replaceAt(index: Int, message: AssistantMessage) {
        val current = _messages.value.toMutableList()
        if (index in current.indices) current[index] = message else current += message
        _messages.value = current
    }

    fun stop() {
        job?.cancel()
        _working.value = null
        val current = _messages.value.toMutableList()
        val last = current.lastOrNull()
        if (last != null && !last.fromUser && last.streaming) {
            val hasContent = last.text.isNotBlank() || last.actions.isNotEmpty() || last.toolSteps.isNotEmpty()
            current[current.lastIndex] = if (hasContent) {
                last.copy(streaming = false)
            } else {
                AssistantMessage(false, "Stopped.", error = true)
            }
            _messages.value = current
        } else {
            _messages.value = current + AssistantMessage(false, "Stopped.", error = true)
        }
    }

    fun retry() {
        val p = lastPrompt ?: return
        val list = _messages.value.toMutableList()
        val failed = list.lastOrNull()?.takeIf { it.error && !it.fromUser }
        // A failed reply that already read or wrote something real (a tool chip, an applied action)
        // made partial progress: that progress stays visible — its writes already happened in the
        // real note/tasks — and a fresh prompt never re-asks the model to redo it from scratch,
        // which would risk a duplicate task or a duplicate insert. A failure with no progress at all
        // is dropped and the exact original message is simply asked again, as before.
        val hadProgress = failed != null && (failed.activity.isNotEmpty() || failed.actions.isNotEmpty())
        if (failed != null) {
            if (hadProgress) list[list.lastIndex] = failed.copy(error = false) else list.removeAt(list.lastIndex)
        }
        if (!hadProgress && list.lastOrNull()?.fromUser == true) list.removeAt(list.lastIndex)
        _messages.value = list
        send(if (hadProgress) "Please continue — don't repeat anything you already did above, just finish the rest." else p)
    }

    fun clear() { job?.cancel(); _working.value = null; _messages.value = emptyList(); stopVoice() }

    fun startVoice() {
        if (_liveState.value != LiveSessionState.DISCONNECTED && _liveState.value != LiveSessionState.ERROR) return
        job?.cancel()
        _working.value = null
        _liveError.value = null
        scope.launch {
            val resolvedHost = host()
            val engine = AssistantEngine(CloudAi.transport(app), resolvedHost)
            val bridge = LiveVoiceAudioBridge(
                context = app,
                scope = scope,
                host = resolvedHost,
                tools = engine.scopedTools(),
                onActionEmitted = { action ->
                    _messages.value = _messages.value + AssistantMessage(
                        fromUser = false,
                        text = action.label,
                        actions = listOf(action),
                        provider = "Gemini Live"
                    )
                },
                onTurnTranscript = { fromUser, text ->
                    if (fromUser && text.isNotBlank()) {
                        val last = _messages.value.lastOrNull()
                        if (last == null || !last.fromUser || last.text != text) {
                            _messages.value = _messages.value + AssistantMessage(fromUser = true, text = text)
                        }
                    }
                }
            )
            voiceBridge = bridge
            voiceCollectJob?.cancel()
            voiceCollectJob = scope.launch {
                launch { bridge.state.collect { _liveState.value = it } }
                launch { bridge.muted.collect { _liveMuted.value = it } }
                launch { bridge.audioLevel.collect { _liveAudioLevel.value = it } }
                launch { bridge.inputTranscript.collect { _liveInputTranscript.value = it } }
                launch { bridge.error.collect { _liveError.value = it } }
            }
            val voiceInstruction = engine.systemPrompt() +
                "\n\nVoice mode instructions: Keep spoken responses concise and natural (1-3 sentences when conversational), call tools immediately when asked about notes or tasks, and confirm clearly when a write succeeds."
            bridge.start(voiceInstruction)
        }
    }

    fun toggleVoiceMute() { voiceBridge?.toggleMute() }

    fun stopVoice() {
        val current = voiceBridge
        voiceBridge = null
        voiceCollectJob?.cancel()
        voiceCollectJob = null
        _liveState.value = LiveSessionState.DISCONNECTED
        _liveMuted.value = false
        _liveAudioLevel.value = 0f
        _liveInputTranscript.value = ""
        scope.launch { runCatching { current?.stop() } }
    }

    fun confirm(action: AssistantAction) = scope.launch {
        if (action.id in _busyActionIds.value) return@launch
        _busyActionIds.value = _busyActionIds.value + action.id
        try {
            if (host?.confirm(action.id) == true) setState(action.id, AssistantAction.State.APPLIED)
        } finally {
            _busyActionIds.value = _busyActionIds.value - action.id
        }
    }

    fun discard(action: AssistantAction) {
        if (action.id in _busyActionIds.value) return
        host?.discard(action.id)
        setState(action.id, AssistantAction.State.DISCARDED)
    }

    fun undo(action: AssistantAction) = scope.launch {
        if (action.id in _busyActionIds.value) return@launch
        _busyActionIds.value = _busyActionIds.value + action.id
        try {
            if (host?.undo(action.id) == true) setState(action.id, AssistantAction.State.UNDONE)
        } finally {
            _busyActionIds.value = _busyActionIds.value - action.id
        }
    }

    private fun setState(id: String, state: AssistantAction.State) {
        _messages.value = _messages.value.map { m -> m.copy(actions = m.actions.map { if (it.id == id) it.copy(state = state) else it }) }
    }
}

/** The assistant across the whole library, for Faith home, Learning home, and lecture sessions. */
class LibraryAssistantViewModel(private val app: Application) : AndroidViewModel(app) {
    val faith = AssistantSession(app, viewModelScope, null, null, libraryLabel = "Faith", faithLibrary = true)
    val learning = AssistantSession(app, viewModelScope, null, null, libraryLabel = "Learning", faithLibrary = false)
    private val learningNoteSessions = mutableMapOf<String, AssistantSession>()

    fun forLearningNote(noteId: String): AssistantSession {
        if (noteId.isBlank()) return learning
        return learningNoteSessions.getOrPut(noteId) {
            AssistantSession(app, viewModelScope, noteId = noteId, bridge = null, libraryLabel = "Learning", faithLibrary = false)
        }
    }
}
