package com.example.core.scripture

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.notify.AppNotifications
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads a whole translation to the phone, for offline reading and search.
 *
 * Runs as background work with a progress notification, so it carries on while the phone is used
 * for something else. Chapters already stored are skipped, so a download that stops (no signal,
 * the phone restarts, the person taps Pause) picks up where it left off. A few chapters are
 * fetched at a time — fast, without hammering the service.
 */
class BibleDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val bibleId get() = inputData.getInt(KEY_BIBLE_ID, 0)
    private var label = "Bible"

    override suspend fun doWork(): Result {
        if (bibleId == 0) return Result.failure()
        val library = ScriptureService.library(applicationContext)
        val info = library.info(bibleId) ?: return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        label = info.abbreviation
        if (!info.offlineAllowed) {
            return Result.failure(workDataOf(KEY_ERROR to "${info.abbreviation} can be read online, but its licence doesn't allow keeping a copy on the phone."))
        }
        AppNotifications.ensureChannels(applicationContext)
        setForeground(foregroundInfo(0, 1, null))

        // Open translations from the Free Use Bible API arrive in one file, streamed book by book.
        if (HelloAo.isHelloAo(bibleId)) {
            var last = 0L
            val ok = runCatching {
                library.importHelloAo(bibleId) { done, total ->
                    val now = System.currentTimeMillis()
                    if (now - last > 600 || done == total) {
                        last = now
                        setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_UNIT to "books"))
                        runCatching { setForegroundAsync(foregroundInfo(done, total, null)) }
                    }
                }
            }.getOrDefault(false)
            return if (ok) {
                AppNotifications.bibleDownloadFinished(applicationContext, bibleId, info.abbreviation, ok = true); Result.success()
            } else if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else { AppNotifications.bibleDownloadFinished(applicationContext, bibleId, info.abbreviation, ok = false); Result.failure() }
        }

        val work = BibleBooks.all.filter { info.has(it) }.flatMap { b -> (1..b.chapterCount).map { b to it } }
        val done = AtomicInteger(work.count { (b, c) -> library.hasChapter(bibleId, b, c) })
        val failed = AtomicInteger(0)
        var lastUpdate = 0L
        val gate = Semaphore(PARALLEL)

        coroutineScope {
            work.filterNot { (b, c) -> library.hasChapter(bibleId, b, c) }.map { (book, chapter) ->
                async {
                    gate.withPermit {
                        if (isStopped) return@withPermit
                        when (library.chapter(bibleId, book, chapter)) {
                            is ChapterResult.Found -> done.incrementAndGet()
                            is ChapterResult.Unavailable -> failed.incrementAndGet()
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 800) {
                            lastUpdate = now
                            setProgress(workDataOf(KEY_DONE to done.get(), KEY_TOTAL to work.size))
                            setForeground(foregroundInfo(done.get(), work.size, book.name))
                        }
                    }
                }
            }.forEach { it.await() }
        }

        val store = BibleStore.get(applicationContext)
        return if (failed.get() == 0 && done.get() >= work.size) {
            store.markComplete(bibleId, true)
            AppNotifications.bibleDownloadFinished(applicationContext, bibleId, info.abbreviation, ok = true)
            Result.success()
        } else if (runAttemptCount < MAX_ATTEMPTS) {
            // Some chapters didn't arrive (usually the connection); try again later for the rest.
            Result.retry()
        } else {
            AppNotifications.bibleDownloadFinished(applicationContext, bibleId, info.abbreviation, ok = false)
            Result.failure(workDataOf(KEY_ERROR to "${failed.get()} chapters couldn't be downloaded."))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 1, null)

    private fun foregroundInfo(done: Int, total: Int, book: String?): ForegroundInfo {
        AppNotifications.ensureChannels(applicationContext)
        val notification = AppNotifications.bibleDownloadProgress(
            applicationContext, label, done, total, book,
            pauseIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        )
        val notificationId = AppNotifications.bibleDownloadId(bibleId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_BIBLE_ID = "bibleId"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val KEY_UNIT = "unit"
        const val TAG_ALL = "meetmind_bible_download"
        private const val PARALLEL = 4
        private const val MAX_ATTEMPTS = 10

        fun uniqueName(bibleId: Int) = "bible_download_$bibleId"

        fun enqueue(context: Context, bibleId: Int, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<BibleDownloadWorker>()
                .setInputData(workDataOf(KEY_BIBLE_ID to bibleId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_ALL)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(bibleId), ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context, bibleId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(bibleId))
        }
    }
}
