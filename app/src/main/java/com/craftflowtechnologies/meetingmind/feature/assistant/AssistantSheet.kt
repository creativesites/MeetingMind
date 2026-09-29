package com.craftflowtechnologies.meetingmind.feature.assistant

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantAction
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantMessage
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.Success
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

/**
 * The assistant as a tall sheet over the note (or the library): a conversation, the tools it used
 * shown as quiet lines, what it changed as cards with Undo, and confirmations for anything that
 * would replace or remove the person's words.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantSheet(session: AssistantSession, onOpenNote: (String) -> Unit, onOpenTasks: () -> Unit, onDismiss: () -> Unit) {
    val messages by session.messages.collectAsState()
    val working by session.working.collectAsState()
    val scope by session.assistantScope.collectAsState()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size, working) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceBase, modifier = Modifier.fillMaxHeight(0.94f)
    ) {
        Column(Modifier.fillMaxHeight().imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(AccentWash), contentAlignment = Alignment.Center) {
                    Icon(AiMark, null, tint = Accent, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Assistant", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        scope?.let { "${it.kindLabel} · ${it.title}" } ?: "", fontSize = 12.sp, color = InkMuted,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (messages.isNotEmpty()) IconButton(onClick = session::clear) { Icon(Icons.Filled.DeleteOutline, "Clear conversation", tint = InkMuted) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (messages.isEmpty()) item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(
                            if (scope?.noteId != null) "Ask about this note, or ask me to change it." else "Ask across your notes, sermons and the Bible.",
                            fontSize = 15.sp, color = InkSecondary, lineHeight = 22.sp
                        )
                        Text(
                            "I add things with Undo. Before I replace or remove anything of yours, I ask.",
                            fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            session.suggestions().forEach { s ->
                                Text(
                                    s.label, fontSize = 13.5.sp, color = Ink,
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, LineSoft, RoundedCornerShape(14.dp))
                                        .clickable { if (s.prompt.endsWith(" ")) input = s.prompt else session.send(s.prompt) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
                items(messages.size) { i ->
                    val m = messages[i]
                    if (m.fromUser) UserBubble(m.text) else AssistantBubble(m, session, onOpenNote, onOpenTasks, onRetry = if (m.error && i == messages.lastIndex) session::retry else null)
                }
                item("working") { if (working != null) Working(working!!) else Spacer(Modifier.height(1.dp)) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).background(SurfaceSunk).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (input.isEmpty()) Text("Message the assistant", fontSize = 15.sp, color = InkFaint)
                    BasicTextField(
                        input, { input = it }, textStyle = TextStyle(fontSize = 15.sp, color = Ink), cursorBrush = SolidColor(Accent),
                        maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("assistant_input")
                    )
                }
                val busy = working != null
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (busy || input.isNotBlank()) Ink else InkFaint)
                        .clickable(enabled = busy || input.isNotBlank()) { if (busy) session.stop() else { session.send(input); input = "" } }
                        .testTag("assistant_send"),
                    contentAlignment = Alignment.Center
                ) { Icon(if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send, if (busy) "Stop" else "Send", tint = OnInk, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text, fontSize = 15.sp, color = Ink, lineHeight = 22.sp,
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(AccentWash).padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun Working(what: String) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "work")
    val a by t.animateFloat(0.3f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Accent.copy(alpha = a)))
        Text(what.let { if (it == "Thinking") "Thinking…" else it }, fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun AssistantBubble(
    m: AssistantMessage, session: AssistantSession, onOpenNote: (String) -> Unit, onOpenTasks: () -> Unit, onRetry: (() -> Unit)?
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (m.activity.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            m.activity.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = InkFaint, modifier = Modifier.size(13.dp))
                    Text(line, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (m.text.isNotBlank()) {
            Text(
                markdown(m.text), fontSize = 15.5.sp, lineHeight = 24.sp, color = if (m.error) Danger else Ink,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
        m.actions.forEach { ActionCard(it, session, onOpenNote, onOpenTasks) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.text.isNotBlank() && !m.error) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(m.text)) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.ContentCopy, "Copy", tint = InkFaint, modifier = Modifier.size(15.dp))
                }
            }
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Try again", color = Accent, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ActionCard(a: AssistantAction, session: AssistantSession, onOpenNote: (String) -> Unit, onOpenTasks: () -> Unit) {
    val muted = a.state == AssistantAction.State.UNDONE || a.state == AssistantAction.State.DISCARDED
    var showPreview by remember { mutableStateOf(a.needsConfirmation) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (a.state) { AssistantAction.State.APPLIED -> Icons.Filled.Check; AssistantAction.State.PENDING -> AiMark; else -> Icons.Filled.Close },
                null, tint = when (a.state) { AssistantAction.State.APPLIED -> Success; AssistantAction.State.PENDING -> Accent; else -> InkFaint }, modifier = Modifier.size(16.dp)
            )
            Text(
                a.label + when (a.state) { AssistantAction.State.UNDONE -> " · undone"; AssistantAction.State.DISCARDED -> " · not done"; else -> "" },
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (muted) InkMuted else Ink, modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            if (a.preview != null && a.state != AssistantAction.State.PENDING) {
                Text(if (showPreview) "Hide" else "Show", fontSize = 12.sp, color = Accent, modifier = Modifier.clickable { showPreview = !showPreview }.padding(4.dp))
            }
        }
        if (showPreview && a.preview != null) {
            Text(
                a.preview, fontSize = 12.5.sp, lineHeight = 18.sp, color = InkSecondary, maxLines = 12, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceSunk).padding(10.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            when (a.state) {
                AssistantAction.State.PENDING -> {
                    TextButton(onClick = { session.discard(a) }) { Text("Keep mine", color = InkSecondary) }
                    Text(
                        "Apply", color = OnInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Ink).clickable { session.confirm(a) }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("assistant_apply")
                    )
                }
                AssistantAction.State.APPLIED -> {
                    a.openNoteId?.let { id -> TextButton(onClick = { onOpenNote(id) }) { Text("Open", color = Accent); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent, modifier = Modifier.size(14.dp)) } }
                    a.openTaskId?.let { TextButton(onClick = onOpenTasks) { Text("Tasks", color = Accent) } }
                    TextButton(onClick = { session.undo(a) }) { Text("Undo", color = InkSecondary) }
                }
                else -> Unit
            }
        }
    }
}

/** Bold, italics, inline code and citation markers, enough for a chat reply; lists and headings keep their text. */
@Composable
private fun markdown(text: String): AnnotatedString {
    val accent = Accent
    return buildAnnotatedString {
    val citation = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    text.lines().forEachIndexed { li, raw ->
        if (li > 0) append('\n')
        var line = raw
        val heading = Regex("^#{1,3}\\s+(.*)").find(line)
        if (heading != null) { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)) { append(heading.groupValues[1]) }; return@forEachIndexed }
        line = line.replace(Regex("^\\s*[-*]\\s+"), "•  ")
        val token = Regex("\\*\\*(.+?)\\*\\*|(?<![*\\w])\\*(.+?)\\*(?![*\\w])|`([^`]+)`|\\[(\\d{1,2}:\\d{2}(?::\\d{2})?)]")
        var i = 0
        token.findAll(line).forEach { m ->
            append(line.substring(i, m.range.first))
            when {
                m.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
                m.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
                m.groupValues[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = androidx.compose.ui.graphics.Color.Unspecified)) { append(m.groupValues[3]) }
                else -> withStyle(citation) { append("[" + m.groupValues[4] + "]") }
            }
            i = m.range.last + 1
        }
        append(line.substring(i))
    }
}
}
