package com.craftflowtechnologies.meetingmind.feature.circles2

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.craftflowtechnologies.meetingmind.MainActivity
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.circles2.CircleDestination
import com.craftflowtechnologies.meetingmind.core.circles2.CircleTarget
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesNotifier
import com.craftflowtechnologies.meetingmind.core.circles2.PushRouting
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Daily rhythm (founder decision D2): an opt-in, per-Circle daily nudge that replaces the streak and the
 * Daily Circle Pulse. It never counts anything, never says you missed a day, and only offers times outside
 * quiet hours (07:00 to 21:00). Off by default. Lives on this phone only.
 */
object CircleRhythm {
    private const val PREFS = "circle_rhythm"

    @Immutable
    data class Slot(val minuteOfDay: Int, val label: String)

    /** The only times offered, all outside quiet hours. */
    val slots = listOf(Slot(7 * 60, "Morning, 7:00"), Slot(12 * 60 + 30, "Midday, 12:30"), Slot(19 * 60, "Evening, 7:00"))

    @Immutable
    data class Setting(val enabled: Boolean = false, val minuteOfDay: Int = slots.first().minuteOfDay)

    fun get(context: Context, circleId: String): Setting {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Setting(p.getBoolean("on_$circleId", false), p.getInt("at_$circleId", slots.first().minuteOfDay))
    }

    fun set(context: Context, circleId: String, name: String, isWork: Boolean, setting: Setting) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("on_$circleId", setting.enabled)
            .putInt("at_$circleId", setting.minuteOfDay)
            .apply()
        if (setting.enabled) schedule(context, circleId, name, isWork, setting.minuteOfDay)
        else WorkManager.getInstance(context).cancelUniqueWork(workName(circleId))
    }

    /** Turns the rhythm off for a circle I've left. */
    fun clear(context: Context, circleId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("on_$circleId").remove("at_$circleId").apply()
        WorkManager.getInstance(context).cancelUniqueWork(workName(circleId))
    }

    internal fun workName(circleId: String) = "circle_rhythm_$circleId"

    /** Milliseconds from [now] until the next [minuteOfDay]; always in the future, at most a day away. */
    internal fun delayUntil(now: LocalDateTime, minuteOfDay: Int): Long {
        var next = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
        if (!next.isAfter(now)) next = next.plusDays(1)
        val zone = ZoneId.systemDefault()
        return next.atZone(zone).toInstant().toEpochMilli() - now.atZone(zone).toInstant().toEpochMilli()
    }

    internal fun schedule(context: Context, circleId: String, name: String, isWork: Boolean, minuteOfDay: Int) {
        val req = OneTimeWorkRequestBuilder<CircleRhythmWorker>()
            .setInitialDelay(delayUntil(LocalDateTime.now(), minuteOfDay), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("circleId" to circleId, "name" to name, "work" to isWork, "at" to minuteOfDay))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(circleId), ExistingWorkPolicy.REPLACE, req)
    }

    /** The words of the nudge. An invitation, never a count or a reproach. */
    internal fun text(name: String, isWork: Boolean): Pair<String, String> =
        if (isWork) "$name: today's board" to "See what needs the team and what's due this week."
        else "A moment with $name" to "See who to hold in prayer and what's been shared."
}

class CircleRhythmWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val circleId = inputData.getString("circleId") ?: return Result.success()
        val name = inputData.getString("name").orEmpty().ifBlank { "your Circle" }
        val isWork = inputData.getBoolean("work", false)
        val setting = CircleRhythm.get(applicationContext, circleId)
        if (!setting.enabled) return Result.success()
        if (CirclesNotifier.canPost(applicationContext)) {
            CirclesNotifier.ensureChannel(applicationContext)
            val (title, body) = CircleRhythm.text(name, isWork)
            val uri = PushRouting.uri(CircleTarget(circleId, CircleDestination.Feed))
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val id = ("rhythm_$circleId").hashCode()
            val pending = PendingIntent.getActivity(applicationContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(applicationContext, CirclesNotifier.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_zuri_glyph)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(applicationContext).notify(id, n) }
        }
        CircleRhythm.schedule(applicationContext, circleId, name, isWork, setting.minuteOfDay)
        return Result.success()
    }
}

/** Notifications and the Daily rhythm, from the Circle's ⋯ menu. */
@Composable
fun CircleRhythmSheet(circleId: String, name: String, isWork: Boolean, muted: Boolean, onMute: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var setting by remember(circleId) { mutableStateOf(CircleRhythm.get(context, circleId)) }
    fun save(s: Setting) { setting = s; CircleRhythm.set(context, circleId, name, isWork, s) }
    CircleSheet(onDismiss) {
        Text("Notifications", style = MM.type.title, color = MM.colors.ink)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Posts and chat", style = MM.type.bodyStrong, color = MM.colors.ink)
                Text(
                    if (muted) "Muted. You'll still see everything when you open the ${if (isWork) "team" else "Circle"}." else "On, gently: new posts, replies to you and answered prayers.",
                    style = MM.type.secondary, color = MM.colors.inkSecondary
                )
            }
            Switch(checked = !muted, onCheckedChange = { onMute(!it) }, modifier = Modifier.semantics { contentDescription = "Notifications for $name" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily rhythm", style = MM.type.bodyStrong, color = MM.colors.ink)
                Text(
                    if (isWork) "One quiet nudge a day to look at the board. Nothing is counted." else "One quiet invitation a day to spend a moment here. Nothing is counted, and missing a day is fine.",
                    style = MM.type.secondary, color = MM.colors.inkSecondary
                )
            }
            Switch(
                checked = setting.enabled,
                onCheckedChange = { save(setting.copy(enabled = it)) },
                modifier = Modifier.semantics { contentDescription = "Daily rhythm for $name" }
            )
        }
        if (setting.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                CircleRhythm.slots.forEach { s ->
                    MMChip(s.label, selected = setting.minuteOfDay == s.minuteOfDay, onClick = { save(setting.copy(minuteOfDay = s.minuteOfDay)) })
                }
            }
            if (!CirclesNotifier.canPost(context)) {
                Text("Notifications are off for MeetingMind, so the rhythm can't reach you yet.", style = MM.type.caption, color = MM.colors.danger)
            }
        }
    }
}

private typealias Setting = CircleRhythm.Setting
