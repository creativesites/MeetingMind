@file:OptIn(ExperimentalLayoutApi::class)

package com.craftflowtechnologies.meetingmind.feature.learn.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learn.exam.ExamPlan
import com.craftflowtechnologies.meetingmind.feature.learn.exam.ExamPlanner
import com.craftflowtechnologies.meetingmind.feature.learn.ui.gate.PaidPlanCard
import com.craftflowtechnologies.meetingmind.feature.notes.editor.LatexMathParser
import com.craftflowtechnologies.meetingmind.feature.notes.editor.MathExpressionView
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One course a plan can be filtered to. */
data class ExamCourseChip(val id: String, val name: String)

/** One exam (a goal) as its row shows it. */
data class ExamGoalUi(
    val id: String,
    val title: String,
    val courseName: String?,
    val examDateMs: Long,
    val dailyMinutes: Int
)

/** One idea in the plan. [leftOut] ideas are kept but not planned. */
data class ExamIdeaUi(
    val id: String,
    val name: String,
    val definition: String,
    val formula: String?,
    val lectureTitle: String?,
    val sessionId: String?,
    val weight: ExamImportance,
    val state: LearningMasteryState,
    val leftOut: Boolean
)

/** The idea list's filters, in the words the screen shows. */
enum class IdeaFilter(val label: String) {
    ALL("All"),
    NEEDS_WORK("Needs work"),
    HIGH_YIELD("High yield"),
    SOLID("Solid")
}

/** Everything the exam plan screen shows. Built by [com.craftflowtechnologies.meetingmind.feature.learn.exam.ExamPlanHost]. */
data class ExamPlanUi(
    val courses: List<ExamCourseChip>,
    val selectedCourseId: String?,
    val goals: List<ExamGoalUi>,
    /** The exam the plan is for; null when the course has none yet. */
    val selectedGoalId: String?,
    val plan: ExamPlan?,
    val readiness: Int,
    /** Counts by the Study words: Solid, Getting there, New, Worth a look. */
    val solid: Int,
    val growing: Int,
    val newCount: Int,
    val worthALook: Int,
    val ideas: List<ExamIdeaUi>,
    val ideaFilter: IdeaFilter,
    val cramMarkdown: String?,
    val cramGenerating: Boolean,
    /** False when the learner is not on a paid plan: the day-by-day plan and cram sheet show a calm card instead. */
    val planOpen: Boolean = true
)

/** The screen's actions. Each one is a call into the host, which does the work and any navigation. */
data class ExamPlanCallbacks(
    val onBack: () -> Unit,
    val onSelectCourse: (courseId: String?) -> Unit,
    val onSelectExam: (goalId: String) -> Unit,
    val onAddExam: () -> Unit,
    val onEditExam: (goalId: String) -> Unit,
    val onDeleteExam: (goalId: String) -> Unit,
    val onStartPractice: () -> Unit,
    val onPractiseIdea: (ideaId: String) -> Unit,
    val onOpenLesson: (sessionId: String) -> Unit,
    val onCycleWeight: (ideaId: String) -> Unit,
    val onToggleLeaveOut: (ideaId: String) -> Unit,
    val onIdeaFilter: (IdeaFilter) -> Unit,
    val onMakeCramSheet: () -> Unit,
    val onReadCramSheet: () -> Unit,
    val onShareCramSheet: () -> Unit,
    val onSeePlans: () -> Unit = {}
)

private const val PLAN_ROWS_SHOWN = 7

/**
 * The exam plan (design/study, Course and Lesson): one hero with the next step, the days of the plan, how well the
 * learner knows the ideas in words, the cram sheet, and the ideas with their weight. Everything is optional and calm.
 */
