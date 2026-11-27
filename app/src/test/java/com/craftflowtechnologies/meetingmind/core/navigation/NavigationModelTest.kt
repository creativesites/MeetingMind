package com.craftflowtechnologies.meetingmind.core.navigation

import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.faith.PrayerTime
import com.craftflowtechnologies.meetingmind.core.faith.ReminderSettings
import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.LEARNING
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.feature.today.home.SpaceGlance
import com.craftflowtechnologies.meetingmind.feature.today.home.SpaceLines
import com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bar, the app menu, background schedules and homes all follow the spaces a person chose. */
class NavigationModelTest {

    // ---- Bar (Work spec R2 table, plus Faith and Personal claiming places under the same rule).
    @Test fun `bar places follow the R2 table`() {
        assertEquals(listOf(BarSlot.WORK, BarSlot.LEARN), BarConfig.slots(setOf(WORK, LEARNING)))
        assertEquals(listOf(BarSlot.WORK, BarSlot.TASKS), BarConfig.slots(setOf(WORK)))
        assertEquals(listOf(BarSlot.LEARN, BarSlot.TASKS), BarConfig.slots(setOf(LEARNING)))
        assertEquals(listOf(BarSlot.PERSONAL, BarSlot.TASKS), BarConfig.slots(setOf(PERSONAL)))
        assertEquals(listOf(BarSlot.FAITH, BarSlot.PERSONAL), BarConfig.slots(setOf(FAITH, PERSONAL)))
        assertEquals(listOf(BarSlot.WORK, BarSlot.FAITH), BarConfig.slots(setOf(WORK, FAITH)))
    }

    @Test fun `the home's vertical leads the bar`() {
        val all = NotebookSpace.entries.toSet()
        assertEquals(listOf(BarSlot.WORK, BarSlot.LEARN), BarConfig.slots(AppIdentity(all), HomeStyle.TODAY))
        assertEquals(listOf(BarSlot.LEARN, BarSlot.WORK), BarConfig.slots(AppIdentity(all), HomeStyle.LEARNING))
        assertEquals(listOf(BarSlot.PERSONAL, BarSlot.WORK), BarConfig.slots(AppIdentity(all), HomeStyle.PERSONAL))
        val sanctuary = AppIdentity(all, look = com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel.SANCTUARY)
        assertEquals(listOf(BarSlot.FAITH, BarSlot.WORK), BarConfig.slots(sanctuary, HomeStyle.TODAY))
    }

    @Test fun `a space that is off never gets a place`() {
        BarConfig.slots(setOf(WORK, PERSONAL)).forEach { assertTrue(it != BarSlot.FAITH && it != BarSlot.LEARN) }
    }

    // ---- App menu.
    private fun routes(spaces: Set<NotebookSpace>, focus: NotebookSpace? = null) =
        AppMenuModel.sections(spaces, focus).flatMap { it.entries }.mapNotNull { (it.target as? MenuTarget.Go)?.route }

    @Test fun `turning Faith off removes every Faith row`() {
        val r = routes(setOf(WORK, LEARNING, PERSONAL))
        listOf(Routes.FAITH, Routes.BIBLE_HOME, Routes.PRAYER_LIST, Routes.TESTIMONIES, Routes.PLANS).forEach { assertFalse(it in r) }
        assertFalse(AppMenuModel.sections(setOf(WORK)).any { it.space == FAITH })
    }

    @Test fun `turning Work or Learning off removes their rows`() {
        val r = routes(setOf(FAITH))
        assertFalse(Routes.WORK in r); assertFalse(Routes.LEARN in r); assertFalse(Routes.WORK_INBOX in r)
        assertTrue(Routes.TASKS in r) // Tasks is everyone's
    }

    @Test fun `the menu opened in a vertical puts that vertical first`() {
        assertEquals(FAITH, AppMenuModel.sections(NotebookSpace.entries.toSet(), focus = FAITH).first().space)
        assertEquals(LEARNING, AppMenuModel.sections(NotebookSpace.entries.toSet(), focus = LEARNING).first().space)
        assertEquals("everyday", AppMenuModel.sections(NotebookSpace.entries.toSet()).first().id)
    }

    @Test fun `settings is always in the menu and no row is listed twice`() {
        listOf(setOf(WORK), setOf(FAITH), NotebookSpace.entries.toSet()).forEach { spaces ->
            val r = routes(spaces)
            assertTrue(Routes.SETTINGS in r)
            assertEquals(r.size, r.distinct().size)
        }
    }

    // ---- Background schedules.
    @Test fun `faith off stops the devotional and faith reminders but keeps the settings`() {
        val profile = DevotionalProfile(enabled = true)
        assertFalse(SpaceSchedules.devotionalFor(profile, setOf(WORK)).enabled)
        assertEquals(profile, SpaceSchedules.devotionalFor(profile, setOf(FAITH)))
        val r = ReminderSettings(prayerTimes = setOf(PrayerTime.entries.first()), readingNudge = true, eveningReflection = true, meetingPrep = true)
        val off = SpaceSchedules.remindersFor(r, setOf(WORK))
        assertTrue(off.prayerTimes.isEmpty()); assertFalse(off.readingNudge); assertFalse(off.eveningReflection)
        assertTrue(off.meetingPrep)
        assertEquals(r, SpaceSchedules.remindersFor(r, setOf(FAITH, WORK)))
    }

    // ---- Homes.
    @Test fun `a vertical's home falls back to Today while its space is off`() {
        assertEquals(HomeStyle.TODAY, HomeStyle.PROFESSIONAL.effectiveFor(setOf(FAITH)))
        assertEquals(HomeStyle.TODAY, HomeStyle.LEARNING.effectiveFor(setOf(WORK)))
        assertEquals(HomeStyle.PROFESSIONAL, HomeStyle.PROFESSIONAL.effectiveFor(setOf(WORK)))
        assertEquals(listOf(HomeStyle.TODAY, HomeStyle.CALM), HomeStyle.selectableFor(setOf(FAITH)))
    }

    @Test fun `space cards say something true and short`() {
        assertEquals("Next: Standup", SpaceLines.line(WORK, SpaceGlance(nextTitle = "Standup")))
        assertEquals("3 on today", SpaceLines.line(WORK, SpaceGlance(todayCount = 3)))
        assertEquals("Today's plan is clear", SpaceLines.line(LEARNING, SpaceGlance()))
        assertEquals("1 card to review", SpaceLines.line(LEARNING, SpaceGlance(learnDue = 1)))
        assertEquals("Today's devotional", SpaceLines.line(FAITH, SpaceGlance()))
        assertEquals("2 due today", SpaceLines.tasks(SpaceGlance(tasksDue = 2)))
    }
}
