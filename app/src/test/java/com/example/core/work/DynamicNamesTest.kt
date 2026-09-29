package com.example.core.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.ChatMessageEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.NoteBlockEntity
import com.example.core.database.NoteEntity
import com.example.core.database.SpeakerEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.model.ActionItem
import com.example.core.repository.TranscriptRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renaming a speaker or a person updates every place that shows them (PLAN_PROFESSIONAL.md §5.5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DynamicNamesTest {

    private lateinit var db: MeetMindDatabase

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        db.noteDao().upsert(NoteEntity("n1", "Kickoff", "MEETING", null, 1, 1, 1, false, false, "OPEN", null, "{\"participants\":\"Speaker 1, Ana\"}", null, "Speaker 1 owns the docs"))
        db.noteDao().upsertBlocks(
            listOf(
                NoteBlockEntity("b1", "n1", 0, "PARAGRAPH", "Speaker 1 owns the docs", "", "{}", "AI", "[]", "summary", false, 0, false, 1),
                NoteBlockEntity("b2", "n1", 1, "PARAGRAPH", "I think Speaker 1 is great", "", "{}", "USER", "[]", "my_notes", false, 0, false, 1),
                NoteBlockEntity("b3", "n1", 2, "TRANSCRIPT_EXCERPT", "I'll send them.", "", "{\"speaker\":\"Speaker 1\"}", "TRANSCRIPT", "[]", null, false, 0, false, 1)
            )
        )
        db.meetingDao().insertMeeting(
            MeetingEntity("m1", "Kickoff", 1, 60000, "LOCAL_RECORDING", null, "READY", 2, "en", "Speaker 1 agreed to send the docs; Speaker 2 approved.", noteId = "n1")
        )
        db.speakerDao().insertSpeakers(
            listOf(
                SpeakerEntity("spk1", "m1", 0, "Speaker 1", "Speaker 1", "#fff"),
                SpeakerEntity("spk2", "m1", 1, "Speaker 2", "Speaker 2", "#000")
            )
        )
        db.transcriptDao().insertSegments(listOf(TranscriptSegmentEntity("s1", "m1", "spk1", "Speaker 1", 0, 1000, "I'll send them.", null)))
        db.itemDao().upsert(ItemsFrom.action(ActionItem("a1", "m1", "Speaker 1 sends docs to Speaker 2", "spk1", "Speaker 1", "Friday"), "n1", null, 1, 0))
        db.chatMessageDao().insertMessage(ChatMessageEntity("c1", "m1", false, "Speaker 1 said they'd send it.", 1, "", ""))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `renaming a speaker updates the summary, items, note, and answers`() = runBlocking {
        TranscriptRepository(db).renameSpeaker("m1", "spk1", "Sarah Chen")

        assertEquals("Sarah Chen agreed to send the docs; Speaker 2 approved.", db.meetingDao().getMeetingById("m1")!!.summaryText)
        val item = db.itemDao().getForMeeting("m1").single()
        assertEquals("Sarah Chen sends docs to Speaker 2", item.item.text)
        assertEquals("Sarah Chen", item.ownerDisplay)
        val blocks = db.noteDao().getBlocks("n1").associateBy { it.id }
        assertEquals("Sarah Chen owns the docs", blocks.getValue("b1").text)
        assertEquals("the person's own writing is theirs", "I think Speaker 1 is great", blocks.getValue("b2").text)
        assertEquals("transcript words are evidence", "I'll send them.", blocks.getValue("b3").text)
        assertTrue(blocks.getValue("b3").payloadJson.contains("Sarah Chen"))
        assertTrue(db.noteDao().getById("n1")!!.metadataJson.contains("Sarah Chen, Ana"))
        assertEquals("Sarah Chen said they'd send it.", db.chatMessageDao().getForMeetingDirect("m1").single().content)
        assertEquals("Sarah Chen", db.transcriptDao().getSegmentsForMeetingDirect("m1").single().speakerName)
    }

    @Test
    fun `a named speaker becomes a person, and renaming the person renames them everywhere`() = runBlocking {
        TranscriptRepository(db).renameSpeaker("m1", "spk1", "Sarah")
        val people = PeopleRepository(db)
        val sarah = people.all().single { it.name == "Sarah" }
        assertEquals(sarah.id, db.speakerDao().getSpeakersForMeetingDirect("m1").first { it.id == "spk1" }.personId)

        people.rename(sarah.id, "Sarah Chen")

        assertEquals("Sarah Chen", db.speakerDao().getSpeakersForMeetingDirect("m1").first { it.id == "spk1" }.customName)
        assertEquals("Sarah Chen agreed to send the docs; Speaker 2 approved.", db.meetingDao().getMeetingById("m1")!!.summaryText)
        assertEquals("Sarah Chen", db.itemDao().observeForMeeting("m1").first().single().ownerDisplay)
        assertEquals(listOf("Sarah"), people.get(sarah.id)!!.aliases)
    }

    @Test
    fun `merging speakers moves ownership and the name`() = runBlocking {
        TranscriptRepository(db).mergeSpeakers("m1", "spk1", "spk2")
        val item = db.itemDao().getForMeeting("m1").single()
        assertEquals("spk2", item.item.ownerSpeakerId)
        assertEquals("Speaker 2 agreed to send the docs; Speaker 2 approved.", db.meetingDao().getMeetingById("m1")!!.summaryText)
    }

    @Test
    fun `people resolve by email and name, and work domains give an organisation`() = runBlocking {
        val people = PeopleRepository(db)
        val a = people.resolve("James Obi", "james@acme.com")!!
        val b = people.resolve("james obi")!!
        val c = people.resolve(null, "JAMES@acme.com")!!
        assertEquals(a.id, b.id)
        assertEquals(a.id, c.id)
        assertEquals("Acme", people.get(a.orgId!!)!!.name)
        assertEquals(null, people.resolve("Speaker 3"))
        val gmail = people.resolve("Tino", "tino@gmail.com")!!
        assertEquals("free mail has no organisation", null, gmail.orgId)
    }
}
