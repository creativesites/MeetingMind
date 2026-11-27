package com.craftflowtechnologies.meetingmind.feature.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.learning.ExplainEngine
import com.craftflowtechnologies.meetingmind.ai.learning.GeneratedStudyContent
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAsk
import com.craftflowtechnologies.meetingmind.ai.learning.StudyKitGenerator
import com.craftflowtechnologies.meetingmind.ai.learning.StudyKitResult
import com.craftflowtechnologies.meetingmind.ai.learning.StudyLectureInput
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.routing.ResolvedLanguageModel
import com.craftflowtechnologies.meetingmind.core.learning.LearningAiStatus
import com.craftflowtechnologies.meetingmind.core.learning.OpenLecture
import com.craftflowtechnologies.meetingmind.core.learning.toLearningStatus
import com.craftflowtechnologies.meetingmind.core.learning.SkyLayout
import com.craftflowtechnologies.meetingmind.core.learning.StudyAiAnalytics
import com.craftflowtechnologies.meetingmind.core.learning.StudyFormat
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.ConceptFlags
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LegacyLesson
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAi
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAskScope
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAskTurn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * One lecture's Lesson page: the summary, sections, concepts, the waveform, review pause, concept edits and Ask.
 *
 * On first open a lecture with a transcript and no kit is generated, unless Learning already has concepts for it
 * (made by the old studio). Then the lesson is laid out from those ([LegacyLesson]), with no AI. Once a kit exists,
 * the page works offline. [generate] runs again on request. [explain] answers "explain it another way" for one concept.
 */
