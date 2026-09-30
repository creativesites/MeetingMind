package com.craftflowtechnologies.meetingmind.core.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.notify.AppNotifications
import com.craftflowtechnologies.meetingmind.core.notify.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One-off task reminders. Each task with a future reminder gets its own alarm; the set is
 * re-synced whenever tasks change and after a reboot ([com.craftflowtechnologies.meetingmind.core.notify.DailyAlarmResync]).
 */
object TaskReminders {
    private const val PREFS = "task_reminders"
    private const val KEY_IDS = "ids"
    const val EXTRA_TASK = "task"
    const val ACTION_FIRE = "com.craftflowtechnologies.meetingmind.TASK_REMINDER"
    const val ACTION_DONE = "com.craftflowtechnologies.meetingmind.TASK_DONE"
    /** Alarms are cheap but not free; the nearest ones are enough, the rest are set as these fire. */
    private const val MAX_ALARMS = 40

    fun repository(context: Context): TaskRepository {
        val app = context.applicationContext
        val db = MeetMindDatabase.getInstance(app)
        return TaskRepository(
            db.taskDao(), db.peopleDao(), onRemindersChanged = { sync(app) },
            onTaskDone = { id, done -> com.craftflowtechnologies.meetingmind.core.work.ItemRepository(db).onTaskDone(id, done) }
        )
    }

    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val db = MeetMindDatabase.getInstance(app)
        val upcoming = TaskRepository(db.taskDao(), db.peopleDao()).upcomingReminders().take(MAX_ALARMS)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val now = upcoming.map { it.id }.toSet()
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        (before - now).forEach { id -> intent(app, id, create = false)?.let { am.cancel(it); it.cancel() } }
        upcoming.forEach { t ->
            val pi = intent(app, t.id, create = true) ?: return@forEach
            val at = t.remindAt ?: return@forEach
            runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        prefs.edit().putStringSet(KEY_IDS, now).apply()
    }

    fun notificationId(taskId: String) = 7000 + (taskId.hashCode() and 0x3FF)

    private fun intent(context: Context, taskId: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, TaskReminderReceiver::class.java).setAction("$ACTION_FIRE.$taskId").putExtra(EXTRA_TASK, taskId)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, taskId.hashCode(), intent, flags)
    }

    fun doneIntent(context: Context, taskId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, taskId.hashCode() xor 0x5A5A,
            Intent(context, TaskReminderReceiver::class.java).setAction("$ACTION_DONE.$taskId").putExtra(EXTRA_TASK, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(TaskReminders.EXTRA_TASK) ?: return
        val action = intent.action.orEmpty()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = TaskReminders.repository(context)
                val task = repo.get(id)
                if (action.startsWith(TaskReminders.ACTION_DONE)) {
                    if (task != null && !task.done) repo.toggleDone(id)
                    context.getSystemService(android.app.NotificationManager::class.java)?.cancel(TaskReminders.notificationId(id))
                } else if (task != null && !task.done) {
                    val person = task.personId?.let { pid -> repo.allPeople().firstOrNull { it.id == pid } }
                    AppNotifications.taskReminder(
                        context, TaskReminders.notificationId(id), task.title,
                        listOfNotNull(task.kind.label.takeIf { task.kind != TaskKind.TASK }, person?.name, task.notes.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "Reminder" },
                        TaskReminders.doneIntent(context, id)
                    )
                    TaskReminders.sync(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
