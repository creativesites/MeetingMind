package com.example.core.export

import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.DecisionType
import com.example.core.model.Meeting
import com.example.core.model.MeetingSource
import com.example.core.model.MeetingStatus
import com.example.core.model.RecordingType
import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownExporterTest {

    private fun meeting() = Meeting(
        id = "m1",
        title = "Roadmap Sync",
        createdAt = System.currentTimeMillis(),
        durationMs = 605_000L,
        source = MeetingSource.LOCAL_RECORDING,
        audioFilePath = "/data/m1.wav",
        status = MeetingStatus.READY,
        recordingType = RecordingType.MEETING
    )

    @Test
    fun `summary markdown includes title, decisions, and action item checkboxes`() {
        val decisions = listOf(Decision(id = "d1", meetingId = "m1", text = "Ship Friday", type = DecisionType.DECISION))
        val items = listOf(
            ActionItem(id = "a1", meetingId = "m1", task = "Notify customers", isCompleted = true),
            ActionItem(id = "a2", meetingId = "m1", task = "Book venue", isCompleted = false)
        )

        val md = MarkdownExporter.summary(meeting(), "The team confirmed the launch.", decisions, items)

        assertTrue(md.contains("# Roadmap Sync"))
        assertTrue(md.contains("## Decisions"))
        assertTrue(md.contains("Ship Friday"))
        assertTrue(md.contains("- [x] Notify customers"))
        assertTrue(md.contains("- [ ] Book venue"))
    }

    @Test
    fun `summary markdown omits Decisions and Action Items headings when empty`() {
        val md = MarkdownExporter.summary(meeting(), "Short summary.", emptyList(), emptyList())
        assertFalse(md.contains("## Decisions"))
        assertFalse(md.contains("## Action Items"))
    }

    @Test
    fun `transcript markdown groups consecutive lines under one speaker header`() {
        val segments = listOf(
            TranscriptSegment(id = "s1", meetingId = "m1", speakerName = "Winston", startMs = 0L, endMs = 2000L, text = "First line."),
            TranscriptSegment(id = "s2", meetingId = "m1", speakerName = "Winston", startMs = 2000L, endMs = 4000L, text = "Second line."),
            TranscriptSegment(id = "s3", meetingId = "m1", speakerName = "Alex", startMs = 5000L, endMs = 6000L, text = "Reply.")
        )

        val md = MarkdownExporter.transcript(meeting(), segments)

        assertTrue(md.contains("**Winston**"))
        assertTrue(md.contains("**Alex**"))
        // Only one header for Winston even though he has two consecutive lines.
        assertTrue(md.split("**Winston**").size == 2)
    }
}
