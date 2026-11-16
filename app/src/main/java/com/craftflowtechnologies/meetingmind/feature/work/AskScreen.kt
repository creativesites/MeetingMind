package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.craftflowtechnologies.meetingmind.ai.assistant.AskScope
import com.craftflowtechnologies.meetingmind.ai.assistant.AskSource
import com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAnswer
import com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAskStreamEvent
import com.craftflowtechnologies.meetingmind.ai.assistant.ScopedReason
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiRecoveryBanner
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiThinkingSkeleton
import com.craftflowtechnologies.meetingmind.core.ui.mm.citedAnnotatedString
import com.craftflowtechnologies.meetingmind.core.ui.work.CiteChip
import com.craftflowtechnologies.meetingmind.core.ui.work.SourceChip
import com.craftflowtechnologies.meetingmind.core.ui.work.WhereItRan
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A suggested question. [instant] is the structured query it runs; null means a model answers it. */
internal data class SuggestedQuestion(val text: String, val instant: InstantQuery?)

/** The suggestions in the order they are shown. The first four are instant: no model, works offline. */
internal fun suggestedQuestions(): List<SuggestedQuestion> = listOf(
    SuggestedQuestion("What am I waiting for?", InstantQuery.WAITING_FOR),
    SuggestedQuestion("What have I promised?", InstantQuery.PROMISED),
    SuggestedQuestion("What did we decide this month?", InstantQuery.DECISIONS_THIS_MONTH),
    SuggestedQuestion("What changed since last week?", InstantQuery.CHANGED_THIS_WEEK),
    SuggestedQuestion("What did we decide most recently?", null),
    SuggestedQuestion("What should I raise next time?", null)
)

/** The structured query behind a question that is one of the suggestions. Anything else is null and goes to the model. */
internal fun instantQueryFor(question: String): InstantQuery? {
    val wanted = normaliseQuestion(question)
    if (wanted.isEmpty()) return null
    return suggestedQuestions().firstOrNull { normaliseQuestion(it.text) == wanted }?.instant
}

private fun normaliseQuestion(text: String): String = text.trim().trimEnd('?', '.', '!', ' ').lowercase()

/** The scope with its time range: "This month" limits it from the first of the month, otherwise it is open. */
internal fun scopeFor(base: AskScope, thisMonth: Boolean, now: Long): AskScope =
    base.copy(from = if (thisMonth) monthStart(now) else null, to = null)

