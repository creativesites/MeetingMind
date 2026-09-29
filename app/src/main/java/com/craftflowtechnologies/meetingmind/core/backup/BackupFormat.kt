package com.craftflowtechnologies.meetingmind.core.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * The `.mmbackup` format (docs/PRD_M0.md §4.3): a zip whose first entry is [MANIFEST], followed by
 * the databases, settings and files it lists, each with its SHA-256.
 */
object BackupFormat {
    const val VERSION = 1
    const val EXTENSION = "mmbackup"
    const val MIME = "application/zip"

    const val MANIFEST = "manifest.json"
    const val MAIN_DB = "db/meetmind_database"
    const val FAITH_DB = "db/faith_extras.db"
    const val HIGHLIGHTS = "bible/highlights.json"
    const val PREFS = "prefs/preferences.json"
    const val CREDENTIALS = "prefs/credentials.json"
    const val FILES = "files/"

    /** App folders that always go in: the person's notes, pictures and devotional art. */
    val CORE_DIRS = listOf("notes", "profile", "backgrounds/mine", "devotional_images")
    const val RECORDINGS_DIR = "meetings"
    const val DEVOTIONAL_AUDIO_DIR = "devotional_audio"

    /** An entry name is only ever a relative path inside the backup. Anything else is refused. */
    fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() && !name.startsWith("/") && !name.contains('\\') && name.split('/').none { it == ".." || it == "." }

    fun fileName(now: java.time.LocalDateTime): String =
        "MeetingMind-%04d-%02d-%02d-%02d%02d.$EXTENSION".format(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute)
}

data class BackupOptions(
    val includeRecordings: Boolean = false,
    val includeDevotionalAudio: Boolean = false,
    val includeApiKey: Boolean = false
)

/** What a backup says about itself. */
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val device: String,
    /** The app's files folder where the backup was made, so paths inside can be moved to this install's. */
    val filesDir: String,
    val counts: Map<String, Int>,
    val options: BackupOptions,
    /** Entry name → SHA-256 (hex). */
    val checksums: Map<String, String>
) {
    fun toJson(): String = JSONObject().apply {
        put("format", formatVersion); put("app", appVersion); put("schema", schemaVersion); put("createdAt", createdAt)
        put("device", device); put("filesDir", filesDir)
        put("counts", JSONObject().apply { counts.forEach { (k, v) -> put(k, v) } })
        put("options", JSONObject().apply {
            put("recordings", options.includeRecordings); put("devotionalAudio", options.includeDevotionalAudio); put("apiKey", options.includeApiKey)
        })
        put("checksums", JSONObject().apply { checksums.forEach { (k, v) -> put(k, v) } })
    }.toString(2)

    companion object {
        fun fromJson(raw: String): BackupManifest {
            val o = JSONObject(raw)
            fun obj(key: String) = o.optJSONObject(key) ?: JSONObject()
            val counts = obj("counts").let { c -> c.keys().asSequence().associateWith { c.optInt(it) } }
            val sums = obj("checksums").let { c -> c.keys().asSequence().associateWith { c.getString(it) } }
            val opts = obj("options")
            return BackupManifest(
                formatVersion = o.getInt("format"), appVersion = o.optString("app"), schemaVersion = o.getInt("schema"),
                createdAt = o.optLong("createdAt"), device = o.optString("device"), filesDir = o.optString("filesDir"),
                counts = counts,
                options = BackupOptions(opts.optBoolean("recordings"), opts.optBoolean("devotionalAudio"), opts.optBoolean("apiKey")),
                checksums = sums
            )
        }
    }
}

/**
 * Settings written as typed JSON rather than DataStore's own file, so a restore can move file
 * paths inside them (a profile picture's, say) to the new install's folder.
 */
object PrefsCodec {
    fun encode(values: Map<String, Any?>): String {
        val out = JSONObject()
        values.forEach { (key, value) ->
            val typed = when (value) {
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is Double -> JSONObject().put("t", "d").put("v", value)
                is String -> JSONObject().put("t", "s").put("v", value)
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> null
            }
            typed?.let { out.put(key, it) }
        }
        return out.toString(2)
    }

    fun decode(raw: String, movePaths: (String) -> String = { it }): Map<String, Any> {
        val o = JSONObject(raw)
        return o.keys().asSequence().mapNotNull { key ->
            val e = o.getJSONObject(key)
            val value: Any? = when (e.getString("t")) {
                "b" -> e.getBoolean("v")
                "i" -> e.getInt("v")
                "l" -> e.getLong("v")
                "f" -> e.getDouble("v").toFloat()
                "d" -> e.getDouble("v")
                "s" -> movePaths(e.getString("v"))
                "ss" -> e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> null
            }
            value?.let { key to it }
        }.toMap()
    }
}
