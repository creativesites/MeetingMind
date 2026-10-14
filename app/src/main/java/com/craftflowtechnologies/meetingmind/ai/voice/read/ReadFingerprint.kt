package com.craftflowtechnologies.meetingmind.ai.voice.read

import java.security.MessageDigest

/** Everything outside the note's own words that changes what a section sounds like. */
data class FingerprintSettings(
    val mode: String,
    val provider: SpeechProvider,
    val model: String,
    val voiceId: String,
    /** A hash or canonical string of the bounded delivery spec (preset, energy, pace, cue). */
    val delivery: String,
    val language: String? = null,
    val scriptVersion: Int = 1,
    val formatVersion: Int = 1
)

/**
 * A section's audio is current only while its fingerprint matches. The words the listener hears
 * are in the hash, including the resolved scripture text and the verse-number and label settings
 * (they change the spoken words), so editing the note, changing translation or re-fetching
 * different text all invalidate it. Playback speed is applied by the player and is not in the key.
 */
object ReadFingerprint {

    fun section(section: SpeechSection, s: FingerprintSettings): String {
        val canonical = buildString {
            append("v").append(s.scriptVersion).append('|').append(s.formatVersion).append('\n')
            append(s.mode).append('|').append(s.provider).append('|').append(s.model).append('|')
            append(s.voiceId).append('|').append(s.delivery).append('|').append(s.language.orEmpty()).append('\n')
            for (u in section.units) {
                append(u.role).append('|').append(u.locked).append('|').append(u.unavailable).append('\n')
                append(u.lead.orEmpty()).append('\n')
                append(u.text).append('\n')
            }
        }
        return sha256(canonical)
    }

    /** True while [stored] is the fingerprint [section] has right now. */
    fun isFresh(stored: String?, section: SpeechSection, s: FingerprintSettings): Boolean =
        stored != null && stored == section(section, s)

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
