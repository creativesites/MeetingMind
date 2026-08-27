package com.example.core.audio

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Covers what's safely testable off-device about the Phase 15 §Part 2 §3.2 rewrite (service owns
 * the recorder) without touching real [android.media.MediaRecorder] — starting/pausing/resuming/
 * stopping an actual recording needs a real microphone and is not exercised here or anywhere else
 * in this suite; that gap is called out explicitly in this phase's status report, not hidden.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34])
class MeetingRecordingServiceTest {

    @Test
    fun `service starts at RecordingState IDLE before any recording begins`() {
        val controller = Robolectric.buildService(MeetingRecordingService::class.java)
        val service = controller.create().get()
        assertEquals(RecordingState.IDLE, service.state.value)
        assertEquals(0f, service.amplitude.value, 0.01f)
        assertEquals(0L, service.durationMs.value)
        assertTrue(!service.focusInterrupted.value)
    }

    @Test
    fun `binding returns a LocalBinder pointing at the same service instance`() {
        val controller = Robolectric.buildService(MeetingRecordingService::class.java)
        val service = controller.create().get()
        val binder = service.onBind(Intent(ApplicationProvider.getApplicationContext(), MeetingRecordingService::class.java))
        val localBinder = binder as? MeetingRecordingService.LocalBinder
        assertTrue("onBind() should return a LocalBinder", localBinder != null)
        assertSame(service, localBinder!!.service)
    }
}
