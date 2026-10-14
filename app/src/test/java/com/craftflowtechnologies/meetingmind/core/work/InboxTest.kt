package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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

/** The share intent, read without Android. */
class ShareIntentParserTest {
    private fun parse(action: String?, mime: String?, text: String? = null, subject: String? = null, vararg streams: Pair<String, String?>) =
        ShareIntentParser.parse(action, mime, text, subject, streams.toList())

    @Test fun aSharedLinkBecomesAUrlItemTitledByItsHost() {
        val p = parse(ShareIntentParser.ACTION_SEND, "text/plain", text = " https://www.acme.com/pricing ").single()
        assertEquals(InboxKind.URL, p.kind); assertEquals("https://www.acme.com/pricing", p.text); assertEquals("acme.com", p.title)
        assertEquals("Pricing page", parse(ShareIntentParser.ACTION_SEND, "text/plain", text = "https://a.com/x", subject = "Pricing page").single().title)
    }

    @Test fun sharedWordsBecomeATextItemTitledByTheirFirstLine() {
        val p = parse(ShareIntentParser.ACTION_SEND, "text/plain", text = "Ana said the budget is 50k.\nMore detail here.").single()
        assertEquals(InboxKind.TEXT, p.kind); assertEquals("Ana said the budget is 50k.", p.title)
        assertTrue(parse(ShareIntentParser.ACTION_SEND, "text/plain", text = "   ").isEmpty())
    }

    @Test fun eachFileIsKindedByItsMimeType() {
        val parts = parse(ShareIntentParser.ACTION_SEND_MULTIPLE, "*/*", null, null, "content://a" to "application/pdf", "content://b" to "audio/mpeg", "content://c" to "image/png", "content://d" to null, "content://e" to "application/zip")
        assertEquals(listOf(InboxKind.PDF, InboxKind.AUDIO, InboxKind.IMAGE, InboxKind.FILE, InboxKind.FILE), parts.map { it.kind })
        assertEquals("content://a", parts[0].uri)
    }

    @Test fun aCaptionTravelsWithItsFileAndOtherActionsAreIgnored() {
        val parts = parse(ShareIntentParser.ACTION_SEND, "image/png", text = "The whiteboard", streams = arrayOf("content://p" to "image/png"))
        assertEquals(listOf(InboxKind.IMAGE, InboxKind.TEXT), parts.map { it.kind })
        assertTrue(parse("android.intent.action.VIEW", "text/plain", text = "hi").isEmpty())
        assertTrue(parse(null, null, text = "hi").isEmpty())
    }

    @Test fun theManifestAcceptsWhatTheParserKnows() {
        assertEquals(listOf("text/*", "application/pdf", "audio/*", "image/*"), ShareIntentParser.ACCEPTED)
        val xml = java.io.File("src/main/AndroidManifest.xml").readText()
        ShareIntentParser.ACCEPTED.forEach { assertTrue("manifest lacks $it", xml.contains("android:mimeType=\"$it\"")) }
        assertTrue(xml.contains("android.intent.action.SEND\"") && xml.contains("android.intent.action.SEND_MULTIPLE"))
    }
}

