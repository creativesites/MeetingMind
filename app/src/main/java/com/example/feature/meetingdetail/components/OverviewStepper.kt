package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.Formatters
import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.Speaker
import com.example.core.model.TranscriptSegment
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.LineSoft
import com.example.ui.theme.SurfaceTrack
import kotlinx.coroutines.launch

private enum class StepKind { WHAT_HAPPENED, DECISIONS, TASKS, WHO_TALKED }

/**
 * The Overview tab's four-step pager (docs/recording-page-implementation.md §2.2/§3.6) — replaces
 * the old always-expanded list-of-cards (BentoOverviewTab) with "one thing at a time"
 * (the chosen direction, `1b`). Steps 2/3 only exist when this recording type actually produces
 * decisions/action items (same [intelligenceProfile] gate the old cards used) — never an empty
 * step pointing at content that was never asked for.
 */
@Composable
fun OverviewStepper(
    summary: String?,
    openQuestionCount: Int,
    decisions: List<Decision>,
    actionItems: List<ActionItem>,
    onToggleActionItem: (ActionItem) -> Unit,
    onAddTask: () -> Unit,
    speakers: List<Speaker>,
    segments: List<TranscriptSegment>,
    showDecisionsStep: Boolean,
    showTasksStep: Boolean,
    onPlayFrom: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = remember(showDecisionsStep, showTasksStep) {
        buildList {
            add(StepKind.WHAT_HAPPENED)
            if (showDecisionsStep) add(StepKind.DECISIONS)
            if (showTasksStep) add(StepKind.TASKS)
            add(StepKind.WHO_TALKED)
        }
    }
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp)) {
                Text(
                    text = "${page + 1} OF ${steps.size}",
                    style = RecordingPageType.stepCounter,
                    modifier = Modifier.padding(top = 34.dp)
                )
                Spacer(modifier = Modifier.height(26.dp))
                when (steps[page]) {
                    StepKind.WHAT_HAPPENED -> WhatHappenedStep(summary, openQuestionCount)
                    StepKind.DECISIONS -> DecisionsStep(decisions, segments, speakers, onPlayFrom)
                    StepKind.TASKS -> TasksStep(actionItems, onToggleActionItem, onAddTask)
                    StepKind.WHO_TALKED -> WhoTalkedStep(speakers, segments)
                }
            }
        }

        StepperFooter(
            stepCount = steps.size,
            currentStep = pagerState.currentPage,
            onPrev = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
            onNext = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(steps.size - 1)) } },
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 22.dp)
        )
    }
}

private fun spellCount(n: Int): String = when (n) {
    1 -> "One"; 2 -> "Two"; 3 -> "Three"; 4 -> "Four"; 5 -> "Five"
    6 -> "Six"; 7 -> "Seven"; 8 -> "Eight"; 9 -> "Nine"
    else -> n.toString()
}

@Composable
private fun WhatHappenedStep(summary: String?, openQuestionCount: Int) {
    Text(text = "What happened", style = RecordingPageType.stepHeading)
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = summary?.takeIf { it.isNotBlank() } ?: "No summary was generated for this recording.",
        style = RecordingPageType.stepBody
    )
    if (openQuestionCount > 0) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "${spellCount(openQuestionCount)} open question${if (openQuestionCount == 1) "" else "s"} " +
                "${if (openQuestionCount == 1) "remains" else "remain"}, left unresolved in the recording.",
            fontSize = 15.sp,
            lineHeight = 26.sp,
            color = InkMuted
        )
    }
}

/** Resolves a decision's timestamp/speaker/quote from the real segment its
 * [Decision.sourceSegmentIds] points at — Decision itself carries no timestamp of its own. */
private data class DecisionEvidence(val startMs: Long?, val speakerName: String?, val speakerId: String?, val quote: String?)

private fun Decision.evidence(segments: List<TranscriptSegment>): DecisionEvidence {
    val seg = sourceSegmentIds.firstNotNullOfOrNull { id -> segments.find { it.id == id } }
    return DecisionEvidence(seg?.startMs, seg?.speakerName, seg?.speakerId, seg?.text)
}

