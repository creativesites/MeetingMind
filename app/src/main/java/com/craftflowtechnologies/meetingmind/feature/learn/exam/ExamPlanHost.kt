package com.craftflowtechnologies.meetingmind.feature.learn.exam

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.CramSheetSheet
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.ExamCourseChip
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.ExamDraft
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.ExamEditorSheet
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.ExamGoalUi
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.ExamIdeaUi
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.ExamPlanCallbacks
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.ExamPlanScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.ExamPlanUi
import com.craftflowtechnologies.meetingmind.feature.learn.ui.exam.IdeaFilter
import com.craftflowtechnologies.meetingmind.feature.learn.ui.gate.StudyGate
import com.craftflowtechnologies.meetingmind.feature.learn.ui.share.StudyPackShareSheet
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.util.UUID

/** Where the exam plan sends the learner. The navigation graph builds one with [learnExamNavigation]. */
data class ExamPlanNavigation(
    val onBack: () -> Unit,
    /** Practise one course (null: every idea due). Start today's exam practice goes here. */
    val onPractise: (courseId: String?) -> Unit,
    val onPractiseConcept: (sessionId: String, conceptId: String) -> Unit,
    val onOpenLesson: (sessionId: String) -> Unit,
    val onOpenNote: (noteId: String) -> Unit,
    val onCreateCircle: () -> Unit,
    /** Opens the plans (the paywall), from the calm card that stands where the paid plan would be. */
    val onSeePlans: () -> Unit = {}
)

