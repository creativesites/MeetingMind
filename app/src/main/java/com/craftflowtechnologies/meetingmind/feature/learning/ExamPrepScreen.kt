package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.ExamPriorityItem
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.feature.notes.editor.LatexMathParser
import com.craftflowtechnologies.meetingmind.feature.notes.editor.MathExpressionView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.School
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

@Composable
fun ExamPrepScreen(
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onShareToCircle: (cramSheetMarkdown: String) -> Unit,
    onStartExamDrill: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val goals by viewModel.goals.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val priorities by viewModel.examPriorities.collectAsState()
    val selectedFilterId by viewModel.selectedExamFilterId.collectAsState()
    val cramSheetMarkdown by viewModel.cramSheetMarkdown.collectAsState()
    val isGeneratingCramSheet by viewModel.isGeneratingCramSheet.collectAsState()

    var showCramSheetPreview by remember { mutableStateOf(false) }

    ExamPrepContent(
        goals = goals,
        courses = courses,
        priorities = priorities,
        cramSheetMarkdown = cramSheetMarkdown,
        isGeneratingCramSheet = isGeneratingCramSheet,
        onCreateGoal = { title, examDateMs, notebookId, targetGrade, dailyMinutes ->
            viewModel.createOrUpdateGoal(
                title = title,
                examDateMs = examDateMs,
                notebookId = notebookId,
                targetGrade = targetGrade,
                dailyMinutesTarget = dailyMinutes
            )
        },
        onDeleteGoal = { viewModel.deleteGoal(it) },
        onGenerateCramSheet = {
            val sessionIds = priorities.map { it.concept.sessionId }.distinct()
            viewModel.generateCramSheet(sessionIds, "Exam Cram Sheet") {
                showCramSheetPreview = true
            }
        },
        onSaveCramSheetAsNote = { title, markdown ->
            viewModel.saveCramSheetAsNote(title, selectedFilterId, markdown) { noteId ->
                onOpenNote(noteId)
            }
        },
        onShareToCircle = { markdown ->
            onShareToCircle(markdown)
        },
        onBack = onBack,
        selectedFilterId = selectedFilterId,
        onSelectFilter = { filterId ->
            viewModel.refreshExamPriorities(filterId)
        },
        onOpenSession = { sessionId ->
            onOpenNote(sessionId)
        },
        onStartExamDrill = {
            val topSessionId = priorities.firstOrNull()?.concept?.sessionId
            viewModel.loadPracticeByFilter(
                filter = PracticeArenaFilter.ALL,
                sessionId = topSessionId,
                courseId = selectedFilterId
            )
            onStartExamDrill?.invoke()
        },
        modifier = modifier
    )

    if (showCramSheetPreview && cramSheetMarkdown != null) {
        CramSheetPreviewDialog(
            markdown = cramSheetMarkdown!!,
            onDismiss = { showCramSheetPreview = false },
            onSaveAsNote = {
                viewModel.saveCramSheetAsNote("Exam Cram Sheet", selectedFilterId, cramSheetMarkdown!!) { noteId ->
                    showCramSheetPreview = false
                    onOpenNote(noteId)
                }
            },
            onShareToCircle = {
                showCramSheetPreview = false
                onShareToCircle(cramSheetMarkdown!!)
            }
        )
    }
}

