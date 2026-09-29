package com.example.core.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.NoteEntity
import com.example.core.database.NotebookEntity
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.ActionItem
import com.example.core.model.ProcessingProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarksAndPrivacyTest {

    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        db.notebookDao().upsert(NotebookEntity("nb", "Acme", "WORK", null, null, 1, 1, null, 0, "PROJECT", "{\"confidential\":true}"))
        db.noteDao().upsert(NoteEntity("n1", "Review", "MEETING", null, 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        db.noteDao().upsert(NoteEntity("n2", "Kickoff", "MEETING", "nb", 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        db.noteDao().upsert(NoteEntity("n3", "Sermon", "SERMON", null, 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        db.meetingDao().insertMeeting(MeetingEntity("m1", "Review", 1, 600000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "MEETING", noteId = "n1"))
        db.meetingDao().insertMeeting(MeetingEntity("m2", "Kickoff", 1, 600000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "MEETING", noteId = "n2"))
        db.meetingDao().insertMeeting(MeetingEntity("m3", "Sermon", 1, 600000, "LOCAL_RECORDING", null, "READY", 1, "en", null, recordingType = "SERMON", noteId = "n3"))
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    private val segments = listOf(
        Marks.Segment("s1", 0, 20_000, "spk1", "Welcome everyone."),
        Marks.Segment("s2", 100_000, 110_000, "spk2", "Can you send the budget by Friday?"),
        Marks.Segment("s3", 300_000, 310_000, "spk1", "Who owns the launch plan?")
    )

    @Test
    fun `a mark extraction also found folds into it, and one it missed takes the words said`() = runBlocking {
        Marks.save(db, "m1", listOf(Mark(MarkKind.ACTION, 104_000), Mark(MarkKind.QUESTION, 305_000), Mark(MarkKind.KEY, 12_000)))
        db.itemDao().upsert(ItemsFrom.action(ActionItem("a1", "m1", "Send the budget", deadline = "Friday", confidence = 0.4f), "n1", null, 1, 100_000))

        Marks.reconcile(db, "m1", segments)

        val items = db.itemDao().getRawForMeeting("m1")
        assertEquals(2, items.size)
        val action = items.single { it.kind == "TASK" }
        assertEquals("a1", action.id)
        assertTrue("a marked item is not a guess", (action.confidence ?: 0f) >= 0.95f)
        val question = items.single { it.kind == "QUESTION" }
        assertEquals("Who owns the launch plan?", question.text)
        assertEquals("MARK", question.source)
        assertEquals(listOf(12_000L), Marks.keyMoments(db.noteDao().getById("n1")!!.metadataJson))
    }

    @Test
    fun `confidential projects, people and kept-on-phone work never use the cloud`() = runBlocking {
        val prefs = UserPreferencesManager(context)
        prefs.setWorkSettings(WorkSettings())
        assertEquals(ProcessingProfile.INTERNET, WorkPrivacy.forMeeting(context, "m1", ProcessingProfile.INTERNET))
        // A confidential project.
        assertEquals(ProcessingProfile.OFFLINE, WorkPrivacy.forMeeting(context, "m2", ProcessingProfile.INTERNET))
        // A confidential person in the note.
        val people = PeopleRepository(db)
        val p = people.resolve("Dr Moyo")!!
        people.update(p.copy(confidential = true))
        people.addToNote("n1", p.id, "ATTENDEE")
        assertEquals(ProcessingProfile.OFFLINE, WorkPrivacy.forMeeting(context, "m1", ProcessingProfile.INTERNET))
        // Clinical work keeps work on the phone, but not a sermon.
        prefs.setWorkSettings(WorkSettings.forProfile(WorkProfile.CLINICAL))
        assertEquals(ProcessingProfile.INTERNET, WorkPrivacy.forMeeting(context, "m3", ProcessingProfile.INTERNET))
        assertEquals(ProcessingProfile.OFFLINE, WorkPrivacy.forNote(context, "n1", ProcessingProfile.INTERNET))
        // Offline asked is offline given.
        assertEquals(ProcessingProfile.OFFLINE, WorkPrivacy.forMeeting(context, "m3", ProcessingProfile.OFFLINE))
        prefs.setWorkSettings(WorkSettings())
    }

    @Test
    fun `a recording with unreviewed work findings opens its Wrap-up, a sermon doesn't`() = runBlocking {
        assertEquals(false, WrapUps.wanted(db, "m1"))
        Marks.save(db, "m1", listOf(Mark(MarkKind.ACTION, 104_000)))
        assertEquals(true, WrapUps.wanted(db, "m1"))
        WorkRepository(db).markReviewed("m1")
        assertEquals(false, WrapUps.wanted(db, "m1"))
        Marks.save(db, "m3", listOf(Mark(MarkKind.ACTION, 1_000)))
        assertEquals(false, WrapUps.wanted(db, "m3"))
    }

    @Test
    fun `a sent follow-up leaves the list and closes my follow-up task`() = runBlocking {
        val work = WorkRepository(db)
        db.itemDao().upsert(ItemsFrom.decision(com.example.core.model.Decision("d1", "m1", "Go"), "n1", null, 1, null))
        db.itemDao().upsert(ItemsFrom.followUp(com.example.core.model.FollowUp("f1", "m1", "Send recap"), "n1", null, 1, null))
        work.markReviewed("m1")
        assertEquals(listOf("m1"), work.followUpsToSend(days = 100_000).map { it.meetingId })
        work.markFollowUpSent("m1", Channel.WHATSAPP)
        assertTrue(work.followUpsToSend(days = 100_000).isEmpty())
        assertEquals("DONE", db.itemDao().getById("f1")!!.status)
    }
}
