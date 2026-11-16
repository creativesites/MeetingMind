package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ai.assistant.AskScope
import com.craftflowtechnologies.meetingmind.ai.assistant.AskSource
import com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAnswer
import com.craftflowtechnologies.meetingmind.ai.assistant.ScopedReason
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiRecoveryBanner
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiThinkingSkeleton
import com.craftflowtechnologies.meetingmind.core.ui.mm.citedAnnotatedString
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Ask about a person, organisation or project, or all of Work (docs/PLAN_PROFESSIONAL.md D5.5).
 * Opened from a context page it starts scoped, and the scope chip can be cleared to ask across
 * everything. An answer appears only with the sources it cites; without a model the sources that
 * search found are listed instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopedAskSheet(
    initial: AskScope,
    viewModel: WorkViewModel,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenNote: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Opens the full Ask screen with this scope; the sheet is then dismissed. Null hides the link. */
    onOpenFull: ((AskScope) -> Unit)? = null
) {
    var scope by remember { mutableStateOf(initial) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<ScopedAnswer?>(null) }
    var streamingText by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var askJob by remember { mutableStateOf<Job?>(null) }
    val coroutine = rememberCoroutineScope()

    fun stopAsk() {
        askJob?.cancel()
        askJob = null
        streamingText = null
        working = false
    }

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || working) return
        working = true
        answer = null
        streamingText = null
        askJob = coroutine.launch {
            try {
                viewModel.scopedAsk.askStream(q, scope).collect { ev ->
                    when (ev) {
                        is com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAskStreamEvent.Partial -> streamingText = ev.text
                        is com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAskStreamEvent.Complete -> {
                            answer = ev.answer
                            streamingText = null
                        }
                    }
                }
            } finally {
                streamingText = null
                working = false
            }
        }
    }

    val hasInitialScope = initial.type != null || initial.from != null
    val isScopedNow = scope.type != null || scope.from != null

    ModalBottomSheet(
        onDismissRequest = { stopAsk(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceBase
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .animateContentSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .testTag("scoped_ask")
        ) {
            Text("Ask", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            onOpenFull?.let { open ->
                Text(
                    "Open full screen",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 4.dp).clickable { stopAsk(); onDismiss(); open(scope) }.testTag("ask_open_full")
                )
            }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (isScopedNow) "${scope.label}  ✕" else scope.label, selected = isScopedNow) {
                    if (isScopedNow) {
                        scope = AskScope()
                        answer = null
                    } else if (hasInitialScope) {
                        scope = initial
                        answer = null
                    }
                }
                if (hasInitialScope && !isScopedNow) {
                    Chip("Limit to ${initial.label}", selected = false) {
                        scope = initial
                        answer = null
                    }
                }
            }
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text("What did we decide? What do I owe?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ask_field"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { ask() }),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (question.isNotEmpty() && !working) {
                            IconButton(onClick = { question = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear question", tint = InkMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (working) {
                            IconButton(onClick = ::stopAsk) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Accent, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            val canAsk = question.isNotBlank()
                            IconButton(onClick = ::ask, enabled = canAsk) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Ask",
                                    tint = if (canAsk) Accent else InkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            )
            val a = answer
            val partial = streamingText
            when {
                a == null && !partial.isNullOrBlank() -> Column(Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text("Answering…", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = Accent)
                    Text(partial, fontSize = 15.sp, color = Ink, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
                }
                a == null -> if (working) {
                    AiThinkingSkeleton(
                        label = "Searching ${scope.label.lowercase()}…",
                        modifier = Modifier.padding(top = 12.dp),
                        onStop = ::stopAsk
                    )
                }
                else -> Column(Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    a.text?.let { body ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Answer", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = Accent)
                            AiCopyButton(text = body)
                        }
                        Text(citedAnnotatedString(body, Accent), fontSize = 15.sp, color = Ink, lineHeight = 22.sp, modifier = Modifier.testTag("ask_answer"))
                    }
                    when (a.reason) {
                        ScopedReason.NO_MODEL -> AiRecoveryBanner(
                            message = "No written answer without an AI model configured in Settings. Here is what your record holds on it:",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        ScopedReason.UNCITED -> AiRecoveryBanner(
                            message = "I couldn't ground an answer in your record, so I won't guess. These are the closest sources:",
                            modifier = Modifier.padding(top = 8.dp),
                            onRetry = ::ask
                        )
                        ScopedReason.FAILED -> AiRecoveryBanner(
                            message = "Ask didn't work this time. These are the sources it found:",
                            modifier = Modifier.padding(top = 8.dp),
                            onRetry = ::ask
                        )
                        ScopedReason.NOTHING_FOUND -> if (a.text == null) {
                            Text("Nothing in ${scope.label.lowercase()} matched that question.", fontSize = 13.5.sp, color = InkSecondary, modifier = Modifier.padding(top = 8.dp))
                        }
                        else -> Unit
                    }
                    val shown = if (a.cited.isNotEmpty()) a.cited else if (a.reason == ScopedReason.NOTHING_FOUND) emptyList() else a.sources
                    if (shown.isNotEmpty()) {
                        WorkSectionTitle(if (a.cited.isNotEmpty()) "Sources" else "Found", "${shown.size}", top = 12.dp)
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.forEach { s ->
                                SourceRow(s) {
                                    s.meetingId?.let { m -> onDismiss(); onOpenMeeting(m, s.startMs) }
                                        ?: s.noteId?.let { n -> onDismiss(); onOpenNote(n) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(s: AskSource, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceSunk,
        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).semantics { role = Role.Button }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("[${s.key}] ${s.title}", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(s.text, fontSize = 14.sp, lineHeight = 19.sp, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
