package com.craftflowtechnologies.meetingmind.feature.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantAction
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantMessage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiRecoveryBanner
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiThinkingSkeleton
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.displayName
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.isNearBottom
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
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
    val busyActionIds by session.busyActionIds.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var confirmClear by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val list = rememberLazyListState()

    LaunchedEffect(messages.size, working) {
        if (messages.isNotEmpty() && list.isNearBottom()) list.animateScrollToItem(messages.size)
    }

    fun submitInput() {
        val text = input.text
        if (text.isBlank() || working != null) return
        session.send(text)
        input = TextFieldValue("")
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = SurfaceBase,
            title = { Text("Clear conversation?", color = Ink) },
            text = { Text("This clears the current chat history from this sheet. Changes already saved to your notes or tasks stay intact.", color = InkSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; session.clear() }) {
                    Text("Clear", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep", color = InkSecondary) }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceBase, modifier = Modifier.fillMaxHeight(0.94f)
    ) {
        Column(Modifier.fillMaxHeight().imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val companionSettings by rememberCompanionSettings()
                val lastReply = messages.lastOrNull { !it.fromUser }
                val companionState = when {
                    working != null -> CompanionState.THINKING
                    lastReply?.error == true -> CompanionState.WORRIED
                    else -> CompanionState.IDLE
                }
                val form = companionSettings.form
                if (form != null) {
                    // Faith wears the space's warm gold rim (ZURI_EXPERIENCE §2.6); Notes keeps the
                    // user's own accent. Scoped to just the avatar so nothing else in the sheet shifts hue.
                    val avatarColors = if (scope?.faith == true) MMAccent.Gold.on(MM.colors) else MM.colors
                    CompositionLocalProvider(LocalMMColors provides avatarColors) {
                        ZuriSlot(page = CompanionPage.ASK, size = 34.dp, state = companionState)
                    }
                } else {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(AccentWash), contentAlignment = Alignment.Center) {
                        Icon(AiMark, null, tint = Accent, modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(form?.let { companionSettings.displayName(it) } ?: "Assistant", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        scope?.let { "${it.kindLabel} · ${it.title}" } ?: "", fontSize = 12.sp, color = InkMuted,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.sizeIn(minWidth = MMSize.minTouch, minHeight = MMSize.minTouch)
                    ) { Icon(Icons.Filled.DeleteOutline, "Clear conversation", tint = InkMuted) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                Surface(
                                    onClick = {
                                        if (s.prompt.endsWith(" ")) {
                                            input = TextFieldValue(s.prompt, TextRange(s.prompt.length))
                                            runCatching { focusRequester.requestFocus() }
                                        } else {
                                            session.send(s.prompt)
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = SurfaceBase,
                                    border = BorderStroke(1.dp, LineSoft),
                                    modifier = Modifier.heightIn(min = MMSize.minTouch).semantics { role = Role.Button }
                                ) {
                                    Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                        Text(s.label, fontSize = 13.5.sp, color = Ink)
                                    }
                                }
                            }
                        }
                    }
                }
                items(messages.size) { i ->
                    val m = messages[i]
                    if (m.fromUser) UserBubble(m.text) else AssistantBubble(m, session, busyActionIds, onOpenNote, onOpenTasks, onRetry = if (m.error && m.retryable && i == messages.lastIndex) session::retry else null)
                }
                item("working") { if (working != null) Working(working!!) else Spacer(Modifier.height(1.dp)) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).background(SurfaceSunk).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (input.text.isEmpty()) Text("Message the assistant", fontSize = 15.sp, color = InkFaint)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(fontSize = 15.sp, color = Ink),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitInput() }),
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Message the assistant" }
                            .testTag("assistant_input")
                    )
                }
                val busy = working != null
                val canAct = busy || input.text.isNotBlank()
                Box(
                    Modifier
                        .sizeIn(minWidth = MMSize.minTouch, minHeight = MMSize.minTouch)
                        .clip(CircleShape)
                        .clickable(
                            enabled = canAct,
                            role = Role.Button,
                            onClickLabel = if (busy) "Stop response" else "Send message"
                        ) {
                            if (busy) session.stop() else submitInput()
                        }
                        .semantics { contentDescription = if (busy) "Stop" else "Send" }
                        .testTag("assistant_send"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(if (canAct) Ink else InkFaint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = OnInk, modifier = Modifier.size(18.dp))
                    }
                }
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
    AiThinkingSkeleton(
        label = what.let { if (it == "Thinking") "Thinking…" else it },
        lines = listOf(0.85f, 0.58f)
    )
}

