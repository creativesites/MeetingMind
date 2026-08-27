package com.example.core.audio

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
    val state: String
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

    fun write(entry: RecordingJournalEntry) {
        val json = JSONObject().apply {
            put("meetingId", entry.meetingId)
            put("title", entry.title)
            put("recordingType", entry.recordingType)
            put("audioFilePath", entry.audioFilePath)
            put("lastKnownDurationMs", entry.lastKnownDurationMs)
            put("markerCount", entry.markerCount)
            put("noteCount", entry.noteCount)
            put("lastHeartbeatAtMs", entry.lastHeartbeatAtMs)
            put("state", entry.state)
        }
        val tempFile = File(journalFile.parentFile, "${journalFile.name}.tmp")
        tempFile.writeText(json.toString())
        tempFile.renameTo(journalFile)
    }

    fun read(): RecordingJournalEntry? {
        if (!journalFile.exists()) return null
        return try {
            val json = JSONObject(journalFile.readText())
            RecordingJournalEntry(
                meetingId = json.getString("meetingId"),
                title = json.getString("title"),
                recordingType = json.getString("recordingType"),
                audioFilePath = json.getString("audioFilePath"),
                lastKnownDurationMs = json.getLong("lastKnownDurationMs"),
                markerCount = json.optInt("markerCount", 0),
                noteCount = json.optInt("noteCount", 0),
                lastHeartbeatAtMs = json.getLong("lastHeartbeatAtMs"),
                state = json.getString("state")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        journalFile.delete()
    }
}
