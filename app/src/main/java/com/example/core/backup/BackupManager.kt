package com.example.core.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import com.example.BuildConfig
import com.example.ai.cloud.GeminiCredentialStore
import com.example.core.database.DatabaseGuard
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.faith.FaithStore
import com.example.core.scripture.BibleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Why a backup can't be restored, in words for the person. */
class BackupRejected(message: String) : Exception(message)

/**
 * Whole-library backup and restore (docs/PRD_M0.md §4.3).
 *
 * A backup is a zip of the notes database, the faith database, Bible highlights, settings and the
 * app's own files, with a manifest of checksums. Restore checks every checksum into a staging
 * folder first, saves the current data aside, and only then replaces anything. The app restarts
 * afterwards so nothing holds the old data open.
 */
class BackupManager(private val context: Context) {

    private val app = context.applicationContext

    // ---------------------------------------------------------------- backup

    suspend fun write(out: OutputStream, options: BackupOptions = BackupOptions()): BackupManifest = withContext(Dispatchers.IO) {
        val checksums = linkedMapOf<String, String>()
        val staged = File(app.cacheDir, "backup-out").apply { deleteRecursively(); mkdirs() }
        try {
            // 1. Everything goes to a staging folder first: the manifest must be the zip's first
            //    entry, and it needs every checksum.
            val parts = linkedMapOf<String, File>()
            copyDatabase(parts, staged)
            app.getDatabasePath(FaithStore.FILE).takeIf { it.exists() }?.let { file ->
                val db = FaithStore.get(app).writableDatabase
                db.beginTransaction()
                try { parts[BackupFormat.FAITH_DB] = file.copyTo(File(staged, "faith.db"), overwrite = true) } finally { db.endTransaction() }
            }
            runCatching { BibleStore.get(app).exportHighlights() }.getOrNull()?.let {
                parts[BackupFormat.HIGHLIGHTS] = File(staged, "highlights.json").apply { writeText(it) }
            }
            parts[BackupFormat.PREFS] = File(staged, "prefs.json").apply { writeText(PrefsCodec.encode(UserPreferencesManager(app).exportAll())) }
            if (options.includeApiKey) {
                GeminiCredentialStore(app).getApiKey()?.let { key ->
                    parts[BackupFormat.CREDENTIALS] = File(staged, "credentials.json").apply { writeText(PrefsCodec.encode(mapOf("gemini_api_key" to key))) }
                }
            }
            val dirs = BackupFormat.CORE_DIRS +
                listOfNotNull(BackupFormat.RECORDINGS_DIR.takeIf { options.includeRecordings }, BackupFormat.DEVOTIONAL_AUDIO_DIR.takeIf { options.includeDevotionalAudio })
            dirs.forEach { dir ->
                val root = File(app.filesDir, dir)
                root.walkTopDown().filter { it.isFile }.forEach { f -> parts[BackupFormat.FILES + dir + "/" + f.relativeTo(root).invariantSeparatorsPath] = f }
            }
            parts.forEach { (name, file) -> checksums[name] = sha256(file) }

            val manifest = BackupManifest(
                formatVersion = BackupFormat.VERSION,
                appVersion = BuildConfig.VERSION_NAME,
                schemaVersion = MeetMindDatabase.VERSION,
                createdAt = System.currentTimeMillis(),
                device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                filesDir = app.filesDir.absolutePath,
                counts = counts(),
                options = options,
                checksums = checksums
            )
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST)); zip.write(manifest.toJson().toByteArray()); zip.closeEntry()
                parts.forEach { (name, file) ->
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            manifest
        } finally {
            staged.deleteRecursively()
        }
    }

    /**
     * A consistent copy of the notes database. Holding a write transaction keeps every writer out
     * while the main file and its write-ahead log are copied, so the pair is one moment in time.
     */
    private fun copyDatabase(parts: MutableMap<String, File>, staged: File) {
        val room = MeetMindDatabase.getInstance(app)
        val db = room.openHelper.writableDatabase
        runCatching { db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
        val file = app.getDatabasePath(DatabaseGuard.NAME)
        val copy = File(staged, "main.db")
        db.beginTransaction()
        try {
            file.copyTo(copy, overwrite = true)
            File(file.path + "-wal").takeIf { it.exists() && it.length() > 0 }?.copyTo(File(copy.path + "-wal"), overwrite = true)
        } finally { db.endTransaction() }
        // Fold any log into the copy so the backup holds a single self-contained file.
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { c ->
            c.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            c.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        }
        File(copy.path + "-wal").delete(); File(copy.path + "-shm").delete()
        parts[BackupFormat.MAIN_DB] = copy
    }

    private fun counts(): Map<String, Int> = runCatching {
        val db = MeetMindDatabase.getInstance(app).openHelper.readableDatabase
        fun count(sql: String) = db.query(sql).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        mapOf(
            "notes" to count("SELECT COUNT(*) FROM notes WHERE isDraft = 0"),
            "notebooks" to count("SELECT COUNT(*) FROM notebooks"),
            "recordings" to count("SELECT COUNT(*) FROM meetings"),
            "attachments" to count("SELECT COUNT(*) FROM attachments"),
            "versions" to count("SELECT COUNT(*) FROM note_versions")
        )
    }.getOrDefault(emptyMap())

    // ---------------------------------------------------------------- inspect

    /** Reads and checks a backup's manifest without changing anything. */
    suspend fun inspect(input: InputStream): BackupManifest = withContext(Dispatchers.IO) {
        ZipInputStream(input).use { zip ->
            val first = zip.nextEntry ?: throw BackupRejected("This file is empty.")
            if (first.name != BackupFormat.MANIFEST) throw BackupRejected("This isn't a MeetingMind backup.")
            val manifest = runCatching { BackupManifest.fromJson(zip.readBytes().toString(Charsets.UTF_8)) }
                .getOrElse { throw BackupRejected("This backup's description is damaged.") }
            check(manifest)
            manifest
        }
    }

    private fun check(manifest: BackupManifest) {
        if (manifest.formatVersion > BackupFormat.VERSION) throw BackupRejected("This backup was made by a newer MeetingMind. Update the app, then restore it.")
        if (manifest.schemaVersion > MeetMindDatabase.VERSION) throw BackupRejected("This backup was made by a newer MeetingMind (${manifest.appVersion}). Update the app, then restore it.")
        if (BackupFormat.MAIN_DB !in manifest.checksums) throw BackupRejected("This backup has no notes database in it.")
    }

    // ---------------------------------------------------------------- restore

    /**
     * Replaces everything with the backup in [input]. Every entry is checked against its checksum
     * before anything is touched, and the current data is saved to `files/db_backups/` first.
     * Recordings not in the backup are left where they are. Call [restartApp] afterwards.
     */
    suspend fun restore(input: InputStream): BackupManifest = withContext(Dispatchers.IO) {
        val staging = File(app.cacheDir, "restore-staging").apply { deleteRecursively(); mkdirs() }
        try {
            val manifest = extract(input, staging)
            // Save what's here now, so a wrong restore can be undone by hand.
            val aside = File(app.filesDir, "db_backups").apply { mkdirs() }
            runCatching { File(aside, "before-restore-${System.currentTimeMillis()}.${BackupFormat.EXTENSION}").outputStream().use { write(it) } }
            aside.let { dir ->
                dir.listFiles { f -> f.name.startsWith("before-restore-") }.orEmpty().sortedByDescending { it.name }.drop(2).forEach { it.delete() }
            }

            val oldFiles = manifest.filesDir
            val newFiles = app.filesDir.absolutePath
            fun move(value: String) = if (oldFiles.isNotBlank() && oldFiles != newFiles) value.replace(oldFiles, newFiles) else value

            // Paths stored in the database point into the old install's folder; move them to ours.
            val stagedDb = File(staging, BackupFormat.MAIN_DB)
            if (oldFiles.isNotBlank() && oldFiles != newFiles) {
                SQLiteDatabase.openDatabase(stagedDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { db -> movePaths(db, oldFiles, newFiles) }
            }

            // The notes database.
            MeetMindDatabase.forget()
            val dbFile = app.getDatabasePath(DatabaseGuard.NAME)
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            stagedDb.copyTo(dbFile, overwrite = true)

            // The faith database.
            File(staging, BackupFormat.FAITH_DB).takeIf { it.exists() }?.let { staged ->
                runCatching { FaithStore.get(app).close() }
                val faith = app.getDatabasePath(FaithStore.FILE)
                listOf("", "-wal", "-shm", "-journal").forEach { File(faith.path + it).delete() }
                staged.copyTo(faith, overwrite = true)
            }

            File(staging, BackupFormat.HIGHLIGHTS).takeIf { it.exists() }?.let { runCatching { BibleStore.get(app).importHighlights(it.readText()) } }

            File(staging, BackupFormat.PREFS).takeIf { it.exists() }?.let {
                UserPreferencesManager(app).importAll(PrefsCodec.decode(it.readText(), ::move))
            }
            File(staging, BackupFormat.CREDENTIALS).takeIf { it.exists() }?.let {
                (PrefsCodec.decode(it.readText())["gemini_api_key"] as? String)?.let { key -> GeminiCredentialStore(app).setApiKey(key) }
            }

            // Files: each folder the backup holds replaces ours; folders it doesn't hold stay.
            val stagedFiles = File(staging, BackupFormat.FILES.trimEnd('/'))
            val folders = BackupFormat.CORE_DIRS +
                listOfNotNull(BackupFormat.RECORDINGS_DIR.takeIf { manifest.options.includeRecordings }, BackupFormat.DEVOTIONAL_AUDIO_DIR.takeIf { manifest.options.includeDevotionalAudio })
            folders.forEach { dir ->
                val target = File(app.filesDir, dir)
                target.deleteRecursively()
                File(stagedFiles, dir).takeIf { it.exists() }?.copyRecursively(target, overwrite = true)
            }
            manifest
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Unzips into [staging], refusing unsafe names and anything whose checksum doesn't match. */
    private fun extract(input: InputStream, staging: File): BackupManifest {
        var manifest: BackupManifest? = null
        val seen = mutableSetOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (entry.name == BackupFormat.MANIFEST) {
                    manifest = runCatching { BackupManifest.fromJson(zip.readBytes().toString(Charsets.UTF_8)) }
                        .getOrElse { throw BackupRejected("This backup's description is damaged.") }
                    check(manifest!!)
                    continue
                }
                val m = manifest ?: throw BackupRejected("This isn't a MeetingMind backup.")
                if (!BackupFormat.isSafeEntryName(entry.name)) throw BackupRejected("This backup contains a file with an unsafe name.")
                val expected = m.checksums[entry.name] ?: throw BackupRejected("This backup contains a file it doesn't list.")
                val file = File(staging, entry.name).apply { parentFile?.mkdirs() }
                if (!file.canonicalPath.startsWith(staging.canonicalPath + File.separator)) throw BackupRejected("This backup contains a file with an unsafe name.")
                val digest = MessageDigest.getInstance("SHA-256")
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                }
                if (hex(digest.digest()) != expected) throw BackupRejected("This backup is damaged (${entry.name} doesn't match). Nothing was changed.")
                seen += entry.name
            }
        }
        val m = manifest ?: throw BackupRejected("This isn't a MeetingMind backup.")
        val missing = m.checksums.keys - seen
        if (missing.isNotEmpty()) throw BackupRejected("This backup is incomplete (${missing.size} files missing). Nothing was changed.")
        return m
    }

    /** Starts the app afresh, so everything opens the restored data. */
    fun restartApp() {
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launch?.let { app.startActivity(it) }
        Runtime.getRuntime().exit(0)
    }

    companion object {
        /** Columns that hold absolute paths into the app's files folder. */
        val PATH_COLUMNS = listOf(
            "attachments" to "path",
            "meetings" to "audioFilePath",
            "notes" to "metadataJson",
            "note_blocks" to "payloadJson"
        )

        /**
         * Moves every stored path from [oldDir] to [newDir]. JSON columns hold paths with `/`
         * written as `\/`, so both spellings are replaced.
         */
        internal fun movePaths(db: SQLiteDatabase, oldDir: String, newDir: String) {
            val spellings = listOf(oldDir to newDir, oldDir.replace("/", "\\/") to newDir.replace("/", "\\/"))
            PATH_COLUMNS.forEach { (table, column) ->
                spellings.forEach { (from, to) ->
                    runCatching { db.execSQL("UPDATE `$table` SET `$column` = REPLACE(`$column`, ?, ?)", arrayOf(from, to)) }
                }
            }
        }

        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
