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
import kotlinx.coroutines.withTimeoutOrNull
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
    private const val EXTRAS = "devotional-extras"
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
    fun writeNow(context: Context, replace: Boolean = false, ask: com.example.ai.devotional.DevotionalAsk? = null) {
        val request = OneTimeWorkRequestBuilder<DevotionalWorker>()
            .setInputData(workDataOf(
                DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_WRITE, DevotionalWorker.KEY_REPLACE to replace,
                DevotionalWorker.KEY_NOTIFY to false, DevotionalWorker.KEY_ASK to ask?.toJson()
            ))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        setLastError(context, null)
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(NOW, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request) }
    }

    /** The picture and the voice, after the words — so the page never waits on them. */
    internal fun finishLater(context: Context) {
        val request = OneTimeWorkRequestBuilder<DevotionalWorker>()
            .setInputData(workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_EXTRAS))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(EXTRAS, ExistingWorkPolicy.REPLACE, request) }
    }

    /** Why the last on-demand write failed (null once one succeeds), for the page to say. */
    fun lastError(context: Context): String? = context.getSharedPreferences("devotional_work", Context.MODE_PRIVATE).getString("error", null)
    fun setLastError(context: Context, message: String?) {
        context.getSharedPreferences("devotional_work", Context.MODE_PRIVATE).edit().apply { if (message == null) remove("error") else putString("error", message) }.apply()
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
            MODE_EXTRAS -> {
                val today = repo.find(LocalDay.today()) ?: return Result.success()
                extras(repo, today, profile)
                Result.success()
            }
            else -> {
                val replace = inputData.getBoolean(KEY_REPLACE, false)
                val onDemand = !inputData.getBoolean(KEY_NOTIFY, true)
                val asked = com.example.ai.devotional.DevotionalAsk.fromJson(inputData.getString(KEY_ASK))
                // Asked for now: it's read now, so it knows whether it's afternoon or evening.
                val ask = if (onDemand) (asked ?: com.example.ai.devotional.DevotionalAsk()).copy(hour = java.time.LocalTime.now().hour) else asked
                // Never "writing…" forever: a stuck model or network gives up and the classic stands in.
                val attempt = runCatching { withTimeoutOrNull(WRITE_TIMEOUT_MS) { repo.ensure(LocalDate.now(), another = replace, ask = ask) } }
                val written = attempt.getOrNull()
                if (written == null) {
                    val why = attempt.exceptionOrNull()?.message ?: if (attempt.isSuccess) "It took too long to write. Try again, or choose another writer." else "Something went wrong."
                    if (onDemand) DevotionalScheduler.setLastError(applicationContext, why)
                    // Someone is waiting on the page: say so now rather than retrying quietly.
                    return if (!onDemand && runAttemptCount < 2) Result.retry() else Result.failure(workDataOf(KEY_ERROR to why))
                }
                if (onDemand) DevotionalScheduler.setLastError(applicationContext, null)
                com.example.core.widget.Widgets.refresh(applicationContext)
                if (onDemand) DevotionalScheduler.finishLater(applicationContext) else extras(repo, written, profile)
                if (!onDemand && profile.enabled) DevotionalScheduler.notifyAt(applicationContext, profile.deliveryMinutes)
                Result.success()
            }
        }
    }

    private suspend fun extras(repo: DevotionalRepository, written: DailyDevotional, profile: DevotionalProfile) {
        if (profile.autoImage) runCatching { withTimeoutOrNull(EXTRA_TIMEOUT_MS) { repo.paint(written, profile) } }
        if (profile.voice.autoVoice && written.note.metadata[DevotionalVoice.META_AUDIO] == null) {
            runCatching { withTimeoutOrNull(EXTRA_TIMEOUT_MS * 3) { DevotionalVoice(applicationContext).record(written) } }
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_REPLACE = "replace"
        const val KEY_NOTIFY = "notify"
        const val KEY_ASK = "ask"
        const val MODE_WRITE = "write"
        const val MODE_NOTIFY = "notify"
        const val MODE_EXTRAS = "extras"
        const val KEY_ERROR = "error"
        const val WRITE_TIMEOUT_MS = 150_000L
        const val EXTRA_TIMEOUT_MS = 90_000L
    }
}
