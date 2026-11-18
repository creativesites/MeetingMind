package com.craftflowtechnologies.meetingmind.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
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

enum class RecorderState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

class AudioRecorder(private val context: Context) {
    companion object {
        internal const val CHECKPOINT_AAC_NAME = "capture.aac"
        internal const val FINAL_M4A_NAME = "audio.m4a"
        private const val CHECKPOINT_SYNC_INTERVAL_MS = 8_000L
        private const val MIN_AUDIO_BYTES = 1024L
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputPfd: ParcelFileDescriptor? = null
    private var currentOutputFile: File? = null
    private var finalM4aTarget: File? = null
    private var capturingAsAdts: Boolean = false
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
    private var lastCheckpointSyncAtMs: Long = 0L

    fun startRecording(meetingId: String): File {
        val meetingDir = File(context.filesDir, "meetings/$meetingId").apply { mkdirs() }
        val captureAac = File(meetingDir, CHECKPOINT_AAC_NAME)
        val outputM4a = File(meetingDir, FINAL_M4A_NAME)
        finalM4aTarget = outputM4a

        // Prefer self-framing AAC ADTS capture so an unexpected battery cut or OS kill never
        // leaves an unplayable .m4a lacking its `moov` atom. Falls back to MPEG_4 only if the
        // device rejects AAC_ADTS.
        val startedAdts = tryStartMediaRecorder(captureAac, useAdts = true)
        val activeFile = if (startedAdts) {
            capturingAsAdts = true
            captureAac
        } else {
            // This phone refused ADTS. The MPEG_4 file has no moov atom until stop(), so a hard
            // kill on this path loses the recording: logged to learn whether any phone takes it.
            com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log(
                "recording", "AAC_ADTS refused, MPEG_4 fallback on ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})"
            )
            runCatching { captureAac.delete() }
            tryStartMediaRecorderOrThrow(outputM4a, useAdts = false)
            capturingAsAdts = false
            outputM4a
        }

        currentOutputFile = activeFile
        _state.value = RecorderState.RECORDING
        startTimeMs = System.currentTimeMillis()
        lastCheckpointSyncAtMs = startTimeMs
        accumulatedDurationMs = 0L
        startAmplitudeAndTimerLoop()
        return activeFile
    }

    private fun tryStartMediaRecorder(target: File, useAdts: Boolean): Boolean = try {
        tryStartMediaRecorderOrThrow(target, useAdts)
        true
    } catch (e: Exception) {
        Log.w("AudioRecorder", "Recorder start failed (useAdts=$useAdts): ${e.message}")
        releaseRecorder()
        false
    }

    private fun tryStartMediaRecorderOrThrow(target: File, useAdts: Boolean) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        val pfd = ParcelFileDescriptor.open(
            target,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE
        )
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(if (useAdts) MediaRecorder.OutputFormat.AAC_ADTS else MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(16000)
                setOutputFile(pfd.fileDescriptor)
                prepare()
                start()
            }
            outputPfd = pfd
            mediaRecorder = recorder
        } catch (e: Exception) {
            runCatching { pfd.close() }
            runCatching { recorder.reset(); recorder.release() }
            throw e
        }
    }

    /** Flushes buffered capture bytes to flash without interrupting live recording. */
    fun checkpointSync() {
        val fd = outputPfd?.fileDescriptor ?: return
        runCatching { fd.sync() }
        lastCheckpointSyncAtMs = System.currentTimeMillis()
    }

    fun pauseRecording() {
        if (_state.value == RecorderState.RECORDING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                checkpointSync()
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

        checkpointSync()
        var stopFailed = false
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Stop error (recording might have been too short)", e)
            stopFailed = true
        } finally {
            releaseRecorder()
        }

        val activeFile = currentOutputFile
        val m4aTarget = finalM4aTarget
        val wasAdts = capturingAsAdts
        currentOutputFile = null
        finalM4aTarget = null
        capturingAsAdts = false

        if (activeFile == null || !activeFile.exists()) return null

        if (wasAdts) {
            // Even when MediaRecorder.stop() fails, valid ADTS frames already on flash stay playable.
            val inspection = runCatching { AdtsAudioInspector.trimToValidFrames(activeFile) }.getOrNull()
            if (inspection == null || !inspection.isPlayable || activeFile.length() < MIN_AUDIO_BYTES) {
                runCatching { activeFile.delete() }
                return null
            }
            if (m4aTarget != null && RecordingAudioSalvager.remuxAdtsToM4a(activeFile, m4aTarget)) {
                runCatching { activeFile.delete() }
                return m4aTarget
            }
            return activeFile
        }

        if (stopFailed || activeFile.length() < MIN_AUDIO_BYTES) {
            runCatching { activeFile.delete() }
            return null
        }

        return activeFile
    }

    /**
     * Ends capture without trimming or converting: stops and releases the microphone and leaves
     * the captured file where it is, for the recovery on the next launch. For when the system
     * destroys the recording service, where slow file work on the main thread could freeze the app.
     */
    fun releaseForRecovery() {
        if (_state.value == RecorderState.RECORDING) {
            accumulatedDurationMs += (System.currentTimeMillis() - startTimeMs)
        }
        _durationMs.value = accumulatedDurationMs
        amplitudeJob?.cancel()
        checkpointSync()
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w("AudioRecorder", "Stop for recovery failed; the file on flash stays: ${e.message}")
        } finally {
            releaseRecorder()
        }
        _state.value = RecorderState.STOPPED
        currentOutputFile = null
        finalM4aTarget = null
        capturingAsAdts = false
    }

    fun discardRecording() {
        val activeFile = currentOutputFile
        val m4aTarget = finalM4aTarget
        try {
            stopRecording()
            activeFile?.delete()
            m4aTarget?.delete()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Discard error", e)
        } finally {
            _state.value = RecorderState.IDLE
            _durationMs.value = 0L
            _amplitude.value = 0f
            currentOutputFile = null
            finalM4aTarget = null
        }
    }

    private fun startAmplitudeAndTimerLoop() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && (_state.value == RecorderState.RECORDING || _state.value == RecorderState.PAUSED)) {
                if (_state.value == RecorderState.RECORDING) {
                    val now = System.currentTimeMillis()
                    val currentRunning = accumulatedDurationMs + (now - startTimeMs)
                    _durationMs.value = currentRunning
                    if (now - lastCheckpointSyncAtMs >= CHECKPOINT_SYNC_INTERVAL_MS) {
                        checkpointSync()
                    }

                    val maxAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
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
        try {
            outputPfd?.close()
        } catch (e: Exception) {
            Log.w("AudioRecorder", "PFD close error: ${e.message}")
        } finally {
            outputPfd = null
        }
    }
}
