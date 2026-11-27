package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.ai.learning.SimpleExplanation
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.NoteDocument
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
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
    val noteDocument by viewModel.activeSessionDocument.collectAsState()
    val segments by viewModel.activeSessionSegments.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val isSynthesizing by viewModel.isSynthesizingSession.collectAsState()
    val conceptVisuals by viewModel.conceptVisuals.collectAsState()
    val generatingVisualConceptId by viewModel.generatingVisualConceptId.collectAsState()
    val visualError by viewModel.visualError.collectAsState()
    val simpleExplanations by viewModel.simpleExplanations.collectAsState()
    val explainingSimpleConceptId by viewModel.explainingSimpleConceptId.collectAsState()
    val savedConceptExplanationIds by viewModel.savedConceptExplanationIds.collectAsState()
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
                val directDetail = viewModel.learningRepo.getSessionDetail(noteId)
                if (directDetail != null) {
                    viewModel.loadSessionDetail(noteId)
                } else {
                    // Automatically ensure a session exists if this is a valid noteId
                    val existingNote = viewModel.noteRepo.getNote(noteId)
                    if (existingNote != null) {
                        val ensured = viewModel.learningRepo.ensureSessionForNote(
                            noteId = existingNote.id,
                            title = existingNote.title.ifBlank { "Lecture Notes" }
                        )
                        viewModel.loadSessionDetail(ensured.id)
                    }
                }
                isLoadingSession = false
            }
        }
    }

    val resolvedNoteId = sessionDetail?.session?.noteId ?: noteId

    LearningSessionContent(
        sessionDetail = sessionDetail,
        noteDocument = noteDocument,
        transcriptSegments = segments,
        courses = courses,
        isLoadingSession = isLoadingSession,
        isCreatingSession = isCreatingSession,
        isSynthesizing = isSynthesizing,
        onBack = onBack,
        onOpenEditor = { onOpenEditor(resolvedNoteId) },
        onReadAloud = { viewModel.readSessionAloud(resolvedNoteId) },
        onStartPractice = onStartPractice,
        onTogglePause = { sessionDetail?.session?.id?.let { viewModel.toggleSessionPause(it) } },
        onOpenAssistant = onOpenAssistant,
        onRegenerateMaterials = {
            sessionDetail?.session?.id?.let { viewModel.regenerateSessionMaterials(it) }
        },
        onAddConcept = { name, definition, formula, importance ->
            sessionDetail?.session?.id?.let { sId ->
                viewModel.addCustomConcept(sId, name, definition, formula, importance)
            }
        },
        onToggleConceptImportance = { concept ->
            viewModel.toggleConceptImportance(concept)
        },
        onCreateCourseFromLecture = { courseName, courseCode, domain ->
            viewModel.createCourseFromNotes(
                noteIds = listOf(resolvedNoteId),
                courseName = courseName,
                courseCode = courseCode,
                domain = domain
            )
        },
        onAssignToCourse = { courseNotebookId ->
            viewModel.assignNoteToCourse(resolvedNoteId, courseNotebookId)
        },
        conceptVisuals = conceptVisuals,
        generatingVisualConceptId = generatingVisualConceptId,
        visualError = visualError,
        simpleExplanations = simpleExplanations,
        explainingSimpleConceptId = explainingSimpleConceptId,
        onGenerateVisual = { concept, kind ->
            viewModel.generateVisualForConcept(concept, sessionDetail?.session?.domain ?: SubjectDomain.GENERAL, kind)
        },
        onExplainConceptSimply = { concept ->
            viewModel.explainConceptSimply(concept, sessionDetail?.session?.domain ?: SubjectDomain.GENERAL)
        },
        savedConceptExplanationIds = savedConceptExplanationIds,
        onSaveExplanationToNote = { concept, explanation ->
            viewModel.saveSimpleExplanationToNote(concept, explanation)
        },
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
    noteDocument: NoteDocument? = null,
    transcriptSegments: List<TranscriptSegment> = emptyList(),
    courses: List<LearningRepository.LearningCourse> = emptyList(),
    isSynthesizing: Boolean = false,
    onRegenerateMaterials: () -> Unit = {},
    onAddConcept: (name: String, definition: String, formula: String?, importance: ExamImportance) -> Unit = { _, _, _, _ -> },
    onToggleConceptImportance: (LearningConcept) -> Unit = {},
    onCreateCourseFromLecture: (courseName: String, courseCode: String, domain: SubjectDomain) -> Unit = { _, _, _ -> },
    onAssignToCourse: (courseNotebookId: String) -> Unit = {},
    conceptVisuals: Map<String, LearningVisualSpec> = emptyMap(),
    generatingVisualConceptId: String? = null,
    visualError: String? = null,
    simpleExplanations: Map<String, SimpleExplanation> = emptyMap(),
    explainingSimpleConceptId: String? = null,
    onGenerateVisual: (LearningConcept, LearningVisualKind) -> Unit = { _, _ -> },
    onExplainConceptSimply: (LearningConcept) -> Unit = {},
    savedConceptExplanationIds: Set<String> = emptySet(),
    onSaveExplanationToNote: (LearningConcept, SimpleExplanation) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    var activeTab by remember { mutableStateOf(SessionTab.UNDERSTAND) }
    var showCourseLinkDialog by remember { mutableStateOf(false) }
    var showAddConceptDialog by remember { mutableStateOf(false) }
    var selectedPracticeFilter by remember { mutableStateOf(PracticeArenaFilter.ALL) }
    var selectedConceptFilter by remember { mutableStateOf("ALL") }
    var conceptSearchQuery by remember { mutableStateOf("") }

    if (showCourseLinkDialog && sessionDetail != null) {
        CourseFromLectureDialog(
            session = sessionDetail.session,
            existingCourses = courses,
            onDismiss = { showCourseLinkDialog = false },
            onCreateNewCourse = { name, code, domain ->
                onCreateCourseFromLecture(name, code, domain)
                showCourseLinkDialog = false
            },
            onAssignExisting = { courseId ->
                onAssignToCourse(courseId)
                showCourseLinkDialog = false
            }
        )
    }

    if (showAddConceptDialog) {
        AddConceptDialog(
            onDismiss = { showAddConceptDialog = false },
            onConfirm = { name, def, formula, imp ->
                onAddConcept(name, def, formula, imp)
                showAddConceptDialog = false
            }
        )
    }

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
                    Icon(AiMark, contentDescription = "Ask Learning Assistant", tint = c.ink)
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
                    val tabBg by animateColorAsState(
                        targetValue = if (isSelected) c.surfaceRaised else c.surfaceSunk,
                        animationSpec = MM.motion.quick(),
                        label = "session_tab_bg_${tab.name}"
                    )
                    val label = when (tab) {
                        SessionTab.UNDERSTAND -> "Lecture Notes"
                        SessionTab.CONCEPTS -> "Concepts (${sessionDetail.concepts.size})"
                        SessionTab.PRACTICE -> "Practice (${sessionDetail.activities.size})"
                        SessionTab.DIAGNOSTICS -> "Signals"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = MMSize.minTouch)
                            .clip(MM.radius.small)
                            .background(tabBg)
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
                            .padding(vertical = MM.space.s, horizontal = MM.space.xs)
                            .testTag("session_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                            color = if (isSelected) c.ink else c.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                    concepts = sessionDetail.concepts,
                                    activitiesCount = sessionDetail.activities.size,
                                    hasRecording = transcriptSegments.isNotEmpty() || sessionDetail.session.meetingId != null,
                                    onOpenEditor = onOpenEditor,
                                    onReadAloud = onReadAloud,
                                    onStartPractice = { onStartPractice(sessionDetail.activities) },
                                    onOpenCourseLink = { showCourseLinkDialog = true },
                                    onAskTutor = onOpenAssistant
                                )
                            }

                            item(key = "recorded_lecture_notes_section") {
                                RecordedLectureNotesCard(
                                    noteDocument = noteDocument,
                                    isSynthesizing = isSynthesizing,
                                    onOpenEditor = onOpenEditor,
                                    onRegenerate = onRegenerateMaterials
                                )
                            }

                            if (transcriptSegments.isNotEmpty()) {
                                item(key = "recorded_transcript_section") {
                                    RecordedLectureTranscriptCard(segments = transcriptSegments)
                                }
                            }
                        }

                        SessionTab.CONCEPTS -> {
                            val filteredConcepts = sessionDetail.concepts.filter { concept ->
                                val matchesFilter = when (selectedConceptFilter) {
                                    "REVIEW" -> concept.state == LearningMasteryState.NEEDS_REVIEW || concept.state == LearningMasteryState.NEW
                                    "HIGH_YIELD" -> concept.examImportance != ExamImportance.NORMAL
                                    "STRONG" -> concept.state == LearningMasteryState.STRONG
                                    else -> true
                                }
                                val matchesQuery = conceptSearchQuery.isBlank() ||
                                    concept.name.contains(conceptSearchQuery.trim(), ignoreCase = true) ||
                                    concept.definition.contains(conceptSearchQuery.trim(), ignoreCase = true)
                                matchesFilter && matchesQuery
                            }

                            item(key = "concepts_toolbar") {
                                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${sessionDetail.concepts.size} Key Concepts",
                                            style = MM.type.bodyStrong,
                                            color = c.ink
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(MM.radius.pill)
                                                    .background(c.surfaceSunk)
                                                    .clickable(enabled = !isSynthesizing, onClick = onRegenerateMaterials)
                                                    .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                                                    .testTag("session_extract_concepts")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Refresh,
                                                        contentDescription = null,
                                                        tint = c.accent,
                                                        modifier = Modifier.size(MM.space.m)
                                                    )
                                                    Text(
                                                        text = if (isSynthesizing) "Extracting…" else "AI Extract",
                                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                                        color = c.accent
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(MM.radius.pill)
                                                    .background(c.accent)
                                                    .clickable { showAddConceptDialog = true }
                                                    .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                                                    .testTag("session_add_concept")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Add,
                                                        contentDescription = null,
                                                        tint = c.onAccent,
                                                        modifier = Modifier.size(MM.space.m)
                                                    )
                                                    Text(
                                                        text = "Add Concept",
                                                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                                        color = c.onAccent
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (sessionDetail.concepts.size > 2) {
                                        val reviewCount = sessionDetail.concepts.count {
                                            it.state == LearningMasteryState.NEEDS_REVIEW || it.state == LearningMasteryState.NEW
                                        }
                                        val highYieldCount = sessionDetail.concepts.count {
                                            it.examImportance != ExamImportance.NORMAL
                                        }
                                        val strongCount = sessionDetail.concepts.count {
                                            it.state == LearningMasteryState.STRONG
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                                        ) {
                                            listOf(
                                                "ALL" to "All (${sessionDetail.concepts.size})",
                                                "REVIEW" to "To Review ($reviewCount)",
                                                "HIGH_YIELD" to "High-Yield ($highYieldCount)",
                                                "STRONG" to "Mastered ($strongCount)"
                                            ).forEach { (key, label) ->
                                                val isSel = selectedConceptFilter == key
                                                val chipBg by animateColorAsState(
                                                    targetValue = if (isSel) c.accent else c.surfaceSunk,
                                                    animationSpec = MM.motion.quick(),
                                                    label = "concept_filter_$key"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(MM.radius.pill)
                                                        .background(chipBg)
                                                        .clickable { selectedConceptFilter = key }
                                                        .padding(horizontal = MM.space.m, vertical = MM.space.xs / 2)
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MM.type.caption.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium),
                                                        color = if (isSel) c.onAccent else c.inkSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (sessionDetail.concepts.size > 4) {
                                        OutlinedTextField(
                                            value = conceptSearchQuery,
                                            onValueChange = { conceptSearchQuery = it },
                                            label = { Text("Search concepts or definitions…") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            if (sessionDetail.concepts.isEmpty()) {
                                item(key = "empty_concepts") {
                                    MMCard(contentPadding = MM.space.l) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(MM.space.s),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "No concepts extracted yet for this lecture.",
                                                style = MM.type.bodyStrong,
                                                color = c.ink
                                            )
                                            Text(
                                                "Tap AI Extract to pull definitions, formulas, and exam concepts from your lecture notes, or add one manually.",
                                                style = MM.type.secondary,
                                                color = c.inkSecondary,
                                                textAlign = TextAlign.Center
                                            )
                                            PrimaryButton(
                                                text = if (isSynthesizing) "Extracting…" else "Extract Concepts & Cards",
                                                onClick = onRegenerateMaterials
                                            )
                                        }
                                    }
                                }
                            } else if (filteredConcepts.isEmpty()) {
                                item(key = "empty_filtered_concepts") {
                                    MMCard(contentPadding = MM.space.m) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "No concepts match this filter.",
                                                style = MM.type.secondary,
                                                color = c.inkSecondary
                                            )
                                            Text(
                                                text = "Show All",
                                                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                                color = c.accent,
                                                modifier = Modifier.clickable {
                                                    selectedConceptFilter = "ALL"
                                                    conceptSearchQuery = ""
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredConcepts, key = { it.id }) { concept ->
                                    val linkedActivities = sessionDetail.activities.filter { it.conceptId == concept.id }
                                    ConceptCard(
                                        concept = concept,
                                        visual = conceptVisuals[concept.id],
                                        isGeneratingVisual = generatingVisualConceptId == concept.id,
                                        visualError = if (generatingVisualConceptId == concept.id || conceptVisuals.containsKey(concept.id)) null else visualError,
                                        simpleExplanation = simpleExplanations[concept.id],
                                        isExplainingSimple = explainingSimpleConceptId == concept.id,
                                        isSavedToNote = concept.id in savedConceptExplanationIds,
                                        onGenerateVisual = { kind -> onGenerateVisual(concept, kind) },
                                        onExplainSimply = { onExplainConceptSimply(concept) },
                                        onSaveExplanationToNote = { exp -> onSaveExplanationToNote(concept, exp) },
                                        onToggleImportance = { onToggleConceptImportance(concept) },
                                        onPracticeConcept = if (linkedActivities.isNotEmpty()) {
                                            { onStartPractice(linkedActivities) }
                                        } else null
                                    )
                                }
                            }
                        }

                        SessionTab.PRACTICE -> {
                            val filteredActivities = sessionDetail.activities.filter { act ->
                                val types = selectedPracticeFilter.matchingTypes()
                                types == null || act.type in types
                            }.ifEmpty { sessionDetail.activities }

                            item(key = "practice_header") {
                                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "${sessionDetail.activities.size} Practice Cards Ready",
                                                style = MM.type.bodyStrong,
                                                color = c.ink
                                            )
                                            Text(
                                                "Flashcards, worked problems & voice teach-back",
                                                style = MM.type.caption,
                                                color = c.inkSecondary
                                            )
                                        }
                                        if (sessionDetail.activities.isNotEmpty()) {
                                            PrimaryButton(
                                                text = "Start Practice",
                                                onClick = { onStartPractice(filteredActivities) },
                                                modifier = Modifier.testTag("start_session_practice")
                                            )
                                        } else {
                                            PrimaryButton(
                                                text = "Generate Cards",
                                                onClick = onRegenerateMaterials,
                                                modifier = Modifier.testTag("generate_session_practice")
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                                    ) {
                                        PracticeArenaFilter.entries.forEach { filter ->
                                            val isSelected = selectedPracticeFilter == filter
                                            Box(
                                                modifier = Modifier
                                                    .clip(MM.radius.pill)
                                                    .background(if (isSelected) c.accent else c.surfaceSunk)
                                                    .clickable { selectedPracticeFilter = filter }
                                                    .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                                            ) {
                                                Text(
                                                    text = filter.label,
                                                    style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                                    color = if (isSelected) c.onAccent else c.inkSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            items(filteredActivities, key = { it.id }) { activity ->
                                ActivitySummaryCard(
                                    activity = activity,
                                    onClick = {
                                        val reordered = listOf(activity) + filteredActivities.filterNot { it.id == activity.id }
                                        onStartPractice(reordered)
                                    }
                                )
                            }
                        }

                        SessionTab.DIAGNOSTICS -> {
                            item(key = "diagnostics") {
                                DiagnosticsCard(
                                    session = sessionDetail.session,
                                    concepts = sessionDetail.concepts,
                                    activities = sessionDetail.activities,
                                    attemptsCount = sessionDetail.attempts.size,
                                    onTogglePause = onTogglePause,
                                    onStartPractice = { onStartPractice(sessionDetail.activities) },
                                    onAskTutor = onOpenAssistant
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
private fun UnderstandTabHeader(
    session: LearningSession,
    concepts: List<LearningConcept>,
    activitiesCount: Int,
    hasRecording: Boolean,
    onOpenEditor: () -> Unit,
    onReadAloud: () -> Unit,
    onStartPractice: () -> Unit,
    onOpenCourseLink: () -> Unit,
    onAskTutor: () -> Unit
) {
    val c = MM.colors
    val hasCourse = !session.courseName.isNullOrBlank() &&
        !session.courseName.equals("Course", ignoreCase = true) &&
        !session.courseName.equals(session.title, ignoreCase = true)

    val strongCount = concepts.count { it.state == LearningMasteryState.STRONG }
    val developingCount = concepts.count { it.state == LearningMasteryState.DEVELOPING || it.state == LearningMasteryState.LEARNING }
    val reviewCount = concepts.count { it.state == LearningMasteryState.NEEDS_REVIEW || it.state == LearningMasteryState.NEW }

    MMCard(contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        // Course Chip or "+ Create Course from Lecture" CTA
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(if (hasCourse) c.accentWash else c.accent.copy(alpha = 0.14f))
                                .clickable(onClick = onOpenCourseLink)
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                                .testTag("session_course_chip")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.xs / 2)
                            ) {
                                Icon(
                                    imageVector = if (hasCourse) Icons.Rounded.School else Icons.Rounded.CreateNewFolder,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(MM.space.m)
                                )
                                Text(
                                    text = if (hasCourse) session.courseName!! else "+ Create / Assign Course",
                                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                    color = c.accent
                                )
                            }
                        }

                        // Domain badge
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(c.surfaceSunk)
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                        ) {
                            Text(
                                text = session.domain.displayName,
                                style = MM.type.caption,
                                color = c.inkSecondary
                            )
                        }

                        if (hasRecording) {
                            Box(
                                modifier = Modifier
                                    .clip(MM.radius.pill)
                                    .background(LearningFixedColors.MasteryStrong.copy(alpha = 0.14f))
                                    .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs / 2)
                                ) {
                                    Icon(
                                        Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = LearningFixedColors.MasteryStrong,
                                        modifier = Modifier.size(MM.space.s + MM.space.xs)
                                    )
                                    Text(
                                        text = "Recorded Lecture",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                        color = LearningFixedColors.MasteryStrong
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = session.title,
                        style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                        color = c.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (concepts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    MasteryStatPill(
                        label = "Strong",
                        count = strongCount,
                        color = LearningFixedColors.MasteryStrong,
                        modifier = Modifier.weight(1f)
                    )
                    MasteryStatPill(
                        label = "Developing",
                        count = developingCount,
                        color = LearningFixedColors.MasteryDeveloping,
                        modifier = Modifier.weight(1f)
                    )
                    MasteryStatPill(
                        label = "To Review",
                        count = reviewCount,
                        color = LearningFixedColors.MasteryNeedsReview,
                        modifier = Modifier.weight(1f)
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
                SecondaryButton(
                    text = "Ask Tutor",
                    onClick = onAskTutor,
                    modifier = Modifier.weight(1f).testTag("session_ask_tutor")
                )
                PrimaryButton(
                    text = "Read Aloud",
                    onClick = onReadAloud,
                    modifier = Modifier.weight(1f).testTag("session_read_aloud")
                )
            }

            if (activitiesCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MM.radius.small)
                        .background(c.surfaceSunk)
                        .clickable(onClick = onStartPractice)
                        .padding(horizontal = MM.space.m, vertical = MM.space.s),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Icon(
                            Icons.Rounded.FitnessCenter,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(MM.space.l)
                        )
                        Column {
                            Text(
                                text = "Practice This Lecture ($activitiesCount cards)",
                                style = MM.type.bodyStrong,
                                color = c.ink
                            )
                            Text(
                                text = "Flashcards, worked problems & voice teach-back",
                                style = MM.type.caption,
                                color = c.inkSecondary
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = c.inkSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MasteryStatPill(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MM.radius.small)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = MM.space.s, vertical = MM.space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MM.type.caption,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$count",
            style = MM.type.bodyStrong,
            color = color
        )
    }
}

/**
 * Renders the actual recorded/written lecture note blocks directly inside the Lecture Session Studio
 * so the student doesn't have to leave Learn to read their lecture notes, exam hints, formulas,
 * and expandable confusion unpacks.
 */
@Composable
private fun RecordedLectureNotesCard(
    noteDocument: NoteDocument?,
    isSynthesizing: Boolean,
    onOpenEditor: () -> Unit,
    onRegenerate: () -> Unit
) {
    val c = MM.colors
    val unpackedState = remember { mutableStateMapOf<String, Boolean>() }
    var noteBlockFilter by remember { mutableStateOf("ALL") }

    MMCard(
        modifier = Modifier.animateContentSize(animationSpec = MM.motion.standard()),
        contentPadding = MM.space.l
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Icon(
                        Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(MM.space.l)
                    )
                    Text(
                        text = "Lecture Notes",
                        style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                        color = c.ink
                    )
                }
                Text(
                    text = "Edit Full Note →",
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent,
                    modifier = Modifier.clickable(onClick = onOpenEditor)
                )
            }

            val blocks = noteDocument?.blocks.orEmpty()
            val calloutCount = remember(blocks) { blocks.count { it.type == NoteBlockType.CALLOUT } }
            val mathCount = remember(blocks) { blocks.count { it.type == NoteBlockType.MATH || it.type == NoteBlockType.DIAGRAM } }
            if (blocks.size > 3 && (calloutCount > 0 || mathCount > 0)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    val filterOptions = buildList {
                        add("ALL" to "All Notes (${blocks.size})")
                        if (calloutCount > 0) add("CALLOUTS" to "Exam Hints & Definitions ($calloutCount)")
                        if (mathCount > 0) add("FORMULAS" to "Formulas & Diagrams ($mathCount)")
                    }
                    filterOptions.forEach { (key, label) ->
                        val isSel = noteBlockFilter == key
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(if (isSel) c.accent else c.surfaceSunk)
                                .clickable { noteBlockFilter = key }
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                        ) {
                            Text(
                                text = label,
                                style = MM.type.caption.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium),
                                color = if (isSel) c.onAccent else c.inkSecondary
                            )
                        }
                    }
                }
            }

            val visibleBlocks = remember(blocks, noteBlockFilter) {
                when (noteBlockFilter) {
                    "CALLOUTS" -> blocks.filter { it.type == NoteBlockType.CALLOUT }
                    "FORMULAS" -> blocks.filter { it.type == NoteBlockType.MATH || it.type == NoteBlockType.DIAGRAM }
                    else -> blocks
                }
            }
            val summary = noteDocument?.note?.metadata?.get("summary")
                ?: noteDocument?.blocks?.firstOrNull { it.sectionKey == "overview" || it.sectionKey == "summary" }?.content?.text.orEmpty().trim()
            val plainText = noteDocument?.note?.plainText.orEmpty().trim()

            if (summary.isNotBlank() && noteBlockFilter == "ALL") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MM.radius.small)
                        .background(c.accentWash)
                        .padding(MM.space.m)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs / 2)) {
                        Text(
                            text = "LECTURE SUMMARY",
                            style = MM.type.overline,
                            color = c.accent
                        )
                        Text(
                            text = summary,
                            style = MM.type.body,
                            color = c.ink
                        )
                    }
                }
            }

            if (visibleBlocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    visibleBlocks.forEach { block ->
                        LectureBlockItem(
                            block = block,
                            isUnpacked = unpackedState[block.id] ?: (block.payload[NoteBlock.PAYLOAD_UNPACKED] == "1"),
                            onToggleUnpack = {
                                val current = unpackedState[block.id] ?: (block.payload[NoteBlock.PAYLOAD_UNPACKED] == "1")
                                unpackedState[block.id] = !current
                            }
                        )
                    }
                }
            } else if (plainText.isNotBlank()) {
                Text(
                    text = plainText,
                    style = MM.type.body,
                    color = c.ink
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MM.radius.small)
                        .background(c.surfaceSunk)
                        .padding(MM.space.m),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Text(
                        text = "This study session doesn't have structured note blocks yet.",
                        style = MM.type.secondary,
                        color = c.inkSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        SecondaryButton(
                            text = "Write in Editor",
                            onClick = onOpenEditor,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            text = if (isSynthesizing) "Synthesizing…" else "Generate Study Cards",
                            onClick = onRegenerate,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LectureBlockItem(
    block: NoteBlock,
    isUnpacked: Boolean,
    onToggleUnpack: () -> Unit
) {
    val c = MM.colors
    val text = block.content.text.trim()
    if (text.isBlank() && block.type != NoteBlockType.DIVIDER && block.type != NoteBlockType.DIAGRAM) return

    when (block.type) {
        NoteBlockType.DIAGRAM -> {
            val code = block.payload[NoteBlock.PAYLOAD_DIAGRAM_SYNTAX]?.takeIf { it.isNotBlank() } ?: text
            DiagramBlock(code = code)
        }

        NoteBlockType.HEADING_1, NoteBlockType.HEADING_2, NoteBlockType.HEADING_3 -> {
            Column(modifier = Modifier.padding(top = MM.space.xs)) {
                Text(
                    text = text,
                    style = if (block.type == NoteBlockType.HEADING_1) {
                        MM.type.heading.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MM.type.bodyStrong
                    },
                    color = c.ink
                )
            }
        }

        NoteBlockType.CALLOUT -> {
            val kind = block.payload[NoteBlock.PAYLOAD_CALLOUT_KIND].orEmpty()
            val title = block.payload[NoteBlock.PAYLOAD_CALLOUT_TITLE]
                ?: block.payload[NoteBlock.PAYLOAD_ENRICH_LABEL]
                ?: when (kind) {
                    "EXAM_TIP" -> "Exam Hint"
                    "DEFINITION" -> "Key Definition"
                    else -> "Tutor Unpack"
                }
            val isEnriched = block.source == BlockSource.ENRICHED
            val calloutColor = when (kind) {
                "EXAM_TIP" -> LearningFixedColors.MasteryDeveloping
                "DEFINITION" -> c.accent
                else -> LearningFixedColors.EnrichmentAccent
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = MM.motion.standard())
                    .clip(MM.radius.small)
                    .background(calloutColor.copy(alpha = 0.10f))
                    .border(MMSize.hairline, calloutColor.copy(alpha = 0.35f), MM.radius.small)
                    .then(if (isEnriched) Modifier.clickable(onClick = onToggleUnpack) else Modifier)
                    .padding(MM.space.m)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                        ) {
                            Icon(
                                imageVector = if (kind == "EXAM_TIP") Icons.Rounded.Star else Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = calloutColor,
                                modifier = Modifier.size(MM.space.m)
                            )
                            Text(
                                text = title,
                                style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                color = calloutColor
                            )
                        }
                        if (isEnriched) {
                            Icon(
                                imageVector = if (isUnpacked) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (isUnpacked) "Collapse" else "Expand",
                                tint = calloutColor,
                                modifier = Modifier.size(MM.space.l)
                            )
                        }
                    }

                    if (!isEnriched || isUnpacked) {
                        Text(
                            text = text,
                            style = MM.type.body,
                            color = c.ink
                        )
                    } else {
                        Text(
                            text = "Tap to unpack step-by-step explanation…",
                            style = MM.type.caption.copy(fontStyle = FontStyle.Italic),
                            color = c.inkSecondary
                        )
                    }
                }
            }
        }

        NoteBlockType.MATH -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MM.radius.small)
                    .background(c.surfaceSunk)
                    .padding(MM.space.m)
            ) {
                val parsed = remember(text) { runCatching { LatexMathParser.parse(text) }.getOrNull() }
                if (parsed != null) {
                    MathExpressionView(
                        node = parsed,
                        fontSize = MM.type.body.fontSize,
                        inkColor = c.ink
                    )
                } else {
                    Text(text = text, style = MM.type.bodyStrong, color = c.ink)
                }
            }
        }

        NoteBlockType.BULLET, NoteBlockType.NUMBERED, NoteBlockType.CHECKLIST -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (block.type == NoteBlockType.CHECKLIST) (if (block.checked) "☑" else "☐") else "•",
                    style = MM.type.bodyStrong,
                    color = c.accent
                )
                Text(
                    text = text,
                    style = MM.type.body,
                    color = c.ink,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        NoteBlockType.QUOTE -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MM.radius.small)
                    .background(c.surfaceSunk)
                    .padding(MM.space.s),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Box(
                    modifier = Modifier
                        .width(MM.space.xs)
                        .height(MM.space.xl)
                        .background(c.accent)
                )
                Text(
                    text = text,
                    style = MM.type.body.copy(fontStyle = FontStyle.Italic),
                    color = c.inkSecondary
                )
            }
        }

        NoteBlockType.DIVIDER -> {
            HorizontalDivider(color = c.line)
        }

        else -> {
            Text(
                text = text,
                style = MM.type.body,
                color = c.ink
            )
        }
    }
}

