package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.DailyBrief
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import kotlinx.coroutines.launch

val LearningTeal = Color(0xFF0891B2)
val LearningTealWash = Color(0xFF0891B2).copy(alpha = 0.12f)

@Composable
fun LearnHomeScreen(
    viewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onStartPractice: (sessionId: String?, activityId: String?) -> Unit
) {
    val dailyBrief by viewModel.dailyBrief.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val dueReviews by viewModel.dueReviews.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Daily Brief, 1: Library, 2: Practice, 3: Progress
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = SurfaceBase,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.semantics { contentDescription = "Go back" }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Ink)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                "Learn",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Text(
                                "Capture, understand, practise, recall",
                                fontSize = 12.5.sp,
                                color = InkSecondary
                            )
                        }
                    }
                    Surface(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = LearningTealWash,
                        border = BorderStroke(1.dp, LearningTeal.copy(alpha = 0.3f)),
                        modifier = Modifier.semantics { contentDescription = "Create new learning session" }
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = LearningTeal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Session", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LearningTeal)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                val tabs = listOf("Daily Brief", "Library", "Practice", "Progress")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tabs.size) { index ->
                        val isSelected = selectedTab == index
                        Surface(
                            onClick = { selectedTab = index },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Ink else SurfaceSunk,
                            border = if (!isSelected) BorderStroke(1.dp, Line) else null,
                            modifier = Modifier.semantics { contentDescription = "${tabs[index]} tab" }
                        ) {
                            Text(
                                tabs[index],
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else InkSecondary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DailyBriefTab(
                    brief = dailyBrief,
                    onStartPractice = { onStartPractice(null, null) },
                    onOpenSession = onOpenSession
                )
                1 -> LibraryTab(sessions = sessions, onOpenSession = onOpenSession)
                2 -> PracticeTab(
                    dueReviews = dueReviews,
                    onStartPractice = onStartPractice,
                    onSnooze = { actId -> viewModel.snoozeActivity(actId) }
                )
                3 -> ProgressTab(sessions = sessions, onOpenSession = onOpenSession)
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var course by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Learning Session", color = Ink, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Create a typed session in your Learning workspace:", fontSize = 13.sp, color = InkSecondary)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Topic or Lecture Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = course,
                        onValueChange = { course = it },
                        label = { Text("Course or Subject (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            showCreateDialog = false
                            scope.launch {
                                val s = viewModel.createTypedSession(title, course.takeIf { it.isNotBlank() })
                                onOpenSession(s.id)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LearningTeal)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = InkMuted) }
            }
        )
    }
}

@Composable
private fun DailyBriefTab(
    brief: DailyBrief?,
    onStartPractice: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    if (brief == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LearningTeal)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Daily Goal / Done card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (brief.isDoneForToday) SurfaceRaised else LearningTealWash,
                border = BorderStroke(1.dp, if (brief.isDoneForToday) LineSoft else LearningTeal.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    if (brief.isDoneForToday) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LearningTeal, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Done for today!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text("No reviews overdue. Knowledge consolidated.", fontSize = 13.sp, color = InkSecondary)
                            }
                        }
                    } else {
                        Text("DAILY RECALL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LearningTeal, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "You have ${brief.estimatedMinutes} min of useful learning today",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink
                        )
                        Text(
                            "${brief.dueActivities.size} reviews due to keep concepts fresh in memory.",
                            fontSize = 13.sp, color = InkSecondary
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onStartPractice,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Start Daily Practice (${brief.dueActivities.size} items)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Recommended Weak Concept Callout
        val weakConceptPair = brief.weakConcept
        if (weakConceptPair != null) {
            val concept: LearningConcept = weakConceptPair.first
            val reason: String = weakConceptPair.second
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceSunk,
                    border = BorderStroke(1.dp, Line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = Color(0xFFF59E0B).copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("RECOMMENDED WEAK CONCEPT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkMuted)
                                Text(concept.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(concept.definition, fontSize = 13.sp, color = InkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text("Why recommended: $reason", fontSize = 12.sp, color = InkMuted, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Continuation Session
        val contSession = brief.continueSession
        if (contSession != null) {
            item {
                Text("CONTINUE SESSION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkMuted)
                Spacer(Modifier.height(6.dp))
                Surface(
                    onClick = { onOpenSession(contSession.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceRaised,
                    border = BorderStroke(1.dp, Line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(contSession.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            contSession.courseName?.let { Text(it, fontSize = 12.5.sp, color = LearningTeal) }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    sessions: List<LearningSession>,
    onOpenSession: (String) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No learning sessions yet. Tap 'New Session' or open a Lecture note to start.", color = InkMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sessions) { session ->
            Surface(
                onClick = { onOpenSession(session.id) },
                shape = RoundedCornerShape(16.dp),
                color = SurfaceRaised,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(session.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            session.courseName?.let {
                                Text(it, fontSize = 12.5.sp, color = LearningTeal, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Status: ${session.status}", fontSize = 11.5.sp, color = InkMuted)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PracticeTab(
    dueReviews: List<Pair<com.craftflowtechnologies.meetingmind.core.model.LearningActivity, com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule>>,
    onStartPractice: (sessionId: String?, activityId: String?) -> Unit,
    onSnooze: (String) -> Unit
) {
    if (dueReviews.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LearningTeal, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("No due reviews right now. Great job!", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = { onStartPractice(null, null) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Practice (${dueReviews.size} due)")
            }
        }
        items(dueReviews) { (act, sched) ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceRaised,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(act.prompt, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Text("${act.type.label} · Interval: ${sched.intervalDays}d", fontSize = 12.sp, color = InkMuted)
                    }
                    IconButton(onClick = { onSnooze(act.id) }, modifier = Modifier.semantics { contentDescription = "Snooze review" }) {
                        Icon(Icons.Filled.Snooze, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressTab(
    sessions: List<LearningSession>,
    onOpenSession: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("TRANSPARENT MASTERY MODEL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkMuted)
            Text(
                "Mastery is earned through spaced recall and retrieval — never inferred simply from listening or opening cards.",
                fontSize = 13.sp, color = InkSecondary
            )
        }
        items(LearningMasteryState.entries) { state ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceRaised,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val color = when (state) {
                        LearningMasteryState.STRONG -> Color(0xFF10B981)
                        LearningMasteryState.DEVELOPING -> Color(0xFF3B82F6)
                        LearningMasteryState.LEARNING -> LearningTeal
                        LearningMasteryState.NEEDS_REVIEW -> Color(0xFFEF4444)
                        LearningMasteryState.NEW -> InkMuted
                    }
                    Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(state.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(state.description, fontSize = 12.sp, color = InkSecondary)
                    }
                }
            }
        }
    }
}
