package com.example.ai.live

/**
 * Explicit state for a Gemini Live session.
 *
 * Scattering `isConnected` / `isSpeaking` / `isThinking` booleans through a ViewModel is how a
 * real-time UI ends up in states that cannot happen and out of states that can. With extended
 * thinking it stops being a style question: the model keeps working after it has finished
 * speaking, so "not speaking" and "idle" are genuinely different states and a boolean cannot tell
 * them apart.
 */
enum class LiveSessionState {
    DISCONNECTED,
    CONNECTING,
    /** Connected, session open, nothing in flight. */
    CONNECTED,
    /** Streaming the user's microphone to the model. */
    LISTENING,
    /** The model is working on a turn and has not started responding. */
    PROCESSING,
    /** Audio is coming back from the model. */
    MODEL_SPEAKING,
    /**
     * The model signalled `turnComplete` but the interaction is still active.
     *
     * This is the state that does not exist in a simple client, and the reason this enum exists.
     * See [LiveSessionController].
     */
    BACKGROUND_REASONING,
    /** One or more tool calls are in flight. Never blocks the session. */
    TOOL_EXECUTION,
    /** The interaction has gone idle and the controller is settling the turn. */
    FINALIZING,
    /** Connected, interaction finished, ready for the next one. */
    IDLE,
    ERROR
}

/**
 * The server's own view of whether an interaction is still active.
 *
 * Gemini Live reports this separately from `turnComplete` precisely because the two are not the
 * same thing, and the client must believe this one.
 */
enum class InteractionStatus { IN_PROGRESS, IDLE }

/** An event received from a Live session. */
sealed interface LiveServerEvent {
    data object Connected : LiveServerEvent
    data object Disconnected : LiveServerEvent

    /** A chunk of transcribed user speech. */
    data class InputTranscription(val text: String, val isFinal: Boolean) : LiveServerEvent

    /** A chunk of the model's spoken response. */
    data class OutputAudio(val text: String?) : LiveServerEvent

    /**
     * The model finished its current output.
     *
     * **This does not mean the session is idle.** With extended thinking the model may still be
     * reasoning, calling tools, or preparing more output after this arrives. Acting on it as an
     * end-of-interaction signal is the single most likely way to build a Live client that
     * truncates the model mid-thought.
     */
    data object TurnComplete : LiveServerEvent

    /** The server's authoritative statement about whether the interaction is still active. */
    data class InteractionStatusChanged(val status: InteractionStatus) : LiveServerEvent

    /** The model wants a tool run. Executed without blocking the session — see [LiveToolCall]. */
    data class ToolCallRequested(val call: LiveToolCall) : LiveServerEvent

    data class Error(val message: String) : LiveServerEvent
}

/** One tool invocation requested by the model. */
data class LiveToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

/** The outcome of running a [LiveToolCall], sent back to the model. */
data class LiveToolResult(
    val callId: String,
    val resultJson: String,
    val isError: Boolean = false
)

/** Messages the client sends into a Live session. */
sealed interface LiveClientCommand {
    data class SendAudio(val pcm16: ByteArray) : LiveClientCommand {
        override fun equals(other: Any?): Boolean =
            this === other || (other is SendAudio && pcm16.contentEquals(other.pcm16))
        override fun hashCode(): Int = pcm16.contentHashCode()
    }
    data class SendText(val text: String) : LiveClientCommand
    data class SendToolResult(val result: LiveToolResult) : LiveClientCommand
    data object EndAudioStream : LiveClientCommand
}
