package com.craftflowtechnologies.meetingmind.feature.meetingdetail.components

import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.model.ChatMessage
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiThinkingSkeleton
import com.craftflowtechnologies.meetingmind.core.ui.mm.isNearBottom
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * The redesigned Ask AI tab (docs/recording-page-implementation.md §2.8): no chat bubbles, the
 * exchange reads as a document. Question recedes to InkMuted, the answer is prose with inline
 * citation chips (real ones only — see [AskAnswerText]), and every answer carries an honest
 * footprint line.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AskAiPanel(
    chatMessages: List<ChatMessage>,
    allSegments: List<TranscriptSegment>,
    speakers: List<Speaker>,
    totalSegmentCount: Int,
    isAnswering: Boolean,
    pendingQuestion: String,
    onSendQuestion: (String) -> Unit,
    onPlayFrom: (Long) -> Unit,
    onOpenInTranscript: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onStopQuestion: (() -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    var openCitation by remember { mutableStateOf<Pair<String, String>?>(null) } // messageId to MM:SS label
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size, isAnswering) {
        val lastIndex = chatMessages.size - 1 + (if (isAnswering) 1 else 0)
        if (lastIndex >= 0 && listState.isNearBottom()) listState.animateScrollToItem(lastIndex)
    }

    fun submitQuestion() {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty() || isAnswering) return
        onSendQuestion(trimmed)
        inputText = ""
    }

    val presetChips = listOf(
        "What did we agree on?",
        "What are the next steps?",
        "Who has the most to do?",
        "Summarise this recording"
    )

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().animateContentSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (chatMessages.isEmpty() && !isAnswering) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Ask anything about this recording. Answers are grounded in the real transcript, with a timestamp for every claim.",
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = InkSecondary
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetChips.forEach { chip ->
                                Surface(
                                    onClick = { onSendQuestion(chip) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = SurfaceBase,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                                    modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "Ask: $chip" }
                                ) {
                                    Text(
                                        text = chip,
                                        fontSize = 13.sp,
                                        color = Ink,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(chatMessages, key = { it.id }) { msg ->
                if (msg.isUser) {
                    Text(text = msg.content, style = RecordingPageType.askQuestion)
                } else {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        AskAnswerText(
                            content = msg.content,
                            onCitationClick = { label ->
                                openCitation = if (openCitation == msg.id to label) null else msg.id to label
                            }
                        )
                        val open = openCitation
                        if (open != null && open.first == msg.id) {
                            val segment = allSegments.find { Formatters.formatDurationHms(it.startMs) == open.second }
                            if (segment != null) {
                                Spacer(modifier = Modifier.height(18.dp))
                                CitationDetailPanel(
                                    segment = segment,
                                    speakers = speakers,
                                    onPlayFrom = { onPlayFrom(segment.startMs) },
                                    onOpenInTranscript = { onOpenInTranscript(segment.startMs) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Read ${msg.readSegmentCount} of $totalSegmentCount segments to answer this.",
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = InkMuted,
                                modifier = Modifier.weight(1f)
                            )
                            AiCopyButton(text = msg.content)
                        }
                    }
                }
            }

            if (isAnswering) {
                item {
                    AiThinkingSkeleton(
                        label = pendingQuestion.ifBlank { "Reading the transcript…" },
                        sublabel = "Checking transcript segments for grounded timestamps…",
                        onStop = onStopQuestion
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceBase, RoundedCornerShape(26.dp))
                    .border(1.dp, Line, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                if (inputText.isEmpty()) {
                    Text(text = "Ask about this recording…", fontSize = 14.5.sp, color = InkMuted)
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.5.sp, color = Ink),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitQuestion() }),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Ask about this recording" }
                )
            }
            val stopping = isAnswering && onStopQuestion != null
            val canAct = stopping || (!isAnswering && inputText.isNotBlank())
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = MMSize.minTouch, minHeight = MMSize.minTouch)
                    .clip(CircleShape)
                    .clickable(
                        enabled = canAct,
                        role = Role.Button,
                        onClickLabel = if (stopping) "Stop answer" else "Send question"
                    ) {
                        if (stopping) onStopQuestion?.invoke() else submitQuestion()
                    }
                    .semantics { contentDescription = if (stopping) "Stop" else "Send question" },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(42.dp).background(if (canAct) Ink else InkFaint, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (stopping) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = OnInk,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CitationDetailPanel(
    segment: TranscriptSegment,
    speakers: List<Speaker>,
    onPlayFrom: () -> Unit,
    onOpenInTranscript: () -> Unit
) {
    SunkenPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val speakerIndex = speakers.indexOfFirst { it.id == segment.speakerId }.coerceAtLeast(0)
            val speakerColor = speakerColorFor(speakers.getOrNull(speakerIndex)?.colorHex, speakerIndex)
            SpeakerAvatar(initial = segment.speakerName ?: "?", color = speakerColor, size = 22.dp)
            Text(
                text = "${segment.speakerName ?: "Unknown speaker"} · ${Formatters.formatDurationHms(segment.startMs)}",
                fontSize = 12.5.sp,
                color = InkMuted
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "\"${segment.text}\"", fontSize = 14.5.sp, lineHeight = 24.sp, color = Ink)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = onPlayFrom,
                shape = RoundedCornerShape(11.dp),
                color = Ink,
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text(
                    text = "Play from ${Formatters.formatDurationHms(segment.startMs)}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnInk,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)
                )
            }
            Surface(
                onClick = onOpenInTranscript,
                shape = RoundedCornerShape(11.dp),
                color = SurfaceBase,
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text(
                    text = "Open in transcript",
                    fontSize = 12.5.sp,
                    color = InkSecondary,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)
                )
            }
        }
    }
}
