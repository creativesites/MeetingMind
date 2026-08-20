package com.example.core.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application-level playback singleton — the ONLY thing in the app allowed to create a media
 * player. Every screen (mini-player, Recording Detail, etc.) reads [state] and calls [play]/
 * [pause]/[stop]/[seekTo] here instead of owning a `MediaPlayer`/`ExoPlayer` itself, which is
 * what previously let playback outlive a Compose screen with no visible controls, or let two
 * playback sessions exist at once. Backed by [PlaybackService]'s single `MediaSession` — state
 * therefore survives navigation, activity recreation, screen lock, and app minimization, and
 * remains in sync with the system notification/lock-screen controls automatically.
 *
 * Starting a new recording's playback replaces the current `MediaItem` on the one shared
 * `ExoPlayer` instance — there is structurally no way for two streams to play at once.
 */
object PlaybackController {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaController: MediaController? = null
    private var connecting = false
    private var pendingPlayRequest: PlayRequest? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private data class PlayRequest(val recordingId: String, val title: String, val file: File)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshFromPlayer()
            if (isPlaying) startProgressLoop() else stopProgressLoop()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
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
                        playInternal(req.recordingId, req.title, req.file)
                    }
                } catch (e: Exception) {
                    _state.value = _state.value.copy(phase = PlaybackPhase.ERROR, errorMessage = "Could not connect to playback service.")
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun play(context: Context, recordingId: String, title: String, file: File) {
        if (!file.exists()) {
            _state.value = PlaybackState(phase = PlaybackPhase.ERROR, recordingId = recordingId, title = title, errorMessage = "Audio file is missing.")
            return
        }
        ensureConnected(context)
        val controller = mediaController
        if (controller == null) {
            pendingPlayRequest = PlayRequest(recordingId, title, file)
            _state.value = PlaybackState(phase = PlaybackPhase.LOADING, recordingId = recordingId, title = title)
            return
        }
        // Already loaded (e.g. resuming after pausing then reopening the same recording) — just resume.
        if (_state.value.recordingId == recordingId && controller.mediaItemCount > 0 && _state.value.phase != PlaybackPhase.COMPLETED) {
            controller.play()
            return
        }
        playInternal(recordingId, title, file)
    }

    private fun playInternal(recordingId: String, title: String, file: File) {
        val controller = mediaController ?: return
        _state.value = PlaybackState(phase = PlaybackPhase.LOADING, recordingId = recordingId, title = title)
        val mediaItem = MediaItem.Builder()
            .setMediaId(recordingId)
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun stop() {
        val controller = mediaController
        controller?.stop()
        controller?.clearMediaItems()
        stopProgressLoop()
        _state.value = PlaybackState()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val clamped = positionMs.coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(clamped)
        refreshFromPlayer()
    }

    private fun refreshFromPlayer() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return
        val phase = when {
            controller.playbackState == Player.STATE_ENDED -> PlaybackPhase.COMPLETED
            controller.isPlaying -> PlaybackPhase.PLAYING
            controller.playbackState == Player.STATE_BUFFERING -> PlaybackPhase.LOADING
            controller.playbackState == Player.STATE_READY -> PlaybackPhase.PAUSED
            else -> _state.value.phase
        }
        _state.value = _state.value.copy(
            phase = phase,
            durationMs = controller.duration.coerceAtLeast(0L),
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            canSeek = controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        )
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
