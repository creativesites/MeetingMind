package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.Wav
import com.craftflowtechnologies.meetingmind.core.audio.AacEncoder
import com.craftflowtechnologies.meetingmind.core.audio.QueueLogic
import com.craftflowtechnologies.meetingmind.core.audio.QueueProgress
import com.craftflowtechnologies.meetingmind.core.audio.ResumeData
import com.craftflowtechnologies.meetingmind.core.audio.ResumptionSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class SectionStatus { PENDING, READY, FAILED, UNAVAILABLE }

data class SectionEntry(
    val sectionId: String,
    val title: String,
    /** [ReadFingerprint] of the section this audio was made from. */
    val fingerprint: String,
    val status: SectionStatus,
    val fileName: String? = null,
    val durationMs: Long = 0,
    val blockIds: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    /** Audio of licensed text: kept only briefly, outside backups. */
    val ephemeral: Boolean = false,
    /** Pause when this part ends (a reflection question). */
    val pauseAfter: Boolean = false,
    val error: String? = null
)

data class ReadingProgress(val mediaId: String?, val sectionIndex: Int, val positionMs: Long, val completed: Boolean, val updatedAt: Long)

/** Everything about one note's reading except the audio itself. Derived data: the note is never changed. */
data class ReadingManifest(
    val noteId: String,
    val mode: String,
    val provider: String,
    val model: String?,
    val voiceId: String,
    val delivery: String,
    val language: String?,
    /** Show "Read to Me" instead of the note's title on the lock screen (private, confidential or faith-locked notes). */
    val genericTitle: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val expectedSections: Int,
    val sections: List<SectionEntry>,
    val progress: ReadingProgress? = null,
    /** The reading moved to the phone voice from this section on, by the person's choice. */
    val switchedToPhoneAtSection: Int? = null,
    val schema: Int = SCHEMA
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", schema).put("noteId", noteId).put("mode", mode).put("provider", provider)
        .put("model", model ?: JSONObject.NULL).put("voiceId", voiceId).put("delivery", delivery)
        .put("language", language ?: JSONObject.NULL).put("genericTitle", genericTitle)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("expectedSections", expectedSections)
        .put("switchedToPhoneAtSection", switchedToPhoneAtSection ?: JSONObject.NULL)
        .put("progress", progress?.let {
            JSONObject().put("mediaId", it.mediaId ?: JSONObject.NULL).put("sectionIndex", it.sectionIndex)
                .put("positionMs", it.positionMs).put("completed", it.completed).put("updatedAt", it.updatedAt)
        } ?: JSONObject.NULL)
        .put("sections", JSONArray().also { arr ->
            sections.forEach { s ->
                arr.put(JSONObject().put("sectionId", s.sectionId).put("title", s.title).put("fingerprint", s.fingerprint)
                    .put("status", s.status.name).put("fileName", s.fileName ?: JSONObject.NULL).put("durationMs", s.durationMs)
                    .put("blockIds", JSONArray(s.blockIds)).put("references", JSONArray(s.references))
                    .put("ephemeral", s.ephemeral).put("pauseAfter", s.pauseAfter).put("error", s.error ?: JSONObject.NULL))
            }
        })

    companion object {
        const val SCHEMA = 1

        /** Null for anything that isn't a readable manifest of this schema. */
        fun fromJson(json: JSONObject): ReadingManifest? = runCatching {
            if (json.getInt("schema") != SCHEMA) return null
            fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
            fun JSONArray.strings() = List(length()) { getString(it) }
            val progress = json.optJSONObject("progress")?.let {
                ReadingProgress(it.str("mediaId"), it.getInt("sectionIndex"), it.getLong("positionMs"), it.getBoolean("completed"), it.getLong("updatedAt"))
            }
            val sections = json.getJSONArray("sections").let { arr ->
                List(arr.length()) { i ->
                    val s = arr.getJSONObject(i)
                    SectionEntry(
                        s.getString("sectionId"), s.getString("title"), s.getString("fingerprint"),
                        SectionStatus.valueOf(s.getString("status")), s.str("fileName"), s.getLong("durationMs"),
                        s.getJSONArray("blockIds").strings(), s.getJSONArray("references").strings(),
                        s.getBoolean("ephemeral"), s.getBoolean("pauseAfter"), s.str("error")
                    )
                }
            }
            ReadingManifest(
                json.getString("noteId"), json.getString("mode"), json.getString("provider"), json.str("model"),
                json.getString("voiceId"), json.getString("delivery"), json.str("language"), json.getBoolean("genericTitle"),
                json.getLong("createdAt"), json.getLong("updatedAt"), json.getInt("expectedSections"), sections, progress,
                if (json.isNull("switchedToPhoneAtSection")) null else json.getInt("switchedToPhoneAtSection")
            )
        }.getOrNull()
    }
}

