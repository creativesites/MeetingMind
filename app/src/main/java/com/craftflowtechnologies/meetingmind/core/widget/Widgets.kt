package com.craftflowtechnologies.meetingmind.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.craftflowtechnologies.meetingmind.MainActivity
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalRepository
import com.craftflowtechnologies.meetingmind.core.devotional.LocalDay
import com.craftflowtechnologies.meetingmind.core.notify.DeepLink
import com.craftflowtechnologies.meetingmind.core.notify.DeepLinks
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import com.craftflowtechnologies.meetingmind.core.share.BackgroundLibrary
import com.craftflowtechnologies.meetingmind.core.share.BackgroundSpec
import com.craftflowtechnologies.meetingmind.core.share.ShareCardContent
import com.craftflowtechnologies.meetingmind.core.share.ShareCardRenderer
import com.craftflowtechnologies.meetingmind.core.share.ShareFont
import com.craftflowtechnologies.meetingmind.core.share.ShareFormat
import com.craftflowtechnologies.meetingmind.core.share.ShareStyle
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Verse of the Day on a picture (PLAN_V2 F6). Drawn by the share-card renderer, so it looks like the app. */
class VerseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)
}

/** Today at a glance: what's next, and today's devotional one tap away. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)
}

/** Your next meeting, a countdown to it, Record, and the prep line (docs/PLAN_PROFESSIONAL.md D5.1). */
class NextMeetingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)
}

/**
 * What the next-meeting widget shows. A home screen is public, so a sensitive profile gets no
 * title and no names: "Next meeting", a countdown, and Record.
 */
data class NextMeetingModel(
    val title: String, val prep: String, val startsInMs: Long, val started: Boolean,
    val event: com.craftflowtechnologies.meetingmind.core.work.PulseEvent?
) {
    companion object {
        fun of(
            s: com.craftflowtechnologies.meetingmind.core.work.WorkSettings, event: com.craftflowtechnologies.meetingmind.core.work.PulseEvent?,
            line: com.craftflowtechnologies.meetingmind.core.work.PulseDayLine?, now: Long
        ): NextMeetingModel {
            if (event == null) return NextMeetingModel("Nothing else today", "", 0, false, null)
            val known = line != null && !line.firstMeeting
            val prep = when {
                !known -> ""
                s.profile.sensitive -> if (line!!.open > 0) "${line.open} open" else ""
                else -> listOfNotNull(line!!.withLabel?.let { "With $it" }, line.open.takeIf { it > 0 }?.let { "$it still open" }).joinToString(" · ")
            }
            return NextMeetingModel(if (s.profile.sensitive) "Your next meeting" else event.title, prep, (event.begin - now).coerceAtLeast(0), event.begin <= now, event)
        }
    }
}

object Widgets {
    private const val NOW = "widgets-now"
    private const val PERIODIC = "widgets-periodic"

