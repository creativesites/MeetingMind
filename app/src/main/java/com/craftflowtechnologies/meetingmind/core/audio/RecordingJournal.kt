package com.craftflowtechnologies.meetingmind.core.audio

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * What survives a crash (design capture-pipeline spec §3.7): enough to detect, on the next app
 * launch, that a recording was in flight when the process died, and to let the user decide what
 * happens to it. Lives in a plain JSON file rather than Room — [MeetingRecordingService] must be
 * able to write and read it without any database machinery being ready, and it may be the only
 * component alive when the process is killed.
 */
data class RecordingJournalEntry(
    val meetingId: String,
    val title: String,
    val recordingType: String,
    val audioFilePath: String,
    val lastKnownDurationMs: Long,
    val markerCount: Int,
    val noteCount: Int,
    val lastHeartbeatAtMs: Long,
    /** [RecordingState.name] as of the last write. Only `RECORDING`/`PAUSED` mean the process
     * died mid-capture; any other value left behind (or no journal at all) means there is nothing
     * to recover — see [RecordingJournalStore.clear], called on every clean stop or discard. */
    val state: String,
    /** What the recording was started with, so a recovered recording keeps them (optional:
     * journals written before these fields read as null). */
    val language: String? = null,
    val speakerCountPreference: Int? = null,
    val noteId: String? = null,
    val customContext: String? = null
)

/**
 * Atomic-write journal store: every [write] goes to a temp file first, then renames over the real
 * one, so a process death mid-write never leaves a half-written `recovery.json` behind — a
 * truncated journal would be worse than no journal, since it could misreport what actually
 * happened. [read] treats a journal it can't parse the same as no journal at all rather than
 * crashing app startup on it.
 */
class RecordingJournalStore(context: Context) {
    private val journalFile = File(context.filesDir, "recovery.json")
    /** Journals of interrupted recordings moved aside when a new recording started before they
     * were recovered: one file per recording, so a new recording never overwrites one. */
    private val parkedDir = File(context.filesDir, "recovery_parked")

    /**
     * Best effort: returns false instead of throwing when the disk is full or the file can't be
     * written. A missed heartbeat must never take down a recording in progress; the previous
     * journal stays in place.
     */
    fun write(entry: RecordingJournalEntry): Boolean = try {
        val tempFile = File(journalFile.parentFile, "${journalFile.name}.tmp")
        tempFile.writeText(toJson(entry).toString())
        tempFile.renameTo(journalFile)
    } catch (e: java.io.IOException) {
        com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log("recording", "journal write failed: ${e.javaClass.simpleName}")
        false
    } catch (e: SecurityException) {
        false
    }

    fun read(): RecordingJournalEntry? = readFile(journalFile)

    fun clear() {
        journalFile.delete()
    }

    /** Clears the journal only if it is still [meetingId]'s: a recording started meanwhile has
     * written its own, which must stay. */
    fun clearIf(meetingId: String) {
        if (read()?.meetingId == meetingId) clear()
    }

    /**
     * Before a new recording writes its journal: if the journal holds a different recording that
     * still needs recovering, move it aside instead of letting the new one overwrite it.
     * Returns true when one was parked. Best effort, like [write].
     */
    fun parkIfOther(newMeetingId: String): Boolean {
        val existing = read() ?: return false
        if (existing.meetingId == newMeetingId || existing.state !in RECOVERABLE_STATES) return false
        return try {
            parkedDir.mkdirs()
            val target = File(parkedDir, "${safeName(existing.meetingId)}.json")
            journalFile.renameTo(target) || run {
                target.writeText(toJson(existing).toString())
                journalFile.delete()
                true
            }
        } catch (e: Exception) {
            com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log("recording", "journal park failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /** Interrupted recordings parked by [parkIfOther], oldest first. Unreadable files are skipped. */
    fun parked(): List<RecordingJournalEntry> =
        (parkedDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
            .sortedBy { it.lastModified() }
            .mapNotNull { readFile(it) }

    fun clearParked(meetingId: String) {
        File(parkedDir, "${safeName(meetingId)}.json").delete()
    }

    private fun readFile(file: File): RecordingJournalEntry? {
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            RecordingJournalEntry(
                meetingId = json.getString("meetingId"),
                title = json.getString("title"),
                recordingType = json.getString("recordingType"),
                audioFilePath = json.getString("audioFilePath"),
                lastKnownDurationMs = json.getLong("lastKnownDurationMs"),
                markerCount = json.optInt("markerCount", 0),
                noteCount = json.optInt("noteCount", 0),
                lastHeartbeatAtMs = json.getLong("lastHeartbeatAtMs"),
                state = json.getString("state"),
                language = json.optString("language").takeIf { it.isNotBlank() },
                speakerCountPreference = if (json.has("speakerCountPreference")) json.optInt("speakerCountPreference") else null,
                noteId = json.optString("noteId").takeIf { it.isNotBlank() },
                customContext = json.optString("customContext").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun toJson(entry: RecordingJournalEntry) = JSONObject().apply {
        put("meetingId", entry.meetingId)
        put("title", entry.title)
        put("recordingType", entry.recordingType)
        put("audioFilePath", entry.audioFilePath)
        put("lastKnownDurationMs", entry.lastKnownDurationMs)
        put("markerCount", entry.markerCount)
        put("noteCount", entry.noteCount)
        put("lastHeartbeatAtMs", entry.lastHeartbeatAtMs)
        put("state", entry.state)
        entry.language?.let { put("language", it) }
        entry.speakerCountPreference?.let { put("speakerCountPreference", it) }
        entry.noteId?.let { put("noteId", it) }
        entry.customContext?.let { put("customContext", it) }
    }

    /** Meeting ids are UUIDs; anything else is reduced to a safe file name. */
    private fun safeName(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        /** Journal states that mean audio was captured but may have no meeting row yet. */
        val RECOVERABLE_STATES: Set<String> = setOf(
            RecordingState.RECORDING, RecordingState.PAUSED, RecordingState.STOPPING,
            RecordingState.SAVING, RecordingState.SAVE_FAILED
        ).map { it.name }.toSet()
    }
}
