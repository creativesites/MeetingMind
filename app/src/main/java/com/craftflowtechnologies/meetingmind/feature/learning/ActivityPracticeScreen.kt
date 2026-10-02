package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.DeterministicSpacedScheduler
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val CorrectGreen = Color(0xFF16A34A)
val IncorrectRed = Color(0xFFDC2626)

@Composable
fun ActivityPracticeScreen(
    sessionId: String?,
    activityId: String?,
    isDiagnosticOnly: Boolean = false,
    viewModel: LearningViewModel,
    onNavigateBack: () -> Unit
) {
    var queue by remember { mutableStateOf<List<LearningActivity>>(emptyList()) }
    var schedulesByActivityId by remember { mutableStateOf<Map<String, ReviewSchedule>>(emptyMap()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var completedCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId, activityId) {
        isLoading = true
        val dues = viewModel.dueReviews.first()
        schedulesByActivityId = dues.associate { it.first.id to it.second }

        if (activityId != null) {
            val act = viewModel.repository.observeActivities(sessionId ?: "").first().find { it.id == activityId }
                ?: viewModel.repository.observeAllSessions().first().let { sessions ->
                    var found: LearningActivity? = null
                    for (s in sessions) {
                        val acts = viewModel.repository.observeActivities(s.id).first()
                        found = acts.find { it.id == activityId }
                        if (found != null) break
                    }
                    found
                }
            queue = if (act != null) listOf(act) else emptyList()
        } else if (sessionId != null) {
            val acts = if (isDiagnosticOnly) {
                viewModel.repository.observeDiagnosticActivities(sessionId).first()
            } else {
                viewModel.repository.observeActivities(sessionId).first()
            }
            queue = acts
        } else {
            // Due reviews across all sessions
            queue = dues.map { it.first }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = SurfaceBase,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Ink
                        )
                    }

                    Text(
                        text = if (isDiagnosticOnly) "Diagnostic Quiz" else "Daily Recall & Practice",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )

                    // Snooze and Dismiss actions
                    Row {
                        if (queue.isNotEmpty() && currentIndex < queue.size) {
                            IconButton(onClick = {
                                val act = queue[currentIndex]
                                viewModel.snoozeActivity(act.id, hours = 24)
                                if (currentIndex + 1 < queue.size) {
                                    currentIndex++
                                } else {
                                    currentIndex = queue.size
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Snooze,
                                    contentDescription = "Snooze 24 hours",
                                    tint = InkMuted
                                )
                            }
                            IconButton(onClick = {
                                val act = queue[currentIndex]
                                viewModel.dismissActivity(act.id)
                                if (currentIndex + 1 < queue.size) {
                                    currentIndex++
                                } else {
                                    currentIndex = queue.size
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Dismiss question",
                                    tint = InkMuted
                                )
                            }
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                    }
                }

                if (queue.isNotEmpty() && currentIndex < queue.size) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (currentIndex + 1).toFloat() / queue.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = LearningTeal,
                        trackColor = LineSoft,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Question ${currentIndex + 1} of ${queue.size}",
                            fontSize = 12.sp,
                            color = InkMuted
                        )
                        val typeLabel = if (queue[currentIndex].type == LearningActivityType.MULTIPLE_CHOICE) "Multiple Choice" else "Recall Flashcard"
                        Text(
                            typeLabel,
                            fontSize = 12.sp,
                            color = LearningTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LearningTeal)
                    }
                }
                queue.isEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "All done",
                            tint = LearningTeal,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No activities due!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You are caught up on your scheduled reviews.",
                            fontSize = 14.sp,
                            color = InkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = LearningTeal)
                        ) {
                            Text("Return")
                        }
                    }
                }
                currentIndex >= queue.size -> {
                    // Completion Screen
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .background(CorrectGreen.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Completed icon",
                                tint = CorrectGreen,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Session Complete!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You practiced $completedCount activities. Mastery states and spaced schedules have been updated deterministically.",
                            fontSize = 14.sp,
                            color = InkMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Text("Done", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                else -> {
                    val currentActivity = queue[currentIndex]
                    when (currentActivity.type) {
                        LearningActivityType.RECALL -> {
                            RecallPracticeItem(
                                activity = currentActivity,
                                schedule = schedulesByActivityId[currentActivity.id],
                                onRate = { rating ->
                                    scope.launch {
                                        val isCorrect = rating != RecallRating.AGAIN
                                        viewModel.recordAttempt(
                                            activityId = currentActivity.id,
                                            userResponse = rating.name,
                                            isCorrect = isCorrect,
                                            rating = rating
                                        )
                                        completedCount++
                                        currentIndex++
                                    }
                                }
                            )
                        }
                        LearningActivityType.MULTIPLE_CHOICE -> {
                            MultipleChoicePracticeItem(
                                activity = currentActivity,
                                onSubmit = { selectedAnswer, isCorrect ->
                                    scope.launch {
                                        val rating = if (isCorrect) RecallRating.GOOD else RecallRating.AGAIN
                                        viewModel.recordAttempt(
                                            activityId = currentActivity.id,
                                            userResponse = selectedAnswer,
                                            isCorrect = isCorrect,
                                            rating = rating
                                        )
                                        completedCount++
                                    }
                                },
                                onNext = {
                                    currentIndex++
                                }
                            )
                        }
                        else -> {
                            RecallPracticeItem(
                                activity = currentActivity,
                                schedule = schedulesByActivityId[currentActivity.id],
                                onRate = { rating ->
                                    scope.launch {
                                        val isCorrect = rating != RecallRating.AGAIN
                                        viewModel.recordAttempt(
                                            activityId = currentActivity.id,
                                            userResponse = rating.name,
                                            isCorrect = isCorrect,
                                            rating = rating
                                        )
                                        completedCount++
                                        currentIndex++
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecallPracticeItem(
    activity: LearningActivity,
    schedule: ReviewSchedule? = null,
    onRate: (RecallRating) -> Unit
) {
    var isRevealed by remember(activity.id) { mutableStateOf(false) }
    val effectiveSchedule = remember(activity.id, schedule) {
        schedule ?: DeterministicSpacedScheduler.initialSchedule(activity.id, activity.sessionId, activity.conceptId)
    }
    val previewAgain = remember(effectiveSchedule) {
        DeterministicSpacedScheduler.previewInterval(effectiveSchedule, RecallRating.AGAIN)
    }
    val previewHard = remember(effectiveSchedule) {
        DeterministicSpacedScheduler.previewInterval(effectiveSchedule, RecallRating.HARD)
    }
    val previewGood = remember(effectiveSchedule) {
        DeterministicSpacedScheduler.previewInterval(effectiveSchedule, RecallRating.GOOD)
    }
    val previewEasy = remember(effectiveSchedule) {
        DeterministicSpacedScheduler.previewInterval(effectiveSchedule, RecallRating.EASY)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Prompt Card
            Card(
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Flashcard Prompt: ${activity.prompt}" },
                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                border = BorderStroke(1.dp, LineSoft),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "QUESTION / CONCEPT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = activity.prompt,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        lineHeight = 26.sp
                    )

                    if (activity.evidence.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "Source citation",
                                tint = LearningTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Evidence: ${activity.evidence.first().displayLabel()}",
                                fontSize = 12.sp,
                                color = LearningTeal
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Answer revealed section
            AnimatedVisibility(visible = isRevealed) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Expected Answer: ${activity.expectedAnswer}" },
                    colors = CardDefaults.cardColors(containerColor = SurfaceSunk),
                    border = BorderStroke(1.dp, Line),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            "EXPECTED ANSWER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LearningTeal
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = activity.expectedAnswer,
                            fontSize = 16.sp,
                            color = Ink,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }

        // Action Buttons
        if (!isRevealed) {
            Button(
                onClick = { isRevealed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Reveal Answer Button" },
                colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Show Answer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column {
                Text(
                    "Rate your recall confidence:",
                    fontSize = 13.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Again
                    RecallRatingButton(
                        label = "Again",
                        sublabel = previewAgain,
                        color = IncorrectRed,
                        onClick = { onRate(RecallRating.AGAIN) },
                        modifier = Modifier.weight(1f)
                    )
                    // Hard
                    RecallRatingButton(
                        label = "Hard",
                        sublabel = previewHard,
                        color = Color(0xFFD97706),
                        onClick = { onRate(RecallRating.HARD) },
                        modifier = Modifier.weight(1f)
                    )
                    // Good
                    RecallRatingButton(
                        label = "Good",
                        sublabel = previewGood,
                        color = LearningTeal,
                        onClick = { onRate(RecallRating.GOOD) },
                        modifier = Modifier.weight(1f)
                    )
                    // Easy
                    RecallRatingButton(
                        label = "Easy",
                        sublabel = previewEasy,
                        color = CorrectGreen,
                        onClick = { onRate(RecallRating.EASY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun RecallRatingButton(
    label: String,
    sublabel: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label rating, next interval: $sublabel" },
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
            Text(sublabel, fontSize = 11.sp, color = InkMuted)
        }
    }
}

@Composable
fun MultipleChoicePracticeItem(
    activity: LearningActivity,
    onSubmit: (selectedAnswer: String, isCorrect: Boolean) -> Unit,
    onNext: () -> Unit
) {
    var selectedOption by remember(activity.id) { mutableStateOf<String?>(null) }
    var isSubmitted by remember(activity.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Question Prompt
            Card(
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Multiple Choice Question: ${activity.prompt}" },
                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                border = BorderStroke(1.dp, LineSoft),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "MULTIPLE CHOICE QUESTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = activity.prompt,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Options List
            activity.options.forEach { option ->
                val isSelected = selectedOption == option
                val isCorrectAnswer = option.equals(activity.expectedAnswer, ignoreCase = true)

                val (bgColor, borderColor, textColor) = when {
                    !isSubmitted && isSelected -> Triple(LearningTealWash, LearningTeal, Ink)
                    !isSubmitted -> Triple(SurfaceRaised, LineSoft, Ink)
                    isSubmitted && isCorrectAnswer -> Triple(CorrectGreen.copy(alpha = 0.12f), CorrectGreen, CorrectGreen)
                    isSubmitted && isSelected && !isCorrectAnswer -> Triple(IncorrectRed.copy(alpha = 0.12f), IncorrectRed, IncorrectRed)
                    else -> Triple(SurfaceRaised, LineSoft, InkMuted)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable(enabled = !isSubmitted) {
                            selectedOption = option
                        }
                        .semantics {
                            contentDescription = buildString {
                                append("Option: $option. ")
                                if (isSubmitted) {
                                    if (isCorrectAnswer) append("Correct answer. ")
                                    if (isSelected && !isCorrectAnswer) append("Selected incorrect answer. ")
                                } else if (isSelected) {
                                    append("Selected. ")
                                }
                            }
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Accessible Icon
                        if (isSubmitted) {
                            if (isCorrectAnswer) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Correct",
                                    tint = CorrectGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                            } else if (isSelected) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Incorrect",
                                    tint = IncorrectRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                        }
                        Text(
                            text = option,
                            fontSize = 15.sp,
                            color = textColor,
                            fontWeight = if (isSelected || (isSubmitted && isCorrectAnswer)) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Post-submission Explanation / Citation
            AnimatedVisibility(visible = isSubmitted) {
                val wasCorrect = selectedOption?.equals(activity.expectedAnswer, ignoreCase = true) == true
                Column(Modifier.padding(top = 12.dp)) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (wasCorrect) CorrectGreen.copy(alpha = 0.08f) else IncorrectRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (wasCorrect) CorrectGreen.copy(alpha = 0.3f) else IncorrectRed.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (wasCorrect) Icons.Filled.CheckCircle else Icons.Filled.Close,
                                    contentDescription = if (wasCorrect) "Correct result" else "Incorrect result",
                                    tint = if (wasCorrect) CorrectGreen else IncorrectRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (wasCorrect) "Correct!" else "Incorrect",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (wasCorrect) CorrectGreen else IncorrectRed
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Expected answer: ${activity.expectedAnswer}",
                                fontSize = 13.sp,
                                color = Ink,
                                fontWeight = FontWeight.Medium
                            )

                            if (activity.evidence.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Source: ${activity.evidence.first().displayLabel()}",
                                    fontSize = 12.sp,
                                    color = LearningTeal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Bottom
        if (!isSubmitted) {
            Button(
                onClick = {
                    if (selectedOption != null) {
                        isSubmitted = true
                        val isCorrect = selectedOption!!.equals(activity.expectedAnswer, ignoreCase = true)
                        onSubmit(selectedOption!!, isCorrect)
                    }
                },
                enabled = selectedOption != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .semantics { contentDescription = "Submit Answer Button" },
                colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Submit Answer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .semantics { contentDescription = "Next Question Button" },
                colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Next Question", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