@Composable
fun ExamPlanScreen(ui: ExamPlanUi, actions: ExamPlanCallbacks, modifier: Modifier = Modifier) {
    val c = MM.colors
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .testTag("exam_plan_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(MM.space.l)
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Exam plan",
                leading = {
                    IconButton(onClick = actions.onBack, modifier = Modifier.size(MMSize.minTouch).testTag("exam_plan_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink)
                    }
                },
                actions = {
                    IconButton(onClick = actions.onAddExam, modifier = Modifier.size(MMSize.minTouch).testTag("exam_plan_add")) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add an exam", tint = c.ink)
                    }
                }
            )
        }

        if (ui.courses.isNotEmpty()) item(key = "courses") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                MMChip(label = "All courses", selected = ui.selectedCourseId == null, onClick = { actions.onSelectCourse(null) }, modifier = Modifier.testTag("exam_course_all"))
                ui.courses.forEach { course ->
                    MMChip(
                        label = course.name,
                        selected = ui.selectedCourseId == course.id,
                        onClick = { actions.onSelectCourse(course.id) },
                        modifier = Modifier.testTag("exam_course_${course.id}")
                    )
                }
            }
        }

        item(key = "hero") { ExamHero(ui, actions) }

        if (ui.goals.size > 1) item(key = "exams") {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                SectionHeader(title = "Your exams", count = ui.goals.size)
                ui.goals.forEach { goal -> ExamRow(goal, selected = goal.id == ui.selectedGoalId, actions) }
            }
        }

        if (ui.ideas.isNotEmpty()) item(key = "progress") { ProgressCard(ui) }

        if (!ui.planOpen) item(key = "plan_locked") {
            PaidPlanCard(
                line = "The day-by-day plan and the cram sheet are part of the paid plans. Practice today stays free.",
                onSeePlans = actions.onSeePlans
            )
        } else {
            ui.plan?.let { plan ->
                item(key = "plan") { PlanSection(plan) }
            }
            item(key = "cram") { CramSheetCard(ui, actions) }
        }

        item(key = "ideas_header") {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                SectionHeader(title = "Ideas for this exam", count = ui.ideas.size)
                if (ui.ideas.size > 2) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        IdeaFilter.entries.forEach { f ->
                            MMChip(label = f.label, selected = ui.ideaFilter == f, onClick = { actions.onIdeaFilter(f) }, modifier = Modifier.testTag("exam_filter_${f.name.lowercase()}"))
                        }
                    }
                }
            }
        }

        val visible = ui.ideas.filter { idea ->
            when (ui.ideaFilter) {
                IdeaFilter.ALL -> true
                IdeaFilter.NEEDS_WORK -> idea.state == LearningMasteryState.NEEDS_REVIEW || idea.state == LearningMasteryState.NEW
                IdeaFilter.HIGH_YIELD -> idea.weight != ExamImportance.NORMAL
                IdeaFilter.SOLID -> idea.state == LearningMasteryState.STRONG
            }
        }
        if (ui.ideas.isEmpty()) item(key = "no_ideas") {
            MMCard(contentPadding = MM.space.l) {
                Text("No ideas yet", style = MM.type.bodyStrong, color = c.ink)
                Spacer(Modifier.height(MM.space.xs))
                Text("Record a lecture or write study notes, and its ideas show up here.", style = MM.type.caption, color = c.inkSecondary)
            }
        } else if (visible.isEmpty()) item(key = "no_match") {
            Text("No ideas in this group yet.", style = MM.type.secondary, color = c.inkSecondary)
        } else {
            items(visible, key = { it.id }) { idea -> IdeaRow(idea, actions) }
        }
    }
}