@Composable
fun ExamPrepContent(
    goals: List<LearningGoal>,
    courses: List<com.craftflowtechnologies.meetingmind.core.repository.LearningRepository.LearningCourse>,
    priorities: List<ExamPriorityItem>,
    cramSheetMarkdown: String?,
    isGeneratingCramSheet: Boolean,
    onCreateGoal: (String, Long, String?, String, Int) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onGenerateCramSheet: () -> Unit,
    onSaveCramSheetAsNote: (String, String) -> Unit,
    onShareToCircle: (String) -> Unit,
    onBack: () -> Unit,
    selectedFilterId: String? = null,
    onSelectFilter: (String?) -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onStartExamDrill: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    var showDialog by remember { mutableStateOf(false) }
    var goalToDelete by remember { mutableStateOf<LearningGoal?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .testTag("exam_prep_screen")
    ) {
        ScreenHeader(
            title = "Exam Readiness Studio",
            leading = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("exam_prep_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink)
                }
            },
            actions = {
                IconButton(onClick = { showDialog = true }, modifier = Modifier.testTag("add_goal_button")) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Goal", tint = c.ink)
                }
            }
        )

        if (courses.isNotEmpty() || goals.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MM.space.l, vertical = MM.space.xs),
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                val allSelected = selectedFilterId == null
                Box(
                    modifier = Modifier
                        .clip(MM.radius.pill)
                        .background(if (allSelected) c.accent else c.surfaceSunk)
                        .clickable { onSelectFilter(null) }
                        .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                ) {
                    Text(
                        text = "All Courses",
                        style = MM.type.caption.copy(fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium),
                        color = if (allSelected) c.onAccent else c.inkSecondary
                    )
                }
                courses.forEach { course ->
                    val isSelected = selectedFilterId == course.notebook.id
                    Box(
                        modifier = Modifier
                            .clip(MM.radius.pill)
                            .background(if (isSelected) c.accent else c.surfaceSunk)
                            .clickable { onSelectFilter(course.notebook.id) }
                            .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                    ) {
                        Text(
                            text = course.notebook.name,
                            style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                            color = if (isSelected) c.onAccent else c.inkSecondary
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            if (priorities.isNotEmpty()) {
                item(key = "readiness_dashboard") {
                    ExamReadinessDashboardCard(
                        priorities = priorities,
                        onStartExamDrill = onStartExamDrill
                    )
                }
            }

            // Exam Goals section (renders all goals)
            item(key = "goals_section") {
                if (goals.isEmpty()) {
                    EmptyGoalsCard(onAddGoal = { showDialog = true })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Exam Goals (${goals.size})",
                                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                color = c.inkSecondary
                            )
                            Text(
                                text = "+ Add Exam",
                                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                color = c.accent,
                                modifier = Modifier.clickable { showDialog = true }
                            )
                        }
                        goals.forEach { goal ->
                            val daysRemaining = ((goal.examDateMs - System.currentTimeMillis()).coerceAtLeast(0L) / (24 * 3600 * 1000L)).toInt()
                            ExamGoalCard(
                                goal = goal,
                                daysRemaining = daysRemaining,
                                isSelected = selectedFilterId == goal.id,
                                onSelect = {
                                    onSelectFilter(if (selectedFilterId == goal.id) null else goal.id)
                                },
                                onDelete = { goalToDelete = goal }
                            )
                        }
                    }
                }
            }

            // Cram Sheet Generation Action
            item(key = "cram_sheet_action") {
                CramSheetActionCard(
                    isGenerating = isGeneratingCramSheet,
                    onGenerate = onGenerateCramSheet
                )
            }

            // High-Yield Exam Priority Matrix
            item(key = "priority_header") {
                SectionHeader(
                    title = "High-Yield Priority Matrix",
                    count = priorities.size
                )
            }

            if (priorities.isEmpty()) {
                item(key = "empty_priorities") {
                    MMCard(contentPadding = MM.space.m) {
                        Text(
                            text = "No concepts extracted yet. Record a lecture or create study notes to generate your exam priority matrix.",
                            style = MM.type.secondary,
                            color = c.inkSecondary
                        )
                    }
                }
            } else {
                items(priorities, key = { it.concept.id }) { item ->
                    ExamPriorityRow(
                        item = item,
                        onClick = { onOpenSession(item.concept.sessionId) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        CreateGoalDialog(
            courses = courses,
            onDismiss = { showDialog = false },
            onConfirm = { title, examDateMs, courseId, targetGrade, dailyMinutes ->
                onCreateGoal(title, examDateMs, courseId, targetGrade, dailyMinutes)
                showDialog = false
            }
        )
    }

    if (goalToDelete != null) {
        val target = goalToDelete!!
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("Delete Exam Goal", style = MM.type.heading, color = c.ink) },
            text = {
                Text(
                    "Are you sure you want to remove '${target.title}' from your exam goals?",
                    style = MM.type.body,
                    color = c.ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGoal(target.id)
                        goalToDelete = null
                    },
                    shape = MM.radius.pill,
                    modifier = Modifier.heightIn(min = MMSize.minTouch).testTag("confirm_delete_goal"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MM.colors.danger,
                        contentColor = FixedWhite
                    ),
                    contentPadding = PaddingValues(horizontal = MM.space.xl, vertical = MM.space.s)
                ) {
                    Text("Delete", style = MM.type.bodyStrong)
                }
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { goalToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_goal")
                )
            }
        )
    }
}

