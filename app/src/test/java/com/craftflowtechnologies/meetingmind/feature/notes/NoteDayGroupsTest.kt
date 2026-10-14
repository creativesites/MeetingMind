package com.craftflowtechnologies.meetingmind.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class NoteDayGroupsTest {
    private val zone = ZoneId.of("UTC")
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test fun aNoteFromNowIsToday() = assertEquals(DayGroup.TODAY, DayGroups.of(now, now, zone))
    @Test fun aNoteFromYesterdayIsYesterday() = assertEquals(DayGroup.YESTERDAY, DayGroups.of(now - day, now, zone))
    @Test fun aNoteFromThisWeekIsThisWeek() = assertEquals(DayGroup.THIS_WEEK, DayGroups.of(now - 3 * day, now, zone))
    @Test fun anOlderNoteIsEarlier() = assertEquals(DayGroup.EARLIER, DayGroups.of(now - 10 * day, now, zone))
    @Test fun aNoteFromTheFutureIsToday() = assertEquals(DayGroup.TODAY, DayGroups.of(now + day, now, zone))
}
