package com.craftflowtechnologies.meetingmind.core.widget

import com.craftflowtechnologies.meetingmind.core.work.PulseDayLine
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import com.craftflowtechnologies.meetingmind.core.work.WorkSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the next-meeting widget says. It sits on a home screen, so a sensitive profile gets no names. */
class NextMeetingModelTest {
    private val now = 1_000_000L
    private val event = PulseEvent("e@1", "Ms Nkomo follow-up", now + 12 * 60_000, now + 42 * 60_000, listOf("Ms Nkomo"))
    private val line = PulseDayLine(event, 2, 1, 0, 0, "Ms Nkomo", firstMeeting = false)

    @Test fun anOrdinaryProfileSeesTheMeetingAndItsPrepLine() {
        val m = NextMeetingModel.of(WorkSettings(), event, line, now)
        assertEquals("Ms Nkomo follow-up", m.title)
        assertEquals("With Ms Nkomo · 3 still open", m.prep)
        assertEquals(12 * 60_000L, m.startsInMs)
        assertFalse(m.started)
    }

    @Test fun aSensitiveProfileSeesNoTitleAndNoNames() {
        val m = NextMeetingModel.of(WorkSettings.forProfile(WorkProfile.CLINICAL), event, line, now)
        assertEquals("Your next meeting", m.title)
        assertEquals("3 open", m.prep)
        assertFalse((m.title + m.prep).contains("Nkomo"))
    }

    @Test fun aMeetingAlreadyUnderwayShowsNowNotACountdown() {
        val m = NextMeetingModel.of(WorkSettings(), event.copy(begin = now - 60_000), line, now)
        assertTrue(m.started); assertEquals(0L, m.startsInMs)
    }

    @Test fun strangersGetNoPrepLineAndNoEventsSaysSo() {
        assertEquals("", NextMeetingModel.of(WorkSettings(), event, line.copy(firstMeeting = true), now).prep)
        assertEquals("", NextMeetingModel.of(WorkSettings(), event, null, now).prep)
        val none = NextMeetingModel.of(WorkSettings(), null, null, now)
        assertEquals("Nothing else today", none.title); assertNull(none.event)
    }
}
