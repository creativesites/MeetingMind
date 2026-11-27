package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeState
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.craftflowtechnologies.meetingmind.ui.theme.StudyFixedColors
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A stand-in for the Study sky (test only): a few stars on the fixed night colour, as the hero's sky slot sees it. */
@Composable
private fun StudySkySample(ignite: Set<String>) {
    Canvas(Modifier.fillMaxSize()) {
        val stars = listOf(0.12f to 0.2f, 0.7f to 0.14f, 0.85f to 0.5f, 0.3f to 0.72f, 0.6f to 0.8f, 0.1f to 0.55f)
        stars.forEachIndexed { i, (x, y) ->
            val lit = ignite.isNotEmpty() && i < 2
            drawCircle(
                color = if (lit) StudyFixedColors.StarCore else StudyFixedColors.Star,
                radius = if (lit) 5.dp.toPx() else 2.dp.toPx(),
                center = Offset(size.width * x, size.height * y)
            )
        }
    }
}

/** Records the practice screens from the approved mockups in Paper and dark. Run with -Proborazzi.test.record=true. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class PracticeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, state: PracticeState, dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(
                LocalCompanionSettingsSource provides FakeCompanionSettings(),
                LocalCompanionReducedMotion provides true,
                LocalCompanionForceCanvas provides true
            ) {
                MeetMindTheme(darkTheme = dark) {
                    PracticeScreen(
                        state = state,
                        onAnswerChoice = {},
                        onNotSure = {},
                        onReveal = {},
                        onRate = {},
                        onContinue = {},
                        onClose = {},
                        onPlaySource = {},
                        onDone = {},
                        onOpenNextStep = {},
                        onMakeCard = {},
                        sky = { ignite -> StudySkySample(ignite) }
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/practice_${name}_${if (dark) "dark" else "paper"}.png")
    }

    @Test fun `question paper`() = shot("question", PracticeSamples.question, dark = false)
    @Test fun `question dark`() = shot("question", PracticeSamples.question, dark = true)
    @Test fun `wrong feedback paper`() = shot("wrong", PracticeSamples.wrong, dark = false)
    @Test fun `wrong feedback dark`() = shot("wrong", PracticeSamples.wrong, dark = true)
    @Test fun `recall revealed paper`() = shot("recall_revealed", PracticeSamples.recallRevealed, dark = false)
    @Test fun `recall revealed dark`() = shot("recall_revealed", PracticeSamples.recallRevealed, dark = true)
    @Test fun `round done paper`() = shot("round_done", PracticeSamples.done, dark = false)
    @Test fun `round done dark`() = shot("round_done", PracticeSamples.done, dark = true)
}
