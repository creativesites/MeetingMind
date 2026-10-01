package com.craftflowtechnologies.meetingmind.core.integrations

import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent

/**
 * Interface for calendar event providers.
 */
interface CalendarProvider : IntegrationProvider {
    override val category: IntegrationCategory
        get() = IntegrationCategory.CALENDAR

    /** Whether required runtime permissions have been granted by the user. */
    fun hasPermission(): Boolean

    /** Retrieve events falling between [from] and [to] timestamps in epoch milliseconds. */
    suspend fun getEvents(from: Long, to: Long): List<CalendarEvent>
}
