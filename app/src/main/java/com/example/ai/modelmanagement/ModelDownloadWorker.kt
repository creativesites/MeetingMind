package com.example.ai.modelmanagement

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
import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.core.database.MeetMindDatabase
import com.example.core.notify.AppNotifications
import com.example.core.repository.ModelRepository
import java.util.concurrent.TimeUnit

/**
 * Downloads one on-device model as background work.
 *
 * It used to run inside the AI Engine screen, so leaving the screen (or the app) stopped it. As
 * WorkManager work with a foreground notification it keeps going while you use the phone for
 * other things. WorkManager holds it until a network is back (Wi-Fi only, if that setting is on),
 * and every attempt resumes from the bytes already on disk via HTTP Range.
 *
 * Pausing is cancelling this work: the partial file stays, and enqueueing again continues it.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val modelId get() = inputData.getString(KEY_MODEL_ID).orEmpty()
    private val modelName get() = ModelCatalog.entries.find { it.id == modelId }?.name ?: "AI model"

    override suspend fun doWork(): Result {
        if (modelId.isEmpty()) return Result.failure()
        AppNotifications.ensureChannels(applicationContext)
        setForeground(foregroundInfo(0, 0))

        val repository = ModelRepository(MeetMindDatabase.getInstance(applicationContext), LocalModelStorage(applicationContext))
        var lastUpdate = 0L
        val result = repository.installModel(modelId) { done, total ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate < 700) return@installModel
            lastUpdate = now
            kotlinx.coroutines.runBlocking {
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                setForeground(foregroundInfo(done, total))
            }
        }
        return when (result) {
            is AiResult.Success -> {
                AppNotifications.downloadFinished(applicationContext, modelId, modelName, ok = true, detail = null)
                Result.success()
            }
            // A dropped connection is retried with backoff; WorkManager also waits for the
            // network to come back before the next attempt.
            is AiResult.Failed -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else {
                AppNotifications.downloadFinished(applicationContext, modelId, modelName, ok = false, detail = result.message)
                Result.failure(workDataOf(KEY_ERROR to result.message))
            }
            else -> {
                val message = result.describeFailure() ?: "This model could not be installed."
                AppNotifications.downloadFinished(applicationContext, modelId, modelName, ok = false, detail = message)
                Result.failure(workDataOf(KEY_ERROR to message))
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

    private fun foregroundInfo(done: Long, total: Long): ForegroundInfo {
        AppNotifications.ensureChannels(applicationContext)
        val notification = AppNotifications.downloadProgress(
            applicationContext, modelName, done, total,
            pauseIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        )
        val notificationId = AppNotifications.downloadId(modelId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_MODEL_ID = "modelId"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val TAG_ALL = "meetmind_model_download"
        private const val MAX_ATTEMPTS = 8

        fun uniqueName(modelId: String) = "model_download_$modelId"
        fun modelTag(modelId: String) = "model_download_id_$modelId"

        /** Starts (or continues) a download. Keeps an existing one rather than starting twice. */
        fun enqueue(context: Context, modelId: String, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL_ID to modelId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .addTag(TAG_ALL)
                .addTag(modelTag(modelId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(modelId), ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context, modelId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(modelId))
        }
    }
}
