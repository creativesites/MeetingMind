package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvents

/**
 * Android system calendar provider adapter.
 * Wraps existing [CalendarEvents] behind the [CalendarProvider] interface with zero behavioral changes.
 */
class DeviceCalendarProvider(
    private val context: Context,
    private val calendarEvents: CalendarEvents = CalendarEvents(context),
    private val enabledChecker: () -> Boolean = { true }
) : CalendarProvider {

    override val id: String = ID

    override val name: String = "Phone Calendar"

    override val capabilities: Set<Capability> = setOf(
        Capability.CALENDAR_EVENTS,
        Capability.CALENDAR_ATTENDEES,
        Capability.CALENDAR_PREP,
        Capability.CALENDAR_WORKFLOW_RULES
    )

    override val status: ProviderStatus
        get() = if (hasPermission() && isEnabled) ProviderStatus.CONNECTED else ProviderStatus.AVAILABLE

    override val isEnabled: Boolean
        get() = enabledChecker()

    override val accountScope: String
        get() = "All synced calendars on device"

    override fun hasPermission(): Boolean = calendarEvents.hasPermission()

    override suspend fun getEvents(from: Long, to: Long): List<CalendarEvent> {
        if (!isEnabled || !hasPermission()) return emptyList()
        return calendarEvents.between(from, to)
    }

    companion object {
        const val ID = "calendar.device"
    }
}
