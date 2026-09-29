package com.example.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 15 → 16: people, tasks and full-text search are added; every existing row survives and is searchable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration15To16Test {
    private lateinit var context: Context
    private val dbName = "migration-15-16-test.db"
    private var migrated: MeetMindDatabase? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 15)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, eventDate, pinned, isPrivate, status, answeredAt, metadataJson, archivedAt, plainText, isDraft, deletedAt) " +
                    "VALUES ('n1', 'Abide in the vine', 'SERMON', NULL, 10, 20, NULL, 0, 0, 'OPEN', NULL, '{}', NULL, 'Grace comes before effort', 0, NULL)"
            )
            db.execSQL(
                "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, language, summaryText, updatedAt, recordingType, processingProfile, processingVersion) " +
                    "VALUES ('m1', 'Sunday service', 5, 1000, 'LOCAL_RECORDING', NULL, 'READY', 1, 'en', NULL, 5, 'SERMON', 'OFFLINE', 1)"
            )
            db.execSQL("INSERT INTO transcript_segments (id, meetingId, speakerId, speakerName, startMs, endMs, text, confidence, isUserEdited, sourceSegmentIdsJson, wordsJson) VALUES ('s1', 'm1', NULL, 'Pastor', 65000, 70000, 'Remain in me as I remain in you', NULL, 0, '[]', '[]')")
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    private fun open(): MeetMindDatabase =
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(MeetMindDatabase.MIGRATION_15_16)
            .allowMainThreadQueries().build().also { migrated = it }

    @Test
    fun existingNotesAndTranscriptsAreSearchable() = runBlocking {
        val db = open()
        assertEquals("Abide in the vine", db.noteDao().getById("n1")!!.title)
        assertEquals(listOf("n1"), db.searchDao().notes("grace*", 10).map { it.id })
        val hit = db.searchDao().segments("remain", 10).single()
        assertEquals(65000L, hit.startMs)
        assertEquals("Sunday service", hit.meetingTitle)
        assertTrue(hit.snippet.contains("Remain"))
    }

    @Test
    fun newAndEditedNotesStayIndexed() = runBlocking {
        val db = open()
        val n = db.noteDao().getById("n1")!!
        db.noteDao().upsert(n.copy(plainText = "Fruit that lasts"))
        assertTrue(db.searchDao().notes("grace", 10).isEmpty())
        assertEquals(1, db.searchDao().notes("fruit", 10).size)
    }

    @Test
    fun tasksAndPeopleWork() = runBlocking {
        val db = open()
        db.peopleDao().upsert(PersonEntity("p1", "Mary", "Friend", "", 1, 1))
        db.peopleDao().link(NotePersonCrossRef("n1", "p1"))
        db.taskDao().upsert(TaskEntity("t1", "Call Mary", "", "TASK", 100, 90, "NONE", null, "p1", "n1", null, null, null, null, 1, 1))
        val person = db.peopleDao().observeWithCounts().first().single()
        assertEquals(1, person.openTasks)
        assertEquals(1, person.noteCount)
        // Deleting the note keeps the task, without its link.
        db.noteDao().delete("n1")
        assertNull(db.taskDao().getById("t1")!!.noteId)
    }
}
