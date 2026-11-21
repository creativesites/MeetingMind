package com.craftflowtechnologies.meetingmind.feature.learning.space

import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/**
 * Practice variety (founder feedback, 2026-11-21: "cards are the same, and they ask the same abstract
 * type of questions").
 *
 * A stored [LearningActivity] is what the spaced-repetition scheduler tracks. This file decides how
 * that activity is *asked* today: as a recall card, a choice with plausible distractors, true or
 * false, a gap to fill from the lecture's own sentence, a matching set, a scenario, steps to order,
 * a worked example, or an explanation in the student's own words.
 *
 * The rules:
 *  - The format follows mastery: recognise, then recall, then apply, then explain ([targetStage]).
 *  - The same activity is never asked the same way twice in a row ([chooseVariant]).
 *  - A set mixes sources and concepts instead of serving a long run from one lecture
 *    ([selectMixed], [interleaveBySource]).
 *  - Everything is built from what the app already extracted and stored (concepts, definitions,
 *    cited quotes and the AI-written questions in Room), so practice works the same offline.
 *
 * Attempts still go to the same activity id, so the scheduler and mastery rules are unchanged. The
 * format used is written to the attempt's feedback field ([PracticeFormat.tag]), which is how the
 * next session knows what was asked last time and which stages a concept has shown.
 *
 * Pure Kotlin, no Android: everything here is unit-tested in PracticeVarietyTest.
 */

/** The difficulty ladder. A concept climbs it as its mastery state improves. */
enum class PracticeStage { RECOGNISE, RECALL, APPLY, EXPLAIN }

/** How one item is asked. Each format sits on one rung of the ladder. */
enum class PracticeFormat(val stage: PracticeStage) {
    RECALL(PracticeStage.RECALL),
    CHOICE(PracticeStage.RECOGNISE),
    TRUE_FALSE(PracticeStage.RECOGNISE),
    MATCH(PracticeStage.RECOGNISE),
    CLOZE(PracticeStage.RECALL),
    SCENARIO(PracticeStage.APPLY),
    ORDER(PracticeStage.APPLY),
    WORKED(PracticeStage.APPLY),
    SHORT(PracticeStage.APPLY),
    EXPLAIN(PracticeStage.EXPLAIN);

    /** Written to [ActivityAttempt.feedback], so the next session can rotate away from it. */
    val tag: String get() = "$TagPrefix$name"

    companion object {
        const val TagPrefix = "format:"

        fun fromTag(feedback: String?): PracticeFormat? {
            val raw = feedback?.trim().orEmpty()
            if (!raw.startsWith(TagPrefix)) return null
            val name = raw.removePrefix(TagPrefix).substringBefore(' ').substringBefore(';').trim()
            return entries.firstOrNull { it.name == name }
        }
    }
}

/** How the stem is phrased. The screen turns this into words from strings_learning_practice.xml. */
enum class PracticePrompt {
    /** The activity's own question, as written. */
    AS_WRITTEN,
    /** "Which best describes [subject]?" Options are meanings. */
    WHICH_DESCRIBES,
    /** "Which term matches this meaning?" [PracticeVariant.body] is the meaning; options are names. */
    WHICH_TERM,
    /** "True or false?" [PracticeVariant.subject] and the statement in [PracticeVariant.body]. */
    TRUE_OR_FALSE,
    /** "Fill the gap. From the lecture:" with the source sentence in [PracticeVariant.body]. */
    FILL_GAP_SOURCE,
    /** "Fill the gap in this definition of [subject]." */
    FILL_GAP_DEFINITION,
    /** "Which concept is this passage about?" with the passage in [PracticeVariant.body]. */
    WHICH_CONCEPT,
    /** "Put the steps in order." [PracticeVariant.body] is the question, or blank. */
    PUT_IN_ORDER,
    /** "Match each term to its meaning." */
    MATCH_TERMS,
    /** "Explain [subject] in your own words", in one of three phrasings ([PracticeVariant.stemIndex]). */
    EXPLAIN_OWN_WORDS,
    /** A worked example: the problem in [PracticeVariant.body]. */
    WORK_IT_OUT
}

/** One term and its meaning in a matching set. [isPrimary] is the concept the item is about. */
data class MatchPair(val term: String, val meaning: String, val isPrimary: Boolean)

/**
 * One way of asking one activity. Only the fields its [format] uses are filled.
 *
 * - [options] and [correctIndex]: CHOICE, SCENARIO, and TRUE_FALSE (0 = True, 1 = False).
 * - [answer]: the correct option, the missing word, the recall answer or the model answer.
 * - [correction] and [correctionOwner]: TRUE_FALSE, the true statement and whose meaning was swapped in.
 * - [steps] and [displayOrder]: ORDER (shown in [displayOrder]); WORKED (revealed one by one).
 * - [pairs] and [displayOrder]: MATCH, meanings shown in [displayOrder].
 * - [keyPoints]: EXPLAIN and SHORT self-check.
 */
