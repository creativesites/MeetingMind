package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.cloud.CloudPermission
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile

/** Whether the person's plan includes the Gemini voice. Stubbed to true until billing exists (one place to change). */
fun interface ReadToMeEntitlement {
    suspend fun canUseGeminiVoice(): Boolean
}

object ReadToMeEntitlements {
    @Volatile var current: ReadToMeEntitlement = ReadToMeEntitlement { true }
}

/**
 * The check made before every Gemini request of a reading (see GeminiSpeechClient). The route decided
 * at the start whether this reading may use the cloud at all; this re-checks the things that can
 * change while it runs: the person turning on offline mode, or losing the plan.
 */
class CloudGate(
    private val routeAllowsCloud: Boolean,
    private val profile: suspend () -> ProcessingProfile,
    private val entitlement: ReadToMeEntitlement = ReadToMeEntitlements.current
) : CloudPermission {
    override suspend fun blockedReason(): String? = when {
        !routeAllowsCloud -> "This reading uses the phone's voice."
        profile() != ProcessingProfile.INTERNET -> "Offline mode is on, so nothing is sent to Gemini."
        !entitlement.canUseGeminiVoice() -> "Your plan doesn't include the Gemini voice."
        else -> null
    }
}
