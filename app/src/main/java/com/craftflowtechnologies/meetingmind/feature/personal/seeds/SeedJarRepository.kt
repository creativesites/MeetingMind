package com.craftflowtechnologies.meetingmind.feature.personal.seeds

import android.content.Context
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskReminders
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The seed jar's data: ideas, the notes waiting to be sorted, and what the person can do with
 * them. Everything stays on the phone.
 */
class SeedJarRepository(
    private val context: Context,
    db: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val notes: NoteRepository = NoteRepository(context, db, clock),
    private val tasks: TaskRepository = TaskReminders.repository(context)
) {

    /** Ideas, newest first. The repository query already leaves out archived and trashed notes. */
    fun observeIdeas(): Flow<List<Note>> =
        notes.observeNotesForWorkflows(listOf(RecordingType.IDEA)).map { list -> list.sortedByDescending { it.createdAt } }

    /**
     * Quick notes in the Personal space that nobody has filed yet: created in the last
     * [SORT_WINDOW_DAYS] days and not marked with [SORTED_KEY]. Newest first.
     *
     * The space is the notebook's space, not the workflow's: a conversation is filed under Work
     * by workflow, but a conversation the person kept in My Notes is Personal.
     */
    fun observeToSort(): Flow<List<Note>> = combine(
        notes.observeNotesForWorkflows(SORTABLE_WORKFLOWS),
        notes.observeNotebooks()
    ) { list, notebooks ->
        val spaceOf = notebooks.associate { it.id to it.space }
        val since = clock() - SORT_WINDOW_DAYS * DAY_MS
        list
            .filter { note ->
                (note.notebookId?.let { spaceOf[it] } ?: NotebookSpace.PERSONAL) == NotebookSpace.PERSONAL &&
                    note.createdAt >= since &&
                    note.metadata[SORTED_KEY] != "1"
            }
            .sortedByDescending { it.createdAt }
    }

    /** Files the note into [space]'s notebook. It leaves To sort because it is no longer in Personal. */
    suspend fun moveTo(noteId: String, space: NotebookSpace) {
        val notebook = notes.ensureSpaceNotebook(space)
        notes.moveToNotebook(noteId, notebook.id)
    }

    /** Keeps the note in Personal and takes it off To sort. */
    suspend fun keepHere(noteId: String) {
        notes.setMetadata(noteId, SORTED_KEY, "1")
    }

    /** Makes a Personal task linked to the note, then takes the note off To sort. */
    suspend fun makeTask(noteId: String) {
        val note = notes.getNote(noteId) ?: return
        tasks.save(Task(id = "", title = titleForTask(note), noteId = note.id, space = "PERSONAL"))
        notes.setMetadata(note.id, SORTED_KEY, "1")
    }

    /** The title a task made from [note] gets: the note's title, else its opening words. */
    private fun titleForTask(note: Note): String =
        titleOf(note, context.getString(R.string.seeds_untitled_task))

    companion object {
        /** Metadata key: "1" once the person has filed the note or kept it in Personal. */
        const val SORTED_KEY = "personal.sorted"

        const val SORT_WINDOW_DAYS = 60L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val PREVIEW_CHARS = 80

        /** The workflows that arrive without saying what they are. */
        val SORTABLE_WORKFLOWS: List<RecordingType> = listOf(
            RecordingType.GENERAL, RecordingType.VOICE_MEMO, RecordingType.DICTATION, RecordingType.CONVERSATION
        )

        /** The note's title, else the first [PREVIEW_CHARS] characters of its text, else [untitled]. */
        fun titleOf(note: Note, untitled: String): String {
            val title = note.title.trim()
            if (title.isNotEmpty()) return title
            val preview = note.plainText.replace(Regex("\\s+"), " ").trim().take(PREVIEW_CHARS).trim()
            return preview.ifEmpty { untitled }
        }
    }
}
