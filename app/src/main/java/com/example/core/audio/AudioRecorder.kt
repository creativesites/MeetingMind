package com.example.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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
import java.io.IOException

enum class RecorderState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow(RecorderState.IDLE)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var startTimeMs: Long = 0L
    private var accumulatedDurationMs: Long = 0L

    fun startRecording(meetingId: String): File {
        val meetingDir = File(context.filesDir, "meetings/$meetingId").apply { mkdirs() }
        val outputFile = File(meetingDir, "audio.m4a")
        currentOutputFile = outputFile

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(16000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _state.value = RecorderState.RECORDING
            startTimeMs = System.currentTimeMillis()
            accumulatedDurationMs = 0L
            startAmplitudeAndTimerLoop()

            return outputFile
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            _state.value = RecorderState.IDLE
            releaseRecorder()
            throw e
        }
    }

    fun pauseRecording() {
        if (_state.value == RecorderState.RECORDING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                accumulatedDurationMs += (System.currentTimeMillis() - startTimeMs)
                _state.value = RecorderState.PAUSED
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Pause error", e)
            }
        }
    }

    fun resumeRecording() {
        if (_state.value == RecorderState.PAUSED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                startTimeMs = System.currentTimeMillis()
                _state.value = RecorderState.RECORDING
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Resume error", e)
            }
        }
    }

    fun stopRecording(): File? {
        if (_state.value == RecorderState.RECORDING) {
            accumulatedDurationMs += (System.currentTimeMillis() - startTimeMs)
        }
        _durationMs.value = accumulatedDurationMs
        _state.value = RecorderState.STOPPED
        amplitudeJob?.cancel()

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Stop error (recording might have been too short)", e)
        } finally {
            releaseRecorder()
        }

        return currentOutputFile
    }

    fun discardRecording() {
        try {
            stopRecording()
            currentOutputFile?.delete()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Discard error", e)
        } finally {
            _state.value = RecorderState.IDLE
            _durationMs.value = 0L
            _amplitude.value = 0f
            currentOutputFile = null
        }
    }

    private fun startAmplitudeAndTimerLoop() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && (_state.value == RecorderState.RECORDING || _state.value == RecorderState.PAUSED)) {
                if (_state.value == RecorderState.RECORDING) {
                    val currentRunning = accumulatedDurationMs + (System.currentTimeMillis() - startTimeMs)
                    _durationMs.value = currentRunning

                    val maxAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    // Normalize amplitude to 0.0 .. 1.0
                    val normalized = (maxAmp.toFloat() / 32767f).coerceIn(0f, 1f)
                    _amplitude.value = normalized
                } else {
                    _amplitude.value = 0f
                }
                delay(80)
            }
        }
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Release error", e)
        } finally {
            mediaRecorder = null
        }
    }
}
