package com.example.core.faith

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.datastore.UserPreferencesManager
import com.example.core.notify.DailyAlarms
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import com.example.core.notify.AppNotifications
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** The three times of the Daily Office, for prayer reminders. */
enum class PrayerTime(val label: String, val defaultMinutes: Int) {
    MORNING("Morning", 7 * 60), MIDDAY("Midday", 12 * 60 + 30), EVENING("Evening", 20 * 60 + 30)
}

/**
 * Which gentle reminders the person wants (PLAN_V2 F6). All off until turned on; nothing ever
 * arrives in quiet hours.
 */
data class ReminderSettings(
    val prayerTimes: Set<PrayerTime> = emptySet(),
    val readingNudge: Boolean = false,
    val readingMinutes: Int = 19 * 60,
    val eveningReflection: Boolean = false,
    val eveningMinutes: Int = 21 * 60,
    val meetingPrep: Boolean = false,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 7 * 60
) {
    /** Whether [minuteOfDay] falls in quiet hours (which may run past midnight). */
    fun isQuiet(minuteOfDay: Int): Boolean =
        if (quietStart == quietEnd) false
        else if (quietStart < quietEnd) minuteOfDay in quietStart until quietEnd
        else minuteOfDay >= quietStart || minuteOfDay < quietEnd

    fun toJson(): String = JSONObject().apply {
        put("prayer", JSONArray(prayerTimes.map { it.name })); put("reading", readingNudge); put("readingAt", readingMinutes)
        put("evening", eveningReflection); put("eveningAt", eveningMinutes); put("prep", meetingPrep); put("quietStart", quietStart); put("quietEnd", quietEnd)
    }.toString()

    companion object {
        fun fromJson(raw: String?): ReminderSettings {
            val o = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return ReminderSettings()
            val d = ReminderSettings()
            val p = o.optJSONArray("prayer")
            return ReminderSettings(
                prayerTimes = (0 until (p?.length() ?: 0)).mapNotNull { i -> runCatching { PrayerTime.valueOf(p!!.getString(i)) }.getOrNull() }.toSet(),
                readingNudge = o.optBoolean("reading", d.readingNudge), readingMinutes = o.optInt("readingAt", d.readingMinutes),
                eveningReflection = o.optBoolean("evening", d.eveningReflection), eveningMinutes = o.optInt("eveningAt", d.eveningMinutes),
                meetingPrep = o.optBoolean("prep", d.meetingPrep), quietStart = o.optInt("quietStart", d.quietStart), quietEnd = o.optInt("quietEnd", d.quietEnd)
            )
        }
    }
}

/**
 * Daily reminders on wall-clock alarms ([DailyAlarms]), so each arrives at its set time. The
 * meeting check is a rolling 15-minute job, where drift doesn't matter.
 */
object ReminderScheduler {
    private const val PRAYER = "reminder-prayer-"
    private const val READING = "reminder-reading"
    private const val EVENING = "reminder-evening"
    private const val PREP = "reminder-prep"

    fun handles(key: String) = key.startsWith(PRAYER) || key == READING || key == EVENING

    fun sync(context: Context, s: ReminderSettings) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        // Up to v30 the daily reminders were periodic jobs under these same names.
        (PrayerTime.entries.map { PRAYER + it.name } + READING + EVENING).forEach { wm.cancelUniqueWork(it) }
        PrayerTime.entries.forEach { t ->
            if (t in s.prayerTimes) DailyAlarms.schedule(context, PRAYER + t.name, t.defaultMinutes) else DailyAlarms.cancel(context, PRAYER + t.name)
        }
        if (s.readingNudge) DailyAlarms.schedule(context, READING, s.readingMinutes) else DailyAlarms.cancel(context, READING)
        if (s.eveningReflection) DailyAlarms.schedule(context, EVENING, s.eveningMinutes) else DailyAlarms.cancel(context, EVENING)
        if (s.meetingPrep) {
            wm.enqueueUniquePeriodicWork(PREP, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).setInputData(workDataOf(ReminderWorker.KEY_KIND to ReminderWorker.PREP)).build())
        } else wm.cancelUniqueWork(PREP)
    }

    fun onAlarm(context: Context, key: String) {
        val (kind, extra) = when {
            key.startsWith(PRAYER) -> ReminderWorker.PRAYER to key.removePrefix(PRAYER)
            key == READING -> ReminderWorker.READING to null
            key == EVENING -> ReminderWorker.EVENING to null
            else -> return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind, ReminderWorker.KEY_EXTRA to extra))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork("$key-run", ExistingWorkPolicy.KEEP, request) }
    }
}

