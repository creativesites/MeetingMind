package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/**
 * How a reading sounds, chosen by the person. Never applied because of what the content is: a
 * Christian note is not read in a preacher's voice unless someone picks that (docs/mvp/READ_TO_ME_PLAN.md §16.9).
 * The instructions describe delivery only; the note's words are never part of them.
 */
enum class DeliveryPreset(val label: String, val faith: Boolean, private val instruction: String) {
    AUTO("Automatic", false, ""),
    CLEAR_PROFESSIONAL("Clear and professional", false, "Speak clearly and evenly, like a calm professional briefing, in a steady neutral tone."),
    ENGAGING_TEACHER("Engaging teacher", false, "Speak like an engaging teacher: clear, lively and easy to follow, with natural emphasis on key ideas."),
    STORYTELLER("Storyteller", false, "Speak like a storyteller: expressive and unhurried, with natural changes of pace and feeling."),
    WARM_COMPANION("Warm companion", false, "Speak warmly and gently, like a thoughtful friend reading aloud."),
    CALM_TEACHER("Calm teacher", true, "Speak calmly and clearly, like a patient Bible teacher explaining a passage, at a steady pace with gentle emphasis."),
    REFLECTIVE_DEVOTIONAL("Reflective devotional", true, "Speak slowly and reflectively, with a quiet, prayerful warmth and room to breathe between thoughts."),
    WARM_PASTOR("Warm pastor", true, "Speak with a warm, caring pastoral voice: encouraging, sincere and personal."),
    VIBRANT_PREACHER("Vibrant Pentecostal preacher", true, "Speak with the energy of a vibrant, passionate Pentecostal preacher: rising conviction, emphasis on key words and an expressive rhythm.");

    fun instruction(): String = instruction
}

/** What a reading will sound like, bounded: a preset, an energy level and a pace. */
data class DeliverySpec(val preset: DeliveryPreset, val energy: Int, val pace: Float) {

    /**
     * The `speech_metadata.style` text for Gemini: fixed phrases picked from the choices above, so it
     * can't grow or carry the note's content. Phone voices ignore everything except [pace].
     */
    fun geminiStyle(): String = buildString {
        append(preset.instruction())
        when {
            energy <= 2 -> append(" Keep the energy low and quiet.")
            energy >= 4 -> append(" Keep the energy high.")
        }
        when {
            pace < 0.95f -> append(" Speak a little slower than usual.")
            pace > 1.05f -> append(" Speak a little brisker than usual.")
        }
        append(" Read the text exactly as written.")
    }.trim()

    /** Part of the audio's fingerprint: a different delivery is different audio. */
    fun canonical(): String = "${preset.name}|$energy|${"%.2f".format(java.util.Locale.ROOT, pace)}"
}

object DeliveryResolver {
    const val MIN_PACE = 0.8f
    const val MAX_PACE = 1.3f

    /** A neutral default by kind of note; faith presets are never a default. */
    fun defaultFor(space: NotebookSpace?): DeliveryPreset = when (space) {
        NotebookSpace.WORK -> DeliveryPreset.CLEAR_PROFESSIONAL
        NotebookSpace.LEARNING -> DeliveryPreset.ENGAGING_TEACHER
        else -> DeliveryPreset.WARM_COMPANION
    }

    /** Explicit choices win; "Automatic" falls back to the neutral default for the note's space. */
    fun resolve(settings: ReadToMeSettings, space: NotebookSpace?): DeliverySpec = DeliverySpec(
        preset = if (settings.preset == DeliveryPreset.AUTO) defaultFor(space) else settings.preset,
        energy = settings.energy.coerceIn(1, 5),
        pace = settings.pace.coerceIn(MIN_PACE, MAX_PACE)
    )

    /** Presets worth offering: the faith ones only where the Faith space is in use. */
    fun offered(faithSpaceOn: Boolean): List<DeliveryPreset> = DeliveryPreset.entries.filter { faithSpaceOn || !it.faith }

    /** Honest one-liner for the sheet about what the chosen voice can change. */
    fun capability(provider: SpeechProvider): String = when (provider) {
        SpeechProvider.GEMINI -> "The Gemini voice follows the style, energy and pace you choose."
        SpeechProvider.LOCAL -> "Your phone's voice can change its pace but not its style or energy."
    }
}
