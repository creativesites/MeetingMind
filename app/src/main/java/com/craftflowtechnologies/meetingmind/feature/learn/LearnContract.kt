package com.craftflowtechnologies.meetingmind.feature.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.craftflowtechnologies.meetingmind.core.learning.LearningAiStatus
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent

/*
 * The Study (Learning) contract: the shapes shared by the data layer (view models, generator,
 * repository) and the screens. Design: docs/mvp/STUDY_AUDIT.md and the approved canvas
 * "MeetingMind Study redesign".
 *
 * Rules every Study file follows:
 * - Screens are stateless: they take one of the *State classes below plus callbacks. View models
 *   produce the states. Previews and tests build states by hand.
 * - Every AI claim can open its source: quotes carry startMs/endMs into the lecture recording.
 * - Plain words on screen. Never show enum names, "stale", "interval", or a mastery percent.
 * - Correct and wrong are shown by an icon and a word as well as colour. Zuri is never sad.
 */

// ---------------------------------------------------------------------------------------------
// Lesson content, generated once per lecture and stored as JSON (StudyKitStore).
// ---------------------------------------------------------------------------------------------

/** Why a moment counts as "stressed". [label] is the exact on-screen text. */
enum class StressReason(val label: String) {
    REPEATED("Said it twice"),
    NAMED_EXAM("Named the exam"),
    SLOWED_DOWN("Slowed right down"),
    CALLED_IMPORTANT("Called it important")
}

/** A moment the lecturer stressed. [quote] is the lecturer's words (or a short description). */
data class StressedMoment(
    val startMs: Long,
    val endMs: Long,
    val quote: String,
    val reason: StressReason,
    val conceptId: String? = null
)

/** One folded recall question shown after a lesson section. */
data class QuickCheck(val prompt: String, val answer: String, val conceptId: String? = null)

/** A section of the formatted lecture note. [terms] are words in [body] that open a concept sheet. */
data class LessonSection(
    val index: Int,
    val title: String,
    val body: String,
    val terms: List<LessonTerm> = emptyList(),
    val quickCheck: QuickCheck? = null,
    val startMs: Long? = null
)

/** A tappable term inside a section body: the exact text to underline and the concept it opens. */
data class LessonTerm(val text: String, val conceptId: String)

data class TimeRange(val startMs: Long, val endMs: Long)

/** The explanation shown when a learner picks [optionIndex] of a multiple-choice activity. */
data class OptionWhy(val activityId: String, val optionIndex: Int, val why: String)

/** Everything the Lesson page needs beyond the Learning tables. */
data class StudyKit(
    val sessionId: String,
    /** About three sentences, readable in 30 seconds. */
    val summary: String,
    val stressed: List<StressedMoment>,
    val sections: List<LessonSection>,
    /** Parts of the recording that were hard to hear. Questions skip them. */
    val hardToHear: List<TimeRange> = emptyList(),
    val optionWhys: List<OptionWhy> = emptyList(),
    /** The reason the right answer is right, per multiple-choice activity id. */
    val rightWhys: Map<String, String> = emptyMap(),
    val modelId: String,
    val createdAt: Long
)

// ---------------------------------------------------------------------------------------------
// Concepts and the sky
// ---------------------------------------------------------------------------------------------

/** A concept's earned state. [label] is the on-screen word. */
enum class StarState(val label: String) {
    SHINING("Solid"),
    GROWING("Getting there"),
    NEW("New"),
    WORTH_A_LOOK("Worth a look")
}

data class ConceptCard(
    val id: String,
    val sessionId: String,
    val name: String,
    /** Optional long name, e.g. "Michaelis constant" for "Km". */
    val subtitle: String? = null,
    val definition: String,
    val lecturerQuote: String? = null,
    val quoteStartMs: Long? = null,
    val quoteEndMs: Long? = null,
    val star: StarState,
    val courseName: String? = null
)

/** A star at ([x], [y]) in 0..1 of the sky's box. Positions are stable per concept id. */
data class SkyStar(val conceptId: String, val label: String, val star: StarState, val x: Float, val y: Float)

data class SkyLink(val fromId: String, val toId: String)

data class SkyState(
    val stars: List<SkyStar>,
    val links: List<SkyLink>,
    val totalIdeas: Int
) {
    fun count(state: StarState): Int = stars.count { it.star == state }

    companion object {
        val Empty = SkyState(emptyList(), emptyList(), 0)
    }
}

