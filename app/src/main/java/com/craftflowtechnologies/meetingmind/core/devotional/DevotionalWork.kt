package com.craftflowtechnologies.meetingmind.core.devotional

import android.content.Context
import androidx.work.Constraints
import com.craftflowtechnologies.meetingmind.core.notify.DailyAlarms
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

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
    const val LEAD_MINUTES = DevotionalDelivery.LEAD_MINUTES

    fun handles(key: String) = key == ALARM_WRITE || key == ALARM_NOTIFY

    fun sync(context: Context, profile: DevotionalProfile) {
        runCatching { WorkManager.getInstance(context) }.getOrNull()?.let { wm ->
            wm.cancelUniqueWork(LEGACY_DAILY); wm.cancelUniqueWork(LEGACY_NOTIFY)
        }
        if (!profile.enabled) {
            DailyAlarms.cancel(context, ALARM_WRITE); DailyAlarms.cancel(context, ALARM_NOTIFY)
            return
        }
        DailyAlarms.schedule(context, ALARM_WRITE, DevotionalDelivery.writeMinute(profile.deliveryMinutes))
        DailyAlarms.schedule(context, ALARM_NOTIFY, profile.deliveryMinutes)
        // Missed the writing time today (phone off, app killed)? Write it now. A devotional that
        // already exists is kept, so this costs nothing on a normal day.
        if (isPastWriteTime(LocalDateTime.now(), profile.deliveryMinutes)) scheduledWrite(context)
    }

    fun onAlarm(context: Context, key: String) {
        when (key) {
            // A fresh request each day: a stale one still waiting for a network must not block today's.
            ALARM_WRITE -> scheduledWrite(context, replace = true)
            // The last try, with no network requirement: the phone's own model can still write offline.
            ALARM_NOTIFY -> runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    NOTIFY_RUN, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<DevotionalWorker>()
                        .setInputData(workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_NOTIFY))
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
                )
            }
        }
    }

    /**
     * Today's devotional as the timetable writes it: ahead of time, once there's a network, retried
     * with growing waits until delivery. Announced once it's due and ready.
     */
    internal fun scheduledWrite(context: Context, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<DevotionalWorker>()
            .setInputData(workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_WRITE))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, DevotionalDelivery.BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(RUN, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request) }
    }

    /** Whether the write-ahead is running this moment. */
    internal suspend fun writeAheadRunning(context: Context): Boolean = runCatching {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(RUN).first().any { it.state == WorkInfo.State.RUNNING }
    }.getOrDefault(false)

    fun isOnline(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    fun isPastWriteTime(now: LocalDateTime, deliveryMinutes: Int): Boolean {
        // A time just after midnight is written from midnight, not the evening before: the day's
        // devotional can't be written before its day has begun.
        val writeAt = DevotionalDelivery.writeMinute(deliveryMinutes)
        return now.hour * 60 + now.minute >= writeAt
    }

    fun isPastDelivery(now: LocalDateTime, deliveryMinutes: Int): Boolean =
        now.hour * 60 + now.minute >= Math.floorMod(deliveryMinutes, 24 * 60)

    /** Announces [daily] unless it has been opened or was already announced today. */
    internal fun announceOnce(context: Context, daily: DailyDevotional) {
        if (daily.note.metadata[DevotionalNotes.META_OPENED] != null) return
        if (announcedToday(context)) return
        announce(context, daily)
    }

    internal fun announcedToday(context: Context): Boolean =
        context.getSharedPreferences("devotional_work", Context.MODE_PRIVATE).getString("announced", null) == LocalDate.now().toString()

    /** Tells the person [daily] is ready, replacing any "isn't ready yet" notice. */
    internal fun announce(context: Context, daily: DailyDevotional) {
        context.getSharedPreferences("devotional_work", Context.MODE_PRIVATE).edit().putString("announced", LocalDate.now().toString()).apply()
        com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.devotionalReady(context, daily.devotional.title, daily.devotional.label)
    }

    /** Writes today's devotional now (opening Today before it was due, say). */
    fun writeNow(
        context: Context, replace: Boolean = false, ask: com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalAsk? = null,
        /** Asked for from the "isn't ready yet" notification: the result is posted there, and a failed AI isn't papered over. */
        fromNotice: Boolean = false
    ) {
        val request = OneTimeWorkRequestBuilder<DevotionalWorker>()
            .setInputData(workDataOf(
                DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_WRITE, DevotionalWorker.KEY_REPLACE to replace,
                DevotionalWorker.KEY_NOTIFY to false, DevotionalWorker.KEY_ASK to ask?.toJson(), DevotionalWorker.KEY_FROM_NOTICE to fromNotice
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

    /** Expedited work on Android 11 and older runs as a foreground service and must say what it shows. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.ensureChannels(applicationContext)
        val notification = com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.devotionalWriting(applicationContext)
        val id = com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.ID_DEVOTIONAL_WORK
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(id, notification)
    }

    private sealed interface Attempt {
        data class Written(val daily: DailyDevotional) : Attempt
        data class Failed(val message: String, val kind: FailureKind) : Attempt
    }

    /** One bounded try at writing today's devotional; never throws, never leaves a blank page. */
    private suspend fun attempt(repo: DevotionalRepository, replace: Boolean, ask: com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalAsk?): Attempt {
        val result = runCatching { withTimeoutOrNull(WRITE_TIMEOUT_MS) { repo.ensure(LocalDate.now(), another = replace, ask = ask) } }
        result.getOrNull()?.let { return Attempt.Written(it) }
        val online = DevotionalScheduler.isOnline(applicationContext)
        val timedOut = result.isSuccess
        val why = result.exceptionOrNull()?.message
            ?: if (timedOut) "It took too long to write. Try again, or choose another writer." else "Something went wrong."
        return Attempt.Failed(why, DevotionalDelivery.classify(why, online, timedOut))
    }

    override suspend fun doWork(): Result {
        val repo = DevotionalRepository(applicationContext)
        val profile = UserPreferencesManager(applicationContext).devotionalProfile.first()
        return when (inputData.getString(KEY_MODE)) {
            MODE_NOTIFY -> { deliver(repo, profile); Result.success() }
            MODE_EXTRAS -> {
                val today = repo.find(LocalDay.today()) ?: return Result.success()
                extras(repo, today, profile)
                Result.success()
            }
            else -> {
                val replace = inputData.getBoolean(KEY_REPLACE, false)
                val onDemand = !inputData.getBoolean(KEY_NOTIFY, true)
                val fromNotice = inputData.getBoolean(KEY_FROM_NOTICE, false)
                val asked = com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalAsk.fromJson(inputData.getString(KEY_ASK))
                // Asked for now: it's read now, so it knows whether it's afternoon or evening.
                val base = asked ?: com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalAsk()
                val ask = if (onDemand) base.copy(hour = java.time.LocalTime.now().hour, keepToAi = fromNotice)
                // The timetable's own write: if the AI fails, retry — don't file a classic as today's.
                else base.copy(keepToAi = true)
                when (val r = attempt(repo, replace, ask)) {
                    is Attempt.Failed -> {
                        DevotionalScheduler.setLastError(applicationContext, r.message)
                        if (fromNotice) {
                            val online = DevotionalScheduler.isOnline(applicationContext)
                            com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.devotionalNotReady(
                                applicationContext, DevotionalDelivery.reasonFor(r.kind, online, writeInFlight = false)
                            )
                        }
                        // Someone is waiting on the page: say so now rather than retrying quietly.
                        if (!onDemand && DevotionalDelivery.shouldRetry(LocalDateTime.now(), profile.deliveryMinutes)) Result.retry()
                        else Result.failure(workDataOf(KEY_ERROR to r.message))
                    }
                    is Attempt.Written -> {
                        val written = r.daily
                        if (onDemand) DevotionalScheduler.setLastError(applicationContext, null)
                        com.craftflowtechnologies.meetingmind.core.widget.Widgets.refresh(applicationContext)
                        if (onDemand) DevotionalScheduler.finishLater(applicationContext) else extras(repo, written, profile)
                        if (fromNotice) DevotionalScheduler.announce(applicationContext, written)
                        else if (!onDemand && profile.enabled && DevotionalScheduler.isPastDelivery(LocalDateTime.now(), profile.deliveryMinutes)) {
                            DevotionalScheduler.announceOnce(applicationContext, written)
                        }
                        Result.success()
                    }
                }
            }
        }
    }

    /**
     * The delivery moment. Ready: announce. Not ready: one last try now (any writer, no network
     * needed for the phone's own model), then either announce or say honestly why not.
     */
    private suspend fun deliver(repo: DevotionalRepository, profile: DevotionalProfile) {
        val ctx = applicationContext
        var today = repo.find(LocalDay.today())
        var state = DeliveryState(
            enabled = profile.enabled, ready = today != null, alreadyAnnounced = DevotionalScheduler.announcedToday(ctx),
            opened = today?.note?.metadata?.get(DevotionalNotes.META_OPENED) != null, online = DevotionalScheduler.isOnline(ctx)
        )
        var action = DevotionalDelivery.atDelivery(state)
        if (action is DeliveryAction.TryWriteNow) {
            val ask = com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalAsk(keepToAi = true)
            val r = attempt(repo, false, ask)
            today = (r as? Attempt.Written)?.daily ?: repo.find(LocalDay.today())
            state = state.copy(
                ready = today != null, attempted = true, online = DevotionalScheduler.isOnline(ctx),
                lastFailure = (r as? Attempt.Failed)?.kind, writeInFlight = DevotionalScheduler.writeAheadRunning(ctx)
            )
            (r as? Attempt.Failed)?.let { DevotionalScheduler.setLastError(ctx, it.message) }
            action = DevotionalDelivery.atDelivery(state)
        }
        when (action) {
            DeliveryAction.AnnounceReady -> today?.let { com.craftflowtechnologies.meetingmind.core.widget.Widgets.refresh(ctx); DevotionalScheduler.announce(ctx, it) }
            is DeliveryAction.AnnounceNotReady -> com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.devotionalNotReady(ctx, action.reason)
            DeliveryAction.Nothing, DeliveryAction.TryWriteNow -> Unit
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
        const val KEY_FROM_NOTICE = "fromNotice"
        const val MODE_WRITE = "write"
        const val MODE_NOTIFY = "notify"
        const val MODE_EXTRAS = "extras"
        const val KEY_ERROR = "error"
        const val WRITE_TIMEOUT_MS = 150_000L
        const val EXTRA_TIMEOUT_MS = 90_000L
    }
}
