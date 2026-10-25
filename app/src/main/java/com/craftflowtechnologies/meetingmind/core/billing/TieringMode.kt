package com.craftflowtechnologies.meetingmind.core.billing

import android.content.Context
import com.craftflowtechnologies.meetingmind.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether paywalls and plan checks run, or are switched off for a debug build.
 *
 * This is a developer tool, never a product setting: [PRODUCTION] is the default everywhere and a
 * release build always enforces tiers, whatever value an earlier debug install may have saved.
 * [CLOSED_TESTING] only exists in debug builds, where it hides unasked paywalls and lets a tester
 * use their own Gemini key without a plan.
 */
enum class TieringMode(val label: String, val description: String) {
    CLOSED_TESTING(
        "Closed testing (no paywalls)",
        "Debug builds only: paywalls are not shown and a personal Gemini key works without a plan."
    ),
    PRODUCTION(
        "Production tiering",
        "Enforces Free, BYOK, Pro and Church tiers, cloud allowances and paywalls."
    )
}

/** True only where the tier switch may be drawn: debug builds. Extracted so it is tested without a Context. */
fun showTierSwitch(isDebug: Boolean): Boolean = isDebug

object TieringModeStore {
    private const val PREFS = "meetmind_tiering_mode"
    private const val KEY = "mode"
    private val _mode = MutableStateFlow(TieringMode.PRODUCTION)
    val mode: StateFlow<TieringMode> = _mode.asStateFlow()
    @Volatile private var loaded = false

    fun current(context: Context): TieringMode {
        // A release build can never be put in closed-testing mode, even by a value saved earlier.
        if (!showTierSwitch(BuildConfig.DEBUG)) {
            _mode.value = TieringMode.PRODUCTION
            loaded = true
            return TieringMode.PRODUCTION
        }
        if (!loaded) {
            val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            _mode.value = TieringMode.entries.firstOrNull { it.name == saved } ?: TieringMode.PRODUCTION
            loaded = true
        }
        return _mode.value
    }

    fun set(context: Context, mode: TieringMode) {
        val next = if (showTierSwitch(BuildConfig.DEBUG)) mode else TieringMode.PRODUCTION
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.name).apply()
        _mode.value = next
        loaded = true
    }

    fun setForTest(mode: TieringMode) {
        _mode.value = mode
        loaded = true
    }
}
