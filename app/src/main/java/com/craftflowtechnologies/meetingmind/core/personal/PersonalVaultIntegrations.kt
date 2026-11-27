package com.craftflowtechnologies.meetingmind.core.personal

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.integrations.SafStorageProvider
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Phase 3 Personal Pro Integrations:
 * 1. **Obsidian Vault Live Markdown Exporter**: Renders Personal Journal entries, Ideas, Life Chapters,
 *    and Playbooks into crisp Markdown with YAML frontmatter (date, workflow, mood, tags, life chapter,
 *    and `[[WikiLinks]]`) and writes them directly to a user-pinned Obsidian folder via [SafStorageProvider].
 * 2. **Day One JSON Importer**: Parses Day One JSON exports (`{ "entries": [ ... ] }`) so users migrating
 *    years of journals immediately unlock "On This Day" memories and Personal Deep Synthesis on Day 1.
 */
object PersonalVaultIntegrations {

    private const val PREFS = "mm_personal_integrations"
    private const val KEY_OBSIDIAN_URI = "obsidian_tree_uri"
    private const val KEY_OBSIDIAN_LABEL = "obsidian_folder_label"
    private const val KEY_LAST_SYNC_MS = "obsidian_last_sync_ms"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPinnedObsidianUri(context: Context): String? = prefs(context).getString(KEY_OBSIDIAN_URI, null)

    fun getPinnedObsidianLabel(context: Context): String =
        prefs(context).getString(KEY_OBSIDIAN_LABEL, null).orEmpty().ifBlank { "Obsidian Vault" }

    fun getLastSyncMs(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC_MS, 0L)

    fun savePinnedObsidianVault(context: Context, treeUri: String, label: String) {
        prefs(context).edit()
            .putString(KEY_OBSIDIAN_URI, treeUri)
            .putString(KEY_OBSIDIAN_LABEL, label)
            .apply()
    }

    fun clearPinnedObsidianVault(context: Context) {
        prefs(context).edit().remove(KEY_OBSIDIAN_URI).remove(KEY_OBSIDIAN_LABEL).apply()
    }

    /**
     * Renders a Personal [Note] into Obsidian-compatible Markdown with YAML frontmatter and `[[Backlinks]]`.
     * Pure JVM function so formatting is deterministic and unit-tested.
     */
    fun renderObsidianMarkdown(
        note: Note,
        blocks: List<NoteBlock> = emptyList(),
        linkedTitles: List<String> = emptyList(),
        chapterTitle: String? = null
    ): String {
        val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(note.eventDate ?: note.createdAt))
        val mood = note.metadata[PersonalNoteMeta.MOOD_VIBE]
        val ideaStage = note.metadata[PersonalNoteMeta.IDEA_STAGE]
        val sb = StringBuilder()

