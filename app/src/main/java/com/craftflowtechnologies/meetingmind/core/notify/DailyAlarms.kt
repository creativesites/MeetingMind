package com.craftflowtechnologies.meetingmind.core.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalScheduler
import com.craftflowtechnologies.meetingmind.core.faith.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Things that must happen at a time of day: the devotional being written and announced, prayer
 * and reading reminders.
 *
 * These used to be 24-hour periodic WorkManager jobs. Periodic work keeps no time of day: it runs
 * roughly a day after it last ran, wherever that was, and re-syncing it with
 * `ExistingPeriodicWorkPolicy.UPDATE` keeps the original enqueue time, so a recomputed initial
 * delay lands at the wrong hour. The devotional drifted away from its set time and its
 * notification with it.
 *
 * An alarm is set for the next occurrence of each time. When it fires, [DailyAlarmReceiver] hands
 * the work to WorkManager and sets the alarm for the next day. Alarms are cleared by a reboot, an
 * update or a clock change, so [DailyAlarmResync] sets them all again then.
 */
object DailyAlarms {
    const val EXTRA_KEY = "key"
    const val EXTRA_MINUTE = "minute"
    private const val PREFS = "daily_alarms"

    /** Sets [key] to fire at [minuteOfDay] every day, replacing any earlier time for it. */
    fun schedule(context: Context, key: String, minuteOfDay: Int, now: LocalDateTime = LocalDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextTrigger(now, minuteOfDay).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(context, key, minuteOfDay, create = true) ?: return
        runCatching {
            if (canBeExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(key, minuteOfDay).apply()
    }

    fun cancel(context: Context, key: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        pendingIntent(context, key, 0, create = false)?.let { am.cancel(it); it.cancel() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
    }

    /** Whether alarms land on the minute. Without the exact-alarm permission they can be a few minutes late in Doze. */
    fun exactAllowed(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.let { canBeExact(it) } ?: false

    /** The next time [minuteOfDay] comes round, strictly after [now]. */
    fun nextTrigger(now: LocalDateTime, minuteOfDay: Int): LocalDateTime {
        val m = Math.floorMod(minuteOfDay, 24 * 60)
        val today = now.toLocalDate().atStartOfDay().plusMinutes(m.toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun canBeExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, key: String, minuteOfDay: Int, create: Boolean): PendingIntent? {
        val intent = Intent(context, DailyAlarmReceiver::class.java)
            .setAction("com.craftflowtechnologies.meetingmind.DAILY_ALARM.$key")
            .putExtra(EXTRA_KEY, key)
            .putExtra(EXTRA_MINUTE, minuteOfDay)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, flags)
    }
}

/** An alarm went off: do its work and set it for tomorrow. */
class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(DailyAlarms.EXTRA_KEY) ?: return
        val minute = intent.getIntExtra(DailyAlarms.EXTRA_MINUTE, -1)
        if (minute >= 0) DailyAlarms.schedule(context, key, minute)
        when {
            DevotionalScheduler.handles(key) -> DevotionalScheduler.onAlarm(context, key)
            ReminderScheduler.handles(key) -> ReminderScheduler.onAlarm(context, key)
        }
    }
}

/** Sets every alarm again after a reboot, an app update or a change of clock or time zone. */
class DailyAlarmResync : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = UserPreferencesManager(context.applicationContext)
                DevotionalScheduler.sync(context.applicationContext, prefs.devotionalProfile.first())
                ReminderScheduler.sync(context.applicationContext, prefs.reminderSettings.first())
                runCatching { com.craftflowtechnologies.meetingmind.core.tasks.TaskReminders.sync(context.applicationContext) }
            } finally {
                pending.finish()
            }
        }
    }
}
