package com.craftflowtechnologies.meetingmind.feature.learning.space

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
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
import com.craftflowtechnologies.meetingmind.ui.theme.LearningTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

/**
 * Practice is a full-screen mode, not a tab (decision D1): one task, a clear exit, progress saved
 * after every answer. Daily Recall, the diagnostic, strengthening a concept and session practice
 * all use this scaffold (design spec §8 PracticeScaffold, §9).
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
            PracticeTopBar(
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
                    results = results,
                    concepts = concepts,
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
                        StatusLine(StatusKind.Warning, error!!, modifier = Modifier.padding(horizontal = LearningTheme.readingGutter, vertical = MM.space.s))
                    }
                    PracticeItem(
                        activity = activity,
                        concept = concept,
                        viewModel = viewModel,
                        savedLine = savedLine,
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
            onPlay = null,
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
    onSkip: (() -> Unit)?
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
            if (onSkip != null) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = MM.colors.inkSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Skip for now", style = MM.type.body) },
                        onClick = { menu = false; onSkip() }
                    )
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
            .padding(horizontal = LearningTheme.readingGutter)
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
        Modifier.fillMaxSize().padding(LearningTheme.readingGutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

@Composable
private fun DiagnosticIntro(count: Int, minutes: Int, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(LearningTheme.readingGutter),
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
            .padding(horizontal = LearningTheme.readingGutter, vertical = MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) { content() }
}

// ───────────────────────────── One item ─────────────────────────────

private enum class ItemStage { ANSWERING, REVEALED }

@Composable
private fun PracticeItem(
    activity: LearningActivity,
    concept: LearningConcept?,
    viewModel: LearnViewModel,
    savedLine: String?,
    onCite: (LearningEvidence) -> Unit,
    onSaved: (String) -> Unit,
    isLast: Boolean
) {
    val correctIndex = remember(activity.id) { correctOptionIndex(activity) }
    val format = when {
        activity.type == LearningActivityType.RECALL -> Format.RECALL
        activity.options.isNotEmpty() && correctIndex != null -> Format.CHOICE
        else -> Format.SHORT
    }
    // Item state is keyed to the activity, so skipping or moving on always starts clean.
    var stage by remember(activity.id) { mutableStateOf(ItemStage.ANSWERING) }
    var selected by remember(activity.id) { mutableStateOf<Int?>(null) }
    var typed by remember(activity.id) { mutableStateOf("") }
    var gaveUp by remember(activity.id) { mutableStateOf(false) }
    val ticks = remember(activity.id) { mutableStateListOf<Int>() }
    val hints by viewModel.hintLadder.collectAsState()
    val hintCount by viewModel.revealedHintCount.collectAsState()
    val hintsLoading by viewModel.isLoadingHints.collectAsState()
    val mistake by viewModel.mistakeExplanation.collectAsState()
    val schedule by produceState<com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule?>(null, activity.id) {
        value = viewModel.scheduleFor(activity.id)
    }
    val evidence = activity.evidence.firstOrNull { it.isShowable } ?: concept?.evidence?.firstOrNull { it.isShowable }
    val nextLabel = if (isLast) "See summary" else "Next"

    fun advance(line: String?) {
        if (line != null) onSaved(line)
        viewModel.nextCard()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LearningTheme.readingGutter, vertical = MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            if (savedLine != null && stage == ItemStage.ANSWERING) {
                Text(savedLine, style = MM.type.caption, color = MM.colors.inkMuted)
            }
            // The prompt, on study paper.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MM.radius.card)
                    .background(LearningTheme.colors.paper)
                    .border(MMSize.hairline, MM.colors.line, MM.radius.card)
                    .padding(LearningTheme.readingGutter),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                Text(
                    listOfNotNull(concept?.name, formatLabel(format)).joinToString(" · ").uppercase(),
                    style = MM.type.overline,
                    color = MM.colors.inkMuted
                )
                Text(activity.prompt, style = LearningTheme.type.prompt, color = MM.colors.ink, modifier = Modifier.testTag("practice_prompt"))
                if (format == Format.RECALL && stage == ItemStage.REVEALED) {
                    Box(Modifier.fillMaxWidth().height(MMSize.hairline).background(MM.colors.line))
                    Text("ANSWER", style = MM.type.overline, color = MM.colors.inkMuted)
                    Text(activity.expectedAnswer, style = LearningTheme.type.reading, color = MM.colors.ink, modifier = Modifier.testTag("practice_answer"))
                }
            }

            when (format) {
                Format.CHOICE -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    activity.options.forEachIndexed { i, option ->
                        AnswerOption(
                            letter = ('A' + i).toString(),
                            text = option,
                            state = when {
                                stage == ItemStage.ANSWERING && selected == i -> OptionState.SELECTED
                                stage == ItemStage.ANSWERING -> OptionState.IDLE
                                i == correctIndex -> OptionState.CORRECT
                                i == selected -> OptionState.CHOSEN_WRONG
                                else -> OptionState.DIMMED
                            },
                            onClick = { if (stage == ItemStage.ANSWERING) selected = i }
                        )
                    }
                }
                Format.SHORT -> {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        enabled = stage == ItemStage.ANSWERING,
                        label = { Text("Your answer", style = MM.type.secondary) },
                        minLines = 3,
                        textStyle = MM.type.body.copy(color = MM.colors.ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MM.colors.accent,
                            unfocusedBorderColor = MM.colors.line,
                            cursorColor = MM.colors.accent
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("practice_short_answer")
                    )
                    if (stage == ItemStage.ANSWERING) {
                        HintBlock(
                            hints = hints.take(hintCount),
                            loading = hintsLoading,
                            canAskMore = hints.isEmpty() || hintCount < hints.size,
                            onHint = { viewModel.requestNextHint(activity) }
                        )
                    }
                }
                Format.RECALL -> Unit
            }

            AnimatedVisibility(visible = stage == ItemStage.REVEALED, enter = fadeIn(MM.motion.standard())) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.l)) {
                    when (format) {
                        Format.CHOICE -> FeedbackPanel(
                            right = selected == correctIndex,
                            body = if (selected == correctIndex) activity.expectedAnswer
                            else mistake?.correctMentalModel?.takeIf { it.isNotBlank() } ?: "The answer is ${activity.options[correctIndex!!]}.",
                            evidence = evidence,
                            onCite = onCite
                        )
                        Format.SHORT -> SelfCheck(
                            modelAnswer = activity.expectedAnswer,
                            points = keyPointsOf(activity.expectedAnswer),
                            typed = typed,
                            gaveUp = gaveUp,
                            ticks = ticks,
                            evidence = evidence,
                            onCite = onCite
                        )
                        Format.RECALL -> if (evidence != null) {
                            EvidenceQuote(evidence = evidence, onOpen = { onCite(evidence) })
                        }
                    }
                }
            }
        }

        FooterBar {
            when (format) {
                Format.RECALL -> if (stage == ItemStage.ANSWERING) {
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
                            viewModel.recordRecallAttempt(
                                userResponse = "Self-rated: ${ratingLabel(r)}",
                                isCorrect = r != RecallRating.AGAIN,
                                rating = r
                            )
                            val days = DeterministicSpacedScheduler.previewIntervalDays(schedule, r)
                            advance("Saved. Next review: ${DeterministicSpacedScheduler.nextReviewLabel(days).lowercase()}.")
                        }
                    )
                }
                Format.CHOICE -> if (stage == ItemStage.ANSWERING) {
                    PrimaryButton(
                        "Check answer",
                        enabled = selected != null,
                        onClick = {
                            val choice = selected ?: return@PrimaryButton
                            stage = ItemStage.REVEALED
                            viewModel.recordRecallAttempt(
                                userResponse = activity.options[choice],
                                isCorrect = choice == correctIndex,
                                rating = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("practice_check")
                    )
                } else {
                    PrimaryButton(nextLabel, onClick = { advance(null) }, modifier = Modifier.fillMaxWidth().testTag("practice_next"))
                }
                Format.SHORT -> if (stage == ItemStage.ANSWERING) {
                    PrimaryButton(
                        "Check my answer",
                        enabled = typed.isNotBlank(),
                        onClick = {
                            ticks.clear()
                            ticks.addAll(suggestedTicks(keyPointsOf(activity.expectedAnswer), typed))
                            stage = ItemStage.REVEALED
                        },
                        modifier = Modifier.fillMaxWidth().testTag("practice_check")
                    )
                    TextAction("Not sure — show me", onClick = { gaveUp = true; stage = ItemStage.REVEALED }, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (gaveUp) {
                    PrimaryButton(
                        nextLabel,
                        onClick = {
                            viewModel.recordRecallAttempt(userResponse = "Asked to see the answer", isCorrect = false, rating = RecallRating.AGAIN)
                            advance("Saved. This one comes back tomorrow.")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("practice_next")
                    )
                } else {
                    Text("Did your answer cover it?", style = MM.type.caption, color = MM.colors.inkSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        SecondaryButton(
                            "Not yet",
                            onClick = {
                                viewModel.recordRecallAttempt(userResponse = typed.trim(), isCorrect = false, rating = RecallRating.AGAIN)
                                advance("Saved. This one comes back tomorrow.")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            "I got it",
                            onClick = {
                                viewModel.recordRecallAttempt(userResponse = typed.trim(), isCorrect = true, rating = RecallRating.GOOD)
                                val days = DeterministicSpacedScheduler.previewIntervalDays(schedule, RecallRating.GOOD)
                                advance("Saved. Next review: ${DeterministicSpacedScheduler.nextReviewLabel(days).lowercase()}.")
                            },
                            modifier = Modifier.weight(1f).testTag("practice_got_it")
                        )
                    }
                }
            }
        }
    }
}

private enum class Format { RECALL, CHOICE, SHORT }

private fun formatLabel(format: Format) = when (format) {
    Format.RECALL -> "Recall"
    Format.CHOICE -> "Choose one"
    Format.SHORT -> "Short answer"
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

private enum class OptionState { IDLE, SELECTED, CORRECT, CHOSEN_WRONG, DIMMED }

/** An answer option. Results carry an icon and a text label, never colour alone (spec §11). */
@Composable
private fun AnswerOption(letter: String, text: String, state: OptionState, onClick: () -> Unit) {
    val clay = LearningTheme.colors.clay
    val ring = when (state) {
        OptionState.SELECTED -> MM.colors.accent
        OptionState.CORRECT -> MM.colors.success
        OptionState.CHOSEN_WRONG -> clay
        else -> MM.colors.line
    }
    val fill = when (state) {
        OptionState.CORRECT -> LearningTheme.colors.successWash
        OptionState.CHOSEN_WRONG -> LearningTheme.colors.clayWash
        OptionState.SELECTED -> MM.colors.accentWash
        else -> MM.colors.surface
    }
    val textColor = if (state == OptionState.DIMMED) MM.colors.inkMuted else MM.colors.ink
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MM.radius.card)
            .background(fill)
            .border(if (state == OptionState.IDLE || state == OptionState.DIMMED) MMSize.hairline else 2.dp, ring, MM.radius.card)
            .selectable(
                selected = state == OptionState.SELECTED || state == OptionState.CHOSEN_WRONG,
                onClick = onClick,
                role = Role.RadioButton,
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
                OptionState.CHOSEN_WRONG -> Icon(Icons.Rounded.Close, contentDescription = null, tint = clay, modifier = Modifier.size(18.dp))
                else -> Text(letter, style = MM.type.caption, color = if (state == OptionState.SELECTED) MM.colors.onAccent else MM.colors.inkSecondary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = MM.type.body, color = textColor)
            when (state) {
                OptionState.CORRECT -> Text("Correct answer", style = MM.type.caption, color = MM.colors.success)
                OptionState.CHOSEN_WRONG -> Text("Your answer", style = MM.type.caption, color = clay)
                else -> Unit
            }
        }
    }
}

