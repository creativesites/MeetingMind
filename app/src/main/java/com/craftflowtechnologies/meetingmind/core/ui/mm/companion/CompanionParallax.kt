package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import com.craftflowtechnologies.meetingmind.core.companion.CompanionTier
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.asin

/**
 * Overrides [CompanionFlags.volumeLayer] below: true draws the T1 volume layer and runs parallax,
 * false never does. Null (the default) follows the flag. Companion Lab and the volume goldens set it.
 */
val LocalCompanionVolume = staticCompositionLocalOf<Boolean?> { null }

/**
 * When the volume layer and its parallax run (§4.2, Z-14). Pure, so every rule is unit-tested.
 *
 * - The layer itself: the flag (or [LocalCompanionVolume]) is on and the slot is T1 or T2 (≥ 48 dp).
 *   It is a still look, so it also draws in reduced motion and in the static goldens.
 * - Touch and scroll parallax: the layer is on and animations are on (duration scale > 0).
 * - The gyroscope: as above, plus a T2 hero (≥ 144 dp, as settled in §4.2), on screen and resumed.
 */
object CompanionParallaxGate {
    fun volume(enabled: Boolean, tier: CompanionTier): Boolean = enabled && tier != CompanionTier.T0

    fun parallax(enabled: Boolean, tier: CompanionTier, durationScale: Float): Boolean =
        volume(enabled, tier) && durationScale > 0f

    fun gyro(enabled: Boolean, tier: CompanionTier, durationScale: Float, onScreen: Boolean, resumed: Boolean): Boolean =
        parallax(enabled, tier, durationScale) && tier == CompanionTier.T2 && onScreen && resumed
}

/**
 * One slot's parallax input, each axis in −1..1, read in the draw phase only so it redraws without
 * recomposing.
 *
 * - [x], [y]: where the companion looks, from touch on the slot plus the shared [CompanionTilt].
 * - [lightY]: [y] plus the slot's scroll position, which only moves the light (the shading field,
 *   specular and rim), never the eyes: a companion high on the screen is lit a little from below.
 */
@Stable
class CompanionLook {
    internal var touchX by mutableFloatStateOf(0f)
    internal var touchY by mutableFloatStateOf(0f)
    internal var scroll by mutableFloatStateOf(0f)

    val x: Float get() = (touchX + CompanionTilt.x).coerceIn(-1f, 1f)
    val y: Float get() = (touchY + CompanionTilt.y).coerceIn(-1f, 1f)
    val lightX: Float get() = x
    val lightY: Float get() = (y + scroll * ScrollWeight).coerceIn(-1f, 1f)

    /** Follows a finger on the slot (without consuming it) and eases back on release. */
    fun touchModifier(): Modifier = Modifier.pointerInput(this) {
        coroutineScope {
            var release: Job? = null
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: continue
                    if (c.pressed) {
                        release?.cancel()
                        release = null
                        val w = size.width.coerceAtLeast(1) / 2f
                        val h = size.height.coerceAtLeast(1) / 2f
                        touchX = ((c.position.x - w) / w).coerceIn(-1f, 1f)
                        touchY = ((c.position.y - h) / h).coerceIn(-1f, 1f)
                    } else if (release == null && (touchX != 0f || touchY != 0f)) {
                        val fromX = touchX
                        val fromY = touchY
                        release?.cancel()
                        release = launch { animate(1f, 0f, animationSpec = tween(ReleaseMs)) { k, _ -> touchX = fromX * k; touchY = fromY * k } }
                    }
                }
            }
        }
    }

    /** Tracks the slot's place on screen: −1 at the top of the window, +1 at the bottom. */
    fun scrollModifier(): Modifier = Modifier.onGloballyPositioned { c ->
        val root = c.findRootCoordinates().size.height
        if (root <= 0) return@onGloballyPositioned
        val b = c.boundsInWindow()
        val v = (((b.top + b.bottom) / 2f / root) - 0.5f) * 2f
        val clamped = v.coerceIn(-1f, 1f)
        if (abs(clamped - scroll) > 0.01f) scroll = clamped
    }

    companion object {
        /** How much the scroll position moves the light. */
        const val ScrollWeight = 0.5f
        const val ReleaseMs = 450
    }
}

