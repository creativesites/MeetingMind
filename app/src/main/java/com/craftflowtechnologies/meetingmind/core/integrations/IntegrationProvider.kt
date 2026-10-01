package com.craftflowtechnologies.meetingmind.core.integrations

/**
 * Category of integration per docs/PLAN_PROFESSIONAL.md §6.6.
 */
enum class IntegrationCategory(val title: String) {
    CALENDAR("Calendar"),
    EMAIL("Email"),
    STORAGE("Storage"),
    OUTPUT_CHANNEL("Share & Export"),
    MEETING_PLATFORM("Meeting Platforms"),
    TASK_MANAGER("Task Mirroring"),
    CRM("CRM Export")
}

/**
 * Connection and operational status of a provider.
 */
enum class ProviderStatus {
    /** Actively connected and enabled. */
    CONNECTED,
    /** Available to be connected / enabled by the user. */
    AVAILABLE,
    /** Planned for future release ("Coming later"); tapping records a local "Notify me" signal. */
    COMING_LATER,
    /** Disabled due to security, policy, or work profile constraint (e.g., Clinical/Legal default). */
    DISABLED_BY_POLICY
}

/**
 * Base interface for all integration providers in MeetingMind.
 */
interface IntegrationProvider {
    /** Unique persistent identifier (e.g. "calendar.device", "email.gmail"). */
    val id: String

    /** Human-readable display name (e.g. "Phone Calendar", "Google Workspace / Gmail"). */
    val name: String

    /** Classification category. */
    val category: IntegrationCategory

    /** The specific capabilities this provider affords. */
    val capabilities: Set<Capability>

    /** Current connection/readiness status. */
    val status: ProviderStatus

    /** Whether the user or policy has enabled this integration. */
    val isEnabled: Boolean

    /** Description of the data scope accessed by this provider (e.g., "Known people and domains only"). */
    val accountScope: String?

    /** Optional privacy/safety caution message. */
    val privacyNotice: String?
        get() = null
}
