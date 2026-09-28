package com.example.core.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.database.NoteVersionSummary
import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteVersionsTest {

    private lateinit var database: MeetMindDatabase
    private lateinit var notes: NoteRepository
    private lateinit var versions: NoteVersionRepository
    private var now = 1_000_000_000L
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        notes = NoteRepository(context, database) { now }
        versions = NoteVersionRepository(database, notes) { now }
    }

    @After
    fun tearDown() = database.close()

    private fun block(noteId: String, text: String, type: NoteBlockType = NoteBlockType.PARAGRAPH) =
        NoteBlock(NoteRepository.newId("block"), noteId, 0, type, RichText.plain(text))

    @Test
    fun `the codec keeps every field of every block`() {
        val original = listOf(
            NoteBlock("a", "n", 0, NoteBlockType.CHECKLIST, RichText.plain("Ship it").applyStyle(InlineStyle.BOLD, 0, 4),
                payload = mapOf("k" to "v"), source = BlockSource.AI, sourceSegmentIds = listOf("s1", "s2"),
                sectionKey = "ai_actions", isUserEdited = true, indent = 2, checked = true),
            NoteBlock("b", "n", 1, NoteBlockType.HEADING_2, RichText.plain("Notes"))
        )
        assertEquals(original, VersionCodec.decode(VersionCodec.encode(original), "n"))
    }

    @Test
    fun `a snapshot identical to the newest is skipped, and empty notes aren't saved`() = runBlocking {
        val note = notes.createNote(title = "Plan")
        assertNull(versions.snapshot(note.id, VersionReason.MANUAL))
        notes.saveBlocks(note.id, listOf(block(note.id, "One")))
        assertNotNull(versions.snapshot(note.id, VersionReason.MANUAL))
        now += 1000
        assertNull(versions.snapshot(note.id, VersionReason.EDIT_SESSION))
        assertEquals(1, versions.observe(note.id).first().size)
    }

    @Test
    fun `restoring puts the version back and keeps the replaced one`() = runBlocking {
        val note = notes.createNote(title = "Plan")
        notes.saveBlocks(note.id, listOf(block(note.id, "Original"), block(note.id, "Keep", NoteBlockType.BULLET)))
        val v1 = versions.snapshot(note.id, VersionReason.BEFORE_AI, "Organise")!!
        now += 1000
        notes.saveBlocks(note.id, listOf(block(note.id, "Rewritten by AI")))
        notes.renameNote(note.id, "New title")

        versions.restore(v1)

        val doc = notes.getDocument(note.id)!!
        assertEquals(listOf("Original", "Keep"), doc.blocks.map { it.content.text })
        assertEquals(NoteBlockType.BULLET, doc.blocks[1].type)
        assertEquals("Plan", doc.note.title)
        val history = versions.observe(note.id).first()
        assertEquals(VersionReason.BEFORE_RESTORE.name, history.first().reason)
        assertEquals(listOf("Rewritten by AI"), versions.load(history.first().id)!!.blocks.map { it.content.text })
    }

    @Test
    fun `versions leave with their note when it is deleted for good`() = runBlocking {
        val note = notes.createNote(title = "Gone")
        notes.saveBlocks(note.id, listOf(block(note.id, "x")))
        versions.snapshot(note.id, VersionReason.MANUAL)
        notes.deleteNote(note.id)
        assertTrue(versions.observe(note.id).first().isEmpty())
    }

    private fun summary(id: String, age: Long, reason: VersionReason = VersionReason.EDIT_SESSION, size: Int = 10) =
        NoteVersionSummary(id, "n", now - age, reason.name, null, "t", size)

    @Test
    fun `retention keeps a day of everything, then hourly, daily and weekly`() {
        val list = listOf(
            summary("today-1", 1 * hour), summary("today-2", 2 * hour),
            // Two in the same hour two days ago: the newer one stays.
            summary("d2-a", 2 * day + 10 * 60_000), summary("d2-b", 2 * day + 20 * 60_000),
            // Two on the same day two weeks ago: one stays.
            summary("w2-a", 14 * day + hour), summary("w2-b", 14 * day + 2 * hour)
        )
        val pruned = VersionRetention.toPrune(list, now).toSet()
        assertTrue("today-1" !in pruned && "today-2" !in pruned)
        assertEquals(1, listOf("d2-a", "d2-b").count { it in pruned })
        assertTrue("d2-a" !in pruned)
        assertEquals(1, listOf("w2-a", "w2-b").count { it in pruned })
    }

    @Test
    fun `versions taken before big changes survive thinning for thirty days`() {
        val list = listOf(summary("edit", 3 * day + 5 * 60_000), summary("ai", 3 * day + 10 * 60_000, VersionReason.BEFORE_AI))
        assertTrue(VersionRetention.toPrune(list, now).isEmpty())
    }

    @Test
    fun `a note never keeps more than the cap, oldest unprotected first`() {
        val list = (0 until VersionRetention.MAX_PER_NOTE + 5).map { summary("v$it", it * 1000L) }
        val pruned = VersionRetention.toPrune(list, now)
        assertEquals(5, pruned.size)
        assertEquals((VersionRetention.MAX_PER_NOTE until VersionRetention.MAX_PER_NOTE + 5).map { "v$it" }.toSet(), pruned.toSet())
    }

    @Test
    fun `over budget, the oldest versions anywhere go first`() {
        val oldestFirst = listOf(summary("a", 30 * day, size = 40), summary("b", 20 * day, size = 40), summary("c", day, size = 40))
        assertEquals(listOf("a", "b"), VersionRetention.overBudget(oldestFirst, totalBytes = 120, budget = 50))
    }

    @Test
    fun `the diff shows what restoring brings back and removes`() {
        val lines = VersionDiff.lines(from = listOf("# Plan", "Keep", "Drop"), to = listOf("# Plan", "Keep", "Add"))
        assertEquals(
            listOf(DiffLine("# Plan", DiffLine.Change.SAME), DiffLine("Keep", DiffLine.Change.SAME), DiffLine("Drop", DiffLine.Change.REMOVED), DiffLine("Add", DiffLine.Change.ADDED)),
            lines
        )
    }
}
