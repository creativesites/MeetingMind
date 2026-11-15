package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar
import com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeScaffold
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
    val onStartSprint: () -> Unit = onStartDailyRecall,
    val onRecordLecture: () -> Unit = {},
    val onAudioCram: () -> Unit = {},
    val onWriteStudyNote: () -> Unit = {},
    val onOpenCircles: () -> Unit = {},
    val onOpenSession: (noteId: String) -> Unit = {},
    val onOpenCourse: (courseId: String) -> Unit = {},
    val onOpenPractice: () -> Unit = {},
    val onOpenPracticeMode: (PracticeArenaFilter) -> Unit = { onOpenPractice() },
    val onPracticeCourse: (courseId: String) -> Unit = { onOpenPractice() },
    val onOpenExamPrep: () -> Unit = {}
)

@Composable
fun LearnHomeScreen(
    viewModel: LearnViewModel,
    actions: LearnHomeActions,
    modifier: Modifier = Modifier,
    onNavigateBottomNav: ((BottomNavDestination) -> Unit)? = null
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val lectureNotes by viewModel.lectureNotes.collectAsState()
    val dailyBrief by viewModel.dailyBrief.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val examPriorities by viewModel.examPriorities.collectAsState()

    val enhancedActions = remember(actions, viewModel) {
        actions.copy(
            onStartDailyRecall = {
                viewModel.loadPracticeByFilter(
                    filter = PracticeArenaFilter.ALL,
                    sessionId = null,
                    courseId = null,
                    onlyDue = false
                )
                actions.onStartDailyRecall()
            },
            onStartSprint = {
                viewModel.loadPracticeByFilter(
                    filter = PracticeArenaFilter.ALL,
                    sessionId = null,
                    courseId = null,
                    onlyDue = false
                )
                if (actions.onStartSprint !== actions.onStartDailyRecall) {
                    actions.onStartSprint()
                } else {
                    actions.onStartDailyRecall()
                }
            },
            onOpenPractice = {
                viewModel.loadPracticeByFilter(
                    filter = PracticeArenaFilter.ALL,
                    sessionId = null,
                    courseId = null
                )
                actions.onOpenPractice()
            },
            onOpenPracticeMode = { filter ->
                viewModel.loadPracticeByFilter(
                    filter = filter,
                    sessionId = null,
                    courseId = null
                )
                actions.onOpenPractice()
            },
            onPracticeCourse = { courseId ->
                viewModel.loadPracticeByFilter(
                    filter = PracticeArenaFilter.ALL,
                    sessionId = null,
                    courseId = courseId
                )
                actions.onOpenPractice()
            }
        )
    }

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
        actions = enhancedActions,
        modifier = modifier,
        onNavigateBottomNav = onNavigateBottomNav,
        lectureNotes = lectureNotes,
        onCreateCourseFromNotes = { noteIds, name, code, domain, instructor ->
            viewModel.createCourseFromNotes(
                noteIds = noteIds,
                courseName = name,
                courseCode = code,
                domain = domain,
                instructor = instructor
            )
        },
        onAssignNoteToCourse = { noteId, courseId ->
            viewModel.assignNoteToCourse(noteId, courseId)
        },
        onGenerateQuickDeck = { topic, domain ->
            viewModel.generateQuickTopicDeck(topic = topic, domain = domain) {
                actions.onOpenPractice()
            }
        },
        onDrillWeakConcepts = { weakList ->
            viewModel.loadWeakConceptsPractice(weakList) {
                actions.onOpenPractice()
            }
        }
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
    modifier: Modifier = Modifier,
    onNavigateBottomNav: ((BottomNavDestination) -> Unit)? = null,
    lectureNotes: List<LearningRepository.LectureNoteSummary> = emptyList(),
    onCreateCourseFromNotes: (noteIds: List<String>, name: String?, code: String, domain: SubjectDomain?, instructor: String) -> Unit = { _, name, code, domain, _ ->
        if (!name.isNullOrBlank()) {
            onCreateCourse(name, code, domain ?: SubjectDomain.GENERAL)
        }
    },
    onAssignNoteToCourse: (noteId: String, courseId: String) -> Unit = { _, _ -> },
    onGenerateQuickDeck: (topic: String, domain: SubjectDomain) -> Unit = { _, _ -> actions.onOpenPractice() },
    onDrillWeakConcepts: (List<LearningConcept>) -> Unit = { actions.onStartDailyRecall() }
) {
    val c = MM.colors
    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var preselectedLectureForCourse by remember { mutableStateOf<LearningRepository.LectureNoteSummary?>(null) }

    val totalLecturesCount = maxOf(lectureNotes.size, sessions.size)
    val contextLine = remember(courses.size, totalLecturesCount, dailyBrief) {
        val coursePart = when (val count = courses.size) {
            0 -> "No courses"
            1 -> "1 course"
            else -> "$count courses"
        }
        val lecturePart = if (totalLecturesCount > 0) "$totalLecturesCount lectures" else null
        val dueCount = dailyBrief?.dueReviews?.size ?: 0
        val duePart = if (dueCount > 0) "$dueCount due today" else "all caught up"
        val exams = dailyBrief?.upcomingExams?.firstOrNull()
        val examPart = exams?.let { "${it.daysRemaining}d to ${it.goal.title}" }
        listOfNotNull(coursePart, lecturePart, duePart, examPart).joinToString(" · ")
    }

    val scaffoldContent: @Composable (Modifier) -> Unit = { paddingModifier ->
        HomeScaffold(
            modifier = modifier
                .fillMaxSize()
                .background(c.background)
                .statusBarsPadding()
                .then(paddingModifier)
                .testTag("learn_home"),
            header = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    ScreenHeader(
                        title = "Learn",
                        leading = if (onNavigateBottomNav == null) {
                            {
                                IconButton(onClick = actions.onBack) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = c.ink
                                    )
                                }
                            }
                        } else null,
                        actions = {
                            IconButton(
                                onClick = actions.onAssistant,
                                modifier = Modifier.testTag("learn_assistant")
                            ) {
                                Icon(
                                    AiMark,
                                    contentDescription = "Ask the Learning Assistant",
                                    tint = c.ink
                                )
                            }
                        }
                    )
                    Text(
                        text = contextLine,
                        style = MM.type.secondary,
                        color = c.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(MM.space.xs))
                    LearnTabSegmentedControl(
                        activeTab = activeTab,
                        dueCount = dailyBrief?.dueReviews?.size ?: 0,
                        courseCount = courses.size,
                        goalCount = goals.size,
                        onSelectTab = onSelectTab
                    )
                }
            },
            hero = {
                AnimatedVisibility(
                    visible = activeTab == LearnTab.TODAY,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    CompanionBriefHero(
                        dailyBrief = dailyBrief,
                        hasSessions = sessions.isNotEmpty() || lectureNotes.isNotEmpty(),
                        hasCourses = courses.isNotEmpty(),
                        onStartRecall = actions.onStartDailyRecall,
                        onRecordLecture = actions.onRecordLecture,
                        onAskAssistant = actions.onAssistant
                    )
                }
            },
            sections = {
                when (activeTab) {
                    LearnTab.TODAY -> {
                        item(key = "quick_actions") {
                            LearnQuickActions(actions = actions)
                        }
                        item(key = "due_reviews") {
                            DueReviewsSection(
                                dailyBrief = dailyBrief,
                                onStartRecall = actions.onStartDailyRecall,
                                onStartSprint = actions.onStartSprint
                            )
                        }
                        item(key = "high_yield") {
                            HighYieldWeakSection(
                                weakConcepts = dailyBrief?.weakHighYieldConcepts.orEmpty(),
                                sessions = sessions,
                                onOpenSession = actions.onOpenSession,
                                onDrillWeakConcepts = onDrillWeakConcepts
                            )
                        }
                        item(key = "recent_sessions") {
                            RecentSessionsSection(
                                sessions = sessions,
                                lectureNotes = lectureNotes,
                                actions = actions,
                                onOpenSession = actions.onOpenSession,
                                onCreateCourseFromLecture = { lecture ->
                                    preselectedLectureForCourse = lecture
                                    showCreateCourseDialog = true
                                }
                            )
                        }
                    }
                    LearnTab.COURSES -> {
                        item(key = "courses_list") {
                            CoursesSection(
                                courses = courses,
                                lectureNotes = lectureNotes,
                                dailyBrief = dailyBrief,
                                onOpenCourse = actions.onOpenCourse,
                                onPracticeCourse = actions.onPracticeCourse,
                                onOpenSession = actions.onOpenSession,
                                onNewCourse = {
                                    preselectedLectureForCourse = null
                                    showCreateCourseDialog = true
                                },
                                onCreateCourseFromLecture = { lecture ->
                                    preselectedLectureForCourse = lecture
                                    showCreateCourseDialog = true
                                },
                                onQuickCreateCourseFromLecture = { lecture ->
                                    onCreateCourseFromNotes(
                                        listOf(lecture.noteId),
                                        null,
                                        "",
                                        lecture.domain,
                                        ""
                                    )
                                },
                                onAssignNoteToCourse = onAssignNoteToCourse
                            )
                        }
                    }
                    LearnTab.PRACTICE -> {
                        item(key = "practice_modes") {
                            PracticeModesSection(
                                actions = actions,
                                dueCount = dailyBrief?.dueReviews?.size ?: 0,
                                lectureCount = totalLecturesCount,
                                onGenerateQuickDeck = onGenerateQuickDeck
                            )
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
    }

    if (onNavigateBottomNav != null) {
        Scaffold(
            containerColor = c.background,
            bottomBar = {
                AppBottomNavigationBar(
                    current = BottomNavDestination.HOME,
                    onNavigate = {
                        if (it != BottomNavDestination.HOME) {
                            onNavigateBottomNav(it)
                        }
                    }
                )
            }
        ) { innerPadding ->
            scaffoldContent(Modifier.padding(innerPadding))
        }
    } else {
        scaffoldContent(Modifier)
    }

    if (showCreateCourseDialog) {
        CreateCourseDialog(
            unassignedLectures = lectureNotes.filter { !it.isAssignedToCourse },
            preselectedLecture = preselectedLectureForCourse,
            onDismiss = {
                showCreateCourseDialog = false
                preselectedLectureForCourse = null
            },
            onConfirm = { name, code, domain, instructor, selectedNoteIds ->
                if (selectedNoteIds.isNotEmpty()) {
                    onCreateCourseFromNotes(selectedNoteIds, name, code, domain, instructor)
                } else {
                    onCreateCourse(name, code, domain)
                }
                showCreateCourseDialog = false
                preselectedLectureForCourse = null
            }
        )
    }
}

@Composable
private fun LearnTabSegmentedControl(
    activeTab: LearnTab,
    dueCount: Int = 0,
    courseCount: Int = 0,
    goalCount: Int = 0,
    onSelectTab: (LearnTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MM.radius.pill,
        color = c.track
    ) {
        Row(Modifier.selectableGroup().padding(MM.space.xs)) {
            LearnTab.entries.forEach { tab ->
                val isSel = tab == activeTab
                val fill = animateColorAsState(
                    if (isSel) c.surfaceRaised else c.track,
                    MM.motion.quick(),
                    label = "learn_tab_fill_${tab.name}"
                )
                val label = when (tab) {
                    LearnTab.TODAY -> "Today"
                    LearnTab.COURSES -> "Courses"
                    LearnTab.PRACTICE -> "Practice"
                    LearnTab.EXAM -> "Exam"
                }
                val badgeCount = when (tab) {
                    LearnTab.COURSES -> courseCount.takeIf { it > 0 }
                    LearnTab.PRACTICE -> dueCount.takeIf { it > 0 }
                    LearnTab.EXAM -> goalCount.takeIf { it > 0 }
                    else -> null
                }
                Surface(
                    shape = MM.radius.pill,
                    color = fill.value,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MMSize.minTouch)
                        .selectable(
                            selected = isSel,
                            role = Role.RadioButton,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectTab(tab)
                            }
                        )
                        .testTag("learn_tab_${tab.name.lowercase()}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = if (isSel) MM.type.bodyStrong else MM.type.body,
                            color = if (isSel) c.ink else c.inkSecondary,
                            maxLines = 1
                        )
                        if (badgeCount != null) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(MM.radius.pill)
                                    .background(if (isSel) c.accentWash else c.surfaceSunk)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$badgeCount",
                                    style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSel) c.accent else c.inkSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class BriefHeroData(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val ctaLabel: String,
    val ctaAction: () -> Unit
)

private val CompanionHeroSize = 56.dp

@Composable
private fun CompanionBriefHero(
    dailyBrief: LearningRepository.DailyCompanionBrief?,
    hasSessions: Boolean,
    hasCourses: Boolean,
    onStartRecall: () -> Unit,
    onRecordLecture: () -> Unit,
    onAskAssistant: () -> Unit = {}
) {
    val companionForm = rememberCompanionSettings().value.form ?: CompanionForm.ZURI
    val dueCount = dailyBrief?.dueReviews?.size ?: 0
    val weakCount = dailyBrief?.weakHighYieldConcepts?.size ?: 0
    val examCountdown = dailyBrief?.upcomingExams?.firstOrNull()
    val sevenDayActivity = dailyBrief?.sevenDayActivity.orEmpty()
    val activeDaysCount = sevenDayActivity.count { it }
    val completedToday = dailyBrief?.sevenDayAttemptCounts?.lastOrNull() ?: 0
    val totalReviews = dueCount + completedToday
    val masteryProgress = when {
        totalReviews > 0 -> (completedToday.toFloat() / totalReviews.toFloat()).coerceIn(0f, 1f)
        hasSessions || hasCourses -> 1f
        else -> 0f
    }

    val companionState = when {
        dueCount > 0 -> CompanionState.CURIOUS
        weakCount > 0 -> CompanionState.THINKING
        else -> CompanionState.IDLE
    }

    val isEmpty = !hasSessions && !hasCourses && dueCount == 0 && examCountdown == null

    val heroData = when {
        examCountdown != null -> {
            BriefHeroData(
                eyebrow = "UPCOMING EXAM · ${examCountdown.daysRemaining}D",
                title = "${examCountdown.daysRemaining} days to ${examCountdown.goal.title}",
                subtitle = "${dailyBrief?.recommendedMinutes ?: 15} min target · focus on high-yield exam topics",
                ctaLabel = EntryPoints.learnDailyRecall.label,
                ctaAction = onStartRecall
            )
        }
        dueCount > 0 -> {
            BriefHeroData(
                eyebrow = "TODAY'S STUDY BRIEF",
                title = "$dueCount active recall ${if (dueCount == 1) "review" else "reviews"} due",
                subtitle = "${dailyBrief?.recommendedMinutes ?: 10} min recommended review time",
                ctaLabel = EntryPoints.learnDailyRecall.label,
                ctaAction = onStartRecall
            )
        }
        isEmpty -> {
            BriefHeroData(
                eyebrow = "WELCOME TO LEARN",
                title = "Start your study workspace",
                subtitle = "Record a lecture or write study notes to build concepts, courses, and active recall.",
                ctaLabel = "Record First Lecture",
                ctaAction = onRecordLecture
            )
        }
        else -> {
            BriefHeroData(
                eyebrow = "TODAY'S STUDY BRIEF",
                title = "Ready for a focused study sprint",
                subtitle = "Practice flashcards, test yourself with worked problems, or ask your Socratic tutor.",
                ctaLabel = "Start Practice Arena",
                ctaAction = onStartRecall
            )
        }
    }

    HeroCard(
        modifier = Modifier.testTag("learn_hero_card"),
        onClick = heroData.ctaAction
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Text(heroData.eyebrow, style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = MM.colors.accent)
                    if (activeDaysCount > 0) {
                        Row(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(LearningFixedColors.MasteryDeveloping.copy(alpha = 0.16f))
                                .padding(horizontal = MM.space.s, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = LearningFixedColors.MasteryDeveloping,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${activeDaysCount}d active",
                                style = MM.type.overline,
                                color = LearningFixedColors.MasteryDeveloping
                            )
                        }
                    }
                }
                Spacer(Modifier.height(MM.space.xs))
                Text(
                    text = heroData.title,
                    style = MM.type.title,
                    color = MM.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(MM.space.xs))
                Text(
                    text = heroData.subtitle,
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(MM.space.m))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                MasteryProgressRing(
                    progress = masteryProgress
                )
                Companion(
                    form = companionForm,
                    state = companionState,
                    size = CompanionHeroSize,
                    modifier = Modifier.testTag("learn_companion_hero")
                )
            }
        }

        if (sevenDayActivity.size == 7) {
            Spacer(Modifier.height(MM.space.m))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "7-Day Momentum",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                    sevenDayActivity.forEachIndexed { idx, active ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) LearningFixedColors.MasteryStrong
                                    else MM.colors.surfaceSunk
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLabels.getOrElse(idx) { "•" },
                                style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                                color = if (active) MM.colors.onAccent else MM.colors.inkSecondary
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = MM.space.l),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryButton(
                text = heroData.ctaLabel,
                onClick = heroData.ctaAction
            )
            SecondaryButton(
                text = "Ask Tutor",
                onClick = onAskAssistant
            )
        }
    }
}

