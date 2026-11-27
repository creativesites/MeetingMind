package com.craftflowtechnologies.meetingmind.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.LearningHomeCallbacks
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.LearningHomeSamples
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.LearningHomeScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.practice.PracticeSamples
import com.craftflowtechnologies.meetingmind.feature.learn.ui.practice.PracticeScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.practice.RoundDoneScreen
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Quality pass shots at the phone's hard case: 360 dp wide, font scale 1.3, in Paper and Midnight.
 * Run with -Proborazzi.test.record=true and look at build/outputs/roborazzi/scale_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi", sdk = [34])
class LearnScaleShotsTest {
    @get:Rule val compose = createComposeRule()

    /** Font scale 1.3 on top of the device density; the content stays 360 dp wide. */
    @Composable
    private fun AtFontScale13(content: @Composable () -> Unit) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) { content() }
    }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalCompanionReducedMotion provides true,
                LocalCompanionForceCanvas provides true,
                LocalCompanionSettingsSource provides FakeCompanionSettings()
            ) {
                MeetMindTheme(darkTheme = dark) { AtFontScale13 { content() } }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/scale_${name}_${if (dark) "midnight" else "paper"}.png")
    }

    @Test fun `home due paper`() = shot("home_due", false) {
        LearningHomeScreen(state = LearningHomeSamples.due, dayLine = "Wednesday evening", cb = homeCallbacks())
    }

    @Test fun `home due midnight`() = shot("home_due", true) {
        LearningHomeScreen(state = LearningHomeSamples.due, dayLine = "Wednesday evening", cb = homeCallbacks())
    }

    @Test fun `home first use paper`() = shot("home_first_use", false) {
        LearningHomeScreen(state = LearningHomeSamples.firstUse, dayLine = "Wednesday evening", cb = homeCallbacks())
    }

    @Test fun `practice question paper`() = shot("practice_question", false) { practice(PracticeSamples.question) }
    @Test fun `practice question midnight`() = shot("practice_question", true) { practice(PracticeSamples.question) }
    @Test fun `practice recall revealed midnight`() = shot("practice_recall", true) { practice(PracticeSamples.recallRevealed) }

    @Test fun `round done paper`() = shot("round_done", false) {
        RoundDoneScreen(result = PracticeSamples.roundResult, onDone = {}, onOpenNextStep = {}, onMakeCard = {})
    }

    @Test fun `round done midnight`() = shot("round_done", true) {
        RoundDoneScreen(result = PracticeSamples.roundResult, onDone = {}, onOpenNextStep = {}, onMakeCard = {})
    }

    @Composable
    private fun practice(state: com.craftflowtechnologies.meetingmind.feature.learn.PracticeState) {
        PracticeScreen(
            state = state, onAnswerChoice = {}, onNotSure = {}, onReveal = {}, onRate = {}, onContinue = {},
            onClose = {}, onPlaySource = {}, onDone = {}, onOpenNextStep = {}, onMakeCard = {}
        )
    }

    private fun homeCallbacks() = LearningHomeCallbacks(
        onAsk = {}, onStartRound = {}, onPractiseAnyway = {}, onOpenLesson = {}, onOpenNote = {},
        onOpenCourse = {}, onAddCourse = {}, onRecordLecture = {}, onOpenYouTube = {}, onImportFile = {},
        onWriteStudyNote = {}, onOpenAllIdeas = {}, onOpenSettings = {}
    )
}
