package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ai.assistant.AskScope
import com.craftflowtechnologies.meetingmind.ai.assistant.AskSource
import com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAnswer
import com.craftflowtechnologies.meetingmind.ai.assistant.ScopedReason
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import kotlinx.coroutines.launch

/**
 * Ask about a person, organisation or project, or all of Work (docs/PLAN_PROFESSIONAL.md D5.5).
 * Opened from a context page it starts scoped, and the scope chip can be cleared to ask across
 * everything. An answer appears only with the sources it cites; without a model the sources that
 * search found are listed instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopedAskSheet(initial: AskScope, viewModel: WorkViewModel, onOpenMeeting: (String, Long?) -> Unit, onOpenNote: (String) -> Unit, onDismiss: () -> Unit) {
    var scope by remember { mutableStateOf(initial) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<ScopedAnswer?>(null) }
    var working by remember { mutableStateOf(false) }
    val coroutine = rememberCoroutineScope()

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || working) return
        working = true; answer = null
        coroutine.launch { answer = viewModel.scopedAsk.ask(q, scope); working = false }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("scoped_ask")) {
            Text("Ask", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The scope chip: tap to clear it and ask across all of Work.
                Chip(if (scope.type != null || scope.from != null) "${scope.label}  ✕" else scope.label, selected = scope.type != null || scope.from != null) {
                    if (scope.type != null || scope.from != null) scope = AskScope()
                }
            }
            OutlinedTextField(
                question, { question = it }, placeholder = { Text("What did we decide? What do I owe?") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ask_field"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { ask() }),
                trailingIcon = { TextButton(onClick = { ask() }, enabled = question.isNotBlank() && !working) { Text(if (working) "…" else "Ask") } }
            )
            val a = answer
            when {
                a == null -> if (working) Text("Reading ${scope.label.lowercase()}…", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 12.dp))
                else -> Column(Modifier.padding(top = 12.dp)) {
                    a.text?.let { Text(it, fontSize = 15.sp, color = Ink, lineHeight = 21.sp, modifier = Modifier.testTag("ask_answer")) }
                    when (a.reason) {
                        ScopedReason.NO_MODEL -> Text("No written answer without a model. Here is what your record holds on it:", fontSize = 13.sp, color = InkSecondary)
                        ScopedReason.UNCITED -> Text("I couldn't ground an answer in your record, so I won't guess. These are the closest sources:", fontSize = 13.sp, color = InkSecondary)
                        ScopedReason.FAILED -> Text("Ask didn't work this time. These are the sources it found:", fontSize = 13.sp, color = InkSecondary)
                        else -> Unit
                    }
                    val shown = if (a.cited.isNotEmpty()) a.cited else if (a.reason == ScopedReason.NOTHING_FOUND) emptyList() else a.sources
                    if (shown.isNotEmpty()) WorkSectionTitle(if (a.cited.isNotEmpty()) "Sources" else "Found", "${shown.size}", top = 12.dp)
                    shown.forEach { s -> SourceRow(s, { s.meetingId?.let { m -> onDismiss(); onOpenMeeting(m, s.startMs) } ?: s.noteId?.let { n -> onDismiss(); onOpenNote(n) } }) }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(s: AskSource, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text("[${s.key}] ${s.title}", fontSize = 12.sp, color = InkMuted, fontWeight = FontWeight.SemiBold)
        Text(s.text, fontSize = 14.sp, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
