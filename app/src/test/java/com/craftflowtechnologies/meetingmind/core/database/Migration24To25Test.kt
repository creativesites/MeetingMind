package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 24 → 25: legacy note-level marks move to their recording only when the owner is certain; every
 * other mark is kept, unassigned or malformed, and no note's JSON is rewritten.
 *
 * The database is built at schema 24 from the exported snapshot, filled with the shapes the app
 * has actually written, then opened through the real migration chain (Room validates the result).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration24To25Test {
    private lateinit var context: Context
    private val dbName = "migration-24-25-test.db"
    private var migrated: MeetMindDatabase? = null
    private val notesBefore = mutableMapOf<String, String?>()

    private fun path() = context.getDatabasePath(dbName).path

    private fun marks(vararg elements: Any): String = JSONObject().put("marks", JSONArray(elements.toList()).toString()).toString()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 24)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            fun note(id: String, metadata: String) {
                db.execSQL(
                    "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, pinned, isPrivate, status, metadataJson, plainText) " +
                        "VALUES (?, 'T', 'SERMON', NULL, 10, 10, 0, 0, 'OPEN', ?, '')",
                    arrayOf<Any?>(id, metadata)
                )
                notesBefore[id] = metadata
            }
            fun meeting(id: String, noteId: String?, createdAt: Long) {
                db.execSQL(
                    "INSERT INTO meetings (id, title, createdAt, durationMs, source, status, participantCount, language, updatedAt, recordingType, processingProfile, processingVersion, noteId) " +
                        "VALUES (?, 'Part', ?, 0, 'LOCAL_RECORDING', 'READY', 2, 'en', ?, 'SERMON', 'OFFLINE', 0, ?)",
                    arrayOf<Any?>(id, createdAt, createdAt, noteId)
                )
            }

            // One recording, with well-formed marks, an unknown kind, a bare string, and an entry with no timestamp.
            note("nA", marks(
                JSONObject().put("kind", "KEY").put("at", 61_000),
                JSONObject().put("kind", "QUESTION").put("at", 120_000).put("text", "Who owns it?"),
                JSONObject().put("kind", "FUTURE_KIND").put("at", 5_000).put("text", "later"),
                "oops",
                JSONObject().put("kind", "NOTE")
            ))
            meeting("mA", "nA", 100)

            // Two recordings. Created in the order mB2 then mB1, though mB1 was attached first in the list.
            note("nB", marks(JSONObject().put("kind", "ACTION").put("at", 5_000)))
            meeting("mB1", "nB", 200)
            meeting("mB2", "nB", 100)

            // A list that does not parse at all, and a marks value that is an object, not an array.
            note("nC", """{"marks":"[{bad"}""")
            note("nF", """{"marks":"{\"kind\":\"KEY\"}"}""")

            // A note that is not JSON at all, and a note with marks but no recording.
            note("nD", "{not json")
            note("nE", marks(JSONObject().put("kind", "KEY").put("at", 1)))
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    /** Opened once, on first use, through the real migration chain. */
    private val db: MeetMindDatabase by lazy {
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build().also { migrated = it }
    }

    private fun <T> rows(sql: String, map: (android.database.Cursor) -> T): List<T> =
        db.openHelper.readableDatabase.query(sql).use { c -> buildList { while (c.moveToNext()) add(map(c)) } }

    @Test
    fun aSingleRecordingsMarksMoveToItKeepingTimeTextAndOrder() {
        val got = rows("SELECT kind, timestampMs, text, sequence FROM recording_markers WHERE meetingId = 'mA' ORDER BY sequence") {
            listOf(it.getString(0), it.getLong(1).toString(), it.getString(2) ?: "", it.getLong(3).toString())
        }
        assertEquals(
            listOf(
                listOf("KEY", "61000", "", "0"),
                listOf("QUESTION", "120000", "Who owns it?", "1"),
                listOf("FUTURE_KIND", "5000", "later", "2")
            ),
            got
        )
    }

    @Test
    fun malformedEntriesAreKeptNotDropped() {
        val got = rows("SELECT ordinal, status, rawJson FROM legacy_note_marks WHERE noteId = 'nA' ORDER BY ordinal") {
            listOf(it.getInt(0).toString(), it.getString(1), it.getString(2))
        }
        assertEquals(
            listOf(listOf("3", "MALFORMED", "oops"), listOf("4", "MALFORMED", """{"kind":"NOTE"}""")),
            got
        )
    }

    @Test
    fun aListThatDoesNotParseIsKeptWhole() {
        val got = rows("SELECT ordinal, status, rawJson FROM legacy_note_marks WHERE noteId IN ('nC', 'nF') ORDER BY noteId") {
            listOf(it.getInt(0).toString(), it.getString(1), it.getString(2))
        }
        assertEquals(listOf(listOf("-1", "MALFORMED", "[{bad"), listOf("-1", "MALFORMED", """{"kind":"KEY"}""")), got)
    }

    @Test
    fun ambiguousMarksOnATwoPartNoteStayUnassignedAndAttachToNoRecording() {
        val unassigned = rows("SELECT status, rawKind, atMs FROM legacy_note_marks WHERE noteId = 'nB'") {
            listOf(it.getString(0), it.getString(1), it.getLong(2).toString())
        }
        assertEquals(listOf(listOf("UNASSIGNED", "ACTION", "5000")), unassigned)

        val placed = rows("SELECT COUNT(*) FROM recording_markers WHERE meetingId IN ('mB1', 'mB2')") { it.getInt(0) }
        assertEquals(listOf(0), placed)
    }

    @Test
    fun aNoteWithoutRecordingKeepsItsMarksAsNoRecording() {
        val got = rows("SELECT status, rawKind, atMs FROM legacy_note_marks WHERE noteId = 'nE'") {
            listOf(it.getString(0), it.getString(1), it.getLong(2).toString())
        }
        assertEquals(listOf(listOf("NO_RECORDING", "KEY", "1")), got)
    }

    @Test
    fun recordingOrderIsBackfilledFromCreationTime() {
        val got = rows("SELECT id, recordingOrder FROM meetings ORDER BY id") { it.getString(0) + "=" + it.getInt(1) }
        // mB2 was created first (100), so it is part 0; mB1 (200) is part 1.
        assertEquals(listOf("mA=0", "mB1=1", "mB2=0"), got)
    }

    @Test
    fun noNoteJsonIsRewrittenAndUnreadableNotesAreUntouched() {
        val after = rows("SELECT id, metadataJson FROM notes") { it.getString(0) to it.getString(1) }.toMap()
        assertEquals(notesBefore, after)
    }

    @Test
    fun runningTheMigrationAgainAddsNothing() {
        val before = rows("SELECT COUNT(*) FROM recording_markers") { it.getInt(0) } + rows("SELECT COUNT(*) FROM legacy_note_marks") { it.getInt(0) }
        LegacyMarkMigration.run(db.openHelper.writableDatabase)
        val after = rows("SELECT COUNT(*) FROM recording_markers") { it.getInt(0) } + rows("SELECT COUNT(*) FROM legacy_note_marks") { it.getInt(0) }
        assertEquals(before, after)
    }

    @Test
    fun theDatabaseOpensWithTheNewTables() {
        assertTrue(MeetMindDatabase.VERSION >= 25)
        assertTrue(db.openHelper.readableDatabase.version >= 25)
        assertTrue(rows("SELECT name FROM sqlite_master WHERE name = 'recording_markers'") { it.getString(0) }.isNotEmpty())
    }
}
