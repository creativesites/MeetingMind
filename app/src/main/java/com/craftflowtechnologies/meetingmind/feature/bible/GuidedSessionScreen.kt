package com.craftflowtechnologies.meetingmind.feature.bible

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.biblestudy.NeedPassages
import com.craftflowtechnologies.meetingmind.core.biblestudy.QuestionBank
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionKind
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionState
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionStep
import com.craftflowtechnologies.meetingmind.core.biblestudy.StepType
import com.craftflowtechnologies.meetingmind.core.biblestudy.WhyLines
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** What the session screen can do. */
data class SessionActions(
    val onClose: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onAnswer: (String, String) -> Unit = { _, _ -> },
    val onListen: () -> Unit = {},
    val onChooseMethod: (SessionKind) -> Unit = {},
    val onUseQuestion: (SessionStep, String) -> Unit = { _, _ -> },
    val onGoDeeper: (Int?) -> Unit = {},
    val onCopyPassage: () -> Unit = {},
    val onCopyNotes: () -> Unit = {},
    val onAddToNote: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenReader: () -> Unit = {},
    val onOpenJournalEntry: (String) -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedSessionScreen(
    viewModel: GuidedSessionViewModel,
    onClose: () -> Unit,
    onOpenReader: (ScriptureReference) -> Unit,
    onOpenNote: (String) -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val listening by viewModel.listening.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The reader's own study sheet: original words, commentary and cross-references for a verse.
    val reader: BibleViewModel = viewModel(key = "session-reader")
    val study by reader.study.collectAsStateWithLifecycle()
    var pickNote by remember { mutableStateOf(false) }

    fun copy(label: String, text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
    val request = ui.request
    BackHandler { onClose() }
    GuidedSessionContent(
        ui, listening,
        SessionActions(
            onClose = onClose,
            onNext = { if (ui.session?.isLast == true) { viewModel.finish(); onClose() } else viewModel.next() },
            onBack = viewModel::back,
            onAnswer = viewModel::answer,
            onListen = viewModel::listen,
            onChooseMethod = viewModel::chooseMethod,
            onUseQuestion = viewModel::useQuestion,
            onGoDeeper = { v -> request?.passage?.let { p -> reader.openStudyFor(ScriptureReference(p.book, p.chapter, v ?: p.verseStart, null)) } },
            onCopyPassage = { copy("Passage", viewModel.passageCopyText()) },
            onCopyNotes = { copy("My notes", viewModel.notesText()) },
            onAddToNote = { pickNote = true },
            onRetry = viewModel::retry,
            onOpenReader = { request?.passage?.let(onOpenReader) },
            onOpenJournalEntry = onOpenNote
        )
    )
    study?.let { st ->
        StudySheet(st, onCommentary = reader::chooseCommentary, onOpen = { r -> reader.closeStudy(); onOpenReader(r) }, onDismiss = reader::closeStudy)
    }
    if (pickNote) NotePicker(
        load = { viewModel.recentNotes() },
        onPick = { n -> pickNote = false; viewModel.addToNote(n.id, n.title) },
        onDismiss = { pickNote = false }
    )
}

/** The stateless session, for the screen and for screenshot tests. */
@Composable
fun GuidedSessionContent(ui: GuidedUi, listening: Boolean, actions: SessionActions) {
    val c = MM.colors
    val session = ui.session
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding().navigationBarsPadding().testTag("bible_session")) {
        Column(Modifier.padding(horizontal = MM.space.l)) {
            ScreenHeader(
                title = ui.request?.passage?.display().orEmpty(),
                leading = { IconButton(onClick = actions.onClose, modifier = Modifier.testTag("session_close")) { Icon(Icons.Rounded.Close, "Close", tint = c.ink) } },
                actions = {
                    if (ui.canListen) IconButton(onClick = actions.onListen, modifier = Modifier.testTag("session_listen")) {
                        Icon(if (listening) Icons.Rounded.Pause else Icons.Rounded.Headphones, if (listening) "Pause" else "Listen", tint = c.accent)
                    }
                }
            )
            if (session != null && session.steps.size > 1) {
                LinearProgressIndicator(
                    progress = { session.progress.coerceAtLeast(0.04f) }, color = c.accent, trackColor = c.track,
                    modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs)
                )
            }
        }
        when {
            ui.load is SessionLoad.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Opening the passage…", style = MM.type.secondary, color = c.inkSecondary)
            }
            ui.load is SessionLoad.Missing -> Column(Modifier.weight(1f).padding(MM.space.l)) {
                StatusLine(StatusKind.Error, (ui.load as SessionLoad.Missing).message, actionLabel = "Retry", onAction = actions.onRetry)
                TextAction("Open in the reader", onClick = actions.onOpenReader)
            }
            ui.choosingMethod -> MethodChooser(actions, Modifier.weight(1f))
            session != null -> {
                val ready = ui.load as SessionLoad.Ready
                val step = session.current
                val expanded = step.type == StepType.READ || step.type == StepType.SCRIPTURE
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (step.type != StepType.CLOSE) PinnedPassage(ready, expanded, actions, Modifier.padding(horizontal = MM.space.l))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l, vertical = MM.space.l),
                        verticalArrangement = Arrangement.spacedBy(MM.space.m)
                    ) {
                        StepBody(ui, session, step, ready, actions)
                    }
                }
                if (step.type != StepType.CLOSE) NavBar(session, step, actions)
            }
        }
    }
}

