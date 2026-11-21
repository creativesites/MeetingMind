package com.craftflowtechnologies.meetingmind.feature.processing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * An honest "about 4 min left" for the processing wait, so a first-time user knows whether to
 * watch or to go and make tea. It is a pure function of what the screen already knows: how long
 * the audio is, where it runs, how long it has been running and the worker's percent.
 *
 * Before there is real progress it uses a deliberately cautious prior (finishing early is a nice
 * surprise, finishing late is a broken promise). As progress comes in, what was actually observed
 * takes over. The shown value is rounded into calm steps and smoothed so it never jitters or
 * suddenly climbs.
 */
internal object ProcessingEstimate {

    /** What the time line says. */
    sealed interface TimeLeft {
        /** Not enough to go on (no audio length and no real progress yet), or running past the estimate. */
        data object Unknown : TimeLeft
        data object AlmostDone : TimeLeft
        data object UnderAMinute : TimeLeft
        data class Minutes(val minutes: Int) : TimeLeft
    }

    /** Cloud: upload plus model time, roughly a tenth of the audio's length, plus a fixed overhead. */
    private const val CloudRatio = 0.12
    private const val CloudOverheadMs = 45_000L
    /** On this phone: speech models run at about real time on a mid-range phone; stay cautious. */
    private const val DeviceRatio = 0.8
    private const val DeviceOverheadMs = 30_000L

    /** Below this percent, or this early, the observed rate is noise (model loading, upload). */
    private const val TrustFromPercent = 8
    private const val TrustFromElapsedMs = 15_000L
    /** By this percent the observed rate is fully trusted. */
    private const val FullTrustPercent = 60
    const val AlmostDonePercent = 95

    /** The cautious prior for the whole run, or null when the audio length is unknown. */
    fun priorTotalMs(audioMs: Long, profile: ProcessingProfile): Long? {
        if (audioMs <= 0) return null
        return if (profile == ProcessingProfile.OFFLINE) {
            (audioMs * DeviceRatio).roundToLong() + DeviceOverheadMs
        } else {
            (audioMs * CloudRatio).roundToLong() + CloudOverheadMs
        }
    }

    /** Raw milliseconds left, or null when there is nothing honest to say. */
    fun remainingMs(audioMs: Long, profile: ProcessingProfile, elapsedMs: Long, percent: Int): Long? {
        val p = percent.coerceIn(0, 100)
        if (p >= AlmostDonePercent) return 0L
        val prior = priorTotalMs(audioMs, profile)?.let { max(0L, it - elapsedMs) }
        val trusted = p >= TrustFromPercent && elapsedMs >= TrustFromElapsedMs
        if (!trusted) return prior?.takeIf { it > 0 }
        val observed = (elapsedMs * (100 - p) / p.toDouble()).roundToLong()
        if (prior == null || prior == 0L) return observed
        val weight = ((p - TrustFromPercent).toDouble() / (FullTrustPercent - TrustFromPercent)).coerceIn(0.0, 1.0)
        return (prior * (1 - weight) + observed * weight).roundToLong()
    }

    /**
     * Smooths what is shown: time left only counts down with the clock, unless the new estimate is
     * clearly worse (more than half as long again), which is then shown rather than hidden.
     */
    fun smooth(previousShownMs: Long?, sinceShownMs: Long, nextMs: Long?): Long? {
        if (nextMs == null) return null
        if (previousShownMs == null) return nextMs
        val countedDown = max(0L, previousShownMs - sinceShownMs)
        return when {
            nextMs <= countedDown -> nextMs
            nextMs > countedDown * 3 / 2 + 30_000L -> nextMs
            else -> countedDown
        }
    }

    /**
     * Remembers what was last shown, so [smooth] can count it down. Calling [next] twice with the
     * same elapsed time gives the same answer, so a repeated composition changes nothing.
     */
    class Smoother {
        private var shownMs: Long? = null
        private var shownAtElapsedMs = 0L

        fun next(elapsedMs: Long, rawMs: Long?): Long? {
            val result = smooth(shownMs, elapsedMs - shownAtElapsedMs, rawMs)
            shownMs = result
            shownAtElapsedMs = elapsedMs
            return result
        }
    }

    /** Calm steps: under a minute, then whole minutes up to 10, then fives. */
    fun bucket(remainingMs: Long?, percent: Int): TimeLeft {
        if (percent >= AlmostDonePercent) return TimeLeft.AlmostDone
        if (remainingMs == null) return TimeLeft.Unknown
        if (remainingMs < 45_000L) return TimeLeft.UnderAMinute
        val minutes = ceil(remainingMs / 60_000.0).toInt()
        return TimeLeft.Minutes(if (minutes <= 10) minutes else ((minutes + 4) / 5) * 5)
    }
}

/**
 * The time line under the stage name: "About 4 min left · you can leave this screen". Falls back to
 * the elapsed time when there is no honest estimate.
 */
@Composable
internal fun rememberTimeLeftLine(audioMs: Long, profile: ProcessingProfile, elapsedMs: Long, percent: Int): String {
    // A plain holder, not state: it only remembers the last value shown and never triggers recomposition.
    val smoother = remember { ProcessingEstimate.Smoother() }
    val shown = smoother.next(elapsedMs, ProcessingEstimate.remainingMs(audioMs, profile, elapsedMs, percent))
    val leave = stringResource(R.string.processing_time_leave_screen)
    val lead = when (val bucket = ProcessingEstimate.bucket(shown, percent)) {
        ProcessingEstimate.TimeLeft.AlmostDone -> stringResource(R.string.processing_time_almost_done)
        ProcessingEstimate.TimeLeft.UnderAMinute -> stringResource(R.string.processing_time_under_minute)
        is ProcessingEstimate.TimeLeft.Minutes -> pluralStringResource(R.plurals.processing_time_minutes_left, bucket.minutes, bucket.minutes)
        ProcessingEstimate.TimeLeft.Unknown -> formatElapsed(elapsedMs)
    }
    return stringResource(R.string.processing_time_line, lead, leave)
}
