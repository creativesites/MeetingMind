package com.craftflowtechnologies.meetingmind.feature.work.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectStatusLogicTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test fun `past-due promises decide first`() {
        val s = projectStatus(ProjectCounts(pastDuePromises = 2, openRisks = 1, pendingDecisions = 1), now - day, now)
        assertEquals(ProjectStatusKind.NEEDS_ATTENTION, s.kind)
        assertEquals("Needs attention", s.label)
        assertEquals("2 promises past due", s.reason)
    }

    @Test fun `with no past-due promises an open risk decides`() {
        val s = projectStatus(ProjectCounts(openRisks = 1, pendingDecisions = 3), now - day, now)
        assertEquals(ProjectStatusKind.RISKS_OPEN, s.kind)
        assertEquals("1 open risk", s.reason)
    }

    @Test fun `pending decisions are named by their count`() {
        val s = projectStatus(ProjectCounts(pendingDecisions = 3), now - day, now)
        assertEquals(ProjectStatusKind.DECISION_PENDING, s.kind)
        assertEquals("3 decisions not confirmed yet", s.reason)
    }

    @Test fun `a project with no activity for fifteen days is quiet, with the days named`() {
        val s = projectStatus(ProjectCounts(), now - 20 * day, now)
        assertEquals(ProjectStatusKind.QUIET, s.kind)
        assertEquals("No activity for 20 days", s.reason)
    }

    @Test fun `a project with nothing open and recent activity is on track, and one never touched is quiet`() {
        assertEquals(ProjectStatusKind.ON_TRACK, projectStatus(ProjectCounts(), now - 2 * day, now).kind)
        assertEquals("No activity yet", projectStatus(ProjectCounts(), null, now).reason)
    }
}
