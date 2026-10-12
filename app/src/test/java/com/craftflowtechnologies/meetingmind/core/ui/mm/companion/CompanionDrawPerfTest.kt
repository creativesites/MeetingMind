package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionDrawKit
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.FormDrawer
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.management.ManagementFactory

/**
 * The Canvas draw path's cost (§10.8, Z-14), on the JVM with Robolectric's native (Skia) canvas:
 *
 * - **Allocations:** the per-frame path (pose into reused buffers, then drawing) must allocate
 *   nothing on the JVM heap once warm, for every form, every animated visual, with and without the
 *   volume layer. Measured with the JVM's per-thread allocated-bytes counter.
 * - **Draw time:** mean time per frame for each form, volume off and on, at 96 dp (288 px, xxhdpi).
 *   Printed and written to `build/reports/companion-perf.txt`. It is a host-CPU number for comparing
 *   forms and the volume layer's relative cost, not a device frame time (§10.8 has the device
 *   procedure).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CompanionDrawPerfTest {
    private val visuals: List<CompanionVisual> = CompanionState.entries + CreateMode.entries
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private class Rig(form: CompanionForm, volume: Boolean, px: Int = 288) {
        val bitmap = ImageBitmap(px, px)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val size = Size(px.toFloat(), px.toFloat())
        val density = Density(3f)
        val kit = CompanionDrawKit()
        val frame = CompanionFrame().apply { lod = true }
        val pose = Pose()
        val drawer = FormDrawer.of(form)
        val palette = companionPalette(MMAccent.Indigo, false)
    }

    /** One animated frame, as `CanvasCompanion` does it. */
    private fun Rig.frame(form: CompanionForm, visual: CompanionVisual, i: Int) {
        val t = i / 60f
        val level = 0.5f + 0.4f * kotlin.math.sin(t * 7f)
        CompanionPoses.poseInto(pose, form, visual, t, level, false)
        frame.pose = pose; frame.visual = visual; frame.t = t; frame.level = level; frame.reduced = false
        scope.draw(density, LayoutDirection.Ltr, canvas, size) { drawCompanion(kit, frame, drawer, palette) }
    }

    private companion object { const val Frames = 960 }

    private fun allocatedBytes() = threads.getThreadAllocatedBytes(Thread.currentThread().id)

    @Test fun `the draw path allocates nothing per frame once warm`() {
        val problems = mutableListOf<String>()
        // The measuring call itself: calibrate it away.
        val c0 = allocatedBytes(); val c1 = allocatedBytes(); val overhead = c1 - c0
        // Warm the whole path first (class loading and JIT of every branch), then measure.
        for (form in CompanionForm.entries) { val rig = Rig(form, false); for (v in visuals) repeat(300) { rig.frame(form, v, it) } }
        for (volume in listOf(false)) for (form in CompanionForm.entries) {
            val rig = Rig(form, volume)
            for (v in visuals) {
                repeat(120) { rig.frame(form, v, it) }   // warm: brushes, strokes, shaders, JIT
                val before = allocatedBytes()
                for (i in 120 until 120 + Frames) rig.frame(form, v, i)
                val perFrame = (allocatedBytes() - before - overhead).toDouble() / Frames
                // The smallest JVM object is 16 bytes: anything allocated every frame shows as ≥ 16.
                // Below 8 is the JVM's own sporadic bookkeeping (JIT, safepoints), not the draw path.
                if (perFrame >= 8.0) problems += "$form ${v.name} volume=$volume: %.1f bytes per frame".format(perFrame)
            }
        }
        assertEquals(problems.joinToString("\n"), 0, problems.size)
    }

    @Test fun `draw time per frame, each form, volume off and on`() {
        val lines = mutableListOf("form       volume  mean µs/frame (host CPU, 288 px, Robolectric native canvas)")
        val visualsTimed = listOf(CompanionState.IDLE, CompanionState.LISTENING, CompanionState.THINKING, CompanionState.CELEBRATING)
        for (form in CompanionForm.entries) for (volume in listOf(false)) {
            val rig = Rig(form, volume)
            repeat(600) { i -> rig.frame(form, visualsTimed[i % visualsTimed.size], i) }
            val n = 2_000
            val start = System.nanoTime()
            for (i in 0 until n) rig.frame(form, visualsTimed[i % visualsTimed.size], i)
            val us = (System.nanoTime() - start) / 1000.0 / n
            lines += "%-10s %-7s %.1f".format(form.name, if (volume) "on" else "off", us)
        }
        val text = lines.joinToString("\n")
        println(text)
        File("build/reports").mkdirs()
        File("build/reports/companion-perf.txt").writeText(text + "\n")
    }
}
