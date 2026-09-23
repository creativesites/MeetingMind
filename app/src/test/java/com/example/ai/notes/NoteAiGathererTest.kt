package com.example.ai.notes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteAiGathererTest {

    private lateinit var db: MeetMindDatabase
    private lateinit var notes: NoteRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MeetMindDatabase::class.java).allowMainThreadQueries().build()
        notes = NoteRepository(ApplicationProvider.getApplicationContext(), db)
    }
    @After fun tearDown() = db.close()

    private fun para(text: String) = NoteBlock(NoteRepository.newId("block"), "", 0, NoteBlockType.PARAGRAPH, RichText.plain(text))

    @Test
    fun `in Internet mode a notebook run leaves private notes on the phone`() = runBlocking {
        val nb = notes.createNotebook("Church", NotebookSpace.FAITH)
        notes.createNote(title = "Sermon", notebookId = nb.id, isPrivate = false, initialBlocks = listOf(para("Grace is a gift")))
        notes.createNote(RecordingType.PRAYER, title = "My prayer", notebookId = nb.id, isPrivate = true, initialBlocks = listOf(para("Please heal my father")))

        val cloud = NoteAiGatherer(db).gather(NoteAiTarget.NOTEBOOK, nb.id, NoteAiTool.SUMMARIZE, null, 10_000, sendingToCloud = true)!!
        assertEquals(listOf("Grace is a gift"), cloud.passages.map { it.text })
        assertTrue(cloud.scope.contains("1 private note kept on this phone"))
        assertTrue(cloud.faith)

        val local = NoteAiGatherer(db).gather(NoteAiTarget.NOTEBOOK, nb.id, NoteAiTool.SUMMARIZE, null, 10_000, sendingToCloud = false)!!
        assertEquals(2, local.passages.size)
        assertFalse(local.scope.contains("private"))
    }

    @Test
    fun `a single note offers its own template sections to organise into`() = runBlocking {
        val sermon = notes.createNote(RecordingType.SERMON, title = "Sunday")
        val g = NoteAiGatherer(db).gather(NoteAiTarget.NOTE, sermon.id, NoteAiTool.ORGANIZE, null, 10_000, sendingToCloud = true)!!
        assertTrue(g.sections.any { it.key == "key_points" })
        assertEquals("This note", g.scope)
        assertTrue(NoteAiGatherer.organizeSections(RecordingType.GENERAL).map { it.key }.contains("next_steps"))
        assertEquals(null, NoteAiGatherer(db).gather(NoteAiTarget.NOTE, "missing", NoteAiTool.SUMMARIZE, null, 10_000, false))
    }

    @Test
    fun `related notes find a note that cites the same chapter`() = runBlocking {
        val a = notes.startDevotional(com.example.core.scripture.ScriptureReferenceParser.parse("Romans 8:28")!!)
        val b = notes.startDevotional(com.example.core.scripture.ScriptureReferenceParser.parse("Romans 8:1")!!)
        notes.createNote(title = "Groceries", initialBlocks = listOf(para("milk eggs bread")))
        val related = notes.relatedNotes(a.id)
        assertEquals(b.id, related.first().first.id)
        assertTrue(related.first().second.reasons.any { it.contains("Romans 8") })
    }
}
