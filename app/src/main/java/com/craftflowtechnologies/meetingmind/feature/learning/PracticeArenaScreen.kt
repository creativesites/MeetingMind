package com.craftflowtechnologies.meetingmind.feature.learning

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.craftflowtechnologies.meetingmind.ai.learning.MistakeExplanation
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackCoverage
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackEvaluation
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import androidx.compose.material3.HorizontalDivider
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackStatus
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

@Composable
fun PracticeArenaScreen(
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    sessionId: String? = null,
    activityId: String? = null,
    modifier: Modifier = Modifier
) {
    val activities by viewModel.practiceActivities.collectAsState()
    val currentIndex by viewModel.currentActivityIndex.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()
    val mistakeExplanation by viewModel.mistakeExplanation.collectAsState()
    val teachBackEvaluation by viewModel.teachBackEvaluation.collectAsState()
    val isEvaluating by viewModel.isEvaluating.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val activeFilter by viewModel.practiceFilter.collectAsState()
    val isLoadingPractice by viewModel.isLoadingPractice.collectAsState()
    val practiceStats by viewModel.practiceStats.collectAsState()
    val hintLadder by viewModel.hintLadder.collectAsState()
    val revealedHintCount by viewModel.revealedHintCount.collectAsState()
    val isLoadingHints by viewModel.isLoadingHints.collectAsState()
    val studyTimerSecondsLeft by viewModel.studyTimerSecondsLeft.collectAsState()
    val isStudyTimerRunning by viewModel.isStudyTimerRunning.collectAsState()
    var isCompleted by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(sessionId, activityId) {
        viewModel.ensurePracticeLoaded(sessionId, activityId)
    }

    PracticeArenaContent(
        activities = activities,
        currentIndex = currentIndex,
        isFlipped = isFlipped,
        isCompleted = isCompleted,
        mistakeExplanation = mistakeExplanation,
        teachBackEvaluation = teachBackEvaluation,
        isEvaluating = isEvaluating,
        isRecordingVoice = isRecordingVoice,
        micLevel = micLevel,
        hintLadder = hintLadder,
        revealedHintCount = revealedHintCount,
        isLoadingHints = isLoadingHints,
        studyTimerSecondsLeft = studyTimerSecondsLeft,
        isStudyTimerRunning = isStudyTimerRunning,
        onToggleStudyTimer = { viewModel.toggleStudyTimer() },
        onResetStudyTimer = { viewModel.resetStudyTimer() },
        onRequestNextHint = { activity -> viewModel.requestNextHint(activity) },
        onFlip = { viewModel.flipCard() },
        onRate = { rating ->
            val isCorrect = rating == RecallRating.GOOD || rating == RecallRating.EASY
            viewModel.recordRecallAttempt(
                userResponse = if (isCorrect) "Correct self-assessment" else "Needs review",
                isCorrect = isCorrect,
                rating = rating
            )
        },
        onNext = { viewModel.nextCard() },
        onFinish = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            isCompleted = true
        },
        onToggleVoice = {
            viewModel.setVoiceRecording(!isRecordingVoice)
        },
        onSubmitTeachBackText = { activity, text ->
            viewModel.evaluateTeachBack(activity, text)
        },
        onRequestMistakeExplanation = { activity, answer ->
            viewModel.explainMistake(activity, answer)
        },
        onBack = onBack,
        activeFilter = activeFilter,
        isLoadingPractice = isLoadingPractice,
        practiceStats = practiceStats,
        onSelectFilter = { filter ->
            isCompleted = false
            viewModel.loadPracticeByFilter(filter = filter)
        },
        onRestartDeck = {
            isCompleted = false
            viewModel.restartPracticeDeck()
        },
        onGenerateQuickTopicDeck = { topic, domain ->
            isCompleted = false
            viewModel.generateQuickTopicDeck(topic = topic, domain = domain)
        },
        modifier = modifier
    )
}

