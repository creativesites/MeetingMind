package com.craftflowtechnologies.meetingmind.feature.learning.space

import com.craftflowtechnologies.meetingmind.core.aireport.AiFeature
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSheetHost
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource
import com.craftflowtechnologies.meetingmind.core.aireport.ReportAiMenuItem
import com.craftflowtechnologies.meetingmind.core.aireport.ReportAiAction
import com.craftflowtechnologies.meetingmind.core.aireport.rememberAiReportState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackController
import java.io.File
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.feature.learning.components.CitationSheet
import com.craftflowtechnologies.meetingmind.feature.learning.components.EvidenceQuote
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryBar
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryDisplayOrder
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryGlyph
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryLabel
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryLegend
import com.craftflowtechnologies.meetingmind.feature.learning.components.NextStepStrip
import com.craftflowtechnologies.meetingmind.feature.learning.components.formatLectureTimestamp
import com.craftflowtechnologies.meetingmind.feature.learning.components.isShowable
import com.craftflowtechnologies.meetingmind.feature.learning.components.plainReason
import com.craftflowtechnologies.meetingmind.ui.theme.LearningTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

/** The four session tabs, in the order of the learning loop (design spec §4.3). */
enum class SessionPane(val label: String) { SOURCE("Source"), UNDERSTAND("Understand"), PRACTISE("Practise"), MASTERY("Mastery") }

/** Below this ASR confidence a transcript span is treated as unclear audio. */
private const val UnclearConfidence = 0.6f

/**
 * One Learning Session: Source · Understand · Practise · Mastery, with a Next step strip that
 * follows the evidence. Every claim can be opened at its lecture moment.
 */
