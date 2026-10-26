package com.craftflowtechnologies.meetingmind.core.common

import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MeetingTitleGeneratorTest {

    @Test
    fun `deterministic fallback title uses the recording type display name and real date, never invented content`() {
        val createdAt = java.util.GregorianCalendar(2026, java.util.Calendar.AUGUST, 21).timeInMillis
        val expectedDate = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(createdAt))

        val title = MeetingTitleGenerator.deterministicFallbackTitle(RecordingType.VOICE_MEMO, createdAt)

        assertEquals("Voice Memo — $expectedDate", title)
    }

    @Test
    fun `deterministic fallback title localizes the date to the device locale rather than Locale US`() {
        val zone = TimeZone.getTimeZone("Africa/Lusaka")
        val createdAt = Calendar.getInstance(zone).apply {
            set(2026, Calendar.OCTOBER, 24, 14, 20, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expectedDate = SimpleDateFormat("MMM d", Locale.FRANCE).apply { timeZone = zone }.format(Date(createdAt))

        val title = MeetingTitleGenerator.deterministicFallbackTitle(
            RecordingType.VOICE_MEMO, createdAt, Locale.FRANCE, zone
        )

        assertEquals("Voice Memo — $expectedDate", title)
        assertFalse("Must not silently use a US-locale month name", title.contains("Oct"))
    }

    @Test
    fun `deterministic fallback can include the start time for disambiguation`() {
        val zone = TimeZone.getTimeZone("UTC")
        val createdAt = Calendar.getInstance(zone).apply {
            set(2026, Calendar.OCTOBER, 24, 14, 20, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expectedTime = SimpleDateFormat("MMM d, HH:mm", Locale.US).apply { timeZone = zone }.format(Date(createdAt))

        val title = MeetingTitleGenerator.deterministicFallbackTitle(
            RecordingType.VOICE_MEMO, createdAt, Locale.US, zone, includeStartTime = true
        )

        assertEquals("Voice Memo — $expectedTime", title)
        assertTrue(title.contains("14:20"))
    }

    @Test
    fun `deterministic fallback title reflects a different recording type`() {
        val createdAt = System.currentTimeMillis()
        val title = MeetingTitleGenerator.deterministicFallbackTitle(RecordingType.INTERVIEW, createdAt)
        assertEquals(true, title.startsWith("Interview — "))
    }

    @Test
    fun `validation table handles quotes, generic titles, blank and too long`() {
        val cases = listOf(
            "\"Budget Review\"" to "Budget Review",
            "'Sprint Retro'" to "Sprint Retro",
            "  Q3 Roadmap Planning  " to "Q3 Roadmap Planning",
            "Meeting Summary" to null,
            "meeting summary" to null,
            "Untitled Meeting" to null,
            "Team Meeting" to null,
            "   " to null,
            "" to null
        )
        cases.forEach { (raw, expected) ->
            assertEquals("sanitizeAndValidate(${raw.take(30)})", expected, MeetingTitleGenerator.sanitizeAndValidate(raw))
        }

        val tooLong = MeetingTitleGenerator.sanitizeAndValidate("A".repeat(200))
        assertEquals(80, tooLong?.length)
    }

    @Test
    fun `imported file names that say nothing about content are not kept`() {
        assertFalse(MeetingTitleGenerator.isUsableImportedTitle("AUD_0042"))
        assertFalse(MeetingTitleGenerator.isUsableImportedTitle("recording"))
        assertFalse(MeetingTitleGenerator.isUsableImportedTitle("IMG_1234"))
        assertFalse(MeetingTitleGenerator.isUsableImportedTitle("2026-10-24 14.20"))
        assertTrue(MeetingTitleGenerator.isUsableImportedTitle("Q3 board planning"))
    }

    @Test
    fun `sanitizeAndValidate accepts a real, specific title unchanged`() {
        val result = MeetingTitleGenerator.sanitizeAndValidate("Q3 Roadmap Planning")
        assertEquals("Q3 Roadmap Planning", result)
    }

    @Test
    fun `sanitizeAndValidate strips wrapping double quotes`() {
        val result = MeetingTitleGenerator.sanitizeAndValidate("\"Budget Review\"")
        assertEquals("Budget Review", result)
    }

    @Test
    fun `sanitizeAndValidate strips wrapping single quotes`() {
        val result = MeetingTitleGenerator.sanitizeAndValidate("'Sprint Retro'")
        assertEquals("Sprint Retro", result)
    }

    @Test
    fun `sanitizeAndValidate rejects null`() {
        assertNull(MeetingTitleGenerator.sanitizeAndValidate(null))
    }

    @Test
    fun `sanitizeAndValidate rejects blank`() {
        assertNull(MeetingTitleGenerator.sanitizeAndValidate("   "))
    }

    @Test
    fun `sanitizeAndValidate rejects known generic placeholders case-insensitively`() {
        assertNull(MeetingTitleGenerator.sanitizeAndValidate("Meeting Summary"))
        assertNull(MeetingTitleGenerator.sanitizeAndValidate("meeting summary"))
        assertNull(MeetingTitleGenerator.sanitizeAndValidate("Untitled Meeting"))
        assertNull(MeetingTitleGenerator.sanitizeAndValidate("Team Meeting"))
    }

    @Test
    fun `sanitizeAndValidate truncates an excessively long title rather than rejecting it`() {
        val longTitle = "A".repeat(200)
        val result = MeetingTitleGenerator.sanitizeAndValidate(longTitle)
        assertEquals(80, result?.length)
    }

    @Test
    fun `sanitizeAndValidate does not reject a title that merely contains a generic word as part of a specific phrase`() {
        val result = MeetingTitleGenerator.sanitizeAndValidate("Weekly Team Meeting Retrospective on Q3 Goals")
        assertEquals("Weekly Team Meeting Retrospective on Q3 Goals", result)
    }
}
