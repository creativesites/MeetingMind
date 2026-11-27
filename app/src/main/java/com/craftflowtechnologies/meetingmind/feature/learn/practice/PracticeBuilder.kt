package com.craftflowtechnologies.meetingmind.feature.learn.practice

import com.craftflowtechnologies.meetingmind.core.learning.DueCard
import com.craftflowtechnologies.meetingmind.feature.learning.space.ClozeBlank
import com.craftflowtechnologies.meetingmind.feature.learn.GapMarker
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeExplain
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeFillGap
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeMatch
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeOrder
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeShortAnswer
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeTrueFalse
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeWhichIdea
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeWorked
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeFormat
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticePrompt
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeVariant
import com.craftflowtechnologies.meetingmind.feature.learning.space.pickDistractors
import kotlin.random.Random

/**
 * Turns one planned Learning variant into the Study item the screens draw. Pure, so the mapping is unit-tested.
 *
 * [siblingNames] are other concept names in the same scope; they give fill-the-gap its word bank.
 * Kit explanations ([StudyKit.optionWhys], [StudyKit.rightWhys]) belong to the activity's own options,
 * so they are used only when the variant asks the activity's own options in their own order.
 */
internal object PracticeBuilder {

    fun toItem(variant: PracticeVariant, card: DueCard, kit: StudyKit?, siblingNames: List<String>): PracticeItem {
        val a = card.activity
        val start = a.evidence.firstOrNull()?.startMs
        val end = a.evidence.firstOrNull()?.endMs
        val id = a.id
        val concept = a.conceptId
        val label = card.sourceLabel
        val meeting = card.meetingId
        val prompt = promptText(variant)
        val native = variant.options == a.options
        return when (variant.format) {
            PracticeFormat.RECALL -> PracticeItem.Recall(
                activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                meetingId = meeting, prompt = prompt, answer = variant.answer.ifBlank { a.expectedAnswer }
            )
            PracticeFormat.CHOICE, PracticeFormat.SCENARIO -> {
                val correct = variant.correctIndex ?: 0
                val options = variant.options
                val answer = options.getOrElse(correct) { variant.answer }
                if (variant.prompt == PracticePrompt.WHICH_CONCEPT) {
                    PracticeWhichIdea(
                        activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                        meetingId = meeting, prompt = prompt, passage = variant.body, options = options,
                        correctIndex = correct,
                        rightWhy = "$answer is the idea this passage is about.",
                        wrongWhys = wrongWhys(options, correct, answer, kit.takeIf { native }?.optionWhys?.filter { it.activityId == id }?.associate { it.optionIndex to it.why })
                    )
                } else {
                    val kitRight = kit?.rightWhys?.get(id)?.takeIf { native && it.isNotBlank() }
                    PracticeItem.Choice(
                        activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                        meetingId = meeting, prompt = prompt, options = options, correctIndex = correct,
                        rightWhy = kitRight ?: rightWhy(variant, answer),
                        wrongWhys = wrongWhys(options, correct, answer, kit.takeIf { native }?.optionWhys?.filter { it.activityId == id }?.associate { it.optionIndex to it.why })
                    )
                }
            }
            PracticeFormat.TRUE_FALSE -> {
                val isTrue = variant.statementIsTrue ?: true
                PracticeTrueFalse(
                    activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                    meetingId = meeting, prompt = "True or false?", subject = variant.subject,
                    statement = variant.body, correctIndex = if (isTrue) 0 else 1,
                    correction = variant.correction.ifBlank { variant.answer },
                    rightWhy = if (isTrue) "That's right. ${variant.correction}"
                    else "Yes. ${variant.subject} means: ${variant.correction}",
                    wrongWhy = if (isTrue) "It's true. ${variant.correction}"
                    else "Not quite. ${variant.subject} means: ${variant.correction}"
                )
            }
            PracticeFormat.CLOZE -> {
                val answer = variant.answer
                val distractors = pickDistractors(answer, siblingNames, 3)
                PracticeFillGap(
                    activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                    meetingId = meeting, prompt = prompt,
                    sentence = variant.body.replace(ClozeBlank, GapMarker),
                    answer = answer,
                    words = (listOf(answer) + distractors).shuffled(Random(id.hashCode().toLong())),
                    fullLine = variant.correction.ifBlank { answer }
                )
            }
            PracticeFormat.MATCH -> PracticeMatch(
                activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                meetingId = meeting, prompt = "Match each term to its meaning.",
                terms = variant.pairs.map { it.term },
                meanings = variant.pairs.map { it.meaning },
                meaningOrder = variant.displayOrder.ifEmpty { variant.pairs.indices.toList() }
            )
            PracticeFormat.ORDER -> PracticeOrder(
                activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                meetingId = meeting, prompt = prompt, steps = variant.steps,
                startOrder = variant.displayOrder.ifEmpty { variant.steps.indices.toList() }
            )
            PracticeFormat.WORKED -> PracticeWorked(
                activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                meetingId = meeting, prompt = prompt, steps = variant.steps, answer = variant.answer
            )
            PracticeFormat.SHORT -> PracticeShortAnswer(
                activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                meetingId = meeting, prompt = prompt, modelAnswer = variant.answer,
                keyPoints = variant.keyPoints
            )
            PracticeFormat.EXPLAIN -> PracticeExplain(
                activityId = id, conceptId = concept, sourceLabel = label, sourceStartMs = start, sourceEndMs = end,
                meetingId = meeting, prompt = prompt, subject = variant.subject.ifBlank { a.prompt },
                modelAnswer = variant.answer, keyPoints = variant.keyPoints
            )
        }
    }

    /** The stem the learner reads, from the variant's prompt kind. */
    fun promptText(v: PracticeVariant): String = when (v.prompt) {
        PracticePrompt.AS_WRITTEN -> v.body.ifBlank { v.subject }
        PracticePrompt.WHICH_DESCRIBES -> "Which best describes ${v.subject}?"
        PracticePrompt.WHICH_TERM -> "Which term matches this meaning?\n\n${v.body}"
        PracticePrompt.TRUE_OR_FALSE -> "True or false?"
        PracticePrompt.FILL_GAP_SOURCE -> "Fill the gap. From the lecture:"
        PracticePrompt.FILL_GAP_DEFINITION -> "Fill the gap in this definition of ${v.subject}."
        PracticePrompt.WHICH_CONCEPT -> "Which idea is this passage about?"
        PracticePrompt.PUT_IN_ORDER -> v.body.ifBlank { "Put these steps in order." }
        PracticePrompt.MATCH_TERMS -> "Match each term to its meaning."
        PracticePrompt.EXPLAIN_OWN_WORDS -> listOf(
            "Explain ${v.subject} in your own words.",
            "In your own words, what is ${v.subject}?",
            "Say what ${v.subject} means, as if to a friend."
        )[v.stemIndex.mod(3)]
        PracticePrompt.WORK_IT_OUT -> v.body.ifBlank { "Work it out, one step at a time." }
    }

    private fun rightWhy(v: PracticeVariant, answer: String): String =
        if (v.subject.isNotBlank() && v.conceptDerived) "${v.subject}: $answer" else "The answer is: $answer."

    /** Every wrong option gets a reason: the kit's when it has one, otherwise the right answer. */
    private fun wrongWhys(options: List<String>, correct: Int, answer: String, fromKit: Map<Int, String>?): Map<Int, String> =
        options.indices.filter { it != correct }.associateWith { i ->
            fromKit?.get(i)?.takeIf { it.isNotBlank() } ?: "That's a different idea. The right one is: $answer"
        }
}
