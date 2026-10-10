package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionTier
import kotlin.math.exp

/**
 * Smooths a raw level (mic or TTS amplitude, 0..1) with separate attack and release (§3.1
 * Listening: 60 ms attack, 250 ms release). Not thread-safe; one per slot.
 */
class LevelSmoother(private val attackMs: Float = 60f, private val releaseMs: Float = 250f) {
    var value: Float = 0f
        private set

    /** Moves toward [target] over [dtMs] and returns the smoothed value. */
    fun update(target: Float, dtMs: Float): Float {
        val goal = target.coerceIn(0f, 1f)
        if (dtMs <= 0f) return value
        val tau = if (goal > value) attackMs else releaseMs
        value += (goal - value) * (1f - exp(-dtMs / tau))
        return value
    }

    fun reset(to: Float = 0f) { value = to.coerceIn(0f, 1f) }
}

/** When the animation clock may run (§10.6, §10.8). Pure, so it is unit-tested. */
object CompanionClockGate {
    /**
     * @param durationScale the system animator duration scale; 0 is "Remove animations".
     * @param visible on screen (window bounds) and the lifecycle is at least STARTED.
     * @param finished a one-shot or capped loop has played out.
     */
    fun shouldRun(spec: MotionSpec, tier: CompanionTier, durationScale: Float, visible: Boolean, finished: Boolean): Boolean =
        durationScale > 0f && tier != CompanionTier.T0 && visible && !finished

    /** Whether a frame at [elapsedMs] since the last drawn one should draw, for a [fps] cap. */
    fun frameDue(elapsedMs: Float, fps: Int): Boolean = elapsedMs >= 1000f / fps - 2f
}

/** Tracks whether a composable is on screen; read [isOnScreen] after attaching [modifier]. */
class OnScreenTracker {
    var isOnScreen by mutableStateOf(true)
        private set

    val modifier: Modifier = Modifier.onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        isOnScreen = b.width > 0f && b.height > 0f
    }
}

@Composable
fun rememberOnScreenTracker(): OnScreenTracker = remember { OnScreenTracker() }

/** True while the host lifecycle is at least [min]. */
@Composable
fun lifecycleAtLeast(min: Lifecycle.State): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(min)
}

/**
 * The frame clock: seconds of animation time, advanced by `withFrameNanos` only while [running].
 * Read the returned state in the draw phase, so frames redraw without recomposing. Time scales by
 * 1 / [durationScale]; [fps] caps the update rate (Thinking and Reading run at 30). Restarts from
 * 0 when [restartKey] changes. Calls [onFinished] once when [runMs] of animation time has played.
 */
@Composable
fun rememberCompanionTime(
    running: Boolean,
    restartKey: Any?,
    fps: Int = 60,
    durationScale: Float = 1f,
    runMs: Long? = null,
    onFinished: () -> Unit = {}
): State<Float> {
    val time: MutableFloatState = remember(restartKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(restartKey, running, fps, durationScale, runMs) {
        if (!running) return@LaunchedEffect
        val scale = if (durationScale > 0f) durationScale else 1f
        var last = -1L
        var sinceDraw = 0f
        var t = time.floatValue
        while (true) {
            val now = withFrameNanos { it }
            if (last >= 0) {
                val dtMs = (now - last) / 1_000_000f
                t += dtMs / 1000f / scale
                sinceDraw += dtMs
            }
            last = now
            if (runMs != null && t * 1000f >= runMs) {
                time.floatValue = runMs / 1000f
                onFinished()
                break
            }
            if (CompanionClockGate.frameDue(sinceDraw, fps)) {
                time.floatValue = t
                sinceDraw = 0f
            }
        }
    }
    return time
}
