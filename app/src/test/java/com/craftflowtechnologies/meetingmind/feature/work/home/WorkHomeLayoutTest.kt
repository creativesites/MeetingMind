package com.craftflowtechnologies.meetingmind.feature.work.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkHomeLayoutTest {

    @Test fun nextMeetingLeadsTheHero() {
        assertEquals(WorkHero.MEETING, WorkHomeLayout.hero(hasNextMeeting = true, needsCount = 3))
        assertEquals(WorkHero.MEETING, WorkHomeLayout.hero(hasNextMeeting = true, needsCount = 0))
    }

    @Test fun withoutAMeetingTheHeroIsWhatNeedsThePerson() {
        assertEquals(WorkHero.ATTENTION, WorkHomeLayout.hero(hasNextMeeting = false, needsCount = 1))
    }

    @Test fun withNothingToDoTheHeroIsEmpty() {
        assertEquals(WorkHero.NONE, WorkHomeLayout.hero(hasNextMeeting = false, needsCount = 0))
    }

    @Test fun needsShowsReviewsFirstThenFillsWithFollowUps() {
        assertEquals(2 to 2, WorkHomeLayout.needsShown(reviews = 2, followUps = 5))
        assertEquals(1 to 0, WorkHomeLayout.needsShown(reviews = 1, followUps = 0))
    }

    @Test fun needsNeverExceedsTheCap() {
        assertEquals(4 to 0, WorkHomeLayout.needsShown(reviews = 6, followUps = 3))
        assertEquals(0 to 4, WorkHomeLayout.needsShown(reviews = 0, followUps = 9))
        assertEquals(0 to 0, WorkHomeLayout.needsShown(reviews = 0, followUps = 0))
    }

    @Test fun needsRespectsACustomCap() {
        assertEquals(2 to 1, WorkHomeLayout.needsShown(reviews = 5, followUps = 5, cap = 3))
    }

    @Test fun pastEventIsPast() {
        assertEquals(ScheduleState.PAST, WorkHomeLayout.scheduleState(start = 100, end = 200, now = 300))
    }

    @Test fun eventInProgressIsLive() {
        assertEquals(ScheduleState.LIVE, WorkHomeLayout.scheduleState(start = 100, end = 400, now = 300))
    }

    @Test fun eventAtItsEndInstantIsStillLive() {
        assertEquals(ScheduleState.LIVE, WorkHomeLayout.scheduleState(start = 100, end = 300, now = 300))
    }

    @Test fun futureEventIsUpcoming() {
        assertEquals(ScheduleState.UPCOMING, WorkHomeLayout.scheduleState(start = 400, end = 500, now = 300))
    }

    @Test fun eventWithoutEndUsesItsStartAsItsEnd() {
        assertEquals(ScheduleState.PAST, WorkHomeLayout.scheduleState(start = 100, end = null, now = 300))
        assertEquals(ScheduleState.LIVE, WorkHomeLayout.scheduleState(start = 300, end = null, now = 300))
        assertEquals(ScheduleState.UPCOMING, WorkHomeLayout.scheduleState(start = 400, end = null, now = 300))
    }

}