@Composable
fun SessionStudioScreen(
    noteId: String,
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    onOpenNotes: (noteId: String) -> Unit,
    onPlayMoment: (meetingId: String, startMs: Long) -> Unit,
    onStartPractice: () -> Unit
) {
    val detail by viewModel.activeSessionDetail.collectAsState()
    val loading by viewModel.isLoadingSession.collectAsState()
    val loadError by viewModel.sessionLoadError.collectAsState()
    val segments by viewModel.activeSessionSegments.collectAsState()
    val meetingId by viewModel.activeSessionMeetingId.collectAsState()
    val document by viewModel.activeSessionDocument.collectAsState()
    val lectures by viewModel.lectureNotes.collectAsState()
    val building by viewModel.isSynthesizingSession.collectAsState()
    val practiceLoading by viewModel.isLoadingPractice.collectAsState()
    val practiceError by viewModel.practiceError.collectAsState()

    var pane by rememberSaveable { mutableStateOf(SessionPane.UNDERSTAND) }
    var openConceptId by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightSegmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var citation by remember { mutableStateOf<LearningEvidence?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var askOpen by rememberSaveable { mutableStateOf(false) }
    var askPrefill by rememberSaveable { mutableStateOf<String?>(null) }

    // Only show the detail that belongs to this route, never a previous session's.
    val current = detail?.takeIf { it.session.noteId == noteId || it.session.id == noteId }

    fun openAsk(prefill: String? = null) {
        val c = current ?: return
        viewModel.openAsk(c.session.id)
        askPrefill = prefill
        askOpen = true
    }

    fun showInTranscript(ev: LearningEvidence) {
        citation = null
        openConceptId = null
        highlightSegmentId = ev.segmentIds.firstOrNull()
            ?: ev.startMs?.let { ms -> segments.minByOrNull { kotlin.math.abs(it.startMs - ms) }?.id }
        pane = SessionPane.SOURCE
    }

    Box(Modifier.fillMaxSize().background(MM.colors.background).testTag("learning_session_studio")) {
        when {
            current == null && loadError != null -> SessionMessage(onBack) {
                StatusLine(
                    StatusKind.Error, loadError!!,
                    actionLabel = "Try again", onAction = { viewModel.loadSessionDetail(noteId) }
                )
            }
            current == null -> SessionMessage(onBack) {
                StatusLine(StatusKind.Info, if (loading) "Opening your session…" else "Preparing your session…")
            }
            else -> {
                val d = current
                val concepts = d.concepts.filter { !it.isDismissed }
                val openConcept = openConceptId?.let { id -> concepts.firstOrNull { it.id == id } }
                if (openConcept != null) {
                    BackHandler { openConceptId = null }
                    ConceptDetail(
                        concept = openConcept,
                        detail = d,
                        courseName = d.session.courseName,
                        onBack = { openConceptId = null },
                        onCite = { citation = it },
                        onShowInTranscript = ::showInTranscript,
                        onStrengthen = {
                            viewModel.startConceptPractice(d.session.id, openConcept.id) { onStartPractice() }
                        },
                        onAsk = { openAsk("Explain ${openConcept.name} in simple terms.") },
                        onRename = { newName ->
                            val old = openConcept
                            viewModel.renameConcept(old, newName)
                            scope.launch {
                                if (snackbar.showSnackbar("Renamed to “$newName”", actionLabel = "Undo") == SnackbarResult.ActionPerformed) {
                                    viewModel.renameConcept(old.copy(name = newName), old.name)
                                }
                            }
                        },
                        onHide = {
                            val c = openConcept
                            openConceptId = null
                            viewModel.setConceptHidden(c, true)
                            scope.launch {
                                if (snackbar.showSnackbar("${c.name} is hidden from practice", actionLabel = "Undo") == SnackbarResult.ActionPerformed) {
                                    viewModel.setConceptHidden(c.copy(isDismissed = true), false)
                                }
                            }
                        },
                        onReport = {
                            val c = openConcept
                            viewModel.setConceptReported(c, !c.isStale)
                            scope.launch {
                                val msg = if (c.isStale) "Questions on ${c.name} are back in practice"
                                else "Thanks. Questions on ${c.name} are held back until it's fixed."
                                if (snackbar.showSnackbar(msg, actionLabel = "Undo") == SnackbarResult.ActionPerformed) {
                                    viewModel.setConceptReported(c.copy(isStale = !c.isStale), c.isStale)
                                }
                            }
                        }
                    )
                } else {
                    SessionBody(
                        detail = d,
                        concepts = concepts,
                        pane = pane,
                        onPane = { pane = it },
                        segments = segments,
                        highlightSegmentId = highlightSegmentId,
                        summary = lectures.firstOrNull { it.noteId == d.session.noteId }?.summary?.takeIf { it.isNotBlank() }
                            ?: document?.note?.plainText?.trim()?.take(700)?.takeIf { it.isNotBlank() },
                        building = building,
                        practiceBusy = practiceLoading,
                        practiceError = practiceError,
                        onBack = onBack,
                        onOpenNotes = { onOpenNotes(d.session.noteId) },
                        onAsk = { openAsk() },
                        onTogglePause = { viewModel.toggleSessionPause(d.session.id) },
                        onOpenConcept = { openConceptId = it.id },
                        onSegment = { seg ->
                            highlightSegmentId = seg.id
                            // Play the line right here; open the recording only if its audio is missing.
                            meetingId?.let { mid ->
                                scope.launch {
                                    val audio = viewModel.citationAudioFor(mid)
                                    if (audio != null) {
                                        PlaybackController.playAt(context, audio.recordingId, audio.title, File(audio.filePath), seg.startMs)
                                    } else {
                                        onPlayMoment(mid, seg.startMs)
                                    }
                                }
                            }
                        },
                        onNextStep = { step ->
                            when (step.kind) {
                                NextStepKind.DIAGNOSTIC -> viewModel.startDiagnostic(d.session.id) { onStartPractice() }
                                NextStepKind.STRENGTHEN, NextStepKind.LEARN_NEW -> step.concept?.let { c ->
                                    viewModel.startConceptPractice(d.session.id, c.id) { onStartPractice() }
                                }
                                NextStepKind.REVIEW -> viewModel.startSessionPractice(d.session.id, dueOnly = true) { onStartPractice() }
                                NextStepKind.DONE -> pane = SessionPane.MASTERY
                                NextStepKind.EMPTY -> pane = SessionPane.UNDERSTAND
                            }
                        },
                        onStartDiagnostic = { viewModel.startDiagnostic(d.session.id) { onStartPractice() } },
                        onPractiseAll = { due -> viewModel.startSessionPractice(d.session.id, dueOnly = due) { onStartPractice() } },
                        onStrengthen = { c -> viewModel.startConceptPractice(d.session.id, c.id) { onStartPractice() } }
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    citation?.let { ev ->
        CitationSheet(
            evidence = ev,
            sourceTitle = current?.session?.title ?: "This lecture",
            onDismiss = { citation = null },
            resolveAudio = viewModel::citationAudioFor,
            fallbackMeetingId = meetingId,
            onShowInTranscript = if (segments.isNotEmpty()) {
                { showInTranscript(ev) }
            } else {
                { citation = null; current?.let { onOpenNotes(it.session.noteId) } }
            }
        )
    }

    val askSession = current
    if (askOpen && askSession != null) {
        AskLearningSheet(
            viewModel = viewModel,
            sessionId = askSession.session.id,
            hasCourse = !askSession.session.courseName.isNullOrBlank(),
            concepts = askSession.concepts,
            onDismiss = { askOpen = false; askPrefill = null },
            onStartPractice = onStartPractice,
            onShowInTranscript = { ev -> askOpen = false; showInTranscript(ev) },
            initialQuestion = askPrefill
        )
    }
}

@Composable
private fun SessionMessage(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MM.colors.ink)
        }
        Column(
            Modifier.fillMaxSize().padding(LearningTheme.readingGutter),
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
private fun SessionBody(
    detail: LearningRepository.LearningSessionDetail,
    concepts: List<LearningConcept>,
    pane: SessionPane,
    onPane: (SessionPane) -> Unit,
    segments: List<TranscriptSegment>,
    highlightSegmentId: String?,
    summary: String?,
    building: Boolean,
    practiceBusy: Boolean,
    practiceError: String?,
    onBack: () -> Unit,
    onOpenNotes: () -> Unit,
    onAsk: () -> Unit,
    onTogglePause: () -> Unit,
    onOpenConcept: (LearningConcept) -> Unit,
    onSegment: (TranscriptSegment) -> Unit,
    onNextStep: (NextStep) -> Unit,
    onStartDiagnostic: () -> Unit,
    onPractiseAll: (dueOnly: Boolean) -> Unit,
    onStrengthen: (LearningConcept) -> Unit
) {
    val session = detail.session
    val step = remember(detail) { nextStepFor(detail) }
    val listState = rememberLazyListState()
    val highlightIndex = segments.indexOfFirst { it.id == highlightSegmentId }
    // Rows above the transcript: the paused and practice status lines, then the quality line.
    val sourceOffset = listOf(session.isPaused, practiceError != null, practiceBusy).count { it } + 1
    LaunchedEffect(pane, highlightIndex) {
        if (pane == SessionPane.SOURCE && highlightIndex >= 0) listState.animateScrollToItem(highlightIndex + sourceOffset)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MM.colors.ink)
            }
            Column(Modifier.weight(1f)) {
                session.courseName?.takeIf { it.isNotBlank() }?.let {
                    Text("$it ›", style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 1)
                }
                Text(
                    session.title, style = MM.type.heading, color = MM.colors.ink, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }
                )
            }
            TextAction("Ask", onClick = onAsk, modifier = Modifier.testTag("session_ask"))
            SessionMenu(paused = session.isPaused, onOpenNotes = onOpenNotes, onAsk = onAsk, onTogglePause = onTogglePause)
        }
        Column(Modifier.padding(horizontal = LearningTheme.readingGutter, vertical = MM.space.s)) {
            NextStepStrip(title = step.title, detail = step.detail, onClick = { onNextStep(step) })
        }
        SessionTabs(selected = pane, onSelect = onPane, dueDot = remember(detail) { dueCount(detail) > 0 })

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = LearningTheme.readingGutter, end = LearningTheme.readingGutter, top = MM.space.l, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            if (session.isPaused) item {
                StatusLine(StatusKind.Info, "Reviews for this session are paused.", actionLabel = "Resume", onAction = onTogglePause)
            }
            if (practiceError != null) item { StatusLine(StatusKind.Error, practiceError) }
            if (practiceBusy) item { StatusLine(StatusKind.Info, "Getting your questions ready…") }
            when (pane) {
                SessionPane.UNDERSTAND -> understandPane(summary, building, concepts, onOpenConcept, onOpenNotes, noteId = detail.session.noteId)
                SessionPane.SOURCE -> sourcePane(segments, highlightSegmentId, onSegment, onOpenNotes)
                SessionPane.PRACTISE -> practisePane(detail, concepts, onStartDiagnostic, onPractiseAll, onStrengthen)
                SessionPane.MASTERY -> masteryPane(detail, concepts, onOpenConcept, session.isPaused, onTogglePause)
            }
        }
    }
}

private fun dueCount(detail: LearningRepository.LearningSessionDetail): Int {
    val now = System.currentTimeMillis()
    val ids = practiceItems(detail).map { it.id }.toSet()
    return detail.schedules.count { !it.isPaused && it.dueAt <= now && it.activityId in ids }
}

@Composable
private fun SessionMenu(paused: Boolean, onOpenNotes: () -> Unit, onAsk: () -> Unit, onTogglePause: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Session options", tint = MM.colors.inkSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Open full notes", style = MM.type.body) }, onClick = { open = false; onOpenNotes() })
            DropdownMenuItem(text = { Text("Ask about this session", style = MM.type.body) }, onClick = { open = false; onAsk() })
            DropdownMenuItem(
                text = { Text(if (paused) "Resume reviews" else "Pause reviews", style = MM.type.body) },
                onClick = { open = false; onTogglePause() }
            )
        }
    }
}