        sb.appendLine("---")
        sb.appendLine("title: \"${note.title.replace("\"", "\\\"").ifBlank { "Untitled" }}\"")
        sb.appendLine("date: $isoDate")
        sb.appendLine("type: ${note.workflow.name.lowercase()}")
        sb.appendLine("space: personal")
        if (!mood.isNullOrBlank()) sb.appendLine("mood: ${mood.lowercase()}")
        if (!ideaStage.isNullOrBlank()) sb.appendLine("idea_stage: ${ideaStage.lowercase()}")
        if (!chapterTitle.isNullOrBlank()) sb.appendLine("chapter: \"${chapterTitle.replace("\"", "\\\"")}\"")
        sb.appendLine("meetingmind_id: ${note.id}")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("# ${note.title.ifBlank { isoDate }}")
        sb.appendLine()

        if (blocks.isNotEmpty()) {
            blocks.sortedBy { it.position }.forEach { b ->
                val text = b.content.text.trim()
                if (text.isEmpty() && b.type != NoteBlockType.DIVIDER) return@forEach
                when (b.type) {
                    NoteBlockType.HEADING_1 -> sb.appendLine("# $text")
                    NoteBlockType.HEADING_2 -> sb.appendLine("## $text")
                    NoteBlockType.HEADING_3 -> sb.appendLine("### $text")
                    NoteBlockType.BULLET -> sb.appendLine("- $text")
                    NoteBlockType.NUMBERED -> sb.appendLine("1. $text")
                    NoteBlockType.CHECKLIST -> sb.appendLine("- [${if (b.checked) "x" else " "}] $text")
                    NoteBlockType.QUOTE -> sb.appendLine("> $text")
                    NoteBlockType.DIVIDER -> sb.appendLine("---")
                    else -> sb.appendLine(text)
                }
                sb.appendLine()
            }
        } else if (note.plainText.isNotBlank()) {
            sb.appendLine(note.plainText.trim())
            sb.appendLine()
        }

        if (linkedTitles.isNotEmpty()) {
            sb.appendLine("## Connected Notes")
            linkedTitles.distinct().forEach { t -> sb.appendLine("- [[$t]]") }
        }
        return sb.toString().trimEnd() + "\n"
    }

    /** Generates a clean, filesystem-safe Obsidian filename like `2026-11-24 - Tuesday, Nov 24.md`. */
    fun obsidianFileName(note: Note): String {
        val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(note.eventDate ?: note.createdAt))
        val safeTitle = note.title
            .ifBlank { note.workflow.displayName }
            .replace(Regex("[^a-zA-Z0-9 _-]"), "")
            .trim()
            .take(48)
            .ifBlank { "entry" }
        return "$isoDate - $safeTitle.md"
    }

    /**
     * Syncs a batch of Personal notes into the user's pinned Obsidian Vault SAF directory.
     * Returns the number of Markdown files successfully written.
     */
    suspend fun syncNotesToPinnedObsidian(
        context: Context,
        noteRepository: NoteRepository,
        notes: List<Note>,
        saf: SafStorageProvider = SafStorageProvider()
    ): Int = withContext(Dispatchers.IO) {
        val treeUri = getPinnedObsidianUri(context) ?: return@withContext 0
        var written = 0
        for (note in notes) {
            val doc = noteRepository.getDocument(note.id)
            val links = noteRepository.getLinks(note.id).mapNotNull { l ->
                val targetId = if (l.fromNoteId == note.id) l.toNoteId else l.fromNoteId
                noteRepository.getNote(targetId)?.title?.takeIf { it.isNotBlank() }
            }
            val md = renderObsidianMarkdown(note, doc?.blocks.orEmpty(), links)
            val uri = saf.writeToPinnedVault(
                context = context,
                treeUriString = treeUri,
                fileName = obsidianFileName(note),
                mimeType = "text/markdown",
                content = md.toByteArray(Charsets.UTF_8)
            )
            if (uri != null) written++
        }
        if (written > 0) {
            prefs(context).edit().putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis()).apply()
        }
        written
    }

    // ------------------------------------------------------------------ Day One JSON Importer

    data class ImportedJournalEntry(
        val title: String,
        val body: String,
        val createdAtMs: Long,
        val tags: List<String>,
        val locationLabel: String?
    )

    /**
     * Pure parser for Day One JSON export files (`Journal.json`).
     * Supports standard Day One keys: `entries[]` with `text`, `creationDate`, `tags`, `location.placeName`.
     */
    fun parseDayOneJson(rawJson: String): List<ImportedJournalEntry> {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
        val entries = root.optJSONArray("entries") ?: return emptyList()
        val out = mutableListOf<ImportedJournalEntry>()
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            val rawText = item.optString("text").trim()
            if (rawText.isEmpty()) continue
            val createdMs = parseIso8601Utc(item.optString("creationDate")) ?: System.currentTimeMillis()
            val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val firstLine = lines.firstOrNull().orEmpty().removePrefix("#").trim()
            val title = firstLine.take(72).ifBlank {
                SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(createdMs))
            }
            val body = if (lines.size > 1 && rawText.startsWith("#")) {
                lines.drop(1).joinToString("\n\n")
            } else {
                rawText
            }
            val tagsArr = item.optJSONArray("tags")
            val tags = if (tagsArr != null) {
                (0 until tagsArr.length()).mapNotNull { idx -> tagsArr.optString(idx).takeIf { it.isNotBlank() } }
            } else emptyList()
            val locObj = item.optJSONObject("location")
            val loc = listOfNotNull(
                locObj?.optString("placeName")?.takeIf { it.isNotBlank() },
                locObj?.optString("localityName")?.takeIf { it.isNotBlank() }
            ).joinToString(", ").ifBlank { null }

            out += ImportedJournalEntry(
                title = title,
                body = body,
                createdAtMs = createdMs,
                tags = tags,
                locationLabel = loc
            )
        }
        return out
    }

    /** Imports parsed Day One entries directly into private Personal Journal notes. */
    suspend fun importDayOneEntries(
        noteRepository: NoteRepository,
        rawJson: String
    ): Int = withContext(Dispatchers.IO) {
        val parsed = parseDayOneJson(rawJson)
        var imported = 0
        for (entry in parsed) {
            val epochDay = PersonalRepository.epochDayOf(entry.createdAtMs)
            val blocks = buildList {
                if (!entry.locationLabel.isNullOrBlank()) {
                    add(
                        NoteBlock(
                            id = NoteRepository.newId("block"),
                            noteId = "",
                            position = 0,
                            type = NoteBlockType.QUOTE,
                            content = RichText.plain("Location: ${entry.locationLabel}"),
                            source = BlockSource.IMPORTED
                        )
                    )
                }
                add(
                    NoteBlock(
                        id = NoteRepository.newId("block"),
                        noteId = "",
                        position = size,
                        type = NoteBlockType.PARAGRAPH,
                        content = RichText.plain(entry.body),
                        source = BlockSource.IMPORTED
                    )
                )
            }
            val meta = buildMap {
                put("import_source", "day_one")
                put(PersonalNoteMeta.DAY_PAGE_EPOCH_DAY, epochDay.toString())
                if (entry.tags.isNotEmpty()) put("imported_tags", entry.tags.joinToString(", "))
            }
            noteRepository.createNote(
                workflow = RecordingType.JOURNAL,
                title = entry.title,
                isPrivate = true,
                eventDate = entry.createdAtMs,
                metadata = meta,
                initialBlocks = blocks,
                useTemplate = false
            )
            imported++
        }
        imported
    }

    private fun parseIso8601Utc(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssX"
        )
        for (p in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