@Composable
private fun RecordedLectureTranscriptCard(
    segments: List<TranscriptSegment>
) {
    val c = MM.colors
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredSegments = remember(segments, searchQuery) {
        if (searchQuery.isBlank()) segments
        else {
            val q = searchQuery.trim().lowercase()
            segments.filter {
                it.text.lowercase().contains(q) || (it.speakerName?.lowercase()?.contains(q) == true)
            }
        }
    }
    val previewSegments = if (expanded || searchQuery.isNotBlank()) filteredSegments else filteredSegments.take(5)

    MMCard(
        modifier = Modifier.animateContentSize(animationSpec = MM.motion.standard()),
        contentPadding = MM.space.l
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = LearningFixedColors.MasteryStrong,
                        modifier = Modifier.size(MM.space.l)
                    )
                    Column {
                        Text(
                            text = "Recorded Lecture Transcript",
                            style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                            color = c.ink
                        )
                        Text(
                            text = "${segments.size} spoken segments with timestamps",
                            style = MM.type.caption,
                            color = c.inkSecondary
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Show less" else "Show all",
                    tint = c.inkSecondary
                )
            }

            if (segments.size > 3) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search lecture transcript…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = c.line)

            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                previewSegments.forEach { seg ->
                    val totalSecs = (seg.startMs / 1000).coerceAtLeast(0)
                    val mins = totalSecs / 60
                    val secs = totalSecs % 60
                    val timeLabel = "%02d:%02d".format(mins, secs)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.small)
                                .background(c.surfaceSunk)
                                .padding(horizontal = MM.space.xs, vertical = MM.space.xs / 2)
                        ) {
                            Text(
                                text = timeLabel,
                                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = c.accent
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (!seg.speakerName.isNullOrBlank()) {
                                Text(
                                    text = seg.speakerName,
                                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                    color = c.inkSecondary
                                )
                            }
                            Text(
                                text = seg.text,
                                style = MM.type.body,
                                color = c.ink
                            )
                        }
                    }
                }
            }

            if (segments.size > 5) {
                SecondaryButton(
                    text = if (expanded) "Show Less Transcript" else "Show All ${segments.size} Segments",
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ConceptCard(
    concept: LearningConcept,
    visual: LearningVisualSpec? = null,
    isGeneratingVisual: Boolean = false,
    visualError: String? = null,
    simpleExplanation: SimpleExplanation? = null,
    isExplainingSimple: Boolean = false,
    isSavedToNote: Boolean = false,
    onGenerateVisual: (LearningVisualKind) -> Unit = {},
    onExplainSimply: () -> Unit = {},
    onSaveExplanationToNote: (SimpleExplanation) -> Unit = {},
    onToggleImportance: () -> Unit = {},
    onPracticeConcept: (() -> Unit)? = null
) {
    val c = MM.colors
    val context = LocalContext.current
    var showVisualSection by remember { mutableStateOf(false) }
    var showSimpleSection by remember { mutableStateOf(false) }
    var selectedVisualKind by remember { mutableStateOf(LearningVisualKind.AUTO) }

    val importanceColor = when (concept.examImportance) {
        ExamImportance.LECTURER_FLAGGED -> LearningFixedColors.MasteryNeedsReview
        ExamImportance.HIGH_YIELD -> LearningFixedColors.MasteryDeveloping
        ExamImportance.NORMAL -> c.inkSecondary
    }
    val masteryColor = when (concept.state) {
        LearningMasteryState.STRONG -> LearningFixedColors.MasteryStrong
        LearningMasteryState.DEVELOPING, LearningMasteryState.LEARNING -> LearningFixedColors.MasteryDeveloping
        LearningMasteryState.NEEDS_REVIEW, LearningMasteryState.NEW -> LearningFixedColors.MasteryNeedsReview
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
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Box(
                        modifier = Modifier
                            .clip(MM.radius.small)
                            .background(masteryColor.copy(alpha = 0.14f))
                            .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                    ) {
                        Text(
                            text = concept.state.label,
                            style = MM.type.overline,
                            color = masteryColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(MM.radius.small)
                            .background(importanceColor.copy(alpha = 0.15f))
                            .clickable(onClick = onToggleImportance)
                            .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                    ) {
                        Text(
                            text = concept.examImportance.label,
                            style = MM.type.overline,
                            color = importanceColor
                        )
                    }
                }
            }

            if (concept.definition.isNotBlank()) {
                Text(
                    text = concept.definition,
                    style = MM.type.body,
                    color = c.ink
                )
            }

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

            // Clean 2-button pill row: "Visualize" & "Explain Simply"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                // Visualize Pill
                Box(
                    modifier = Modifier
                        .clip(MM.radius.pill)
                        .background(if (showVisualSection) c.accent else c.accentWash)
                        .clickable {
                            if (!showVisualSection) {
                                showVisualSection = true
                                if (visual == null && !isGeneratingVisual) {
                                    onGenerateVisual(selectedVisualKind)
                                }
                            } else {
                                showVisualSection = false
                            }
                        }
                        .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (showVisualSection) c.onAccent else c.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isGeneratingVisual) "Visualizing…" else "Visualize",
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = if (showVisualSection) c.onAccent else c.accent
                        )
                    }
                }

                // Explain Simply Pill
                Box(
                    modifier = Modifier
                        .clip(MM.radius.pill)
                        .background(if (showSimpleSection) c.accent else c.accentWash)
                        .clickable {
                            if (!showSimpleSection) {
                                showSimpleSection = true
                                if (simpleExplanation == null && !isExplainingSimple) {
                                    onExplainSimply()
                                }
                            } else {
                                showSimpleSection = false
                            }
                        }
                        .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = if (showSimpleSection) c.onAccent else c.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isExplainingSimple) "Explaining…" else "Explain Simply",
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = if (showSimpleSection) c.onAccent else c.accent
                        )
                    }
                }
            }

            // Visual Section inside AnimatedVisibility
            AnimatedVisibility(visible = showVisualSection || isGeneratingVisual) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    // Compact horizontal kind selector row: Auto, Graph, Chart, Flow, Image
                    val visualKinds = remember {
                        listOf(
                            "Auto" to LearningVisualKind.AUTO,
                            "Graph" to LearningVisualKind.FUNCTION_PLOT,
                            "Chart" to LearningVisualKind.BAR_CHART,
                            "Flow" to LearningVisualKind.MERMAID_DIAGRAM,
                            "Image" to LearningVisualKind.AI_IMAGE
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visualKinds.forEach { (label, kind) ->
                            val isSelected = (selectedVisualKind == kind)
                            Box(
                                modifier = Modifier
                                    .clip(MM.radius.pill)
                                    .background(if (isSelected) c.accent else c.surfaceSunk)
                                    .clickable {
                                        selectedVisualKind = kind
                                        onGenerateVisual(kind)
                                    }
                                    .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                            ) {
                                Text(
                                    text = label,
                                    style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (isSelected) c.onAccent else c.inkSecondary
                                )
                            }
                        }
                    }

                    if (isGeneratingVisual) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MM.radius.card)
                                .background(c.surfaceSunk)
                                .padding(MM.space.m),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = c.accent
                            )
                            Spacer(Modifier.width(MM.space.s))
                            Text(
                                text = if (selectedVisualKind == LearningVisualKind.AI_IMAGE) "Generating AI diagram illustration…" else "Synthesizing concept visual…",
                                style = MM.type.caption,
                                color = c.inkSecondary
                            )
                        }
                    }

                    if (!isGeneratingVisual && visualError != null && visual == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MM.radius.card)
                                .background(LearningFixedColors.PitfallAccent.copy(alpha = 0.12f))
                                .border(MMSize.hairline, LearningFixedColors.PitfallAccent.copy(alpha = 0.4f), MM.radius.card)
                                .padding(MM.space.s)
                        ) {
                            Text(
                                text = visualError,
                                style = MM.type.caption,
                                color = LearningFixedColors.PitfallAccent
                            )
                        }
                    }

                    if (!isGeneratingVisual && visual != null) {
                        LearningVisualCard(
                            spec = visual,
                            onShareImage = { shareVisualSpecAsPng(context, it) }
                        )
                    }
                }
            }

            // Simple Explanation Section inside AnimatedVisibility
            AnimatedVisibility(visible = showSimpleSection || isExplainingSimple) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    if (isExplainingSimple) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MM.radius.card)
                                .background(c.surfaceSunk)
                                .padding(MM.space.m),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = c.accent
                            )
                            Spacer(Modifier.width(MM.space.s))
                            Text(
                                text = "Explaining simply (Feynman technique)…",
                                style = MM.type.caption,
                                color = c.inkSecondary
                            )
                        }
                    }

                    if (!isExplainingSimple && simpleExplanation != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MM.radius.card)
                                .background(c.accentWash)
                                .border(MMSize.hairline, c.accent.copy(alpha = 0.35f), MM.radius.card)
                                .padding(MM.space.m)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lightbulb,
                                        contentDescription = null,
                                        tint = c.accent,
                                        modifier = Modifier.size(MM.space.m)
                                    )
                                    Text(
                                        text = "Explain Simply (ELI12)",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                        color = c.accent
                                    )
                                }

                                if (simpleExplanation.analogy.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs / 2)) {
                                        Text(
                                            text = "Intuitive Analogy",
                                            style = MM.type.overline,
                                            color = c.accent
                                        )
                                        Text(
                                            text = simpleExplanation.analogy,
                                            style = MM.type.body,
                                            color = c.ink
                                        )
                                    }
                                }

                                if (simpleExplanation.everydayExample.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs / 2)) {
                                        Text(
                                            text = "Everyday Example",
                                            style = MM.type.overline,
                                            color = c.accent
                                        )
                                        Text(
                                            text = simpleExplanation.everydayExample,
                                            style = MM.type.body,
                                            color = c.inkSecondary
                                        )
                                    }
                                }

                                if (simpleExplanation.keyTakeaway.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs / 2)) {
                                        Text(
                                            text = "Key Takeaway",
                                            style = MM.type.overline,
                                            color = c.accent
                                        )
                                        Text(
                                            text = simpleExplanation.keyTakeaway,
                                            style = MM.type.bodyStrong,
                                            color = c.ink
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(MM.radius.pill)
                                            .background(if (isSavedToNote) LearningFixedColors.MasteryStrong.copy(alpha = 0.16f) else c.surface)
                                            .clickable(enabled = !isSavedToNote) { onSaveExplanationToNote(simpleExplanation) }
                                            .padding(horizontal = MM.space.m, vertical = MM.space.xs / 2)
                                    ) {
                                        Text(
                                            text = if (isSavedToNote) "✓ Saved to Lecture Note" else "+ Save Intuition to Note",
                                            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSavedToNote) LearningFixedColors.MasteryStrong else c.accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (onPracticeConcept != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Practice Concept →",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = c.accent,
                        modifier = Modifier.clickable(onClick = onPracticeConcept)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(
    activity: LearningActivity,
    onClick: () -> Unit = {}
) {
    val c = MM.colors
    var showAnswerPeek by remember { mutableStateOf(false) }

    MMCard(
        contentPadding = MM.space.m,
        modifier = Modifier
            .animateContentSize(animationSpec = MM.motion.standard())
            .clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activity.type.label.uppercase(),
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
                text = activity.prompt.replace("**", ""),
                style = MM.type.bodyStrong,
                color = c.ink
            )
            if (activity.expectedAnswer.isNotBlank()) {
                AnimatedVisibility(
                    visible = showAnswerPeek,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MM.space.xs)
                            .clip(MM.radius.small)
                            .background(c.surfaceSunk)
                            .padding(MM.space.s)
                    ) {
                        Text(
                            text = activity.expectedAnswer.replace("**", ""),
                            style = MM.type.caption,
                            color = c.ink
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = MM.space.xs / 2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAnswerPeek) "Hide Answer" else "Peek Answer",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = c.inkSecondary,
                        modifier = Modifier.clickable { showAnswerPeek = !showAnswerPeek }
                    )
                    Text(
                        text = "Practice Card →",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = c.accent,
                        modifier = Modifier.clickable(onClick = onClick)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    session: LearningSession,
    concepts: List<LearningConcept> = emptyList(),
    activities: List<LearningActivity> = emptyList(),
    attemptsCount: Int = 0,
    onTogglePause: () -> Unit,
    onStartPractice: () -> Unit = {},
    onAskTutor: () -> Unit = {}
) {
    val c = MM.colors
    val strongCount = concepts.count { it.state == LearningMasteryState.STRONG }
    val developingCount = concepts.count { it.state == LearningMasteryState.DEVELOPING || it.state == LearningMasteryState.LEARNING }
    val reviewCount = concepts.count { it.state == LearningMasteryState.NEEDS_REVIEW || it.state == LearningMasteryState.NEW }
    val totalConcepts = concepts.size.coerceAtLeast(1)
    val masteryPercent = if (concepts.isNotEmpty()) {
        (((strongCount * 100f) + (developingCount * 55f) + (reviewCount * 15f)) / totalConcepts).toInt().coerceIn(0, 100)
    } else 0

    val flashcardCount = activities.count { it.type == LearningActivityType.RECALL }
    val quizCount = activities.count {
        it.type in setOf(LearningActivityType.MULTIPLE_CHOICE, LearningActivityType.WORKED_PROBLEM, LearningActivityType.APPLICATION)
    }
    val teachBackCount = activities.count { it.type == LearningActivityType.TEACH_BACK }

    MMCard(contentPadding = MM.space.l) {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Session Intelligence & Signals", style = MM.type.heading.copy(fontWeight = FontWeight.Bold), color = c.ink)
                    Text(
                        text = if (concepts.isNotEmpty()) "$masteryPercent% concept readiness across ${concepts.size} concepts" else "Session telemetry & spaced review controls",
                        style = MM.type.caption,
                        color = c.inkSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(MM.radius.pill)
                        .background(
                            if (session.status == "ACTIVE") LearningFixedColors.MasteryStrong.copy(alpha = 0.14f)
                            else LearningFixedColors.MasteryDeveloping.copy(alpha = 0.14f)
                        )
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                ) {
                    Text(
                        text = session.status,
                        style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                        color = if (session.status == "ACTIVE") LearningFixedColors.MasteryStrong else LearningFixedColors.MasteryDeveloping
                    )
                }
            }

            if (concepts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MM.radius.pill)
                            .background(c.surfaceSunk)
                    ) {
                        if (strongCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(strongCount.toFloat())
                                    .height(8.dp)
                                    .background(LearningFixedColors.MasteryStrong)
                            )
                        }
                        if (developingCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(developingCount.toFloat())
                                    .height(8.dp)
                                    .background(LearningFixedColors.MasteryDeveloping)
                            )
                        }
                        if (reviewCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(reviewCount.toFloat())
                                    .height(8.dp)
                                    .background(LearningFixedColors.MasteryNeedsReview)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$strongCount Strong", style = MM.type.overline, color = LearningFixedColors.MasteryStrong)
                        Text("$developingCount Developing", style = MM.type.overline, color = LearningFixedColors.MasteryDeveloping)
                        Text("$reviewCount To Review", style = MM.type.overline, color = LearningFixedColors.MasteryNeedsReview)
                    }
                }
            }

            if (activities.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    MasteryStatPill(
                        label = "Flashcards",
                        count = flashcardCount,
                        color = c.accent,
                        modifier = Modifier.weight(1f)
                    )
                    MasteryStatPill(
                        label = "Problems",
                        count = quizCount,
                        color = LearningFixedColors.MasteryDeveloping,
                        modifier = Modifier.weight(1f)
                    )
                    MasteryStatPill(
                        label = "Teach-Back",
                        count = teachBackCount,
                        color = LearningFixedColors.MasteryStrong,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = c.line)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Exam Signals Detected", style = MM.type.secondary, color = c.ink)
                Text(
                    "${maxOf(session.examSignalCount, concepts.count { it.examImportance != ExamImportance.NORMAL })}",
                    style = MM.type.bodyStrong,
                    color = c.accent
                )
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
                Text("Active Recall Attempts", style = MM.type.secondary, color = c.ink)
                Text("$attemptsCount", style = MM.type.bodyStrong, color = c.ink)
            }

            HorizontalDivider(color = c.line)

            if (activities.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    PrimaryButton(
                        text = "Practice Session",
                        onClick = onStartPractice,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Ask Tutor",
                        onClick = onAskTutor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SecondaryButton(
                text = if (session.status == "ACTIVE") "Pause Spaced Reviews" else "Resume Spaced Reviews",
                onClick = onTogglePause,
                modifier = Modifier.fillMaxWidth().testTag("toggle_session_pause")
            )
        }
    }
}

