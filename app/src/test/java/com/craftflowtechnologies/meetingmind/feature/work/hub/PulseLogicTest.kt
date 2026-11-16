package com.craftflowtechnologies.meetingmind.feature.work.hub

import com.craftflowtechnologies.meetingmind.core.work.WorkSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PulseLogicTest {
    private val zone = ZoneId.of("UTC")
    private val minute = 60_000L
    /** Thursday 9 Oct 2025, 11:00 UTC. */
    private val now = ZonedDateTime.of(2025, 10, 9, 11, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun facts(
        nextMeetingStart: Long? = null,
        meetingsEndedToday: Int = 0,
        unreviewed: Int = 0,
        weeklyReviewDue: Boolean = false,
        coldStart: Boolean = false
    ) = PulseFacts(
        now = now, meetingsToday = 2, meetingsEndedToday = meetingsEndedToday, nextMeetingStart = nextMeetingStart,
        prepLeadMs = 15 * minute, unreviewed = unreviewed, weeklyReviewDue = weeklyReviewDue, coldStart = coldStart
    )

    @Test fun `day one with nothing reviewed is the calendar-led hero whatever else is true`() {
        assertEquals(PulseMoment.FIRST_DAY, pulseMoment(facts(nextMeetingStart = now + 5 * minute, unreviewed = 3, coldStart = true)))
    }

    @Test fun `a meeting inside the prep window gives the Next hero`() {
        assertEquals(PulseMoment.BEFORE_MEETING, pulseMoment(facts(nextMeetingStart = now + 10 * minute, meetingsEndedToday = 1)))
    }

    @Test fun `before the first meeting of the day the Next hero shows even when the meeting is far off`() {
        assertEquals(PulseMoment.BEFORE_MEETING, pulseMoment(facts(nextMeetingStart = now + 3 * 60 * minute, meetingsEndedToday = 0)))
    }

    @Test fun `after meetings with an unreviewed recording the To review hero shows`() {
        assertEquals(PulseMoment.AFTER_MEETINGS, pulseMoment(facts(nextMeetingStart = now + 3 * 60 * minute, meetingsEndedToday = 1, unreviewed = 1)))
    }

    @Test fun `the weekly review outranks quiet but not an unreviewed recording`() {
        assertEquals(PulseMoment.WEEKLY_REVIEW, pulseMoment(facts(weeklyReviewDue = true)))
        assertEquals(PulseMoment.AFTER_MEETINGS, pulseMoment(facts(weeklyReviewDue = true, unreviewed = 1, meetingsEndedToday = 1)))
    }

    @Test fun `nothing due and no meeting ahead is quiet`() {
        assertEquals(PulseMoment.QUIET, pulseMoment(facts(meetingsEndedToday = 1)))
    }

    @Test fun `the weekly review is due on Friday at or after the review time only`() {
        val s = WorkSettings()  // weeklyReviewDay = 6 (Friday), weeklyReviewMinute = 16:30
        val friday = { h: Int, m: Int -> ZonedDateTime.of(2025, 10, 10, h, m, 0, 0, zone).toInstant().toEpochMilli() }
        assertTrue(weeklyReviewDue(s, friday(16, 30), zone))
        assertTrue(weeklyReviewDue(s, friday(18, 0), zone))
        assertFalse(weeklyReviewDue(s, friday(15, 59), zone))
        assertFalse(weeklyReviewDue(s, now, zone))  // Thursday
    }

    @Test fun `ranking puts failed recordings first, then what I owe, then what they owe past due`() {
        val ranked = rankNeeds(
            listOf(
                NeedCandidate(NeedRank.INBOX, "inbox"),
                NeedCandidate(NeedRank.THEY_OWE_OPEN, "they-open"),
                NeedCandidate(NeedRank.FAILED, "failed"),
                NeedCandidate(NeedRank.DECISION, "decision"),
                NeedCandidate(NeedRank.YOU_OWE_DUE, "mine"),
                NeedCandidate(NeedRank.THEY_OWE_PAST, "they-past"),
                NeedCandidate(NeedRank.QUIET, "quiet")
            )
        )
        assertEquals(listOf("failed", "mine", "they-past", "decision"), ranked.map { it.value })
    }

    @Test fun `ranking keeps the incoming order within a rank and never shows more than the limit`() {
        val same = rankNeeds(listOf(NeedCandidate(NeedRank.DECISION, "a"), NeedCandidate(NeedRank.DECISION, "b"), NeedCandidate(NeedRank.DECISION, "c")), limit = 2)
        assertEquals(listOf("a", "b"), same.map { it.value })
        assertTrue(rankNeeds(emptyList<NeedCandidate<String>>()).isEmpty())
    }
}
