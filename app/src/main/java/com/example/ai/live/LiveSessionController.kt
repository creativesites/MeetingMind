package com.example.ai.live

import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.core.model.ProcessingProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A live session's transport. Separated from [LiveSessionController] for the same reason
 * [com.example.ai.cloud.GeminiTransport] is separated from the transcription engine: it is the only
 * thing that touches the network, and it will be replaced when sessions are brokered by
 * MeetingMind's own backend instead of opened directly from the device.
 */
interface LiveTransport {
    /** Opens a session. The returned flow emits until the session closes. */
    suspend fun connect(modelId: String, systemInstruction: String): AiResult<Flow<LiveServerEvent>>
    suspend fun send(command: LiveClientCommand)
    suspend fun close()
    fun isConfigured(): Boolean
}

/** Runs one tool the model asked for. Implementations must be safe to call concurrently. */
fun interface LiveToolExecutor {
    suspend fun execute(call: LiveToolCall): LiveToolResult
}

/**
 * Drives a Gemini Live session's state.
 *
 * ### The hazard this class exists to handle
 *
 * A simple Live client treats `turnComplete` as "the model is done": it stops listening for server
 * events, re-enables the microphone, and shows an idle UI. With
 * `gemini-3.8-live-extended-thinking` that is wrong, and wrong in a way that is hard to notice in
 * testing — the model emits `turnComplete` when it has finished *speaking*, and then keeps
 * reasoning in the background, running tools and producing further output. A client that stopped
 * listening truncates it, and the user sees a model that answers half the question.
 *
 * So this controller never infers idleness. `turnComplete` moves it to
 * [LiveSessionState.BACKGROUND_REASONING] — still active, still listening — and only an explicit
 * [InteractionStatus.IDLE] from the server ends the interaction. The two signals are treated as
 * what they are: one is about output, the other is about the interaction.
 *
 * ### Tool calls do not block
 *
 * A tool request is dispatched into its own coroutine and the session keeps consuming events while
 * it runs. Several tools can be in flight at once; each result is sent back as it arrives. Awaiting
 * a tool inline would stall the event loop, which in a duplex audio session means dropping the
 * user's speech.
 *
 * ### Status
 *
 * The state machine and the tool lifecycle are complete and tested. **No [LiveTransport]
 * implementation ships**: a bidirectional streaming session against the real endpoint could not be
 * built or verified in an environment with no credential and no device, and a transport written
 * blind would be untested code that looks finished. See docs/TRANSCRIPTION_OVERHAUL.md §6.
 */