@Composable
private fun ExamReadinessDashboardCard(
    priorities: List<ExamPriorityItem>,
    onStartExamDrill: () -> Unit
) {
    val c = MM.colors
    val total = priorities.size.coerceAtLeast(1)
    val strongCount = priorities.count { it.mastery.state == LearningMasteryState.STRONG }
    val developingCount = priorities.count {
        it.mastery.state == LearningMasteryState.DEVELOPING || it.mastery.state == LearningMasteryState.LEARNING
    }
    val gapCount = priorities.count {
        it.mastery.state == LearningMasteryState.NEEDS_REVIEW || it.mastery.state == LearningMasteryState.NEW
    }
    val readinessScore = (((strongCount * 100f) + (developingCount * 60f) + (gapCount * 20f)) / total).toInt().coerceIn(0, 100)

    val readinessColor = when {
        readinessScore >= 75 -> LearningFixedColors.MasteryStrong
        readinessScore >= 45 -> LearningFixedColors.MasteryDeveloping
        else -> LearningFixedColors.MasteryNeedsReview
    }

    MMCard(contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EXAM READINESS SCORE",
                        style = MM.type.overline,
                        color = c.inkSecondary
                    )
                    Text(
                        text = "$readinessScore% Ready",
                        style = MM.type.title.copy(fontWeight = FontWeight.Bold),
                        color = readinessColor
                    )
                    Text(
                        text = "$strongCount Strong · $developingCount Developing · $gapCount Priority Gaps",
                        style = MM.type.caption,
                        color = c.inkSecondary
                    )
                }
                PrimaryButton(
                    text = "Drill Priority Gaps",
                    onClick = onStartExamDrill,
                    modifier = Modifier.testTag("start_exam_drill_button")
                )
            }

            // Segmented readiness bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MM.radius.pill)
                    .background(c.surfaceSunk)
            ) {
                if (strongCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(strongCount.toFloat())
                            .height(8.dp)
                            .background(LearningFixedColors.MasteryStrong)
                    )
                }
                if (developingCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(developingCount.toFloat())
                            .height(8.dp)
                            .background(LearningFixedColors.MasteryDeveloping)
                    )
                }
                if (gapCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(gapCount.toFloat())
                            .height(8.dp)
                            .background(LearningFixedColors.MasteryNeedsReview)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamGoalCard(
    goal: LearningGoal,
    daysRemaining: Int,
    isSelected: Boolean = false,
    onSelect: () -> Unit = {},
    onDelete: () -> Unit
) {
    val c = MM.colors

    MMCard(
        contentPadding = MM.space.m,
        modifier = Modifier.clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = goal.title,
                    style = MM.type.heading,
                    color = if (isSelected) c.accent else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Format: ${goal.examFormat} · ${goal.dailyMinutesTarget} min daily study",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Box(
                    modifier = Modifier
                        .clip(MM.radius.small)
                        .background(c.accent.copy(alpha = 0.15f))
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                ) {
                    Text(
                        text = "$daysRemaining days",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = c.accent
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(MMSize.minTouch).testTag("delete_goal_${goal.id}")
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete Goal",
                        tint = c.inkSecondary,
                        modifier = Modifier.size(MMSize.iconSmall)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGoalsCard(onAddGoal: () -> Unit) {
    val c = MM.colors
    MMCard(contentPadding = MM.space.l) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = c.accent, modifier = Modifier.size(36.dp))
            Text("Set an Exam Goal", style = MM.type.bodyStrong, color = c.ink)
            Text(
                "Track days remaining, target grades, and unlock the High-Yield Priority Matrix.",
                style = MM.type.caption,
                color = c.inkSecondary
            )
            PrimaryButton(text = "Add Exam Goal", onClick = onAddGoal, modifier = Modifier.testTag("empty_add_goal"))
        }
    }
}

@Composable
private fun CramSheetActionCard(
    isGenerating: Boolean,
    onGenerate: () -> Unit
) {
    val c = MM.colors

    MMCard(contentPadding = MM.space.m) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "1-Page Cram Sheet",
                    style = MM.type.bodyStrong,
                    color = c.ink
                )
                Text(
                    text = "Synthesizes exam hints, core definitions & LaTeX formulas into one high-yield sheet.",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
            Spacer(Modifier.width(MM.space.s))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MMSize.iconSmall),
                        color = c.accent,
                        strokeWidth = 2.dp
                    )
                }
                PrimaryButton(
                    text = "Generate",
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    modifier = Modifier.testTag("generate_cram_sheet_button")
                )
            }
        }
    }
}

