package com.craftflowtechnologies.meetingmind.feature.learn.ui.ask

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.craftflowtechnologies.meetingmind.ai.learning.AnswerKind
import com.craftflowtechnologies.meetingmind.ai.learning.AskPassage
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAnswer
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAskScope
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAskTurn
import com.craftflowtechnologies.meetingmind.feature.learning.AskScopeKind
import com.craftflowtechnologies.meetingmind.feature.learning.AskTurn
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel

/*
 * Maps each Ask owner's data to the one [AskSheet]. The sheet knows nothing about either view model.
 */

/** What the lesson's Ask sheet shows: the conversation, the scope, starter questions and where a report points. */
data class LessonAskUi(
    val turns: List<LessonAskTurn>,
    val scope: LessonAskScope,
    /** The lecture's course. "This course" is offered only when there is one. */
    val courseName: String?,
    val suggestions: List<String>,
    val noteId: String,
    val meetingId: String?
)

/** Three starter questions from the names of ideas. Empty names give the generic one. */
fun askSuggestions(conceptNames: List<String>): List<String> {
    val names = conceptNames.filter { it.isNotBlank() }.take(3)
    val out = mutableListOf<String>()
    names.firstOrNull()?.let { out += "What is $it?" }
    if (names.size >= 2) out += "How is ${names[0]} different from ${names[1]}?"
    names.getOrNull(2)?.let { out += "Why does $it matter?" }
    if (out.isEmpty()) out += "What were the main points?"
    return out
}

/** The lesson's Ask: a moment plays in the lecture here; the sheet's scope maps onto the lesson's two scopes. */
fun LessonAskUi.toSheetUi(): AskSheetUi = AskSheetUi(
    title = "Ask about this lecture",
    turns = turns.map { t ->
        val answer = t.answer
        val general = t.general
        AskSheetTurn(
            id = t.id,
            question = t.question,
            scopeSentence = if (t.scope == LessonAskScope.COURSE) AskSheetScope.COURSE.sentence else AskSheetScope.LECTURE.sentence,
            pending = t.pending,
            answer = answer?.text,
            notInSource = answer?.kind == AnswerKind.NOT_IN_SOURCE,
            canQuiz = answer?.kind == AnswerKind.FROM_SOURCE,
            unclear = answer?.unclearSource == true,
            citations = answer?.citations { p -> p.evidence.startMs?.let { AskCiteTarget.Play(it) } }.orEmpty(),
            generalPending = t.generalPending,
            general = general?.text,
            error = t.error,
            reportText = aiWritten(answer, general),
            reportNoteId = noteId,
            reportMeetingId = meetingId
        )
    },
    scope = if (scope == LessonAskScope.COURSE) AskSheetScope.COURSE else AskSheetScope.LECTURE,
    scopes = listOfNotNull(
        AskSheetScope.LECTURE,
        AskSheetScope.COURSE.takeIf { !courseName.isNullOrBlank() }
    ),
    suggestions = suggestions
)

/**
 * The Learning home's Ask ("Ask or find anything"), and a session's Ask when [sessionId] is given. A citation opens its
 * lesson; "Quiz me on this" starts a practice round for the answer and calls [onStartPractice].
 */
@Composable
fun HomeAskSheet(
    learn: LearnViewModel,
    sessionId: String?,
    hasCourse: Boolean,
    concepts: List<LearningConcept>,
    onDismiss: () -> Unit,
    onStartPractice: () -> Unit,
    onOpenLesson: (noteId: String) -> Unit
) {
    val turns by learn.askTurns.collectAsState()
    val scope by learn.askScope.collectAsState()
    val scopes = listOfNotNull(
        AskSheetScope.LECTURE.takeIf { sessionId != null },
        AskSheetScope.COURSE.takeIf { sessionId != null && hasCourse },
        AskSheetScope.ALL
    )
    val ui = AskSheetUi(
        title = "Ask about your learning",
        turns = turns.map { it.toSheetTurn() },
        scope = scope.toSheet(),
        scopes = scopes,
        suggestions = askSuggestions(concepts.filter { !it.isDismissed }.map { it.name })
    )
    AskSheet(
        ui = ui,
        onQuestion = { learn.ask(it) },
        onScope = { learn.setAskScope(it.toVm()) },
        onGeneral = { learn.askGenerally(it) },
        onQuiz = { id ->
            learn.askTurns.value.firstOrNull { it.id == id }?.let { turn ->
                learn.quizFromAnswer(turn) { onDismiss(); onStartPractice() }
            }
        },
        onCite = { target ->
            if (target is AskCiteTarget.OpenLesson) {
                onDismiss()
                onOpenLesson(target.noteId)
            }
        },
        onDismiss = onDismiss
    )
}

private fun AskTurn.toSheetTurn(): AskSheetTurn {
    val a = answer
    val source = a?.cited?.firstOrNull()?.evidence ?: a?.closest?.evidence
    return AskSheetTurn(
        id = id,
        question = question,
        scopeSentence = scopeLabel,
        pending = pending,
        answer = a?.text,
        notInSource = a?.kind == AnswerKind.NOT_IN_SOURCE,
        canQuiz = a?.kind == AnswerKind.FROM_SOURCE,
        unclear = a?.unclearSource == true,
        citations = a?.citations { p -> p.evidence.noteId?.let { AskCiteTarget.OpenLesson(it) } }.orEmpty(),
        generalPending = generalPending,
        general = general?.text,
        error = error,
        reportText = aiWritten(a, general),
        reportNoteId = source?.noteId,
        reportMeetingId = source?.meetingId
    )
}

private fun AskScopeKind.toSheet(): AskSheetScope = when (this) {
    AskScopeKind.SESSION -> AskSheetScope.LECTURE
    AskScopeKind.COURSE -> AskSheetScope.COURSE
    AskScopeKind.ALL -> AskSheetScope.ALL
}

private fun AskSheetScope.toVm(): AskScopeKind = when (this) {
    AskSheetScope.LECTURE -> AskScopeKind.SESSION
    AskSheetScope.COURSE -> AskScopeKind.COURSE
    AskSheetScope.ALL -> AskScopeKind.ALL
}

/** The sources an answer shows: the cited passages, or the closest one when the lecture does not say. */
private fun LearningAnswer.citations(target: (AskPassage) -> AskCiteTarget?): List<AskCitation> =
    (if (kind == AnswerKind.NOT_IN_SOURCE) listOfNotNull(closest) else cited).map { p ->
        AskCitation(
            number = p.number,
            sourceTitle = p.sourceTitle,
            timeLabel = p.evidence.startMs?.let { formatLectureTime(it) },
            quote = p.evidence.quote.ifBlank { p.text }.take(220),
            target = target(p)
        )
    }

/** The AI-written words a learner may report. A quoted (offline) answer is the lecture itself, so it is not reportable. */
private fun aiWritten(answer: LearningAnswer?, general: LearningAnswer?): String? =
    listOfNotNull(answer?.takeIf { !it.extractive }?.text, general?.text).joinToString("\n\n").ifBlank { null }