data class PracticeVariant(
    val activityId: String,
    val format: PracticeFormat,
    val prompt: PracticePrompt,
    val subject: String = "",
    val body: String = "",
    val answer: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int? = null,
    val statementIsTrue: Boolean? = null,
    val correction: String = "",
    val correctionOwner: String = "",
    val steps: List<String> = emptyList(),
    val displayOrder: List<Int> = emptyList(),
    val pairs: List<MatchPair> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val stemIndex: Int = 0,
    /** Built from the concept (its definition or quotes) rather than from the activity's own question. */
    val conceptDerived: Boolean = false
) {
    val stage: PracticeStage get() = format.stage
}

/** One planned item in a practice set: the scheduled activity and how it is asked today. */
data class PracticePlanItem(val activity: LearningActivity, val variant: PracticeVariant)

/** The blank shown in a fill-the-gap item. */
const val ClozeBlank = "_____"

/** A practice set is short: this many items at most (8 to 12 is the target length). */
const val PracticeSetSize = 12

/** A "Quiz me" from Ask stays shorter than a full set. */
const val QuizSetSize = 6

// ───────────────────────────── The ladder ─────────────────────────────

/**
 * The rung a concept is ready for. New concepts start with recognition; a concept that slipped
 * goes back to recall (retrieval, not re-reading); developing concepts are applied; strong ones are
 * explained. Items without a concept climb by their own run of correct answers.
 */
fun targetStage(state: LearningMasteryState?, correctStreak: Int = 0): PracticeStage = when (state) {
    LearningMasteryState.NEW -> PracticeStage.RECOGNISE
    LearningMasteryState.LEARNING -> PracticeStage.RECALL
    LearningMasteryState.NEEDS_REVIEW -> PracticeStage.RECALL
    LearningMasteryState.DEVELOPING -> PracticeStage.APPLY
    LearningMasteryState.STRONG -> PracticeStage.EXPLAIN
    null -> when {
        correctStreak <= 0 -> PracticeStage.RECOGNISE
        correctStreak == 1 -> PracticeStage.RECALL
        correctStreak == 2 -> PracticeStage.APPLY
        else -> PracticeStage.EXPLAIN
    }
}

/**
 * The stage to show as "next" on a concept's ladder: the first one not yet shown at or above the
 * rung its state calls for, otherwise the lowest one not yet shown. Null when all four are shown.
 */
fun nextStageFor(state: LearningMasteryState?, reached: Set<PracticeStage>): PracticeStage? {
    val floor = targetStage(state).ordinal
    return PracticeStage.entries.firstOrNull { it !in reached && it.ordinal >= floor }
        ?: PracticeStage.entries.firstOrNull { it !in reached }
}

/** The format an older attempt (saved before formats were tagged) was most likely asked in. */
fun inferredFormat(type: LearningActivityType): PracticeFormat = when (type) {
    LearningActivityType.RECALL -> PracticeFormat.RECALL
    LearningActivityType.MULTIPLE_CHOICE -> PracticeFormat.CHOICE
    LearningActivityType.APPLICATION -> PracticeFormat.SCENARIO
    LearningActivityType.WORKED_PROBLEM -> PracticeFormat.WORKED
    else -> PracticeFormat.SHORT
}

/** What one attempt says about the stage it showed. */
fun attemptFormat(attempt: ActivityAttempt, type: LearningActivityType?): PracticeFormat? =
    PracticeFormat.fromTag(attempt.feedback) ?: type?.let(::inferredFormat)

/**
 * The stages each concept has shown with a correct answer, from its attempt history. This is the
 * per-concept progress shown in Mastery and the practice summary.
 */
fun stagesReached(
    attempts: List<ActivityAttempt>,
    activityTypes: Map<String, LearningActivityType>
): Map<String, Set<PracticeStage>> =
    attempts.asSequence()
        .filter { it.isCorrect && it.conceptId != null }
        .mapNotNull { a -> attemptFormat(a, activityTypes[a.activityId])?.let { a.conceptId!! to it.stage } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, stages) -> stages.toSet() }

// ───────────────────────────── Mixing ─────────────────────────────

/**
 * Picks up to [max] items fairly across sources: one from each source in turn, keeping each
 * source's own priority order. A backlog in one lecture can't crowd the others out.
 */