@Composable
private fun ExamPriorityRow(
    item: ExamPriorityItem,
    onClick: () -> Unit = {}
) {
    val c = MM.colors

    val importanceColor = when (item.concept.examImportance) {
        ExamImportance.LECTURER_FLAGGED -> LearningFixedColors.MasteryNeedsReview
        ExamImportance.HIGH_YIELD -> LearningFixedColors.MasteryDeveloping
        ExamImportance.NORMAL -> c.inkSecondary
    }

    val masteryColor = when (item.mastery.state) {
        LearningMasteryState.STRONG -> LearningFixedColors.MasteryStrong
        LearningMasteryState.DEVELOPING -> LearningFixedColors.MasteryDeveloping
        LearningMasteryState.LEARNING -> LearningFixedColors.MasteryLearning
        LearningMasteryState.NEEDS_REVIEW -> LearningFixedColors.MasteryNeedsReview
        LearningMasteryState.NEW -> LearningFixedColors.MasteryNew
    }

    MMCard(
        contentPadding = MM.space.m,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.concept.name,
                        style = MM.type.bodyStrong,
                        color = c.ink
                    )
                    if (item.sessionTitle.isNotBlank()) {
                        Text(
                            text = "Lecture: ${item.sessionTitle}",
                            style = MM.type.caption,
                            color = c.accent
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Box(
                        modifier = Modifier
                            .clip(MM.radius.small)
                            .background(importanceColor.copy(alpha = 0.15f))
                            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    ) {
                        Text(
                            text = item.concept.examImportance.label,
                            style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                            color = importanceColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(MM.radius.small)
                            .background(masteryColor.copy(alpha = 0.15f))
                            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    ) {
                        Text(
                            text = item.mastery.state.label,
                            style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                            color = masteryColor
                        )
                    }
                }
            }

            if (item.concept.definition.isNotBlank()) {
                Text(
                    text = item.concept.definition,
                    style = MM.type.caption,
                    color = c.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            item.concept.formulaLatex?.takeIf { it.isNotBlank() }?.let { formula ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MM.radius.small)
                        .background(c.surfaceSunk)
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                ) {
                    val parsedNode = remember(formula) {
                        runCatching { LatexMathParser.parse(formula) }.getOrNull()
                    }
                    if (parsedNode != null) {
                        MathExpressionView(
                            node = parsedNode,
                            fontSize = MM.type.body.fontSize,
                            inkColor = c.ink
                        )
                    } else {
                        Text(
                            text = formula,
                            style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                            color = c.ink
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateGoalDialog(
    courses: List<com.craftflowtechnologies.meetingmind.core.repository.LearningRepository.LearningCourse>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, examDateMs: Long, courseId: String?, targetGrade: String, dailyMinutes: Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf("") }
    var targetGrade by remember { mutableStateOf("A") }
    var dailyMinutes by remember { mutableStateOf("30") }
    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    var selectedExamDateMs by remember {
        mutableStateOf(System.currentTimeMillis() + (14 * 24 * 3600 * 1000L))
    }
    val dateFormat = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }

    val datePickerDialog = remember {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = selectedExamDateMs
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = java.util.Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                }
                selectedExamDateMs = chosen.timeInMillis
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Exam Goal", style = MM.type.heading, color = MM.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Exam Title (e.g. Midterm 1)") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_title_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Text(
                        text = "Exam Date: ${dateFormat.format(java.util.Date(selectedExamDateMs))}",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = MM.colors.ink
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                        verticalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        listOf(7 to "7d", 14 to "14d", 30 to "30d", 60 to "60d").forEach { (days, label) ->
                            val isChosen = ((selectedExamDateMs - System.currentTimeMillis()) / (24 * 3600 * 1000L)).toInt() == days
                            Box(
                                modifier = Modifier
                                    .clip(MM.radius.small)
                                    .background(if (isChosen) MM.colors.accent.copy(alpha = 0.2f) else MM.colors.surfaceSunk)
                                    .clickable {
                                        selectedExamDateMs = System.currentTimeMillis() + (days * 24 * 3600 * 1000L)
                                    }
                                    .padding(horizontal = MM.space.m, vertical = MM.space.s)
                            ) {
                                Text(
                                    label,
                                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                    color = if (isChosen) MM.colors.accent else MM.colors.ink
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.small)
                                .background(MM.colors.surfaceSunk)
                                .clickable { datePickerDialog.show() }
                                .padding(horizontal = MM.space.m, vertical = MM.space.s)
                        ) {
                            Text("Pick...", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                        }
                    }
                }

                OutlinedTextField(
                    value = targetGrade,
                    onValueChange = { targetGrade = it },
                    label = { Text("Target Grade (e.g. A, 90%)") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_grade_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = dailyMinutes,
                    onValueChange = { dailyMinutes = it },
                    label = { Text("Daily Study Target (minutes)") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_minutes_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )

                if (courses.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedDropdown,
                        onExpandedChange = { expandedDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = courses.firstOrNull { it.notebook.id == selectedCourseId }?.notebook?.name ?: "All Courses",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Associated Course") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Courses") },
                                onClick = {
                                    selectedCourseId = null
                                    expandedDropdown = false
                                }
                            )
                            courses.forEach { course ->
                                DropdownMenuItem(
                                    text = { Text(course.notebook.name) },
                                    onClick = {
                                        selectedCourseId = course.notebook.id
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Save Goal",
                onClick = {
                    if (title.isNotBlank()) {
                        val minutes = dailyMinutes.toIntOrNull() ?: 30
                        onConfirm(title.trim(), selectedExamDateMs, selectedCourseId, targetGrade.trim(), minutes)
                    }
                },
                modifier = Modifier.testTag("save_goal_confirm")
            )
        },
        dismissButton = {
            SecondaryButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

@Composable
private fun CramSheetPreviewDialog(
    markdown: String,
    onDismiss: () -> Unit,
    onSaveAsNote: () -> Unit,
    onShareToCircle: () -> Unit
) {
    val c = MM.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("1-Page Cram Sheet", style = MM.type.heading, color = c.ink) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 450.dp)
                    .clip(MM.radius.small)
                    .background(c.surfaceSunk)
                    .verticalScroll(rememberScrollState())
                    .padding(MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                CramSheetMarkdownViewer(markdown = markdown)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                SecondaryButton(
                    text = "Share to Circle",
                    onClick = onShareToCircle,
                    modifier = Modifier.testTag("cram_share_circle")
                )
                PrimaryButton(
                    text = "Save as Note",
                    onClick = onSaveAsNote,
                    modifier = Modifier.testTag("cram_save_note")
                )
            }
        },
        dismissButton = {
            SecondaryButton(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.testTag("cram_close_button")
            )
        }
    )
}

@Composable
private fun CramSheetMarkdownViewer(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val lines = remember(markdown) { markdown.lines() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(MM.space.xs))
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### "),
                        style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold),
                        color = c.ink,
                        modifier = Modifier.padding(top = MM.space.xs)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## "),
                        style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                        color = c.ink,
                        modifier = Modifier.padding(top = MM.space.s)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# "),
                        style = MM.type.title.copy(fontWeight = FontWeight.Bold),
                        color = c.accent,
                        modifier = Modifier.padding(top = MM.space.m)
                    )
                }
                trimmed == "---" || trimmed == "***" -> {
                    HorizontalDivider(
                        color = c.line,
                        modifier = Modifier.padding(vertical = MM.space.xs)
                    )
                }
                (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) ||
                (trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length > 2) -> {
                    val latex = if (trimmed.startsWith("$$")) {
                        trimmed.removeSurrounding("$$").trim()
                    } else {
                        trimmed.removeSurrounding("$").trim()
                    }
                    val parsedNode = remember(latex) {
                        runCatching { LatexMathParser.parse(latex) }.getOrNull()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MM.radius.small)
                            .background(c.surface)
                            .padding(horizontal = MM.space.m, vertical = MM.space.s),
                        contentAlignment = Alignment.Center
                    ) {
                        if (parsedNode != null) {
                            MathExpressionView(
                                node = parsedNode,
                                fontSize = MM.type.body.fontSize,
                                inkColor = c.ink
                            )
                        } else {
                            Text(
                                text = latex,
                                style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                color = c.ink
                            )
                        }
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletText = trimmed.substring(2).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MM.type.bodyStrong,
                            color = c.accent
                        )
                        if ((bulletText.startsWith("$$") && bulletText.endsWith("$$")) ||
                            (bulletText.startsWith("$") && bulletText.endsWith("$"))) {
                            val latex = if (bulletText.startsWith("$$")) {
                                bulletText.removeSurrounding("$$").trim()
                            } else {
                                bulletText.removeSurrounding("$").trim()
                            }
                            val parsedNode = remember(latex) {
                                runCatching { LatexMathParser.parse(latex) }.getOrNull()
                            }
                            if (parsedNode != null) {
                                MathExpressionView(
                                    node = parsedNode,
                                    fontSize = MM.type.body.fontSize,
                                    inkColor = c.ink
                                )
                            } else {
                                Text(
                                    text = bulletText,
                                    style = MM.type.caption,
                                    color = c.ink,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Text(
                                text = bulletText,
                                style = MM.type.caption,
                                color = c.ink,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = trimmed,
                        style = MM.type.caption,
                        color = c.ink
                    )
                }
            }
        }
    }
}
