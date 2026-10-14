package com.craftflowtechnologies.meetingmind.feature.processing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Z-10: Processing (Thinking + caption) and Failed (Worried + Retry), Paper and Graphite. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ProcessingCompanionScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val compose = createComposeRule()
    private val rows = Workflows.processingStageRows(RecordingType.MEETING, null)

    private fun capture(state: ProcessingUiState, space: NotebookSpace?, name: String, onRetry: () -> Unit = {}) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalCompanionSettingsSource provides FakeCompanionSettings(), LocalCompanionReducedMotion provides true, LocalCompanionForceCanvas provides true) {
                    ProcessingRunning(state, ProcessingProfile.INTERNET, rows, {}, {}, onRetry, {}, {}, space = space)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/processing/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun thinking() {
        capture(
            ProcessingUiState(recordingTitle = "Team sync", progressPercent = 40, stage = ProcessingStage.ANALYZING),
            NotebookSpace.WORK, "thinking"
        )
        compose.onNodeWithText("Pulling out decisions and action items…").assertIsDisplayed()
    }

    @Test fun failedIsWorriedWithRetry() {
        var retried = 0
        capture(
            ProcessingUiState(recordingTitle = "Team sync", progressPercent = 100, stage = ProcessingStage.FAILED, error = "Transcribing failed."),
            null, "failed", onRetry = { retried++ }
        )
        compose.onNodeWithTag("processing_companion").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }
}