/**
 * The gyroscope for every companion in the app: one listener, ref-counted (§10.8 "no sensor
 * listeners off screen"). Slots call [acquire] while they need it and [release] when they stop;
 * the listener is registered on the first acquire and unregistered on the last release.
 *
 * Game rotation vector (no magnetometer, so no compass drift or calibration prompts), else
 * gravity. Asked for about 30 Hz and throttled to that in code, low-pass filtered, and measured
 * against a slowly re-centring baseline, so however the phone is held the companion rests centred
 * and only reacts to tilting. Publishes only changes ≥ 0.01, so a phone lying still causes no
 * redraws. Main thread only.
 */
object CompanionTilt {
    const val SamplingPeriodUs = 33_333
    private const val MinIntervalNs = 30_000_000L
    /** ±25° of tilt from the resting angle is the full −1..1. */
    private const val RangeRad = 0.436f
    /** Low-pass weight per sample (~120 ms at 30 Hz). */
    private const val Smooth = 0.3f
    /** Baseline drift per sample (~3 s at 30 Hz): the new resting angle. */
    private const val Drift = 0.011f

    private val _x = mutableFloatStateOf(0f)
    private val _y = mutableFloatStateOf(0f)
    val x: Float get() = _x.floatValue
    val y: Float get() = _y.floatValue

    /** Slots currently holding the sensor. */
    var holders = 0
        private set
    /** Whether the one listener is registered right now (Companion Lab, leak tests). */
    var registered = false
        private set
    /** Lifetime registrations, for tests: one per run of holders > 0. */
    var registrations = 0
        private set

    private var manager: SensorManager? = null
    private val rotation = FloatArray(9)
    private val angles = FloatArray(3)
    private var lastNs = 0L
    private var hasBase = false
    private var basePitch = 0f
    private var baseRoll = 0f
    private var sx = 0f
    private var sy = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (e.timestamp - lastNs < MinIntervalNs) return
            lastNs = e.timestamp
            val pitch: Float
            val roll: Float
            if (e.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotation, e.values)
                SensorManager.getOrientation(rotation, angles)
                pitch = angles[1]; roll = angles[2]
            } else {
                val g = SensorManager.GRAVITY_EARTH
                roll = asin((-e.values[0] / g).coerceIn(-1f, 1f))
                pitch = asin((e.values[1] / g).coerceIn(-1f, 1f))
            }
            onAngles(pitch, roll)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** One filtered sample; internal for tests. */
    internal fun onAngles(pitch: Float, roll: Float) {
        if (!hasBase) { basePitch = pitch; baseRoll = roll; hasBase = true }
        basePitch += (pitch - basePitch) * Drift
        baseRoll += (roll - baseRoll) * Drift
        val tx = ((roll - baseRoll) / RangeRad).coerceIn(-1f, 1f)
        val ty = ((pitch - basePitch) / RangeRad).coerceIn(-1f, 1f)
        sx += (tx - sx) * Smooth
        sy += (ty - sy) * Smooth
        if (abs(sx - _x.floatValue) >= 0.01f) _x.floatValue = sx
        if (abs(sy - _y.floatValue) >= 0.01f) _y.floatValue = sy
    }

    /** Takes a hold on the sensor; registers the listener for the first holder. */
    fun acquire(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "main thread only" }
        holders++
        if (holders == 1) register(context)
    }

    /** Drops a hold; the last one unregisters the listener and recentres. */
    fun release() {
        if (holders == 0) return
        holders--
        if (holders == 0) unregister()
    }

    private fun register(context: Context) {
        val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: return
        registered = runCatching { sm.registerListener(listener, sensor, SamplingPeriodUs) }
            .onFailure { Log.w("Companion", "tilt sensor unavailable", it) }.getOrDefault(false)
        if (registered) { manager = sm; registrations++ }
    }

    private fun unregister() {
        manager?.unregisterListener(listener)
        manager = null
        registered = false
        hasBase = false
        lastNs = 0L
        sx = 0f; sy = 0f
        _x.floatValue = 0f; _y.floatValue = 0f
    }
}

/** Holds [CompanionTilt] while [active]; releases it when [active] turns false or the slot leaves composition. */
@Composable
fun CompanionTiltEffect(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active, context) {
        if (active) CompanionTilt.acquire(context)
        onDispose { if (active) CompanionTilt.release() }
    }
}
