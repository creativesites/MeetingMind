package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.ActionItemEntity
import com.craftflowtechnologies.meetingmind.core.database.DecisionEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.core.database.NotePersonCrossRef
import com.craftflowtechnologies.meetingmind.core.database.QuestionEntity
import com.craftflowtechnologies.meetingmind.core.database.SpeakerEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import kotlinx.coroutines.runBlocking

/**
 * A two-meeting work history for the item tests: an unfiled kickoff with an action for someone
 * else, an unowned action, a decision and two questions; and a client call filed under a project
 * that belongs to an organisation.
 */
object ItemsFixture {
    fun database(): MeetMindDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
    }

    fun seed(db: MeetMindDatabase, reviewed: Boolean = false) = runBlocking {
        val reviewedAt = if (reviewed) 50L else null
        db.peopleDao().upsert(PersonEntity("org1", "Acme", null, "", 1, 1, kind = "ORG"))
        db.peopleDao().upsert(PersonEntity("ana", "Ana", null, "", 1, 1, orgId = "org1"))
        db.notebookDao().upsert(NotebookEntity("nb", "Acme launch", "WORK", null, null, 1, 1, null, 0, kind = "PROJECT", propertiesJson = "{\"orgId\":\"org1\"}"))
        db.noteDao().upsert(NoteEntity("n1", "Kickoff", "MEETING", null, 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        db.noteDao().upsert(NoteEntity("n2", "Acme review", "CLIENT_CALL", "nb", 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        db.meetingDao().insertMeeting(MeetingEntity("m1", "Kickoff", 1_000, 60000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "MEETING", noteId = "n1", reviewedAt = reviewedAt))
        db.meetingDao().insertMeeting(MeetingEntity("m2", "Acme review", 2_000, 60000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "CLIENT_CALL", noteId = "n2", reviewedAt = reviewedAt))
        db.peopleDao().link(NotePersonCrossRef("n2", "ana"))
        db.speakerDao().insertSpeakers(listOf(
            SpeakerEntity("spk1", "m1", 0, "Speaker 1", "Speaker 1", "#fff"), SpeakerEntity("spk2", "m1", 1, "Speaker 2", "Speaker 2", "#000"),
            SpeakerEntity("spk3", "m2", 0, "Speaker 1", "Ana", "#fff", personId = "ana")
        ))
        db.transcriptDao().insertSegments(listOf(
            TranscriptSegmentEntity("s1", "m1", "spk1", "Speaker 1", 0, 20_000, "I'll send the docs by Friday.", null),
            TranscriptSegmentEntity("s2", "m1", "spk2", "Speaker 2", 100_000, 110_000, "Who owns the launch plan?", null),
            TranscriptSegmentEntity("s3", "m1", "spk2", "Speaker 2", 120_000, 130_000, "We go with OAuth2.", null),
            TranscriptSegmentEntity("s4", "m2", "spk3", "Ana", 5_000, 9_000, "Launch is October 14.", null),
            TranscriptSegmentEntity("s5", "m2", "spk3", "Ana", 15_000, 19_000, "I'll get you the API keys.", null)
        ))
        db.actionItemDao().insertActionItem(ActionItemEntity("a1", "m1", "Speaker 1 sends docs", "spk1", "Speaker 1", "Friday", 0.9f, false, "[\"s1\"]"))
        db.actionItemDao().insertActionItem(ActionItemEntity("a2", "m1", "Book the room", null, null, null, 0.8f, false, "[]"))
        db.actionItemDao().insertActionItem(ActionItemEntity("a3", "m2", "Ana sends API keys", "spk3", "Ana", null, 0.9f, false, "[\"s5\"]"))
        db.decisionDao().insertDecisions(listOf(
            DecisionEntity("d1", "m1", "Use OAuth2", "DECISION", 0.8f, "[\"s3\"]"),
            DecisionEntity("d2", "m2", "Launch on October 14", "DECISION", 0.9f, "[\"s4\"]")
        ))
        db.questionDao().insertQuestions(listOf(
            QuestionEntity("q1", "m1", "Who owns the launch plan?", "spk2", false, null, "[\"s2\"]"),
            QuestionEntity("q2", "m1", "Which region?", null, true, "EU", "[]")
        ))
    }
}
