package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.craftflowtechnologies.meetingmind.core.share.ShareHelper
import java.io.File

/**
 * Standard Android Sharesheet output channel.
 * Moves existing share functionality behind [OutputChannel] without behavioral changes.
 */
class ShareSheetOutputChannel : OutputChannel {

    override val id: String = ID

    override val name: String = "Share to Apps"

    override val capabilities: Set<Capability> = setOf(
        Capability.OUTPUT_SHARE_SHEET
    )

    override val status: ProviderStatus = ProviderStatus.CONNECTED

    override val isEnabled: Boolean = true

    override val accountScope: String = "Android system Sharesheet (all sharing apps)"

    override fun shareText(context: Context, subject: String, text: String, title: String) {
        ShareHelper.shareText(context, subject, text, title)
    }

    override fun shareFile(context: Context, file: File, title: String) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = context.contentResolver.getType(uri) ?: when {
            file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            file.name.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            file.name.endsWith(".csv", ignoreCase = true) -> "text/csv"
            file.name.endsWith(".md", ignoreCase = true) -> "text/markdown"
            else -> "*/*"
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    companion object {
        const val ID = "output.sharesheet"
    }
}
