package com.craftflowtechnologies.meetingmind.core.circles

import android.content.Intent
import com.craftflowtechnologies.meetingmind.core.circles2.CircleTarget
import com.craftflowtechnologies.meetingmind.core.circles2.InviteCodes
import com.craftflowtechnologies.meetingmind.core.circles2.PushRouting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Two kinds of link reach Circles:
 *
 * - invite links: `meetingmind://c/GRACE-7K2Q` (and an https `.../c/GRACE-7K2Q` link once a domain exists).
 *   Replaces the old `mindcircle://join?...&key=...` link, which carried an encryption key in the URL.
 *   Only the code travels; the server decides whether it is valid.
 * - notification taps: `meetingmind://c/open?circle=ID&post=ID` from our own notifications, or the `data`
 *   extras Android puts on the launch intent when the system shows a push while the app is closed.
 */
object CircleDeepLinks {
    private val _pendingCode = MutableStateFlow<String?>(null)
    /** A code from a tapped link, waiting for the Circles list to open its Join sheet. */
    val pendingCode: StateFlow<String?> = _pendingCode.asStateFlow()

    private val _pendingTarget = MutableStateFlow<CircleTarget?>(null)
    /** A circle (and maybe a post) from a tapped notification, waiting for the app to navigate there. */
    val pendingTarget: StateFlow<CircleTarget?> = _pendingTarget.asStateFlow()

    fun handle(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        // Our own notification link first: it also starts with meetingmind://c/ but is not an invite.
        PushRouting.fromUri(data?.toString())?.let { _pendingTarget.value = it; return }
        if (data != null) {
            val isInvite = (data.scheme == "meetingmind" && data.host == "c") || data.pathSegments?.firstOrNull() == "c"
            if (isInvite) { InviteCodes.fromLink(data.toString())?.let { _pendingCode.value = it }; return }
        }
        // A push shown by the system while the app was closed: the data payload arrives as extras on the launcher intent.
        val extras = intent.extras ?: return
        if (!extras.containsKey("kind")) return
        val map = listOf("kind", "circleId", "postId").associateWith { extras.getString(it) }
        PushRouting.fromData(map)?.let { _pendingTarget.value = it }
    }

    fun consume() { _pendingCode.value = null }
    fun consumeTarget() { _pendingTarget.value = null }
}
