package com.example.feature.meetingdetail.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.Formatters
import com.example.core.model.ChatMessage
import com.example.core.model.TranscriptSegment
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line

/**
 * The redesigned Ask AI tab (docs/recording-page-implementation.md §2.8): no chat bubbles, the
 * exchange reads as a document. Question recedes to InkMuted, the answer is prose with inline
 * citation chips (real ones only — see [AskAnswerText]), and every answer carries an honest
 * footprint line.
 *
 * Not built this phase, disclosed: "Ask next" follow-ups (§3.5 item 31) — the spec is explicit
 * they must be "generated from the transcript, not hard-coded," and there's no real generation
 * wired up yet, so this omits the section rather than show fabricated-looking suggestions. The
 * running state also doesn't claim per-chunk progress ("chunk 2 of 6") since that isn't plumbed
 * from the use case to this screen yet — it shows a plain, honest "thinking" state instead.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AskAiPanel(
    chatMessages: List<ChatMessage>,
    allSegments: List<TranscriptSegment>,
    totalSegmentCount: Int,
    isAnswering: Boolean,
    pendingQuestion: String,
    onSendQuestion: (String) -> Unit,
    onPlayFrom: (Long) -> Unit,
    onOpenInTranscript: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var openCitation by remember { mutableStateOf<Pair<String, String>?>(null) } // messageId to MM:SS label
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size, isAnswering) {
        val lastIndex = chatMessages.size - 1 + (if (isAnswering) 1 else 0)
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    val presetChips = listOf(
        "What did we agree on?",
        "What are the next steps?",
        "Who has the most to do?",
        "Summarise this recording"
    )

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presetChips.forEach { chip ->
                                Text(
                                    text = chip,
                                    fontSize = 13.sp,
                                    color = Ink,
                                    modifier = Modifier
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, Line, RoundedCornerShape(10.dp))
                                        .clickable { onSendQuestion(chip) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            items(chatMessages, key = { it.id }) { msg ->
                if (msg.isUser) {
                    Text(text = msg.content, style = RecordingPageType.askQuestion)
                } else {
                    Column {
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
                                    onPlayFrom = { onPlayFrom(segment.startMs) },
                                    onOpenInTranscript = { onOpenInTranscript(segment.startMs) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Read ${msg.readSegmentCount} of $totalSegmentCount segments to answer this. Nothing outside the transcript was used.",
                            fontSize = 12.5.sp,
                            lineHeight = 20.sp,
                            color = InkMuted
                        )
                    }
                }
            }

            if (isAnswering) {
                item {
                    SunkenPanel {
                        Text(text = pendingQuestion, fontSize = 15.sp, color = Ink)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
                            Text(text = "Reading the transcript, on this phone.", fontSize = 13.sp, color = InkSecondary)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(26.dp))
                    .border(1.dp, Line, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                if (inputText.isEmpty()) {
                    Text(text = "Ask about this recording…", fontSize = 14.5.sp, color = InkMuted)
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.5.sp, color = Ink),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Ink, CircleShape)
                    .clickable(enabled = inputText.isNotBlank()) {
                        onSendQuestion(inputText.trim())
                        inputText = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CitationDetailPanel(
    segment: TranscriptSegment,
    onPlayFrom: () -> Unit,
    onOpenInTranscript: () -> Unit
) {
    SunkenPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SpeakerAvatar(initial = segment.speakerName ?: "?", color = Accent, size = 22.dp)
            Text(
                text = "${segment.speakerName ?: "Unlabeled speaker"} · ${Formatters.formatDurationHms(segment.startMs)}",
                fontSize = 12.5.sp,
                color = InkMuted
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "\"${segment.text}\"", fontSize = 14.5.sp, lineHeight = 24.sp, color = Ink)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Play from ${Formatters.formatDurationHms(segment.startMs)}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .background(Ink, RoundedCornerShape(11.dp))
                    .clickable(onClick = onPlayFrom)
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            )
            Text(
                text = "Open in transcript",
                fontSize = 12.5.sp,
                color = InkSecondary,
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(11.dp))
                    .border(1.dp, Line, RoundedCornerShape(11.dp))
                    .clickable(onClick = onOpenInTranscript)
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            )
        }
    }
}