/** Underline tabs with tab semantics. A dot on Practise has a text alternative. */
@Composable
private fun SessionTabs(selected: SessionPane, onSelect: (SessionPane) -> Unit, dueDot: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MM.space.s).selectableGroup().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            SessionPane.entries.forEach { p ->
                val on = p == selected
                Column(
                    Modifier
                        .heightIn(min = MMSize.minTouch)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(p) })
                        .padding(horizontal = MM.space.m)
                        .testTag("session_tab_${p.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Spacer(Modifier.height(MM.space.m))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, style = MM.type.bodyStrong, color = if (on) MM.colors.ink else MM.colors.inkSecondary)
                        if (p == SessionPane.PRACTISE && dueDot) {
                            Spacer(Modifier.width(MM.space.xs))
                            Box(
                                Modifier.size(6.dp).clip(MM.radius.pill).background(MM.colors.accent)
                                    .semantics { this.contentDescription = "reviews due" }
                            )
                        }
                    }
                    Spacer(Modifier.height(MM.space.s))
                    Box(Modifier.width(32.dp).height(2.dp).background(if (on) MM.colors.accent else MM.colors.background))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(MMSize.hairline).background(MM.colors.line))
    }
}

// ───────────────────────────── Understand ─────────────────────────────

private fun LazyListScope.understandPane(
    summary: String?,
    building: Boolean,
    concepts: List<LearningConcept>,
    onOpenConcept: (LearningConcept) -> Unit,
    onOpenNotes: () -> Unit,
    noteId: String? = null
) {
    if (building) item { StatusLine(StatusKind.Info, "Building your study guide…") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SUMMARY", style = MM.type.overline, color = MM.colors.inkMuted, modifier = Modifier.weight(1f))
                // The summary is AI-written: "Report this" sits with it (Play AI-content policy).
                if (summary != null) ReportAiAction(source = { AiReportSource(AiFeature.LECTURE_NOTES, summary, noteId = noteId) })
            }
            if (summary != null) {
                Text(summary, style = LearningTheme.type.reading, color = MM.colors.ink)
            } else {
                Text("No summary yet. Your notes are still there in full.", style = MM.type.body, color = MM.colors.inkSecondary)
            }
        }
    }
    if (concepts.isNotEmpty()) {
        item { SectionHeader("Key concepts", count = concepts.size) }
        items(concepts, key = { it.id }) { c -> ConceptRow(c, onClick = { onOpenConcept(c) }) }
    }
    item { TextAction("Open full notes", onClick = onOpenNotes) }
}

