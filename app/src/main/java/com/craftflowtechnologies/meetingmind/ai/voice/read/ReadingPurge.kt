package com.craftflowtechnologies.meetingmind.ai.voice.read

/**
 * Whether a saved reading may still be kept or played, given the note's privacy right now. Pure.
 *
 * Audio made with the Gemini voice must not outlive a change that makes the note private, confidential or
 * work-on-device: the cloud copy is removed and the person hears the phone voice instead. Phone-voice audio
 * stays, since it never left the phone.
 */
object ReadingPurge {
    /** The saved reading's provider was the cloud voice and the note now stays on the phone. */
    fun mustPurge(savedProvider: String?, stayOnPhone: Boolean): Boolean =
        stayOnPhone && savedProvider == SpeechProvider.GEMINI.name
}