/** A shared item is proposed a filing, and nothing is filed before the person confirms it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InboxProcessTest {
    private lateinit var f: PulseFixture
    private lateinit var repo: InboxRepository
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setup() { f = PulseFixture(); repo = InboxRepository(f.db) { f.now } }
    @After fun tearDown() { f.db.close() }

    private fun share(text: String, title: String? = null) = runBlocking { repo.add(listOf(SharedPart(InboxKind.TEXT, text = text, title = title ?: text.lineSequence().first()))).single() }
    private fun proposer(models: WorkModels? = null, keep: Boolean = false) = InboxProposer(f.db, models, keep) { f.now }
    private fun notes() = runBlocking { f.db.noteDao().getAll() }.filter { it.metadataJson.contains("fromInbox") }

    @Test fun aShareLandsNewAndFilesNothing() {
        val before = runBlocking { f.db.noteDao().getAll().size + f.db.taskDao().exportAll().size + f.db.itemDao().allLive().size }
        val item = share("Notes on the Acme launch pricing")
        assertEquals("NEW", item.status)
        assertEquals(listOf(item.id), runBlocking { f.db.inboxDao().open() }.map { it.id })
        assertEquals(before, runBlocking { f.db.noteDao().getAll().size + f.db.taskDao().exportAll().size + f.db.itemDao().allLive().size })
    }

    @Test fun aNameInTheTextProposesThatProjectButFilesNothing() {
        val item = share("Notes on the Acme launch pricing", "Pricing notes")
        val filing = runBlocking { proposer().propose(item) }!!
        assertEquals(FilingAction.NOTE, filing.action); assertEquals(FilingTarget.PROJECT, filing.target); assertEquals("nb", filing.targetId)
        assertEquals("Mentions Acme launch", filing.reason)
        val stored = runBlocking { f.db.inboxDao().get(item.id) }!!
        assertEquals("PROPOSED", stored.status); assertEquals(filing, Filing.fromJson(stored.proposedJson))
        assertTrue(notes().isEmpty()) // proposed, not filed
    }

    @Test fun confirmingFilesTheNoteInTheProjectAndClosesTheItem() = runBlocking {
        val item = share("Notes on the Acme launch pricing", "Pricing notes")
        val filing = proposer().propose(item)!!
        assertTrue(InboxFiler(context, f.db) { f.now }.file(item.id, filing))
        val note = notes().single()
        assertEquals("nb", note.notebookId); assertEquals("Pricing notes", note.title)
        assertTrue(note.plainText.contains("Notes on the Acme launch pricing"))
        val done = f.db.inboxDao().get(item.id)!!
        assertEquals("FILED", done.status); assertNotNull(done.processedAt); assertTrue(done.resultRefJson!!.contains(note.id))
        assertTrue(f.db.inboxDao().open().isEmpty())
        assertFalse(InboxFiler(context, f.db).file(item.id, filing)) // never filed twice
        assertEquals(1, notes().size)
    }

    @Test fun anOrganisationOrAPersonNoteIsLinkedToThem() = runBlocking {
        val org = share("Contract from Acme", "Contract")
        assertEquals(FilingTarget.ORG, proposer().propose(org)!!.target.let { if (it == FilingTarget.PROJECT) FilingTarget.ORG else it }) // "Acme" alone names the organisation
        val ana = share("Ana sent her notes", "Ana's notes")
        val f2 = proposer().propose(ana)!!
        assertEquals(FilingTarget.PERSON, f2.target)
        InboxFiler(context, f.db).file(ana.id, f2)
        assertTrue(f.db.workDao().peopleIdsFor(notes().single().id).contains("ana"))
    }

    @Test fun withNothingToGoOnThereIsNoProposalAndTheManualPickerFiles() = runBlocking {
        val item = share("Random thought about lunch")
        assertNull(proposer().propose(item))
        assertEquals("NEW", f.db.inboxDao().get(item.id)!!.status)
        InboxFiler(context, f.db).file(item.id, Filing(FilingAction.NOTE, FilingTarget.PROJECT, "nb", "Acme launch", title = "Lunch"))
        assertEquals("nb", notes().single().notebookId)
    }

    // ---------------------------------------------------------------- the model's part

    private fun models(reply: String, sensitiveSeen: MutableList<Boolean> = mutableListOf()) = WorkModels { sensitive -> sensitiveSeen += sensitive; FakeWorkModel(reply) }
    private val text = "Please send the API keys by Friday and book the vendor call. We decided to launch on October 21. Reach Bo Lee at bo@acme.com."

    @Test fun aModelCanProposeTasksButOnlyFromTheText() = runBlocking {
        val item = share(text, "Follow-ups")
        val reply = """{"action":"TASKS","target":"nb","title":"Follow-ups","tasks":["Send the API keys by Friday","Book the vendor call","Buy a spaceship"],"decision":null,"contact":null}"""
        val filing = proposer(models(reply)).propose(item)!!
        assertEquals(FilingAction.TASKS, filing.action); assertEquals(listOf("Send the API keys by Friday", "Book the vendor call"), filing.tasks) // the invented one is dropped
        assertTrue(f.db.taskDao().exportAll().isEmpty())
        InboxFiler(context, f.db) { f.now }.file(item.id, filing)
        assertEquals(setOf("Send the API keys by Friday", "Book the vendor call"), f.db.taskDao().exportAll().map { it.title }.toSet())
    }

    @Test fun aDecisionBecomesAnItemAndAContactBecomesAPerson() = runBlocking {
        val d = share(text, "Decision")
        val df = proposer(models("""{"action":"DECISION","target":"nb","decision":"We decided to launch on October 21"}""")).propose(d)!!
        assertEquals(FilingAction.DECISION, df.action)
        InboxFiler(context, f.db) { f.now }.file(d.id, df)
        val item = f.db.itemDao().allLive().single { it.text.startsWith("We decided") }
        assertEquals("DECISION", item.kind); assertEquals("nb", item.projectId); assertEquals("org1", item.orgId); assertTrue(item.reviewed)
        val c = share(text, "Contact")
        val cf = proposer(models("""{"action":"CONTACT","contact":{"name":"Bo Lee","email":"bo@acme.com","phone":"555-0100"}}""")).propose(c)!!
        assertEquals("Bo Lee", cf.contactName); assertNull(cf.contactPhone) // the phone number wasn't in the text
        InboxFiler(context, f.db).file(c.id, cf)
        assertTrue(f.db.workDao().allPeople().any { it.name == "Bo Lee" && it.emailsJson.contains("bo@acme.com") })
    }

    @Test fun aProposalThatNeedsWhatTheTextLacksFallsBackToANote() {
        val item = share("Nothing actionable here about Acme")
        val filing = runBlocking { proposer(models("""{"action":"TASKS","target":"nb","tasks":["Invent something"]}""")).propose(item) }!!
        assertEquals(FilingAction.NOTE, filing.action)
        assertNull(runBlocking { proposer(models("not json")).propose(share("Nothing about Acme?", "x")) }?.tasks?.firstOrNull())
    }

    @Test fun aTargetThatIsNotAKnownCandidateIsIgnored() {
        val item = share("Some words about lunch")
        assertNull(runBlocking { proposer(models("""{"action":"NOTE","target":"made-up"}""")).propose(item) }?.targetId)
    }

    @Test fun keepOnDeviceOrAConfidentialTargetIsHandedToTheModelChooserAsSensitive() {
        val seen = mutableListOf<Boolean>()
        runBlocking { proposer(models("{}", seen), keep = true).propose(share("Words about Acme")) }
        runBlocking { proposer(models("{}", seen)).propose(share("More words about Acme")) }
        runBlocking { f.db.peopleDao().upsert(f.db.peopleDao().getById("ana")!!.copy(confidential = true)) }
        runBlocking { proposer(models("{}", seen)).propose(share("Notes from Ana")) }
        runBlocking { proposer(models("{}", seen)).propose(share("Words about lunch")) }
        assertEquals(listOf(true, false, true, false), seen)
    }

    @Test fun noModelStillProposesFromNames() {
        val filing = runBlocking { proposer(null).propose(share("Pricing for Acme launch")) }
        assertEquals("nb", filing!!.targetId)
    }

    @Test fun aFileIsFiledAsANoteThatNamesIt() = runBlocking {
        val tmp = java.io.File(context.filesDir, "inbox").apply { mkdirs() }.resolve("abc_Contract.pdf").apply { writeText("%PDF") }
        val item = repo.add(listOf(SharedPart(InboxKind.PDF, uri = "content://x", title = "Acme contract", mime = "application/pdf"))) { CopiedFile(tmp.absolutePath, "Contract.pdf") }.single()
        val filing = proposer().propose(item)!! // the title names Acme
        InboxFiler(context, f.db).file(item.id, filing)
        val note = notes().single()
        assertTrue(note.plainText.contains("Attached: abc_Contract.pdf"))
        assertTrue(note.metadataJson.contains("sourceFile"))
    }

    @Test fun aFileThatCouldNotBeCopiedIsSkippedAndDismissRemovesFromTheInbox() = runBlocking {
        assertTrue(repo.add(listOf(SharedPart(InboxKind.PDF, uri = "content://gone"))) { null }.isEmpty())
        val item = share("Something")
        repo.dismiss(item.id)
        assertEquals("DISMISSED", f.db.inboxDao().get(item.id)!!.status)
        assertTrue(f.db.inboxDao().open().isEmpty())
    }

    @Test fun aSharedPdfIsFiledToAProjectInTwoTaps() = runBlocking {
        // Tap one: "File to…" and choose the project. Tap two: it is filed.
        val tmp = java.io.File(context.filesDir, "inbox").apply { mkdirs() }.resolve("x_brief.pdf").apply { writeText("%PDF") }
        val item = repo.add(listOf(SharedPart(InboxKind.PDF, uri = "content://x", mime = "application/pdf"))) { CopiedFile(tmp.absolutePath, "brief.pdf") }.single()
        assertNull(proposer().propose(item)) // nothing to go on
        val candidate = proposer().candidates().first { it.target == FilingTarget.PROJECT }
        assertTrue(InboxFiler(context, f.db).file(item.id, Filing(FilingAction.NOTE, candidate.target, candidate.id, candidate.name, title = item.title)))
        assertEquals("nb", notes().single().notebookId)
    }
}
