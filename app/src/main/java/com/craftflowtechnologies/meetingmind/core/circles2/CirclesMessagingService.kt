package com.craftflowtechnologies.meetingmind.core.circles2

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives Circles pushes. A refreshed token is registered with the Worker (only if this person uses Circles);
 * a push that arrives while the app is open is shown on the "Circles" channel. When the app is closed the system
 * shows the push itself and the tap carries the same data to [CircleDeepLinks][com.craftflowtechnologies.meetingmind.core.circles.CircleDeepLinks].
 */
class CirclesMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { Circles2.pushTokens(applicationContext).onNewToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data: Map<String, String?> = message.data
        // Only our own push types (they all carry a kind). Anything else is not ours to show.
        if (data["kind"].isNullOrBlank()) return
        CirclesNotifier.show(applicationContext, data, message.notification?.title, message.notification?.body)
    }
}
