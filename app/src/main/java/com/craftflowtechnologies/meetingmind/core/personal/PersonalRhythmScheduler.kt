package com.craftflowtechnologies.meetingmind.core.personal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.notify.AppNotifications
import com.craftflowtechnologies.meetingmind.core.notify.DailyAlarms
import com.craftflowtechnologies.meetingmind.core.notify.DeepLink
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Wall-clock alarms for Personal routines ([DailyAlarms]).
 *
 * Strictly obeys:
 * - Opt-in only: a routine fires a gentle reminder only when [PersonalRoutine.reminderEnabled] is true.
 * - Space-aware: turning off [NotebookSpace.PERSONAL] cancels all personal routine alarms while
 *   keeping the person's settings intact, restoring them when Personal is turned back on.
 * - Quiet hours: never notifies during the user's configured quiet window.
 * - Zero guilt: reminders invite calmly ("Time for Morning Reset · 2-min version ready too") and
 *   never say "You missed..." or threaten a streak.
 */
object PersonalRhythmScheduler {
    private const val PREFIX = "personal-routine-"

    fun handles(key: String): Boolean = key.startsWith(PREFIX)

    suspend fun sync(context: Context, hasPersonal: Boolean) {
        val db = MeetMindDatabase.getInstance(context.applicationContext)
        val routines = runCatching { db.personalGrowthDao().getActiveRoutines() }.getOrDefault(emptyList())
        routines.forEach { r ->
            val key = PREFIX + r.id
            val minute = r.scheduledMinuteOfDay
            if (hasPersonal && r.reminderEnabled && minute != null && !r.isArchived) {
                DailyAlarms.schedule(context, key, minute)
            } else {
                DailyAlarms.cancel(context, key)
            }
        }
    }

    fun onAlarm(context: Context, key: String) {
        val routineId = key.removePrefix(PREFIX)
        if (routineId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<PersonalRoutineReminderWorker>()
            .setInputData(workDataOf(PersonalRoutineReminderWorker.KEY_ROUTINE_ID to routineId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork("$key-run", ExistingWorkPolicy.KEEP, request)
        }
    }

    /**
     * Schedules a gentle local notification when a sealed Time Capsule reaches its unlock date.
     */
    fun scheduleTimeCapsuleUnlock(context: Context, noteId: String, title: String, unlockAtMs: Long) {
        val delayMs = (unlockAtMs - System.currentTimeMillis()).coerceAtLeast(60_000L)
        val request = OneTimeWorkRequestBuilder<TimeCapsuleUnlockWorker>()
            .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    TimeCapsuleUnlockWorker.KEY_NOTE_ID to noteId,
                    TimeCapsuleUnlockWorker.KEY_TITLE to title
                )
            )
            .build()
        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork("personal-capsule-$noteId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

class PersonalRoutineReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val routineId = inputData.getString(KEY_ROUTINE_ID) ?: return Result.success()
        val app = applicationContext
        val prefs = UserPreferencesManager(app)
        val spaces = prefs.preferencesFlow.first().identity.spaces
        if (NotebookSpace.PERSONAL !in spaces) return Result.success()

        val reminders = prefs.reminderSettings.first()
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (reminders.isQuiet(nowMinute)) return Result.success()

        val repo = PersonalRepository(app)
        val routine = repo.getRoutine(routineId) ?: return Result.success()
        if (routine.isArchived || !routine.reminderEnabled) return Result.success()

        val dayIdx = repo.dayOfWeekIndex()
        if (!routine.isScheduledOnDay(dayIdx)) return Result.success()

        val today = repo.todayEpochDay()
        val existing = MeetMindDatabase.getInstance(app).personalGrowthDao().latestCompletionForDay(routineId, today)
        if (existing != null && RoutineCompletionStatus.fromNameOrDefault(existing.status).isDone) {
            return Result.success()
        }

        val mins = routine.totalMinutes(minEffort = false)
        val minMins = routine.totalMinutes(minEffort = true)
        val body = buildString {
            append("$mins min")
            if (minMins < mins) append(" (or a gentle $minMins-min version)")
            if (routine.anchorCue.isNotBlank()) append(" · ${routine.anchorCue}")
        }
        AppNotifications.personalReminder(
            app,
            400 + (routineId.hashCode() and 0xFF),
            routine.title,
            body,
            DeepLink.PersonalSpace
        )
        return Result.success()
    }

    companion object {
        const val KEY_ROUTINE_ID = "routine_id"
    }
}

class TimeCapsuleUnlockWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val prefs = UserPreferencesManager(app)
        val spaces = prefs.preferencesFlow.first().identity.spaces
        if (NotebookSpace.PERSONAL !in spaces) return Result.success()

        val noteId = inputData.getString(KEY_NOTE_ID) ?: return Result.success()
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Your time capsule" }
        AppNotifications.personalReminder(
            app,
            800 + (noteId.hashCode() and 0xFF),
            "Time capsule ready to unseal",
            "\"$title\" is waiting in your Memory Vault.",
            DeepLink.PersonalSpace
        )
        return Result.success()
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"
        const val KEY_TITLE = "title"
    }
}
