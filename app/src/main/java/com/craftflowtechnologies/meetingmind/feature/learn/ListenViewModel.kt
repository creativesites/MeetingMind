package com.craftflowtechnologies.meetingmind.feature.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackController
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackState
import com.craftflowtechnologies.meetingmind.core.learning.StudyFormat
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lecture replay with read-along, on the app's one shared player ([PlaybackController]).
 *
 * Voices: only [ListenVoice.LECTURER] is offered ([voices]). Read to Me is not wired to lectures
 * yet, so it is not offered rather than faked. Without an audio file the page still shows the
 * read-along lines, with playback controls disabled ([ListenState.hasLecturerAudio] false).
 */
class ListenViewModel(
    application: Application,
    val noteId: String,
    private val services: StudyServices = StudyServices.forApp(application)
) : AndroidViewModel(application) {

    /** The voices this page offers. Read to Me is left out until it can read a lecture. */
    val voices: List<ListenVoice> = listOf(ListenVoice.LECTURER)

    private val _state = MutableStateFlow(
        ListenState(
            title = "",
            courseName = null,
            sectionLabel = null,
            lines = emptyList(),
            currentLine = 0,
            positionMs = 0L,
            durationMs = 0L,
            isPlaying = false
        )
    )
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var meetingId: String? = null
    private var recordingTitle: String = ""
    private var audio: File? = null
    private var sectionStarts: List<Pair<Long, String>> = emptyList()
    private var stressed: List<Long> = emptyList()
    private var lastPositionMs = 0L

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            PlaybackController.state.collect { onPlayback(it) }
        }
    }

    internal suspend fun load() {
        val open = services.openLecture(noteId) ?: return
        meetingId = open.meetingId
        recordingTitle = open.title
        audio = open.meeting?.audioFilePath?.let { File(it) }?.takeIf { it.exists() }
        val kit = services.kits.load(open.session.id)
        sectionStarts = kit?.sections.orEmpty()
            .mapNotNull { s -> s.startMs?.let { it to s.title } }
            .sortedBy { it.first }
        stressed = kit?.stressed.orEmpty().map { it.startMs }.distinct().sorted()
        val lines = open.segments.sortedBy { it.startMs }.map { s ->
            ReadAlongLine(text = (s.cleanedText ?: s.text).trim(), startMs = s.startMs, endMs = s.endMs)
        }.filter { it.text.isNotEmpty() }
        _state.update {
            it.copy(
                title = open.title,
                courseName = open.session.courseName,
                lines = lines,
                durationMs = open.durationMs,
                hasLecturerAudio = audio != null,
                stressedAtMs = stressed,
                waveform = StudyFormat.waveform(open.segments, open.durationMs),
                currentLine = currentLineFor(lines, it.positionMs),
                sectionLabel = sectionFor(it.positionMs)
            )
        }
    }

    fun togglePlayPause() {
        val file = audio ?: return
        val id = meetingId ?: return
        if (isMine(PlaybackController.state.value)) {
            PlaybackController.togglePlayPause()
        } else {
            PlaybackController.playAt(getApplication(), id, recordingTitle, file, _state.value.positionMs)
        }
    }

    /** Jump to [ms] in the lecture. Playback position only moves when this lecture is loaded. */
    fun seekTo(ms: Long) {
        val clamped = ms.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        if (isMine(PlaybackController.state.value)) {
            PlaybackController.seekTo(clamped)
        } else {
            setPosition(clamped, playing = false)
        }
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    /** Plays the line at [index] from its start. */
    fun playLine(index: Int) {
        val line = _state.value.lines.getOrNull(index) ?: return
        val file = audio
        val id = meetingId
        if (file != null && id != null) {
            PlaybackController.playAt(getApplication(), id, recordingTitle, file, line.startMs)
        } else {
            seekTo(line.startMs)
        }
    }

    fun setSpeed(speed: Float) {
        PlaybackController.setSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** Next stressed moment after the playhead, or nothing when there is none left. */
    fun nextStressedMoment() {
        val next = stressed.firstOrNull { it > _state.value.positionMs + STRESS_SLACK_MS } ?: return
        playLine(lineIndexAt(next))
    }

    fun setStopAtSectionEnd(stop: Boolean) = _state.update { it.copy(stopAtSectionEnd = stop) }

    fun setQuizAfter(quiz: Boolean) = _state.update { it.copy(quizAfter = quiz) }

    // ---------------------------------------------------------------- internals

    private fun isMine(ps: PlaybackState): Boolean = ps.recordingId != null && ps.recordingId == meetingId

    private fun onPlayback(ps: PlaybackState) {
        if (!isMine(ps)) {
            if (_state.value.isPlaying) _state.update { it.copy(isPlaying = false) }
            return
        }
        val before = lastPositionMs
        setPosition(ps.positionMs, playing = ps.isPlaying, durationMs = ps.durationMs)
        // Stop-at-section-end: pause when playback crosses the next section's start.
        if (_state.value.stopAtSectionEnd && ps.isPlaying) {
            val crossed = sectionStarts.any { (start, _) -> start > before && start <= ps.positionMs }
            if (crossed) PlaybackController.pause()
        }
    }

    private fun setPosition(positionMs: Long, playing: Boolean, durationMs: Long? = null) {
        lastPositionMs = positionMs
        _state.update { s ->
            s.copy(
                positionMs = positionMs,
                isPlaying = playing,
                durationMs = maxOf(s.durationMs, durationMs ?: 0L),
                currentLine = currentLineFor(s.lines, positionMs),
                sectionLabel = sectionFor(positionMs)
            )
        }
    }

    private fun currentLineFor(lines: List<ReadAlongLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return 0
        val idx = lines.indexOfLast { it.startMs <= positionMs }
        return if (idx < 0) 0 else idx
    }

    private fun lineIndexAt(ms: Long): Int = currentLineFor(_state.value.lines, ms)

    private fun sectionFor(positionMs: Long): String? =
        sectionStarts.lastOrNull { it.first <= positionMs }?.second

    private companion object {
        /** A stressed moment this close to the playhead counts as already reached. */
        const val STRESS_SLACK_MS = 1_000L
    }
}
