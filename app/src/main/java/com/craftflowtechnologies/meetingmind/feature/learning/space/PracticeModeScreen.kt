package com.craftflowtechnologies.meetingmind.feature.learning.space

import com.craftflowtechnologies.meetingmind.core.aireport.AiFeature
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSheetHost
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource
import com.craftflowtechnologies.meetingmind.core.aireport.ReportAiMenuItem
import com.craftflowtechnologies.meetingmind.core.aireport.rememberAiReportState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.DeterministicSpacedScheduler
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.feature.learning.PracticeKind
import com.craftflowtechnologies.meetingmind.feature.learning.PracticeResult
import com.craftflowtechnologies.meetingmind.feature.learning.components.CitationSheet
import com.craftflowtechnologies.meetingmind.feature.learning.components.EvidenceQuote
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryGlyph
import com.craftflowtechnologies.meetingmind.feature.learning.components.MasteryLabel
import com.craftflowtechnologies.meetingmind.feature.learning.components.isShowable
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

/**
 * Practice is a full-screen mode, not a tab (decision D1): one task, a clear exit, progress saved
 * after every answer. Daily Recall, the diagnostic, strengthening a concept and session practice
 * all use this scaffold (design spec §8 PracticeScaffold, §9).
 *
 * Each item is asked in the format its plan chose (PracticeVariety.kt): recall, choice, true or
 * false, fill the gap, match, apply it, put in order, worked example, short answer or explain in
 * your own words. Items opened outside a planned set are asked as written.
 */
@Composable
fun PracticeModeScreen(
    viewModel: LearnViewModel,
    onClose: () -> Unit,
    onOpenSession: (sessionIdOrNoteId: String) -> Unit
) {
    val activities by viewModel.practiceActivities.collectAsState()
    val index by viewModel.currentActivityIndex.collectAsState()
    val kind by viewModel.practiceKind.collectAsState()
    val title by viewModel.practiceTitle.collectAsState()
    val loading by viewModel.isLoadingPractice.collectAsState()
    val error by viewModel.practiceError.collectAsState()
    val results by viewModel.practiceResults.collectAsState()
    val concepts by viewModel.practiceConcepts.collectAsState()
    val variants by viewModel.practiceVariants.collectAsState()
    val stages by viewModel.practiceStages.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmStop by remember { mutableStateOf(false) }
    var introDone by rememberSaveable(kind, title) { mutableStateOf(kind != PracticeKind.DIAGNOSTIC) }
    var savedLine by remember { mutableStateOf<String?>(null) }
    var citation by remember { mutableStateOf<LearningEvidence?>(null) }

    val finished = activities.isNotEmpty() && index >= activities.size
    val inProgress = activities.isNotEmpty() && !finished && results.isNotEmpty()

    fun requestClose() {
        if (inProgress) confirmStop = true else {
            if (finished) viewModel.closePractice()
            onClose()
        }
    }
    BackHandler { requestClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MM.colors.background)
            .testTag("learn_practice_mode")
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // "Report this" for the AI-written question and answer (Play AI-content policy); never the student's own answer.
            val reportState = rememberAiReportState()
            AiReportSheetHost(reportState)
            val current = activities.getOrNull(index)?.takeIf { !finished && introDone }
            PracticeTopBar(
                onReportAi = current?.let { act ->
                    {
                        val noteId = viewModel.sessions.value.firstOrNull { it.id == act.sessionId }?.noteId
                        reportState.open(AiReportSource(AiFeature.PRACTICE, (listOf(act.prompt, act.expectedAnswer) + act.options).filter { it.isNotBlank() }.joinToString("\n"), noteId = noteId))
                    }
                },
                title = title,
                counter = when {
                    activities.isEmpty() || finished || !introDone -> null
                    kind == PracticeKind.RECALL -> "Card ${index + 1} of ${activities.size}"
                    else -> "Question ${index + 1} of ${activities.size}"
                },
                onClose = ::requestClose,
                onSkip = if (!finished && introDone && activities.size > 1) {
                    {
                        val skipped = activities.getOrNull(index)
                        viewModel.skipCurrent()
                        if (skipped != null) scope.launch {
                            val r = snackbar.showSnackbar(
                                "Skipped. It comes back at the end, no penalty.",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (r == SnackbarResult.ActionPerformed) viewModel.undoSkip(skipped.id)
                        }
                    }
                } else null
            )
            if (activities.isNotEmpty() && !finished && introDone) {
                SegmentedProgress(total = activities.size, done = index)
            }

            when {
                loading && activities.isEmpty() -> PracticeMessage {
                    StatusLine(StatusKind.Info, "Getting your questions ready…")
                }
                error != null && activities.isEmpty() -> PracticeMessage {
                    StatusLine(StatusKind.Error, error!!)
                    Spacer(Modifier.height(MM.space.l))
                    SecondaryButton("Back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                activities.isEmpty() -> PracticeMessage {
                    EmptyState(
                        title = "Nothing to practise right now",
                        body = "New questions appear after a lecture is processed, and reviews come back when they are due.",
                        action = { SecondaryButton("Back to Learn", onClick = onClose) }
                    )
                }
                finished -> CompletionSummary(
                    kind = kind,
                    title = title,
                    results = results,
                    concepts = concepts,
                    stages = stages,
                    onStrengthen = { concept ->
                        viewModel.startConceptPractice(concept.sessionId, concept.id) { }
                    },
                    onOpenSession = { id -> viewModel.closePractice(); onOpenSession(id) },
                    onDone = { viewModel.closePractice(); onClose() }
                )
                !introDone -> DiagnosticIntro(
                    count = activities.size,
                    minutes = minutesFor(activities),
                    onStart = { introDone = true }
                )
                else -> {
                    val activity = activities[index]
                    val concept = activity.conceptId?.let { concepts[it] }
                    if (error != null) {
                        StatusLine(StatusKind.Warning, error!!, modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s))
                    }
                    PracticeItem(
                        activity = activity,
                        variant = variants[activity.id] ?: nativeVariant(activity),
                        concept = concept,
                        viewModel = viewModel,
                        savedLine = savedLine,
                        momentum = momentumMilestone(trailingStreak(results.map { it.isCorrect })),
                        onCite = { citation = it },
                        onSaved = { line -> savedLine = line },
                        isLast = index == activities.size - 1
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
    }

    if (confirmStop) {
        val answered = results.size
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = MM.colors.surfaceRaised,
            title = { Text("Stop here?", style = MM.type.heading, color = MM.colors.ink) },
            text = {
                Text(
                    "Your $answered ${if (answered == 1) "answer is" else "answers are"} saved. You can pick up from item ${index + 1} on Learn.",
                    style = MM.type.body,
                    color = MM.colors.inkSecondary
                )
            },
            confirmButton = { TextAction("Stop", onClick = { confirmStop = false; onClose() }) },
            dismissButton = { TextAction("Keep going", onClick = { confirmStop = false }) }
        )
    }

    citation?.let { ev ->
        CitationSheet(
            evidence = ev,
            sourceTitle = title,
            onDismiss = { citation = null },
            resolveAudio = viewModel::citationAudioFor,
            onShowInTranscript = activities.getOrNull(index)?.sessionId?.let { sid ->
                { citation = null; onOpenSession(sid) }
            }
        )
    }
}

@Composable
private fun PracticeTopBar(
    title: String,
    counter: String?,
    onClose: () -> Unit,
    onSkip: (() -> Unit)?,
    onReportAi: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MM.space.xs, vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("practice_close")) {
            Icon(Icons.Rounded.Close, contentDescription = "Close practice", tint = MM.colors.ink)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, modifier = Modifier.semantics { heading() })
            if (counter != null) {
                Text(
                    counter,
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        Box {
            if (onSkip != null || onReportAi != null) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = MM.colors.inkSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (onSkip != null) DropdownMenuItem(
                        text = { Text("Skip for now", style = MM.type.body) },
                        onClick = { menu = false; onSkip() }
                    )
                    if (onReportAi != null) ReportAiMenuItem { menu = false; onReportAi() }
                }
            } else {
                Spacer(Modifier.size(MMSize.minTouch))
            }
        }
    }
}

