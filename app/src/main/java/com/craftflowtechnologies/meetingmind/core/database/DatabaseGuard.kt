package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Keeps the notes database safe across app updates (docs/PRD_M0.md §4.1).
 *
 * - Before Room migrates the database, a copy of it as it was is kept in `files/db_backups/`.
 * - Opening is checked once at launch. If it fails (a migration missing or broken), the app shows
 *   a recovery screen that can export the raw files. Nothing is ever deleted to make it open:
 *   `fallbackToDestructiveMigration` is gone.
 */
object DatabaseGuard {
    const val NAME = "meetmind_database"
    private const val KEEP = 3

    sealed interface OpenResult {
        data object Ok : OpenResult
        data class Failed(val message: String) : OpenResult
    }

    /** The on-disk schema version, or null when there is no database yet. Never opens it for writing. */
    fun storedVersion(file: File): Int? {
        if (!file.exists()) return null
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        }.getOrNull()
    }

    /**
     * Copies the database aside when it is about to be migrated to [targetVersion]. Returns the copy,
     * or null when no migration is pending. Only the newest [KEEP] copies are kept.
     */
    fun copyBeforeMigration(context: Context, targetVersion: Int, now: Long = System.currentTimeMillis()): File? {
        val db = context.getDatabasePath(NAME)
        val version = storedVersion(db) ?: return null
        if (version >= targetVersion) return null
        val dir = File(context.filesDir, "db_backups").apply { mkdirs() }
        val base = "pre-v$targetVersion-from-v$version-$now"
        val copy = File(dir, "$base.sqlite")
        db.copyTo(copy, overwrite = true)
        listOf("-wal", "-shm").forEach { suffix ->
            File(db.path + suffix).takeIf { it.exists() }?.copyTo(File(dir, "$base.sqlite$suffix"), overwrite = true)
        }
        prune(dir)
        return copy
    }

    private fun prune(dir: File) {
        val copies = dir.listFiles { f -> f.name.startsWith("pre-v") && f.name.endsWith(".sqlite") }.orEmpty()
            .sortedByDescending { it.lastModified() }
        copies.drop(KEEP).forEach { old ->
            old.delete()
            File(old.path + "-wal").delete()
            File(old.path + "-shm").delete()
        }
    }

    /** Opens the database once (running any migrations) and reports whether that worked. */
    fun open(context: Context): OpenResult = runCatching {
        MeetMindDatabase.getInstance(context).openHelper.writableDatabase
        OpenResult.Ok
    }.getOrElse { e ->
        MeetMindDatabase.forget()
        OpenResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    /**
     * Everything needed to get the data back by hand: the database files, the copies taken
     * before migrations, and the notes' attachments. Written to [out] as a zip.
     */
    fun exportRaw(context: Context, out: java.io.OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun add(file: File, name: String) {
                if (!file.isFile) return
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val db = context.getDatabasePath(NAME)
            listOf("", "-wal", "-shm").forEach { add(File(db.path + it), "database/$NAME$it") }
            File(context.filesDir, "db_backups").listFiles().orEmpty().forEach { add(it, "db_backups/${it.name}") }
            val notes = File(context.filesDir, "notes")
            notes.walkTopDown().filter { it.isFile }.forEach { add(it, "files/notes/" + it.relativeTo(notes).path) }
            listOf("faith_extras.db").forEach { name -> add(context.getDatabasePath(name), "database/$name") }
        }
    }
}
