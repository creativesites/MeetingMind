package com.craftflowtechnologies.meetingmind.core.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CrashLogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a report names the version, the thread and the failing line`() {
        val text = CrashLog.report(Thread.currentThread(), IllegalStateException("boom"), "1.0-test")
        assertTrue(text.startsWith("MeetingMind 1.0-test — crash at "))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.contains("CrashLogTest"))
    }

    @Test
    fun `a saved crash is found once and gone after it is cleared`() {
        File(context.filesDir, "last_crash.txt").writeText("something went wrong")
        assertEquals("something went wrong", CrashLog.pending(context))
        CrashLog.clear(context)
        assertNull(CrashLog.pending(context))
    }

    @Test
    fun `a very long trace is cut so it can be copied`() {
        val text = CrashLog.report(Thread.currentThread(), RuntimeException("x".repeat(50_000)), "v")
        assertNotNull(text)
        assertEquals(12_000, text.length)
    }
}