@Composable
private fun LearnQuickActions(actions: LearnHomeActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        LearnQuickAction(
            label = "Lecture",
            icon = Icons.Rounded.Mic,
            primary = true,
            onClick = actions.onRecordLecture,
            modifier = Modifier.weight(1f).testTag("learn_record_lecture")
        )
        LearnQuickAction(
            label = "Audio Cram",
            icon = Icons.Rounded.Headphones,
            primary = true,
            onClick = actions.onAudioCram,
            modifier = Modifier.weight(1f).testTag("learn_audio_cram")
        )
        LearnQuickAction(
            label = "Write Note",
            icon = Icons.Rounded.Edit,
            primary = false,
            onClick = actions.onWriteStudyNote,
            modifier = Modifier.weight(1f).testTag("learn_write_study_note")
        )
        LearnQuickAction(
            label = "Circles",
            icon = Icons.Rounded.Groups,
            primary = false,
            onClick = actions.onOpenCircles,
            modifier = Modifier.weight(1f).testTag("learn_circles")
        )
    }
}

@Composable
private fun LearnQuickAction(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .clickable(role = Role.Button, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .padding(vertical = MM.space.m, horizontal = MM.space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        Box(
            modifier = Modifier
                .size(MMSize.minTouch)
                .background(if (primary) MM.colors.accent else MM.colors.accentWash, MM.radius.pill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (primary) MM.colors.onAccent else MM.colors.accent
            )
        }
        Text(
            text = label,
            style = MM.type.caption,
            color = MM.colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DueReviewsSection(
    dailyBrief: LearningRepository.DailyCompanionBrief?,
    onStartRecall: () -> Unit,
    onStartSprint: () -> Unit = onStartRecall
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
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = LearningFixedColors.MasteryStrong
                    )
                    Text(
                        text = "No reviews due right now. You're completely up to date!",
                        style = MM.type.secondary,
                        color = MM.colors.inkSecondary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecondaryButton(
                        text = "Practice Ahead",
                        onClick = onStartRecall,
                        modifier = Modifier.testTag("due_reviews_practice_ahead")
                    )
                    QuickSprintPill(
                        onClick = onStartSprint,
                        modifier = Modifier.testTag("quick_sprint_pill_due")
                    )
                }
            }
        }
    } else {
        MMCard(contentPadding = MM.space.m) {
            due.take(3).forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MM.colors.lineSoft,
                        modifier = Modifier.padding(vertical = MM.space.xs)
                    )
                }
                ListRow(
                    title = item.activity.prompt.replace("**", ""),
                    subtitle = "${item.session.title} · ${item.concept?.name ?: item.activity.type.label}",
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(MMSize.iconSmall + MM.space.s)
                                .clip(MM.radius.pill)
                                .background(MM.colors.accentWash),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.FitnessCenter,
                                contentDescription = null,
                                tint = MM.colors.accent,
                                modifier = Modifier.size(MMSize.iconSmall)
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MM.colors.inkSecondary
                        )
                    },
                    onClick = onStartRecall
                )
            }
            HorizontalDivider(
                color = MM.colors.lineSoft,
                modifier = Modifier.padding(vertical = MM.space.xs)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MM.space.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${due.size} reviews ready",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
                QuickSprintPill(
                    onClick = onStartSprint,
                    modifier = Modifier.testTag("quick_sprint_pill_due")
                )
            }
        }
    }
}

