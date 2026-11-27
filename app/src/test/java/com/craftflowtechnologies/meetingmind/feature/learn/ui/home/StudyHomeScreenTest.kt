package com.craftflowtechnologies.meetingmind.feature.learn.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.skySummary
import com.craftflowtechnologies.meetingmind.feature.learn.SkyState
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2200dp-xxhdpi", sdk = [34])
class StudyHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private class Taps {
        var startRound = 0
        var practise = 0
        var later = 0
        var record = 0
        var import = 0
        var allIdeas = 0
    }

    /** Companion forced still and on-canvas, so the composition can go idle (TESTING gotcha 2). */
    private fun show(state: StudyHomeState, taps: Taps = Taps(), dark: Boolean = false) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalCompanionReducedMotion provides true,
                    LocalCompanionForceCanvas provides true,
                    LocalCompanionSettingsSource provides FakeCompanionSettings()
                ) {
                    StudyHomeScreen(
                        state = state,
                        onStartRound = { taps.startRound++ },
                        onPractiseAnyway = { taps.practise++ },
                        onLater = { taps.later++ },
                        onOpenLesson = {},
                        onOpenCourse = {},
                        onOpenAllIdeas = { taps.allIdeas++ },
                        onSearch = {},
                        onRecordLecture = { taps.record++ },
                        onImportLecture = { taps.import++ }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `a due round shows the round card and Start round fires onStartRound`() {
        val taps = Taps()
        show(StudyHomeSamples.due, taps)
        compose.onNodeWithText("Tonight's round").assertExists()
        compose.onNodeWithText("8 cards · about 5 min").assertExists()
        compose.onNodeWithText("From Enzyme kinetics, Supply shocks and Acids and bases").assertExists()
        compose.onNodeWithText("Start round").performClick()
        compose.onNodeWithText("Later").performClick()
        assertEquals(1, taps.startRound)
        assertEquals(1, taps.later)
        assertEquals(0, taps.practise)
    }

    @Test fun `caught up says so and offers Practise anyway instead of a round`() {
        val taps = Taps()
        show(StudyHomeSamples.caughtUp, taps)
        compose.onNodeWithText("All caught up. Nice work.").assertExists()
        compose.onNodeWithText("Start round").assertDoesNotExist()
        compose.onNodeWithText("Practise anyway").performClick()
        assertEquals(1, taps.practise)
        assertEquals(0, taps.startRound)
    }

    @Test fun `first use offers Record a lecture and Import a recording`() {
        val taps = Taps()
        show(StudyHomeSamples.firstUse, taps)
        compose.onNodeWithText("Study from your lectures").assertExists()
        compose.onNodeWithText("Start round").assertDoesNotExist()
        compose.onNodeWithText("Your sky").assertDoesNotExist()
        compose.onNodeWithText("Record a lecture").performClick()
        compose.onNodeWithText("Import a recording").performClick()
        assertEquals(1, taps.record)
        assertEquals(1, taps.import)
    }

    @Test fun `the sky is summarised for TalkBack by counts and opens all ideas`() {
        val taps = Taps()
        show(StudyHomeSamples.due, taps)
        compose.onNodeWithContentDescription("5 shining, 4 growing, 3 new, 2 worth a look").assertExists()
        compose.onNodeWithText("All 31 ideas").performClick()
        assertEquals(1, taps.allIdeas)
    }

    @Test fun `the sky summary reads plainly when empty`() {
        assertEquals("No ideas in the sky yet", skySummary(SkyState.Empty))
        assertEquals("5 shining, 4 growing, 3 new, 2 worth a look", skySummary(StudyHomeSamples.sky))
    }

    @Test fun `the sources line joins titles in plain English`() {
        assertEquals(null, sourcesLine(emptyList()))
        assertEquals("From A", sourcesLine(listOf("A")))
        assertEquals("From A and B", sourcesLine(listOf("A", "B")))
        assertEquals("From A, B and C", sourcesLine(listOf("A", "B", "C")))
    }

    @Test fun `the week reads as days studied with no streak wording`() {
        show(StudyHomeSamples.due)
        compose.onNodeWithText("You studied 4 days this week").assertExists()
        compose.onNodeWithText("No streaks to lose. A quiet day is fine.").assertExists()
        compose.onNodeWithContentDescription("Wednesday, no study, today", substring = false).assertExists()
    }

    @Test fun `the loading state shows no round and no welcome`() {
        show(StudyHomeState(loading = true))
        compose.onNodeWithText("Start round").assertDoesNotExist()
        compose.onNodeWithText("Study from your lectures").assertDoesNotExist()
    }

    @Test fun `paper home`() = shot("paper", StudyHomeSamples.due, dark = false)
    @Test fun `midnight home`() = shot("midnight", StudyHomeSamples.due, dark = true)
    @Test fun `caught up paper`() = shot("caught_up_paper", StudyHomeSamples.caughtUp, dark = false)
    @Test fun `first use midnight`() = shot("first_use_midnight", StudyHomeSamples.firstUse, dark = true)

    private fun shot(name: String, state: StudyHomeState, dark: Boolean) {
        show(state, dark = dark)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/study_home_$name.png")
    }
}