/**
 * Ask as a full screen, opened scoped to the page it came from. The scope chips widen or narrow
 * it. Suggested questions run offline where they map to a structured query; anything else is
 * answered from cited sources only, and with no source there is no answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    initial: AskScope,
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenNote: (String) -> Unit,
    confidential: Boolean = false
) {
    val pageVm: ContextPageViewModel = composeViewModel()
    val now = remember { System.currentTimeMillis() }
    var entityOn by rememberSaveable { mutableStateOf(initial.type != null) }
    var thisMonth by rememberSaveable { mutableStateOf(false) }
    val scope = scopeFor(if (entityOn && initial.type != null) initial else AskScope(label = "All of Work"), thisMonth, now)
    val label = scope.label

    var question by rememberSaveable { mutableStateOf("") }
    var asked by remember { mutableStateOf<String?>(null) }
    var instant by remember { mutableStateOf<List<InstantLine>>(emptyList()) }
    var instantRan by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<ScopedAnswer?>(null) }
    var streaming by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var askJob by remember { mutableStateOf<Job?>(null) }
    var source by remember { mutableStateOf<SourceRef?>(null) }
    val coroutine = rememberCoroutineScope()

    fun stop() {
        askJob?.cancel()
        askJob = null
        streaming = null
        working = false
    }

    fun submit(text: String) {
        val q = text.trim()
        if (q.isEmpty() || working) return
        asked = q
        answer = null
        instant = emptyList()
        instantRan = false
        streaming = null
        val query = instantQueryFor(q)
        if (query != null) {
            working = true
            askJob = coroutine.launch {
                try {
                    instant = pageVm.instant(query, scope)
                    instantRan = true
                } finally {
                    working = false
                }
            }
            return
        }
        working = true
        askJob = coroutine.launch {
            try {
                viewModel.scopedAsk.askStream(q, scope).collect { ev ->
                    when (ev) {
                        is ScopedAskStreamEvent.Partial -> streaming = ev.text
                        is ScopedAskStreamEvent.Complete -> {
                            answer = ev.answer
                            streaming = null
                        }
                    }
                }
            } finally {
                streaming = null
                working = false
            }
        }
    }

    fun openSource(s: AskSource) {
        when {
            s.meetingId != null -> source = SourceRef(meetingId = s.meetingId, startMs = s.startMs, title = s.title, quote = s.text.takeIf { it.isNotBlank() })
            s.noteId != null -> onOpenNote(s.noteId)
        }
    }

    Scaffold(
        containerColor = SurfaceBase,
        bottomBar = {
            AskInputBar(
                text = question,
                placeholder = "Ask about $label",
                working = working,
                onChange = { question = it },
                onSend = { submit(question); question = "" },
                onStop = ::stop
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
                .animateContentSize()
                .testTag("ask_screen")
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.ink) }
                Column(Modifier.weight(1f).padding(start = MM.space.xs)) {
                    Text("Ask", style = MM.type.heading, color = MM.colors.ink)
                    Text("Your professional memory", style = MM.type.caption, color = MM.colors.inkMuted)
                }
            }

            Row(
                Modifier.padding(top = MM.space.s).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                if (initial.type != null) {
                    Chip(initial.label, selected = entityOn) { entityOn = true }
                }
                Chip("All of Work", selected = !entityOn) { entityOn = false }
                Chip("This month", selected = thisMonth) { thisMonth = !thisMonth }
            }

            if (confidential) {
                Text(
                    "Confidential. Answers for this page are written on this phone only.",
                    style = MM.type.caption,
                    color = MM.colors.inkMuted,
                    modifier = Modifier.padding(top = MM.space.s)
                )
            }

            val q = asked
            if (q == null) {
                Text(
                    "What do you want to know about $label?",
                    style = MM.type.heading,
                    color = MM.colors.ink,
                    modifier = Modifier.padding(top = MM.space.l)
                )
                Text(
                    "Answers come from your meetings, notes and files, and every line opens its source.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(top = MM.space.xs)
                )
                Column(Modifier.padding(top = MM.space.m).testTag("ask_suggestions"), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    suggestedQuestions().forEach { s -> SuggestionRow(s) { submit(s.text) } }
                }
            } else {
                QuestionBubble(q)
                if (instantQueryFor(q) != null) {
                    InstantPanel(
                        ran = instantRan,
                        lines = instant,
                        label = label,
                        onOpen = { line -> line.itemId?.let { source = SourceRef(itemId = it, meetingId = line.meetingId) } }
                    )
                } else {
                    ModelPanel(
                        answer = answer,
                        streaming = streaming,
                        working = working,
                        label = label,
                        confidential = confidential,
                        onStop = ::stop,
                        onRetry = { submit(q) },
                        onOpenSource = ::openSource
                    )
                }
            }
            Spacer(Modifier.heightIn(min = MM.space.xl))
        }
    }

    source?.let { s ->
        SourceSheet(
            source = s,
            onOpenInTranscript = { meetingId, at -> source = null; onOpenMeeting(meetingId, at) },
            onDismiss = { source = null }
        )
    }
}

@Composable
private fun SuggestionRow(s: SuggestedQuestion, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = MM.colors.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
    ) {
        Row(Modifier.padding(horizontal = MM.space.l, vertical = MM.space.m), verticalAlignment = Alignment.CenterVertically) {
            Text(s.text, style = MM.type.bodyStrong, color = MM.colors.ink, modifier = Modifier.weight(1f))
            if (s.instant != null) Text("instant", style = MM.type.caption, color = MM.colors.success)
        }
    }
}

@Composable
private fun QuestionBubble(q: String) {
    Row(Modifier.fillMaxWidth().padding(top = MM.space.m), horizontalArrangement = Arrangement.End) {
        Surface(color = MM.colors.ink, shape = MM.radius.card) {
            Text(
                q,
                style = MM.type.secondary,
                color = MM.colors.background,
                modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s)
            )
        }
    }
}

/** Structured-query answer: the matching lines, each opening its source. No model is involved. */
@Composable
private fun InstantPanel(ran: Boolean, lines: List<InstantLine>, label: String, onOpen: (InstantLine) -> Unit) {
    if (!ran) {
        Text("Looking in your record…", style = MM.type.secondary, color = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.m))
        return
    }
    if (lines.isEmpty()) {
        NoSourceCard("Nothing in ${label.lowercase()} matches that right now. When it comes up in a meeting, it will be listed here.")
        return
    }
    WorkSectionTitle("From your record", "${lines.size}", top = MM.space.l)
    lines.forEach { line ->
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = line.itemId != null) { onOpen(line) }
                .padding(vertical = MM.space.s)
        ) {
            Text(line.text, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            line.detail?.let { Text(it, style = MM.type.caption, color = MM.colors.inkMuted) }
        }
    }
    Row(Modifier.padding(top = MM.space.s)) { WhereItRan(onDevice = true) }
}

