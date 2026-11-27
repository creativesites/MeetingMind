package com.craftflowtechnologies.meetingmind.feature.learn

/*
 * The practice formats beyond Choice and Recall (LearnContract.kt holds those two). Each is a
 * subtype of the sealed [PracticeItem], declared here because the sealed hierarchy allows subtypes
 * in the same package and module. Every format is built from Learning's PracticeVariety variants
 * (see feature/learn/practice/PracticeBuilder.kt) and drawn by feature/learn/ui/practice.
 *
 * Every item carries the same source fields as PracticeItem: the source label, the lecture time
 * for "Hear your lecturer", and the meeting id. Attempts are saved to the same activity id, with
 * the format written to the attempt's feedback (PracticeFormat.tag in Learning).
 */

/** Ten formats in all: Recall and Choice in LearnContract.kt, then the eight below. */

/**
 * True or false, with "fix it" when the statement is false. Tapping an answer is the answer: no Check.
 * [correctIndex] is 0 for true and 1 for false, as in Learning.
 */
data class PracticeTrueFalse(
    override val activityId: String,
    override val conceptId: String?,
    override val sourceLabel: String,
    override val sourceStartMs: Long?,
    override val sourceEndMs: Long?,
    override val meetingId: String?,
    val prompt: String,
    /** The concept the statement is about, e.g. "Vmax". */
    val subject: String,
    val statement: String,
    val correctIndex: Int,
    /** The right meaning of [subject]. Shown when the statement is false. */
    val correction: String,
    val rightWhy: String,
    val wrongWhy: String
) : PracticeItem

/** Fill the gap: tap a word from the lecture's own words, then Check. [sentence] holds [ClozeBlank]-style gap text. */
data class PracticeFillGap(
    override val activityId: String,
    override val conceptId: String?,
    override val sourceLabel: String,
    override val sourceStartMs: Long?,
    override val sourceEndMs: Long?,
    override val meetingId: String?,
    val prompt: String,
    /** The sentence with the gap marked by [GapMarker]. */
    val sentence: String,
    val answer: String,
    /** The word bank: the answer and a few neighbours, in display order. */
    val words: List<String>,
    /** The full line, shown after answering. */
    val fullLine: String
) : PracticeItem

/**
 * Match terms: [terms] and [meanings] are index-matched (term i belongs with meaning i). The meanings
 * are shown in [meaningOrder], so the learner sees them shuffled. Check when every term is matched.
 */
data class PracticeMatch(
    override val activityId: String,
    override val conceptId: String?,
    override val sourceLabel: String,
    override val sourceStartMs: Long?,
    override val sourceEndMs: Long?,
    override val meetingId: String?,
    val prompt: String,
    val terms: List<String>,
    val meanings: List<String>,
    val meaningOrder: List<Int>
) : PracticeItem

/**
 * Which idea is this passage about: a passage and options. Tapping an option answers at once.
 * [wrongWhys] explains each wrong option by its index.
 */
data class PracticeWhichIdea(
    override val activityId: String,
    override val conceptId: String?,
    override val sourceLabel: String,
    override val sourceStartMs: Long?,
    override val sourceEndMs: Long?,
    override val meetingId: String?,
    val prompt: String,
    val passage: String,
    val options: List<String>,
    val correctIndex: Int,
    val rightWhy: String,
    val wrongWhys: Map<Int, String>
) : PracticeItem

/**
 * Put steps in order. [steps] are in the lecture's order; the learner starts from [startOrder] (a
 * permutation of step indices) and moves steps with the arrows. Correct when the order is 0..n-1. Check to answer.
 */
data class PracticeOrder(
    override val activityId: String,
    override val conceptId: String?,
    override val sourceLabel: String,
    override val sourceStartMs: Long?,
    override val sourceEndMs: Long?,
    override val meetingId: String?,
    val prompt: String,
    val steps: List<String>,
    val startOrder: List<Int>
) : PracticeItem

/** Worked example: the [problem], then [steps] revealed one at a time. The learner rates it afterwards. */
data class PracticeWorked(
    override val activityId: String,
    override val conceptId: String?,
    override val sourceLabel: String,
    override val sourceStartMs: Long?,
    override val sourceEndMs: Long?,
    override val meetingId: String?,
    val prompt: String,
    val steps: List<String>,
    /** The final answer, shown with the steps. */
    val answer: String
) : PracticeItem

/** Short answer: the learner types, then compares with [modelAnswer] and [keyPoints] and rates it. */
data class PracticeShortAnswer(
    override val activityId: String,
    override val conceptId: String?,
    override val sourceLabel: String,
    override val sourceStartMs: Long?,
    override val sourceEndMs: Long?,
    override val meetingId: String?,
    val prompt: String,
    val modelAnswer: String,
    val keyPoints: List<String>
) : PracticeItem

/** Explain in your own words: think, reveal [modelAnswer] and [keyPoints], then rate it. */
data class PracticeExplain(
    override val activityId: String,
    override val conceptId: String?,
    override val sourceLabel: String,
    override val sourceStartMs: Long?,
    override val sourceEndMs: Long?,
    override val meetingId: String?,
    val prompt: String,
    /** The concept's name, for the prompt, e.g. "Vmax". */
    val subject: String,
    val modelAnswer: String,
    val keyPoints: List<String>
) : PracticeItem

/** The marker that stands for the gap in [PracticeFillGap.sentence]. */
const val GapMarker = "_____"

/**
 * What the learner did on the current item. The view model checks it against the item and grades it.
 * Free-text items send [Typed] first, then a rating through [PracticeViewModel.rate].
 */
sealed interface PracticeAnswer {
    /** Options and true or false: the index picked (0 is true for true or false). */
    data class Pick(val index: Int) : PracticeAnswer

    /** Fill the gap: the word the learner put in. */
    data class Fill(val word: String) : PracticeAnswer

    /** Match: term index to meaning index, for every term matched. */
    data class Match(val termToMeaning: Map<Int, Int>) : PracticeAnswer

    /** Order: the step indices in the order the learner left them. */
    data class Order(val order: List<Int>) : PracticeAnswer

    /** Short answer: what the learner typed. Shows the model answer; the learner then rates. */
    data class Typed(val text: String) : PracticeAnswer
}
