package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.MasteryCalculator
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LearningSessionScreen(
    sessionId: String,
    viewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onStartPractice: (sessionId: String?, activityId: String?) -> Unit,
    onStartDiagnostic: (sessionId: String) -> Unit,
    onOpenNote: (noteId: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<LearningSession?>(null) }
    var concepts by remember { mutableStateOf<List<LearningConcept>>(emptyList()) }
    var activities by remember { mutableStateOf<List<LearningActivity>>(emptyList()) }
    var attempts by remember { mutableStateOf<List<ActivityAttempt>>(emptyList()) }
    var isGenerating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Understand, 1: Practise, 2: Source, 3: Mastery
    var showTutorSheet by remember { mutableStateOf(false) }
    var selectedCitationToView by remember { mutableStateOf<LearningEvidence?>(null) }

    LaunchedEffect(sessionId) {
        viewModel.repository.observeSession(sessionId).collect { s ->
            session = s
        }
    }

    LaunchedEffect(sessionId) {
        viewModel.repository.observeConcepts(sessionId).collect { cList ->
            concepts = cList.filter { !it.isDismissed }
        }
    }

    LaunchedEffect(sessionId) {
        viewModel.repository.observeActivities(sessionId).collect { aList ->
            activities = aList.filter { !it.isDismissed }
        }
    }

    LaunchedEffect(sessionId) {
        viewModel.repository.observeAttempts(sessionId).collect { attList ->
            attempts = attList
        }
    }

    val vmIsGenerating by viewModel.isGenerating.collectAsState()
    val vmStatusMessage by viewModel.statusMessage.collectAsState()

    Scaffold(
        containerColor = SurfaceBase,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = session?.title ?: "Learning Session",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            session?.courseName?.let { course ->
                                Text(
                                    text = course,
                                    fontSize = 12.sp,
                                    color = LearningTeal,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pause / Resume Session Toggle
                        IconButton(onClick = {
                            val current = session ?: return@IconButton
                            viewModel.pauseSession(current.id, !current.isPaused)
                        }) {
                            Icon(
                                if (session?.isPaused == true) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (session?.isPaused == true) "Resume Session" else "Pause Session",
                                tint = if (session?.isPaused == true) Color(0xFFD97706) else InkMuted
                            )
                        }

                        // Ask Tutor Button
                        IconButton(onClick = { showTutorSheet = true }) {
                            Icon(
                                Icons.Filled.Lightbulb,
                                contentDescription = "Ask Tutor",
                                tint = LearningTeal
                            )
                        }
                    }
                }

                // Paused Banner if applicable
                if (session?.isPaused == true) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "This session is paused. Reviews will not appear in Daily Brief.",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E)
                        )
                    }
                }

                // Generation status if active
                if (vmIsGenerating) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(LearningTealWash, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = LearningTeal,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = vmStatusMessage ?: "AI generating study materials...",
                            fontSize = 12.sp,
                            color = LearningTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Tabs: Understand, Practise, Source, Mastery
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceBase,
                    contentColor = LearningTeal,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = LearningTeal
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Understand", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Practise", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Source", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Mastery", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> UnderstandSection(
                    sessionId = sessionId,
                    concepts = concepts,
                    isGenerating = vmIsGenerating,
                    onGenerate = { viewModel.generateStudyGuideAndDiagnostic(sessionId) },
                    onDismissConcept = { cid -> scope.launch { viewModel.repository.dismissConcept(cid) } },
                    onViewCitation = { selectedCitationToView = it }
                )
                1 -> PractiseSection(
                    sessionId = sessionId,
                    activities = activities,
                    onStartPractice = onStartPractice,
                    onStartDiagnostic = { onStartDiagnostic(sessionId) },
                    onAskTutor = { showTutorSheet = true }
                )
                2 -> SourceSection(
                    session = session,
                    onOpenNote = { session?.noteId?.let { onOpenNote(it) } }
                )
                3 -> MasterySection(
                    concepts = concepts,
                    activities = activities,
                    attempts = attempts
                )
            }
        }
    }

    // Tutor Bottom Sheet
    if (showTutorSheet) {
        AskLearningSheet(
            sessionId = sessionId,
            viewModel = viewModel,
            onDismiss = { showTutorSheet = false },
            onStartPracticeActivity = { actId ->
                showTutorSheet = false
                onStartPractice(sessionId, actId)
            }
        )
    }

    // Citation Preview Dialog
    selectedCitationToView?.let { citation ->
        AlertDialog(
            onDismissRequest = { selectedCitationToView = null },
            title = {
                Text("Cited Lecture Source", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column {
                    Text(
                        "Source reference: ${citation.displayLabel()}",
                        fontWeight = FontWeight.SemiBold,
                        color = LearningTeal,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (citation.excerpt != null) "\"${citation.excerpt}\"" else "Referenced in lecture source block/segment.",
                        fontSize = 14.sp,
                        color = Ink
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCitationToView = null }) {
                    Text("Close", color = LearningTeal)
                }
            }
        )
    }
}

