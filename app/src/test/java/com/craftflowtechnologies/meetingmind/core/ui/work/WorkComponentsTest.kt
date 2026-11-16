package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.Color
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Work components that carry behaviour: the vertical bar, the progress ring, change lines and project colours. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class WorkComponentsTest {
    @get:Rule val compose = createComposeRule()

    private val items = listOf(
        VerticalBarItem("today", "Today", Icons.Filled.Home),
        VerticalBarItem("tasks", "Tasks", Icons.Filled.CheckCircle),
        VerticalBarItem("search", "Search", Icons.Filled.Search)
    )

    @Test fun theSelectedItemShowsItsLabelAndTheOthersAreIconOnly() {
        compose.setContent {
            MeetMindTheme(darkTheme = false) { VerticalBar(items, selected = "tasks", onSelect = {}) }
        }
        compose.onNodeWithText("Tasks").assertExists()
        compose.onNodeWithText("Today").assertDoesNotExist()
        compose.onNodeWithText("Search").assertDoesNotExist()
        compose.onNodeWithTag("vbar_today").assertExists()
        compose.onNodeWithContentDescription("Today").assertExists()
    }

    @Test fun tappingAnIdleItemSelectsItsKey() {
        val selected = mutableListOf<String>()
        compose.setContent {
            MeetMindTheme(darkTheme = true) { VerticalBar(items, selected = "tasks", onSelect = { selected += it }) }
        }
        compose.onNodeWithTag("vbar_search").performClick()
        assertEquals(listOf("search"), selected)
    }

    @Test fun tappingTheSelectedItemReportsItsKeyToo() {
        val selected = mutableListOf<String>()
        compose.setContent {
            MeetMindTheme(darkTheme = false) { VerticalBar(items, selected = "tasks", onSelect = { selected += it }) }
        }
        compose.onNodeWithTag("vbar_tasks").performClick()
        assertEquals(listOf("tasks"), selected)
    }

    @Test fun theHomeButtonLeavesTheVerticalOnlyWhenItIsGiven() {
        var homeTaps = 0
        compose.setContent {
            MeetMindTheme(darkTheme = false) {
                VerticalBar(items, selected = "tasks", onSelect = {}, homeItem = { homeTaps++ })
            }
        }
        compose.onNodeWithTag("vbar_home").performClick()
        assertEquals(1, homeTaps)
    }

    @Test fun withoutAHomeItemThereIsNoHomeButton() {
        compose.setContent {
            MeetMindTheme(darkTheme = false) { VerticalBar(items, selected = "tasks", onSelect = {}) }
        }
        compose.onNodeWithTag("vbar_home").assertDoesNotExist()
    }

    @Test fun changeLineShowsTheLabelAndBothValues() {
        compose.setContent {
            MeetMindTheme(darkTheme = false) { ChangeLine(label = "Credentials", old = "Mon 17 Nov", new = "Thu 19 Nov") }
        }
        compose.onNodeWithText("Credentials").assertTextEquals("Credentials")
        compose.onNodeWithText("Mon 17 Nov").assertTextEquals("Mon 17 Nov")
        compose.onNodeWithText("Thu 19 Nov").assertTextEquals("Thu 19 Nov")
    }

    @Test fun progressRingReportsDoneOfTotalAndPercentage() {
        compose.setContent {
            MeetMindTheme(darkTheme = false) { ProgressRing(done = 2, total = 5) }
        }
        compose.onNodeWithContentDescription("2 of 5 done").assertExists()
        compose.onNodeWithText("40%").assertExists()
    }

    @Test fun progressRingWithNoItemsIsEmptyNotBroken() {
        compose.setContent {
            MeetMindTheme(darkTheme = false) { ProgressRing(done = 0, total = 0) }
        }
        compose.onNodeWithText("0%").assertExists()
    }

    @Test fun projectColourIsStableForAnId() {
        assertEquals(projectColor("acme", dark = false), projectColor("acme", dark = false))
        assertEquals(projectColor("acme", dark = true), projectColor("acme", dark = true))
    }

    @Test fun projectColourHasADistinctDarkVariant() {
        assertNotEquals(projectColor("acme", dark = false), projectColor("acme", dark = true))
    }

    @Test fun projectColourStaysInThePalette() {
        val palette = (0 until 50).map { projectColor("project-$it", dark = false) }.toSet()
        assertTrue(palette.size <= 8)
        assertTrue(palette.all { it != Color.Unspecified })
    }
}
