package com.craftflowtechnologies.meetingmind.feature.today.home

import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.feature.today.TodayFocus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionsTest {
    private fun spaces(vararg s: NotebookSpace) = AppIdentity(spaces = s.toSet())

    @Test fun allThreeSpacesGetTheFullHome() {
        val sections = HomeSections.of(spaces(NotebookSpace.WORK, NotebookSpace.FAITH, NotebookSpace.PERSONAL), TodayFocus.ALL)
        assertEquals(HomeSections(faith = true, work = true, personal = true), sections)
        assertEquals(HomeLayout.ALL, sections.layout)
    }

    @Test fun workAndPersonalHasNoFaithSections() {
        val sections = HomeSections.of(spaces(NotebookSpace.WORK, NotebookSpace.PERSONAL), TodayFocus.ALL)
        assertEquals(HomeSections(faith = false, work = true, personal = true), sections)
        assertEquals(HomeLayout.WORK_PERSONAL, sections.layout)
    }

    @Test fun personalOnlyHasNoWorkOrFaith() {
        val sections = HomeSections.of(spaces(NotebookSpace.PERSONAL), TodayFocus.ALL)
        assertEquals(HomeSections(faith = false, work = false, personal = true), sections)
        assertEquals(HomeLayout.PERSONAL, sections.layout)
    }

    @Test fun learningCountsAsWork() {
        val sections = HomeSections.of(spaces(NotebookSpace.LEARNING, NotebookSpace.PERSONAL), TodayFocus.ALL)
        assertEquals(true, sections.work)
        assertEquals(HomeLayout.WORK_PERSONAL, sections.layout)
    }

    @Test fun faithFocusShowsOnlyFaith() {
        val sections = HomeSections.of(spaces(NotebookSpace.WORK, NotebookSpace.FAITH, NotebookSpace.PERSONAL), TodayFocus.FAITH)
        assertEquals(HomeSections(faith = true, work = false, personal = false), sections)
    }

    @Test fun workFocusShowsOnlyWork() {
        val sections = HomeSections.of(spaces(NotebookSpace.WORK, NotebookSpace.FAITH, NotebookSpace.PERSONAL), TodayFocus.WORK)
        assertEquals(HomeSections(faith = false, work = true, personal = false), sections)
    }

    @Test fun focusThatLeavesNothingFallsBackToEverything() {
        // A personal-only person has no faith to focus on, so the faith chip shows their whole home.
        val sections = HomeSections.of(spaces(NotebookSpace.PERSONAL), TodayFocus.FAITH)
        assertEquals(HomeSections(faith = false, work = false, personal = true), sections)
    }
}