@Composable
fun UnderstandSection(
    sessionId: String,
    concepts: List<LearningConcept>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onDismissConcept: (String) -> Unit,
    onViewCitation: (LearningEvidence) -> Unit
) {
    if (concepts.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(LearningTealWash, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "AI Study Guide",
                    tint = LearningTeal,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "No Study Guide Extracted Yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Extract key concepts, definitions, emphasis levels, and source citations directly from your lecture note and audio transcript.",
                fontSize = 14.sp,
                color = InkMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onGenerate,
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Extract Study Guide & Diagnostic")
            }
        }
    } else {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${concepts.size} Key Concepts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkSecondary
                    )
                    TextButton(onClick = onGenerate, enabled = !isGenerating) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", tint = LearningTeal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Regenerate", fontSize = 12.sp, color = LearningTeal)
                    }
                }
            }

            items(concepts) { concept ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Concept: ${concept.name}. ${concept.definition}" },
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    border = BorderStroke(1.dp, LineSoft),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = concept.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                modifier = Modifier.weight(1f)
                            )
                            // Emphasis pill
                            val emphasisColor = when (concept.emphasis?.uppercase()) {
                                "HIGH" -> Color(0xFFDC2626)
                                "MEDIUM" -> Color(0xFFD97706)
                                else -> LearningTeal
                            }
                            Box(
                                Modifier
                                    .background(emphasisColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    (concept.emphasis ?: "NORMAL").uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = emphasisColor
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = concept.definition,
                            fontSize = 14.sp,
                            color = InkSecondary,
                            lineHeight = 20.sp
                        )

                        // Relationships
                        if (concept.relationships.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Relates to: ", fontSize = 11.sp, color = InkMuted, fontWeight = FontWeight.SemiBold)
                                Text(concept.relationships.joinToString(", "), fontSize = 12.sp, color = Ink)
                            }
                        }

                        // Evidence Pills
                        if (concept.evidence.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(concept.evidence) { ev ->
                                    Surface(
                                        modifier = Modifier.clickable { onViewCitation(ev) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = LearningTealWash,
                                        border = BorderStroke(1.dp, LearningTeal.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = ev.displayLabel(),
                                            fontSize = 11.sp,
                                            color = LearningTeal,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Dismiss action
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDismissConcept(concept.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Dismiss concept", tint = InkMuted, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Dismiss", fontSize = 11.sp, color = InkMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PractiseSection(
    sessionId: String,
    activities: List<LearningActivity>,
    onStartPractice: (sessionId: String?, activityId: String?) -> Unit,
    onStartDiagnostic: () -> Unit,
    onAskTutor: () -> Unit
) {
    val diagnosticActivities = activities.filter { it.isDiagnostic }
    val practiceActivities = activities.filter { !it.isDiagnostic }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Diagnostic Quiz Banner
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LearningTealWash),
                border = BorderStroke(1.dp, LearningTeal.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = LearningTeal, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Diagnostic Quiz",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Ink
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (diagnosticActivities.isNotEmpty()) {
                            "${diagnosticActivities.size} diagnostic questions generated from lecture source to establish baseline mastery."
                        } else {
                            "Extract study guide first to generate the diagnostic quiz."
                        },
                        fontSize = 13.sp,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onStartDiagnostic,
                        enabled = diagnosticActivities.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Take Diagnostic Quiz", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Action: Quiz me on this session
        item {
            OutlinedButton(
                onClick = onAskTutor,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = LearningTeal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask Tutor / Quiz me on specific topic", color = LearningTeal, fontWeight = FontWeight.SemiBold)
            }
        }

        // Activity Queue List
        item {
            Text(
                "All Practice Activities (${activities.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        }

        if (activities.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No practice activities yet.", fontSize = 13.sp, color = InkMuted)
                }
            }
        } else {
            items(activities) { act ->
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onStartPractice(sessionId, act.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceRaised,
                    border = BorderStroke(1.dp, LineSoft)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = act.prompt,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val typeBadge = if (act.type == com.craftflowtechnologies.meetingmind.core.model.LearningActivityType.MULTIPLE_CHOICE) "Multiple Choice" else "Recall Card"
                                Text(typeBadge, fontSize = 11.sp, color = LearningTeal, fontWeight = FontWeight.Medium)
                                if (act.isDiagnostic) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("• Diagnostic", fontSize = 11.sp, color = InkMuted)
                                }
                            }
                        }
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Practice this question",
                            tint = LearningTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SourceSection(
    session: LearningSession?,
    onOpenNote: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
            border = BorderStroke(1.dp, LineSoft),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("UNDERLYING SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkMuted)
                Spacer(Modifier.height(8.dp))
                Text(
                    session?.title ?: "Lecture Note",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                session?.courseName?.let {
                    Text("Course: $it", fontSize = 13.sp, color = LearningTeal)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "This Learning Session is a scoped view directly over the source lecture note and audio recording. No duplicate notes or transcript pipelines exist.",
                    fontSize = 13.sp,
                    color = InkSecondary,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenNote,
                    colors = ButtonDefaults.buttonColors(containerColor = LearningTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Source Note / Transcript", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceSunk),
            border = BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("EVIDENCE & CITATION INTEGRITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkMuted)
                Spacer(Modifier.height(6.dp))
                Text(
                    "All AI-extracted concepts and practice items maintain cryptographic and segment IDs linking back to exact transcript timestamps or note blocks.",
                    fontSize = 12.sp,
                    color = InkSecondary,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
fun MasterySection(
    concepts: List<LearningConcept>,
    activities: List<LearningActivity>,
    attempts: List<ActivityAttempt>
) {
    // Transparent qualitative mastery calculation
    val evaluation = MasteryCalculator.evaluate(attempts, null)
    val mastery = evaluation.state

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Overall Mastery State Card
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                border = BorderStroke(1.dp, LineSoft),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "QUALITATIVE MASTERY STATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val stateColor = when (mastery) {
                            LearningMasteryState.STRONG -> Color(0xFF16A34A)
                            LearningMasteryState.DEVELOPING -> LearningTeal
                            LearningMasteryState.LEARNING -> Color(0xFFD97706)
                            LearningMasteryState.NEEDS_REVIEW -> Color(0xFFDC2626)
                            LearningMasteryState.NEW -> InkMuted
                        }
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(stateColor, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            mastery.name.replace("_", " "),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = stateColor
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = evaluation.reason,
                        fontSize = 13.sp,
                        color = InkSecondary,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        // Non-opaque transparency notice
        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = SurfaceSunk,
                border = BorderStroke(1.dp, Line)
            ) {
                Text(
                    text = "No opaque percentages or exam pass predictions. Mastery is based solely on your recall interval and verified practice attempts.",
                    fontSize = 12.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Recent Attempt History
        item {
            Text(
                "Attempt History (${attempts.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        }

        if (attempts.isEmpty()) {
            item {
                Text("No attempts recorded yet. Take the diagnostic or practice recall cards to begin.", fontSize = 13.sp, color = InkMuted)
            }
        } else {
            items(attempts) { att ->
                val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(att.attemptedAt))
                val act = activities.find { it.id == att.activityId }

                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceRaised,
                    border = BorderStroke(1.dp, LineSoft)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                act?.prompt ?: "Activity item",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "$dateStr • Rating: ${att.selfRating?.name ?: (if (att.isCorrect) "Correct" else "Incorrect")}",
                                fontSize = 11.sp,
                                color = InkMuted
                            )
                        }

                        // Accessible correctness icon + text
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (att.isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = if (att.isCorrect) "Correct attempt" else "Incorrect attempt",
                                tint = if (att.isCorrect) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (att.isCorrect) "Pass" else "Review",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (att.isCorrect) Color(0xFF16A34A) else Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        }
    }
}
