package com.craftflowtechnologies.meetingmind.core.navigation

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.LEARNING
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK
import com.craftflowtechnologies.meetingmind.feature.learn.learningMenu
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A vertical's own menu sits on the main app menu (founder request, 2026-11-16). */
class AppMenuLocalTest {

    private val all = NotebookSpace.entries.toSet()
    private fun learning(hasSessions: Boolean = true) = learningMenu(hasSessions, {}, {}, {}, {}, {})
    private fun routes(sections: List<MenuSection>) = sections.flatMap { it.entries }.mapNotNull { (it.target as? MenuTarget.Go)?.route }

    @Test fun `a vertical's own section leads and replaces its short group`() {
        val sections = AppMenuModel.sections(all, focus = LEARNING, local = learning())
        assertEquals(LEARNING, sections.first().space)
        assertTrue(sections.first().entries.any { it.id == "learn.new" })
        assertEquals(1, sections.count { it.space == LEARNING })
        // The rest of the main menu is still there.
        assertTrue(sections.any { it.id == "everyday" })
        assertTrue(sections.any { it.id == "app" })
    }

    @Test fun `a vertical's own section never brings back a space that is off`() {
        val sections = AppMenuModel.sections(setOf(WORK), focus = LEARNING, local = learning())
        assertFalse(sections.any { it.space == LEARNING })
        assertFalse(sections.flatMap { it.entries }.any { it.id.startsWith("learn.") })
    }

    @Test fun `no route is listed twice, with or without a vertical's own section`() {
        listOf(
            AppMenuModel.sections(all),
            AppMenuModel.sections(all, focus = LEARNING),
            AppMenuModel.sections(all, focus = LEARNING, local = learning()),
            AppMenuModel.sections(all, focus = FAITH)
        ).forEach { s ->
            val r = routes(s)
            assertEquals(r.size, r.distinct().size)
            assertTrue(Routes.SETTINGS in r)
        }
    }

    @Test fun `Faith's group opens Faith settings, and only when Faith is on`() {
        assertTrue(Routes.settingsGroupRoute("faith") in routes(AppMenuModel.sections(all, focus = FAITH)))
        assertFalse(Routes.settingsGroupRoute("faith") in routes(AppMenuModel.sections(setOf(WORK, LEARNING))))
    }

    @Test fun `Ask and daily recall wait until there is something to ask about`() {
        val empty = learning(hasSessions = false).entries.map { it.id }
        assertFalse("learn.ask" in empty); assertFalse("learn.recall" in empty)
        assertTrue("learn.new" in empty)
        val full = learning().entries.map { it.id }
        assertTrue("learn.ask" in full); assertTrue("learn.recall" in full)
    }
}