    /** Redraws both widgets soon (they're cheap), and keeps them fresh through the day. */
    fun refresh(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!hasAny(context)) return
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetWorker>().build())
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<WidgetWorker>(3, TimeUnit.HOURS).build())
    }

    private fun ids(context: Context, cls: Class<*>) = runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls)) }.getOrDefault(IntArray(0))

    fun hasAny(context: Context) = ids(context, VerseWidget::class.java).isNotEmpty() || ids(context, TodayWidget::class.java).isNotEmpty() ||
        ids(context, NextMeetingWidget::class.java).isNotEmpty()

    internal suspend fun draw(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val verseIds = ids(context, VerseWidget::class.java)
        if (verseIds.isNotEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_verse)
            val scripture = ScriptureService(context)
            val today = LocalDate.now()
            val ref = runCatching { scripture.verseOfTheDay(today.dayOfYear) }.getOrNull()
            val passage = ref?.let { (runCatching { scripture.passage(it) }.getOrNull() as? PassageResult.Found)?.passage }
            if (ref != null && passage != null) {
                val bg = BackgroundLibrary.forDay(context, today.toEpochDay(), 1)?.let { BackgroundSpec.Photo(it.file.path) } ?: BackgroundSpec.Pack("dawn")
                val card = ShareCardRenderer.render(
                    context, ShareCardContent("Verse of the day", passage.text, "${ref.display()} · ${passage.versionAbbreviation}", null),
                    ShareStyle(format = ShareFormat.SQUARE, background = bg, font = ShareFont.LORA, scrim = 0.45f, watermark = false), scale = 0.5f
                )
                views.setImageViewBitmap(R.id.widget_verse_image, card)
                views.setViewVisibility(R.id.widget_verse_placeholder, android.view.View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_verse_root, DeepLinks.pendingIntent(context, DeepLink.Bible))
            manager.updateAppWidget(verseIds, views)
        }

        val nextIds = ids(context, NextMeetingWidget::class.java)
        if (nextIds.isNotEmpty()) runCatching { drawNextMeeting(context, manager, nextIds) }

        val todayIds = ids(context, TodayWidget::class.java)
        if (todayIds.isNotEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_today_date, SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()))
            val next = runCatching {
                val prefs = com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(context).preferencesFlow
                val on = prefs.first().calendarEnabled
                val calendar = com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvents(context)
                if (!on || !calendar.hasPermission()) null
                else {
                    val now = System.currentTimeMillis()
                    val end = LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    calendar.between(now, end).filter { !it.allDay && it.begin >= now - 10 * 60_000 }.minByOrNull { it.begin }
                }
            }.getOrNull()
            views.setTextViewText(R.id.widget_today_next, next?.let { "${SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(it.begin))}  ${it.title}" } ?: "Nothing else scheduled today")
            val devotional = runCatching { DevotionalRepository(context).find(LocalDay.today())?.devotional }.getOrNull()
            views.setTextViewText(R.id.widget_today_devotional_title, devotional?.title ?: "Today's devotional")
            views.setTextViewText(R.id.widget_today_listen, if (devotional != null) "Read" else "Begin")
            views.setOnClickPendingIntent(R.id.widget_today_devotional, DeepLinks.pendingIntent(context, DeepLink.Devotional))
            views.setOnClickPendingIntent(R.id.widget_today_root, android.app.PendingIntent.getActivity(
                context, 77, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            ))
            manager.updateAppWidget(todayIds, views)
        }
    }
}

private suspend fun drawNextMeeting(context: Context, manager: AppWidgetManager, ids: IntArray) {
    val prefs = com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(context)
    val settings = prefs.workSettings.first()
    val now = System.currentTimeMillis()
    val event = if (!prefs.preferencesFlow.first().calendarEnabled) null else {
        val calendar = com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvents(context)
        val end = LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        calendar.between(now - 10 * 60_000L, end).filter { !it.allDay && it.end > now }.minByOrNull { it.begin }?.let {
            com.craftflowtechnologies.meetingmind.core.work.PulseEvent(it.key, it.title, it.begin, it.end, it.otherPeople, it.attendees.filter { a -> !a.isSelf }.mapNotNull { a -> a.email })
        }
    }
    val line = event?.let { runCatching { com.craftflowtechnologies.meetingmind.core.work.Pulse(com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase.getInstance(context)).today(listOf(it)).firstOrNull() }.getOrNull() }
    val m = NextMeetingModel.of(settings, event, line, now)
    val views = RemoteViews(context.packageName, R.layout.widget_next_meeting)
    views.setTextViewText(R.id.widget_next_title, m.title)
    views.setTextViewText(R.id.widget_next_prep, m.prep)
    views.setViewVisibility(R.id.widget_next_prep, if (m.prep.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
    views.setViewVisibility(R.id.widget_next_countdown, if (event == null || m.started) android.view.View.GONE else android.view.View.VISIBLE)
    views.setViewVisibility(R.id.widget_next_now, if (event != null && m.started) android.view.View.VISIBLE else android.view.View.GONE)
    if (event != null && !m.started) {
        views.setChronometerCountDown(R.id.widget_next_countdown, true)
        views.setChronometer(R.id.widget_next_countdown, android.os.SystemClock.elapsedRealtime() + m.startsInMs, null, true)
    }
    views.setViewVisibility(R.id.widget_next_record, if (event == null) android.view.View.GONE else android.view.View.VISIBLE)
    if (event != null) {
        views.setOnClickPendingIntent(R.id.widget_next_record, DeepLinks.pendingIntent(context, DeepLink.RecordEvent(event.key, event.title, "MEETING")))
        views.setOnClickPendingIntent(R.id.widget_next_root, DeepLinks.pendingIntent(context, DeepLink.Prepare(event.key, event.title, event.begin, event.end, event.people.joinToString(", "), event.emails.joinToString(", "))))
    } else views.setOnClickPendingIntent(R.id.widget_next_root, DeepLinks.pendingIntent(context, DeepLink.WorkSpace))
    manager.updateAppWidget(ids, views)
}

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Widgets.draw(applicationContext) }
        return Result.success()
    }
}