@Composable
private fun ConceptRow(concept: LearningConcept, onClick: () -> Unit) {
    MMCard(Modifier.fillMaxWidth().testTag("concept_row"), onClick = onClick, contentPadding = MM.space.m) {
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            MasteryGlyph(concept.state, size = 18.dp, modifier = Modifier.padding(top = 3.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(concept.name, style = MM.type.bodyStrong, color = MM.colors.ink, modifier = Modifier.weight(1f, fill = false))
                    if (!concept.emphasis.isNullOrBlank()) {
                        Spacer(Modifier.width(MM.space.s))
                        Text("emphasised", style = MM.type.caption, color = MM.colors.accent)
                    }
                }
                if (concept.definition.isNotBlank()) {
                    Text(concept.definition, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (concept.isStale) {
                    Text("Reported · questions held back", style = MM.type.caption, color = LearningTheme.colors.clay)
                }
            }
        }
    }
}

// ───────────────────────────── Source ─────────────────────────────

private fun LazyListScope.sourcePane(
    segments: List<TranscriptSegment>,
    highlightId: String?,
    onSegment: (TranscriptSegment) -> Unit,
    onOpenNotes: () -> Unit
) {
    if (segments.isEmpty()) {
        item {
            InsetPanel(Modifier.fillMaxWidth()) {
                Text(
                    "This session comes from typed notes, so there is no transcript. Citations point to passages in your notes.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
            }
        }
        item { SecondaryButton("Open notes", onClick = onOpenNotes) }
        return
    }
    val unclear = segments.count { (it.confidence ?: 1f) < UnclearConfidence }
    item {
        if (unclear > 0) {
            StatusLine(
                StatusKind.Warning,
                "$unclear ${if (unclear == 1) "part" else "parts"} of the audio may be unclear. They're underlined: check them against the recording before relying on them."
            )
        } else {
            Text("Tap a line to hear it.", style = MM.type.caption, color = MM.colors.inkSecondary)
        }
    }
    items(segments, key = { it.id }) { seg ->
        val on = seg.id == highlightId
        val isUnclear = (seg.confidence ?: 1f) < UnclearConfidence
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MM.radius.small)
                .background(if (on) MM.colors.accentWash else MM.colors.background)
                .clickable(role = Role.Button, onClickLabel = "Play from ${formatLectureTimestamp(seg.startMs)}") { onSegment(seg) }
                .padding(MM.space.s),
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text(
                formatLectureTimestamp(seg.startMs),
                style = LearningTheme.type.mono,
                color = if (on) MM.colors.accent else MM.colors.inkMuted,
                modifier = Modifier.width(52.dp).padding(top = 3.dp)
            )
            Column(Modifier.weight(1f)) {
                seg.speakerName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MM.type.caption, color = MM.colors.inkSecondary)
                }
                Text(
                    seg.text,
                    style = MM.type.body.copy(textDecoration = if (isUnclear) TextDecoration.Underline else null),
                    color = MM.colors.ink
                )
                if (isUnclear) Text("Unclear audio", style = MM.type.caption, color = LearningTheme.colors.clay)
            }
        }
    }
}

