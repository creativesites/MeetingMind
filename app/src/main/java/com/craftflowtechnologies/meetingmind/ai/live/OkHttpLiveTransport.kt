package com.craftflowtechnologies.meetingmind.ai.live

import android.util.Base64
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.FeatureGate
import com.craftflowtechnologies.meetingmind.core.billing.TieringMode
import com.craftflowtechnologies.meetingmind.core.billing.TieringModeStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real bidirectional Gemini Live WebSocket transport (`BidiGenerateContent`) for [LiveSessionController].
 *
 * Streams microphone PCM upstream, receives server audio + transcriptions + native `toolCall`
 * frames downstream, and forwards `toolResponse` results back over the active socket.
 */
class OkHttpLiveTransport(
    private val credentials: GeminiCredentialStore,
    private val toolsJson: String? = null,
    private val voiceName: String = "Aoede",
    private val endpointUrl: String = DEFAULT_WS_URL,
    private val onOutputAudioPcm24k: (ByteArray) -> Unit = {},
    private val onInterrupted: () -> Unit = {},
    private val client: OkHttpClient = com.craftflowtechnologies.meetingmind.core.net.Net.base.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
) : LiveTransport {

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var usesExtendedThinking: Boolean = false

    private suspend fun resolveDirectApiKey(): String? {
        val tierMode = TieringModeStore.current(credentials.appContext)
        val canUseOwn = tierMode == TieringMode.CLOSED_TESTING || FeatureGate.can(Capability.BYOK_KEY)
        if (canUseOwn) {
            credentials.getUserKey()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return GeminiCredentialStore.systemKey?.takeIf { it.isNotBlank() }
    }

    override fun isConfigured(): Boolean = runBlocking {
        resolveDirectApiKey() != null
    }

    override suspend fun connect(modelId: String, systemInstruction: String): AiResult<Flow<LiveServerEvent>> {
        val apiKey = resolveDirectApiKey()
            ?: return AiResult.ModelUnavailable(modelId, "Live voice requires a personal Gemini API key in Settings.")
        usesExtendedThinking = modelId.contains("extended", ignoreCase = true)

        val setupJson = buildSetupJson(modelId, systemInstruction)
        val flow = callbackFlow {
            val sep = if ('?' in endpointUrl) '&' else '?'
            val req = Request.Builder().url("$endpointUrl${sep}key=${apiKey.trim()}").build()
            val ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(setupJson.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseServerMessage(text).forEach { trySend(it) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    trySend(LiveServerEvent.Disconnected)
                    close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    trySend(LiveServerEvent.Error(GeminiLiveVoice.explain(response?.code, t.message, "Live connection dropped.")))
                    trySend(LiveServerEvent.Disconnected)
                    close()
                }
            })
            socket = ws
            awaitClose {
                ws.close(1000, "closed")
                if (socket === ws) socket = null
            }
        }
        return AiResult.Success(flow)
    }

    override suspend fun send(command: LiveClientCommand) {
        val ws = socket ?: return
        val payload = when (command) {
            is LiveClientCommand.SendAudio -> GeminiLiveVoice.audioMessage(command.pcm16)
            is LiveClientCommand.SendText -> GeminiLiveVoice.textMessage(command.text)
            is LiveClientCommand.EndAudioStream -> GeminiLiveVoice.audioEndMessage()
            is LiveClientCommand.SendToolResult -> {
                val respObj = runCatching { JSONObject(command.result.resultJson) }
                    .getOrElse { JSONObject().put("result", command.result.resultJson) }
                val fnResp = JSONObject()
                    .put("id", command.result.callId)
                    .put("response", respObj)
                JSONObject().put(
                    "toolResponse",
                    JSONObject().put("functionResponses", JSONArray().put(fnResp))
                ).toString()
            }
        }
        ws.send(payload)
    }

    override suspend fun close() {
        socket?.close(1000, "ended")
        socket = null
    }

    internal fun buildSetupJson(modelId: String, systemInstruction: String): JSONObject {
        val setup = JSONObject()
            .put("model", if (modelId.startsWith("models/")) modelId else "models/$modelId")
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
                        )
                    )
            )
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        if (systemInstruction.isNotBlank()) {
            setup.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
        }
        toolsJson?.let { runCatching { JSONArray(it) }.getOrNull() }?.let { setup.put("tools", it) }
        return JSONObject().put("setup", setup)
    }

    internal fun parseServerMessage(raw: String): List<LiveServerEvent> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val events = mutableListOf<LiveServerEvent>()
        if (root.has("setupComplete")) events += LiveServerEvent.Connected

        root.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val c = calls.optJSONObject(i) ?: continue
                val name = c.optString("name").trim()
                if (name.isEmpty()) continue
                val id = c.optString("id").ifBlank { "live_call_$i" }
                val argsJson = c.optJSONObject("args")?.toString() ?: "{}"
                events += LiveServerEvent.ToolCallRequested(LiveToolCall(id = id, name = name, argumentsJson = argsJson))
            }
        }

        root.optJSONObject("serverContent")?.let { sc ->
            if (sc.optBoolean("interrupted")) {
                onInterrupted()
            }
            sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let {
                events += LiveServerEvent.InputTranscription(it, isFinal = true)
            }
            val spokenText = sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }
            var hadAudio = false
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                    if (data.isNotEmpty()) {
                        hadAudio = true
                        onOutputAudioPcm24k(Base64.decode(data, Base64.DEFAULT))
                    }
                }
            }
            if (hadAudio || spokenText != null) {
                events += LiveServerEvent.OutputAudio(spokenText)
            }
            if (sc.optBoolean("turnComplete")) {
                events += LiveServerEvent.TurnComplete
                if (!usesExtendedThinking && !root.has("interactionStatus")) {
                    events += LiveServerEvent.InteractionStatusChanged(InteractionStatus.IDLE)
                }
            }
        }

        root.optJSONObject("interactionStatus")?.optString("status")?.let { status ->
            when (status.uppercase()) {
                "IN_PROGRESS" -> events += LiveServerEvent.InteractionStatusChanged(InteractionStatus.IN_PROGRESS)
                "IDLE" -> events += LiveServerEvent.InteractionStatusChanged(InteractionStatus.IDLE)
            }
        }

        root.optJSONObject("error")?.let { e ->
            events += LiveServerEvent.Error(GeminiLiveVoice.explain(e.optInt("code"), e.optString("message"), "Live session error."))
        }
        return events
    }

    companion object {
        const val DEFAULT_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}
