package com.craftflowtechnologies.meetingmind.feature.notes.editor

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Founder report (2026-11-18): a YouTube import failed, its retry filled the note, and the note was empty
 * again later. The editor opened on the failed note stayed in the back stack, never reloaded the retry's
 * notes, and saved its empty copy back over them. Here: fail, retry, reopen from the database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteEditorRetryKeepsContentTest {
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

    // No db.close(): the view models are never cleared here, so nothing may still write to a closed database.
    @After fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
    }

    private fun waitFor(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper(20, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
        assertTrue(what, condition())
    }

    private fun open(id: String): NoteEditorViewModel =
        NoteEditorViewModel(app, id).also { vm -> waitFor("the editor loaded") { vm.loaded.value } }

    /** A YouTube import that failed: the note holds only its recording card. */
    private fun failedImportNote(): String = runBlocking {
        val note = notes.createNote(title = "YouTube Video (abc)")
        notes.saveBlocks(note.id, listOf(
            NoteBlock(NoteRepository.newId("block"), note.id, 0, NoteBlockType.RECORDING,
                payload = mapOf(NoteBlock.PAYLOAD_MEETING_ID to "m-yt"), source = BlockSource.TRANSCRIPT, sectionKey = "recording")
        ))
        note.id
    }

    /** What the successful retry writes: the generated notes, after the recording. */
    private fun retrySucceeds(id: String) = runBlocking {
        notes.applyGeneratedSections(
            id,
            listOf(
                NoteBlock(NoteRepository.newId("block"), id, 0, NoteBlockType.HEADING_2, RichText.plain("Main theme"), source = BlockSource.AI, sectionKey = "notes"),
                NoteBlock(NoteRepository.newId("block"), id, 0, NoteBlockType.PARAGRAPH, RichText.plain("Grace is a gift, not a wage."), source = BlockSource.AI, sectionKey = "notes")
            ),
            emptyList(),
            setOf("notes")
        )
    }

    private fun storedText(id: String): List<String> =
        runBlocking { notes.getDocument(id)!!.blocks.sortedBy { it.position }.map { it.content.text } }

    @Test fun `the retry's notes survive the editor left open on the failed note`() {
        val id = failedImportNote()
        val stale = open(id)
        runBlocking { stale.flush() } // It pauses when the person opens the recording to retry.

        retrySucceeds(id)

        // The editor still open on the note picks the notes up...
        waitFor("the open editor reloads the retry's notes") { stale.blocks.value.any { it.content.text == "Grace is a gift, not a wage." } }
        // ...and its next save (pause, Back, being cleared) doesn't take them away.
        runBlocking { stale.flush() }
        assertTrue(storedText(id).toString(), "Grace is a gift, not a wage." in storedText(id))

        // Reopened later from the database: the notes are there.
        val reopened = open(id)
        assertTrue(reopened.blocks.value.any { it.content.text == "Grace is a gift, not a wage." })
        assertTrue(reopened.blocks.value.any { it.content.text == "Main theme" })
    }

    @Test fun `an editor that changes nothing never writes the note`() {
        val id = failedImportNote()
        val stale = open(id)
        retrySucceeds(id)
        // Saving before it has seen the change must not put the empty note back.
        runBlocking { stale.flush() }
        assertTrue(storedText(id).toString(), "Grace is a gift, not a wage." in storedText(id))
    }

    @Test fun `unsaved edits and notes written meanwhile both survive`() {
        val id = runBlocking {
            val note = notes.createNote(title = "Sunday")
            notes.saveBlocks(note.id, listOf(
                NoteBlock("rec", note.id, 0, NoteBlockType.RECORDING, payload = mapOf(NoteBlock.PAYLOAD_MEETING_ID to "m-yt"), source = BlockSource.TRANSCRIPT, sectionKey = "recording"),
                NoteBlock("mine", note.id, 1, NoteBlockType.PARAGRAPH, RichText.plain("My own line"))
            ))
            note.id
        }
        val editor = open(id)
        // The person types; the save is still pending when the retry's notes are written.
        editor.onTextChanged("mine", "My own line, edited", "My own line, edited".length)
        retrySucceeds(id)
        runBlocking { editor.flush() }

        val stored = storedText(id)
        assertTrue(stored.toString(), "My own line, edited" in stored)
        assertTrue(stored.toString(), "Grace is a gift, not a wage." in stored)
        assertTrue(stored.toString(), "My own line" !in stored)
        // The editor shows the merged note too, and nothing twice.
        val shown = editor.blocks.value.map { it.content.text }
        assertTrue(shown.toString(), "My own line, edited" in shown && "Grace is a gift, not a wage." in shown)
        assertEquals(shown.toString(), 1, shown.count { it == "My own line, edited" })
    }
}