fun <T> selectMixed(items: List<T>, max: Int, sourceOf: (T) -> String): List<T> {
    if (max <= 0 || items.isEmpty()) return emptyList()
    if (items.size <= max) return items
    val queues = LinkedHashMap<String, ArrayDeque<T>>()
    items.forEach { queues.getOrPut(sourceOf(it)) { ArrayDeque() }.addLast(it) }
    val picked = mutableListOf<T>()
    while (picked.size < max && queues.values.any { it.isNotEmpty() }) {
        for (q in queues.values) {
            if (picked.size >= max) break
            q.removeFirstOrNull()?.let(picked::add)
        }
    }
    // Keep the caller's priority order among what was picked; interleaving comes next.
    val chosen = picked.toHashSet()
    return items.filter { it in chosen }
}

/**
 * Orders a set so it doesn't run long from one source or dwell on one concept. Greedy: each step
 * takes from the source with the most items left that isn't the one just used, and within it the
 * first item on a different concept from the last item. Order within a source is kept otherwise.
 */
fun <T> interleaveBySource(items: List<T>, sourceOf: (T) -> String, conceptOf: (T) -> String?): List<T> {
    if (items.size < 3) return items
    val queues = LinkedHashMap<String, MutableList<T>>()
    items.forEach { queues.getOrPut(sourceOf(it)) { mutableListOf() }.add(it) }
    val order = queues.keys.toList()
    val out = mutableListOf<T>()
    var lastSource: String? = null
    var lastConcept: String? = null
    while (out.size < items.size) {
        val candidates = order.filter { queues.getValue(it).isNotEmpty() }
        val source = candidates
            .sortedWith(compareByDescending<String> { queues.getValue(it).size }.thenBy { order.indexOf(it) })
            .firstOrNull { it != lastSource || candidates.size == 1 }
            ?: candidates.first()
        val queue = queues.getValue(source)
        val idx = queue.indexOfFirst { lastConcept == null || conceptOf(it) == null || conceptOf(it) != lastConcept }
            .takeIf { it >= 0 } ?: 0
        val item = queue.removeAt(idx)
        out += item
        lastSource = source
        lastConcept = conceptOf(item)
    }
    return out
}

/** The length of the run of correct answers at the end of [outcomes]. */
fun trailingStreak(outcomes: List<Boolean>): Int = outcomes.takeLastWhile { it }.size

/** The longest run of correct answers anywhere in [outcomes]. */
fun longestStreak(outcomes: List<Boolean>): Int {
    var best = 0
    var run = 0
    outcomes.forEach { right -> run = if (right) run + 1 else 0; if (run > best) best = run }
    return best
}

/** Runs worth a quiet word. Anything else passes without comment: calm, not gamey. */
val MomentumMilestones = setOf(3, 5, 8, 12)

fun momentumMilestone(streak: Int): Int? = streak.takeIf { it in MomentumMilestones }

// ───────────────────────────── Building variants ─────────────────────────────

/** What a variant can be built from, besides the activity itself. */
data class VariantContext(
    val concept: LearningConcept?,
    /** Other concepts, closest first (same session before others). */
    val siblings: List<LearningConcept> = emptyList(),
    /** Answers of other activities in the same session, for plausible distractors. */
    val siblingAnswers: List<String> = emptyList(),
    val seed: Long = 0L
)

/** How the item is asked when nothing else is known: the activity's own type (the old behaviour). */
fun nativeVariant(activity: LearningActivity): PracticeVariant {
    val correct = correctOptionIndex(activity)
    return when {
        activity.type == LearningActivityType.RECALL -> PracticeVariant(
            activity.id, PracticeFormat.RECALL, PracticePrompt.AS_WRITTEN,
            body = activity.prompt, answer = activity.expectedAnswer
        )
        activity.options.isNotEmpty() && correct != null -> PracticeVariant(
            activity.id,
            if (activity.type == LearningActivityType.APPLICATION) PracticeFormat.SCENARIO else PracticeFormat.CHOICE,
            PracticePrompt.AS_WRITTEN,
            body = activity.prompt, answer = activity.options[correct],
            options = activity.options, correctIndex = correct
        )
        else -> PracticeVariant(
            activity.id, PracticeFormat.SHORT, PracticePrompt.AS_WRITTEN,
            body = activity.prompt, answer = activity.expectedAnswer, keyPoints = keyPointsOf(activity.expectedAnswer)
        )
    }
}

/** The activity's answer as text: the correct option for a choice, otherwise the stored answer. */
fun answerTextOf(activity: LearningActivity): String {
    val idx = correctOptionIndex(activity)
    return if (idx != null) activity.options[idx] else activity.expectedAnswer
}

/**
 * Every way this activity can be asked with what is known. The activity's own question is always
 * among them, so the list is never empty.
 */
