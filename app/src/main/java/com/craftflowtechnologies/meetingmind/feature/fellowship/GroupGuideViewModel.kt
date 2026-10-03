package com.craftflowtechnologies.meetingmind.feature.fellowship

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.notes.CitedItem
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiOutcome
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiRepository
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiStatus
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTarget
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTool
import com.craftflowtechnologies.meetingmind.ai.notes.SectionDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One section of the guide as the person edits it: plain text, no citations to carry around. */
data class GuideSection(val key: String, val title: String, val items: List<String>)

sealed interface GuideState {
    /** Nothing has been made for this note yet. */
    data object Idle : GuideState
    data class Working(val jobId: String?) : GuideState
    data class Failed(val message: String) : GuideState
    data class Ready(val jobId: String) : GuideState
}

/**
 * Makes and edits a discussion guide for one note. The writing itself is the app's existing note-AI
 * pipeline (same model choice, same privacy rules, runs in the background); this only watches that
 * job and keeps the person's edits.
 */
class GroupGuideViewModel(app: Application, private val noteId: String) : AndroidViewModel(app) {

    private val repository = NoteAiRepository(app)
    private val _state = MutableStateFlow<GuideState>(GuideState.Idle)
    val state: StateFlow<GuideState> = _state.asStateFlow()
    private val _sections = MutableStateFlow<List<GuideSection>>(emptyList())
    val sections: StateFlow<List<GuideSection>> = _sections.asStateFlow()
    private var loadedJob: String? = null

    init {
        viewModelScope.launch {
            repository.observe(noteId).collect { jobs ->
                val job = jobs.filter { it.tool == NoteAiTool.STUDY_GUIDE }.maxByOrNull { it.createdAt }
                _state.value = when (job?.status) {
                    null, NoteAiStatus.CANCELLED -> GuideState.Idle
                    NoteAiStatus.QUEUED, NoteAiStatus.RUNNING -> GuideState.Working(job.id)
                    NoteAiStatus.FAILED -> GuideState.Failed(job.error ?: "The guide couldn't be written.")
                    NoteAiStatus.SUCCEEDED -> {
                        val outcome = job.result?.outcome as? NoteAiOutcome.Sections
                        if (outcome == null) GuideState.Failed("The guide came back in a form that couldn't be read.")
                        else {
                            // Keep the person's edits: only load a result the first time it's seen.
                            if (loadedJob != job.id) {
                                loadedJob = job.id
                                _sections.value = outcome.sections.map { s -> GuideSection(s.key, s.title, s.items.map { it.text }) }
                            }
                            GuideState.Ready(job.id)
                        }
                    }
                }
            }
        }
    }

    fun generate() {
        _state.value = GuideState.Working(null)
        loadedJob = null
        viewModelScope.launch { repository.run(NoteAiTarget.NOTE, noteId, NoteAiTool.STUDY_GUIDE) }
    }

    fun cancel() {
        val working = _state.value as? GuideState.Working ?: return
        viewModelScope.launch { working.jobId?.let { repository.cancel(it) } }
        _state.value = GuideState.Idle
    }

    fun edit(section: Int, item: Int, text: String) = update(section) { items -> items.toMutableList().also { it[item] = text } }
    fun remove(section: Int, item: Int) = update(section) { items -> items.toMutableList().also { it.removeAt(item) } }
    fun add(section: Int) = update(section) { it + "" }

    private fun update(section: Int, change: (List<String>) -> List<String>) {
        _sections.value = _sections.value.mapIndexed { i, s -> if (i == section) s.copy(items = change(s.items)) else s }
    }

    /** What will be sent: the edited sections, minus empty items and sections. */
    fun draft(): List<SectionDraft> = _sections.value
        .map { s -> SectionDraft(s.key, s.title, s.items.map { it.trim() }.filter { it.isNotEmpty() }.map { CitedItem(it, emptyList()) }) }
        .filter { it.items.isNotEmpty() }

    class Factory(private val app: Application, private val noteId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GroupGuideViewModel(app, noteId) as T
    }
}
