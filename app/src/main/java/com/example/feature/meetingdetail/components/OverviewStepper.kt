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
import com.example.core.model.Question
import com.example.core.model.Speaker
import com.example.core.model.Topic
import com.example.core.model.TranscriptSegment
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.LineSoft
import com.example.ui.theme.SurfaceTrack
import kotlinx.coroutines.launch

/**
 * One step of [OverviewStepper]'s pager, also the type [OverviewStepper] can be asked to jump
 * straight to (Phase 15 §7) — public so a caller like the "AI tools" sheet's
 * FIND_DECISIONS/FIND_ACTION_ITEMS/FIND_QUESTIONS/IDENTIFY_TOPICS/EXTRACT_KEY_POINTS entries can
 * navigate here instead of just saying "already shown on Overview" without actually taking the
 * user there. [EXTRACT_KEY_POINTS] and [IDENTIFY_TOPICS] both target [TOPICS] —
 * [com.example.core.model.Topic] is the one "key points/concepts/topics" list
 * [com.example.core.model.IntelligenceProfile.topicsLabel] already documents as always-present,
 * just under a type-varying label.
 */
enum class OverviewStepTarget { WHAT_HAPPENED, DECISIONS, TASKS, QUESTIONS, TOPICS, WHO_TALKED }

/** Pure step-list construction, pulled out of the composable so it's directly unit-testable
 * (see [OverviewStepperStepsTest]) without a Compose test harness. [WHAT_HAPPENED]/[TOPICS]/
 * [WHO_TALKED] are always present; [DECISIONS]/[TASKS]/[QUESTIONS] only exist when this
 * recording type actually produces them — never an empty step pointing at content that was
 * never asked for. */
internal fun buildOverviewSteps(showDecisionsStep: Boolean, showTasksStep: Boolean, showQuestionsStep: Boolean): List<OverviewStepTarget> =
    buildList {
        add(OverviewStepTarget.WHAT_HAPPENED)
        if (showDecisionsStep) add(OverviewStepTarget.DECISIONS)
        if (showTasksStep) add(OverviewStepTarget.TASKS)
        if (showQuestionsStep) add(OverviewStepTarget.QUESTIONS)
        add(OverviewStepTarget.TOPICS)
        add(OverviewStepTarget.WHO_TALKED)
    }

/**
 * The Overview tab's step pager (docs/recording-page-implementation.md §2.2/§3.6) — replaces
 * the old always-expanded list-of-cards (BentoOverviewTab) with "one thing at a time"
 * (the chosen direction, `1b`). Decisions/Tasks/Questions only exist when this recording type
 * actually produces them (same [intelligenceProfile] gate the old cards used) — never an empty
 * step pointing at content that was never asked for. Topics is always present, per
 * [com.example.core.model.IntelligenceProfile.topicsLabel]'s own doc.
 */
@Composable
fun OverviewStepper(
    summary: String?,
    openQuestionCount: Int,
    decisions: List<Decision>,
    actionItems: List<ActionItem>,
    questions: List<Question>,
    topics: List<Topic>,
    topicsLabel: String,
    onToggleActionItem: (ActionItem) -> Unit,
    onAddTask: () -> Unit,
    speakers: List<Speaker>,
    segments: List<TranscriptSegment>,
    showDecisionsStep: Boolean,
    showTasksStep: Boolean,
    showQuestionsStep: Boolean,
    onPlayFrom: (Long) -> Unit,
    /** Set (non-null) to animate straight to that step once, e.g. from the AI tools sheet's
     * "already available" entries (Phase 15 §7) — consumed once via [LaunchedEffect] so it
     * doesn't fight the user's own subsequent swipes. */
    jumpTo: OverviewStepTarget? = null,
    modifier: Modifier = Modifier
) {
    val steps = remember(showDecisionsStep, showTasksStep, showQuestionsStep) {
        buildOverviewSteps(showDecisionsStep, showTasksStep, showQuestionsStep)
    }
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(jumpTo, steps) {
        val target = jumpTo ?: return@LaunchedEffect
        val index = steps.indexOf(target)
        if (index >= 0) pagerState.animateScrollToPage(index)
    }

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
                    OverviewStepTarget.WHAT_HAPPENED -> WhatHappenedStep(summary, openQuestionCount)
                    OverviewStepTarget.DECISIONS -> DecisionsStep(decisions, segments, speakers, onPlayFrom)
                    OverviewStepTarget.TASKS -> TasksStep(actionItems, onToggleActionItem, onAddTask)
                    OverviewStepTarget.QUESTIONS -> QuestionsStep(questions, segments, onPlayFrom)
                    OverviewStepTarget.TOPICS -> TopicsStep(topics, topicsLabel)
                    OverviewStepTarget.WHO_TALKED -> WhoTalkedStep(speakers, segments)
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

/** Backs [com.example.core.model.TranscriptAiToolType.FIND_QUESTIONS] (Phase 15 §7) — the data
 * was already extracted and persisted during normal processing; this is its first place to
 * actually show up, mirroring [DecisionsStep]'s evidence-with-play-from pattern. */
@Composable
private fun QuestionsStep(questions: List<Question>, segments: List<TranscriptSegment>, onPlayFrom: (Long) -> Unit) {
    Text(text = "Questions", style = RecordingPageType.stepHeading)
    Spacer(modifier = Modifier.height(26.dp))
    if (questions.isEmpty()) {
        Text(text = "No questions came up in this recording.", style = RecordingPageType.stepBody)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        questions.forEach { question ->
            val evidenceMs = question.sourceSegmentIds.firstNotNullOfOrNull { id -> segments.find { it.id == id }?.startMs }
            Column {
                Text(text = question.text, style = RecordingPageType.decisionLine)
                Spacer(modifier = Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (question.resolved) "Answered" else "Open",
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = if (question.resolved) InkMuted else Ink
                    )
                    if (evidenceMs != null) {
                        Text(
                            text = "Play from ${Formatters.formatDurationHms(evidenceMs)}",
                            fontSize = 12.sp,
                            color = InkMuted,
                            modifier = Modifier.clickable { onPlayFrom(evidenceMs) }
                        )
                    }
                }
                if (question.resolved && question.answer != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = question.answer, fontSize = 13.5.sp, lineHeight = 20.sp, color = InkMuted)
                }
            }
        }
    }
}

/** Backs both [com.example.core.model.TranscriptAiToolType.EXTRACT_KEY_POINTS] and
 * [com.example.core.model.TranscriptAiToolType.IDENTIFY_TOPICS] (Phase 15 §7) — both point at the
 * same underlying [Topic] list, just under a type-varying [topicsLabel] (see
 * [com.example.core.model.IntelligenceProfile.topicsLabel]'s own doc). */
@Composable
private fun TopicsStep(topics: List<Topic>, topicsLabel: String) {
    Text(text = topicsLabel, style = RecordingPageType.stepHeading)
    Spacer(modifier = Modifier.height(26.dp))
    if (topics.isEmpty()) {
        Text(text = "Nothing distinct enough to call out separately came up in this recording.", style = RecordingPageType.stepBody)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        topics.sortedByDescending { it.relevance }.forEach { topic ->
            Text(text = topic.name, fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
        }
    }
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
