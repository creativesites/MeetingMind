package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.work.ItemsFixture
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

/** The Notes library's SQL (U-2): FTS ranking and privacy, chip filtering, keyset paging, over a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteLibraryStoreTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var store: NoteLibraryStore

    @Before fun setup() {
        db = ItemsFixture.database()
        store = NoteLibraryStore(db)
        runBlocking {
            db.notebookDao().upsert(NotebookEntity("work", "Launch", "WORK", null, null, 1, 1, null, 0))
            db.notebookDao().upsert(NotebookEntity("faith", "Church", "FAITH", null, null, 1, 1, null, 0))
            db.notebookDao().upsert(NotebookEntity("home", "Home", "PERSONAL", null, null, 1, 1, null, 0))
        }
    }

    @After fun tearDown() = db.close()

    private fun note(
        id: String, title: String, body: String = "", workflow: String = "GENERAL", notebook: String? = "home", updated: Long = 1_000,
        pinned: Boolean = false, private: Boolean = false, archived: Long? = null, deleted: Long? = null, draft: Boolean = false
    ) = runBlocking {
        db.noteDao().upsert(NoteEntity(id, title, workflow, notebook, updated, updated, null, pinned, private, "OPEN", null, "{}", archived, body, isDraft = draft, deletedAt = deleted))
    }

    private fun ids(q: LibraryQuery, size: Int = 100) = runBlocking { store.page(q, null, size) }.map { it.id }

    // ---------------------------------------------------------------- search

    @Test fun `a title match ranks above a body match, then the newest edit`() {
        note("body-new", "Weekly plan", "we talked about covenant at length", updated = 9_000)
        note("title-old", "Covenant notes", "nothing here", updated = 1_000)
        note("title-new", "Covenant study", "more", updated = 5_000)
        note("body-old", "Misc", "the covenant again", updated = 2_000)
        assertEquals(listOf("title-new", "title-old", "body-new", "body-old"), ids(LibraryQuery(search = "covenant")))
    }

    @Test fun `search matches word prefixes and is case-insensitive`() {
        note("a", "Forgiveness", "")
        note("b", "Other", "")
        assertEquals(listOf("a"), ids(LibraryQuery(search = "FORGIV")))
        assertEquals(emptyList<String>(), ids(LibraryQuery(search = "zzz")))
    }

    @Test fun `search stays in step as notes are edited and deleted`() {
        note("a", "Alpha", "grace abounds")
        assertEquals(listOf("a"), ids(LibraryQuery(search = "grace")))
        note("a", "Alpha", "mercy only")
        assertEquals(emptyList<String>(), ids(LibraryQuery(search = "grace")))
        assertEquals(listOf("a"), ids(LibraryQuery(search = "mercy")))
        runBlocking { db.noteDao().delete("a") }
        assertEquals(emptyList<String>(), ids(LibraryQuery(search = "mercy")))
    }

    @Test fun `drafts, trash and archive stay out of the live library, search included`() {
        note("live", "Zeal", "")
        note("draft", "Zeal draft", "", draft = true)
        note("gone", "Zeal gone", "", deleted = 5)
        note("old", "Zeal old", "", archived = 5)
        assertEquals(listOf("live"), ids(LibraryQuery(search = "zeal")))
        assertEquals(listOf("old"), ids(LibraryQuery(mode = LibraryMode.ARCHIVED, search = "zeal")))
        assertEquals(listOf("gone"), ids(LibraryQuery(mode = LibraryMode.TRASH, search = "zeal")))
    }

    @Test fun `with the Faith lock on, private and Faith notes are left out of search but not of the list`() {
        note("pub", "Prayer plan for the office", "", workflow = "GENERAL")
        note("secret", "Prayer journal", "I confess", workflow = "JOURNAL", private = true)
        note("faith", "Prayer for healing", "", workflow = "PRAYER", notebook = "faith")
        val open = LibraryQuery(search = "prayer")
        assertEquals(setOf("pub", "secret", "faith"), ids(open).toSet())
        val locked = open.copy(hideLocked = true)
        assertEquals(listOf("pub"), ids(locked))
        // Their text is not found through the body either.
        assertEquals(emptyList<String>(), ids(LibraryQuery(search = "confess", hideLocked = true)))
        // Browsing still lists them, with no preview.
        val browse = runBlocking { store.page(LibraryQuery(hideLocked = true), null, 50) }
        assertEquals(setOf("pub", "secret", "faith"), browse.map { it.id }.toSet())
        assertEquals("", browse.first { it.id == "secret" }.preview)
        assertEquals("", browse.first { it.id == "faith" }.preview)
    }

    @Test fun `recording transcripts are searched only when asked`() {
        note("rec", "Team call", "")
        runBlocking {
            db.meetingDao().insertMeeting(MeetingEntity("m", "Team call", 1, 90_000, "LOCAL_RECORDING", null, "READY", 1, "en", null, noteId = "rec"))
            db.transcriptDao().insertSegments(listOf(TranscriptSegmentEntity("s", "m", null, null, 0, 1, "we agreed on the budget", null)))
        }
        assertEquals(emptyList<String>(), ids(LibraryQuery(search = "budget")))
        assertEquals(listOf("rec"), ids(LibraryQuery(search = "budget", includeTranscripts = true)))
    }

    @Test fun `a body hit shows the matched words in the preview`() {
        note("a", "Plain", "x ".repeat(120) + "the lighthouse keeper waved")
        val item = runBlocking { store.page(LibraryQuery(search = "lighthouse"), null, 10) }.single()
        assertTrue(item.preview, "lighthouse" in item.preview)
        assertEquals(listOf("lighthouse"), item.highlight)
    }

    // ---------------------------------------------------------------- chips

    @Test fun `Work counts meetings even in a personal notebook, and project notes`() {
        note("meeting", "Standup", workflow = "MEETING", notebook = "home")
        note("brief", "Brief", workflow = "GENERAL", notebook = "work")
        note("groceries", "Groceries", notebook = "home")
        note("sermon", "Sunday", workflow = "SERMON", notebook = "faith")
        assertEquals(setOf("meeting", "brief"), ids(LibraryQuery(chips = setOf(LibraryChip.WORK))).toSet())
    }

    @Test fun `Faith counts faith workflows and faith notebooks but never a Work workflow`() {
        note("sermon", "Sunday", workflow = "SERMON", notebook = "home")
        note("plain", "Church picnic", notebook = "faith")
        note("meeting", "Elders meeting", workflow = "MEETING", notebook = "faith")
        note("groceries", "Groceries", notebook = "home")
        assertEquals(setOf("sermon", "plain"), ids(LibraryQuery(chips = setOf(LibraryChip.FAITH))).toSet())
    }

    @Test fun `Bible is the study journal`() {
        note("study", "John 3", workflow = "BIBLE_STUDY", notebook = "faith")
        note("sermon", "Sunday", workflow = "SERMON", notebook = "faith")
        assertEquals(listOf("study"), ids(LibraryQuery(chips = setOf(LibraryChip.BIBLE))))
    }

    @Test fun `spaces add up and Recordings and Pinned narrow`() {
        note("meeting", "Standup", workflow = "MEETING", pinned = true)
        note("sermon", "Sunday", workflow = "SERMON")
        note("groceries", "Groceries")
        runBlocking { db.meetingDao().insertMeeting(MeetingEntity("m", "Standup", 1, 60_000, "LOCAL_RECORDING", null, "READY", 1, "en", null, noteId = "meeting")) }
        val both = LibraryQuery(chips = setOf(LibraryChip.WORK, LibraryChip.FAITH))
        assertEquals(setOf("meeting", "sermon"), ids(both).toSet())
        assertEquals(listOf("meeting"), ids(LibraryQuery(chips = setOf(LibraryChip.RECORDINGS))))
        assertEquals(listOf("meeting"), ids(LibraryQuery(chips = setOf(LibraryChip.PINNED))))
        assertEquals(emptyList<String>(), ids(both.copy(chips = both.chips + LibraryChip.RECORDINGS)).filter { it == "sermon" })
        assertEquals(listOf("meeting"), ids(both.copy(chips = both.chips + LibraryChip.PINNED)))
    }

    @Test fun `type filters and tags narrow too, and chips apply inside a search`() {
        note("idea", "Spark", workflow = "IDEA")
        note("memo", "Spark memo", workflow = "VOICE_MEMO")
        assertEquals(listOf("idea"), ids(LibraryQuery(types = setOf(RecordingType.IDEA))))
        assertEquals(setOf("idea", "memo"), ids(LibraryQuery(types = setOf(RecordingType.IDEA, RecordingType.VOICE_MEMO))).toSet())
        assertEquals(listOf("memo"), ids(LibraryQuery(search = "spark", types = setOf(RecordingType.VOICE_MEMO))))
        runBlocking {
            db.noteDao().insertTag(com.craftflowtechnologies.meetingmind.core.database.TagEntity("t", "urgent"))
            db.noteDao().insertNoteTag(com.craftflowtechnologies.meetingmind.core.database.NoteTagCrossRef("memo", "t"))
        }
        assertEquals(listOf("memo"), ids(LibraryQuery(tagId = "t")))
    }

    @Test fun `a recording shows its length and processing state on the row`() {
        note("rec", "Call")
        runBlocking {
            db.meetingDao().insertMeeting(MeetingEntity("m1", "Call", 1, 600_000, "LOCAL_RECORDING", null, "PROCESSING", 1, "en", null, noteId = "rec"))
            db.meetingDao().insertMeeting(MeetingEntity("m2", "Call 2", 1, 120_000, "LOCAL_RECORDING", null, "READY", 1, "en", null, noteId = "rec"))
        }
        val item = runBlocking { store.page(LibraryQuery(), null, 10) }.single()
        assertTrue(item.hasRecording)
        assertEquals(720_000L, item.durationMs)
        assertEquals(NoteTranscriptStatus.Processing, item.transcript)
    }

    // ---------------------------------------------------------------- paging

    @Test fun `a thousand notes page without a gap or a repeat, pinned first, in every sort`() {
        runBlocking {
            for (i in 0 until 1000) db.noteDao().upsert(
                NoteEntity("n%04d".format(i), if (i % 7 == 0) "" else "Note ${(i * 31) % 97}", "GENERAL", "home", i.toLong(), 1_000L + i / 5, if (i % 3 == 0) 5_000L - i else null,
                    i % 40 == 0, false, "OPEN", null, "{}", null, "body $i")
            )
        }
        for (sort in NoteSort.entries) {
            val q = LibraryQuery(sort = sort)
            val got = mutableListOf<String>()
            var after: Cursor? = null
            while (true) {
                val page = runBlocking { store.page(q, after, 30) }
                assertTrue(page.size <= 30)
                got += page.map { it.id }
                if (page.size < 30) break
                after = page.last().cursor
            }
            assertEquals("$sort covers every note once", 1000, got.size)
            assertEquals("$sort has no repeats", 1000, got.toSet().size)
            if (sort != NoteSort.TITLE) {
                val firstUnpinned = got.indexOfFirst { it.removePrefix("n").toInt() % 40 != 0 }
                assertTrue("$sort pinned notes lead", got.take(firstUnpinned).all { it.removePrefix("n").toInt() % 40 == 0 } && firstUnpinned == 25)
            }
        }
    }

    @Test fun `search pages by offset and stops at the cap`() {
        runBlocking { for (i in 0 until 250) db.noteDao().upsert(NoteEntity("s%03d".format(i), "Hit $i", "GENERAL", "home", 1, 1_000L + i, null, false, false, "OPEN", null, "{}", null, "")) }
        val q = LibraryQuery(search = "hit")
        val got = mutableListOf<LibraryItem>()
        var after: Cursor? = null
        while (true) {
            val page = runBlocking { store.page(q, after, 30) }
            got += page
            if (page.size < 30) break
            after = page.last().cursor
        }
        assertEquals(NoteLibraryStore.SEARCH_MAX_RESULTS, got.size)
        assertEquals(got.size, got.map { it.id }.toSet().size)
        assertFalse(got.isEmpty())
        // Newest edit first within the same rank.
        assertEquals("s249", got.first().id)
    }
}
