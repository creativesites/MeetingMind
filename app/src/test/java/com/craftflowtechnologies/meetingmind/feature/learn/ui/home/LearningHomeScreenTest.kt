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
import com.craftflowtechnologies.meetingmind.feature.learn.SkyState
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningHomeUiState
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
class LearningHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    /** Every callback appends its name, so a test can read what was tapped, in order. */
    private val log = mutableListOf<String>()

    private fun callbacks() = LearningHomeCallbacks(
        onAsk = { log += "ask" },
        onStartRound = { log += "round" },
        onPractiseAnyway = { log += "practise" },
        onOpenLesson = { log += "lesson:$it" },
        onOpenNote = { log += "note:$it" },
        onOpenCourse = { log += "course:$it" },
        onAddCourse = { log += "add course" },
        onRecordLecture = { log += "record" },
        onOpenYouTube = { log += "video" },
        onImportFile = { log += "import" },
        onWriteStudyNote = { log += "write" },
        onOpenAllIdeas = { log += "ideas" },
        onOpenSettings = { log += "settings" }
    )

    /** Companion forced still and on-canvas, so the composition can go idle (TESTING gotcha 2). */
    private fun show(state: LearningHomeUiState, dark: Boolean = false) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalCompanionReducedMotion provides true,
                    LocalCompanionForceCanvas provides true,
                    LocalCompanionSettingsSource provides FakeCompanionSettings()
                ) {
                    LearningHomeScreen(state = state, dayLine = "Wednesday evening", cb = callbacks())
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `a due home shows the round, the courses and recent lectures`() {
        show(LearningHomeSamples.due)
        compose.onNodeWithText("Tonight's round").assertExists()
        compose.onNodeWithText("8 cards · about 5 min").assertExists()
        compose.onNodeWithText("Done by 9:20 PM if you start now").assertExists()
        compose.onNodeWithText("Biochemistry").assertExists()
        compose.onNodeWithText("4 due · exam in 12 days").assertExists()
        compose.onNodeWithText("All caught up").assertExists()
        compose.onNodeWithText("Supply shocks").assertExists()
        compose.onNodeWithText("Making lesson…").assertExists()
        compose.onNodeWithText("You studied 4 days this week").assertExists()
    }

    @Test fun `the sky says its counts in words`() {
        show(LearningHomeSamples.due)
        compose.onNodeWithText("Your sky").assertExists()
        compose.onNodeWithText("5 shining · 4 growing · 2 worth a look").assertExists()
        compose.onNodeWithContentDescription("5 shining, 4 growing, 3 new, 2 worth a look").assertExists()
    }

    @Test fun `Start round fires the round, and Later folds the card away`() {
        show(LearningHomeSamples.due)
        compose.onNodeWithText("Start round").performClick()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText("Your round is still there when you want it.").assertExists()
        assertEquals(listOf("round"), log)
    }

    @Test fun `each start-something-new tile fires its own callback`() {
        show(LearningHomeSamples.due)
        compose.onNodeWithText("Record a lecture").performClick()
        compose.onNodeWithText("From a video link").performClick()
        compose.onNodeWithText("Import a file").performClick()
        compose.onNodeWithText("Write a study note").performClick()
        assertEquals(listOf("record", "video", "import", "write"), log)
    }

    @Test fun `tapping a course, a recent lecture, the ask field and the sky link fire their callbacks`() {
        show(LearningHomeSamples.due)
        compose.onNodeWithText("Biochemistry").performClick()
        compose.onNodeWithText("Supply shocks").performClick()
        compose.onNodeWithText("Ask or find anything in your lectures").performClick()
        compose.onNodeWithContentDescription("All 31 ideas").performClick()
        compose.onNodeWithContentDescription("Account and settings").performClick()
        assertEquals(
            listOf("course:bio201", "lesson:note-econ-supply", "ask", "ideas", "settings"),
            log
        )
    }

    @Test fun `a plain study note in recent opens the note, not a lesson`() {
        show(LearningHomeSamples.due)
        compose.onNodeWithText("Study note: cell signalling").performClick()
        assertEquals(listOf("note:note-bio-cell"), log)
    }

    @Test fun `caught up says so and Practise anyway fires`() {
        show(LearningHomeSamples.caughtUp)
        compose.onNodeWithText("All caught up. Nice work.").assertExists()
        compose.onNodeWithText("Start round").assertDoesNotExist()
        compose.onNodeWithText("Practise anyway").performClick()
        assertEquals(listOf("practise"), log)
    }

    @Test fun `first use asks for the first lecture and hides the sky, the round and the lists`() {
        show(LearningHomeSamples.firstUse)
        compose.onNodeWithText("Study from your lectures").assertExists()
        compose.onNodeWithText("Record your first lecture").performClick()
        compose.onNodeWithText("Your sky").assertDoesNotExist()
        compose.onNodeWithText("Start round").assertDoesNotExist()
        compose.onNodeWithText("Biochemistry").assertDoesNotExist()
        assertEquals(listOf("record"), log)
    }

    @Test fun `the day line and the sky caption read plainly`() {
        assertEquals("Wednesday evening", dayLine("Wednesday", 19))
        assertEquals("Monday morning", dayLine("Monday", 7))
        assertEquals("Friday night", dayLine("Friday", 23))
        assertEquals("No ideas in the sky yet", skyCaption(SkyState.Empty))
    }

    @Test fun `paper home`() = shot("paper", LearningHomeSamples.due, dark = false)
    @Test fun `midnight home`() = shot("midnight", LearningHomeSamples.due, dark = true)
    @Test fun `first use paper`() = shot("first_use_paper", LearningHomeSamples.firstUse, dark = false)

    private fun shot(name: String, state: LearningHomeUiState, dark: Boolean) {
        show(state, dark = dark)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/learning_home_$name.png")
    }
}
