package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeExplain
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeShortAnswer
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeWorked
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * Worked example: the problem, then the solution one step at a time. "Next step" reveals the next
 * line; when every step is shown, the learner rates how it went.
 */
@Composable
internal fun WorkedItem(
    item: PracticeWorked,
    index: Int,
    feedback: AnswerFeedback?,
    revealed: Boolean,
    recallHints: Map<RecallButton, String>,
    onReveal: () -> Unit,
    onRate: (RecallButton) -> Unit,
    onContinue: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    var shown by remember(index) { mutableStateOf(0) }
    val pose = rememberChoicePose(index, feedback?.correct)
    ItemFrame(
        item = item,
        sourceLabel = item.sourceLabel,
        sourceStartMs = item.sourceStartMs,
        pose = pose,
        feedback = feedback,
        onPlaySource = onPlaySource,
        onContinue = onContinue,
        bottom = {
            if (!revealed) {
                if (shown < item.steps.size) {
                    InkButton(if (shown == 0) "Show the first step" else "Next step", onClick = { shown++ })
                } else {
                    InkButton("Done, rate it", onReveal)
                }
            } else {
                RatingRow(recallHints, onRate)
            }
        }
    ) {
        Text(
            "Work it out, one step at a time.",
            style = MM.type.secondary,
            color = c.inkSecondary,
            modifier = Modifier.padding(top = MM.space.l)
        )
        Text(
            item.prompt,
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier.padding(top = MM.space.s).semantics { heading() }
        )
        Column(
            Modifier.fillMaxWidth().padding(top = MM.space.xl),
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            val visible = if (revealed) item.steps.size else shown
            item.steps.take(visible).forEachIndexed { i, step ->
                InsetPanel(Modifier.fillMaxWidth().testTag("worked_step_$i")) {
                    Text("Step ${i + 1}", style = MM.type.caption, color = c.accent)
                    Text(step, style = MM.type.body, color = c.ink)
                }
            }
            if (revealed && item.answer.isNotBlank()) {
                InsetPanel(Modifier.fillMaxWidth()) {
                    Text("Answer", style = MM.type.caption, color = c.accent)
                    Text(item.answer, style = MM.type.bodyStrong, color = c.ink)
                }
            }
        }
    }
}

/**
 * Short answer: type it, then compare with the model answer and the key points, and rate it. The
 * model answer is shown after the typed answer; nothing is graded by a machine.
 */
@Composable
internal fun ShortAnswerItem(
    item: PracticeShortAnswer,
    index: Int,
    feedback: AnswerFeedback?,
    revealed: Boolean,
    recallHints: Map<RecallButton, String>,
    onAnswer: (PracticeAnswer) -> Unit,
    onRate: (RecallButton) -> Unit,
    onContinue: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    var text by remember(index) { mutableStateOf("") }
    val pose = rememberChoicePose(index, feedback?.correct)
    ItemFrame(
        item = item,
        sourceLabel = item.sourceLabel,
        sourceStartMs = item.sourceStartMs,
        pose = pose,
        feedback = feedback,
        onPlaySource = onPlaySource,
        onContinue = onContinue,
        bottom = {
            if (!revealed) {
                InkButton("Compare with the model answer", onClick = { onAnswer(PracticeAnswer.Typed(text)) })
            } else {
                RatingRow(recallHints, onRate)
            }
        }
    ) {
        Text(
            item.prompt,
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier.padding(top = MM.space.l).semantics { heading() }
        )
        OutlinedTextField(
            value = text,
            onValueChange = { if (!revealed) text = it },
            readOnly = revealed,
            label = { Text("Your answer") },
            modifier = Modifier.fillMaxWidth().padding(top = MM.space.xl).testTag("practice_short_answer"),
            minLines = 3
        )
        if (revealed) {
            ModelAnswer(item.modelAnswer, item.keyPoints)
        }
    }
}

/**
 * Explain it in your own words: think it through, reveal the model answer and key points, then rate.
 */
@Composable
internal fun ExplainItem(
    item: PracticeExplain,
    index: Int,
    feedback: AnswerFeedback?,
    revealed: Boolean,
    recallHints: Map<RecallButton, String>,
    onReveal: () -> Unit,
    onRate: (RecallButton) -> Unit,
    onContinue: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    val pose = rememberChoicePose(index, feedback?.correct)
    ItemFrame(
        item = item,
        sourceLabel = item.sourceLabel,
        sourceStartMs = item.sourceStartMs,
        pose = pose,
        feedback = feedback,
        onPlaySource = onPlaySource,
        onContinue = onContinue,
        bottom = {
            if (!revealed) {
                InkButton("Show the model answer", onReveal)
            } else {
                RatingRow(recallHints, onRate)
            }
        }
    ) {
        Text(
            item.prompt,
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier.padding(top = MM.space.l).semantics { heading() }
        )
        Text(
            "Say it in your own words first. Then check.",
            style = MM.type.secondary,
            color = c.inkSecondary,
            modifier = Modifier.padding(top = MM.space.m)
        )
        if (revealed) {
            ModelAnswer(item.modelAnswer, item.keyPoints)
        }
    }
}

/** The model answer and the points a good answer covers, shown after the learner has answered or thought it through. */
@Composable
private fun ModelAnswer(modelAnswer: String, keyPoints: List<String>) {
    val c = MM.colors
    Column(
        Modifier.fillMaxWidth().padding(top = MM.space.xl),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        InsetPanel(Modifier.fillMaxWidth()) {
            Text("Model answer", style = MM.type.caption, color = c.accent)
            Text(modelAnswer, style = MM.type.body, color = c.ink)
        }
        if (keyPoints.isNotEmpty()) {
            InsetPanel(Modifier.fillMaxWidth()) {
                Text("A good answer covers", style = MM.type.caption, color = c.accent)
                keyPoints.forEach { point ->
                    Text("•  $point", style = MM.type.body, color = c.ink)
                }
            }
        }
    }
}