fun candidateVariants(activity: LearningActivity, ctx: VariantContext): List<PracticeVariant> {
    val out = mutableListOf<PracticeVariant>()
    val native = nativeVariant(activity)
    out += native
    val concept = ctx.concept?.takeIf { usableDefinition(it.definition) && it.name.isNotBlank() }
    val answer = answerTextOf(activity).trim()
    val seed = mix(ctx.seed, activity.id)

    // Recall card from any item with a readable answer (a choice question asked without options).
    if (native.format != PracticeFormat.RECALL && answer.length >= 2 && !isBareLetter(answer) &&
        activity.type != LearningActivityType.WORKED_PROBLEM && !activity.prompt.contains("following", ignoreCase = true)
    ) {
        out += PracticeVariant(activity.id, PracticeFormat.RECALL, PracticePrompt.AS_WRITTEN, body = activity.prompt, answer = answer)
    }

    // Choice with plausible distractors, from the other answers in the session.
    if (native.format == PracticeFormat.RECALL && answer.isNotBlank()) {
        val distractors = pickDistractors(answer, ctx.siblingAnswers, 3)
        if (distractors.size >= 2) {
            out += shuffledChoice(activity.id, PracticeFormat.CHOICE, PracticePrompt.AS_WRITTEN, activity.prompt, "", answer, distractors, seed, conceptDerived = false)
        }
    }

    // Worked example and ordering, from step-shaped answers.
    val steps = extractSteps(activity.expectedAnswer)
    if (activity.type == LearningActivityType.WORKED_PROBLEM && activity.expectedAnswer.isNotBlank()) {
        val shown = steps.ifEmpty { keyPointsOf(activity.expectedAnswer) }.ifEmpty { listOf(activity.expectedAnswer.trim()) }
        out += PracticeVariant(
            activity.id, PracticeFormat.WORKED, PracticePrompt.WORK_IT_OUT,
            body = activity.prompt, answer = activity.expectedAnswer, steps = shown
        )
    }
    if (steps.size >= 3) {
        out += PracticeVariant(
            activity.id, PracticeFormat.ORDER, PracticePrompt.PUT_IN_ORDER,
            body = activity.prompt, steps = steps, displayOrder = derangement(steps.size, seed),
            answer = steps.joinToString("\n")
        )
    }

    if (concept == null) return out.distinctBy { it.format }

    val siblings = ctx.siblings.filter {
        it.id != concept.id && usableDefinition(it.definition) && it.name.isNotBlank() &&
            normalize(it.name) != normalize(concept.name)
    }
    val siblingMeanings = siblings.map { firstSentence(it.definition) }
    val meaning = firstSentence(concept.definition)

    // Name to meaning, or meaning to name: recognition with distractors from neighbouring concepts.
    val meaningDistractors = pickDistractors(meaning, siblingMeanings, 3, preferredCount = siblingMeanings.size)
    val nameDistractors = pickDistractors(concept.name, siblings.map { it.name }, 3)
    val useNameToMeaning = ((seed ushr 3) and 1L) == 0L
    // A meaning that repeats its own name would give the answer away in a choice or a match.
    val nameInMeaning = containsWord(meaning, concept.name)
    if (nameInMeaning) {
        // Recognition comes from true or false below instead.
    } else if (meaningDistractors.size >= 2 && (useNameToMeaning || nameDistractors.size < 2)) {
        out += shuffledChoice(activity.id, PracticeFormat.CHOICE, PracticePrompt.WHICH_DESCRIBES, "", concept.name, meaning, meaningDistractors, seed, conceptDerived = true)
    } else if (nameDistractors.size >= 2) {
        out += shuffledChoice(activity.id, PracticeFormat.CHOICE, PracticePrompt.WHICH_TERM, meaning, "", concept.name, nameDistractors, seed, conceptDerived = true)
    }

    // True or false, and fix the false one: a neighbour's meaning swapped in about half the time.
    val swap = siblings.firstOrNull { firstSentence(it.definition) == meaningDistractors.firstOrNull() }
    val statementTrue = swap == null || ((seed ushr 5) and 1L) == 0L
    out += PracticeVariant(
        activity.id, PracticeFormat.TRUE_FALSE, PracticePrompt.TRUE_OR_FALSE,
        subject = concept.name,
        body = if (statementTrue) meaning else firstSentence(swap!!.definition),
        answer = meaning,
        correctIndex = if (statementTrue) 0 else 1,
        statementIsTrue = statementTrue,
        correction = meaning,
        correctionOwner = if (statementTrue) "" else swap!!.name,
        conceptDerived = true
    )

    // Matching: this concept and two or three neighbours.
    val partners = siblings.distinctBy { normalize(it.name) }
        .filter { firstSentence(it.definition) != meaning }
        .take(3)
    if (partners.size >= 2 && !nameInMeaning) {
        val pairs = (listOf(MatchPair(concept.name, meaning, true)) + partners.map { MatchPair(it.name, firstSentence(it.definition), false) })
        val termOrder = permutation(pairs.size, seed)
        val shuffledPairs = termOrder.map { pairs[it] }
        out += PracticeVariant(
            activity.id, PracticeFormat.MATCH, PracticePrompt.MATCH_TERMS,
            subject = concept.name, pairs = shuffledPairs, displayOrder = derangement(pairs.size, seed xor 0x5DEECE66DL),
            answer = meaning, conceptDerived = true
        )
    }

    // Fill the gap: from the lecture's own sentence where possible, otherwise from the definition.
    val quotes = (activity.evidence + concept.evidence).map { it.quote }.filter { it.isNotBlank() }
    buildCloze(concept, quotes)?.let { cloze ->
        out += PracticeVariant(
            activity.id, PracticeFormat.CLOZE,
            if (cloze.fromSource) PracticePrompt.FILL_GAP_SOURCE else PracticePrompt.FILL_GAP_DEFINITION,
            subject = concept.name, body = cloze.text, answer = cloze.answer, correction = cloze.full,
            conceptDerived = true
        )
    }

    // Which concept is this passage about: the lecture's words, the concept's name hidden.
    if (native.format != PracticeFormat.SCENARIO && nameDistractors.size >= 2) {
        val passage = quotes.map { maskName(it.trim(), concept.name) }
            .firstOrNull { it.length in 50..360 && !containsWord(it, concept.name) }
        if (passage != null) {
            out += shuffledChoice(activity.id, PracticeFormat.SCENARIO, PracticePrompt.WHICH_CONCEPT, passage, "", concept.name, nameDistractors, seed, conceptDerived = true)
        }
    }

    // Steps of a process concept.
    if (steps.size < 3) {
        val conceptSteps = extractSteps(concept.definition)
        if (conceptSteps.size >= 3) {
            out += PracticeVariant(
                activity.id, PracticeFormat.ORDER, PracticePrompt.PUT_IN_ORDER,
                subject = concept.name, steps = conceptSteps, displayOrder = derangement(conceptSteps.size, seed),
                answer = conceptSteps.joinToString("\n"), conceptDerived = true
            )
        }
    }

    // Explain in your own words, checked against the lecture's definition.
    val model = listOfNotNull(concept.definition.trim(), concept.emphasis?.trim()?.takeIf { it.isNotBlank() })
        .joinToString(" ")
    if (model.length >= 20) {
        out += PracticeVariant(
            activity.id, PracticeFormat.EXPLAIN, PracticePrompt.EXPLAIN_OWN_WORDS,
            subject = concept.name, answer = model, keyPoints = keyPointsOf(model),
            stemIndex = Math.floorMod(seed, 3L).toInt(), conceptDerived = true
        )
    }
    return out.distinctBy { it.format }
}