@Composable
private fun MethodChooser(actions: SessionActions, modifier: Modifier) {
    val c = MM.colors
    Column(modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        Text("Walk me through it", style = MM.type.title, color = c.ink)
        Text("Choose a way in. You can write as you go, or just read and think.", style = MM.type.secondary, color = c.inkSecondary)
        MMCard(onClick = { actions.onChooseMethod(SessionKind.SOAP) }, modifier = Modifier.testTag("method_soap")) {
            Text("SOAP", style = MM.type.heading, color = c.ink)
            Text("Scripture, Observation, Application, Prayer. Four short steps.", style = MM.type.secondary, color = c.inkSecondary)
        }
        MMCard(onClick = { actions.onChooseMethod(SessionKind.VERSE_BY_VERSE) }, modifier = Modifier.testTag("method_verses")) {
            Text("Verse by verse", style = MM.type.heading, color = c.ink)
            Text("One verse at a time, with the original words, commentary and cross-references a tap away.", style = MM.type.secondary, color = c.inkSecondary)
        }
    }
}

/** The passage, pinned above every step. Collapsed to a few lines except while reading it. */
@Composable
private fun PinnedPassage(ready: SessionLoad.Ready, startExpanded: Boolean, actions: SessionActions, modifier: Modifier) {
    val c = MM.colors
    var open by remember(startExpanded) { mutableStateOf(startExpanded) }
    MMCard(modifier = modifier.testTag("session_passage"), contentPadding = MM.space.m) {
        SelectionContainer {
            Text(
                ready.passageText, style = MM.type.scripture, color = c.ink,
                maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                modifier = if (open) Modifier.heightIn(max = MMSize.minTouch * 5).verticalScroll(rememberScrollState()) else Modifier
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction(if (open) "Show less" else "Show all", onClick = { open = !open })
            Spacer(Modifier.weight(1f))
            TextAction("Copy", onClick = actions.onCopyPassage, modifier = Modifier.testTag("session_copy_passage"))
        }
        Text(ready.attribution.substringBefore(" · "), style = MM.type.caption, color = c.inkMuted)
    }
}

@Composable
private fun NavBar(session: SessionState, step: SessionStep, actions: SessionActions) {
    val empty = step.answerable && session.answers[step.id].isNullOrBlank()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.m),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically
    ) {
        if (!session.isFirst) SecondaryButton("Back", onClick = actions.onBack, modifier = Modifier.testTag("session_back"))
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (empty) "Skip" else "Next", onClick = actions.onNext, modifier = Modifier.testTag("session_next"))
    }
}

@Composable
private fun StepBody(ui: GuidedUi, session: SessionState, step: SessionStep, ready: SessionLoad.Ready, actions: SessionActions) {
    val c = MM.colors
    val request = ui.request!!
    val help = ui.help["${step.type}-${step.verse ?: 0}"]
    when (step.type) {
        StepType.WHY -> {
            Text("Why it matters", style = MM.type.heading, color = c.ink)
            val curated = request.need?.let { n -> NeedPassages.passages(n).firstOrNull { it.reference == request.passage } }
            val lines = WhyLines.lines(request.passage, emptySet(), curated)
            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) { lines.forEach { Text(it, style = MM.type.body, color = c.ink) } } }
            Suggested(help, onUse = null)
        }
        StepType.READ -> {
            Text("Read it, or listen", style = MM.type.heading, color = c.ink)
            Text("Read it once for what it says, and once for what stands out.", style = MM.type.secondary, color = c.inkSecondary)
        }
        StepType.SCRIPTURE -> {
            Text("Scripture", style = MM.type.heading, color = c.ink)
            Text("Read the passage slowly. The next step asks what you notice.", style = MM.type.secondary, color = c.inkSecondary)
        }
        StepType.OBSERVE, StepType.APPLY, StepType.VERSE -> {
            if (step.type == StepType.VERSE && step.verse != null) {
                val verse = ready.verses.firstOrNull { it.number == step.verse }
                SelectionContainer {
                    Text("${step.verse}  ${verse?.text.orEmpty()}", style = MM.type.scripture, color = c.ink, modifier = Modifier.testTag("session_verse"))
                }
                TextAction("Original words, commentary, cross-references", onClick = { actions.onGoDeeper(step.verse) }, modifier = Modifier.testTag("session_deeper"))
            } else if (step.type == StepType.VERSE) {
                TextAction("Original words, commentary, cross-references", onClick = { actions.onGoDeeper(null) })
            }
            val question = session.prompts[step.id] ?: step.title
            SelectionContainer { Text(question, style = MM.type.heading, color = c.ink, modifier = Modifier.testTag("session_question")) }
            AnswerField(step, session, actions, "Your thoughts (optional)")
            Suggested(help, onUse = { q -> actions.onUseQuestion(step, q) })
        }
        StepType.PRAYER -> {
            Text("Prayer", style = MM.type.heading, color = c.ink)
            Text(QuestionBank.PRAYER_PROMPT, style = MM.type.secondary, color = c.inkSecondary)
            InsetPanel { SelectionContainer { Text(QuestionBank.prayer(request.need), style = MM.type.scripture, color = c.ink) } }
            AnswerField(step, session, actions, "Your own words (optional)")
        }
        StepType.LIVE -> {
            Text("Live it today", style = MM.type.heading, color = c.ink)
            InsetPanel { SelectionContainer { Text(QuestionBank.liveIt(request.need), style = MM.type.body, color = c.ink) } }
            AnswerField(step, session, actions, "Your one thing for today (optional)")
        }
        StepType.CLOSE -> Close(ui, session, actions)
    }
}

