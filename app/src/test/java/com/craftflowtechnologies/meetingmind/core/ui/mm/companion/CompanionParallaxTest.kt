package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionFlags
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionTier
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionDrawKit
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.FormDrawer
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSensor

/** Z-14: when the volume layer and parallax run, and that the one shared tilt listener never leaks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CompanionParallaxTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val sensors get() = shadowOf(ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager)

    @Before fun sensor() {
        sensors.addSensor(ShadowSensor.newInstance(Sensor.TYPE_GAME_ROTATION_VECTOR))
    }

    @After fun clean() {
        assertFalse("no Rive on the JVM", com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.RiveRuntime.available)
        while (CompanionTilt.holders > 0) CompanionTilt.release()
    }

    @Test fun `the flag ships off, so nothing changes until the perf check passes on a device`() {
        assertFalse(CompanionFlags.volumeLayer)
    }

    @Test fun `gate rules`() {
        val g = CompanionParallaxGate
        assertTrue(g.volume(true, CompanionTier.T1)); assertTrue(g.volume(true, CompanionTier.T2))
        assertFalse(g.volume(true, CompanionTier.T0)); assertFalse(g.volume(false, CompanionTier.T1))
        assertTrue("the layer is a still look: reduced motion keeps it", g.volume(true, CompanionTier.T1))
        assertTrue(g.parallax(true, CompanionTier.T1, 1f))
        assertFalse("no parallax with animations off", g.parallax(true, CompanionTier.T1, 0f))
        assertTrue(g.gyro(true, CompanionTier.T2, 1f, onScreen = true, resumed = true))
        assertFalse("gyro is for T2 heroes only", g.gyro(true, CompanionTier.T1, 1f, true, true))
        assertFalse(g.gyro(true, CompanionTier.T2, 0f, true, true))
        assertFalse(g.gyro(true, CompanionTier.T2, 1f, onScreen = false, resumed = true))
        assertFalse(g.gyro(true, CompanionTier.T2, 1f, onScreen = true, resumed = false))
        assertFalse(g.gyro(false, CompanionTier.T2, 1f, true, true))
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun `one listener for many heroes, gone when they leave or the app pauses`() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var count by mutableStateOf(2)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            // Canvas: the gyro is independent of the renderer, and the JVM must never start Rive's natives.
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalCompanionVolume provides true, LocalCompanionReducedMotion provides false, LocalCompanionForceCanvas provides true) {
                MeetMindTheme { repeat(count) { Companion(CompanionForm.ZURI, CompanionState.IDLE, 160.dp) } }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        assertEquals("two heroes hold it", 2, CompanionTilt.holders)
        assertTrue(CompanionTilt.registered)
        assertEquals("but share one listener", 1, sensors.listeners.size)
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }   // the app pauses
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        assertEquals("paused: unregistered", 0, sensors.listeners.size)
        assertFalse(CompanionTilt.registered)
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        assertEquals(1, sensors.listeners.size)
        count = 0                                   // the last hero leaves composition
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        assertEquals("left: unregistered", 0, sensors.listeners.size)
        assertEquals(0, CompanionTilt.holders)
    }

    @Test fun `no listener for T1 slots, reduced motion, or with the flag off`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MeetMindTheme { CompositionLocalProvider(LocalCompanionForceCanvas provides true) {
                CompositionLocalProvider(LocalCompanionVolume provides true, LocalCompanionReducedMotion provides false) {
                    Companion(CompanionForm.NAS, CompanionState.IDLE, 96.dp)
                }
                CompositionLocalProvider(LocalCompanionVolume provides true, LocalCompanionReducedMotion provides true) {
                    Companion(CompanionForm.NAS, CompanionState.IDLE, 160.dp)
                }
                CompositionLocalProvider(LocalCompanionReducedMotion provides false) {
                    Companion(CompanionForm.NAS, CompanionState.IDLE, 160.dp)   // flag (off) decides
                }
            } }
        }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0, CompanionTilt.holders)
        assertEquals(0, sensors.listeners.size)
    }

    @Test fun `the tilt filter recentres, smooths and clamps`() {
        CompanionTilt.acquire(ctx)
        CompanionTilt.onAngles(0.2f, 0.1f)          // the resting angle
        assertEquals(0f, CompanionTilt.x, 0.02f)
        repeat(10) { CompanionTilt.onAngles(0.2f, 0.1f + 0.3f) }   // tilt right
        assertTrue("follows the tilt: ${CompanionTilt.x}", CompanionTilt.x > 0.5f)
        repeat(10) { CompanionTilt.onAngles(0.2f, 3f) }
        assertTrue(CompanionTilt.x <= 1f)
        repeat(1200) { CompanionTilt.onAngles(0.2f, 3f) }           // held there: the baseline catches up
        assertEquals("recentres when held", 0f, CompanionTilt.x, 0.05f)
        CompanionTilt.release()
        assertEquals(0f, CompanionTilt.x, 0f)
    }

    private fun render(volume: Boolean, px: Int, form: CompanionForm): ImageBitmap {
        val bmp = ImageBitmap(px, px)
        val frame = CompanionFrame().apply {
            pose = CompanionPoses.pose(form, CompanionState.IDLE, 0f, 0f, true); reduced = true; lod = true; this.volume = volume
        }
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(bmp), androidx.compose.ui.geometry.Size(px.toFloat(), px.toFloat())
        ) { drawCompanion(CompanionDrawKit(), frame, FormDrawer.of(form), companionPalette(MMAccent.Indigo, false)) }
        return bmp
    }

    @Test fun `the volume layer changes the drawing only when on`() {
        for (form in CompanionForm.entries) {
            val off = render(false, 192, form).toPixelMap()
            val off2 = render(false, 192, form).toPixelMap()
            val on = render(true, 192, form).toPixelMap()
            var diffOn = 0; var diffOff = 0
            for (x in 0 until 192) for (y in 0 until 192) {
                if (off[x, y] != on[x, y]) diffOn++
                if (off[x, y] != off2[x, y]) diffOff++
            }
            assertEquals("$form off is deterministic", 0, diffOff)
            assertTrue("$form on shades the body: $diffOn px", diffOn > 500)
        }
    }
}