/** What the session already knows when the next variant is chosen. */
data class VariantChoiceState(
    /** How this activity was asked last time, if ever. */
    val lastFormat: PracticeFormat? = null,
    /** The format of the item just before, in this set. */
    val previousFormat: PracticeFormat? = null,
    /** How often each format has been used in this set. */
    val usedInSet: Map<PracticeFormat, Int> = emptyMap(),
    /** True when this concept already had a concept-built item in this set. */
    val conceptAlreadyCovered: Boolean = false
)

/**
 * Picks the variant: nearest to the [target] stage (a step down before a step up), never the same
 * format as last time when there is any other choice, not the same as the item before, and spread
 * across formats. Ties are broken by [seed], so the choice is stable for a day and varies across days.
 */
fun chooseVariant(
    candidates: List<PracticeVariant>,
    target: PracticeStage,
    state: VariantChoiceState,
    seed: Long
): PracticeVariant {
    require(candidates.isNotEmpty()) { "No variant to choose from" }
    if (candidates.size == 1) return candidates.first()
    val n = candidates.size
    val offset = Math.floorMod(seed, n.toLong()).toInt()
    return candidates.withIndex().minBy { (i, v) ->
        val gap = v.stage.ordinal - target.ordinal
        val stageCost = when {
            gap == 0 -> 0.0
            gap == -1 -> 10.0
            gap == 1 -> 14.0
            gap == -2 -> 26.0
            gap == 2 -> 30.0
            else -> 45.0
        }
        val repeatLast = if (v.format == state.lastFormat) 100.0 else 0.0
        val repeatPrev = if (v.format == state.previousFormat) 25.0 else 0.0
        val overused = 6.0 * (state.usedInSet[v.format] ?: 0)
        val sameConcept = if (v.conceptDerived && state.conceptAlreadyCovered) 20.0 else 0.0
        val rotation = ((i + offset) % n) / (n * 10.0)
        stageCost + repeatLast + repeatPrev + overused + sameConcept + rotation
    }.value
}

