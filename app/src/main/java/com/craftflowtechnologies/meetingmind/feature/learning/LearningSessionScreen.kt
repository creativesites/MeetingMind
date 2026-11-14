package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.feature.notes.editor.LatexMathParser
import com.craftflowtechnologies.meetingmind.feature.notes.editor.MathExpressionView
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

private val MMColors.surfaceMuted: Color get() = surfaceSunk
private val MMColors.card: Color get() = surface

enum class SessionTab { UNDERSTAND, CONCEPTS, PRACTICE, DIAGNOSTICS }

@Composable
fun LearningSessionScreen(
    noteId: String,
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    onOpenEditor: (noteId: String) -> Unit,
    onStartPractice: (activities: List<LearningActivity>) -> Unit,
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionDetail by viewModel.activeSessionDetail.collectAsState()
    var isLoadingSession by remember(noteId) { mutableStateOf(true) }
    var isCreatingSession by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(noteId) {
        isLoadingSession = true
        viewModel.learningRepo.observeSessionByNoteId(noteId).collect { session ->
            if (session != null) {
                viewModel.loadSessionDetail(session.id)
                isLoadingSession = false
            } else {
                // If not found by noteId, check if noteId is actually a sessionId
                val directDetail = viewModel.learningRepo.getSessionDetail(noteId)
                if (directDetail != null) {
                    viewModel.loadSessionDetail(noteId)
                }
                isLoadingSession = false
            }
        }
    }

    LearningSessionContent(
        sessionDetail = sessionDetail,
        isLoadingSession = isLoadingSession,
        isCreatingSession = isCreatingSession,
        onBack = onBack,
        onOpenEditor = { onOpenEditor(noteId) },
        onReadAloud = { viewModel.readSessionAloud(noteId) },
        onStartPractice = onStartPractice,
        onTogglePause = { sessionDetail?.session?.id?.let { viewModel.toggleSessionPause(it) } },
        onOpenAssistant = onOpenAssistant,
        onRetry = {
            scope.launch {
                isLoadingSession = true
                val direct = viewModel.learningRepo.getSessionDetail(noteId)
                if (direct != null) {
                    viewModel.loadSessionDetail(noteId)
                }
                isLoadingSession = false
            }
        },
        onCreateSession = {
            if (!isCreatingSession) {
                isCreatingSession = true
                scope.launch {
                    try {
                        val newSession = viewModel.learningRepo.ensureSessionForNote(
                            noteId = noteId,
                            title = "Study Session"
                        )
                        viewModel.loadSessionDetail(newSession.id)
                    } finally {
                        isCreatingSession = false
                    }
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun LearningSessionContent(
    sessionDetail: LearningRepository.LearningSessionDetail?,
    isLoadingSession: Boolean = false,
    isCreatingSession: Boolean = false,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onReadAloud: () -> Unit,
    onStartPractice: (List<LearningActivity>) -> Unit,
    onTogglePause: () -> Unit,
    onOpenAssistant: () -> Unit,
    onRetry: () -> Unit = {},
    onCreateSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    var activeTab by remember { mutableStateOf(SessionTab.UNDERSTAND) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .testTag("learning_session_screen")
    ) {
        ScreenHeader(
            title = sessionDetail?.session?.title ?: "Study Session",
            leading = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("session_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink)
                }
            },
            actions = {
                IconButton(onClick = onOpenAssistant, modifier = Modifier.testTag("session_assistant_button")) {
                    Icon(AiMark, contentDescription = "Ask Socratic Tutor", tint = c.ink)
                }
            }
        )

        if (sessionDetail == null) {
            if (isLoadingSession) {
                LearningSessionSkeleton()
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(MM.space.xl),
                    contentAlignment = Alignment.Center
                ) {
                    MMCard(contentPadding = MM.space.l) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MM.space.m)
                        ) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = LearningFixedColors.MasteryNeedsReview,
                                modifier = Modifier.size(MM.space.xxl + MM.space.s)
                            )
                            Text(
                                text = "No Study Session Yet",
                                style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                                color = c.ink
                            )
                            Text(
                                text = "No active study session was found for this note. You can create a new session or return to Learn.",
                                style = MM.type.secondary,
                                color = c.inkSecondary,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                            ) {
                                SecondaryButton(
                                    text = "Go Back",
                                    onClick = onBack,
                                    modifier = Modifier.weight(1f).testTag("session_unavailable_back")
                                )
                                Button(
                                    onClick = onCreateSession,
                                    enabled = !isCreatingSession,
                                    shape = MM.radius.pill,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = MMSize.minTouch)
                                        .testTag("session_unavailable_create"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = c.accent,
                                        contentColor = c.onAccent,
                                        disabledContainerColor = c.track,
                                        disabledContentColor = c.inkMuted
                                    ),
                                    contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s)
                                ) {
                                    if (isCreatingSession) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(MM.space.l),
                                            strokeWidth = MMSize.hairline * 2,
                                            color = c.onAccent
                                        )
                                        Spacer(Modifier.width(MM.space.s))
                                        Text("Creating…", style = MM.type.bodyStrong, color = c.onAccent)
                                    } else {
                                        Text("Create Session", style = MM.type.bodyStrong)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.l, vertical = MM.space.xs)
                    .clip(MM.radius.card)
                    .background(c.surfaceSunk)
                    .padding(MM.space.xs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SessionTab.entries.forEach { tab ->
                    val isSelected = tab == activeTab
                    val label = when (tab) {
                        SessionTab.UNDERSTAND -> "Understand"
                        SessionTab.CONCEPTS -> "Concepts"
                        SessionTab.PRACTICE -> "Practice"
                        SessionTab.DIAGNOSTICS -> "Diagnostics"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = MMSize.minTouch)
                            .clip(MM.radius.small)
                            .background(if (isSelected) c.surface else c.surfaceSunk)
                            .semantics {
                                role = Role.Tab
                                selected = isSelected
                            }
                            .clickable(role = Role.Tab) {
                                if (activeTab != tab) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activeTab = tab
                                }
                            }
                            .padding(vertical = MM.space.s)
                            .testTag("session_tab_${tab.name.lowercase()}"),
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

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(250)) { it / 6 * dir } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(180)))
                },
                label = "session_tab",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(MM.space.l),
                    verticalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    when (tab) {
                        SessionTab.UNDERSTAND -> {
                            item(key = "understand_header") {
                                UnderstandTabHeader(
                                    session = sessionDetail.session,
                                    onOpenEditor = onOpenEditor,
                                    onReadAloud = onReadAloud
                                )
                            }
                        }
                        SessionTab.CONCEPTS -> {
                            if (sessionDetail.concepts.isEmpty()) {
                                item(key = "empty_concepts") {
                                    MMCard(contentPadding = MM.space.m) {
                                        Text("No concepts extracted yet for this session.", style = MM.type.secondary, color = c.inkSecondary)
                                    }
                                }
                            } else {
                                items(sessionDetail.concepts, key = { it.id }) { concept ->
                                    ConceptCard(concept = concept)
                                }
                            }
                        }
                        SessionTab.PRACTICE -> {
                            item(key = "practice_header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${sessionDetail.activities.size} Activities Available", style = MM.type.bodyStrong, color = c.ink)
                                    if (sessionDetail.activities.isNotEmpty()) {
                                        PrimaryButton(
                                            text = "Start Practice",
                                            onClick = { onStartPractice(sessionDetail.activities) },
                                            modifier = Modifier.testTag("start_session_practice")
                                        )
                                    }
                                }
                            }

                            items(sessionDetail.activities, key = { it.id }) { activity ->
                                ActivitySummaryCard(activity = activity)
                            }
                        }
                        SessionTab.DIAGNOSTICS -> {
                            item(key = "diagnostics") {
                                DiagnosticsCard(
                                    session = sessionDetail.session,
                                    onTogglePause = onTogglePause
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
private fun LearningSessionSkeleton(modifier: Modifier = Modifier) {
    val c = MM.colors
    val transition = rememberInfiniteTransition(label = "learning_session_skeleton_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MM.space.l)
            .testTag("learning_session_skeleton"),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        // Session header skeleton (card block)
        MMCard(contentPadding = MM.space.l) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.65f)
                            .height(MM.space.l)
                            .clip(MM.radius.small)
                            .background(c.surfaceMuted.copy(alpha = alpha))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(MM.space.m)
                            .clip(MM.radius.small)
                            .background(c.surfaceMuted.copy(alpha = alpha))
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MMSize.hairline)
                        .background(c.line)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(MMSize.minTouch)
                            .clip(MM.radius.pill)
                            .background(c.surfaceMuted.copy(alpha = alpha))
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(MMSize.minTouch)
                            .clip(MM.radius.pill)
                            .background(c.surfaceMuted.copy(alpha = alpha))
                    )
                }
            }
        }

        // Tab strip skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MM.radius.card)
                .background(c.surfaceSunk)
                .padding(MM.space.xs),
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(MM.space.xl + MM.space.xs)
                        .clip(MM.radius.small)
                        .background(c.card.copy(alpha = alpha))
                )
            }
        }

        // 2 concept card blocks
        repeat(2) {
            MMCard(contentPadding = MM.space.m) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.5f)
                                .height(MM.space.m)
                                .clip(MM.radius.small)
                                .background(c.surfaceMuted.copy(alpha = alpha))
                        )
                        Box(
                            Modifier
                                .width(MM.space.xxl + MM.space.m)
                                .height(MM.space.m)
                                .clip(MM.radius.small)
                                .background(c.surfaceMuted.copy(alpha = alpha))
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(MM.space.m)
                            .clip(MM.radius.small)
                            .background(c.surfaceMuted.copy(alpha = alpha))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.75f)
                            .height(MM.space.m)
                            .clip(MM.radius.small)
                            .background(c.surfaceMuted.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun UnderstandTabHeader(
    session: LearningSession,
    onOpenEditor: () -> Unit,
    onReadAloud: () -> Unit
) {
    val c = MM.colors

    MMCard(contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs / 2)
                ) {
                    Text(
                        text = session.title,
                        style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                        color = c.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.courseName ?: "Course"} · ${session.subjectDomain}",
                        style = MM.type.caption,
                        color = c.inkSecondary
                    )
                }
            }

            HorizontalDivider(color = c.line)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                SecondaryButton(
                    text = "Open Editor",
                    onClick = onOpenEditor,
                    modifier = Modifier.weight(1f).testTag("session_open_editor")
                )
                PrimaryButton(
                    text = "Read Aloud",
                    onClick = onReadAloud,
                    modifier = Modifier.weight(1f).testTag("session_read_aloud")
                )
            }
        }
    }
}

