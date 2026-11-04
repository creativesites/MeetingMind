package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeScaffold
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

data class LearnHomeActions(
    val onBack: () -> Unit = {},
    val onAssistant: () -> Unit = {},
    val onStartDailyRecall: () -> Unit = {},
    val onRecordLecture: () -> Unit = {},
    val onAudioCram: () -> Unit = {},
    val onWriteStudyNote: () -> Unit = {},
    val onOpenCircles: () -> Unit = {},
    val onOpenSession: (noteId: String) -> Unit = {},
    val onOpenCourse: (courseId: String) -> Unit = {},
    val onOpenPractice: () -> Unit = {},
    val onOpenExamPrep: () -> Unit = {}
)

@Composable
fun LearnHomeScreen(
    viewModel: LearnViewModel,
    actions: LearnHomeActions,
    modifier: Modifier = Modifier
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val dailyBrief by viewModel.dailyBrief.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val examPriorities by viewModel.examPriorities.collectAsState()

    LearnHomeContent(
        activeTab = activeTab,
        courses = courses,
        sessions = sessions,
        dailyBrief = dailyBrief,
        goals = goals,
        examPriorities = examPriorities,
        onSelectTab = { viewModel.setTab(it) },
        onCreateCourse = { name, code, domain ->
            viewModel.createCourse(name = name, courseCode = code, domain = domain)
        },
        actions = actions,
        modifier = modifier
    )
}

@Composable
fun LearnHomeContent(
    activeTab: LearnTab,
    courses: List<LearningRepository.LearningCourse>,
    sessions: List<LearningSession>,
    dailyBrief: LearningRepository.DailyCompanionBrief?,
    goals: List<com.craftflowtechnologies.meetingmind.core.model.LearningGoal>,
    examPriorities: List<com.craftflowtechnologies.meetingmind.core.model.ExamPriorityItem>,
    onSelectTab: (LearnTab) -> Unit,
    onCreateCourse: (name: String, code: String, domain: SubjectDomain) -> Unit,
    actions: LearnHomeActions,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    var showCreateCourseDialog by remember { mutableStateOf(false) }

    HomeScaffold(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .testTag("learn_home"),
        header = {
            ScreenHeader(
                title = "Learn",
                leading = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink)
                    }
                },
                actions = {
                    IconButton(
                        onClick = actions.onAssistant,
                        modifier = Modifier.testTag("learn_assistant")
                    ) {
                        Icon(AiMark, contentDescription = "Ask the companion tutor", tint = c.ink)
                    }
                }
            )
        },
        hero = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                CompanionBriefHero(dailyBrief = dailyBrief, onStartRecall = actions.onStartDailyRecall)
                PrimaryActionRow(actions = actions)
                TabNavigationBar(activeTab = activeTab, onSelectTab = onSelectTab)
            }
        },
        sections = {
            when (activeTab) {
                LearnTab.TODAY -> {
                    item(key = "due_reviews") {
                        DueReviewsSection(
                            dailyBrief = dailyBrief,
                            onStartRecall = actions.onStartDailyRecall
                        )
                    }
                    item(key = "high_yield") {
                        HighYieldWeakSection(
                            weakConcepts = dailyBrief?.weakHighYieldConcepts.orEmpty(),
                            onOpenSession = actions.onOpenSession
                        )
                    }
                    item(key = "recent_sessions") {
                        RecentSessionsSection(
                            sessions = sessions.take(5),
                            onOpenSession = actions.onOpenSession
                        )
                    }
                }
                LearnTab.COURSES -> {
                    item(key = "courses_list") {
                        CoursesSection(
                            courses = courses,
                            onOpenCourse = actions.onOpenCourse,
                            onNewCourse = { showCreateCourseDialog = true }
                        )
                    }
                }
                LearnTab.PRACTICE -> {
                    item(key = "practice_modes") {
                        PracticeModesSection(actions = actions)
                    }
                }
                LearnTab.EXAM -> {
                    item(key = "exam_priorities") {
                        ExamPrepOverviewSection(
                            goals = goals,
                            priorities = examPriorities,
                            actions = actions
                        )
                    }
                }
            }
        }
    )

    if (showCreateCourseDialog) {
        CreateCourseDialog(
            onDismiss = { showCreateCourseDialog = false },
            onConfirm = { name, code, domain ->
                onCreateCourse(name, code, domain)
                showCreateCourseDialog = false
            }
        )
    }
}

