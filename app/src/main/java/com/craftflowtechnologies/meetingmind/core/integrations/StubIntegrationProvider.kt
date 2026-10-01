package com.craftflowtechnologies.meetingmind.core.integrations

/**
 * Stub representation for integrations planned for future releases.
 * Displays capabilities and enables the user to express interest via "Notify me".
 */
class StubIntegrationProvider(
    override val id: String,
    override val name: String,
    override val category: IntegrationCategory,
    override val capabilities: Set<Capability>,
    override val accountScope: String? = null
) : IntegrationProvider {

    override val status: ProviderStatus = ProviderStatus.COMING_LATER

    override val isEnabled: Boolean = false

    companion object {
        fun comingLaterProviders(): List<StubIntegrationProvider> = listOf(
            StubIntegrationProvider(
                id = "storage.drive",
                name = "Google Drive",
                category = IntegrationCategory.STORAGE,
                capabilities = setOf(
                    Capability.STORAGE_IMPORT_DOCS,
                    Capability.STORAGE_ATTACHMENTS,
                    Capability.STORAGE_RETRIEVAL
                ),
                accountScope = "Google Workspace documents"
            ),
            StubIntegrationProvider(
                id = "storage.onedrive",
                name = "Microsoft OneDrive",
                category = IntegrationCategory.STORAGE,
                capabilities = setOf(
                    Capability.STORAGE_IMPORT_DOCS,
                    Capability.STORAGE_ATTACHMENTS,
                    Capability.STORAGE_RETRIEVAL
                ),
                accountScope = "Microsoft 365 cloud documents"
            ),
            StubIntegrationProvider(
                id = "storage.dropbox",
                name = "Dropbox",
                category = IntegrationCategory.STORAGE,
                capabilities = setOf(
                    Capability.STORAGE_IMPORT_DOCS,
                    Capability.STORAGE_ATTACHMENTS
                ),
                accountScope = "Dropbox synced folders"
            ),
            StubIntegrationProvider(
                id = "channel.slack",
                name = "Slack",
                category = IntegrationCategory.OUTPUT_CHANNEL,
                capabilities = setOf(
                    Capability.OUTPUT_DIRECT_MESSAGE
                ),
                accountScope = "Connected Slack channels"
            ),
            StubIntegrationProvider(
                id = "meeting.zoom",
                name = "Zoom",
                category = IntegrationCategory.MEETING_PLATFORM,
                capabilities = setOf(
                    Capability.MEETING_RECORDING_IMPORT
                ),
                accountScope = "Cloud meeting recordings"
            ),
            StubIntegrationProvider(
                id = "meeting.meet",
                name = "Google Meet",
                category = IntegrationCategory.MEETING_PLATFORM,
                capabilities = setOf(
                    Capability.MEETING_RECORDING_IMPORT
                ),
                accountScope = "Google Drive Meet recordings"
            ),
            StubIntegrationProvider(
                id = "meeting.teams",
                name = "Microsoft Teams",
                category = IntegrationCategory.MEETING_PLATFORM,
                capabilities = setOf(
                    Capability.MEETING_RECORDING_IMPORT
                ),
                accountScope = "Teams cloud recordings"
            ),
            StubIntegrationProvider(
                id = "crm.hubspot",
                name = "HubSpot",
                category = IntegrationCategory.CRM,
                capabilities = setOf(
                    Capability.OUTPUT_SHARE_TEXT
                ),
                accountScope = "One-way contact & meeting note export"
            )
        )
    }
}
