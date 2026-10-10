package com.craftflowtechnologies.meetingmind.core.circles

import android.content.Intent
import com.craftflowtechnologies.meetingmind.core.circles2.InviteCodes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Invite links: `meetingmind://c/GRACE-7K2Q` (and an https `.../c/GRACE-7K2Q` link once a domain exists).
 * Replaces the old `mindcircle://join?...&key=...` link, which carried an encryption key in the URL.
 * Only the code travels; the server decides whether it is valid.
 */
object CircleDeepLinks {
    private val _pendingCode = MutableStateFlow<String?>(null)
    /** A code from a tapped link, waiting for the Circles list to open its Join sheet. */
    val pendingCode: StateFlow<String?> = _pendingCode.asStateFlow()

    fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        val isInvite = (data.scheme == "meetingmind" && data.host == "c") || data.pathSegments?.firstOrNull() == "c"
        if (!isInvite) return
        InviteCodes.fromLink(data.toString())?.let { _pendingCode.value = it }
    }

    fun consume() { _pendingCode.value = null }
}