@Composable
fun PracticeArenaContent(
    activities: List<LearningActivity>,
    currentIndex: Int,
    isFlipped: Boolean,
    isCompleted: Boolean = false,
    mistakeExplanation: MistakeExplanation?,
    teachBackEvaluation: TeachBackEvaluation?,
    isEvaluating: Boolean,
    isRecordingVoice: Boolean,
    micLevel: Float,
    hintLadder: List<String> = emptyList(),
    revealedHintCount: Int = 0,
    isLoadingHints: Boolean = false,
    studyTimerSecondsLeft: Int = 25 * 60,
    isStudyTimerRunning: Boolean = false,
    onToggleStudyTimer: () -> Unit = {},
    onResetStudyTimer: () -> Unit = {},
    onRequestNextHint: (LearningActivity) -> Unit = {},
    onFlip: () -> Unit,
    onRate: (RecallRating) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit = {},
    onToggleVoice: () -> Unit,
    onSubmitTeachBackText: (LearningActivity, String) -> Unit,
    onRequestMistakeExplanation: (LearningActivity, String) -> Unit,
    onBack: () -> Unit,
    activeFilter: PracticeArenaFilter = PracticeArenaFilter.ALL,
    isLoadingPractice: Boolean = false,
    practiceStats: PracticeSessionStats = PracticeSessionStats(),
    onSelectFilter: (PracticeArenaFilter) -> Unit = {},
    onRestartDeck: () -> Unit = {},
    onGenerateQuickTopicDeck: (topic: String, domain: SubjectDomain) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val companionForm = rememberCompanionSettings().value.form ?: CompanionForm.ZURI

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .testTag("practice_arena_screen")
    ) {
        ScreenHeader(
            title = "Practice Arena",
            leading = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("practice_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink)
                }
            },
            actions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    modifier = Modifier.padding(end = MM.space.m)
                ) {
                    StudyTimerPill(
                        secondsLeft = studyTimerSecondsLeft,
                        isRunning = isStudyTimerRunning,
                        onToggle = onToggleStudyTimer,
                        onReset = onResetStudyTimer
                    )

                    if (practiceStats.currentStreak > 0) {
                        Row(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(LearningFixedColors.MasteryDeveloping.copy(alpha = 0.16f))
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs / 2)
                        ) {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = LearningFixedColors.MasteryDeveloping,
                                modifier = Modifier.size(MM.space.m)
                            )
                            Text(
                                text = "${practiceStats.currentStreak}",
                                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                color = LearningFixedColors.MasteryDeveloping
                            )
                        }
                    }
                    if (activities.isNotEmpty()) {
                        Text(
                            "${(currentIndex + 1).coerceAtMost(activities.size)} / ${activities.size}",
                            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = c.inkSecondary
                        )
                    }
                }
            }
        )

        // Mode Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l, vertical = MM.space.xs),
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            PracticeArenaFilter.entries.forEach { filter ->
                val isSelected = activeFilter == filter
                Box(
                    modifier = Modifier
                        .clip(MM.radius.pill)
                        .background(if (isSelected) c.accent else c.surfaceSunk)
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                        .testTag("practice_filter_${filter.name.lowercase()}")
                ) {
                    Text(
                        text = filter.label,
                        style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                        color = if (isSelected) c.onAccent else c.inkSecondary
                    )
                }
            }
        }

        if (activities.isNotEmpty() && !isCompleted && currentIndex < activities.size) {
            val progress = ((currentIndex + 1).toFloat() / activities.size.coerceAtLeast(1)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.l, vertical = MM.space.xs / 2)
                    .height(4.dp)
                    .clip(MM.radius.pill),
                color = c.accent,
                trackColor = c.surfaceSunk
            )
        }

        if (isLoadingPractice && activities.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    CircularProgressIndicator(color = c.accent)
                    Text(
                        "Preparing your practice deck…",
                        style = MM.type.secondary,
                        color = c.inkSecondary
                    )
                }
            }
        } else if (activities.isEmpty()) {
            EmptyPracticeScreen(
                companionForm = companionForm,
                onGenerateQuickDeck = onGenerateQuickTopicDeck,
                onBack = onBack
            )
        } else if (isCompleted || currentIndex >= activities.size) {
            AllDoneScreen(
                companionForm = companionForm,
                stats = practiceStats,
                totalCards = activities.size,
                onPracticeAgain = onRestartDeck,
                onBack = onBack
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MM.space.l, vertical = MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                AnimatedContent(
                    targetState = currentIndex,
                    label = "practice_card"
                ) { targetIndex ->
                    val activityForIndex = activities.getOrNull(targetIndex) ?: return@AnimatedContent
                    val isTeachBack = activityForIndex.type == LearningActivityType.TEACH_BACK
                    val isInteractiveQuiz = activityForIndex.options.isNotEmpty() &&
                        activityForIndex.type in setOf(
                            LearningActivityType.MULTIPLE_CHOICE,
                            LearningActivityType.WORKED_PROBLEM,
                            LearningActivityType.APPLICATION
                        )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MM.space.m)
                    ) {
                        when {
                            isTeachBack -> {
                                TeachBackStudioCard(
                                    activity = activityForIndex,
                                    companionForm = companionForm,
                                    isRecording = isRecordingVoice,
                                    micLevel = micLevel,
                                    isEvaluating = isEvaluating,
                                    evaluation = teachBackEvaluation,
                                    onToggleVoice = onToggleVoice,
                                    onSubmitText = { onSubmitTeachBackText(activityForIndex, it) }
                                )
                            }
                            isInteractiveQuiz -> {
                                InteractiveProblemCard(
                                    activity = activityForIndex,
                                    onRate = onRate,
                                    onRequestMistakeExplanation = onRequestMistakeExplanation,
                                    revealedHintCount = revealedHintCount,
                                    hintLadder = hintLadder,
                                    isLoadingHints = isLoadingHints,
                                    onRequestNextHint = onRequestNextHint
                                )

                                AnimatedVisibility(visible = mistakeExplanation != null) {
                                    mistakeExplanation?.let {
                                        MistakeExplanationCard(explanation = it)
                                    }
                                }
                            }
                            else -> {
                                RecallFlashcard(
                                    activity = activityForIndex,
                                    isFlipped = isFlipped,
                                    onFlip = onFlip,
                                    onRate = onRate,
                                    onRequestMistakeExplanation = onRequestMistakeExplanation,
                                    revealedHintCount = revealedHintCount,
                                    hintLadder = hintLadder,
                                    isLoadingHints = isLoadingHints,
                                    onRequestNextHint = onRequestNextHint
                                )

                                AnimatedVisibility(visible = mistakeExplanation != null) {
                                    mistakeExplanation?.let {
                                        MistakeExplanationCard(explanation = it)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MM.space.l),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(
                    text = "Back to Study",
                    onClick = onBack,
                    modifier = Modifier.testTag("practice_exit_button")
                )
                if (currentIndex + 1 < activities.size) {
                    PrimaryButton(
                        text = "Next Card",
                        onClick = onNext,
                        modifier = Modifier.testTag("practice_next_button")
                    )
                } else {
                    PrimaryButton(
                        text = "Finish Practice",
                        onClick = onFinish,
                        modifier = Modifier.testTag("practice_finish_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun RecallFlashcard(
    activity: LearningActivity,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onRate: (RecallRating) -> Unit,
    onRequestMistakeExplanation: (LearningActivity, String) -> Unit,
    revealedHintCount: Int = 0,
    hintLadder: List<String> = emptyList(),
    isLoadingHints: Boolean = false,
    onRequestNextHint: (LearningActivity) -> Unit = {}
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    var studentAnswer by remember(activity.id) { mutableStateOf("") }
    var lastRating by remember(activity.id) { mutableStateOf<RecallRating?>(null) }
    var lastRatingTapMs by remember(activity.id) { mutableStateOf(0L) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = MM.motion.standard(),
        label = "card_flip_rotation"
    )

    MMCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .semantics { stateDescription = if (isFlipped) "Answer revealed" else "Question side" }
            .clickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onFlip()
            })
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .testTag("flashcard_container"),
        contentPadding = MM.space.l
    ) {
        if (rotation <= 90f) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MM.space.m),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(MM.radius.small)
                        .background(c.surfaceSunk)
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                ) {
                    Text(
                        text = activity.type.name.replace("_", " "),
                        style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                        color = c.accent
                    )
                }

                Text(
                    text = activity.prompt,
                    style = MM.type.heading.copy(fontWeight = FontWeight.Medium),
                    color = c.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = MM.space.m)
                )

                // 3-Tier Socratic Hint Ladder
                SocraticHintLadder(
                    activity = activity,
                    revealedHintCount = revealedHintCount,
                    hintLadder = hintLadder,
                    isLoadingHints = isLoadingHints,
                    onRequestNextHint = onRequestNextHint
                )

                // Optional answer input field before/after flipping
                OutlinedTextField(
                    value = studentAnswer,
                    onValueChange = { studentAnswer = it },
                    label = { Text("Your answer / thought (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("student_recall_attempt"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                Text(
                    text = "Tap to reveal answer",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationY = 180f },
                verticalArrangement = Arrangement.spacedBy(MM.space.m),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(MM.radius.small)
                        .background(c.surfaceSunk)
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                ) {
                    Text(
                        text = activity.type.name.replace("_", " "),
                        style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                        color = c.accent
                    )
                }

                Text(
                    text = activity.prompt,
                    style = MM.type.secondary,
                    color = c.inkSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                HorizontalDivider(color = c.line)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Model Answer",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = c.inkSecondary
                    )
                    Text(
                        text = activity.expectedAnswer,
                        style = MM.type.bodyStrong,
                        color = c.ink,
                        textAlign = TextAlign.Center
                    )
                }

                // 4-tier recall grading buttons with debounce and SM-2 interval hints
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MM.space.m),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    RecallButton(
                        label = "Again",
                        intervalHint = "<10m",
                        color = LearningFixedColors.MasteryNeedsReview,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastRatingTapMs >= 500L) {
                                lastRatingTapMs = now
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastRating = RecallRating.AGAIN
                                onRate(RecallRating.AGAIN)
                                val answer = studentAnswer.ifBlank { "Incorrect attempt on: ${activity.prompt}" }
                                onRequestMistakeExplanation(activity, answer)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("rate_again")
                    )
                    RecallButton(
                        label = "Hard",
                        intervalHint = "1d",
                        color = LearningFixedColors.MasteryDeveloping,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastRatingTapMs >= 500L) {
                                lastRatingTapMs = now
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastRating = RecallRating.HARD
                                onRate(RecallRating.HARD)
                                val answer = studentAnswer.ifBlank { "Unsure/hard attempt on: ${activity.prompt}" }
                                onRequestMistakeExplanation(activity, answer)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("rate_hard")
                    )
                    RecallButton(
                        label = "Good",
                        intervalHint = "3d",
                        color = c.accent,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastRatingTapMs >= 500L) {
                                lastRatingTapMs = now
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastRating = RecallRating.GOOD
                                onRate(RecallRating.GOOD)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("rate_good")
                    )
                    RecallButton(
                        label = "Easy",
                        intervalHint = "7d",
                        color = LearningFixedColors.MasteryStrong,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastRatingTapMs >= 500L) {
                                lastRatingTapMs = now
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastRating = RecallRating.EASY
                                onRate(RecallRating.EASY)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("rate_easy")
                    )
                }

                if (lastRating == RecallRating.AGAIN || lastRating == RecallRating.HARD) {
                    Spacer(Modifier.height(MM.space.xs))
                    SecondaryButton(
                        text = "Explain My Mistake",
                        onClick = {
                            val answer = studentAnswer.ifBlank { "Needs explanation for: ${activity.prompt}" }
                            onRequestMistakeExplanation(activity, answer)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("explain_mistake_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun RecallButton(
    label: String,
    intervalHint: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MM.radius.small)
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .heightIn(min = MMSize.minTouch)
            .padding(vertical = MM.space.xs, horizontal = MM.space.xs),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = intervalHint,
                style = MM.type.overline.copy(fontWeight = FontWeight.Normal),
                color = color.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun TeachBackStudioCard(
    activity: LearningActivity,
    companionForm: CompanionForm,
    isRecording: Boolean,
    micLevel: Float,
    isEvaluating: Boolean,
    evaluation: TeachBackEvaluation?,
    onToggleVoice: () -> Unit,
    onSubmitText: (String) -> Unit
) {
    val c = MM.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var textInput by remember(activity.id) { mutableStateOf("") }
    var speechNotice by remember(activity.id) { mutableStateOf<String?>(null) }

    val isSpeechAvailable = remember {
        SpeechRecognizer.isRecognitionAvailable(context)
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechNotice = null
            onToggleVoice()
        } else {
            speechNotice = "Microphone permission is required for voice teach-back."
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            if (!isSpeechAvailable || speechRecognizer == null) {
                speechNotice = "Speech recognition is not available on this device. Please type your explanation."
                onToggleVoice()
                return@LaunchedEffect
            }
            speechNotice = null
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    speechNotice = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice teach-back."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Couldn't hear clearly — try again or type your explanation."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network issue encountered during speech recognition."
                        else -> "Couldn't recognize speech — try again or type your explanation."
                    }
                    if (isRecording) {
                        onToggleVoice()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        textInput = if (textInput.isBlank()) spoken else "$textInput $spoken"
                    }
                    if (isRecording) {
                        onToggleVoice()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()
                    if (!partial.isNullOrBlank() && textInput.isBlank()) {
                        textInput = partial
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer.startListening(intent)
        } else {
            speechRecognizer?.stopListening()
        }
    }

    val onMicButtonClick = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!isSpeechAvailable || speechRecognizer == null) {
            speechNotice = "Speech recognition is not available on this device. Please type your explanation."
        } else if (isRecording) {
            onToggleVoice()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                speechNotice = null
                onToggleVoice()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    MMCard(contentPadding = MM.space.l) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MM.space.m),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(MM.radius.small)
                    .background(c.accent.copy(alpha = 0.1f))
                    .padding(horizontal = MM.space.s, vertical = MM.space.xs)
            ) {
                Text(
                    text = EntryPoints.learnTeachBack.label,
                    style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                    color = c.accent
                )
            }

            Text(
                text = activity.prompt,
                style = MM.type.heading.copy(fontWeight = FontWeight.SemiBold),
                color = c.ink,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Companion reactive display
            Companion(
                form = companionForm,
                state = if (isRecording) CompanionState.LISTENING else CompanionState.IDLE,
                size = 72.dp,
                level = { if (isRecording) micLevel else 0f },
                modifier = Modifier.testTag("teach_back_companion")
            )

            // Audio Record Toggle Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Box(
                    modifier = Modifier
                        .size(MMSize.minTouch)
                        .clip(CircleShape)
                        .background(if (isRecording) LearningFixedColors.MasteryNeedsReview else c.accent)
                        .clickable(onClick = onMicButtonClick)
                        .testTag("teach_back_voice_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (isRecording) "Stop" else "Speak",
                        tint = c.surface
                    )
                }

                Text(
                    text = if (isRecording) "Listening... Tap to stop" else "Tap to explain out loud",
                    style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = c.ink
                )
            }

            speechNotice?.let { notice ->
                Text(
                    text = notice,
                    style = MM.type.caption,
                    color = LearningFixedColors.MasteryNeedsReview,
                    textAlign = TextAlign.Center
                )
            }

            // Or write explanation text
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("Or type your explanation here") },
                modifier = Modifier.fillMaxWidth().testTag("teach_back_input"),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                PrimaryButton(
                    text = "Evaluate Explanation",
                    onClick = { onSubmitText(textInput) },
                    enabled = !isEvaluating && textInput.isNotBlank(),
                    modifier = Modifier.testTag("teach_back_submit")
                )
                if (isEvaluating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MMSize.iconSmall),
                        color = c.accent,
                        strokeWidth = 2.dp
                    )
                }
            }

            // Evaluation Rubric Result Display
            evaluation?.let { eval ->
                TeachBackEvaluationSection(eval = eval)
            }
        }
    }
}

