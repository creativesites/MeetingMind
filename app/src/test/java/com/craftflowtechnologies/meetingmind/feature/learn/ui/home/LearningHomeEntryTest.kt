package com.craftflowtechnologies.meetingmind.feature.learn.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningHomeUiState
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ported from the retired Learning space tests (LearningSpaceUiTest, LearningSpaceYouTubeTest).
 * The rules now hold on the Learning home (feature/learn/ui/home/LearningHomeScreen.kt): the first
 * run asks for the first lecture and records it, and the video link is a tagged tile that opens
 * YouTube. Practice rating and choice rules are covered by PracticeScreenTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2200dp-xxhdpi", sdk = [34])
class LearningHomeEntryTest {
    @get:Rule val compose = createComposeRule()

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

    private fun show(state: LearningHomeUiState) {
        compose.setContent {
            MeetMindTheme(darkTheme = false) {
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

    @Test fun `first run explains the home and records the first lecture`() {
        show(LearningHomeSamples.firstUse)
        compose.onNodeWithText("Study from your lectures").assertIsDisplayed()
        compose.onNodeWithTag("learning_tile_record").performClick()
        compose.onNodeWithText("Record your first lecture").performClick()
        assertEquals(listOf("record", "record"), log)
    }

    @Test fun `the video link is a tagged tile that opens YouTube`() {
        show(LearningHomeSamples.due)
        compose.onNodeWithTag("learning_tile_video").assertIsDisplayed().performClick()
        compose.onNodeWithText("From a video link").performClick()
        assertEquals(listOf("video", "video"), log)
    }
}
