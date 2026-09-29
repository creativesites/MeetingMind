package com.example.core.work

import android.content.Context
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.NotebookSpace
import com.example.core.model.ProcessingProfile
import com.example.core.model.RecordingType
import com.example.core.model.Workflows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Confidential is a behaviour, not a label (docs/PLAN_PROFESSIONAL.md §6.4). A recording or note
 * stays on the phone, whatever Internet mode says, when:
 * - its project is confidential,
 * - anyone in it is marked confidential, or
 * - it's work and the person keeps work on the phone (always so for clinical and legal work).
 *
 * Every AI entry point asks here before it may use a cloud profile.
 */
object WorkPrivacy {

    suspend fun forMeeting(context: Context, meetingId: String, requested: ProcessingProfile): ProcessingProfile {
        if (!requested.requiresNetwork) return requested
        return if (mustStayOnDevice(context, meetingId = meetingId)) ProcessingProfile.OFFLINE else requested
    }

    suspend fun forNote(context: Context, noteId: String, requested: ProcessingProfile): ProcessingProfile {
        if (!requested.requiresNetwork) return requested
        return if (mustStayOnDevice(context, noteId = noteId)) ProcessingProfile.OFFLINE else requested
    }

    /** A confidential project notebook keeps notebook-wide AI (summaries across it) on the phone. */
    suspend fun forNotebook(context: Context, notebookId: String, requested: ProcessingProfile): ProcessingProfile = withContext(Dispatchers.IO) {
        if (!requested.requiresNetwork) return@withContext requested
        val nb = MeetMindDatabase.getInstance(context).notebookDao().getById(notebookId)
        val confidential = nb != null && runCatching { JSONObject(nb.propertiesJson).optBoolean("confidential") }.getOrDefault(false)
        if (confidential) ProcessingProfile.OFFLINE else requested
    }

    suspend fun mustStayOnDevice(context: Context, meetingId: String? = null, noteId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        val meeting = meetingId?.let { db.meetingDao().getMeetingById(it) }
        val nId = noteId ?: meeting?.noteId
        val note = nId?.let { db.noteDao().getById(it) }
        val type = (meeting?.recordingType ?: note?.workflow)?.let { runCatching { RecordingType.valueOf(it) }.getOrNull() }
        val settings = UserPreferencesManager(context).workSettings.first()
        if (type != null && Workflows.space(type) == NotebookSpace.WORK && settings.keepOnDevice) return@withContext true
        val notebook = note?.notebookId?.let { db.notebookDao().getById(it) }
        if (notebook != null && runCatching { JSONObject(notebook.propertiesJson).optBoolean("confidential") }.getOrDefault(false)) return@withContext true
        if (nId != null) {
            val people = db.peopleDao().getNoteLinks(nId).mapNotNull { db.peopleDao().getById(it.personId) }
            if (people.any { it.confidential }) return@withContext true
            // An organisation marked confidential covers its people.
            if (people.mapNotNull { it.orgId }.distinct().mapNotNull { db.peopleDao().getById(it) }.any { it.confidential }) return@withContext true
        }
        false
    }
}