/** Posts one reminder when it's due, if it still applies and it isn't quiet time. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = UserPreferencesManager(applicationContext)
        val s = prefs.reminderSettings.first()
        val now = LocalDateTime.now()
        if (s.isQuiet(now.hour * 60 + now.minute) && inputData.getString(KEY_KIND) != PREP) return Result.success()
        val store = FaithStore.get(applicationContext)
        when (inputData.getString(KEY_KIND)) {
            PRAYER -> {
                val time = runCatching { PrayerTime.valueOf(inputData.getString(KEY_EXTRA).orEmpty()) }.getOrNull() ?: PrayerTime.MORNING
                if (time !in s.prayerTimes) return Result.success()
                val names = PrayerRotation.today(store.people(), now.toLocalDate().toEpochDay()).map { it.name }
                AppNotifications.faithReminder(
                    applicationContext, 100 + time.ordinal, "${time.label} prayer",
                    if (names.isEmpty()) "A moment with God. Add people to your prayer list to pray for them by name." else "Today you're praying for ${names.joinToString(", ")}.",
                    com.example.core.notify.DeepLink.PrayerList
                )
            }
            READING -> {
                if (!s.readingNudge) return Result.success()
                val today = now.toLocalDate().toEpochDay()
                val plan = store.active().firstOrNull { !it.finished } ?: return Result.success()
                val day = plan.dayFor(today)
                if (day in plan.done) return Result.success()
                AppNotifications.faithReminder(applicationContext, 110, "Today's reading", "${plan.plan.name}: ${ReadingPlans.describe(plan.plan.days[day])}", com.example.core.notify.DeepLink.ReadingPlans)
            }
            EVENING -> {
                if (!s.eveningReflection) return Result.success()
                AppNotifications.faithReminder(applicationContext, 111, "Evening reflection", "Where did you notice God today? Take a minute before you rest.", com.example.core.notify.DeepLink.Devotional)
            }
            PREP -> {
                if (!s.meetingPrep) return Result.success()
                val app = prefs.preferencesFlow.first()
                val calendar = com.example.core.calendar.CalendarEvents(applicationContext)
                if (!app.calendarEnabled || !calendar.hasPermission()) return Result.success()
                val nowMs = System.currentTimeMillis()
                val sent = applicationContext.getSharedPreferences("reminders_prep", Context.MODE_PRIVATE)
                calendar.between(nowMs + 8 * 60_000L, nowMs + 26 * 60_000L).filter { !it.allDay }.forEach { e ->
                    if (sent.getBoolean(e.key, false)) return@forEach
                    sent.edit().putBoolean(e.key, true).apply()
                    val mins = ((e.begin - nowMs) / 60_000L).coerceAtLeast(1)
                    val people = e.otherPeople.take(3).joinToString(", ")
                    AppNotifications.faithReminder(applicationContext, 200 + (e.key.hashCode() and 0xFF), "In $mins min: ${e.title}",
                        if (people.isNotBlank()) "With $people. Tap to prepare or record." else "Tap to prepare or record.", com.example.core.notify.DeepLink.Home)
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KEY_EXTRA = "extra"
        const val PRAYER = "prayer"
        const val READING = "reading"
        const val EVENING = "evening"
        const val PREP = "prep"
    }
}