@Composable
private fun ExamHero(ui: ExamPlanUi, actions: ExamPlanCallbacks) {
    val c = MM.colors
    val goal = ui.goals.firstOrNull { it.id == ui.selectedGoalId }
    HeroCard(modifier = Modifier.testTag("exam_hero")) {
        if (goal == null || ui.plan == null) {
            Text("Set an exam date", style = MM.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
            Text(
                "Pick the exam and its day. The plan fills in from your lectures.",
                style = MM.type.secondary,
                color = c.inkSecondary
            )
            PrimaryButton(text = "Set exam date", onClick = actions.onAddExam, modifier = Modifier.testTag("exam_set_date"))
        } else {
            Text(goal.title, style = MM.type.title, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            Text(
                listOfNotNull(goal.courseName, examWhen(ui.plan.daysLeft, goal.examDateMs)).joinToString(" · "),
                style = MM.type.secondary,
                color = c.inkSecondary
            )
            ui.plan.doneByMs?.let { done ->
                Text("Aim to be done by ${dayLabel(done)}. After that, rest.", style = MM.type.secondary, color = c.ink)
            }
            Text("${ui.readiness}% ready · ${goal.dailyMinutes} min a day", style = MM.type.caption, color = c.inkSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = "Start today's exam practice",
                    onClick = actions.onStartPractice,
                    modifier = Modifier.weight(1f).testTag("start_exam_practice")
                )
                SecondaryButton(text = "Change", onClick = { actions.onEditExam(goal.id) }, modifier = Modifier.testTag("exam_change"))
            }
        }
    }
}

@Composable
private fun ExamRow(goal: ExamGoalUi, selected: Boolean, actions: ExamPlanCallbacks) {
    val c = MM.colors
    MMCard(
        modifier = Modifier.clip(MM.radius.card).clickable(role = Role.RadioButton, onClick = { actions.onSelectExam(goal.id) }).testTag("exam_row_${goal.id}"),
        contentPadding = MM.space.m
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = MM.type.bodyStrong, color = if (selected) c.accent else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(goal.courseName, dayLabel(goal.examDateMs)).joinToString(" · "),
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
            IconButton(onClick = { actions.onDeleteExam(goal.id) }, modifier = Modifier.size(MMSize.minTouch).testTag("delete_goal_${goal.id}")) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove ${goal.title}", tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
            }
        }
    }
}

@Composable
private fun ProgressCard(ui: ExamPlanUi) {
    val c = MM.colors
    val total = (ui.solid + ui.growing + ui.newCount + ui.worthALook).coerceAtLeast(1).toFloat()
    MMCard(contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Text("How well you know it", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = c.inkSecondary)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(MM.radius.pill).background(c.surfaceSunk)) {
                if (ui.solid > 0) Box(Modifier.weight(ui.solid / total).fillMaxWidth().height(10.dp).background(c.accent))
                if (ui.growing > 0) Box(Modifier.weight(ui.growing / total).fillMaxWidth().height(10.dp).background(c.accentWash))
                if (ui.newCount > 0) Box(Modifier.weight(ui.newCount / total).fillMaxWidth().height(10.dp).background(c.line))
                if (ui.worthALook > 0) Box(Modifier.weight(ui.worthALook / total).fillMaxWidth().height(10.dp).background(c.warning))
            }
            Text(
                "${ui.solid} solid · ${ui.growing} getting there · ${ui.newCount} new · ${ui.worthALook} worth a look",
                style = MM.type.caption,
                color = c.inkSecondary,
                modifier = Modifier.testTag("exam_progress_words")
            )
        }
    }
}

