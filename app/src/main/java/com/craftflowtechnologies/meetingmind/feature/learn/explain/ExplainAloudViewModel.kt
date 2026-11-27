package com.craftflowtechnologies.meetingmind.feature.learn.explain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the "Explain it aloud" round is. The screen draws one layout per phase. */
enum class ExplainPhase {
    /** Nothing said yet. The start button is the big one. */
    READY,
    /** The learner tapped start but the microphone is not allowed yet. Asks, with a plain reason. */
    NEEDS_MIC,
    /** Listening: level bars, timer, the checklist filling in as the words come. */
    RECORDING,
    /** Typing instead, because the microphone is off or the recognizer failed. */
    TYPING,
    /** Zuri is judging the explanation. */
    THINKING,
    /** Covered, partly, not yet, with the lecture's words for each missing idea. */
    RESULT,
    /** No model could judge it: the learner rates it themselves. */
    SELF_RATE,
    /** The self-rating is saved. */
    SAVED,
    /** The concept has no lecture words or definition to judge against. */
    NO_EVIDENCE
}

/** One line of the "Key ideas, as you go" checklist. */
data class IdeaRow(
    val claim: String,
    val label: String,
    val coverage: IdeaCoverage,
    /** The lecturer's words for this idea, when the lecture has them. */
    val quote: String?,
    /** Where in the lecture those words start, for the play button. Null when unknown. */
    val startMs: Long?
)

data class ExplainUiState(
    val phase: ExplainPhase = ExplainPhase.READY,
    /** e.g. "BIO 201 · Inhibition". */
    val subjectLine: String = "",
    val conceptName: String = "",
    val prompt: String = "",
    val elapsedMs: Long = 0L,
    /** The last [LEVEL_BARS] input levels, 0..1, oldest first. */
    val levels: List<Float> = List(LEVEL_BARS) { 0f },
    val ideas: List<IdeaRow> = emptyList(),
    /** What was said or typed. Kept so a typed fallback can start from it. */
    val transcript: String = "",
    /** A plain status line, e.g. why the microphone is not on. */
    val message: String? = null,
    val hint: String? = null,
    /** The model's short judgement, shown on the result. */
    val summary: String? = null,
    /** The one next prompt on the result. */
    val nextPrompt: String? = null,
    val rated: RecallButton? = null
) {
    val allCovered: Boolean get() = ideas.isNotEmpty() && ideas.all { it.coverage == IdeaCoverage.COVERED }

    companion object {
        const val LEVEL_BARS = 28
    }
}

/** Everything the round needs about the concept, loaded by the host before the screen opens. */
data class ExplainContent(
    val sessionId: String,
    val concept: LearningConcept,
    /** e.g. "BIO 201 · Inhibition". */
    val subjectLine: String,
    /** The lecture's recording, for "play the lecturer's words". Null when there is none. */
    val meetingId: String?,
    val lectureTitle: String
)

/**
 * The "Explain it aloud" round for one concept. Speech, judging and saving are behind interfaces, so the
 * whole flow is testable without a microphone or a model.
 *
 * Rules it keeps: the audio is never kept (the recognizer's file, if any, is deleted once read); only the
 * text is saved with the attempt; no number is ever shown as a grade.
 */