// ---------------------------------------------------------------------------------------------
// Study home
// ---------------------------------------------------------------------------------------------

data class RoundSummary(val dueCount: Int, val minutes: Int, val sourceTitles: List<String>)

data class ContinueLesson(
    val sessionId: String,
    val title: String,
    val courseName: String?,
    /** e.g. "2 · Inhibition"; null when not started. */
    val stoppedAt: String?,
    /** 0..1, how far through the lecture the learner has read or listened. */
    val progress: Float,
    val waveform: List<Float> = emptyList()
)

data class CourseTile(
    val name: String,
    /** Short code such as "BIO 201", when the course name starts with one. */
    val code: String?,
    /** One plain line: "2 worth a look", "All caught up", "3 lessons". */
    val status: String,
    val needsAttention: Boolean
)

data class StudyHomeState(
    val loading: Boolean = false,
    /** Null when nothing is due: the home shows "All caught up" and "Practise anyway". */
    val round: RoundSummary? = null,
    val sky: SkyState = SkyState.Empty,
    val continueLesson: ContinueLesson? = null,
    val courses: List<CourseTile> = emptyList(),
    /** Seven entries, Monday first: whether the learner studied that day. */
    val studiedDays: List<Boolean> = List(7) { false },
    /** 0..6, Monday = 0. */
    val todayIndex: Int = 0,
    /** Zuri's one line, e.g. "8 cards are due. A quick round?" or "All caught up. Nice work." */
    val companionLine: String = "",
    /** False on first use: the home shows how to record or import a lecture. */
    val hasAnyLesson: Boolean = false,
    val recentLessons: List<ContinueLesson> = emptyList()
)

// ---------------------------------------------------------------------------------------------
// Lesson page
// ---------------------------------------------------------------------------------------------

sealed interface LessonKitStatus {
    data object Ready : LessonKitStatus
    /** [step] is a plain caption, e.g. "Finding what your lecturer stressed". */
    data class Generating(val step: String) : LessonKitStatus
    /** No AI available (offline and no local model, or not set up). */
    data class NeedsAi(val message: String) : LessonKitStatus
    data class Failed(val message: String) : LessonKitStatus
    /**
     * The managed allowance is used up and no on-device model could make the lesson. The page says so calmly,
     * that the lecture's notes, practice and listening still work, and offers plans when this build can sell them.
     */
    data class AllowanceUsedUp(val resetsAtMs: Long) : LessonKitStatus
    /** The lecture has no transcript yet (still processing, or no words). */
    data object NoTranscript : LessonKitStatus
}

data class LessonState(
    val sessionId: String,
    val noteId: String,
    val meetingId: String?,
    val title: String,
    val courseName: String?,
    /** e.g. "Tue 12 Nov · 1 h 14 min · 12 ideas" (already formatted). */
    val metaLine: String,
    val durationMs: Long,
    val kit: StudyKit?,
    val status: LessonKitStatus,
    val concepts: List<ConceptCard> = emptyList(),
    /** About 60 bars, each 0..1. Empty when there is no audio. */
    val waveform: List<Float> = emptyList(),
    val playheadMs: Long = 0L,
    /** Activities ready to practise for this lesson. */
    val practiceCount: Int = 0,
    val isPlaying: Boolean = false
)

/** "Explain it another way" modes on the concept sheet. */
enum class ExplainMode(val label: String) {
    SIMPLER("Simpler"),
    ANALOGY("An analogy"),
    EXAMPLE("An example"),
    DRAW("Draw it")
}

/** A small generated diagram: boxes joined by labelled arrows, drawn by Compose. */
data class DiagramSpec(val nodes: List<String>, val edges: List<DiagramEdge>, val caption: String? = null)

data class DiagramEdge(val from: Int, val to: Int, val label: String? = null)

data class ExplainState(
    val mode: ExplainMode? = null,
    val loading: Boolean = false,
    val text: String? = null,
    val diagram: DiagramSpec? = null,
    /** Lecture timestamps the explanation came from, e.g. "14:02 and 16:40". */
    val sourceLabel: String? = null,
    val error: String? = null,
    /** Set when the managed allowance is used up: the sheet shows the calm panel with plans, not [error]. */
    val allowance: LearningAiStatus.AllowanceUsedUp? = null,
    /** True when the text was made on this phone after the cloud route ran out. Said quietly. */
    val madeOnPhone: Boolean = false
)

