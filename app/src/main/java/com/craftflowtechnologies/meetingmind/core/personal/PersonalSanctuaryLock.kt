package com.craftflowtechnologies.meetingmind.core.personal

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-space Sanctuary Privacy Lock & Recents Shield preferences for Personal.
 *
 * Lets a user protect the Personal Sanctuary (journals, capsules, inner circle) behind the device's
 * biometric / screen-lock credential even while the rest of the app (e.g., Work) stays open.
 * Session unlock state stays in memory and re-locks automatically when locked manually or after
 * the app process restarts.
 */
object PersonalSanctuaryLock {
    private const val PREFS = "mm_personal_sanctuary_lock"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_RECENTS_SHIELD = "recents_shield"

    private val _sessionUnlocked = MutableStateFlow(false)
    val sessionUnlocked: StateFlow<Boolean> = _sessionUnlocked.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isLockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK_ENABLED, false)

    fun setLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        if (!enabled) _sessionUnlocked.value = true
    }

    fun isRecentsShieldEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_RECENTS_SHIELD, false)

    fun setRecentsShieldEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RECENTS_SHIELD, enabled).apply()
    }

    fun markUnlocked() {
        _sessionUnlocked.value = true
    }

    fun lockNow() {
        _sessionUnlocked.value = false
    }

    fun requiresUnlock(context: Context): Boolean =
        isLockEnabled(context) && !_sessionUnlocked.value
}
