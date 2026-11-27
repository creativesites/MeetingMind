package com.craftflowtechnologies.meetingmind.feature.work.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkUpNextTest {

    @Test fun inTheMorningTheNextMeetingLeads() {
        assertEquals(UpNextChoice.MEETING, WorkUpNext.choose(hour = 9, hasNextMeeting = true, reviews = 2, followUps = 1))
    }

    @Test fun fromEveningWhatToReviewLeadsEvenWithAMeetingLeft() {
        assertEquals(UpNextChoice.REVIEW, WorkUpNext.choose(hour = 18, hasNextMeeting = true, reviews = 2, followUps = 0))
        assertEquals(UpNextChoice.REVIEW, WorkUpNext.choose(hour = WorkUpNext.EVENING_HOUR, hasNextMeeting = false, reviews = 1, followUps = 0))
    }

    @Test fun justBeforeEveningIsStillTheMeeting() {
        assertEquals(UpNextChoice.MEETING, WorkUpNext.choose(hour = WorkUpNext.EVENING_HOUR - 1, hasNextMeeting = true, reviews = 3, followUps = 0))
    }

    @Test fun withNoMeetingAndNothingToReviewTheFollowUpLeads() {
        assertEquals(UpNextChoice.FOLLOW_UP, WorkUpNext.choose(hour = 10, hasNextMeeting = false, reviews = 0, followUps = 2))
    }

    @Test fun withNothingAtAllThereIsNoTile() {
        assertEquals(UpNextChoice.NONE, WorkUpNext.choose(hour = 10, hasNextMeeting = false, reviews = 0, followUps = 0))
        assertEquals(UpNextChoice.NONE, WorkUpNext.choose(hour = 20, hasNextMeeting = false, reviews = 0, followUps = 0))
    }

    @Test fun reviewWithNoMeetingLeadsInTheMorningToo() {
        assertEquals(UpNextChoice.REVIEW, WorkUpNext.choose(hour = 8, hasNextMeeting = false, reviews = 1, followUps = 0))
    }

    @Test fun reviewLineCountsRecordingsWithTheRightPlural() {
        assertEquals("To review · 1 recording", WorkUpNext.reviewLine(1))
        assertEquals("To review · 2 recordings", WorkUpNext.reviewLine(2))
    }
}
