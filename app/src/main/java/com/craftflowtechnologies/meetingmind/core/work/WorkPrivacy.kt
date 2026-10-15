package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Confidential is a behaviour, not a label (docs/PLAN_PROFESSIONAL.md §6.4). A recording or note
 * stays on the phone, whatever Internet mode says, when:
 * - its project is confidential,
 * - anyone in it (or their organisation) is marked confidential, or
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

    /** A confidential project keeps notebook-wide AI on the phone. */
    suspend fun forNotebook(context: Context, notebookId: String, requested: ProcessingProfile): ProcessingProfile = withContext(Dispatchers.IO) {
        if (!requested.requiresNetwork) return@withContext requested
        val nb = MeetMindDatabase.getInstance(context).notebookDao().getById(notebookId)
        if (nb != null && confidential(nb.propertiesJson)) ProcessingProfile.OFFLINE else requested
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
        if (notebook != null && confidential(notebook.propertiesJson)) return@withContext true
        if (nId != null) {
            val people = db.workDao().peopleIdsFor(nId).mapNotNull { db.peopleDao().getById(it) }
            if (people.any { it.confidential }) return@withContext true
            if (people.mapNotNull { it.orgId }.distinct().mapNotNull { db.peopleDao().getById(it) }.any { it.confidential }) return@withContext true
        }
        false
    }

    private fun confidential(propertiesJson: String) = runCatching { JSONObject(propertiesJson).optBoolean("confidential") }.getOrDefault(false)
}
