package com.craftflowtechnologies.meetingmind.core.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls whether subscription tiering is enforced or bypassed for Play Store Closed Testing.
 * In [CLOSED_TESTING], testers get full Pro features and managed/built-in AI without paywall gates.
 * In [PRODUCTION], Free/BYOK/Pro/Church tiers and paywall rules apply.
 */
enum class TieringMode(val label: String, val description: String) {
    CLOSED_TESTING(
        "Closed Testing (No Tiering)",
        "Tester's benefit: all Pro features unlocked with no paywall interruptions."
    ),
    PRODUCTION(
        "Production Tiering",
        "Enforces Free, BYOK, Pro and Church tiers, cloud allowances and paywalls."
    )
}

object TieringModeStore {
    private const val PREFS = "meetmind_tiering_mode"
    private const val KEY = "mode"
    private val _mode = MutableStateFlow(TieringMode.CLOSED_TESTING)
    val mode: StateFlow<TieringMode> = _mode.asStateFlow()
    @Volatile private var loaded = false

    fun current(context: Context): TieringMode {
        if (!loaded) {
            val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            _mode.value = TieringMode.entries.firstOrNull { it.name == saved } ?: TieringMode.CLOSED_TESTING
            loaded = true
        }
        return _mode.value
    }

    fun set(context: Context, mode: TieringMode) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        _mode.value = mode
        loaded = true
    }

    fun setForTest(mode: TieringMode) {
        _mode.value = mode
        loaded = true
    }

    fun effectiveEntitlements(raw: Entitlements, mode: TieringMode = _mode.value): Entitlements =
        if (mode == TieringMode.CLOSED_TESTING && !raw.isPaid) {
            Entitlements(plan = Plan.PRO, state = EntitlementState.ACTIVE, willRenew = true, verifiedAtMs = System.currentTimeMillis())
        } else raw
}
