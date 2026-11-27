package com.craftflowtechnologies.meetingmind.feature.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.learning.ExplainEngine
import com.craftflowtechnologies.meetingmind.ai.learning.GeneratedStudyContent
import com.craftflowtechnologies.meetingmind.ai.learning.StudyKitGenerator
import com.craftflowtechnologies.meetingmind.ai.learning.StudyKitResult
import com.craftflowtechnologies.meetingmind.ai.learning.StudyLectureInput
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.routing.ResolvedLanguageModel
import com.craftflowtechnologies.meetingmind.core.learning.OpenLecture
import com.craftflowtechnologies.meetingmind.core.learning.SkyLayout
import com.craftflowtechnologies.meetingmind.core.learning.StudyFormat
import com.craftflowtechnologies.meetingmind.core.learning.StudyKitStore
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One lecture's Lesson page: the summary, sections, concepts and the waveform.
 *
 * On first open a lecture with a transcript and no kit is generated. Once a kit exists, the page
 * works offline: it only reads the kit file and the Learning tables. [generate] runs again on
 * request. [explain] answers "explain it another way" for one concept, from its own evidence.
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

    /** Reads the lecture again, and starts generation when it has a transcript and no kit yet. */
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
                _explain.value = ExplainEngine(model.languageModel).explain(concept, mode, open.segments)
            } finally {
                releaseIfLocal(model)
            }
        }
    }

    fun clearExplain() {
        explaining?.cancel()
        _explain.value = ExplainState()
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
        publish(null)
        val hasWords = open.segments.any { textOf(it).isNotBlank() }
        val hasKit = services.kits.load(open.session.id) != null
        if (!hasKit && hasWords && generation?.isActive != true) {
            generate()
        } else if (!hasWords && !hasKit) {
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
            StudyKitGenerator(model.languageModel, model.modelId, model.contextLengthTokens).generate(
                StudyLectureInput(open.session.id, open.meetingId, open.segments)
            ) { step -> _state.update { it.copy(status = LessonKitStatus.Generating(step)) } }
        } finally {
            releaseIfLocal(model)
        }
        when (result) {
            is StudyKitResult.Ready -> saveContent(open, result.content)
            is StudyKitResult.NoTranscript -> publish(LessonKitStatus.NoTranscript)
            is StudyKitResult.NeedsAi -> publish(LessonKitStatus.NeedsAi(result.message))
            is StudyKitResult.Failed -> publish(LessonKitStatus.Failed(result.message))
        }
    }

    private suspend fun saveContent(open: OpenLecture, content: GeneratedStudyContent) {
        services.learning.regenerateSession(open.session.id, content.concepts, content.activities)
        services.kits.save(content.kit)
        concepts = services.learning.getConcepts(open.session.id)
        publish(LessonKitStatus.Ready)
    }

    /**
     * Rebuilds the whole page state from storage. [forced] replaces the status when given; otherwise
     * the status follows the kit, the transcript and any generation running.
     */
    private suspend fun publish(forced: LessonKitStatus?) {
        val open = lecture ?: return
        val kit = services.kits.load(open.session.id)
        val now = services.now()
        val snap = services.snapshots.session(open.session.id)
        val activities = services.learning.getActivities(open.session.id)
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
            metaLine = metaLine(open, concepts.size),
            durationMs = open.durationMs,
            kit = kit,
            status = status,
            concepts = cards,
            waveform = StudyFormat.waveform(open.segments, open.durationMs),
            practiceCount = activities.count { !it.isStale },
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
