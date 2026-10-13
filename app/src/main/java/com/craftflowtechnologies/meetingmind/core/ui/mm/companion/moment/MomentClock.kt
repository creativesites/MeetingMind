package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Freezes every moment below at this many **played** milliseconds (goldens, the Lab scrubber). Null plays
 * in real time.
 */
val LocalMomentTime = staticCompositionLocalOf<Int?> { null }

/** Real time → moment time, pure, for tests: elapsed / animator scale, clamped to the moment's length. */
object MomentTimeline {
    /** Played ms at [frameNanos] for a moment that started at [startNanos]. Scale 0 (animations off) is the end. */
    fun playedMs(startNanos: Long, frameNanos: Long, durationScale: Float, playedMs: Int): Float {
        if (durationScale <= 0f) return playedMs.toFloat()
        val elapsed = (frameNanos - startNanos).coerceAtLeast(0L) / 1_000_000f
        return (elapsed / durationScale).coerceAtMost(playedMs.toFloat())
    }
}

/**
 * The clock of one moment (§7.4): a frame loop that runs **only while the moment plays** and stops at its
 * end (0 fps after). Skip jumps to the end. When the screen drops below STARTED mid-moment the clock
 * jumps to the end too, so a return shows the end state and nothing replays.
 */
@Stable
class MomentClock internal constructor(val playedMs: Int) {
    /** Played milliseconds, 0..[playedMs]. Read it in the draw phase. */
    var timeMs: Float by mutableFloatStateOf(0f)
        internal set
    var playing: Boolean by mutableStateOf(true)
        internal set
    /** True when the end came from a skip (tap, Back, leaving the app): no haptic after it. */
    var skipped: Boolean by mutableStateOf(false)
        internal set

    val atEnd: Boolean get() = !playing

    fun skip() {
        if (!playing) return
        skipped = true
        timeMs = playedMs.toFloat()
        playing = false
    }

    internal fun finish() {
        timeMs = playedMs.toFloat()
        playing = false
    }
}

/**
 * Runs [MomentClock] for [playedMs] (before the system animator scale [durationScale]). A new [key]
 * restarts it. [onFrame] sees each frame's played ms (haptics); [onEnd] runs once at the end or skip.
 */
@Composable
fun rememberMomentClock(
    key: Any?,
    playedMs: Int,
    durationScale: Float,
    onFrame: (prevMs: Float, nowMs: Float) -> Unit = { _, _ -> },
    onEnd: () -> Unit = {}
): MomentClock {
    val frozen = LocalMomentTime.current
    val clock = remember(key, playedMs) { MomentClock(playedMs) }
    val endCb by rememberUpdatedState(onEnd)
    val frameCb by rememberUpdatedState(onFrame)
    LaunchedEffect(clock, frozen, durationScale) {
        if (frozen != null) {
            clock.timeMs = frozen.toFloat().coerceIn(0f, playedMs.toFloat())
            clock.playing = frozen < playedMs
            return@LaunchedEffect
        }
        if (!clock.playing) return@LaunchedEffect
        if (durationScale <= 0f) { clock.finish(); endCb(); return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (clock.playing) {
            val now = withFrameNanos { it }
            val prev = clock.timeMs
            val t = MomentTimeline.playedMs(start, now, durationScale, playedMs)
            clock.timeMs = t
            frameCb(prev, t)
            if (t >= playedMs) clock.finish()
        }
        endCb()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(clock, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && clock.playing && frozen == null) clock.skip()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return clock
}
