package com.example.ai.live

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** What happens in a live voice conversation, for the screen to show. */
sealed interface LiveVoiceEvent {
    data object Ready : LiveVoiceEvent
    /** A piece of what the person said (transcribed), or of what the companion said. */
    data class Heard(val text: String) : LiveVoiceEvent
    data class Said(val text: String) : LiveVoiceEvent
    data object TurnDone : LiveVoiceEvent
    data object Interrupted : LiveVoiceEvent
    data class Failed(val message: String) : LiveVoiceEvent
    data object Closed : LiveVoiceEvent
}

enum class LiveVoiceState { CONNECTING, LISTENING, SPEAKING, PAUSED, ENDED, FAILED }

/**
 * A live, spoken conversation with Gemini (Live API, `gemini-3.8-live`): the microphone streams up
 * as 16 kHz PCM, the companion's voice streams back as 24 kHz PCM and plays as it arrives, and
 * both sides are transcribed. Used for "Pray with me" and "Talk it through" (PLAN_V2 F7).
 *
 * The key is passed in from [com.example.ai.cloud.GeminiCredentialStore] for this session only.
 */
class GeminiLiveVoice(
    private val apiKey: String,
    private val setup: JSONObject,
    // No WebSocket pings: the Live endpoint doesn't answer them, and OkHttp would drop the
    // conversation after one missed pong. A setup timeout guards the start instead.
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
) {
    companion object {
        const val MODEL = "gemini-3.8-live"
        private const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val IN_RATE = 16_000
        private const val OUT_RATE = 24_000
        private const val SETUP_TIMEOUT_MS = 15_000L

        /** The first message: model, voice, instructions, and transcripts both ways. */
        fun setupMessage(systemInstruction: String, voice: String, model: String = MODEL): JSONObject = JSONObject().put("setup", JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject()))

        fun audioMessage(pcm: ByteArray): String = JSONObject().put("realtimeInput", JSONObject()
            .put("audio", JSONObject().put("data", Base64.encodeToString(pcm, Base64.NO_WRAP)).put("mimeType", "audio/pcm;rate=$IN_RATE"))).toString()

        fun textMessage(text: String): String = JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString()

        /** Tells the server the microphone went quiet (muted), so it doesn't wait for more speech. */
        fun audioEndMessage(): String = JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString()

        /** Plain words for a socket that closed or failed, from Gemini's own reason where it gave one. */
        fun explain(code: Int?, reason: String?, fallback: String?): String {
            val r = reason.orEmpty()
            return when {
                r.contains("API key", true) -> "Gemini didn't accept your API key. Check it in Settings → Internet mode."
                r.contains("not found", true) || r.contains("not supported", true) -> "This Gemini key can't use the live voice model yet ($r)."
                r.contains("quota", true) || r.contains("exhausted", true) || code == 429 -> "Gemini's free quota is used up for now. Try again later."
                r.contains("permission", true) || code == 403 -> "This Gemini key isn't allowed to use live voice ($r)."
                r.isNotBlank() -> "Gemini closed the conversation: $r"
                !fallback.isNullOrBlank() -> "Couldn't reach Gemini: $fallback"
                else -> "Couldn't reach Gemini. Check your connection and try again."
            }
        }

        /** Reads one server message into events and audio. */
        fun parse(raw: String, onAudio: (ByteArray) -> Unit): List<LiveVoiceEvent> {
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
            val out = mutableListOf<LiveVoiceEvent>()
            if (o.has("setupComplete")) out += LiveVoiceEvent.Ready
            o.optJSONObject("serverContent")?.let { sc ->
                sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                    for (i in 0 until parts.length()) {
                        val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                        if (data.isNotEmpty()) onAudio(Base64.decode(data, Base64.DEFAULT))
                    }
                }
                sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let { out += LiveVoiceEvent.Heard(it) }
                sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let { out += LiveVoiceEvent.Said(it) }
                if (sc.optBoolean("interrupted")) out += LiveVoiceEvent.Interrupted
                if (sc.optBoolean("turnComplete")) out += LiveVoiceEvent.TurnDone
            }
            o.optJSONObject("error")?.let { out += LiveVoiceEvent.Failed(explain(it.optInt("code"), it.optString("message"), "The conversation stopped.")) }
            return out
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<LiveVoiceEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<LiveVoiceEvent> = _events.asSharedFlow()
    private val _state = MutableStateFlow(LiveVoiceState.CONNECTING)
    val state: StateFlow<LiveVoiceState> = _state.asStateFlow()
    /** 0–1 loudness of whoever is talking, for the orb. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var micJob: Job? = null
    @Volatile private var muted = false
    @Volatile private var ready = false

    fun start() {
        val request = Request.Builder().url("$URL?key=${apiKey.trim()}").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setup.toString())
                scope.launch {
                    kotlinx.coroutines.delay(SETUP_TIMEOUT_MS)
                    if (!ready && _state.value == LiveVoiceState.CONNECTING) {
                        _state.value = LiveVoiceState.FAILED
                        _events.tryEmit(LiveVoiceEvent.Failed("Gemini didn't answer in time. Check your connection and try again."))
                        runCatching { webSocket.cancel() }
                    }
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (_state.value != LiveVoiceState.FAILED && _state.value != LiveVoiceState.ENDED) {
                    _state.value = LiveVoiceState.FAILED
                    _events.tryEmit(LiveVoiceEvent.Failed(explain(response?.code, response?.message, t.message)))
                }
                stopAudio()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val failed = code != 1000 && _state.value != LiveVoiceState.ENDED
                if (_state.value != LiveVoiceState.FAILED) _state.value = if (failed) LiveVoiceState.FAILED else LiveVoiceState.ENDED
                _events.tryEmit(if (failed) LiveVoiceEvent.Failed(explain(code, reason, "closed ($code)")) else LiveVoiceEvent.Closed)
                stopAudio()
            }
        })
    }

    private fun handle(text: String) {
        val events = parse(text) { pcm -> play(pcm) }
        events.forEach { e ->
            when (e) {
                LiveVoiceEvent.Ready -> { ready = true; startAudio(); _state.value = LiveVoiceState.LISTENING }
                LiveVoiceEvent.Interrupted -> { player?.pause(); player?.flush(); player?.play(); _state.value = LiveVoiceState.LISTENING }
                LiveVoiceEvent.TurnDone -> if (_state.value == LiveVoiceState.SPEAKING) _state.value = LiveVoiceState.LISTENING
                is LiveVoiceEvent.Failed -> _state.value = LiveVoiceState.FAILED
                else -> Unit
            }
            _events.tryEmit(e)
        }
    }

    /** Sends typed words (a prayer list, or a thought) into the conversation. */
    fun say(text: String) { if (ready) socket?.send(textMessage(text)) }

    fun setMuted(value: Boolean) {
        if (value && !muted && ready) socket?.send(audioEndMessage())
        muted = value; _state.value = if (value) LiveVoiceState.PAUSED else LiveVoiceState.LISTENING }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        val outMin = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        player = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(OUT_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(outMin, OUT_RATE))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().also { it.play() }

        val inMin = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Voice-communication input gets the phone's echo cancellation, so the companion
        // doesn't hear itself through the speaker.
        val rec = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(inMin, IN_RATE / 5 * 2)) }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            _events.tryEmit(LiveVoiceEvent.Failed("The microphone isn't available.")); return
        }
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
        recorder = rec
        rec.startRecording()
        micJob = scope.launch {
            val buffer = ByteArray(IN_RATE / 10 * 2) // 100 ms
            while (isActive) {
                val n = rec.read(buffer, 0, buffer.size)
                if (n <= 0) continue
                if (_state.value != LiveVoiceState.SPEAKING) _level.value = rms(buffer, n)
                if (!muted) socket?.send(audioMessage(buffer.copyOf(n)))
            }
        }
    }

    private fun play(pcm: ByteArray) {
        _state.value = LiveVoiceState.SPEAKING
        _level.value = rms(pcm, pcm.size)
        player?.write(pcm, 0, pcm.size)
    }

    private fun rms(bytes: ByteArray, n: Int): Float {
        val b = ByteBuffer.wrap(bytes, 0, n).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        var count = 0
        while (b.remaining() >= 2) { val s = b.short / 32768.0; sum += s * s; count++ }
        return if (count == 0) 0f else (kotlin.math.sqrt(sum / count) * 4).toFloat().coerceIn(0f, 1f)
    }

    private fun stopAudio() {
        micJob?.cancel()
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null
        runCatching { player?.stop() }; runCatching { player?.release() }; player = null
    }

    fun end() {
        _state.value = LiveVoiceState.ENDED
        runCatching { socket?.close(1000, "Amen") }
        stopAudio()
        _state.value = LiveVoiceState.ENDED
        scope.cancel()
    }
}