class ExplainAloudViewModel(
    private val content: ExplainContent,
    private val speech: ExplainSpeech,
    private val assessor: TeachBackAssessor,
    private val store: ExplainAttemptStore,
    private val playAt: (startMs: Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = 250L
) : ViewModel() {

    private val concept = content.concept
    private val rubric: List<TeachBackCriterion> = ExplainRubric.build(concept)

    private val _state = MutableStateFlow(
        ExplainUiState(
            phase = if (rubric.isEmpty()) ExplainPhase.NO_EVIDENCE else ExplainPhase.READY,
            subjectLine = content.subjectLine,
            conceptName = concept.name,
            prompt = "Explain ${concept.name} as if to a friend. Aim for about a minute.",
            ideas = ideaRows(emptyList())
        )
    )
    val state: StateFlow<ExplainUiState> = _state.asStateFlow()

    private var phase = _state.value.phase
    private var transcript = ""
    private var message: String? = null
    private var hint: String? = null
    private var elapsedMs = 0L
    private var levels = List(ExplainUiState.LEVEL_BARS) { 0f }
    private var startedAt = 0L
    private var ticker: Job? = null
    private var awaitingFinal: CompletableDeferred<SpeechEvent.Final>? = null
    private var judged: ExplainAssessment.Judged? = null
    private var rated: RecallButton? = null

    // ----------------------------------------------------------------- what the learner does

    /** The start button. Asks for the microphone first when it is not allowed yet. */
    fun onStartTapped(micGranted: Boolean) {
        if (phase != ExplainPhase.READY) return
        if (micGranted) startRecording() else {
            phase = ExplainPhase.NEEDS_MIC
            publish()
        }
    }

    /** The answer to the microphone prompt. Refusing falls back to typing, never to a dead end. */
    fun onMicPermissionResult(granted: Boolean) {
        if (phase != ExplainPhase.NEEDS_MIC) return
        if (granted) {
            startRecording()
        } else {
            phase = ExplainPhase.TYPING
            message = "The microphone is off, so Zuri can't hear you. Type it instead."
            publish()
        }
    }

    /** "Type it instead", from the microphone prompt or while listening. */
    fun onTypeInstead() {
        if (phase != ExplainPhase.READY && phase != ExplainPhase.NEEDS_MIC && phase != ExplainPhase.RECORDING) return
        if (phase == ExplainPhase.RECORDING) speech.cancel()
        stopTicker()
        phase = ExplainPhase.TYPING
        message = null
        publish()
    }

    /** The big stop button. Waits briefly for the recognizer's last words, then judges what it has. */
    fun onStop() {
        if (phase != ExplainPhase.RECORDING) return
        stopTicker()
        phase = ExplainPhase.THINKING
        publish()
        val waiting = CompletableDeferred<SpeechEvent.Final>()
        awaitingFinal = waiting
        speech.stop()
        viewModelScope.launch {
            val final = withTimeoutOrNull(FINAL_WAIT_MS) { waiting.await() }
            awaitingFinal = null
            settle(final)
        }
    }

    /** Drops what was said and goes back to the start. Nothing is saved. */
    fun onStartOver() {
        if (phase == ExplainPhase.RECORDING) speech.cancel()
        awaitingFinal?.cancel()
        awaitingFinal = null
        resetToReady()
    }

    /** "Try again" after a result: a fresh explanation, and the last one stays saved. */
    fun onTryAgain() {
        resetToReady()
    }

    /** "Give a hint": names one idea word the explanation has not used. Never the answer. */
    fun onHint() {
        val term = rubric.firstNotNullOfOrNull { ExplainRubric.unusedTerm(it.claim, transcript) }
        hint = term?.let { "Try to mention $it." } ?: "You have touched every idea. Try an example from your course."
        publish()
    }

    /** The typed explanation, when the learner has finished typing. */
    fun onSubmitTyped(text: String) {
        if (phase != ExplainPhase.TYPING) return
        viewModelScope.launch { assess(text.trim()) }
    }

    /** A recall rating on the self-rated path. Saves the attempt once. */
    fun onRate(button: RecallButton) {
        if (phase != ExplainPhase.SELF_RATE) return
        viewModelScope.launch {
            val saved = runCatching { store.saveSelfRated(concept, rubric, transcript, button) }.isSuccess
            if (saved) {
                rated = button
                phase = ExplainPhase.SAVED
                message = null
            } else {
                message = SAVE_FAILED
            }
            publish()
        }
    }

    /** The play button beside a missing idea: the lecturer's words, from the recording. */
    fun onPlayIdea(index: Int) {
        ideaRows(judged?.statuses ?: liveStatuses()).getOrNull(index)?.startMs?.let(playAt)
    }

    override fun onCleared() {
        awaitingFinal?.cancel()
        speech.cancel()
        super.onCleared()
    }

    // ----------------------------------------------------------------- speech

    private fun startRecording() {
        transcript = ""
        message = null
        hint = null
        elapsedMs = 0L
        levels = List(ExplainUiState.LEVEL_BARS) { 0f }
        judged = null
        rated = null
        startedAt = clock()
        phase = ExplainPhase.RECORDING
        publish()
        startTicker()
        speech.start { event -> onSpeech(event) }
    }

    private fun onSpeech(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Level -> if (phase == ExplainPhase.RECORDING) {
                levels = levels.drop(1) + event.value
                publish()
            }
            is SpeechEvent.Partial -> if (phase == ExplainPhase.RECORDING) {
                transcript = event.text
                publish()
            }
            is SpeechEvent.Final -> {
                val waiting = awaitingFinal
                when {
                    waiting != null -> waiting.complete(event)
                    phase == ExplainPhase.RECORDING -> {
                        // The recognizer finished on its own (a long pause, or the end of the session).
                        stopTicker()
                        phase = ExplainPhase.THINKING
                        publish()
                        viewModelScope.launch { settle(event) }
                    }
                    else -> event.audioFile?.delete()
                }
            }
            is SpeechEvent.Failed -> if (phase == ExplainPhase.RECORDING) {
                speech.cancel()
                stopTicker()
                phase = ExplainPhase.TYPING
                message = when (event.reason) {
                    SpeechFailure.PERMISSION -> "The microphone is off, so Zuri can't hear you. Type it instead."
                    SpeechFailure.NO_RECOGNIZER -> "Speech recognition isn't on this phone. Type it instead."
                    SpeechFailure.OTHER -> "Zuri couldn't hear that. Type it instead."
                }
                publish()
            }
        }
    }

    /** Takes the final words (or the last partial ones), deletes any audio, and judges them. */
    private suspend fun settle(final: SpeechEvent.Final?) {
        final?.audioFile?.delete()
        assess((final?.text ?: transcript).trim())
    }

    // ----------------------------------------------------------------- judging

    private suspend fun assess(text: String) {
        transcript = text
        if (text.isEmpty()) {
            phase = ExplainPhase.TYPING
            message = "Zuri didn't catch anything. Try again, or type it instead."
            publish()
            return
        }
        phase = ExplainPhase.THINKING
        message = null
        publish()
        when (val outcome = assessor.assess(rubric, text, concept)) {
            is ExplainAssessment.Judged -> {
                judged = outcome
                phase = ExplainPhase.RESULT
                val saved = runCatching { store.saveJudged(concept, rubric, text, outcome.overallCovered) }.isSuccess
                message = if (saved) null else SAVE_FAILED
            }
            ExplainAssessment.AiUnavailable -> {
                phase = ExplainPhase.SELF_RATE
                message = "AI isn't available right now, so Zuri can't check this one. How did it go?"
            }
            ExplainAssessment.AllowanceUsed -> {
                phase = ExplainPhase.SELF_RATE
                message = "Your cloud AI allowance for this month is used up, so Zuri can't check this one. How did it go?"
            }
        }
        publish()
    }

    private fun resetToReady() {
        stopTicker()
        phase = ExplainPhase.READY
        transcript = ""
        message = null
        hint = null
        elapsedMs = 0L
        levels = List(ExplainUiState.LEVEL_BARS) { 0f }
        judged = null
        rated = null
        publish()
    }

    // ----------------------------------------------------------------- timer and state

    private fun startTicker() {
        stopTicker()
        ticker = viewModelScope.launch {
            while (phase == ExplainPhase.RECORDING) {
                elapsedMs = clock() - startedAt
                publish()
                delay(tickMs)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun liveStatuses(): List<IdeaCoverage> = rubric.map { ExplainRubric.coverage(it.claim, transcript) }

    private fun ideaRows(statuses: List<IdeaCoverage>): List<IdeaRow> = rubric.mapIndexed { i, c ->
        IdeaRow(
            claim = c.claim,
            label = ExplainRubric.label(c.claim),
            coverage = statuses.getOrElse(i) { IdeaCoverage.NOT_YET },
            quote = c.evidence?.quote?.takeIf { it.isNotBlank() },
            startMs = c.evidence?.startMs
        )
    }

    private fun publish() {
        val result = judged
        val statuses = result?.statuses ?: liveStatuses()
        val missing = ideaRows(statuses).firstOrNull { it.coverage != IdeaCoverage.COVERED }
        _state.value = ExplainUiState(
            phase = phase,
            subjectLine = content.subjectLine,
            conceptName = concept.name,
            prompt = "Explain ${concept.name} as if to a friend. Aim for about a minute.",
            elapsedMs = elapsedMs,
            levels = levels,
            ideas = ideaRows(statuses),
            transcript = transcript,
            message = message,
            hint = hint,
            summary = result?.summary,
            nextPrompt = if (phase == ExplainPhase.RESULT) {
                result?.nextQuestion
                    ?: missing?.let { "Next, say in one sentence how ${it.label.lowercase()} works." }
                    ?: "Nothing is missing. Try it again with an example from your course."
            } else {
                null
            },
            rated = rated
        )
    }

    companion object {
        /** How long to wait for the recognizer's last words after stop before judging what we have. */
        const val FINAL_WAIT_MS = 4_000L
        const val SAVE_FAILED = "Zuri couldn't save this attempt. Your words are still here."
    }
}
