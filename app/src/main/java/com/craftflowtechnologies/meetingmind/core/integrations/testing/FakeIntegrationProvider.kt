package com.craftflowtechnologies.meetingmind.core.integrations.testing

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.integrations.CalendarProvider
import com.craftflowtechnologies.meetingmind.core.integrations.Capability
import com.craftflowtechnologies.meetingmind.core.integrations.EmailMessage
import com.craftflowtechnologies.meetingmind.core.integrations.EmailProvider
import com.craftflowtechnologies.meetingmind.core.integrations.EmailScopeFilter
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationCategory
import com.craftflowtechnologies.meetingmind.core.integrations.OutputChannel
import com.craftflowtechnologies.meetingmind.core.integrations.ProviderStatus
import com.craftflowtechnologies.meetingmind.core.integrations.StorageProvider
import java.io.File

/**
 * In-memory fake provider for automated unit testing.
 * Guarantees zero network calls and zero OS dependency in test suites.
 */
class FakeCalendarProvider(
    override val id: String = "calendar.fake",
    override val name: String = "Fake Calendar",
    override var isEnabled: Boolean = true,
    var hasPermissionGranted: Boolean = true,
    var mockEvents: List<CalendarEvent> = emptyList()
) : CalendarProvider {

    override val capabilities: Set<Capability> = setOf(
        Capability.CALENDAR_EVENTS,
        Capability.CALENDAR_ATTENDEES,
        Capability.CALENDAR_PREP,
        Capability.CALENDAR_WORKFLOW_RULES
    )

    override val status: ProviderStatus
        get() = if (hasPermissionGranted && isEnabled) ProviderStatus.CONNECTED else ProviderStatus.AVAILABLE

    override val accountScope: String = "Test calendar scope"

    override fun hasPermission(): Boolean = hasPermissionGranted

    override suspend fun getEvents(from: Long, to: Long): List<CalendarEvent> {
        if (!isEnabled || !hasPermissionGranted) return emptyList()
        return mockEvents.filter { it.begin in from..to || it.end in from..to }
    }
}

/**
 * In-memory fake email provider for automated unit testing.
 */
class FakeEmailProvider(
    override val id: String = "email.fake",
    override val name: String = "Fake Email",
    override var isEnabled: Boolean = true,
    var mockMessages: List<EmailMessage> = emptyList()
) : EmailProvider {

    override val capabilities: Set<Capability> = setOf(
        Capability.EMAIL_INDEX_READ,
        Capability.EMAIL_DRAFT_SEND,
        Capability.EMAIL_ATTACH_MINUTES
    )

    override val status: ProviderStatus
        get() = if (isEnabled) ProviderStatus.CONNECTED else ProviderStatus.AVAILABLE

    override val accountScope: String = "Test scoped emails"

    var draftedReplies: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun getScopedMessages(
        knownEmails: Set<String>,
        knownDomains: Set<String>,
        sinceEpochMs: Long
    ): List<EmailMessage> {
        if (!isEnabled) return emptyList()
        val sinceFiltered = mockMessages.filter { it.timestampMs >= sinceEpochMs }
        return EmailScopeFilter.filterInScope(sinceFiltered, knownEmails, knownDomains)
    }

    override fun prepareDraft(context: Context, toEmail: String, subject: String, body: String) {
        draftedReplies.add(toEmail to "$subject\n$body")
    }
}

/**
 * In-memory fake output channel for automated unit testing.
 */
class FakeOutputChannel(
    override val id: String = "output.fake",
    override val name: String = "Fake Output Channel",
    override var isEnabled: Boolean = true
) : OutputChannel {

    override val capabilities: Set<Capability> = setOf(
        Capability.OUTPUT_SHARE_SHEET,
        Capability.OUTPUT_DIRECT_MESSAGE
    )

    override val status: ProviderStatus = ProviderStatus.CONNECTED

    override val accountScope: String = "Test output channel"

    val sharedTexts: MutableList<Pair<String, String>> = mutableListOf()
    val sharedFiles: MutableList<File> = mutableListOf()

    override fun shareText(context: Context, subject: String, text: String, title: String) {
        sharedTexts.add(subject to text)
    }

    override fun shareFile(context: Context, file: File, title: String) {
        sharedFiles.add(file)
    }
}

/**
 * In-memory fake storage provider for automated unit testing.
 */
class FakeStorageProvider(
    override val id: String = "storage.fake",
    override val name: String = "Fake Storage",
    override var isEnabled: Boolean = true
) : StorageProvider {

    override val capabilities: Set<Capability> = setOf(
        Capability.STORAGE_IMPORT_DOCS,
        Capability.STORAGE_ATTACHMENTS,
        Capability.STORAGE_RETRIEVAL
    )

    override val status: ProviderStatus = ProviderStatus.CONNECTED

    override val accountScope: String = "Test storage"
}