// ───────────────────────────── Practise ─────────────────────────────

private fun LazyListScope.practisePane(
    detail: LearningRepository.LearningSessionDetail,
    concepts: List<LearningConcept>,
    onStartDiagnostic: () -> Unit,
    onPractiseAll: (Boolean) -> Unit,
    onStrengthen: (LearningConcept) -> Unit
) {
    val practice = practiceItems(detail)
    if (practice.isEmpty()) {
        item {
            InsetPanel(Modifier.fillMaxWidth()) {
                Text("Questions appear here once the study guide is built.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
        }
        return
    }
    val due = dueCount(detail)
    if (detail.attempts.isEmpty()) {
        item {
            MMCard(Modifier.fillMaxWidth(), onClick = onStartDiagnostic) {
                Text("QUICK CHECK", style = MM.type.overline, color = MM.colors.accent)
                Text("Find out what to focus on", style = MM.type.heading, color = MM.colors.ink)
                val n = diagnosticSet(detail).size
                Text("$n questions · not graded", style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
        }
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            if (due > 0) {
                PrimaryButton("Review $due due ${if (due == 1) "card" else "cards"}", onClick = { onPractiseAll(true) }, modifier = Modifier.fillMaxWidth())
            }
            SecondaryButton(
                "Practise all ${practice.size} questions · about ${minutesFor(practice)} min",
                onClick = { onPractiseAll(false) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    val withItems = concepts.filter { c -> practice.any { it.conceptId == c.id } }
    if (withItems.isNotEmpty()) {
        item { SectionHeader("By concept", count = withItems.size) }
        items(withItems, key = { it.id }) { c ->
            val n = practice.count { it.conceptId == c.id }.coerceAtMost(3)
            MMCard(Modifier.fillMaxWidth(), onClick = { onStrengthen(c) }, contentPadding = MM.space.m) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                    MasteryGlyph(c.state, size = 18.dp)
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MM.type.bodyStrong, color = MM.colors.ink)
                        Text("$n ${if (n == 1) "question" else "questions"} · ${c.state.label}", style = MM.type.caption, color = MM.colors.inkSecondary)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Mastery ─────────────────────────────

private fun LazyListScope.masteryPane(
    detail: LearningRepository.LearningSessionDetail,
    concepts: List<LearningConcept>,
    onOpenConcept: (LearningConcept) -> Unit,
    paused: Boolean,
    onTogglePause: () -> Unit
) {
    val counts = masteryCounts(concepts)
    val last = lastAttemptByConcept(detail.attempts)
    item {
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            MasteryBar(counts)
            MasteryLegend(counts)
            Text(
                "States come from your answers over time, never from reading or from time spent.",
                style = MM.type.caption,
                color = MM.colors.inkSecondary
            )
        }
    }
    MasteryDisplayOrder.forEach { state ->
        val group = concepts.filter { it.state == state }
        if (group.isNotEmpty()) {
            item(key = "h_${state.name}") { SectionHeader(state.label, count = group.size) }
            items(group, key = { "m_${it.id}" }) { c ->
                MMCard(Modifier.fillMaxWidth(), onClick = { onOpenConcept(c) }, contentPadding = MM.space.m) {
                    Text(c.name, style = MM.type.bodyStrong, color = MM.colors.ink)
                    MasteryLabel(c.state, reason = reasonLine(c, last[c.id]))
                }
            }
        }
    }
    item {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Pause reviews for this session", style = MM.type.bodyStrong, color = MM.colors.ink)
                Text("Its cards stop appearing in Daily Recall until you resume.", style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            Switch(
                checked = paused,
                onCheckedChange = { onTogglePause() },
                colors = SwitchDefaults.colors(checkedTrackColor = MM.colors.accent, checkedThumbColor = MM.colors.onAccent)
            )
        }
    }
}

/** The reason under a state: from the last attempt when there is one, otherwise the plain rule. */
private fun reasonLine(c: LearningConcept, last: com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt?): String {
    if (last == null) return c.state.plainReason()
    val days = ((System.currentTimeMillis() - last.createdAt) / 86_400_000L).toInt()
    val whenText = when (days) {
        0 -> "today"
        1 -> "yesterday"
        else -> "$days days ago"
    }
    val outcome = if (last.isCorrect) "recalled" else "missed"
    return "Last practised $whenText · $outcome. ${c.state.plainReason()}"
}

// ───────────────────────────── Concept ─────────────────────────────

@Composable
private fun ConceptDetail(
    concept: LearningConcept,
    detail: LearningRepository.LearningSessionDetail,
    courseName: String?,
    onBack: () -> Unit,
    onCite: (LearningEvidence) -> Unit,
    onShowInTranscript: (LearningEvidence) -> Unit,
    onStrengthen: () -> Unit,
    onAsk: () -> Unit,
    onRename: (String) -> Unit,
    onHide: () -> Unit,
    onReport: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // "Report this" for the AI-written concept (Play AI-content policy). Not offered once the student has edited it.
    val reportState = rememberAiReportState()
    AiReportSheetHost(reportState)
    val last = remember(detail) { lastAttemptByConcept(detail.attempts)[concept.id] }
    val hasPractice = remember(detail, concept.id) { conceptSet(detail, concept.id).isNotEmpty() }
    val evidence = concept.evidence.filter { it.isShowable }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("concept_detail")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to session", tint = MM.colors.ink)
            }
            Text(
                listOfNotNull(courseName, detail.session.title).joinToString(" › "),
                style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Concept options", tint = MM.colors.inkSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename", style = MM.type.body) }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Hide from practice", style = MM.type.body) }, onClick = { menu = false; onHide() })
                    DropdownMenuItem(
                        text = { Text(if (concept.isStale) "Undo report" else "Report a problem", style = MM.type.body) },
                        onClick = { menu = false; onReport() }
                    )
                    if (!concept.isUserEdited) ReportAiMenuItem {
                        menu = false
                        reportState.open(
                            AiReportSource(
                                AiFeature.LECTURE_NOTES,
                                listOfNotNull(concept.name, concept.definition, concept.emphasis).filter { it.isNotBlank() }.joinToString("\n"),
                                noteId = detail.session.noteId
                            )
                        )
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = LearningTheme.readingGutter, vertical = MM.space.m),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            Text(concept.name, style = MM.type.title, color = MM.colors.ink, modifier = Modifier.semantics { heading() })
            if (concept.isUserEdited) Text("Edited by you · kept when the guide is rebuilt", style = MM.type.caption, color = MM.colors.inkMuted)
            MasteryLabel(concept.state, reason = reasonLine(concept, last))
            if (concept.definition.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth().clip(MM.radius.card).background(LearningTheme.colors.paper)
                        .border(MMSize.hairline, MM.colors.line, MM.radius.card).padding(LearningTheme.readingGutter),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Text("DEFINITION", style = MM.type.overline, color = MM.colors.inkMuted)
                    Text(concept.definition, style = LearningTheme.type.reading, color = MM.colors.ink)
                }
            }
            if (!concept.emphasis.isNullOrBlank()) {
                InsetPanel(Modifier.fillMaxWidth()) {
                    Text("EMPHASISED IN THE LECTURE", style = MM.type.overline, color = MM.colors.accent)
                    Text(concept.emphasis, style = MM.type.secondary, color = MM.colors.ink)
                }
            }
            if (evidence.isNotEmpty()) {
                Text("FROM THE LECTURE", style = MM.type.overline, color = MM.colors.inkMuted)
                evidence.forEach { ev ->
                    EvidenceQuote(evidence = ev, onOpen = { onCite(ev) }, onShowInTranscript = { onShowInTranscript(ev) })
                }
            } else {
                Text(
                    "No lecture quote is linked to this concept yet, so check it against your notes.",
                    style = MM.type.caption, color = MM.colors.inkSecondary
                )
            }
            if (concept.relationships.isNotEmpty()) {
                Text("RELATED", style = MM.type.overline, color = MM.colors.inkMuted)
                Text(concept.relationships.joinToString(" · "), style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = LearningTheme.readingGutter, vertical = MM.space.m).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            if (hasPractice) {
                PrimaryButton("Strengthen ${concept.name}", onClick = onStrengthen, modifier = Modifier.fillMaxWidth().testTag("concept_strengthen"))
            }
            TextAction("Ask about this", onClick = onAsk, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(concept.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = MM.colors.surfaceRaised,
            title = { Text("Rename concept", style = MM.type.heading, color = MM.colors.ink) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = MM.type.body.copy(color = MM.colors.ink),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MM.colors.accent, unfocusedBorderColor = MM.colors.line)
                )
            },
            confirmButton = {
                TextAction("Save", enabled = text.isNotBlank(), onClick = { renaming = false; onRename(text.trim()) })
            },
            dismissButton = { TextAction("Cancel", onClick = { renaming = false }) }
        )
    }
}