/** Model answer: prose with cited numbers, the sources behind them, or the reason there is none. */
@Composable
private fun ModelPanel(
    answer: ScopedAnswer?,
    streaming: String?,
    working: Boolean,
    label: String,
    confidential: Boolean,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onOpenSource: (AskSource) -> Unit
) {
    val a = answer
    val partial = streaming
    when {
        a == null && !partial.isNullOrBlank() -> Column(Modifier.padding(top = MM.space.m).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text("Answering…", style = MM.type.overline, color = MM.colors.accent)
            Text(partial, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.padding(top = MM.space.xs))
        }
        a == null -> if (working) {
            AiThinkingSkeleton(
                label = "Searching ${label.lowercase()}…",
                modifier = Modifier.padding(top = MM.space.m),
                onStop = onStop
            )
        }
        else -> Column(Modifier.padding(top = MM.space.m).semantics { liveRegion = LiveRegionMode.Polite }) {
            val body = a.text
            if (body != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Answer", style = MM.type.overline, color = MM.colors.accent, modifier = Modifier.weight(1f))
                    AiCopyButton(text = body)
                }
                Text(
                    citedAnnotatedString(body, MM.colors.accent),
                    style = MM.type.body,
                    color = MM.colors.ink,
                    modifier = Modifier.padding(top = MM.space.xs).testTag("ask_answer")
                )
                if (a.cited.isNotEmpty()) {
                    Row(Modifier.padding(top = MM.space.s).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        a.cited.forEach { s -> CiteChip(n = s.key, onClick = { onOpenSource(s) }) }
                    }
                }
                Row(Modifier.padding(top = MM.space.s)) { WhereItRan(onDevice = confidential) }
            }
            when (a.reason) {
                ScopedReason.NO_MODEL -> AiRecoveryBanner(
                    message = "Written answers need an AI model set up in Settings. Here is what your record holds on it:",
                    modifier = Modifier.padding(top = MM.space.s)
                )
                ScopedReason.UNCITED -> NoSourceCard(
                    "I couldn't find a sentence in your record that answers this, so I won't guess. The closest sources are listed below."
                )
                ScopedReason.NOTHING_FOUND -> NoSourceCard(
                    "Nothing in ${label.lowercase()} mentions that yet. If it came up in a meeting, record or file that meeting here, then ask again."
                )
                ScopedReason.FAILED -> AiRecoveryBanner(
                    message = "Ask didn't work this time. These are the sources it found:",
                    modifier = Modifier.padding(top = MM.space.s),
                    onRetry = onRetry
                )
                null -> Unit
            }
            val shown = when {
                a.cited.isNotEmpty() -> a.cited
                a.reason == ScopedReason.NOTHING_FOUND -> emptyList()
                else -> a.sources
            }
            if (shown.isNotEmpty()) {
                WorkSectionTitle(if (a.cited.isNotEmpty()) "Sources" else "Found", "${shown.size}", top = MM.space.l)
                shown.forEach { s -> SourceLine(s) { onOpenSource(s) } }
            }
        }
    }
}

@Composable
private fun SourceLine(s: AskSource, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = MM.space.xs), verticalAlignment = Alignment.Top) {
        SourceChip(label = s.startMs?.let { formatTime(it) } ?: s.kind.label, onClick = onOpen)
        Column(Modifier.weight(1f).padding(start = MM.space.xs)) {
            Text(s.title, style = MM.type.caption, color = MM.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.text, style = MM.type.secondary, color = MM.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "No source, no answer": said plainly, with what to do next. */
@Composable
private fun NoSourceCard(message: String) {
    Surface(
        color = MM.colors.surfaceSunk,
        shape = MM.radius.card,
        modifier = Modifier.fillMaxWidth().padding(top = MM.space.m)
    ) {
        Column(Modifier.padding(horizontal = MM.space.l, vertical = MM.space.m)) {
            Text("No source, no answer", style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(message, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
        }
    }
}

@Composable
private fun AskInputBar(
    text: String,
    placeholder: String,
    working: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        Modifier
            .navigationBarsPadding()
            .imePadding()
            .fillMaxWidth()
            .padding(horizontal = MM.space.l, vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = MM.radius.card,
            modifier = Modifier.weight(1f).testTag("ask_field"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!working && text.isNotBlank()) onSend() })
        )
        if (working) {
            IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = MM.colors.accent) }
        } else {
            val canAsk = text.isNotBlank()
            IconButton(onClick = onSend, enabled = canAsk) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Ask",
                    tint = if (canAsk) MM.colors.accent else MM.colors.inkMuted
                )
            }
        }
    }
}