@Composable
private fun TeachBackEvaluationSection(eval: TeachBackEvaluation) {
    val c = MM.colors
    val coverageColor = when (eval.overallCoverage) {
        TeachBackCoverage.COVERED -> LearningFixedColors.MasteryStrong
        TeachBackCoverage.PARTLY_COVERED -> LearningFixedColors.MasteryDeveloping
        TeachBackCoverage.NOT_YET_DEMONSTRATED -> LearningFixedColors.MasteryNeedsReview
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .background(c.surfaceSunk)
            .padding(MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = eval.overallCoverage.label,
                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                color = coverageColor
            )
            Text(
                text = "${eval.scorePercent}%",
                style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                color = coverageColor
            )
        }

        Text(
            text = eval.summary,
            style = MM.type.secondary,
            color = c.ink
        )

        // Socratic follow-up prompt
        eval.socraticFollowUp?.let { followUp ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                Text(
                    text = followUp,
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent
                )
            }
        }

        // Criteria checklist
        eval.criteriaEvaluations.forEach { crit ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        when (crit.status) {
                            TeachBackStatus.MET -> Icons.Rounded.Check
                            TeachBackStatus.PARTIAL -> Icons.Rounded.HelpOutline
                            TeachBackStatus.UNMET -> Icons.Rounded.Close
                        },
                        contentDescription = null,
                        tint = when (crit.status) {
                            TeachBackStatus.MET -> LearningFixedColors.MasteryStrong
                            TeachBackStatus.PARTIAL -> LearningFixedColors.MasteryDeveloping
                            TeachBackStatus.UNMET -> LearningFixedColors.MasteryNeedsReview
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = crit.criterion.claim,
                        style = MM.type.caption,
                        color = c.ink
                    )
                }
            }
        }
    }
}