@Composable
private fun ConceptCard(concept: LearningConcept) {
    val c = MM.colors

    val importanceColor = when (concept.examImportance) {
        ExamImportance.LECTURER_FLAGGED -> LearningFixedColors.MasteryNeedsReview
        ExamImportance.HIGH_YIELD -> LearningFixedColors.MasteryDeveloping
        ExamImportance.NORMAL -> c.inkSecondary
    }

    MMCard(contentPadding = MM.space.m) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = concept.name,
                    style = MM.type.bodyStrong,
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(MM.space.s))
                Box(
                    modifier = Modifier
                        .clip(MM.radius.small)
                        .background(importanceColor.copy(alpha = 0.15f))
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                ) {
                    Text(
                        text = concept.examImportance.label,
                        style = MM.type.overline,
                        color = importanceColor
                    )
                }
            }

            Text(
                text = concept.definition,
                style = MM.type.body,
                color = c.ink
            )

            concept.formulaLatex?.takeIf { it.isNotBlank() }?.let { formula ->
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

@Composable
private fun ActivitySummaryCard(activity: LearningActivity) {
    val c = MM.colors

    MMCard(contentPadding = MM.space.m) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activity.type.name.replace("_", " "),
                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = c.accent
                )
                Text(
                    text = activity.difficulty,
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
            Text(
                text = activity.prompt,
                style = MM.type.bodyStrong,
                color = c.ink
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    session: LearningSession,
    onTogglePause: () -> Unit
) {
    val c = MM.colors

    MMCard(contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Text("Session Diagnostics", style = MM.type.heading.copy(fontWeight = FontWeight.Bold), color = c.ink)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Exam Signals Detected", style = MM.type.secondary, color = c.ink)
                Text("${session.examSignalCount}", style = MM.type.bodyStrong, color = c.accent)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unresolved Confusion Points", style = MM.type.secondary, color = c.ink)
                Text(
                    "${session.unresolvedConfusionCount}",
                    style = MM.type.bodyStrong,
                    color = if (session.unresolvedConfusionCount > 0) LearningFixedColors.MasteryNeedsReview else c.inkSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session Status", style = MM.type.secondary, color = c.ink)
                Text(
                    session.status,
                    style = MM.type.bodyStrong,
                    color = if (session.status == "ACTIVE") LearningFixedColors.MasteryStrong else LearningFixedColors.MasteryDeveloping
                )
            }

            HorizontalDivider(color = c.line)

            SecondaryButton(
                text = if (session.status == "ACTIVE") "Pause Spaced Reviews" else "Resume Spaced Reviews",
                onClick = onTogglePause,
                modifier = Modifier.fillMaxWidth().testTag("toggle_session_pause")
            )
        }
    }
}
