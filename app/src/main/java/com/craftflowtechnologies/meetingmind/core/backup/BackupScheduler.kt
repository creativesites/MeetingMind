package com.craftflowtechnologies.meetingmind.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

enum class BackupSchedule(val label: String, val days: Long) { OFF("Off", 0), DAILY("Every day", 1), WEEKLY("Every week", 7) }

/** Automatic backups' settings and last result, kept on this phone only. */
data class AutoBackupState(
    val schedule: BackupSchedule = BackupSchedule.OFF,
    val folder: Uri? = null,
    val keep: Int = 7,
    val includeRecordings: Boolean = false,
    val lastAt: Long? = null,
    val lastBytes: Long? = null,
    val lastError: String? = null
)

/**
 * Automatic backups to a folder the person chose with the system picker — Google Drive, Downloads,
 * an SD card (docs/PRD_M0.md §4.3). Keeps the newest [AutoBackupState.keep] and deletes older ones
 * it made. Timing drift doesn't matter here, so it's periodic work.
 */
object BackupScheduler {
    private const val WORK = "auto-backup"
    private const val PREFS = "backup"

    fun state(context: Context): AutoBackupState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AutoBackupState(
            schedule = runCatching { BackupSchedule.valueOf(p.getString("schedule", null) ?: "OFF") }.getOrDefault(BackupSchedule.OFF),
            folder = p.getString("folder", null)?.let(Uri::parse),
            keep = p.getInt("keep", 7),
            includeRecordings = p.getBoolean("recordings", false),
            lastAt = p.getLong("lastAt", 0L).takeIf { it > 0 },
            lastBytes = p.getLong("lastBytes", 0L).takeIf { it > 0 },
            lastError = p.getString("lastError", null)
        )
    }

    fun update(context: Context, state: AutoBackupState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("schedule", state.schedule.name)
            .putString("folder", state.folder?.toString())
            .putInt("keep", state.keep.coerceIn(1, 60))
            .putBoolean("recordings", state.includeRecordings)
            .apply()
        sync(context, state)
    }

    fun sync(context: Context, state: AutoBackupState = state(context)) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (state.schedule == BackupSchedule.OFF || state.folder == null) { wm.cancelUniqueWork(WORK); return }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(state.schedule.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    internal fun recordResult(context: Context, bytes: Long?, error: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (error == null) { putLong("lastAt", System.currentTimeMillis()); putLong("lastBytes", bytes ?: 0L); remove("lastError") }
            else putString("lastError", error)
        }.apply()
    }

    /** Makes one backup into [folder] now and prunes older ones. Returns its size. */
    suspend fun backUpTo(context: Context, folder: Uri, keep: Int, options: BackupOptions): Long {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val name = BackupFormat.fileName(LocalDateTime.now())
        val doc = DocumentsContract.createDocument(resolver, parent, BackupFormat.MIME, name)
            ?: error("Couldn't create a file in the backup folder.")
        try {
            resolver.openOutputStream(doc)?.use { BackupManager(context).write(it, options) } ?: error("Couldn't write to the backup folder.")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        val size = resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        prune(context, folder, keep)
        return size
    }

    private fun prune(context: Context, folder: Uri, keep: Int) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val ours = mutableListOf<Pair<String, String>>() // name to document id
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                if (name.startsWith("MeetingMind-") && name.endsWith("." + BackupFormat.EXTENSION)) ours += name to c.getString(1)
            }
        }
        // The file name carries the date and time, so name order is age order.
        ours.sortedByDescending { it.first }.drop(keep).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val state = BackupScheduler.state(applicationContext)
        val folder = state.folder ?: return Result.success()
        return try {
            val size = BackupScheduler.backUpTo(applicationContext, folder, state.keep, BackupOptions(includeRecordings = state.includeRecordings))
            BackupScheduler.recordResult(applicationContext, size, null)
            Result.success()
        } catch (e: Exception) {
            BackupScheduler.recordResult(applicationContext, null, e.message ?: "The backup didn't finish.")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