/**
 * Builds a practice set: a fair, interleaved selection of [activities] (already in priority order),
 * each with the variant that fits its concept's stage and history.
 *
 * @param rampUp sort the set from recognition up to explanation (used for one-concept practice).
 */
fun buildPracticeDeck(
    activities: List<LearningActivity>,
    concepts: Map<String, LearningConcept>,
    attempts: List<ActivityAttempt>,
    maxItems: Int = PracticeSetSize,
    seed: Long = 0L,
    interleave: Boolean = true,
    rampUp: Boolean = false,
    siblingActivities: List<LearningActivity> = activities
): List<PracticePlanItem> {
    val selected = selectMixed(activities.distinctBy { it.id }, maxItems) { it.sessionId }
    val ordered = if (interleave) interleaveBySource(selected, { it.sessionId }, { it.conceptId }) else selected

    val attemptsByActivity = attempts.groupBy { it.activityId }.mapValues { (_, list) -> list.sortedBy { it.createdAt } }
    val allConcepts = concepts.values.filter { !it.isDismissed && !it.isStale }
    val answersBySession = (siblingActivities + activities).distinctBy { it.id }.groupBy { it.sessionId }

    val used = mutableMapOf<PracticeFormat, Int>()
    val coveredConcepts = mutableSetOf<String>()
    var previous: PracticeFormat? = null
    val plan = ordered.map { activity ->
        val concept = activity.conceptId?.let { concepts[it] }
        val history = attemptsByActivity[activity.id].orEmpty()
        val last = history.lastOrNull()?.let { attemptFormat(it, activity.type) }
        val streak = trailingStreak(history.map { it.isCorrect })
        val siblings = concept?.let { c ->
            val same = allConcepts.filter { it.sessionId == c.sessionId && it.id != c.id }
            val other = allConcepts.filter { it.sessionId != c.sessionId }
            same + other
        }.orEmpty()
        val siblingAnswers = answersBySession[activity.sessionId].orEmpty()
            .filter { it.id != activity.id }
            .map { answerTextOf(it).trim() }
            .filter { it.length >= 2 && !isBareLetter(it) }
        val itemSeed = mix(seed, activity.id)
        val ctx = VariantContext(concept, siblings, siblingAnswers, itemSeed)
        val candidates = runCatching { candidateVariants(activity, ctx) }.getOrElse { listOf(nativeVariant(activity)) }
        val variant = chooseVariant(
            candidates,
            targetStage(concept?.state, streak),
            VariantChoiceState(
                lastFormat = last,
                previousFormat = previous,
                usedInSet = used.toMap(),
                conceptAlreadyCovered = concept != null && concept.id in coveredConcepts
            ),
            itemSeed
        )
        previous = variant.format
        used[variant.format] = (used[variant.format] ?: 0) + 1
        if (variant.conceptDerived && concept != null) coveredConcepts += concept.id
        PracticePlanItem(activity, variant)
    }
    return if (rampUp) plan.sortedBy { it.variant.stage.ordinal } else plan
}

// ───────────────────────────── Distractors ─────────────────────────────

/**
 * Wrong options that look like they could be right: similar length and some shared vocabulary,
 * but never a near-copy of the answer or of each other. The first [preferredCount] of [pool]
 * (the same lecture) get a small edge.
 */
fun pickDistractors(correct: String, pool: List<String>, count: Int, preferredCount: Int = 0): List<String> {
    val key = normalize(correct)
    if (key.isEmpty() || count <= 0) return emptyList()
    val correctTokens = tokens(correct)
    val seen = mutableSetOf(key)
    val scored = pool.withIndex().mapNotNull { (i, raw) ->
        val text = raw.trim()
        val n = normalize(text)
        if (n.isEmpty() || n.length < 2 || isBareLetter(text)) return@mapNotNull null
        if (n == key || n.contains(key) && key.length > 3 || key.contains(n) && n.length > 3) return@mapNotNull null
        val overlap = jaccard(correctTokens, tokens(text))
        if (overlap >= 0.8) return@mapNotNull null
        val ratio = (text.length.toDouble() / correct.trim().length.coerceAtLeast(1)).coerceIn(0.05, 20.0)
        val score = 2.0 * overlap - abs(ln(ratio)) + if (i < preferredCount) 0.3 else 0.0
        Triple(text, n, score)
    }
    return scored.sortedWith(compareByDescending<Triple<String, String, Double>> { it.third }.thenBy { it.first })
        .filter { seen.add(it.second) }
        .take(count)
        .map { it.first }
}

