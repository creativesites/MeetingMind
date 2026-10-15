package com.craftflowtechnologies.meetingmind.feature.devotional

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted

/** Puts [text] on the clipboard and says so briefly. */
internal fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, "Couldn't copy", Toast.LENGTH_SHORT).show() }
}

/** Opens the Android Sharesheet with [text]. */
internal fun shareAsText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Two small icons — copy and share — for one block of the devotional. Quiet by design: they sit
 * beside the block's heading and never compete with it.
 */
@Composable
internal fun BlockActions(name: String, text: String?, modifier: Modifier = Modifier, tint: Color = InkMuted) {
    if (text.isNullOrBlank()) return
    val context = LocalContext.current
    Row(modifier) {
        IconButton(onClick = { copyToClipboard(context, name, text) }, modifier = Modifier.size(36.dp).testTag("copy_$name")) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $name", tint = tint, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { shareAsText(context, name, text) }, modifier = Modifier.size(36.dp).testTag("share_$name")) {
            Icon(Icons.Filled.Share, contentDescription = "Share $name", tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}
