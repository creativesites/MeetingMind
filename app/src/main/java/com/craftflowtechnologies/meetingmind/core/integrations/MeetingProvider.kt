package com.craftflowtechnologies.meetingmind.core.integrations

/**
 * Interface for meeting recording platform providers (Zoom, Google Meet, Microsoft Teams).
 */
interface MeetingProvider : IntegrationProvider {
    override val category: IntegrationCategory
        get() = IntegrationCategory.MEETING_PLATFORM
}