enum class Freshness { FRESH, STALE, MISSING }

/**
 * A note's saved reading on disk: one manifest plus one audio file per section, under [persistentRoot]
 * (and [ephemeralRoot] for audio of licensed text). The note is the source of truth; a saved section
 * is used only while its fingerprint still matches what the note says now.
 */
class ReadingStore(
    private val persistentRoot: File,
    private val ephemeralRoot: File,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private fun dir(noteId: String, ephemeral: Boolean) = File(if (ephemeral) ephemeralRoot else persistentRoot, safe(noteId))
    private fun manifestFile(noteId: String) = File(dir(noteId, false), MANIFEST)

    fun load(noteId: String): ReadingManifest? {
        val file = manifestFile(noteId)
        if (!file.exists()) return null
        val manifest = runCatching { ReadingManifest.fromJson(JSONObject(file.readText())) }.getOrNull()
        if (manifest == null) { delete(noteId); return null } // unreadable: forget it and make it again
        return manifest
    }

    /** Written to a temporary file and renamed, so a crash never leaves half a manifest. */
    @Synchronized
    fun save(manifest: ReadingManifest) {
        val target = manifestFile(manifest.noteId)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "$MANIFEST.tmp")
        tmp.writeText(manifest.toJson().toString())
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    /**
     * Saves [pcm] as the audio for a section and returns its entry (READY). AAC when the phone can
     * encode it, WAV otherwise.
     */
    fun commitAudio(noteId: String, sectionId: String, title: String, fingerprint: String, pcm: Pcm, blockIds: List<String>, references: List<String>, ephemeral: Boolean, pauseAfter: Boolean): SectionEntry {
        val folder = dir(noteId, ephemeral).apply { mkdirs() }
        val base = "${safe(sectionId)}-${fingerprint.take(12)}"
        val m4a = File(folder, "$base.m4a")
        val file = if (AacEncoder.encode(pcm, m4a)) m4a else { m4a.delete(); File(folder, "$base.wav").also { Wav.write(pcm, it) } }
        return SectionEntry(sectionId, title, fingerprint, SectionStatus.READY, file.name, pcm.durationMs, blockIds, references, ephemeral, pauseAfter)
    }

    fun audioFile(noteId: String, entry: SectionEntry): File? =
        entry.fileName?.let { File(dir(noteId, entry.ephemeral), it) }?.takeIf { it.exists() && it.length() > 0 }

    /** For each section of the note as it is now: is the saved audio current, out of date, or not there. */
    fun freshness(manifest: ReadingManifest?, source: SpeechSource, settings: FingerprintSettings): List<Freshness> =
        source.sections.map { section ->
            val saved = manifest?.sections?.firstOrNull { it.sectionId == section.id && it.status == SectionStatus.READY }
            when {
                saved == null -> Freshness.MISSING
                audioFile(manifest.noteId, saved) == null -> Freshness.MISSING
                saved.fingerprint == ReadFingerprint.section(section, settings) -> Freshness.FRESH
                else -> Freshness.STALE
            }
        }

    @Synchronized
    fun saveProgress(p: QueueProgress) {
        val noteId = noteIdOf(p.queueId) ?: return
        val manifest = load(noteId) ?: return
        save(manifest.copy(updatedAt = clock(), progress = ReadingProgress(p.mediaId, p.index, p.positionMs, p.completed, clock())))
    }

    /** The reading played most recently, for resuming after the app was killed. */
    fun lastPlayed(): ReadingManifest? =
        persistentRoot.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { load(it.name) }
            .filter { it.progress != null }
            .maxByOrNull { it.progress!!.updatedAt }

    fun delete(noteId: String) {
        dir(noteId, false).deleteRecursively()
        dir(noteId, true).deleteRecursively()
    }

    fun clearAll() {
        persistentRoot.deleteRecursively()
        ephemeralRoot.deleteRecursively()
    }

    fun usedBytes(): Long = listOf(persistentRoot, ephemeralRoot).sumOf { root -> root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    /** Removes the least recently played readings until the total is under [maxBytes]; [protectNoteId] is never removed. */
    fun trimTo(maxBytes: Long, protectNoteId: String? = null): Int {
        var removed = 0
        val readings = persistentRoot.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir -> load(dir.name)?.let { it to dir } }
            .sortedBy { (m, _) -> m.progress?.updatedAt ?: m.updatedAt }
        for ((manifest, _) in readings) {
            if (usedBytes() <= maxBytes) break
            if (manifest.noteId == protectNoteId) continue
            delete(manifest.noteId)
            removed++
        }
        return removed
    }

    /** Audio of licensed text is dropped once it is older than [ttlMs]. */
    fun purgeExpiredEphemeral(ttlMs: Long): Int {
        var removed = 0
        val now = clock()
        ephemeralRoot.listFiles { f -> f.isDirectory }.orEmpty().forEach { noteDir ->
            noteDir.listFiles().orEmpty().filter { now - it.lastModified() > ttlMs }.forEach { if (it.deleteRecursively()) removed++ }
            if (noteDir.listFiles().isNullOrEmpty()) noteDir.delete()
        }
        return removed
    }

    companion object {
        private const val MANIFEST = "manifest.json"
        const val MAX_BYTES = 200L * 1024 * 1024
        const val EPHEMERAL_TTL_MS = 24L * 60 * 60 * 1000
        const val ID_PREFIX = "read:"

        fun queueId(noteId: String) = "$ID_PREFIX$noteId"
        fun noteIdOf(queueId: String): String? = queueId.removePrefix(ID_PREFIX).takeIf { queueId.startsWith(ID_PREFIX) && it.isNotBlank() }

        /** Changes whenever the section's content or settings do, so the player never mistakes new audio for old. */
        fun mediaId(noteId: String, sectionId: String, fingerprint: String) = "$ID_PREFIX$noteId:$sectionId:${fingerprint.take(12)}"

        private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

        @Volatile private var shared: ReadingStore? = null
        fun get(context: Context): ReadingStore = shared ?: synchronized(this) {
            shared ?: ReadingStore(File(context.applicationContext.filesDir, "read_to_me"), File(context.applicationContext.cacheDir, "read_to_me")).also { shared = it }
        }
    }
}