class LessonViewModel(
    application: Application,
    val noteId: String,
    private val services: StudyServices = StudyServices.forApp(application)
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        LessonState(
            sessionId = "",
            noteId = noteId,
            meetingId = null,
            title = "",
            courseName = null,
            metaLine = "",
            durationMs = 0L,
            kit = null,
            status = LessonKitStatus.Generating(OPENING)
        )
    )
    val state: StateFlow<LessonState> = _state.asStateFlow()

    private val _explain = MutableStateFlow(ExplainState())
    val explain: StateFlow<ExplainState> = _explain.asStateFlow()

    private val _reviewsPaused = MutableStateFlow(false)
    /** Whether this lecture's reviews are paused (the overflow menu's Pause or Resume reviews). */
    val reviewsPaused: StateFlow<Boolean> = _reviewsPaused.asStateFlow()

    private val _conceptFlags = MutableStateFlow<Map<String, ConceptFlags>>(emptyMap())
    /** Per concept: hidden from practice, reported, edited by the learner. Read by the concept sheet. */
    val conceptFlags: StateFlow<Map<String, ConceptFlags>> = _conceptFlags.asStateFlow()

    private val _askTurns = MutableStateFlow<List<LessonAskTurn>>(emptyList())
    val askTurns: StateFlow<List<LessonAskTurn>> = _askTurns.asStateFlow()

    private val _askScope = MutableStateFlow(LessonAskScope.THIS_LECTURE)
    val askScope: StateFlow<LessonAskScope> = _askScope.asStateFlow()

    private val ai: LearningAiEngine by lazy { LessonAi.engine(application) }

    private var lecture: OpenLecture? = null
    private var concepts: List<LearningConcept> = emptyList()
    private var generation: Job? = null
    private var explaining: Job? = null

    init {
        reload()
        // The playhead and play state follow the app's player while it holds this lecture.
        viewModelScope.launch {
            com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.state.collect { ps ->
                val mine = ps.recordingId != null && ps.recordingId == lecture?.meetingId
                _state.update {
                    if (mine) it.copy(playheadMs = ps.positionMs, isPlaying = ps.isPlaying)
                    else if (it.isPlaying) it.copy(isPlaying = false) else it
                }
            }
        }
    }

    /** Plays the lecture recording from [ms]: a stressed moment, a quote, a seek on the ribbon. */
    fun playFrom(ms: Long) {
        val open = lecture ?: return
        val file = open.meeting?.audioFilePath?.let { java.io.File(it) }?.takeIf { it.exists() } ?: return
        com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.playAt(getApplication(), open.meetingId ?: return, open.title, file, ms)
    }

    /** Play or pause the lecture from the playhead. */
    fun togglePlay() {
        val ps = com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.state.value
        if (ps.recordingId != null && ps.recordingId == lecture?.meetingId) {
            com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.togglePlayPause()
        } else {
            playFrom(_state.value.playheadMs)
        }
    }

    /** Reads the lecture again, and starts generation when it has a transcript and nothing to show yet. */
    fun reload() {
        viewModelScope.launch { load() }
    }

    /** Makes the study kit now (or again). Does nothing while one is already being made. */
    fun generate() {
        if (generation?.isActive == true) return
        generation = viewModelScope.launch { runGeneration() }
    }

    /** "Explain it another way" for one concept. The result lands in [explain]. */
    fun explain(conceptId: String, mode: ExplainMode) {
        val open = lecture ?: return
        val concept = concepts.firstOrNull { it.id == conceptId } ?: return
        explaining?.cancel()
        _explain.value = ExplainState(mode = mode, loading = true)
        explaining = viewModelScope.launch {
            val model = services.models.resolve(noteId)
            if (model == null) {
                _explain.value = ExplainState(mode = mode, error = NO_AI_MESSAGE)
                return@launch
            }
            try {
                var result = ExplainEngine(model.languageModel).explain(concept, mode, open.segments)
                // The cloud allowance ran out: try once on this phone before saying so.
                if (result.allowance != null) {
                    val local = services.models.resolveLocal(noteId)
                    if (local != null) {
                        try {
                            result = ExplainEngine(local.languageModel).explain(concept, mode, open.segments)
                            if (result.allowance == null && result.error == null) result = result.copy(madeOnPhone = true)
                        } finally {
                            releaseIfLocal(local)
                        }
                    }
                }
                StudyAiAnalytics.track(StudyAiAnalytics.FEATURE_EXPLAIN, result.toStatus(), result.madeOnPhone)
                _explain.value = result
            } finally {
                releaseIfLocal(model)
            }
        }
    }

    fun clearExplain() {
        explaining?.cancel()
        _explain.value = ExplainState()
    }

    // ---------------------------------------------------------------- review pause

    /** Pauses or resumes this lecture's reviews, as the studio's session menu did. */
    fun toggleReviewsPaused() {
        val open = lecture ?: return
        val next = !_reviewsPaused.value
        viewModelScope.launch {
            services.learning.pauseSession(open.session.id, next)
            _reviewsPaused.value = next
        }
    }

    // ---------------------------------------------------------------- concept edits (the studio's concept menu)

    /** Renames a concept. The learner's edit is kept when the lesson is made again. */
    fun renameConcept(conceptId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        editConcept(conceptId) { it.copy(name = trimmed, isUserEdited = true) }
    }

    /** Hides a concept from practice, or shows it again. The lesson still shows it. */
    fun toggleConceptHidden(conceptId: String) {
        val current = concepts.firstOrNull { it.id == conceptId } ?: return
        editConcept(conceptId) { it.copy(isDismissed = !current.isDismissed, isUserEdited = true) }
    }

    /** "Report a problem" holds questions on a concept back until it is fixed. Tapping again undoes it. */
    fun toggleConceptReported(conceptId: String) {
        val current = concepts.firstOrNull { it.id == conceptId } ?: return
        editConcept(conceptId) { it.copy(isStale = !current.isStale, isUserEdited = true) }
    }

    private fun editConcept(conceptId: String, change: (LearningConcept) -> LearningConcept) {
        val current = concepts.firstOrNull { it.id == conceptId } ?: return
        viewModelScope.launch {
            runCatching {
                services.learning.updateConcept(change(current).copy(updatedAt = services.now()))
            }
            concepts = services.learning.getConcepts(current.sessionId)
            publish(null)
        }
    }

    // ---------------------------------------------------------------- Ask about this lecture

    /** Changes where Ask looks. Only "this course" is offered when the lecture has a course. */
    fun setAskScope(scope: LessonAskScope) {
        _askScope.value = scope
    }

    /** Asks a question of this lecture (or its course), and answers with numbered citations. */
    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _askTurns.value.any { it.pending }) return
        val scope = _askScope.value
        val turn = LessonAskTurn(id = "ask_${UUID.randomUUID()}", question = q, scope = scope, pending = true)
        _askTurns.update { it + turn }
        viewModelScope.launch {
            if (lecture == null) {
                updateTurn(turn.id) { it.copy(pending = false, error = "This lesson is still opening. Ask again in a moment.") }
                return@launch
            }
            val answer = runCatching {
                val passages = passagesFor(scope)
                ai.answerFromSources(q, LearningAsk.rank(q, passages))
            }.getOrNull()
            val status = ai.lastStatus
            val madeOnPhone = ai.lastMadeLocally
            StudyAiAnalytics.track(StudyAiAnalytics.FEATURE_LESSON_ASK, status, madeOnPhone)
            updateTurn(turn.id) {
                it.copy(
                    pending = false,
                    answer = answer,
                    allowance = status as? LearningAiStatus.AllowanceUsedUp,
                    madeOnPhone = madeOnPhone,
                    error = if (answer == null) "Something went wrong while searching. Try asking again." else null
                )
            }
        }
    }

    /** General knowledge, only when the learner asks for it. Always labelled as not from the lecture. */
    fun askGenerally(turnId: String) {
        val turn = _askTurns.value.firstOrNull { it.id == turnId } ?: return
        updateTurn(turnId) { it.copy(generalPending = true) }
        viewModelScope.launch {
            val general = runCatching { ai.answerGenerally(turn.question) }.getOrNull()
            val status = ai.lastStatus
            val madeOnPhone = ai.lastMadeLocally
            StudyAiAnalytics.track(StudyAiAnalytics.FEATURE_LESSON_ASK, status, madeOnPhone)
            val allowance = status as? LearningAiStatus.AllowanceUsedUp
            updateTurn(turnId) {
                it.copy(
                    generalPending = false,
                    general = general,
                    allowance = allowance ?: it.allowance,
                    madeOnPhone = madeOnPhone,
                    error = if (general == null && allowance == null) "A general answer needs an AI model, and none is available right now." else null
                )
            }
        }
    }

    private fun updateTurn(id: String, change: (LessonAskTurn) -> LessonAskTurn) {
        _askTurns.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    /** The passages an answer may cite: this lecture's words and concepts, or the same for its course. */
    private suspend fun passagesFor(scope: LessonAskScope): List<com.craftflowtechnologies.meetingmind.ai.learning.AskPassage> {
        val open = lecture ?: return emptyList()
        val title = open.title
        if (scope == LessonAskScope.THIS_LECTURE) {
            val transcript = LearningAsk.transcriptPassages(open.segments, noteId, open.meetingId, title)
            val notes = if (open.segments.isEmpty()) {
                LearningAsk.notePassages(services.notes.getDocument(noteId)?.note?.plainText.orEmpty(), noteId, title)
            } else emptyList()
            val summary = services.kits.load(open.session.id)?.summary.orEmpty()
            val summaryPassage = summary.takeIf { it.isNotBlank() }?.let {
                listOf(com.craftflowtechnologies.meetingmind.ai.learning.AskPassage(0, it, com.craftflowtechnologies.meetingmind.core.model.LearningEvidence(noteId = noteId, quote = it.take(LearningAsk.QuoteChars)), title))
            }.orEmpty()
            return transcript + notes + LearningAsk.conceptPassages(concepts) { title } + summaryPassage
        }
        val course = open.session.courseName?.takeIf { it.isNotBlank() } ?: return LearningAsk.conceptPassages(concepts) { title }
        val sessions = services.learning.observeAllSessions().first().filter { it.courseName.equals(course, ignoreCase = true) }
        val titles = sessions.associate { it.id to it.title }
        val courseConcepts = sessions.flatMap { services.learning.getConcepts(it.id) }
        return LearningAsk.conceptPassages(courseConcepts) { titles[it] ?: "Your notes" }
    }

    // ---------------------------------------------------------------- internals

    internal suspend fun load() {
        val open = services.openLecture(noteId)
        if (open == null) {
            _state.update { it.copy(status = LessonKitStatus.Failed(NOT_FOUND_MESSAGE)) }
            return
        }
        lecture = open
        concepts = services.learning.getConcepts(open.session.id)
        _reviewsPaused.value = services.learning.getSession(open.session.id)?.isPaused == true
        publish(null)
        val hasWords = open.segments.any { textOf(it).isNotBlank() }
        // Learning's concepts for this lecture stand in for a kit: no second generation over the same lecture.
        val hasLesson = services.kits.load(open.session.id) != null || concepts.any { !it.isDismissed }
        if (!hasLesson && hasWords && generation?.isActive != true) {
            generate()
        } else if (!hasWords && !hasLesson) {
            publish(LessonKitStatus.NoTranscript)
        }
    }

    private suspend fun runGeneration() {
        val open = lecture ?: return
        if (open.segments.none { textOf(it).isNotBlank() }) {
            publish(LessonKitStatus.NoTranscript)
            return
        }
        publish(LessonKitStatus.Generating(StudyKitGenerator.CAPTION_READING))
        val model = services.models.resolve(noteId)
        if (model == null) {
            publish(LessonKitStatus.NeedsAi(NO_AI_MESSAGE))
            return
        }
        val result = try {
            generateKit(model, open)
        } finally {
            releaseIfLocal(model)
        }
        // The cloud allowance ran out before anything was made: try once on this phone, quietly.
        var final = result
        var madeOnPhone = false
        if (result is StudyKitResult.AllowanceUsedUp) {
            services.models.resolveLocal(noteId)?.let { local ->
                final = try {
                    generateKit(local, open)
                } finally {
                    releaseIfLocal(local)
                }
                madeOnPhone = final is StudyKitResult.Ready
            }
        }
        StudyAiAnalytics.track(StudyAiAnalytics.FEATURE_LESSON_KIT, final.toLearningStatus(), madeOnPhone)
        when (val outcome = final) {
            is StudyKitResult.Ready -> saveContent(open, outcome.content)
            is StudyKitResult.NoTranscript -> publish(LessonKitStatus.NoTranscript)
            is StudyKitResult.NeedsAi -> publish(LessonKitStatus.NeedsAi(outcome.message))
            is StudyKitResult.AllowanceUsedUp -> publish(LessonKitStatus.AllowanceUsedUp(outcome.resetsAtMs))
            is StudyKitResult.Failed -> publish(LessonKitStatus.Failed(outcome.message))
        }
    }

    private suspend fun generateKit(model: ResolvedLanguageModel, open: OpenLecture): StudyKitResult =
        StudyKitGenerator(model.languageModel, model.modelId, model.contextLengthTokens).generate(
            StudyLectureInput(open.session.id, open.meetingId, open.segments)
        ) { step -> _state.update { it.copy(status = LessonKitStatus.Generating(step)) } }

    private suspend fun saveContent(open: OpenLecture, content: GeneratedStudyContent) {
        services.learning.regenerateSession(open.session.id, content.concepts, content.activities)
        services.kits.save(content.kit)
        concepts = services.learning.getConcepts(open.session.id)
        publish(LessonKitStatus.Ready)
    }

    /**
     * Rebuilds the whole page state from storage. [forced] replaces the status when given; otherwise
     * the status follows the kit (stored, or laid out from Learning's concepts), the transcript and any generation running.
     */
    private suspend fun publish(forced: LessonKitStatus?) {
        val open = lecture ?: return
        val now = services.now()
        val snap = services.snapshots.session(open.session.id)
        val activities: List<LearningActivity> = services.learning.getActivities(open.session.id)
        val kit = services.kits.load(open.session.id)
            ?: LegacyLesson.kitFrom(open.session.id, concepts, activities, now)
        val cards = concepts.map { c ->
            val evidence = c.evidence.firstOrNull { it.quote.isNotBlank() }
            ConceptCard(
                id = c.id,
                sessionId = c.sessionId,
                name = c.name,
                definition = c.definition,
                lecturerQuote = evidence?.quote,
                quoteStartMs = evidence?.startMs,
                quoteEndMs = evidence?.endMs,
                star = SkyLayout.conceptStar(
                    snap?.attemptsByConcept?.get(c.id).orEmpty(),
                    snap?.scheduleByConcept?.get(c.id),
                    now
                ),
                courseName = open.session.courseName
            )
        }
        _conceptFlags.value = concepts.associate { c ->
            c.id to ConceptFlags(hidden = c.isDismissed, reported = c.isStale, userEdited = c.isUserEdited)
        }
        val status = forced ?: when {
            kit != null -> LessonKitStatus.Ready
            open.segments.none { textOf(it).isNotBlank() } -> LessonKitStatus.NoTranscript
            generation?.isActive == true -> _state.value.status
            else -> LessonKitStatus.Generating(OPENING)
        }
        _state.value = LessonState(
            sessionId = open.session.id,
            noteId = noteId,
            meetingId = open.meetingId,
            title = open.title,
            courseName = open.session.courseName,
            metaLine = metaLine(open, concepts.count { !it.isDismissed }),
            durationMs = open.durationMs,
            kit = kit,
            status = status,
            concepts = cards,
            waveform = StudyFormat.waveform(open.segments, open.durationMs),
            practiceCount = activities.count { !it.isStale && !it.isDismissed },
            isPlaying = false
        )
    }

    private fun metaLine(open: OpenLecture, ideas: Int): String {
        val date = SimpleDateFormat("EEE d MMM", Locale.getDefault())
            .format(Date(open.meeting?.createdAt ?: open.session.createdAt))
        return listOfNotNull(
            date,
            open.durationMs.takeIf { it > 0 }?.let { StudyFormat.durationLabel(it) },
            "$ideas ideas"
        ).joinToString(" · ")
    }

    private fun releaseIfLocal(model: ResolvedLanguageModel) {
        if (!model.isCloud) {
            viewModelScope.launch { LlmEngineManager.release() }
        }
    }

    private fun textOf(segment: TranscriptSegment): String = (segment.cleanedText ?: segment.text).trim()

    companion object {
        const val OPENING = "Opening your lesson"
        const val NO_AI_MESSAGE = "No AI is set up for study kits. Install an on-device model in Settings, or turn on Internet mode."
        const val NOT_FOUND_MESSAGE = "This lesson could not be found."
    }
}
