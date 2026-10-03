package com.craftflowtechnologies.meetingmind.core.circles

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles incoming `mindcircle://join?...` deep links.
 */
object CircleDeepLinks {
    private val _pendingInviteUri = MutableStateFlow<String?>(null)
    val pendingInviteUri = _pendingInviteUri.asStateFlow()

    fun handle(intent: Intent?) {
        val uri = intent?.data?.toString() ?: return
        if (uri.startsWith("mindcircle://join")) {
            _pendingInviteUri.value = uri
        }
    }

    fun consume(): String? {
        val uri = _pendingInviteUri.value
        _pendingInviteUri.value = null
        return uri
    }
}
