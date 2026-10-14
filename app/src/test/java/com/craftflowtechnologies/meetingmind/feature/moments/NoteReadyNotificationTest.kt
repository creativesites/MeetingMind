package com.craftflowtechnologies.meetingmind.feature.moments

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.NoteReadyCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/** Z-34: the note-ready notification's copy, its cap, the glyph and the link into the note. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteReadyNotificationTest {
    private fun copy(sermon: Boolean = false, sensitive: Boolean = false, onDevice: Boolean = false, voiced: Boolean = true) = NoteReadyCopy.of(
        "Mercy at the table", sermon, sensitive, onDevice, voiced,
        ready = { "$it is ready." }, sermonReady = "Your sermon notes are ready 🙏", tapToOpen = "Tap to open your note.", madeOnPhone = "Made on your phone."
    )

    @Test fun `the voiced line, the sermon line and the plain status`() {
        assertEquals(NoteReadyCopy("Mercy at the table is ready.", "Tap to open your note.", alert = true), copy())
        assertEquals("Your sermon notes are ready 🙏", copy(sermon = true).title)
        // Beyond the cap: the plain status, quietly, never the sermon emoji.
        assertEquals(NoteReadyCopy("Mercy at the table is ready.", null, alert = false), copy(sermon = true, voiced = false))
        // Sacred notes never get the voiced line.
        assertEquals("Mercy at the table is ready.", copy(sermon = true, sensitive = true).title)
        assertEquals("Made on your phone.", copy(onDevice = true).text)
    }

    private fun facts(id: String) = NoteFacts(id, "Mercy at the table", NoteOutcome.COMPLETE, RecordingType.SERMON, NotebookSpace.FAITH,
        sermon = true, sensitive = false, audioMs = 600_000L, transcriptWords = 900, onDevice = false)

    private fun at(hour: Int): Long = Calendar.getInstance(TimeZone.getDefault()).apply {
        set(2026, Calendar.OCTOBER, 13, hour, 10, 0)
    }.timeInMillis

    @Test fun `once a day outside quiet hours, the glyph, and a tap opens the note`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "moments-${System.nanoTime()}.preferences_pb")
        val store = MomentLedgerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file })
        val nm = shadowOf(context.getSystemService(NotificationManager::class.java))

        postNoteReadyNotification(context, facts("a"), nowMs = at(23), store = store) // quiet hours: plain
        val night = nm.allNotifications.single()
        assertEquals(R.drawable.ic_zuri_glyph, night.smallIcon.resId)
        assertEquals("Mercy at the table is ready.", night.extras.getString("android.title"))
        assertNotNull(night.contentIntent)

        postNoteReadyNotification(context, facts("b"), nowMs = at(9), store = store) // the one voiced today
        postNoteReadyNotification(context, facts("c"), nowMs = at(11), store = store) // capped: plain again
        val titles = nm.allNotifications.map { it.extras.getString("android.title") }
        assertEquals(1, titles.count { it == "Your sermon notes are ready 🙏" })
        assertTrue(titles.count { it == "Mercy at the table is ready." } == 2)
        assertFalse(nm.allNotifications.any { it.smallIcon.resId != R.drawable.ic_zuri_glyph })
    }
}
