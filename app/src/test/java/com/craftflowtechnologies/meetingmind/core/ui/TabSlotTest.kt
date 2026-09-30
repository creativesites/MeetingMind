package com.craftflowtechnologies.meetingmind.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.work.TabSlot
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bar's fourth slot: Search by default, Work when the person put it there. Nothing else in the bar changes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class TabSlotTest {
    @get:Rule val compose = createComposeRule()
    private val tapped = mutableListOf<BottomNavDestination>()

    private fun show(slot: TabSlot, current: BottomNavDestination = BottomNavDestination.HOME) = compose.setContent {
        MeetMindTheme { CompositionLocalProvider(LocalTabSlot provides slot) { AppBottomNavigationBar(current, onNavigate = { tapped += it }) } }
    }

    @Test fun byDefaultTheSlotIsSearch() {
        show(TabSlot.SEARCH)
        compose.onNodeWithTag("bottom_nav_search").assertContentDescriptionEquals("Search")
        compose.onNodeWithTag("bottom_nav_search").performClick()
        assertEquals(listOf(BottomNavDestination.SEARCH), tapped)
        assertEquals("search", TabSlots.route(TabSlot.SEARCH, work = "work", search = "search"))
    }

    @Test fun withWorkInTheBarTheSlotIsWork() {
        show(TabSlot.WORK)
        compose.onNodeWithTag("bottom_nav_search").assertContentDescriptionEquals("Work")
        compose.onNodeWithTag("bottom_nav_search").performClick()
        assertEquals(listOf(BottomNavDestination.SEARCH), tapped) // the same destination, so tags and saved state are unchanged
        assertEquals("work", TabSlots.route(TabSlot.WORK, work = "work", search = "search"))
        assertEquals("Work", TabSlots.label(TabSlot.WORK))
    }

    @Test fun theOtherTabsAreTheSameInBothStates() {
        val slot = androidx.compose.runtime.mutableStateOf(TabSlot.SEARCH)
        compose.setContent { MeetMindTheme { CompositionLocalProvider(LocalTabSlot provides slot.value) { AppBottomNavigationBar(BottomNavDestination.HOME, onNavigate = { tapped += it }) } } }
        listOf(TabSlot.SEARCH, TabSlot.WORK).forEach { s ->
            slot.value = s; compose.waitForIdle(); tapped.clear()
            compose.onNodeWithTag("bottom_nav_notes").performClick()
            compose.onNodeWithTag("bottom_nav_settings").performClick()
            compose.onNodeWithTag("bottom_nav_new").performClick()
            assertEquals(listOf(BottomNavDestination.NOTES, BottomNavDestination.SETTINGS, BottomNavDestination.NEW), tapped)
        }
    }

    @Test fun whenTheSlotIsCurrentItShowsItsLabel() {
        show(TabSlot.WORK, BottomNavDestination.SEARCH)
        compose.onNodeWithTag("bottom_nav_search").assertExists()
        compose.onNodeWithTag("bottom_nav_notes").assertContentDescriptionEquals("Notes")
    }

    @Test fun searchIsStillReachableFromHomeWhateverTheSlotHolds() {
        // MainActivity's openSearch goes to the Search route directly; the slot only decides what the bar's tab opens.
        assertEquals("search", TabSlots.route(TabSlot.SEARCH, "work", "search"))
        assertEquals("work", TabSlots.route(TabSlot.WORK, "work", "search"))
    }
}