private fun shuffledChoice(
    activityId: String,
    format: PracticeFormat,
    prompt: PracticePrompt,
    body: String,
    subject: String,
    correct: String,
    distractors: List<String>,
    seed: Long,
    conceptDerived: Boolean
): PracticeVariant {
    val all = listOf(correct) + distractors
    val order = permutation(all.size, seed)
    val options = order.map { all[it] }
    return PracticeVariant(
        activityId, format, prompt,
        subject = subject, body = body, answer = correct,
        options = options, correctIndex = order.indexOf(0),
        conceptDerived = conceptDerived
    )
}

// ───────────────────────────── Fill the gap ─────────────────────────────

/** A gap-fill: [text] has [ClozeBlank] where [answer] was; [full] is the original sentence. */
data class ClozeSpec(val text: String, val answer: String, val full: String, val fromSource: Boolean)

/**
 * A gap to fill. First choice: a sentence from the lecture that names the concept, with the name
 * blanked. Otherwise the definition, with the name or its most specific word blanked.
 */
fun buildCloze(concept: LearningConcept, quotes: List<String>): ClozeSpec? {
    val name = concept.name.trim()
    if (name.length >= 3) {
        quotes.asSequence()
            .flatMap { splitSentences(it).asSequence() }
            .firstOrNull { it.length in 30..260 && containsWord(it, name) }
            ?.let { s -> return ClozeSpec(replaceWord(s, name, ClozeBlank), name, s, fromSource = true) }
    }
    val definition = firstSentence(concept.definition)
    if (definition.length < 25 || definition.split(Regex("""\s+""")).size < 4) return null
    if (name.length >= 3 && containsWord(definition, name)) {
        return ClozeSpec(replaceWord(definition, name, ClozeBlank), name, definition, fromSource = false)
    }
    val nameWords = tokens(name)
    val keyword = Regex("""[\p{L}][\p{L}\-]*""").findAll(definition).map { it.value }
        .filter { it.length >= 6 && it.lowercase() !in StopWords && it.lowercase() !in nameWords }
        .maxByOrNull { it.length } ?: return null
    return ClozeSpec(replaceWord(definition, keyword, ClozeBlank), keyword, definition, fromSource = false)
}

/**
 * Whether a typed gap-fill counts: case, punctuation and a leading article are ignored, and a small
 * slip is forgiven (one letter in a short word, two in a long one).
 */
fun clozeMatches(expected: String, typed: String): Boolean {
    val e = stripArticle(normalize(expected))
    val t = stripArticle(normalize(typed))
    if (e.isEmpty() || t.isEmpty()) return false
    if (e == t) return true
    val allowed = when {
        e.length < 4 -> 0
        e.length < 8 -> 1
        else -> 2
    }
    return levenshtein(e, t) <= allowed
}

// ───────────────────────────── Steps ─────────────────────────────

private val StepMarker = Regex("""^\s*(?:step\s*\d+\s*[:.)\-]?|\d+\s*[.)]|[-•*])\s*""", RegexOption.IGNORE_CASE)
private val InlineNumber = Regex("""(?:^|\s)(\d)[.)]\s+""")
private val SequenceWords = setOf("first", "firstly", "then", "next", "after", "afterwards", "finally", "second", "secondly", "third", "lastly", "last")

/**
 * Steps in an answer, in order, when it reads as a process: numbered or bulleted lines, inline
 * "1) … 2) … 3) …", or sentences led by first / then / finally. 3 to 6 steps, otherwise none.
 */
fun extractSteps(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val marked = lines.filter { StepMarker.containsMatchIn(it) }
    // Numbered or bulleted lines; one unmarked line (a heading such as "Steps") is allowed and left out.
    val fromLines = if (marked.size >= 3 && marked.size >= lines.size - 1) marked.map { it.replace(StepMarker, "").trim() } else emptyList()
    val candidates = when {
        fromLines.size >= 3 -> fromLines
        else -> {
            val numbers = InlineNumber.findAll(trimmed).map { it.groupValues[1].toInt() }.toList()
            if (numbers.size >= 3 && numbers == (1..numbers.size).toList()) {
                trimmed.split(InlineNumber).map { it.trim() }.filter { it.isNotEmpty() }
                    .let { parts -> if (parts.size > numbers.size) parts.drop(parts.size - numbers.size) else parts }
            } else {
                val sentences = splitSentences(trimmed)
                val led = sentences.count { s -> (s.split(Regex("""[\s,]+""")).firstOrNull()?.lowercase() ?: "") in SequenceWords }
                if (sentences.size in 3..6 && led >= 2) sentences else emptyList()
            }
        }
    }
    val clean = candidates.map { it.trim().trimEnd(';') }.filter { it.length in 3..200 }.distinct()
    return if (clean.size in 3..6) clean else emptyList()
}

// ───────────────────────────── Summary ─────────────────────────────

enum class SessionMood { NONE, ALL_RIGHT, MOSTLY_RIGHT, MIXED, TOUGH }

