package com.craftflowtechnologies.meetingmind.core.applock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLockState {
    /** App Lock is off: nothing is gated. */
    Disabled,
    /** App Lock is on and the owner has not authenticated in this session (or it timed out). */
    Locked,
    /** The system prompt is up. Still treated as locked for content purposes. */
    Unlocking,
    /** App Lock is on and the owner authenticated recently. */
    Unlocked;

    /** Private content must not be visible or reachable. */
    val isContentHidden: Boolean get() = this == Locked || this == Unlocking
}

/**
 * The single source of truth for "is MeetingMind locked?". Pure Kotlin — no Android types, time
 * comes from [now] (a monotonic clock in production) — so every rule below is unit-tested.
 *
 * Rules:
 *  - Fails closed: until [onPreferenceLoaded] runs the state is [AppLockState.Locked].
 *  - A fresh process is always Locked when App Lock is on (state lives in memory only).
 *  - Leaving the app starts a clock; returning after [graceMs] or more locks it. Rotation and
 *    moving between screens never reach this class (the former is filtered by the caller).
 *  - Nothing here lowers protection except a successful authentication or turning the setting off.
 */
class AppLockController(
    private val now: () -> Long,
    private val graceMs: Long = DEFAULT_GRACE_MS
) {
    private val _state = MutableStateFlow(AppLockState.Locked)
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** False until the saved preference has been read, so the UI shows nothing rather than guess. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var enabled = true
    private var backgroundedAt: Long? = null

    /** Applies the saved (or just-changed) preference. */
    fun onPreferenceLoaded(appLockEnabled: Boolean) {
        enabled = appLockEnabled
        _ready.value = true
        _state.value = when {
            !appLockEnabled -> AppLockState.Disabled
            _state.value == AppLockState.Disabled -> AppLockState.Locked
            else -> _state.value
        }
        if (!appLockEnabled) backgroundedAt = null
    }

    /** The owner has just authenticated to turn the lock on: this session starts unlocked. */
    fun onEnabledBySession() {
        enabled = true
        _ready.value = true
        _state.value = AppLockState.Unlocked
        backgroundedAt = null
    }

    /** The app left the foreground. Ignored for configuration changes and when nothing is unlocked. */
    fun onAppBackgrounded(isChangingConfigurations: Boolean = false) {
        if (isChangingConfigurations) return
        if (_state.value == AppLockState.Unlocked && backgroundedAt == null) backgroundedAt = now()
    }

    /** The app is visible again. Locks if it was away for at least the grace period. */
    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (enabled && _state.value == AppLockState.Unlocked && now() - since >= graceMs) {
            _state.value = AppLockState.Locked
        }
    }

    /** Locks right now (e.g. a future "Lock now" action). No-op when App Lock is off. */
    fun lockNow() {
        if (enabled && _ready.value) _state.value = AppLockState.Locked
        backgroundedAt = null
    }

    /** Called just before showing the prompt. Idempotent so a re-created screen can resume it. */
    fun beginUnlock(): Boolean {
        if (!enabled) return false
        return when (_state.value) {
            AppLockState.Locked, AppLockState.Unlocking -> { _state.value = AppLockState.Unlocking; true }
            else -> false
        }
    }

    fun onUnlockSucceeded() {
        if (enabled && _state.value == AppLockState.Unlocking) {
            _state.value = AppLockState.Unlocked
            backgroundedAt = null
        }
    }

    fun onUnlockFailed() {
        if (enabled && _state.value == AppLockState.Unlocking) _state.value = AppLockState.Locked
    }

    companion object {
        /**
         * How long MeetingMind may be away before it locks. Long enough that a permission dialog,
         * the share sheet, the photo picker or a quick app switch doesn't ask again; short enough
         * that leaving the phone on a table does. One constant — change it here.
         */
        const val DEFAULT_GRACE_MS = 5 * 60_000L // 5 minutes
    }
}
