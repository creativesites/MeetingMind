package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.DocumentPageEntity
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Documents in the Inbox (spec §R4): queued for reading when they arrive, proposed only once read, with
 * page-aware chunk selection and page citations. Also dismiss and filing, both undoable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentInboxTest {
    private lateinit var f: PulseFixture
    private lateinit var repo: InboxRepository
    private val enqueued = mutableListOf<String>()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setup() {
        f = PulseFixture()
        repo = InboxRepository(f.db, enqueueRead = { enqueued += it }) { f.now }
    }

    @After fun tearDown() { f.db.close() }

    private fun proposer(models: WorkModels? = null) = InboxProposer(f.db, models, false) { f.now }

    private fun pdf(title: String): InboxItemEntity = runBlocking {
        val tmp = java.io.File(context.filesDir, "inbox").apply { mkdirs() }.resolve("doc_${System.nanoTime()}.pdf").apply { writeText("%PDF") }
        repo.add(listOf(SharedPart(InboxKind.PDF, uri = "content://doc", title = title, mime = "application/pdf"))) { CopiedFile(tmp.absolutePath, tmp.name) }.single()
    }

    /** Stands in for the on-device reader: the document is read, page by page, and marked DONE. */
    private fun readDone(itemId: String, vararg pages: String) = runBlocking {
        f.db.documentPageDao().upsertPages(pages.mapIndexed { i, t -> DocumentPageEntity(itemId, i + 1, t, "TEXT_LAYER") })
        f.db.inboxDao().updateReadState(itemId, "DONE", pages.size, pages.size, pages.joinToString("\n\n"))
    }

    private fun stored(id: String) = runBlocking { f.db.inboxDao().get(id)!! }

    @Test fun aDocumentIsQueuedForReadingAndNothingIsProposedUntilItIsRead() = runBlocking {
        val item = pdf("Acme launch")
        assertEquals("PENDING", item.readStatus)
        assertEquals(listOf(item.id), enqueued)
        assertNull(proposer().propose(item)) // still reading
        assertEquals("NEW", stored(item.id).status)

        readDone(item.id, "Acme launch statement of work.")
        val filing = proposer().propose(stored(item.id))!!
        assertEquals(FilingTarget.PROJECT, filing.target)
        assertEquals("PROPOSED", stored(item.id).status)
    }

    @Test fun aPageMarkerTooBigForAnIntInTheDocumentTextDoesNotCrashTheProposal() = runBlocking {
        // Crash audit: OCR or a pasted citation can put "[p. 99999999999]" in a page's text; parsing it must not throw.
        val item = pdf("Acme launch")
        readDone(item.id, "Acme launch statement of work.\n[p. 99999999999]\nSee the appendix.")
        val filing = proposer().propose(stored(item.id))
        assertEquals("nb", filing!!.targetId)
    }

    @Test fun aDocumentThatCouldNotBeReadIsStillProposedByItsName() = runBlocking {
        val item = pdf("Acme launch")
        runBlocking { f.db.inboxDao().updateReadState(item.id, "LOCKED", null, 0, null) }
        assertEquals("nb", proposer().propose(stored(item.id))!!.targetId)
    }

    @Test fun theModelIsShownOnlyTheChosenPagesAndTheProposalCitesThePage() = runBlocking {
        val item = pdf("Acme launch")
        val filler = "Lunch options and office plants are discussed here at some length. ".repeat(15)
        val pages = Array(10) { i ->
            when (i) {
                0 -> "Statement of work between Acme Corporation and the supplier."
                8 -> "Section 4. The supplier shall provide staff SSO via Azure AD."
                else -> filler
            }
        }
        readDone(item.id, *pages)
        val model = FakeWorkModel(
            """{"action":"TASKS","target":"nb","title":"Acme launch","tasks":["Provide staff SSO via Azure AD"],"decision":null,"contact":null,"summary":"Statement of work between Acme Corporation and the supplier."}"""
        )
        val filing = proposer(WorkModels { model }).propose(stored(item.id))!!

        // Page 9 carries the requirement (a "shall") and is chosen; the filler pages in the middle are not.
        val prompt = model.prompts.single()
        assertTrue(prompt.contains("[p. 9]"))
        assertFalse(prompt.contains("[p. 5]"))

        assertEquals(listOf("Provide staff SSO via Azure AD"), filing.tasks)
        assertEquals(9, filing.citations["Provide staff SSO via Azure AD"]?.page)
        assertEquals("Statement of work between Acme Corporation and the supplier.", filing.summary)
        // The citation survives the round trip through the stored proposal.
        assertEquals(9, Filing.fromJson(stored(item.id).proposedJson)!!.citations.values.single().page)
    }

    @Test fun aClaimTheModelInventedFromTheDocumentIsStillDropped() = runBlocking {
        val item = pdf("Acme launch")
        readDone(item.id, "Statement of work for Acme launch.", "Deliveries are due in March.")
        val model = FakeWorkModel("""{"action":"TASKS","target":"nb","tasks":["Buy a spaceship"]}""")
        val filing = proposer(WorkModels { model }).propose(stored(item.id))!!
        assertEquals(FilingAction.NOTE, filing.action)
        assertTrue(filing.citations.isEmpty())
    }

    @Test fun dismissCanBeUndoneAndReturnsToItsProposal() = runBlocking {
        val item = repo.add(listOf(SharedPart(InboxKind.TEXT, text = "Pricing for Acme launch", title = "Pricing"))) { null }.single()
        proposer().propose(item)
        assertEquals("PROPOSED", stored(item.id).status)

        repo.dismiss(item.id)
        assertEquals("DISMISSED", stored(item.id).status)
        assertTrue(repo.restore(item.id))
        assertEquals("PROPOSED", stored(item.id).status)
        assertFalse(repo.restore(item.id)) // nothing left to restore
    }

    @Test fun anItemWithNoSuggestionIsRestoredAsNew() = runBlocking {
        val item = repo.add(listOf(SharedPart(InboxKind.TEXT, text = "Random thought about lunch"))) { null }.single()
        repo.dismiss(item.id)
        assertTrue(repo.restore(item.id))
        assertEquals("NEW", stored(item.id).status)
    }

    @Test fun unfilingPutsTheItemBackAndRemovesTheNoteItMade() = runBlocking {
        val item = repo.add(listOf(SharedPart(InboxKind.TEXT, text = "Pricing for Acme launch", title = "Pricing"))) { null }.single()
        val filing = proposer().propose(item)!!
        assertTrue(InboxFiler(context, f.db) { f.now }.file(item.id, filing))
        val noteId = JSONObject(stored(item.id).resultRefJson!!).getString("noteId")
        assertNotNull(f.db.noteDao().getById(noteId))

        assertTrue(InboxFiler(context, f.db) { f.now }.unfile(item.id))
        val back = stored(item.id)
        assertEquals("PROPOSED", back.status)
        assertNull(back.resultRefJson)
        assertNull(f.db.noteDao().getById(noteId))
        assertFalse(InboxFiler(context, f.db).unfile(item.id)) // only a filed item can be unfiled
    }

    @Test fun unfilingKeepsANoteThatAlreadyExisted() = runBlocking {
        // A recording's note exists before filing; filing moves it, and Undo must not delete it.
        val item = repo.add(listOf(SharedPart(InboxKind.TEXT, text = "We agreed the Acme launch date", title = "Launch date"))) { null }.single()
        val note = com.craftflowtechnologies.meetingmind.core.repository.NoteRepository(context, f.db) { f.now }.createNote(
            workflow = com.craftflowtechnologies.meetingmind.core.model.RecordingType.GENERAL,
            title = "Launch date",
            useTemplate = false
        )
        f.db.inboxDao().update(stored(item.id).copy(meetingId = "m_existing"))
        f.db.meetingDao().insertMeeting(
            com.craftflowtechnologies.meetingmind.core.database.MeetingEntity(
                id = "m_existing", title = "Launch date", createdAt = f.now, durationMs = 1000L, source = "IMPORTED_AUDIO",
                audioFilePath = null, status = "READY", participantCount = 1, language = "en", summaryText = null, noteId = note.id
            )
        )
        val filing = proposer().propose(stored(item.id))!!
        InboxFiler(context, f.db) { f.now }.file(item.id, filing)
        assertTrue(InboxFiler(context, f.db) { f.now }.unfile(item.id))
        assertNotNull(f.db.noteDao().getById(note.id))
    }
}
