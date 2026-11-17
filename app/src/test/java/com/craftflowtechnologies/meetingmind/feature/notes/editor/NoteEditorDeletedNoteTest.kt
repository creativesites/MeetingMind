package com.craftflowtechnologies.meetingmind.feature.notes.editor

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Crash audit: a save that lands after the note was deleted (from another screen, or by the empty-note
 * cleanup) used to throw a FOREIGN KEY error from saveBlocks and crash the app. Now the editor treats the
 * note as gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteEditorDeletedNoteTest {
    private lateinit var app: Application
    private lateinit var db: MeetMindDatabase
    private lateinit var notes: NoteRepository

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor(Executors.newFixedThreadPool(2)).setTransactionExecutor(Executors.newFixedThreadPool(1)).build()
        MeetMindDatabase.setInstanceForTest(db)
        notes = NoteRepository(app, db)
    }

    // No db.close(): the view model is never cleared here, so nothing may still write to a closed database.
    @After fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
    }

    @Test fun `a save after the note was deleted does not throw`() {
        val id = runBlocking {
            val note = notes.createNote(title = "Gone soon")
            notes.saveBlocks(note.id, listOf(NoteBlock(NoteRepository.newId("block"), note.id, 0, NoteBlockType.PARAGRAPH, RichText.plain("Words"))))
            note.id
        }
        val vm = NoteEditorViewModel(app, id)
        val deadline = System.currentTimeMillis() + 30_000
        while (!vm.loaded.value && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper(20, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
        assertTrue("the editor loaded", vm.loaded.value)

        runBlocking { notes.deleteNote(id) }
        runBlocking { vm.flush() } // threw SQLiteConstraintException (FOREIGN KEY) before the fix
        runBlocking { vm.flush() } // and the editor now knows the note is gone

        assertNull(runBlocking { notes.getNote(id) })
    }
}
