package com.craftflowtechnologies.meetingmind.feature.learning

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.WarningAmber
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
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM

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

    LaunchedEffect(noteId) {
        viewModel.learningRepo.observeSessionByNoteId(noteId).collect { session ->
            if (session != null) {
                viewModel.loadSessionDetail(session.id)
            }
        }
    }

    LearningSessionContent(
        sessionDetail = sessionDetail,
        onBack = onBack,
        onOpenEditor = { onOpenEditor(noteId) },
        onReadAloud = { viewModel.readSessionAloud(noteId) },
        onStartPractice = onStartPractice,
        onTogglePause = { sessionDetail?.session?.id?.let { viewModel.toggleSessionPause(it) } },
        onOpenAssistant = onOpenAssistant,
        modifier = modifier
    )
}

@Composable
fun LearningSessionContent(
    sessionDetail: LearningRepository.LearningSessionDetail?,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onReadAloud: () -> Unit,
    onStartPractice: (List<LearningActivity>) -> Unit,
    onTogglePause: () -> Unit,
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
        } else {
            // Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.l, vertical = MM.space.xs)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceSunk)
                    .padding(4.dp),
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) c.surface else c.surfaceSunk)
                            .clickable { activeTab = tab }
                            .padding(vertical = 8.dp)
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                when (activeTab) {
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = session.title,
                        style = MM.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = c.ink
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
                    text = "Read to Student",
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = concept.name,
                    style = MM.type.bodyStrong,
                    color = c.ink
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(importanceColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = concept.examImportance.label,
                        style = MM.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = importanceColor
                    )
                }
            }

            Text(
                text = concept.definition,
                style = MM.type.body,
                color = c.ink
            )

            concept.formulaLatex?.let { formula ->
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

@Composable
private fun ActivitySummaryCard(activity: LearningActivity) {
    val c = MM.colors

    MMCard(contentPadding = MM.space.m) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Text("Session Diagnostics", style = MM.type.title.copy(fontSize = 18.sp), color = c.ink)

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
