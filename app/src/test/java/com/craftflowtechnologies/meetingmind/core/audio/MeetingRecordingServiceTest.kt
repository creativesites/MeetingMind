package com.craftflowtechnologies.meetingmind.core.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
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

    // ---- Keep recording after Back or a swipe-away ------------------------------------------

    @Test
    fun `live and finishing states keep the service, the rest let it stop`() {
        for (s in listOf(RecordingState.PREPARING, RecordingState.RECORDING, RecordingState.PAUSED)) {
            assertTrue(s.name, MeetingRecordingService.isLive(s))
            assertTrue(s.name, MeetingRecordingService.hasWork(s))
        }
        for (s in listOf(RecordingState.STOPPING, RecordingState.SAVING)) {
            assertFalse(s.name, MeetingRecordingService.isLive(s))
            assertTrue(s.name, MeetingRecordingService.hasWork(s))
        }
        for (s in RecordingState.entries.filterNot { MeetingRecordingService.hasWork(it) }) {
            assertFalse(s.name, MeetingRecordingService.isLive(s))
        }
        assertFalse(MeetingRecordingService.hasWork(RecordingState.IDLE))
        assertFalse(MeetingRecordingService.hasWork(RecordingState.SAVED))
    }

    @Test
    fun `a Stop with no recording running stops the service and is not sticky`() {
        val service = Robolectric.buildService(MeetingRecordingService::class.java).create().get()
        val stop = Intent(ApplicationProvider.getApplicationContext(), MeetingRecordingService::class.java)
            .setAction(MeetingRecordingService.ACTION_STOP)
        val result = service.onStartCommand(stop, 0, 1)
        assertEquals(Service.START_NOT_STICKY, result)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(RecordingState.IDLE, service.state.value)
    }

    @Test
    fun `a restart with no intent stops the service`() {
        val service = Robolectric.buildService(MeetingRecordingService::class.java).create().get()
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, Service.START_FLAG_REDELIVERY, 1))
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `a swipe from Recents with no recording stops the service`() {
        val service = Robolectric.buildService(MeetingRecordingService::class.java).create().get()
        service.onTaskRemoved(null)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `destroying an idle service is safe and leaves no recovery journal`() {
        val controller = Robolectric.buildService(MeetingRecordingService::class.java).create()
        controller.destroy()
        assertNull(RecordingJournalStore(ApplicationProvider.getApplicationContext()).read())
        assertFalse(MeetingRecordingService.capturing.value)
    }

    @Test
    fun `releasing the recorder for recovery with nothing recording does not throw`() {
        val recorder = AudioRecorder(ApplicationProvider.getApplicationContext())
        recorder.releaseForRecovery()
        assertEquals(RecorderState.STOPPED, recorder.state.value)
    }

    @Test
    fun `the notification says Recording, has Pause and Stop, and its tap opens the recorder`() {
        val service = Robolectric.buildService(MeetingRecordingService::class.java).create().get()
        val n = service.buildNotification()

        assertEquals("Recording", n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertTrue("ongoing", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Pause", "Stop"), n.actions.map { it.title.toString() })

        // Stop: an immutable intent to the service with ACTION_STOP.
        val stop = shadowOf(n.actions[1].actionIntent)
        assertTrue(stop.isServiceIntent)
        assertEquals(MeetingRecordingService.ACTION_STOP, stop.savedIntent.action)
        assertTrue("immutable", stop.flags and PendingIntent.FLAG_IMMUTABLE != 0)

        // Tap: an immutable activity intent carrying the live-recording deep link.
        val tap = shadowOf(n.contentIntent)
        assertTrue(tap.isActivityIntent)
        assertTrue("immutable", tap.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        com.craftflowtechnologies.meetingmind.core.notify.DeepLinks.handle(Intent(tap.savedIntent))
        assertEquals(
            com.craftflowtechnologies.meetingmind.core.notify.DeepLink.LiveRecording,
            com.craftflowtechnologies.meetingmind.core.notify.DeepLinks.consume()
        )
    }
}
