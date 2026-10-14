package com.craftflowtechnologies.meetingmind.ai.voice.read.profile

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.voice.read.DeliveryPreset
import com.craftflowtechnologies.meetingmind.ai.voice.read.DeliveryResolver
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadToMeSettings
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import org.json.JSONObject

/** Kinds of note whose reading the person tends to want the same way. */
enum class ReadCategory(val label: String) {
    SERMON_STUDY("Sermons and Bible study"),
    PRAYER_DEVOTIONAL("Prayer and devotionals"),
    REFLECTION_JOURNAL("Reflections and journals"),
    WORK("Meetings and work"),
    LEARNING("Lectures and research"),
    OTHER("Other notes");

    companion object {
        fun of(type: RecordingType): ReadCategory = when (type) {
            RecordingType.SERMON, RecordingType.BIBLE_STUDY -> SERMON_STUDY
            RecordingType.DEVOTIONAL, RecordingType.PRAYER, RecordingType.PRAYER_REQUEST, RecordingType.TESTIMONY, RecordingType.GRATITUDE -> PRAYER_DEVOTIONAL
            RecordingType.REFLECTION, RecordingType.JOURNAL, RecordingType.VOICE_MEMO, RecordingType.IDEA -> REFLECTION_JOURNAL
            RecordingType.MEETING, RecordingType.INTERVIEW, RecordingType.CLIENT_CALL, RecordingType.ONE_ON_ONE, RecordingType.STANDUP,
            RecordingType.CONSULTATION, RecordingType.PROJECT_BRIEF, RecordingType.DECISION_RECORD, RecordingType.WEEKLY_REVIEW -> WORK
            RecordingType.LECTURE, RecordingType.RESEARCH -> LEARNING
            else -> OTHER
        }
    }
}

enum class ProfileField(val label: String) { PRESET("Style"), ENERGY("Energy"), PACE("Pace"), VOICE("Voice") }

data class Streak(val value: String, val count: Int)

/** A default that was just learned: the sheet says so and offers Undo. */
data class Promotion(val category: ReadCategory, val which: ProfileField, val value: String, val previous: String?) {
    val message: String get() = "${category.label} will now start with ${display(which, value)} when set to Automatic."
}

internal fun display(field: ProfileField, value: String): String = when (field) {
    ProfileField.PRESET -> runCatching { DeliveryPreset.valueOf(value).label }.getOrDefault(value)
    ProfileField.ENERGY -> "energy $value"
    ProfileField.PACE -> "pace $value"
    ProfileField.VOICE -> "the $value voice"
}

/**
 * What Read to Me has learned about how the person likes things read, on this phone only. Enums, numbers and
 * voice names only: never a note's words, titles, references or ids. Learning is from deliberate, repeated
 * choices; watching what the person skips or abandons never changes anything.
 */
data class ReadToMeProfile(
    val adaptive: Boolean = true,
    val learned: Map<ReadCategory, Map<ProfileField, String>> = emptyMap(),
    val streaks: Map<String, Streak> = emptyMap(),
    val version: Int = 1
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version).put("adaptive", adaptive)
        .put("learned", JSONObject().also { o -> learned.forEach { (c, m) -> o.put(c.name, JSONObject().also { j -> m.forEach { (f, v) -> j.put(f.name, v) } }) } })
        .put("streaks", JSONObject().also { o -> streaks.forEach { (k, s) -> o.put(k, JSONObject().put("v", s.value).put("n", s.count)) } })

    companion object {
        fun fromJson(json: JSONObject?): ReadToMeProfile = runCatching {
            json ?: return ReadToMeProfile()
            val learned = mutableMapOf<ReadCategory, Map<ProfileField, String>>()
            json.optJSONObject("learned")?.let { o ->
                o.keys().forEach { ck ->
                    val c = ReadCategory.entries.firstOrNull { it.name == ck } ?: return@forEach
                    val inner = o.getJSONObject(ck)
                    val m = mutableMapOf<ProfileField, String>()
                    inner.keys().forEach { fk -> ProfileField.entries.firstOrNull { it.name == fk }?.let { m[it] = inner.getString(fk) } }
                    if (m.isNotEmpty()) learned[c] = m
                }
            }
            val streaks = mutableMapOf<String, Streak>()
            json.optJSONObject("streaks")?.let { o -> o.keys().forEach { k -> o.getJSONObject(k).let { streaks[k] = Streak(it.getString("v"), it.getInt("n")) } } }
            ReadToMeProfile(json.optBoolean("adaptive", true), learned, streaks, json.optInt("version", 1))
        }.getOrDefault(ReadToMeProfile())
    }
}

