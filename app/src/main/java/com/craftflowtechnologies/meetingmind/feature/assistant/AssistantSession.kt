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
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var host: AppAssistantHost? = null
    private var job: Job? = null
    private var lastPrompt: String? = null

    init { scope.launch { _scope.value = resolveScope() } }

    private suspend fun resolveScope(): AssistantScope {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
        if (noteId == null) return AssistantScope(libraryLabel, faithLibrary, libraryLabel, today = today)
        val db = MeetMindDatabase.getInstance(app)
        val note = db.noteDao().getById(noteId)
        val workflow = note?.workflow?.let { runCatching { RecordingType.valueOf(it) }.getOrNull() } ?: RecordingType.GENERAL
        val meeting = db.noteDao().getMeetingsForNote(noteId).firstOrNull()
        val meetingType = meeting?.recordingType?.let { runCatching { RecordingType.valueOf(it) }.getOrNull() }
        return AssistantScope(
            title = note?.title?.ifBlank { "Untitled note" } ?: "This note",
            faith = AskSermon.isFaith(workflow) || AskSermon.isFaith(meetingType),
            kindLabel = workflow.displayName,
            noteId = noteId,
            meetingId = meeting?.id,
            sermon = meetingType in setOf(RecordingType.SERMON, RecordingType.BIBLE_STUDY, RecordingType.DEVOTIONAL, RecordingType.TESTIMONY),
            today = today
        )
    }

    private suspend fun host(): AppAssistantHost =
        host ?: AppAssistantHost(app, _scope.value ?: resolveScope().also { _scope.value = it }, bridge).also { host = it }

    fun suggestions(): List<AssistantSuggestion> {
        val s = _scope.value
        return when {
            s == null -> emptyList()
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
                AssistantSuggestion("Prayer reminders", "Remind me to pray for my family every morning at 7.")
            )
        }
    }

    fun send(text: String) {
        val prompt = text.trim().ifEmpty { return }
        if (_working.value != null) return
        lastPrompt = prompt
        val history = _messages.value
        _messages.value = history + AssistantMessage(true, prompt)
        _working.value = "Thinking"
        job = scope.launch {
            try {
                val engine = AssistantEngine(CloudAi.transport(app), host())
                val reply = if (!CloudAi.transport(app).refreshConfigured()) {
                    AssistantMessage(false, "The assistant needs Internet mode (a Gemini key) or the backup AI. You can set either up in Settings.", error = true)
                } else engine.reply(history.filterNot { it.error }, prompt) { chip -> _working.value = chip }
                _messages.value = _messages.value + reply
            } finally {
                _working.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        _working.value = null
        _messages.value = _messages.value + AssistantMessage(false, "Stopped.", error = true)
    }

    fun retry() {
        val p = lastPrompt ?: return
        // Drop the failed reply and the prompt it answered, then ask again.
        val list = _messages.value.toMutableList()
        if (list.lastOrNull()?.error == true) list.removeAt(list.lastIndex)
        if (list.lastOrNull()?.fromUser == true) list.removeAt(list.lastIndex)
        _messages.value = list
        send(p)
    }

    fun clear() { job?.cancel(); _working.value = null; _messages.value = emptyList() }

    fun confirm(action: AssistantAction) = scope.launch {
        if (host?.confirm(action.id) == true) setState(action.id, AssistantAction.State.APPLIED)
    }

    fun discard(action: AssistantAction) { host?.discard(action.id); setState(action.id, AssistantAction.State.DISCARDED) }

    fun undo(action: AssistantAction) = scope.launch {
        if (host?.undo(action.id) == true) setState(action.id, AssistantAction.State.UNDONE)
    }

    private fun setState(id: String, state: AssistantAction.State) {
        _messages.value = _messages.value.map { m -> m.copy(actions = m.actions.map { if (it.id == id) it.copy(state = state) else it }) }
    }
}

/** The assistant across the whole library, for Faith home and elsewhere without a note open. */
class LibraryAssistantViewModel(app: Application) : AndroidViewModel(app) {
    val faith = AssistantSession(app, viewModelScope, null, null, libraryLabel = "Faith", faithLibrary = true)
    /** Ask about my work: the same library, opened from the Pulse. */
    val work = AssistantSession(app, viewModelScope, null, null, libraryLabel = "Your work")
}
