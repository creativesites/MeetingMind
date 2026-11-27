package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lesson page screenshots against design/study/Lesson.dc.html and ConceptSheet.dc.html. Record with -Proborazzi.test.record=true. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1900dp-xxhdpi", sdk = [34])
class LessonScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun lesson(dark: Boolean, explain: ExplainState = ExplainState(), openConceptId: String? = null) {
        compose.setContent {
            LearnHarness(dark = dark) {
                LessonScreen(
                    state = StudySamples.lesson(),
                    explain = explain,
                    openConceptId = openConceptId,
                    onBack = {}, onPlayFrom = {}, onTogglePlay = {}, onHearSummary = {}, onOpenConcept = {},
                    onCloseConcept = {}, onExplain = { _, _ -> }, onQuizConcept = {}, onReportConcept = {},
                    onListen = {}, onPractice = {}, onAsk = {}, onGenerate = {}, onFixAudio = {}
                )
            }
        }
    }

    @Test
    fun `lesson ready, Paper`() {
        lesson(dark = false)
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/study/lesson_paper.png")
    }

    @Test
    fun `lesson ready, dark`() {
        lesson(dark = true)
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/study/lesson_dark.png")
    }

    @Test
    fun `lesson with the concept sheet open, Paper`() {
        lesson(dark = false, explain = StudySamples.drawnExplanation, openConceptId = "km")
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/study/lesson_concept_sheet_paper.png")
    }
}
