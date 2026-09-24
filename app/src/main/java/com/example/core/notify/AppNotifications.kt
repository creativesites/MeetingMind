package com.example.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a notification tap should land. */
sealed interface DeepLink {
    data class Processing(val meetingId: String) : DeepLink
    data class Recording(val meetingId: String) : DeepLink
    data object Models : DeepLink
    data object Bible : DeepLink
    data object Devotional : DeepLink
    data object PrayerList : DeepLink
    data object ReadingPlans : DeepLink
    data object Home : DeepLink
}

/**
 * Hands deep links from notification taps to the Compose navigation. The activity puts them in;
 * the app's navigation takes each one out exactly once.
 */
object DeepLinks {
    private const val EXTRA_TARGET = "meetmind.deeplink.target"
    private const val EXTRA_MEETING = "meetmind.deeplink.meeting"

    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun consume(): DeepLink? = _pending.value.also { _pending.value = null }

    fun handle(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: return
        val meeting = intent.getStringExtra(EXTRA_MEETING)
        _pending.value = when (target) {
            "processing" -> meeting?.let { DeepLink.Processing(it) }
            "recording" -> meeting?.let { DeepLink.Recording(it) }
            "models" -> DeepLink.Models
            "bible" -> DeepLink.Bible
            "devotional" -> DeepLink.Devotional
            "prayerlist" -> DeepLink.PrayerList
            "plans" -> DeepLink.ReadingPlans
            "home" -> DeepLink.Home
            else -> null
        }
        intent.removeExtra(EXTRA_TARGET)
    }

    fun pendingIntent(context: Context, link: DeepLink): PendingIntent {
        val (target, meeting) = when (link) {
            is DeepLink.Processing -> "processing" to link.meetingId
            is DeepLink.Recording -> "recording" to link.meetingId
            DeepLink.Models -> "models" to null
            DeepLink.Bible -> "bible" to null
            DeepLink.Devotional -> "devotional" to null
            DeepLink.PrayerList -> "prayerlist" to null
            DeepLink.ReadingPlans -> "plans" to null
            DeepLink.Home -> "home" to null
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_TARGET, target)
            .apply { meeting?.let { putExtra(EXTRA_MEETING, it) } }
        // A distinct request code per destination, so one notification's tap target never
        // replaces another's.
        return PendingIntent.getActivity(
            context, (target + meeting).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Every notification MeetingMind posts, and their channels.
 *
 * Privacy rule shared by all of them: only a recording's title and a stage name ever appear —
 * never transcript or summary text, which could be read off a lock screen.
 */
object AppNotifications {
    const val CHANNEL_WORK = "meetmind_processing_channel"
    const val CHANNEL_DOWNLOADS = "meetmind_downloads"
    const val CHANNEL_DONE = "meetmind_finished"
    const val CHANNEL_DEVOTIONAL = "meetmind_devotional"
    private const val ID_DEVOTIONAL = 5001

    const val ID_PROCESSING = 2001
    private const val ID_DOWNLOAD_BASE = 3000
    private const val ID_DONE_BASE = 4000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WORK, "Transcription in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while a recording is transcribed and analysed"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while an AI model or a Bible translation downloads"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEVOTIONAL, "Daily devotional", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when today's devotional is ready"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Finished", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a transcript is ready or a download has finished"
            }
        )
    }

    /**
     * Today's devotional is ready. Only its title shows — a devotional can touch on private
     * things, and lock screens are public.
     */
    fun devotionalReady(context: Context, title: String, label: String) {
        post(
            context, ID_DEVOTIONAL,
            NotificationCompat.Builder(context, CHANNEL_DEVOTIONAL)
                .setSmallIcon(android.R.drawable.ic_menu_day)
                .setContentTitle("Your devotional for today")
                .setContentText(title)
                .setSubText(label)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(DeepLinks.pendingIntent(context, DeepLink.Devotional))
                .addAction(android.R.drawable.ic_menu_view, "Open", DeepLinks.pendingIntent(context, DeepLink.Devotional))
                .build()
        )
    }

    const val CHANNEL_REMINDERS = "meetmind_reminders"