@Composable
private fun MistakeExplanationCard(explanation: MistakeExplanation) {
    val c = MM.colors

    MMCard(contentPadding = MM.space.m) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = LearningFixedColors.MasteryDeveloping, modifier = Modifier.size(MMSize.iconSmall))
                Text(
                    text = "Explain My Mistake",
                    style = MM.type.bodyStrong,
                    color = c.ink
                )
            }

            Text(
                text = "Root Misconception: ${explanation.rootMisconception}",
                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = LearningFixedColors.MasteryNeedsReview
            )

            Text(
                text = "Correct Mental Model: ${explanation.correctMentalModel}",
                style = MM.type.caption,
                color = c.ink
            )

            Text(
                text = explanation.stepByStepCorrection,
                style = MM.type.caption,
                color = c.inkSecondary
            )
        }
    }
}

@Composable
private fun InteractiveProblemCard(
    activity: LearningActivity,
    onRate: (RecallRating) -> Unit,
    onRequestMistakeExplanation: (LearningActivity, String) -> Unit,
    revealedHintCount: Int = 0,
    hintLadder: List<String> = emptyList(),
    isLoadingHints: Boolean = false,
    onRequestNextHint: (LearningActivity) -> Unit = {}
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    var selectedOption by remember(activity.id) { mutableStateOf<String?>(null) }
    var isChecked by remember(activity.id) { mutableStateOf(false) }

    MMCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interactive_problem_card"),
        contentPadding = MM.space.l
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(MM.radius.small)
                        .background(c.surfaceSunk)
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                ) {
                    Text(
                        text = activity.type.name.replace("_", " "),
                        style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                        color = c.accent
                    )
                }
                Text(
                    text = activity.difficulty,
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }

            Text(
                text = activity.prompt.replace("**", ""),
                style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                color = c.ink
            )

            // 3-Tier Socratic Hint Ladder
            SocraticHintLadder(
                activity = activity,
                revealedHintCount = revealedHintCount,
                hintLadder = hintLadder,
                isLoadingHints = isLoadingHints,
                onRequestNextHint = onRequestNextHint
            )

            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                activity.options.forEachIndexed { idx, option ->
                    val optionLabel = ('A' + idx).toString()
                    val isSelected = selectedOption == option
                    val isCorrectOption = option.trim().equals(activity.expectedAnswer.trim(), ignoreCase = true) ||
                        activity.expectedAnswer.contains(option.trim(), ignoreCase = true)

                    val borderColor = when {
                        !isChecked && isSelected -> c.accent
                        isChecked && isCorrectOption -> LearningFixedColors.MasteryStrong
                        isChecked && isSelected && !isCorrectOption -> LearningFixedColors.MasteryNeedsReview
                        else -> c.line
                    }
                    val bgColor = when {
                        !isChecked && isSelected -> c.accentWash
                        isChecked && isCorrectOption -> LearningFixedColors.MasteryStrong.copy(alpha = 0.14f)
                        isChecked && isSelected && !isCorrectOption -> LearningFixedColors.MasteryNeedsReview.copy(alpha = 0.14f)
                        else -> c.surfaceSunk
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MM.radius.small)
                            .background(bgColor)
                            .border(MMSize.hairline, borderColor, MM.radius.small)
                            .clickable(enabled = !isChecked) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedOption = option
                                isChecked = true
                                if (isCorrectOption) {
                                    onRate(RecallRating.GOOD)
                                } else {
                                    onRate(RecallRating.AGAIN)
                                    onRequestMistakeExplanation(activity, option)
                                }
                            }
                            .padding(MM.space.m),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(borderColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = optionLabel,
                                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                color = c.ink
                            )
                        }
                        Text(
                            text = option,
                            style = MM.type.body,
                            color = c.ink,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isChecked) {
                val gotItRight = selectedOption?.let { opt ->
                    opt.trim().equals(activity.expectedAnswer.trim(), ignoreCase = true) ||
                        activity.expectedAnswer.contains(opt.trim(), ignoreCase = true)
                } == true

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MM.radius.small)
                        .background(
                            if (gotItRight) LearningFixedColors.MasteryStrong.copy(alpha = 0.12f)
                            else LearningFixedColors.MasteryNeedsReview.copy(alpha = 0.12f)
                        )
                        .padding(MM.space.m)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        Text(
                            text = if (gotItRight) "Correct! Strong Reasoning" else "Not quite — Review Model Solution",
                            style = MM.type.bodyStrong,
                            color = if (gotItRight) LearningFixedColors.MasteryStrong else LearningFixedColors.MasteryNeedsReview
                        )
                        Text(
                            text = activity.expectedAnswer,
                            style = MM.type.body,
                            color = c.ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPracticeScreen(
    companionForm: CompanionForm,
    onGenerateQuickDeck: (topic: String, domain: SubjectDomain) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val c = MM.colors
    var customTopic by remember { mutableStateOf("") }
    var selectedDomain by remember { mutableStateOf(SubjectDomain.MATH_QUANT) }

    val starterTopics = remember {
        listOf(
            "Calculus: Limits & Derivatives" to SubjectDomain.MATH_QUANT,
            "Data Structures & Algorithms" to SubjectDomain.PROGRAMMING_CS,
            "Cellular Respiration & ATP" to SubjectDomain.SCIENCES_MED,
            "Financial Accounting & Double-Entry" to SubjectDomain.ACCOUNTING_FINANCE
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MM.space.xl)
            .testTag("empty_practice_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Companion(
            form = companionForm,
            state = CompanionState.IDLE,
            size = 84.dp,
            modifier = Modifier.testTag("practice_empty_companion")
        )
        Spacer(Modifier.height(MM.space.m))
        Text(
            text = "No Practice Cards Yet",
            style = MM.type.title.copy(fontWeight = FontWeight.Bold),
            color = c.ink
        )
        Spacer(Modifier.height(MM.space.xs))
        Text(
            text = "Record a lecture, or generate an instant study deck below to start practicing right away.",
            style = MM.type.secondary,
            color = c.inkSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MM.space.l))

        MMCard(contentPadding = MM.space.l) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                Text(
                    text = "Generate Instant Study Deck",
                    style = MM.type.bodyStrong,
                    color = c.ink
                )
                OutlinedTextField(
                    value = customTopic,
                    onValueChange = { customTopic = it },
                    label = { Text("Topic (e.g. Linear Algebra, Organic Chemistry)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    SubjectDomain.entries.forEach { domain ->
                        val isSelected = domain == selectedDomain
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(if (isSelected) c.accent else c.surfaceSunk)
                                .clickable { selectedDomain = domain }
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                        ) {
                            Text(
                                text = domain.shortLabel,
                                style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                color = if (isSelected) c.onAccent else c.inkSecondary
                            )
                        }
                    }
                }
                PrimaryButton(
                    text = "Generate Practice Cards",
                    onClick = {
                        val topic = customTopic.ifBlank { "${selectedDomain.displayName} Essentials" }
                        onGenerateQuickDeck(topic, selectedDomain)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("practice_generate_quick_deck")
                )

                HorizontalDivider(color = c.line)

                Text(
                    text = "Or tap a quick starter deck:",
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.inkSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    starterTopics.forEach { (topic, domain) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MM.radius.small)
                                .background(c.surfaceSunk)
                                .clickable { onGenerateQuickDeck(topic, domain) }
                                .padding(horizontal = MM.space.m, vertical = MM.space.s),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(topic, style = MM.type.bodyStrong, color = c.ink)
                                Text(domain.displayName, style = MM.type.caption, color = c.inkSecondary)
                            }
                            Text("Start →", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(MM.space.l))
        SecondaryButton(
            text = "Return to Learn",
            onClick = onBack,
            modifier = Modifier.testTag("practice_empty_return")
        )
    }
}

@Composable
private fun AllDoneScreen(
    companionForm: CompanionForm,
    stats: PracticeSessionStats = PracticeSessionStats(),
    totalCards: Int = 0,
    onPracticeAgain: () -> Unit = {},
    onBack: () -> Unit
) {
    val c = MM.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MM.space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Companion(
            form = companionForm,
            state = CompanionState.CELEBRATING,
            size = 96.dp,
            modifier = Modifier.testTag("practice_done_companion")
        )
        Spacer(Modifier.height(MM.space.m))
        Text(
            text = "Practice Complete!",
            style = MM.type.title.copy(fontWeight = FontWeight.Bold),
            color = c.ink
        )
        Spacer(Modifier.height(MM.space.xs))
        Text(
            text = "Your spaced review schedule and mastery evaluations have been updated.",
            style = MM.type.secondary,
            color = c.inkSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(MM.space.l))

        MMCard(contentPadding = MM.space.l) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${maxOf(stats.attemptedCount, totalCards)}",
                        style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                        color = c.ink
                    )
                    Text("Reviewed", style = MM.type.caption, color = c.inkSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (stats.attemptedCount > 0) "${stats.accuracyPercent}%" else "100%",
                        style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                        color = LearningFixedColors.MasteryStrong
                    )
                    Text("Accuracy", style = MM.type.caption, color = c.inkSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.bestStreak}",
                        style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                        color = LearningFixedColors.MasteryDeveloping
                    )
                    Text("Best Streak", style = MM.type.caption, color = c.inkSecondary)
                }
            }
        }

        Spacer(Modifier.height(MM.space.l))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            SecondaryButton(
                text = "Practice Again",
                onClick = onPracticeAgain,
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = "Return to Learn",
                onClick = onBack,
                modifier = Modifier.weight(1f).testTag("practice_done_return")
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudyTimerPill(
    secondsLeft: Int,
    isRunning: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current

    val minutes = (secondsLeft / 60).coerceAtLeast(0)
    val seconds = (secondsLeft % 60).coerceAtLeast(0)
    val timeFormatted = "%02d:%02d".format(minutes, seconds)

    Row(
        modifier = modifier
            .clip(MM.radius.pill)
            .background(if (isRunning) c.accentWash else c.surfaceSunk)
            .border(
                MMSize.hairline,
                if (isRunning) c.accent.copy(alpha = 0.4f) else c.lineSoft,
                MM.radius.pill
            )
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onReset()
                }
            )
            .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
            .testTag("study_timer_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs / 2)
    ) {
        Icon(
            if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isRunning) "Pause timer" else "Start timer",
            tint = if (isRunning) c.accent else c.inkSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = timeFormatted,
            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
            color = if (isRunning) c.accent else c.inkSecondary
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onReset()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Reset timer",
                tint = c.inkSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun SocraticHintLadder(
    activity: LearningActivity,
    revealedHintCount: Int,
    hintLadder: List<String>,
    isLoadingHints: Boolean,
    onRequestNextHint: (LearningActivity) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MM.space.s),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val canRequestMore = revealedHintCount < 3 && !isLoadingHints
        Box(
            modifier = Modifier
                .clip(MM.radius.pill)
                .background(if (canRequestMore) c.accentWash else c.surfaceSunk)
                .border(
                    MMSize.hairline,
                    if (canRequestMore) c.accent.copy(alpha = 0.35f) else c.lineSoft,
                    MM.radius.pill
                )
                .clickable(enabled = canRequestMore) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRequestNextHint(activity)
                }
                .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                .testTag("hint_ladder_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                if (isLoadingHints) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = c.accent
                    )
                    Text(
                        text = "Thinking...",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                        color = c.accent
                    )
                } else {
                    Icon(
                        Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = if (canRequestMore) c.accent else c.inkSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (revealedHintCount == 0) "Hint (0/3)" else "Hint ($revealedHintCount/3)",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (canRequestMore) c.accent else c.inkSecondary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = revealedHintCount > 0,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MM.radius.card)
                    .background(c.accentWash)
                    .border(MMSize.hairline, c.accent.copy(alpha = 0.2f), MM.radius.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(MM.space.m)
                    .testTag("hint_ladder_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    val tierNames = listOf("1: Nudge", "2: Clue", "3: First Step")
                    for (i in 0 until revealedHintCount.coerceAtMost(3)) {
                        val tierTitle = tierNames.getOrElse(i) { "Hint ${i + 1}" }
                        val hintText = hintLadder.getOrNull(i) ?: "Pondering the problem deeper..."
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(MM.radius.pill)
                                        .background(c.accent.copy(alpha = 0.18f))
                                        .padding(horizontal = MM.space.xs, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tierTitle,
                                        style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                                        color = c.accent
                                    )
                                }
                            }
                            Text(
                                text = hintText,
                                style = MM.type.caption,
                                color = c.ink
                            )
                        }
                        if (i < (revealedHintCount - 1).coerceAtMost(2)) {
                            HorizontalDivider(color = c.lineSoft, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
