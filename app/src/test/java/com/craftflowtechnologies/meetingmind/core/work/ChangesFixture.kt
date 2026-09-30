package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.DecisionEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.SegmentSignalEntity
import com.craftflowtechnologies.meetingmind.core.database.SpeakerEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import kotlinx.coroutines.runBlocking

/**
 * Helpers for the change-detection tests, on the [PulseFixture] world (project "Acme launch").
 * A meeting has one paragraph per line said, and signals cite them by id.
 */
fun PulseFixture.addMeeting(id: String, at: Long, lines: List<String>, project: String? = "nb", type: String = "CLIENT_CALL", reviewed: Boolean = false) = runBlocking {
    db.noteDao().upsert(NoteEntity("note_$id", "Call $id", type, project, 1, 1, at, false, false, "OPEN", null, "{}", null, ""))
    db.meetingDao().insertMeeting(MeetingEntity(id, "Call $id", at, 60_000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = type, noteId = "note_$id", reviewedAt = if (reviewed) at else null))
    db.speakerDao().insertSpeakers(listOf(SpeakerEntity("spk_$id", id, 0, "Speaker 1", "Speaker 1", "#fff")))
    db.transcriptDao().insertSegments(lines.mapIndexed { i, text ->
        TranscriptSegmentEntity("${id}_s$i", id, "spk_$id", "Speaker 1", i * 10_000L, i * 10_000L + 8_000, text, null)
    })
}

fun PulseFixture.addDecision(meetingId: String, id: String, text: String, segment: Int) = runBlocking {
    db.decisionDao().insertDecisions(listOf(DecisionEntity(id, meetingId, text, "DECISION", 0.9f, "[\"${meetingId}_s$segment\"]")))
}

fun PulseFixture.addSignal(meetingId: String, signalId: String, kind: ItemKind, text: String, segments: List<Int>, value: String? = null, confidence: Float = 0.8f, speakerId: String? = null) = runBlocking {
    db.signalDao().insertAll(segments.mapIndexed { i, s ->
        SegmentSignalEntity("${signalId}_$i", "${meetingId}_s$s", meetingId, kind.name, speakerId, value, confidence, text, signalId)
    })
}