class LiveSessionController(
    private val transport: LiveTransport,
    private val scope: CoroutineScope,
    private val toolExecutor: LiveToolExecutor? = null,
    private val profile: ProcessingProfile = ProcessingProfile.LIVE
) {

    private val _state = MutableStateFlow(LiveSessionState.DISCONNECTED)
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow("")
    /** Live input transcription so far. Not a meeting transcript — see the class doc: live and
     * post-recording processing have different requirements and do not share a pipeline. */
    val inputTranscript: StateFlow<String> = _transcript.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Whether the server says this interaction is still active. Kept separate from [state] on
     * purpose: the UI state and the server's interaction status are different facts, and merging
     * them is exactly the bug this class avoids.
     */
    private var interactionActive = false

    private val inFlightTools = mutableSetOf<String>()
    private var eventJob: Job? = null

    suspend fun connect(systemInstruction: String = ""): AiResult<Unit> {
        if (!transport.isConfigured()) {
            _state.value = LiveSessionState.DISCONNECTED
            return AiResult.ModelUnavailable(
                modelId = modelId(),
                message = "Live mode isn't set up on this build."
            )
        }
        _state.value = LiveSessionState.CONNECTING
        return when (val result = transport.connect(modelId(), systemInstruction)) {
            is AiResult.Success -> {
                eventJob = scope.launch {
                    result.value.collect { onEvent(it) }
                }
                AiResult.Success(Unit)
            }
            else -> {
                _state.value = LiveSessionState.ERROR
                _lastError.value = result.describeFailure() ?: "Live session could not be opened."
                @Suppress("UNCHECKED_CAST")
                result as AiResult<Unit>
            }
        }
    }

    suspend fun startListening() {
        if (_state.value in setOf(LiveSessionState.CONNECTED, LiveSessionState.IDLE)) {
            _state.value = LiveSessionState.LISTENING
        }
    }

    suspend fun sendAudio(pcm16: ByteArray) {
        transport.send(LiveClientCommand.SendAudio(pcm16))
    }

    suspend fun finishSpeaking() {
        transport.send(LiveClientCommand.EndAudioStream)
        interactionActive = true
        _state.value = LiveSessionState.PROCESSING
    }

    suspend fun disconnect() {
        eventJob?.cancel()
        eventJob = null
        inFlightTools.clear()
        interactionActive = false
        transport.close()
        _state.value = LiveSessionState.DISCONNECTED
    }

    /**
     * The whole state machine. Every transition is here rather than spread across callbacks, so
     * "can the UI be listening while a tool is running?" is answerable by reading one function.
     */
    internal fun onEvent(event: LiveServerEvent) {
        when (event) {
            is LiveServerEvent.Connected -> {
                _state.value = LiveSessionState.CONNECTED
                _lastError.value = null
            }

            is LiveServerEvent.Disconnected -> {
                interactionActive = false
                inFlightTools.clear()
                _state.value = LiveSessionState.DISCONNECTED
            }

            is LiveServerEvent.InputTranscription -> {
                interactionActive = true
                _transcript.value = (_transcript.value + " " + event.text).trim()
                if (_state.value != LiveSessionState.MODEL_SPEAKING) {
                    _state.value = LiveSessionState.LISTENING
                }
            }

            is LiveServerEvent.OutputAudio -> {
                interactionActive = true
                _state.value = LiveSessionState.MODEL_SPEAKING
            }

            // The critical case. The model has stopped speaking; the interaction has NOT ended
            // unless the server has separately said so. Moving to BACKGROUND_REASONING keeps the
            // session listening for the further output extended thinking produces.
            is LiveServerEvent.TurnComplete -> {
                _state.value = when {
                    inFlightTools.isNotEmpty() -> LiveSessionState.TOOL_EXECUTION
                    interactionActive -> LiveSessionState.BACKGROUND_REASONING
                    else -> LiveSessionState.FINALIZING
                }
            }

            is LiveServerEvent.InteractionStatusChanged -> when (event.status) {
                InteractionStatus.IN_PROGRESS -> {
                    interactionActive = true
                    if (_state.value == LiveSessionState.IDLE || _state.value == LiveSessionState.CONNECTED) {
                        _state.value = LiveSessionState.PROCESSING
                    }
                }
                InteractionStatus.IDLE -> {
                    interactionActive = false
                    // Even an explicit IDLE does not end the turn while a tool is still running:
                    // its result is still owed to the model, and the model may respond to it.
                    _state.value = if (inFlightTools.isNotEmpty()) {
                        LiveSessionState.TOOL_EXECUTION
                    } else {
                        LiveSessionState.IDLE
                    }
                }
            }

            is LiveServerEvent.ToolCallRequested -> {
                inFlightTools += event.call.id
                _state.value = LiveSessionState.TOOL_EXECUTION
                dispatchTool(event.call)
            }

            is LiveServerEvent.Error -> {
                _lastError.value = event.message
                _state.value = LiveSessionState.ERROR
            }
        }
    }

    /**
     * Runs a tool off the event loop and sends its result back when it finishes. The session keeps
     * consuming server events throughout, so a slow tool cannot stall audio or drop the user's
     * speech, and several tools may overlap.
     */
    private fun dispatchTool(call: LiveToolCall) {
        val executor = toolExecutor
        if (executor == null) {
            // Nothing can run it. Telling the model so lets it continue with what it has, which is
            // far better than leaving it waiting for a result that will never arrive.
            scope.launch {
                completeTool(LiveToolResult(call.id, """{"error":"No tool executor is configured."}""", isError = true))
            }
            return
        }
        scope.launch {
            val result = try {
                executor.execute(call)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LiveToolResult(call.id, """{"error":"${e.message?.replace('"', '\'') ?: "Tool failed"}"}""", isError = true)
            }
            completeTool(result)
        }
    }

    private suspend fun completeTool(result: LiveToolResult) {
        transport.send(LiveClientCommand.SendToolResult(result))
        inFlightTools -= result.callId
        if (inFlightTools.isEmpty()) {
            // Whether the turn is over is the server's call, not this client's. With every tool
            // settled, the state reflects what the server last said about the interaction.
            _state.value = if (interactionActive) LiveSessionState.BACKGROUND_REASONING else LiveSessionState.IDLE
        }
    }

    private fun modelId(): String = com.example.ai.routing.DefaultAiModelRouter.route(
        if (profile == ProcessingProfile.LIVE_ADVANCED) {
            com.example.ai.routing.AiRoute.GEMINI_LIVE_EXTENDED
        } else {
            com.example.ai.routing.AiRoute.GEMINI_LIVE
        }
    ).modelId ?: com.example.ai.routing.DefaultAiModelRouter.GEMINI_LIVE_MODEL

    /** Test seam: lets a test observe how many tools the controller currently has outstanding. */
    internal fun inFlightToolCount(): Int = inFlightTools.size
}

/** No live transport ships — see [LiveSessionController]'s status note. */
class UnconfiguredLiveTransport : LiveTransport {
    override suspend fun connect(modelId: String, systemInstruction: String): AiResult<Flow<LiveServerEvent>> =
        AiResult.ModelUnavailable(modelId, "Live mode isn't set up on this build.")
    override suspend fun send(command: LiveClientCommand) = Unit
    override suspend fun close() = Unit
    override fun isConfigured(): Boolean = false
}