@Composable
private fun AssistantBubble(
    m: AssistantMessage,
    session: AssistantSession,
    busyActionIds: Set<String>,
    onOpenNote: (String) -> Unit,
    onOpenTasks: () -> Unit,
    onRetry: (() -> Unit)?
) {
    Column(
        Modifier.fillMaxWidth().animateContentSize().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (m.activity.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            m.activity.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = InkFaint, modifier = Modifier.size(13.dp))
                    Text(line, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (m.error) {
            AiRecoveryBanner(
                message = m.text.ifBlank { "Something went wrong before I could answer." },
                onRetry = onRetry
            )
        } else if (m.text.isNotBlank()) {
            Text(
                markdown(m.text), fontSize = 15.5.sp, lineHeight = 24.sp, color = Ink,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
        m.actions.forEach { ActionCard(it, session, busy = it.id in busyActionIds, onOpenNote, onOpenTasks) }
        if (m.text.isNotBlank() && !m.error) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AiCopyButton(text = m.text)
                m.provider?.let { Text("via $it", fontSize = 11.5.sp, color = InkFaint) }
            }
        }
    }
}

@Composable
private fun ActionCard(
    a: AssistantAction,
    session: AssistantSession,
    busy: Boolean,
    onOpenNote: (String) -> Unit,
    onOpenTasks: () -> Unit
) {
    val muted = a.state == AssistantAction.State.UNDONE || a.state == AssistantAction.State.DISCARDED
    var showPreview by remember { mutableStateOf(a.needsConfirmation) }
    Column(
        Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(14.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(14.dp)).padding(12.dp)
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
                TextButton(
                    onClick = { showPreview = !showPreview },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Text(if (showPreview) "Hide" else "Show", fontSize = 12.5.sp, color = Accent)
                }
            }
        }
        if (showPreview && a.preview != null) {
            Text(
                a.preview, fontSize = 12.5.sp, lineHeight = 18.sp, color = InkSecondary, maxLines = 12, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceSunk).padding(10.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            when (a.state) {
                AssistantAction.State.PENDING -> {
                    TextButton(onClick = { session.discard(a) }, enabled = !busy) { Text("Keep mine", color = if (busy) InkFaint else InkSecondary) }
                    Surface(
                        onClick = { session.confirm(a) },
                        enabled = !busy,
                        shape = RoundedCornerShape(50),
                        color = if (busy) InkMuted else Ink,
                        modifier = Modifier.heightIn(min = 42.dp).semantics { role = Role.Button }.testTag("assistant_apply")
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = OnInk)
                            Text(if (busy) "Applying…" else "Apply", color = OnInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                AssistantAction.State.APPLIED -> {
                    a.openNoteId?.let { id -> TextButton(onClick = { onOpenNote(id) }) { Text("Open", color = Accent); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent, modifier = Modifier.size(14.dp)) } }
                    a.openTaskId?.let { TextButton(onClick = onOpenTasks) { Text("Tasks", color = Accent) } }
                    TextButton(onClick = { session.undo(a) }, enabled = !busy) { Text(if (busy) "Undoing…" else "Undo", color = if (busy) InkFaint else InkSecondary) }
                }
                else -> Unit
            }
        }
    }
}

/** Bold, italics, inline code, fenced code blocks, blockquotes, lists, headings and citation markers. */
@Composable
private fun markdown(text: String): AnnotatedString {
    val accent = Accent
    val codeBg = SurfaceSunk
    val quoteColor = InkSecondary
    return buildAnnotatedString {
        val citation = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        val token = Regex("\\*\\*(.+?)\\*\\*|(?<![*\\w])\\*(.+?)\\*(?![*\\w])|`([^`]+)`|\\[(\\d{1,2}:\\d{2}(?::\\d{2})?)]")
        var inCodeFence = false
        var firstLine = true
        text.lines().forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence
                return@forEach
            }
            if (!firstLine) append('\n')
            firstLine = false
            if (inCodeFence) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp, background = codeBg)) { append(raw) }
                return@forEach
            }
            val heading = Regex("^#{1,3}\\s+(.*)").find(raw)
            if (heading != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)) { append(heading.groupValues[1]) }
                return@forEach
            }
            val quote = Regex("^>\\s?(.*)").find(raw)
            val line = if (quote != null) {
                withStyle(SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic)) { append("│  " + quote.groupValues[1]) }
                return@forEach
            } else {
                raw.replace(Regex("^\\s*[-*]\\s+"), "•  ")
            }
            var i = 0
            token.findAll(line).forEach { m ->
                append(line.substring(i, m.range.first))
                when {
                    m.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
                    m.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
                    m.groupValues[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(m.groupValues[3]) }
                    else -> withStyle(citation) { append("[" + m.groupValues[4] + "]") }
                }
                i = m.range.last + 1
            }
            append(line.substring(i))
        }
    }
}