/** "That's right" or "Not quite": the reasoning and the lecture moment. Never "Wrong". */
@Composable
private fun FeedbackPanel(right: Boolean, body: String, evidence: LearningEvidence?, onCite: (LearningEvidence) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .background(if (right) LearningTheme.colors.successWash else LearningTheme.colors.clayWash)
            .padding(MM.space.l)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        Text(
            if (right) "That's right" else "Not quite",
            style = MM.type.heading,
            color = if (right) MM.colors.success else LearningTheme.colors.clay
        )
        Text(body, style = LearningTheme.type.reading, color = MM.colors.ink)
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
            Text(modelAnswer, style = LearningTheme.type.reading, color = MM.colors.ink)
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

@Composable
private fun CompletionSummary(
    kind: PracticeKind,
    results: List<PracticeResult>,
    concepts: Map<String, LearningConcept>,
    onStrengthen: (LearningConcept) -> Unit,
    onOpenSession: (String) -> Unit,
    onDone: () -> Unit
) {
    val answered = results.size
    val right = results.count { it.isCorrect }
    // The latest result per concept says what changed.
    val byConcept = results.filter { it.activity.conceptId != null }
        .groupBy { it.activity.conceptId!! }
        .mapValues { (_, list) -> list.last() }
    val missed = byConcept.filterValues { !it.isCorrect }.keys.mapNotNull { concepts[it] }
    val solid = byConcept.filterValues { it.isCorrect }.keys.mapNotNull { concepts[it] }
    val nextReview = results.minOfOrNull { it.nextReviewDays }
    val sessionId = results.firstOrNull()?.activity?.sessionId

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(LearningTheme.readingGutter),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            Spacer(Modifier.height(MM.space.l))
            Text(
                when (kind) {
                    PracticeKind.RECALL -> "Daily Recall done"
                    PracticeKind.DIAGNOSTIC -> "Here's where you are"
                    PracticeKind.CONCEPT -> "Practice saved"
                    PracticeKind.SET -> "Practice done"
                },
                style = MM.type.display,
                color = MM.colors.ink,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                when {
                    answered == 0 -> "Nothing was answered this time. That's fine."
                    kind == PracticeKind.RECALL -> "$answered ${if (answered == 1) "card" else "cards"} reviewed."
                    else -> "You answered $answered. $right right first time."
                },
                style = MM.type.body,
                color = MM.colors.inkSecondary
            )
            if (kind == PracticeKind.DIAGNOSTIC && (missed.isNotEmpty() || solid.isNotEmpty())) {
                if (missed.isNotEmpty()) ConceptGroup("WORTH STRENGTHENING", missed, byConcept)
                if (solid.isNotEmpty()) ConceptGroup("LOOKS SOLID FOR NOW", solid, byConcept)
            } else if (byConcept.isNotEmpty()) {
                ConceptGroup("WHERE THINGS STAND", byConcept.keys.mapNotNull { concepts[it] }, byConcept)
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
private fun ConceptGroup(label: String, concepts: List<LearningConcept>, results: Map<String, PracticeResult>) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text(label, style = MM.type.overline, color = MM.colors.inkMuted)
        concepts.forEach { c ->
            val r = results[c.id]
            val state = r?.newState ?: c.state
            MMCard(Modifier.fillMaxWidth(), contentPadding = MM.space.m) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                    MasteryGlyph(state, size = 20.dp, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MM.type.bodyStrong, color = MM.colors.ink)
                        MasteryLabel(state, reason = r?.reason)
                    }
                }
            }
        }
    }
}
