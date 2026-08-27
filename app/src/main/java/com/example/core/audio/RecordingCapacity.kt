package com.example.core.audio

/**
 * Pre-flight and in-flight storage/battery checks (design capture-pipeline spec §3.8), kept as
 * pure functions over primitives — no [android.content.Context], no [android.os.StatFs] — so the
 * thresholds themselves are directly unit-testable on plain JVM, the same pattern already used for
 * [com.example.core.audio.PlaybackController]'s logic functions and
 * [com.example.core.common.DeviceCapabilityDetector.recommendedLlmModelId]. Callers read live
 * storage via [com.example.core.common.DeviceCapabilityDetector.getAvailableStorageMb] and battery
 * via [android.os.BatteryManager], then hand the numbers in here.
 */
object RecordingCapacity {

    /** Below this, starting a new recording is refused outright — spec: "Refuse to start below
     * ~200 MB free with a plain explanation." */
    const val MIN_FREE_MB_TO_START = 200L

    /** During capture, at or below this free space a quiet storage warning appears. */
    const val LOW_STORAGE_WARNING_MB = 500L

    /** During capture, at or below this battery percent a quiet battery warning appears. */
    const val LOW_BATTERY_WARNING_PERCENT = 15

    /** The bitrate [AudioRecorder] actually encodes at — used only to translate free space into an
     * honest, approximate hours estimate. Keep in sync with [AudioRecorder]'s
     * `setAudioEncodingBitRate` call; this is duplicated rather than shared because pulling it out
     * into a real shared constant would mean threading it through a constructor for one number. */
    private const val ENCODING_BITRATE_BPS = 128_000

    /** "11 GB free, enough for about 40 hours" — the `#7a` footer line. Always rounds down; never
     * claims more recording time than is actually available. */
    fun formatStorageLine(availableStorageMb: Long): String {
        val freeMb = availableStorageMb.coerceAtLeast(0L)
        val gb = freeMb / 1024.0
        val bytesPerHour = (ENCODING_BITRATE_BPS / 8) * 3600L
        val hours = ((freeMb * 1024L * 1024L) / bytesPerHour)
        val gbText = if (gb >= 10) "%.0f".format(java.util.Locale.US, gb) else "%.1f".format(java.util.Locale.US, gb)
        return "$gbText GB free, enough for about $hours hours"
    }

    /** True when starting a new recording must be refused outright rather than left to fail
     * mid-capture once the disk actually fills up. */
    fun shouldRefuseToStart(availableStorageMb: Long): Boolean = availableStorageMb < MIN_FREE_MB_TO_START

    /** A single quiet in-flight warning line for the recording screen and notification, or null
     * when nothing is wrong. Storage is checked first since running out of disk stops a recording
     * outright, where a low battery still leaves some runway. [batteryPercent] is optional — a
     * device that won't report it just skips that half of the check rather than warning on a value
     * it doesn't actually have. */
    fun inFlightWarning(availableStorageMb: Long, batteryPercent: Int?): String? = when {
        availableStorageMb <= LOW_STORAGE_WARNING_MB -> "Storage is running low. Recording may stop."
        batteryPercent != null && batteryPercent <= LOW_BATTERY_WARNING_PERCENT -> "Battery low — recording may stop soon."
        else -> null
    }
}
