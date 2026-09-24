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
    /** For the phone's call audio mode (echo cancellation); null in tests. */
    private val context: android.content.Context? = null,
    // No WebSocket pings: the Live endpoint doesn't answer them, and OkHttp would drop the
    // conversation after one missed pong. A setup timeout guards the start instead.
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
) {
    companion object {
        const val MODEL = "gemini-3.8-live"
        private const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val IN_RATE = 16_000
        private const val OUT_RATE = 24_000
        private const val SETUP_TIMEOUT_MS = 20_000L
        /** Echo can linger in the room for a moment after the voice stops. */
        private const val ECHO_TAIL_MS = 250L

        /**
         * Checks a key end to end without the microphone: opens the live socket, sends setup and
         * waits for Gemini to confirm. Returns null when it works, or what went wrong.
         */
        suspend fun probe(apiKey: String, model: String = MODEL, timeoutMs: Long = 20_000L): String? {
            val result = kotlinx.coroutines.CompletableDeferred<String?>()
            val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
            var opened = false
            val ws = client.newWebSocket(Request.Builder().url("$URL?key=${apiKey.trim()}").build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened = true
                    webSocket.send(setupMessage("Reply briefly.", "Sulafat", model).toString())
                }
                override fun onMessage(webSocket: WebSocket, text: String) { check(text) }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) { check(bytes.utf8()) }
                private fun check(raw: String) {
                    val events = parse(raw) {}
                    if (LiveVoiceEvent.Ready in events) result.complete(null)
                    events.filterIsInstance<LiveVoiceEvent.Failed>().firstOrNull()?.let { result.complete(it.message) }
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { result.complete(explain(code, reason, "closed ($code)")) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { result.complete(explain(response?.code, response?.message, t.message)) }
            })
            val answer = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { result.await() }
                ?: if (opened) "Connected, but Gemini didn't start a live session in time." else "Couldn't reach Gemini's live service."
            runCatching { ws.close(1000, null) }
            client.dispatcher.executorService.shutdown()
            return answer
        }

        /** The first message: model, voice, instructions, and transcripts both ways. */
        fun setupMessage(systemInstruction: String, voice: String, model: String = MODEL): JSONObject = JSONObject().put("setup", JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            // Prayer has pauses: don't end someone's turn at the first breath, and don't take a
            // cough or a bit of echo as them starting to speak.
            .put("realtimeInputConfig", JSONObject().put("automaticActivityDetection", JSONObject()
                .put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                .put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW")
                .put("prefixPaddingMs", 200)
                .put("silenceDurationMs", 1100))))

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
                // Pieces can be a lone space — that space is what separates two words, so keep it.
                sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { out += LiveVoiceEvent.Heard(it) }
                sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { out += LiveVoiceEvent.Said(it) }
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
    /** 0–1 loudness of the person's own voice, even while the companion talks (for the ring). */
    private val _userLevel = MutableStateFlow(0f)
    val userLevel: StateFlow<Float> = _userLevel.asStateFlow()

    /** Where the connection is, in words, while it's getting ready — so a stall says where. */
    private val _stage = MutableStateFlow("Connecting to Gemini…")
    val stage: StateFlow<String> = _stage.asStateFlow()

    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var micJob: Job? = null
    private var playJob: Job? = null
    private var watchJob: Job? = null
    @Volatile private var muted = false
    @Volatile private var ready = false
    @Volatile private var opened = false

    // Playback: audio arrives faster than it plays. It's queued and written by its own coroutine
    // (never on the socket's thread), and what's *audible* is tracked from the play head — so the
    // screen says "speaking" exactly while the voice is heard, and the mic knows when it's safe.
    private val queue = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    @Volatile private var framesWritten = 0L
    @Volatile private var turnDone = true
    @Volatile private var quietSince = 0L
    private val audible: Boolean get() {
        val p = player ?: return false
        val head = runCatching { p.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrDefault(framesWritten)
        return framesWritten - head > OUT_RATE / 50 || !queue.isEmpty
    }

    private var audioManager: android.media.AudioManager? = null
    private var previousMode = android.media.AudioManager.MODE_NORMAL

    fun start() {
        val request = Request.Builder().url("$URL?key=${apiKey.trim()}").build()
        // One clock for the whole start — reaching Google, the handshake, and setup — so a stall
        // anywhere ends in a clear message rather than "Getting ready…" forever.
        scope.launch {
            kotlinx.coroutines.delay(SETUP_TIMEOUT_MS)
            if (!ready && _state.value == LiveVoiceState.CONNECTING) {
                val where = _stage.value
                _state.value = LiveVoiceState.FAILED
                _events.tryEmit(LiveVoiceEvent.Failed(
                    if (opened) "Gemini connected but didn't start the voice session. The live model may not be available for this key — try Settings → Check Gemini key."
                    else "Couldn't reach Gemini ($where). Check your connection and try again."
                ))
                runCatching { socket?.cancel() }
            }
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened = true
                _stage.value = "Connected — starting the voice…"
                webSocket.send(setup.toString())
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
        val events = parse(text) { pcm -> turnDone = false; queue.trySend(pcm) }
        events.forEach { e ->
            when (e) {
                LiveVoiceEvent.Ready -> { ready = true; startAudio(); _state.value = LiveVoiceState.LISTENING }
                LiveVoiceEvent.Interrupted -> stopPlayback()
                LiveVoiceEvent.TurnDone -> turnDone = true
                is LiveVoiceEvent.Failed -> _state.value = LiveVoiceState.FAILED
                else -> Unit
            }
            _events.tryEmit(e)
        }
    }

    /** Drops whatever is still queued or playing — they've started speaking, or typed. */
    private fun stopPlayback() {
        while (queue.tryReceive().isSuccess) Unit
        player?.let { p -> runCatching { p.pause(); p.flush(); p.play() } }
        framesWritten = 0
        runCatching { player?.let { framesWritten = it.playbackHeadPosition.toLong() and 0xFFFFFFFFL } }
        if (_state.value == LiveVoiceState.SPEAKING) _state.value = if (muted) LiveVoiceState.PAUSED else LiveVoiceState.LISTENING
    }

    /**
     * Sends typed words into the conversation: whatever the companion was saying stops, and the
     * words go in as the person's turn.
     */
    fun say(text: String) {
        if (!ready || text.isBlank()) return
        stopPlayback()
        socket?.send(textMessage(text.trim()))
    }

    fun setMuted(value: Boolean) {
        if (value && !muted && ready) socket?.send(audioEndMessage())
        muted = value
        if (_state.value != LiveVoiceState.SPEAKING) _state.value = if (value) LiveVoiceState.PAUSED else LiveVoiceState.LISTENING
    }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        // Call audio: the platform's echo canceller only removes what's played on the voice-call
        // path. Played as media, the companion hears itself through the mic and answers itself.
        context?.let { ctx ->
            val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager = am
            am?.let {
                previousMode = it.mode
                runCatching { it.mode = android.media.AudioManager.MODE_IN_COMMUNICATION }
                runCatching { routeToSpeaker(it) }
            }
        }

        val inMin = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(inMin, IN_RATE / 5 * 2)) }.getOrNull()
        val outMin = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        player = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(OUT_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(outMin, OUT_RATE))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .apply { if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) setSessionId(rec.audioSessionId) }
            .build().also { it.play() }
        framesWritten = 0

        playJob = scope.launch {
            for (pcm in queue) {
                val p = player ?: break
                _level.value = rms(pcm, pcm.size)
                var off = 0
                while (off < pcm.size && isActive) {
                    val n = p.write(pcm, off, pcm.size - off)
                    if (n <= 0) break
                    off += n
                    framesWritten += n / 2
                }
            }
        }
        // What the screen shows follows what's heard, not what's arrived.
        watchJob = scope.launch {
            while (isActive) {
                val speaking = audible
                val st = _state.value
                if (speaking && (st == LiveVoiceState.LISTENING || st == LiveVoiceState.PAUSED)) _state.value = LiveVoiceState.SPEAKING
                if (!speaking && st == LiveVoiceState.SPEAKING && (turnDone || queue.isEmpty)) {
                    quietSince = System.currentTimeMillis()
                    _state.value = if (muted) LiveVoiceState.PAUSED else LiveVoiceState.LISTENING
                }
                if (!speaking) _level.value = _level.value * 0.8f
                kotlinx.coroutines.delay(40)
            }
        }

        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            _events.tryEmit(LiveVoiceEvent.Failed("The microphone isn't available. Allow microphone access for MeetingMind, or type instead."))
            return
        }
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
        runCatching { if (android.media.audiofx.AutomaticGainControl.isAvailable()) android.media.audiofx.AutomaticGainControl.create(rec.audioSessionId)?.enabled = true }
        recorder = rec
        rec.startRecording()
        micJob = scope.launch {
            val buffer = ByteArray(IN_RATE / 10 * 2) // 100 ms
            val silence = ByteArray(buffer.size)
            val gate = BargeIn()
            while (isActive) {
                val n = rec.read(buffer, 0, buffer.size)
                if (n <= 0) continue
                val level = rms(buffer, n)
                _userLevel.value = level
                if (_state.value != LiveVoiceState.SPEAKING) _level.value = level
                if (muted) continue
                // While the companion is audible (and a moment after), the mic stays closed unless
                // the person clearly starts talking — then it stops and listens to them.
                val companionAudible = audible || System.currentTimeMillis() - quietSince < ECHO_TAIL_MS
                val open = gate.decide(level, companionAudible)
                if (open && companionAudible && audible) stopPlayback()
                socket?.send(audioMessage(if (open) buffer.copyOf(n) else silence.copyOf(n)))
            }
        }
    }

    /** A headset if one's connected, otherwise the loudspeaker (call audio defaults to the earpiece). */
    private fun routeToSpeaker(am: android.media.AudioManager) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val devices = am.availableCommunicationDevices
            val headset = devices.firstOrNull { d ->
                d.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || d.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    d.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET || d.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            val target = headset ?: devices.firstOrNull { d -> d.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (target != null) am.setCommunicationDevice(target)
        } else {
            @Suppress("DEPRECATION")
            if (!am.isWiredHeadsetOn && !am.isBluetoothScoOn) am.isSpeakerphoneOn = true
        }
    }

    private fun rms(bytes: ByteArray, n: Int): Float {
        val b = ByteBuffer.wrap(bytes, 0, n).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        var count = 0
        while (b.remaining() >= 2) { val s = b.short / 32768.0; sum += s * s; count++ }
        return if (count == 0) 0f else (kotlin.math.sqrt(sum / count) * 4).toFloat().coerceIn(0f, 1f)
    }

    private fun stopAudio() {
        micJob?.cancel(); playJob?.cancel(); watchJob?.cancel()
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null
        runCatching { player?.stop() }; runCatching { player?.release() }; player = null
        audioManager?.let { am ->
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
                else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                }
            }
            runCatching { am.mode = previousMode }
        }
        audioManager = null
    }

    fun end() {
        _state.value = LiveVoiceState.ENDED
        runCatching { socket?.close(1000, "Amen") }
        stopAudio()
        scope.cancel()
    }
}

/**
 * Decides, every 100 ms of microphone, whether to pass the person's voice on. While the companion
 * is quiet everything goes through. While it's audible, only a sustained voice clearly louder than
 * leftover echo does (about a quarter of a second) — that's someone starting to speak, and it
 * interrupts the companion. Without this, the companion hears itself and answers itself.
 */
class BargeIn(private val threshold: Float = 0.16f, private val framesNeeded: Int = 3) {
    private var loud = 0
    /** Opened by the person talking over the companion (not just left open from before it spoke). */
    private var bargedIn = false

    fun decide(level: Float, companionAudible: Boolean): Boolean {
        if (!companionAudible) { loud = 0; bargedIn = false; return true }
        if (bargedIn && level > threshold * 0.6f) return true
        bargedIn = false
        loud = if (level > threshold) loud + 1 else 0
        if (loud >= framesNeeded) { bargedIn = true; loud = 0 }
        return bargedIn
    }
}
