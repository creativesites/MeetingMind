package com.craftflowtechnologies.meetingmind.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Copies the legacy note-level marks (`notes.metadataJson["marks"]`, a JSON array held as text)
 * into the source-owned tables (schema 25). Runs inside the 24→25 migration.
 *
 * The rules are deliberately conservative:
 * - A note with exactly one recording: each well-formed mark moves to that recording, keeping its
 *   timestamp, text and order. There is only one candidate, so nothing is guessed.
 * - A note with several recordings, or none: marks are kept in [LegacyNoteMarkEntity] as
 *   UNASSIGNED or NO_RECORDING. They are never placed by timestamp, recording order or proximity
 *   to transcript segments; the person assigns them.
 * - Unknown kinds keep their raw text. A malformed entry, or a list that does not parse, is kept
 *   in legacy_note_marks with its raw JSON.
 * - `notes.metadataJson` is never written, so the original data survives this migration intact.
 * - Re-running adds nothing: ids are derived from the note and position, and inserts ignore clashes.
 */
internal object LegacyMarkMigration {
    const val KEY = "marks"

    private sealed interface Legacy {
        data object None : Legacy
        data class Broken(val raw: String) : Legacy
        class Entries(val array: JSONArray) : Legacy
    }

    fun run(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
        val notes = db.query("SELECT id, metadataJson FROM notes").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to if (c.isNull(1)) null else c.getString(1)) }
        }
        for ((noteId, json) in notes) {
            when (val legacy = legacyMarks(json)) {
                Legacy.None -> Unit
                is Legacy.Broken -> insertLegacy(db, noteId, -1, "MALFORMED", null, null, null, legacy.raw, now)
                is Legacy.Entries -> migrateNote(db, noteId, legacy.array, now)
            }
        }
    }

    private fun migrateNote(db: SupportSQLiteDatabase, noteId: String, marks: JSONArray, now: Long) {
        val recordings = db.query(
            "SELECT id FROM meetings WHERE noteId = ? ORDER BY recordingOrder ASC, createdAt ASC, id ASC",
            arrayOf<Any?>(noteId)
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

        for (i in 0 until marks.length()) {
            val element = marks.opt(i)
            val obj = element as? JSONObject
            val at = obj?.let { if (it.has("at")) (it.opt("at") as? Number)?.toLong() else null }
            val rawKind = obj?.optString("kind")?.ifBlank { null }
            val text = obj?.optString("text")?.ifBlank { null }
            val rawJson = element.toString()
            when {
                obj == null || at == null ->
                    insertLegacy(db, noteId, i, "MALFORMED", rawKind, null, text, rawJson, now)
                recordings.size == 1 ->
                    insertMarker(db, recordings.single(), i, at, rawKind ?: "UNKNOWN", text, now)
                recordings.isEmpty() ->
                    insertLegacy(db, noteId, i, "NO_RECORDING", rawKind, at, text, rawJson, now)
                else ->
                    insertLegacy(db, noteId, i, "UNASSIGNED", rawKind, at, text, rawJson, now)
            }
        }
    }

    private fun legacyMarks(json: String?): Legacy {
        if (json == null) return Legacy.None
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            // The note's JSON is unreadable. Keep the whole value if it may have held marks, so it is not lost.
            return if (json.contains("\"$KEY\"")) Legacy.Broken(json) else Legacy.None
        }
        if (!root.has(KEY)) return Legacy.None
        val text = root.get(KEY).toString()
        return try {
            Legacy.Entries(JSONArray(text))
        } catch (e: JSONException) {
            Legacy.Broken(text)
        }
    }

    private fun insertMarker(db: SupportSQLiteDatabase, meetingId: String, ordinal: Int, atMs: Long, kind: String, text: String?, now: Long) {
        db.execSQL(
            "INSERT OR IGNORE INTO recording_markers " +
                "(id, meetingId, sequence, timestampMs, kind, text, sectionKey, resolvedSegmentId, createdAt, updatedAt, createdDuringRecording) " +
                "VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, 0)",
            arrayOf<Any?>("legacy:$meetingId:$ordinal", meetingId, ordinal.toLong(), atMs, kind, text, now, now)
        )
    }

    private fun insertLegacy(
        db: SupportSQLiteDatabase,
        noteId: String,
        ordinal: Int,
        status: String,
        rawKind: String?,
        atMs: Long?,
        text: String?,
        rawJson: String,
        now: Long
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO legacy_note_marks (id, noteId, ordinal, status, rawKind, atMs, text, rawJson, createdAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy:$noteId:$ordinal", noteId, ordinal, status, rawKind, atMs, text, rawJson, now)
        )
    }
}