@Composable
private fun PlanSection(plan: ExamPlan) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s), modifier = Modifier.testTag("exam_plan_days")) {
        SectionHeader(title = "Your plan")
        if (plan.days.isEmpty()) {
            Text("The exam is today or has passed. Rest well.", style = MM.type.secondary, color = c.inkSecondary)
        }
        plan.days.take(PLAN_ROWS_SHOWN).forEachIndexed { index, day ->
            MMCard(contentPadding = MM.space.m) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(92.dp)) {
                        Text(if (index == 0) "Today" else dayLabel(day.dateMs), style = MM.type.bodyStrong, color = if (index == 0) c.accent else c.ink)
                        Text("${day.minutes} min", style = MM.type.caption, color = c.inkSecondary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        if (day.isEmpty) {
                            Text("Nothing due. Practise anyway if you like.", style = MM.type.secondary, color = c.inkSecondary)
                        } else {
                            if (day.reviewNames.isNotEmpty()) {
                                Text("Review: ${day.reviewNames.take(3).joinToString(", ")}", style = MM.type.secondary, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (day.newNames.isNotEmpty()) {
                                Text("Learn: ${day.newNames.take(3).joinToString(", ")}", style = MM.type.secondary, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        if (plan.days.size > PLAN_ROWS_SHOWN) {
            Text("More days follow as you go.", style = MM.type.caption, color = c.inkMuted)
        }
    }
}

@Composable
private fun CramSheetCard(ui: ExamPlanUi, actions: ExamPlanCallbacks) {
    val c = MM.colors
    val markdown = ui.cramMarkdown
    MMCard(contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Text("Cram sheet", style = MM.type.bodyStrong, color = c.ink)
            Text(
                if (markdown.isNullOrBlank()) "One page of the key ideas, definitions and formulas, to read the night before."
                else "Ready. Read it, copy it, or share it to a Circle.",
                style = MM.type.caption,
                color = c.inkSecondary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                if (markdown.isNullOrBlank()) {
                    PrimaryButton(
                        text = if (ui.cramGenerating) "Making it…" else "Make cram sheet",
                        onClick = actions.onMakeCramSheet,
                        enabled = !ui.cramGenerating,
                        modifier = Modifier.testTag("generate_cram_sheet_button")
                    )
                } else {
                    PrimaryButton(text = "Read it", onClick = actions.onReadCramSheet, modifier = Modifier.testTag("cram_read"))
                    SecondaryButton(text = "Make it again", onClick = actions.onMakeCramSheet, modifier = Modifier.testTag("cram_regenerate"))
                    TextAction(text = "Share to a Circle", onClick = actions.onShareCramSheet, modifier = Modifier.testTag("cram_share_circle"))
                }
            }
        }
    }
}

@Composable
private fun IdeaRow(idea: ExamIdeaUi, actions: ExamPlanCallbacks) {
    val c = MM.colors
    val weightWord = when (idea.weight) {
        ExamImportance.NORMAL -> "Standard"
        ExamImportance.HIGH_YIELD -> "High yield"
        ExamImportance.LECTURER_FLAGGED -> "Lecturer flagged"
    }
    MMCard(contentPadding = MM.space.m, modifier = Modifier.testTag("exam_idea_${idea.id}")) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(idea.name, style = MM.type.bodyStrong, color = if (idea.leftOut) c.inkMuted else c.ink)
                    idea.lectureTitle?.let { Text(it, style = MM.type.caption, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Text(ExamPlanner.stateWord(idea.state), style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = c.inkSecondary)
            }
            if (idea.definition.isNotBlank() && !idea.leftOut) {
                Text(idea.definition, style = MM.type.caption, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            idea.formula?.takeIf { it.isNotBlank() && !idea.leftOut }?.let { formula ->
                val node = remember(formula) { runCatching { LatexMathParser.parse(formula) }.getOrNull() }
                if (node != null) {
                    MathExpressionView(node = node, fontSize = MM.type.body.fontSize, inkColor = c.ink)
                } else {
                    Text(formula, style = MM.type.caption, color = c.ink)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                MMChip(label = weightWord, selected = idea.weight != ExamImportance.NORMAL, onClick = { actions.onCycleWeight(idea.id) }, modifier = Modifier.testTag("idea_weight_${idea.id}"))
                if (!idea.leftOut) TextAction("Practise", onClick = { actions.onPractiseIdea(idea.id) }, modifier = Modifier.testTag("idea_practise_${idea.id}"))
                idea.sessionId?.let { sid ->
                    TextAction("Open lecture", onClick = { actions.onOpenLesson(sid) }, modifier = Modifier.testTag("idea_open_${idea.id}"))
                }
                TextAction(
                    if (idea.leftOut) "Put back" else "Leave out",
                    onClick = { actions.onToggleLeaveOut(idea.id) },
                    modifier = Modifier.testTag("idea_leave_out_${idea.id}")
                )
            }
        }
    }
}

private fun dayLabel(ms: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

private fun examWhen(daysLeft: Int, examDateMs: Long): String = when {
    examDateMs < System.currentTimeMillis() - ExamPlanner.DAY_MS -> "passed ${dayLabel(examDateMs)}"
    daysLeft <= 0 -> "today"
    daysLeft == 1 -> "tomorrow"
    else -> "in $daysLeft days"
}
