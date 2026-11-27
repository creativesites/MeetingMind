package com.craftflowtechnologies.meetingmind.feature.learn.ui.exam

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.feature.learn.exam.ExamPlan
import com.craftflowtechnologies.meetingmind.feature.learn.exam.ExamPlanner
import com.craftflowtechnologies.meetingmind.feature.learn.exam.PlanIdea
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The exam plan: setting a date, the plan, starting today's practice, and sharing the cram sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class ExamPlanScreenTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_795_000_000_000L
    private val calls = mutableListOf<String>()

    private val ideas = listOf(
        ExamIdeaUi("i1", "Michaelis–Menten", "Reaction speed levels off at Vmax.", null, "Enzyme kinetics", "s1", ExamImportance.HIGH_YIELD, LearningMasteryState.NEEDS_REVIEW, leftOut = false),
        ExamIdeaUi("i2", "Competitive inhibition", "Binds the active site.", null, "Inhibition", "s2", ExamImportance.NORMAL, LearningMasteryState.NEW, leftOut = false),
        ExamIdeaUi("i3", "Km", "Half-speed concentration.", null, "Enzyme kinetics", "s1", ExamImportance.LECTURER_FLAGGED, LearningMasteryState.STRONG, leftOut = false)
    )

    private fun ui(
        withGoal: Boolean = true,
        cram: String? = null,
        ideasList: List<ExamIdeaUi> = ideas
    ): ExamPlanUi {
        val planned = ideasList.map { PlanIdea(it.name, it.state) }
        val goalMs = now + 12 * ExamPlanner.DAY_MS
        return ExamPlanUi(
            courses = listOf(ExamCourseChip("c1", "Biochemistry")),
            selectedCourseId = "c1",
            goals = if (withGoal) listOf(ExamGoalUi("g1", "Midterm 1", "Biochemistry", goalMs, 30)) else emptyList(),
            selectedGoalId = if (withGoal) "g1" else null,
            plan = if (withGoal) ExamPlanner.build(now, goalMs, 30, planned) else null,
            readiness = ExamPlanner.readinessPercent(planned),
            solid = 1, growing = 0, newCount = 1, worthALook = 1,
            ideas = ideasList,
            ideaFilter = IdeaFilter.ALL,
            cramMarkdown = cram,
            cramGenerating = false
        )
    }

    private fun callbacks() = ExamPlanCallbacks(
        onBack = { calls += "back" },
        onSelectCourse = { calls += "course:$it" },
        onSelectExam = { calls += "exam:$it" },
        onAddExam = { calls += "add" },
        onEditExam = { calls += "edit:$it" },
        onDeleteExam = { calls += "delete:$it" },
        onStartPractice = { calls += "practice" },
        onPractiseIdea = { calls += "practise:$it" },
        onOpenLesson = { calls += "lesson:$it" },
        onCycleWeight = { calls += "weight:$it" },
        onToggleLeaveOut = { calls += "leave:$it" },
        onIdeaFilter = { calls += "filter:$it" },
        onMakeCramSheet = { calls += "make" },
        onReadCramSheet = { calls += "read" },
        onShareCramSheet = { calls += "share" }
    )

    /** Lazy items below the fold are not composed until the list scrolls to them. */
    private fun scrollTo(tag: String) = compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag)).let { compose.onNodeWithTag(tag) }

    private fun show(state: ExamPlanUi, dark: Boolean = false) {
        compose.setContent {
            LearnHarness(dark = dark) { ExamPlanScreen(ui = state, actions = callbacks()) }
        }
    }

    @Test
    fun `with no exam, setting the date is the one action`() {
        show(ui(withGoal = false))

        compose.onNodeWithText("Set an exam date").assertIsDisplayed()
        compose.onNodeWithTag("exam_set_date").performClick()

        assertEquals(listOf("add"), calls)
    }

    @Test
    fun `the plan renders its days, with due reviews first`() {
        show(ui())

        compose.onNodeWithTag("exam_plan_days").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Review: Michaelis–Menten", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Learn: Competitive inhibition", substring = true).assertIsDisplayed()
    }

    @Test
    fun `without a paid plan the plan and cram sheet show a calm card, and see plans opens the paywall`() {
        var seePlans = 0
        compose.setContent {
            LearnHarness {
                ExamPlanScreen(
                    ui = ui().copy(planOpen = false),
                    actions = callbacks().copy(onSeePlans = { seePlans++ })
                )
            }
        }

        compose.onNodeWithTag("paid_plan_card").assertIsDisplayed()
        compose.onNodeWithTag("see_plans").performClick()
        compose.onAllNodesWithTag("generate_cram_sheet_button").assertCountEquals(0)
        compose.onNodeWithTag("exam_hero").assertIsDisplayed()
        compose.onNodeWithTag("start_exam_practice").assertIsDisplayed()

        assertEquals(1, seePlans)
    }

    @Test
    fun `progress is in words, not only colour`() {
        show(ui())

        compose.onNodeWithTag("exam_progress_words")
            .assertIsDisplayed()
            .assertTextContains("1 solid", substring = true)
    }

    @Test
    fun `start today's exam practice fires`() {
        show(ui())

        compose.onNodeWithTag("start_exam_practice").performClick()

        assertEquals(listOf("practice"), calls)
    }

    @Test
    fun `making the cram sheet fires`() {
        show(ui())
        scrollTo("generate_cram_sheet_button").performClick()
        assertEquals(listOf("make"), calls)
    }

    @Test
    fun `sharing the cram sheet to a Circle fires`() {
        show(ui(cram = "# Cram\n- Km: half speed"))
        scrollTo("cram_share_circle").performClick()
        assertEquals(listOf("share"), calls)
    }

    @Test
    fun `leaving an idea out and practising it fire with its id`() {
        show(ui())

        scrollTo("idea_leave_out_i2").performClick()
        scrollTo("idea_practise_i1").performClick()

        assertEquals(listOf("leave:i2", "practise:i1"), calls)
    }

    @Test
    fun `the editor saves a title and a day two weeks away`() {
        var saved: ExamDraft? = null
        compose.setContent {
            LearnHarness {
                ExamEditorSheet(
                    initial = ExamDraft(),
                    courses = listOf(ExamCourseChip("c1", "Biochemistry")),
                    onSave = { saved = it },
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithTag("goal_title_input").performTextInput("Midterm 2")
        compose.onNodeWithTag("exam_in_14").performClick()
        compose.onNodeWithTag("save_goal_confirm").performClick()

        val draft = saved ?: error("the editor did not save")
        assertEquals("Midterm 2", draft.title)
        assertEquals(true, draft.examDateMs - System.currentTimeMillis() > 13 * ExamPlanner.DAY_MS)
    }

    @Test
    fun `the exam plan, in light`() {
        show(ui())
        compose.onNodeWithTag("exam_hero").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/study/exam_plan_paper.png")
    }

    @Test
    fun `the exam plan, in dark`() {
        show(ui(), dark = true)
        compose.onNodeWithTag("exam_hero").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/study/exam_plan_midnight.png")
    }
}