@Composable
private fun CompanionBriefHero(
    dailyBrief: LearningRepository.DailyCompanionBrief?,
    onStartRecall: () -> Unit
) {
    val companionForm = rememberCompanionSettings().value.form ?: CompanionForm.ZURI
    val dueCount = dailyBrief?.dueReviews?.size ?: 0
    val weakCount = dailyBrief?.weakHighYieldConcepts?.size ?: 0
    val examCountdown = dailyBrief?.upcomingExams?.firstOrNull()

    val companionState = when {
        dueCount > 0 -> CompanionState.CURIOUS
        weakCount > 0 -> CompanionState.THINKING
        else -> CompanionState.IDLE
    }

    HeroCard(
        modifier = Modifier.testTag("learn_hero_card"),
        onClick = onStartRecall
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Today's Study Brief", style = MM.type.caption, color = MM.colors.accent)
                Spacer(Modifier.height(MM.space.xs))
                Text(
                    text = when {
                        examCountdown != null -> "${examCountdown.daysRemaining} days to ${examCountdown.goal.title}"
                        dueCount > 0 -> "$dueCount active recall reviews due"
                        else -> "All caught up for today"
                    },
                    style = MM.type.title,
                    color = MM.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(MM.space.xs))
                Text(
                    text = when {
                        examCountdown != null -> "${dailyBrief?.recommendedMinutes ?: 15} min target · focus on high-yield exam topics"
                        dueCount > 0 -> "${dailyBrief?.recommendedMinutes ?: 10} min recommended review time"
                        else -> "Start a new lecture or test your knowledge."
                    },
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(MM.space.m))
            Companion(
                form = companionForm,
                state = companionState,
                size = 56.dp,
                modifier = Modifier.testTag("learn_companion_hero")
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = MM.space.l),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            PrimaryButton(
                text = EntryPoints.learnDailyRecall.label,
                onClick = onStartRecall
            )
        }
    }
}

@Composable
private fun PrimaryActionRow(actions: LearnHomeActions) {
    val c = MM.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        MMCard(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = actions.onRecordLecture)
                .testTag("learn_record_lecture"),
            contentPadding = MM.space.m
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                Text(
                    text = EntryPoints.learnRecordLecture.label,
                    style = MM.type.bodyStrong,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Live markers & hints",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
        }

        MMCard(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = actions.onAudioCram)
                .testTag("learn_audio_cram"),
            contentPadding = MM.space.m
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                Text(
                    text = EntryPoints.learnAudioCram.label,
                    style = MM.type.bodyStrong,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Spoken math & tables",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        MMCard(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = actions.onWriteStudyNote)
                .testTag("learn_write_study_note"),
            contentPadding = MM.space.s
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                Text(
                    text = EntryPoints.learnWriteStudyNote.label,
                    style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        MMCard(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = actions.onOpenCircles)
                .testTag("learn_circles"),
            contentPadding = MM.space.s
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                Text(
                    text = EntryPoints.learnCircles.label,
                    style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TabNavigationBar(
    activeTab: LearnTab,
    onSelectTab: (LearnTab) -> Unit
) {
    val c = MM.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceSunk)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LearnTab.entries.forEach { tab ->
            val isSelected = tab == activeTab
            val label = when (tab) {
                LearnTab.TODAY -> "Today"
                LearnTab.COURSES -> "Courses"
                LearnTab.PRACTICE -> "Practice"
                LearnTab.EXAM -> "Exam Prep"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) c.surface else c.surfaceSunk)
                    .clickable { onSelectTab(tab) }
                    .padding(vertical = 8.dp)
                    .testTag("learn_tab_${tab.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                    color = if (isSelected) c.ink else c.inkSecondary
                )
            }
        }
    }
}

