package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM

@Composable
fun ExamPrepScreen(
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onShareToCircle: (cramSheetMarkdown: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val goals by viewModel.goals.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val priorities by viewModel.examPriorities.collectAsState()
    val cramSheetMarkdown by viewModel.cramSheetMarkdown.collectAsState()
    val isGeneratingCramSheet by viewModel.isGeneratingCramSheet.collectAsState()

    var showCreateGoalDialog by remember { mutableStateOf(false) }
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
            viewModel.saveCramSheetAsNote(title, null, markdown) { noteId ->
                onOpenNote(noteId)
            }
        },
        onShareToCircle = { markdown ->
            onShareToCircle(markdown)
        },
        onBack = onBack,
        modifier = modifier
    )

    if (showCreateGoalDialog) {
        CreateGoalDialog(
            courses = courses,
            onDismiss = { showCreateGoalDialog = false },
            onConfirm = { title, examDateMs, courseId, targetGrade, dailyMinutes ->
                viewModel.createOrUpdateGoal(
                    title = title,
                    examDateMs = examDateMs,
                    notebookId = courseId,
                    targetGrade = targetGrade,
                    dailyMinutesTarget = dailyMinutes
                )
                showCreateGoalDialog = false
            }
        )
    }

    if (showCramSheetPreview && cramSheetMarkdown != null) {
        CramSheetPreviewDialog(
            markdown = cramSheetMarkdown!!,
            onDismiss = { showCramSheetPreview = false },
            onSaveAsNote = {
                viewModel.saveCramSheetAsNote("Exam Cram Sheet", null, cramSheetMarkdown!!) { noteId ->
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
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .testTag("exam_prep_screen")
    ) {
        ScreenHeader(
            title = "Exam Readiness",
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            // Exam Countdown Banner
            item(key = "countdown_banner") {
                if (goals.isEmpty()) {
                    EmptyGoalsCard(onAddGoal = { showDialog = true })
                } else {
                    val primaryGoal = goals.first()
                    val daysRemaining = ((primaryGoal.examDateMs - System.currentTimeMillis()).coerceAtLeast(0L) / (24 * 3600 * 1000L)).toInt()
                    ExamCountdownHero(goal = primaryGoal, daysRemaining = daysRemaining, onDelete = { onDeleteGoal(primaryGoal.id) })
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
                    ExamPriorityRow(item = item)
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
}

@Composable
private fun ExamCountdownHero(
    goal: LearningGoal,
    daysRemaining: Int,
    onDelete: () -> Unit
) {
    val c = MM.colors

    MMCard(contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = goal.title,
                        style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = c.ink
                    )
                    Text(
                        text = "Format: ${goal.examFormat} · ${goal.dailyMinutesTarget} min daily study",
                        style = MM.type.caption,
                        color = c.inkSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accent.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$daysRemaining days",
                        style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                        color = c.accent
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
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = c.accent)
            } else {
                PrimaryButton(
                    text = "Generate",
                    onClick = onGenerate,
                    modifier = Modifier.testTag("generate_cram_sheet_button")
                )
            }
        }
    }
}

@Composable
private fun ExamPriorityRow(item: ExamPriorityItem) {
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

    MMCard(contentPadding = MM.space.m) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.concept.name,
                    style = MM.type.bodyStrong,
                    color = c.ink,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(importanceColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.concept.examImportance.label,
                            style = MM.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = importanceColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(masteryColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.mastery.state.label,
                            style = MM.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = masteryColor
                        )
                    }
                }
            }

            Text(
                text = item.concept.definition,
                style = MM.type.caption,
                color = c.inkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            item.concept.formulaLatex?.let { formula ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.surfaceSunk)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGoalDialog(
    courses: List<com.craftflowtechnologies.meetingmind.core.repository.LearningRepository.LearningCourse>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, examDateMs: Long, courseId: String?, targetGrade: String, dailyMinutes: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var targetGrade by remember { mutableStateOf("A") }
    var dailyMinutes by remember { mutableStateOf("30") }
    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    val defaultExamDate = remember { System.currentTimeMillis() + (14 * 24 * 3600 * 1000L) } // 14 days from now

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Exam Goal", style = MM.type.title.copy(fontSize = 18.sp), color = MM.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Exam Title (e.g. Midterm 1)") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_title_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = targetGrade,
                    onValueChange = { targetGrade = it },
                    label = { Text("Target Grade (e.g. A, 90%)") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_grade_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = dailyMinutes,
                    onValueChange = { dailyMinutes = it },
                    label = { Text("Daily Study Target (minutes)") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_minutes_input"),
                    singleLine = true
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
                        onConfirm(title.trim(), defaultExamDate, selectedCourseId, targetGrade.trim(), minutes)
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
        title = { Text("1-Page Cram Sheet", style = MM.type.title.copy(fontSize = 18.sp), color = c.ink) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surfaceSunk)
                    .padding(MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Text(
                    text = markdown,
                    style = MM.type.caption,
                    color = c.ink
                )
            }
        },
        confirmButton = {
            PrimaryButton(text = "Save as Note", onClick = onSaveAsNote, modifier = Modifier.testTag("cram_save_note"))
        },
        dismissButton = {
            SecondaryButton(text = "Share to Circle", onClick = onShareToCircle, modifier = Modifier.testTag("cram_share_circle"))
        }
    )
}
