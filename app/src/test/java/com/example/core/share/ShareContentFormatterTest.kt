package com.example.core.share

import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.DecisionType
import com.example.core.model.Meeting
import com.example.core.model.MeetingSource
import com.example.core.model.MeetingStatus
import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareContentFormatterTest {

    private fun meeting() = Meeting(
        id = "m1",
        title = "Roadmap Sync",
        createdAt = System.currentTimeMillis(),
        durationMs = 605_000L,
        source = MeetingSource.LOCAL_RECORDING,
        audioFilePath = "/data/m1.wav",
        status = MeetingStatus.READY
    )

    @Test
    fun `transcript text includes title, timestamps, and speaker lines`() {
        val segments = listOf(
            TranscriptSegment(id = "s1", meetingId = "m1", speakerName = "Winston", startMs = 0L, endMs = 2000L, text = "Let's ship Friday."),
            TranscriptSegment(id = "s2", meetingId = "m1", speakerName = null, startMs = 5000L, endMs = 7000L, text = "Sounds good.")
        )

        val text = ShareContentFormatter.transcriptText(meeting(), segments)

        assertTrue(text.contains("Roadmap Sync"))
        assertTrue(text.contains("Winston: Let's ship Friday."))
        assertTrue("Unknown speaker must be labeled honestly, never fabricated", text.contains("Unknown speaker: Sounds good."))
    }

    @Test
    fun `transcript text with no segments says so instead of an empty body`() {
        val text = ShareContentFormatter.transcriptText(meeting(), emptyList())
        assertTrue(text.contains("No transcript is available"))
    }

    @Test
    fun `summary text includes decisions and action items when present`() {
        val decisions = listOf(Decision(id = "d1", meetingId = "m1", text = "Ship on Friday", type = DecisionType.DECISION))
        val actionItems = listOf(ActionItem(id = "a1", meetingId = "m1", task = "Notify customers", assigneeName = "Alex", deadline = "Friday"))

        val text = ShareContentFormatter.summaryText(meeting(), "The team confirmed the launch date.", decisions, actionItems)

        assertTrue(text.contains("The team confirmed the launch date."))
        assertTrue(text.contains("Ship on Friday"))
        assertTrue(text.contains("Notify customers"))
        assertTrue(text.contains("Alex"))
    }

    @Test
    fun `summary text is honest when no summary was generated`() {
        val text = ShareContentFormatter.summaryText(meeting(), null, emptyList(), emptyList())
        assertTrue(text.contains("No summary is available"))
        assertFalse("Must not fabricate a Decisions section when there are none", text.contains("Decisions:"))
    }

    @Test
    fun `action items text marks completion state and honest empty state`() {
        val items = listOf(
            ActionItem(id = "a1", meetingId = "m1", task = "Send invoice", isCompleted = true),
            ActionItem(id = "a2", meetingId = "m1", task = "Book venue", isCompleted = false)
        )

        val text = ShareContentFormatter.actionItemsText(meeting(), items)

        assertTrue(text.contains("[x] Send invoice"))
        assertTrue(text.contains("[ ] Book venue"))
    }

    @Test
    fun `action items text is honest when none were extracted`() {
        val text = ShareContentFormatter.actionItemsText(meeting(), emptyList())
        assertTrue(text.contains("No action items were found"))
    }
}
