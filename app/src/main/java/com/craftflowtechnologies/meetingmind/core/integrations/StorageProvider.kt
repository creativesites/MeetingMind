package com.craftflowtechnologies.meetingmind.core.integrations

/**
 * Interface for file and document storage providers.
 */
interface StorageProvider : IntegrationProvider {
    override val category: IntegrationCategory
        get() = IntegrationCategory.STORAGE
}
