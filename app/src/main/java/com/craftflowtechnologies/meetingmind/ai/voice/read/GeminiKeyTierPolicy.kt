package com.craftflowtechnologies.meetingmind.ai.voice.read

/**
 * Which Gemini plan tier Read to Me assumes for the key in use. Pure, so the rule is tested without a device.
 *
 * With the gate off (dev and test builds, which carry the built-in key for everyone) every key is treated as
 * paid, so nobody is held back or asked to pay while testing. With the gate on (Play Store builds) a key is
 * treated as paid only when the person confirmed a paid plan or the key is the built-in one, which the owner
 * bills.
 */
object GeminiKeyTierPolicy {
    fun tier(gateOn: Boolean, confirmedPaid: Boolean): GeminiKeyTier =
        if (!gateOn || confirmedPaid) GeminiKeyTier.PAID else GeminiKeyTier.UNKNOWN
}