@Composable
private fun CourseFromLectureDialog(
    session: LearningSession,
    existingCourses: List<LearningRepository.LearningCourse>,
    onDismiss: () -> Unit,
    onCreateNewCourse: (name: String, code: String, domain: SubjectDomain) -> Unit,
    onAssignExisting: (courseId: String) -> Unit
) {
    val c = MM.colors
    var courseName by remember(session.title) {
        mutableStateOf(
            session.courseName?.takeIf { !it.equals("Course", true) }
                ?: session.title.ifBlank { "New Course" }
        )
    }
    var courseCode by remember(session.domain) {
        mutableStateOf("${session.domain.shortLabel.take(4).uppercase()} 101")
    }
    var selectedDomain by remember(session.domain) { mutableStateOf(session.domain) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Link Lecture to Course", style = MM.type.heading.copy(fontWeight = FontWeight.Bold), color = c.ink)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                if (existingCourses.isNotEmpty()) {
                    Text(
                        text = "Assign to an existing course:",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = c.inkSecondary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        existingCourses.take(4).forEach { course ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MM.radius.small)
                                    .background(c.surfaceSunk)
                                    .clickable { onAssignExisting(course.notebook.id) }
                                    .padding(horizontal = MM.space.m, vertical = MM.space.s),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.notebook.name, style = MM.type.bodyStrong, color = c.ink)
                                    if (course.properties.courseCode.isNotBlank()) {
                                        Text(course.properties.courseCode, style = MM.type.caption, color = c.inkSecondary)
                                    }
                                }
                                Text("Assign →", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent)
                            }
                        }
                    }
                    HorizontalDivider(color = c.line)
                }

                Text(
                    text = "Or create a new course from this lecture:",
                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = c.inkSecondary
                )
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = courseCode,
                    onValueChange = { courseCode = it },
                    label = { Text("Course Code (e.g. MATH 201)") },
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
                        val selected = dom == selectedDomain
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(if (selected) c.accent else c.surfaceSunk)
                                .clickable { selectedDomain = dom }
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs / 2)
                        ) {
                            Text(
                                text = dom.shortLabel,
                                style = MM.type.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                color = if (selected) c.onAccent else c.inkSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (courseName.isNotBlank()) {
                        onCreateNewCourse(courseName.trim(), courseCode.trim(), selectedDomain)
                    }
                }
            ) {
                Text("Create Course")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddConceptDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, definition: String, formula: String?, importance: ExamImportance) -> Unit
) {
    val c = MM.colors
    var name by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf("") }
    var formula by remember { mutableStateOf("") }
    var importance by remember { mutableStateOf(ExamImportance.HIGH_YIELD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Key Concept", style = MM.type.heading.copy(fontWeight = FontWeight.Bold), color = c.ink)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Concept Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = definition,
                    onValueChange = { definition = it },
                    label = { Text("Definition / Intuition") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formula,
                    onValueChange = { formula = it },
                    label = { Text("Formula / LaTeX (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    ExamImportance.entries.forEach { imp ->
                        val isSelected = imp == importance
                        Box(
                            modifier = Modifier
                                .clip(MM.radius.pill)
                                .background(if (isSelected) c.accent else c.surfaceSunk)
                                .clickable { importance = imp }
                                .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                        ) {
                            Text(
                                text = imp.label,
                                style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                color = if (isSelected) c.onAccent else c.inkSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, definition, formula.takeIf { it.isNotBlank() }, importance)
                    }
                }
            ) {
                Text("Add & Create Flashcard")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
