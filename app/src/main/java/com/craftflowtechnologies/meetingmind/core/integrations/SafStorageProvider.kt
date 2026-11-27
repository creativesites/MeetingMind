package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Storage Access Framework (SAF) system picker provider.
 * Supports system document picking and write-to-pinned-vault (e.g. Obsidian vaults).
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

    fun writeToPinnedVault(
        context: Context,
        treeUriString: String,
        fileName: String,
        mimeType: String,
        content: ByteArray
    ): Uri? = runCatching {
        val treeUri = Uri.parse(treeUriString)
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val resolver = context.contentResolver
        val fileUri = DocumentsContract.createDocument(resolver, parentUri, mimeType, fileName) ?: return null
        resolver.openOutputStream(fileUri)?.use { out ->
            out.write(content)
        } ?: return null
        fileUri
    }.getOrNull()

    companion object {
        const val ID = "storage.saf"
    }
}
