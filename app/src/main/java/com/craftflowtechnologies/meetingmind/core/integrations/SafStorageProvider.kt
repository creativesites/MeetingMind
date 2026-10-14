package com.craftflowtechnologies.meetingmind.core.integrations

/**
 * Storage Access Framework (SAF) system picker provider.
 */
class SafStorageProvider : StorageProvider {

    override val id: String = ID

    override val name: String = "Device Storage"

    override val capabilities: Set<Capability> = setOf(
        Capability.STORAGE_IMPORT_DOCS,
        Capability.STORAGE_ATTACHMENTS,
        Capability.STORAGE_RETRIEVAL
    )

    override val status: ProviderStatus = ProviderStatus.CONNECTED

    override val isEnabled: Boolean = true

    override val accountScope: String = "Device documents & Storage Access Framework"

    companion object {
        const val ID = "storage.saf"
    }
}
