package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.learning.plansAction
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-xxhdpi", sdk = [34])
class AllowancePanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `see plans is not shown when this build cannot sell plans, and the lesson message still is`() {
        compose.setContent {
            LearnHarness {
                AllowanceUsedUpPanel(resetsAtMs = 0L, onSeePlans = plansAction(billingConfigured = false) {})
            }
        }
        compose.onNodeWithText(ALLOWANCE_TITLE).assertIsDisplayed()
        compose.onAllNodesWithText("See plans").assertCountEquals(0)
    }

    @Test
    fun `see plans is shown and opens plans when billing is configured`() {
        var opened = 0
        compose.setContent {
            LearnHarness {
                AllowanceUsedUpPanel(resetsAtMs = 0L, onSeePlans = plansAction(billingConfigured = true) { opened++ })
            }
        }
        compose.onNodeWithText("See plans").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the reset day is named only when the service said when`() {
        assertNull(allowanceResetLine(0L, Locale.US))
        val line = allowanceResetLine(1_800_000_000_000L, Locale.US)
        assertTrue("got $line", line != null && line.startsWith("Resets on "))
    }
}
