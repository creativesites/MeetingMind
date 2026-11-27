package com.craftflowtechnologies.meetingmind.feature.learn.ui.explain

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.feature.learn.explain.ExplainPhase
import com.craftflowtechnologies.meetingmind.feature.learn.explain.ExplainUiState
import com.craftflowtechnologies.meetingmind.feature.learn.explain.IdeaCoverage
import com.craftflowtechnologies.meetingmind.feature.learn.explain.IdeaRow
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Explain it aloud screenshots against design/study/ExplainAloud.dc.html. Record with -Proborazzi.test.record=true. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1900dp-xxhdpi", sdk = [34])
class ExplainAloudScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val ideas = listOf(
        IdeaRow("Binds the active site", "Binds the active site", IdeaCoverage.COVERED, null, null),
        IdeaRow("More substrate wins it back", "More substrate wins it back", IdeaCoverage.PARTLY, "Binding is the first step.", 740_000L),
        IdeaRow("Vmax stays the same", "Vmax stays the same", IdeaCoverage.NOT_YET, "More substrate wins it back, but Vmax stays the same.", 754_000L)
    )

    private fun show(state: ExplainUiState, dark: Boolean) {
        compose.setContent {
            LearnHarness(dark = dark) { ExplainAloudScreen(state = state) }
        }
    }

    private fun base(phase: ExplainPhase) = ExplainUiState(
        phase = phase,
        subjectLine = "BIO 201 · Inhibition",
        conceptName = "Competitive inhibition",
        prompt = "Explain Competitive inhibition as if to a friend. Aim for about a minute.",
        ideas = ideas
    )

    @Test
    fun `recording, Paper`() {
        show(
            base(ExplainPhase.RECORDING).copy(
                elapsedMs = 38_000L,
                transcript = "It binds the active site so the substrate",
                levels = List(ExplainUiState.LEVEL_BARS) { i -> 0.2f + 0.6f * ((i * 7) % 10) / 10f }
            ),
            dark = false
        )
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/study/explain_recording_paper.png")
    }

    @Test
    fun `recording, dark`() {
        show(
            base(ExplainPhase.RECORDING).copy(
                elapsedMs = 38_000L,
                levels = List(ExplainUiState.LEVEL_BARS) { i -> 0.2f + 0.6f * ((i * 7) % 10) / 10f }
            ),
            dark = true
        )
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/study/explain_recording_dark.png")
    }

    @Test
    fun `result, Paper`() {
        show(
            base(ExplainPhase.RESULT).copy(
                summary = "You have the mechanism. The point about Vmax needs the lecturer's example.",
                nextPrompt = "What happens to Vmax when there is more substrate?"
            ),
            dark = false
        )
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/study/explain_result_paper.png")
    }

    @Test
    fun `result, dark`() {
        show(
            base(ExplainPhase.RESULT).copy(
                summary = "You have the mechanism. The point about Vmax needs the lecturer's example.",
                nextPrompt = "What happens to Vmax when there is more substrate?"
            ),
            dark = true
        )
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/study/explain_result_dark.png")
    }
}
