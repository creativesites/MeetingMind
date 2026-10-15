package com.craftflowtechnologies.meetingmind.ai.live

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
    /** The server will close this connection soon (`goAway`); a successor is opened first. */
    data class GoAway(val timeLeftMs: Long?) : LiveVoiceEvent
    /** A `sessionResumptionUpdate`: [handle] can restart the conversation where it is, when [resumable]. */
    data class ResumptionHandle(val handle: String?, val resumable: Boolean) : LiveVoiceEvent
    /** The connection was replaced or restored; the conversation carried on. */
    data object Resumed : LiveVoiceEvent
}

/** THINKING: the person finished and the reply hasn't started. RECONNECTING: restoring the link, never "failed". */
enum class LiveVoiceState { CONNECTING, LISTENING, THINKING, SPEAKING, PAUSED, RECONNECTING, ENDED, FAILED }

/**
 * A live, spoken conversation with Gemini (Live API, `gemini-3.8-live`): the microphone streams up
 * as 16 kHz PCM, the companion's voice streams back as 24 kHz PCM and plays as it arrives, and
 * both sides are transcribed. Used for "Pray with me" and "Talk it through" (PLAN_V2 F7).
 *
 * The key is passed in from [com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore] for this session only.
 */
class GeminiLiveVoice(
    private val apiKey: String,
    private val setup: JSONObject,
    /** For the phone's call audio mode (echo cancellation); null in tests. */
    private val context: android.content.Context? = null,
    // No WebSocket pings: the Live endpoint doesn't answer them, and OkHttp would drop the
    // conversation after one missed pong. A setup timeout guards the start instead.
    private val client: OkHttpClient = com.craftflowtechnologies.meetingmind.core.net.Net.base.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
) {
    companion object {
        const val MODEL = "gemini-3.8-live"
        private const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val IN_RATE = 16_000
        private const val OUT_RATE = 24_000
        private const val SETUP_TIMEOUT_MS = 20_000L
        /** Echo can linger in the room for a moment after the voice stops. */
        private const val ECHO_TAIL_MS = 250L
        /** How long after the person goes quiet, with no reply yet, before the screen says "thinking". */
        private const val THINKING_AFTER_MS = 900L

        /**
         * Checks a key end to end without the microphone: opens the live socket, sends setup and
         * waits for Gemini to confirm. Returns null when it works, or what went wrong.
         */
        suspend fun probe(apiKey: String, model: String = MODEL, timeoutMs: Long = 20_000L): String? {
            val result = kotlinx.coroutines.CompletableDeferred<String?>()
            val client = com.craftflowtechnologies.meetingmind.core.net.Net.base.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
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
            // Connections last ~10 minutes and audio sessions are capped: resumption lets a new
            // connection pick the conversation up, and a sliding window keeps the context (and
            // the cap) from running out in a long prayer.
            .put("sessionResumption", JSONObject())
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
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

        /** The same setup, asking the server to resume the conversation held by [handle]. */
        fun withResumption(setup: JSONObject, handle: String?): JSONObject {
            val copy = JSONObject(setup.toString())
            if (!handle.isNullOrEmpty()) copy.getJSONObject("setup").put("sessionResumption", JSONObject().put("handle", handle))
            return copy
        }

        /** `timeLeft` arrives as a protobuf Duration string such as "50s" or "0.5s". */
        fun parseDurationMs(raw: String?): Long? {
            val t = raw?.trim()?.removeSuffix("s")?.toDoubleOrNull() ?: return null
            return (t * 1000).toLong()
        }

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
            o.optJSONObject("goAway")?.let { out += LiveVoiceEvent.GoAway(parseDurationMs(it.opt("timeLeft")?.toString())) }
            o.optJSONObject("sessionResumptionUpdate")?.let {
                out += LiveVoiceEvent.ResumptionHandle(it.optString("newHandle").takeIf { h -> h.isNotEmpty() }, it.optBoolean("resumable"))
            }
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

    /** True while audio goes to a wired, USB or Bluetooth headset rather than the phone's loudspeaker. */
    private val _headset = MutableStateFlow(false)
    val headset: StateFlow<Boolean> = _headset.asStateFlow()

    private val lock = Any()
    /** The socket carrying the conversation; a successor waits in [candidate] until Gemini confirms its setup. */
    @Volatile private var active: WebSocket? = null
    private var candidate: WebSocket? = null
    private val machine = ReconnectMachine()
    private var reconnectJob: Job? = null
    @Volatile private var userEnded = false
    @Volatile private var linkUp = false
    @Volatile private var audioStarted = false
    private val startedAt = System.currentTimeMillis()

    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var micJob: Job? = null
    private var playJob: Job? = null
    private var watchJob: Job? = null
    @Volatile private var muted = false
    @Volatile private var focusPaused = false
    @Volatile private var opened = false
    /** null until the person chooses; then it follows the audio route (headset → voice, speaker → calm). */
    @Volatile private var savedVoiceInterrupt: Boolean? = null
    @Volatile private var lastHeardAt = 0L
    @Volatile private var lastOutputAt = 0L
    /** After "tap to interrupt", audio of the interrupted turn still in flight is dropped. */
    @Volatile private var dropUntilTurnEnds = false

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
    private var focusRequest: Any? = null
    private var deviceCallback: android.media.AudioDeviceCallback? = null

    /** Timestamped line in the Gemini log, so a real device shows why a conversation ended. */
    private fun log(message: String) {
        val line = "Live +${(System.currentTimeMillis() - startedAt) / 1000}s: $message"
        runCatching {
            context?.let { com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.attach(it) }
            com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.add(line)
        }
    }

    fun start() {
        log("starting")
        openSocket(resumeHandle = null)
    }

    /** Opens a connection. The first one, a successor after `goAway`, and each retry all come through here. */
    private fun openSocket(resumeHandle: String?) {
        if (userEnded) return
        val request = Request.Builder().url("$URL?key=${apiKey.trim()}").build()
        val message = withResumption(setup, resumeHandle)
        val ws: WebSocket
        // Callbacks check which socket they belong to under this lock, so the reference must be set first.
        synchronized(lock) {
            ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened = true
                    if (!audioStarted) _stage.value = "Connected — starting the voice…"
                    log("socket open${if (resumeHandle != null) " (resuming)" else ""}")
                    webSocket.send(message.toString())
                }
                override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, text)
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(webSocket, bytes.utf8())
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    log("socket failure: ${t.javaClass.simpleName} ${t.message} http=${response?.code}")
                    dropped(webSocket, response?.code, response?.message ?: t.message, explain(response?.code, response?.message, t.message))
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    log("server closing: code=$code reason='$reason'")
                    if (webSocket === active) linkUp = false
                    webSocket.close(1000, null)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    log("socket closed: code=$code reason='$reason'")
                    dropped(webSocket, code, reason, explain(code, reason, "closed ($code)"))
                }
            })
            candidate = ws
        }
        // One clock per connection attempt, so a stall ends in a clear message rather than waiting forever.
        scope.launch {
            kotlinx.coroutines.delay(SETUP_TIMEOUT_MS)
            if (synchronized(lock) { ws === candidate } && !userEnded) {
                log("setup timed out (opened=$opened, stage='${_stage.value}')")
                runCatching { ws.cancel() }
                if (!audioStarted) {
                    failWith(
                        if (opened) "Gemini connected but didn't start the voice session. The live model may not be available for this key — try Settings → Check Gemini key."
                        else "Couldn't reach Gemini (${_stage.value}). Check your connection and try again."
                    )
                } else dropped(ws, 0, "setup timeout", "Couldn't reach Gemini.")
            }
        }
    }

    /** A socket closed or failed. Anything but the person ending the session is answered by reconnecting. */
    private fun dropped(ws: WebSocket, code: Int?, reason: String?, explained: String) {
        val wasActive: Boolean
        synchronized(lock) {
            when {
                ws === candidate -> { candidate = null; wasActive = false }
                ws === active -> { active = null; linkUp = false; wasActive = true }
                else -> return
            }
        }
        if (userEnded || _state.value == LiveVoiceState.ENDED || _state.value == LiveVoiceState.FAILED) return
        when (val d = machine.onDropped(code, reason)) {
            ReconnectMachine.Decision.Ignore -> Unit
            is ReconnectMachine.Decision.GiveUp -> failWith(if (audioStarted) "$explained ${d.reason}" else explained)
            is ReconnectMachine.Decision.Reconnect -> {
                log("reconnecting: attempt ${d.attempt}, in ${d.delayMs}ms, ${if (d.handle != null) "with" else "without"} handle")
                if (active == null) _state.value = LiveVoiceState.RECONNECTING
                scheduleReconnect(d)
            }
        }
    }

    private fun scheduleReconnect(d: ReconnectMachine.Decision.Reconnect) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            if (d.delayMs > 0) kotlinx.coroutines.delay(d.delayMs)
            openSocket(d.handle)
        }
    }

    /** The conversation really cannot continue: say so once and stop. */
    private fun failWith(message: String) {
        if (_state.value == LiveVoiceState.FAILED || _state.value == LiveVoiceState.ENDED) return
        log("giving up: $message")
        _state.value = LiveVoiceState.FAILED
        _events.tryEmit(LiveVoiceEvent.Failed(message))
        synchronized(lock) { candidate?.let { c -> runCatching { c.cancel() } }; candidate = null; active?.let { a -> runCatching { a.cancel() } }; active = null; linkUp = false }
        stopAudio()
    }

    /** Gemini confirmed a connection's setup: it takes over the conversation, and the old one is let go. */
    private fun promote(ws: WebSocket) {
        val old: WebSocket?
        synchronized(lock) {
            if (ws !== candidate) return
            old = active; active = ws; candidate = null
        }
        machine.onReady()
        linkUp = true
        if (old != null && old !== ws) runCatching { old.close(1000, "Resumed") }
        if (!audioStarted) {
            audioStarted = true
            ready = true
            startAudio()
            _state.value = LiveVoiceState.LISTENING
            _events.tryEmit(LiveVoiceEvent.Ready)
        } else {
            log("connection restored")
            val st = _state.value
            if (st == LiveVoiceState.RECONNECTING || st == LiveVoiceState.CONNECTING) _state.value = if (muted || focusPaused) LiveVoiceState.PAUSED else LiveVoiceState.LISTENING
            _events.tryEmit(LiveVoiceEvent.Resumed)
        }
    }
    @Volatile private var ready = false

    private fun handle(ws: WebSocket, text: String) {
        val fromActive: Boolean
        synchronized(lock) {
            if (ws !== active && ws !== candidate) return
            fromActive = ws === active
        }
        val events = parse(text) { pcm -> if (fromActive && !dropUntilTurnEnds) { turnDone = false; lastOutputAt = System.currentTimeMillis(); queue.trySend(pcm) } }
        events.forEach { e ->
            when (e) {
                LiveVoiceEvent.Ready -> { promote(ws); return@forEach }
                is LiveVoiceEvent.GoAway -> {
                    log("goAway: timeLeft=${e.timeLeftMs}ms, handle ${if (machine.handle != null) "stored" else "none"}")
                    val d = machine.onGoAway()
                    if (d is ReconnectMachine.Decision.Reconnect) scheduleReconnect(d)
                }
                is LiveVoiceEvent.ResumptionHandle -> {
                    log("resumption update: resumable=${e.resumable}, handle ${if (e.handle != null) "present" else "absent"}")
                    machine.onHandle(e.handle, e.resumable)
                }
                else -> if (!fromActive) return@forEach
            }
            when (e) {
                LiveVoiceEvent.Interrupted -> { log("interrupted by server"); dropUntilTurnEnds = false; stopPlayback() }
                LiveVoiceEvent.TurnDone -> { turnDone = true; dropUntilTurnEnds = false }
                is LiveVoiceEvent.Heard -> lastHeardAt = System.currentTimeMillis()
                is LiveVoiceEvent.Failed -> { log("server error: ${e.message}"); _state.value = LiveVoiceState.FAILED }
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
        if (_state.value == LiveVoiceState.SPEAKING) _state.value = if (muted || focusPaused) LiveVoiceState.PAUSED else LiveVoiceState.LISTENING
    }

    /**
     * Sends typed words into the conversation: whatever the companion was saying stops, and the
     * words go in as the person's turn.
     */
    fun say(text: String) {
        if (!ready || text.isBlank()) return
        stopPlayback()
        dropUntilTurnEnds = false
        if (linkUp) active?.send(textMessage(text.trim()))
    }

    /** "Interrupt": the person taps to take the floor (Calm mode, or any time). The mic opens at once. */
    fun interrupt() {
        log("interrupt tapped")
        dropUntilTurnEnds = !turnDone
        stopPlayback()
    }

    /** The person's choice for "Interrupt by voice" (null = follow the audio route). */
    fun setVoiceInterrupt(value: Boolean?) {
        savedVoiceInterrupt = value
        log("interrupt by voice: ${value ?: "auto"}")
    }

    fun voiceInterruptEnabled(): Boolean = InterruptPolicy.voiceInterrupt(savedVoiceInterrupt, _headset.value)

    fun setMuted(value: Boolean) {
        if (value && !muted && ready && linkUp) active?.send(audioEndMessage())
        muted = value
        // Unmuting is also the way back from a pause someone else's audio caused.
        if (!value) focusPaused = false
        if (_state.value != LiveVoiceState.SPEAKING && _state.value != LiveVoiceState.RECONNECTING) _state.value = if (value) LiveVoiceState.PAUSED else LiveVoiceState.LISTENING
    }

    /** Another app took the audio (a phone call): pause, and pick up again when it's given back. */
    private fun onAudioFocus(change: Int) {
        val lost = change == android.media.AudioManager.AUDIOFOCUS_LOSS || change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        log("audio focus change=$change")
        if (lost && !focusPaused) {
            focusPaused = true
            if (ready && linkUp) active?.send(audioEndMessage())
            stopPlayback()
            if (_state.value != LiveVoiceState.RECONNECTING) _state.value = LiveVoiceState.PAUSED
        } else if (change == android.media.AudioManager.AUDIOFOCUS_GAIN && focusPaused) {
            focusPaused = false
            if (_state.value == LiveVoiceState.PAUSED && !muted) _state.value = LiveVoiceState.LISTENING
        }
    }

    private fun requestFocus(am: android.media.AudioManager) {
        val listener = android.media.AudioManager.OnAudioFocusChangeListener { onAudioFocus(it) }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(listener).build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            focusRequest = listener
            @Suppress("DEPRECATION")
            am.requestAudioFocus(listener, android.media.AudioManager.STREAM_VOICE_CALL, android.media.AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonFocus(am: android.media.AudioManager) {
        val r = focusRequest ?: return
        focusRequest = null
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26 && r is android.media.AudioFocusRequest) am.abandonAudioFocusRequest(r)
            else if (r is android.media.AudioManager.OnAudioFocusChangeListener) {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(r)
            }
        }
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
                runCatching { requestFocus(it) }
                runCatching { routeToBestOutput(it) }
                // A headset plugged in or taken off mid-prayer re-routes and changes the interruption default.
                val cb = object : android.media.AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) { runCatching { routeToBestOutput(it) } }
                    override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) { runCatching { routeToBestOutput(it) } }
                }
                deviceCallback = cb
                runCatching { it.registerAudioDeviceCallback(cb, null) }
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
                val now = System.currentTimeMillis()
                if (speaking && (st == LiveVoiceState.LISTENING || st == LiveVoiceState.PAUSED || st == LiveVoiceState.THINKING) && !focusPaused) _state.value = LiveVoiceState.SPEAKING
                if (!speaking && st == LiveVoiceState.SPEAKING && (turnDone || queue.isEmpty)) {
                    quietSince = now
                    _state.value = if (muted || focusPaused) LiveVoiceState.PAUSED else LiveVoiceState.LISTENING
                }
                // The person has finished and nothing is coming back yet: the companion is thinking.
                val waiting = lastHeardAt > 0 && lastOutputAt < lastHeardAt && now - lastHeardAt > THINKING_AFTER_MS && _userLevel.value < 0.05f
                if (st == LiveVoiceState.LISTENING && waiting && !speaking) _state.value = LiveVoiceState.THINKING
                if (st == LiveVoiceState.THINKING && (!waiting || turnDone && lastOutputAt >= lastHeardAt)) _state.value = LiveVoiceState.LISTENING
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
                if (muted || focusPaused || !linkUp) continue
                // While the companion is audible (and a moment after), the mic stays closed. With
                // "Interrupt by voice" a sustained voice clearly louder than leftover echo opens it
                // (and stops the companion); in Calm mode it stays closed until the person taps.
                val companionAudible = audible || System.currentTimeMillis() - quietSince < ECHO_TAIL_MS
                val voice = voiceInterruptEnabled()
                val gateOpen = gate.decide(level, companionAudible)
                val open = InterruptPolicy.micOpen(voice, companionAudible, gateOpen)
                if (voice && open && companionAudible && audible) stopPlayback()
                active?.send(audioMessage(if (open) buffer.copyOf(n) else silence.copyOf(n)))
            }
        }
    }

    /** A headset if one's connected (wired, USB or Bluetooth), otherwise the loudspeaker (call audio defaults to the earpiece). */
    private fun routeToBestOutput(am: android.media.AudioManager) {
        val wasHeadset = _headset.value
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val devices = am.availableCommunicationDevices
            val headset = devices.firstOrNull { d ->
                d.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || d.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    d.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                    d.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            val target = headset ?: devices.firstOrNull { d -> d.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (target != null) am.setCommunicationDevice(target)
            _headset.value = headset != null
        } else {
            val outs = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            val bluetooth = outs.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            val wired = outs.any { it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET }
            @Suppress("DEPRECATION")
            if (bluetooth && am.isBluetoothScoAvailableOffCall) { am.startBluetoothSco(); am.isBluetoothScoOn = true; am.isSpeakerphoneOn = false }
            else am.isSpeakerphoneOn = !wired
            _headset.value = bluetooth || wired
        }
        if (wasHeadset != _headset.value) log("audio route: ${if (_headset.value) "headset" else "loudspeaker"}")
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
            deviceCallback?.let { cb -> runCatching { am.unregisterAudioDeviceCallback(cb) } }
            deviceCallback = null
            abandonFocus(am)
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
                else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                    @Suppress("DEPRECATION")
                    if (am.isBluetoothScoOn) { am.stopBluetoothSco(); am.isBluetoothScoOn = false }
                }
            }
            runCatching { am.mode = previousMode }
        }
        audioManager = null
    }

    fun end() {
        log("ended by the person")
        userEnded = true
        machine.onUserEnd()
        reconnectJob?.cancel()
        _state.value = LiveVoiceState.ENDED
        synchronized(lock) {
            candidate?.let { c -> runCatching { c.close(1000, "Amen") } }; candidate = null
            active?.let { a -> runCatching { a.close(1000, "Amen") } }; active = null
            linkUp = false
        }
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
