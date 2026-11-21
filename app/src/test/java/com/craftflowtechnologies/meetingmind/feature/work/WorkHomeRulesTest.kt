package com.craftflowtechnologies.meetingmind.feature.work

import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkHomeRulesTest {
    private val hour = 3_600_000L
    private val now = DueDates.startOfDay(System.currentTimeMillis()) + 11 * hour
    private val startOfToday = DueDates.startOfDay(now)
    private val endOfToday = startOfToday + 24 * hour

    private fun task(id: String, dueAt: Long?, done: Boolean = false, owner: String? = null) =
        WorkTask(id, id, dueAt, if (done) now else null, false, owner, null, null, null, null, TaskKind.TASK)

    @Test fun overdueTasksComeFirstThenTodaysByTime() {
        val list = listOf(
            task("later", startOfToday + 16 * hour),
            task("tomorrow", endOfToday + hour),
            task("undated", null),
            task("overdue", startOfToday - 30 * hour),
            task("soon", startOfToday + 12 * hour),
            task("finished", startOfToday + 9 * hour, done = true)
        )
        assertEquals(listOf("overdue", "soon", "later"), WorkHomeRules.todayTasks(list, now, endOfToday).map { it.id })
    }

    @Test fun waitingCountsPeopleNotTasks() {
        assertNull(WorkHomeRules.waitingLine(emptyList()))
        assertEquals("Waiting on Sarah", WorkHomeRules.waitingLine(listOf(task("a", null, owner = "Sarah"), task("b", null, owner = "Sarah"))))
        assertEquals("Waiting on 2 people", WorkHomeRules.waitingLine(listOf(task("a", null, owner = "Sarah"), task("b", null, owner = "Tom"))))
        assertEquals("Waiting on 1 person", WorkHomeRules.waitingLine(listOf(task("a", null))))
    }

    @Test fun theFreeDayLineSaysWhatTheDayHeld() {
        assertNull(WorkHomeRules.doneLine(0))
        assertEquals("1 meeting earlier today", WorkHomeRules.doneLine(1))
        assertEquals("3 meetings earlier today", WorkHomeRules.doneLine(3))
    }
}
