package com.example.core.common

import com.example.core.model.RecordingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeetingTitleGeneratorTest {

    @Test
    fun `deterministic fallback title uses the recording type display name and real date, never invented content`() {
        val createdAt = java.util.GregorianCalendar(2026, java.util.Calendar.AUGUST, 21).timeInMillis
        val expectedDate = SimpleDateFormat("MMM d", Locale.US).format(Date(createdAt))

        val title = MeetingTitleGenerator.deterministicFallbackTitle(RecordingType.VOICE_MEMO, createdAt)

        assertEquals("Voice Memo — $expectedDate", title)
    }

    @Test
    fun `deterministic fallback title reflects a different recording type`() {
        val createdAt = System.currentTimeMillis()
        val title = MeetingTitleGenerator.deterministicFallbackTitle(RecordingType.INTERVIEW, createdAt)
        assertEquals(true, title.startsWith("Interview — "))
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
