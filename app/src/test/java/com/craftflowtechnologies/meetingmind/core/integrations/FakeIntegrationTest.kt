package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.integrations.testing.FakeCalendarProvider
import com.craftflowtechnologies.meetingmind.core.integrations.testing.FakeEmailProvider
import com.craftflowtechnologies.meetingmind.core.integrations.testing.FakeOutputChannel
import com.craftflowtechnologies.meetingmind.core.integrations.testing.FakeStorageProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Tests verifying the behavior of test fakes used across integration tests.
 */
@RunWith(RobolectricTestRunner::class)
class FakeIntegrationTest {

    @Test
    fun fakeCalendar_filtersByTimestampWindow() = runBlocking {
        val calendar = FakeCalendarProvider(
            mockEvents = listOf(
                CalendarEvent(1L, 100L, 200L, "Event 1", null, false, "Work", null),
                CalendarEvent(2L, 500L, 600L, "Event 2", null, false, "Work", null)
            )
        )

        val events = calendar.getEvents(50L, 250L)
        assertEquals(1, events.size)
        assertEquals("Event 1", events[0].title)

        calendar.hasPermissionGranted = false
        assertEquals(0, calendar.getEvents(50L, 250L).size)
    }

    @Test
    fun fakeEmail_enforcesScopeFilteringOnMockMessages() = runBlocking {
        val emailProvider = FakeEmailProvider(
            mockMessages = listOf(
                EmailMessage("1", "t1", "Alice", "alice@corp.com", emptyList(), "Update", "Hi", 1000L, "fake"),
                EmailMessage("2", "t2", "Spammer", "spam@random.com", emptyList(), "Deal", "Buy now", 1000L, "fake")
            )
        )

        val results = emailProvider.getScopedMessages(
            knownEmails = setOf("alice@corp.com"),
            knownDomains = emptySet(),
            sinceEpochMs = 500L
        )

        assertEquals(1, results.size)
        assertEquals("Update", results[0].subject)
    }

    @Test
    fun fakeEmail_recordsDraftReplies() {
        val emailProvider = FakeEmailProvider()
        // Fake context not needed for fake implementation
        emailProvider.prepareDraft(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            toEmail = "bob@client.com",
            subject = "Follow up",
            body = "Meeting notes attached"
        )

        assertEquals(1, emailProvider.draftedReplies.size)
        assertEquals("bob@client.com", emailProvider.draftedReplies[0].first)
        assertTrue(emailProvider.draftedReplies[0].second.contains("Follow up"))
    }

    @Test
    fun fakeOutputChannel_recordsSharedContent() {
        val channel = FakeOutputChannel()
        val context: Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()

        channel.shareText(context, "Summary", "Here is the meeting summary", "Share")
        assertEquals(1, channel.sharedTexts.size)
        assertEquals("Summary", channel.sharedTexts[0].first)

        val testFile = File("/tmp/test.pdf")
        channel.shareFile(context, testFile, "Share file")
        assertEquals(1, channel.sharedFiles.size)
    }

    @Test
    fun fakeStorage_providesExpectedCapabilities() {
        val storage = FakeStorageProvider()
        assertTrue(Capability.STORAGE_IMPORT_DOCS in storage.capabilities)
        assertTrue(Capability.STORAGE_ATTACHMENTS in storage.capabilities)
        assertTrue(Capability.STORAGE_RETRIEVAL in storage.capabilities)
        assertEquals(ProviderStatus.CONNECTED, storage.status)
    }
}