    /** A gentle reminder: prayer times, today's reading, evening reflection, a meeting soon. */
    fun faithReminder(context: Context, id: Int, title: String, text: String, link: DeepLink) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Prayer times, reading plans, evening reflection and meetings" }
            )
        }
        post(
            context, 6000 + id,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(DeepLinks.pendingIntent(context, link))
                .build()
        )
    }

    /** The ongoing notification while a recording is processed. */
    fun processingProgress(
        context: Context,
        meetingId: String,
        title: String,
        step: String,
        percent: Int,
        queuedBehind: Int,
        cancelIntent: PendingIntent?
    ) = NotificationCompat.Builder(context, CHANNEL_WORK)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(if (queuedBehind > 0) "Transcribing “$title” · $queuedBehind more waiting" else "Transcribing “$title”")
        .setContentText(step)
        .setSubText(if (percent in 1..99) "$percent%" else null)
        .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(DeepLinks.pendingIntent(context, DeepLink.Processing(meetingId)))
        .apply { cancelIntent?.let { addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", it) } }
        .build()

    fun processingFinished(context: Context, meetingId: String, title: String, outcome: Outcome, detail: String? = null) {
        val (heading, text, link) = when (outcome) {
            Outcome.READY -> Triple("Transcript ready", "“$title” is ready to read.", DeepLink.Recording(meetingId))
            Outcome.NEEDS_MODEL -> Triple("Recording saved", "Install the speech model to transcribe “$title”.", DeepLink.Models)
            Outcome.FAILED -> Triple("Couldn’t finish “$title”", detail ?: "Tap to try again.", DeepLink.Processing(meetingId))
        }
        post(
            context, ID_DONE_BASE + (meetingId.hashCode() and 0xFFF),
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(if (outcome == Outcome.FAILED) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done)
                .setContentTitle(heading)
                .setContentText(text)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(DeepLinks.pendingIntent(context, link))
                .build()
        )
    }

    enum class Outcome { READY, NEEDS_MODEL, FAILED }

    fun downloadId(modelId: String) = ID_DOWNLOAD_BASE + (modelId.hashCode() and 0x3FF)

    fun downloadProgress(context: Context, modelName: String, done: Long, total: Long, pauseIntent: PendingIntent?) =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $modelName")
            .setContentText(if (total > 0) "${mb(done)} of ${mb(total)}" else "Starting…")
            .setSubText(if (total > 0) "${(done * 100 / total).coerceIn(0, 100)}%" else null)
            .setProgress(100, if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(DeepLinks.pendingIntent(context, DeepLink.Models))
            .apply { pauseIntent?.let { addAction(android.R.drawable.ic_media_pause, "Pause", it) } }
            .build()

    fun downloadFinished(context: Context, modelId: String, modelName: String, ok: Boolean, detail: String?) {
        post(
            context, downloadId(modelId) + 500,
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (ok) "$modelName is ready" else "Download paused: $modelName")
                .setContentText(if (ok) "It will be used for your next recording." else detail ?: "It will continue when you're back online.")
                .setAutoCancel(true)
                .setContentIntent(DeepLinks.pendingIntent(context, DeepLink.Models))
                .build()
        )
    }

    fun bibleDownloadId(bibleId: Int) = ID_DOWNLOAD_BASE + 1500 + (bibleId and 0x3FF)

    fun bibleDownloadProgress(context: Context, label: String, done: Int, total: Int, book: String?, pauseIntent: PendingIntent?) =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading the $label Bible")
            .setContentText(if (done > 0) "$done of $total chapters" + (book?.let { " · $it" } ?: "") else "Starting…")
            .setSubText(if (total > 1 && done > 0) "${done * 100 / total}%" else null)
            .setProgress(total.coerceAtLeast(1), done.coerceIn(0, total.coerceAtLeast(1)), done <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(DeepLinks.pendingIntent(context, DeepLink.Bible))
            .apply { pauseIntent?.let { addAction(android.R.drawable.ic_media_pause, "Pause", it) } }
            .build()

    fun bibleDownloadFinished(context: Context, bibleId: Int, label: String, ok: Boolean) {
        post(
            context, bibleDownloadId(bibleId) + 500,
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (ok) "The $label Bible is on your phone" else "Bible download paused")
                .setContentText(if (ok) "Read and search it anywhere, even offline." else "Open the Bible to try again.")
                .setAutoCancel(true)
                .setContentIntent(DeepLinks.pendingIntent(context, DeepLink.Bible))
                .build()
        )
    }

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        ensureChannels(context)
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) } // no permission → silently skip
    }

    private fun mb(bytes: Long) = if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1e9) else "${bytes / 1_000_000} MB"
}
