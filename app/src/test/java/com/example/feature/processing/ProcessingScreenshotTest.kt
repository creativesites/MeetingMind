package com.example.feature.processing

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import com.example.core.model.ProcessingProfile
import com.example.core.model.ProcessingStage
import com.example.core.model.RecordingType
import com.example.core.model.Workflows
import com.example.ui.theme.MeetMindTheme
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
            MeetMindTheme { ProcessingRunning(state, ProcessingProfile.INTERNET, rows, {}, {}, {}, {}, {}) }
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
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun `step names read as plain words`() {
        assertEquals("Getting ready", friendlyStep("Initializing AI Pipeline..."))
        assertEquals("Transcribing part 1 of 3", friendlyStep("Transcribing part 1 of 3..."))
        assertEquals("1:05", formatElapsed(65_000))
    }
}