/** The outcome of an explanation, for analytics. */
fun ExplainState.toStatus(): LearningAiStatus = when {
    allowance != null -> allowance
    error != null -> LearningAiStatus.Failed(error, retryable = true)
    else -> LearningAiStatus.Ready
}

// ---------------------------------------------------------------------------------------------
// Listen (lecture replay with read-along)
// ---------------------------------------------------------------------------------------------

enum class ListenVoice(val label: String) { LECTURER("Lecturer"), READ_TO_ME("Read to Me") }

data class ReadAlongLine(val text: String, val startMs: Long, val endMs: Long)

data class ListenState(
    val title: String,
    val courseName: String?,
    val sectionLabel: String?,
    val lines: List<ReadAlongLine>,
    val currentLine: Int,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val speed: Float = 1f,
    val voice: ListenVoice = ListenVoice.LECTURER,
    /** False when the lecture has no audio file: only Read to Me is offered. */
    val hasLecturerAudio: Boolean = true,
    val stressedAtMs: List<Long> = emptyList(),
    val waveform: List<Float> = emptyList(),
    val stopAtSectionEnd: Boolean = false,
    val quizAfter: Boolean = false
)

// ---------------------------------------------------------------------------------------------
// Practice round
// ---------------------------------------------------------------------------------------------

sealed interface PracticeItem {
    val activityId: String
    val conceptId: String?
    /** e.g. "BIO 201 · Inhibition". */
    val sourceLabel: String
    /** Where in the lecture this came from, for "Hear your lecturer". Null when unknown. */
    val sourceStartMs: Long?
    val sourceEndMs: Long?
    val meetingId: String?

    data class Choice(
        override val activityId: String,
        override val conceptId: String?,
        override val sourceLabel: String,
        override val sourceStartMs: Long?,
        override val sourceEndMs: Long?,
        override val meetingId: String?,
        val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
        /** Shown on a right answer. */
        val rightWhy: String,
        /** Shown when the learner picks a wrong option, by option index. */
        val wrongWhys: Map<Int, String>
    ) : PracticeItem

    data class Recall(
        override val activityId: String,
        override val conceptId: String?,
        override val sourceLabel: String,
        override val sourceStartMs: Long?,
        override val sourceEndMs: Long?,
        override val meetingId: String?,
        val prompt: String,
        val answer: String
    ) : PracticeItem
}

/** What happened on the current item. */
data class AnswerFeedback(
    /** The option picked, for Choice items. Null for Recall. */
    val chosenIndex: Int?,
    val correct: Boolean,
    val why: String,
    /** e.g. "Next time: in 3 days" or "This one comes back in 3 questions". */
    val nextLine: String
)

/** The three plain recall buttons. Each maps to a [RecallRating]. */
enum class RecallButton(val label: String, val rating: RecallRating) {
    MISSED("Missed it", RecallRating.AGAIN),
    GOT_IT("Got it", RecallRating.GOOD),
    EASY("Easy", RecallRating.EASY)
}

data class NextStep(val sessionId: String, val conceptId: String?, val title: String, val detail: String)

data class RoundResult(
    val total: Int,
    val firstTryCorrect: Int,
    /** Concept names that became SHINING in this round. */
    val newStars: List<String>,
    val backTomorrow: Int,
    val minutes: Int,
    val daysThisWeek: Int,
    val nextStep: NextStep?,
    /** True when every due card is now cleared: Zuri celebrates instead of just being proud. */
    val allCleared: Boolean,
    val sky: SkyState = SkyState.Empty
)

data class PracticeState(
    val loading: Boolean = false,
    /** Items in the round, including ones re-queued after a miss. */
    val total: Int = 0,
    /** 0-based position of [current]. */
    val index: Int = 0,
    val current: PracticeItem? = null,
    /** For Recall: whether the answer side is showing. */
    val revealed: Boolean = false,
    /** For Recall: the "in 3 days" style line under each button. */
    val recallHints: Map<RecallButton, String> = emptyMap(),
    val feedback: AnswerFeedback? = null,
    /** Non-null when the round is over. */
    val result: RoundResult? = null,
    /** Non-null when there is nothing to practise or generation failed. */
    val emptyMessage: String? = null
)

// ---------------------------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------------------------

/** Study's own accent (Ocean), the way Faith uses Gold. Wrap every Study screen in this. */
@Composable
fun StudyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMMColors provides MMAccent.Ocean.on(LocalMMColors.current)) {
        content()
    }
}

/** "m:ss" or "h:mm:ss" for lecture timestamps. */
fun formatLectureTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
