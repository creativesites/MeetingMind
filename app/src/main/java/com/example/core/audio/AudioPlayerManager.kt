package com.example.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val loadedFile: String? = null
)

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    fun loadAudio(audioFile: File) {
        if (!audioFile.exists()) {
            Log.w("AudioPlayerManager", "Audio file does not exist: ${audioFile.absolutePath}")
            return
        }

        release()
        try {
            val player = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(audioFile))
                prepare()
                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(isPlaying = false, currentPositionMs = 0L)
                    progressJob?.cancel()
                }
            }
            mediaPlayer = player
            _playerState.value = AudioPlayerState(
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = player.duration.toLong(),
                loadedFile = audioFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to load audio file", e)
        }
    }

    fun play() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) {
            player.start()
            _playerState.value = _playerState.value.copy(isPlaying = true)
            startProgressLoop()
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
            progressJob?.cancel()
        }
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, player.duration.toLong())
        player.seekTo(clamped.toInt())
        _playerState.value = _playerState.value.copy(currentPositionMs = clamped)
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val current = mediaPlayer?.currentPosition?.toLong() ?: 0L
                _playerState.value = _playerState.value.copy(currentPositionMs = current)
                delay(100)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Release error", e)
        } finally {
            mediaPlayer = null
            _playerState.value = AudioPlayerState()
        }
    }
}
