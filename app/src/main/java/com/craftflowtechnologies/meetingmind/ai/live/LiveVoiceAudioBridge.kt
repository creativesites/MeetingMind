package com.craftflowtechnologies.meetingmind.ai.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantAction
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantHost
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantTool
import com.craftflowtechnologies.meetingmind.ai.assistant.ToolCall
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Full-duplex audio + tool execution bridge wrapping [OkHttpLiveTransport] and [LiveSessionController].
 *
 * Captures 16 kHz PCM16 mono microphone audio (with hardware AEC/NS/AGC when available), plays
 * 24 kHz PCM16 server speech via [AudioTrack], and routes live function calls directly into an
 * [AssistantHost] so voice turns can read/edit notes, scripture, prayer requests, and tasks.
 */
class LiveVoiceAudioBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: AssistantHost,
    private val tools: List<AssistantTool>,
    private val onActionEmitted: (AssistantAction) -> Unit = {},
    private val onTurnTranscript: (fromUser: Boolean, text: String) -> Unit = { _, _ -> }
) {
    private val _state = MutableStateFlow(LiveSessionState.DISCONNECTED)
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _inputTranscript = MutableStateFlow("")
    val inputTranscript: StateFlow<String> = _inputTranscript.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var controller: LiveSessionController? = null
    private var transport: OkHttpLiveTransport? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var audioQueue: Channel<ByteArray>? = null

    private var playJob: Job? = null
    private var micJob: Job? = null
    private var observeJob: Job? = null
    private var deviceCallback: AudioDeviceCallback? = null
    private var prevAudioMode: Int? = null
    private var prevSpeaker: Boolean? = null

    fun isConfigured(): Boolean = OkHttpLiveTransport(GeminiCredentialStore(context)).isConfigured()

    suspend fun start(systemInstruction: String): AiResult<Unit> {
        stop()
        _error.value = null
        _muted.value = false
        _state.value = LiveSessionState.CONNECTING

        val queue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
        audioQueue = queue

        val liveTransport = OkHttpLiveTransport(
            credentials = GeminiCredentialStore(context),
            toolsJson = AssistantTool.nativeDeclarationsJson(tools),
            onOutputAudioPcm24k = { pcm -> queue.trySend(pcm) },
            onInterrupted = { flushPlayback(queue) }
        )
        transport = liveTransport

        val executor = LiveToolExecutor { call -> executeHostTool(call) }
        val ctrl = LiveSessionController(
            transport = liveTransport,
            scope = scope,
            toolExecutor = executor,
            profile = ProcessingProfile.LIVE
        )
        controller = ctrl

        observeJob = scope.launch {
            launch {
                ctrl.state.collect { st ->
                    _state.value = st
                }
            }
            launch {
                ctrl.inputTranscript.collect { text ->
                    _inputTranscript.value = text
                    if (text.isNotBlank()) onTurnTranscript(true, text)
                }
            }
            launch {
                ctrl.lastError.collect { err ->
                    if (!err.isNullOrBlank()) _error.value = err
                }
            }
        }

        val connectResult = ctrl.connect(systemInstruction)
        if (connectResult !is AiResult.Success) {
            val msg = connectResult.describeFailure() ?: "Couldn't start Gemini Live voice."
            _error.value = msg
            _state.value = LiveSessionState.ERROR
            return connectResult
        }

        val audioOk = setupAudio(queue, ctrl)
        if (!audioOk) {
            stop()
            val msg = "Microphone unavailable. Allow microphone permission for MeetingMind."
            _error.value = msg
            _state.value = LiveSessionState.ERROR
            return AiResult.Failed(msg)
        }

        ctrl.startListening()
        return AiResult.Success(Unit)
    }

    fun toggleMute() {
        _muted.value = !_muted.value
    }

    suspend fun stop() {
        micJob?.cancel()
        micJob = null
        playJob?.cancel()
        playJob = null
        observeJob?.cancel()
        observeJob = null

        audioQueue?.close()
        audioQueue = null

        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null

        runCatching { player?.pause() }
        runCatching { player?.flush() }
        runCatching { player?.release() }
        player = null

        restoreAudioRouting()

        runCatching { controller?.disconnect() }
        controller = null
        transport = null

        _audioLevel.value = 0f
        _inputTranscript.value = ""
        _state.value = LiveSessionState.DISCONNECTED
    }

    private suspend fun executeHostTool(call: LiveToolCall): LiveToolResult {
        val tool = AssistantTool.byId(call.name)
            ?: return LiveToolResult(call.id, JSONObject().put("error", "Unknown tool: ${call.name}").toString(), isError = true)
        if (tool !in tools) {
            return LiveToolResult(call.id, JSONObject().put("error", "Tool ${tool.id} is not available in this scope.").toString(), isError = true)
        }
        val args = runCatching { JSONObject(call.argumentsJson) }.getOrElse { JSONObject() }
        val toolCall = ToolCall(tool, args)
        return when (tool.kind) {
            AssistantTool.Kind.READ -> {
                val res = host.read(toolCall)
                val payload = JSONObject()
                    .put("ok", res.ok)
                    .put("summary", res.chip)
                    .put("output", res.text)
                LiveToolResult(call.id, payload.toString(), isError = !res.ok)
            }
            AssistantTool.Kind.WRITE, AssistantTool.Kind.CONFIRM -> {
                val (res, action) = host.write(toolCall)
                onActionEmitted(action)
                val payload = JSONObject()
                    .put("ok", res.ok)
                    .put("summary", res.chip)
                    .put("output", res.text)
                    .put("requires_confirmation", action.needsConfirmation)
                LiveToolResult(call.id, payload.toString(), isError = !res.ok)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupAudio(queue: Channel<ByteArray>, ctrl: LiveSessionController): Boolean {
        configureAudioRouting()

        val inMin = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                IN_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(inMin, IN_RATE / 5 * 2)
            )
        }.getOrNull()

        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord initialization failed")
            runCatching { rec?.release() }
            restoreAudioRouting()
            return false
        }

        val outMin = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(OUT_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(outMin, OUT_RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(rec.audioSessionId)
                .build()
                .also { it.play() }
        }.getOrNull()

        if (track == null) {
            runCatching { rec.release() }
            restoreAudioRouting()
            return false
        }

        player = track
        recorder = rec

        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
        runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(rec.audioSessionId)?.enabled = true }

        playJob = scope.launch(Dispatchers.IO) {
            for (pcm in queue) {
                if (!isActive) break
                _audioLevel.value = rms(pcm, pcm.size)
                var offset = 0
                while (offset < pcm.size && isActive) {
                    val written = track.write(pcm, offset, pcm.size - offset)
                    if (written <= 0) break
                    offset += written
                }
            }
        }

        runCatching { rec.startRecording() }
        micJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(IN_RATE / 10 * 2) // 100ms @ 16kHz PCM16 mono
            while (isActive) {
                val read = rec.read(buffer, 0, buffer.size)
                // A negative code (mic taken over or revoked) repeats forever: wait instead of spinning.
                if (read <= 0) { kotlinx.coroutines.delay(50); continue }
                val lvl = rms(buffer, read)
                if (_state.value != LiveSessionState.MODEL_SPEAKING) {
                    _audioLevel.value = lvl
                }
                if (_muted.value) continue
                ctrl.sendAudio(buffer.copyOf(read))
            }
        }

        // Decay meter smoothly while quiet
        scope.launch {
            while (isActive && recorder != null) {
                if (_state.value != LiveSessionState.MODEL_SPEAKING && _muted.value) {
                    _audioLevel.value *= 0.7f
                }
                delay(60)
            }
        }
        return true
    }

    private fun flushPlayback(queue: Channel<ByteArray>) {
        while (queue.tryReceive().isSuccess) {
            // Drain queued model audio on user interruption
        }
        runCatching {
            player?.pause()
            player?.flush()
            player?.play()
        }
    }

    private fun configureAudioRouting() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            prevAudioMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                routeCommunicationDevice(am)
                val cb = object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        runCatching { routeCommunicationDevice(am) }
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        runCatching { routeCommunicationDevice(am) }
                    }
                }
                deviceCallback = cb
                am.registerAudioDeviceCallback(cb, null)
            } else {
                @Suppress("DEPRECATION")
                prevSpeaker = am.isSpeakerphoneOn
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }
        }
    }

    private fun routeCommunicationDevice(am: AudioManager) {
        if (Build.VERSION.SDK_INT < 31) return
        val devices = am.availableCommunicationDevices
        val headset = devices.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        val target = headset ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (target != null) am.setCommunicationDevice(target)
    }

    private fun restoreAudioRouting() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            deviceCallback?.let { am.unregisterAudioDeviceCallback(it) }
            deviceCallback = null
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                prevSpeaker?.let {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = it
                }
            }
            prevAudioMode?.let { am.mode = it }
        }
        prevAudioMode = null
        prevSpeaker = null
    }

    companion object {
        private const val TAG = "LiveVoiceAudioBridge"
        private const val IN_RATE = 16_000
        private const val OUT_RATE = 24_000

        private fun rms(pcm: ByteArray, length: Int): Float {
            if (length < 2) return 0f
            var sumSq = 0.0
            var i = 0
            val count = length / 2
            while (i + 1 < length) {
                val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toDouble() / 32768.0
                sumSq += sample * sample
                i += 2
            }
            return sqrt(sumSq / count.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
        }
    }
}
