package com.example.core.devotional

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * The daily devotional's timetable: written about 90 minutes before it's due (so a slow model or
 * network doesn't make it late), announced at the chosen time.
 */
object DevotionalScheduler {
    private const val DAILY = "devotional-daily"
    private const val NOTIFY = "devotional-notify"
    private const val NOW = "devotional-now"
    const val LEAD_MINUTES = 90

    fun sync(context: Context, profile: DevotionalProfile) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!profile.enabled) {
            wm.cancelUniqueWork(DAILY); wm.cancelUniqueWork(NOTIFY)
            return
        }
        val delay = delayUntil(LocalDateTime.now(), profile.deliveryMinutes - LEAD_MINUTES)
        val request = PeriodicWorkRequestBuilder<DevotionalWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .setInputData(workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_WRITE))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Writes today's devotional now (opening Today before it was due, say). */
    fun writeNow(context: Context, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<DevotionalWorker>()
            .setInputData(workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_WRITE, DevotionalWorker.KEY_REPLACE to replace, DevotionalWorker.KEY_NOTIFY to false))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(NOW, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request) }
    }

    fun observeWriting(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NOW)

    internal fun notifyAt(context: Context, deliveryMinutes: Int) {
        val delay = delayUntil(LocalDateTime.now(), deliveryMinutes, allowNow = true)
        val request = OneTimeWorkRequestBuilder<DevotionalWorker>()
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .setInputData(workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_NOTIFY))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOTIFY, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Time from [now] to the next [minuteOfDay] (wrapping round midnight; negative values are the
     * evening before). With [allowNow], a time already passed today means "now".
     */
    fun delayUntil(now: LocalDateTime, minuteOfDay: Int, allowNow: Boolean = false): Duration {
        val m = Math.floorMod(minuteOfDay, 24 * 60)
        var target = now.toLocalDate().atStartOfDay().plusMinutes(m.toLong())
        if (!target.isAfter(now)) {
            if (allowNow) return Duration.ZERO
            target = target.plusDays(1)
        }
        return Duration.between(now, target)
    }
}

class DevotionalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = DevotionalRepository(applicationContext)
        val profile = UserPreferencesManager(applicationContext).devotionalProfile.first()
        return when (inputData.getString(KEY_MODE)) {
            MODE_NOTIFY -> {
                val today = repo.find(LocalDay.today())
                if (today != null && today.note.metadata[DevotionalNotes.META_OPENED] == null && profile.enabled) {
                    com.example.core.notify.AppNotifications.devotionalReady(applicationContext, today.devotional.title, today.devotional.label)
                }
                Result.success()
            }
            else -> {
                val replace = inputData.getBoolean(KEY_REPLACE, false)
                val written = runCatching { repo.ensure(LocalDate.now(), replace = replace) }.getOrNull()
                if (written == null) return if (runAttemptCount < 2) Result.retry() else Result.failure()
                if (inputData.getBoolean(KEY_NOTIFY, true) && profile.enabled) DevotionalScheduler.notifyAt(applicationContext, profile.deliveryMinutes)
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_REPLACE = "replace"
        const val KEY_NOTIFY = "notify"
        const val MODE_WRITE = "write"
        const val MODE_NOTIFY = "notify"
    }
}
