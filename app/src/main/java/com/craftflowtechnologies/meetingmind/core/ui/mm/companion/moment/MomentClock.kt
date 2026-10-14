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
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * Companion Lab's speed control: when set, replaces the system animator scale for moments below
 * (4 = a quarter speed). Null uses the system scale.
 */
val LocalMomentDurationScale = staticCompositionLocalOf<Float?> { null }

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
    /**
     * True once skipped (a tap, Back, leaving the app). A skip rushes to the end in [SkipMs] (the spec's
     * 150 ms crossfade) and no haptic or sound fires after it.
     */
    var skipped: Boolean by mutableStateOf(false)
        internal set

    val atEnd: Boolean get() = !playing

    /** Rush to the end over [SkipMs]. Safe to call any time; a no-op once ended. */
    fun skip() {
        if (!playing) return
        skipped = true
        if (!looping) finish()
    }

    /** Jump to the end now (the screen stopped mid-moment; on return it shows the end state). */
    fun finishNow() {
        if (!playing) return
        skipped = true
        finish()
    }

    internal var looping = false

    internal fun finish() {
        timeMs = playedMs.toFloat()
        playing = false
    }

    companion object {
        const val SkipMs = 150f
    }
}

/**
 * Runs [MomentClock] for [playedMs] (before the system animator scale [durationScale]). [key] must be
 * stable and equal across recreation (a String such as the note id plus the moment): once a moment has
 * started under a key it is remembered as played (`rememberSaveable`), so a configuration change, a
 * process restore or a return to the screen shows the end state and never replays. A new key plays again.
 * [onFrame] sees each frame's played ms (haptics, the chime); [onEnd] runs once at the end or after a skip.
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
    var played by rememberSaveable(key, playedMs) { mutableStateOf(false) }
    val clock = remember(key, playedMs) { MomentClock(playedMs).also { if (played) it.finish() } }
    val endCb by rememberUpdatedState(onEnd)
    val frameCb by rememberUpdatedState(onFrame)
    LaunchedEffect(clock, frozen, durationScale) {
        if (frozen != null) {
            clock.timeMs = frozen.toFloat().coerceIn(0f, playedMs.toFloat())
            clock.playing = frozen < playedMs
            return@LaunchedEffect
        }
        if (!clock.playing) return@LaunchedEffect
        played = true
        if (durationScale <= 0f) { clock.finish(); endCb(); return@LaunchedEffect }
        val start = withFrameNanos { it }
        var skipStart = 0L
        var skipFrom = 0f
        clock.looping = true
        try {
            while (clock.playing) {
                val now = withFrameNanos { it }
                val prev = clock.timeMs
                val t = if (clock.skipped) {
                    if (skipStart == 0L) { skipStart = now; skipFrom = prev }
                    val u = ((now - skipStart) / 1_000_000f / MomentClock.SkipMs).coerceIn(0f, 1f)
                    skipFrom + (playedMs - skipFrom) * u
                } else MomentTimeline.playedMs(start, now, durationScale, playedMs)
                clock.timeMs = t
                if (!clock.skipped) frameCb(prev, t)
                if (t >= playedMs) clock.finish()
            }
        } finally {
            clock.looping = false
        }
        endCb()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(clock, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && clock.playing && frozen == null) clock.finishNow()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return clock
}