/** A free-text answer, always optional. Typing here saves it to the journal on the first keystroke. */
@Composable
private fun AnswerField(step: SessionStep, session: SessionState, actions: SessionActions, hint: String) {
    val c = MM.colors
    var text by remember(step.id) { mutableStateOf(session.answers[step.id].orEmpty()) }
    InsetPanel {
        BasicTextField(
            value = text, onValueChange = { text = it; actions.onAnswer(step.id, it) },
            textStyle = MM.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 2).testTag("session_answer"),
            decorationBox = { inner ->
                Box { if (text.isEmpty()) Text(hint, style = MM.type.body, color = c.inkMuted); inner() }
            }
        )
    }
}

/** What the model added to a step: cited background, other questions, or the one honest line when it could not. */
@Composable
private fun Suggested(help: StepHelp?, onUse: ((String) -> Unit)?) {
    val c = MM.colors
    when {
        help == null -> Unit
        help.loading -> Text("Zuri is thinking of questions…", style = MM.type.caption, color = c.inkMuted)
        help.line != null -> StatusLine(StatusKind.Info, help.line, modifier = Modifier.testTag("session_ai_line"))
        else -> Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            if (help.context.isNotEmpty()) {
                Text("Background, suggested by AI", style = MM.type.caption, color = c.inkMuted)
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        help.context.forEach { Text("${it.text} (${it.cite.display()})", style = MM.type.secondary, color = c.inkSecondary) }
                    }
                }
            }
            if (onUse != null && help.questions.isNotEmpty()) {
                Text("More questions from Zuri", style = MM.type.caption, color = c.inkMuted)
                help.questions.forEach { q -> InsetPanel(onClick = { onUse(q) }) { Text(q, style = MM.type.secondary, color = c.ink) } }
            }
        }
    }
}

@Composable
private fun Close(ui: GuidedUi, session: SessionState, actions: SessionActions) {
    val c = MM.colors
    Text(if (session.touched) "Well done" else "That's the reading", style = MM.type.title, color = c.ink)
    if (session.touched) {
        StatusLine(StatusKind.Info, "Saved to your study journal.", modifier = Modifier.testTag("session_saved"))
        ui.addedTo?.let { StatusLine(StatusKind.Info, "Added to $it.") }
        SecondaryButton("Add to an existing note", onClick = actions.onAddToNote, modifier = Modifier.testTag("session_add_to_note"))
        TextAction("Copy my notes", onClick = actions.onCopyNotes)
    } else {
        Text("You didn't write anything, so nothing was saved.", style = MM.type.secondary, color = c.inkSecondary)
    }
    PrimaryButton("Done", onClick = actions.onNext, modifier = Modifier.testTag("session_done"))
    if (session.index > 0) TextAction("Back", onClick = actions.onBack)
}

/** Pick a note to add the session's answers to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotePicker(load: suspend () -> List<Note>, onPick: (Note) -> Unit, onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    LaunchedEffect(Unit) { notes = load() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.l)) {
            Text("Add to which note?", style = MM.type.title, color = MM.colors.ink)
            val list = notes
            when {
                list == null -> Text("Loading…", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.m))
                list.isEmpty() -> Text("You don't have any notes yet.", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.m))
                else -> Column(Modifier.heightIn(max = MMSize.minTouch * 8).verticalScroll(rememberScrollState())) {
                    list.forEach { n -> ListRow(title = n.title.ifBlank { "Untitled" }, onClick = { onPick(n) }) }
                }
            }
        }
    }
}
