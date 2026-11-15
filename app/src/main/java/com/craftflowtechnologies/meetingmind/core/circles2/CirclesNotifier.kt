package com.craftflowtechnologies.meetingmind.core.circles2

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.craftflowtechnologies.meetingmind.MainActivity
import com.craftflowtechnologies.meetingmind.R

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** In-app alert emitted when a foreground push arrives for a Circle tab other than the active tab. */
data class ForegroundCircleAlert(
    val circleId: String,
    val destination: CircleDestination,
    val title: String,
    val body: String,
    val postId: String? = null
)

/**
 * Shows the Worker's pushes on the Circles channels. Text comes from the push itself (the Worker never puts a post's
 * words or an anonymous author in it); tapping opens the right circle or post through `meetingmind://c/open?...`.
 */
object CirclesNotifier {
    const val CHANNEL_ID = "circles"
    const val CHANNEL_HIGH_ID = "circles_posts"

    /** Set while the user is actively viewing a circle screen + tab so redundant foreground banners don't interrupt. */
    @Volatile var activeCircleId: String? = null
    @Volatile var activeDestination: CircleDestination? = null

    private val _foregroundAlerts = MutableSharedFlow<ForegroundCircleAlert>(extraBufferCapacity = 16)
    val foregroundAlerts: SharedFlow<ForegroundCircleAlert> = _foregroundAlerts

    /** True when the user is already looking at the exact Circle & tab this push targets. */
    internal fun shouldSuppressForeground(
        data: Map<String, String?>,
        currentCircleId: String? = activeCircleId,
        currentDestination: CircleDestination? = activeDestination
    ): Boolean {
        val circleId = data["circleId"] ?: return false
        if (circleId != currentCircleId) return false
        val target = PushRouting.fromData(data) ?: return true
        return currentDestination == null || currentDestination == target.destination
    }

    internal fun onForegroundMessage(context: Context, data: Map<String, String?>, title: String?, body: String?) {
        val target = PushRouting.fromData(data) ?: return
        val circleId = data["circleId"]
        if (!circleId.isNullOrBlank() && circleId == activeCircleId) {
            if (shouldSuppressForeground(data)) return
            // Different tab inside the same open Circle: surface a subtle in-app notice rather than interrupting.
            val kind = PushKind.from(data["kind"])
            val (fallbackTitle, fallbackBody) = PushRouting.fallbackText(kind)
            _foregroundAlerts.tryEmit(
                ForegroundCircleAlert(
                    circleId = circleId,
                    destination = target.destination,
                    title = title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                    body = body?.takeIf { it.isNotBlank() } ?: fallbackBody,
                    postId = target.postId
                )
            )
            return
        }
        show(context, data, title, body)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Circles", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Circles digests and general updates."
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_HIGH_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_HIGH_ID, "Circle Posts & Activity", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New posts, testimonies, prayer requests, comments, and chat messages in your circles."
                }
            )
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Shows one push. [title] and [body] are the push's own text when it had any. */
    fun show(context: Context, data: Map<String, String?>, title: String?, body: String?) {
        if (!canPost(context)) return
        val target = PushRouting.fromData(data) ?: return
        ensureChannel(context)
        val kind = PushKind.from(data["kind"])
        val channelId = if (kind == PushKind.Digest) CHANNEL_ID else CHANNEL_HIGH_ID
        val (fallbackTitle, fallbackBody) = PushRouting.fallbackText(kind)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PushRouting.uri(target)), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val id = PushRouting.notificationId(data)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val resolvedBody = body?.takeIf { it.isNotBlank() } ?: fallbackBody
        val groupKey = "circles_" + (data["circleId"] ?: "all")
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_zuri_glyph)
            .setContentTitle(resolvedTitle)
            .setContentText(resolvedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(resolvedBody))
            .setPriority(if (kind == PushKind.Digest) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .build()
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_zuri_glyph)
            .setContentTitle(resolvedTitle)
            .setContentText(resolvedBody)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Circle activity"))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        runCatching {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(id, n)
            nm.notify(groupKey.hashCode(), summary)
        }
    }
}
