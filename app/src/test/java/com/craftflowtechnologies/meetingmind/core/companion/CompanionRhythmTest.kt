package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.core.companion.CompanionRhythm.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompanionRhythmTest {

    private val day = LocalClock.DayMs
    private val hour = LocalClock.HourMs

    /** 2026-11-16 is a Monday (epoch day 20773); 2026-11-18 a Wednesday. */
    private val monday = 20773L
    private fun at(epochDay: Long, h: Int = 8) = epochDay * day + h * hour
    private val wednesdayEvening = LocalClock(at(monday + 2, 20))

    @Test
    fun `faith counts distinct days this week`() {
        val reads = listOf(at(monday), at(monday, 21), at(monday + 1), at(monday + 2))
        assertEquals(CompanionRhythm.Line(Kind.FAITH_DAYS, 3), CompanionRhythm.thisWeek(Kind.FAITH_DAYS, reads, wednesdayEvening))
    }

    @Test
    fun `study counts distinct days this week`() {
        val reviews = listOf(at(monday, 7), at(monday, 9), at(monday + 2))
        assertEquals(CompanionRhythm.Line(Kind.STUDY_DAYS, 2), CompanionRhythm.thisWeek(Kind.STUDY_DAYS, reviews, wednesdayEvening))
    }

    @Test
    fun `notes kept counts every note including several on one day`() {
        val notes = listOf(at(monday, 9), at(monday, 10), at(monday, 11))
        assertEquals(CompanionRhythm.Line(Kind.NOTES_KEPT, 3), CompanionRhythm.thisWeek(Kind.NOTES_KEPT, notes, wednesdayEvening))
    }

    @Test
    fun `a quiet week shows no line at all`() {
        assertNull(CompanionRhythm.thisWeek(Kind.FAITH_DAYS, emptyList(), wednesdayEvening))
        assertNull(CompanionRhythm.thisWeek(Kind.FAITH_DAYS, listOf(at(monday)), wednesdayEvening))
        assertNull(CompanionRhythm.thisWeek(Kind.NOTES_KEPT, listOf(at(monday)), wednesdayEvening))
    }

    @Test
    fun `last week does not carry over into this week`() {
        val lastWeek = listOf(at(monday - 1), at(monday - 2), at(monday - 3), at(monday))
        assertNull(CompanionRhythm.thisWeek(Kind.FAITH_DAYS, lastWeek, wednesdayEvening))
    }

    @Test
    fun `a time in the future is ignored`() {
        val reads = listOf(at(monday), at(monday + 4))
        assertNull(CompanionRhythm.thisWeek(Kind.FAITH_DAYS, reads, wednesdayEvening))
    }

    @Test
    fun `the week follows the local zone`() {
        // 23:00 UTC on Sunday is already Monday at UTC+2, so a Sunday-night UTC reading belongs to the new week.
        val sundayLateUtc = at(monday + 6, 23)
        val nextMondayMorningLocal = LocalClock(at(monday + 7, 6), utcOffsetMs = 2 * hour)
        val notes = listOf(sundayLateUtc, at(monday + 7, 5))
        assertEquals(CompanionRhythm.Line(Kind.NOTES_KEPT, 2), CompanionRhythm.thisWeek(Kind.NOTES_KEPT, notes, nextMondayMorningLocal))
        // In UTC the Sunday note is last week's, so only one is this week's.
        assertNull(CompanionRhythm.thisWeek(Kind.NOTES_KEPT, notes, LocalClock(at(monday + 7, 6))))
    }

    @Test
    fun `rhythm copy never talks about streaks or what was missed`() {
        val file = listOf(
            File("src/main/res/values/strings_companion_milestones.xml"),
            File("app/src/main/res/values/strings_companion_milestones.xml")
        ).first { it.exists() }
        val text = file.readText().lowercase()
        listOf("streak", "missed", "don't break", "haven't", "you lost").forEach { banned ->
            val copy = text.lines().filter { it.contains("<item") || it.contains("<string") }
            assertTrue("found \"$banned\" in rhythm or milestone copy", copy.none { it.contains(banned) })
        }
    }
}
