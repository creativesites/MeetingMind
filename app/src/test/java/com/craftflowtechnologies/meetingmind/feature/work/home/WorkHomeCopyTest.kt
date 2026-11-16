package com.craftflowtechnologies.meetingmind.feature.work.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkHomeCopyTest {

    private val day = 86_400_000L
    private val now = 100 * day

    @Test fun summaryNamesTheDayAndWhatNeedsYou() {
        assertEquals(
            "2 meetings, 5 tasks due, 1 overdue. One thing needs you.",
            WorkHomeCopy.summarySentence(meetings = 2, tasksDueToday = 5, overdue = 1, needYou = 1)
        )
    }

    @Test fun summaryWithOnlyMeetingsHasNoTrailingSentence() {
        assertEquals("1 meeting.", WorkHomeCopy.summarySentence(meetings = 1, tasksDueToday = 0, overdue = 0, needYou = 0))
    }

    @Test fun summaryCountsThingsThatNeedYouWhenTheDayIsEmpty() {
        assertEquals("3 things need you.", WorkHomeCopy.summarySentence(meetings = 0, tasksDueToday = 0, overdue = 0, needYou = 3))
    }

    @Test fun aQuietDayHasAHumanSentence() {
        assertEquals(
            "A clear day so far. Record a conversation when one starts.",
            WorkHomeCopy.summarySentence(meetings = 0, tasksDueToday = 0, overdue = 0, needYou = 0)
        )
    }

    @Test fun pluralsAreRight() {
        assertEquals("1 task", WorkHomeCopy.plural(1, "task"))
        assertEquals("0 tasks", WorkHomeCopy.plural(0, "task"))
        assertEquals("2 tasks", WorkHomeCopy.plural(2, "task"))
    }

    @Test fun aProjectWithNoOpenWorkIsClear() {
        assertEquals("Clear", WorkHomeCopy.projectStatus(openTasks = 0, lastActivity = now, now = now))
    }

    @Test fun aProjectWithFewOpenTasksIsInProgress() {
        assertEquals("In progress", WorkHomeCopy.projectStatus(openTasks = 2, lastActivity = now, now = now))
    }

    @Test fun aBusyProjectNeedsAttention() {
        assertEquals("Needs attention", WorkHomeCopy.projectStatus(openTasks = WorkHomeCopy.BUSY_OPEN_TASKS, lastActivity = now, now = now))
    }

    @Test fun openWorkOnAProjectUntouchedForTwoWeeksIsQuiet() {
        assertEquals("Quiet", WorkHomeCopy.projectStatus(openTasks = 3, lastActivity = now - WorkHomeCopy.QUIET_MS - 1, now = now))
    }

    @Test fun aQuietProjectWithNothingOpenIsStillClear() {
        assertEquals("Clear", WorkHomeCopy.projectStatus(openTasks = 0, lastActivity = now - WorkHomeCopy.QUIET_MS - 1, now = now))
    }

    @Test fun unknownActivityIsNotQuiet() {
        assertEquals("In progress", WorkHomeCopy.projectStatus(openTasks = 1, lastActivity = null, now = now))
    }

    @Test fun iOweThemShowsYouOwe() {
        assertEquals("you owe", WorkHomeCopy.peopleBadge("james", owedToMe = emptySet(), iOwe = setOf("james")))
    }

    @Test fun theyOweMeShowsOwes() {
        assertEquals("owes", WorkHomeCopy.peopleBadge("sarah", owedToMe = setOf("sarah"), iOwe = emptySet()))
    }

    @Test fun bothWaysShowsWhatIOweFirst() {
        assertEquals("you owe", WorkHomeCopy.peopleBadge("ana", owedToMe = setOf("ana"), iOwe = setOf("ana")))
    }

    @Test fun noOpenWorkMeansNoBadge() {
        assertNull(WorkHomeCopy.peopleBadge("mutale", owedToMe = setOf("sarah"), iOwe = setOf("james")))
    }
}
