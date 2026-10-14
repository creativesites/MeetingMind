package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiError
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiErrorKind

enum class Recovery { RETRY, RETRY_LATER, SWITCH_TO_PHONE_VOICE, FIX_API_KEY, CHECK_PLAN, CHECK_CONNECTION, INSTALL_VOICE_DATA }

/** What to tell the person and what they can do, when the Gemini voice fails part-way. */
data class RecoveryPlan(val message: String, val options: List<Recovery>)

/**
 * Switching to the phone voice is always an explicit choice listed here, never done for the person:
 * a reading must not change voice behind their back, and a policy block can never be retried.
 */
object ReadingRecovery {

    fun forGemini(error: GeminiError): RecoveryPlan = when (error.kind) {
        GeminiErrorKind.NO_KEY -> RecoveryPlan("Add a Gemini key to use the Gemini voice.", listOf(Recovery.FIX_API_KEY, Recovery.SWITCH_TO_PHONE_VOICE))
        GeminiErrorKind.AUTH -> RecoveryPlan("Gemini didn't accept the key.", listOf(Recovery.FIX_API_KEY, Recovery.SWITCH_TO_PHONE_VOICE))
        GeminiErrorKind.BILLING, GeminiErrorKind.QUOTA_EXHAUSTED ->
            RecoveryPlan("The Gemini plan has run out of quota.", listOf(Recovery.CHECK_PLAN, Recovery.RETRY_LATER, Recovery.SWITCH_TO_PHONE_VOICE))
        GeminiErrorKind.RATE_LIMITED -> RecoveryPlan("Gemini is busy right now.", listOf(Recovery.RETRY, Recovery.SWITCH_TO_PHONE_VOICE))
        GeminiErrorKind.NETWORK -> RecoveryPlan("Couldn't reach Gemini. Check the connection.", listOf(Recovery.RETRY, Recovery.CHECK_CONNECTION, Recovery.SWITCH_TO_PHONE_VOICE))
        GeminiErrorKind.TIMEOUT, GeminiErrorKind.SERVER -> RecoveryPlan("The Gemini voice isn't answering.", listOf(Recovery.RETRY, Recovery.SWITCH_TO_PHONE_VOICE))
        GeminiErrorKind.BAD_REQUEST, GeminiErrorKind.NOT_FOUND, GeminiErrorKind.MALFORMED, GeminiErrorKind.EMPTY_AUDIO ->
            RecoveryPlan("The Gemini voice couldn't read this part.", listOf(Recovery.RETRY, Recovery.SWITCH_TO_PHONE_VOICE))
        GeminiErrorKind.POLICY_BLOCKED -> RecoveryPlan(error.message, listOf(Recovery.SWITCH_TO_PHONE_VOICE))
    }
}
