package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    val contextLine = remember(courses.size, dailyBrief) {
        val coursePart = when (val count = courses.size) {
            0 -> "No courses"
            1 -> "1 active course"
            else -> "$count active courses"
        }
        val dueCount = dailyBrief?.dueReviews?.size ?: 0
        val duePart = if (dueCount > 0) "$dueCount due today" else "all caught up"
        val exams = dailyBrief?.upcomingExams?.firstOrNull()
        val examPart = exams?.let { "${it.daysRemaining}d to ${it.goal.title}" }
        listOfNotNull(coursePart, duePart, examPart).joinToString(" · ")
    }

    HomeScaffold(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .testTag("learn_home"),
        header = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                ScreenHeader(
                    title = "Learn",
                    leading = {
                        IconButton(onClick = actions.onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = c.ink
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = actions.onAssistant,
                            modifier = Modifier.testTag("learn_assistant")
                        ) {
                            Icon(
                                AiMark,
                                contentDescription = "Ask the companion tutor",
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
                    hasSessions = sessions.isNotEmpty(),
                    hasCourses = courses.isNotEmpty(),
                    onStartRecall = actions.onStartDailyRecall,
                    onRecordLecture = actions.onRecordLecture
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
                            onStartRecall = actions.onStartDailyRecall
                        )
                    }
                    item(key = "high_yield") {
                        HighYieldWeakSection(
                            weakConcepts = dailyBrief?.weakHighYieldConcepts.orEmpty(),
                            sessions = sessions,
                            onOpenSession = actions.onOpenSession
                        )
                    }
                    item(key = "recent_sessions") {
                        RecentSessionsSection(
                            sessions = sessions.take(5),
                            actions = actions,
                            onOpenSession = actions.onOpenSession
                        )
                    }
                }
                LearnTab.COURSES -> {
                    item(key = "courses_list") {
                        CoursesSection(
                            courses = courses,
                            dailyBrief = dailyBrief,
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
private fun LearnTabSegmentedControl(
    activeTab: LearnTab,
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
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = if (isSel) MM.type.bodyStrong else MM.type.body,
                            color = if (isSel) c.ink else c.inkSecondary,
                            maxLines = 1
                        )
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
    onRecordLecture: () -> Unit
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
                subtitle = "Record a lecture or write study notes to build concepts and active recall.",
                ctaLabel = "Record First Lecture",
                ctaAction = onRecordLecture
            )
        }
        else -> {
            BriefHeroData(
                eyebrow = "TODAY'S STUDY BRIEF",
                title = "All caught up for today",
                subtitle = "Review ahead or explore practice modes in the arena.",
                ctaLabel = "Start Review",
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
                Text(heroData.eyebrow, style = MM.type.caption, color = MM.colors.accent)
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
            Companion(
                form = companionForm,
                state = companionState,
                size = CompanionHeroSize,
                modifier = Modifier.testTag("learn_companion_hero")
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = MM.space.l),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            PrimaryButton(
                text = heroData.ctaLabel,
                onClick = heroData.ctaAction
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
                SecondaryButton(
                    text = "Review Ahead",
                    onClick = onStartRecall,
                    modifier = Modifier.testTag("due_reviews_practice_ahead")
                )
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
                    title = item.activity.prompt,
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
        }
    }
}

@Composable
private fun HighYieldWeakSection(
    weakConcepts: List<LearningConcept>,
    sessions: List<LearningSession> = emptyList(),
    onOpenSession: (noteId: String) -> Unit
) {
    SectionHeader(
        title = "High-Yield Concepts to Strengthen",
        count = if (weakConcepts.isNotEmpty()) weakConcepts.size else null
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
        weakConcepts.take(3).forEachIndexed { index, concept ->
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
            }
        }
    }
}

@Composable
private fun RecentSessionsSection(
    sessions: List<LearningSession>,
    actions: LearnHomeActions,
    onOpenSession: (noteId: String) -> Unit
) {
    SectionHeader(
        title = "Recent Study Sessions",
        count = if (sessions.isNotEmpty()) sessions.size else null
    )
    if (sessions.isEmpty()) {
        MMCard(contentPadding = MM.space.m) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Text(
                    text = "No study sessions yet. Start by recording a lecture or writing a study note.",
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
        MMCard(contentPadding = MM.space.m) {
            sessions.forEachIndexed { index, session ->
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
                        .clickable(role = Role.Button) { onOpenSession(session.noteId) }
                        .padding(vertical = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(MMSize.iconSmall + MM.space.s)
                            .clip(MM.radius.pill)
                            .background(MM.colors.surfaceSunk),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.School,
                            contentDescription = null,
                            tint = MM.colors.inkSecondary,
                            modifier = Modifier.size(MMSize.iconSmall)
                        )
                    }
                    Spacer(Modifier.width(MM.space.m))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = session.title,
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
                                text = session.courseName ?: "General",
                                style = MM.type.secondary,
                                color = MM.colors.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "·",
                                style = MM.type.secondary,
                                color = MM.colors.inkMuted
                            )
                            Text(
                                text = session.domain.shortLabel,
                                style = MM.type.caption,
                                color = MM.colors.accent
                            )
                        }
                    }
                    Spacer(Modifier.width(MM.space.s))
                    if (session.unresolvedConfusionCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(LearningFixedColors.MasteryNeedsReviewBg)
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                        ) {
                            Text(
                                text = "${session.unresolvedConfusionCount} questions",
                                style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                color = LearningFixedColors.MasteryNeedsReview
                            )
                        }
                    } else if (session.status == "ACTIVE") {
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(LearningFixedColors.MasteryStrongBg)
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs)
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
            }
        }
    }
}

