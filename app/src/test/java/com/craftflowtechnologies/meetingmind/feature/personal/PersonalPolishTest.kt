package com.craftflowtechnologies.meetingmind.feature.personal

import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.personal.PersonalGrowthIntelligence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PersonalPolishTest {

    @Test
    fun buildMonthGridProducesValidSevenColumnWeeksAndLeapYearDays() {
        val febLeap = buildMonthGrid(2024, Calendar.FEBRUARY)
        val nonNullLeap = febLeap.weeks.flatten().mapNotNull { it.dayOfMonth }
        assertEquals(29, nonNullLeap.size)
        assertEquals(1, nonNullLeap.first())
        assertEquals(29, nonNullLeap.last())
        assertTrue(febLeap.weeks.all { it.size == 7 })

        val febCommon = buildMonthGrid(2025, Calendar.FEBRUARY)
        assertEquals(28, febCommon.weeks.flatten().mapNotNull { it.dayOfMonth }.size)
        assertTrue(febCommon.weeks.all { it.size == 7 })
    }

    @Test
    fun formatMinuteOfDayFormatsMidnightNoonAndEvening() {
        assertEquals("12:00 AM", formatMinuteOfDay(0))
        assertEquals("7:30 AM", formatMinuteOfDay(7 * 60 + 30))
        assertEquals("12:15 PM", formatMinuteOfDay(12 * 60 + 15))
        assertEquals("9:00 PM", formatMinuteOfDay(21 * 60))
    }

    @Test
    fun ideaCollisionRespectsExcludedPairsOrderIndependently() {
        val n1 = Note(id = "a", workflow = RecordingType.IDEA, title = "Podcast studio", plainText = "Build a quiet acoustic podcast studio for founders", createdAt = 1L, updatedAt = 1L, notebookId = null, eventDate = null, pinned = false, isPrivate = false, status = NoteStatus.OPEN, answeredAt = null, metadata = emptyMap())
        val n2 = Note(id = "b", workflow = RecordingType.IDEA, title = "Acoustic room", plainText = "Design a quiet acoustic studio for founder podcast interviews", createdAt = 2L, updatedAt = 2L, notebookId = null, eventDate = null, pinned = false, isPrivate = false, status = NoteStatus.OPEN, answeredAt = null, metadata = emptyMap())
        val n3 = Note(id = "c", workflow = RecordingType.IDEA, title = "Garden beds", plainText = "Plant autumn herbs and tomatoes in raised cedar beds", createdAt = 3L, updatedAt = 3L, notebookId = null, eventDate = null, pinned = false, isPrivate = false, status = NoteStatus.OPEN, answeredAt = null, metadata = emptyMap())

        val first = PersonalGrowthIntelligence.findBestIdeaCollision(listOf(n1, n2, n3))
        assertNotNull(first)
        val excluded = setOf(PersonalGrowthIntelligence.canonicalPair("b", "a"))
        val afterExclude = PersonalGrowthIntelligence.findBestIdeaCollision(listOf(n1, n2, n3), excludedPairs = excluded)
        assertNull(afterExclude)
    }
}
