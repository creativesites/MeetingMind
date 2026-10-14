package com.craftflowtechnologies.meetingmind.feature.processing

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
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
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ProcessingScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val rows = Workflows.processingStageRows(RecordingType.MEETING, null)

    private fun capture(state: ProcessingUiState, name: String) {
        compose.setContent {
            MeetMindTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource provides com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings(),
                    com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion provides true,
                    com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas provides true
                ) { ProcessingRunning(state, ProcessingProfile.INTERNET, rows, {}, {}, {}, {}, {}) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `transcribing shows the ring, the live step and a one-line button`() {
        capture(
            ProcessingUiState(stepTitle = "Transcribing part 1 of 3...", recordingTitle = "AUD-20260916-WA0001", progressPercent = 25, stage = ProcessingStage.TRANSCRIBING),
            "processing_running"
        )
        compose.onNodeWithText("Continue in background").assertIsDisplayed()
        compose.onNodeWithText("With Google's AI").assertIsDisplayed()
    }

    @Test
    fun `a failure says why and offers to try again`() {
        capture(
            ProcessingUiState(recordingTitle = "AUD-20260916-WA0001", progressPercent = 100, stage = ProcessingStage.FAILED,
                error = "Google's AI couldn't transcribe this recording. Transcribing failed: your Gemini API key was rejected."),
            "processing_failed"
        )
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist() // one primary action: the companion line carries it
    }

    @Test
    fun `step names read as plain words`() {
        assertEquals("Getting ready", friendlyStep("Initializing AI Pipeline..."))
        assertEquals("Transcribing part 1 of 3", friendlyStep("Transcribing part 1 of 3..."))
        assertEquals("1:05", formatElapsed(65_000))
    }
}
