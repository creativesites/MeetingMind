package com.craftflowtechnologies.meetingmind.core.documents

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What a person sees about a document's reading. Built from the inbox row, in one place. */
sealed interface DocumentReadState {
    /** Not a document, or never queued. */
    data object NotStarted : DocumentReadState

    /** Queued, not yet started. */
    data object Pending : DocumentReadState

    /** Reading now. [pageCount] is null until the document has been opened. */
    data class Reading(val pageCount: Int?, val readPages: Int) : DocumentReadState

    /** Read. Partial when pages past the 300-page limit were left unread. */
    data class Done(val pageCount: Int, val readPages: Int) : DocumentReadState {
        val partial: Boolean get() = readPages < pageCount
    }

    /** Read, but there were no words anywhere in it. Filed by name. */
    data class NoText(val pageCount: Int?) : DocumentReadState

    /** Password protected. */
    data object Locked : DocumentReadState

    /** Could not be read. Filed by name. */
    data object Failed : DocumentReadState

    /** The plain sentence to show under the document, or null when there is nothing to say. */
    fun message(): String? = when (this) {
        is Locked -> "Can't read a locked PDF. Open it in another app, or file it as is."
        is NoText -> "No readable text. Filed by name."
        is Failed -> "This file couldn't be read. Filed by name."
        is Done -> if (partial) "Read the first $readPages of $pageCount pages. The rest was not read." else null
        is Reading, Pending, NotStarted -> null
    }

    companion object {
        /** Maps a stored row. [statusName] is `InboxItemEntity.readStatus`; a missing row is NotStarted. */
        fun from(statusName: String?, pageCount: Int?, readPages: Int): DocumentReadState =
            when (ReadStatus.parse(statusName)) {
                ReadStatus.NONE -> NotStarted
                ReadStatus.PENDING -> Pending
                ReadStatus.READING -> Reading(pageCount, readPages)
                ReadStatus.DONE -> Done(pageCount ?: readPages, readPages)
                ReadStatus.NO_TEXT -> NoText(pageCount)
                ReadStatus.LOCKED -> Locked
                ReadStatus.FAILED -> Failed
            }
    }
}

/**
 * The public face of document reading. [enqueue] starts the worker for an item; [observe] streams its
 * state. Reading always happens on the phone, whatever the processing profile says.
 */
object DocumentReading {

    fun workName(itemId: String): String = "meetmind_document_read_$itemId"

    /** Queues the read for [itemId]. A read that is already queued or running is kept, not restarted. */
    fun enqueue(context: Context, itemId: String) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(itemId),
            ExistingWorkPolicy.KEEP,
            request(itemId)
        )
    }

    /** Reads again from the start, for a document that was locked, failed or read with an old version. */
    suspend fun reread(context: Context, itemId: String) {
        val database = withContext(Dispatchers.IO) { MeetMindDatabase.getInstance(context.applicationContext) }
        withContext(Dispatchers.IO) { DocumentRows(database).setState(itemId, ReadStatus.PENDING, null, 0) }
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(itemId),
            ExistingWorkPolicy.REPLACE,
            request(itemId)
        )
    }

    /**
     * Emits the item's reading state now, then each time the inbox table changes (including page
     * progress). Emits NotStarted when there is no such item.
     */
    fun observe(context: Context, itemId: String): Flow<DocumentReadState> = flow {
        val database = withContext(Dispatchers.IO) { MeetMindDatabase.getInstance(context.applicationContext) }
        emitAll(
            database.inboxDao().observeById(itemId)
                .map { item -> DocumentReadState.from(item?.readStatus, item?.pageCount, item?.readPages ?: 0) }
                .distinctUntilChanged()
        )
    }

    private fun request(itemId: String) = OneTimeWorkRequestBuilder<DocumentReadWorker>()
        .setInputData(workDataOf(DocumentReadWorker.KEY_ITEM_ID to itemId))
        .build()
}
