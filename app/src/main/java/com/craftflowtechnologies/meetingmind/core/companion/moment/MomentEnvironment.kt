package com.craftflowtechnologies.meetingmind.core.companion.moment

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Reads [DeviceFacts] from the system at open time (signature moments §3.3 rows 5 and 7). */
object MomentEnvironment {

    @Volatile private var lowEndCache: Boolean? = null

    fun read(context: Context): DeviceFacts = DeviceFacts(
        batterySaver = batterySaver(context),
        lowEnd = lowEnd(context),
        durationScale = durationScale(context)
    )

    /** The system animator duration scale; 0 is "animations off". */
    fun durationScale(context: Context): Float =
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)

    fun batterySaver(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

    /** `isLowRamDevice()`, or total RAM under 3 GB, or API < 28. Computed once and cached. */
    fun lowEnd(context: Context): Boolean = lowEndCache ?: computeLowEnd(context).also { lowEndCache = it }

    private fun computeLowEnd(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return isLowEnd(Build.VERSION.SDK_INT, lowRam = false, totalRamBytes = info.totalMem)
    }

    /** The rule, pure, for tests. */
    fun isLowEnd(sdk: Int, lowRam: Boolean, totalRamBytes: Long): Boolean =
        sdk < 28 || lowRam || (totalRamBytes in 1 until LowEndRamBytes)

    private const val LowEndRamBytes = 3L * 1024 * 1024 * 1024
}
