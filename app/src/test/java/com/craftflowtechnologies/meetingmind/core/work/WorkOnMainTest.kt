package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.ActionItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ChatMessageEntity
import com.craftflowtechnologies.meetingmind.core.database.DecisionEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteBlockEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.database.QuestionEntity
import com.craftflowtechnologies.meetingmind.core.database.SpeakerEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.repository.TranscriptRepository
import kotlinx.coroutines.flow.first
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

/**
 * The professional vertical on the app's own tables (docs/PLAN_PROFESSIONAL.md): dynamic names,
 * People from history, the Wrap-up turning findings into tasks, marks and confidential work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkOnMainTest {
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        db.notebookDao().upsert(NotebookEntity("nb", "Acme", "WORK", null, null, 1, 1, null, 0, kind = "PROJECT", propertiesJson = "{\"confidential\":true}"))
        db.noteDao().upsert(NoteEntity("n1", "Kickoff", "MEETING", null, 1, 1, 1, false, false, "OPEN", null, "{\"participants\":\"Speaker 1, Ana\"}", null, "Speaker 1 owns the docs"))
        db.noteDao().upsert(NoteEntity("n2", "Acme review", "CLIENT_CALL", "nb", 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        db.noteDao().upsertBlocks(
            listOf(
                NoteBlockEntity("b1", "n1", 0, "PARAGRAPH", "Speaker 1 owns the docs", "", "{}", "AI", "[]", "summary", false, 0, false, 1),
                NoteBlockEntity("b2", "n1", 1, "PARAGRAPH", "I think Speaker 1 is great", "", "{}", "USER", "[]", "my_notes", false, 0, false, 1)
            )
        )
        db.meetingDao().insertMeeting(MeetingEntity("m1", "Kickoff", 1, 60000, "LOCAL_RECORDING", null, "READY", 2, "en", "Speaker 1 agreed to send the docs; Speaker 2 approved.", recordingType = "MEETING", noteId = "n1"))
        db.meetingDao().insertMeeting(MeetingEntity("m2", "Acme review", 1, 60000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "CLIENT_CALL", noteId = "n2"))
        db.speakerDao().insertSpeakers(listOf(SpeakerEntity("spk1", "m1", 0, "Speaker 1", "Speaker 1", "#fff"), SpeakerEntity("spk2", "m1", 1, "Speaker 2", "Speaker 2", "#000")))
        db.transcriptDao().insertSegments(listOf(
            TranscriptSegmentEntity("s1", "m1", "spk1", "Speaker 1", 0, 20_000, "I'll send the docs by Friday.", null),
            TranscriptSegmentEntity("s2", "m1", "spk2", "Speaker 2", 100_000, 110_000, "Who owns the launch plan?", null)
        ))
        db.actionItemDao().insertActionItem(ActionItemEntity("a1", "m1", "Speaker 1 sends docs to Speaker 2", "spk1", "Speaker 1", "Friday", 0.9f, false, "[\"s1\"]"))
        db.actionItemDao().insertActionItem(ActionItemEntity("a2", "m1", "Book the room", null, null, null, 0.8f, false, "[]"))
        db.decisionDao().insertDecisions(listOf(DecisionEntity("d1", "m1", "Speaker 2 approved OAuth2", "DECISION", 0.8f, "[]")))
        db.chatMessageDao().insertMessage(ChatMessageEntity("c1", "m1", false, "Speaker 1 said they'd send it.", 1, "", ""))
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    @Test
    fun renamingASpeakerUpdatesEverythingDerivedButNotTheirOwnWriting() = runBlocking {
        TranscriptRepository(db).renameSpeaker("m1", "spk1", "Sarah Chen")
        assertEquals("Sarah Chen agreed to send the docs; Speaker 2 approved.", db.meetingDao().getMeetingById("m1")!!.summaryText)
        val a1 = db.actionItemDao().getActionItemsForMeetingDirect("m1").first { it.id == "a1" }
        assertEquals("Sarah Chen sends docs to Speaker 2", a1.task)
        assertEquals("Sarah Chen", a1.assigneeName)
        val blocks = db.noteDao().getBlocks("n1").associateBy { it.id }
        assertEquals("Sarah Chen owns the docs", blocks.getValue("b1").text)
        assertEquals("I think Speaker 1 is great", blocks.getValue("b2").text)
        assertEquals("Sarah Chen said they'd send it.", db.workDao().chatFor("m1").single().content)
        // A real name became a person, linked to the speaker.
        val sarah = WorkPeople(db).all().single { it.name == "Sarah Chen" }
        assertEquals(sarah.id, db.speakerDao().getSpeakersForMeetingDirect("m1").first { it.id == "spk1" }.personId)
    }

    @Test
    fun renamingThePersonRenamesTheSpeakerEverywhere() = runBlocking {
        TranscriptRepository(db).renameSpeaker("m1", "spk2", "James")
        val people = WorkPeople(db)
        val james = people.all().single { it.name == "James" }
        people.rename(james.id, "James Obi")
        assertEquals("James Obi approved OAuth2", db.decisionDao().getDecisionsForMeetingDirect("m1").single().text)
        assertEquals("James Obi", db.speakerDao().getSpeakersForMeetingDirect("m1").first { it.id == "spk2" }.customName)
        assertEquals(listOf("James"), people.get(james.id)!!.aliases)
    }

    @Test
    fun wrapUpTurnsActionsIntoTasksOwedByTheRightPeople() = runBlocking {
        val work = WorkRepository(db)
        TranscriptRepository(db).renameSpeaker("m1", "spk1", "Sarah Chen")
        assertTrue(work.wantsWrapUp("m1"))
        work.confirm("m1")
        assertFalse(work.wantsWrapUp("m1"))
        val tasks = db.workDao().tasksForMeeting("m1")
        assertEquals(2, tasks.size)
        val theirs = tasks.single { it.sourceItemId == "a1" }
        assertTrue("Sarah owes it", theirs.waitingOn)
        assertEquals("Sarah Chen", db.peopleDao().getById(theirs.personId!!)!!.name)
        assertTrue(theirs.dueAt != null)
        val mine = tasks.single { it.sourceItemId == "a2" }
        assertFalse(mine.waitingOn)
        // Confirming again never makes a second task.
        db.workDao().setReviewed("m1", null)
        work.confirm("m1")
        assertEquals(2, db.workDao().tasksForMeeting("m1").size)
        // The tasks list names the owner as they are now.
        people().rename(theirs.personId!!, "Sarah C. Chen")
        assertEquals("Sarah C. Chen", work.observeTasks().first().single { it.id == theirs.id }.ownerName)
    }

    private fun people() = WorkPeople(db)

    @Test
    fun marksBecomeFindingsOrConfirmThem() = runBlocking {
        Marks.save(db, "m1", listOf(Mark(MarkKind.ACTION, 5_000), Mark(MarkKind.QUESTION, 104_000), Mark(MarkKind.KEY, 12_000)))
        val segs = db.transcriptDao().getSegmentsForMeetingDirect("m1").map { Marks.Segment(it.id, it.startMs, it.endMs, it.speakerId, it.text) }
        Marks.reconcile(db, "m1", segs)
        assertTrue((db.actionItemDao().getActionItemsForMeetingDirect("m1").first { it.id == "a1" }.confidence ?: 0f) >= 0.95f)
        assertEquals("Who owns the launch plan?", db.questionDao().getQuestionsForMeetingDirect("m1").single().text)
        assertEquals(listOf(12_000L), Marks.keyMoments(db.noteDao().getById("n1")!!.metadataJson))
    }

    @Test
    fun confidentialWorkStaysOnThePhone() = runBlocking {
        val prefs = UserPreferencesManager(context)
        prefs.setWorkSettings(WorkSettings())
        assertEquals(ProcessingProfile.INTERNET, WorkPrivacy.forMeeting(context, "m1", ProcessingProfile.INTERNET))
        assertEquals(ProcessingProfile.OFFLINE, WorkPrivacy.forMeeting(context, "m2", ProcessingProfile.INTERNET))
        val p = people().resolve("Dr Moyo")!!
        people().update(p.copy(confidential = true))
        people().addToNote("n1", p.id)
        assertEquals(ProcessingProfile.OFFLINE, WorkPrivacy.forMeeting(context, "m1", ProcessingProfile.INTERNET))
    }

    @Test
    fun peopleResolveByEmailAndNameAndWorkDomainsMakeOrganisations() = runBlocking {
        val a = people().resolve("James Obi", "james@acme.com")!!
        assertEquals(a.id, people().resolve("james obi")!!.id)
        assertEquals(a.id, people().resolve(null, "JAMES@acme.com")!!.id)
        assertEquals("Acme", people().get(a.orgId!!)!!.name)
        assertEquals(null, people().resolve("Speaker 3"))
        assertEquals(null, people().resolve("Tino", "tino@gmail.com")!!.orgId)
    }
}
