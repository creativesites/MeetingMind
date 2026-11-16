package com.craftflowtechnologies.meetingmind.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bar follows the person's spaces (core/navigation/BarConfig.kt, Work spec R2): Today, Notes,
 * New, then two space places. Replaces TabSlotTest, whose Work-or-Search slot R2 retired.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class BottomBarTest {
    @get:Rule val compose = createComposeRule()
    private val tapped = mutableListOf<BottomNavDestination>()

    private fun show(spaces: Set<NotebookSpace>, current: BottomNavDestination = BottomNavDestination.HOME) = compose.setContent {
        MeetMindTheme {
            CompositionLocalProvider(LocalAppIdentity provides AppIdentity(spaces = spaces)) {
                AppBottomNavigationBar(current, onNavigate = { tapped += it })
            }
        }
    }

    @Test fun everySpaceOnShowsWorkAndLearn() {
        show(NotebookSpace.entries.toSet())
        compose.onNodeWithTag("bottom_nav_work").performClick()
        compose.onNodeWithTag("bottom_nav_learn").performClick()
        assertEquals(listOf(BottomNavDestination.WORK, BottomNavDestination.LEARN), tapped)
        compose.onNodeWithTag("bottom_nav_settings").assertDoesNotExist()
        compose.onNodeWithTag("bottom_nav_faith").assertDoesNotExist()
    }

    @Test fun faithOnlyGetsFaithAndTasks() {
        show(setOf(NotebookSpace.FAITH))
        compose.onNodeWithTag("bottom_nav_faith").performClick()
        compose.onNodeWithTag("bottom_nav_tasks").performClick()
        assertEquals(listOf(BottomNavDestination.FAITH, BottomNavDestination.TASKS), tapped)
        compose.onNodeWithTag("bottom_nav_work").assertDoesNotExist()
    }

    @Test fun personalOnlyGetsTasksAndSearch() {
        show(setOf(NotebookSpace.PERSONAL))
        compose.onNodeWithTag("bottom_nav_tasks").performClick()
        compose.onNodeWithTag("bottom_nav_search").performClick()
        assertEquals(listOf(BottomNavDestination.TASKS, BottomNavDestination.SEARCH), tapped)
    }

    @Test fun todayNotesAndNewAreAlwaysThere() {
        show(setOf(NotebookSpace.WORK))
        compose.onNodeWithTag("bottom_nav_notes").performClick()
        compose.onNodeWithTag("bottom_nav_new").performClick()
        assertEquals(listOf(BottomNavDestination.NOTES, BottomNavDestination.NEW), tapped)
        compose.onNodeWithTag("bottom_nav_notes").assertContentDescriptionEquals("Notes")
    }

    @Test fun theCurrentSpaceShowsAsTheActiveChip() {
        show(setOf(NotebookSpace.WORK, NotebookSpace.FAITH), current = BottomNavDestination.FAITH)
        compose.onNodeWithTag("bottom_nav_faith").assertExists()
        compose.onNodeWithTag("bottom_nav_work").assertContentDescriptionEquals("Work")
    }
}
