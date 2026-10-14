package com.craftflowtechnologies.meetingmind.feature.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeModesTest {

    @Test fun workHomeOffersEverythingAndWorkWhenFaithIsOn() {
        assertEquals(listOf(TodayFocus.ALL, TodayFocus.WORK), HomeModes.offered(ModeHome.WORK, showsFaith = true))
    }

    @Test fun workHomeWithoutFaithHasNothingToSwitch() {
        assertEquals(listOf(TodayFocus.ALL), HomeModes.offered(ModeHome.WORK, showsFaith = false))
    }

    @Test fun calmHomeOffersAllFaithAndWorkWhenFaithIsOn() {
        assertEquals(
            listOf(TodayFocus.ALL, TodayFocus.FAITH, TodayFocus.WORK),
            HomeModes.offered(ModeHome.CALM, showsFaith = true)
        )
    }

    @Test fun calmHomeWithoutFaithLeavesOutFaith() {
        assertEquals(listOf(TodayFocus.ALL, TodayFocus.WORK), HomeModes.offered(ModeHome.CALM, showsFaith = false))
    }

    @Test fun aModeTheHomeDoesNotOfferFallsBackToItsFirst() {
        val offered = HomeModes.offered(ModeHome.WORK, showsFaith = true)
        // Faith is not a Work mode, so arriving from Calm with Faith selected shows Everything.
        assertEquals(TodayFocus.ALL, HomeModes.effective(TodayFocus.FAITH, offered))
    }

    @Test fun nextCyclesThroughTheOfferedModesAndWraps() {
        val calm = HomeModes.offered(ModeHome.CALM, showsFaith = true)
        assertEquals(TodayFocus.FAITH, HomeModes.next(TodayFocus.ALL, calm))
        assertEquals(TodayFocus.WORK, HomeModes.next(TodayFocus.FAITH, calm))
        assertEquals(TodayFocus.ALL, HomeModes.next(TodayFocus.WORK, calm))
    }

    @Test fun nextFromAnUnofferedModeStartsFromTheShownOne() {
        // Shown mode is Everything, so the next tap goes to Work, not to whatever was stored.
        val work = HomeModes.offered(ModeHome.WORK, showsFaith = true)
        assertEquals(TodayFocus.WORK, HomeModes.next(TodayFocus.FAITH, work))
    }

    @Test fun nextNamesTheModeATapWillShow() {
        val calm = HomeModes.offered(ModeHome.CALM, showsFaith = true)
        // The label must match the mode that next() selects, so the text never lies about the tap.
        assertEquals("Next: Faith", HomeModes.nextLine(TodayFocus.ALL, calm))
        assertEquals("Next: Work", HomeModes.nextLine(TodayFocus.FAITH, calm))
        assertEquals("Next: Everything", HomeModes.nextLine(TodayFocus.WORK, calm))
    }

    @Test fun singleModeHasNoNextLine() {
        assertNull(HomeModes.nextLine(TodayFocus.ALL, listOf(TodayFocus.ALL)))
    }

    @Test fun todayOffersTheSameModesAsCalm() {
        assertEquals(
            HomeModes.offered(ModeHome.CALM, showsFaith = true),
            HomeModes.offered(ModeHome.TODAY, showsFaith = true)
        )
        assertEquals(listOf(TodayFocus.ALL, TodayFocus.WORK), HomeModes.offered(ModeHome.TODAY, showsFaith = false))
    }

    @Test fun todayDescribesItsModesLikeCalm() {
        assertEquals("Showing Faith · Faith only", HomeModes.showingLine(ModeHome.TODAY, TodayFocus.FAITH))
    }

    @Test fun showingLineSaysWhatTheModeIncludes() {
        assertEquals("Showing Everything · Work and faith", HomeModes.showingLine(ModeHome.WORK, TodayFocus.ALL))
        assertEquals("Showing Work · Work only", HomeModes.showingLine(ModeHome.WORK, TodayFocus.WORK))
        assertEquals("Showing Faith · Faith only", HomeModes.showingLine(ModeHome.CALM, TodayFocus.FAITH))
        assertEquals("Showing Everything · Faith, work and personal", HomeModes.showingLine(ModeHome.CALM, TodayFocus.ALL))
    }
}
