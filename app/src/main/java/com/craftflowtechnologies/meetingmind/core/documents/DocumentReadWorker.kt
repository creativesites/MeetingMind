package com.craftflowtechnologies.meetingmind.core.documents

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.craftflowtechnologies.meetingmind.core.database.DocumentPageEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.notify.AppNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads one inbox document in the background (spec §R4, step 2). Modelled on NoteAiWorker: the
 * state lives in the database, so it survives the app closing, and WorkManager re-runs an unfinished
 * read after process death.
 *
 * State path: PENDING → READING (page progress as it goes) → DONE, NO_TEXT, LOCKED or FAILED.
 * Cancelled work goes back to PENDING so it can be queued again. Pages are replaced as a set, so a
 * re-run never leaves stale rows behind.
 *
 * Writes go through [DocumentRows], not the DAOs, so this worker never overwrites columns another
 * writer changed in the meantime (for example the proposal).
 */
class DocumentReadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val database = MeetMindDatabase.getInstance(applicationContext)
        val rows = DocumentRows(database)

        val item = withContext(Dispatchers.IO) { database.inboxDao().get(itemId) } ?: return Result.failure()
        val kind = DocumentKind.fromItemKind(item.kind) ?: return Result.success()
        if (ReadStatus.parse(item.readStatus) == ReadStatus.DONE) return Result.success()

        val file = item.uri?.let { File(it) }
        if (file == null || !file.isFile) {
            withContext(Dispatchers.IO) { rows.setState(itemId, ReadStatus.FAILED, null, 0) }
            return Result.success()
        }

        withContext(Dispatchers.IO) { rows.setState(itemId, ReadStatus.READING, null, 0) }

        val outcome = try {
            DocumentTextExtractor(AndroidDocumentSourceOpener(applicationContext)).extract(file, kind, file.length()) { pageCount, readPages ->
                withContext(Dispatchers.IO) { rows.setState(itemId, ReadStatus.READING, pageCount, readPages) }
                // Long documents run as a foreground notification, so Android keeps the read alive.
                if (pageCount > DocumentLimits.FOREGROUND_PAGE_THRESHOLD && (readPages == 0 || readPages % PROGRESS_EVERY == 0)) {
                    showForeground(readPages, pageCount)
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { rows.setState(itemId, ReadStatus.PENDING, null, 0) }
            throw e
        } catch (e: Exception) {
            ExtractionOutcome.Failed(FailureReason.CORRUPT)
        } catch (e: OutOfMemoryError) {
            // Too big for this phone: the item says it couldn't be read, and the app keeps running.
            ExtractionOutcome.Failed(FailureReason.CORRUPT)
        }

        withContext(Dispatchers.IO) {
            when (outcome) {
                is ExtractionOutcome.Read -> {
                    // Pages first, then the status: DONE is only ever seen with its pages in place.
                    rows.writePages(itemId, outcome.pages)
                    val compatText = if (outcome.hasText) compatibilityText(outcome.pages) else null
                    rows.setState(
                        itemId,
                        if (outcome.hasText) ReadStatus.DONE else ReadStatus.NO_TEXT,
                        outcome.pageCount,
                        outcome.pages.size,
                        compatText
                    )
                }
                ExtractionOutcome.Locked -> rows.setState(itemId, ReadStatus.LOCKED, null, 0)
                is ExtractionOutcome.Failed -> rows.setState(itemId, ReadStatus.FAILED, null, 0)
            }
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 1)

    /** Promotes the work to a foreground service. If Android refuses, the read carries on without it. */
    private suspend fun showForeground(readPages: Int, pageCount: Int) {
        try {
            AppNotifications.ensureChannels(applicationContext)
            setForeground(foregroundInfo(readPages, pageCount))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Background start not allowed on this device right now: keep reading in the background.
        }
    }

    private fun foregroundInfo(readPages: Int, pageCount: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, AppNotifications.CHANNEL_WORK)
            .setContentTitle("Reading a document")
            .setContentText(if (pageCount > 1) "Page $readPages of $pageCount · on this phone" else "Reading on this phone")
            .setSmallIcon(android.R.drawable.ic_menu_day)
            .setProgress(pageCount.coerceAtLeast(1), readPages, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun compatibilityText(pages: List<ExtractedPage>): String =
        pages.filter { it.text.isNotBlank() }
            .joinToString("\n\n") { it.text }
            .take(DocumentLimits.COMPAT_TEXT_CHARS)

    companion object {
        const val KEY_ITEM_ID = "documentItemId"
        /** Unused by the other foreground notifications (see AppNotifications.ID_PROCESSING). */
        const val NOTIFICATION_ID = 2071
        /** The foreground notification is refreshed every this many pages. */
        private const val PROGRESS_EVERY = 10
    }
}

/**
 * Writes reading state with one targeted UPDATE, and document pages through their DAO. The state goes
 * through InboxDao.updateReadState, a targeted UPDATE, so a read never overwrites other columns.
 */
internal class DocumentRows(private val database: MeetMindDatabase) {

    /** Sets the read state. A non-null [text] replaces the compatibility text; null keeps it. */
    suspend fun setState(itemId: String, status: ReadStatus, pageCount: Int?, readPages: Int, text: String? = null) {
        database.inboxDao().updateReadState(itemId, status.name, pageCount, readPages, text)
    }

    /** Replaces every page of the item. Runs inside a read, so the set is always the whole document. */
    suspend fun writePages(itemId: String, pages: List<ExtractedPage>) {
        val dao = database.documentPageDao()
        dao.deleteForItem(itemId)
        pages.chunked(PAGE_BATCH).forEach { batch ->
            dao.upsertPages(
                batch.map { page ->
                    DocumentPageEntity(
                        itemId = itemId,
                        page = page.page,
                        text = page.text,
                        source = page.source.name,
                        confidence = page.confidence,
                        boxesJson = page.boxesJson
                    )
                }
            )
        }
    }

    private companion object {
        const val PAGE_BATCH = 50
    }
}
