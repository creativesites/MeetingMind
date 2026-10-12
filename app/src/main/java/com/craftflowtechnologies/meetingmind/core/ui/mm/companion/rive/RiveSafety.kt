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
 *
 * The cost: one failed start (or a process death in that window, for any reason) turns Rive off
 * silently for the whole version. The window is kept short: the mark clears after half a second of
 * play, or when a loaded Rive view leaves the screen. See docs/mvp/ZURI_EXPERIENCE.md §10.11.
 */
object RiveSafety {
    private const val Prefs = "companion_rive_safety"
    private const val InFlight = "in_flight"
    private const val DisabledVersion = "disabled_version"

    @Volatile private var checked = false
    @Volatile private var allowed = true
    @Volatile private var attempted = false
    @Volatile private var healthy = false
    @Volatile private var processDisabled = false

    /** Why Rive is or isn't allowed (Companion Lab). */
    enum class Status {
        /** Rive may run. */
        ALLOWED,
        /** An earlier run of this version stopped while a Rive view was starting: off for this version. */
        TRIPPED_FOR_VERSION,
        /** The native runtime failed in this process (caught): off until the app restarts. */
        OFF_FOR_PROCESS
    }

    fun status(context: Context): Status = when {
        allowed(context) -> Status.ALLOWED
        processDisabled -> Status.OFF_FOR_PROCESS
        else -> Status.TRIPPED_FOR_VERSION
    }

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
                    val ok = p.getInt(DisabledVersion, -1) != BuildConfig.VERSION_CODE
                    if (!ok) Log.w(RiveRuntime.Tag, "Rive is off for version ${BuildConfig.VERSION_CODE} (crash guard); companions use Canvas. Companion Lab: Reset Rive guard")
                    ok
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
        processDisabled = true
    }

    /**
     * Debug only (Companion Lab's "Reset Rive guard"): forgets a tripped guard and the in-flight
     * mark, so the next Rive companion tries again. A real crash will trip it again.
     */
    fun reset(context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
                .putBoolean(InFlight, false).remove(DisabledVersion).commit()
        }
        synchronized(this) {
            checked = false; allowed = true; attempted = false; healthy = false; processDisabled = false
        }
        Log.i(RiveRuntime.Tag, "Rive crash guard reset")
    }
}
