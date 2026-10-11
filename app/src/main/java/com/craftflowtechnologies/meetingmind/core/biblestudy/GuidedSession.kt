package com.craftflowtechnologies.meetingmind.core.biblestudy

import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference

/** Which kind of guided session this is. Stored on the journal entry as `sessionKind`. */
enum class SessionKind(val key: String, val label: String) {
    /** Today's reading: "pick it for me" shape. */
    TODAY("today", "Today's reading"),
    /** A passage picked for how the person feels: same shape. */
    NEED("need", "How I feel"),
    /** A reading-plan day: same shape. */
    PLAN("plan", "Reading plan"),
    /** Walk me through it, as Scripture, Observation, Application, Prayer. */
    SOAP("soap", "SOAP"),
    /** Walk me through it, one verse at a time. */
    VERSE_BY_VERSE("verses", "Verse by verse");

    /** Today, a need and a plan day share one shape: why, read, questions, prayer, live it. */
    val isPickedForYou: Boolean get() = this == TODAY || this == NEED || this == PLAN

    companion object {
        fun byKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** What one step of a session is for. */
enum class StepType(val label: String) {
    WHY("Why it matters"),
    READ("Read or listen"),
    OBSERVE("Observe"),
    APPLY("Apply"),
    PRAYER("Pray"),
    LIVE("Live it today"),
    SCRIPTURE("Scripture"),
    VERSE("Verse"),
    CLOSE("Done")
}

/**
 * One step. [answerable] steps take free text, always optional. [title] is the heading shown on the
 * step and, once answered, the heading in the journal entry.
 */
data class SessionStep(
    val id: String,
    val type: StepType,
    val title: String,
    val answerable: Boolean,
    /** For [StepType.VERSE]: the verse this step is about. */
    val verse: Int? = null,
    /** Which of several questions of the same type this is (0-based). */
    val ordinal: Int = 0
)

/** Builds the steps of a session from its kind. Pure and data-driven, so it is easy to test. */
object SessionPlan {

    /**
     * The steps for [kind]. [verses] are the verse numbers of the passage, used by verse by verse; when
     * empty, a verse-by-verse session has a single step for the whole passage.
     */
    fun steps(kind: SessionKind, verses: List<Int> = emptyList()): List<SessionStep> = when (kind) {
        SessionKind.TODAY, SessionKind.NEED, SessionKind.PLAN -> listOf(
            SessionStep("why", StepType.WHY, "Why it matters", answerable = false),
            SessionStep("read", StepType.READ, "Read or listen", answerable = false),
            SessionStep("q1", StepType.OBSERVE, "What stands out", answerable = true, ordinal = 0),
            SessionStep("q2", StepType.OBSERVE, "A question to sit with", answerable = true, ordinal = 1),
            SessionStep("q3", StepType.APPLY, "What it asks of you", answerable = true, ordinal = 2),
            SessionStep("prayer", StepType.PRAYER, "Prayer", answerable = true),
            SessionStep("live", StepType.LIVE, "Live it today", answerable = true),
            SessionStep("close", StepType.CLOSE, "Done", answerable = false)
        )
        SessionKind.SOAP -> listOf(
            SessionStep("scripture", StepType.SCRIPTURE, "Scripture", answerable = false),
            SessionStep("observation", StepType.OBSERVE, "Observation", answerable = true),
            SessionStep("application", StepType.APPLY, "Application", answerable = true),
            SessionStep("prayer", StepType.PRAYER, "Prayer", answerable = true),
            SessionStep("close", StepType.CLOSE, "Done", answerable = false)
        )
        SessionKind.VERSE_BY_VERSE -> {
            val each = verses.ifEmpty { listOf(0) }.map { v ->
                if (v == 0) SessionStep("verse-all", StepType.VERSE, "The passage", answerable = true)
                else SessionStep("verse-$v", StepType.VERSE, "Verse $v", answerable = true, verse = v)
            }
            each + SessionStep("close", StepType.CLOSE, "Done", answerable = false)
        }
    }
}

/**
 * Where a session is and what has been written. A session that has never been written in
 * ([touched] false) leaves nothing behind: nothing here, and, in the journal, no note.
 */
data class SessionState(
    val kind: SessionKind,
    val steps: List<SessionStep>,
    val index: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    /** The question shown on each step (a curated or suggested one), so the journal can say what was asked. */
    val prompts: Map<String, String> = emptyMap()
) {
    val current: SessionStep get() = steps[index.coerceIn(0, steps.lastIndex)]
    val isFirst: Boolean get() = index <= 0
    val isLast: Boolean get() = index >= steps.lastIndex
    val progress: Float get() = if (steps.size <= 1) 1f else index.toFloat() / steps.lastIndex

    /** The person has written something. */
    val touched: Boolean get() = answers.values.any { it.isNotBlank() }

    fun next() = if (isLast) this else copy(index = index + 1)
    fun back() = if (isFirst) this else copy(index = index - 1)
    fun goTo(i: Int) = copy(index = i.coerceIn(0, steps.lastIndex))

    fun answer(stepId: String, text: String) = copy(answers = if (text.isEmpty()) answers - stepId else answers + (stepId to text))

    fun withPrompt(stepId: String, question: String) = copy(prompts = prompts + (stepId to question))

    /** The written steps, in order, as (heading, text) pairs for the journal. The heading is the question that was shown. */
    fun written(): List<Written> =
        steps.filter { it.answerable }.mapNotNull { s ->
            answers[s.id]?.takeIf { it.isNotBlank() }?.let { Written(s.id, prompts[s.id] ?: s.title, it.trim()) }
        }
}

/** One written answer: which step, the question it answers, and the text. */
data class Written(val stepId: String, val heading: String, val text: String)

/**
 * Everything a session needs to start, as passed between screens. [ref] is the passage as written
 * ("John 3:16–18"); [need] and [planDay] are set for their kinds.
 */
data class SessionRequest(
    val kind: SessionKind,
    val passage: ScriptureReference,
    val need: Need? = null,
    val planId: String? = null,
    val planDay: Int? = null
)
