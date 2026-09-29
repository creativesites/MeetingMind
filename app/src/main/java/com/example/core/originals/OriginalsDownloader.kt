package com.example.core.originals

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.net.Net
import okhttp3.Request
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Downloads a pack from STEPBible and keeps it: each file is streamed line by line, parsed, and
 * saved in batches, so nothing large is held in memory and an interrupted download can be run
 * again (rows are replaced, not duplicated). The pack counts as installed only when every file
 * and its lexicon are in.
 */
class OriginalsDownloader(private val store: OriginalsStore, private val open: (String) -> BufferedReader = ::openUrl) {

    /** [onProgress] gets (files done, files total, current file's label). */
    fun install(pack: OriginalsPack, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): Boolean {
        val all = pack.files + pack.lexicon
        all.forEachIndexed { i, file ->
            onProgress(i, all.size, if (file == pack.lexicon) "Dictionary" else file.substringBefore(" -"))
            open(pack.fileUrl(file)).use { reader ->
                if (file == pack.lexicon) ingestLexicon(reader) else ingestWords(reader, pack.hebrew)
            }
        }
        store.markInstalled(pack)
        onProgress(all.size, all.size, "Done")
        return true
    }

    internal fun ingestWords(reader: BufferedReader, hebrew: Boolean): Int {
        val batch = ArrayList<OriginalWord>(BATCH)
        var n = 0
        reader.lineSequence().forEach { line ->
            val w = if (hebrew) OriginalsParser.hebrew(line) else OriginalsParser.greek(line)
            if (w != null) {
                batch += w; n++
                if (batch.size >= BATCH) { store.saveWords(batch); batch.clear() }
            }
        }
        if (batch.isNotEmpty()) store.saveWords(batch)
        return n
    }

    internal fun ingestLexicon(reader: BufferedReader): Int {
        val batch = ArrayList<LexiconEntry>(BATCH)
        var n = 0
        reader.lineSequence().forEach { line ->
            OriginalsParser.lexicon(line)?.let { batch += it; n++ }
            if (batch.size >= BATCH) { store.saveLexicon(batch); batch.clear() }
        }
        if (batch.isNotEmpty()) store.saveLexicon(batch)
        return n
    }

    companion object {
        private const val BATCH = 2000
        fun openUrl(url: String): BufferedReader {
            val response = Net.base.newBuilder().readTimeout(120, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) { response.close(); throw java.io.IOException("Couldn't download (${response.code})") }
            return response.body!!.byteStream().bufferedReader(Charsets.UTF_8)
        }
    }
}

class OriginalsDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val pack = runCatching { OriginalsPack.valueOf(inputData.getString(KEY_PACK).orEmpty()) }.getOrNull() ?: return Result.failure()
        return try {
            OriginalsDownloader(OriginalsStore.get(applicationContext)).install(pack) { done, total, label ->
                setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_LABEL to label))
            }
            Result.success()
        } catch (e: java.io.IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "The download stopped.")))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "The download failed.")))
        }
    }

    companion object {
        const val KEY_PACK = "pack"; const val KEY_DONE = "done"; const val KEY_TOTAL = "total"; const val KEY_LABEL = "label"; const val KEY_ERROR = "error"
        fun name(pack: OriginalsPack) = "originals_${pack.name}"
        fun enqueue(context: Context, pack: OriginalsPack, wifiOnly: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<OriginalsDownloadWorker>()
                .setInputData(workDataOf(KEY_PACK to pack.name))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name(pack), ExistingWorkPolicy.KEEP, request)
        }
    }
}