@Composable
private fun DueReviewsSection(
    dailyBrief: LearningRepository.DailyCompanionBrief?,
    onStartRecall: () -> Unit
) {
    val due = dailyBrief?.dueReviews.orEmpty()
    SectionHeader(
        title = "Due for Review",
        count = due.size,
        actionLabel = if (due.isNotEmpty()) "Review all" else null,
        onAction = if (due.isNotEmpty()) onStartRecall else null
    )

    if (due.isEmpty()) {
        MMCard(contentPadding = MM.space.m) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LearningFixedColors.MasteryStrong)
                Text(
                    text = "No reviews due right now. You're completely up to date!",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            due.take(3).forEach { item ->
                MMCard(contentPadding = MM.space.s) {
                    ListRow(
                        title = item.activity.prompt,
                        subtitle = "${item.session.title} · ${item.concept?.name ?: item.activity.type.name}",
                        leading = {
                            Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = MM.colors.accent)
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MM.colors.inkSecondary)
                        },
                        onClick = onStartRecall
                    )
                }
            }
        }
    }
}

@Composable
private fun HighYieldWeakSection(
    weakConcepts: List<LearningConcept>,
    onOpenSession: (noteId: String) -> Unit
) {
    if (weakConcepts.isEmpty()) return

    SectionHeader(title = "High-Yield Concepts to Strengthen")
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        weakConcepts.take(3).forEach { concept ->
            MMCard(contentPadding = MM.space.s) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = LearningFixedColors.MasteryNeedsReview, modifier = Modifier.size(16.dp))
                            Text(
                                text = concept.name,
                                style = MM.type.bodyStrong,
                                color = MM.colors.ink
                            )
                        }
                        Text(
                            text = concept.definition,
                            style = MM.type.caption,
                            color = MM.colors.inkSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(LearningFixedColors.MasteryNeedsReview.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = concept.examImportance.label,
                            style = MM.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = LearningFixedColors.MasteryNeedsReview
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSessionsSection(
    sessions: List<LearningSession>,
    onOpenSession: (noteId: String) -> Unit
) {
    SectionHeader(title = "Recent Study Sessions")
    if (sessions.isEmpty()) {
        MMCard(contentPadding = MM.space.m) {
            Text(
                text = "No study sessions yet. Tap 'Record a lecture' or 'Write a study note' above.",
                style = MM.type.secondary,
                color = MM.colors.inkSecondary
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            sessions.forEach { session ->
                MMCard(contentPadding = MM.space.s) {
                    ListRow(
                        title = session.title,
                        subtitle = "${session.courseName ?: "General"} · ${session.subjectDomain}",
                        leading = {
                            Icon(Icons.Rounded.School, contentDescription = null, tint = MM.colors.inkSecondary)
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MM.colors.inkSecondary)
                        },
                        onClick = { onOpenSession(session.noteId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CoursesSection(
    courses: List<LearningRepository.LearningCourse>,
    onOpenCourse: (courseId: String) -> Unit,
    onNewCourse: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Your Courses", style = MM.type.title.copy(fontSize = 18.sp), color = MM.colors.ink)
        TextButton(onClick = onNewCourse, modifier = Modifier.testTag("learn_new_course_button")) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("New Course", style = MM.type.caption.copy(fontWeight = FontWeight.Bold))
        }
    }

    if (courses.isEmpty()) {
        MMCard(contentPadding = MM.space.l) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(36.dp))
                Text("Organize by Course", style = MM.type.bodyStrong, color = MM.colors.ink)
                Text(
                    "Create courses for your subjects (Math, Engineering, Accounting, CS, etc.) to organize lecture notes and exam prep.",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                SecondaryButton(text = "Create First Course", onClick = onNewCourse)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            courses.forEach { course ->
                MMCard(
                    modifier = Modifier.clickable { onOpenCourse(course.notebook.id) },
                    contentPadding = MM.space.m
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = course.notebook.name,
                                style = MM.type.bodyStrong,
                                color = MM.colors.ink
                            )
                            if (course.properties.courseCode.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MM.colors.surfaceSunk)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = course.properties.courseCode,
                                        style = MM.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                        color = MM.colors.ink
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MM.space.m),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${course.noteCount} notes · ${course.sessionCount} sessions",
                                style = MM.type.caption,
                                color = MM.colors.inkSecondary
                            )
                            Text(
                                text = course.properties.domain.displayName,
                                style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                color = MM.colors.accent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeModesSection(actions: LearnHomeActions) {
    SectionHeader(title = "Practice Arena")
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        MMCard(
            modifier = Modifier.clickable(onClick = actions.onOpenPractice),
            contentPadding = MM.space.m
        ) {
            ListRow(
                title = "Flashcard & Problem Recall",
                subtitle = "Active retrieval with instant 'Explain My Mistake' feedback",
                leading = {
                    Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = MM.colors.accent)
                },
                trailing = {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MM.colors.inkSecondary)
                },
                onClick = actions.onOpenPractice
            )
        }

        MMCard(
            modifier = Modifier.clickable(onClick = actions.onOpenPractice),
            contentPadding = MM.space.m
        ) {
            ListRow(
                title = EntryPoints.learnTeachBack.label,
                subtitle = "Explain a concept out loud to your companion and receive a qualitative rubric score",
                leading = {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = MM.colors.accent)
                },
                trailing = {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MM.colors.inkSecondary)
                },
                onClick = actions.onOpenPractice
            )
        }
    }
}

@Composable
private fun ExamPrepOverviewSection(
    goals: List<com.craftflowtechnologies.meetingmind.core.model.LearningGoal>,
    priorities: List<com.craftflowtechnologies.meetingmind.core.model.ExamPriorityItem>,
    actions: LearnHomeActions
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Exam Readiness Studio", style = MM.type.title.copy(fontSize = 18.sp), color = MM.colors.ink)
        TextButton(onClick = actions.onOpenExamPrep) {
            Text("Open Studio", style = MM.type.caption.copy(fontWeight = FontWeight.Bold))
        }
    }

    if (goals.isEmpty()) {
        MMCard(contentPadding = MM.space.m) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Set an Exam Goal", style = MM.type.bodyStrong, color = MM.colors.ink)
                Text(
                    "Add an upcoming exam to unlock countdowns, the High-Yield Priority Matrix, and 1-Page Cram Sheets.",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
                PrimaryButton(text = "Set Exam Goal", onClick = actions.onOpenExamPrep)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            goals.take(2).forEach { goal ->
                val daysRemaining = ((goal.examDateMs - System.currentTimeMillis()).coerceAtLeast(0L) / (24 * 3600 * 1000L)).toInt()
                MMCard(
                    modifier = Modifier.clickable(onClick = actions.onOpenExamPrep),
                    contentPadding = MM.space.m
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(goal.title, style = MM.type.bodyStrong, color = MM.colors.ink)
                            Text("Format: ${goal.examFormat} · ${goal.dailyMinutesTarget} min/day", style = MM.type.caption, color = MM.colors.inkSecondary)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MM.colors.surfaceSunk)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$daysRemaining days", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = MM.colors.accent)
                        }
                    }
                }
            }

            if (priorities.isNotEmpty()) {
                SectionHeader(title = "Top Exam Priorities")
                priorities.take(3).forEach { item ->
                    MMCard(contentPadding = MM.space.s) {
                        ListRow(
                            title = item.concept.name,
                            subtitle = "${item.sessionTitle} · ${item.concept.examImportance.label} (${item.mastery.state.label})",
                            leading = {
                                Icon(Icons.Rounded.Assignment, contentDescription = null, tint = MM.colors.accent)
                            },
                            trailing = {
                                Text(
                                    "Score: ${String.format(java.util.Locale.US, "%.0f", item.priorityScore)}",
                                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                    color = MM.colors.inkSecondary
                                )
                            },
                            onClick = actions.onOpenExamPrep
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCourseDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, code: String, domain: SubjectDomain) -> Unit
) {
    var courseName by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var selectedDomain by remember { mutableStateOf(SubjectDomain.GENERAL) }
    var expandedDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Course", style = MM.type.title.copy(fontSize = 18.sp), color = MM.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course Name (e.g. Calculus II)") },
                    modifier = Modifier.fillMaxWidth().testTag("course_name_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = courseCode,
                    onValueChange = { courseCode = it },
                    label = { Text("Course Code (e.g. MATH 201)") },
                    modifier = Modifier.fillMaxWidth().testTag("course_code_input"),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedDomain.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject Domain") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("course_domain_select")
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        SubjectDomain.entries.forEach { domain ->
                            DropdownMenuItem(
                                text = { Text(domain.displayName) },
                                onClick = {
                                    selectedDomain = domain
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Create",
                onClick = {
                    if (courseName.isNotBlank()) {
                        onConfirm(courseName.trim(), courseCode.trim(), selectedDomain)
                    }
                },
                modifier = Modifier.testTag("course_dialog_confirm")
            )
        },
        dismissButton = {
            SecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.testTag("course_dialog_cancel")
            )
        }
    )
}