/** The end-of-set summary: what went well, what to revisit, how it was practised. */
data class SessionDigest(
    val answered: Int,
    val right: Int,
    val bestRun: Int,
    val mood: SessionMood,
    /** Concepts whose latest answer in the set was right, in order of first appearance. */
    val stuck: List<String>,
    /** Concepts whose latest answer in the set was missed. */
    val revisit: List<String>,
    /** Distinct formats used, in order of first use. */
    val formats: List<PracticeFormat>
)

/** One answered item, reduced to what the digest needs. */
data class AnsweredItem(val conceptId: String?, val isCorrect: Boolean, val format: PracticeFormat?)

fun digestOf(items: List<AnsweredItem>): SessionDigest {
    val answered = items.size
    val right = items.count { it.isCorrect }
    val latest = LinkedHashMap<String, Boolean>()
    items.forEach { a -> a.conceptId?.let { latest[it] = a.isCorrect } }
    val mood = when {
        answered == 0 -> SessionMood.NONE
        right == answered -> SessionMood.ALL_RIGHT
        right * 4 >= answered * 3 -> SessionMood.MOSTLY_RIGHT
        right * 2 >= answered -> SessionMood.MIXED
        else -> SessionMood.TOUGH
    }
    return SessionDigest(
        answered = answered,
        right = right,
        bestRun = longestStreak(items.map { it.isCorrect }),
        mood = mood,
        stuck = latest.filterValues { it }.keys.toList(),
        revisit = latest.filterValues { !it }.keys.toList(),
        formats = items.mapNotNull { it.format }.distinct()
    )
}

// ───────────────────────────── Text helpers ─────────────────────────────

private val StopWords = setOf(
    "the", "and", "that", "this", "with", "from", "which", "their", "there", "these", "those", "where", "when",
    "while", "about", "because", "between", "through", "during", "before", "after", "within", "without",
    "other", "another", "should", "would", "could", "being", "having", "usually", "often", "called",
    "process", "something", "different", "important", "example", "including", "especially", "certain"
)

internal fun normalize(s: String): String =
    s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim().replace(Regex("""\s+"""), " ")

private fun stripArticle(s: String): String = s.removePrefix("the ").removePrefix("a ").removePrefix("an ").trim()

private fun tokens(s: String): Set<String> =
    normalize(s).split(' ').filter { it.length > 2 && it !in StopWords }.toSet()

private fun jaccard(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val inter = a.count { it in b }
    return inter.toDouble() / (a.size + b.size - inter)
}

private fun isBareLetter(s: String): Boolean = Regex("""^[A-Ha-h][).:]?$""").matches(s.trim())

private fun usableDefinition(d: String): Boolean = d.trim().length >= 12

internal fun splitSentences(text: String): List<String> =
    text.trim().split(Regex("""(?<=[.!?])\s+""")).map { it.trim() }.filter { it.isNotEmpty() }

/** The first sentence, or the first 200 characters on a word boundary for a long run-on. */
internal fun firstSentence(text: String): String {
    val s = splitSentences(text).firstOrNull() ?: text.trim()
    if (s.length <= 220) return s
    val cut = s.take(200)
    return cut.substring(0, cut.lastIndexOf(' ').takeIf { it > 100 } ?: cut.length).trimEnd(',', ';', ' ') + "…"
}

private fun wordRegex(word: String) =
    Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(word.trim()) + """(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

private fun containsWord(text: String, word: String): Boolean = word.isNotBlank() && wordRegex(word).containsMatchIn(text)

private fun replaceWord(text: String, word: String, with: String): String {
    val m = wordRegex(word).find(text) ?: return text
    return text.replaceRange(m.range, with)
}

private fun maskName(text: String, name: String): String =
    if (name.isBlank()) text else wordRegex(name).replace(text, ClozeBlank)

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (abs(a.length - b.length) > 3) return 4
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        prev = cur
    }
    return prev[b.length]
}

/** A stable per-item seed: the same item gets the same choice on the same day. */
internal fun mix(seed: Long, id: String): Long {
    var h = seed xor 0x2545F4914F6CDD1DL
    id.forEach { c -> h = (h xor c.code.toLong()) * 0x100000001B3L }
    return h xor (h ushr 29)
}

/** A seeded shuffle of 0 until n. */
internal fun permutation(n: Int, seed: Long): List<Int> = (0 until n).shuffled(Random(seed))

/** A seeded shuffle of 0 until n that never leaves everything in place (so order is never given away). */
internal fun derangement(n: Int, seed: Long): List<Int> {
    if (n < 2) return (0 until n).toList()
    val p = permutation(n, seed)
    return if (p == (0 until n).toList()) p.drop(1) + p.first() else p
}
