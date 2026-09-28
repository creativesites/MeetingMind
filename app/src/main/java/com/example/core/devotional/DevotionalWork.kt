package com.example.core.devotional

import android.content.Context
import androidx.work.Constraints
import com.example.core.notify.DailyAlarms
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The daily devotional's timetable: written [LEAD_MINUTES] before it's due (so a slow model or
 * network doesn't make it late), announced at the chosen time.
 *
 * Both moments are wall-clock alarms ([DailyAlarms]), not periodic work, so they stay on the set
 * time. If the phone was off or asleep through the writing time, opening the app catches up.
 */
object DevotionalScheduler {
    // Names of the periodic jobs used up to v30; cancelled so they can't fire at odd hours.
    private const val LEGACY_DAILY = "devotional-daily"
    private const val LEGACY_NOTIFY = "devotional-notify"
    private const val RUN = "devotional-scheduled"
    private const val NOTIFY_RUN = "devotional-announce"
    private const val NOW = "devotional-now"
    private const val EXTRAS = "devotional-extras"
    const val ALARM_WRITE = "devotional-write"
    const val ALARM_NOTIFY = "devotional-notify"
    const val LEAD_MINUTES = 90

    fun handles(key: String) = key == ALARM_WRITE || key == ALARM_NOTIFY

    fun sync(context: Context, profile: DevotionalProfile) {
        runCatching { WorkManager.getInstance(context) }.getOrNull()?.let { wm ->
            wm.cancelUniqueWork(LEGACY_DAILY); wm.cancelUniqueWork(LEGACY_NOTIFY)
        }
        if (!profile.enabled) {
            DailyAlarms.cancel(context, ALARM_WRITE); DailyAlarms.cancel(context, ALARM_NOTIFY)
            return
        }
        DailyAlarms.schedule(context, ALARM_WRITE, (Math.floorMod(profile.deliveryMinutes, 24 * 60) - LEAD_MINUTES).coerceAtLeast(0))
        DailyAlarms.schedule(context, ALARM_NOTIFY, profile.deliveryMinutes)
        // Missed the writing time today (phone off, app killed)? Write it now. A devotional that
        // already exists is kept, so this costs nothing on a normal day.
        if (isPastWriteTime(LocalDateTime.now(), profile.deliveryMinutes)) scheduledWrite(context)
    }

    fun onAlarm(context: Context, key: String) {
        when (key) {
            ALARM_WRITE -> scheduledWrite(context)
            ALARM_NOTIFY -> enqueue(context, NOTIFY_RUN, workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_NOTIFY))
        }
    }

    /** Today's devotional as the timetable writes it: announced once it's due and ready. */
    internal fun scheduledWrite(context: Context) =
        enqueue(context, RUN, workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_WRITE))

    private fun enqueue(context: Context, name: String, data: androidx.work.Data) {
        val request = OneTimeWorkRequestBuilder<DevotionalWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request) }
    }

    fun isPastWriteTime(now: LocalDateTime, deliveryMinutes: Int): Boolean {
        // A time just after midnight is written from midnight, not the evening before: the day's
        // devotional can't be written before its day has begun.
        val writeAt = (Math.floorMod(deliveryMinutes, 24 * 60) - LEAD_MINUTES).coerceAtLeast(0)
        return now.hour * 60 + now.minute >= writeAt
    }

    fun isPastDelivery(now: LocalDateTime, deliveryMinutes: Int): Boolean =
        now.hour * 60 + now.minute >= Math.floorMod(deliveryMinutes, 24 * 60)

    /** Announces [daily] unless it has been opened or was already announced today. */
    internal fun announceOnce(context: Context, daily: DailyDevotional) {
        if (daily.note.metadata[DevotionalNotes.META_OPENED] != null) return
        val prefs = context.getSharedPreferences("devotional_work", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString("announced", null) == today) return
        prefs.edit().putString("announced", today).apply()
        com.example.core.notify.AppNotifications.devotionalReady(context, daily.devotional.title, daily.devotional.label)
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
                if (!profile.enabled) return Result.success()
                val today = repo.find(LocalDay.today())
                // Not written yet (offline at writing time, say): write it now; it's announced when done.
                if (today == null) DevotionalScheduler.scheduledWrite(applicationContext)
                else DevotionalScheduler.announceOnce(applicationContext, today)
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
                if (!onDemand && profile.enabled && DevotionalScheduler.isPastDelivery(LocalDateTime.now(), profile.deliveryMinutes)) {
                    DevotionalScheduler.announceOnce(applicationContext, written)
                }
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
