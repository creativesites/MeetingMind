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

/**
 * Shows the Worker's pushes on the "Circles" channel. Text comes from the push itself (the Worker never puts a post's
 * words or an anonymous author in it); tapping opens the right circle or post through `meetingmind://c/open?...`.
 */
object CirclesNotifier {
    const val CHANNEL_ID = "circles"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Circles", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New posts, prayer requests, answered prayers and a daily count of who prayed for you."
            }
        )
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
        val (fallbackTitle, fallbackBody) = PushRouting.fallbackText(PushKind.from(data["kind"]))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PushRouting.uri(target)), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val id = PushRouting.notificationId(data)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_day)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: fallbackTitle)
            .setContentText(body?.takeIf { it.isNotBlank() } ?: fallbackBody)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup("circles_" + (data["circleId"] ?: "all"))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