/**
 * The exam plan, wired to [LearnViewModel]. The plan is for one exam at a time: the chosen one, or the soonest in the
 * chosen course, or the soonest of all. [goalId] or [courseId] from a route open it on that exam or course.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamPlanHost(goalId: String?, courseId: String?, nav: ExamPlanNavigation) {
    val context = LocalContext.current
    val learn: LearnViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
    val goals by learn.goals.collectAsState()
    val courses by learn.courses.collectAsState()
    val priorities by learn.examPriorities.collectAsState()
    val filterId by learn.selectedExamFilterId.collectAsState()
    val cramMarkdown by learn.cramSheetMarkdown.collectAsState()
    val cramBusy by learn.isGeneratingCramSheet.collectAsState()
    // The day-by-day plan and the cram sheet are a paid extra (DEEP_SYNTHESIS). Setting a date and practice stay free.
    val planOpen = StudyGate.open(Capability.DEEP_SYNTHESIS)

    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(goalId, courseId) {
        if (!opened) {
            opened = true
            learn.refreshExamPriorities(goalId ?: courseId)
        }
    }

    var ideaFilter by rememberSaveable { mutableStateOf(IdeaFilter.ALL) }
    var editor by remember { mutableStateOf<ExamDraft?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var showCram by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }

    // The course chosen by the filter, or the course of a chosen exam.
    val selectedCourseId = when {
        courses.any { it.notebook.id == filterId } -> filterId
        else -> goals.firstOrNull { it.id == filterId }?.notebookId
    }
    val now = System.currentTimeMillis()
    val shownGoals = goals
        .filter { selectedCourseId == null || it.notebookId == selectedCourseId }
        .sortedBy { it.examDateMs }
    val selectedGoal: LearningGoal? = goals.firstOrNull { it.id == filterId }
        ?: shownGoals.firstOrNull { it.examDateMs >= now - ExamPlanner.DAY_MS }
        ?: shownGoals.firstOrNull()

    val ideas = priorities.map { item ->
        ExamIdeaUi(
            id = item.concept.id,
            name = item.concept.name,
            definition = item.concept.definition,
            formula = item.concept.formulaLatex,
            lectureTitle = item.sessionTitle.takeIf { it.isNotBlank() },
            sessionId = item.concept.sessionId,
            weight = item.concept.examImportance,
            state = item.mastery.state,
            leftOut = item.concept.isDismissed
        )
    }
    val conceptById = remember(priorities) { priorities.associate { it.concept.id to it.concept } }
    val planned = ideas.filter { !it.leftOut }.map { PlanIdea(it.name, it.state) }

    val ui = ExamPlanUi(
        courses = courses.map { ExamCourseChip(it.notebook.id, it.notebook.name) },
        selectedCourseId = selectedCourseId,
        goals = shownGoals.map { g ->
            ExamGoalUi(
                id = g.id,
                title = g.title,
                courseName = courses.firstOrNull { it.notebook.id == g.notebookId }?.notebook?.name,
                examDateMs = g.examDateMs,
                dailyMinutes = g.dailyMinutesTarget
            )
        },
        selectedGoalId = selectedGoal?.id,
        plan = selectedGoal?.let {
            ExamPlanner.build(now, it.examDateMs, it.dailyMinutesTarget, planned)
        },
        readiness = ExamPlanner.readinessPercent(planned),
        solid = ideas.count { it.state == LearningMasteryState.STRONG },
        growing = ideas.count { it.state == LearningMasteryState.DEVELOPING || it.state == LearningMasteryState.LEARNING },
        newCount = ideas.count { it.state == LearningMasteryState.NEW },
        worthALook = ideas.count { it.state == LearningMasteryState.NEEDS_REVIEW },
        ideas = ideas,
        ideaFilter = ideaFilter,
        cramMarkdown = cramMarkdown,
        cramGenerating = cramBusy,
        planOpen = planOpen
    )

    val actions = ExamPlanCallbacks(
        onBack = nav.onBack,
        onSelectCourse = { learn.refreshExamPriorities(it) },
        onSelectExam = { learn.refreshExamPriorities(it) },
        onAddExam = { editor = ExamDraft(courseId = selectedCourseId) },
        onEditExam = { id ->
            goals.firstOrNull { it.id == id }?.let { g ->
                editor = ExamDraft(
                    id = g.id,
                    title = g.title,
                    examDateMs = g.examDateMs,
                    courseId = g.notebookId,
                    targetGrade = g.examFormat,
                    dailyMinutes = g.dailyMinutesTarget
                )
            }
        },
        onDeleteExam = { deleting = it },
        onStartPractice = { nav.onPractise(selectedCourseId) },
        onPractiseIdea = { id ->
            conceptById[id]?.let { concept -> nav.onPractiseConcept(concept.sessionId, concept.id) }
        },
        onOpenLesson = nav.onOpenLesson,
        onCycleWeight = { id -> conceptById[id]?.let { learn.toggleConceptImportance(it) } },
        onToggleLeaveOut = { id -> conceptById[id]?.let { learn.setConceptHidden(it, !it.isDismissed) } },
        onIdeaFilter = { ideaFilter = it },
        onMakeCramSheet = {
            val sessionIds = priorities.map { it.concept.sessionId }.distinct()
            learn.generateCramSheet(sessionIds, "Exam cram sheet") { showCram = true }
        },
        onReadCramSheet = { showCram = true },
        onShareCramSheet = { sharing = true },
        onSeePlans = nav.onSeePlans
    )

    Box(Modifier.fillMaxSize().testTag("exam_plan_host")) {
        ExamPlanScreen(ui = ui, actions = actions)
    }

    editor?.let { draft ->
        ExamEditorSheet(
            initial = draft,
            courses = ui.courses,
            onDismiss = { editor = null },
            onSave = { saved ->
                learn.createOrUpdateGoal(
                    id = saved.id.ifBlank { UUID.randomUUID().toString() },
                    title = saved.title,
                    examDateMs = saved.examDateMs,
                    notebookId = saved.courseId,
                    targetGrade = saved.targetGrade,
                    dailyMinutesTarget = saved.dailyMinutes
                )
                editor = null
            }
        )
    }

    deleting?.let { id ->
        val goal = goals.firstOrNull { it.id == id }
        ModalBottomSheet(
            onDismissRequest = { deleting = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MM.colors.surfaceRaised,
            shape = MM.radius.sheet,
            modifier = Modifier.testTag("exam_delete_sheet")
        ) {
            Column(Modifier.padding(horizontal = MM.space.xl).padding(bottom = MM.space.xl), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                Text("Remove ${goal?.title ?: "this exam"}?", style = MM.type.heading, color = MM.colors.ink)
                Text("Your ideas and lectures stay. Only the exam date goes.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    SecondaryButton(text = "Keep it", onClick = { deleting = null }, modifier = Modifier.testTag("cancel_delete_goal"))
                    PrimaryButton(
                        text = "Remove",
                        onClick = { learn.deleteGoal(id); deleting = null },
                        modifier = Modifier.testTag("confirm_delete_goal")
                    )
                }
            }
        }
    }

    if (showCram && cramMarkdown != null) {
        CramSheetSheet(
            markdown = cramMarkdown!!,
            onDismiss = { showCram = false },
            onShare = { showCram = false; sharing = true },
            onSaveAsNote = {
                learn.saveCramSheetAsNote("Exam cram sheet", selectedCourseId, cramMarkdown!!) { noteId ->
                    showCram = false
                    nav.onOpenNote(noteId)
                }
            }
        )
    }

    if (sharing && cramMarkdown != null) {
        StudyPackShareSheet(
            title = "Exam cram sheet",
            markdown = cramMarkdown!!,
            onDismiss = { sharing = false },
            onPublished = { circleName ->
                android.widget.Toast.makeText(context, "Shared to $circleName", android.widget.Toast.LENGTH_SHORT).show()
            },
            onNavigateCreateCircle = nav.onCreateCircle,
            onSeePlans = nav.onSeePlans
        )
    }
}