object LearningRules {
    /** Same deliberate choice this many times in a row (no other choice in between) becomes the default. */
    const val PROMOTE_AFTER = 3

    data class Outcome(val profile: ReadToMeProfile, val promotions: List<Promotion>)

    private fun key(c: ReadCategory, f: ProfileField) = "${c.name}|${f.name}"

    /** The values the person chose on purpose for this reading (Automatic and the stock values are not choices). */
    fun deliberate(s: ReadToMeSettings): Map<ProfileField, String> = buildMap {
        if (s.preset != DeliveryPreset.AUTO) put(ProfileField.PRESET, s.preset.name)
        if (s.energy != 3) put(ProfileField.ENERGY, s.energy.toString())
        if (kotlin.math.abs(s.pace - 1f) > 0.01f) put(ProfileField.PACE, "%.2f".format(java.util.Locale.ROOT, s.pace))
        if (s.geminiVoice != "Kore") put(ProfileField.VOICE, s.geminiVoice)
    }

    fun recordUse(profile: ReadToMeProfile, category: ReadCategory, choices: Map<ProfileField, String>): Outcome {
        if (!profile.adaptive || choices.isEmpty()) return Outcome(profile, emptyList())
        val streaks = profile.streaks.toMutableMap()
        val learned = profile.learned.toMutableMap()
        val promotions = mutableListOf<Promotion>()
        choices.forEach { (field, value) ->
            val k = key(category, field)
            val next = streaks[k]?.takeIf { it.value == value }?.let { it.copy(count = it.count + 1) } ?: Streak(value, 1)
            streaks[k] = next
            val current = learned[category]?.get(field)
            if (next.count >= PROMOTE_AFTER && current != value) {
                learned[category] = (learned[category].orEmpty()) + (field to value)
                promotions += Promotion(category, field, value, current)
            }
        }
        return Outcome(profile.copy(learned = learned, streaks = streaks), promotions)
    }

    fun undo(profile: ReadToMeProfile, p: Promotion): ReadToMeProfile {
        val m = profile.learned[p.category].orEmpty().toMutableMap()
        if (p.previous == null) m.remove(p.which) else m[p.which] = p.previous
        val learned = profile.learned.toMutableMap()
        if (m.isEmpty()) learned.remove(p.category) else learned[p.category] = m
        // The streak is cleared too, so the same choice must be made again to promote it again.
        return profile.copy(learned = learned, streaks = profile.streaks - key(p.category, p.which))
    }

    fun reset(profile: ReadToMeProfile, category: ReadCategory? = null): ReadToMeProfile =
        if (category == null) profile.copy(learned = emptyMap(), streaks = emptyMap())
        else profile.copy(learned = profile.learned - category, streaks = profile.streaks.filterKeys { !it.startsWith("${category.name}|") })

    /** Settings with learned defaults filled in only where the person left things on Automatic or stock values. Explicit choices always win. */
    fun apply(settings: ReadToMeSettings, category: ReadCategory, profile: ReadToMeProfile): ReadToMeSettings {
        if (!profile.adaptive) return settings
        val l = profile.learned[category] ?: return settings
        var s = settings
        l[ProfileField.PRESET]?.takeIf { s.preset == DeliveryPreset.AUTO }?.let { v -> DeliveryPreset.entries.firstOrNull { it.name == v }?.let { s = s.copy(preset = it) } }
        l[ProfileField.ENERGY]?.takeIf { s.energy == 3 }?.toIntOrNull()?.let { s = s.copy(energy = it.coerceIn(1, 5)) }
        l[ProfileField.PACE]?.takeIf { kotlin.math.abs(s.pace - 1f) <= 0.01f }?.toFloatOrNull()?.let { s = s.copy(pace = it.coerceIn(DeliveryResolver.MIN_PACE, DeliveryResolver.MAX_PACE)) }
        l[ProfileField.VOICE]?.takeIf { s.geminiVoice == "Kore" }?.let { v -> if (com.craftflowtechnologies.meetingmind.ai.cloud.GeminiSpeechClient.isKnownVoice(v)) s = s.copy(geminiVoice = v) }
        return s
    }
}

class ReadToMeProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("read_to_me_profile", Context.MODE_PRIVATE)
    @Synchronized fun load(): ReadToMeProfile = ReadToMeProfile.fromJson(prefs.getString("profile", null)?.let { runCatching { JSONObject(it) }.getOrNull() })
    @Synchronized fun save(p: ReadToMeProfile) { prefs.edit().putString("profile", p.toJson().toString()).apply() }
    @Synchronized fun update(change: (ReadToMeProfile) -> ReadToMeProfile): ReadToMeProfile = change(load()).also(::save)
}
