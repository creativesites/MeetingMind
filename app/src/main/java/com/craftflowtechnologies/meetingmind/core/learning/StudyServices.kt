package com.craftflowtechnologies.meetingmind.core.learning

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.learning.AppStudyModelSource
import com.craftflowtechnologies.meetingmind.ai.learning.StudyModelSource
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.Meeting
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.repository.TranscriptRepository

/** A lecture note opened for study: its session, its recording and its transcript. */
data class OpenLecture(
    val noteId: String,
    val title: String,
    val session: LearningSession,
    val meeting: Meeting?,
    val segments: List<TranscriptSegment>
) {
    val meetingId: String? get() = meeting?.id
    val durationMs: Long get() = maxOf(meeting?.durationMs ?: 0L, segments.maxOfOrNull { it.endMs } ?: 0L)
}

/**
 * Everything the Study view models use, built once per app. View models take this as a
 * constructor argument so tests can supply an in-memory database and a fake model.
 */
class StudyServices(
    val context: Context,
    database: MeetMindDatabase,
    val learning: LearningRepository = LearningRepository(context, database),
    val meetings: MeetingRepository = MeetingRepository(context, database),
    val transcripts: TranscriptRepository = TranscriptRepository(database),
    val notes: NoteRepository = NoteRepository(context, database),
    val kits: StudyKitStore = StudyKitStore.forContext(context),
    val models: StudyModelSource = AppStudyModelSource(context),
    val now: () -> Long = System::currentTimeMillis
) {
    val snapshots = StudySnapshotLoader(learning)

    /**
     * Opens a lecture note for study, creating its session on first use. Null when the note does
     * not exist. The transcript is empty when the lecture has no words yet.
     */
    suspend fun openLecture(noteId: String): OpenLecture? {
        val note = notes.getNote(noteId) ?: return null
        val courseName = note.notebookId?.let { notes.getNotebook(it)?.name }
        val session = learning.getOrCreateSessionForNote(noteId, courseName)
        val meetingId = session.meetingId ?: learning.meetingIdForNote(noteId)
        val meeting = meetingId?.let { meetings.getMeetingByIdDirect(it) }
        val segments = meetingId?.let { transcripts.getTranscriptDirect(it).segments }.orEmpty()
        return OpenLecture(
            noteId = noteId,
            title = session.title,
            session = session,
            meeting = meeting,
            segments = segments
        )
    }

    companion object {
        fun forApp(context: Context): StudyServices =
            StudyServices(context.applicationContext, MeetMindDatabase.getInstance(context.applicationContext))
    }
}