@Composable
private fun DecisionsStep(decisions: List<Decision>, segments: List<TranscriptSegment>, speakers: List<Speaker>, onPlayFrom: (Long) -> Unit) {
    Text(text = "${spellCount(decisions.size)} decision${if (decisions.size == 1) "" else "s"}", style = RecordingPageType.stepHeading)
    Spacer(modifier = Modifier.height(30.dp))
    if (decisions.isEmpty()) {
        Text(text = "Nothing was decided in this recording. That's a normal result for notes and open-ended conversations.", style = RecordingPageType.stepBody)
        return
    }
    var openIndex by remember { mutableIntStateOf(-1) }
    Column {
        decisions.forEachIndexed { index, decision ->
            val evidence = remember(decision) { decision.evidence(segments) }
            val isOpen = openIndex == index
            TimelineSpineRow(isLast = index == decisions.lastIndex) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openIndex = if (isOpen) -1 else index }
                        .padding(bottom = 26.dp)
                ) {
                    if (evidence.startMs != null) {
                        Text(text = Formatters.formatDurationHms(evidence.startMs), style = RecordingPageType.monoTimestamp)
                    }
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(text = decision.text, style = RecordingPageType.decisionLine)
                    if (isOpen) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(modifier = Modifier.padding(start = 15.dp)) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(LineSoft))
                                    Spacer(modifier = Modifier.width(13.dp))
                                    Column {
                                        if (evidence.quote != null) {
                                            Text(text = "\"${evidence.quote}\"", fontSize = 14.5.sp, lineHeight = 23.sp, color = InkMuted)
                                            Spacer(modifier = Modifier.height(9.dp))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (evidence.speakerName != null) {
                                                val speakerIndex = speakers.indexOfFirst { it.id == evidence.speakerId }.coerceAtLeast(0)
                                                val speakerColor = speakerColorFor(speakers.getOrNull(speakerIndex)?.colorHex, speakerIndex)
                                                SpeakerAvatar(initial = evidence.speakerName, color = speakerColor, size = 20.dp)
                                                Text(text = evidence.speakerName, fontSize = 12.sp, color = InkMuted)
                                            }
                                            if (evidence.startMs != null) {
                                                Text(
                                                    text = "Play from ${Formatters.formatDurationHms(evidence.startMs)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                    color = Ink,
                                                    modifier = Modifier.clickable { onPlayFrom(evidence.startMs) }
                                                )
                                            }
                                        }
                                    }
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
private fun TasksStep(actionItems: List<ActionItem>, onToggle: (ActionItem) -> Unit, onAddTask: () -> Unit) {
    Text(text = "${spellCount(actionItems.size)} thing${if (actionItems.size == 1) "" else "s"} to do", style = RecordingPageType.stepHeading)
    Spacer(modifier = Modifier.height(26.dp))
    if (actionItems.isEmpty()) {
        Text(text = "No tasks came out of this recording. Not every conversation has one — add your own below.", style = RecordingPageType.stepBody)
        Spacer(modifier = Modifier.height(20.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        actionItems.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .then(if (item.isCompleted) Modifier.background(Ink) else Modifier)
                        .border(1.5.dp, if (item.isCompleted) Ink else InkFaint, CircleShape)
                        .clickable { onToggle(item) }
                )
                Column {
                    Text(
                        text = item.task,
                        fontSize = 16.5.sp,
                        lineHeight = 25.sp,
                        color = if (item.isCompleted) InkMuted else Ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(item.assigneeName, item.deadline).joinToString(" · ").ifBlank { "Unassigned" },
                        fontSize = 12.5.sp,
                        color = InkMuted
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "+ Add a task",
        fontSize = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        color = Ink,
        modifier = Modifier.clickable(onClick = onAddTask)
    )
}

@Composable
private fun WhoTalkedStep(speakers: List<Speaker>, segments: List<TranscriptSegment>) {
    Text(text = "Who talked", style = RecordingPageType.stepHeading)
    Spacer(modifier = Modifier.height(28.dp))
    val totalMs = segments.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) }.coerceAtLeast(1)
    val bySpeaker = segments.groupBy { it.speakerId }
        .mapValues { (_, segs) -> segs.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) } }
        .entries
        .sortedByDescending { it.value }
    if (bySpeaker.isEmpty()) {
        Text(text = "No speakers were identified in this recording.", style = RecordingPageType.stepBody)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        bySpeaker.forEachIndexed { index, entry ->
            val speakerId = entry.key
            val ms = entry.value
            val speaker = speakers.find { it.id == speakerId }
            val color = speakerColorFor(speaker?.colorHex, index)
            val share = ms.toFloat() / totalMs.toFloat()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpeakerAvatar(initial = speaker?.customName ?: "?", color = color, size = 34.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = speaker?.customName ?: "Unknown speaker", fontSize = 16.sp, color = Ink)
                    Spacer(modifier = Modifier.height(7.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(SurfaceTrack, RoundedCornerShape(3.dp))) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(share.coerceIn(0.02f, 1f))
                                .height(5.dp)
                                .background(color, RoundedCornerShape(3.dp))
                        )
                    }
                }
                Text(text = Formatters.formatDurationHms(ms), style = RecordingPageType.monoTimestamp)
            }
        }
    }
}
