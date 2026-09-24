package com.example.core.widget

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
import com.example.MainActivity
import com.example.R
import com.example.core.devotional.DevotionalRepository
import com.example.core.devotional.LocalDay
import com.example.core.notify.DeepLink
import com.example.core.notify.DeepLinks
import com.example.core.scripture.PassageResult
import com.example.core.scripture.ScriptureService
import com.example.core.share.BackgroundLibrary
import com.example.core.share.BackgroundSpec
import com.example.core.share.ShareCardContent
import com.example.core.share.ShareCardRenderer
import com.example.core.share.ShareFont
import com.example.core.share.ShareFormat
import com.example.core.share.ShareStyle
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

    fun hasAny(context: Context) = ids(context, VerseWidget::class.java).isNotEmpty() || ids(context, TodayWidget::class.java).isNotEmpty()

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

        val todayIds = ids(context, TodayWidget::class.java)
        if (todayIds.isNotEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_today_date, SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()))
            val next = runCatching {
                val prefs = com.example.core.datastore.UserPreferencesManager(context).preferencesFlow
                val on = prefs.first().calendarEnabled
                val calendar = com.example.core.calendar.CalendarEvents(context)
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

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Widgets.draw(applicationContext) }
        return Result.success()
    }
}
