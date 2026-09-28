package com.example.core.backup

import android.content.Context
import com.example.core.database.MeetMindDatabase
import com.example.core.export.MarkdownDocumentRenderer
import com.example.core.export.NoteExportOptions
import com.example.core.export.NoteExportService
import com.example.core.model.Note
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Every note as Markdown in a zip: a folder per notebook, one `.md` per note with its details in
 * YAML front matter, and its pictures beside it (docs/PRD_M0.md §4.4). Opens in Obsidian as a vault.
 * It's the person's own library, so private sections are included.
 */
class MarkdownVaultExporter(private val context: Context) {

    suspend fun write(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val database = MeetMindDatabase.getInstance(context)
        val repo = NoteRepository(context, database)
        // Verse text isn't fetched: the export stays fast and offline, and references are kept.
        val exporter = NoteExportService(context, database, passageSource = null)
        val notebooks = database.notebookDao().getAll().associateBy { it.id }
        val notes = database.noteDao().getActiveOnce() + database.noteDao().getAll().filter { it.archivedAt != null && it.deletedAt == null && !it.isDraft }
        val usedNames = mutableSetOf<String>()
        var written = 0
        ZipOutputStream(out).use { zip ->
            for (entity in notes.distinctBy { it.id }) {
                val doc = repo.getDocument(entity.id) ?: continue
                val folder = safeName(entity.notebookId?.let { notebooks[it]?.name } ?: "Unfiled").ifBlank { "Unfiled" } +
                    if (entity.archivedAt != null) "/Archived" else ""
                val base = unique("$folder/${safeName(doc.note.title).ifBlank { "Untitled" }}", usedNames)
                val document = exporter.build(doc, NoteExportOptions(includePrivateSections = true))
                val markdown = frontMatter(doc.note, notebooks[entity.notebookId]?.name, doc.tags.map { it.name }) + MarkdownDocumentRenderer.render(document)
                zip.putNextEntry(ZipEntry("$base.md")); zip.write(markdown.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                doc.attachments.forEach { a ->
                    val file = File(a.path)
                    if (!file.isFile) return@forEach
                    val name = "$folder/${file.name}"
                    if (!usedNames.add(name)) return@forEach
                    zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
                written++
            }
        }
        written
    }

    companion object {
        internal fun frontMatter(note: Note, notebook: String?, tags: List<String>): String = buildString {
            fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString()
            fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            appendLine("---")
            appendLine("title: ${quote(note.title.ifBlank { "Untitled" })}")
            appendLine("created: ${date(note.createdAt)}")
            appendLine("updated: ${date(note.updatedAt)}")
            note.eventDate?.let { appendLine("event_date: ${date(it)}") }
            notebook?.let { appendLine("notebook: ${quote(it)}") }
            appendLine("workflow: ${note.workflow.name.lowercase()}")
            if (note.pinned) appendLine("pinned: true")
            if (tags.isNotEmpty()) appendLine("tags: [${tags.joinToString(", ") { quote(it) }}]")
            appendLine("---")
            appendLine()
        }

        /** A file or folder name that works on every system. */
        internal fun safeName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80)

        internal fun unique(path: String, used: MutableSet<String>): String {
            var candidate = path
            var n = 2
            while (!used.add("$candidate.md")) candidate = "$path ($n)".also { n++ }
            return candidate
        }
    }
}
