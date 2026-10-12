package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The crash guard: a run that stops mid-start turns Rive off for the version; reset turns it back on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RiveSafetyTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @After fun clean() = RiveSafety.reset(ctx)

    @Test fun `an unfinished start trips the guard for this version, and reset clears it`() {
        RiveSafety.reset(ctx)
        assertEquals(RiveSafety.Status.ALLOWED, RiveSafety.status(ctx))
        RiveSafety.markAttempt(ctx)
        // Simulate the next launch: the process died before markHealthy.
        val prefs = ctx.getSharedPreferences("companion_rive_safety", Context.MODE_PRIVATE)
        val inFlight = prefs.getBoolean("in_flight", false)
        assertEquals(true, inFlight)
        resetMemoryOnly()
        assertEquals(RiveSafety.Status.TRIPPED_FOR_VERSION, RiveSafety.status(ctx))
        RiveSafety.reset(ctx)
        assertEquals(RiveSafety.Status.ALLOWED, RiveSafety.status(ctx))
    }

    @Test fun `a healthy start leaves Rive on next launch`() {
        RiveSafety.reset(ctx)
        RiveSafety.markAttempt(ctx)
        RiveSafety.markHealthy(ctx)
        resetMemoryOnly()
        assertEquals(RiveSafety.Status.ALLOWED, RiveSafety.status(ctx))
    }

    @Test fun `a caught native failure is off for the process only`() {
        RiveSafety.reset(ctx)
        RiveSafety.disableForProcess()
        assertEquals(RiveSafety.Status.OFF_FOR_PROCESS, RiveSafety.status(ctx))
        resetMemoryOnly()
        assertEquals(RiveSafety.Status.ALLOWED, RiveSafety.status(ctx))
    }

    /** A new process: the in-memory state starts over, the preferences stay. */
    private fun resetMemoryOnly() {
        for (name in listOf("checked", "attempted", "healthy", "processDisabled")) {
            RiveSafety::class.java.getDeclaredField(name).apply { isAccessible = true }.setBoolean(RiveSafety, false)
        }
        RiveSafety::class.java.getDeclaredField("allowed").apply { isAccessible = true }.setBoolean(RiveSafety, true)
    }
}
