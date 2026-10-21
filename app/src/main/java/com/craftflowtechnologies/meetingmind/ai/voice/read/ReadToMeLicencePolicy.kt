package com.craftflowtechnologies.meetingmind.ai.voice.read

/**
 * Whether scripture text may be sent to Gemini and its audio kept, for Read to Me.
 *
 * Owner decision (2026-10-14): the purpose is personal listening, and the service only reads the text
 * back, so licensed translations (for example NIV, ESV, NLT) are treated like open ones here. KJV is still
 * not "open" in [com.craftflowtechnologies.meetingmind.core.scripture.BibleLicensing]; that classification is
 * used elsewhere in the app and is not changed by this. Set [SEND_LICENSED_TEXT_TO_CLOUD] to false to restore
 * the cautious behaviour (phone voice and short-lived audio for display-only translations).
 */
object ReadToMeLicencePolicy {
    const val SEND_LICENSED_TEXT_TO_CLOUD = false

    /** Whether the text of a passage may go to the cloud voice, given whether its translation is open. */
    fun cloudOk(openForCloud: Boolean, sendLicensed: Boolean = SEND_LICENSED_TEXT_TO_CLOUD): Boolean =
        openForCloud || sendLicensed
}
