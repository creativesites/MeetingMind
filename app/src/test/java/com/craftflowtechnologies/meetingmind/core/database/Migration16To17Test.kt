package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 16 → 17: work columns on people, tasks, speakers, notebooks and meetings; every row survives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration16To17Test {
    private lateinit var context: Context
    private val dbName = "migration-16-17-test.db"
    private var migrated: MeetMindDatabase? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 16)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO people (id, name, relationship, notes, createdAt, updatedAt, deletedAt) VALUES ('p1', 'Mary', 'Friend', '', 1, 1, NULL)")
            db.execSQL(
                "INSERT INTO tasks (id, title, notes, kind, dueAt, remindAt, repeat, doneAt, personId, noteId, blockId, meetingId, startMs, scripture, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('t1', 'Call Mary', '', 'TASK', NULL, NULL, 'NONE', NULL, 'p1', NULL, NULL, NULL, NULL, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, language, summaryText, updatedAt, recordingType, processingProfile, processingVersion) " +
                    "VALUES ('m1', 'Review', 5, 1000, 'LOCAL_RECORDING', NULL, 'READY', 2, 'en', NULL, 7, 'MEETING', 'OFFLINE', 1)"
            )
            db.execSQL("INSERT INTO speakers (id, meetingId, speakerIndex, originalLabel, customName, colorHex, confidence) VALUES ('s1', 'm1', 0, 'Speaker 1', 'Speaker 1', '#fff', NULL)")
            db.execSQL("INSERT INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder, deletedAt) VALUES ('nb', 'Acme', 'WORK', NULL, NULL, 1, 1, NULL, 0, NULL)")
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    private fun open(): MeetMindDatabase =
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries().build().also { migrated = it }

    @Test
    fun rowsSurviveWithWorkDefaults() = runBlocking {
        val db = open()
        val mary = db.peopleDao().getById("p1")!!
        assertEquals("Mary", mary.name)
        assertEquals("PERSON", mary.kind)
        assertEquals("[]", mary.emailsJson)
        assertFalse(mary.isSelf)
        assertNull(mary.space)
        val task = db.taskDao().getById("t1")!!
        assertFalse(task.waitingOn)
        assertNull(task.space)
        assertNull(db.speakerDao().getSpeakersForMeetingDirect("m1").single().personId)
        assertEquals("NOTEBOOK", db.notebookDao().getById("nb")!!.kind)
    }

    @Test
    fun oldRecordingsDoNotAskForAWrapUp() = runBlocking {
        val db = open()
        assertNotNull(db.meetingDao().getMeetingById("m1")!!.reviewedAt)
    }
}
