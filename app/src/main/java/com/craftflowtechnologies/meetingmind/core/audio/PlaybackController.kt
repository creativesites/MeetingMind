package com.craftflowtechnologies.meetingmind.core.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.craftflowtechnologies.meetingmind.core.common.findActiveTranscriptSegment
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pure phase-mapping decision, extracted out of [PlaybackController.refreshFromPlayer] so it's
 * directly unit-testable without faking a Media3 [MediaController] (this project has no
 * mocking library, and `MediaController` isn't something a plain fake can stand in for). The one
 * case worth calling out: [playWhenReady] true together with [playbackState] ==
 * [Player.STATE_BUFFERING] must still map to [PlaybackPhase.PLAYING], not [PlaybackPhase.LOADING]
 * — a brief mid-playback rebuffer (a real, common ExoPlayer transition) must never read as if
 * playback had stopped just because the initial-load path also passes through STATE_BUFFERING.
 * [previousPhase] is only used as the fallback for a state this mapping doesn't explicitly handle
 * (e.g. STATE_IDLE), so an unrecognized transition never silently resets to something wrong.
 */
fun computePlaybackPhase(
    playbackState: Int,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    previousPhase: PlaybackPhase
): PlaybackPhase = when {
    playbackState == Player.STATE_ENDED -> PlaybackPhase.COMPLETED
    isPlaying -> PlaybackPhase.PLAYING
    playWhenReady && playbackState == Player.STATE_BUFFERING -> PlaybackPhase.PLAYING
    playbackState == Player.STATE_READY -> PlaybackPhase.PAUSED
    playbackState == Player.STATE_BUFFERING -> PlaybackPhase.LOADING
    else -> previousPhase
}

/** Pure: whether [requestedRecordingId] is already loaded and ready on the real player, such that
 * a seek can land immediately with no async-load race to guard against (see
 * [PlaybackController.playAt]). */
fun isRecordingReadyToResume(
    hasController: Boolean,
    loadedRecordingId: String?,
    requestedRecordingId: String,
    mediaItemCount: Int,
    phase: PlaybackPhase
): Boolean = hasController &&
    loadedRecordingId == requestedRecordingId &&
    mediaItemCount > 0 &&
    phase != PlaybackPhase.COMPLETED

/** Pure: whether playback has reached (or passed) the end of an active loop window and should be
 * seeked back to its start — see [PlaybackController.setLoop]. */
fun shouldLoopBack(positionMs: Long, loopRange: LongRange?): Boolean =
    loopRange != null && positionMs >= loopRange.last

/**
 * Application-level playback singleton — the ONLY thing in the app allowed to create a media
 * player. Every screen (mini-player, Recording Detail, etc.) reads [state] and calls [play]/
 * [playAt]/[pause]/[stop]/[seekTo] here instead of owning a `MediaPlayer`/`ExoPlayer` itself, which
 * is what previously let playback outlive a Compose screen with no visible controls, or let two
 * playback sessions exist at once. Backed by [PlaybackService]'s single `MediaSession` — state
 * therefore survives navigation, activity recreation, screen lock, and app minimization, and
 * remains in sync with the system notification/lock-screen controls automatically.
 *
 * Starting a new recording's playback replaces the current `MediaItem` on the one shared
 * `ExoPlayer` instance — there is structurally no way for two streams to play at once.
 *
 * This is also the single place that resolves "which transcript segment/speaker is playing right
 * now" (see [setSegments]) — a screen reads [PlaybackState.currentSegmentId]/[currentSpeakerId]
 * instead of re-deriving it from [PlaybackState.positionMs] on its own, so there is exactly one
 * authoritative answer regardless of how many screens are observing playback.
 */
object PlaybackController {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaController: MediaController? = null
    private var connecting = false
    private var pendingPlayRequest: PlayRequest? = null
    // Set once immediately after a fresh MediaItem load that was asked to start at a specific
    // position (see playAt) — applied the moment the player actually reaches STATE_READY for that
    // item, never seeked-and-hoped-for synchronously against a controller/item that may not exist
    // or be prepared yet. This is the fix for the seek race: previously a caller would fire
    // play() then seekTo() back-to-back with no ordering guarantee against the async
    // connect/setMediaItem/prepare sequence, which is exactly what let a tapped transcript segment
    // silently start playback from 0:00 instead of the tapped timestamp.
    private var pendingSeekMs: Long? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var segmentsRecordingId: String? = null
    private var segments: List<TranscriptSegment> = emptyList()

    private data class PlayRequest(val recordingId: String, val title: String, val uri: Uri, val seekToMs: Long?)

    // --- queue (a reading made of several parts) ---
    private data class QueueRequest(val queueId: String, val items: List<QueueItem>, val startIndex: Int, val startPositionMs: Long, val expectedCount: Int)
    private var queueId: String? = null
    private var queueItems: List<QueueItem> = emptyList()
    private var queueExpected = 0
    private var pendingQueueRequest: QueueRequest? = null
    private var lastProgressSavedAt = 0L
    private var wasReading = false

    /** Told where a queue was left: on pause, on moving between parts, on stop, and every few seconds while playing. */
    @Volatile var progressListener: ((QueueProgress) -> Unit)? = null

    private val _readAloudEvents = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 8)
    /** [CompanionEvent.ReadAloudStarted] / [CompanionEvent.ReadAloudStopped] for `read:` queues. Nothing in the app hosts the companion machine yet; whoever does can collect this. */
    val readAloudEvents: SharedFlow<CompanionEvent> = _readAloudEvents.asSharedFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshFromPlayer()
            if (isPlaying) startProgressLoop() else { stopProgressLoop(); saveProgress(force = true) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            if (queueId == null) return
            val previous = queueItems.getOrNull(controller.currentMediaItemIndex - 1)
            if (QueueLogic.pauseOnTransition(previous?.pauseAfter, reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)) {
                controller.pause()
                _state.value = _state.value.copy(queue = _state.value.queue?.copy(pausedAfterQuestion = true))
            }
            saveProgress(force = true)
            refreshFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                pendingSeekMs?.let { seekMs ->
                    pendingSeekMs = null
                    val controller = mediaController
                    if (controller != null) {
                        controller.seekTo(seekMs.coerceIn(0L, controller.duration.coerceAtLeast(0L)))
                        controller.play()
                    }
                }
            }
            refreshFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            pendingSeekMs = null
            _state.value = _state.value.copy(phase = PlaybackPhase.ERROR, errorMessage = error.message)
        }
    }

    fun ensureConnected(context: Context) {
        if (mediaController != null || connecting) return
        connecting = true
        val sessionToken = SessionToken(context.applicationContext, ComponentName(context.applicationContext, PlaybackService::class.java))
        val future = MediaController.Builder(context.applicationContext, sessionToken).buildAsync()
        future.addListener(
            {
                connecting = false
                try {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    refreshFromPlayer()
                    pendingPlayRequest?.let { req ->
                        pendingPlayRequest = null
                        playInternal(req.recordingId, req.title, req.uri, req.seekToMs)
                    }
                    pendingQueueRequest?.let { req ->
                        pendingQueueRequest = null
                        playQueueInternal(req)
                    }
                } catch (e: Exception) {
                    _state.value = _state.value.copy(phase = PlaybackPhase.ERROR, errorMessage = "Could not connect to playback service.")
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun play(context: Context, recordingId: String, title: String, file: File) {
        playAt(context, recordingId, title, file, positionMs = null)
    }

    /**
     * Atomically loads (if needed) and plays [recordingId] starting at [positionMs] — the single
     * entry point for "seek to this timestamp and play," replacing the old pattern of a caller
     * composing this from separate [play]/[seekTo] calls with no ordering guarantee between them.
     * [positionMs] of null behaves exactly like [play] (start/resume from wherever the item
     * already is). Safe to call from any state: stopped (nothing loaded), paused, or already
     * playing — always ends with the requested recording playing from [positionMs].
     */
    fun playAt(context: Context, recordingId: String, title: String, file: File, positionMs: Long?) {
        if (!file.exists()) {
            _state.value = PlaybackState(phase = PlaybackPhase.ERROR, recordingId = recordingId, title = title, errorMessage = "Audio file is missing.")
            return
        }
        playUriAt(context, recordingId, title, Uri.fromFile(file), positionMs)
    }

    /** Streams [uri] (an audio Bible chapter, say) through the same single player. */
    fun playUriAt(context: Context, recordingId: String, title: String, uri: Uri, positionMs: Long?) {
        ensureConnected(context)
        val controller = mediaController
        val alreadyLoaded = isRecordingReadyToResume(
            hasController = controller != null,
            loadedRecordingId = _state.value.recordingId,
            requestedRecordingId = recordingId,
            mediaItemCount = controller?.mediaItemCount ?: 0,
            phase = _state.value.phase
        )
        if (controller == null) {
            pendingPlayRequest = PlayRequest(recordingId, title, uri, positionMs)
            _state.value = PlaybackState(phase = PlaybackPhase.LOADING, recordingId = recordingId, title = title)
            return
        }
        if (alreadyLoaded) {
            // The item is already prepared on the real player — a seek here lands immediately,
            // no race to guard against.
            if (positionMs != null) {
                controller.seekTo(positionMs.coerceIn(0L, controller.duration.coerceAtLeast(0L)))
            }
            controller.play()
            refreshFromPlayer()
            return
        }
        playInternal(recordingId, title, uri, positionMs)
    }

    private fun playInternal(recordingId: String, title: String, uri: Uri, seekToMs: Long?) {
        val controller = mediaController ?: return
        stopAtMs = null
        clearQueue()
        pendingSeekMs = seekToMs
        _state.value = PlaybackState(phase = PlaybackPhase.LOADING, recordingId = recordingId, title = title)
        val mediaItem = MediaItem.Builder()
            .setMediaId(recordingId)
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        // A plain play() (no target position) can start immediately — nothing to wait for. A
        // seek-on-load instead waits for STATE_READY (see playerListener) so the seek lands on the
        // real, prepared item rather than racing its own load.
        if (seekToMs == null) {
            controller.play()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Queue: a reading made of several parts, played one after another on the same player.
    // ---------------------------------------------------------------------------------------

    /**
     * Loads [items] as one queue named [queueId] (for a reading: `read:<noteId>`) and plays from
     * [startIndex] at [startPositionMs]. If the same audio is already loaded it only seeks. More parts can
     * arrive later with [appendItem]; [expectedCount] says how many there will be, so running out of
     * available parts reads as "preparing the next one", not "finished".
     */
    fun playQueue(context: Context, queueId: String, items: List<QueueItem>, startIndex: Int = 0, startPositionMs: Long = 0L, expectedCount: Int = items.size) {
        if (items.isEmpty()) return
        ensureConnected(context)
        val request = QueueRequest(queueId, items, startIndex, startPositionMs, maxOf(expectedCount, items.size))
        if (mediaController == null) {
            pendingQueueRequest = request
            val idx = QueueLogic.clampIndex(startIndex, items.size)
            _state.value = PlaybackState(phase = PlaybackPhase.LOADING, recordingId = queueId, title = items[idx].title, queue = queueInfo(items, idx, request.expectedCount, waiting = false, paused = false))
            return
        }
        playQueueInternal(request)
    }

    private fun playQueueInternal(r: QueueRequest) {
        val controller = mediaController ?: return
        stopAtMs = null
        pendingSeekMs = null
        val index = QueueLogic.clampIndex(r.startIndex, r.items.size)
        val reuse = _state.value.recordingId == r.queueId &&
            controller.mediaItemCount == r.items.size &&
            QueueLogic.canReuse(queueItems.map { it.mediaId }, r.items.map { it.mediaId })
        queueId = r.queueId
        queueItems = r.items
        queueExpected = r.expectedCount
        if (reuse) {
            controller.seekTo(index, r.startPositionMs.coerceAtLeast(0))
            controller.play()
            refreshFromPlayer()
            return
        }
        _state.value = PlaybackState(phase = PlaybackPhase.LOADING, recordingId = r.queueId, title = r.items[index].title, queue = queueInfo(r.items, index, r.expectedCount, waiting = false, paused = false))
        controller.setMediaItems(r.items.map(::toMediaItem), index, r.startPositionMs.coerceAtLeast(0))
        controller.prepare()
        controller.play()
    }

    /** Adds a part to the loaded queue as it becomes ready. If playback had run out of parts, it carries on into this one. */
    fun appendItem(item: QueueItem) {
        queueItems = queueItems + item
        queueExpected = maxOf(queueExpected, queueItems.size)
        pendingQueueRequest = pendingQueueRequest?.copy(items = pendingQueueRequest!!.items + item, expectedCount = queueExpected)
        val controller = mediaController ?: return
        if (queueId == null || pendingQueueRequest != null) return
        val wasWaiting = _state.value.queue?.waitingForNext == true
        controller.addMediaItem(toMediaItem(item))
        if (wasWaiting) {
            controller.seekTo(queueItems.lastIndex, 0)
            controller.play()
        }
        refreshFromPlayer()
    }

    /** The queue's final size once known, so the end of the last part is the end of the reading. */
    fun setExpectedCount(count: Int) {
        queueExpected = maxOf(count, queueItems.size)
        refreshFromPlayer()
    }

    fun next() {
        val controller = mediaController ?: return
        QueueLogic.nextIndex(controller.currentMediaItemIndex, queueItems.size)?.let { controller.seekTo(it, 0); controller.play() }
    }

    fun previous() {
        val controller = mediaController ?: return
        controller.seekTo(QueueLogic.previousTarget(controller.currentMediaItemIndex, controller.currentPosition), 0)
        controller.play()
    }

    fun skipTo(index: Int) {
        val controller = mediaController ?: return
        if (queueItems.isEmpty()) return
        controller.seekTo(QueueLogic.clampIndex(index, queueItems.size), 0)
        controller.play()
    }

    private fun clearQueue() {
        queueId = null
        queueItems = emptyList()
        queueExpected = 0
    }

    private fun toMediaItem(item: QueueItem): MediaItem = MediaItem.Builder()
        .setMediaId(item.mediaId)
        .setUri(item.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
        .build()

    private fun queueInfo(items: List<QueueItem>, index: Int, expected: Int, waiting: Boolean, paused: Boolean) = QueueInfo(
        titles = items.map { it.title },
        index = index,
        expectedCount = expected,
        waitingForNext = waiting,
        blockIds = items.map { it.blockIds },
        pausedAfterQuestion = paused,
        sectionIds = items.map { it.sectionId.orEmpty() }
    )

    private fun saveProgress(force: Boolean) {
        val id = queueId ?: return
        val controller = mediaController ?: return
        val listener = progressListener ?: return
        val now = System.currentTimeMillis()
        if (!force && !QueueLogic.progressDue(lastProgressSavedAt, now)) return
        val index = QueueLogic.clampIndex(controller.currentMediaItemIndex, queueItems.size)
        val item = queueItems.getOrNull(index) ?: return
        lastProgressSavedAt = now
        val completed = _state.value.phase == PlaybackPhase.COMPLETED
        listener(QueueProgress(id, item.mediaId, index, if (completed) 0L else controller.currentPosition.coerceAtLeast(0L), completed))
    }

    private fun announceReadAloud() {
        val reading = ReadAloudSignals.isReading(_state.value)
        when (ReadAloudSignals.transition(wasReading, reading)) {
            ReadAloudSignals.Signal.STARTED -> _readAloudEvents.tryEmit(CompanionEvent.ReadAloudStarted)
            ReadAloudSignals.Signal.STOPPED -> _readAloudEvents.tryEmit(CompanionEvent.ReadAloudStopped)
            null -> Unit
        }
        wasReading = reading
    }

    fun togglePlayPause() {
        stopAtMs = null
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun stop() {
        val controller = mediaController
        saveProgress(force = true)
        controller?.stop()
        controller?.clearMediaItems()
        pendingSeekMs = null
        pendingPlayRequest = null
        pendingQueueRequest = null
        stopAtMs = null
        clearQueue()
        stopProgressLoop()
        _state.value = PlaybackState()
        announceReadAloud()
    }

    /** Plain seek against an already-loaded item — safe only when the caller already knows
     * playback is active/loaded (e.g. a scrub-bar drag on a visible player). Prefer [playAt] for
     * "jump to this timestamp," which is race-free regardless of whether anything is loaded yet. */
    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val clamped = positionMs.coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(clamped)
        refreshFromPlayer()
    }

    /**
     * Registers the transcript segments for [recordingId] so [PlaybackState.currentSegmentId]/
     * [currentSpeakerId] can be computed as position updates arrive — call this whenever a
     * screen's transcript for the currently-relevant recording loads or changes. Segments for a
     * recording other than whatever is currently loaded are stored but not applied (see
     * [refreshFromPlayer]), so an off-screen recording's transcript loading first never corrupts
     * the currently playing one's active-segment state.
     */
    fun setSegments(recordingId: String, segments: List<TranscriptSegment>) {
        segmentsRecordingId = recordingId
        this.segments = segments
        refreshFromPlayer()
    }

    /** Repeats [range] on the currently loaded recording — used by review-style UI that wants to
     * replay a short window (e.g. around a flagged word) without the caller polling position to
     * detect the loop boundary. A no-op if nothing is loaded. */
    fun setLoop(range: LongRange?) {
        _state.value = _state.value.copy(loopRange = range)
        if (range != null) {
            val controller = mediaController ?: return
            controller.seekTo(range.first.coerceIn(0L, controller.duration.coerceAtLeast(0L)))
        }
    }

    fun clearLoop() = setLoop(null)

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun setSpeed(value: Float) {
        _speed.value = value
        mediaController?.setPlaybackSpeed(value)
    }

    fun seekBy(deltaMs: Long) = seekTo((_state.value.positionMs + deltaMs).coerceAtLeast(0L))

    /** Pause when playback reaches this position (a devotional's prayer, played on its own). */
    @Volatile private var stopAtMs: Long? = null

    /** Plays [file] from [fromMs] and pauses at [toMs] — one section of a recording. */
    fun playRange(context: Context, recordingId: String, title: String, file: File, fromMs: Long, toMs: Long?) {
        stopAtMs = toMs
        playAt(context, recordingId, title, file, fromMs)
    }

    private fun refreshFromPlayer() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return
        val isBuffering = controller.playbackState == Player.STATE_BUFFERING
        var phase = computePlaybackPhase(
            playbackState = controller.playbackState,
            isPlaying = controller.isPlaying,
            playWhenReady = controller.playWhenReady,
            previousPhase = _state.value.phase
        )
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        stopAtMs?.let { stop -> if (positionMs >= stop && controller.isPlaying) { stopAtMs = null; controller.pause() } }
        val loopRange = _state.value.loopRange
        if (shouldLoopBack(positionMs, loopRange)) {
            controller.seekTo(loopRange!!.first.coerceIn(0L, controller.duration.coerceAtLeast(0L)))
        }
        val activeSegment = if (segmentsRecordingId == _state.value.recordingId) {
            findActiveTranscriptSegment(segments, positionMs)
        } else null
        var queue: QueueInfo? = null
        var title = _state.value.title
        if (queueId != null && queueItems.isNotEmpty()) {
            val index = QueueLogic.clampIndex(controller.currentMediaItemIndex, queueItems.size)
            val ended = controller.playbackState == Player.STATE_ENDED
            val waiting = QueueLogic.isWaitingForNext(ended, index, queueItems.size, queueExpected)
            if (waiting) phase = PlaybackPhase.LOADING
            val paused = (_state.value.queue?.pausedAfterQuestion == true) && !controller.isPlaying
            queue = queueInfo(queueItems, index, queueExpected, waiting, paused)
            title = queueItems[index].title
        }
        _state.value = _state.value.copy(
            phase = phase,
            title = title,
            durationMs = controller.duration.coerceAtLeast(0L),
            positionMs = positionMs,
            canSeek = controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            isBuffering = isBuffering,
            currentSegmentId = activeSegment?.id,
            currentSpeakerId = activeSegment?.speakerId,
            queue = queue
        )
        if (phase == PlaybackPhase.PLAYING) saveProgress(force = false)
        if (phase == PlaybackPhase.COMPLETED) saveProgress(force = true)
        announceReadAloud()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                refreshFromPlayer()
                delay(200)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
    }
}
