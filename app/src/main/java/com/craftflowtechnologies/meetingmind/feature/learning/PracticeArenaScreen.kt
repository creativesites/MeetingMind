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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM

@Composable
fun PracticeArenaScreen(
    viewModel: LearnViewModel,
    onBack: () -> Unit,
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
    var isCompleted by remember { mutableStateOf(false) }

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
        onFinish = { isCompleted = true },
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
    onFlip: () -> Unit,
    onRate: (RecallRating) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit = {},
    onToggleVoice: () -> Unit,
    onSubmitTeachBackText: (LearningActivity, String) -> Unit,
    onRequestMistakeExplanation: (LearningActivity, String) -> Unit,
    onBack: () -> Unit,
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
                if (activities.isNotEmpty()) {
                    Text(
                        "${(currentIndex + 1).coerceAtMost(activities.size)} / ${activities.size}",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = c.inkSecondary,
                        modifier = Modifier.padding(end = MM.space.m)
                    )
                }
            }
        )

        if (activities.isEmpty()) {
            EmptyPracticeScreen(companionForm = companionForm, onBack = onBack)
        } else if (isCompleted || currentIndex >= activities.size) {
            AllDoneScreen(companionForm = companionForm, onBack = onBack)
        } else {
            val currentActivity = activities[currentIndex]
            val isTeachBack = currentActivity.type == LearningActivityType.TEACH_BACK

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MM.space.l, vertical = MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                if (isTeachBack) {
                    TeachBackStudioCard(
                        activity = currentActivity,
                        companionForm = companionForm,
                        isRecording = isRecordingVoice,
                        micLevel = micLevel,
                        isEvaluating = isEvaluating,
                        evaluation = teachBackEvaluation,
                        onToggleVoice = onToggleVoice,
                        onSubmitText = { onSubmitTeachBackText(currentActivity, it) }
                    )
                } else {
                    RecallFlashcard(
                        activity = currentActivity,
                        isFlipped = isFlipped,
                        onFlip = onFlip,
                        onRate = onRate,
                        onRequestMistakeExplanation = onRequestMistakeExplanation
                    )

                    AnimatedVisibility(visible = mistakeExplanation != null) {
                        mistakeExplanation?.let {
                            MistakeExplanationCard(explanation = it)
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
    onRequestMistakeExplanation: (LearningActivity, String) -> Unit
) {
    val c = MM.colors
    var studentAnswer by remember(activity.id) { mutableStateOf("") }
    var lastRating by remember(activity.id) { mutableStateOf<RecallRating?>(null) }

    MMCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onFlip)
            .testTag("flashcard_container"),
        contentPadding = MM.space.l
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MM.space.m),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.surfaceSunk)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = activity.type.name.replace("_", " "),
                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = c.accent
                )
            }

            Text(
                text = activity.prompt,
                style = MM.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = MM.space.m)
            )

            // Optional answer input field before/after flipping
            OutlinedTextField(
                value = studentAnswer,
                onValueChange = { studentAnswer = it },
                label = { Text("Your answer / thought (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("student_recall_attempt"),
                singleLine = true
            )

            if (!isFlipped) {
                Text(
                    text = "Tap to reveal answer",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            } else {
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

                // 4-tier recall grading buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MM.space.m),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    RecallButton(
                        label = "Again",
                        color = LearningFixedColors.MasteryNeedsReview,
                        onClick = {
                            lastRating = RecallRating.AGAIN
                            onRate(RecallRating.AGAIN)
                            val answer = studentAnswer.ifBlank { "Incorrect attempt on: ${activity.prompt}" }
                            onRequestMistakeExplanation(activity, answer)
                        },
                        modifier = Modifier.weight(1f).testTag("rate_again")
                    )
                    RecallButton(
                        label = "Hard",
                        color = LearningFixedColors.MasteryDeveloping,
                        onClick = {
                            lastRating = RecallRating.HARD
                            onRate(RecallRating.HARD)
                            val answer = studentAnswer.ifBlank { "Unsure/hard attempt on: ${activity.prompt}" }
                            onRequestMistakeExplanation(activity, answer)
                        },
                        modifier = Modifier.weight(1f).testTag("rate_hard")
                    )
                    RecallButton(
                        label = "Good",
                        color = c.accent,
                        onClick = {
                            lastRating = RecallRating.GOOD
                            onRate(RecallRating.GOOD)
                        },
                        modifier = Modifier.weight(1f).testTag("rate_good")
                    )
                    RecallButton(
                        label = "Easy",
                        color = LearningFixedColors.MasteryStrong,
                        onClick = {
                            lastRating = RecallRating.EASY
                            onRate(RecallRating.EASY)
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
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
            color = color
        )
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.accent.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = EntryPoints.learnTeachBack.label,
                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = c.accent
                )
            }

            Text(
                text = activity.prompt,
                style = MM.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = c.ink,
                textAlign = TextAlign.Center
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
                        .size(48.dp)
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
                maxLines = 4
            )

            if (textInput.isNotBlank()) {
                PrimaryButton(
                    text = "Evaluate Explanation",
                    onClick = { onSubmitText(textInput) },
                    modifier = Modifier.testTag("teach_back_submit")
                )
            }

            if (isEvaluating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = c.accent)
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
            .clip(RoundedCornerShape(12.dp))
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
                style = MM.type.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = LearningFixedColors.MasteryDeveloping, modifier = Modifier.size(20.dp))
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
private fun EmptyPracticeScreen(
    companionForm: CompanionForm,
    onBack: () -> Unit
) {
    val c = MM.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MM.space.xl)
            .testTag("empty_practice_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Companion(
            form = companionForm,
            state = CompanionState.IDLE,
            size = 96.dp,
            modifier = Modifier.testTag("practice_empty_companion")
        )
        Spacer(Modifier.height(MM.space.m))
        Text(
            text = "No Practice Cards Yet",
            style = MM.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = c.ink
        )
        Spacer(Modifier.height(MM.space.xs))
        Text(
            text = "Record lectures or create study notes to generate flashcards and teach-back prompts.",
            style = MM.type.secondary,
            color = c.inkSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MM.space.l))
        PrimaryButton(
            text = "Return to Learn",
            onClick = onBack,
            modifier = Modifier.testTag("practice_empty_return")
        )
    }
}

@Composable
private fun AllDoneScreen(
    companionForm: CompanionForm,
    onBack: () -> Unit
) {
    val c = MM.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            style = MM.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
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
        PrimaryButton(
            text = "Return to Learn",
            onClick = onBack,
            modifier = Modifier.testTag("practice_done_return")
        )
    }
}