@Composable
private fun SegmentedProgress(total: Int, done: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MM.space.l)
            .height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (total > 20) 0.dp else 3.dp)
    ) {
        if (total > 20) {
            Box(Modifier.weight(done.coerceAtLeast(0).toFloat() + 0.0001f).height(4.dp).clip(MM.radius.pill).background(MM.colors.accent))
            Box(Modifier.weight((total - done).coerceAtLeast(0).toFloat() + 0.0001f).height(4.dp).clip(MM.radius.pill).background(MM.colors.track))
        } else {
            repeat(total) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(MM.radius.pill)
                        .background(if (i < done) MM.colors.accent else MM.colors.track)
                )
            }
        }
    }
}

@Composable
private fun PracticeMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(MM.space.l),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

@Composable
private fun DiagnosticIntro(count: Int, minutes: Int, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            Spacer(Modifier.height(MM.space.xl))
            Text("QUICK CHECK", style = MM.type.overline, color = MM.colors.accent)
            Text("Find out what to focus on", style = MM.type.display, color = MM.colors.ink)
            Text(
                "$count questions · about $minutes min · not graded.",
                style = MM.type.body,
                color = MM.colors.inkSecondary
            )
            InsetPanel(Modifier.fillMaxWidth()) {
                Text(
                    "Getting one wrong is useful: it shows where a little practice will help most. Your answers set where each concept starts.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
            }
        }
        FooterBar { PrimaryButton("Start", onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("diagnostic_start")) }
    }
}

@Composable
private fun FooterBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MM.colors.background)
            .padding(horizontal = MM.space.l, vertical = MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) { content() }
}

// ───────────────────────────── One item ─────────────────────────────

private enum class ItemStage { ANSWERING, REVEALED }

