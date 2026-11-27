package com.craftflowtechnologies.meetingmind.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.craftflowtechnologies.meetingmind.feature.learn.LessonKitStatus
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes

import com.craftflowtechnologies.meetingmind.feature.learn.ui.course.CourseSamples
import com.craftflowtechnologies.meetingmind.feature.learn.ui.course.CourseScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.NewSessionSheet
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson.LessonScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson.StudySamples
import com.craftflowtechnologies.meetingmind.feature.learn.ui.listen.ListenScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Quality pass, second half: the lesson in each status, the concept sheet, listen, course and the
 * new-session sheet, at 360 dp and font scale 1.3. Writes build/outputs/roborazzi/scale_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi", sdk = [34])
class LearnStatusShotsTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun AtFontScale13(content: @Composable () -> Unit) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) { content() }
    }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            LearnHarness(dark = dark) { AtFontScale13 { content() } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/scale_${name}_${if (dark) "midnight" else "paper"}.png")
    }

    private fun lessonScreen(status: LessonKitStatus, kit: Boolean) = @Composable {
        LessonScreen(
            state = StudySamples.lesson(status = status, kitOverride = if (kit) StudySamples.kit else null),
            explain = ExplainState(),
            openConceptId = null,
            onBack = {}, onPlayFrom = {}, onTogglePlay = {}, onHearSummary = {}, onOpenConcept = {},
            onCloseConcept = {}, onExplain = { _, _ -> }, onQuizConcept = {}, onReportConcept = {},
            onListen = {}, onPractice = {}, onAsk = {}, onGenerate = {}, onFixAudio = {}
        )
    }

    @Test fun `lesson ready paper`() = shot("lesson_ready", false) { lessonScreen(LessonKitStatus.Ready, kit = true)() }
    @Test fun `lesson generating paper`() = shot("lesson_generating", false) {
        lessonScreen(LessonKitStatus.Generating("Finding what your lecturer stressed"), kit = false)()
    }
    @Test fun `lesson needs AI paper`() = shot("lesson_needs_ai", false) {
        lessonScreen(LessonKitStatus.NeedsAi("Turn on AI to make this lesson."), kit = false)()
    }
    @Test fun `lesson failed paper`() = shot("lesson_failed", false) {
        lessonScreen(LessonKitStatus.Failed("The lesson didn't finish. Try again."), kit = false)()
    }
    @Test fun `lesson no transcript midnight`() = shot("lesson_no_transcript", true) {
        lessonScreen(LessonKitStatus.NoTranscript, kit = false)()
    }

    @Test fun `concept sheet midnight`() = shot("lesson_concept", true) {
        LessonScreen(
            state = StudySamples.lesson(),
            explain = StudySamples.drawnExplanation,
            openConceptId = "km",
            onBack = {}, onPlayFrom = {}, onTogglePlay = {}, onHearSummary = {}, onOpenConcept = {},
            onCloseConcept = {}, onExplain = { _, _ -> }, onQuizConcept = {}, onReportConcept = {},
            onListen = {}, onPractice = {}, onAsk = {}, onGenerate = {}, onFixAudio = {}
        )
    }

    @Test fun `listen midnight`() = shot("listen", true) {
        ListenScreen(
            state = StudySamples.listen(),
            onClose = {}, onTogglePlay = {}, onSeek = {}, onSkip = {}, onNextStressed = {},
            onSpeed = {}, onVoice = {}, onToggleStopAtSectionEnd = {}, onToggleQuizAfter = {}
        )
    }

    @Test fun `course paper`() = shot("course", false) {
        CourseScreen(
            state = CourseSamples.biochemistry,
            onBack = {}, onPractise = {}, onOpenExamPlan = {}, onOpenLecture = {}, onAddLecture = {}
        )
    }

    @Test fun `course midnight`() = shot("course", true) {
        CourseScreen(
            state = CourseSamples.biochemistry,
            onBack = {}, onPractise = {}, onOpenExamPlan = {}, onOpenLecture = {}, onAddLecture = {}
        )
    }

    @Test fun `new session sheet paper`() = shot("new_session", false) {
        NewSessionSheet(
            courses = listOf(
                LearningFakes.course("bio", "Biochemistry", code = "BIO 201"),
                LearningFakes.course("econ", "Economics", code = "ECON 101")
            ),
            presetCourseId = "bio",
            onDismiss = {},
            onStart = { _, _ -> }
        )
    }
}
