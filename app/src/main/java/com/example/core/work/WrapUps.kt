package com.example.core.work

import com.example.core.database.MeetMindDatabase
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.model.Workflows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WrapUps {
    /**
     * Whether a finished recording opens its Wrap-up (docs/PLAN_PROFESSIONAL.md §4.3): a work
     * recording with findings nobody has reviewed. Faith, learning and personal recordings open
     * as they always have.
     */
    suspend fun wanted(database: MeetMindDatabase, meetingId: String): Boolean = withContext(Dispatchers.IO) {
        val m = database.meetingDao().getMeetingById(meetingId) ?: return@withContext false
        val type = runCatching { RecordingType.valueOf(m.recordingType) }.getOrDefault(RecordingType.GENERAL)
        val work = Workflows.space(type) == NotebookSpace.WORK || type == RecordingType.GENERAL || type == RecordingType.CUSTOM
        work && database.itemDao().getRawForMeeting(meetingId).any { !it.reviewed }
    }
}