@Composable
private fun PracticeItem(
    activity: LearningActivity,
    variant: PracticeVariant,
    concept: LearningConcept?,
    viewModel: LearnViewModel,
    savedLine: String?,
    momentum: Int?,
    onCite: (LearningEvidence) -> Unit,
    onSaved: (String) -> Unit,
    isLast: Boolean
) {
    val format = variant.format
    val context = LocalContext.current
    // Item state is keyed to the activity and its format, so skipping or moving on always starts clean.
    var stage by remember(activity.id, format) { mutableStateOf(ItemStage.ANSWERING) }
    var selected by remember(activity.id, format) { mutableStateOf<Int?>(null) }
    var typed by remember(activity.id, format) { mutableStateOf("") }
    var fix by remember(activity.id, format) { mutableStateOf("") }
    var gaveUp by remember(activity.id, format) { mutableStateOf(false) }
    var right by remember(activity.id, format) { mutableStateOf(false) }
    var stepsShown by remember(activity.id, format) { mutableStateOf(0) }
    var activeTerm by remember(activity.id, format) { mutableStateOf<Int?>(if (format == PracticeFormat.MATCH) 0 else null) }
    val ticks = remember(activity.id, format) { mutableStateListOf<Int>() }
    val placed = remember(activity.id, format) { mutableStateListOf<Int>() }
    val matches = remember(activity.id, format) { mutableStateMapOf<Int, Int>() }
    val hints by viewModel.hintLadder.collectAsState()
    val hintCount by viewModel.revealedHintCount.collectAsState()
    val hintsLoading by viewModel.isLoadingHints.collectAsState()
    val mistake by viewModel.mistakeExplanation.collectAsState()
    val schedule by produceState<com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule?>(null, activity.id) {
        value = viewModel.scheduleFor(activity.id)
    }
    val evidence = activity.evidence.firstOrNull { it.isShowable } ?: concept?.evidence?.firstOrNull { it.isShowable }
    val nextLabel = stringResource(if (isLast) R.string.practice_see_summary else R.string.practice_next)
    val savedTomorrow = stringResource(R.string.practice_saved_tomorrow)

    fun savedLineFor(rating: RecallRating): String {
        val days = DeterministicSpacedScheduler.previewIntervalDays(schedule, rating)
        return context.getString(R.string.practice_saved_next, DeterministicSpacedScheduler.nextReviewLabel(days).lowercase())
    }

    fun record(response: String, correct: Boolean, rating: RecallRating?) {
        viewModel.recordRecallAttempt(userResponse = response, isCorrect = correct, rating = rating, format = format)
    }

    fun advance(line: String?) {
        if (line != null) onSaved(line)
        viewModel.nextCard()
    }

    /** Marks an objective item (choice, true or false, gap, order, match) and shows the result. */
    fun checkObjective(response: String, correct: Boolean) {
        right = correct
        stage = ItemStage.REVEALED
        record(response, correct, null)
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l, vertical = MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            if (stage == ItemStage.ANSWERING && (savedLine != null || momentum != null)) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    if (savedLine != null) Text(savedLine, style = MM.type.caption, color = MM.colors.inkMuted)
                    if (momentum != null) {
                        Text(
                            stringResource(momentumRes(momentum)),
                            style = MM.type.caption,
                            color = MM.colors.accent,
                            modifier = Modifier.testTag("practice_momentum").semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }

            PromptCard(variant = variant, concept = concept, revealed = stage == ItemStage.REVEALED)

            when (format) {
                PracticeFormat.CHOICE, PracticeFormat.SCENARIO -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    variant.options.forEachIndexed { i, option ->
                        AnswerOption(
                            letter = ('A' + i).toString(),
                            text = option,
                            state = optionState(stage == ItemStage.ANSWERING, i, selected, variant.correctIndex),
                            onClick = { if (stage == ItemStage.ANSWERING) selected = i }
                        )
                    }
                }
                PracticeFormat.TRUE_FALSE -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    listOf(stringResource(R.string.practice_true), stringResource(R.string.practice_false)).forEachIndexed { i, label ->
                        AnswerOption(
                            letter = label.take(1),
                            text = label,
                            state = optionState(stage == ItemStage.ANSWERING, i, selected, variant.correctIndex),
                            onClick = { if (stage == ItemStage.ANSWERING) selected = i },
                            modifier = Modifier.testTag(if (i == 0) "practice_true" else "practice_false")
                        )
                    }
                    if (selected == 1 && stage == ItemStage.ANSWERING) {
                        PracticeTextField(
                            value = fix,
                            onValueChange = { fix = it },
                            label = stringResource(R.string.practice_tf_fix_label, variant.subject),
                            enabled = true,
                            minLines = 2,
                            tag = "practice_tf_fix"
                        )
                    }
                }
                PracticeFormat.CLOZE -> PracticeTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = stringResource(R.string.practice_cloze_hint),
                    enabled = stage == ItemStage.ANSWERING,
                    minLines = 1,
                    tag = "practice_cloze_answer"
                )
                PracticeFormat.ORDER -> OrderBoard(
                    steps = variant.steps,
                    displayOrder = variant.displayOrder.ifEmpty { variant.steps.indices.toList() },
                    placed = placed,
                    revealed = stage == ItemStage.REVEALED,
                    onPlace = { placed.add(it) },
                    onUnplace = { placed.remove(it) }
                )
                PracticeFormat.MATCH -> MatchBoard(
                    pairs = variant.pairs,
                    displayOrder = variant.displayOrder.ifEmpty { variant.pairs.indices.toList() },
                    matches = matches,
                    activeTerm = activeTerm,
                    revealed = stage == ItemStage.REVEALED,
                    onPickTerm = { activeTerm = if (activeTerm == it) null else it },
                    onPickMeaning = { meaning ->
                        val term = activeTerm
                        val holder = matches.entries.firstOrNull { it.value == meaning }?.key
                        if (term != null) {
                            if (holder != null) matches.remove(holder)
                            matches[term] = meaning
                            activeTerm = variant.pairs.indices.firstOrNull { it !in matches.keys }
                        } else if (holder != null) {
                            matches.remove(holder)
                            activeTerm = holder
                        }
                    }
                )
                PracticeFormat.WORKED -> WorkedSteps(steps = variant.steps, shown = stepsShown)
                PracticeFormat.SHORT, PracticeFormat.EXPLAIN -> {
                    PracticeTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = stringResource(R.string.practice_your_answer),
                        enabled = stage == ItemStage.ANSWERING,
                        minLines = 3,
                        tag = "practice_short_answer"
                    )
                    // AI hints are written for the activity's own question, so only that format offers them.
                    if (stage == ItemStage.ANSWERING && format == PracticeFormat.SHORT) {
                        HintBlock(
                            hints = hints.take(hintCount),
                            loading = hintsLoading,
                            canAskMore = hints.isEmpty() || hintCount < hints.size,
                            onHint = { viewModel.requestNextHint(activity) }
                        )
                    }
                }
                PracticeFormat.RECALL -> Unit
            }

            AnimatedVisibility(visible = stage == ItemStage.REVEALED, enter = fadeIn(MM.motion.standard())) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.l)) {
                    when (format) {
                        PracticeFormat.CHOICE, PracticeFormat.SCENARIO -> {
                            val answerLine = stringResource(R.string.practice_answer_is, variant.answer)
                            // The AI explanation is about the activity's own question, so it is used only when that was asked.
                            val explained = mistake?.correctMentalModel?.takeIf { it.isNotBlank() && variant.prompt == PracticePrompt.AS_WRITTEN }
                            FeedbackPanel(
                                right = right,
                                body = if (right) variant.answer else explained ?: answerLine,
                                evidence = evidence,
                                onCite = onCite
                            )
                        }
                        PracticeFormat.TRUE_FALSE -> {
                            val isTrue = variant.statementIsTrue == true
                            FeedbackPanel(
                                right = right,
                                body = when {
                                    isTrue && right -> stringResource(R.string.practice_tf_true_right)
                                    isTrue -> stringResource(R.string.practice_tf_true_missed)
                                    else -> stringResource(R.string.practice_tf_belongs_to, variant.correctionOwner)
                                },
                                evidence = evidence,
                                onCite = onCite
                            ) {
                                if (!isTrue) {
                                    Text(stringResource(R.string.practice_tf_true_version).uppercase(), style = MM.type.overline, color = MM.colors.inkMuted)
                                    Text("${variant.subject}: ${variant.correction}", style = MM.type.body, color = MM.colors.ink)
                                    if (fix.isNotBlank()) {
                                        Text(stringResource(R.string.practice_tf_your_fix).uppercase(), style = MM.type.overline, color = MM.colors.inkMuted)
                                        Text(fix.trim(), style = MM.type.body, color = MM.colors.inkSecondary)
                                    }
                                }
                            }
                        }
                        PracticeFormat.CLOZE -> FeedbackPanel(
                            right = right && !gaveUp,
                            body = if (right && !gaveUp) variant.correction.ifBlank { variant.answer }
                            else stringResource(R.string.practice_cloze_missing, variant.answer),
                            evidence = evidence,
                            onCite = onCite
                        ) {
                            if (!right || gaveUp) {
                                Text(stringResource(R.string.practice_cloze_in_full).uppercase(), style = MM.type.overline, color = MM.colors.inkMuted)
                                Text(variant.correction, style = MM.type.body, color = MM.colors.ink)
                            }
                        }
                        PracticeFormat.ORDER -> FeedbackPanel(
                            right = right,
                            body = stringResource(if (right) R.string.practice_order_all_right else R.string.practice_order_here_is),
                            evidence = evidence,
                            onCite = onCite
                        ) {
                            if (!right) {
                                variant.steps.forEachIndexed { i, step ->
                                    Text("${i + 1}. $step", style = MM.type.body, color = MM.colors.ink)
                                }
                            }
                        }
                        PracticeFormat.MATCH -> {
                            val correctCount = variant.pairs.indices.count { matches[it] == it }
                            FeedbackPanel(
                                right = right,
                                body = if (correctCount == variant.pairs.size) stringResource(R.string.practice_match_all_right)
                                else stringResource(R.string.practice_match_some, correctCount, variant.pairs.size),
                                evidence = evidence,
                                onCite = onCite
                            ) {
                                if (correctCount < variant.pairs.size) {
                                    variant.pairs.forEach { p ->
                                        Text("${p.term}: ${p.meaning}", style = MM.type.secondary, color = MM.colors.ink)
                                    }
                                }
                            }
                        }
                        PracticeFormat.SHORT -> SelfCheck(
                            modelAnswer = activity.expectedAnswer,
                            points = keyPointsOf(activity.expectedAnswer),
                            typed = typed,
                            gaveUp = gaveUp,
                            ticks = ticks,
                            evidence = evidence,
                            onCite = onCite
                        )
                        PracticeFormat.EXPLAIN -> SelfCheck(
                            modelAnswer = variant.answer,
                            points = variant.keyPoints,
                            typed = typed,
                            gaveUp = gaveUp,
                            ticks = ticks,
                            evidence = evidence,
                            onCite = onCite
                        )
                        PracticeFormat.RECALL, PracticeFormat.WORKED -> if (evidence != null) {
                            EvidenceQuote(evidence = evidence, onOpen = { onCite(evidence) })
                        }
                    }
                }
            }
        }

        FooterBar {
            when (format) {
                PracticeFormat.RECALL -> if (stage == ItemStage.ANSWERING) {
                    PrimaryButton("Show answer", onClick = { stage = ItemStage.REVEALED }, modifier = Modifier.fillMaxWidth().testTag("practice_show_answer"))
                } else {
                    Text("How well did you remember it?", style = MM.type.caption, color = MM.colors.inkSecondary)
                    RecallRatings(
                        labelFor = { r ->
                            DeterministicSpacedScheduler.nextReviewLabel(
                                DeterministicSpacedScheduler.previewIntervalDays(schedule, r)
                            )
                        },
                        onRate = { r ->
                            record("Self-rated: ${ratingLabel(r)}", r != RecallRating.AGAIN, r)
                            advance(savedLineFor(r))
                        }
                    )
                }
                PracticeFormat.CHOICE, PracticeFormat.SCENARIO, PracticeFormat.TRUE_FALSE -> if (stage == ItemStage.ANSWERING) {
                    val trueLabel = stringResource(R.string.practice_true)
                    val falseLabel = stringResource(R.string.practice_false)
                    PrimaryButton(
                        stringResource(R.string.practice_check),
                        enabled = selected != null,
                        onClick = {
                            val choice = selected ?: return@PrimaryButton
                            val response = when (format) {
                                PracticeFormat.TRUE_FALSE -> (if (choice == 0) trueLabel else falseLabel) +
                                    fix.trim().takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
                                else -> variant.options.getOrElse(choice) { "" }
                            }
                            checkObjective(response, choice == variant.correctIndex)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("practice_check")
                    )
                } else {
                    NextButton(nextLabel) { advance(savedLineFor(if (right) RecallRating.GOOD else RecallRating.AGAIN)) }
                }
                PracticeFormat.CLOZE -> if (stage == ItemStage.ANSWERING) {
                    PrimaryButton(
                        stringResource(R.string.practice_check),
                        enabled = typed.isNotBlank(),
                        onClick = { checkObjective(typed.trim(), clozeMatches(variant.answer, typed)) },
                        modifier = Modifier.fillMaxWidth().testTag("practice_check")
                    )
                    TextAction(
                        stringResource(R.string.practice_not_sure),
                        onClick = { gaveUp = true; right = false; stage = ItemStage.REVEALED },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else if (gaveUp) {
                    NextButton(nextLabel) {
                        record("Asked to see the answer", false, RecallRating.AGAIN)
                        advance(savedTomorrow)
                    }
                } else {
                    NextButton(nextLabel) { advance(savedLineFor(if (right) RecallRating.GOOD else RecallRating.AGAIN)) }
                }
                PracticeFormat.ORDER -> if (stage == ItemStage.ANSWERING) {
                    PrimaryButton(
                        stringResource(R.string.practice_check),
                        enabled = placed.size == variant.steps.size,
                        onClick = {
                            val order = placed.toList()
                            checkObjective(order.joinToString(" ") { (it + 1).toString() }, order == variant.steps.indices.toList())
                        },
                        modifier = Modifier.fillMaxWidth().testTag("practice_check")
                    )
                    if (placed.isNotEmpty()) {
                        TextAction(
                            stringResource(R.string.practice_order_start_over),
                            onClick = { placed.clear() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                } else {
                    NextButton(nextLabel) { advance(savedLineFor(if (right) RecallRating.GOOD else RecallRating.AGAIN)) }
                }
                PracticeFormat.MATCH -> if (stage == ItemStage.ANSWERING) {
                    PrimaryButton(
                        stringResource(R.string.practice_check),
                        enabled = matches.size == variant.pairs.size && variant.pairs.isNotEmpty(),
                        onClick = {
                            // Marked on the concept this item is about; the others are shown, not scored.
                            val primary = variant.pairs.indexOfFirst { it.isPrimary }
                            val correctCount = variant.pairs.indices.count { matches[it] == it }
                            checkObjective("Matched $correctCount of ${variant.pairs.size}", primary >= 0 && matches[primary] == primary)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("practice_check")
                    )
                } else {
                    NextButton(nextLabel) { advance(savedLineFor(if (right) RecallRating.GOOD else RecallRating.AGAIN)) }
                }
                PracticeFormat.WORKED -> if (stage == ItemStage.ANSWERING) {
                    val total = variant.steps.size
                    PrimaryButton(
                        stringResource(if (stepsShown == 0) R.string.practice_worked_show_first else R.string.practice_worked_show_next),
                        onClick = {
                            stepsShown = (stepsShown + 1).coerceAtMost(total)
                            if (stepsShown >= total) stage = ItemStage.REVEALED
                        },
                        modifier = Modifier.fillMaxWidth().testTag("practice_worked_next")
                    )
                    if (total > 1) {
                        TextAction(
                            stringResource(R.string.practice_worked_show_all),
                            onClick = { stepsShown = total; stage = ItemStage.REVEALED },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                } else {
                    Text(stringResource(R.string.practice_worked_how), style = MM.type.caption, color = MM.colors.inkSecondary)
                    SelfRatings(
                        labelFor = { r -> DeterministicSpacedScheduler.nextReviewLabel(DeterministicSpacedScheduler.previewIntervalDays(schedule, r)) },
                        onRate = { r ->
                            record("Worked example: ${ratingLabel(r)}", r != RecallRating.AGAIN, r)
                            advance(savedLineFor(r))
                        }
                    )
                }
                PracticeFormat.SHORT, PracticeFormat.EXPLAIN -> if (stage == ItemStage.ANSWERING) {
                    PrimaryButton(
                        if (format == PracticeFormat.EXPLAIN) stringResource(R.string.practice_compare) else "Check my answer",
                        enabled = typed.isNotBlank(),
                        onClick = {
                            val points = if (format == PracticeFormat.EXPLAIN) variant.keyPoints else keyPointsOf(activity.expectedAnswer)
                            ticks.clear()
                            ticks.addAll(suggestedTicks(points, typed))
                            stage = ItemStage.REVEALED
                        },
                        modifier = Modifier.fillMaxWidth().testTag("practice_check")
                    )
                    TextAction(stringResource(R.string.practice_not_sure), onClick = { gaveUp = true; stage = ItemStage.REVEALED }, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (gaveUp) {
                    NextButton(nextLabel) {
                        record("Asked to see the answer", false, RecallRating.AGAIN)
                        advance(savedTomorrow)
                    }
                } else if (format == PracticeFormat.EXPLAIN) {
                    Text(stringResource(R.string.practice_explain_how), style = MM.type.caption, color = MM.colors.inkSecondary)
                    SelfRatings(
                        labelFor = { r -> DeterministicSpacedScheduler.nextReviewLabel(DeterministicSpacedScheduler.previewIntervalDays(schedule, r)) },
                        onRate = { r ->
                            record(typed.trim(), r != RecallRating.AGAIN, r)
                            advance(savedLineFor(r))
                        }
                    )
                } else {
                    Text("Did your answer cover it?", style = MM.type.caption, color = MM.colors.inkSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        SecondaryButton(
                            "Not yet",
                            onClick = {
                                record(typed.trim(), false, RecallRating.AGAIN)
                                advance(savedTomorrow)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            "I got it",
                            onClick = {
                                record(typed.trim(), true, RecallRating.GOOD)
                                advance(savedLineFor(RecallRating.GOOD))
                            },
                            modifier = Modifier.weight(1f).testTag("practice_got_it")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextButton(label: String, onClick: () -> Unit) {
    PrimaryButton(label, onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("practice_next"))
}

/** The question itself, on the study surface: an overline naming the concept and the format, then the stem. */
@Composable
private fun PromptCard(variant: PracticeVariant, concept: LearningConcept?, revealed: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .border(MMSize.hairline, MM.colors.line, MM.radius.card)
            .padding(MM.space.l),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Text(
            listOfNotNull(concept?.name, stringResource(formatLabelRes(variant.format))).joinToString(" · ").uppercase(),
            style = MM.type.overline,
            color = MM.colors.inkMuted
        )
        val promptModifier = Modifier.testTag("practice_prompt")
        val subject = variant.subject.ifBlank { concept?.name.orEmpty() }
        when (variant.prompt) {
            PracticePrompt.AS_WRITTEN -> Text(variant.body, style = stemStyle(variant.body), color = MM.colors.ink, modifier = promptModifier)
            PracticePrompt.WHICH_DESCRIBES ->
                Text(stringResource(R.string.practice_stem_which_describes, subject), style = MM.type.title, color = MM.colors.ink, modifier = promptModifier)
            PracticePrompt.WHICH_TERM -> {
                Text(stringResource(R.string.practice_stem_which_term), style = MM.type.secondary, color = MM.colors.inkSecondary)
                Text(variant.body, style = stemStyle(variant.body), color = MM.colors.ink, modifier = promptModifier)
            }
            PracticePrompt.TRUE_OR_FALSE -> {
                Text(stringResource(R.string.practice_stem_true_false), style = MM.type.secondary, color = MM.colors.inkSecondary)
                Text(subject, style = MM.type.bodyStrong, color = MM.colors.accent)
                Text(variant.body, style = stemStyle(variant.body), color = MM.colors.ink, modifier = promptModifier)
            }
            PracticePrompt.FILL_GAP_SOURCE -> {
                Text(stringResource(R.string.practice_stem_fill_gap_source), style = MM.type.secondary, color = MM.colors.inkSecondary)
                Text(variant.body, style = stemStyle(variant.body), color = MM.colors.ink, modifier = promptModifier)
            }
            PracticePrompt.FILL_GAP_DEFINITION -> {
                Text(stringResource(R.string.practice_stem_fill_gap_definition, subject), style = MM.type.secondary, color = MM.colors.inkSecondary)
                Text(variant.body, style = stemStyle(variant.body), color = MM.colors.ink, modifier = promptModifier)
            }
            PracticePrompt.WHICH_CONCEPT -> {
                Text(stringResource(R.string.practice_stem_which_concept), style = MM.type.secondary, color = MM.colors.inkSecondary)
                Text("“${variant.body}”", style = MM.type.heading, color = MM.colors.ink, modifier = promptModifier)
            }
            PracticePrompt.PUT_IN_ORDER -> {
                val stem = if (variant.body.isBlank() && subject.isNotBlank()) stringResource(R.string.practice_stem_put_in_order_of, subject)
                else stringResource(R.string.practice_stem_put_in_order)
                if (variant.body.isNotBlank()) {
                    Text(stem, style = MM.type.secondary, color = MM.colors.inkSecondary)
                    Text(variant.body, style = stemStyle(variant.body), color = MM.colors.ink, modifier = promptModifier)
                } else {
                    Text(stem, style = MM.type.title, color = MM.colors.ink, modifier = promptModifier)
                }
            }
            PracticePrompt.MATCH_TERMS ->
                Text(stringResource(R.string.practice_stem_match), style = MM.type.title, color = MM.colors.ink, modifier = promptModifier)
            PracticePrompt.EXPLAIN_OWN_WORDS -> Text(
                stringResource(
                    when (variant.stemIndex) {
                        1 -> R.string.practice_stem_explain_1
                        2 -> R.string.practice_stem_explain_2
                        else -> R.string.practice_stem_explain_0
                    },
                    subject
                ),
                style = MM.type.title,
                color = MM.colors.ink,
                modifier = promptModifier
            )
            PracticePrompt.WORK_IT_OUT -> {
                Text(variant.body, style = stemStyle(variant.body), color = MM.colors.ink, modifier = promptModifier)
                Text(stringResource(R.string.practice_stem_worked), style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
        }
        if (variant.format == PracticeFormat.RECALL && revealed) {
            Box(Modifier.fillMaxWidth().height(MMSize.hairline).background(MM.colors.line))
            Text("ANSWER", style = MM.type.overline, color = MM.colors.inkMuted)
            Text(variant.answer, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.testTag("practice_answer"))
        }
    }
}

/** Short stems read large; long passages step down so the screen isn't a wall of title text. */
@Composable
private fun stemStyle(text: String) = if (text.length > 140) MM.type.heading else MM.type.title

private fun optionState(answering: Boolean, i: Int, selected: Int?, correct: Int?): OptionState = when {
    answering && selected == i -> OptionState.SELECTED
    answering -> OptionState.IDLE
    i == correct -> OptionState.CORRECT
    i == selected -> OptionState.CHOSEN_WRONG
    else -> OptionState.DIMMED
}

private fun momentumRes(streak: Int): Int = when {
    streak >= 12 -> R.string.practice_momentum_12
    streak >= 8 -> R.string.practice_momentum_8
    streak >= 5 -> R.string.practice_momentum_5
    else -> R.string.practice_momentum_3
}

@Composable
private fun PracticeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    minLines: Int,
    tag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label, style = MM.type.secondary) },
        minLines = minLines,
        singleLine = minLines == 1,
        textStyle = MM.type.body.copy(color = MM.colors.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MM.colors.accent,
            unfocusedBorderColor = MM.colors.line,
            cursorColor = MM.colors.accent
        ),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

private fun ratingLabel(r: RecallRating) = when (r) {
    RecallRating.AGAIN -> "Not yet"
    RecallRating.HARD -> "Hard"
    RecallRating.GOOD -> "Got it"
    RecallRating.EASY -> "Easy"
}

/** Three ratings, each with the next review it would set (decision D7: no EASY in Release 1). */
@Composable
private fun RecallRatings(labelFor: (RecallRating) -> String, onRate: (RecallRating) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        listOf(
            Triple(RecallRating.AGAIN, Icons.Rounded.Refresh, "Not yet"),
            Triple(RecallRating.HARD, Icons.Rounded.Schedule, "Hard"),
            Triple(RecallRating.GOOD, Icons.Rounded.ThumbUp, "Got it")
        ).forEach { (rating, icon, label) ->
            RatingButton(icon, label, labelFor(rating), Modifier.weight(1f).testTag("rate_${rating.name.lowercase()}")) { onRate(rating) }
        }
    }
}

/** The same three, worded for an explanation or a worked example: not yet, partly, got it. */
@Composable
private fun SelfRatings(labelFor: (RecallRating) -> String, onRate: (RecallRating) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        listOf(
            Triple(RecallRating.AGAIN, Icons.Rounded.Refresh, stringResource(R.string.practice_rate_not_yet)),
            Triple(RecallRating.HARD, Icons.Rounded.Schedule, stringResource(R.string.practice_rate_partly)),
            Triple(RecallRating.GOOD, Icons.Rounded.ThumbUp, stringResource(R.string.practice_rate_got_it))
        ).forEach { (rating, icon, label) ->
            RatingButton(icon, label, labelFor(rating), Modifier.weight(1f).testTag("self_rate_${rating.name.lowercase()}")) { onRate(rating) }
        }
    }
}

@Composable
private fun RatingButton(icon: ImageVector, label: String, next: String, modifier: Modifier, onClick: () -> Unit) {
    MMCard(modifier = modifier.heightIn(min = 72.dp), onClick = onClick, contentPadding = MM.space.s) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.iconSmall))
            Text(label, style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(next, style = MM.type.caption, color = MM.colors.inkSecondary)
        }
    }
}

internal enum class OptionState { IDLE, SELECTED, CORRECT, CHOSEN_WRONG, DIMMED }

/**
 * An answer option, an order step or a meaning to match. Results carry an icon and a text label,
 * never colour alone (spec §11). [note] replaces the default result label.
 */
@Composable
internal fun AnswerOption(
    letter: String,
    text: String,
    state: OptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    role: Role = Role.RadioButton
) {
    val ring = when (state) {
        OptionState.SELECTED -> MM.colors.accent
        OptionState.CORRECT -> MM.colors.success
        OptionState.CHOSEN_WRONG -> MM.colors.warning
        else -> MM.colors.line
    }
    val fill = when (state) {
        OptionState.CORRECT -> MM.colors.success.copy(alpha = 0.08f)
        OptionState.CHOSEN_WRONG -> MM.colors.warning.copy(alpha = 0.08f)
        OptionState.SELECTED -> MM.colors.accentWash
        else -> MM.colors.surface
    }
    val textColor = if (state == OptionState.DIMMED) MM.colors.inkMuted else MM.colors.ink
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MM.radius.card)
            .background(fill)
            .border(if (state == OptionState.IDLE || state == OptionState.DIMMED) MMSize.hairline else 2.dp, ring, MM.radius.card)
            .selectable(
                selected = state == OptionState.SELECTED || state == OptionState.CHOSEN_WRONG,
                onClick = onClick,
                role = role,
                enabled = state == OptionState.IDLE || state == OptionState.SELECTED
            )
            .padding(horizontal = MM.space.m, vertical = MM.space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(MM.radius.pill)
                .background(if (state == OptionState.SELECTED) MM.colors.accent else MM.colors.surfaceSunk),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                OptionState.CORRECT -> Icon(Icons.Rounded.Check, contentDescription = null, tint = MM.colors.success, modifier = Modifier.size(18.dp))
                OptionState.CHOSEN_WRONG -> Icon(Icons.Rounded.Close, contentDescription = null, tint = MM.colors.warning, modifier = Modifier.size(18.dp))
                else -> Text(letter, style = MM.type.caption, color = if (state == OptionState.SELECTED) MM.colors.onAccent else MM.colors.inkSecondary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = MM.type.body, color = textColor)
            val label = note ?: when (state) {
                OptionState.CORRECT -> "Correct answer"
                OptionState.CHOSEN_WRONG -> "Your answer"
                else -> null
            }
            if (label != null) {
                Text(
                    label,
                    style = MM.type.caption,
                    color = when (state) {
                        OptionState.CORRECT -> MM.colors.success
                        OptionState.CHOSEN_WRONG -> MM.colors.warning
                        else -> MM.colors.inkMuted
                    }
                )
            }
        }
    }
}

/** "That's right" or "Not quite": the reasoning and the lecture moment. Never "Wrong". */
@Composable
private fun FeedbackPanel(
    right: Boolean,
    body: String,
    evidence: LearningEvidence?,
    onCite: (LearningEvidence) -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .background(if (right) MM.colors.success.copy(alpha = 0.08f) else MM.colors.warning.copy(alpha = 0.08f))
            .padding(MM.space.l)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        Text(
            if (right) "That's right" else "Not quite",
            style = MM.type.heading,
            color = if (right) MM.colors.success else MM.colors.warning
        )
        Text(body, style = MM.type.body, color = MM.colors.ink)
        extra()
        if (evidence != null) {
            Spacer(Modifier.height(MM.space.xs))
            EvidenceQuote(evidence = evidence, onOpen = { onCite(evidence) })
        }
    }
}

/** Model answer plus key points with suggested ticks. The learner decides (decision D9). */
@Composable
private fun SelfCheck(
    modelAnswer: String,
    points: List<String>,
    typed: String,
    gaveUp: Boolean,
    ticks: MutableList<Int>,
    evidence: LearningEvidence?,
    onCite: (LearningEvidence) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        Column(
            Modifier.fillMaxWidth().clip(MM.radius.card).background(MM.colors.surfaceSunk).padding(MM.space.l)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Text(if (gaveUp) "Here's the answer" else "Compare with the lecture", style = MM.type.heading, color = MM.colors.ink)
            Text(modelAnswer, style = MM.type.body, color = MM.colors.ink)
        }
        if (!gaveUp && points.size >= 2) {
            Text("KEY POINTS · TICK WHAT YOU COVERED", style = MM.type.overline, color = MM.colors.inkMuted)
            if (typed.isNotBlank()) {
                Text("Suggested ticks are a guess from your wording. Change them if they're wrong.", style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            points.forEachIndexed { i, point ->
                val on = i in ticks
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MMSize.minTouch)
                        .toggleable(value = on, role = Role.Checkbox, onValueChange = { if (it) ticks.add(i) else ticks.remove(i) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    Icon(
                        if (on) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (on) MM.colors.accent else MM.colors.inkMuted
                    )
                    Text(point, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        if (evidence != null) EvidenceQuote(evidence = evidence, onOpen = { onCite(evidence) })
    }
}

/** A key point is suggested as covered when most of its meaningful words appear in the answer. */
internal fun suggestedTicks(points: List<String>, typed: String): List<Int> {
    val words = typed.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length > 3 }.toSet()
    if (words.isEmpty()) return emptyList()
    return points.mapIndexedNotNull { i, p ->
        val key = p.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length > 3 }.distinct()
        if (key.isNotEmpty() && key.count { it in words } * 2 >= key.size) i else null
    }
}

@Composable
private fun HintBlock(hints: List<String>, loading: Boolean, canAskMore: Boolean, onHint: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        hints.forEachIndexed { i, h ->
            InsetPanel(Modifier.fillMaxWidth()) {
                Text("Hint ${i + 1}", style = MM.type.caption, color = MM.colors.accent)
                Text(h, style = MM.type.secondary, color = MM.colors.ink)
            }
        }
        when {
            loading -> Text("Thinking of a hint…", style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            canAskMore -> TextAction(if (hints.isEmpty()) "Get a hint" else "Another hint", onClick = onHint)
        }
    }
}

// ───────────────────────────── Summary ─────────────────────────────

/**
 * The end of a set: a headline that fits how it went (one of a few, so it doesn't read the same
 * every day), the counts, the longest run, the ways it was practised, then what stuck and what to
 * revisit, each concept with its stage ladder.
 */
@Composable
private fun CompletionSummary(
    kind: PracticeKind,
    title: String,
    results: List<PracticeResult>,
    concepts: Map<String, LearningConcept>,
    stages: Map<String, Set<PracticeStage>>,
    onStrengthen: (LearningConcept) -> Unit,
    onOpenSession: (String) -> Unit,
    onDone: () -> Unit
) {
    val digest = remember(results) { digestOf(results.map { AnsweredItem(it.activity.conceptId, it.isCorrect, it.format) }) }
    // The latest result per concept says what changed.
    val byConcept = results.filter { it.activity.conceptId != null }
        .groupBy { it.activity.conceptId!! }
        .mapValues { (_, list) -> list.last() }
    val missed = digest.revisit.mapNotNull { concepts[it] }
    val solid = digest.stuck.mapNotNull { concepts[it] }
    val nextReview = results.minOfOrNull { it.nextReviewDays }
    val sessionId = results.firstOrNull()?.activity?.sessionId
    val headlines = when (digest.mood) {
        SessionMood.ALL_RIGHT -> stringArrayResource(R.array.practice_summary_all_right)
        SessionMood.MOSTLY_RIGHT -> stringArrayResource(R.array.practice_summary_mostly_right)
        SessionMood.MIXED -> stringArrayResource(R.array.practice_summary_mixed)
        SessionMood.TOUGH -> stringArrayResource(R.array.practice_summary_tough)
        SessionMood.NONE -> emptyArray<String>()
    }
    val pick = Math.floorMod(System.currentTimeMillis() / DeterministicSpacedScheduler.DAY_MS + results.size, headlines.size.coerceAtLeast(1).toLong()).toInt()
    val formatNames = digest.formats.map { stringResource(formatLabelRes(it)) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            Spacer(Modifier.height(MM.space.l))
            Text(title.uppercase(), style = MM.type.overline, color = MM.colors.accent)
            Text(
                when {
                    kind == PracticeKind.DIAGNOSTIC -> "Here's where you are"
                    digest.mood == SessionMood.NONE || headlines.isEmpty() -> stringResource(R.string.practice_summary_none)
                    else -> headlines[pick]
                },
                style = MM.type.display,
                color = MM.colors.ink,
                modifier = Modifier.semantics { heading() }.testTag("practice_summary_headline")
            )
            if (digest.answered > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Text(
                        stringResource(R.string.practice_summary_counts, digest.answered, digest.right),
                        style = MM.type.body,
                        color = MM.colors.inkSecondary
                    )
                    if (digest.bestRun >= 3) {
                        Text(stringResource(R.string.practice_summary_best_run, digest.bestRun), style = MM.type.secondary, color = MM.colors.inkSecondary)
                    }
                    if (formatNames.isNotEmpty()) {
                        Text(
                            pluralStringResource(R.plurals.practice_summary_ways, formatNames.size, formatNames.size, formatNames.joinToString(", ")),
                            style = MM.type.secondary,
                            color = MM.colors.inkSecondary
                        )
                    }
                }
            }
            if (kind == PracticeKind.DIAGNOSTIC && (missed.isNotEmpty() || solid.isNotEmpty())) {
                if (missed.isNotEmpty()) ConceptGroup("WORTH STRENGTHENING", missed, byConcept, stages)
                if (solid.isNotEmpty()) ConceptGroup("LOOKS SOLID FOR NOW", solid, byConcept, stages)
            } else {
                if (missed.isNotEmpty()) {
                    ConceptGroup(stringResource(R.string.practice_summary_revisit).uppercase(), missed, byConcept, stages)
                    Text(stringResource(R.string.practice_summary_revisit_note), style = MM.type.caption, color = MM.colors.inkSecondary)
                }
                if (solid.isNotEmpty()) ConceptGroup(stringResource(R.string.practice_summary_stuck).uppercase(), solid, byConcept, stages)
            }
            if (nextReview != null) {
                Text(
                    "Next review: ${DeterministicSpacedScheduler.nextReviewLabel(nextReview).lowercase()}.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
            }
        }
        FooterBar {
            val weakest = missed.firstOrNull()
            if (weakest != null) {
                PrimaryButton("Strengthen ${weakest.name}", onClick = { onStrengthen(weakest) }, modifier = Modifier.fillMaxWidth())
                SecondaryButton("Done for now", onClick = onDone, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("practice_done"))
                if (sessionId != null && kind != PracticeKind.RECALL) {
                    TextAction("Back to the session", onClick = { onOpenSession(sessionId) }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun ConceptGroup(
    label: String,
    concepts: List<LearningConcept>,
    results: Map<String, PracticeResult>,
    stages: Map<String, Set<PracticeStage>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text(label, style = MM.type.overline, color = MM.colors.inkMuted)
        concepts.forEach { c ->
            val r = results[c.id]
            val state = r?.newState ?: c.state
            val reached = stages[c.id].orEmpty()
            MMCard(Modifier.fillMaxWidth(), contentPadding = MM.space.m) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                    MasteryGlyph(state, size = 20.dp, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MM.type.bodyStrong, color = MM.colors.ink)
                        MasteryLabel(state, reason = r?.reason)
                    }
                }
                Spacer(Modifier.height(MM.space.s))
                StageLadder(reached = reached, next = nextStageFor(state, reached))
            }
        }
    }
}
