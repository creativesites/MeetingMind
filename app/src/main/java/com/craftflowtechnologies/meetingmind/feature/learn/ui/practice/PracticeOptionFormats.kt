package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeTrueFalse
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeWhichIdea
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import androidx.compose.material3.Text

/** How an option looks: open before an answer; after it, the right one, the learner's wrong pick, or dimmed. */
internal fun answerMark(i: Int, correctIndex: Int, feedback: AnswerFeedback?): AnswerMark = when {
    feedback == null -> AnswerMark.OPEN
    i == correctIndex -> AnswerMark.RIGHT
    i == feedback.chosenIndex -> AnswerMark.WRONG_PICK
    else -> AnswerMark.DIMMED
}

/** The word under an option after answering. Text, so colour is never the only signal. */
internal fun answerTag(mark: AnswerMark): String? = when (mark) {
    AnswerMark.RIGHT -> "Correct answer"
    AnswerMark.WRONG_PICK -> "Your answer"
    else -> null
}

/**
 * True or false, with "fix it". Tapping True or False answers at once: there is no Check. When the
 * statement is false, the answer shows the corrected version under the statement.
 */
@Composable
internal fun TrueFalseItem(
    item: PracticeTrueFalse,
    index: Int,
    feedback: AnswerFeedback?,
    onAnswer: (PracticeAnswer) -> Unit,
    onNotSure: () -> Unit,
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
        bottom = { AnswerHintRow("Tap True or False. No submit button.", onNotSure) }
    ) {
        Text(
            item.prompt,
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier.padding(top = MM.space.l).semantics { heading() }
        )
        Text(item.subject, style = MM.type.caption, color = c.accent, modifier = Modifier.padding(top = MM.space.m))
        InsetPanel(Modifier.fillMaxWidth().padding(top = MM.space.s)) {
            Text(item.statement, style = MM.type.heading, color = c.ink)
        }
        if (feedback != null && item.correctIndex == 1) {
            InsetPanel(Modifier.fillMaxWidth().padding(top = MM.space.s), ) {
                Text("The fix", style = MM.type.caption, color = c.accent)
                Text(item.correction, style = MM.type.body, color = c.ink)
            }
        }
        Column(Modifier.padding(top = MM.space.xl), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            listOf("True" to 0, "False" to 1).forEach { (label, i) ->
                val mark = answerMark(i, item.correctIndex, feedback)
                PracticeOption(
                    label = label,
                    mark = mark,
                    tag = answerTag(mark),
                    testTag = "practice_tf_$i",
                    onClick = { onAnswer(PracticeAnswer.Pick(i)) }
                )
            }
        }
    }
}

/** Which idea is this passage about: the passage in a panel, then options. Tapping an option answers. */
@Composable
internal fun WhichIdeaItem(
    item: PracticeWhichIdea,
    index: Int,
    feedback: AnswerFeedback?,
    onAnswer: (PracticeAnswer) -> Unit,
    onNotSure: () -> Unit,
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
        bottom = { AnswerHintRow("Tap an idea. No submit button.", onNotSure) }
    ) {
        Text(
            item.prompt,
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier.padding(top = MM.space.l).semantics { heading() }
        )
        InsetPanel(Modifier.fillMaxWidth().padding(top = MM.space.l)) {
            Text(item.passage, style = MM.type.body, color = c.ink)
        }
        Column(Modifier.padding(top = MM.space.xl), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            item.options.forEachIndexed { i, label ->
                val mark = answerMark(i, item.correctIndex, feedback)
                PracticeOption(
                    label = label,
                    mark = mark,
                    tag = answerTag(mark),
                    testTag = "practice_option_$i",
                    letter = ('A' + i).toString(),
                    onClick = { onAnswer(PracticeAnswer.Pick(i)) }
                )
            }
        }
    }
}
