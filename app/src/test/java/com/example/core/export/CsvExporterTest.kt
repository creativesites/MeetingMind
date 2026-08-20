package com.example.core.export

import com.example.core.model.ActionItem
import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun `transcript csv has header row and one row per segment`() {
        val segments = listOf(
            TranscriptSegment(id = "s1", meetingId = "m1", speakerName = "Winston", startMs = 0L, endMs = 2000L, text = "Hello.", confidence = 0.92f)
        )

        val csv = CsvExporter.transcript(segments)
        val lines = csv.trim().lines()

        assertEquals("timestamp,speaker,text,confidence", lines[0])
        assertTrue(lines[1].startsWith("00:00,Winston,Hello.,0.92"))
    }

    @Test
    fun `csv escapes fields containing commas, quotes, and newlines`() {
        val segments = listOf(
            TranscriptSegment(id = "s1", meetingId = "m1", speakerName = "Winston, Jr.", startMs = 0L, endMs = 1000L, text = "He said \"hi\"\nagain.")
        )

        val csv = CsvExporter.transcript(segments)
        val dataLine = csv.trim().lines()[1]

        assertTrue("Speaker with a comma must be quoted", dataLine.contains("\"Winston, Jr.\""))
        assertTrue("Embedded quotes must be doubled", dataLine.contains("\"He said \"\"hi\"\""))
    }

    @Test
    fun `action items csv includes assignee and deadline columns`() {
        val items = listOf(
            ActionItem(id = "a1", meetingId = "m1", task = "Send invoice", assigneeName = "Alex", deadline = "Friday", confidence = 0.8f)
        )

        val csv = CsvExporter.actionItems(items)
        val lines = csv.trim().lines()

        assertEquals("task,assignee,deadline,confidence", lines[0])
        assertEquals("Send invoice,Alex,Friday,0.8", lines[1])
    }
}