/** Rebuilds the last reading's queue after the process was killed, from the saved sections and position. */
class ReadingResumptionSource(private val store: ReadingStore) : ResumptionSource {
    override suspend fun last(): ResumeData? {
        val manifest = store.lastPlayed() ?: return null
        val ready = manifest.sections.mapNotNull { entry ->
            if (entry.status != SectionStatus.READY) return@mapNotNull null
            val file = store.audioFile(manifest.noteId, entry) ?: return@mapNotNull null
            entry to file
        }
        if (ready.isEmpty()) return null
        val items = ready.map { (entry, file) ->
            MediaItem.Builder()
                .setMediaId(ReadingStore.mediaId(manifest.noteId, entry.sectionId, entry.fingerprint))
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(if (manifest.genericTitle) "Read to Me" else entry.title).build())
                .build()
        }
        val progress = manifest.progress
        val savedIndex = progress?.mediaId?.let { id -> items.indexOfFirst { it.mediaId == id } }?.takeIf { it >= 0 } ?: progress?.sectionIndex ?: 0
        val duration = ready.getOrNull(savedIndex)?.first?.durationMs
        val (index, position) = QueueLogic.resumePoint(savedIndex, progress?.positionMs ?: 0, duration, items.size)
        return ResumeData(items, index, position)
    }
}
