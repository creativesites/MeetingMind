package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.content.Context

/** How long to wait after a reflection question before reading on. */
enum class PauseAfterQuestion(val label: String, val silenceMs: Long) {
    NONE("None", 400),
    SHORT("Short", 5_000),
    MEDIUM("Medium", 10_000),
    /** Playback pauses at the end of the question until the person presses play. */
    UNTIL_RESUME("Until I resume", 600)
}

/**
 * The person's explicit Read to Me choices. Enums, numbers and voice names only: never note text,
 * titles or ids. (Learning from behaviour, with a reset, comes later and builds on this.)
 */
data class ReadToMeSettings(
    val prefersLocal: Boolean = false,
    /** Accepted "Read to Me sends the note text to Google Gemini to make the audio". */
    val disclosureAccepted: Boolean = false,
    val preset: DeliveryPreset = DeliveryPreset.AUTO,
    val energy: Int = 3,
    val pace: Float = 1f,
    val geminiVoice: String = "Kore",
    val localVoice: String? = null,
    val readVerseNumbers: Boolean = false,
    val labelStyle: LabelStyle = LabelStyle.FULL,
    val announceTranslation: TranslationAnnouncement = TranslationAnnouncement.WHEN_CHANGED,
    val pauseAfterQuestion: PauseAfterQuestion = PauseAfterQuestion.SHORT,
    /** Smart Read (smoother wording for listening) rather than Read Exactly. Scripture, quotes, prayers and personal writing are never reworded either way. */
    val smartRead: Boolean = true
) {
    fun extractOptions() = ExtractOptions(readVerseNumbers, labelStyle, announceTranslation)
}

/** Whether the feature is on at all: a switch for a build or a person to turn Read to Me off. */
object ReadToMeFlags {
    private const val FILE = "read_to_me"
    private const val KEY = "enabled"

    fun enabled(context: Context): Boolean = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}

class ReadToMeSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("read_to_me", Context.MODE_PRIVATE)

    fun load(): ReadToMeSettings = ReadToMeSettings(
        prefersLocal = prefs.getBoolean("prefersLocal", false),
        disclosureAccepted = prefs.getBoolean("disclosureAccepted", false),
        preset = enumOf("preset", DeliveryPreset.AUTO),
        energy = prefs.getInt("energy", 3).coerceIn(1, 5),
        pace = prefs.getFloat("pace", 1f).coerceIn(DeliveryResolver.MIN_PACE, DeliveryResolver.MAX_PACE),
        geminiVoice = prefs.getString("geminiVoice", null)?.takeIf { com.craftflowtechnologies.meetingmind.ai.cloud.GeminiSpeechClient.isKnownVoice(it) } ?: "Kore",
        localVoice = prefs.getString("localVoice", null),
        readVerseNumbers = prefs.getBoolean("readVerseNumbers", false),
        labelStyle = enumOf("labelStyle", LabelStyle.FULL),
        announceTranslation = enumOf("announceTranslation", TranslationAnnouncement.WHEN_CHANGED),
        pauseAfterQuestion = enumOf("pauseAfterQuestion", PauseAfterQuestion.SHORT),
        smartRead = prefs.getBoolean("smartRead", true)
    )

    fun update(change: (ReadToMeSettings) -> ReadToMeSettings): ReadToMeSettings {
        val s = change(load())
        prefs.edit()
            .putBoolean("prefersLocal", s.prefersLocal).putBoolean("disclosureAccepted", s.disclosureAccepted)
            .putString("preset", s.preset.name).putInt("energy", s.energy).putFloat("pace", s.pace)
            .putString("geminiVoice", s.geminiVoice).putString("localVoice", s.localVoice)
            .putBoolean("readVerseNumbers", s.readVerseNumbers).putString("labelStyle", s.labelStyle.name)
            .putString("announceTranslation", s.announceTranslation.name).putString("pauseAfterQuestion", s.pauseAfterQuestion.name).putBoolean("smartRead", s.smartRead)
            .apply()
        return s
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
