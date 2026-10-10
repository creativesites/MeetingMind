package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive

import android.content.Context
import android.util.Log
import com.craftflowtechnologies.meetingmind.BuildConfig

/**
 * A crash guard for the Rive renderer. Rive is native code that only runs on a real device (tests
 * always draw with Canvas), so a device-specific failure in it would otherwise take the app down
 * every time a companion appears, including on launch.
 *
 * Before the first Rive view of a process is created the guard writes an "in flight" mark,
 * synchronously; once a Rive companion has played for a moment it clears it. If the process dies
 * in between, the next launch finds the mark, turns Rive off for this app version and every
 * companion draws with Canvas instead. A new version gets a fresh try.
 */
object RiveSafety {
    private const val Prefs = "companion_rive_safety"
    private const val InFlight = "in_flight"
    private const val DisabledVersion = "disabled_version"

    @Volatile private var checked = false
    @Volatile private var allowed = true
    @Volatile private var attempted = false
    @Volatile private var healthy = false

    /** Whether Rive may be used in this process. The first call settles it from the last run. */
    fun allowed(context: Context): Boolean {
        if (checked) return allowed
        synchronized(this) {
            if (!checked) {
                allowed = runCatching {
                    val p = context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
                    if (p.getBoolean(InFlight, false)) {
                        Log.w(RiveRuntime.Tag, "The last run stopped while starting Rive; companions use Canvas for this version")
                        p.edit().putBoolean(InFlight, false).putInt(DisabledVersion, BuildConfig.VERSION_CODE).commit()
                    }
                    p.getInt(DisabledVersion, -1) != BuildConfig.VERSION_CODE
                }.getOrDefault(false)
                checked = true
            }
        }
        return allowed
    }

    /** Marks that a Rive view is about to be created. Written synchronously so it survives a crash. */
    fun markAttempt(context: Context) {
        if (attempted || healthy) return
        attempted = true
        runCatching {
            context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putBoolean(InFlight, true).commit()
        }
    }

    /** Clears the mark once a Rive companion has been playing without trouble. */
    fun markHealthy(context: Context) {
        if (healthy) return
        healthy = true
        runCatching {
            context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putBoolean(InFlight, false).apply()
        }
    }

    /** Turns Rive off for the rest of this process after a caught failure. */
    fun disableForProcess() {
        allowed = false
        checked = true
    }
}
