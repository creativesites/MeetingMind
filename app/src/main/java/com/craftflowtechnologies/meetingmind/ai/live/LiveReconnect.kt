package com.craftflowtechnologies.meetingmind.ai.live

/**
 * Decides what to do when a live voice socket goes away, with no Android or network in it.
 *
 * Gemini Live connections are short-lived by design (about ten minutes) and warn first with a
 * `goAway`; a network blip or server close ends them with no warning at all. Either way the
 * conversation should carry on from the latest resumption handle, and only end when the person
 * ends it or reconnecting truly fails.
 */
class ReconnectMachine(
    private val maxAttempts: Int = 4,
    private val backoffMs: List<Long> = listOf(400L, 1_200L, 2_500L, 5_000L),
    /** Attempts after which the (possibly stale) handle is dropped and a fresh session is tried. */
    private val dropHandleAtAttempt: Int = 3
) {
    sealed interface Decision {
        data class Reconnect(val delayMs: Long, val handle: String?, val attempt: Int) : Decision
        data class GiveUp(val reason: String) : Decision
        /** Nothing to do: the person already ended the session. */
        data object Ignore : Decision
    }

    /** The latest handle the server said can be resumed from. */
    var handle: String? = null
        private set
    var attempts = 0
        private set
    private var ended = false
    private var everReady = false

    fun onReady() { everReady = true; attempts = 0 }

    /** `sessionResumptionUpdate`: only a resumable point replaces the stored handle. */
    fun onHandle(newHandle: String?, resumable: Boolean) {
        if (resumable && !newHandle.isNullOrEmpty()) handle = newHandle
    }

    fun onUserEnd() { ended = true }

    /** The server announced it will close soon: connect a successor now, with no wait. */
    fun onGoAway(): Decision = if (ended) Decision.Ignore else Decision.Reconnect(0, handle, 0)

    /** A socket closed or failed without the person asking. */
    fun onDropped(code: Int?, reason: String?): Decision {
        if (ended) return Decision.Ignore
        if (!everReady) return Decision.GiveUp("Couldn't start the voice session.")
        if (isFatal(code, reason)) return Decision.GiveUp("The conversation can't continue.")
        if (attempts >= maxAttempts) return Decision.GiveUp("Couldn't get the connection back after $maxAttempts tries.")
        attempts++
        val useHandle = if (attempts >= dropHandleAtAttempt) null else handle
        return Decision.Reconnect(backoffMs[(attempts - 1).coerceAtMost(backoffMs.lastIndex)], useHandle, attempts)
    }

    companion object {
        /** Closes that retrying cannot fix: a bad key, no permission, or no quota left. */
        fun isFatal(code: Int?, reason: String?): Boolean {
            val r = reason.orEmpty()
            return code == 1007 || code == 1008 || code == 403 || code == 429 ||
                listOf("API key", "permission", "quota", "exhausted", "not found", "not supported").any { r.contains(it, true) }
        }
    }
}

/** Whether the person's voice may cut the companion off, and when the mic should stream. */
object InterruptPolicy {
    /**
     * The saved choice wins; otherwise a headset allows natural barge-in and the loudspeaker
     * defaults to "Calm" (the phone's own voice through its speaker is what it would hear).
     */
    fun voiceInterrupt(saved: Boolean?, headset: Boolean): Boolean = saved ?: headset

    /**
     * Whether the microphone's audio goes to the model for this frame.
     * @param bargeGateOpen what [BargeIn] decided (only used when voice interruption is on)
     */
    fun micOpen(voiceInterrupt: Boolean, companionAudible: Boolean, bargeGateOpen: Boolean): Boolean = when {
        !companionAudible -> true
        voiceInterrupt -> bargeGateOpen
        else -> false
    }
}