@Composable
private fun HighYieldWeakSection(
    weakConcepts: List<LearningConcept>,
    sessions: List<LearningSession> = emptyList(),
    onOpenSession: (noteId: String) -> Unit,
    onDrillWeakConcepts: (List<LearningConcept>) -> Unit = {}
) {
    SectionHeader(
        title = "High-Yield Concepts to Strengthen",
        count = if (weakConcepts.isNotEmpty()) weakConcepts.size else null,
        actionLabel = if (weakConcepts.isNotEmpty()) "Drill All" else null,
        onAction = if (weakConcepts.isNotEmpty()) {
            { onDrillWeakConcepts(weakConcepts) }
        } else null
    )
    if (weakConcepts.isEmpty()) {
        MMCard(contentPadding = MM.space.m) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = LearningFixedColors.MasteryStrong
                )
                Text(
                    text = "All high-yield concepts are in good shape! Keep up the momentum.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
            }
        }
        return
    }

    MMCard(contentPadding = MM.space.m) {
        weakConcepts.take(4).forEachIndexed { index, concept ->
            val targetSession = sessions.firstOrNull { it.id == concept.sessionId }
            val targetId = targetSession?.noteId ?: concept.sessionId
            if (index > 0) {
                HorizontalDivider(
                    color = MM.colors.lineSoft,
                    modifier = Modifier.padding(vertical = MM.space.xs)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MMSize.minTouch)
                    .clickable(role = Role.Button) { onOpenSession(targetId) }
                    .testTag("weak_concept_${concept.id}")
                    .padding(vertical = MM.space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = LearningFixedColors.MasteryNeedsReview,
                            modifier = Modifier.size(MMSize.iconSmall)
                        )
                        Text(
                            text = concept.name,
                            style = MM.type.bodyStrong,
                            color = MM.colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
                Spacer(Modifier.width(MM.space.s))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(MM.radius.small)
                            .background(LearningFixedColors.MasteryNeedsReviewBg)
                            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    ) {
                        Text(
                            text = concept.examImportance.label,
                            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = LearningFixedColors.MasteryNeedsReview
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(MM.radius.pill)
                            .background(MM.colors.accentWash)
                            .clickable { onDrillWeakConcepts(listOf(concept)) }
                            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    ) {
                        Text(
                            text = "Drill",
                            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = MM.colors.accent
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
    lectureNotes: List<LearningRepository.LectureNoteSummary>,
    actions: LearnHomeActions,
    onOpenSession: (noteId: String) -> Unit,
    onCreateCourseFromLecture: (LearningRepository.LectureNoteSummary) -> Unit
) {
    var selectedLectureFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var showAllLectures by remember { mutableStateOf(false) }

    // Merge lectureNotes and any session not yet in lectureNotes so every recorded lecture & session is shown
    val displayLectures = remember(lectureNotes, sessions) {
        val byNoteId = lectureNotes.associateBy { it.noteId }.toMutableMap()
        sessions.forEach { s ->
            if (s.noteId !in byNoteId) {
                byNoteId[s.noteId] = LearningRepository.LectureNoteSummary(
                    noteId = s.noteId,
                    sessionId = s.id,
                    meetingId = s.meetingId,
                    title = s.title,
                    summary = "",
                    notebookId = null,
                    courseName = s.courseName?.takeIf { !it.equals("Course", true) && !it.equals(s.title, true) },
                    domain = s.domain,
                    workflow = "LECTURE",
                    durationSeconds = 0L,
                    hasRecording = s.meetingId != null,
                    conceptCount = 0,
                    activityCount = 0,
                    strongCount = 0,
                    updatedAt = s.updatedAt
                )
            }
        }
        byNoteId.values.sortedByDescending { it.updatedAt }
    }

    val unassignedCount = remember(displayLectures) { displayLectures.count { !it.isAssignedToCourse } }
    val uniqueCourses = remember(displayLectures) {
        displayLectures.mapNotNull { it.courseName?.takeIf { c -> c.isNotBlank() } }.distinct().take(4)
    }

    val filteredLectures = remember(displayLectures, selectedLectureFilter, searchQuery) {
        val byCategory = when {
            selectedLectureFilter == "ALL" -> displayLectures
            selectedLectureFilter == "UNASSIGNED" -> displayLectures.filter { !it.isAssignedToCourse }
            selectedLectureFilter.startsWith("COURSE:") -> {
                val cName = selectedLectureFilter.removePrefix("COURSE:")
                displayLectures.filter { it.courseName.equals(cName, ignoreCase = true) }
            }
            else -> displayLectures
        }
        if (searchQuery.isBlank()) byCategory
        else {
            val q = searchQuery.trim().lowercase()
            byCategory.filter {
                it.title.lowercase().contains(q) ||
                    it.summary.lowercase().contains(q) ||
                    (it.courseName?.lowercase()?.contains(q) == true) ||
                    it.domain.displayName.lowercase().contains(q)
            }
        }
    }

    SectionHeader(
        title = "Recorded Lectures & Study Notes",
        count = if (displayLectures.isNotEmpty()) displayLectures.size else null
    )
    if (displayLectures.isEmpty()) {
        MMCard(contentPadding = MM.space.m) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Text(
                    text = "No lectures or study sessions yet. Record a class lecture or write a study note to begin.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    PrimaryButton(
                        text = "Record Lecture",
                        onClick = actions.onRecordLecture,
                        modifier = Modifier.weight(1f).testTag("empty_recent_record")
                    )
                    SecondaryButton(
                        text = "Write Note",
                        onClick = actions.onWriteStudyNote,
                        modifier = Modifier.weight(1f).testTag("empty_recent_write")
                    )
                }
            }
        }
    } else {
        MMCard(
            modifier = Modifier.animateContentSize(animationSpec = MM.motion.standard()),
            contentPadding = MM.space.m
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                if (displayLectures.size > 2 && (unassignedCount > 0 || uniqueCourses.isNotEmpty())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        val filterItems = buildList {
                            add("ALL" to "All (${displayLectures.size})")
                            if (unassignedCount > 0) add("UNASSIGNED" to "Unassigned ($unassignedCount)")
                            uniqueCourses.forEach { cName -> add("COURSE:$cName" to cName) }
                        }
                        filterItems.forEach { (key, label) ->
                            val isSel = selectedLectureFilter == key
                            val pillBg by animateColorAsState(
                                targetValue = if (isSel) MM.colors.accent else MM.colors.surfaceSunk,
                                animationSpec = MM.motion.quick(),
                                label = "lecture_filter_$key"
                            )
                            Box(
                                modifier = Modifier
                                    .clip(MM.radius.pill)
                                    .background(pillBg)
                                    .clickable { selectedLectureFilter = key }
                                    .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                            ) {
                                Text(
                                    text = label,
                                    style = MM.type.caption.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium),
                                    color = if (isSel) MM.colors.onAccent else MM.colors.inkSecondary
                                )
                            }
                        }
                    }
                }

                if (displayLectures.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search lectures by title, course or topic…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val visibleLectures = if (showAllLectures || searchQuery.isNotBlank()) filteredLectures else filteredLectures.take(6)

                visibleLectures.forEachIndexed { index, lecture ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MM.colors.lineSoft,
                            modifier = Modifier.padding(vertical = MM.space.xs)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onOpenSession(lecture.noteId) }
                            .padding(vertical = MM.space.xs),
                        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(MMSize.iconSmall + MM.space.s)
                                    .clip(MM.radius.pill)
                                    .background(
                                        if (lecture.hasRecording) LearningFixedColors.MasteryStrongBg
                                        else MM.colors.surfaceSunk
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (lecture.hasRecording) Icons.Rounded.GraphicEq else Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = if (lecture.hasRecording) LearningFixedColors.MasteryStrong else MM.colors.inkSecondary,
                                    modifier = Modifier.size(MMSize.iconSmall)
                                )
                            }
                            Spacer(Modifier.width(MM.space.m))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = lecture.title,
                                    style = MM.type.bodyStrong,
                                    color = MM.colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lecture.courseName ?: "Unassigned Lecture",
                                        style = MM.type.secondary,
                                        color = if (lecture.courseName != null) MM.colors.inkSecondary else MM.colors.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("·", style = MM.type.secondary, color = MM.colors.inkMuted)
                                    Text(
                                        text = lecture.domain.shortLabel,
                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                        color = MM.colors.accent
                                    )
                                    if (lecture.durationSeconds >= 60L) {
                                        Text("·", style = MM.type.secondary, color = MM.colors.inkMuted)
                                        Text(
                                            text = "${lecture.durationSeconds / 60L}m",
                                            style = MM.type.caption,
                                            color = MM.colors.inkSecondary
                                        )
                                    }
                                    if (lecture.conceptCount > 0) {
                                        Text("·", style = MM.type.secondary, color = MM.colors.inkMuted)
                                        Text(
                                            text = if (lecture.strongCount > 0) {
                                                "${lecture.strongCount}/${lecture.conceptCount} mastered"
                                            } else {
                                                "${lecture.conceptCount} concepts"
                                            },
                                            style = MM.type.caption,
                                            color = if (lecture.strongCount > 0) LearningFixedColors.MasteryStrong else MM.colors.inkSecondary
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(MM.space.xs))
                            if (!lecture.isAssignedToCourse) {
                                Box(
                                    modifier = Modifier
                                        .clip(MM.radius.pill)
                                        .background(MM.colors.accentWash)
                                        .clickable { onCreateCourseFromLecture(lecture) }
                                        .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                                        .testTag("create_course_from_note_${lecture.noteId}")
                                ) {
                                    Text(
                                        text = "+ Course",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                        color = MM.colors.accent
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(MM.radius.pill)
                                        .background(LearningFixedColors.MasteryStrongBg)
                                        .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                                ) {
                                    Text(
                                        text = "Active",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                        color = LearningFixedColors.MasteryStrong
                                    )
                                }
                            }
                            Spacer(Modifier.width(MM.space.xs))
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MM.colors.inkSecondary
                            )
                        }
                        if (lecture.summary.isNotBlank()) {
                            Text(
                                text = lecture.summary,
                                style = MM.type.caption,
                                color = MM.colors.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = MMSize.iconSmall + MM.space.s + MM.space.m)
                            )
                        }
                    }
                }

                if (filteredLectures.size > 6 && searchQuery.isBlank()) {
                    SecondaryButton(
                        text = if (showAllLectures) "Show Fewer Lectures" else "Show All ${filteredLectures.size} Lectures",
                        onClick = { showAllLectures = !showAllLectures },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CoursesSection(
    courses: List<LearningRepository.LearningCourse>,
    lectureNotes: List<LearningRepository.LectureNoteSummary>,
    dailyBrief: LearningRepository.DailyCompanionBrief?,
    onOpenCourse: (courseId: String) -> Unit,
    onPracticeCourse: (courseId: String) -> Unit,
    onOpenSession: (noteId: String) -> Unit,
    onNewCourse: () -> Unit,
    onCreateCourseFromLecture: (LearningRepository.LectureNoteSummary) -> Unit,
    onQuickCreateCourseFromLecture: (LearningRepository.LectureNoteSummary) -> Unit,
    onAssignNoteToCourse: (noteId: String, courseId: String) -> Unit
) {
    val unassignedLectures = remember(lectureNotes) {
        lectureNotes.filter { !it.isAssignedToCourse }
    }

    Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (courses.isNotEmpty()) "Your Courses · ${courses.size}" else "Your Courses",
                style = MM.type.heading,
                color = MM.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Row(
                modifier = Modifier
                    .heightIn(min = MMSize.minTouch)
                    .clickable(role = Role.Button, onClick = onNewCourse)
                    .testTag("learn_new_course_button")
                    .padding(horizontal = MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(MMSize.iconSmall), tint = MM.colors.accent)
                Spacer(Modifier.width(MM.space.xs))
                Text("New Course", style = MM.type.bodyStrong, color = MM.colors.accent)
            }
        }

        // 1-Tap "Create Course from Recorded Lectures" Spotlight
        if (unassignedLectures.isNotEmpty()) {
            MMCard(contentPadding = MM.space.m) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(MM.radius.pill)
                                .background(MM.colors.accentWash),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.CreateNewFolder,
                                contentDescription = null,
                                tint = MM.colors.accent,
                                modifier = Modifier.size(MMSize.iconSmall)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Create Course from Recorded Notes",
                                style = MM.type.bodyStrong,
                                color = MM.colors.ink
                            )
                            Text(
                                text = "Turn your recorded lessons into structured courses in one tap",
                                style = MM.type.caption,
                                color = MM.colors.inkSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = MM.colors.lineSoft)

                    unassignedLectures.take(4).forEach { lecture ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MM.radius.small)
                                .background(MM.colors.surfaceSunk)
                                .padding(horizontal = MM.space.m, vertical = MM.space.s),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenSession(lecture.noteId) }
                            ) {
                                Text(
                                    text = lecture.title,
                                    style = MM.type.bodyStrong,
                                    color = MM.colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${lecture.domain.displayName} · ${if (lecture.hasRecording) "Recorded Lesson" else "Study Note"}",
                                    style = MM.type.caption,
                                    color = MM.colors.inkSecondary
                                )
                            }
                            Spacer(Modifier.width(MM.space.s))
                            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                Box(
                                    modifier = Modifier
                                        .clip(MM.radius.pill)
                                        .background(MM.colors.accent)
                                        .clickable { onQuickCreateCourseFromLecture(lecture) }
                                        .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                                        .testTag("instant_course_from_${lecture.noteId}")
                                ) {
                                    Text(
                                        text = "1-Tap Course",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                        color = MM.colors.onAccent
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(MM.radius.pill)
                                        .background(MM.colors.surface)
                                        .clickable { onCreateCourseFromLecture(lecture) }
                                        .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                                ) {
                                    Text(
                                        text = "Customize",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                        color = MM.colors.ink
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (courses.isEmpty()) {
            MMCard(contentPadding = MM.space.l) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Box(
                        modifier = Modifier
                            .size(MMSize.minTouch)
                            .clip(MM.radius.pill)
                            .background(MM.colors.accentWash),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.School,
                            contentDescription = null,
                            tint = MM.colors.accent,
                            modifier = Modifier.size(MMSize.icon)
                        )
                    }
                    Text("Organize by Course", style = MM.type.bodyStrong, color = MM.colors.ink)
                    Text(
                        "Create courses for your subjects (Math, Engineering, Accounting, CS, Medicine, Law) or turn any recorded lecture into a course.",
                        style = MM.type.secondary,
                        color = MM.colors.inkSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(MM.space.xs))
                    SecondaryButton(text = "Create First Course", onClick = onNewCourse)
                }
            }
        } else {
            var expandedCourseId by remember { mutableStateOf<String?>(null) }
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                courses.forEach { course ->
                    val courseLectures = remember(lectureNotes, course.notebook.id, course.notebook.name, course.properties.courseCode) {
                        lectureNotes.filter { note ->
                            note.notebookId == course.notebook.id ||
                                note.courseName.equals(course.notebook.name, ignoreCase = true) ||
                                (course.properties.courseCode.isNotBlank() && note.courseName.equals(course.properties.courseCode, ignoreCase = true))
                        }
                    }
                    val isExpanded = expandedCourseId == course.notebook.id
                    val dueForCourse = maxOf(
                        course.dueReviewCount,
                        dailyBrief?.dueReviews?.count { it.session.courseName.equals(course.notebook.name, ignoreCase = true) } ?: 0
                    )
                    val progressFraction = remember(course.strongConceptCount, course.conceptCount, course.sessionCount, course.noteCount) {
                        when {
                            course.conceptCount > 0 -> (course.strongConceptCount.toFloat() / course.conceptCount).coerceIn(0.12f, 1f)
                            course.noteCount > 0 -> (course.sessionCount.toFloat() / (course.noteCount * 2).coerceAtLeast(1)).coerceIn(0.15f, 1f)
                            else -> 0.15f
                        }
                    }
                    val animatedProgress by animateFloatAsState(
                        targetValue = progressFraction,
                        animationSpec = MM.motion.standard(),
                        label = "course_mastery_progress"
                    )

                    MMCard(
                        modifier = Modifier
                            .animateContentSize(animationSpec = MM.motion.standard())
                            .clickable { onOpenCourse(course.notebook.id) },
                        contentPadding = MM.space.m
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = course.notebook.name,
                                        style = MM.type.title,
                                        color = MM.colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (course.properties.lecturer.isNotBlank()) {
                                        Text(
                                            text = course.properties.lecturer,
                                            style = MM.type.caption,
                                            color = MM.colors.inkSecondary
                                        )
                                    }
                                }
                                if (course.properties.courseCode.isNotBlank()) {
                                    Spacer(Modifier.width(MM.space.s))
                                    Box(
                                        modifier = Modifier
                                            .clip(MM.radius.small)
                                            .background(MM.colors.surfaceSunk)
                                            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                                    ) {
                                        Text(
                                            text = course.properties.courseCode,
                                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                            color = MM.colors.ink
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(MM.radius.pill)
                                        .background(MM.colors.accentWash)
                                        .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                                ) {
                                    Text(
                                        text = course.properties.domain.displayName,
                                        style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                        color = MM.colors.accent
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${maxOf(course.noteCount, courseLectures.size)} notes · ${course.sessionCount} sessions",
                                        style = MM.type.caption,
                                        color = MM.colors.inkSecondary
                                    )
                                    if (dueForCourse > 0) {
                                        Text(
                                            text = "·",
                                            style = MM.type.caption,
                                            color = MM.colors.inkMuted
                                        )
                                        Text(
                                            text = "$dueForCourse due",
                                            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                            color = LearningFixedColors.MasteryNeedsReview
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs),
                                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (course.conceptCount > 0) {
                                            "${course.strongConceptCount}/${course.conceptCount} concepts mastered"
                                        } else {
                                            "Course mastery"
                                        },
                                        style = MM.type.overline,
                                        color = MM.colors.inkSecondary
                                    )
                                    Text(
                                        text = "${(animatedProgress * 100).toInt()}%",
                                        style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                                        color = MM.colors.accent
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(MMSize.marker)
                                        .clip(MM.radius.pill)
                                        .background(MM.colors.track)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animatedProgress)
                                            .fillMaxHeight()
                                            .clip(MM.radius.pill)
                                            .background(MM.colors.accent)
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs),
                                    verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                                ) {
                                    HorizontalDivider(color = MM.colors.lineSoft)
                                    if (courseLectures.isEmpty()) {
                                        Text(
                                            text = "No lectures linked to this course yet.",
                                            style = MM.type.caption,
                                            color = MM.colors.inkSecondary
                                        )
                                    } else {
                                        courseLectures.forEach { lecture ->
                                            val subtitleParts = buildList {
                                                if (lecture.durationSeconds >= 60L) add("${lecture.durationSeconds / 60L}m")
                                                if (lecture.conceptCount > 0) {
                                                    if (lecture.strongCount > 0) {
                                                        add("${lecture.strongCount}/${lecture.conceptCount} mastered")
                                                    } else {
                                                        add("${lecture.conceptCount} concepts")
                                                    }
                                                }
                                                add("Lecture Studio")
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(MM.radius.small)
                                                    .background(MM.colors.surfaceSunk)
                                                    .clickable { onOpenSession(lecture.noteId) }
                                                    .padding(horizontal = MM.space.s, vertical = MM.space.xs),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = lecture.title,
                                                        style = MM.type.bodyStrong,
                                                        color = MM.colors.ink,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = subtitleParts.joinToString(" · "),
                                                        style = MM.type.caption,
                                                        color = MM.colors.inkSecondary
                                                    )
                                                }
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MM.colors.inkSecondary
                                                )
                                            }
                                        }
                                    }
                                    if (unassignedLectures.isNotEmpty()) {
                                        Text(
                                            text = "Tap to add an unassigned lecture to ${course.notebook.name}:",
                                            style = MM.type.overline,
                                            color = MM.colors.inkSecondary,
                                            modifier = Modifier.padding(top = MM.space.xs)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                                        ) {
                                            unassignedLectures.take(4).forEach { unassigned ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(MM.radius.pill)
                                                        .background(MM.colors.accentWash)
                                                        .clickable {
                                                            onAssignNoteToCourse(unassigned.noteId, course.notebook.id)
                                                        }
                                                        .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                                                ) {
                                                    Text(
                                                        text = "+ ${unassigned.title.take(22)}",
                                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MM.colors.accent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs),
                                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                            ) {
                                SecondaryButton(
                                    text = if (isExpanded) "Hide Lectures" else "View Lectures (${courseLectures.size})",
                                    onClick = {
                                        expandedCourseId = if (isExpanded) null else course.notebook.id
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                PrimaryButton(
                                    text = "Practice",
                                    onClick = { onPracticeCourse(course.notebook.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeModesSection(
    actions: LearnHomeActions,
    dueCount: Int = 0,
    lectureCount: Int = 0,
    onGenerateQuickDeck: (topic: String, domain: SubjectDomain) -> Unit = { _, _ -> actions.onOpenPractice() }
) {
    var quickTopic by remember { mutableStateOf("") }
    var selectedDomain by remember { mutableStateOf(SubjectDomain.MATH_QUANT) }

    SectionHeader(
        title = "Practice Arena",
        count = if (dueCount > 0) dueCount else null
    )
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        // 3-Min Sprint Quick Start Card
        MMCard(
            modifier = Modifier
                .clickable { actions.onStartSprint() }
                .testTag("practice_sprint_card"),
            contentPadding = MM.space.m
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(MMSize.minTouch)
                            .clip(MM.radius.pill)
                            .background(MM.colors.accentWash),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MM.colors.accent,
                            modifier = Modifier.size(MMSize.iconSmall)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "3-Min Quick Sprint",
                            style = MM.type.bodyStrong,
                            color = MM.colors.ink
                        )
                        Text(
                            text = "5 rapid recall cards · Jump in for 1 tap",
                            style = MM.type.caption,
                            color = MM.colors.inkSecondary
                        )
                    }
                }
                QuickSprintPill(
                    onClick = actions.onStartSprint,
                    modifier = Modifier.testTag("quick_sprint_pill_practice")
                )
            }
        }

        // 1. Flashcard Recall
        MMCard(
            modifier = Modifier.clickable { actions.onOpenPracticeMode(PracticeArenaFilter.FLASHCARDS) },
            contentPadding = MM.space.m
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                ListRow(
                    title = "Flashcard & Active Recall",
                    subtitle = "Spaced repetition cards with 3D flip, LaTeX formulas & 'Explain My Mistake'",
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(MMSize.minTouch)
                                .clip(MM.radius.pill)
                                .background(MM.colors.accentWash),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.FitnessCenter,
                                contentDescription = null,
                                tint = MM.colors.accent,
                                modifier = Modifier.size(MMSize.iconSmall)
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MM.colors.inkSecondary
                        )
                    },
                    onClick = { actions.onOpenPracticeMode(PracticeArenaFilter.FLASHCARDS) }
                )
                PrimaryButton(
                    text = "Start Flashcards",
                    onClick = { actions.onOpenPracticeMode(PracticeArenaFilter.FLASHCARDS) },
                    modifier = Modifier.fillMaxWidth().testTag("practice_start_flashcards")
                )
            }
        }

        // 2. Worked Problems & Multiple-Choice Quiz
        MMCard(
            modifier = Modifier.clickable { actions.onOpenPracticeMode(PracticeArenaFilter.PROBLEMS_QUIZ) },
            contentPadding = MM.space.m
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                ListRow(
                    title = "Quiz & Worked Problems",
                    subtitle = "Interactive multiple-choice & quantitative problem solving with step-by-step verification",
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(MMSize.minTouch)
                                .clip(MM.radius.pill)
                                .background(MM.colors.accentWash),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Quiz,
                                contentDescription = null,
                                tint = MM.colors.accent,
                                modifier = Modifier.size(MMSize.iconSmall)
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MM.colors.inkSecondary
                        )
                    },
                    onClick = { actions.onOpenPracticeMode(PracticeArenaFilter.PROBLEMS_QUIZ) }
                )
                SecondaryButton(
                    text = "Start Quiz & Problems",
                    onClick = { actions.onOpenPracticeMode(PracticeArenaFilter.PROBLEMS_QUIZ) },
                    modifier = Modifier.fillMaxWidth().testTag("practice_start_problems")
                )
            }
        }

        // 3. Voice Teach-Back Studio
        MMCard(
            modifier = Modifier.clickable { actions.onOpenPracticeMode(PracticeArenaFilter.TEACH_BACK) },
            contentPadding = MM.space.m
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                ListRow(
                    title = EntryPoints.learnTeachBack.label,
                    subtitle = "Explain a concept out loud to your companion and receive a qualitative rubric score",
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(MMSize.minTouch)
                                .clip(MM.radius.pill)
                                .background(MM.colors.accentWash),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = MM.colors.accent,
                                modifier = Modifier.size(MMSize.iconSmall)
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MM.colors.inkSecondary
                        )
                    },
                    onClick = { actions.onOpenPracticeMode(PracticeArenaFilter.TEACH_BACK) }
                )
                SecondaryButton(
                    text = "Start Voice Teach-Back",
                    onClick = { actions.onOpenPracticeMode(PracticeArenaFilter.TEACH_BACK) },
                    modifier = Modifier.fillMaxWidth().testTag("practice_start_teachback")
                )
            }
        }

        // 4. Instant Topic Deck Generator
        MMCard(contentPadding = MM.space.m) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Text(
                    text = "Instant Topic Deck Generator",
                    style = MM.type.bodyStrong,
                    color = MM.colors.ink
                )
                Text(
                    text = "Need flashcards on any topic right now? Enter a subject below to generate a practice deck.",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
                OutlinedTextField(
                    value = quickTopic,
                    onValueChange = { quickTopic = it },
                    label = { Text("Topic (e.g. Eigenvalues, Contract Law, Mitosis)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    SubjectDomain.entries.forEach { dom ->
                        val isSel = dom == selectedDomain
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(if (isSel) MM.colors.accent else MM.colors.surfaceSunk)
                                .clickable { selectedDomain = dom }
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                        ) {
                            Text(
                                text = dom.shortLabel,
                                style = MM.type.caption.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal),
                                color = if (isSel) MM.colors.onAccent else MM.colors.inkSecondary
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    listOf(
                        "Eigenvalues & Matrices" to SubjectDomain.MATH_QUANT,
                        "Cell Signalling & ATP" to SubjectDomain.SCIENCES_MED,
                        "Contract Law Remedies" to SubjectDomain.HUMANITIES_LAW,
                        "Financial Ratios & DCF" to SubjectDomain.ACCOUNTING_FINANCE
                    ).forEach { (presetTopic, presetDomain) ->
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(MM.colors.accentWash)
                                .clickable {
                                    quickTopic = presetTopic
                                    selectedDomain = presetDomain
                                }
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                        ) {
                            Text(
                                text = presetTopic,
                                style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                color = MM.colors.accent
                            )
                        }
                    }
                }
                PrimaryButton(
                    text = "Generate & Practice Now",
                    onClick = {
                        val topic = quickTopic.ifBlank { "${selectedDomain.displayName} Core Concepts" }
                        onGenerateQuickDeck(topic, selectedDomain)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ExamPrepOverviewSection(
    goals: List<com.craftflowtechnologies.meetingmind.core.model.LearningGoal>,
    priorities: List<com.craftflowtechnologies.meetingmind.core.model.ExamPriorityItem>,
    actions: LearnHomeActions
) {
    SectionHeader(
        title = "Exam Readiness Studio",
        actionLabel = "Open Studio",
        onAction = actions.onOpenExamPrep
    )

    if (goals.isEmpty()) {
        MMCard(contentPadding = MM.space.m) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Text("Set an Exam Goal", style = MM.type.bodyStrong, color = MM.colors.ink)
                Text(
                    "Add an upcoming exam to unlock countdowns, the High-Yield Priority Matrix, and 1-Page Cram Sheets.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
                Spacer(Modifier.height(MM.space.xs))
                PrimaryButton(text = "Set Exam Goal", onClick = actions.onOpenExamPrep)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
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
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                            Text(
                                text = goal.title,
                                style = MM.type.bodyStrong,
                                color = MM.colors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Format: ${goal.examFormat} · ${goal.dailyMinutesTarget} min/day",
                                style = MM.type.caption,
                                color = MM.colors.inkSecondary
                            )
                        }
                        Spacer(Modifier.width(MM.space.s))
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(MM.colors.accentWash)
                                .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                        ) {
                            Text(
                                "$daysRemaining days",
                                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                color = MM.colors.accent
                            )
                        }
                    }
                }
            }

            if (priorities.isNotEmpty()) {
                SectionHeader(title = "Top Exam Priorities", count = priorities.size)
                MMCard(contentPadding = MM.space.m) {
                    priorities.take(3).forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MM.colors.lineSoft,
                                modifier = Modifier.padding(vertical = MM.space.xs)
                            )
                        }
                        ListRow(
                            title = item.concept.name,
                            subtitle = "${item.sessionTitle} · ${item.concept.examImportance.label} (${item.mastery.state.label})",
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(MMSize.iconSmall + MM.space.s)
                                        .clip(MM.radius.pill)
                                        .background(MM.colors.accentWash),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Assignment,
                                        contentDescription = null,
                                        tint = MM.colors.accent,
                                        modifier = Modifier.size(MMSize.iconSmall)
                                    )
                                }
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
            } else {
                MMCard(contentPadding = MM.space.m) {
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        Text("No Exam Priorities Ranked Yet", style = MM.type.bodyStrong, color = MM.colors.ink)
                        Text(
                            "Record lectures or generate study notes to populate your high-yield priority matrix.",
                            style = MM.type.secondary,
                            color = MM.colors.inkSecondary
                        )
                        Spacer(Modifier.height(MM.space.xs))
                        SecondaryButton(text = "Record Lecture", onClick = actions.onRecordLecture)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCourseDialog(
    unassignedLectures: List<LearningRepository.LectureNoteSummary> = emptyList(),
    preselectedLecture: LearningRepository.LectureNoteSummary? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, code: String, domain: SubjectDomain, instructor: String, selectedNoteIds: List<String>) -> Unit
) {
    var courseName by remember(preselectedLecture) {
        mutableStateOf(preselectedLecture?.title.orEmpty())
    }
    var courseCode by remember(preselectedLecture) {
        mutableStateOf(
            if (preselectedLecture != null) "${preselectedLecture.domain.shortLabel.take(4).uppercase()} 101"
            else ""
        )
    }
    var instructor by remember { mutableStateOf("") }
    var selectedDomain by remember(preselectedLecture) {
        mutableStateOf(preselectedLecture?.domain ?: SubjectDomain.GENERAL)
    }
    var expandedDropdown by remember { mutableStateOf(false) }
    val selectedNoteIds = remember(preselectedLecture) {
        mutableStateListOf<String>().apply {
            if (preselectedLecture != null) add(preselectedLecture.noteId)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (preselectedLecture != null) "Create Course from Lecture" else "New Course",
                style = MM.type.title,
                color = MM.colors.ink
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course Name (e.g. Calculus II)") },
                    modifier = Modifier.fillMaxWidth().testTag("course_name_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = courseCode,
                    onValueChange = { courseCode = it },
                    label = { Text("Course Code (e.g. MATH 201)") },
                    modifier = Modifier.fillMaxWidth().testTag("course_code_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it },
                    label = { Text("Professor / Instructor (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
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

                if (unassignedLectures.isNotEmpty()) {
                    Spacer(Modifier.height(MM.space.xs))
                    Text(
                        text = "Include recorded lectures in this course:",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = MM.colors.inkSecondary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        unassignedLectures.take(4).forEach { lecture ->
                            val isChecked = lecture.noteId in selectedNoteIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MM.radius.small)
                                    .background(if (isChecked) MM.colors.accentWash else MM.colors.surfaceSunk)
                                    .clickable {
                                        if (isChecked) {
                                            selectedNoteIds.remove(lecture.noteId)
                                        } else {
                                            selectedNoteIds.add(lecture.noteId)
                                            if (courseName.isBlank()) courseName = lecture.title
                                            if (selectedDomain == SubjectDomain.GENERAL) selectedDomain = lecture.domain
                                            if (courseCode.isBlank()) {
                                                courseCode = "${lecture.domain.shortLabel.take(4).uppercase()} 101"
                                            }
                                        }
                                    }
                                    .padding(horizontal = MM.space.s, vertical = MM.space.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.width(MM.space.xs))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lecture.title,
                                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                        color = MM.colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = lecture.domain.shortLabel,
                                        style = MM.type.overline,
                                        color = MM.colors.inkSecondary
                                    )
                                }
                            }
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
                        onConfirm(
                            courseName.trim(),
                            courseCode.trim(),
                            selectedDomain,
                            instructor.trim(),
                            selectedNoteIds.toList()
                        )
                    }
                },
                enabled = courseName.isNotBlank(),
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

@Composable
private fun QuickSprintPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .clip(MM.radius.pill)
            .background(c.accent)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = MM.space.m, vertical = MM.space.xs)
            .testTag("quick_sprint_button"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                tint = c.onAccent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "3-Min Sprint",
                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                color = c.onAccent
            )
        }
    }
}

@Composable
private fun MasteryProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val trackColor = MM.colors.track
    val accentColor = MM.colors.accent
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = MM.motion.standard(),
        label = "mastery_ring_progress"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .testTag("learn_mastery_ring"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 4.dp.toPx()
            val inset = strokeWidthPx / 2f
            val arcSize = Size(
                width = size.width - strokeWidthPx,
                height = size.height - strokeWidthPx
            )
            val topLeft = Offset(inset, inset)

            // MM.colors.track background arc
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            )

            // MM.colors.accent foreground arc
            if (animatedProgress > 0f) {
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
            color = MM.colors.ink,
            textAlign = TextAlign.Center
        )
    }
}
