package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAnswer
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskLearningSheet(
    sessionId: String,
    viewModel: LearningViewModel,
    onDismiss: () -> Unit,
    onStartPracticeActivity: (activityId: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var questionText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentAnswer by remember { mutableStateOf<LearningAnswer?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var quizMeLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceBase
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(LearningTealWash, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Lightbulb,
                            contentDescription = "Tutor Icon",
                            tint = LearningTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Ask Lecture Tutor",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Text(
                            "Grounded strictly in lecture notes & transcript",
                            fontSize = 12.sp,
                            color = InkMuted
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = InkMuted)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Answer area or empty state
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LearningTeal, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Reviewing lecture evidence...", fontSize = 13.sp, color = InkSecondary)
                    }
                }
            } else if (currentAnswer != null) {
                val ans = currentAnswer!!
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceRaised,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LineSoft)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = ans.answer,
                            fontSize = 14.sp,
                            color = Ink,
                            lineHeight = 20.sp
                        )

                        if (ans.citedEvidence.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "CITATIONS & EVIDENCE:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = InkMuted
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyColumn(Modifier.height(80.dp)) {
                                items(ans.citedEvidence) { citation ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .background(SurfaceSunk, RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            citation.displayLabel(),
                                            fontSize = 12.sp,
                                            color = LearningTeal,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        // Quiz Me On This Button
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !quizMeLoading) {
                                    scope.launch {
                                        quizMeLoading = true
                                        val res = viewModel.createQuizMe(
                                            sessionId = sessionId,
                                            conceptName = questionText.ifBlank { "Tutor concept" },
                                            answerText = ans.answer,
                                            evidence = ans.citedEvidence
                                        )
                                        quizMeLoading = false
                                        if (res is AiResult.Success) {
                                            onDismiss()
                                            onStartPracticeActivity(res.value.id)
                                        }
                                    }
                                }
                                .semantics { contentDescription = "Quiz me on this concept button" },
                            shape = RoundedCornerShape(8.dp),
                            color = LearningTeal
                        ) {
                            Row(
                                Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (quizMeLoading) {
                                    CircularProgressIndicator(
                                        color = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Quiz me on this",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (errorMessage != null) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceSunk,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Unable to answer from lecture source:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            errorMessage!!,
                            fontSize = 12.sp,
                            color = InkMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Query Input
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    placeholder = { Text("Ask about this lecture...", fontSize = 14.sp, color = InkMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (questionText.isNotBlank()) {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                val result = viewModel.askTutor(sessionId, questionText.trim())
                                isLoading = false
                                if (result is AiResult.Success) {
                                    currentAnswer = result.value
                                } else {
                                    errorMessage = (result as? AiResult.Failed)?.message
                                        ?: "No cited answer could be derived from this lecture."
                                }
                            }
                        }
                    },
                    enabled = questionText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (questionText.isNotBlank()) LearningTeal else SurfaceSunk,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Question",
                        tint = if (questionText.isNotBlank()) androidx.compose.ui.graphics.Color.White else InkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