@Composable
private fun CoursesSection(
    courses: List<LearningRepository.LearningCourse>,
    dailyBrief: LearningRepository.DailyCompanionBrief?,
    onOpenCourse: (courseId: String) -> Unit,
    onNewCourse: () -> Unit
) {
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
                    "Create courses for your subjects (Math, Engineering, Accounting, CS, etc.) to organize lecture notes and exam prep.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(MM.space.xs))
                SecondaryButton(text = "Create First Course", onClick = onNewCourse)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            courses.forEach { course ->
                val dueForCourse = dailyBrief?.dueReviews?.count { it.session.courseName == course.notebook.name } ?: 0
                val progressFraction = remember(course.sessionCount, course.noteCount) {
                    if (course.noteCount == 0) 0.15f
                    else (course.sessionCount.toFloat() / (course.noteCount * 2).coerceAtLeast(1)).coerceIn(0.15f, 1f)
                }
                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction,
                    label = "course_mastery_progress"
                )

                MMCard(
                    modifier = Modifier.clickable { onOpenCourse(course.notebook.id) },
                    contentPadding = MM.space.m
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = course.notebook.name,
                                style = MM.type.title,
                                color = MM.colors.ink,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                                    text = "${course.noteCount} notes · ${course.sessionCount} sessions",
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
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeModesSection(actions: LearnHomeActions) {
    SectionHeader(title = "Practice Arena")
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        MMCard(
            modifier = Modifier.clickable(onClick = actions.onOpenPractice),
            contentPadding = MM.space.m
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                ListRow(
                    title = "Flashcard & Problem Recall",
                    subtitle = "Active retrieval with instant 'Explain My Mistake' feedback",
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
                    onClick = actions.onOpenPractice
                )
                PrimaryButton(
                    text = "Start Flashcards",
                    onClick = actions.onOpenPractice,
                    modifier = Modifier.fillMaxWidth().testTag("practice_start_flashcards")
                )
            }
        }

        MMCard(
            modifier = Modifier.clickable(onClick = actions.onOpenPractice),
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
                    onClick = actions.onOpenPractice
                )
                SecondaryButton(
                    text = "Start Voice Teach-Back",
                    onClick = actions.onOpenPractice,
                    modifier = Modifier.fillMaxWidth().testTag("practice_start_teachback")
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
    onDismiss: () -> Unit,
    onConfirm: (name: String, code: String, domain: SubjectDomain) -> Unit
) {
    var courseName by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var selectedDomain by remember { mutableStateOf(SubjectDomain.GENERAL) }
    var expandedDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Course", style = MM.type.title, color = MM.colors.ink) },
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
